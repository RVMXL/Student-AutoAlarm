package ru.uniautoalarm.kgeu.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.AutoAlarmApp
import ru.uniautoalarm.kgeu.data.model.AlarmTarget
import ru.uniautoalarm.kgeu.work.WorkScheduler

/**
 * После перезагрузки: будильники AlarmManager теряются, WorkManager — нет.
 * Восстанавливаем обе ночные сверки (00:05 + 06:00) и, при включённом тумблере,
 * разовый будильник движка приложения (будильники часов перезагрузку переживают сами).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT_POWERON) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val appContext = context.applicationContext

                // 1) восстановить обе ночные сверки, напоминание и разовую синхронизацию
                NightlyScheduler.scheduleNext(appContext)
                ReminderScheduler.scheduleNext(appContext)
                WorkScheduler.enqueueSyncNow(appContext)

                // 2) восстановление ближайшего будильника. Слоты AlarmManager
                //    перезагрузку НЕ переживают (движок приложения), маркер
                //    дедупликации — переживает: без сброса маркера постановка
                //    «подтвердила» бы мёртвый слот (AL-1). Будильники стоковых
                //    часов перезагрузку переживают, но если телефон был выключен
                //    ВСЮ НОЧЬ, тики 00:05/06:00 пропущены и будильник на сегодня
                //    никто не ставил — сверка ставит его, пока время подъёма
                //    не прошло (AL-3).
                val container = (appContext as AutoAlarmApp).container
                val s = container.settings.current()
                if (s.setupComplete && s.autoAlarmEnabled) {
                    when (s.alarmTarget) {
                        AlarmTarget.APP -> container.alarm.rearmFromBoot()
                        AlarmTarget.CLOCK -> container.alarm.reconcileUpcoming(notifyOnChange = false)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Обработка загрузки не удалась: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        // Константы Intent.ACTION_QUICKBOOT_POWERON в публичном SDK нет — берём строкой
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
