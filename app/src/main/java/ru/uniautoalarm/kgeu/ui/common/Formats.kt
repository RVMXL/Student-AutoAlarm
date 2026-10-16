package ru.uniautoalarm.kgeu.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.uniautoalarm.kgeu.R

/**
 * Формат «HH:mm», единый для всех экранов. ФУНКЦИЯ, а не val-синглтон (SET-4/UI-5,
 * 0.10.0): статический форматтер захватывал локаль первого использования и после
 * смены языка продолжал ей пользоваться (у арабского — старые цифры). Локаль
 * берётся на момент вызова: attachBaseContext уже обернул контекст в язык приложения.
 */
fun timeFormat(): DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

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
