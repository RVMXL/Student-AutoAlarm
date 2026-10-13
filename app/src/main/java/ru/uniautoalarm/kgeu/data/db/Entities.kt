package ru.uniautoalarm.kgeu.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Кэш занятий расписания КГЭУ.
 * Время хранится числами (эпоха-день + минуты от 00:00) — дёшево сортировать и сравнивать.
 */
@Entity(
    tableName = "lessons",
    indices = [Index(value = ["targetKey", "dateEpochDay"])]
)
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val autoId: Long = 0,
    val targetKey: String,          // «g:<id>» — группа, «t:<id>» — преподаватель
    val dateEpochDay: Long,         // день занятия (LocalDate.toEpochDay)
    val startMinutes: Int,          // начало пары, минут от 00:00
    val endMinutes: Int,            // конец пары, минут от 00:00
    val subject: String,            // дисциплина (как в API, с префиксом «лек /пр /лаб »)
    val teacher: String,
    val room: String,               // аудитория
    val kind: String,               // тип занятия: «лек»/«пр»/«лаб»/… (префикс дисциплины)
    val isReplacement: Boolean,     // «замена:true» в API
    val group: String               // группа (актуально в расписании преподавателя)
)

/** Своё занятие (секция) с собственным смещением будильника. */
@Entity(tableName = "custom_events")
data class CustomEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dayOfWeekValue: Int? = null, // 1..7 = java.time.DayOfWeek.getValue(), либо null
    val dateEpochDay: Long? = null,  // разовое событие (день), либо null
    val startMinutes: Int,           // время события, минут от 00:00
    val offsetMinutes: Int,          // за сколько минут до события будить
    val enabled: Boolean = true
)

/** Занятие-исключение: перед ним будильник не ставится. */
@Entity(tableName = "exceptions")
data class ExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,             // сопоставляется по нормализованному названию дисциплины
    val enabled: Boolean = true
)
