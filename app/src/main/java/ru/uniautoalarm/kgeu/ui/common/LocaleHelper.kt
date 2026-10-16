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

    /**
     * SET-12 (0.11.0): системная локаль, снятая ОДИН РАЗ при старте процесса
     * ([captureSystemLocale] из Application.onCreate). Нужна для честного возврата
     * на «язык системы»: раньше Locale.setDefault не сбрасывался — выбрав ru и
     * вернувшись на «язык системы», пользователь получал ru-форматтеры навсегда.
     */
    @Volatile
    private var systemDefault: Locale? = null

    /** Зафиксировать системную локаль (вызывается один раз из AutoAlarmApp.onCreate). */
    fun captureSystemLocale() {
        if (systemDefault == null) systemDefault = Locale.getDefault()
    }

    /** Контекст с локалью приложения; для «языка системы» возвращается как есть. */
    fun wrap(context: Context): Context {
        val tag = LocaleStore.language(context)
        if (tag.isBlank()) {
            // SET-12: «язык системы» — возвращаем системную локаль по умолчанию
            // (после предшествующего выбора конкретного языка она была перезаписана)
            systemDefault?.let { Locale.setDefault(it) }
            return context
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * Локаль приложения БЕЗ создания обёрнутого контекста — для дат/времени
     * (DateTimeFormatter) в уведомлениях и приёмниках: строки там и так идут
     * через [string], а форматтеры раньше брали Locale.getDefault() — на холодном
     * старте это язык СИСТЕМЫ (SET-3: смешанные языки в одном уведомлении).
     */
    fun locale(context: Context): Locale {
        val tag = LocaleStore.language(context)
        return if (tag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(tag)
    }

    /** Локализованная строка из любого контекста (учитывает язык приложения). */
    fun string(context: Context, resId: Int, vararg args: Any): String =
        wrap(context).getString(resId, *args)
}
