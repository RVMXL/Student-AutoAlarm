package ru.uniautoalarm.kgeu.alarm

import android.content.Context
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.data.model.AlarmTarget
import ru.uniautoalarm.kgeu.data.repository.SettingsRepository
import ru.uniautoalarm.kgeu.di.AppClock
import ru.uniautoalarm.kgeu.domain.AlarmPlanner
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper

/**
 * Единая точка постановки/снятия будильников для UI и фоновых задач (0.7.0; 0.8.0 —
 * единая логика «одна ночь вперёд»). Маршрутизирует по Settings.alarmTarget:
 *  — CLOCK: стоковые часы через ACTION_SET_ALARM ([StockAlarmSetter]);
 *  — APP: собственный точный будильник ([InAppAlarmScheduler]) — тумблер, снятие
 *    и дедупликация работают без капризов оболочки.
 *
 * ЛОГИКА 0.8.0 (предложена пользователем):
 *  1) включение тумблера / смена цели / онбординг — будильник на БЛИЖАЙШЕЕ предстоящее
 *     пробуждение в пределах СЕГОДНЯ и ЗАВТРА, больше — ничего;
 *  2) ночная сверка 00:05 (+страховка 06:00) каждый день: есть пара сегодня — ставит
 *     на ближайшее время, нет — снимает фантом; будильник «на день недели», который
 *     потом пришлось бы выключать руками, не ставится НИКОГДА (программа
 *     «на несколько дней вперёд» удалена в 0.8.0, наследие снято миграцией).
 *
 * Снятие ([dismissAll]) всегда бьёт по ОБОИМ движкам: могли остаться будильники
 * от прежнего режима/цели, а SET_ALARM/DISMISS не позволяют «перечислить» свои.
 * Сверка ([reconcileUpcoming]) сравнивает маркер с ближайшим ПРЕДСТОЯЩИМ пробуждением
 * — не с планом «на завтра» (баг 0.7.0: живой будильник снимался).
 *
 * 0.9.0: все входы сериализованы мьютексом [gate] (AL-7/SET-7) — интерливинг
 * тумблер × ночной тик × смена цели больше не может дать двойной SET_ALARM
 * или снять только что поставленный будильник.
 *
 * 0.10.0: снятие ([dismissAll]) больше НИКОГДА не открывает стоковые часы
 * (баг пользователя: авто-фолбэк SHOW_ALARMS выпрыгивал в часы при отказе
 * оболочки принимать DISMISS) — в часы попадаем только по явной кнопке
 * [StockAlarmSetter.openAlarmList]. AL-9: если часы НЕ приняли DISMISS при
 * переносе будильника, новый SET не отправляется (иначе прозвенели бы оба) —
 * маркер чистится, ставит заново ночная сверка, пользователь получает
 * уведомление «снимите старый будильник вручную». AL-16: найденный план
 * пробуждения передаётся в постановку без повторного чтения planFor.
 * AL-21: время — через инжектируемый [AppClock]. Метка будильника APP-движка
 * локализуется (SET-5): в часы метка пишется как раньше — «Авто: …» (стабильный
 * канал DISMISS), на звонке приложения — на языке интерфейса.
 */
class AlarmGateway(
    private val context: Context,
    private val settings: SettingsRepository,
    private val stock: StockAlarmSetter,
    private val inApp: InAppAlarmScheduler,
    private val planner: AlarmPlanner,
    private val clock: AppClock = AppClock.SYSTEM,
) {

    /** Сериализация постановки/снятия/свёрки: взаимное исключение всех вызовов. */
    private val gate = Mutex()

    private suspend fun target(): AlarmTarget = settings.current().alarmTarget

    /** Поставить будильник на [date] активным движком. */
    suspend fun setForDate(date: LocalDate, notify: Boolean = true): AlarmPlan? =
        gate.withLock { setForDateLocked(date, notify) }

    /**
     * Мгновенная постановка: ближайшее ПРЕДСТОЯЩЕЕ пробуждение — сегодня, если время
     * подъёма ещё не прошло, иначе ЗАВТРА (дальше — не смотрим: за следующие дни
     * отвечает ночная сверка 00:05, см. логику 0.8.0). Вызывается при включении
     * тумблера, смене цели и после онбординга. Повторная постановка того же момента
     * блокируется маркером lastScheduledWakeEpochMinute (SET_ALARM создаёт НОВЫЙ
     * будильник, не обновляет) — НО только если слот реально стоит (AL-1).
     */
    suspend fun setNextUpcoming(notify: Boolean = true): AlarmPlan? =
        gate.withLock { setNextUpcomingLocked(notify) }

    private suspend fun setNextUpcomingLocked(notify: Boolean): AlarmPlan? {
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
        val markerMatches = wakeEpochMinute == s.lastScheduledWakeEpochMinute
        // AL-1: маркер переживает перезагрузку и force-stop, слот AlarmManager — нет.
        // Ранний выход «уже стоит» разрешаем только после пробы фактического слота
        // (FLAG_NO_CREATE). Для часов проба невозможна (их будильники живут в БД
        // часов и ребут переживают) — маркеру доверяем, как и раньше.
        val slotAlive = s.alarmTarget != AlarmTarget.APP || inApp.hasOneShot()
        if (markerMatches && slotAlive) {
            // будильник на этот момент уже стоит — дубликат не плодим, только подтверждаем
            if (notify) Notifications.alarmSet(context, next)
            return next
        }
        if (markerMatches && !slotAlive) {
            Log.w(TAG, "Маркер=$wakeEpochMinute есть, слот APP мёртв (ребут/force-stop) — перепоставляю")
        }
        // AL-16: план уже найден — передаём его в постановку без повторного planFor
        return if (s.alarmTarget == AlarmTarget.APP) {
            setForDateInAppLocked(next, notify)
        } else {
            stock.setForDate(next.date, notify)
        }
    }

    /**
     * Восстановление после перезагрузки (BootReceiver, AL-1): слоты AlarmManager
     * перезагрузку НЕ переживают, а маркер дедупликации в DataStore — переживает.
     * Без сброса маркера setNextUpcoming «подтвердил» бы мёртвый слот, и будильник
     * молча не прозвенел бы до следующей сверки (которая тоже верила бы маркеру).
     * Сбрасываем маркер и перепоставляем ближайшее предстоящее безусловно.
     */
    suspend fun rearmFromBoot() {
        gate.withLock {
            clearLastScheduledLocked()
            setNextUpcomingLocked(notify = false)
        }
    }

    /**
     * Ближайшее ПРЕДСТОЯЩЕЕ пробуждение по кэшу расписания или null.
     * Горизонт 0.8.0 — СЕГОДНЯ и ЗАВТРА: поставленный заранее будильник на дату
     * позже завтра превращался бы в «на день недели, который придётся выключать».
     */
    private suspend fun findNextUpcoming(): AlarmPlan? {
        val now = clock.now()
        for (i in 0..1) {
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
        return setForDateInAppLocked(plan, notify)
    }

    /**
     * Постановка по готовому плану (AL-16): слот перезаписывается свежим планом.
     * Метка локализуется (SET-5): на звонке/в уведомлениях приложения она на языке
     * интерфейса; в DataStore маркер хранит ПЛАНОВУЮ метку — для CLOCK-постановки
     * из того же плана (часам нужен стабильный «Авто: …» для DISMISS).
     */
    private suspend fun setForDateInAppLocked(plan: AlarmPlan, notify: Boolean): AlarmPlan? {
        if (inApp.scheduleOneShot(plan.wakeAt, appDisplayLabel(plan.label))) {
            if (notify) Notifications.alarmSet(context, plan)
            updateLastScheduledLocked(plan)
            return plan
        }
        if (notify) {
            Notifications.alarmSkipped(
                context, plan.date, LocaleHelper.string(context, R.string.skip_clock_refused)
            )
        }
        return null
    }

    private suspend fun setForDateLocked(date: LocalDate, notify: Boolean): AlarmPlan? =
        if (settings.current().alarmTarget == AlarmTarget.APP) setForDateInApp(date, notify)
        else stock.setForDate(date, notify)

    /**
     * Снять все будильники приложения: собственный движок отменяется гарантированно
     * (PendingIntent.cancel), стоковые часы получают DISMISS по меткам; маркеры
     * постановки сбрасываются — следующий тумблер/тик ставит заново, а не «молчит».
     *
     * 0.10.0: параметр openListOnUnsupported УДАЛЁН вместе с авто-фолбэком SHOW_ALARMS
     * — приложение само не открывает часы ни в каком движке (баг пользователя).
     */
    suspend fun dismissAll(): DismissResult =
        gate.withLock { dismissAllLocked() }

    private suspend fun dismissAllLocked(): DismissResult {
        val s = settings.current()
        val appEngine = s.alarmTarget == AlarmTarget.APP
        inApp.cancelOneShot()
        inApp.cancelTest()
        // часы тоже чистим (могли остаться от прежнего режима/цели); в сами часы
        // не переходим — объясняем итог снекбаром, список часов открывает пользователь
        val res = stock.dismissAppAlarms()
        return res.copy(inAppCancelled = appEngine)
    }

    /** Тестовый будильник «через 2 минуты» активным движком (проверка из настроек). */
    suspend fun setTestAlarm(): Boolean =
        gate.withLock {
            if (target() == AlarmTarget.APP) inApp.scheduleTest()
            else stock.setTestAlarm()
        }

    /**
     * Сверка будильника с ближайшим ПРЕДСТОЯЩИМ пробуждением (ночная сверка 00:05,
     * страховка 06:00 и фоновый sync).
     *
     * Ключ 0.7.1: маркер сравнивается не с планом «на завтра», а с ближайшим предстоящим
     * моментом. Сравнение с завтрашним планом снимало ЖИВОЙ будильник: поставленный во
     * вторник на четверг, он в четверг 00:05 «не совпадал» с планом на пятницу и удалялся
     * за часы до звонка. Горизонт 0.8.0 — сегодня+завтра (см. findNextUpcoming).
     *
     * APP: слот перезаписывается свежим планом ближайшего пробуждения (замены
     * самолечатся); совпавший с маркером момент не трогается; предстоящих пробуждений
     * нет — фантомный слот снимается.
     *
     * CLOCK: совпал — не трогаем; не совпал (замена/перенос/устарел) — старый снимаем
     * по метке, новый ставим; предстоящих нет — фантомный снимаем. Если часы не приняли
     * постановку (в т.ч. «завтра» ночью — разовый зазвенел бы сегодня), маркер чистим:
     * ночная сверка поставит будильник в 00:05.
     * Ограничение: если часы игнорируют DISMISS/SET в фоне (ColorOS), сверка бессильна —
     * для полной надёжности существует движок «В приложении» (цель по умолчанию с 0.7.1).
     *
     * @param allowClockDelivery AL-2: из фонового SyncWorker SET_ALARM часам не
     *   отправляем (startActivity из фона может быть молча заблокирован BAL-политикой
     *   Android 10+, а «исключение не брошено» ≠ «будильник поставлен» — запись маркера
     *   по ложной доставке превращала ночную сверку в no-op). Фоновая сверка правит
     *   только APP-слот; CLOCK-постановку делает ночной тик (temporary allowlist
     *   точного будильника) или тумблер на переднем плане.
     */
    suspend fun reconcileUpcoming(
        notifyOnChange: Boolean = true,
        allowClockDelivery: Boolean = true,
    ) = gate.withLock { reconcileUpcomingLocked(notifyOnChange, allowClockDelivery) }

    private suspend fun reconcileUpcomingLocked(notifyOnChange: Boolean, allowClockDelivery: Boolean) {
        val s = settings.current()
        val next = findNextUpcoming()
        val markerWake = markerToLocalDateTime(s.lastScheduledWakeEpochMinute)

        if (s.alarmTarget == AlarmTarget.APP) {
            when {
                next == null -> {
                    if (markerWake != null) {
                        // предстоящих пробуждений в кэше нет — слот фантомный
                        inApp.cancelOneShot()
                        clearLastScheduledLocked()
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
                    inApp.scheduleOneShot(next.wakeAt, appDisplayLabel(next.label))
                    updateLastScheduledLocked(next)
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
                    clearLastScheduledLocked()
                }
            }
            markerWake == null -> {
                if (!allowClockDelivery) {
                    Log.i(TAG, "Фоновая сверка: SET_ALARM не отправляю (BAL-риск, AL-2) — поставит ночная сверка")
                    return
                }
                // AL-17 (0.11.0): ночная сверка — фоновая гигиена, «отложено до ночи»
                // каждую ночь в шторке — спам; итог постановки виден в карточке «Мои будильники»
                val plan = stock.setForDate(next.date, notify = true, notifyDeferred = false)
                if (plan == null) clearLastScheduledLocked() // часы отказались — не храним ложный маркер
            }
            markerWake == next.wakeAt -> Unit // живой будильник на верный момент — не трогаем
            else -> {
                if (!allowClockDelivery) {
                    Log.i(TAG, "Фоновая сверка: перенос CLOCK-будильника отложен до ночной сверки (AL-2)")
                    return
                }
                // время/день изменились (замена/перенос) или маркер устарел.
                // AL-9: если часы НЕ приняли DISMISS (ColorOS молча игнорирует),
                // новый SET НЕ отправляем — иначе прозвенели бы ОБА. Маркер чистим:
                // ночная сверка поставит заново (когда старый уже не актуален или
                // пользователь снял его сам), а пользователя честно предупреждаем.
                val dismissed = stock.dismissSingleLabel(s.lastScheduledLabel)
                if (!dismissed) {
                    Log.w(TAG, "DISMISS не принят оболочкой — новый SET отменён (AL-9)")
                    clearLastScheduledLocked()
                    Notifications.alarmSkipped(
                        context, LocalDate.now(),
                        LocaleHelper.string(context, R.string.notif_dismiss_not_accepted)
                    )
                    return
                }
                // AL-17: как выше — ночной тик молчит об откладке (уведомление — только
                // о реальных сбоях вроде «DISMISS не принят» выше)
                val plan = stock.setForDate(next.date, notify = true, notifyDeferred = false)
                if (plan == null) clearLastScheduledLocked()
            }
        }
    }

    /** Ночная сверка (тик 00:05 и страховка 06:00). */
    suspend fun nightlyReconcile() = reconcileUpcoming(notifyOnChange = true)

    private suspend fun updateLastScheduledLocked(plan: AlarmPlan) {
        settings.update {
            it.copy(
                lastScheduledWakeEpochMinute =
                    plan.wakeAt.toLocalDate().toEpochDay() * 1440L +
                        plan.wakeAt.hour * 60L + plan.wakeAt.minute,
                lastScheduledLabel = plan.label
            )
        }
    }

    private suspend fun clearLastScheduledLocked() {
        settings.update {
            it.copy(lastScheduledWakeEpochMinute = -1L, lastScheduledLabel = "")
        }
    }

    /** Эпоха-минута маркера → момент пробуждения (некорректный/пустой маркер → null). */
    private fun markerToLocalDateTime(v: Long): LocalDateTime? =
        if (v <= 0) null
        else LocalDate.ofEpochDay(v / 1440L).atStartOfDay().plusMinutes(v % 1440L)

    /**
     * Метка для звонка/уведомлений APP-движка (SET-5): плановая «Авто: …»
     * перепрефиксуется на язык интерфейса. Не-«Авто»-метки (тест) не трогаются.
     */
    private fun appDisplayLabel(label: String): String =
        if (label.startsWith(LABEL_PREFIX)) {
            LocaleHelper.string(context, R.string.auto_label_prefix) + " " +
                label.removePrefix(LABEL_PREFIX)
        } else {
            label
        }

    private companion object {
        const val TAG = "AlarmGateway"

        /** Префикс плановых меток (AlarmPlanner) — единый для SET/DISMISS в часах. */
        const val LABEL_PREFIX = "Авто: "
    }
}
