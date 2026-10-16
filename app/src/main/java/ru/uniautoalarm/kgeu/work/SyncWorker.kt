package ru.uniautoalarm.kgeu.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import ru.uniautoalarm.kgeu.AutoAlarmApp
import ru.uniautoalarm.kgeu.alarm.Notifications
import ru.uniautoalarm.kgeu.data.model.Target

/**
 * Фоновая перепроверка расписания: сегодня, сегодня+7, раз в 7 дней — весь семестр,
 * плюс сверка будильника с ближайшим предстоящим пробуждением (логика «одна ночь вперёд»).
 *
 * DATA-9 (0.10.0): сетевые сбои больше не глотаются в Result.success — воркер вернёт
 * Result.retry() (экспоненциальный backoff задан на запросах в WorkScheduler);
 * после MAX_ATTEMPTS попыток сдаёмся и выходим с успехом, чтобы периодика не зависала.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AutoAlarmApp).container
        val s = container.settings.current()
        if (!s.setupComplete) return Result.success() // онбординг не пройден

        var failed = false
        val type = s.targetType
        val id = s.targetId
        if (type != null && id != null) {
            val target = Target(type, id, s.targetName)

            // Неделя сейчас и через 7 дней — дёшево (~15 КБ каждый запрос).
            // DATA-10: CancellationException не глотается — отмену воркера отдаём системе
            try {
                container.scheduleRepository.refreshWeek(target, LocalDate.now())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            }
            try {
                container.scheduleRepository.refreshWeek(target, LocalDate.now().plusDays(7))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            }

            // Полный семестр — не чаще раза в 7 дней
            val now = System.currentTimeMillis()
            if (now - s.lastFullSyncEpochMs > FULL_SYNC_INTERVAL_MS) {
                try {
                    container.scheduleRepository.refreshFull(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failed = true
                }
            }
        }

        // Единая логика 0.8.0: сверка с ближайшим ПРЕДСТОЯЩИМ пробуждением (сегодня/завтра)
        // по свежему расписанию — замена, опубликованная среди дня, переставляет будильник
        // сразу, а не следующей ночью; верный будильник не трогается.
        // AL-2: allowClockDelivery=false — из фона SET_ALARM часам не отправляем:
        // startActivity вне allowlist может быть молча заблокирован (BAL, Android 10+),
        // а «исключение не брошено» ≠ «будильник поставлен» — запись маркера
        // по ложной доставке превращала ночную сверку в no-op. APP-слот правится
        // отсюда безопасно (AlarmManager, без BAL).
        if (s.autoAlarmEnabled) {
            runCatching {
                container.alarm.reconcileUpcoming(notifyOnChange = true, allowClockDelivery = false)
            }
        }

        // DATA-9: сеть падала — просим retry с backoff (не чаще MAX_ATTEMPTS раз)
        return if (failed && runAttemptCount < MAX_ATTEMPTS) Result.retry()
        else Result.success()
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

        /** DATA-9: максимум retry-попыток, после — сдаёмся до следующего графика. */
        const val MAX_ATTEMPTS = 3

        private const val FULL_SYNC_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        private const val FOREGROUND_NOTIFICATION_ID = 2001
    }
}
