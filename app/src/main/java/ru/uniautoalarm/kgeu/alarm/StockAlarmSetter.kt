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
 * android.intent.action.SET_ALARM (EXTRA_SKIP_UI = true). Своих будильников нет —
 * сигнал звенит там, где пользователь привык.
 */
class StockAlarmSetter(
    private val context: Context,
    private val planner: AlarmPlanner,
    private val settings: SettingsRepository,
) {

    /** Есть ли на устройстве часы, декларирующие обработку ACTION_SET_ALARM. */
    fun canSet(): Boolean =
        try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= 33) {
                pm.resolveActivity(alarmIntent(), PackageManager.ResolveInfoFlags.of(0)) != null
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(alarmIntent(), 0) != null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось проверить обработчик SET_ALARM: ${e.message}")
            false
        }

    /**
     * Поставить будильник на [date] по плану планировщика.
     * @return план, если будильник реально передан часам;
     *         null — пар нет / часы не приняли команду / сбой запуска.
     */
    suspend fun setForDate(date: LocalDate, notify: Boolean = true): AlarmPlan? =
        withContext(Dispatchers.Default) {
            // resolveActivity ненадёжен на части оболочек (package visibility, кастомные часы
            // на Realme/Xiaomi): отрицательный ответ — НЕ повод отказываться, пробуем ставить.
            if (!canSet()) {
                Log.w(TAG, "resolveActivity не нашёл обработчик SET_ALARM — пробуем поставить всё равно")
            }

            val plan = planner.planFor(date)
            if (plan == null) {
                if (notify) Notifications.alarmSkipped(context, date, "пар нет")
                return@withContext null
            }

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                putExtra(AlarmClock.EXTRA_HOUR, plan.wakeAt.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, plan.wakeAt.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, plan.label)
                putExtra(AlarmClock.EXTRA_VIBRATE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // контекст приложения — метка обязательна
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Часы не приняли SET_ALARM: ${e.message}")
                if (notify) {
                    Notifications.alarmSkipped(
                        context, date,
                        "часы телефона не приняли команду — включите «Напоминание о будильнике» в настройках"
                    )
                }
                return@withContext null
            }
            if (notify) Notifications.alarmSet(context, plan)
            plan
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
    fun setTestAlarm(minutesFromNow: Int = 2): Boolean {
        val at = LocalDateTime.now().plusMinutes(minutesFromNow.toLong())
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_HOUR, at.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, at.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Тест: Автобудильник работает!")
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Тестовый будильник не принят часами: ${e.message}")
            false
        }
    }

    private fun alarmIntent(): Intent = Intent(AlarmClock.ACTION_SET_ALARM)

    companion object {
        private const val TAG = "StockAlarmSetter"
    }
}
