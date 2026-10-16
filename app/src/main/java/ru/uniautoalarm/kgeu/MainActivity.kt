package ru.uniautoalarm.kgeu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.ui.common.LocalAppContainer
import ru.uniautoalarm.kgeu.ui.common.LocaleHelper
import ru.uniautoalarm.kgeu.ui.home.HomeScreen
import ru.uniautoalarm.kgeu.ui.navigation.Routes
import ru.uniautoalarm.kgeu.ui.schedule.ScheduleScreen
import ru.uniautoalarm.kgeu.ui.settings.SettingsScreen
import ru.uniautoalarm.kgeu.ui.setup.SetupScreen
import ru.uniautoalarm.kgeu.ui.theme.AutoAlarmTheme
import ru.uniautoalarm.kgeu.ui.theme.GlassBackdropState
import ru.uniautoalarm.kgeu.ui.theme.GlassPanel
import ru.uniautoalarm.kgeu.ui.theme.LocalGlassBackdrop
import ru.uniautoalarm.kgeu.ui.theme.glassCardColor
import ru.uniautoalarm.kgeu.ui.theme.glassContentColor
import ru.uniautoalarm.kgeu.ui.theme.glassSecondaryColor
import ru.uniautoalarm.kgeu.ui.theme.resolveDarkTheme

/**
 * Единственная активность приложения: edge-to-edge, язык из LocaleStore
 * (attachBaseContext — до первого кадра), тема из настроек (DataStore),
 * провайдинг DI-контейнера через CompositionLocal и корневая маршрутизация:
 * загрузка → мастер настройки → главный каркас с нижней навигацией.
 */
class MainActivity : ComponentActivity() {

    /** Язык интерфейса приложения — обёртыванием контекста (работает на всех API). */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AutoAlarmApp
        setContent {
            val container = app.container
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = null)
            // Окно — в цвет ТЕМЫ ПРИЛОЖЕНИЯ (не системы): под стеклом/прозрачными
            // поверхностями виден именно windowBackground. Раньше окно оставалось
            // тёмным (values-night) при светлой теме приложения — серая каша.
            val darkTheme = resolveDarkTheme(settings)
            SideEffect {
                window.setBackgroundDrawableResource(
                    if (darkTheme) R.color.window_background_dark
                    else R.color.window_background_light
                )
            }
            AutoAlarmTheme(settings) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Root(settings)
                }
            }
        }
    }
}

/** Корневой роутер: снимок настроек ещё не пришёл → загрузка; онбординг не пройден → мастер. */
@Composable
private fun Root(settings: Settings?) {
    // Явный contentColor: корень без Surface оставлял LocalContentColor = чёрный —
    // заголовки мастера были чёрными на тёмном фоне (скрин с Realme GT Neo 5 SE).
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        when {
            settings == null -> BootingScreen()

            !settings.setupComplete -> SetupScreen(
                // Данные настроек обновятся из DataStore — recomposition произойдёт сам
                onFinished = { }
            )

            else -> MainScaffold(settings)
        }
    }
}

/** Компактный загрузочный экран на время первого чтения DataStore. */
@Composable
private fun BootingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/** Пункт нижней навигации: маршрут + ресурс подписи + пара иконок (выбранная/невыбранная). */
private data class TopDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TOP_DESTINATIONS = listOf(
    TopDestination(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    TopDestination(
        Routes.SCHEDULE, R.string.nav_schedule,
        Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth
    ),
    TopDestination(
        Routes.SETTINGS, R.string.nav_settings,
        Icons.Rounded.Settings, Icons.Outlined.Settings
    )
)

/**
 * Главный каркас: нижняя навигация (3 пункта) + NavHost.
 * Большой тумблер авто-будильника живёт на главном экране (HomeScreen),
 * в bottom bar ничего лишнего нет.
 *
 * Своя фоновая картинка: если задана — Scaffold рисуется прозрачным,
 * а картинка (файл из filesDir) кладётся под NavHost с пользовательской прозрачностью.
 *
 * Стекло 2.3 (0.17.0): фоновая картинка ВСЕГДА резкая; [LocalGlassBackdrop]
 * поставляет ЛЕСТНИЦУ копий (резкая + 4 уровня размытия) и ПРОБУ яркости;
 * [GlassPanel] на карточках/нижней панели рисует под ними точные фрагменты
 * (0.17.0: исправлена математика фрагмента — без «отпечатка» и горизонтальных
 * полос) и подбирает адаптивный цвет содержимого по яркости фрагмента;
 * мáтовость 0 — прозрачное стекло, 1 — мутный блёр; искажение — только AGSL
 * на Android 13+ (см. ui.theme.GlassBackdrop).
 * Провайдер оборачивает ВЕСЬ Scaffold: нижняя панель тоже стеклянная
 * (в 0.15.0 провайдер стоял только вокруг NavHost — панель оставалась плотной).
 *
 * @param settings снимок настроек — фоновая картинка и её прозрачность.
 */
@Composable
private fun MainScaffold(settings: Settings) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val backgroundPath = settings.backgroundImagePath

    // UI-20 (0.11.0): файл могли удалить вручную/очисткой хранилища — тогда AsyncImage
    // молча рисует НИЧЕГО над прозрачным Scaffold. Проверяем существование: нет файла —
    // работаем как без фона (плотный background), а не «прозрачная» поверхность
    val backgroundFile = remember(backgroundPath) {
        backgroundPath?.let { path -> File(path).takeIf { it.isFile && it.length() > 0 } }
    }

    val darkTheme = resolveDarkTheme(settings)
    val glassState = remember { GlassBackdropState() }
    val glassActive = settings.glassEnabled && backgroundFile != null

    // 0.16.0: лестница уровней фона (резкая ~1600px + 4 размытых) строится
    // один раз на файл — ползунки не перезапускают декодирование, мáтовость
    // лишь выбирает пару соседних уровней при отрисовке.
    // 0.17.0: рядом строится ПРОБА (маленькая копия резкого уровня) — по ней
    // GlassPanel оценивает яркость фрагмента ПОД каждой панелью и подбирает
    // светлый/тёмный цвет текста (стекло остаётся бесцветным)
    LaunchedEffect(backgroundFile) {
        val file = backgroundFile
        val art = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) { produceBackdrop(file) }
        }
        glassState.levels = art?.levels?.map { it.asImageBitmap() }
        glassState.probe = art?.probe?.asImageBitmap()
    }
    SideEffect {
        glassState.matteness = settings.glassBlur
        glassState.distortion = settings.glassDistortion
        glassState.dark = darkTheme
    }

    // 0.17.0: провайдер вокруг ВСЕГО Scaffold — стеклянные панели в bottomBar и
    // на экранах видят состояние стекла (в 0.15.0 провайдер стоял только вокруг NavHost)
    CompositionLocalProvider(
        LocalGlassBackdrop provides if (glassActive) glassState else null
    ) {
        Scaffold(
            containerColor = if (backgroundFile != null) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.background
            },
            bottomBar = {
                // стекло 2.3 (0.17.0): панель навигации — стеклянная, цвет пунктов
                // АДАПТИВНЫЙ (по яркости фрагмента под панелью): на тёмном фоне —
                // светлые подписи/иконки, на светлом — тёмные. Без стекла —
                // ровно прежние цвета темы.
                GlassPanel(RectangleShape, Modifier.fillMaxWidth()) {
                    NavigationBar(
                        // заливка прозрачная при стекле — фрагмент рисует GlassPanel
                        containerColor = glassCardColor(MaterialTheme.colorScheme.surface),
                        contentColor = glassContentColor(MaterialTheme.colorScheme.onSurface)
                    ) {
                        for (destination in TOP_DESTINATIONS) {
                            val selected = currentRoute == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = glassSecondaryColor(
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    unselectedTextColor = glassSecondaryColor(
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ),
                                icon = {
                                    val label = stringResource(destination.labelRes)
                                    Icon(
                                        imageVector = if (selected) {
                                            destination.selectedIcon
                                        } else {
                                            destination.unselectedIcon
                                        },
                                        contentDescription = label
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // геометрия слоя фона: фон рисуется matchParentSize в этом же Box —
                    // origin/размер Box = origin/размер картинки для пересчёта фрагментов
                    .onGloballyPositioned {
                        glassState.origin = it.positionInRoot()
                        glassState.layerSize =
                            androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())
                    }
            ) {
                if (backgroundFile != null) {
                    AsyncImage(
                        model = backgroundFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // 0.14.0: картинка БОЛЬШЕ не размывается целиком — резкая;
                        // размытие живёт только в стеклянных поверхностях (выше)
                        modifier = Modifier.matchParentSize(),
                        alpha = settings.backgroundOpacity
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Routes.HOME) { HomeScreen() }
                    composable(Routes.SCHEDULE) { ScheduleScreen() }
                    composable(Routes.SETTINGS) { SettingsScreen() }
                }
            }
        }
    }
}

/**
 * Лестница копий фоновой картинки для стекла (0.17.0) + проба яркости:
 *  0) резкая — декод с прореживанием до ~1600px по большей стороне (стекло при
 *     мáтовости 0 показывает фон «один в один» — настоящая прозрачность);
 *  1..4) размытые — из копии 720px: ÷2 (лёгкая дымка), ÷6 (средний блёр),
 *     ÷14 ×2 прохода (сильный), ÷14 ×3 (предельная муть).
 *  probe) копия резкого уровня шириной 72px — по ней GlassPanel оценивает
 *     яркость фрагмента под каждой панелью (адаптивный цвет текста).
 * null — файл не читается (стеклянные поверхности рисуют плотную заглушку).
 */
private class BackdropArt(val levels: List<Bitmap>, val probe: Bitmap)

private fun produceBackdrop(file: File): BackdropArt? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
    val sharp = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return@runCatching null
    val probeWidth = 72
    val probe = Bitmap.createScaledBitmap(
        sharp,
        probeWidth,
        maxOf(1, (sharp.height * probeWidth.toFloat() / sharp.width).roundToInt()),
        true
    )
    val base = if (sharp.width > 720) {
        Bitmap.createScaledBitmap(
            sharp, 720, (sharp.height * 720f / sharp.width).roundToInt(), true
        )
    } else {
        sharp
    }
    BackdropArt(
        listOf(
            sharp,
            blurred(base, divisor = 2, passes = 1),
            blurred(base, divisor = 6, passes = 1),
            blurred(base, divisor = 14, passes = 2),
            blurred(base, divisor = 14, passes = 3)
        ),
        probe
    )
}.getOrNull()

/**
 * Один уровень размытия: [passes] проходов «даунскейл до 1/[divisor] → апскейл
 * до размера [base]». Каждый проход размывает уже размытое — мутность копится.
 */
private fun blurred(base: Bitmap, divisor: Int, passes: Int): Bitmap {
    var result = base
    repeat(passes) {
        val w = maxOf(1, result.width / divisor)
        val h = maxOf(1, result.height / divisor)
        val small = Bitmap.createScaledBitmap(result, w, h, true)
        result = Bitmap.createScaledBitmap(small, base.width, base.height, true)
    }
    return result
}
