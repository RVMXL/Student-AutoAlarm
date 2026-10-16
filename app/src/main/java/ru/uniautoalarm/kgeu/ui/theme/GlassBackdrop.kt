package ru.uniautoalarm.kgeu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Чистая математика «стекла 2.0» (0.14.0) — без Android/Compose-типов поверх чисел,
 * покрыта юнит-тестом GlassMathTest.
 */
object GlassMath {

    /**
     * Коэффициент прореживания для бокового размытия (downscale→upscale):
     * матовость 0..1 → множитель 2..16. Чем больше, тем сильнее размытие.
     */
    fun blurScale(matteness: Float): Int = (2f + matteness.coerceIn(0f, 1f) * 14f).toInt()

    /**
     * Альфа «морозной» заливки поверх размытого фрагмента:
     * тёмная тема — чернильная заливка плотнее, светлая — белая.
     */
    fun frostAlpha(matteness: Float, dark: Boolean): Float =
        if (dark) 0.22f + matteness.coerceIn(0f, 1f) * 0.38f
        else 0.28f + matteness.coerceIn(0f, 1f) * 0.40f

    /** Число вертикальных полос «линзового» искажения (0 искажения → 1 полоса = ровное стекло). */
    fun stripCount(distortion: Float): Int =
        if (distortion <= 0.01f) 1 else (6f + distortion.coerceIn(0f, 1f) * 10f).toInt()

    /**
     * Вертикальный сдвиг полосы [index] из [count] в долях высоты фрагмента:
     * синусоидальное «преломление» — как в неровном стекле. Амплитуда пропорциональна
     * [distortion]; при distortion = 0 всегда 0.
     */
    fun stripShiftFraction(index: Int, count: Int, distortion: Float): Float {
        if (count <= 1 || distortion <= 0f) return 0f
        val phase = index.toDouble() * 2.0 * PI / count
        return (sin(phase) * distortion * 0.12).toFloat()
    }

    /** Линзовый зум фрагмента: стекло слегка «приближает» пространство за собой. */
    fun zoomFactor(distortion: Float): Float = 1f + distortion.coerceIn(0f, 1f) * 0.10f
}

/**
 * Общее состояние фона «стекла»: размытая копия фоновой картинки + параметры.
 * Создаётся в MainScaffold; поля — mutableStateOf, чтобы drawBehind у стеклянных
 * поверхностей перерисовывался при изменении ползунков/скролле/пересоздании фона.
 * origin/layerSize — геометрия слоя фоновой картинки в окне (пересчёт координат
 * карточки → координаты картинки, как при ContentScale.Crop).
 */
class GlassBackdropState {
    var bitmap by mutableStateOf<ImageBitmap?>(null)
    var origin by mutableStateOf(Offset.Zero)
    var layerSize by mutableStateOf(Size.Zero)
    var matteness by mutableStateOf(0.5f)
    var distortion by mutableStateOf(0.25f)
    var dark by mutableStateOf(false)
}

/** Локал состояния стекла; null — стекла нет (glassSurface вырождается в no-op). */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropState?> { null }

/**
 * Стеклянная поверхность (0.14.0): рисует ПОД содержимым элемента фрагмент фоновой
 * картинки — размытый (кэш в [GlassBackdropState.bitmap]) и искажённый полосами
 * («линзовое» преломление по [GlassMath]) — плюс объёмные кромки в духе iOS:
 * светлая фаска сверху, тёмная снизу. Сам фоновый рисунок вне стекла остаётся резким.
 *
 * Без фона/стекла (LocalGlassBackdrop == null) модификатор — no-op.
 * Composable-фабрика: composed() удалён в compose-ui 1.7, а параметры Local* нужны
 * в композиции (density/layoutDirection — для createOutline в draw-фазе).
 */
@Composable
fun Modifier.glassSurface(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val state = LocalGlassBackdrop.current ?: return this
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return this
        .onGloballyPositioned { coords = it }
        .drawBehind {
            val st = state
            val bmp = st.bitmap ?: return@drawBehind
            val layer = st.layerSize
            if (layer.width < 1f || layer.height < 1f) return@drawBehind
            val c = coords ?: return@drawBehind
            val pos = c.positionInRoot()
            val rect = Rect(
                left = pos.x - st.origin.x,
                top = pos.y - st.origin.y,
                right = pos.x - st.origin.x + size.width,
                bottom = pos.y - st.origin.y + size.height
            )
            drawGlassFragment(bmp, st, layer, rect, shape.createOutline(size, layoutDirection, density))
        }
}

private fun outlineToPath(outline: Outline): Path = when (outline) {
    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    is Outline.Generic -> outline.path
}

/**
 * Отрисовка стеклянного фрагмента: размытые полосы фона + морозная заливка
 * + объёмные кромки. Всё в координатах слоя фона ([rect] — прямоугольник элемента
 * внутри слоя фоновой картинки).
 */
private fun DrawScope.drawGlassFragment(
    bmp: ImageBitmap,
    st: GlassBackdropState,
    layer: Size,
    rect: Rect,
    outline: Outline
) {
    val bmpW = bmp.width
    val bmpH = bmp.height
    // ContentScale.Crop: картинка вписана по большей стороне и отцентрирована
    val scale = max(layer.width / bmpW, layer.height / bmpH)
    val offX = (layer.width - bmpW * scale) / 2f
    val offY = (layer.height - bmpH * scale) / 2f
    val zoom = GlassMath.zoomFactor(st.distortion)
    val strips = GlassMath.stripCount(st.distortion)
    val stripW = rect.width / strips
    val path = outlineToPath(outline)

    clipPath(path) {
        // 1) морозная заливка — база «матовости» (даже при отсутствии фрагментов)
        val frostColor = if (st.dark) Color(0xFF171310) else Color(0xFFFFFBF4)
        drawPath(path, frostColor.copy(alpha = GlassMath.frostAlpha(st.matteness, st.dark)))
        // 2) размытые фрагменты фона по полосам (сдвиг по синусу = искажение)
        for (i in 0 until strips) {
            val shift = GlassMath.stripShiftFraction(i, strips, st.distortion)
            val dstLeft = rect.left + i * stripW
            val dstWidth = stripW + 1f // перекрытие полос против швов
            val centerX = dstLeft + dstWidth / 2f - offX
            val centerY = rect.top + rect.height / 2f - offY + shift * rect.height
            val srcW = (dstWidth / scale / zoom)
                .roundToInt().coerceIn(1, bmpW)
            val srcH = (rect.height / scale / zoom)
                .roundToInt().coerceIn(1, bmpH)
            val sx = (centerX / scale).roundToInt().coerceIn(0, bmpW - srcW)
            val sy = (centerY / scale).roundToInt().coerceIn(0, bmpH - srcH)
            drawImage(
                bmp,
                srcOffset = IntOffset(sx, sy),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset(dstLeft.roundToInt(), rect.top.roundToInt()),
                dstSize = IntSize(dstWidth.roundToInt(), rect.height.roundToInt()),
                filterQuality = FilterQuality.Low
            )
        }
    }

    // 3) объёмные кромки (iOS-стекло): светлая фаска сверху → тёмная снизу
    val highlight = if (st.dark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.65f)
    val shade = if (st.dark) Color.Black.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.16f)
    val edgeBrush = Brush.verticalGradient(
        listOf(highlight, Color.Transparent, Color.Transparent, shade),
        startY = rect.top,
        endY = rect.bottom
    )
    drawPath(path, edgeBrush, style = Stroke(width = 1.4.dp.toPx()))
    // тонкий внешний контур для читаемости кромки на любом фоне
    drawPath(path, shade.copy(alpha = shade.alpha * 0.6f), style = Stroke(width = 0.8.dp.toPx()))
}
