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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
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
import ru.uniautoalarm.kgeu.ui.theme.CalendarDecor
import ru.uniautoalarm.kgeu.ui.theme.CalendarSkin
import ru.uniautoalarm.kgeu.ui.theme.GlassPanel
import ru.uniautoalarm.kgeu.ui.theme.glassCardColor
import ru.uniautoalarm.kgeu.ui.theme.glassContentColor
import ru.uniautoalarm.kgeu.ui.theme.glassHairlineColor
import ru.uniautoalarm.kgeu.ui.theme.glassSecondaryColor
import ru.uniautoalarm.kgeu.ui.theme.skin

private val CardShape = RoundedCornerShape(20.dp)
private val ChipShape = RoundedCornerShape(8.dp)

/** «12 октября» — функция, а не синглтон: локаль на момент вызова (SET-4/UI-5). */
private fun monthDay(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())

/** Шрифт скина: моно (Терминал/Неон) → рукописный (Мел) → антиква (Бумага/Ретро) → обычный. */
private fun CalendarSkin?.fontFamily(): FontFamily = when {
    this?.mono == true -> FontFamily.Monospace
    this?.cursive == true -> FontFamily.Cursive
    this?.serif == true -> FontFamily.Serif
    else -> FontFamily.Default
}

/** Ширина рамки оформления (внутренний отступ содержимого листа), 0.15.0. */
private fun CalendarSkin.frameInsetDp(): androidx.compose.ui.unit.Dp = when (decor) {
    CalendarDecor.SPRINGS -> 0.dp
    CalendarDecor.WOOD -> 7.dp
    CalendarDecor.MONITOR -> 12.dp
    CalendarDecor.GLOW -> 5.dp
    CalendarDecor.WASH -> 6.dp
    CalendarDecor.PRESS -> 4.dp
}

/**
 * Экран расписания: неделя «12 – 18 октября», чипы дней, карточки пар.
 * Стиль STANDARD — обычная Material3; остальные — скины [CalendarSkin]
 * (Бумажный календарь, Мел и доска, Неон, Терминал, Акварель, Ретро-печать),
 * у каждого своё оформление листа [CalendarDecor] (0.15.0).
 *
 * 0.15.0: «сегодня» — скруглённая полоска НАД названием дня (акцентный цвет,
 * толщина точки); точки под числом — только про расписание: серая «есть пары»,
 * цветная с числом «обозначенных (исключённых) секций».
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
                // Лист стиля (фон) с собственным оформлением стиля — 0.15.0:
                // у каждого скина своя рамка (дерево/монитор/свечение/кайма/линейки)
                SkinSheet(skin!!, Modifier.matchParentSize())
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (skin?.background != null) {
                            // контент живёт ВНУТРИ листа: внешние поля + рамка декора
                            val inset = skin!!.frameInsetDp()
                            Modifier.padding(
                                horizontal = 12.dp + inset,
                                vertical = 8.dp + inset
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (skin != null) {
                    SkinCalendarHeader(skin, monthTitle)
                }
                // FIX 0.15.0: в стандартном стиле шапка жила прямо на фоне — с картинкой
                // текст мог слиться. 0.15 — поверхность surfaceContainer; 0.17 — стеклянная
                // панель (GlassPanel) с АДАПТИВНЫМ цветом содержимого: по яркости фрагмента
                // фона под шапкой — светлый текст на тёмном участке, тёмный на светлом.
                // В скинах шапка живёт на листе скина — как было.
                val weekHeader: @Composable () -> Unit = {
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
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
                            else skin?.ink ?: glassContentColor(MaterialTheme.colorScheme.onSurface)
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
                            else skin?.inkSoft
                                ?: glassSecondaryColor(MaterialTheme.colorScheme.onSurfaceVariant)
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
                }
                if (skin == null) {
                    GlassPanel(
                        RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    glassCardColor(MaterialTheme.colorScheme.surfaceContainer),
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            weekHeader()
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        weekHeader()
                    }
                }

            if (sync.busy && dayLessons.isEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            // FIX 0.17.0: в стандартном стиле кнопки дней были прозрачны — с фоновой
            // картинкой текст сливался, а режим стекла на них не действовал. Ряд
            // дней получает ФОН как у шапки календаря: при стекле — стеклянная
            // полоса (GlassPanel) с АДАПТИВНЫМ цветом содержимого, без стекла —
            // плотная полоса surfaceContainer (glassCardColor). В скинах чипы
            // по-прежнему живут на листе скина — как было.
            val dayChipRow: @Composable () -> Unit = {
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
            }
            if (skin == null) {
                GlassPanel(
                    RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                glassCardColor(MaterialTheme.colorScheme.surfaceContainer),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        dayChipRow()
                    }
                }
            } else {
                dayChipRow()
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

// --- Лист стиля: рамка по декору скина (0.15.0) ---

/**
 * Лист расписания с СОБСТВЕННЫМ оформлением стиля (0.15.0): у бумаги — кольца-пружина,
 * у доски — деревянная рама, у терминала — корпус лампового монитора (бежевый кожух,
 * тёмный безель), у неона — светящаяся кайма, у акварели — акварельные пятна, у
 * ретро — газетные линейки. Раньше кольца-пружина рисовались у всех стилей подряд.
 */
@Composable
private fun SkinSheet(skin: CalendarSkin, modifier: Modifier = Modifier) {
    val bg = skin.background ?: return
    val sheetShape = RoundedCornerShape(14.dp)
    val frameShape = RoundedCornerShape(22.dp)
    when (skin.decor) {
        CalendarDecor.SPRINGS -> Box(
            modifier
                .background(bg, shape = sheetShape)
                .border(1.dp, skin.line, shape = sheetShape)
        )

        // деревянная school-рама: вертикальный градиент дерева + тёмная кромка
        CalendarDecor.WOOD -> Box(
            modifier
                .background(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF8A5A33), Color(0xFF5F3D1F), Color(0xFF8A5A33))
                    ),
                    shape = frameShape
                )
                .border(1.dp, Color(0xFF3B2A1A), shape = frameShape)
                .padding(7.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(bg, shape = sheetShape)
                    .border(1.dp, skin.line, shape = sheetShape)
            )
        }

        // ламповый монитор: бежевый кожух → тёмный безель → экран скина
        CalendarDecor.MONITOR -> Box(
            modifier
                .background(Color(0xFFD8D0BC), shape = frameShape)
                .border(1.dp, Color(0xFFB2A78D), shape = frameShape)
                .padding(9.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF12160F), RoundedCornerShape(10.dp))
                    .padding(3.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(bg, RoundedCornerShape(7.dp))
                        .border(1.dp, skin.line, RoundedCornerShape(7.dp))
                )
            }
        }

        // неоновая кайма: цветной ореол вокруг листа + акцентная обводка
        CalendarDecor.GLOW -> Box(
            modifier
                .background(skin.accent.copy(alpha = 0.16f), shape = frameShape)
                .padding(5.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(bg, shape = sheetShape)
                    .border(1.5.dp, skin.accent, shape = sheetShape)
            )
        }

        // акварельная кайма: мягкие пастельные пятна по периметру
        CalendarDecor.WASH -> Box(
            modifier
                .background(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFF9E2E0), Color(0xFFE0ECF6), Color(0xFFE8F3E4))
                    ),
                    shape = frameShape
                )
                .padding(6.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(bg, shape = sheetShape)
                    .border(1.dp, skin.line, shape = sheetShape)
            )
        }

        // газета: двойная типографская рамка (жирная снаружи, тонкая внутри)
        CalendarDecor.PRESS -> Box(
            modifier
                .background(Color(0xFFE9E0C9), RoundedCornerShape(10.dp))
                .border(1.5.dp, skin.ink, RoundedCornerShape(10.dp))
                .padding(4.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(bg, RoundedCornerShape(6.dp))
                    .border(0.5.dp, skin.ink.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            )
        }
    }
}

// --- Шапка скина: декор по стилю + месяц/год ---

@Composable
private fun SkinCalendarHeader(skin: CalendarSkin, monthTitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // верхний декор шапки — у каждого стиля свой (кольца — только у бумаги)
        when (skin.decor) {
            CalendarDecor.SPRINGS -> Row(
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

            // ламповый монитор: светодиод питания + «решётка динамика»
            CalendarDecor.MONITOR -> Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(skin.accent, shape = CircleShape)
                        .border(1.dp, skin.mark, shape = CircleShape)
                )
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(4.dp)
                        .background(skin.inkSoft.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                )
            }

            else -> {}
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
        // разделитель под заголовком — тоже по стилю
        when (skin.decor) {
            // газета: двойная линейка (толстая + тонкая)
            CalendarDecor.PRESS -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(skin.ink)
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(skin.ink.copy(alpha = 0.7f))
                )
            }

            // неон: светящаяся акцентная линия (ореол + ядро)
            CalendarDecor.GLOW -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(skin.accent.copy(alpha = 0.22f), RoundedCornerShape(2.dp))
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(skin.accent, RoundedCornerShape(1.dp))
                )
            }

            // доска: деревянная планка
            CalendarDecor.WOOD -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(5.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF8A5A33), Color(0xFF5F3D1F))
                        ),
                        shape = RoundedCornerShape(2.dp)
                    )
            )

            // бумага / монитор / акварель — тонкая линия цвета стиля
            else -> androidx.compose.material3.HorizontalDivider(
                color = skin.line,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
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
    val onAccentColor = skin?.onAccent ?: MaterialTheme.colorScheme.onPrimary
    // на стекле — адаптивные цвета маркеров (0.17.0); без стекла — прежние темы
    val neutralMark = skin?.inkSoft
        ?: glassSecondaryColor(MaterialTheme.colorScheme.onSurfaceVariant)
    val sectionMark = skin?.mark ?: MaterialTheme.colorScheme.tertiary
    val label = "${dayShortName(date.dayOfWeek)} ${date.dayOfMonth}"
    // 0.15.0: «сегодня» — скруглённая полоска (толщиной в точку) НАД названием дня
    // внутри кнопки; раньше точка «сегодня» стояла в ряду маркеров и сливалась
    // с точкой «есть пары». Ряд маркеров под числом теперь только про расписание:
    // серая точка «есть пары», цветная с числом «исключённых секций».
    // Маркеры скрываются у выбранного дня — чип и так залит акцентом.
    // FIX 0.17.0: слоты над и под названием ФИКСИРОВАННОЙ высоты — подпись дня
    // стоит на одной высоте у ВСЕХ чипов, независимо от того, есть ли сверху
    // полоска «сегодня» и есть ли снизу точки маркеров (раньше Column рос
    // вниз/вверх и подписи «плавали» по вертикали относительно друг друга).
    @Composable
    fun dayLabel() {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.height(6.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .size(width = 16.dp, height = 4.dp)
                            .background(
                                if (selected) onAccentColor else accent,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
            Text(label, fontWeight = if (isToday) FontWeight.Bold else null)
            Box(
                modifier = Modifier.height(13.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (!selected && (hasLessons || markedCount > 0)) {
                    Row(
                        modifier = Modifier.padding(top = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                // на стекле — АДАПТИВНЫЙ цвет (читаемо и над тёмным,
                                // и над светлым участком фона); без стекла — tertiary
                                color = glassContentColor(sectionMark)
                            )
                        }
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
        // FIX 0.17.0: у чипов стандартного стиля теперь ЕСТЬ фон: при стекле
        // заливка прозрачная (фрагмент фона рисует стеклянная полоса ряда —
        // GlassPanel выше), без стекла — плотный surfaceContainer (glassCardColor).
        // Подпись — АДАПТИВНОГО цвета на стекле (светлая на тёмном участке фона,
        // тёмная на светлом); выбранный день залит акцентом — как в скинах.
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { dayLabel() },
            shape = ChipShape,
            modifier = chipModifier,
            border = BorderStroke(1.dp, glassHairlineColor(MaterialTheme.colorScheme.outline)),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = glassCardColor(MaterialTheme.colorScheme.surfaceContainer),
                labelColor = glassContentColor(MaterialTheme.colorScheme.onSurface),
                selectedContainerColor = accent,
                selectedLabelColor = onAccentColor
            )
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
