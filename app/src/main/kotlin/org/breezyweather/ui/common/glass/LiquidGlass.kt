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

package org.breezyweather.ui.common.glass

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import org.breezyweather.R
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.getThemeColor

/**
 * "Liquid glass" surface, inspired by the translucent panels of iOS weather apps.
 *
 * It is made of three layers:
 * 1. a translucent tinted body (drawn by the host, e.g. the card background color),
 * 2. a soft specular sheen falling from the top edge,
 * 3. a thin rim whose light fades from the top-left edge to the bottom-right edge,
 *    which gives the panel its "glass" thickness.
 *
 * This drawable renders layers 2 and 3 and optionally layer 1 (see [fillColor]),
 * so it can be used either as an overlay on a card or as a standalone background.
 */
class LiquidGlassDrawable(
    private var cornerRadius: Float,
    private val rimWidth: Float,
    private val fillColor: Int = Color.TRANSPARENT,
    private val isLight: Boolean = false,
) : Drawable() {

    private val panelBounds = RectF()
    private val rimBounds = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidth
    }

    fun setCornerRadius(radius: Float) {
        if (radius != cornerRadius) {
            cornerRadius = radius
            invalidateSelf()
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        panelBounds.set(bounds)
        val half = rimWidth / 2f
        rimBounds.set(
            bounds.left + half,
            bounds.top + half,
            bounds.right - half,
            bounds.bottom - half
        )

        val top = bounds.top.toFloat()
        val height = bounds.height().toFloat().coerceAtLeast(1f)
        // Sheen: bright at the top edge, vanishing within ~45% of the panel height
        sheenPaint.shader = LinearGradient(
            0f,
            top,
            0f,
            top + height * 0.45f,
            intArrayOf(
                Color.argb(if (isLight) 70 else 38, 255, 255, 255),
                Color.argb(if (isLight) 14 else 8, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP
        )
        // Rim: light catches the top-left edge, fades along the sides, picks up again faintly at the bottom
        rimPaint.shader = LinearGradient(
            bounds.left.toFloat(),
            top,
            bounds.left + bounds.width() * 0.6f,
            bounds.bottom.toFloat(),
            intArrayOf(
                Color.argb(if (isLight) 200 else 140, 255, 255, 255),
                Color.argb(if (isLight) 60 else 28, 255, 255, 255),
                Color.argb(if (isLight) 90 else 50, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (panelBounds.isEmpty) return
        if (Color.alpha(fillColor) > 0) {
            canvas.drawRoundRect(panelBounds, cornerRadius, cornerRadius, fillPaint)
        }
        canvas.drawRoundRect(panelBounds, cornerRadius, cornerRadius, sheenPaint)
        val rimRadius = (cornerRadius - rimWidth / 2f).coerceAtLeast(0f)
        canvas.drawRoundRect(rimBounds, rimRadius, rimRadius, rimPaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha * Color.alpha(fillColor) / 255
        sheenPaint.alpha = alpha
        rimPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        sheenPaint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

object LiquidGlass {

    /** Opacity of the tinted glass body, on top of the animated weather background. */
    private const val BODY_ALPHA_LIGHT = 0.42f
    private const val BODY_ALPHA_DARK = 0.30f

    private fun isLightSurface(view: View): Boolean {
        val surface = view.context.getThemeColor(com.google.android.material.R.attr.colorSurface)
        return ColorUtils.calculateLuminance(surface) > 0.5
    }

    /**
     * Tinted, translucent body color for a glass panel.
     * Uses the current theme surface so text contrast keeps following the light/dark weather theme.
     */
    fun bodyColor(view: View): Int {
        val surface = view.context.getThemeColor(com.google.android.material.R.attr.colorSurface)
        val alpha = if (isLightSurface(view)) BODY_ALPHA_LIGHT else BODY_ALPHA_DARK
        return ColorUtils.setAlphaComponent(surface, (alpha * 255).toInt())
    }

    /**
     * Turns a [MaterialCardView] into a liquid glass panel:
     * translucent tinted body, no shadow, specular sheen and a light-catching rim.
     * Safe to call several times on the same card (e.g. when a ViewHolder is rebound).
     */
    fun applyTo(card: MaterialCardView) {
        val isLight = isLightSurface(card)
        card.setCardBackgroundColor(ColorStateList.valueOf(bodyColor(card)))
        card.cardElevation = 0f
        card.elevation = 0f
        card.strokeWidth = 0
        card.radius = card.context.resources.getDimension(R.dimen.glass_corner_radius)

        val previous = card.getTag(R.id.tag_liquid_glass_overlay) as? Pair<*, *>
        if (previous != null) {
            card.overlay.remove(previous.first as Drawable)
            card.removeOnLayoutChangeListener(previous.second as View.OnLayoutChangeListener)
        }

        val overlay = LiquidGlassDrawable(
            cornerRadius = card.radius,
            rimWidth = card.context.dpToPx(1f),
            isLight = isLight
        )
        overlay.setBounds(0, 0, card.width, card.height)
        val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            overlay.setBounds(0, 0, v.width, v.height)
        }
        card.overlay.add(overlay)
        card.addOnLayoutChangeListener(listener)
        card.setTag(R.id.tag_liquid_glass_overlay, overlay to listener)
    }

    /**
     * Standalone glass background (e.g. for pills and capsules).
     */
    fun background(view: View, cornerRadius: Float): Drawable {
        return LiquidGlassDrawable(
            cornerRadius = cornerRadius,
            rimWidth = view.context.dpToPx(1f),
            fillColor = bodyColor(view),
            isLight = isLightSurface(view)
        )
    }
}
