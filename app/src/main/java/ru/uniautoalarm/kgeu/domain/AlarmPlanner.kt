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
 * Исключения сопоставляются ТОЧНО по полному названию пары, вместе с приставкой типа:
 * правило «л. материаловедение» исключает только лекцию, «пр. материаловедение» —
 * только практику (тип уже написан в названии пары; отдельные «тумблеры типов» убраны).
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

        // (1) исключения: точные полные названия (с приставкой «лек /пр /лаб »)
        val excluded = exceptionDao.allOnce()
            .filter { it.enabled }
            .mapTo(HashSet()) { ScheduleRepository.normalizeForMatch(it.subject) }

        // (3) свои события, попадающие на дату: по дню недели ИЛИ по конкретной дате
        val events = customEventDao.allOnce().map { it.toModel() }

        return computePlan(
            date = date,
            lessons = lessons,
            excludedNormalized = excluded,
            events = events,
            wakeOffsetMinutes = s.wakeOffsetMinutes,
            firstLessonOffsets = s.firstLessonOffsets,
        )
    }

    // --- Приватные помощники ---

    private fun CustomEventEntity.toModel(): CustomEvent = CustomEvent(
        id = id,
        name = name,
        dayOfWeek = dayOfWeekValue?.let { DayOfWeek.of(it) },
        date = dateEpochDay?.let { LocalDate.ofEpochDay(it) },
        time = ScheduleRepository.minutesToTime(startMinutes),
        offsetMinutes = offsetMinutes,
        enabled = enabled
    )

    companion object {

        /**
         * Чистая функция выбора плана — вся доменная логика без DAO/DataStore/Android:
         * покрывается юнит-тестами (0.9.0: исключения точным названием, свои события
         * со своим смещением, spill через полночь, «кандидатов нет → null»).
         */
        fun computePlan(
            date: LocalDate,
            lessons: List<Lesson>,
            excludedNormalized: Set<String>,
            events: List<CustomEvent>,
            wakeOffsetMinutes: Int,
            firstLessonOffsets: Map<Int, Int>,
        ): AlarmPlan? {
            // (2) первая не-исключённая пара дня
            val first = lessons
                .filter { ScheduleRepository.normalizeForMatch(it.subject) !in excludedNormalized }
                .minByOrNull { it.start }

            // (3) свои события, попадающие на дату: по дню недели ИЛИ по конкретной дате
            val dayEvents = events.filter { e ->
                e.enabled && (
                    (e.dayOfWeek != null && e.dayOfWeek == date.dayOfWeek) ||
                        (e.date != null && e.date == date)
                    )
            }

            // (4) кандидаты на пробуждение
            val candidates = buildList {
                if (first != null) {
                    // Свой запас на сборы для этого времени первой пары (см. «Подъём под каждую пару»);
                    // записи нет — общий wakeOffsetMinutes.
                    val startMinutes = first.start.hour * 60 + first.start.minute
                    val offset = firstLessonOffsets[startMinutes] ?: wakeOffsetMinutes
                    val wakeAt = date.atTime(first.start).minusMinutes(offset.toLong())
                    add(AlarmPlan(date, wakeAt, first, null, lessonLabel(first)))
                }
                for (e in dayEvents) {
                    val wakeAt = date.atTime(e.time).minusMinutes(e.offsetMinutes.toLong())
                    add(AlarmPlan(date, wakeAt, null, e, "Авто: ${e.name}"))
                }
            }

            // (5) самый ранний кандидат; метка обрезается до 60 символов (лимит EXTRA_MESSAGE)
            return candidates.minByOrNull { it.wakeAt }?.let { it.copy(label = it.label.take(60)) }
        }

        private fun lessonLabel(lesson: Lesson): String =
            if (lesson.room.isBlank()) "Авто: ${lesson.subject}"
            else "Авто: ${lesson.subject} (${lesson.room})"
    }
}
