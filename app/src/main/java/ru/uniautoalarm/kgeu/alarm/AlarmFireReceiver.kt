package ru.uniautoalarm.kgeu.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
 * 3) 0.10.0 (AL-10/AL-11): экран звонка поднимается startActivity'ем. Запуск
 *    разрешён: приёмник точного будильника получает temporary allowlist, а
 *    приложение держит USE_EXACT_ALARM (Android 14 BAL-exemption для будильников).
 *    0.19.0: подъём ВСЕГДА, а не только при отказанных уведомлениях/FSI —
 *    локскрин Android не показывает кнопки действий уведомлений, поэтому
 *    при невызванном FSI (Android 14+ не выдаёт его свежим установкам) экран
 *    загорался, а способа выключить будильник без разблокировки не было;
 *    RingActivity (showWhenLocked) поверх лока даёт кнопку «Отключить» всегда.
 *    Повторный подъём при уже открытой FSI активити безвреден (singleTop → onNewIntent).
 * 4) 0.14.0 (локскрин-отключение): страховочно ВКЛЮЧАЕМ экран wake-lock'ом
 *    (ACQUIRE_CAUSES_WAKEUP): оболочки, игнорирующие FSI, оставляли экран погашенным —
 *    будильник звенел «в темноту», а снять его можно было только через шторку после
 *    разблокировки. Теперь экран загорается, и RingActivity с кнопкой «Отключить»
 *    открывается поверх лока (п. 3) — снятие без разблокировки устройства.
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

        // 3) 0.19.0: подъём экрана звонка ВСЕГДА. Раньше — только при недоступных
        //    уведомлениях/FSI, но кнопки действий уведомлений системный локскрин
        //    не показывает, и при невызванном FSI выключить будильник с лока
        //    было нечем. Дубликат launchMode=singleTop — экран не задвоится.
        runCatching {
            appContext.startActivity(
                Intent(appContext, RingActivity::class.java)
                    .putExtra(EXTRA_LABEL, label)
                    .putExtra(EXTRA_TRIGGER_MS, triggerAtMs)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "Экран звонка не поднят: ${it.message}") }
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

    companion object {
        private const val TAG = "AlarmFireReceiver"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_TRIGGER_MS = "extra_trigger_ms"
    }
}
