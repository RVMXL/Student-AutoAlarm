package ru.uniautoalarm.kgeu.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ChevronLeft
import androidx.compose.material.icons.automirrored.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.data.model.CalendarStyle
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.ui.common.LocalAppContainer
import ru.uniautoalarm.kgeu.ui.theme.PaperColors

private val CardShape = RoundedCornerShape(20.dp)
private val ChipShape = RoundedCornerShape(8.dp)

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val RU_MONTH_DAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale("ru", "RU"))

/** Бумажная палитра как Color (константы PaperColors — Long). */
private val PaperBackground = Color(PaperColors.paperBackground)
private val PaperCard = Color(PaperColors.paperCard)
private val PaperInk = Color(PaperColors.paperInk)
private val PaperInkSoft = Color(PaperColors.paperInkSoft)
private val PaperAccent = Color(PaperColors.paperAccent)
private val PaperLine = Color(PaperColors.paperLine)
private val PaperOnAccent = Color(0xFFFFFDF4)

/**
 * Экран расписания: неделя «12 – 18 октября», чипы дней, карточки пар.
 * Стиль STANDARD — обычная Material3; PAPER — «бумажный календарь» на PaperColors.
 */
@Composable
fun ScheduleScreen() {
    val container = LocalAppContainer.current
    val vm: ScheduleViewModel = viewModel(
        factory = viewModelFactory { initializer { ScheduleViewModel(container) } }
    )

    val settings by vm.settings.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val weekMonday by vm.weekMonday.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val weekLessons by vm.weekLessons.collectAsStateWithLifecycle()
    val exceptions by vm.exceptions.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val paper = settings?.calendarStyle == CalendarStyle.PAPER
    val dayLessons = weekLessons[selectedDate].orEmpty()

    Scaffold(
        containerColor = if (paper) PaperBackground else Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.prevWeek() }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ChevronLeft,
                        contentDescription = "Предыдущая неделя"
                    )
                }
                Text(
                    "${weekMonday.format(RU_MONTH_DAY)} – " +
                        weekMonday.plusDays(6).format(RU_MONTH_DAY),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = if (paper) PaperInk else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.nextWeek() }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ChevronRight,
                        contentDescription = "Следующая неделя"
                    )
                }
                IconButton(onClick = { vm.refreshCurrent() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Обновить неделю")
                }
            }

            if (sync.busy && dayLessons.isEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items((0..6).toList()) { offset ->
                    val date = weekMonday.plusDays(offset.toLong())
                    DayChip(
                        date = date,
                        selected = date == selectedDate,
                        paper = paper,
                        onClick = { vm.selectDate(date) }
                    )
                }
            }

            if (dayLessons.isEmpty()) {
                EmptyDayCard(
                    paper = paper,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(dayLessons) { lesson ->
                        LessonCard(
                            lesson = lesson,
                            paper = paper,
                            excluded = vm.isExceptional(lesson.subject, exceptions),
                            onToggleException = {
                                val willExclude =
                                    !vm.isExceptional(lesson.subject, exceptions)
                                vm.toggleException(lesson.subject)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (willExclude) {
                                            "Перед „${lesson.subject}“ будильник не ставится"
                                        } else {
                                            "Исключение снято"
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- Чип дня недели ---

@Composable
private fun DayChip(date: LocalDate, selected: Boolean, paper: Boolean, onClick: () -> Unit) {
    val label = "${ruShortDay(date.dayOfWeek)} ${date.dayOfMonth}"
    if (paper) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
            shape = ChipShape,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = PaperCard,
                labelColor = PaperInkSoft,
                selectedContainerColor = PaperAccent,
                selectedLabelColor = PaperOnAccent
            )
        )
    } else {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
            shape = ChipShape
        )
    }
}

// --- Карточка занятия ---

@Composable
private fun LessonCard(
    lesson: Lesson,
    paper: Boolean,
    excluded: Boolean,
    onToggleException: () -> Unit
) {
    val containerColor = if (paper) PaperCard else MaterialTheme.colorScheme.surface
    val contentColor = if (paper) PaperInk else MaterialTheme.colorScheme.onSurface
    val softColor = if (paper) PaperInkSoft else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = if (paper) PaperAccent else MaterialTheme.colorScheme.primary

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = if (paper) BorderStroke(1.dp, PaperLine) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    lesson.start.format(TIME),
                    style = MaterialTheme.typography.titleLarge
                        .copy(fontFeatureSettings = "tnum"),
                    color = contentColor
                )
                Text(
                    lesson.end.format(TIME),
                    style = MaterialTheme.typography.labelMedium
                        .copy(fontFeatureSettings = "tnum"),
                    color = softColor
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    lesson.subject,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = if (paper) FontFamily.Serif else FontFamily.Default
                    ),
                    color = contentColor
                )
                if (lesson.kind.isNotBlank() || lesson.isReplacement) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (lesson.kind.isNotBlank()) {
                            SmallBadge(
                                text = lesson.kind,
                                container = accentColor,
                                content = PaperOnAccent
                            )
                        }
                        if (lesson.isReplacement) {
                            SmallBadge(
                                text = "замена",
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                content = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                val details = buildList {
                    if (lesson.room.isNotBlank()) add("ауд. ${lesson.room}")
                    if (lesson.teacher.isNotBlank()) add(lesson.teacher)
                }.joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = softColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            IconButton(onClick = onToggleException) {
                Icon(
                    if (excluded) Icons.Rounded.NotificationsOff
                    else Icons.Rounded.NotificationsActive,
                    contentDescription = if (excluded) {
                        "Включить будильник перед этой парой"
                    } else {
                        "Не ставить будильник перед этой парой"
                    },
                    tint = if (excluded) accentColor else softColor
                )
            }
        }
    }
}

@Composable
private fun SmallBadge(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// --- Пустой день ---

@Composable
private fun EmptyDayCard(paper: Boolean, modifier: Modifier = Modifier) {
    val containerColor = if (paper) PaperCard else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (paper) PaperInk else MaterialTheme.colorScheme.onSurfaceVariant
    val softColor = if (paper) PaperInkSoft else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = if (paper) BorderStroke(1.dp, PaperLine) else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.EventBusy, contentDescription = null)
            Text("Занятий нет", style = MaterialTheme.typography.titleMedium)
            Text(
                "В этот день будильник не понадобится",
                style = MaterialTheme.typography.bodySmall,
                color = softColor
            )
        }
    }
}

// --- Утилиты ---

/** Короткое русское имя дня недели: Пн, Вт, … Вс. */
private fun ruShortDay(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Пн"
    DayOfWeek.TUESDAY -> "Вт"
    DayOfWeek.WEDNESDAY -> "Ср"
    DayOfWeek.THURSDAY -> "Чт"
    DayOfWeek.FRIDAY -> "Пт"
    DayOfWeek.SATURDAY -> "Сб"
    DayOfWeek.SUNDAY -> "Вс"
}
