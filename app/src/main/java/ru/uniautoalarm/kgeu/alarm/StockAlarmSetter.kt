package ru.uniautoalarm.kgeu.alarm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.data.repository.SettingsRepository
import ru.uniautoalarm.kgeu.domain.AlarmPlanner

/**
 * Постановка будильника в СТОКОВОЕ приложение часов через публичный intent
 * android.intent.action.SET_ALARM. Своих будильников нет — сигнал звенит там,
 * где пользователь привык.
 *
 * Тактика против оболочек, молча глушащих неявный интент (ColorOS/Realme UI,
 * часть MIUI — см. разбор Gemini в docs):
 * 1) автопоиск установленного пакета часов по списку известных OEM-пакетов;
 * 2) явный setPackage(найденный) — система ОБЯЗАНА доставить интент по адресу;
 * 3) EXTRA_SKIP_UI = false, если включён тумблер «показывать часы» (фон-постановка
 *    с SKIP_UI=true на ColorOS считается подозрительной и блокируется молча);
 * 4) сбой явного запуска → откат к неявному интенту → сбой и его → уведомление
 *    с подсказкой включить «Напоминание о будильнике».
 */
class StockAlarmSetter(
    private val context: Context,
    private val planner: AlarmPlanner,
    private val settings: SettingsRepository,
) {

    /**
     * Известные пакеты стоковых часов (в порядке приоритета). Видимость на Android 11+
     * обеспечена <queries><package> в манифесте — без них getPackageInfo() рапортует
     * «не установлено» даже при наличии часов.
     */
    private val knownClockPackages = listOf(
        "com.oplus.alarmclock",             // Oppo / OnePlus / Realme — ColorOS 13+, Realme UI
        "com.coloros.alarmclock",           // старые ColorOS
        "com.sec.android.app.clockpackage", // Samsung One UI
        "com.google.android.deskclock",     // Pixel / Motorola / стоковый AOSP
        "com.android.deskclock",            // MIUI/HyperOS, EMUI, FuntouchOS (стандартное имя)
        "com.huawei.deskclock",             // Huawei (отдельный пакет)
        "com.transsion.deskclock"           // Tecno / Infinix / Itel
    )

    /** Пакет установленных часов из известного списка или null. */
    fun detectClockPackage(): String? {
        val pm = context.packageManager
        return knownClockPackages.firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                Log.w(TAG, "probe $pkg: ${e.message}")
                false
            }
        }
    }

    /** Есть ли на устройстве часы: явный пакет из списка ИЛИ декларирование SET_ALARM. */
    fun canSet(): Boolean {
        if (detectClockPackage() != null) return true
        return try {
            val pm = context.packageManager
            val resolved = if (Build.VERSION.SDK_INT >= 33) {
                pm.resolveActivity(alarmIntent(), PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(alarmIntent(), 0)
            }
            resolved != null
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось проверить обработчик SET_ALARM: ${e.message}")
            false
        }
    }

    /**
     * Поставить будильник на [date] по плану планировщика.
     * @return план, если будильник реально передан часам;
     *         null — пар нет / часы не приняли команду / сбой запуска.
     */
    suspend fun setForDate(date: LocalDate, notify: Boolean = true): AlarmPlan? =
        withContext(Dispatchers.Default) {
            val s = settings.current()
            // resolveActivity ненадёжен на части оболочек: отрицательный ответ — НЕ повод
            // отказываться, пробуем ставить (пакетный таргетинг выше по надёжности).
            if (!canSet()) {
                Log.w(TAG, "Обработчик SET_ALARM не обнаружен — пробуем поставить всё равно")
            }

            val plan = planner.planFor(date)
            if (plan == null) {
                if (notify) Notifications.alarmSkipped(context, date, "пар нет")
                return@withContext null
            }

            if (deliver(plan.wakeAt.hour, plan.wakeAt.minute, plan.label, s.alarmShowClockUi)) {
                if (notify) Notifications.alarmSet(context, plan)
                plan
            } else {
                if (notify) {
                    Notifications.alarmSkipped(
                        context, date,
                        "часы телефона не приняли команду — включите «Напоминание о будильнике» в настройках"
                    )
                }
                null
            }
        }

    /**
     * Режим «на неделю вперёд»: одноразовые будильники на weekAheadDays дней
     * после [fromDate] (страховка от агрессивных планировщиков ОС).
     * Прошедшее время и дни без пар пропускаются. Уведомления — одно итоговое.
     * @return сколько будильников реально поставлено.
     */
    suspend fun setWeekAhead(fromDate: LocalDate = LocalDate.now()): Int {
        val s = settings.current()
        var count = 0
        for (i in 1..s.weekAheadDays) {
            val date = fromDate.plusDays(i.toLong())
            val plan = planner.planFor(date) ?: continue
            if (plan.wakeAt > LocalDateTime.now()) {
                if (setForDate(date, notify = false) != null) count++
            }
        }
        val until = fromDate.plusDays(s.weekAheadDays.toLong())
        settings.update { it.copy(weekAlarmsSetUntilEpochDay = until.toEpochDay()) }
        Notifications.weekSet(context, count, fromDate, until)
        return count
    }

    /**
     * Тестовый будильник через [minutesFromNow] минут (кнопка «Проверить будильник» в настройках).
     * Ставится напрямую, без планировщика, с меткой «Тест».
     * @return true, если будильник передан часам.
     */
    suspend fun setTestAlarm(minutesFromNow: Int = 2): Boolean {
        val at = LocalDateTime.now().plusMinutes(minutesFromNow.toLong())
        val showUi = settings.current().alarmShowClockUi
        return deliver(at.hour, at.minute, "Тест: Автобудильник работает!", showUi)
    }

    /**
     * Доставка интента SET_ALARM: явный пакет → неявный фолбэк.
     * @return true, если стартовал хотя бы один вариант (исключений нет).
     */
    private fun deliver(hour: Int, minute: Int, label: String, showClockUi: Boolean): Boolean {
        val target = detectClockPackage()
        val base = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_SKIP_UI, !showClockUi)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // контекст приложения — метка обязательна
        }

        // 1) явный таргетинг найденного пакета — ColorOS/OEM обязаны принять по адресу
        if (target != null) {
            try {
                context.startActivity(base.apply { setPackage(target) })
                Log.i(TAG, "Будильник передан часам $target")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Явный запуск $target не удался: ${e.message} — пробую неявный")
            }
        }

        // 2) фолбэк: неявный интент (сработает на стоковом Android и часах с intent-filter)
        return try {
            context.startActivity(base.apply { setPackage(null) })
            Log.i(TAG, "Будильник передан часам по неявному интенту")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Часы не приняли SET_ALARM даже неявно: ${e.message}")
            false
        }
    }

    private fun alarmIntent(): Intent = Intent(AlarmClock.ACTION_SET_ALARM)

    companion object {
        private const val TAG = "StockAlarmSetter"
    }
}
