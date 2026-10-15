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

                // 2) движок приложения: будильник AlarmManager перезагрузку НЕ переживает —
                //    ставим ближайшее предстоящее (сегодня/завтра) немедленно. Будильники
                //    стоковых часов перезагрузку переживают — их сверит ночной тик.
                //    Если телефон был выключен в 00:05 и включён утром — это и есть самовосстановление
                val container = (appContext as AutoAlarmApp).container
                val s = container.settings.current()
                if (s.setupComplete && s.autoAlarmEnabled && s.alarmTarget == AlarmTarget.APP) {
                    container.alarm.setNextUpcoming(notify = false)
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
