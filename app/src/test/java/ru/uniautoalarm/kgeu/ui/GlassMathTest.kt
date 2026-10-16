package ru.uniautoalarm.kgeu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.uniautoalarm.kgeu.ui.theme.GlassMath

/**
 * 0.14.0: математика «стекла 2.0» — ползунки матовости/искажения.
 */
class GlassMathTest {

    @Test
    fun `blur scale bounds`() {
        assertEquals(2, GlassMath.blurScale(0f))
        assertEquals(16, GlassMath.blurScale(1f))
        assertEquals(16, GlassMath.blurScale(5f)) // зажим сверху
        assertEquals(2, GlassMath.blurScale(-1f)) // зажим снизу
    }

    @Test
    fun `blur scale grows monotonically`() {
        var prev = GlassMath.blurScale(0f)
        var m = 0.1f
        while (m <= 1.001f) {
            val cur = GlassMath.blurScale(m)
            assertTrue("blurScale($m)=$cur >= предыдущей $prev", cur >= prev)
            prev = cur
            m += 0.1f
        }
    }

    @Test
    fun `frost alpha within readable range`() {
        // UI-20-наследие: заливка не тоньше порога читаемости; максимум 0.68 (светлая, матовость 1)
        listOf(false, true).forEach { dark ->
            (0..10).forEach { i ->
                val a = GlassMath.frostAlpha(i / 10f, dark)
                assertTrue("alpha=$a dark=$dark", a in 0.20f..0.70f)
            }
        }
    }

    @Test
    fun `no distortion means single strip and zero shift`() {
        assertEquals(1, GlassMath.stripCount(0f))
        assertEquals(1, GlassMath.stripCount(-0.5f))
        assertEquals(0f, GlassMath.stripShiftFraction(3, 1, 0.7f), 1e-6f)
        assertEquals(0f, GlassMath.stripShiftFraction(3, 8, 0f), 1e-6f)
    }

    @Test
    fun `strip count grows with distortion`() {
        assertTrue(GlassMath.stripCount(0.5f) > GlassMath.stripCount(0.1f))
        assertTrue(GlassMath.stripCount(1f) >= GlassMath.stripCount(0.5f))
    }

    @Test
    fun `strip shift is bounded and wavy`() {
        val count = 10
        val distortion = 1f
        val shifts = (0 until count).map { GlassMath.stripShiftFraction(it, count, distortion) }
        // амплитуда не превышает 0.12 (доля высоты фрагмента)
        shifts.forEach { assertTrue("|shift|<=0.12, got $it", kotlin.math.abs(it) <= 0.12f + 1e-6f) }
        // волна: есть и положительные, и отрицательные сдвиги
        assertTrue(shifts.any { it > 0f })
        assertTrue(shifts.any { it < 0f })
    }

    @Test
    fun `zoom factor range`() {
        assertEquals(1f, GlassMath.zoomFactor(0f), 1e-6f)
        assertEquals(1.1f, GlassMath.zoomFactor(1f), 1e-6f)
        assertEquals(1.05f, GlassMath.zoomFactor(0.5f), 1e-6f)
    }
}
