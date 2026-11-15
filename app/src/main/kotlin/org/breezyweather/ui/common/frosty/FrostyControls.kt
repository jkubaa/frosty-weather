/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.ui.common.frosty

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import org.breezyweather.ui.common.glass.GlassStyle
import org.breezyweather.ui.common.glass.LensBackdrop
import org.breezyweather.ui.common.glass.rememberGlassPainter
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

/* ---------------------------------------------------------------------------------------------- */
/* Press feedback                                                                                 */
/* ---------------------------------------------------------------------------------------------- */

/**
 * iOS cell highlight: a flat colour behind the content that appears on touch down and fades out
 * after release, so even a quick tap shows it.
 */
class FrostyHighlight(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        HighlightNode(interactionSource, color)

    override fun equals(other: Any?) = other is FrostyHighlight && other.color == color
    override fun hashCode() = color.hashCode()

    private class HighlightNode(
        private val interactionSource: InteractionSource,
        private val color: Color,
    ) : Modifier.Node(), DrawModifierNode {
        private val alpha = Animatable(0f)
        private var fade: Job? = null

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> {
                            fade?.cancel()
                            alpha.snapTo(1f)
                            invalidateDraw()
                        }
                        is PressInteraction.Release, is PressInteraction.Cancel -> {
                            fade?.cancel()
                            fade = coroutineScope.launch {
                                alpha.animateTo(0f, tween(durationMillis = 380))
                            }
                        }
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            val a = alpha.value
            if (a > 0f) drawRect(color, alpha = a)
            drawContent()
        }
    }
}

/**
 * iOS feedback for borderless buttons (icons, text links): the content dims while pressed.
 */
object FrostyDim : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = DimNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7

    private class DimNode(private val interactionSource: InteractionSource) : Modifier.Node(), DrawModifierNode {
        private val alpha = Animatable(1f)
        private var fade: Job? = null

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> {
                            fade?.cancel()
                            alpha.snapTo(0.35f)
                            invalidateDraw()
                        }
                        is PressInteraction.Release, is PressInteraction.Cancel -> {
                            fade?.cancel()
                            fade = coroutineScope.launch { alpha.animateTo(1f, tween(durationMillis = 260)) }
                        }
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            val a = alpha.value
            if (a >= 1f) {
                drawContent()
            } else {
                drawContext.canvas.saveLayer(
                    androidx.compose.ui.geometry.Rect(Offset.Zero, size),
                    androidx.compose.ui.graphics.Paint().apply { this.alpha = a }
                )
                drawContent()
                drawContext.canvas.restore()
            }
        }
    }
}

/**
 * Clickable with the iOS cell highlight.
 */
@Composable
fun Modifier.frostyClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = FrostyHighlight(frostyColors.highlight),
    enabled = enabled,
    role = role,
    onClick = onClick
)

/**
 * Clickable that dims its content while pressed, for borderless buttons.
 */
@Composable
fun Modifier.frostyDimClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = FrostyDim,
    enabled = enabled,
    role = role,
    onClick = onClick
)

/* ---------------------------------------------------------------------------------------------- */
/* Glyphs                                                                                         */
/* ---------------------------------------------------------------------------------------------- */

enum class ChevronDirection { Right, Left, Down, UpDown }

/**
 * SF Symbols-like chevron drawn with strokes.
 */
@Composable
fun FrostyChevron(
    modifier: Modifier = Modifier,
    direction: ChevronDirection = ChevronDirection.Right,
    color: Color = frostyColors.tertiaryLabel,
    size: Dp = 13.dp,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path()
        when (direction) {
            ChevronDirection.Right -> {
                path.moveTo(w * 0.34f, h * 0.12f)
                path.lineTo(w * 0.70f, h * 0.5f)
                path.lineTo(w * 0.34f, h * 0.88f)
            }
            ChevronDirection.Left -> {
                path.moveTo(w * 0.66f, h * 0.12f)
                path.lineTo(w * 0.30f, h * 0.5f)
                path.lineTo(w * 0.66f, h * 0.88f)
            }
            ChevronDirection.Down -> {
                path.moveTo(w * 0.14f, h * 0.36f)
                path.lineTo(w * 0.5f, h * 0.70f)
                path.lineTo(w * 0.86f, h * 0.36f)
            }
            ChevronDirection.UpDown -> {
                path.moveTo(w * 0.24f, h * 0.36f)
                path.lineTo(w * 0.5f, h * 0.12f)
                path.lineTo(w * 0.76f, h * 0.36f)
                path.moveTo(w * 0.24f, h * 0.64f)
                path.lineTo(w * 0.5f, h * 0.88f)
                path.lineTo(w * 0.76f, h * 0.64f)
            }
        }
        drawPath(path, color, style = stroke)
    }
}

/**
 * The checkmark of a selected row.
 */
@Composable
fun FrostyCheckmark(
    modifier: Modifier = Modifier,
    color: Color = frostyColors.tint,
    size: Dp = 18.dp,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.54f)
            lineTo(w * 0.40f, h * 0.82f)
            lineTo(w * 0.90f, h * 0.18f)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * An "xmark" glyph.
 */
@Composable
fun FrostyXMark(
    modifier: Modifier = Modifier,
    color: Color = frostyColors.label,
    size: Dp = 14.dp,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.12f, h * 0.12f), Offset(w * 0.88f, h * 0.88f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.88f, h * 0.12f), Offset(w * 0.12f, h * 0.88f), stroke.width, StrokeCap.Round)
    }
}

/* ---------------------------------------------------------------------------------------------- */
/* Toggle                                                                                         */
/* ---------------------------------------------------------------------------------------------- */

private val ToggleWidth = 62.dp
private val ToggleHeight = 28.dp
private val ThumbWidth = 38.dp
private val ThumbHeight = 24.dp
private val ThumbInset = 2.dp

/** How much the thumb swells into a glass lens while touched (iOS 26). */
private const val GlassThumbScale = 1.42f

/** How much the glass lens magnifies what is under it (below 1 it takes in more, like a fisheye, so the track's edges curve through it). */
private const val LensMagnification = 0.82f

/**
 * iOS 26 toggle: a capsule track that turns green, with a white pill thumb. While touched the
 * thumb swells into a clear liquid glass lens (the app's glass material, with rims of light) that
 * can be dragged across; letting go settles it back into a solid pill.
 */
@Composable
fun FrostySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = frostyColors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val travel = ToggleWidth - ThumbWidth - ThumbInset * 2
    val travelPx = with(density) { travel.toPx() }
    val currentChecked by rememberUpdatedState(checked)
    val onChange by rememberUpdatedState(onCheckedChange)

    var touched by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val settled by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "toggle"
    )
    val fraction = dragFraction ?: settled
    val track by animateColorAsState(
        targetValue = if ((dragFraction ?: if (checked) 1f else 0f) > 0.5f) colors.green else colors.fill,
        animationSpec = tween(220),
        label = "toggleTrack"
    )
    // 0 = solid white pill, 1 = glass lens
    val glass by animateFloatAsState(
        targetValue = if (touched) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = if (touched) 700f else 380f),
        label = "toggleGlass"
    )
    // The glass bends and blurs what is under it, like the home bottom bar: here the track and
    // the cell around it, drawn again into the lens
    val cellColor = frostyCellColor()
    val under = remember { ToggleUnderLens() }
    val backdrop = remember { LensBackdrop(magnification = LensMagnification) { under.draw(it) } }
    val glassPainter = rememberGlassPainter(
        GlassStyle.Lens.copy(
            cornerRadius = ThumbHeight / 2,
            tint = Color.White.copy(alpha = if (colors.isDark) 0.06f else 0.12f)
        ),
        backdrop
    )

    val interactive = enabled && onCheckedChange != null
    val gestures = if (interactive) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                touched = true
                val start = down.position
                val base = if (currentChecked) 1f else 0f
                var dragging = false
                var cancelled = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - start.x
                    val dy = change.position.y - start.y
                    if (!dragging) {
                        if (abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) {
                            dragging = true
                        } else if (abs(dy) > viewConfiguration.touchSlop) {
                            // A vertical scroll that started on the toggle: leave it alone
                            cancelled = true
                            break
                        }
                    }
                    if (dragging) {
                        dragFraction = (base + dx / travelPx).coerceIn(0f, 1f)
                        change.consume()
                    }
                }
                val newValue = when {
                    cancelled -> currentChecked
                    dragging -> (dragFraction ?: base) > 0.5f
                    else -> !currentChecked
                }
                touched = false
                dragFraction = null
                if (newValue != currentChecked) {
                    haptics.performHapticFeedback(if (newValue) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onChange?.invoke(newValue)
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .size(ToggleWidth, ToggleHeight)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                if (interactive) {
                    onClick {
                        onChange?.invoke(!currentChecked)
                        true
                    }
                }
            }
            .then(gestures)
    ) {
        // Track
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(track)
        )
        // Thumb: drawn unclipped so the glass lens can overflow the track
        Box(
            modifier = Modifier
                .offset { IntOffset(((ThumbInset + travel * fraction).toPx()).roundToInt(), ThumbInset.roundToPx()) }
                .size(ThumbWidth, ThumbHeight)
                .graphicsLayer {
                    val scale = 1f + (GlassThumbScale - 1f) * glass
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = (2f + 4f * glass).dp.toPx()
                    shape = CircleShape
                    clip = true
                    ambientShadowColor = Color.Black.copy(alpha = 0.25f)
                    spotShadowColor = Color.Black.copy(alpha = 0.25f)
                }
                .drawWithContent {
                    under.track = track
                    under.cell = cellColor
                    under.width = ToggleWidth.toPx()
                    under.height = ToggleHeight.toPx()
                    // Glass lens fading in over the solid pill as it lifts
                    if (glass < 1f) drawRect(Color.White, alpha = 1f - glass)
                    if (glass > 0f) {
                        val layerAlpha = glass.coerceIn(0f, 1f)
                        drawContext.canvas.saveLayer(
                            Rect(Offset.Zero, size),
                            androidx.compose.ui.graphics.Paint().apply { alpha = layerAlpha }
                        )
                        // The lens sees the toggle around the thumb's centre, scaled back to its real
                        // size (the thumb is drawn swollen) and slightly magnified
                        backdrop.place(
                            elementCenter = Offset(size.width / 2f, size.height / 2f),
                            elementScale = 1f + (GlassThumbScale - 1f) * glass
                        )
                        val at = Offset(
                            (ThumbInset + travel * fraction).toPx() + size.width / 2f,
                            ThumbInset.toPx() + size.height / 2f
                        )
                        with(glassPainter) { drawGlass(Rect(Offset.Zero, size), at) }
                        drawContext.canvas.restore()
                    }
                }
        )
    }
}

/**
 * What a toggle's glass thumb sees under it: the cell colour around the track and the track
 * itself, in the toggle's own coordinates.
 */
private class ToggleUnderLens {
    var track: Color = Color.Gray
    var cell: Color = Color.Black
    var width = 0f
    var height = 0f
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: android.graphics.Canvas) {
        paint.color = cell.toArgb()
        canvas.drawRect(-height * 2, -height * 2, width + height * 2, height * 3, paint)
        paint.color = track.toArgb()
        canvas.drawRoundRect(0f, 0f, width, height, height / 2, height / 2, paint)
    }
}

/* ---------------------------------------------------------------------------------------------- */
/* Slider                                                                                         */
/* ---------------------------------------------------------------------------------------------- */

/**
 * iOS slider: a thin track filled in the tint colour up to a white pill thumb. Snaps to [steps]
 * stops between the ends like Material's slider.
 */
@Composable
fun FrostySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = frostyColors
    val density = LocalDensity.current
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val thumbW = 38.dp
    val thumbH = 24.dp
    val thumbWPx = with(density) { thumbW.toPx() }
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span == 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)

    fun snap(f: Float): Float {
        val clamped = f.coerceIn(0f, 1f)
        if (steps <= 0) return valueRange.start + clamped * span
        val segments = steps + 1
        val idx = (clamped * segments).roundToInt()
        return valueRange.start + idx.toFloat() / segments * span
    }

    fun valueAt(x: Float): Float {
        val usable = (widthPx - thumbWPx).coerceAtLeast(1f)
        return snap((x - thumbWPx / 2f) / usable)
    }

    val scale by animateFloatAsState(if (dragging) 1.18f else 1f, spring(0.6f, 700f), label = "sliderThumb")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(valueRange, steps) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    dragging = true
                                    onValueChange(valueAt(offset.x))
                                },
                                onDragEnd = {
                                    dragging = false
                                    onValueChangeFinished?.invoke()
                                },
                                onDragCancel = {
                                    dragging = false
                                    onValueChangeFinished?.invoke()
                                }
                            ) { change, _ ->
                                val newValue = valueAt(change.position.x)
                                if (steps > 0 && newValue != value) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                                onValueChange(newValue)
                            }
                        }
                        .pointerInput(valueRange, steps) {
                            detectTapGestures { offset ->
                                onValueChange(valueAt(offset.x))
                                onValueChangeFinished?.invoke()
                            }
                        }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        val usable = maxWidth - thumbW
        Box(
            Modifier
                .padding(horizontal = thumbW / 2)
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.fill)
        )
        Box(
            Modifier
                .padding(start = thumbW / 2)
                .width(usable * fraction)
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.tint)
        )
        Box(
            Modifier
                .offset(x = usable * fraction)
                .size(thumbW, thumbH)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(3.dp, CircleShape, ambientColor = Color.Black.copy(0.25f), spotColor = Color.Black.copy(0.25f))
                .background(Color.White, CircleShape)
        )
    }
}

/* ---------------------------------------------------------------------------------------------- */
/* Buttons                                                                                        */
/* ---------------------------------------------------------------------------------------------- */

enum class FrostyButtonStyle { Gray, Filled, Plain }

/**
 * iOS 26 capsule button.
 */
@Composable
fun FrostyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: FrostyButtonStyle = FrostyButtonStyle.Gray,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = frostyColors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(0.6f, 800f), label = "buttonPress")
    val container = when (style) {
        FrostyButtonStyle.Gray -> colors.tertiaryFill
        FrostyButtonStyle.Filled -> colors.tint
        FrostyButtonStyle.Plain -> Color.Transparent
    }
    val pressedOverlay by animateColorAsState(
        if (pressed) {
            if (colors.isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        label = "buttonOverlay"
    )
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .background(container)
            .background(pressedOverlay)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .alpha(if (enabled) 1f else 0.4f)
            .padding(contentPadding),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides FrostyType.headline.copy(
                color = if (style == FrostyButtonStyle.Filled) Color.White else colors.tint
            )
        ) {
            content()
        }
    }
}

/**
 * Set by alerts and sheets so the drop-in [org.breezyweather.ui.common.composables.TextButton]
 * draws as a full-width capsule.
 */
internal val LocalFrostyDialogButtons = staticCompositionLocalOf { false }
