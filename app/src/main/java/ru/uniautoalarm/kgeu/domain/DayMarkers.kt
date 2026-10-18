package ru.uniautoalarm.kgeu.domain

import ru.uniautoalarm.kgeu.data.model.ExceptionRule
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.data.repository.ScheduleRepository

/**
 * Маркеры дней в расписании (0.14.0): чип дня показывает, есть ли в этот день пары
 * и сколько ДРУГИХ обозначенных (исключённых из будильника) дисциплин приходится
 * на этот день. Чистая логика — покрыта юнит-тестом DayMarkersTest.
 */
object DayMarkers {

    /**
     * Число ПАР дня (0.20.0) — по уникальным слотам времени (start, end).
     * Лабораторные двух подгрупп в одно и то же время приходят из расписания
     * как ДВЕ записи (разные преподаватели/кабинеты) — точка в календаре
     * показывает это как ОДНУ пару, поэтому считаем разные (start, end),
     * не различая предмет/преподавателя/кабинет. Чистая логика — покрыта
     * юнит-тестом DayMarkersTest.
     */
    fun countPairs(lessons: List<Lesson>): Int =
        lessons.map { it.start to it.end }.distinct().size

    /**
     * Число РАЗЛИЧНЫХ дисциплин дня, попавших под активные исключения
     * (правило привязано к точному полному названию пары — сравнение через
     * [ScheduleRepository.normalizeForMatch], как в isExceptional).
     */
    fun countMarked(lessons: List<Lesson>, exceptions: List<ExceptionRule>): Int =
        lessons.map { it.subject }
            .filter { subject ->
                exceptions.any {
                    it.enabled &&
                        ScheduleRepository.normalizeForMatch(it.subject) ==
                        ScheduleRepository.normalizeForMatch(subject)
                }
            }
            .distinct()
            .size
}
