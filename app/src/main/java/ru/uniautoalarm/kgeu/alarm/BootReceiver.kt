package ru.uniautoalarm.kgeu.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.AutoAlarmApp
import ru.uniautoalarm.kgeu.data.model.AlarmMode
import ru.uniautoalarm.kgeu.work.WorkScheduler

/**
 * После перезагрузки: будильники AlarmManager теряются, WorkManager — нет.
 * Восстанавливаем ночной планировщик, дотягиваем расписание и, если партия
 * «на неделю вперёд» истекла, ставим её заново.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT_POWERON) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val appContext = context.applicationContext

                // 1) восстановить ночной тик, напоминание и разовую синхронизацию
                NightlyScheduler.scheduleNext(appContext)
                ReminderScheduler.scheduleNext(appContext)
                WorkScheduler.enqueueSyncNow(appContext)

                // 2) страховка режима «на неделю вперёд»:
                // если прошлая партия будильников не покрывает даже завтра — поставить заново
                val container = (appContext as AutoAlarmApp).container
                val s = container.settings.current()
                val tomorrowEpochDay = LocalDate.now().plusDays(1).toEpochDay()
                if (s.setupComplete && s.autoAlarmEnabled &&
                    s.mode == AlarmMode.WEEK_AHEAD &&
                    s.weekAlarmsSetUntilEpochDay < tomorrowEpochDay
                ) {
                    container.stockAlarms.setWeekAhead()
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
