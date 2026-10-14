package ru.uniautoalarm.kgeu.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.uniautoalarm.kgeu.MainActivity
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper

/**
 * Статусные уведомления движка: «будильник поставлен / не поставлен / партия на неделю».
 * Один канал IMPORTANCE_DEFAULT — постановка будильника не должна орать как тревога.
 */
object Notifications {

    const val CHANNEL_ID = "autoalarm_status"

    private const val TAG = "Notifications"
    private const val NOTIF_SET_ID = 1001
    private const val NOTIF_SKIPPED_ID = 1002
    private const val NOTIF_WEEK_ID = 1003
    private const val NOTIF_REMINDER_ID = 1004

    // форматтеры — функции, а не val: даты уведомлений должны следовать
    // языку приложения, выбранному в момент отправки
    private fun ruDate(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE dd MMMM", Locale.getDefault())

    private fun ruDateShort(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMMM", Locale.getDefault())

    /** Канал создаётся идемпотентно перед любой отправкой (minSdk 26 — API доступен всегда). */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            LocaleHelper.string(context, R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = LocaleHelper.string(context, R.string.notif_channel_desc)
        }
        notificationManager(context).createNotificationChannel(channel)
    }

    /** «Будильник поставлен на HH:MM», текст «<дата>: <предмет>, ауд. <аудитория>». */
    fun alarmSet(context: Context, plan: AlarmPlan) {
        val time = String.format(Locale.getDefault(), "%02d:%02d", plan.wakeAt.hour, plan.wakeAt.minute)
        val details = when {
            plan.lesson != null -> lessonDetails(context, plan.lesson.subject, plan.lesson.room)
            plan.customEvent != null -> plan.customEvent.name
            else -> plan.label
        }
        val text = "${plan.date.format(ruDate())}: $details"
        val notification = baseBuilder(context)
            .setContentTitle(LocaleHelper.string(context, R.string.notif_alarm_set, time))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(mainActivityIntent(context))
            .setAutoCancel(true)
            .build()
        post(context, NOTIF_SET_ID, notification)
    }

    /** Будильник не поставлен: пар нет, цель не выбрана и т.п. */
    fun alarmSkipped(context: Context, date: LocalDate, reason: String) {
        val text = "${date.format(ruDate())}: $reason"
        val notification = baseBuilder(context)
            .setContentTitle(LocaleHelper.string(context, R.string.notif_skipped_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(mainActivityIntent(context))
            .setAutoCancel(true)
            .build()
        post(context, NOTIF_SKIPPED_ID, notification)
    }

    /** Партия повторяющихся будильников «на несколько дней вперёд» поставлена. */
    fun weekSet(context: Context, count: Int, from: LocalDate, to: LocalDate) {
        val title = if (count == 1) {
            LocaleHelper.string(context, R.string.notif_week_one)
        } else {
            LocaleHelper.string(context, R.string.notif_week_many, count)
        }
        val text = LocaleHelper.string(
            context,
            R.string.notif_week_text,
            from.format(ruDateShort()),
            to.format(ruDateShort())
        )
        val notification = baseBuilder(context)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(mainActivityIntent(context))
            .setAutoCancel(true)
            .build()
        post(context, NOTIF_WEEK_ID, notification)
    }

    /** Партия «на несколько дней вперёд» уже стоит и актуальна — перестановка не нужна. */
    fun weekAlreadySet(context: Context) {
        val notification = baseBuilder(context)
            .setContentTitle(LocaleHelper.string(context, R.string.notif_week_already_title))
            .setContentText(LocaleHelper.string(context, R.string.notif_week_already_text))
            .setContentIntent(mainActivityIntent(context))
            .setAutoCancel(true)
            .build()
        post(context, NOTIF_WEEK_ID, notification)
    }

    /**
     * Напоминание о будильнике (фолбэк для оболочек, где стоковые часы не принимают
     * SET_ALARM — Realme/Huawei и др.): «завтра такая-то пара во столько —
     * не забудьте поставить будильник».
     */
    fun reminder(context: Context, plan: AlarmPlan) {
        val time = String.format(Locale.getDefault(), "%02d:%02d", plan.wakeAt.hour, plan.wakeAt.minute)
        val details = when {
            plan.lesson != null -> lessonDetails(context, plan.lesson.subject, plan.lesson.room)
            plan.customEvent != null -> plan.customEvent.name
            else -> plan.label
        }
        val text = LocaleHelper.string(
            context,
            R.string.notif_reminder_text,
            plan.date.format(ruDate()),
            time,
            details
        )
        val notification = baseBuilder(context)
            .setContentTitle(LocaleHelper.string(context, R.string.notif_reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(mainActivityIntent(context))
            .setAutoCancel(true)
            .build()
        post(context, NOTIF_REMINDER_ID, notification)
    }

    /**
     * Тихая заглушка для foreground-сервиса WorkManager (expedited-fallback на Android < 12).
     * Уведомление FGS разрешению POST_NOTIFICATIONS на Android 13+ не требует.
     */
    fun syncForeground(context: Context): Notification =
        baseBuilder(context)
            .setContentTitle(LocaleHelper.string(context, R.string.notif_sync_title))
            .setContentText(LocaleHelper.string(context, R.string.notif_sync_text))
            .setOngoing(true)
            .build()

    // --- Внутреннее ---

    private fun lessonDetails(context: Context, subject: String, room: String): String =
        if (room.isBlank()) subject
        else LocaleHelper.string(context, R.string.notif_details_room, subject, room)

    private fun baseBuilder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    private fun mainActivityIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun post(context: Context, id: Int, notification: Notification) {
        ensureChannel(context)
        // Android 13+: без разрешения POST_NOTIFICATIONS отправка бессмысленна — молча пропускаем
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Нет разрешения POST_NOTIFICATIONS — уведомление пропущено")
            return
        }
        try {
            notificationManager(context).notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Уведомление отклонено системой: ${e.message}")
        }
    }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
