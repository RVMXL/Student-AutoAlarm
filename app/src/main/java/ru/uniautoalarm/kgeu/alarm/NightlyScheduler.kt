package ru.uniautoalarm.kgeu.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import ru.uniautoalarm.kgeu.work.WorkScheduler

/**
 * Ночной планировщик: точные AlarmManager на 00:05 и на 01:00/02:00/03:00 ночи
 * (+ периодический sync WorkManager). В 00:05 NightlyAlarmReceiver обновляет
 * расписание на день и ставит/снимает будильник — до того, как агрессивные
 * планировщики ОС усыпят приложение.
 *
 * 0.8.0: второй точный тик в 06:00 — СТРАХОВКА ночной сверки: если 00:05 пропущено
 * (телефон был выключен, ОМ-оболочка придавила точный будильник), утренний тик всё
 * ещё успевает поставить будильник на первую пару дня.
 *
 * 0.12.0 (AL-23): страховка перенесена с 06:00 на 01:00/02:00/03:00 ночи. У живущих
 * далеко от корпуса подъём бывает в 05:00–06:00 (к паре в 08:00) — страховка 06:00
 * срабатывала уже ПОСЛЕ такого пробуждения, и пропущенная ночь оставалась без
 * будильника до самого звонка. Теперь сверка наверстывает упущенное ночью, пока
 * пользователь гарантированно ещё спит; три тика подряд (а не один) переживают даже
 * придавленный оболочкой одиночный тик. Сверка идемпотентна (маркер последнего
 * поставленного момента) — двойного будильника не будет ни при каком порядке
 * срабатывания. После перезагрузки все тики восстанавливает BootReceiver.
 */
object NightlyScheduler {

    const val NIGHT_HOUR = 0
    const val NIGHT_MINUTE = 5

    /**
     * Страховочные тики (0.12.0, AL-23): 01:00, 02:00 и 03:00 ночи — пока пользователь
     * ещё спит. Раньше был один тик в 06:00 — уже после ранних подъёмов (см. KDoc объекта).
     */
    val BACKUP_TICKS: List<Pair<Int, Int>> = listOf(1 to 0, 2 to 0, 3 to 0)

    private const val REQUEST_CODE_NIGHTLY = 42

    /** Коды страховочных тиков: 43/44/45 по индексу в [BACKUP_TICKS] (43 — наследие 0.8.0). */
    private const val REQUEST_CODE_BACKUP_BASE = 43

    // не const: TimeUnit.MINUTES.toMillis — вызов функции, const запрещён языком
    private val WINDOW_MS = TimeUnit.MINUTES.toMillis(10)

    /** Окно для не-точного режима — переиспользуется ReminderScheduler. */
    fun windowMs(): Long = WINDOW_MS

    /**
     * Ближайшее наступление HH:MM: сегодня, если время ещё не прошло, иначе завтра.
     * Чистая функция без часов — покрыта юнит-тестами [NightlyTickTest].
     */
    fun nextTick(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
        var next = now.toLocalDate().atTime(hour, minute)
        if (!now.isBefore(next)) next = next.plusDays(1) // время уже прошло — на завтра
        return next
    }

    /** Поставить/перепоставить все четыре тика и гарантировать периодический sync. Идемпотентно. */
    fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (am != null) {
            val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            scheduleTick(appContext, am, exactAllowed, REQUEST_CODE_NIGHTLY, NIGHT_HOUR, NIGHT_MINUTE)
            BACKUP_TICKS.forEachIndexed { i, (hour, minute) ->
                scheduleTick(
                    appContext, am, exactAllowed,
                    REQUEST_CODE_BACKUP_BASE + i, hour, minute
                )
            }
        }
        WorkScheduler.ensurePeriodic(appContext)
    }

    /** Один тик на ближайшее наступление HH:MM (если время уже прошло — на завтра). */
    private fun scheduleTick(
        appContext: Context,
        am: AlarmManager,
        exactAllowed: Boolean,
        requestCode: Int,
        hour: Int,
        minute: Int,
    ) {
        val next = nextTick(LocalDateTime.now(), hour, minute)

        val pending = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            Intent(appContext, NightlyAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val triggerAtMs = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
        } else {
            // Фолбэк: окно ±10 минут — теряем точность, но продолжаем работать
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAtMs, WINDOW_MS, pending)
        }
    }
}
