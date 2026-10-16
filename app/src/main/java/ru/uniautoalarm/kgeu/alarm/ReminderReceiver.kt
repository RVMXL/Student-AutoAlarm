package ru.uniautoalarm.kgeu.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.uniautoalarm.kgeu.AutoAlarmApp
import ru.uniautoalarm.kgeu.data.model.AlarmTarget
import ru.uniautoalarm.kgeu.data.model.Target

/**
 * Тик напоминания (время выбирает пользователь, по умолчанию 21:00):
 * обновляет расписание на завтра и показывает своё уведомление
 * «завтра такая-то пара во столько — не забудьте поставить будильник».
 * Работает независимо от стоковых часов — фолбэк для Realme/Huawei и др.,
 * где ACTION_SET_ALARM блокируется оболочкой.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val appContext = context.applicationContext
                val container = (appContext as AutoAlarmApp).container

                // 1) всегда перепланируем следующий тик
                ReminderScheduler.scheduleNext(appContext)

                val s = container.settings.current()
                if (!s.setupComplete || !s.reminderEnabled) return@launch

                val tomorrow = LocalDate.now().plusDays(1)

                // 2) свежее расписание на завтра (максимум 9 с; при ошибке остаёмся на кэше)
                val type = s.targetType
                val id = s.targetId
                if (type != null && id != null) {
                    try {
                        withTimeoutOrNull(9_000) {
                            container.scheduleRepository.refreshWeek(
                                Target(type, id, s.targetName), tomorrow
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Обновление расписания перед напоминанием не удалось: ${e.message}")
                    }
                }

                // 3) на завтра есть пара? → напоминание (текст считает Notifications).
                // AL-12 (0.10.0): напоминание показывается ТОЛЬКО в движке «В часах» —
                // оно фолбэк «не забудьте поставить будильник», а в движке приложения
                // будильник ставится сам, и такое напоминание — дезинформация.
                val s2 = container.settings.current()
                if (s2.alarmTarget == AlarmTarget.CLOCK) {
                    val plan = container.planner.planFor(tomorrow)
                    if (plan != null) {
                        Notifications.reminder(appContext, plan)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Тик напоминания прерван: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
    }
}
