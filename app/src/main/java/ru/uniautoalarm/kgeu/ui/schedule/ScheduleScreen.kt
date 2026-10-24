package ru.uniautoalarm.kgeu.ui.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
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
import ru.uniautoalarm.kgeu.ui.theme.skin

private val CardShape = RoundedCornerShape(20.dp)
private val ChipShape = RoundedCornerShape(8.dp)

// --- 0.25.0: свайп-навигация по дням и неделям ---

/** Окно пейджера: ±366 дней от текущего понедельника (≈ год в каждую сторону). */
private const val PAGER_SPAN_DAYS = 366

/** Всего страниц пейджера: окно в обе стороны + неделя вперёд в запасе. */
private const val PAGER_PAGE_COUNT = PAGER_SPAN_DAYS * 2 + 8

/** Прыжки длиннее этого числа страниц («сегодня» издалека) — без анимации. */
private const val PAGER_INSTANT_JUMP_PAGES = 10

/** Индекс страницы пейджера для даты (страницы = дни от базового понедельника). */
private fun pagerIndexOf(base: LocalDate, date: LocalDate): Int =
    ChronoUnit.DAYS.between(base, date).toInt()

/** «Октябрь · 2025» из понедельника недели. */
private fun monthTitleOf(monday: LocalDate): String {
    val month = monday.month
        .getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { it.uppercase() }
    return "$month · ${monday.year}"
}

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
 *
 * 0.25.0: перелистывание дней и недель СВАЙПОМ. Контент дня и шапка (месяц +
 * неделя) — два синхронизированных пейджера на ОДНОЙ PagerState: двигаются
 * одним жестом. Внутри недели страницы шапки идентичны — она визуально
 * неподвижна, а на границе недели (воскресенье → понедельник и обратно)
 * перелистывается сама; у скинов вместе с ней уезжает «шапка листа» с месяцем.
 * Ряд чипов дней стоит на месте — выделение ПЛАВНО ПЕРЕТЕКАЕТ цветом с
 * предыдущего чипа на новый (см. [DayChip]). Лист скина (рамка/фон) — статичный
 * «прибор», страницы уезжают из-под него. Стрелки недель и «сегодня» ведут
 * себя как раньше, но тоже едут с анимацией.
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
    val accent = skin?.accent ?: MaterialTheme.colorScheme.primary
    val onAccent = skin?.onAccent ?: MaterialTheme.colorScheme.onPrimary

    // Страницы пейджера = дни в окне ±PAGER_SPAN_DAYS от текущего понедельника.
    val pagerBase = remember {
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusDays(PAGER_SPAN_DAYS.toLong())
    }
    val pagerState = rememberPagerState(
        initialPage = pagerIndexOf(pagerBase, LocalDate.now()),
        pageCount = { PAGER_PAGE_COUNT }
    )
    val usePager = pagerIndexOf(pagerBase, selectedDate) in 0 until PAGER_PAGE_COUNT

    // Страница установилась → фиксируем день в VM (свайп или завершение анимации).
    // Если день из другой недели, VM сдвигает неделю следом и тихо подтягивает
    // её из API (как при стрелках недель).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            vm.settleOn(pagerBase.plusDays(page.toLong())) { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }
    // Внешняя смена дня (чипы, стрелки недель, «сегодня») → доводим пейджер:
    // ближние прыжки — с анимацией, дальние («сегодня» из другого месяца) — мгновенно.
    LaunchedEffect(selectedDate, usePager) {
        if (!usePager) return@LaunchedEffect
        val idx = pagerIndexOf(pagerBase, selectedDate)
        if (idx in 0 until PAGER_PAGE_COUNT && pagerState.currentPage != idx) {
            if (abs(idx - pagerState.currentPage) > PAGER_INSTANT_JUMP_PAGES) {
                pagerState.scrollToPage(idx)
            } else {
                pagerState.animateScrollToPage(idx)
            }
        }
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
                // у каждого скина своя рамка (дерево/монитор/свечение/кайма/линейки).
                // Статичен при свайпах: «прибор», страницы уезжают из-под него.
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
                // --- Шапка: месяц (скины) + неделя. Пейджер — перелистывается ---
                if (usePager) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        beyondViewportPageCount = 1
                    ) { page ->
                        val pageMonday = remember(page) {
                            pagerBase.plusDays(page.toLong())
                                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        }
                        if (skin != null) {
                            SkinCalendarHeader(
                                skin,
                                remember(page) { monthTitleOf(pageMonday) }
                            )
                        }
                        WeekHeaderBox(
                            monday = pageMonday,
                            currentWeekMonday = currentWeekMonday,
                            skin = skin,
                            accent = accent,
                            onAccent = onAccent,
                            busy = sync.busy,
                            onPrevWeek = {
                                vm.prevWeek { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                            onNextWeek = {
                                vm.nextWeek { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                            onGoToday = vm::goToToday,
                            onRefresh = {
                                vm.refreshCurrent { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            }
                        )
                    }
                } else {
                    // Дата вне окна пейджера (стрелками «уехали» дальше года) — прямой
                    // рендер без пейджера, чтобы ничего не падало на крайних значениях
                    if (skin != null) {
                        SkinCalendarHeader(skin, monthTitleOf(weekMonday))
                    }
                    WeekHeaderBox(
                        monday = weekMonday,
                        currentWeekMonday = currentWeekMonday,
                        skin = skin,
                        accent = accent,
                        onAccent = onAccent,
                        busy = sync.busy,
                        onPrevWeek = {
                            vm.prevWeek { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        onNextWeek = {
                            vm.nextWeek { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        onGoToday = vm::goToToday,
                        onRefresh = {
                            vm.refreshCurrent { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                    )
                }

                if (sync.busy && dayLessons.isEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                // у ряда дней в стандартном стиле плотный фон surfaceContainer (как у
                // шапки) — с фоновой картинкой подписи не сливаются. В скинах чипы
                // живут на листе скина. Подписи дней — на одной высоте
                // (фиксированные слоты в dayLabel).
                // 0.20.0: ряд дней НЕ прокручивается — 7 чипов распределены равномерно
                // по ширине экрана (weight(1f)), перематывать не нужно.
                // 0.25.0: ряд стоит на месте и при свайпах — «перелистывается» только
                // выделение (плавный переход цвета со старого чипа на новый).
                // Точки дня = числу ПАР (0.20.0): уникальные слоты времени —
                // лабы двух подгрупп в одно время = одна пара
                val dayChipRow: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (0..6).forEach { offset ->
                            key(offset) { // UI-12: key — переиспользование состояния чипов
                                val date = weekMonday.plusDays(offset.toLong())
                                val dateLessons = weekLessons[date].orEmpty()
                                DayChip(
                                    date = date,
                                    selected = date == selectedDate,
                                    isToday = date == today,
                                    skin = skin,
                                    pairCount = DayMarkers.countPairs(dateLessons),
                                    markedCount = DayMarkers.countMarked(dateLessons, exceptions),
                                    onClick = { vm.selectDate(date) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                if (skin == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        dayChipRow()
                    }
                } else {
                    dayChipRow()
                }

                // --- Контент дня: страницы-дни пейджера (свайп влево — день вперёд) ---
                if (usePager) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1
                    ) { page ->
                        DayPage(
                            date = pagerBase.plusDays(page.toLong()),
                            weekLessons = weekLessons,
                            skin = skin,
                            busy = sync.busy,
                            firstSync = sync.lastSyncEpochMs == 0L,
                            isExceptional = { subject -> vm.isExceptional(subject, exceptions) },
                            onToggleException = { subject -> vm.toggleException(subject) },
                            onShowSnackbar = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }
                } else {
                    DayPage(
                        date = selectedDate,
                        weekLessons = weekLessons,
                        skin = skin,
                        busy = sync.busy,
                        firstSync = sync.lastSyncEpochMs == 0L,
                        isExceptional = { subject -> vm.isExceptional(subject, exceptions) },
                        onToggleException = { subject -> vm.toggleException(subject) },
                        onShowSnackbar = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }
            }
        }
    }
}

// --- Шапка недели (страница верхнего пейджера, 0.25.0) ---

/**
 * Шапка недели: стрелки, «12 – 18 октября», бейдж «идёт текущая неделя»,
 * «сегодня», обновить. Рисуется на КАЖДОЙ странице верхнего пейджера:
 * внутри недели все страницы идентичны (перелистывание незаметно), при
 * переходе через границу недели шапка уезжает вместе с контентом дня.
 *
 * 0.25.0: бейдж текущей недели всегда занимает место, видимость — плавной
 * альфой: высота шапки постоянна, страницы пейджера строго одной высоты
 * (иначе при перелистывании «прыгала» бы вся колонка).
 */
@Composable
private fun WeekHeaderBox(
    monday: LocalDate,
    currentWeekMonday: LocalDate,
    skin: CalendarSkin?,
    accent: Color,
    onAccent: Color,
    busy: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onGoToday: () -> Unit,
    onRefresh: () -> Unit
) {
    val isCurrentWeek = monday == currentWeekMonday
    // FIX 0.19.0: без явного цвета иконки брали LocalContentColor ТЕМЫ приложения —
    // в тёмной теме на светлых листах скинов (бумажный, акварель, ретро-печать)
    // они почти сливались с фоном
    val ink = skin?.ink ?: MaterialTheme.colorScheme.onSurface
    val badgeAlpha by animateFloatAsState(
        targetValue = if (isCurrentWeek) 1f else 0f,
        animationSpec = tween(280),
        label = "weekBadgeAlpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .then(
                if (skin == null) {
                    // FIX 0.15.0: у шапки стандартного стиля плотная поверхность —
                    // с фоновой картинкой текст больше не сливается
                    Modifier.background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(16.dp)
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevWeek) {
                Icon(
                    Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.cd_prev_week),
                    tint = ink
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "${monday.format(monthDay())} – ${monday.plusDays(6).format(monthDay())}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    // текущая неделя выделена акцентом — из неё чаще всего и «уезжают»
                    color = if (isCurrentWeek) accent else ink
                )
                Box(Modifier.alpha(badgeAlpha)) {
                    SmallBadge(
                        text = stringResource(R.string.badge_this_week),
                        container = accent,
                        content = onAccent
                    )
                }
            }
            // UI-14 (0.10.0): кнопка отключена во время загрузки, чтобы не штормить API
            IconButton(onClick = onNextWeek, enabled = !busy) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.cd_next_week),
                    tint = ink
                )
            }
            IconButton(onClick = onGoToday) {
                Icon(
                    Icons.Rounded.Today,
                    contentDescription = stringResource(R.string.cd_go_today),
                    // подсвечена, пока пользователь «уехал» с текущей недели
                    tint = if (!isCurrentWeek) accent
                        else skin?.inkSoft
                            ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh_week),
                    tint = ink
                )
            }
        }
    }
}

// --- Страница дня (контент нижнего пейджера, 0.25.0) ---

/**
 * Содержимое ОДНОГО дня — страница нижнего пейджера: карточки пар или пустое
 * состояние. Данные берутся из окна трёх недель [weekLessons]: страницы
 * соседних дней (в том числе за границей недели) уже наполнены во время свайпа.
 * Тап по иконке пары гасит/возвращает будильник (исключения).
 */
@Composable
private fun DayPage(
    date: LocalDate,
    weekLessons: Map<LocalDate, List<Lesson>>,
    skin: CalendarSkin?,
    busy: Boolean,
    firstSync: Boolean,
    isExceptional: (String) -> Boolean,
    onToggleException: (String) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val dayLessons = weekLessons[date].orEmpty()
    if (dayLessons.isEmpty()) {
        if (busy && firstSync) {
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
                    excluded = isExceptional(lesson.subject),
                    onToggleException = {
                        val willExclude = !isExceptional(lesson.subject)
                        onToggleException(lesson.subject)
                        onShowSnackbar(if (willExclude) msgExcludedOn else msgExcludedOff)
                    }
                )
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
    pairCount: Int,
    markedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = skin?.accent ?: MaterialTheme.colorScheme.primary
    val onAccentColor = skin?.onAccent ?: MaterialTheme.colorScheme.onPrimary
    // маркеры — цвета темы
    val neutralMark = skin?.inkSoft ?: MaterialTheme.colorScheme.onSurfaceVariant
    val sectionMark = skin?.mark ?: MaterialTheme.colorScheme.tertiary
    val label = "${dayShortName(date.dayOfWeek)} ${date.dayOfMonth}"
    // 0.25.0: переход выделения АНИМИРОВАН — при перелистывании дня (свайп или тап
    // по соседнему чипу) заливка/подпись/кромка плавно «перетекают» с предыдущего
    // чипа на следующий: старый выбранный гаснет из акцента, новый разгорается.
    // Визуальный прежний: контейнер / заливка выбранного = акцент, кромка скина,
    // у стандартного стиля — surfaceContainer/outline
    val colorSpec = tween<Color>(280)
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            accent
        } else {
            skin?.card ?: MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = colorSpec,
        label = "dayChipContainer"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            onAccentColor
        } else {
            skin?.inkSoft ?: MaterialTheme.colorScheme.onSurface
        },
        animationSpec = colorSpec,
        label = "dayChipLabel"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            accent
        } else {
            skin?.line ?: MaterialTheme.colorScheme.outline
        },
        animationSpec = colorSpec,
        label = "dayChipBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 1.5.dp else 1.dp,
        animationSpec = tween(280),
        label = "dayChipBorderWidth"
    )
    // полоска «сегодня» и точки маркеров проявляются/тают плавно, а не скачком:
    // слоты фиксированной высоты, layout чипа не прыгает при перелистывании
    val todayBarColor by animateColorAsState(
        targetValue = if (selected) onAccentColor else accent,
        animationSpec = colorSpec,
        label = "dayChipTodayBar"
    )
    val hasMarks = pairCount > 0 || markedCount > 0
    val marksAlpha by animateFloatAsState(
        targetValue = if (selected || !hasMarks) 0f else 1f,
        animationSpec = tween(280),
        label = "dayChipMarks"
    )
    // 0.15.0: «сегодня» — скруглённая полоска (толщиной в точку) НАД названием дня
    // внутри кнопки; раньше точка «сегодня» стояла в ряду маркеров и сливалась
    // с точкой «есть пары». Ряд маркеров под числом теперь только про расписание:
    // ТОЧКИ ПАР (0.20.0) — по одной на каждый уникальный слот времени
    // (лабы двух подгрупп в одно время = одна точка), цветной маркер
    // с числом «исключённых секций».
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
                            .background(todayBarColor, RoundedCornerShape(2.dp))
                    )
                }
            }
            // 0.21.0: стиль подписи закреплён (labelLarge, строка 20sp) — раньше
            // подпись наследовала bodyLarge (строка 24sp) и колонка контента
            // (6+24+13 = 43dp) не влезала в чип 32dp: Column прижимался к верху,
            // и нижний слот с точками обрезался краем чипа («точки наполовину
            // скрыты»). Теперь чип 40dp, а контент 6+20+13 = 39dp — точки
            // целиком внутри даже при увеличенном системном шрифте
            Text(
                label,
                color = labelColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.Bold else null
            )
            Box(
                modifier = Modifier.height(13.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (hasMarks) {
                    Row(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .alpha(marksAlpha),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // точки пар — по одной на каждый уникальный слот (start,end);
                        // при исключённых секциях точек меньше (4), чтобы влез
                        // цветной маркер с числом; точное число всегда в description
                        val pairCap = if (markedCount > 0) 4 else 6
                        repeat(minOf(pairCount, pairCap)) {
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
                                // 0.21.0: явная высота строки 11sp — дефолтные 16sp
                                // labelSmall вылезали из слота маркеров 13dp
                                lineHeight = 11.sp,
                                color = sectionMark
                            )
                        }
                    }
                }
            }
        }
    }
    val description = buildString {
        append(label)
        if (pairCount > 0) {
            append(". ").append(stringResource(R.string.cd_day_pairs, pairCount))
        }
        if (markedCount > 0) {
            append(". ").append(stringResource(R.string.cd_day_marked, markedCount))
        }
    }
    // 0.20.0: компактный чип вместо FilterChip — тот держал минимальную ширину
    // touch-target 48dp и внутренние паддинги, из-за чего 7 дней не помещались
    // в экран и ряд приходилось перематывать.
    // 0.21.0: высота 32→40dp — контент чипа (6dp полоска + 20sp подпись +
    // 13dp слот маркеров = 39dp) больше не обрезается: точки пар видны целиком
    // (фикс фидбека 0.20.0 «точки наполовину скрыты»)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics { contentDescription = description }
            .clip(ChipShape)
            .background(containerColor)
            .border(borderWidth, borderColor, ChipShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        dayLabel()
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
