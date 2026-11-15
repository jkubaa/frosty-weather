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

package org.breezyweather.ui.common.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How a piece of liquid glass looks and behaves. Every glass element in the app is one of the
 * presets below, adjusted with [copy] where it needs to (a capsule's own radius, a tinted body).
 * Tune a preset here and everything made of it follows.
 *
 * @param cornerRadius radius of the rounded shape (half the height for a capsule)
 * @param tint body colour over the blurred backdrop; null for the theme's default glass body
 * @param refraction how strongly light bends at the rounded rim (Android 13+)
 * @param saturation colour boost of the backdrop seen through the glass (vibrancy)
 * @param blurScale how frosted, relative to the user's "Glass blur" setting (small controls want less)
 * @param rim a thin rim of light around the glass
 * @param shadow drop shadow under floating glass (buttons), 0 for none
 * @param press how the glass reacts while pressed, null for no reaction
 */
@Immutable
data class GlassStyle(
    val cornerRadius: Dp,
    val tint: Color? = null,
    val refraction: Float = LiquidGlassBlur.REFRACTION_STRENGTH,
    val saturation: Float = LiquidGlassBlur.SATURATION,
    val blurScale: Float = 1f,
    val rim: Boolean = false,
    val shadow: Dp = 0.dp,
    val press: GlassPress? = null,
) {
    companion object {
        /** Home cards and tiles, over the sky. */
        val Card = GlassStyle(
            cornerRadius = GlassCornerRadius,
            refraction = GlassEdgeRefraction,
            saturation = GlassVividSaturation,
            rim = true
        )

        /** The home bottom bar and its buttons: clear and vivid. Set [cornerRadius] to half the bar height. */
        val Bar = GlassStyle(
            cornerRadius = 24.dp,
            refraction = GlassEdgeRefraction,
            saturation = GlassBarSaturation
        )

        /** The pull-to-refresh bubble. */
        val Bubble = GlassStyle(cornerRadius = 22.dp)

        /**
         * Floating glass buttons over plain backgrounds (sheets, settings, detail screens): no
         * backdrop, a light body, a rim and a shadow; swells and brightens while pressed.
         */
        val Button = GlassStyle(
            cornerRadius = 22.dp,
            tint = Color.White,
            rim = true,
            shadow = 6.dp,
            press = GlassPress(scale = 1.12f, bodyAlpha = 0.78f, pressedBodyAlpha = 0.95f)
        )

        /**
         * A small lens over a control (the toggle thumb): nearly clear, gently frosted, natural
         * colours, so what is under it stays readable as it bends at the rim.
         */
        val Lens = GlassStyle(
            cornerRadius = 12.dp,
            tint = Color.White.copy(alpha = 0.06f),
            saturation = 1.05f,
            blurScale = 0.12f,
            rim = true
        )
    }
}

/**
 * Pressed-state reaction of a [GlassStyle]: the glass swells and its body becomes denser, like iOS
 * glass controls.
 */
@Immutable
data class GlassPress(
    val scale: Float = 1.12f,
    val bodyAlpha: Float = 0.78f,
    val pressedBodyAlpha: Float = 0.95f,
)

/**
 * Liquid glass of the given [style] behind this element's content, showing [backdrop] (null for
 * glass over a plain background). Pass [interactionSource] (the one given to the element's
 * clickable) for styles with a [GlassStyle.press] reaction.
 */
fun Modifier.liquidGlass(
    style: GlassStyle,
    backdrop: GlassBackdrop?,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val press = style.press
    val pressed = interactionSource?.collectIsPressedAsState()?.value == true
    val tint = if (press != null && style.tint != null) {
        style.tint.copy(alpha = if (pressed) press.pressedBodyAlpha else press.bodyAlpha)
    } else {
        style.tint
    }
    val floating = if (press != null || style.shadow > 0.dp) {
        val scale by animateFloatAsState(
            targetValue = if (pressed && press != null) press.scale else 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
            label = "glassPress"
        )
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            if (style.shadow > 0.dp) {
                shadowElevation = style.shadow.toPx()
                shape = RoundedCornerShape(style.cornerRadius)
                clip = true
                ambientShadowColor = Color.Black.copy(alpha = 0.3f)
                spotShadowColor = Color.Black.copy(alpha = 0.3f)
            }
        }
    } else {
        Modifier
    }
    this
        .then(floating)
        .liquidGlassMaterial(
            backdrop = backdrop,
            tint = tint,
            cornerRadius = style.cornerRadius,
            refractionStrength = style.refraction,
            saturation = style.saturation,
            rim = style.rim,
            blurScale = style.blurScale
        )
}

/**
 * The glass of [style], to draw by hand in any rounded box (see [GlassPainter.drawGlass]), for
 * elements that place or animate their glass themselves.
 */
@Composable
fun rememberGlassPainter(style: GlassStyle, backdrop: GlassBackdrop?): GlassPainter = rememberGlassPainter(
    backdrop = backdrop,
    tint = style.tint,
    cornerRadius = style.cornerRadius,
    refractionStrength = style.refraction,
    saturation = style.saturation,
    rim = style.rim,
    blurScale = style.blurScale
)
