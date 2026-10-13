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
 * Ночной планировщик: точный AlarmManager на 00:05 (+ периодический sync WorkManager).
 * В 00:05 NightlyAlarmReceiver обновляет расписание на завтра и ставит будильник —
 * до того, как агрессивные планировщики ОС усыпят приложение.
 */
object NightlyScheduler {

    const val NIGHT_HOUR = 0
    const val NIGHT_MINUTE = 5

    private const val REQUEST_CODE_NIGHTLY = 42
    private const val WINDOW_MS = TimeUnit.MINUTES.toMillis(10) // запас для не-точного режима

    /** Поставить/перепоставить ночной тик и гарантировать периодический sync. Идемпотентно. */
    fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (am != null) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(NIGHT_HOUR, NIGHT_MINUTE)
            if (!now.isBefore(next)) next = next.plusDays(1) // 00:05 уже прошла — на завтра

            val pending = PendingIntent.getBroadcast(
                appContext,
                REQUEST_CODE_NIGHTLY,
                Intent(appContext, NightlyAlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val triggerAtMs = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            // На API < 31 разрешение SCHEDULE_EXACT_ALARM выдано всегда;
            // на 31+ — только если пользователь его не отключил в настройках.
            val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
            } else {
                // Фолбэк: окно ±10 минут — теряем точность, но продолжаем работать
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMs, WINDOW_MS, pending)
            }
        }
        WorkScheduler.ensurePeriodic(appContext)
    }
}
