package ru.uniautoalarm.kgeu.alarm

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.9.0 (аудит, разделы 8.2–8.3): правило доставки разового SET_ALARM —
 * чистая функция [StockAlarmSetter.deliveryDecision]. Именно этот класс
 * ошибок ловил релизы 0.7.1/0.8.0 («звенел не в день пары»).
 *
 * Правило 0.8.0: сегодня — разовый; завтра — только когда время подъёма
 * сегодня уже прошло (иначе одноразовый зазвенел бы СЕГОДНЯ); дальше — не ставим.
 */
class StockDeliveryTest {

    private val tuesday: LocalDateTime = LocalDateTime.parse("2026-10-20T18:00:00")

    @Test
    fun `lesson today later in the evening delivers`() {
        val wake = tuesday.toLocalDate().atTime(21, 0)
        assertEquals(Delivery.DELIVER, StockAlarmSetter.deliveryDecision(tuesday, wake))
    }

    @Test
    fun `tomorrow lesson with wake time not yet passed today defers to nightly check`() {
        // пара завтра в 08:30, сейчас сегодня 06:00 — время подъёма 08:30 ещё НЕ прошло:
        // одноразовый будильник на 08:30 зазвенел бы СЕГОДНЯ утром → до ночи
        val now = tuesday.withHour(6).withMinute(0)
        val wake = tuesday.plusDays(1).toLocalDate().atTime(8, 30)
        assertEquals(Delivery.DEFER_TO_NIGHT, StockAlarmSetter.deliveryDecision(now, wake))
    }

    @Test
    fun `tomorrow lesson late evening now delivers for tomorrow morning`() {
        // пара завтра в 08:30, сейчас 18:00 — время 08:30 сегодня уже прошло:
        // ближайшее наступление 08:30 — завтра, ставим сразу
        val wake = tuesday.plusDays(1).toLocalDate().atTime(8, 30)
        assertEquals(Delivery.DELIVER, StockAlarmSetter.deliveryDecision(tuesday, wake))
    }

    @Test
    fun `tomorrow lesson with wake time already passed today delivers`() {
        // пара завтра в 08:30, сейчас сегодня 23:50 — время подъёма сегодня прошло
        val now = tuesday.withHour(23).withMinute(50)
        val wake = now.plusDays(1).toLocalDate().atTime(8, 30)
        assertEquals(Delivery.DELIVER, StockAlarmSetter.deliveryDecision(now, wake))
    }

    @Test
    fun `wake time in the past is never delivered`() {
        val wake = tuesday.withHour(7).withMinute(0)
        assertEquals(Delivery.PAST, StockAlarmSetter.deliveryDecision(tuesday, wake))
    }

    @Test
    fun `exactly now is past`() {
        assertEquals(Delivery.PAST, StockAlarmSetter.deliveryDecision(tuesday, tuesday))
    }

    @Test
    fun `lesson after tomorrow is too far for SET_ALARM`() {
        val wake = tuesday.plusDays(2).toLocalDate().atTime(8, 30)
        assertEquals(Delivery.TOO_FAR, StockAlarmSetter.deliveryDecision(tuesday, wake))
    }

    @Test
    fun `a week ahead is too far`() {
        val wake = tuesday.plusDays(7).toLocalDate().atTime(8, 30)
        assertEquals(Delivery.TOO_FAR, StockAlarmSetter.deliveryDecision(tuesday, wake))
    }

    /**
     * AL-6 (P3, Wave 2): своё событие в 00:30 с офсетом ≥30 минут даёт wakeAt
     * вчера — сейчас документировано как PAST (постановка откладывается до
     * ночного тика), тест фиксирует поведение от регрессии.
     */
    @Test
    fun `spill wake at yesterday evening is past for today delivery`() {
        val wake = tuesday.minusDays(1).toLocalDate().atTime(23, 45)
        assertEquals(Delivery.PAST, StockAlarmSetter.deliveryDecision(tuesday, wake))
    }
}
