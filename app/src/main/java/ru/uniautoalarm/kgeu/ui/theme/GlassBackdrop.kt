package ru.uniautoalarm.kgeu.ui.theme

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Чистая математика стекла (0.17.0) — без Android/Compose-типов поверх чисел,
 * покрыта юнит-тестом GlassMathTest.
 *
 * Семантика ползунка «Матовость»: 0 — стекло ПРОЗРАЧНОЕ (фон виден один в один),
 * 1 — стекло МУТНОЕ (предельный блёр). Мáтовость ведёт по ЛЕСТНИЦЕ из
 * [LEVEL_COUNT] уровней размытия: каждая ступень — заметный скачок мутности.
 * Стекло БЕСЦВЕТНОЕ: подложки цветом темы поверх фрагмента нет —
 * сквозь стекло виден только фон, читаемость обеспечивает АДАПТИВНЫЙ цвет
 * содержимого (см. GlassPanel): светлый на тёмном фрагменте, тёмный на светлом.
 *
 * 0.17.0: ИСПРАВЛЕНА математика фрагмента (cropTopLeft) — раньше сдвиг слоя
 * не делился на масштаб, область сэмплирования уезжала за пределы картинки,
 * режим CLAMP растягивал крайние ряды → «отпечаток» и горизонтальные полосы.
 * Полосный фолбэк искажения (API < 33) удалён вовсе — вертикальные полосы
 * с изломами выглядели как артефакты; искажение теперь только AGSL (13+).
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

    /**
     * Левый-верхний угол фрагмента картинки (в ПИКСЕЛЯХ картинки), который
     * виден в точке слоя (dx, dy) при ContentScale.Crop: картинка вписана
     * по большей стороне слоя и отцентрирована. Раньше (0.14–0.16) сдвиг не
     * делился на масштаб — низ экрана сэмплировал ЗA нижней кромкой картинки,
     * CLAMP растягивал последний ряд в горизонтальные полосы.
     */
    fun cropTopLeft(
        layerWidth: Float,
        layerHeight: Float,
        bmpWidth: Float,
        bmpHeight: Float,
        dx: Float,
        dy: Float
    ): Pair<Float, Float> {
        val scale = max(layerWidth / bmpWidth, layerHeight / bmpHeight)
        val offX = (layerWidth - bmpWidth * scale) / 2f
        val offY = (layerHeight - bmpHeight * scale) / 2f
        return ((dx - offX) / scale) to ((dy - offY) / scale)
    }

    /** Масштаб «слой → пиксели картинки» при Crop (см. cropTopLeft). */
    fun cropScale(
        layerWidth: Float,
        layerHeight: Float,
        bmpWidth: Float,
        bmpHeight: Float
    ): Float = max(layerWidth / bmpWidth, layerHeight / bmpHeight)

    /** Яркость пикселя ARGB (0..1), Rec. 709. */
    fun pixelLuminance(argb: Int): Float {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Какой цвет содержимого нужен на фрагменте со средней яркостью [luma]:
     * на тёмном фоне — СВЕТЛЫЙ текст, на светлом — тёмный. Порог 0.55 —
     * чуть в сторону светлого текста: полупрозрачный тёмный фон читается хуже.
     */
    fun lightContentFor(luma: Float): Boolean = luma < 0.55f

    /**
     * Амплитуда искажения в пикселях для AGSL-шейдера (Android 13+).
     * 0.16.0: 4.5% → 12% высоты — искажение различимо глазом.
     */
    fun distortionAmp(distortion: Float, heightPx: Float): Float =
        distortion.coerceIn(0f, 1f) * heightPx * 0.12f
}

/**
 * Состояние фона «стекла» (0.17.0): ЛЕСТНИЦА копий фоновой картинки от резкой
 * до предельно размытой ([GlassMath.LEVEL_COUNT] уровней) + ПРОБА — маленькая
 * копия резкого уровня для локальной оценки яркости фрагмента под панелью
 * (адаптивный цвет текста: светлый на тёмном участке фона, тёмный на светлом).
 * Все копии строятся ОДИН раз на файл (ползунки не перезапускают декод);
 * мáтовость выбирает пару соседних уровней (GlassMath.levelMix).
 * origin/layerSize — геометрия слоя фоновой картинки в окне (пересчёт
 * «координата карточки → пиксель картинки», как при ContentScale.Crop).
 */
class GlassBackdropState {
    var levels by mutableStateOf<List<ImageBitmap>?>(null)

    /** Маленькая копия резкого уровня (по ширине ~72px) для проб яркости. */
    var probe by mutableStateOf<ImageBitmap?>(null)
    var origin by mutableStateOf(Offset.Zero)
    var layerSize by mutableStateOf(Size.Zero)
    var matteness by mutableStateOf(0.5f)
    var distortion by mutableStateOf(0.25f)
    var dark by mutableStateOf(false)
}

/** Локал состояния стекла; null — стекла нет (glassSurface вырождается в no-op). */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropState?> { null }

/**
 * Адаптивный цвет содержимого НА СТЕКЛЕ (0.17.0): GlassPanel вычисляет среднюю
 * яркость фрагмента фона ПОД панелью и подставляет светлый/тёмный цвет текста.
 * null — стекла нет (используется обычный цвет темы). Стекло при этом остаётся
 * БЕСЦВЕТНЫМ — тонировка не возвращается, адаптируется только ТЕКСТ.
 */
val LocalGlassContentColor = staticCompositionLocalOf<Color?> { null }

/** Светлый цвет содержимого на тёмном фрагменте (тёплый белый — палитра приложения). */
val GlassContentLight: Color = Color(0xFFFFF8F1)

/** Тёмный цвет содержимого на светлом фрагменте (тёплый чёрный — палитра приложения). */
val GlassContentDark: Color = Color(0xFF201B15)

/** Основной цвет текста/иконок на стеклянной панели; без стекла — [fallback]. */
@Composable
fun glassContentColor(fallback: Color): Color = LocalGlassContentColor.current ?: fallback

/** Второстепенный текст на стекле: адаптивный цвет с приглушением; без стекла — [fallback]. */
@Composable
fun glassSecondaryColor(fallback: Color): Color {
    val content = LocalGlassContentColor.current ?: return fallback
    return content.copy(alpha = 0.75f)
}

/** Линии/разделители на стекле: адаптивный цвет, ещё сильнее приглушён; без стекла — [fallback]. */
@Composable
fun glassHairlineColor(fallback: Color): Color {
    val content = LocalGlassContentColor.current ?: return fallback
    return content.copy(alpha = 0.30f)
}

/**
 * Цвет заливки стеклянной поверхности: при активном стекле — прозрачный
 * (фрагмент фона рисуется [glassSurface] под содержимым), без стекла —
 * обычный цвет Material. Вызывается на местах Card/Surface.
 */
@Composable
fun glassCardColor(fallback: Color): Color =
    if (LocalGlassBackdrop.current != null) Color.Transparent else fallback

/**
 * Стеклянная панель (0.17.0): рисует фрагмент фона ([glassSurface]) и
 * ПОДСТАВЛЯЕТ содержимому адаптивный цвет: по СРЕДНЕЙ ЯРКОСТИ фрагмента фона
 * под этой панелью (проба 3×3 из маленькой копии резкого уровня) — светлый
 * текст на тёмном участке фона, тёмный на светлом. Так карточка читаема и над
 * тёмным космосом, и над светлым платьем, а стекло остаётся бесцветным.
 * Без активного стекла панель — простой Box (цвета не подменяются).
 */
@Composable
fun GlassPanel(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = LocalGlassBackdrop.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val adaptive: Color? = remember(state) {
        derivedStateOf {
            val c = coords ?: return@derivedStateOf null
            val probe = state?.probe ?: return@derivedStateOf null
            val layer = state.layerSize
            if (layer.width < 1f || layer.height < 1f || !c.isAttached) {
                return@derivedStateOf null
            }
            val pos = c.positionInRoot()
            val size = IntSize(c.size.width, c.size.height)
            val luma = probeLuminance(probe, layer, state.origin, pos, size)
            if (GlassMath.lightContentFor(luma)) GlassContentLight else GlassContentDark
        }
    }.value
    val outerContent = LocalContentColor.current
    CompositionLocalProvider(
        LocalGlassContentColor provides adaptive,
        // содержимое без явных цветов (иконки, Text без color=) тоже адаптируется
        LocalContentColor provides (adaptive ?: outerContent)
    ) {
        Box(
            modifier
                .onGloballyPositioned { coords = it }
                .glassSurface(shape)
        ) {
            content()
        }
    }
}

/**
 * Готовая стеклянная карточка (0.17.0): GlassPanel + Card с прозрачной заливкой
 * при стекле и адаптивным цветом содержимого. Без стекла — обычная Material-карта
 * цветов ([fallbackContainer]/[fallbackContent]) — ровно как до стекла.
 */
@Composable
fun GlassCard(
    shape: Shape,
    fallbackContainer: Color,
    fallbackContent: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    GlassPanel(shape, modifier) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = glassCardColor(fallbackContainer),
                contentColor = glassContentColor(fallbackContent)
            ),
            border = border,
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

/**
 * Средняя яркость фрагмента фона под элементом: проба 3×3 из маленькой копии
 * резкого уровня, координаты — через исправленную математику Crop.
 */
private fun probeLuminance(
    probe: ImageBitmap,
    layer: Size,
    origin: Offset,
    pos: Offset,
    size: IntSize
): Float {
    val ab = probe.asAndroidBitmap()
    val scale = GlassMath.cropScale(
        layer.width, layer.height, probe.width.toFloat(), probe.height.toFloat()
    )
    val (tx, ty) = GlassMath.cropTopLeft(
        layer.width, layer.height, probe.width.toFloat(), probe.height.toFloat(),
        pos.x - origin.x, pos.y - origin.y
    )
    val spanX = size.width / scale
    val spanY = size.height / scale
    var sum = 0f
    var n = 0
    for (gy in 0..2) {
        for (gx in 0..2) {
            val px = (tx + spanX * gx / 2f).toInt().coerceIn(0, probe.width - 1)
            val py = (ty + spanY * gy / 2f).toInt().coerceIn(0, probe.height - 1)
            sum += GlassMath.pixelLuminance(ab.getPixel(px, py))
            n++
        }
    }
    return if (n == 0) 0f else sum / n
}

/**
 * Стеклянная поверхность (0.16.0): рисует ПОД содержимым элемента точный
 * фрагмент фоновой картинки (BitmapShader-матрица отображает координаты
 * элемента в пиксели картинки 1:1 — стекло «прозрачно», а не «отпечаток»),
 * смешанный по мáтовости из пары соседних уровней лестницы размытия.
 * Стекло БЕСЦВЕТНОЕ: только фрагмент фона (степень мутности — по мáтовости)
 * и объёмные кромки; никакой подложки цветом темы (0.16.0: убрана — она
 * тонировала стекло цветом светлой/тёмной темы, пользователь видел «карточку»).
 * Искажение — AGSL-шейдер на Android 13+ (0.17.0: ниже API 33 искажения нет —
 * полосный фолбэк удалён как выглядящий артефактами).
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
 * 0.17.0: математика через [GlassMath.cropTopLeft] — сдвиг ДЕЛИТСЯ на масштаб
 * (раньше область сэмплирования уезжала за пределы картинки, CLAMP растягивал
 * крайние ряды в горизонтальные полосы, а «стекло» показывало отпечаток со сдвигом).
 * Уровни лестницы могут отличаться разрешением — у каждого своя матрица.
 */
private fun cropMatrix(
    layer: Size,
    origin: Offset,
    elementPos: Offset,
    bmpWidth: Float,
    bmpHeight: Float
): Matrix {
    val scale = GlassMath.cropScale(layer.width, layer.height, bmpWidth, bmpHeight)
    val (topLeftX, topLeftY) = GlassMath.cropTopLeft(
        layer.width, layer.height, bmpWidth, bmpHeight,
        elementPos.x - origin.x, elementPos.y - origin.y
    )
    return Matrix().apply {
        postScale(1f / scale, 1f / scale)
        postTranslate(topLeftX, topLeftY)
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
                // фолбэк (API < 33): нижний уровень целиком + следующий поверх
                // с альфой доли. 0.17.0: полосное «искажение» удалено — вертикальные
                // полосы со сдвигами выглядели как артефакты (горизонтальные швы);
                // искажение теперь существует только в AGSL на Android 13+.
                val paintA = Paint().apply { isFilterBitmap = true; shader = shaderA }
                val paintB = Paint().apply {
                    isFilterBitmap = true; shader = shaderB
                    alpha = (fraction * 255f).toInt().coerceIn(0, 255)
                }
                native.drawRect(0f, 0f, elementSize.width, elementSize.height, paintA)
                native.drawRect(0f, 0f, elementSize.width, elementSize.height, paintB)
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
