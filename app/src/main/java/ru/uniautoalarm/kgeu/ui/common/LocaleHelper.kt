package ru.uniautoalarm.kgeu.ui.common

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import ru.uniautoalarm.kgeu.data.local.LocaleStore

/**
 * Обёртывание контекста в выбранный в приложении язык.
 *
 * Зачем: Activity оборачивается в attachBaseContext (UI), а уведомления,
 * приёмники и ViewModel получают системный контекст — им обёртывание нужно
 * явно (Notifications, ViewModels), иначе их строки будут на языке системы,
 * а не на языке, выбранном в приложении.
 */
object LocaleHelper {

    /** Контекст с локалью приложения; для «языка системы» возвращается как есть. */
    fun wrap(context: Context): Context {
        val tag = LocaleStore.language(context)
        if (tag.isBlank()) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /** Локализованная строка из любого контекста (учитывает язык приложения). */
    fun string(context: Context, resId: Int, vararg args: Any): String =
        wrap(context).getString(resId, *args)
}
