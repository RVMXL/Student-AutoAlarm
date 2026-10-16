package ru.uniautoalarm.kgeu.ui.theme

import androidx.compose.ui.graphics.Color
import ru.uniautoalarm.kgeu.data.model.CalendarStyle

/**
 * Скин экрана расписания (0.14.0): единый набор ролей вместо прежних булевых
 * проверок «paper» — добавление нового стиля = новый [CalendarSkin] в [skin].
 *
 * STANDARD не имеет скина (null) — экран живёт обычными цветами Material 3.
 * Скины не зависят от акцента и светлой/тёмной темы — каждый выглядит одинаково
 * всегда (как «бумага» с 0.9.0).
 *
 * @param background фон листа (панель под содержимым); null — фон темы
 * @param card       фон карточки занятия
 * @param ink        основные чернила (текст)
 * @param inkSoft    второстепенные чернила (подписи)
 * @param accent     акцент стиля (выделение дней/бейджи) — ОТЛИЧАЕТСЯ от точки «сегодня»,
 *                   чтобы маркеры дней не путались с ней
 * @param onAccent   цвет текста на акценте
 * @param mark       цвет маркера «есть обозначенные (исключённые) секции» в чипе дня
 * @param line       линии сетки/разлиновки/обводки
 * @param serif      заголовки и названия пар — антиква (Serif)
 * @param mono       весь текст — монопространственный (Terminal/Neon)
 * @param glow       неоновая обводка карточек (яркая акцентная линия)
 */
data class CalendarSkin(
    val background: Color?,
    val card: Color,
    val ink: Color,
    val inkSoft: Color,
    val accent: Color,
    val onAccent: Color,
    val mark: Color,
    val line: Color,
    val serif: Boolean = false,
    val mono: Boolean = false,
    val glow: Boolean = false
)

/**
 * Скин по выбранному стилю. null — STANDARD (обычная Material 3 схема).
 * Чистая функция — покрыта юнит-тестом CalendarSkinsTest.
 */
fun CalendarStyle.skin(): CalendarSkin? = when (this) {
    CalendarStyle.STANDARD -> null
    CalendarStyle.PAPER -> CalendarSkin(
        background = Color(PaperColors.paperBackground),
        card = Color(PaperColors.paperCard),
        ink = Color(PaperColors.paperInk),
        inkSoft = Color(PaperColors.paperInkSoft),
        accent = Color(PaperColors.paperAccent),
        onAccent = Color(0xFFFFFDF4),
        mark = Color(0xFF3E6B3A), // приглушённый шалфейно-зелёный: на бумаге виден и не спорит с янтарём
        line = Color(PaperColors.paperLine),
        serif = true
    )

    // «Мел и доска»: тёмно-зелёная школьная доска, светлые «меловые» чернила,
    // меловые линии; акцент — жёлтый мелок, маркер — розовый мелок.
    CalendarStyle.CHALK -> CalendarSkin(
        background = Color(0xFF24382E),
        card = Color(0xFF2C4438),
        ink = Color(0xFFEFF5F0),
        inkSoft = Color(0xFFB9CCC0),
        accent = Color(0xFFF2D24B),
        onAccent = Color(0xFF2C2410),
        mark = Color(0xFFEFB0C4),
        line = Color(0xFF46604F),
        serif = true
    )

    // «Неон»: почти чёрный фон, кислотно-розовый акцент с яркой обводкой карточек,
    // мятно-зелёный маркер; шрифт — моно (музыка киберпанка).
    CalendarStyle.NEON -> CalendarSkin(
        background = Color(0xFF120A1A),
        card = Color(0xFF1C1126),
        ink = Color(0xFFF4ECFF),
        inkSoft = Color(0xFFB3A5C9),
        accent = Color(0xFFFF4FA3),
        onAccent = Color(0xFF2B0116),
        mark = Color(0xFF61F5C4),
        line = Color(0xFF3A2750),
        mono = true,
        glow = true
    )

    // «Терминал»: чёрный экран, фосфорно-зелёный текст, монопространственный шрифт,
    // тонкие зелёные линии; акцент — фосфор яркий, маркер — янтарный («системный лог»).
    CalendarStyle.TERMINAL -> CalendarSkin(
        background = Color(0xFF0B0F0B),
        card = Color(0xFF111711),
        ink = Color(0xFF7DDB7D),
        inkSoft = Color(0xFF4E8F4E),
        accent = Color(0xFF9BFF9B),
        onAccent = Color(0xFF062206),
        mark = Color(0xFFE5C07B),
        line = Color(0xFF223122),
        mono = true
    )

    // «Акварель»: мягкий пастельный лист с тёплым подтоном, размытые акварельные
    // линии; акцент — лососево-коралловый, маркер — морской волной (тил).
    CalendarStyle.AQUA -> CalendarSkin(
        background = Color(0xFFF6F1F7),
        card = Color(0xFFFDFBFE),
        ink = Color(0xFF4A4155),
        inkSoft = Color(0xFF8C8098),
        accent = Color(0xFFC96F5E),
        onAccent = Color(0xFFFFF7F4),
        mark = Color(0xFF2F8C7E),
        line = Color(0xFFE3DAE7)
    )

    // «Ретро-печать»: кремовая газетная бумага, чёрные чернила, серифы;
    // акцент — печатный красный, маркер — синий чернильный штамп.
    CalendarStyle.RETRO -> CalendarSkin(
        background = Color(0xFFF7F0DF),
        card = Color(0xFFFCF8ED),
        ink = Color(0xFF2B2620),
        inkSoft = Color(0xFF6E6557),
        accent = Color(0xFFB3382C),
        onAccent = Color(0xFFFFF6EC),
        mark = Color(0xFF34568B),
        line = Color(0xFFD9CFB8),
        serif = true
    )
}
