package ru.uniautoalarm.kgeu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.alarm.DismissResult
import ru.uniautoalarm.kgeu.alarm.NightlyScheduler
import ru.uniautoalarm.kgeu.alarm.ReminderScheduler
import ru.uniautoalarm.kgeu.data.db.CustomEventEntity
import ru.uniautoalarm.kgeu.data.db.ExceptionEntity
import ru.uniautoalarm.kgeu.data.model.AlarmTarget
import ru.uniautoalarm.kgeu.data.model.CalendarStyle
import ru.uniautoalarm.kgeu.data.model.CustomEvent
import ru.uniautoalarm.kgeu.data.model.ExceptionRule
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.ThemeMode
import ru.uniautoalarm.kgeu.data.repository.ScheduleRepository
import ru.uniautoalarm.kgeu.di.AppContainer
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper
import ru.uniautoalarm.kgeu.ui.common.OffsetBounds
import ru.uniautoalarm.kgeu.ui.theme.CUSTOM_ACCENT_INDEX

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
            list.map { e ->
                ExceptionRule(id = e.id, subject = e.subject, enabled = e.enabled)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Свои занятия (реактивно из Room). */
    val customEvents: StateFlow<List<CustomEvent>> = container.database.customEventDao().all()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Уникальные дисциплины из кэша расписания — подсказки для исключений. */
    val subjects: StateFlow<List<String>> = container.scheduleRepository.subjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Времена первых пар из кэша расписания — список для «подъём под каждую пару». */
    val pairStartTimes: StateFlow<List<LocalTime>> =
        container.scheduleRepository.firstLessonStartTimes()
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
                // 0..5 — готовые палитры, 6 (CUSTOM_ACCENT_INDEX) — «свой цвет»;
                // при 6 без выбранного цвета тема даёт безопасный фолбэк на дефолт
                it.copy(accentIndex = index.coerceIn(0, CUSTOM_ACCENT_INDEX))
            }
        }
    }

    /** Свой акцентный цвет из палитры выбора: сохраняет цвет и сразу включает его. */
    fun setCustomAccent(argb: Int) {
        viewModelScope.launch {
            container.settings.update {
                it.copy(customAccentColor = argb or 0xFF000000.toInt(), accentIndex = CUSTOM_ACCENT_INDEX)
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

    /**
     * Свой подъём для пары, начинающейся в [startMinutes] (минуты от 00:00).
     * [offset] = null — сброс на общий wakeOffsetMinutes.
     */
    fun setPairOffset(startMinutes: Int, offset: Int?) {
        viewModelScope.launch {
            container.settings.update {
                val map = it.firstLessonOffsets.toMutableMap()
                if (offset == null) map.remove(startMinutes)
                else map[startMinutes] = offset.coerceIn(MIN_OFFSET, MAX_OFFSET)
                it.copy(firstLessonOffsets = map)
            }
        }
    }

    /**
     * Тумблер авто-будильника.
     *
     * 0.8.0 (единая логика «одна ночь вперёд»): в ОБА направления сначала снятие обоих
     * движков (чистый лист, маркеры сбрасываются); при включении — свежее расписание
     * (замены появляются после последнего синка) и постановка ближайшего предстоящего
     * пробуждения — максимум на ЗАВТРА, за следующие дни отвечает ночная сверка 00:05/01:00–03:00.
     * Движок «В приложении» снимается гарантированно — PendingIntent.cancel;
     * часы — DISMISS по меткам с честным фолбэком.
     * [onResult] — необязательный текст для снекбара (итог снятия при выключении).
     *
     * 0.9.0 (AL-8): engine-работа — в appScope (переживает экраны); OFF — снятие до
     * записи настройки. 0.9.0 (UI-11): [toggling] блокирует повторное переключение.
     */
    private val _toggling = MutableStateFlow(false)

    /** Тумблер/смена движка в работе (до 12 с) — переключатели заблокированы. */
    val toggling: StateFlow<Boolean> = _toggling.asStateFlow()

    fun toggleAuto(enabled: Boolean, onResult: (String?) -> Unit = {}) {
        if (!_toggling.compareAndSet(false, true)) return
        var dismissed: DismissResult? = null
        val engine = container.appScope.launch {
            runCatching {
                if (enabled) {
                    container.alarm.dismissAll()
                    container.settings.update { it.copy(autoAlarmEnabled = true) }
                    withTimeoutOrNull(12_000) { container.scheduleRepository.refreshAround() }
                    container.alarm.setNextUpcoming()
                } else {
                    // AL-8: сначала снимаем, потом пишем настройку
                    dismissed = container.alarm.dismissAll()
                    container.settings.update { it.copy(autoAlarmEnabled = false) }
                }
                NightlyScheduler.scheduleNext(container.appContext)
            }
        }
        viewModelScope.launch {
            try {
                engine.join()
            } finally {
                _toggling.value = false
            }
            if (!enabled) {
                // итог снятия — в снекбар: у выключения нет других уведомлений
                dismissed?.let { onResult(dismissMessage(it)) }
            }
        }
    }

    /** Кнопка «Отключить будильники приложения»: тот же канал, что и у тумблера. */
    fun dismissAlarms(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.Default) { container.alarm.dismissAll() }
            onResult(
                when {
                    res.inAppCancelled -> LocaleHelper.string(
                        container.appContext, R.string.toggle_off_app_cancelled
                    )
                    res.sent > 0 -> LocaleHelper.string(
                        container.appContext, R.string.dismiss_alarms_sent, res.sent, res.total
                    )
                    else -> LocaleHelper.string(container.appContext, R.string.dismiss_unsupported_manual)
                }
            )
        }
    }

    /** Локализованный итог снятия будильников для снекбара тумблера.
     *  0.10.0: вариант «открыл список часов» удалён вместе с авто-переходами. */
    private fun dismissMessage(res: DismissResult): String = when {
        res.inAppCancelled ->
            LocaleHelper.string(container.appContext, R.string.toggle_off_app_cancelled)
        res.sent > 0 -> LocaleHelper.string(container.appContext, R.string.toggle_off_dismissed)
        else -> LocaleHelper.string(container.appContext, R.string.toggle_off_manual)
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

    /**
     * Куда ставится будильник: «В часах» (SET_ALARM, как раньше) или «В приложении»
     * (собственный точный будильник — тумблер и снятие работают без капризов оболочки).
     * Смена цели — чистый лист: снимаем оба движка, ставим заново активным.
     * 0.10.0: снятие больше никогда не открывает часы (баг пользователя).
     *
     * 0.9.0 (SET-7): dismissal/постановка сериализуются мьютексом AlarmGateway,
     * а работа перенесена в appScope — быстрые переключения больше не могут
     * снять только что поставленный будильник; повторный клик ждёт ([toggling]).
     */
    fun setAlarmTarget(target: AlarmTarget) {
        if (!_toggling.compareAndSet(false, true)) return
        val engine = container.appScope.launch {
            runCatching {
                container.alarm.dismissAll()
                container.settings.update { it.copy(alarmTarget = target) }
                if (container.settings.current().autoAlarmEnabled) {
                    container.alarm.setNextUpcoming(notify = false)
                }
                NightlyScheduler.scheduleNext(container.appContext)
            }
        }
        viewModelScope.launch {
            try {
                engine.join()
            } finally {
                _toggling.value = false
            }
        }
    }

    // --- Эффект стекла ---

    fun setGlassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(glassEnabled = enabled) }
        }
    }

    /** Матóвость стекла (0..1): сила размытия и плотность морозной заливки. */
    fun setGlassBlur(value: Float) {
        viewModelScope.launch {
            container.settings.update { it.copy(glassBlur = value.coerceIn(0f, 1f)) }
        }
    }

    /** Искажение стекла (0..1): «линзовое» искривление пространства за стеклом. */
    fun setGlassDistortion(value: Float) {
        viewModelScope.launch {
            container.settings.update { it.copy(glassDistortion = value.coerceIn(0f, 1f)) }
        }
    }

    /** Смена цели: сбрасываем флаг мастера — он откроется заново, offset подтянется в init. */
    fun changeTarget() {
        viewModelScope.launch {
            container.settings.update { it.copy(setupComplete = false) }
        }
    }

    // --- Исключения ---

    /**
     * Добавить исключение: без пустых и дублей (сравнение по ТОЧНОМУ полному названию
     * — вместе с приставкой типа: «л. материаловедение» и «пр. материаловедение» —
     * разные правила). UI-9 (0.10.0): проверка и запись — под мьютексом, двойной
     * ввод не создаёт дубль.
     */
    private val exceptionGate = Mutex()

    fun addException(subject: String) {
        val trimmed = subject.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            exceptionGate.withLock {
                val dao = container.database.exceptionDao()
                val normalized = ScheduleRepository.normalizeForMatch(trimmed)
                val exists = dao.allOnce().any {
                    ScheduleRepository.normalizeForMatch(it.subject) == normalized
                }
                if (!exists) dao.upsert(ExceptionEntity(subject = trimmed))
            }
        }
    }

    /** Включить/выключить правило (DAO не имеет setEnabled — переписываем строку целиком). */
    fun setExceptionEnabled(rule: ExceptionRule, enabled: Boolean) {
        viewModelScope.launch {
            container.database.exceptionDao().upsert(
                ExceptionEntity(id = rule.id, subject = rule.subject, enabled = enabled)
            )
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

    /** Тестовый будильник «через 2 минуты» — проверка активного движка. */
    fun setTestAlarm(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            onResult(container.alarm.setTestAlarm())
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
        // SET-13 (0.11.0): границы — из единого источника ui.common.OffsetBounds
        const val MIN_OFFSET = OffsetBounds.WAKE_MIN
        const val MAX_OFFSET = OffsetBounds.WAKE_MAX
        const val MIN_EVENT_OFFSET = OffsetBounds.EVENT_MIN
        const val MAX_EVENT_OFFSET = OffsetBounds.EVENT_MAX
    }
}
