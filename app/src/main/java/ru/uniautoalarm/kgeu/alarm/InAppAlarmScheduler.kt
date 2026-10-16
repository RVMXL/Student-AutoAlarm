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
 *  — [REQ_ONE_SHOT]: разовый будильник ближайшего пробуждения (логика «одна ночь вперёд»);
 *  — [REQ_TEST]: тестовый звонок из настроек.
 * Повторная постановка с тем же кодом ПЕРЕЗАПИСЫВАЕТ будильник (FLAG_UPDATE_CURRENT) —
 * дедупликация получается сама собой, маркеры нужны только для UI и ночной сверки.
 *
 * Слоты партии прежнего режима «на несколько дней вперёд» (удалён в 0.8.0) больше
 * не создаются; [cancelRange] остался для одноразовой очистки наследия при обновлении.
 */
class InAppAlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "InAppAlarmScheduler"
        private const val REQ_ONE_SHOT = 101
        private const val REQ_TEST = 102

        /** Legacy: коды слотов партии прежнего режима «на несколько дней вперёд» (очистка). */
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

    /** Разовый будильник ближайшего пробуждения (логика «одна ночь вперёд», 0.8.0). */
    fun scheduleOneShot(wakeAt: LocalDateTime, label: String): Boolean =
        schedule(REQ_ONE_SHOT, wakeAt, label)

    /** Тестовый звонок на [at] с меткой «Тест» (проверка движка из настроек).
     *  0.13.0: момент принимает гейт — он же сохраняет его в настройках для карточки. */
    fun scheduleTest(at: LocalDateTime): Boolean =
        schedule(
            REQ_TEST,
            at,
            LocaleHelper.string(context, R.string.test_alarm_label)
        )

    /** Снять слот по коду, если он стоит. */
    private fun cancel(requestCode: Int) {
        val am = alarmManager() ?: return
        val pending = slotProbe(requestCode)
        if (pending != null) am.cancel(pending)
    }

    fun cancelOneShot() = cancel(REQ_ONE_SHOT)

    fun cancelTest() = cancel(REQ_TEST)

    /**
     * Стоит ли сейчас слот разового будильника (AL-1): PendingIntent-проба
     * FLAG_NO_CREATE. Маркер дедупликации в DataStore переживает перезагрузку,
     * а слот AlarmManager — нет, поэтому перед ранним выходом по маркеру
     * [AlarmGateway.setNextUpcoming] проверяет слот фактическим состоянием системы.
     */
    fun hasOneShot(): Boolean = slotProbe(REQ_ONE_SHOT) != null

    /** Стоит ли сейчас слот тестового будильника (0.13.0): карточка скрывает строку
     *  теста, если слот умер (ребут/force-stop) — как [hasOneShot] для авто-будильника. */
    fun hasTest(): Boolean = slotProbe(REQ_TEST) != null

    /** Проба слота: тот же интент-образец, что и при постановке, но без создания. */
    private fun slotProbe(requestCode: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmFireReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

    /** Legacy: снять слоты партии «на несколько дней вперёд» в диапазоне дат (с запасом по краям). */
    fun cancelRange(fromEpochDay: Long, toEpochDay: Long) {
        if (toEpochDay < fromEpochDay) return
        for (day in fromEpochDay..toEpochDay) cancel(weekRequestCode(day))
    }
}
