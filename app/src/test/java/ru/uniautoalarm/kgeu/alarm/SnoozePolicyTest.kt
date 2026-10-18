package ru.uniautoalarm.kgeu.alarm

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.22.0: отложенный звонок «Отложить» — фиксация контракта [SnoozePolicy]:
 *  1) повторный звонок ровно через [SnoozePolicy.MINUTES] минут (по умолчанию 5);
 *  2) маркер постановки кодируется как в AlarmGateway (epochDay*1440 + h*60 + m)
 *     и хранит только минутное разрешение — секунды/миллисекунды отбрасываются
 *     (иначе карточка «Мои будильники» и ночная сверка сравнивают минуты
 *     с точностью до секунды и «разъезжаются» с фактическим слотом).
 */
class SnoozePolicyTest {

    @Test
    fun `snooze is exactly five minutes by default`() {
        val nowMs = 1_000_000L
        assertEquals(nowMs + 5 * 60_000L, SnoozePolicy.atMs(nowMs))
        assertEquals(SnoozePolicy.MINUTES, 5)
    }

    @Test
    fun `snooze honors custom minutes`() {
        assertEquals(600_000L, SnoozePolicy.atMs(0L, 10))
        assertEquals(60_000L, SnoozePolicy.atMs(0L, 1))
    }

    @Test
    fun `marker minute truncates seconds and matches gateway encoding`() {
        val zone = ZoneOffset.UTC
        val wake = LocalDateTime.of(2026, 10, 20, 7, 5, 43, 500_000_000)
        val atMs = wake.toInstant(ZoneOffset.UTC).toEpochMilli()

        val expected = LocalDate.of(2026, 10, 20).toEpochDay() * 1440L + 7L * 60 + 5L
        assertEquals(expected, SnoozePolicy.markerMinute(atMs, zone))

        // та же минута без секунд — маркер идентичен (минутное разрешение)
        val sameMinuteNoSeconds = LocalDateTime.of(2026, 10, 20, 7, 5, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, SnoozePolicy.markerMinute(sameMinuteNoSeconds, zone))
    }

    @Test
    fun `marker minute crosses midnight into next day`() {
        val zone = ZoneId.of("Europe/Moscow")
        // 23:59:59 локального дня → маркер последней минуты этого дня
        val late = LocalDateTime.of(2026, 10, 20, 23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        val expectedDay = LocalDate.of(2026, 10, 20).toEpochDay()
        assertEquals(expectedDay * 1440L + 23L * 60 + 59L, SnoozePolicy.markerMinute(late, zone))
    }
}
