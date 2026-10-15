package ru.uniautoalarm.kgeu.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.uniautoalarm.kgeu.data.model.CustomEvent
import ru.uniautoalarm.kgeu.data.model.Lesson

/**
 * 0.9.0 (аудит, раздел 8.3): приоритетная тест-цель №1 — AlarmPlanner.
 * Чистая функция [AlarmPlanner.computePlan]: исключения точным названием
 * (включая приставку типа «л./пр./лаб.»), свои события со своим смещением,
 * spill через полночь, «кандидатов нет → null», лимит метки 60 символов.
 */
class AlarmPlannerTest {

    private val date: LocalDate = LocalDate.of(2026, 10, 20) // вторник

    private fun lesson(
        subject: String,
        start: String,
        room: String = "Д-301",
    ) = Lesson(
        date = date,
        start = LocalTime.parse(start),
        end = LocalTime.parse(start).plusHours(1).plusMinutes(30),
        subject = subject,
        teacher = "Иванов И.И.",
        room = room,
        kind = "",
        isReplacement = false,
        group = "гр. 14566"
    )

    private fun event(
        name: String,
        time: String,
        offset: Int = 45,
        day: DayOfWeek? = null,
        onDate: LocalDate? = null,
    ) = CustomEvent(
        name = name, dayOfWeek = day, date = onDate,
        time = LocalTime.parse(time), offsetMinutes = offset
    )

    // --- первая пара: общий офсет ---

    @Test
    fun `wake time is lesson start minus global offset`() {
        val plan = AlarmPlanner.computePlan(
            date, listOf(lesson("л. Физика", "08:30")), emptySet(), emptyList(), 90, emptyMap()
        )
        assertEquals(date.atTime(7, 0), plan!!.wakeAt)
        assertEquals("Авто: л. Физика (Д-301)", plan.label)
    }

    @Test
    fun `earliest lesson wins when several on the same day`() {
        val plan = AlarmPlanner.computePlan(
            date,
            listOf(lesson("пр. Химия", "12:00"), lesson("л. Физика", "08:30")),
            emptySet(), emptyList(), 60, emptyMap()
        )
        assertEquals(date.atTime(7, 30), plan!!.wakeAt)
        assertEquals("л. Физика", plan.lesson!!.subject)
    }

    // --- исключения: точное название с приставкой типа (Bug C / DATA-3) ---

    @Test
    fun `exception with type prefix excludes only that exact lesson type`() {
        val excluded = setOf("л. материаловедение") // уже нормализовано (нижний регистр)
        val plan = AlarmPlanner.computePlan(
            date,
            listOf(lesson("пр. материаловедение", "08:30"), lesson("л. Материаловедение", "10:00")),
            excluded, emptyList(), 60, emptyMap()
        )
        // лекция исключена, практика той же дисциплины — НЕТ
        assertEquals("пр. материаловедение", plan!!.lesson!!.subject)
    }

    @Test
    fun `exception without prefix does not match prefixed names`() {
        val excluded = setOf("материаловедение") // правило 0.6.0 (без типа)
        val plan = AlarmPlanner.computePlan(
            date, listOf(lesson("л. Материаловедение", "08:30")),
            excluded, emptyList(), 60, emptyMap()
        )
        // точная семантика 0.7.0+: безпрефиксное правило не матчится (регрессия DATA-3)
        assertEquals(date.atTime(7, 30), plan!!.wakeAt)
    }

    @Test
    fun `all lessons excluded means no plan`() {
        val excluded = setOf("л. физика")
        val plan = AlarmPlanner.computePlan(
            date, listOf(lesson("л. Физика", "08:30")), excluded, emptyList(), 60, emptyMap()
        )
        assertNull(plan)
    }

    // --- свои события ---

    @Test
    fun `weekly custom event creates candidate with own offset`() {
        val plan = AlarmPlanner.computePlan(
            date, emptyList(), emptySet(),
            listOf(event("Секция по плаванию", "19:00", offset = 45, day = DayOfWeek.TUESDAY)),
            60, emptyMap()
        )
        assertEquals(date.atTime(18, 15), plan!!.wakeAt)
        assertEquals("Авто: Секция по плаванию", plan.label)
    }

    @Test
    fun `dated custom event matches only its date`() {
        val plan = AlarmPlanner.computePlan(
            date, emptyList(), emptySet(),
            listOf(event("Экзамен", "09:00", offset = 120, onDate = date)),
            60, emptyMap()
        )
        assertEquals(date.atTime(7, 0), plan!!.wakeAt)
    }

    @Test
    fun `custom event on another weekday does not match`() {
        val plan = AlarmPlanner.computePlan(
            date, emptyList(), emptySet(),
            listOf(event("Секция", "19:00", day = DayOfWeek.WEDNESDAY)),
            60, emptyMap()
        )
        assertNull(plan)
    }

    // --- spill через полночь (AL-6: событие 00:30 с офсетом в прошлый день) ---

    @Test
    fun `spill wake time crosses midnight to previous day`() {
        val plan = AlarmPlanner.computePlan(
            date, emptyList(), emptySet(),
            listOf(event("Ночная смена", "00:30", offset = 45, onDate = date)),
            60, emptyMap()
        )
        // wakeAt = 00:30 − 45 мин = 23:45 ПРЕДЫДУЩЕГО дня
        assertEquals(date.minusDays(1).atTime(23, 45), plan!!.wakeAt)
    }

    // --- per-pair офсет ---

    @Test
    fun `per-pair offset overrides global offset`() {
        val offsets = mapOf(8 * 60 + 30 to 30) // пара в 08:30 → подъём за 30 мин
        val plan = AlarmPlanner.computePlan(
            date, listOf(lesson("л. Физика", "08:30")), emptySet(), emptyList(), 90, offsets
        )
        assertEquals(date.atTime(8, 0), plan!!.wakeAt)
    }

    // --- метка ---

    @Test
    fun `label truncated to 60 chars (EXTRA_MESSAGE limit)`() {
        val long = "л. " + "ОченьДлинноеНазвание".repeat(6)
        val plan = AlarmPlanner.computePlan(
            date, listOf(lesson(long, "08:30", room = "")), emptySet(), emptyList(), 60, emptyMap()
        )
        assertEquals(60, plan!!.label.length)
    }

    @Test
    fun `empty day gives null plan`() {
        assertNull(
            AlarmPlanner.computePlan(date, emptyList(), emptySet(), emptyList(), 60, emptyMap())
        )
    }
}
