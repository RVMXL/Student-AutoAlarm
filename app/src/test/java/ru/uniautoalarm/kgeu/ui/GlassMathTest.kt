package ru.uniautoalarm.kgeu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.uniautoalarm.kgeu.ui.theme.GlassMath

/**
 * 0.17.0: математика стекла — мáтовость ведёт по ЛЕСТНИЦЕ из 5 уровней размытия
 * (0 = ровно резкий уровень, 1 = ровно самый мутный, позиция растёт монотонно);
 * стекло бесцветное, читаемость обеспечивает АДАПТИВНЫЙ цвет содержимого
 * (lightContentFor по средней яркости фрагмента + pixelLuminance Rec. 709).
 *
 * ГЛАВНОЕ (регрессия 0.14–0.16): cropTopLeft — точный фрагмент фона под элементом.
 * Раньше сдвиг слоя не делился на масштаб: область сэмплирования низа экрана
 * уходила ЗА нижнюю кромку картинки, BitmapShader CLAMP растягивал последний
 * ряд → «отпечаток» и горизонтальные полосы на стеклянных карточках.
 * Тесты ниже фиксируют: у ЛЮБОЙ карточки (включая нижний край экрана)
 * левый-верхний угол фрагмента остаётся ВНУТРИ картинки.
 *
 * Полосный фолбэк-искажение (API < 33) удалён в 0.17.0 — его тесты сняты;
 * искажение существует только в AGSL-шейдере (Android 13+), амплитуда 12% высоты.
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
    fun `crop top left maps layer origin to bitmap origin without crop`() {
        // слой и картинка одинакового размера — масштаб 1, сдвиг 0:
        // точка (0,0) слоя = пиксель (0,0) картинки; точка (25,60) = (25,60)
        val (x, y) = GlassMath.cropTopLeft(100f, 100f, 100f, 100f, 0f, 0f)
        assertEquals(0f, x, 1e-4f)
        assertEquals(0f, y, 1e-4f)
        val (x2, y2) = GlassMath.cropTopLeft(100f, 100f, 100f, 100f, 25f, 60f)
        assertEquals(25f, x2, 1e-4f)
        assertEquals(60f, y2, 1e-4f)
    }

    @Test
    fun `crop top left accounts for centered vertical crop`() {
        // картинка ВЫШЕ слоя (100×100 в слое 100×50): масштаб 1, вертикальный
        // кроп по центру → offY = (50 − 100)/2 = −25; точка слоя (10,10) видит
        // пиксель (10, 35) — на 25px ниже (верх картинки обрезан)
        val (x, y) = GlassMath.cropTopLeft(100f, 50f, 100f, 100f, 10f, 10f)
        assertEquals(10f, x, 1e-4f)
        assertEquals(35f, y, 1e-4f)
    }

    @Test
    fun `crop top left divides offset by scale — bottom cards stay inside bitmap`() {
        // РЕГРЕССИЯ горизонтальных полос 0.14–0.16: картинка 720×1600 растянута
        // в слой 1080×2200 (масштаб max(1.5, 1.375) = 1.5). Картинка выше слоя
        // (1600×1.5 = 2400 > 2200) → вертикальный кроп по центру: offY = −100.
        // Карточка у НИЗА экрана (dy = 1800): раньше сдвиг 1800 НЕ делился на
        // масштаб и сэмплирование шло с пикселя 1800 (в 1.5 раза дальше нужного) —
        // у нижних карточек уходило ЗА картинку, CLAMP растягивал ряд в полосы.
        // Правильно: (1800 + 100) / 1.5 = 1266.7 — внутри картинки (высота 1600).
        val scale = GlassMath.cropScale(1080f, 2200f, 720f, 1600f)
        assertEquals(1.5f, scale, 1e-4f)
        val (x, y) = GlassMath.cropTopLeft(1080f, 2200f, 720f, 1600f, 0f, 1800f)
        assertEquals(0f, x, 1e-4f)
        assertEquals(1266.67f, y, 1e-2f)
        assertTrue("фрагмент низа экрана обязан быть внутри картинки", y < 1600f)

        // и у середины экрана: dx = 540, dy = 1100 → ((540 − 0)/1.5, (1100 + 100)/1.5)
        val (mx, my) = GlassMath.cropTopLeft(1080f, 2200f, 720f, 1600f, 540f, 1100f)
        assertEquals(360f, mx, 1e-3f)
        assertEquals(800f, my, 1e-2f)
    }

    @Test
    fun `crop top left handles bitmap smaller than layer on both axes`() {
        // картинка 100×100 в слое 200×400: масштаб max(2, 4) = 4; offY = (400−400)/2 = 0,
        // offX = (200−400)/2 = −100 → точка (10, 20) слоя = ((10+100)/4, 20/4) = (27.5, 5)
        val scale = GlassMath.cropScale(200f, 400f, 100f, 100f)
        assertEquals(4f, scale, 1e-4f)
        val (x, y) = GlassMath.cropTopLeft(200f, 400f, 100f, 100f, 10f, 20f)
        assertEquals(27.5f, x, 1e-3f)
        assertEquals(5f, y, 1e-3f)
    }

    @Test
    fun `pixel luminance follows Rec 709`() {
        assertEquals(0f, GlassMath.pixelLuminance(0xFF000000.toInt()), 1e-6f)
        assertEquals(1f, GlassMath.pixelLuminance(0xFFFFFFFF.toInt()), 1e-6f)
        // чистый зелёный — самый яркий из основных (0.7152), синий — самый тёмный (0.0722)
        val g = GlassMath.pixelLuminance(0xFF00FF00.toInt())
        val b = GlassMath.pixelLuminance(0xFF0000FF.toInt())
        val r = GlassMath.pixelLuminance(0xFFFF0000.toInt())
        assertTrue(g > r)
        assertTrue(r > b)
        assertEquals(0.7152f, g, 1e-4f)
        // alpha не влияет на яркость (битмапы фона непрозрачные)
        assertEquals(g, GlassMath.pixelLuminance(0x8000FF00.toInt()), 1e-6f)
    }

    @Test
    fun `light content is chosen for dark fragments and vice versa`() {
        // тёмный фон → светлый текст; светлый фон → тёмный текст
        assertTrue(GlassMath.lightContentFor(0f))
        assertTrue(GlassMath.lightContentFor(0.2f))
        assertFalse(GlassMath.lightContentFor(0.9f))
        assertFalse(GlassMath.lightContentFor(1f))
        // порог 0.55: граница относится к светлому фону (тёмный текст)
        assertTrue(GlassMath.lightContentFor(0.54f))
        assertFalse(GlassMath.lightContentFor(0.56f))
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
