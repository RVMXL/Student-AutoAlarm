package ru.uniautoalarm.kgeu.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import ru.uniautoalarm.kgeu.R

/**
 * Формат времени, УВАЖАЮЩИЙ локаль (SET-14, 0.11.0): раньше «HH:mm» был 24-часовым
 * во всех 15 локалях — для en/fr это было неверно (12-часовой формат с AM/PM).
 * ofLocalizedTime(SHORT) берёт привычный локали формат (и 12/24h, и разделитель);
 * локаль — на момент вызова (SET-4/UI-5: attachBaseContext уже обернул контекст).
 */
fun timeFormat(): DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/**
 * Короткая дата «14 окт.» с ЛОКАЛИЗОВАННЫМ месяцем (SET-14): раньше было жёсткое
 * «dd.MM» — русская конвенция во всех локалях. Аббревиатура месяца берётся из
 * локали (арабский/иероглифы — корректно через ICU), цифры — локальные.
 */
fun shortDate(): DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/**
 * Локализованная «длинная» дата («вторник, 14 октября») — тоже функция по той же
 * причине, что и [timeFormat] (UI-5).
 */
fun longDate(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

/**
 * Локализованное «N ч M мин» (смещение подъёма): 90 → «1 ч 30 мин»,
 * 60 → «1 ч», 45 → «45 мин». Раньше формат был зашит по-русски.
 */
@Composable
fun formatOffset(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> stringResource(R.string.fmt_offset_hm, hours, rest)
        hours > 0 -> stringResource(R.string.fmt_offset_h, hours)
        else -> stringResource(R.string.fmt_offset_m, rest)
    }
}

/**
 * Короткое локализованное имя дня недели для чипов календаря: Пн, Вт, …
 * (латиница/арабица/иероглифы — по языку интерфейса).
 */
@Composable
fun dayShortName(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.day_short_1
        DayOfWeek.TUESDAY -> R.string.day_short_2
        DayOfWeek.WEDNESDAY -> R.string.day_short_3
        DayOfWeek.THURSDAY -> R.string.day_short_4
        DayOfWeek.FRIDAY -> R.string.day_short_5
        DayOfWeek.SATURDAY -> R.string.day_short_6
        DayOfWeek.SUNDAY -> R.string.day_short_7
    }
)

/** Время будильника для примеров-подписей: первая пара условно в 08:30. */
fun exampleWakeTime(offsetMinutes: Int): LocalTime =
    LocalTime.of(8, 30).minusMinutes(offsetMinutes.toLong())

/** «08:30» из LocalTime — без локали, часы всегда двузначные. */
fun LocalTime.hhmm(): String = "%02d:%02d".format(hour, minute)

/**
 * Bidi-изолят сегмента (SET-14): FSI…PDI вокруг вставки в смешанное направление.
 * Составная строка «день 09:00 · за 45 мин» при арабском интерфейсе без изолятов
 * рендерилась с перепутанным порядком сегментов (двоеточия/дефисы «плавали»).
 * На LTR-локалях изоляты невидимы и безвредны.
 */
fun bidiSegment(s: String): String = "\u2068$s\u2069"
