package ru.uniautoalarm.kgeu.alarm

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.data.model.AlarmTarget
import ru.uniautoalarm.kgeu.data.model.WeekAheadResult
import ru.uniautoalarm.kgeu.data.repository.SettingsRepository
import ru.uniautoalarm.kgeu.domain.AlarmPlanner
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper

/**
 * Единая точка постановки/снятия будильников для UI и фоновых задач (0.7.0).
 * Маршрутизирует по Settings.alarmTarget:
 *  — CLOCK: стоковые часы через ACTION_SET_ALARM ([StockAlarmSetter]);
 *  — APP: собственный точный будильник ([InAppAlarmScheduler]) — тумблер, снятие
 *    и дедупликация работают без капризов оболочки.
 *
 * Снятие ([dismissAll]) всегда бьёт по ОБОИМ движкам: могли остаться будильники
 * от прежнего режима/цели, а SET_ALARM/DISMISS не позволяют «перечислить» свои.
 */
class AlarmGateway(
    private val context: Context,
    private val settings: SettingsRepository,
    private val stock: StockAlarmSetter,
    private val inApp: InAppAlarmScheduler,
    private val planner: AlarmPlanner,
) {

    private suspend fun target(): AlarmTarget = settings.current().alarmTarget

    /** Поставить будильник на [date] активным движком. */
    suspend fun setForDate(date: LocalDate, notify: Boolean = true): AlarmPlan? =
        if (target() == AlarmTarget.APP) setForDateInApp(date, notify)
        else stock.setForDate(date, notify)

    /**
     * Мгновенная постановка: ближайшее ПРЕДСТОЯЩЕЕ пробуждение — сегодня, если время
     * подъёма ещё не прошло, иначе завтра и далее в пределах кэша расписания.
     * Вызывается при включении тумблера, смене режима/цели и после онбординга.
     * Повторная постановка того же момента блокируется маркером
     * lastScheduledWakeEpochMinute (SET_ALARM создаёт НОВЫЙ будильник, не обновляет).
     */
    suspend fun setNextUpcoming(notify: Boolean = true): AlarmPlan? {
        val now = LocalDateTime.now()
        val s = settings.current()
        for (i in 0..13) { // две недели — горизонт кэша расписания
            val date = now.toLocalDate().plusDays(i.toLong())
            val plan = planner.planFor(date) ?: continue
            if (!plan.wakeAt.isAfter(now)) continue
            val wakeEpochMinute = plan.wakeAt.toLocalDate().toEpochDay() * 1440L +
                plan.wakeAt.hour * 60L + plan.wakeAt.minute
            if (wakeEpochMinute == s.lastScheduledWakeEpochMinute) {
                // будильник на этот момент уже стоит — дубликат не плодим, только подтверждаем
                if (notify) Notifications.alarmSet(context, plan)
                return plan
            }
            return setForDate(date, notify)
        }
        if (notify) {
            Notifications.alarmSkipped(
                context, now.toLocalDate(),
                LocaleHelper.string(context, R.string.skip_no_upcoming)
            )
        }
        return null
    }

    /** Постановка на дату движком приложения (слот перезаписывается — дубликата не будет). */
    private suspend fun setForDateInApp(date: LocalDate, notify: Boolean): AlarmPlan? {
        val plan = planner.planFor(date)
        if (plan == null) {
            // на дату ничего не осталось (замены отменили пары) — слот снимаем,
            // иначе продолжил бы звенеть будильник по устаревшему плану
            inApp.cancelOneShot()
            if (notify) {
                Notifications.alarmSkipped(
                    context, date, LocaleHelper.string(context, R.string.skip_no_lessons)
                )
            }
            return null
        }
        if (inApp.scheduleOneShot(plan.wakeAt, plan.label)) {
            if (notify) Notifications.alarmSet(context, plan)
            settings.update {
                it.copy(
                    lastScheduledWakeEpochMinute =
                        plan.wakeAt.toLocalDate().toEpochDay() * 1440L +
                            plan.wakeAt.hour * 60L + plan.wakeAt.minute,
                    lastScheduledLabel = plan.label
                )
            }
            return plan
        }
        if (notify) {
            Notifications.alarmSkipped(
                context, date, LocaleHelper.string(context, R.string.skip_clock_refused)
            )
        }
        return null
    }

    /**
     * Партия «на несколько дней вперёд».
     * CLOCK — повторяющиеся по дням недели (EXTRA_DAYS, как в 0.3.1+);
     * APP — отдельный точный будильник на каждую дату окна (у AlarmManager нет
     * недельных повторов; слоты по датам перезаписываются — идемпотентно, [force]
     * не нужен).
     */
    suspend fun setWeekAhead(notify: Boolean = true, force: Boolean = false): WeekAheadResult {
        if (target() != AlarmTarget.APP) {
            return stock.setWeekAhead(notify = notify, force = force)
        }
        val s = settings.current()
        val horizon = maxOf(s.weekAheadDays, 7)
        val now = LocalDateTime.now()
        var count = 0
        for (i in 0 until horizon) {
            val date = LocalDate.now().plusDays(i.toLong())
            val plan = planner.planFor(date) ?: continue
            if (!plan.wakeAt.isAfter(now)) continue
            if (inApp.scheduleWeekDate(date.toEpochDay(), plan.wakeAt, plan.label)) count++
        }
        val until = LocalDate.now().plusDays(s.weekAheadDays.toLong())
        settings.update { it.copy(weekAlarmsSetUntilEpochDay = until.toEpochDay()) }
        if (notify) Notifications.weekSet(context, count, LocalDate.now(), until)
        return WeekAheadResult(count, alreadyUpToDate = false)
    }

    /**
     * Снять все будильники приложения: собственный движок отменяется гарантированно
     * (PendingIntent.cancel), стоковые часы получают DISMISS по меткам; маркеры
     * постановки сбрасываются — следующий тумблер/тик ставит заново, а не «молчит».
     * [openListOnUnsupported] = false — не открывать список часов при отказе оболочки
     * (смена цели будильника: выскакивать в чужое приложение не зачем).
     */
    suspend fun dismissAll(openListOnUnsupported: Boolean = true): DismissResult {
        val s = settings.current()
        val appEngine = s.alarmTarget == AlarmTarget.APP
        inApp.cancelOneShot()
        inApp.cancelTest()
        if (s.weekAlarmsSetUntilEpochDay > 0) {
            // окно партии: конец известен, начало могло сместиться (меняли weekAheadDays)
            inApp.cancelRange(s.weekAlarmsSetUntilEpochDay - 20, s.weekAlarmsSetUntilEpochDay + 20)
        }
        // часы тоже чистим (могли остаться от прежнего режима/цели); при движке приложения
        // фолбэк «открыть список часов» лишний — выскакивать в чужое приложение не зачем
        val res = stock.dismissAppAlarms(openListOnUnsupported = openListOnUnsupported && !appEngine)
        return res.copy(inAppCancelled = appEngine)
    }

    /** Тестовый будильник «через 2 минуты» активным движком (проверка из настроек). */
    suspend fun setTestAlarm(): Boolean =
        if (target() == AlarmTarget.APP) inApp.scheduleTest()
        else stock.setTestAlarm()

    /**
     * Ночная сверка (тик 00:05, режим «Стандарт»).
     *
     * APP: слот всегда перезаписывается свежим планом (после ночного обновления
     * расписания) — фантомные будильники отменённого дня и сдвинутые заменной
     * времена самолечатся.
     *
     * CLOCK: сверяем маркер последней постановки с планом на завтра:
     *  — план исчез (пары отменили), а будильник на завтра стоит → снять старый по метке;
     *  — время подъёма изменилось → снять старый, поставить новый;
     *  — совпал → не дублируем.
     *  Ограничение: если часы игнорируют DISMISS/SET в фоне (ColorOS), сверка бессильна —
     *  для полной надёжности существует движок «В приложении».
     */
    suspend fun nightlySetForDate(date: LocalDate) {
        if (target() == AlarmTarget.APP) {
            inApp.cancelOneShot()
            setForDate(date, notify = true)
            return
        }
        val s = settings.current()
        val plan = planner.planFor(date)
        val markerWake = markerToLocalDateTime(s.lastScheduledWakeEpochMinute)
        when {
            plan == null && markerWake != null && markerWake.toLocalDate() == date -> {
                // завтра пар не стало (замены) — стоящий будильник фантомный
                stock.dismissSingleLabel(s.lastScheduledLabel)
                settings.update {
                    it.copy(lastScheduledWakeEpochMinute = -1L, lastScheduledLabel = "")
                }
            }
            plan != null && (markerWake == null || markerWake != plan.wakeAt) -> {
                // время подъёма изменилось (замена/перенос) или будильника ещё не было
                if (markerWake != null) stock.dismissSingleLabel(s.lastScheduledLabel)
                stock.setForDate(date, notify = true)
            }
            else -> Unit // план совпал с маркером (или пар нет и маркера нет) — ничего не делаем
        }
    }

    /** Эпоха-минута маркера → момент пробуждения (некорректный/пустой маркер → null). */
    private fun markerToLocalDateTime(v: Long): LocalDateTime? =
        if (v <= 0) null
        else LocalDate.ofEpochDay(v / 1440L).atStartOfDay().plusMinutes(v % 1440L)
}
