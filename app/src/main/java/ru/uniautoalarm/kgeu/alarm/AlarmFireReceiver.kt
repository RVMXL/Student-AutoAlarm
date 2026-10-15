package ru.uniautoalarm.kgeu.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Срабатывание собственного будильника (движок «В приложении»):
 * 1) мгновенно показываем звонящее уведомление с full-screen intent — система сама
 *    развернёт RingActivity поверх экрана блокировки, если разрешение выдано,
 *    иначе покажет heads-up с кнопкой «Отключить»;
 * 2) запускаем foreground-сервис звонка (звук + вибрация).
 * Запуск FGS из приёмника точного будильника — разрешённый системой сценарий
 * (так же устроен Google DeskClock).
 */
class AlarmFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: ""
        val triggerAtMs = intent.getLongExtra(EXTRA_TRIGGER_MS, System.currentTimeMillis())
        val appContext = context.applicationContext

        // 1) уведомление сразу — ещё до подъёма сервиса (задержка FSI минимальна)
        runCatching { Notifications.postRinging(appContext, label) }
            .onFailure { Log.w(TAG, "Звонящее уведомление не показано: ${it.message}") }

        // 2) сервис со звуком и вибрацией
        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, AlarmRingingService::class.java)
                    .putExtra(AlarmRingingService.EXTRA_LABEL, label)
                    .putExtra(AlarmRingingService.EXTRA_TRIGGER_MS, triggerAtMs)
            )
        }.onFailure { Log.w(TAG, "Сервис звонка не запущен: ${it.message}") }
    }

    companion object {
        private const val TAG = "AlarmFireReceiver"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_TRIGGER_MS = "extra_trigger_ms"
    }
}
