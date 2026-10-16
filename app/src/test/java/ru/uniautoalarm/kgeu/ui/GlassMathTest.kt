package ru.uniautoalarm.kgeu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.uniautoalarm.kgeu.ui.theme.GlassMath

/**
 * 0.15.0: математика стекла — мáтовость 0 = прозрачное стекло, 1 = мутный блёр;
 * подложка контраста поверх фрагмента тонкая (стекло не превращается в карточку);
 * фолбэк-искажение полосами и амплитуда AGSL-шейдера (Android 13+).
 */
class GlassMathTest {

    @Test
    fun `scrim alpha is thin and grows with matteness`() {
        // стекло не должно становиться плотной карточкой: подложка <= 0.5 даже на максимуме
        listOf(false, true).forEach { dark ->
            var prev = -1f
            (0..10).forEach { i ->
                val a = GlassMath.scrimAlpha(i / 10f, dark)
                assertTrue("alpha=$a dark=$dark", a in 0.05f..0.50f)
                assertTrue("монотонный рост, alpha=$a", a >= prev)
                prev = a
            }
        }
        // на минимуме — почти чистое прозрачное стекло
        assertTrue(GlassMath.scrimAlpha(0f, dark = false) < 0.15f)
        assertTrue(GlassMath.scrimAlpha(0f, dark = true) < 0.20f)
    }

    @Test
    fun `fallback scrim is dense enough for readable text`() {
        // заглушка (пока битмап/геометрия не готовы) обязана быть плотной —
        // иначе до загрузки фона текст лежит на «голой» картинке
        assertTrue(GlassMath.fallbackScrimAlpha(dark = false) >= 0.85f)
        assertTrue(GlassMath.fallbackScrimAlpha(dark = true) >= 0.85f)
    }

    @Test
    fun `no distortion means single strip and zero shift`() {
        assertEquals(1, GlassMath.stripCount(0f))
        assertEquals(1, GlassMath.stripCount(-0.5f))
        assertEquals(0f, GlassMath.sampleShiftY(0.3f, 0f), 1e-6f)
        assertEquals(0f, GlassMath.sampleShiftY(0.7f, -1f), 1e-6f)
    }

    @Test
    fun `strip count grows with distortion`() {
        assertTrue(GlassMath.stripCount(0.5f) > GlassMath.stripCount(0.1f))
        assertTrue(GlassMath.stripCount(1f) >= GlassMath.stripCount(0.5f))
        assertTrue(GlassMath.stripCount(1f) <= 16)
    }

    @Test
    fun `sample shift is bounded and wavy`() {
        val distortion = 1f
        val xs = (0..20).map { it / 20f }
        val shifts = xs.map { GlassMath.sampleShiftY(it, distortion) }
        // амплитуда не превышает 0.05 (доля высоты фрагмента)
        shifts.forEach {
            assertTrue("|shift|<=0.05, got $it", kotlin.math.abs(it) <= 0.05f + 1e-6f)
        }
        // волна: есть и положительные, и отрицательные сдвиги
        assertTrue(shifts.any { it > 0f })
        assertTrue(shifts.any { it < 0f })
        // нули на краях диапазона фазы (x=0 → sin(0)=0)
        assertEquals(0f, GlassMath.sampleShiftY(0f, distortion), 1e-6f)
    }

    @Test
    fun `agsl amplitude is proportional to distortion and height`() {
        assertEquals(0f, GlassMath.distortionAmp(0f, 400f), 1e-6f)
        assertEquals(18f, GlassMath.distortionAmp(1f, 400f), 1e-6f)
        assertEquals(4.5f, GlassMath.distortionAmp(0.5f, 200f), 1e-6f)
        // зажим за пределами 0..1
        assertEquals(18f, GlassMath.distortionAmp(2f, 400f), 1e-6f)
        assertEquals(0f, GlassMath.distortionAmp(-1f, 400f), 1e-6f)
    }
}
