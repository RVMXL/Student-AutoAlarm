package ru.uniautoalarm.kgeu.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.data.db.ExceptionEntity
import ru.uniautoalarm.kgeu.data.model.ExceptionRule
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.SyncInfo
import ru.uniautoalarm.kgeu.data.model.Target
import ru.uniautoalarm.kgeu.data.repository.ScheduleRepository
import ru.uniautoalarm.kgeu.di.AppContainer

/**
 * ViewModel экрана расписания: выбранная неделя (понедельник), выбранный день,
 * пары всех 7 дней недели одним map'ом и список исключений.
 *
 * Пары недели — flatMapLatest на combine из семи lessonsFlow: при смене недели
 * старые потоки отменяются, новые подхватываются автоматически.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(private val container: AppContainer) : ViewModel() {

    /** Снимок настроек (стиль «бумажного календаря» и пр.). */
    val settings: StateFlow<Settings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Состояние синхронизации (busy — показываем полосу загрузки). */
    val sync: StateFlow<SyncInfo> = container.scheduleRepository.syncState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncInfo())

    /** Понедельник выбранной недели; при создании — текущая неделя. */
    private val _weekMonday = MutableStateFlow(
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    )
    val weekMonday: StateFlow<LocalDate> = _weekMonday.asStateFlow()

    /** Выбранный день недели. */
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** Пары понедельник–воскресенье выбранной недели, отсортированные по времени начала. */
    val weekLessons: StateFlow<Map<LocalDate, List<Lesson>>> = _weekMonday
        .flatMapLatest { monday ->
            combine(
                (0..6).map { offset ->
                    container.scheduleRepository.lessonsFlow(monday.plusDays(offset.toLong()))
                }
            ) { lists ->
                (0..6).associate { offset ->
                    monday.plusDays(offset.toLong()) to lists[offset].sortedBy { it.start }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Исключения (реактивно из Room). */
    val exceptions: StateFlow<List<ExceptionRule>> = container.database.exceptionDao().all()
        .map { list -> list.map { e -> ExceptionRule(id = e.id, subject = e.subject, enabled = e.enabled) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Выбрать день недели. */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /** Предыдущая неделя: сдвигаем и понедельник, и выбранный день (тот же день недели). */
    fun prevWeek() {
        val newMonday = _weekMonday.value.minusDays(7)
        _weekMonday.value = newMonday
        _selectedDate.value = newMonday.plusDays(weekdayOffset(_selectedDate.value))
    }

    /**
     * Следующая неделя: сдвиг плюс тихая подгрузка новой недели из API
     * (если цели нет — просто показываем кэш).
     */
    fun nextWeek() {
        val newMonday = _weekMonday.value.plusDays(7)
        _weekMonday.value = newMonday
        _selectedDate.value = newMonday.plusDays(weekdayOffset(_selectedDate.value))
        viewModelScope.launch {
            val target = container.settings.current().toTarget() ?: return@launch
            container.scheduleRepository.refreshWeek(target, newMonday)
        }
    }

    /** Обновить текущую (выбранную) неделю из API. */
    fun refreshCurrent() {
        viewModelScope.launch {
            val target = container.settings.current().toTarget() ?: return@launch
            container.scheduleRepository.refreshWeek(target, _weekMonday.value)
        }
    }

    /**
     * Переключить исключение для дисциплины: если правило уже есть — удалить, иначе добавить.
     * Сопоставление — по нормализованному названию (ScheduleRepository.normalizeSubject).
     */
    fun toggleException(subject: String) {
        viewModelScope.launch {
            val dao = container.database.exceptionDao()
            val normalized = ScheduleRepository.normalizeSubject(subject)
            val existing = dao.allOnce().firstOrNull {
                ScheduleRepository.normalizeSubject(it.subject) == normalized
            }
            if (existing != null) {
                dao.delete(existing.id)
            } else {
                dao.upsert(ExceptionEntity(subject = subject.trim()))
            }
        }
    }

    /** Дисциплина попадает под активное исключение? */
    fun isExceptional(subject: String, exceptions: List<ExceptionRule>): Boolean =
        exceptions.any {
            it.enabled &&
                ScheduleRepository.normalizeSubject(it.subject) ==
                ScheduleRepository.normalizeSubject(subject)
        }

    /** Смещение дня недели относительно понедельника (0..6). */
    private fun weekdayOffset(date: LocalDate): Long = (date.dayOfWeek.value - 1).toLong()

    /** Цель расписания из снимка настроек; не выбрана → null. */
    private fun Settings.toTarget(): Target? {
        val type = targetType ?: return null
        val id = targetId ?: return null
        return Target(type, id, targetName)
    }
}
