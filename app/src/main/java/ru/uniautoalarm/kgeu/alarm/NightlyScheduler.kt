package ru.uniautoalarm.kgeu.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import ru.uniautoalarm.kgeu.work.WorkScheduler

/**
 * Ночной планировщик: точные AlarmManager на 00:05 и на 06:00 (+ периодический sync WorkManager).
 * В 00:05 NightlyAlarmReceiver обновляет расписание на день и ставит/снимает будильник —
 * до того, как агрессивные планировщики ОС усыпят приложение.
 *
 * 0.8.0: второй точный тик в 06:00 — СТРАХОВКА ночной сверки. Если 00:05 пропущено
 * (телефон был выключен, ОМ-оболочка придавила точный будильник), тик 06:00 всё ещё
 * успевает поставить будильник на первую пару дня (обычно 08:20 и позже; подъём 06:50).
 * Сверка идемпотентна (маркер последнего поставленного момента) — двойного будильника
 * не будет ни при каком порядке срабатывания. После перезагрузки оба тика восстанавливает
 * BootReceiver.
 */
object NightlyScheduler {

    const val NIGHT_HOUR = 0
    const val NIGHT_MINUTE = 5

    /** Страховочный тик: утро того же дня — последний шанс поставить будильник на сегодня. */
    const val BACKUP_HOUR = 6
    const val BACKUP_MINUTE = 0

    private const val REQUEST_CODE_NIGHTLY = 42
    private const val REQUEST_CODE_BACKUP = 43

    // не const: TimeUnit.MINUTES.toMillis — вызов функции, const запрещён языком
    private val WINDOW_MS = TimeUnit.MINUTES.toMillis(10)

    /** Окно для не-точного режима — переиспользуется ReminderScheduler. */
    fun windowMs(): Long = WINDOW_MS

    /** Поставить/перепоставить оба тика и гарантировать периодический sync. Идемпотентно. */
    fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (am != null) {
            val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            scheduleTick(appContext, am, exactAllowed, REQUEST_CODE_NIGHTLY, NIGHT_HOUR, NIGHT_MINUTE)
            scheduleTick(appContext, am, exactAllowed, REQUEST_CODE_BACKUP, BACKUP_HOUR, BACKUP_MINUTE)
        }
        WorkScheduler.ensurePeriodic(appContext)
    }

    /** Один тик на ближайшее наступление HH:MM (если время уже прошло — на завтра). */
    private fun scheduleTick(
        appContext: Context,
        am: AlarmManager,
        exactAllowed: Boolean,
        requestCode: Int,
        hour: Int,
        minute: Int,
    ) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, minute)
        if (!now.isBefore(next)) next = next.plusDays(1) // время уже прошло — на завтра

        val pending = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            Intent(appContext, NightlyAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val triggerAtMs = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
        } else {
            // Фолбэк: окно ±10 минут — теряем точность, но продолжаем работать
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMs, WINDOW_MS, pending)
        }
    }
}
