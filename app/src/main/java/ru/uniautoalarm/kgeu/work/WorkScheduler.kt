package ru.uniautoalarm.kgeu.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Диспетчеризация WorkManager-задач (имена уникальных работ — в SyncWorker). */
object WorkScheduler {

    /** Разовая expedited-синхронизация: тянем свежие данные сразу (предыдущую заменяем). */
    fun enqueueSyncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MS, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.WORK_NAME_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Периодическая синхронизация каждые 12 часов при наличии сети.
     * DATA-12 (0.10.0): политика UPDATE вместо KEEP — при изменении запроса
     * (например, будущая смена периода/ограничений) новая спецификация ДОЙДЁТ
     * до уже установленных копий приложения, а не заморозится навсегда.
     */
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MS, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Экспоненциальный backoff для retry-попыток SyncWorker (DATA-9). */
    private const val BACKOFF_DELAY_MS = 10L * 60 * 1000
}
