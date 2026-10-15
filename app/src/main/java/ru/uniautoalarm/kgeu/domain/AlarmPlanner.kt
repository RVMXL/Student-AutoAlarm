package ru.uniautoalarm.kgeu.domain

import java.time.DayOfWeek
import java.time.LocalDate
import ru.uniautoalarm.kgeu.data.db.CustomEventDao
import ru.uniautoalarm.kgeu.data.db.CustomEventEntity
import ru.uniautoalarm.kgeu.data.db.ExceptionDao
import ru.uniautoalarm.kgeu.data.model.AlarmPlan
import ru.uniautoalarm.kgeu.data.model.CustomEvent
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.data.repository.ScheduleRepository
import ru.uniautoalarm.kgeu.data.repository.SettingsRepository

/**
 * Планировщик будильника: собирает кандидатов на пробуждение на дату и выбирает самый ранний.
 *
 * Кандидаты:
 *  1) первая НЕ исключённая пара дня (start − wakeOffsetMinutes из настроек);
 *  2) свои события этого дня (time − их offsetMinutes).
 *
 * Кандидатов нет (пар нет или все исключены, событий нет) → planFor вернёт null.
 */
class AlarmPlanner(
    private val settings: SettingsRepository,
    private val schedule: ScheduleRepository,
    private val customEventDao: CustomEventDao,
    private val exceptionDao: ExceptionDao,
) {

    /** План на дату из кэша расписания. */
    suspend fun planFor(date: LocalDate): AlarmPlan? =
        planFor(date, schedule.lessonsOn(date))

    /** План на дату по готовому списку пар (например, сразу после обновления недели). */
    suspend fun planFor(date: LocalDate, lessons: List<Lesson>): AlarmPlan? {
        val s = settings.current()

        // (1) исключения: без типа — дисциплина целиком (нормализованное название);
        //     с типом («лек»/«пр»/«лаб») — только пары этого типа той же дисциплины
        //     (студенты нередко ходят на практику, но пропускают лекции)
        val rules = exceptionDao.allOnce().filter { it.enabled }
        val excludedAll = rules
            .filter { it.kind == null }
            .mapTo(HashSet()) { ScheduleRepository.normalizeSubject(it.subject) }
        val excludedTyped = rules
            .filter { it.kind != null }
            .map { ScheduleRepository.normalizeSubject(it.subject) to it.kind!! }

        // (2) первая не-исключённая пара дня
        val first = lessons
            .filter { lesson ->
                val normalized = ScheduleRepository.normalizeSubject(lesson.subject)
                if (normalized in excludedAll) return@filter false
                excludedTyped.none { (subject, kind) -> subject == normalized && kind == lesson.kind }
            }
            .minByOrNull { it.start }

        // (3) свои события, попадающие на дату: по дню недели ИЛИ по конкретной дате
        val events = customEventDao.allOnce().filter { e ->
            e.enabled && (
                (e.dayOfWeekValue != null && e.dayOfWeekValue == date.dayOfWeek.value) ||
                    (e.dateEpochDay != null && e.dateEpochDay == date.toEpochDay())
                )
        }

        // (4) кандидаты на пробуждение
        val candidates = buildList {
            if (first != null) {
                // Свой запас на сборы для этого времени первой пары (см. «Подъём под каждую пару»);
                // записи нет — общий wakeOffsetMinutes.
                val startMinutes = first.start.hour * 60 + first.start.minute
                val offset = s.firstLessonOffsets[startMinutes] ?: s.wakeOffsetMinutes
                val wakeAt = date.atTime(first.start).minusMinutes(offset.toLong())
                add(AlarmPlan(date, wakeAt, first, null, lessonLabel(first)))
            }
            for (e in events) {
                val wakeAt = date.atTime(ScheduleRepository.minutesToTime(e.startMinutes))
                    .minusMinutes(e.offsetMinutes.toLong())
                add(AlarmPlan(date, wakeAt, null, e.toModel(), "Авто: ${e.name}"))
            }
        }

        // (5) самый ранний кандидат; метка обрезается до 60 символов (лимит EXTRA_MESSAGE)
        return candidates.minByOrNull { it.wakeAt }?.let { it.copy(label = it.label.take(60)) }
    }

    // --- Приватные помощники ---

    private fun lessonLabel(lesson: Lesson): String =
        if (lesson.room.isBlank()) "Авто: ${lesson.subject}"
        else "Авто: ${lesson.subject} (${lesson.room})"

    private fun CustomEventEntity.toModel(): CustomEvent = CustomEvent(
        id = id,
        name = name,
        dayOfWeek = dayOfWeekValue?.let { DayOfWeek.of(it) },
        date = dateEpochDay?.let { LocalDate.ofEpochDay(it) },
        time = ScheduleRepository.minutesToTime(startMinutes),
        offsetMinutes = offsetMinutes,
        enabled = enabled
    )
}
