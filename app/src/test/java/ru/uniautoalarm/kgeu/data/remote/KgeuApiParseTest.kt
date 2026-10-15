package ru.uniautoalarm.kgeu.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.9.0 (аудит, раздел 8.3): парсер KgeuApi на golden-образцах живого API.
 * Ключевой контракт DATA-1: несуществующий idGroup → state:1 + rasp:[]
 * ШТАТНО, без ошибки — «пустой ответ ≠ успех-очистка» решается guard'ом
 * в ScheduleRepository, парсер обязан честно возвращать пустой список.
 */
class KgeuApiParseTest {

    /** Golden-образец реального ответа /api/Rasp (поля на русском, 40+ лишних полей). */
    private val goldenNormal = """
        {
          "data": {
            "isCyclicalSchedule": false,
            "rasp": [
              {
                "датаНачала": "2026-10-20T08:30:00",
                "датаОкончания": "2026-10-20T10:00:00",
                "дисциплина": "л. Материаловедение",
                "преподаватель": "Иванов И.И.",
                "аудитория": "Д-301",
                "день_недели": "Вторник",
                "замена": false,
                "группа": "гр. 14566",
                "номерЗанятия": 1,
                "этоЛишнееПолеСервера": 42,
                "иЕщёОдно": {"вложение": true}
              },
              {
                "датаНачала": "2026-10-20T12:00:00",
                "датаОкончания": "2026-10-20T13:30:00",
                "дисциплина": "пр. Материаловедение",
                "фиоПреподавателя": "Петров П.П.",
                "аудитория": "Д-302",
                "замена": true,
                "тема": "Замена пары"
              }
            ]
          },
          "state": 1,
          "msg": null,
          "лишнееПолеОбёртки": "ignored"
        }
    """.trimIndent()

    @Test
    fun `golden normal response parses both lessons with russian fields`() {
        val items = KgeuApi.parseRaspResponse(goldenNormal)
        assertEquals(2, items.size)
        assertEquals("л. Материаловедение", items[0].subject)
        assertEquals("2026-10-20T08:30:00", items[0].startDateTime)
        assertEquals(false, items[0].isReplacement)
        // замена + фиоПреподавателя
        assertTrue(items[1].isReplacement)
        assertEquals("Петров П.П.", items[1].teacherFull)
    }

    @Test
    fun `unknown fields are ignored (ignoreUnknownKeys)`() {
        val items = KgeuApi.parseRaspResponse(goldenNormal)
        assertEquals(2, items.size) // не упало на лишних полях
    }

    /**
     * DATA-1, живьём проверено: несуществующий/устаревший idGroup → state:1 + rasp:[]
     * — штатный ответ, НЕ ошибка.
     */
    @Test
    fun `empty rasp array is a valid success (DATA-1 contract)`() {
        val raw = """{"data":{"isCyclicalSchedule":false,"rasp":[]},"state":1,"msg":null}"""
        assertEquals(emptyList<KgeuRaspItem>(), KgeuApi.parseRaspResponse(raw))
    }

    @Test
    fun `null data yields empty list without error`() {
        val raw = """{"data":null,"state":1,"msg":null}"""
        assertEquals(emptyList<KgeuRaspItem>(), KgeuApi.parseRaspResponse(raw))
    }

    @Test
    fun `state 2 raises KgeuApiException with server message`() {
        val raw = """{"data":null,"state":2,"msg":"нет доступа"}"""
        try {
            KgeuApi.parseRaspResponse(raw)
            throw AssertionError("ожидалось KgeuApiException")
        } catch (e: KgeuApiException) {
            assertEquals("нет доступа", e.message)
        }
    }

    @Test
    fun `broken date string maps to null via parseIsoDateTime`() {
        assertEquals(null, KgeuApi.parseIsoDateTime(""))
        assertEquals(null, KgeuApi.parseIsoDateTime(null))
        assertEquals(null, KgeuApi.parseIsoDateTime("не-дата"))
        assertEquals(
            java.time.LocalDateTime.parse("2026-10-20T08:30:00"),
            KgeuApi.parseIsoDateTime("2026-10-20T08:30:00")
        )
    }
}
