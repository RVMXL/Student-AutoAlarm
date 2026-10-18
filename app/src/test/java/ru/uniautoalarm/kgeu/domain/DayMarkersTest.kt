package ru.uniautoalarm.kgeu.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.uniautoalarm.kgeu.data.model.ExceptionRule
import ru.uniautoalarm.kgeu.data.model.Lesson

/**
 * 0.14.0: маркеры дней в расписании — считаем «обозначенные (исключённые) секции»
 * дня: РАЗЛИЧНЫЕ дисциплины, попавшие под активные правила (нормализация как в
 * isExceptional: регистр/пробелы игнорируются, правило — точное полное название).
 * 0.20.0: точки дней = числу ПАР — уникальные слоты времени (start, end):
 * лабы двух подгрупп в одно и то же время (разные преподаватели) — ОДНА пара.
 */
class DayMarkersTest {

    private fun lesson(subject: String) = Lesson(
        date = LocalDate.of(2026, 10, 20),
        start = LocalTime.of(8, 30), end = LocalTime.of(10, 0),
        subject = subject, teacher = "", room = "",
        kind = "", isReplacement = false, group = ""
    )

    private fun lessonAt(
        startH: Int, startM: Int, endH: Int, endM: Int,
        subject: String = "пр. Физика", teacher: String = ""
    ) = Lesson(
        date = LocalDate.of(2026, 10, 20),
        start = LocalTime.of(startH, startM), end = LocalTime.of(endH, endM),
        subject = subject, teacher = teacher, room = "",
        kind = "", isReplacement = false, group = ""
    )

    @Test
    fun `empty day has zero marked`() {
        assertEquals(0, DayMarkers.countMarked(emptyList(), emptyList()))
    }

    @Test
    fun `no exceptions means zero marked`() {
        val lessons = listOf(lesson("л. материаловедение"), lesson("пр. физика"))
        assertEquals(0, DayMarkers.countMarked(lessons, emptyList()))
    }

    @Test
    fun `single exception counts once even in two lessons`() {
        val lessons = listOf(lesson("л. Математика"), lesson("пр. Математика"), lesson("пр. Физика"))
        val rules = listOf(ExceptionRule(subject = "л. математика"))
        // одна РАЗЛИЧНая дисциплина (математика) — счётчик 1, а не 2
        assertEquals(1, DayMarkers.countMarked(lessons, rules))
    }

    @Test
    fun `distinct subjects are counted separately`() {
        val lessons = listOf(
            lesson("л. Математика"), lesson("пр. Физика"), lesson("пр. Химия")
        )
        // правила — точные полные названия (с приставкой типа), как их создаёт тумблер
        val rules = listOf(
            ExceptionRule(subject = "л. Математика"),
            ExceptionRule(subject = "пр. Физика")
        )
        assertEquals(2, DayMarkers.countMarked(lessons, rules))
    }

    @Test
    fun `disabled rules are ignored`() {
        val lessons = listOf(lesson("пр. Физика"))
        val rules = listOf(ExceptionRule(subject = "физика", enabled = false))
        assertEquals(0, DayMarkers.countMarked(lessons, rules))
    }

    @Test
    fun `match is normalized like isExceptional`() {
        val lessons = listOf(lesson("л.  Материаловедение  "))
        val rules = listOf(ExceptionRule(subject = "Л. материаловедение"))
        // регистр и двойные пробелы не мешают совпадению
        assertEquals(1, DayMarkers.countMarked(lessons, rules))
    }

    @Test
    fun `kind prefix distinguishes rules`() {
        val lessons = listOf(lesson("пр. Математика"))
        val rules = listOf(ExceptionRule(subject = "л. математика"))
        // «пр. математика» != «л. математика» — точное совпадение с приставкой типа
        assertEquals(0, DayMarkers.countMarked(lessons, rules))
    }

    @Test
    fun `empty day has zero pairs`() {
        assertEquals(0, DayMarkers.countPairs(emptyList()))
    }

    @Test
    fun `subgroup labs in the same slot count as one pair`() {
        // 0.20.0: две записи расписания в ОДНО время (лабы двух подгрупп,
        // разные преподаватели/кабинеты) — одна пара, одна точка
        val lessons = listOf(
            lessonAt(8, 30, 10, 0, "л. Химия", "Иванова А.А."),
            lessonAt(8, 30, 10, 0, "л. Химия", "Петров Б.Б.")
        )
        assertEquals(1, DayMarkers.countPairs(lessons))
    }

    @Test
    fun `different slots count separately`() {
        val lessons = listOf(
            lessonAt(8, 30, 10, 0),
            lessonAt(10, 10, 11, 40),
            lessonAt(12, 0, 13, 30)
        )
        assertEquals(3, DayMarkers.countPairs(lessons))
    }

    @Test
    fun `same start with different end is a separate slot`() {
        val lessons = listOf(
            lessonAt(8, 30, 10, 0),
            lessonAt(8, 30, 9, 50)
        )
        assertEquals(2, DayMarkers.countPairs(lessons))
    }
}
