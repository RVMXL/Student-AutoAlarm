package ru.uniautoalarm.kgeu.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.launch
import ru.uniautoalarm.kgeu.R
import ru.uniautoalarm.kgeu.data.model.CalendarStyle
import ru.uniautoalarm.kgeu.data.model.Lesson
import ru.uniautoalarm.kgeu.ui.common.LocalAppContainer
import ru.uniautoalarm.kgeu.ui.common.dayShortName
import ru.uniautoalarm.kgeu.ui.common.timeFormat
import ru.uniautoalarm.kgeu.ui.theme.PaperColors

private val CardShape = RoundedCornerShape(20.dp)
private val ChipShape = RoundedCornerShape(8.dp)

/** «12 октября» — функция, а не синглтон: локаль на момент вызова (SET-4/UI-5). */
private fun monthDay(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())

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
    // UI-13 (0.11.0): «сегодня» — состояние, обновляемое на каждом возврате экрана на
    // передний план, а не замороженное на входе в композицию: приложение, висевшее
    // открытым через полночь, подсвечивало вчерашний день до пересоздания активити
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    val currentWeekMonday =
        remember(today) { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val isCurrentWeek = weekMonday == currentWeekMonday
    val accent = if (paper) PaperAccent else MaterialTheme.colorScheme.primary
    val monthTitle = remember(weekMonday) {
        val month = weekMonday.month
            .getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { it.uppercase() }
        "$month · ${weekMonday.year}"
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (paper) {
                // Лист настенного календаря (фон): выбранная пользователем картинка/фон видны ВОКРУГ него
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(PaperBackground, shape = RoundedCornerShape(18.dp))
                        .border(1.dp, PaperLine, shape = RoundedCornerShape(18.dp))
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (paper) {
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (paper) {
                    PaperCalendarHeader(monthTitle)
                }
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.prevWeek() }) {
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = stringResource(R.string.cd_prev_week)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${weekMonday.format(monthDay())} – " +
                            weekMonday.plusDays(6).format(monthDay()),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        // текущая неделя выделена акцентом — из неё чаще всего и «уезжают»
                        color = if (isCurrentWeek) accent
                            else if (paper) PaperInk else MaterialTheme.colorScheme.onSurface
                    )
                    if (isCurrentWeek) {
                        SmallBadge(
                            text = stringResource(R.string.badge_this_week),
                            container = accent,
                            content = PaperOnAccent
                        )
                    }
                }
                // UI-14 (0.10.0): перелистывание вперёд не гейтится по busy —
                // но кнопка отключена во время загрузки, чтобы не штормить API подряд
                IconButton(onClick = {
                    vm.nextWeek { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                }, enabled = !sync.busy) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = stringResource(R.string.cd_next_week)
                    )
                }
                IconButton(onClick = { vm.goToToday() }) {
                    Icon(
                        Icons.Rounded.Today,
                        contentDescription = stringResource(R.string.cd_go_today),
                        // подсвечена, пока пользователь «уехал» с текущей недели
                        tint = if (!isCurrentWeek) accent
                            else if (paper) PaperInkSoft else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    vm.refreshCurrent { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                }) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_week)
                    )
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
                items((0..6).toList(), key = { it }) { offset -> // UI-12: key — переиспользование состояния чипов
                    val date = weekMonday.plusDays(offset.toLong())
                    DayChip(
                        date = date,
                        selected = date == selectedDate,
                        isToday = date == today,
                        paper = paper,
                        onClick = { vm.selectDate(date) }
                    )
                }
            }

            if (dayLessons.isEmpty()) {
                if (sync.busy && sync.lastSyncEpochMs == 0L) {
                    // UI-15 (0.10.0): первая загрузка — вместо ложного «нет пар» показываем
                    // индикатор; EmptyDayCard рисуем только когда данные уже приходили
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(R.string.refreshing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (paper) PaperInkSoft else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    EmptyDayCard(
                        paper = paper,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // UI-12: стабильный составной key — пересортировка/замены не пересоздают карточки заново
                    items(dayLessons, key = { lesson ->
                        "${lesson.date}:${lesson.start}:${lesson.end}:${lesson.subject}:" +
                            "${lesson.room}:${lesson.kind}:${lesson.group}"
                    }) { lesson ->
                        // строки снекбара вынесены в композицию: внутри scope.launch
                        // composable-функции (stringResource) вызывать нельзя
                        val msgExcludedOn =
                            stringResource(R.string.snackbar_excluded_on, lesson.subject)
                        val msgExcludedOff = stringResource(R.string.snackbar_excluded_off)
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
                                        if (willExclude) msgExcludedOn else msgExcludedOff
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
}

// --- Шапка настенного календаря: кольца-пружина + месяц/год ---

@Composable
private fun PaperCalendarHeader(monthTitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
        ) {
            repeat(7) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(PaperCard, shape = CircleShape)
                        .border(1.5.dp, PaperInkSoft, shape = CircleShape)
                )
            }
        }
        Text(
            monthTitle,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = PaperInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        androidx.compose.material3.HorizontalDivider(
            color = PaperLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

// --- Чип дня недели ---

@Composable
private fun DayChip(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    paper: Boolean,
    onClick: () -> Unit
) {
    val accent = if (paper) PaperAccent else MaterialTheme.colorScheme.primary
    val label = "${dayShortName(date.dayOfWeek)} ${date.dayOfMonth}"
    // сегодняшний день: жирный + точка-акцент под числом (как в настенных календарях)
    @Composable
    fun todayLabel() {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = if (isToday) FontWeight.Bold else null)
            if (isToday && !selected) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(4.dp)
                        .background(accent, CircleShape)
                )
            }
        }
    }
    if (paper) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { todayLabel() },
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
            label = { todayLabel() },
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
                    lesson.start.format(timeFormat()),
                    style = MaterialTheme.typography.titleLarge
                        .copy(fontFeatureSettings = "tnum"),
                    color = contentColor
                )
                Text(
                    lesson.end.format(timeFormat()),
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
                                text = stringResource(R.string.badge_replacement),
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                content = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                val details = buildList {
                    if (lesson.room.isNotBlank()) {
                        add(stringResource(R.string.room_prefix, lesson.room))
                    }
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
                        stringResource(R.string.cd_enable_alarm)
                    } else {
                        stringResource(R.string.cd_disable_alarm)
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
            Text(
                stringResource(R.string.empty_no_lessons),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.empty_no_alarm_needed),
                style = MaterialTheme.typography.bodySmall,
                color = softColor
            )
        }
    }
}
