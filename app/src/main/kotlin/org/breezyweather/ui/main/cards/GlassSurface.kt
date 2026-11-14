/*
 * This file is part of Frosty Weather, a modified version of Breezy Weather.
 *
 * Frosty Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Frosty Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Frosty Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.ui.main.cards

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.common.glass.LiquidGlass
import org.breezyweather.ui.common.glass.LiquidGlassBlur
import org.breezyweather.ui.common.glass.LiquidGlassDrawable
import kotlin.math.roundToInt

/** Corner radius of the glass cards (glass_corner_radius). */
val GlassCornerRadius = 26.dp

/**
 * Liquid glass in Compose, the same material as the View-based [LiquidGlass]: the sky behind the
 * element, blurred, with boosted colors (vibrancy) and, on Android 13+, bent towards the inside near
 * the rounded edges (lens refraction); then a translucent tint, the content, and soft rims of light
 * along the top and bottom edges.
 *
 * Instead of capturing the screen, the element draws the part of the sky ([backdrop]) that is under
 * it and lets the GPU blur it. Without blur (Android < 12, or the "Glass blur" setting at 0), it is a
 * plain translucent tint, like the View version.
 *
 * @param tint body color over the blur; null for the theme's default glass body
 */
fun Modifier.liquidGlass(
    backdrop: GlassBackdrop?,
    tint: Color? = null,
    cornerRadius: Dp = GlassCornerRadius,
): Modifier = composed {
    val glass = rememberGlassPainter(backdrop, tint, cornerRadius)
    val hidden = LocalGlassHidden.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }

    this
        // Read at draw time: scrolling moves the card, so its piece of sky is redrawn
        .onGloballyPositioned {
            coordinates[0] = it
            origin = it.positionInWindow()
        }
        .clip(RoundedCornerShape(cornerRadius))
        .drawWithContent {
            // Always read the tracked position: that read is what redraws the glass whenever the
            // element moves (scrolling), since the sky no longer redraws it every frame.
            // Then take the exact position now, which also covers moves without a layout (dragging)
            val tracked = origin
            if (hidden?.invoke() == true) {
                drawContent()
            } else {
                val at = coordinates[0]?.takeIf { it.isAttached }?.positionInWindow() ?: tracked
                with(glass) { drawGlass(Rect(Offset.Zero, size), at) { this@drawWithContent.drawContent() } }
            }
        }
}

/**
 * Tells the [liquidGlass] under it to draw its content only, no glass, while this returns true (read
 * at draw time): the card list draws the glass of a collapsing card itself, in its real place.
 */
val LocalGlassHidden = compositionLocalOf<(() -> Boolean)?> { null }

/** The [liquidGlass] material, to draw by hand in any rounded box (see [GlassPainter.drawGlass]). */
@Composable
fun rememberGlassPainter(
    backdrop: GlassBackdrop?,
    tint: Color? = null,
    cornerRadius: Dp = GlassCornerRadius,
): GlassPainter {
    val context = LocalContext.current
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val isLight = LiquidGlass.isLightSurface(context)
    val body = tint ?: Color(context.getThemeColor(com.google.android.material.R.attr.colorSurface))
        .copy(alpha = if (isLight) LiquidGlass.BODY_ALPHA_LIGHT else LiquidGlass.BODY_ALPHA_DARK)
    val intensity = SettingsManager.getInstance(context).glassBlurIntensity
    val frosted = backdrop != null && LiquidGlass.isBlurEnabled(context)
    // BlurView blurred a 1/6 scale snapshot: the same look at full resolution needs 6x the radius
    val blurPx = intensity / 100f * LiquidGlass.MAX_BLUR_RADIUS * BLUR_SCALE
    val edgePx = minOf(radiusPx * 0.6f, with(density) { LiquidGlass.REFRACTION_EDGE_DP.dp.toPx() })
    val layer = rememberGraphicsLayer()
    return remember(backdrop, body, frosted, blurPx, radiusPx, edgePx, isLight, layer) {
        GlassPainter(backdrop?.takeIf { frosted }, body, blurPx, radiusPx, edgePx, isLight, layer)
    }
}

/** Draws the liquid glass material (sky blur, tint, rims of light) in a box. */
class GlassPainter internal constructor(
    private val backdrop: GlassBackdrop?,
    private val body: Color,
    private val blurPx: Float,
    private val radiusPx: Float,
    private val edgePx: Float,
    private val isLight: Boolean,
    private val layer: GraphicsLayer,
) {
    private val effects = EffectCache()
    private var sheenRadius = radiusPx
    private var sheen = LiquidGlassDrawable(radiusPx, isLight = isLight)

    /**
     * Draws the glass in [box] (the caller clips it to its rounded shape), [content] over the tint
     * and under the rims of light. [windowPosition]: where the box's top left corner is in the
     * window, which picks the piece of sky behind it.
     */
    fun DrawScope.drawGlass(
        box: Rect,
        windowPosition: Offset,
        cornerRadius: Float = radiusPx,
        content: (DrawScope.() -> Unit)? = null,
    ) {
        if (box.width <= 0f || box.height <= 0f) return
        if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdrop.isReady) {
            layer.renderEffect = effects.get(box.size, blurPx, cornerRadius, minOf(edgePx, cornerRadius * 0.6f))
            layer.topLeft = IntOffset(box.left.roundToInt(), box.top.roundToInt())
            layer.record(size = IntSize(box.width.roundToInt(), box.height.roundToInt())) {
                drawIntoCanvas { backdrop.drawBackdrop(it.nativeCanvas, windowPosition) }
            }
            drawLayer(layer)
        }
        drawRect(body, box.topLeft, box.size)
        content?.invoke(this)
        if (cornerRadius != sheenRadius) {
            // Rims to match the corners (tighter in a box too short for the usual ones)
            sheenRadius = cornerRadius
            sheen = LiquidGlassDrawable(cornerRadius, isLight = isLight)
        }
        sheen.setBounds(
            box.left.roundToInt(),
            box.top.roundToInt(),
            box.right.roundToInt(),
            box.bottom.roundToInt()
        )
        drawIntoCanvas { sheen.draw(it.nativeCanvas) }
    }
}

private const val BLUR_SCALE = 6f

/** The blur + vibrancy + refraction effect depends on the element's size: rebuilt only when it changes. */
private class EffectCache {
    private var key: List<Float>? = null
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    fun get(size: Size, blur: Float, cornerRadius: Float, edge: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || size.width <= 0f || size.height <= 0f) return null
        val newKey = listOf(size.width, size.height, blur, cornerRadius, edge)
        if (newKey != key) {
            key = newKey
            effect = build(size, blur, cornerRadius, edge).asComposeRenderEffect()
        }
        return effect
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun build(size: Size, blur: Float, cornerRadius: Float, edge: Float): RenderEffect {
        val blurred = RenderEffect.createBlurEffect(blur.coerceAtLeast(0.1f), blur.coerceAtLeast(0.1f), Shader.TileMode.CLAMP)
        val vibrancy = RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(LiquidGlassBlur.SATURATION) }),
            blurred
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return vibrancy
        return refraction(vibrancy, size, cornerRadius, edge)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun refraction(input: RenderEffect, size: Size, cornerRadius: Float, edge: Float): RenderEffect {
        val shader = RuntimeShader(LiquidGlassBlur.REFRACTION_SHADER).apply {
            setFloatUniform("size", size.width, size.height)
            setFloatUniform("cornerRadius", cornerRadius.coerceAtMost(minOf(size.width, size.height) / 2f))
            setFloatUniform("edge", edge.coerceAtLeast(1f))
            setFloatUniform("strength", edge * LiquidGlassBlur.REFRACTION_STRENGTH)
        }
        return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"), input)
    }
}
