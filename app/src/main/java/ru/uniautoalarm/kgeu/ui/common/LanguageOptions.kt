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
 * 0.21.0: + армянский, азербайджанский, грузинский, монгольский.
 */
val SUPPORTED_LANGUAGES: List<LanguageOption> = listOf(
    LanguageOption("ru", "Русский"),
    LanguageOption("tt", "Татарча"),
    LanguageOption("en", "English"),
    LanguageOption("ar", "العربية"),
    LanguageOption("az", "Azərbaycan dili"),
    LanguageOption("ba", "Башҡортса"),
    LanguageOption("es", "Español"),
    LanguageOption("fr", "Français"),
    LanguageOption("kk", "Қазақша"),
    LanguageOption("ky", "Кыргызча"),
    LanguageOption("mn", "Монгол"),
    LanguageOption("uz", "Oʻzbekcha"),
    LanguageOption("tg", "Тоҷикӣ"),
    LanguageOption("tr", "Türkçe"),
    LanguageOption("tk", "Türkmençe"),
    LanguageOption("cv", "Чӑвашла"),
    LanguageOption("hy", "Հայերեն"),
    LanguageOption("ka", "ქართული"),
    LanguageOption("zh", "中文"),
)

/**
 * 0.21.0: имя языка НА ТЕКУЩЕМ языке интерфейса — из ресурсов приложения
 * (lang_name_*), а не из CLDR устройства. Раньше имя бралось через
 * Locale.getDisplayLanguage, и на части оболочек у редких языков (башкирский,
 * чувашский, туркменский…) локализованного имени не находилось — в списке
 * оставалось только родное имя (фидбек 0.20.0: «у башкирского нет названия на
 * текущем языке системы»). Ресурсы дают одинаковый вид на любом устройстве
 * и покрываются тестом паритета локалей.
 */
private val TAG_TO_NAME_RES: Map<String, Int> = mapOf(
    "ru" to R.string.lang_name_ru,
    "tt" to R.string.lang_name_tt,
    "en" to R.string.lang_name_en,
    "ar" to R.string.lang_name_ar,
    "az" to R.string.lang_name_az,
    "ba" to R.string.lang_name_ba,
    "es" to R.string.lang_name_es,
    "fr" to R.string.lang_name_fr,
    "kk" to R.string.lang_name_kk,
    "ky" to R.string.lang_name_ky,
    "mn" to R.string.lang_name_mn,
    "uz" to R.string.lang_name_uz,
    "tg" to R.string.lang_name_tg,
    "tr" to R.string.lang_name_tr,
    "tk" to R.string.lang_name_tk,
    "cv" to R.string.lang_name_cv,
    "hy" to R.string.lang_name_hy,
    "ka" to R.string.lang_name_ka,
    "zh" to R.string.lang_name_zh,
)

/**
 * Подпись языка в списке: родное имя + имя на текущем языке интерфейса,
 * например для английского при русском интерфейсе — «English / Английский».
 * Если имена совпадают (в том числе когда язык совпадает с языком интерфейса) —
 * только родное. Для «языка системы» — общий ресурс.
 */
@Composable
fun LanguageOption.displayName(): String {
    if (isSystem) return stringResource(R.string.lang_system)
    val nameRes = TAG_TO_NAME_RES[tag] ?: return bidiSegment(nativeName)
    val localized = stringResource(nameRes)
    val uiTag = Locale.getDefault().getLanguage()
    return if (localized.equals(nativeName, ignoreCase = true) ||
        tag.equals(uiTag, ignoreCase = true)
    ) {
        bidiSegment(nativeName)
    } else {
        // SET-14 (0.11.0): сегменты в bidi-изолятах — «English / Английский» при
        // арабском интерфейсе рендерился с плавающим разделителем
        "${bidiSegment(nativeName)} / ${bidiSegment(localized)}"
    }
}
