package ru.uniautoalarm.kgeu.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Цель расписания: студент (группа) или преподаватель. */
enum class TargetType { STUDENT, TEACHER }

/** Выбранная цель: тип + код в БД расписания КГЭУ + человекочитаемое имя. */
data class Target(val type: TargetType, val id: Int, val name: String)

/** Занятие расписания (доменная модель, уже развёрнутая в конкретную дату/время). */
data class Lesson(val date: LocalDate, val start: LocalTime, val end: LocalTime,
    val subject: String, val teacher: String, val room: String,
    val kind: String, val isReplacement: Boolean, val group: String)

/**
 * Своё занятие (секция) с собственным смещением будильника.
 * Либо по дню недели (еженедельное), либо на конкретную дату (разовое).
 */
data class CustomEvent(val id: Long = 0, val name: String,
    val dayOfWeek: DayOfWeek? = null, val date: LocalDate? = null,
    val time: LocalTime, val offsetMinutes: Int, val enabled: Boolean = true)

/**
 * Занятие-исключение: перед ним будильник не ставится.
 * [kind] — тип пары ("лек"/"пр"/"лаб" как в расписании КГЭУ) или null = все типы:
 * студенты нередко ходят на практику, но пропускают лекции той же дисциплины.
 */
data class ExceptionRule(val id: Long = 0, val subject: String,
    val kind: String? = null, val enabled: Boolean = true)

/** Режим авто-будильника: на завтра (Standard) или партия на неделю вперёд. */
enum class AlarmMode { STANDARD, WEEK_AHEAD }

/** Тема приложения. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Стиль экрана расписания. */
enum class CalendarStyle { STANDARD, PAPER }

/** Полный снимок настроек (хранится в DataStore, см. SettingsRepository). */
data class Settings(val setupComplete: Boolean = false,
    val targetType: TargetType? = null, val targetId: Int? = null, val targetName: String = "",
    val wakeOffsetMinutes: Int = 90, val autoAlarmEnabled: Boolean = true,
    val mode: AlarmMode = AlarmMode.STANDARD, val weekAheadDays: Int = 7,
    val semesterMin: LocalDate? = null, val semesterMax: LocalDate? = null,
    val lastFullSyncEpochMs: Long = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = true,
    val accentIndex: Int = 0, val calendarStyle: CalendarStyle = CalendarStyle.STANDARD,
    val backgroundImagePath: String? = null, val backgroundOpacity: Float = 0.35f,
    val weekAlarmsSetUntilEpochDay: Long = -1,
    /** Напоминание о будильнике: своё уведомление — фолбэк, когда стоковые часы не принимают SET_ALARM. */
    val reminderEnabled: Boolean = false, val reminderTime: LocalTime = LocalTime.of(21, 0),
    /** Открывать экран часов при постановке (SKIP_UI=false) — для ColorOS/Realme UI,
     *  где фоновая постановка с SKIP_UI=true молча блокируется; пользователь подтверждает тапом. */
    val alarmShowClockUi: Boolean = false,
    /** Свой подъём для каждой первой пары: ключ — минуты от 00:00 начала пары,
     *  значение — запас на сборы. Пары без записи используют общий wakeOffsetMinutes. */
    val firstLessonOffsets: Map<Int, Int> = emptyMap(),
    /** Эффект стекла: полупрозрачные карточки + размытие фоновой картинки. */
    val glassEnabled: Boolean = false, val glassIntensity: Float = 0.5f,
    /** Сигнатура партии «на несколько дней вперёд» («день недели:время подъёма»):
     *  дедупликация — SET_ALARM не умеет читать/удалять уже стоящие будильники. */
    val weekAlarmsSignature: String = "",
    /** Эпоха-минута (день*1440 + минуты) последнего поставленного разового будильника —
     *  чтобы мгновенная постановка при включении тумблера не дублировала ночной тик 00:05. */
    val lastScheduledWakeEpochMinute: Long = -1,
    /** Метка последнего поставленного разового будильника — под DISMISS_ALARM по метке
     *  при выключении тумблера (метки «Авто: …» ищутся в часах точным совпадением). */
    val lastScheduledLabel: String = "")

/** План пробуждения на дату: победивший кандидат (пара или своё событие). */
data class AlarmPlan(val date: LocalDate, val wakeAt: LocalDateTime,
    val lesson: Lesson?, val customEvent: CustomEvent?, val label: String)

/** Итог постановки партии «на несколько дней вперёд».
 *  delivered — сколько повторяющихся будильников реально передано часам;
 *  alreadyUpToDate — партия уже стоит, расписание не меняло времена подъёма. */
data class WeekAheadResult(val delivered: Int, val alreadyUpToDate: Boolean)

/** Состояние синхронизации расписания для UI. */
data class SyncInfo(val lastSyncEpochMs: Long = 0, val lastError: String? = null,
    val lastSyncWeekCount: Int = 0, val busy: Boolean = false)
