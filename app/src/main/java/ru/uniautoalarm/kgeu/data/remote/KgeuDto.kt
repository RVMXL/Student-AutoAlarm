package ru.uniautoalarm.kgeu.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO публичного JSON-API КГЭУ (kabinet.kgeu.ru, платформа DSTU WebApp).
 * Контракт: docs/research/kgeu-api.md — поля ответов на русском, успех = state:1,
 * лишние поля сервера игнорируются (ignoreUnknownKeys).
 *
 * Обёртки сделаны отдельными классами: поле data бывает массивом (списки)
 * и объектом (расписание, даты семестра).
 */

/** Обёртка со списком: {"data":[...],"state":1,"msg":null}. */
@Serializable
data class KgeuListEnvelope<T>(val data: List<T> = emptyList(), val state: Int = 0, val msg: String? = null)

/** Обёртка с объектом: {"data":{...},"state":1,"msg":null}. */
@Serializable
data class KgeuObjectEnvelope<T>(val data: T? = null, val state: Int = 0, val msg: String? = null)

/** Элемент /api/raspGrouplist. */
@Serializable
data class KgeuGroup(val id: Int, val name: String, val kurs: Int? = null, val facul: String? = null, val yearName: String? = null)

/** Элемент /api/raspTeacherlist. */
@Serializable
data class KgeuTeacher(val id: Int, val name: String, val kaf: String? = null)

/** data /api/GetRaspDates: границы семестра и даты, когда есть занятия. */
@Serializable
data class KgeuDatesData(val minDate: String? = null, val maxDate: String? = null, val dates: List<String> = emptyList())

/** data /api/Rasp. */
@Serializable
data class KgeuRaspData(val isCyclicalSchedule: Boolean? = null, val rasp: List<KgeuRaspItem> = emptyList())

/**
 * Запись расписания. Сервер сам разворачивает числитель/знаменатель в конкретные даты:
 * датаНачала/датаОкончания — «2026-10-12T08:00:00» (МСК, без смещения в строке).
 */
@Serializable
data class KgeuRaspItem(
    @SerialName("датаНачала") val startDateTime: String? = null,
    @SerialName("датаОкончания") val endDateTime: String? = null,
    @SerialName("начало") val startTime: String? = null,
    @SerialName("конец") val endTime: String? = null,
    @SerialName("дисциплина") val subject: String? = null,
    @SerialName("преподаватель") val teacher: String? = null,
    @SerialName("фиоПреподавателя") val teacherFull: String? = null,
    @SerialName("аудитория") val room: String? = null,
    @SerialName("день_недели") val dayName: String? = null,
    @SerialName("замена") val isReplacement: Boolean = false,
    @SerialName("группа") val group: String? = null,
    @SerialName("тема") val topic: String? = null,
    @SerialName("номерЗанятия") val lessonNumber: Int? = null
)
