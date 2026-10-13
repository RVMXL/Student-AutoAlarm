package ru.uniautoalarm.kgeu.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.uniautoalarm.kgeu.AutoAlarmApp
import ru.uniautoalarm.kgeu.data.model.Target
import ru.uniautoalarm.kgeu.work.SyncWorker

/**
 * Ночной тик (00:05): обновить расписание на завтра, поставить будильник в стоковые часы,
 * перепланировать себя на следующую ночь и запустить досинхронизацию через WorkManager.
 */
class NightlyAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync() // держим Broadcast открытым до конца асинхронной работы
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val appContext = context.applicationContext
                val container = (appContext as AutoAlarmApp).container

                val s = container.settings.current()
                if (!s.setupComplete) return@launch // онбординг не пройден — не мешаем

                // 1) всегда перепланируем следующую ночь
                NightlyScheduler.scheduleNext(appContext)

                if (!s.autoAlarmEnabled) return@launch // тумблер автовключения выключен

                val tomorrow = LocalDate.now().plusDays(1)

                // 2) свежее расписание на завтра (максимум 9 с; при ошибке остаёмся на кэше)
                val type = s.targetType
                val id = s.targetId
                if (type != null && id != null) {
                    try {
                        withTimeoutOrNull(9_000) {
                            container.scheduleRepository.refreshWeek(
                                Target(type, id, s.targetName), tomorrow
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Ночное обновление расписания не удалось: ${e.message}")
                    }
                }

                // 3) будильник на завтра (планировщик возьмёт кэш, если сеть упала)
                container.stockAlarms.setForDate(tomorrow, notify = true)

                // 4) фоновая досинхронизация: сегодня, неделя+7, полный семестр по графику
                val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    SyncWorker.WORK_NAME_ONESHOT,
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Ночной тик прерван: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NightlyAlarmReceiver"
    }
}
