package ru.uniautoalarm.kgeu.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
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
 * 4) 0.14.0 (локскрин-отключение): страховочно ВКЛЮЧАЕМ экран wake-lock'ом
 *    (ACQUIRE_CAUSES_WAKEUP): оболочки, игнорирующие FSI, оставляли экран погашенным —
 *    будильник звенел «в темноту», а снять его можно было только через шторку после
 *    разблокировки. Теперь экран загорается, на локскрине видно звонящее уведомление
 *    (VISIBILITY_PUBLIC) с кнопкой «Отключить» — снятие без разблокировки устройства.
 */
class AlarmFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: ""
        val triggerAtMs = intent.getLongExtra(EXTRA_TRIGGER_MS, System.currentTimeMillis())
        val appContext = context.applicationContext

        // 0) страховочное включение экрана (0.14.0): если FSI не отработает (оболочка
        //    игнорирует) — экран всё равно загорится с уведомлением и кнопкой «Отключить»
        runCatching { wakeScreenUp(appContext) }
            .onFailure { Log.w(TAG, "Экран не включён wake-lock'ом: ${it.message}") }

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
     * Включить экран на несколько секунд (0.14.0): deprecated-флаги FULL_WAKE_LOCK +
     * ACQUIRE_CAUSES_WAKEUP — единственный надёжный способ «зажечь» экран из приёмника;
     * RingActivity при этом тоже имеет turnScreenOn (путь FSI). Таймаут короткий —
     * экран не держим насильно, дальше его держит RingActivity (FLAG_KEEP_SCREEN_ON).
     */
    private fun wakeScreenUp(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "uniautoalarm:alarm_fire_wake"
        )
        wakeLock.acquire(10_000L)
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
