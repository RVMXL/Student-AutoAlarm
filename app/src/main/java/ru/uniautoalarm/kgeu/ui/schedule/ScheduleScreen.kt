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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import ru.uniautoalarm.kgeu.domain.DayMarkers
import ru.uniautoalarm.kgeu.ui.common.LocalAppContainer
import ru.uniautoalarm.kgeu.ui.common.dayShortName
import ru.uniautoalarm.kgeu.ui.common.timeFormat
import ru.uniautoalarm.kgeu.ui.theme.CalendarSkin
import ru.uniautoalarm.kgeu.ui.theme.skin

private val CardShape = RoundedCornerShape(20.dp)
private val ChipShape = RoundedCornerShape(8.dp)

/** «12 октября» — функция, а не синглтон: локаль на момент вызова (SET-4/UI-5). */
private fun monthDay(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())

/** Шрифт скина: моно (Терминал/Неон) → антиква (Бумага/Ретро) → обычный. */
private fun CalendarSkin?.fontFamily(): FontFamily = when {
    this?.mono == true -> FontFamily.Monospace
    this?.serif == true -> FontFamily.Serif
    else -> FontFamily.Default
}

/**
 * Экран расписания: неделя «12 – 18 октября», чипы дней, карточки пар.
 * Стиль STANDARD — обычная Material3; остальные — скины [CalendarSkin]
 * (Бумажный календарь, Мел и доска, Неон, Терминал, Акварель, Ретро-печать).
 *
 * 0.14.0: чипы дней помечаются маркерами — серая точка «в этот день есть пары»,
 * цветная точка с числом «столько обозначенных (исключённых) секций». Точка
 * «сегодня» — акцентная, как раньше; маркеры рисуются другими цветами,
 * чтобы их нельзя было спутать с ней.
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

    // Скин стиля: null — стандартная Material-схема
    val skin = settings?.calendarStyle?.skin()
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
    val accent = skin?.accent ?: MaterialTheme.colorScheme.primary
    val onAccent = skin?.onAccent ?: MaterialTheme.colorScheme.onPrimary
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
            if (skin?.background != null) {
                // Лист стиля (фон): выбранная пользователем картинка/фон видны ВОКРУГ него
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(skin.background, shape = RoundedCornerShape(18.dp))
                        .border(1.dp, skin.line, shape = RoundedCornerShape(18.dp))
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (skin?.background != null) {
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (skin != null) {
                    SkinCalendarHeader(skin, monthTitle)
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
                            else skin?.ink ?: MaterialTheme.colorScheme.onSurface
                    )
                    if (isCurrentWeek) {
                        SmallBadge(
                            text = stringResource(R.string.badge_this_week),
                            container = accent,
                            content = onAccent
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
                            else skin?.inkSoft ?: MaterialTheme.colorScheme.onSurfaceVariant
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
                    val dateLessons = weekLessons[date].orEmpty()
                    DayChip(
                        date = date,
                        selected = date == selectedDate,
                        isToday = date == today,
                        skin = skin,
                        hasLessons = dateLessons.isNotEmpty(),
                        markedCount = DayMarkers.countMarked(dateLessons, exceptions),
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
                            color = skin?.inkSoft ?: MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    EmptyDayCard(
                        skin = skin,
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
                            skin = skin,
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

// --- Шапка скина: кольца-пружина + месяц/год ---

@Composable
private fun SkinCalendarHeader(skin: CalendarSkin, monthTitle: String) {
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
                        .background(skin.card, shape = CircleShape)
                        .border(1.5.dp, skin.inkSoft, shape = CircleShape)
                )
            }
        }
        Text(
            monthTitle,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = skin.fontFamily(),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = skin.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        androidx.compose.material3.HorizontalDivider(
            color = skin.line,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

// --- Чип дня недели с маркерами ---

@Composable
private fun DayChip(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    skin: CalendarSkin?,
    hasLessons: Boolean,
    markedCount: Int,
    onClick: () -> Unit
) {
    val accent = skin?.accent ?: MaterialTheme.colorScheme.primary
    val neutralMark = skin?.inkSoft ?: MaterialTheme.colorScheme.onSurfaceVariant
    val sectionMark = skin?.mark ?: MaterialTheme.colorScheme.tertiary
    val label = "${dayShortName(date.dayOfWeek)} ${date.dayOfMonth}"
    // 0.14.0: маркеры под числом — акцентная точка «сегодня» (как в настенных
    // календарях), серая «есть пары», цветная с числом «исключённых секций».
    // Маркеры скрываются у выбранного дня — чип и так залит акцентом.
    @Composable
    fun dayLabel() {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = if (isToday) FontWeight.Bold else null)
            if (!selected && (isToday || hasLessons || markedCount > 0)) {
                Row(
                    modifier = Modifier.padding(top = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(accent, CircleShape)
                        )
                    }
                    if (hasLessons) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(neutralMark, CircleShape)
                        )
                    }
                    if (markedCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(sectionMark, CircleShape)
                        )
                        Text(
                            markedCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = sectionMark
                        )
                    }
                }
            }
        }
    }
    val description = buildString {
        append(label)
        if (hasLessons) append(". ").append(stringResource(R.string.cd_day_lessons))
        if (markedCount > 0) {
            append(". ").append(stringResource(R.string.cd_day_marked, markedCount))
        }
    }
    val chipModifier = Modifier.semantics { contentDescription = description }
    if (skin != null) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { dayLabel() },
            shape = ChipShape,
            modifier = chipModifier,
            border = if (selected) {
                BorderStroke(1.5.dp, skin.accent)
            } else {
                BorderStroke(1.dp, skin.line)
            },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = skin.card,
                labelColor = skin.inkSoft,
                selectedContainerColor = skin.accent,
                selectedLabelColor = skin.onAccent
            )
        )
    } else {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { dayLabel() },
            shape = ChipShape,
            modifier = chipModifier
        )
    }
}

// --- Карточка занятия ---

@Composable
private fun LessonCard(
    lesson: Lesson,
    skin: CalendarSkin?,
    excluded: Boolean,
    onToggleException: () -> Unit
) {
    val containerColor = skin?.card ?: MaterialTheme.colorScheme.surface
    val contentColor = skin?.ink ?: MaterialTheme.colorScheme.onSurface
    val softColor = skin?.inkSoft ?: MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = skin?.accent ?: MaterialTheme.colorScheme.primary
    val onAccentColor = skin?.onAccent ?: MaterialTheme.colorScheme.onPrimary

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = when {
            skin?.glow == true -> BorderStroke(1.5.dp, accentColor) // неоновая обводка
            skin != null -> BorderStroke(1.dp, skin.line)
            else -> null
        },
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
                        .copy(fontFeatureSettings = "tnum", fontFamily = skin.fontFamily()),
                    color = contentColor
                )
                Text(
                    lesson.end.format(timeFormat()),
                    style = MaterialTheme.typography.labelMedium
                        .copy(fontFeatureSettings = "tnum", fontFamily = skin.fontFamily()),
                    color = softColor
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    lesson.subject,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = skin.fontFamily()
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
                                content = onAccentColor
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
private fun EmptyDayCard(skin: CalendarSkin?, modifier: Modifier = Modifier) {
    val containerColor = skin?.card ?: MaterialTheme.colorScheme.surfaceVariant
    val contentColor = skin?.ink ?: MaterialTheme.colorScheme.onSurfaceVariant
    val softColor = skin?.inkSoft ?: MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = when {
            skin?.glow == true -> BorderStroke(1.5.dp, skin.accent)
            skin != null -> BorderStroke(1.dp, skin.line)
            else -> null
        },
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
