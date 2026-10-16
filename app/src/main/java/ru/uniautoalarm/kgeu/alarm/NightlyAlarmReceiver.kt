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
 * Ночная сверка (00:05 и страховки 01:00/02:00/03:00 ночи, AL-23): обновить расписание
 * на день, поставить/снять
 * будильник, перепланировать себя на следующий день и запустить досинхронизацию
 * через WorkManager. Тумблер включён — сверка обязана проснуться каждый день (логика 0.8.0).
 */
class NightlyAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync() // держим Broadcast открытым до конца асинхронной работы
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val appContext = context.applicationContext
                val container = (appContext as AutoAlarmApp).container

                // 1) ВСЕГДА перепланируем следующую ночь (AL-14, 0.10.0): раньше это
                //    стояло после раннего выхода по !setupComplete — цепочка тиков
                //    умирала навсегда, пока пользователь не включил бы тумблер или
                //    не перезагрузил телефон. Теперь тик самовосстанавливается всегда.
                NightlyScheduler.scheduleNext(appContext)

                val s = container.settings.current()
                if (!s.setupComplete) return@launch // онбординг не пройден — не мешаем

                if (!s.autoAlarmEnabled) return@launch // тумблер автовключения выключен

                // 2) свежее расписание на текущую неделю (+следующая внутри refreshWeek;
                //    0.10.0 (AL-5): бюджет сжат 9с → 7с — внутри ~10-с окна goAsync
                //    должен уместиться и ещё быстрый reconcile на кэше; при ошибке
                //    остаёмся на кэше)
                val today = LocalDate.now()
                val type = s.targetType
                val id = s.targetId
                if (type != null && id != null) {
                    try {
                        withTimeoutOrNull(7_000) {
                            container.scheduleRepository.refreshWeek(
                                Target(type, id, s.targetName), today
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Ночное обновление расписания не удалось: ${e.message}")
                    }
                }

                // 3) сверка будильника с ближайшим ПРЕДСТОЯЩИМ пробуждением (сегодня/завтра):
                //    есть пара — ставит на ближайшее время, нет — снимает фантом.
                //    0.7.1: сверка сравнивает маркер с ближайшим предстоящим моментом —
                //    живой будильник не снимается; 0.8.0: режимов больше нет, сверка одна
                container.alarm.nightlyReconcile()

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
