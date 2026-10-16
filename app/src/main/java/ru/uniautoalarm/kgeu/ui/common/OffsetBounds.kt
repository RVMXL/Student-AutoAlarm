package ru.uniautoalarm.kgeu.ui.common

/**
 * SET-13 (0.11.0): единый источник границ смещения подъёма. Раньше диапазоны
 * 15/240 и 15/180 были продублированы в SetupViewModel, SettingsViewModel,
 * SetupScreen и SettingsScreen — при смене границы менялись в одном месте,
 * и VM клампил значение, которое UI считал валидным.
 *
 * Все значения в МИНУТАХ: WAKE — общий подъём и «подъём под каждую пару»,
 * EVENT — свои занятия.
 */
object OffsetBounds {

    /** Минимальный запас на подъём (минуты). */
    const val WAKE_MIN = 15

    /** Максимальный запас на общий подъём (минуты). */
    const val WAKE_MAX = 240

    /** Минимальный запас на своё занятие (минуты). */
    const val EVENT_MIN = 15

    /** Максимальный запас на своё занятие (минуты). */
    const val EVENT_MAX = 180

    /** Шаг слайдера и пресетов (минуты). */
    const val STEP = 15

    /** Диапазон слайдера общего подъёма (для valueRange). */
    val WAKE_RANGE: ClosedFloatingPointRange<Float> = WAKE_MIN.toFloat()..WAKE_MAX.toFloat()

    /** Диапазон слайдера своего занятия (для valueRange). */
    val EVENT_RANGE: ClosedFloatingPointRange<Float> = EVENT_MIN.toFloat()..EVENT_MAX.toFloat()
}
