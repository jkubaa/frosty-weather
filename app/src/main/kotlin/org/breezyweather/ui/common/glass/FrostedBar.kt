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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnAttach
import eightbitlab.com.blurview.BlurView
import org.breezyweather.common.extensions.dpToPx

/**
 * Frosted bar (e.g. behind a screen's top buttons and title): blurs what scrolls under it in a root
 * view, with a tint on top so text over it stays readable, and a bottom edge that fades out softly
 * instead of ending on a hard line, like the scroll edge of iOS bars.
 *
 * Without blur (Android < 12, or the "Glass blur" setting at 0) it falls back to a dense tint.
 */
class FrostedBar(context: Context) : FrameLayout(context) {

    private val blurView = BlurView(context)
    private val fallback = View(context)
    private val fadeLength = context.dpToPx(FADE_DP)
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    @ColorInt
    private var tint: Int = Color.TRANSPARENT

    init {
        addView(fallback, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(blurView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Only a backdrop: touches go to the views above it, or through it to the list
        isClickable = false
    }

    /** Blurs [root] (which must not contain this bar), tinted with [tint]. */
    fun setup(root: ViewGroup, @ColorInt tint: Int) {
        if (LiquidGlass.isBlurEnabled(context)) {
            fallback.visibility = GONE
            blurView.visibility = VISIBLE
            doOnAttach {
                blurView.setupWith(root, LiquidGlassBlur(0f, 0f))
                    .setBlurRadius(BLUR_RADIUS)
                    .setBlurAutoUpdate(true)
                setTint(tint)
            }
        } else {
            blurView.visibility = GONE
            fallback.visibility = VISIBLE
        }
        setTint(tint)
    }

    fun setTint(@ColorInt tint: Int) {
        this.tint = tint
        blurView.setOverlayColor(tint)
        // Without blur, the cards would show through a light tint: make it nearly opaque
        fallback.setBackgroundColor(ColorUtils.setAlphaComponent(tint, (FALLBACK_ALPHA * 255).toInt()))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maskPaint.shader = LinearGradient(
            0f,
            h - fadeLength,
            0f,
            h.toFloat(),
            Color.BLACK,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        canvas.restoreToCount(saved)
    }

    companion object {
        /** Length of the soft bottom edge. */
        const val FADE_DP = 24f

        private const val BLUR_RADIUS = 22f
        private const val FALLBACK_ALPHA = 0.94f
    }
}
