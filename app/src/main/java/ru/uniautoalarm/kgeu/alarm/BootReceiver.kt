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
 * Восстанавливаем все ночные сверки (00:05 + 01:00/02:00/03:00, AL-23) и, при включённом тумблере,
 * разовый будильник движка приложения (будильники часов перезагрузку переживают сами).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT_POWERON) return

        val appContext = context.applicationContext
        // SEC-6 (0.11.0): ACTION_QUICKBOOT_POWERON не входит в protected-broadcast на
        // части прошивок — послать его может ЛЮБОЕ приложение. BOOT_COMPLETED защищён
        // системой, поэтому спам-гейт нужен только для QUICKBOOT: не чаще раза в 60 с
        // (rearm идемпотентен, но бесконечный спам будил бы CPU/батарею).
        if (action == ACTION_QUICKBOOT_POWERON && !quickbootAllowed(appContext)) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // 1) восстановить все ночные сверки, напоминание и разовую синхронизацию
                NightlyScheduler.scheduleNext(appContext)
                ReminderScheduler.scheduleNext(appContext)
                WorkScheduler.enqueueSyncNow(appContext)

                // 2) восстановление ближайшего будильника. Слоты AlarmManager
                //    перезагрузку НЕ переживают (движок приложения), маркер
                //    дедупликации — переживает: без сброса маркера постановка
                //    «подтвердила» бы мёртвый слот (AL-1). Будильники стоковых
                //    часов перезагрузку переживают, но если телефон был выключен
                //    ВСЮ НОЧЬ, тики 00:05/01:00–03:00 пропущены и будильник на сегодня
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

        private const val GUARD_PREFS = "boot_guard"
        private const val GUARD_KEY_LAST_QUICKBOOT = "last_quickboot_ms"
        private const val QUICKBOOT_MIN_INTERVAL_MS = 60_000L

        /** true, если QUICKBOOT_POWERON-спам прошёл: с последней обработки прошло ≥ 60 с (SEC-6). */
        private fun quickbootAllowed(context: Context): Boolean {
            val prefs = context.getSharedPreferences(GUARD_PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val last = prefs.getLong(GUARD_KEY_LAST_QUICKBOOT, 0L)
            if (now - last < QUICKBOOT_MIN_INTERVAL_MS) {
                Log.i(TAG, "QUICKBOOT_POWERON чаще раза в 60 с — игнорирую (SEC-6)")
                return false
            }
            prefs.edit().putLong(GUARD_KEY_LAST_QUICKBOOT, now).apply()
            return true
        }
    }
}
