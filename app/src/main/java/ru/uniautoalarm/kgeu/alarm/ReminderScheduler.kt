package ru.uniautoalarm.kgeu.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.AutoAlarmApp
import ru.uniautoalarm.kgeu.di.AppContainer

/**
 * Планировщик напоминаний о будильнике (фолбэк для оболочек, где стоковые часы
 * не принимают ACTION_SET_ALARM — например Realme UI китайского рынка).
 * Каждый день в выбранное пользователем время ReminderReceiver покажет уведомление
 * «завтра такая-то пара во столько — не забудьте поставить будильник».
 */
object ReminderScheduler {

    private const val REQUEST_CODE_REMINDER = 43

    /** Поставить/перепоставить ближайший тик напоминания. Идемпотентно, чует настройки. */
    fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (am != null) {
            val settings = (appContext as AutoAlarmApp).container.settings.current()
            if (settings.setupComplete && settings.reminderEnabled) {
                val now = LocalDateTime.now()
                var next = now.toLocalDate().atTime(settings.reminderTime)
                if (!now.isBefore(next)) next = next.plusDays(1) // время уже прошло — на завтра

                val pending = PendingIntent.getBroadcast(
                    appContext,
                    REQUEST_CODE_REMINDER,
                    Intent(appContext, ReminderReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val triggerAtMs = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
                if (exactAllowed) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
                } else {
                    am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMs, NightlyScheduler.windowMs(), pending)
                }
            } else {
                cancel(context)
            }
        }
    }

    /** Снять запланированное напоминание (вызывается при выключении тумблера). */
    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_REMINDER,
            Intent(appContext, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pending != null) am.cancel(pending)
    }

    /** Перепланировать после смены настроек (тумблер/время) — вызывается из ViewModel. */
    fun rescheduleAfterSettingsChange(container: AppContainer) {
        container.appScope.launch {
            scheduleNext(container.appContext)
        }
    }
}
