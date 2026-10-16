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
 * Сопоставление — ТОЧНОЕ по полному названию пары, вместе с приставкой типа
 * ("л. материаловедение" ≠ "пр. материаловедение"): тип уже написан в названии,
 * отдельного поля типа у правила нет.
 */
data class ExceptionRule(val id: Long = 0, val subject: String, val enabled: Boolean = true)

/**
 * Куда ставится будильник:
 *  CLOCK — в стоковые часы через ACTION_SET_ALARM (привычный сигнал, но оболочки
 *  вроде ColorOS/MIUI могут молча блокировать команды постановки и снятия);
 *  APP — собственный будильник приложения (AlarmManager.setAlarmClock + полноэкранный
 *  звонок): тумблер, исключения и снятие работают без капризов оболочки.
 */
enum class AlarmTarget { CLOCK, APP }

/** Тема приложения. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Стиль экрана расписания. */
enum class CalendarStyle { STANDARD, PAPER }

/**
 * Полный снимок настроек (хранится в DataStore, см. SettingsRepository).
 *
 * 0.8.0: логика авто-будильника ОДНА для всех — «одна ночь вперёд». Тумблер ставит
 * будильник максимум на завтра; ночная сверка 00:05 (+страховки 01:00–03:00 ночи) каждый день
 * ставит/снимает по факту. Режима «на несколько дней вперёд» (партия недельных
 * будильников «Авто: Пн…Вс») больше нет — он противоречил этой логике и оставлял
 * будильники на день недели, которые приходилось выключать руками.
 */
data class Settings(val setupComplete: Boolean = false,
    val targetType: TargetType? = null, val targetId: Int? = null, val targetName: String = "",
    val wakeOffsetMinutes: Int = 90, val autoAlarmEnabled: Boolean = true,
    val semesterMin: LocalDate? = null, val semesterMax: LocalDate? = null,
    val lastFullSyncEpochMs: Long = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = true,
    val accentIndex: Int = 0, val calendarStyle: CalendarStyle = CalendarStyle.STANDARD,
    val backgroundImagePath: String? = null, val backgroundOpacity: Float = 0.35f,
    /** Напоминание о будильнике: своё уведомление — фолбэк, когда стоковые часы не принимают SET_ALARM. */
    val reminderEnabled: Boolean = false, val reminderTime: LocalTime = LocalTime.of(21, 0),
    /** Открывать экран часов при постановке (SKIP_UI=false) — для ColorOS/Realme UI,
     *  где фоновая постановка с SKIP_UI=true молча блокируется; пользователь подтверждает тапом. */
    val alarmShowClockUi: Boolean = false,
    /** Куда ставится будильник: в стоковые часы или в приложение. С 0.7.1 по умолчанию —
     *  приложение: SET_ALARM часов не умеет «на дату» (звонил завтра под пару послезавтра),
     *  а DISMISS на части оболочек молча игнорируется (тумблер не снимал будильники). */
    val alarmTarget: AlarmTarget = AlarmTarget.APP,
    /** Свой подъём для каждой первой пары: ключ — минуты от 00:00 начала пары,
     *  значение — запас на сборы. Пары без записи используют общий wakeOffsetMinutes. */
    val firstLessonOffsets: Map<Int, Int> = emptyMap(),
    /** Эффект стекла: полупрозрачные карточки + размытие фоновой картинки. */
    val glassEnabled: Boolean = false, val glassIntensity: Float = 0.5f,
    /** Эпоха-минута (день*1440 + минуты) последнего поставленного разового будильника —
     *  дедупликация: мгновенная постановка при включении тумблера не дублирует
     *  ночную сверку 00:05, а сверка не снимает живой будильник. */
    val lastScheduledWakeEpochMinute: Long = -1,
    /** Метка последнего поставленного разового будильника — под DISMISS_ALARM по метке
     *  при выключении тумблера (метки «Авто: …» ищутся в часах точным совпадением). */
    val lastScheduledLabel: String = "")

/** План пробуждения на дату: победивший кандидат (пара или своё событие). */
data class AlarmPlan(val date: LocalDate, val wakeAt: LocalDateTime,
    val lesson: Lesson?, val customEvent: CustomEvent?, val label: String)

/** Состояние синхронизации расписания для UI. */
data class SyncInfo(val lastSyncEpochMs: Long = 0, val lastError: String? = null,
    val lastSyncWeekCount: Int = 0, val busy: Boolean = false)
