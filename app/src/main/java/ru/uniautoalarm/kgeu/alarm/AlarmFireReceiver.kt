package ru.uniautoalarm.kgeu.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import ru.uniautoalarm.kgeu.ui.ring.RingActivity

/**
 * Срабатывание собственного будильника (движок «В приложении»):
 * 1) мгновенно показываем звонящее уведомление с full-screen intent — система сама
 *    развернёт RingActivity поверх экрана блокировки, если разрешение выдано,
 *    иначе покажет heads-up с кнопкой «Отключить»;
 * 2) запускаем foreground-сервис звонка (звук + вибрация);
 * 3) 0.10.0 (AL-10/AL-11): если уведомления недоступны (нет POST_NOTIFICATIONS)
 *    или full-screen intent не выдан (Android 14+ не выдаёт FSI свежим установкам) —
 *    ДОПОЛНИТЕЛЬНО поднимаем RingActivity startActivity'ем. Запуск разрешён:
 *    приёмник точного будильника получает temporary allowlist, а приложение
 *    держит USE_EXACT_ALARM (Android 14 BAL-exemption для будильников).
 *    Раньше при отказанных уведомлениях звонок шёл 10 минут без единого способа
 *    выключить — теперь экран звонка открывается всегда.
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

        // 3) AL-10/AL-11: страховочный подъём экрана звонка, когда FSI/уведомления
        //    не сработают. Дубликат launchMode=singleTop — экран просто не задвоится.
        if (!canShowRingingUi(appContext)) {
            runCatching {
                appContext.startActivity(
                    Intent(appContext, RingActivity::class.java)
                        .putExtra(EXTRA_LABEL, label)
                        .putExtra(EXTRA_TRIGGER_MS, triggerAtMs)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { Log.w(TAG, "Экран звонка не поднят (фолбэк): ${it.message}") }
        }
    }

    /**
     * true — уведомление с FSI разовьётся само; false — нужен страховочный
     * startActivity(RingActivity). Уведомление бесполезно, если запрещён
     * POST_NOTIFICATIONS (heads-up не показывается вовсе) ИЛИ не выдан
     * full-screen intent (Android 14+; тогда система покажет тихий heads-up).
     */
    private fun canShowRingingUi(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && !nm.canUseFullScreenIntent()) return false
        }
        return true
    }

    companion object {
        private const val TAG = "AlarmFireReceiver"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_TRIGGER_MS = "extra_trigger_ms"
    }
}
