package ru.uniautoalarm.kgeu.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.time.LocalDate
import ru.uniautoalarm.kgeu.AutoAlarmApp
import ru.uniautoalarm.kgeu.alarm.Notifications
import ru.uniautoalarm.kgeu.data.model.AlarmMode
import ru.uniautoalarm.kgeu.data.model.Target

/**
 * Фоновая перепроверка расписания: сегодня, сегодня+7, раз в 7 дней — весь семестр,
 * плюс дособор партии будильников «на неделю вперёд».
 * Все ошибки глотаются (Result.success): подробности уже в ScheduleRepository.syncState.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AutoAlarmApp).container
        val s = container.settings.current()
        if (!s.setupComplete) return Result.success() // онбординг не пройден

        val type = s.targetType
        val id = s.targetId
        if (type != null && id != null) {
            val target = Target(type, id, s.targetName)

            // Неделя сейчас и через 7 дней — дёшево (~15 КБ каждый запрос)
            runCatching { container.scheduleRepository.refreshWeek(target, LocalDate.now()) }
            runCatching { container.scheduleRepository.refreshWeek(target, LocalDate.now().plusDays(7)) }

            // Полный семестр — не чаще раза в 7 дней
            val now = System.currentTimeMillis()
            if (now - s.lastFullSyncEpochMs > FULL_SYNC_INTERVAL_MS) {
                runCatching { container.scheduleRepository.refreshFull(target) }
            }
        }

        // «На несколько дней вперёд»: партия будильников переставляется по актуальному
        // расписанию; часы — по сигнатуре (повторы), приложение — перезаписью слотов
        if (s.mode == AlarmMode.WEEK_AHEAD && s.autoAlarmEnabled) {
            runCatching { container.alarm.setWeekAhead(notify = false) }
        }

        return Result.success()
    }

    /**
     * На Android < 12 expedited-работа исполняется как foreground-сервис:
     * без этой заглушки WorkManager уронит задачу IllegalStateException-ом.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = Notifications.syncForeground(applicationContext)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        /** Уникальное имя периодической синхронизации (каждые 12 часов). */
        const val WORK_NAME_PERIODIC = "sync"

        /** Уникальное имя разовой expedited-синхронизации. */
        const val WORK_NAME_ONESHOT = "syncNow"

        private const val FULL_SYNC_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        private const val FOREGROUND_NOTIFICATION_ID = 2001
    }
}
