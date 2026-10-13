package ru.uniautoalarm.kgeu.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import ru.uniautoalarm.kgeu.di.AppContainer

/**
 * DI-контейнер приложения, доступный любому composable через CompositionLocal.
 * Провайдится один раз в MainActivity (CompositionLocalProvider) вокруг корня дерева.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer не предоставлен")
}
