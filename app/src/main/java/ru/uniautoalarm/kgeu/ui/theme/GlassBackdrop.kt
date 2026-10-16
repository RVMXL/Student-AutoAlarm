package ru.uniautoalarm.kgeu.ui.theme

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Чистая математика стекла (0.15.0) — без Android/Compose-типов поверх чисел,
 * покрыта юнит-тестом GlassMathTest.
 *
 * Семантика ползунка «Матовость» (исправление 0.15.0): 0 — стекло ПРОЗРАЧНОЕ
 * (сквозь него виден фон один в один), 1 — стекло МУТНОЕ (сильный блёр).
 * В 0.14.0 мáтовость была переименованным ползунком «интенсивность»: при 0
 * поверхность становилась просто плотной карточкой, а фрагмент фона почти
 * не размывался («отпечаток»).
 */
object GlassMath {

    /**
     * Альфа тонкой подложки ПОВЕРХ фрагмента фона (для контраста текста):
     * светлая тема — высветляющая, тёмная — затемняющая. Намеренно малая:
     * стекло не должно превращаться в плотную заливку даже при мáтовости 1 —
     * мутность даёт сам блёр фрагмента.
     */
    fun scrimAlpha(matteness: Float, dark: Boolean): Float =
        if (dark) 0.16f + matteness.coerceIn(0f, 1f) * 0.32f
        else 0.10f + matteness.coerceIn(0f, 1f) * 0.24f

    /**
     * Плотная заглушка на случай, когда фрагмент фона ещё недоступен
     * (битмап не готов, геометрия слоя не измерена): поверхность остаётся
     * читаемой, а не рисует «отпечаток» с мусорной геометрией.
     */
    fun fallbackScrimAlpha(dark: Boolean): Float = if (dark) 0.88f else 0.92f

    /** Число вертикальных полос фолбэк-искажения (API < 33): 1 полоса = ровное стекло. */
    fun stripCount(distortion: Float): Int =
        if (distortion <= 0.01f) 1 else (6f + distortion.coerceIn(0f, 1f) * 10f).toInt()

    /**
     * Вертикальный сдвиг сэмплинга (доля высоты фрагмента) для точки x в 0..1 —
     * синусоидальная «волна» преломления фолбэка; при distortion = 0 всегда 0.
     */
    fun sampleShiftY(x01: Float, distortion: Float): Float {
        if (distortion <= 0f) return 0f
        val phase = 2.0 * PI * x01.toDouble().coerceIn(0.0, 1.0) * 1.75
        return sin(phase).toFloat() * distortion.coerceIn(0f, 1f) * 0.05f
    }

    /** Амплитуда искажения в пикселях для AGSL-шейдера (Android 13+). */
    fun distortionAmp(distortion: Float, heightPx: Float): Float =
        distortion.coerceIn(0f, 1f) * heightPx * 0.045f
}

/**
 * Состояние фона «стекла» (0.15.0): резкая и сильно размытая копии фоновой
 * картинки + параметры. Обе копии строятся ОДИН раз на файл (блюр больше не
 * зависит от ползунка — мáтовость смешивает резкую и размытую копии по альфе).
 * origin/layerSize — геометрия слоя фоновой картинки в окне (пересчёт
 * «координата карточки → пиксель картинки», как при ContentScale.Crop).
 */
class GlassBackdropState {
    var sharpBitmap by mutableStateOf<ImageBitmap?>(null)
    var blurBitmap by mutableStateOf<ImageBitmap?>(null)
    var origin by mutableStateOf(Offset.Zero)
    var layerSize by mutableStateOf(Size.Zero)
    var matteness by mutableStateOf(0.5f)
    var distortion by mutableStateOf(0.25f)
    var dark by mutableStateOf(false)
}

/** Локал состояния стекла; null — стекла нет (glassSurface вырождается в no-op). */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropState?> { null }

/**
 * Цвет заливки стеклянной поверхности: при активном стекле — прозрачный
 * (фрагмент фона и подложка рисуются [glassSurface] под содержимым),
 * без стекла — обычный цвет Material. Вызывается на местах Card/Surface,
 * чтобы убрать плотную заливку, которая в 0.14.0 закрывала собою фрагмент.
 */
@Composable
fun glassCardColor(fallback: Color): Color =
    if (LocalGlassBackdrop.current != null) Color.Transparent else fallback

/**
 * Стеклянная поверхность (0.15.0): рисует ПОД содержимым элемента точный
 * фрагмент фоновой картинки (BitmapShader-матрица отображает координаты
 * элемента в пиксели картинки 1:1 — стекло «прозрачно», а не «отпечаток»),
 * поверх — размытую копию с альфой = мáтовость, поверх — тонкая подложка
 * контраста и объёмные кромки в духе iOS. Искажение — AGSL-шейдер на
 * Android 13+, на старых API — полосный фолбэк.
 *
 * Без фона/стекла (LocalGlassBackdrop == null) модификатор — no-op.
 * Пока битмап/геометрия не готовы — плотная заглушка вместо фрагмента.
 */
@Composable
fun Modifier.glassSurface(shape: Shape): Modifier {
    val state = LocalGlassBackdrop.current ?: return this
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return this
        .onGloballyPositioned { coords = it }
        .drawBehind {
            val c = coords ?: return@drawBehind
            val sharp = state.sharpBitmap
            val blur = state.blurBitmap
            val layer = state.layerSize
            val ready = sharp != null && blur != null &&
                layer.width >= 1f && layer.height >= 1f &&
                state.origin != Offset.Zero && c.isAttached
            val outline = shape.createOutline(size, layoutDirection, density)
            val path = outlineToPath(outline)
            if (!ready) {
                drawFallbackGlass(path, state.dark)
                return@drawBehind
            }
            drawGlassFragment(path, state, sharp!!, blur!!, c, size)
        }
}

private fun outlineToPath(outline: Outline): Path = when (outline) {
    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    is Outline.Generic -> outline.path
}

/** Объёмные кромки (iOS-стекло): светлая фаска сверху → тёмная снизу. */
private fun DrawScope.drawEdges(path: Path, dark: Boolean) {
    val highlight = if (dark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.65f)
    val shade = if (dark) Color.Black.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.16f)
    val edgeBrush = Brush.verticalGradient(
        listOf(highlight, Color.Transparent, Color.Transparent, shade),
        startY = 0f,
        endY = size.height
    )
    drawPath(path, edgeBrush, style = Stroke(width = 1.4.dp.toPx()))
    drawPath(path, shade.copy(alpha = shade.alpha * 0.6f), style = Stroke(width = 0.8.dp.toPx()))
}

/** Плотная заглушка (битмап/геометрия не готовы) — читаемо, без «отпечатков». */
private fun DrawScope.drawFallbackGlass(path: Path, dark: Boolean) {
    val base = if (dark) Color(0xFF201B15) else Color(0xFFFFF8F1)
    drawPath(path, base.copy(alpha = GlassMath.fallbackScrimAlpha(dark)))
    drawEdges(path, dark)
}

/**
 * Стеклянный фрагмент: точный кусок фона под элементом + размытая копия
 * (альфа = мáтовость) + подложка контраста ПОВЕРХ + кромки.
 */
private fun DrawScope.drawGlassFragment(
    path: Path,
    st: GlassBackdropState,
    sharp: ImageBitmap,
    blur: ImageBitmap,
    c: LayoutCoordinates,
    elementSize: Size
) {
    val layer = st.layerSize
    val pos = c.positionInRoot()
    // ContentScale.Crop: картинка вписана по большей стороне и отцентрирована
    val scale = max(layer.width / sharp.width, layer.height / sharp.height)
    val offX = (layer.width - sharp.width * scale) / 2f
    val offY = (layer.height - sharp.height * scale) / 2f
    // матрица «локальные координаты элемента → пиксели картинки»:
    // сначала перенос в слой (позиция элемента − origin слоя − центрирование), затем 1/scale
    val base = Matrix().apply {
        postTranslate(pos.x - st.origin.x - offX, pos.y - st.origin.y - offY)
        postScale(1f / scale, 1f / scale)
    }

    clipPath(path) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val sharpShader = BitmapShader(
                sharp.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
            ).apply { setLocalMatrix(base) }
            val blurShader = BitmapShader(
                blur.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
            ).apply { setLocalMatrix(base) }
            val matteness = st.matteness.coerceIn(0f, 1f)
            if (Build.VERSION.SDK_INT >= 33) {
                // AGSL: искажение (синусоидальное преломление) + микс резкой/размытой копий
                val rs = AgslGlass.shader
                rs.setInputShader("uSharp", sharpShader)
                rs.setInputShader("uBlur", blurShader)
                rs.setFloatUniform("uMatteness", matteness)
                rs.setFloatUniform("uAmp", GlassMath.distortionAmp(st.distortion, elementSize.height))
                val paint = Paint().apply { isFilterBitmap = true; shader = rs }
                native.drawPath(path.asAndroidPath(), paint)
            } else {
                // фолбэк: резкая копия + размытая поверх с альфой мáтовости;
                // искажение — полосный сдвиг сэмплинга
                val paintSharp = Paint().apply { isFilterBitmap = true; shader = sharpShader }
                val paintBlur = Paint().apply {
                    isFilterBitmap = true; shader = blurShader
                    alpha = (matteness * 255f).toInt().coerceIn(0, 255)
                }
                val strips = GlassMath.stripCount(st.distortion)
                val w = elementSize.width
                val h = elementSize.height
                if (strips <= 1) {
                    native.drawRect(0f, 0f, w, h, paintSharp)
                    native.drawRect(0f, 0f, w, h, paintBlur)
                } else {
                    for (i in 0 until strips) {
                        val left = i * w / strips
                        val right = (i + 1) * w / strips + 1f
                        val shift = GlassMath.sampleShiftY(
                            ((left + right) / 2f) / w, st.distortion
                        ) * h
                        val mi = Matrix(base)
                        mi.postTranslate(0f, shift)
                        sharpShader.setLocalMatrix(mi)
                        blurShader.setLocalMatrix(mi)
                        native.save()
                        native.clipRect(left, 0f, right, h)
                        native.drawRect(0f, 0f, w, h, paintSharp)
                        native.drawRect(0f, 0f, w, h, paintBlur)
                        native.restore()
                    }
                }
            }
        }
        // подложка контраста — ПОВЕРХ фрагмента (в 0.14.0 заливка была ПОД ним
        // и закрывала фрагмент полупрозрачным цветом карточки)
        val scrim = if (st.dark) Color(0xFF14100C) else Color(0xFFFFFBF4)
        drawPath(path, scrim.copy(alpha = GlassMath.scrimAlpha(st.matteness, st.dark)))
    }

    drawEdges(path, st.dark)
}

/**
 * AGSL-шейдер стекла (Android 13+): синусоидальное преломление сэмплинга
 * (амплитуда uAmp) + смешение резкой и размытой копий по мáтовости.
 * Отдельный object: класс RuntimeShader существует только с API 33, объект
 * инициализируется лишь при фактическом обращении из ветки SDK >= 33.
 */
private object AgslGlass {
    private const val SKSL = """
        uniform shader uSharp;
        uniform shader uBlur;
        uniform float uMatteness;
        uniform float uAmp;
        half4 main(float2 fragCoord) {
            float wave = sin(fragCoord.y * 0.043 + sin(fragCoord.x * 0.011) * 1.7);
            float2 off = float2(wave * uAmp, wave * uAmp * 0.4);
            half4 s = uSharp.eval(fragCoord + off);
            half4 b = uBlur.eval(fragCoord + off);
            return mix(s, b, uMatteness);
        }
    """
    val shader = RuntimeShader(SKSL)
}
