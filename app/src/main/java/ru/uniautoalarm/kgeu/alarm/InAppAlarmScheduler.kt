package ru.uniautoalarm.kgeu.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId
import ru.uniautoalarm.kgeu.MainActivity
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper

/**
 * Собственный будильник приложения (целевой движок «В приложении», 0.7.0).
 *
 * Основа — AlarmManager.setAlarmClock: системный контракт ПОЛЬЗОВАТЕЛЬСКОГО будильника —
 * точен даже в Doze, показывает значок будильника в статус-баре, и (в отличие от
 * ACTION_SET_ALARM в стоковые часы) полностью управляется приложением: постановка
 * перезаписывает слот, снятие — гарантированный PendingIntent.cancel. Оболочки
 * (ColorOS/Realme UI, MIUI) не могут молча заблокировать вызов собственного
 * AlarmManager — капризов «поставилось, но не снимается» здесь нет по построению.
 *
 * Слоты (requestCode):
 *  — [REQ_ONE_SHOT]: разовый будильник ближайшего пробуждения (режим «Стандарт»);
 *  — [REQ_TEST]: тестовый звонок из настроек;
 *  — [REQ_WEEK_BASE + день]: отдельный слот на каждую дату партии «на несколько дней вперёд».
 * Повторная постановка с тем же кодом ПЕРЕЗАПИСЫВАЕТ будильник (FLAG_UPDATE_CURRENT) —
 * дедупликация получается сама собой, маркеры нужны только для UI и ночной сверки.
 */
class InAppAlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "InAppAlarmScheduler"
        private const val REQ_ONE_SHOT = 101
        private const val REQ_TEST = 102
        private const val REQ_WEEK_BASE = 1000

        /** Стабильный код слота партии «на несколько дней вперёд» для даты. */
        fun weekRequestCode(epochDay: Long): Int =
            REQ_WEEK_BASE + (epochDay % 100_000L).toInt()

        /** Intent остановки звонка (кнопка «Отключить», действие уведомления). */
        fun stopRingingIntent(context: Context): Intent =
            Intent(context, AlarmRingingService::class.java)
                .setAction(AlarmRingingService.ACTION_STOP)

        /** Broadcast-интент срабатывания будильника (см. AlarmFireReceiver). */
        private fun fireIntent(context: Context, label: String, triggerAtMs: Long): Intent =
            Intent(context, AlarmFireReceiver::class.java)
                .putExtra(AlarmFireReceiver.EXTRA_LABEL, label)
                .putExtra(AlarmFireReceiver.EXTRA_TRIGGER_MS, triggerAtMs)
    }

    private fun alarmManager(): AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Поставить точный будильник на [wakeAt] с меткой [label] в слот [requestCode].
     * @return true, если система приняла будильник.
     */
    private fun schedule(requestCode: Int, wakeAt: LocalDateTime, label: String): Boolean {
        val am = alarmManager() ?: return false
        val triggerAtMs = wakeAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val fire = PendingIntent.getBroadcast(
            context, requestCode,
            fireIntent(context, label, triggerAtMs),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return try {
            // setAlarmClock не требует SCHEDULE_EXACT_ALARM: это пользовательский будильник
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, openApp), fire)
            true
        } catch (e: Exception) {
            Log.w(TAG, "setAlarmClock не принят: ${e.message}")
            false
        }
    }

    /** Разовый будильник ближайшего пробуждения (режим «Стандарт»). */
    fun scheduleOneShot(wakeAt: LocalDateTime, label: String): Boolean =
        schedule(REQ_ONE_SHOT, wakeAt, label)

    /** Будильник партии «на несколько дней вперёд» на конкретную дату. */
    fun scheduleWeekDate(epochDay: Long, wakeAt: LocalDateTime, label: String): Boolean =
        schedule(weekRequestCode(epochDay), wakeAt, label)

    /** Тестовый звонок «через 2 минуты» с меткой «Тест» (проверка движка из настроек). */
    fun scheduleTest(minutesFromNow: Int = 2): Boolean =
        schedule(
            REQ_TEST,
            LocalDateTime.now().plusMinutes(minutesFromNow.toLong()),
            LocaleHelper.string(context, R.string.test_alarm_label)
        )

    /** Снять слот по коду, если он стоит. */
    private fun cancel(requestCode: Int) {
        val am = alarmManager() ?: return
        val pending = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmFireReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pending != null) am.cancel(pending)
    }

    fun cancelOneShot() = cancel(REQ_ONE_SHOT)

    fun cancelTest() = cancel(REQ_TEST)

    /** Снять партию «на несколько дней вперёд» в диапазоне дат (с запасом по краям). */
    fun cancelRange(fromEpochDay: Long, toEpochDay: Long) {
        if (toEpochDay < fromEpochDay) return
        for (day in fromEpochDay..toEpochDay) cancel(weekRequestCode(day))
    }
}
