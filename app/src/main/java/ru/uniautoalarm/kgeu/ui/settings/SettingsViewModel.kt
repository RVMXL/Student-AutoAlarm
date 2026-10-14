package ru.uniautoalarm.kgeu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.uniautoalarm.kgeu.alarm.ReminderScheduler
import ru.uniautoalarm.kgeu.data.db.CustomEventEntity
import ru.uniautoalarm.kgeu.data.db.ExceptionEntity
import ru.uniautoalarm.kgeu.data.model.AlarmMode
import ru.uniautoalarm.kgeu.data.model.CalendarStyle
import ru.uniautoalarm.kgeu.data.model.CustomEvent
import ru.uniautoalarm.kgeu.data.model.ExceptionRule
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.ThemeMode
import ru.uniautoalarm.kgeu.data.repository.ScheduleRepository
import ru.uniautoalarm.kgeu.di.AppContainer
import ru.uniautoalarm.kgeu.ui.theme.ACCENT_PALETTES

/**
 * ViewModel экрана настроек: настройки (DataStore), исключения и свои занятия (Room).
 * Все записи БД — suspend-запросы DAO из viewModelScope; настройки — через
 * SettingsRepository.update (атомарно, весь снимок).
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    /** Снимок настроек: null до первого значения DataStore. */
    val settings: StateFlow<Settings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Исключения (реактивно из Room). */
    val exceptions: StateFlow<List<ExceptionRule>> = container.database.exceptionDao().all()
        .map { list ->
            list.map { e -> ExceptionRule(id = e.id, subject = e.subject, enabled = e.enabled) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Свои занятия (реактивно из Room). */
    val customEvents: StateFlow<List<CustomEvent>> = container.database.customEventDao().all()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Уникальные дисциплины из кэша расписания — подсказки для исключений. */
    val subjects: StateFlow<List<String>> = container.scheduleRepository.subjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Настройки-«примитивы» ---

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            container.settings.update { it.copy(themeMode = mode) }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(dynamicColor = enabled) }
        }
    }

    fun setAccent(index: Int) {
        viewModelScope.launch {
            container.settings.update {
                it.copy(accentIndex = index.coerceIn(0, ACCENT_PALETTES.lastIndex))
            }
        }
    }

    fun setCalendarStyle(style: CalendarStyle) {
        viewModelScope.launch {
            container.settings.update { it.copy(calendarStyle = style) }
        }
    }

    fun setWakeOffset(minutes: Int) {
        viewModelScope.launch {
            container.settings.update {
                it.copy(wakeOffsetMinutes = minutes.coerceIn(MIN_OFFSET, MAX_OFFSET))
            }
        }
    }

    fun setMode(mode: AlarmMode) {
        viewModelScope.launch {
            container.settings.update { it.copy(mode = mode) }
        }
    }

    fun setWeekAheadDays(days: Int) {
        viewModelScope.launch {
            container.settings.update {
                it.copy(weekAheadDays = days.coerceIn(MIN_WEEK_AHEAD_DAYS, MAX_WEEK_AHEAD_DAYS))
            }
        }
    }

    fun toggleAuto(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(autoAlarmEnabled = enabled) }
        }
    }

    fun setBackground(path: String?) {
        viewModelScope.launch {
            container.settings.update { it.copy(backgroundImagePath = path) }
        }
    }

    fun setBackgroundOpacity(value: Float) {
        viewModelScope.launch {
            container.settings.update { it.copy(backgroundOpacity = value.coerceIn(0f, 1f)) }
        }
    }

    // --- Напоминание о будильнике (фолбэк для Realme/Huawei и др.) ---

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(reminderEnabled = enabled) }
            ReminderScheduler.rescheduleAfterSettingsChange(container)
        }
    }

    fun setReminderTime(time: LocalTime) {
        viewModelScope.launch {
            container.settings.update { it.copy(reminderTime = time) }
            ReminderScheduler.rescheduleAfterSettingsChange(container)
        }
    }

    /** Экран часов при постановке (SKIP_UI=false) — для ColorOS/Realme UI, где фон-постановка блокируется. */
    fun setAlarmShowClockUi(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(alarmShowClockUi = enabled) }
        }
    }

    // --- Эффект стекла ---

    fun setGlassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(glassEnabled = enabled) }
        }
    }

    fun setGlassIntensity(value: Float) {
        viewModelScope.launch {
            container.settings.update { it.copy(glassIntensity = value.coerceIn(0.05f, 0.9f)) }
        }
    }

    /** Смена цели: сбрасываем флаг мастера — он откроется заново, offset подтянется в init. */
    fun changeTarget() {
        viewModelScope.launch {
            container.settings.update { it.copy(setupComplete = false) }
        }
    }

    // --- Исключения ---

    /** Добавить исключение: без пустых и дублей (сравнение по нормализованному названию). */
    fun addException(subject: String) {
        val trimmed = subject.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val dao = container.database.exceptionDao()
            val normalized = ScheduleRepository.normalizeSubject(trimmed)
            val exists = dao.allOnce().any {
                ScheduleRepository.normalizeSubject(it.subject) == normalized
            }
            if (!exists) dao.upsert(ExceptionEntity(subject = trimmed))
        }
    }

    /** Включить/выключить правило (DAO не имеет setEnabled — переписываем строку целиком). */
    fun setExceptionEnabled(rule: ExceptionRule, enabled: Boolean) {
        viewModelScope.launch {
            container.database.exceptionDao()
                .upsert(ExceptionEntity(id = rule.id, subject = rule.subject, enabled = enabled))
        }
    }

    fun deleteException(id: Long) {
        viewModelScope.launch {
            container.database.exceptionDao().delete(id)
        }
    }

    // --- Свои занятия ---

    /** Еженедельное событие: на каждый выбранный день — своя запись (как в планировщике). */
    fun addWeeklyEvent(name: String, days: Set<DayOfWeek>, time: LocalTime, offsetMinutes: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || days.isEmpty()) return
        viewModelScope.launch {
            val dao = container.database.customEventDao()
            val startMinutes = time.hour * 60 + time.minute
            val offset = offsetMinutes.coerceIn(MIN_EVENT_OFFSET, MAX_EVENT_OFFSET)
            days.forEach { day ->
                dao.upsert(
                    CustomEventEntity(
                        name = trimmed,
                        dayOfWeekValue = day.value,
                        dateEpochDay = null,
                        startMinutes = startMinutes,
                        offsetMinutes = offset
                    )
                )
            }
        }
    }

    /** Разовое событие на конкретную дату. */
    fun addDatedEvent(name: String, date: LocalDate, time: LocalTime, offsetMinutes: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            container.database.customEventDao().upsert(
                CustomEventEntity(
                    name = trimmed,
                    dayOfWeekValue = null,
                    dateEpochDay = date.toEpochDay(),
                    startMinutes = time.hour * 60 + time.minute,
                    offsetMinutes = offsetMinutes.coerceIn(MIN_EVENT_OFFSET, MAX_EVENT_OFFSET)
                )
            )
        }
    }

    fun setCustomEventEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            container.database.customEventDao().setEnabled(id, enabled)
        }
    }

    fun deleteCustomEvent(id: Long) {
        viewModelScope.launch {
            container.database.customEventDao().delete(id)
        }
    }

    // --- Будильники через движок ---

    /**
     * Партия одноразовых будильников «на неделю вперёд».
     * [days] — необязательный пересчёт weekAheadDays: сначала атомарно записывается
     * в настройки (иначе «Поставить сейчас» могло бы прочитать устаревшее значение,
     * пока запись слайдера ещё летит в DataStore), затем ставится партия.
     */
    fun setWeekAhead(days: Int? = null, onResult: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (days != null) {
                container.settings.update {
                    it.copy(weekAheadDays = days.coerceIn(MIN_WEEK_AHEAD_DAYS, MAX_WEEK_AHEAD_DAYS))
                }
            }
            val count = withContext(Dispatchers.Default) {
                container.stockAlarms.setWeekAhead()
            }
            onResult(count)
        }
    }

    /** Тестовый будильник «через 2 минуты» — проверка, что часы принимают SET_ALARM. */
    fun setTestAlarm(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            onResult(container.stockAlarms.setTestAlarm())
        }
    }

    // --- Внутреннее ---

    private fun CustomEventEntity.toModel(): CustomEvent = CustomEvent(
        id = id,
        name = name,
        dayOfWeek = dayOfWeekValue?.let { DayOfWeek.of(it) },
        date = dateEpochDay?.let { LocalDate.ofEpochDay(it) },
        time = ScheduleRepository.minutesToTime(startMinutes),
        offsetMinutes = offsetMinutes,
        enabled = enabled
    )

    private companion object {
        const val MIN_OFFSET = 15
        const val MAX_OFFSET = 240
        const val MIN_EVENT_OFFSET = 15
        const val MAX_EVENT_OFFSET = 180
        const val MIN_WEEK_AHEAD_DAYS = 3
        const val MAX_WEEK_AHEAD_DAYS = 14
    }
}
