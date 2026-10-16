package ru.uniautoalarm.kgeu.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.util.Locale
import ru.uniautoalarm.kgeu.R

/** Один язык в списке выбора: тег BCP-47 («ru», «tt», …) и родное имя. */
data class LanguageOption(
    val tag: String,
    val nativeName: String
) {
    /** Пустой тег — «язык системы». */
    val isSystem: Boolean get() = tag.isBlank()
}

/**
 * Поддерживаемые языки. Сверху — самые востребованные в КГЭУ
 * (русский, татарский, английский), дальше — по алфавиту родных имён
 * (SET-11, 0.11.0: «Oʻzbekcha» на «O» стояла после «Тоҷикӣ»/«Türkçe» — порядок
 * приведён к заявленному в комментарии).
 */
val SUPPORTED_LANGUAGES: List<LanguageOption> = listOf(
    LanguageOption("ru", "Русский"),
    LanguageOption("tt", "Татарча"),
    LanguageOption("en", "English"),
    LanguageOption("ar", "العربية"),
    LanguageOption("ba", "Башҡортса"),
    LanguageOption("es", "Español"),
    LanguageOption("fr", "Français"),
    LanguageOption("kk", "Қазақша"),
    LanguageOption("ky", "Кыргызча"),
    LanguageOption("uz", "Oʻzbekcha"),
    LanguageOption("tg", "Тоҷикӣ"),
    LanguageOption("tr", "Türkçe"),
    LanguageOption("tk", "Türkmençe"),
    LanguageOption("cv", "Чӑвашла"),
    LanguageOption("zh", "中文"),
)

/**
 * Подпись языка в списке: родное имя + имя на текущем языке интерфейса,
 * например для английского при русском интерфейсе — «English / Английский».
 * Если имена совпадают — только родное. Для «языка системы» — общий ресурс.
 */
@Composable
fun LanguageOption.displayName(): String {
    if (isSystem) return stringResource(R.string.lang_system)
    val current = Locale.getDefault()
    val native = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.forLanguageTag(tag))
        .ifBlank { nativeName }
    val localized = Locale.forLanguageTag(tag).getDisplayLanguage(current)
    return if (localized.isNullOrBlank() || localized == native) {
        nativeName
    } else {
        val capitalized = localized.replaceFirstChar { it.uppercase(current) }
        // SET-14 (0.11.0): сегменты в bidi-изолятах — «English / Английский» при
        // арабском интерфейсе рендерился с плавающим разделителем
        "${bidiSegment(nativeName)} / ${bidiSegment(capitalized)}"
    }
}
