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
 * Чистая математика стекла (0.16.0) — без Android/Compose-типов поверх чисел,
 * покрыта юнит-тестом GlassMathTest.
 *
 * Семантика ползунка «Матовость»: 0 — стекло ПРОЗРАЧНОЕ (фон виден один в один),
 * 1 — стекло МУТНОЕ (предельный блёр). С 0.16.0 мáтовость ведёт по ЛЕСТНИЦЕ из
 * [LEVEL_COUNT] уровней размытия: каждая ступень — заметный скачок мутности,
 * поэтому ползунок «действует» по всему диапазону, а не только на краях
 * (в 0.15.0 кроссфейд «резкая → предельная муть» + подложка темы гасили отклик).
 * Стекло БЕСЦВЕТНОЕ: подложки цветом темы поверх фрагмента больше нет —
 * она тонировала стекло белым/тёмным цветом светлой/тёмной темы.
 */
object GlassMath {

    /** Уровней размытия в лестнице (включая уровень 0 — резкий). Минимум 2. */
    const val LEVEL_COUNT = 5

    /**
     * Позиция на лестнице размытия для мáтовости: (индекс нижнего уровня,
     * доля смешения к следующему). 0 → (0, 0) — ровно резкий уровень;
     * 1 → (LEVEL_COUNT-2, 1) — ровно самый мутный уровень.
     */
    fun levelMix(matteness: Float): Pair<Int, Float> {
        val t = matteness.coerceIn(0f, 1f) * (LEVEL_COUNT - 1)
        val index = t.toInt().coerceIn(0, LEVEL_COUNT - 2)
        return index to (t - index)
    }

    /**
     * Плотная заглушка на случай, когда фрагмент фона ещё недоступен
     * (уровни не декодированы, геометрия слоя не измерена): поверхность
     * остаётся читаемой, а не рисует «отпечаток» с мусорной геометрией.
     */
    fun fallbackScrimAlpha(dark: Boolean): Float = if (dark) 0.88f else 0.92f

    /** Число вертикальных полос фолбэк-искажения (API < 33): 1 полоса = ровное стекло. */
    fun stripCount(distortion: Float): Int =
        if (distortion <= 0.01f) 1 else (6f + distortion.coerceIn(0f, 1f) * 10f).toInt()

    /**
     * Вертикальный сдвиг сэмплинга (доля высоты фрагмента) для точки x в 0..1 —
     * синусоидальная «волна» преломления фолбэка; при distortion = 0 всегда 0.
     * 0.16.0: амплитуда 0.05 → 0.12 — искажение стало различимым глазом.
     */
    fun sampleShiftY(x01: Float, distortion: Float): Float {
        if (distortion <= 0f) return 0f
        val phase = 2.0 * PI * x01.toDouble().coerceIn(0.0, 1.0) * 1.75
        return sin(phase).toFloat() * distortion.coerceIn(0f, 1f) * 0.12f
    }

    /**
     * Амплитуда искажения в пикселях для AGSL-шейдера (Android 13+).
     * 0.16.0: 4.5% → 12% высоты — искажение стало различимым глазом.
     */
    fun distortionAmp(distortion: Float, heightPx: Float): Float =
        distortion.coerceIn(0f, 1f) * heightPx * 0.12f
}

/**
 * Состояние фона «стекла» (0.16.0): ЛЕСТНИЦА копий фоновой картинки от резкой
 * до предельно размытой ([GlassMath.LEVEL_COUNT] уровней) + параметры. Все
 * уровни строятся ОДИН раз на файл (ползунки не перезапускают декод); мáтовость
 * выбирает пару соседних уровней и долю смешения между ними (GlassMath.levelMix).
 * origin/layerSize — геометрия слоя фоновой картинки в окне (пересчёт
 * «координата карточки → пиксель картинки», как при ContentScale.Crop).
 */
class GlassBackdropState {
    var levels by mutableStateOf<List<ImageBitmap>?>(null)
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
 * (фрагмент фона рисуется [glassSurface] под содержимым), без стекла —
 * обычный цвет Material. Вызывается на местах Card/Surface.
 */
@Composable
fun glassCardColor(fallback: Color): Color =
    if (LocalGlassBackdrop.current != null) Color.Transparent else fallback

/**
 * Стеклянная поверхность (0.16.0): рисует ПОД содержимым элемента точный
 * фрагмент фоновой картинки (BitmapShader-матрица отображает координаты
 * элемента в пиксели картинки 1:1 — стекло «прозрачно», а не «отпечаток»),
 * смешанный по мáтовости из пары соседних уровней лестницы размытия.
 * Стекло БЕСЦВЕТНОЕ: только фрагмент фона (степень мутности — по мáтовости)
 * и объёмные кромки; никакой подложки цветом темы (0.16.0: убрана — она
 * тонировала стекло цветом светлой/тёмной темы, пользователь видел «карточку»).
 * Искажение — AGSL-шейдер на Android 13+, на старых API — полосный фолбэк.
 *
 * Без фона/стекла (LocalGlassBackdrop == null) модификатор — no-op.
 * Пока уровни/геометрия не готовы — плотная заглушка вместо фрагмента.
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
            val levels = state.levels
            val layer = state.layerSize
            // layerSize >= 1 достаточно: origin и layerSize ставятся одним
            // onGloballyPositioned слоя фона — раз размер известен, известен origin
            val ready = levels != null && levels.size == GlassMath.LEVEL_COUNT &&
                layer.width >= 1f && layer.height >= 1f && c.isAttached
            val outline = shape.createOutline(size, layoutDirection, density)
            val path = outlineToPath(outline)
            if (!ready || levels == null) {
                drawFallbackGlass(path, state.dark)
                return@drawBehind
            }
            drawGlassFragment(path, state, levels, c, size)
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

/** Плотная заглушка (уровни/геометрия не готовы) — читаемо, без «отпечатков». */
private fun DrawScope.drawFallbackGlass(path: Path, dark: Boolean) {
    val base = if (dark) Color(0xFF201B15) else Color(0xFFFFF8F1)
    drawPath(path, base.copy(alpha = GlassMath.fallbackScrimAlpha(dark)))
    drawEdges(path, dark)
}

/**
 * Матрица «локальные координаты элемента → пиксели картинки» при
 * ContentScale.Crop: картинка вписана по большей стороне и отцентрирована.
 * Уровни лестницы могут отличаться разрешением — у каждого своя матрица;
 * пропорции у всех одинаковые, поэтому отображение визуально совпадает.
 */
private fun cropMatrix(
    layer: Size,
    origin: Offset,
    elementPos: Offset,
    bmpWidth: Float,
    bmpHeight: Float
): Matrix {
    val scale = max(layer.width / bmpWidth, layer.height / bmpHeight)
    val offX = (layer.width - bmpWidth * scale) / 2f
    val offY = (layer.height - bmpHeight * scale) / 2f
    return Matrix().apply {
        postTranslate(elementPos.x - origin.x - offX, elementPos.y - origin.y - offY)
        postScale(1f / scale, 1f / scale)
    }
}

/**
 * Стеклянный фрагмент: точный кусок фона под элементом из пары соседних
 * уровней лестницы (мáтовость), искажение по ползунку, кромки. БЕЗ подложки
 * цветом темы (0.16.0) — стекло бесцветное, сквозь него виден только фон.
 */
private fun DrawScope.drawGlassFragment(
    path: Path,
    st: GlassBackdropState,
    levels: List<ImageBitmap>,
    c: LayoutCoordinates,
    elementSize: Size
) {
    val pos = c.positionInRoot()
    val (index, fraction) = GlassMath.levelMix(st.matteness)
    val bmpA = levels[index]
    val bmpB = levels[index + 1]
    val matrixA = cropMatrix(
        st.layerSize, st.origin, pos, bmpA.width.toFloat(), bmpA.height.toFloat()
    )
    val matrixB = cropMatrix(
        st.layerSize, st.origin, pos, bmpB.width.toFloat(), bmpB.height.toFloat()
    )

    clipPath(path) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val shaderA = BitmapShader(
                bmpA.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
            ).apply { setLocalMatrix(matrixA) }
            val shaderB = BitmapShader(
                bmpB.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
            ).apply { setLocalMatrix(matrixB) }
            if (Build.VERSION.SDK_INT >= 33) {
                // AGSL: искажение (синусоидальное преломление) + смешение уровней
                val rs = AgslGlass.shader
                rs.setInputShader("uA", shaderA)
                rs.setInputShader("uB", shaderB)
                rs.setFloatUniform("uMix", fraction)
                rs.setFloatUniform(
                    "uAmp", GlassMath.distortionAmp(st.distortion, elementSize.height)
                )
                val paint = Paint().apply { isFilterBitmap = true; shader = rs }
                native.drawPath(path.asAndroidPath(), paint)
            } else {
                // фолбэк: нижний уровень целиком + следующий поверх с альфой доли;
                // искажение — полосный сдвиг сэмплинга
                val paintA = Paint().apply { isFilterBitmap = true; shader = shaderA }
                val paintB = Paint().apply {
                    isFilterBitmap = true; shader = shaderB
                    alpha = (fraction * 255f).toInt().coerceIn(0, 255)
                }
                val strips = GlassMath.stripCount(st.distortion)
                val w = elementSize.width
                val h = elementSize.height
                if (strips <= 1) {
                    native.drawRect(0f, 0f, w, h, paintA)
                    native.drawRect(0f, 0f, w, h, paintB)
                } else {
                    for (i in 0 until strips) {
                        val left = i * w / strips
                        val right = (i + 1) * w / strips + 1f
                        val shift = GlassMath.sampleShiftY(
                            ((left + right) / 2f) / w, st.distortion
                        ) * h
                        shaderA.setLocalMatrix(
                            Matrix(matrixA).apply { postTranslate(0f, shift) }
                        )
                        shaderB.setLocalMatrix(
                            Matrix(matrixB).apply { postTranslate(0f, shift) }
                        )
                        native.save()
                        native.clipRect(left, 0f, right, h)
                        native.drawRect(0f, 0f, w, h, paintA)
                        native.drawRect(0f, 0f, w, h, paintB)
                        native.restore()
                    }
                }
            }
        }
    }

    drawEdges(path, st.dark)
}

/**
 * AGSL-шейдер стекла (Android 13+): синусоидальное преломление сэмплинга
 * по ДВУМ осям (амплитуда uAmp, форма волны — «жидкое стекло») + смешение
 * пары уровней лестницы по uMix. Отдельный object: класс RuntimeShader
 * существует только с API 33, объект инициализируется лишь при фактическом
 * обращении из ветки SDK >= 33.
 */
private object AgslGlass {
    private const val SKSL = """
        uniform shader uA;
        uniform shader uB;
        uniform float uMix;
        uniform float uAmp;
        half4 main(float2 fragCoord) {
            float2 off = float2(
                sin(fragCoord.y * 0.045 + sin(fragCoord.x * 0.013) * 1.7) * uAmp,
                cos(fragCoord.x * 0.038 + sin(fragCoord.y * 0.021) * 1.3) * uAmp * 0.6
            );
            half4 a = uA.eval(fragCoord + off);
            half4 b = uB.eval(fragCoord + off);
            return mix(a, b, uMix);
        }
    """
    val shader = RuntimeShader(SKSL)
}
