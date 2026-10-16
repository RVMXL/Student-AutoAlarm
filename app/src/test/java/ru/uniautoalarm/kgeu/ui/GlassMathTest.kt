package ru.uniautoalarm.kgeu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.uniautoalarm.kgeu.ui.theme.GlassMath

/**
 * 0.16.0: математика стекла — мáтовость ведёт по ЛЕСТНИЦЕ из 5 уровней размытия
 * (0 = ровно резкий уровень, 1 = ровно самый мутный, позиция растёт монотонно);
 * стекло бесцветное — подложки цветом темы больше нет (тест scrimAlpha удалён);
 * фолбэк-искажение полосами и амплитуда AGSL-шейдера (Android 13+) — различимые
 * глазом (амплитуды усилены в 0.16.0: сдвиг до 0.12 высоты, AGSL до 12% высоты).
 */
class GlassMathTest {

    @Test
    fun `level mix maps the full slider range onto the ladder`() {
        assertEquals(0 to 0f, GlassMath.levelMix(0f))
        assertEquals(0 to 0f, GlassMath.levelMix(-0.3f)) // зажим снизу
        assertEquals(
            GlassMath.LEVEL_COUNT - 2 to 1f, GlassMath.levelMix(1f)
        )
        assertEquals(
            GlassMath.LEVEL_COUNT - 2 to 1f, GlassMath.levelMix(1.7f) // зажим сверху
        )
        // середина диапазона — ровно средний уровень лестницы (индекс LEVEL_COUNT/2)
        assertEquals(GlassMath.LEVEL_COUNT / 2 to 0f, GlassMath.levelMix(0.5f))
        // промежуточное значение — половина первого сегмента
        val (index, fraction) = GlassMath.levelMix(0.125f)
        assertEquals(0, index)
        assertEquals(0.5f, fraction, 1e-6f)
    }

    @Test
    fun `level mix position grows monotonically across the range`() {
        // «позиция» = индекс уровня + доля — должна расти по всему ползунку,
        // чтобы стекло visibly менялось при каждом движении ползунка
        var prevPos = -0.01f
        (0..100).forEach { n ->
            val (index, fraction) = GlassMath.levelMix(n / 100f)
            val pos = index + fraction
            assertTrue("позиция растёт: $prevPos -> $pos", pos > prevPos)
            prevPos = pos
        }
        assertEquals(GlassMath.LEVEL_COUNT - 1f, prevPos, 1e-4f)
    }

    @Test
    fun `level count is enough for visible steps`() {
        // лестница имеет смысл при >= 3 уровнях: резкий, промежуточный, мутный
        assertTrue(GlassMath.LEVEL_COUNT >= 3)
    }

    @Test
    fun `fallback scrim is dense enough for readable text`() {
        // заглушка (пока уровни/геометрия не готовы) обязана быть плотной —
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
        // амплитуда не превышает 0.12 (доля высоты фрагмента) — 0.16.0: усилено
        shifts.forEach {
            assertTrue("|shift|<=0.12, got $it", kotlin.math.abs(it) <= 0.12f + 1e-6f)
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
        assertEquals(48f, GlassMath.distortionAmp(1f, 400f), 1e-6f)
        assertEquals(12f, GlassMath.distortionAmp(0.5f, 200f), 1e-6f)
        // зажим за пределами 0..1
        assertEquals(48f, GlassMath.distortionAmp(2f, 400f), 1e-6f)
        assertEquals(0f, GlassMath.distortionAmp(-1f, 400f), 1e-6f)
    }
}
