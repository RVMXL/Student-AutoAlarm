package ru.uniautoalarm.kgeu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import java.io.File
import ru.uniautoalarm.kgeu.data.model.Settings
import ru.uniautoalarm.kgeu.ui.common.LocalAppContainer
import ru.uniautoalarm.kgeu.ui.home.HomeScreen
import ru.uniautoalarm.kgeu.ui.navigation.Routes
import ru.uniautoalarm.kgeu.ui.schedule.ScheduleScreen
import ru.uniautoalarm.kgeu.ui.settings.SettingsScreen
import ru.uniautoalarm.kgeu.ui.setup.SetupScreen
import ru.uniautoalarm.kgeu.ui.theme.AutoAlarmTheme

/**
 * Единственная активность приложения: edge-to-edge, тема из настроек (DataStore),
 * провайдинг DI-контейнера через CompositionLocal и корневая маршрутизация:
 * загрузка → мастер настройки → главный каркас с нижней навигацией.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AutoAlarmApp
        setContent {
            val container = app.container
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = null)
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

/** Пункт нижней навигации: маршрут + пара иконок (выбранная/невыбранная). */
private data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TOP_DESTINATIONS = listOf(
    TopDestination(Routes.HOME, "Главная", Icons.Rounded.Home, Icons.Outlined.Home),
    TopDestination(
        Routes.SCHEDULE, "Расписание", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth
    ),
    TopDestination(Routes.SETTINGS, "Настройки", Icons.Rounded.Settings, Icons.Outlined.Settings)
)

/**
 * Главный каркас: нижняя навигация (3 пункта) + NavHost.
 * Большой тумблер авто-будильника живёт на главном экране (HomeScreen),
 * в bottom bar ничего лишнего нет.
 *
 * Своя фоновая картинка: если задана — Scaffold рисуется прозрачным,
 * а картинка (файл из filesDir) кладётся под NavHost с пользовательской прозрачностью.
 *
 * @param settings снимок настроек — фоновая картинка и её прозрачность.
 */
@Composable
private fun MainScaffold(settings: Settings) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val backgroundPath = settings.backgroundImagePath

    Scaffold(
        containerColor = if (backgroundPath != null) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.background
        },
        bottomBar = {
            NavigationBar {
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
                        icon = {
                            Icon(
                                imageVector = if (selected) {
                                    destination.selectedIcon
                                } else {
                                    destination.unselectedIcon
                                },
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (backgroundPath != null) {
                AsyncImage(
                    model = File(backgroundPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        // Эффект стекла: фоновая картинка размыта по ползунку (API 31+,
                        // на старых Android блюр молча не применяется — остаётся прозрачность)
                        .then(
                            if (settings.glassEnabled) {
                                Modifier.blur((settings.glassIntensity * 20).dp)
                            } else {
                                Modifier
                            }
                        ),
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
