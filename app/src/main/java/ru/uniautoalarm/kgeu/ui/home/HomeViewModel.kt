package ru.uniautoalarm.kgeu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.alarm.DismissResult
import ru.uniautoalarm.kgeu.alarm.NightlyScheduler
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.data.model.AlarmTarget
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.data.model.SyncInfo
import ru.uniautoalarm.kgeu.data.model.Target
import ru.uniautoalarm.kgeu.di.AppContainer
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper
import ru.uniautoalarm.kgeu.ui.common.refreshErrorMessage
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Поставленный будильник для карточки «Мои будильники» (Bug E, 0.10.0):
 * момент, метка, движок и живой ли слот (проба FLAG_NO_CREATE — только APP).
 */
data class ScheduledAlarm(
    val wakeAt: LocalDateTime,
    val label: String,
    val target: AlarmTarget,
    val slotAlive: Boolean,
    val autoEnabled: Boolean,
)

/** Тестовый будильник для карточки (Bug G, 0.13.0): момент и движок. */
data class TestAlarmUi(
    val triggerAt: LocalDateTime,
    val target: AlarmTarget,
)

/**
 * ViewModel главного экрана: снимок настроек, состояние синхронизации, пары на сегодня/завтра
 * и план пробуждения на завтра (планировщик вызывается прямо из combine — данные уже в кэше).
 *
 * DATA-15 (0.11.0): «сегодня/завтра» — ПРОИЗВОДНЫЕ от тика возврата экрана (см. [resumeTick]),
 * а не константы на время жизни VM: приложение, висевшее открытым через полночь,
 * показывало блок «Сегодня» со вчерашними парами до пересоздания активити.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /** Счётчик возвратов экрана на передний план — перечитывать пробы/разрешения (UI-6)
     *  и даты «сегодня/завтра» (DATA-15). */
    private val resumeTick = MutableStateFlow(0)

    /** «Сегодня» — производная от тика возврата экрана (DATA-15). */
    private val todayFlow: StateFlow<LocalDate> = resumeTick
        .map { LocalDate.now() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDate.now())

    /** «Завтра» — производная от «сегодня». */
    private val tomorrowFlow: StateFlow<LocalDate> = todayFlow
        .map { it.plusDays(1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDate.now().plusDays(1))

    /** Настройки: null до первого значения DataStore (краткий миг после запуска). */
    val settings: StateFlow<Settings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Состояние синхронизации расписания (busy / lastError). */
    val sync: StateFlow<SyncInfo> = container.scheduleRepository.syncState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncInfo())

    /** Пары на завтра (реактивно, из кэша Room; дата — производная, DATA-15). */
    val tomorrowLessons: StateFlow<List<Lesson>> = tomorrowFlow
        .flatMapLatest { container.scheduleRepository.lessonsFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Пары на сегодня — для блока «Ближайшие занятия» (дата — производная, DATA-15). */
    val todayLessons: StateFlow<List<Lesson>> = todayFlow
        .flatMapLatest { container.scheduleRepository.lessonsFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** План пробуждения на завтра; null — мастер не пройден / кандидатов нет. */
    val plan: StateFlow<AlarmPlan?> = combine(
        settings.filterNotNull(),
        tomorrowLessons,
        tomorrowFlow
    ) { s, lessons, tomorrow ->
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
                    // чистый лист от обоих движков (0.10.0: часы сами не открываются никогда)
                    container.alarm.dismissAll()
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

    /** Локализованный итог снятия будильников для снекбара тумблера.
     *  0.10.0: вариант «открыл список часов» удалён вместе с авто-переходами. */
    private fun dismissMessage(res: DismissResult): String = when {
        res.inAppCancelled ->
            LocaleHelper.string(container.appContext, R.string.toggle_off_app_cancelled)
        res.sent > 0 -> LocaleHelper.string(container.appContext, R.string.toggle_off_dismissed)
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
            // DATA-15: даты берём НА МОМЕНТ вызова, а не поля времени жизни VM
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
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

    /**
     * Bug E (0.10.0): поставленный будильник — для карточки «Мои будильники».
     * Источник: маркер последней постановки + проба живого слота (APP-движок).
     * Пересчитывается на каждой эмиссии настроек и на [resumeTick] (возврат
     * на экран — UI-6: проба слота вне композиции, состояние всегда свежее).
     */
    val nextAlarm: StateFlow<ScheduledAlarm?> = combine(
        settings.filterNotNull(),
        resumeTick
    ) { s, _ -> scheduledAlarm(s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Тестовый будильник для карточки (Bug G, 0.13.0): раньше слот теста
     * не был виден нигде, кроме шторки после срабатывания. Виден, пока:
     * маркер стоит И момент в будущем; в движке «В приложении» дополнительно
     * проба живого слота (ребут/force-stop — строка честно исчезает);
     * «В часах» — маркеру доверяем (проба невозможна, как и для авто-будильника).
     */
    val testAlarm: StateFlow<TestAlarmUi?> = combine(
        settings.filterNotNull(),
        resumeTick
    ) { s, _ -> testAlarmUi(s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun testAlarmUi(s: Settings): TestAlarmUi? {
        if (s.testAlarmEpochMs <= 0) return null
        val at = Instant.ofEpochMilli(s.testAlarmEpochMs)
            .atZone(ZoneId.systemDefault()).toLocalDateTime()
        if (!at.isAfter(LocalDateTime.now())) return null // прозвенел/устарел — не показываем
        if (s.alarmTarget == AlarmTarget.APP && !container.inAppAlarms.hasTest()) return null
        return TestAlarmUi(triggerAt = at, target = s.alarmTarget)
    }

    /** Снять тестовый будильник из карточки (движок «В приложении») — до звона. */
    fun cancelTestAlarm() {
        viewModelScope.launch { container.alarm.cancelTestAlarm() }
    }

    /** Снять все будильники из карточки «Мои будильники»; итог — в снекбар. */
    fun dismissAlarmsFromCard(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = container.alarm.dismissAll()
            onResult(dismissMessage(res))
        }
    }

    /** Явно открыть список будильников часов (движок «В часах»). */
    fun openClockList(onResult: (String?) -> Unit) {
        val ok = container.stockAlarms.openAlarmList()
        if (!ok) {
            onResult(LocaleHelper.string(container.appContext, R.string.alarms_list_open_failed))
        }
    }

    private suspend fun scheduledAlarm(s: Settings): ScheduledAlarm? {
        if (!s.setupComplete) return null
        if (s.lastScheduledWakeEpochMinute <= 0) return null
        val wake = LocalDate.ofEpochDay(s.lastScheduledWakeEpochMinute / 1440L)
            .atStartOfDay().plusMinutes(s.lastScheduledWakeEpochMinute % 1440L)
        val slotAlive = s.alarmTarget != AlarmTarget.APP || container.inAppAlarms.hasOneShot()
        return ScheduledAlarm(
            wakeAt = wake,
            label = s.lastScheduledLabel,
            target = s.alarmTarget,
            slotAlive = slotAlive,
            autoEnabled = s.autoAlarmEnabled
        )
    }

    /** Экран вернулся на передний план — пересчитать пробы слотов/разрешений. */
    fun onScreenResume() {
        resumeTick.value = resumeTick.value + 1
    }

    private companion object {
        /** Трое суток в миллисекундах — порог «расписание устарело». */
        const val STALE_AFTER_MS = 3L * 24 * 60 * 60 * 1000
    }
}
