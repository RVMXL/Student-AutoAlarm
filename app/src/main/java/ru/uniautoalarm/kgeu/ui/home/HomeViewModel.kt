package ru.uniautoalarm.kgeu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.alarm.DismissResult
import ru.uniautoalarm.kgeu.alarm.NightlyScheduler
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.SyncInfo
import ru.uniautoalarm.kgeu.data.model.Target
import ru.uniautoalarm.kgeu.di.AppContainer
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper
import ru.uniautoalarm.kgeu.ui.common.refreshErrorMessage

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

    /**
     * Тумблер авто-будильника (как в настройках — см. SettingsViewModel.toggleAuto):
     * выключение снимает будильники (оба движка) и сбрасывает маркеры, включение
     * ставит заново — по СВЕЖЕМУ расписанию, максимум на завтра (логика 0.8.0:
     * «одна ночь вперёд», дальше — ночная сверка каждый день).
     * [onResult] — текст для снекбара (итог снятия при выключении).
     *
     * 0.9.0 (AL-8): engine-работа — в appScope, НЕ в viewModelScope: уход с экрана
     * посреди переключения больше не отменяет снятие/постановку (раньше смерть VM
     * в окне оставляла «выключено в настройках, но звонит»). Порядок OFF: СНАЧАЛА
     * dismissAll, потом запись настройки — смерть процесса в окне не оставит
     * «выключено, но звонит» (ночной тик выйдет по !autoAlarmEnabled до снятия).
     * 0.9.0 (UI-11): [toggling] — UI блокирует переключение до конца engine-работы.
     */
    private val _toggling = MutableStateFlow(false)

    /** Тумблер в работе (снятие/постановка до 12 с) — тумблер заблокирован. */
    val toggling: StateFlow<Boolean> = _toggling.asStateFlow()

    fun toggleAuto(enabled: Boolean, onResult: (String?) -> Unit = {}) {
        if (!_toggling.compareAndSet(false, true)) return
        var dismissed: DismissResult? = null
        // engine-работа переживает экраны (AL-8) и сериализуется мьютексом AlarmGateway
        val engine = container.appScope.launch {
            runCatching {
                if (enabled) {
                    // чистый лист от обоих движков; в чужие часы не выпрыгиваем
                    container.alarm.dismissAll(openListOnUnsupported = false)
                    container.settings.update { it.copy(autoAlarmEnabled = true) }
                    // свежее расписание ПЕРЕД постановкой: замены появляются после
                    // последнего синка — по устаревшему кэшу тумблер ставил будильник
                    // на отменённый день (баг 0.6.0). Бюджет 12 с, при сбое — кэш
                    withTimeoutOrNull(12_000) { container.scheduleRepository.refreshAround() }
                    // будильник на ближайшее утро (сегодня/завтра) — сразу, а не после сверки 00:05
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

    /** Локализованный итог снятия будильников для снекбара тумблера. */
    private fun dismissMessage(res: DismissResult): String = when {
        res.inAppCancelled ->
            LocaleHelper.string(container.appContext, R.string.toggle_off_app_cancelled)
        res.sent > 0 -> LocaleHelper.string(container.appContext, R.string.toggle_off_dismissed)
        res.openedList -> LocaleHelper.string(container.appContext, R.string.toggle_off_opened)
        else -> LocaleHelper.string(container.appContext, R.string.toggle_off_manual)
    }

    /**
     * Перепроверка расписания: текущая неделя и неделя завтрашнего дня.
     * onResult — текст для снекбара: раньше обновление молчало и в успехе,
     * и в ошибке — было непонятно, сработала ли кнопка вообще.
     */
    fun refresh(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val target = container.settings.current().toTarget()
            if (target == null) {
                onResult(LocaleHelper.string(container.appContext, R.string.err_no_target))
                return@launch
            }
            val results = listOf(
                container.scheduleRepository.refreshWeek(target, today),
                container.scheduleRepository.refreshWeek(target, tomorrow)
            )
            val firstError = results.firstOrNull { it.isFailure }?.exceptionOrNull()
            onResult(
                if (firstError == null) {
                    LocaleHelper.string(container.appContext, R.string.msg_refreshed)
                } else {
                    refreshErrorMessage(container.appContext, firstError)
                }
            )
        }
    }

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
