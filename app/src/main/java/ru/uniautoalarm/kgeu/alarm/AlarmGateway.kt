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
 * Сверка состояния ([reconcileUpcoming]) сравнивает маркер с ближайшим ПРЕДСТОЯЩИМ
 * пробуждением — не с планом «на завтра» (баг 0.7.0: живой будильник снимался).
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
        val s = settings.current()
        val next = findNextUpcoming()
        if (next == null) {
            if (notify) {
                Notifications.alarmSkipped(
                    context, LocalDate.now(),
                    LocaleHelper.string(context, R.string.skip_no_upcoming)
                )
            }
            return null
        }
        val wakeEpochMinute = next.wakeAt.toLocalDate().toEpochDay() * 1440L +
            next.wakeAt.hour * 60L + next.wakeAt.minute
        if (wakeEpochMinute == s.lastScheduledWakeEpochMinute) {
            // будильник на этот момент уже стоит — дубликат не плодим, только подтверждаем
            if (notify) Notifications.alarmSet(context, next)
            return next
        }
        return setForDate(next.date, notify)
    }

    /** Ближайшее ПРЕДСТОЯЩЕЕ пробуждение по кэшу расписания (сегодня…+13) или null. */
    private suspend fun findNextUpcoming(): AlarmPlan? {
        val now = LocalDateTime.now()
        for (i in 0..13) { // две недели — горизонт кэша расписания
            val date = now.toLocalDate().plusDays(i.toLong())
            val plan = planner.planFor(date) ?: continue
            if (!plan.wakeAt.isAfter(now)) continue
            return plan
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
     * Сверка будильника с ближайшим ПРЕДСТОЯЩИМ пробуждением (ночной тик 00:05 и фоновый
     * sync; режим «Стандарт»).
     *
     * Ключ 0.7.1: маркер сравнивается не с планом «на завтра», а с ближайшим предстоящим
     * моментом. Сравнение с завтрашним планом снимало ЖИВОЙ будильник: поставленный во
     * вторник на четверг, он в четверг 00:05 «не совпадал» с планом на пятницу и удалялся
     * за часы до звонка; движок приложения к тому же снимал слот и ставил только «на
     * завтра», теряя пару послезавтра, когда завтра пусто.
     *
     * APP: слот перезаписывается свежим планом ближайшего пробуждения (замены
     * самолечатся); совпавший с маркером момент не трогается; предстоящих пробуждений
     * нет — фантомный слот снимается.
     *
     * CLOCK: совпал — не трогаем; не совпал (замена/перенос/устарел) — старый снимаем
     * по метке, новый ставим; предстоящих нет — фантомный снимаем.
     * Ограничение: если часы игнорируют DISMISS/SET в фоне (ColorOS), сверка бессильна —
     * для полной надёжности существует движок «В приложении» (цель по умолчанию с 0.7.1).
     */
    suspend fun reconcileUpcoming(notifyOnChange: Boolean = true) {
        val s = settings.current()
        val next = findNextUpcoming()
        val markerWake = markerToLocalDateTime(s.lastScheduledWakeEpochMinute)

        if (target() == AlarmTarget.APP) {
            when {
                next == null -> {
                    if (markerWake != null) {
                        // предстоящих пробуждений в кэше нет — слот фантомный
                        inApp.cancelOneShot()
                        clearLastScheduled()
                        if (notifyOnChange) {
                            Notifications.alarmSkipped(
                                context, LocalDate.now(),
                                LocaleHelper.string(context, R.string.skip_no_upcoming)
                            )
                        }
                    }
                }
                markerWake == next.wakeAt -> Unit // слот уже стоит на верный момент
                else -> {
                    // замена/перенос/устаревший маркер — перезаписываем слот свежим планом
                    inApp.scheduleOneShot(next.wakeAt, next.label)
                    updateLastScheduled(next)
                    if (notifyOnChange) Notifications.alarmSet(context, next)
                }
            }
            return
        }

        // CLOCK
        when {
            next == null -> {
                if (markerWake != null) {
                    stock.dismissSingleLabel(s.lastScheduledLabel)
                    clearLastScheduled()
                }
            }
            markerWake == null -> stock.setForDate(next.date, notify = true)
            markerWake == next.wakeAt -> Unit // живой будильник на верный момент — не трогаем
            else -> {
                // время/день изменились (замена/перенос) или маркер устарел
                stock.dismissSingleLabel(s.lastScheduledLabel)
                stock.setForDate(next.date, notify = true)
            }
        }
    }

    /** Ночная сверка (тик 00:05). */
    suspend fun nightlyReconcile() = reconcileUpcoming(notifyOnChange = true)

    private suspend fun updateLastScheduled(plan: AlarmPlan) {
        settings.update {
            it.copy(
                lastScheduledWakeEpochMinute =
                    plan.wakeAt.toLocalDate().toEpochDay() * 1440L +
                        plan.wakeAt.hour * 60L + plan.wakeAt.minute,
                lastScheduledLabel = plan.label
            )
        }
    }

    private suspend fun clearLastScheduled() {
        settings.update {
            it.copy(lastScheduledWakeEpochMinute = -1L, lastScheduledLabel = "")
        }
    }

    /** Эпоха-минута маркера → момент пробуждения (некорректный/пустой маркер → null). */
    private fun markerToLocalDateTime(v: Long): LocalDateTime? =
        if (v <= 0) null
        else LocalDate.ofEpochDay(v / 1440L).atStartOfDay().plusMinutes(v % 1440L)
}
