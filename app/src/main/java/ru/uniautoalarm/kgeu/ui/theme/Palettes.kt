package ru.uniautoalarm.kgeu.ui.theme

/**
 * Акцентная палитра: готовые роли Material 3 для светлой и тёмной схем.
 * Все значения подобраны вручную как гармоничные ТЁПЛЫЕ оттенки
 * (синих/индиго в списке нет по требованию дизайна).
 *
 * Прямые ссылки из UI: [primary] (светлая схема) и [primaryDark] (тёмная схема) —
 * базовый акцентный цвет, например для превью палитры в настройках.
 * Полные схемы собираются в Theme.kt (AccentPalette.toLightScheme()/toDarkScheme()).
 */
data class AccentPalette(
    val id: Int,
    val name: String, // русское имя («Янтарь», «Оранжевый», …) — для списка в настройках

    // --- Базовый акцент, светлая схема ---
    val primary: Long,
    val onPrimary: Long = 0xFFFFFFFF,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,

    // --- Базовый акцент, тёмная схема ---
    val primaryDark: Long,
    val onPrimaryDark: Long,
    val primaryContainerDark: Long,
    val onPrimaryContainerDark: Long,

    // --- Вторичный цвет, светлая схема ---
    val secondary: Long,
    val onSecondary: Long = 0xFFFFFFFF,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,

    // --- Вторичный цвет, тёмная схема ---
    val secondaryDark: Long,
    val onSecondaryDark: Long,
    val secondaryContainerDark: Long,
    val onSecondaryContainerDark: Long,

    // --- Третичный цвет, светлая схема ---
    val tertiary: Long,
    val onTertiary: Long = 0xFFFFFFFF,
    val tertiaryContainer: Long,
    val onTertiaryContainer: Long,

    // --- Третичный цвет, тёмная схема ---
    val tertiaryDark: Long,
    val onTertiaryDark: Long,
    val tertiaryContainerDark: Long,
    val onTertiaryContainerDark: Long
)

/**
 * Шесть акцентных палитр. Дефолт — index 0 «Янтарь»
 * (тёплый янтарь: 0xFFB45309 на светлом, 0xFFFFB300 на тёмном).
 * Выбор хранится в Settings.accentIndex (индекс в этом списке).
 */
val ACCENT_PALETTES: List<AccentPalette> = listOf(

    // 0 — Янтарь (дефолт)
    AccentPalette(
        id = 0,
        name = "Янтарь",
        primary = 0xFFB45309,
        primaryContainer = 0xFFFFDDB4,
        onPrimaryContainer = 0xFF2E1500,
        primaryDark = 0xFFFFB300,
        onPrimaryDark = 0xFF4A2800,
        primaryContainerDark = 0xFF683C00,
        onPrimaryContainerDark = 0xFFFFDDB4,
        secondary = 0xFF74593F,
        secondaryContainer = 0xFFFFDDBE,
        onSecondaryContainer = 0xFF2A1704,
        secondaryDark = 0xFFE4BF9D,
        onSecondaryDark = 0xFF3E2C16,
        secondaryContainerDark = 0xFF57422B,
        onSecondaryContainerDark = 0xFFFFDDBE,
        tertiary = 0xFF8B5038,
        tertiaryContainer = 0xFFFFDBCF,
        onTertiaryContainer = 0xFF380D00,
        tertiaryDark = 0xFFFFB697,
        onTertiaryDark = 0xFF521F0A,
        tertiaryContainerDark = 0xFF6D3521,
        onTertiaryContainerDark = 0xFFFFDBCF
    ),

    // 1 — Оранжевый
    AccentPalette(
        id = 1,
        name = "Оранжевый",
        primary = 0xFFBC5700,
        primaryContainer = 0xFFFFDCC2,
        onPrimaryContainer = 0xFF3B1800,
        primaryDark = 0xFFFFB774,
        onPrimaryDark = 0xFF4A2800,
        primaryContainerDark = 0xFF673D00,
        onPrimaryContainerDark = 0xFFFFDCC2,
        secondary = 0xFF755845,
        secondaryContainer = 0xFFFFDCC4,
        onSecondaryContainer = 0xFF2A1705,
        secondaryDark = 0xFFE4BFA4,
        onSecondaryDark = 0xFF3E2B1A,
        secondaryContainerDark = 0xFF5B412F,
        onSecondaryContainerDark = 0xFFFFDCC4,
        tertiary = 0xFF715A2B,
        tertiaryContainer = 0xFFFCDFA6,
        onTertiaryContainer = 0xFF241A00,
        tertiaryDark = 0xFFE5C386,
        onTertiaryDark = 0xFF261A00,
        tertiaryContainerDark = 0xFF574315,
        onTertiaryContainerDark = 0xFFFCDFA6
    ),

    // 2 — Красный
    AccentPalette(
        id = 2,
        name = "Красный",
        primary = 0xFFBA1A1A,
        primaryContainer = 0xFFFFDAD6,
        onPrimaryContainer = 0xFF410002,
        primaryDark = 0xFFFFB4AB,
        onPrimaryDark = 0xFF690005,
        primaryContainerDark = 0xFF93000A,
        onPrimaryContainerDark = 0xFFFFDAD6,
        secondary = 0xFF775652,
        secondaryContainer = 0xFFFFDAD5,
        onSecondaryContainer = 0xFF2C1512,
        secondaryDark = 0xFFE7BDB7,
        onSecondaryDark = 0xFF4D2F2B,
        secondaryContainerDark = 0xFF5D3F3B,
        onSecondaryContainerDark = 0xFFFFDAD5,
        tertiary = 0xFF715B2D,
        tertiaryContainer = 0xFFFDDFA4,
        onTertiaryContainer = 0xFF241A00,
        tertiaryDark = 0xFFE5C284,
        onTertiaryDark = 0xFF261A00,
        tertiaryContainerDark = 0xFF584415,
        onTertiaryContainerDark = 0xFFFDDFA4
    ),

    // 3 — Зелёный
    AccentPalette(
        id = 3,
        name = "Зелёный",
        primary = 0xFF386A20,
        primaryContainer = 0xFFB7F397,
        onPrimaryContainer = 0xFF052100,
        primaryDark = 0xFF9CD67D,
        onPrimaryDark = 0xFF0E3900,
        primaryContainerDark = 0xFF1F5210,
        onPrimaryContainerDark = 0xFFB7F397,
        secondary = 0xFF55624C,
        secondaryContainer = 0xFFD8E7CB,
        onSecondaryContainer = 0xFF131F0E,
        secondaryDark = 0xFFBCCBB0,
        onSecondaryDark = 0xFF273321,
        secondaryContainerDark = 0xFF3E4A36,
        onSecondaryContainerDark = 0xFFD8E7CB,
        tertiary = 0xFF705B2F,
        tertiaryContainer = 0xFFFBDFA5,
        onTertiaryContainer = 0xFF251A00,
        tertiaryDark = 0xFFE4C283,
        onTertiaryDark = 0xFF261A00,
        tertiaryContainerDark = 0xFF574316,
        onTertiaryContainerDark = 0xFFFBDFA5
    ),

    // 4 — Розовый
    AccentPalette(
        id = 4,
        name = "Розовый",
        primary = 0xFFB02E55,
        primaryContainer = 0xFFFFD9DF,
        onPrimaryContainer = 0xFF3E0719,
        primaryDark = 0xFFFFB1C1,
        onPrimaryDark = 0xFF65002C,
        primaryContainerDark = 0xFF8A1546,
        onPrimaryContainerDark = 0xFFFFD9DF,
        secondary = 0xFF75565C,
        secondaryContainer = 0xFFFFD9DF,
        onSecondaryContainer = 0xFF2B1519,
        secondaryDark = 0xFFE4BDC3,
        onSecondaryDark = 0xFF4C292F,
        secondaryContainerDark = 0xFF5C3F45,
        onSecondaryContainerDark = 0xFFFFD9DF,
        tertiary = 0xFF7C5636,
        tertiaryContainer = 0xFFFFDCC2,
        onTertiaryContainer = 0xFF2E1500,
        tertiaryDark = 0xFFEFBD94,
        onTertiaryDark = 0xFF452A12,
        tertiaryContainerDark = 0xFF623E20,
        onTertiaryContainerDark = 0xFFFFDCC2
    ),

    // 5 — Фиолетовый (не синий/индиго — тёплый баклажановый пурпур)
    AccentPalette(
        id = 5,
        name = "Фиолетовый",
        primary = 0xFF7A4CA6,
        primaryContainer = 0xFFF2DAFF,
        onPrimaryContainer = 0xFF2C0A44,
        primaryDark = 0xFFE0B3FF,
        onPrimaryDark = 0xFF451464,
        primaryContainerDark = 0xFF5D2A85,
        onPrimaryContainerDark = 0xFFF2DAFF,
        secondary = 0xFF6E596F,
        secondaryContainer = 0xFFF7DBFF,
        onSecondaryContainer = 0xFF27172A,
        secondaryDark = 0xFFDDBFE1,
        onSecondaryDark = 0xFF3F2A43,
        secondaryContainerDark = 0xFF56415A,
        onSecondaryContainerDark = 0xFFF7DBFF,
        tertiary = 0xFF825249,
        tertiaryContainer = 0xFFFFDAD4,
        onTertiaryContainer = 0xFF330F0A,
        tertiaryDark = 0xFFF6B8AC,
        onTertiaryDark = 0xFF52150D,
        tertiaryContainerDark = 0xFF693D34,
        onTertiaryContainerDark = 0xFFFFDAD4
    )
)

/**
 * Фиксированные цвета темы «Бумажный календарь» (экран расписания, CalendarStyle.PAPER).
 * Не зависят от акцента и системной темы — «бумага» выглядит одинаково всегда.
 */
object PaperColors {
    /** Фон «бумаги». */
    const val paperBackground = 0xFFFFF8EC

    /** Основные чернила (текст, цифры). */
    const val paperInk = 0xFF4E4436

    /** Чернила второстепенные (подписи, факультеты). */
    const val paperInkSoft = 0xFF8A7C68

    /** Акцент (выделение текущего дня, первой пары). */
    const val paperAccent = 0xFFB45309

    /** Линии сетки/разлиновка. */
    const val paperLine = 0xFFE4D9C3

    /** Фон карточки занятия чуть светлее бумаги. */
    const val paperCard = 0xFFFFFDF4
}
