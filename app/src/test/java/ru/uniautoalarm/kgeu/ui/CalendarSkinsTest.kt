package ru.uniautoalarm.kgeu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.uniautoalarm.kgeu.data.model.CalendarStyle
import ru.uniautoalarm.kgeu.ui.theme.CUSTOM_ACCENT_INDEX
import ru.uniautoalarm.kgeu.ui.theme.argbToHsv
import ru.uniautoalarm.kgeu.ui.theme.customAccentPalette
import ru.uniautoalarm.kgeu.ui.theme.hsvToArgb
import ru.uniautoalarm.kgeu.ui.theme.skin

/**
 * 0.14.0: скины расписания (STANDARD без скина, у остальных — палитра, где акцент
 * и маркер обозначенных секций РАЗНЫЕ цвета) и «свой цвет» (customAccentPalette
 * из одного ARGB + чистые HSV-функции).
 */
class CalendarSkinsTest {

    @Test
    fun `standard has no skin`() {
        assertNull(CalendarStyle.STANDARD.skin())
    }

    @Test
    fun `all non-standard styles have a skin`() {
        val styles = listOf(
            CalendarStyle.PAPER, CalendarStyle.CHALK, CalendarStyle.NEON,
            CalendarStyle.TERMINAL, CalendarStyle.AQUA, CalendarStyle.RETRO
        )
        styles.forEach { style ->
            val skin = style.skin()
            assertTrue("скин $style должен существовать", skin != null)
        }
    }

    @Test
    fun `marker color differs from accent and today dot can not be confused`() {
        // пользователь просил, чтобы маркеры дней не путались с точкой «сегодня»:
        // точка «сегодня» рисуется accent, маркер секций — mark (другой цвет)
        listOf(
            CalendarStyle.PAPER, CalendarStyle.CHALK, CalendarStyle.NEON,
            CalendarStyle.TERMINAL, CalendarStyle.AQUA, CalendarStyle.RETRO
        ).forEach { style ->
            val skin = style.skin()!!
            assertNotEquals("accent != mark у $style", skin.accent, skin.mark)
        }
    }

    @Test
    fun `font flags follow style`() {
        assertTrue(CalendarStyle.CHALK.skin()!!.serif)
        assertTrue(CalendarStyle.RETRO.skin()!!.serif)
        assertTrue(CalendarStyle.TERMINAL.skin()!!.mono)
        assertTrue(CalendarStyle.NEON.skin()!!.mono)
        assertTrue(CalendarStyle.NEON.skin()!!.glow)
        // бумага — сериф без моно и без неона
        val paper = CalendarStyle.PAPER.skin()!!
        assertTrue(paper.serif)
        assertTrue(!paper.mono)
        assertTrue(!paper.glow)
    }

    // --- «Свой цвет» ---

    @Test
    fun `hsv roundtrip keeps hue`() {
        // оранжево-янтарный базовый: h≈33, s=1, v=0.7
        val argb = 0xFFB45309.toInt()
        val hsv = argbToHsv(argb)
        val back = hsvToArgb(hsv[0], hsv[1], hsv[2]).toInt()
        val hsv2 = argbToHsv(back)
        assertEquals(hsv[0], hsv2[0], 0.5f)
        assertEquals(hsv[1], hsv2[1], 0.01f)
        assertEquals(hsv[2], hsv2[2], 0.01f)
    }

    @Test
    fun `custom palette keeps custom id and opaque colors`() {
        val palette = customAccentPalette(0xFF2E7D32.toInt())
        assertEquals(CUSTOM_ACCENT_INDEX, palette.id)
        // все роли — непрозрачные Long с альфой FF
        listOf(
            palette.primary, palette.primaryContainer, palette.primaryDark,
            palette.primaryContainerDark, palette.secondary, palette.tertiary
        ).forEach {
            assertEquals("альфа FF", 0xFF000000L, it and 0xFF000000L)
        }
    }

    @Test
    fun `dark primary is lighter than light primary for same base`() {
        val base = 0xFF2E7D32.toInt() // тёмно-зелёный
        val palette = customAccentPalette(base)
        val lightV = argbToHsv(palette.primary.toInt())[2]
        val darkV = argbToHsv(palette.primaryDark.toInt())[2]
        assertTrue("в тёмной схеме primary светлее", darkV > lightV)
    }

    @Test
    fun `tertiary hue is shifted`() {
        val base = argbToHsv(0xFF2E7D32.toInt())
        val tertiary = customAccentPalette(0xFF2E7D32.toInt())
        val tertiaryH = argbToHsv(tertiary.tertiary.toInt())[0]
        val expected = (base[0] + 40f) % 360f
        assertEquals(expected, tertiaryH, 1.5f)
    }
}
