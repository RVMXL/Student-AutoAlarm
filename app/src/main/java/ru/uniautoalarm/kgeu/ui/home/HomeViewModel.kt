package ru.uniautoalarm.kgeu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.SyncInfo
import ru.uniautoalarm.kgeu.data.model.Target
import ru.uniautoalarm.kgeu.di.AppContainer

/**
 * ViewModel главного экрана: снимок настроек, состояние синхронизации, пары на сегодня/завтра
 * и план пробуждения на завтра (планировщик вызывается прямо из combine — данные уже в кэше).
 *
 * Дата «завтра» фиксируется на момент создания VM — для главного экрана этого достаточно
 * (при возврате на вкладку VM живёт, при пересоздании активити пересчитается).
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /** Настройки: null до первого значения DataStore (краткий миг после запуска). */
    val settings: StateFlow<Settings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Состояние синхронизации расписания (busy / lastError). */
    val sync: StateFlow<SyncInfo> = container.scheduleRepository.syncState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncInfo())

    private val today: LocalDate = LocalDate.now()
    private val tomorrow: LocalDate = today.plusDays(1)

    /** Пары на завтра (реактивно, из кэша Room). */
    val tomorrowLessons: StateFlow<List<Lesson>> =
        container.scheduleRepository.lessonsFlow(tomorrow)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Пары на сегодня — для блока «Ближайшие занятия». */
    val todayLessons: StateFlow<List<Lesson>> =
        container.scheduleRepository.lessonsFlow(today)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** План пробуждения на завтра; null — мастер не пройден / кандидатов нет. */
    val plan: StateFlow<AlarmPlan?> = combine(
        settings.filterNotNull(),
        tomorrowLessons
    ) { s, lessons ->
        if (!s.setupComplete) null
        else container.planner.planFor(tomorrow, lessons)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Тумблер авто-будильника. */
    fun toggleAuto(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.update { it.copy(autoAlarmEnabled = enabled) }
        }
    }

    /** Перепроверка расписания: текущая неделя и неделя завтрашнего дня (Result игнорируем). */
    fun refresh() {
        viewModelScope.launch {
            val target = container.settings.current().toTarget() ?: return@launch
            container.scheduleRepository.refreshWeek(target, today)
            container.scheduleRepository.refreshWeek(target, tomorrow)
        }
    }

    /**
     * Режим «на неделю вперёд»: партия одноразовых будильников через StockAlarmSetter.
     * setWeekAhead сам делает withContext(Default) внутри setForDate, но всю партию
     * дополнительно уводим с главного потока; onResult приходит уже на Main.
     */
    fun setWeekAhead(onResult: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val count = withContext(Dispatchers.Default) {
                container.stockAlarms.setWeekAhead()
            }
            onResult(count)
        }
    }

    /** Партия одноразовых будильников ещё активна (покрывает сегодня). */
    fun weekAlarmsActive(s: Settings): Boolean =
        s.weekAlarmsSetUntilEpochDay >= LocalDate.now().toEpochDay()

    /** Полная синхронизация не была ни разу или была дольше трёх суток назад. */
    fun dataStale(s: Settings): Boolean =
        s.lastFullSyncEpochMs == 0L ||
            System.currentTimeMillis() - s.lastFullSyncEpochMs > STALE_AFTER_MS

    /** Цель расписания из снимка настроек; не выбрана → null. */
    private fun Settings.toTarget(): Target? {
        val type = targetType ?: return null
        val id = targetId ?: return null
        return Target(type, id, targetName)
    }

    private companion object {
        /** Трое суток в миллисекундах — порог «расписание устарело». */
        const val STALE_AFTER_MS = 3L * 24 * 60 * 60 * 1000
    }
}
