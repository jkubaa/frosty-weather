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

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnAttach
import eightbitlab.com.blurview.BlurView
import org.breezyweather.R
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.domain.settings.SettingsManager

/**
 * "Liquid glass" surface, inspired by the translucent panels of iOS weather apps.
 *
 * It is made of two layers:
 * 1. a translucent tinted body (drawn by the host, e.g. the card background color, or the frosted
 *    blur layer),
 * 2. soft, identical rims of light along the top and bottom edges (light caught at both rims, which
 *    makes the panel read as a thick, rounded piece of glass).
 *
 * There is deliberately no outline: the edge comes from the contrast between the frosted body and
 * the background.
 *
 * This drawable renders layer 2 and optionally layer 1 (see [fillColor]),
 * so it can be used either as an overlay on a card or as a standalone background.
 */
class LiquidGlassDrawable(
    private var cornerRadius: Float,
    private val fillColor: Int = Color.TRANSPARENT,
    private val isLight: Boolean = false,
) : Drawable() {

    private val panelBounds = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bottomGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        panelBounds.set(bounds)

        val top = bounds.top.toFloat()
        val height = bounds.height().toFloat().coerceAtLeast(1f)
        val density = Resources.getSystem().displayMetrics.density
        // Rims of light, identical at the top and the bottom so the glass looks evenly lit: same
        // brightness, same depth (fixed, so big cards don't get a wide band, and capped by the height
        // so small buttons keep a clear middle), brightest right at the edge.
        val depth = minOf(height * RIM_DEPTH_FRACTION, RIM_MAX_DEPTH_DP * density)
        val colors = intArrayOf(
            Color.argb(if (isLight) 40 else 26, 255, 255, 255),
            Color.argb(if (isLight) 10 else 6, 255, 255, 255),
            Color.TRANSPARENT
        )
        val stops = floatArrayOf(0f, 0.4f, 1f)
        sheenPaint.shader = LinearGradient(0f, top, 0f, top + depth, colors, stops, Shader.TileMode.CLAMP)
        val bottom = bounds.bottom.toFloat()
        bottomGlowPaint.shader = LinearGradient(0f, bottom, 0f, bottom - depth, colors, stops, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        if (panelBounds.isEmpty) return
        if (Color.alpha(fillColor) > 0) {
            canvas.drawRoundRect(panelBounds, cornerRadius, cornerRadius, fillPaint)
        }
        canvas.drawRoundRect(panelBounds, cornerRadius, cornerRadius, sheenPaint)
        canvas.drawRoundRect(panelBounds, cornerRadius, cornerRadius, bottomGlowPaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha * Color.alpha(fillColor) / 255
        sheenPaint.alpha = alpha
        bottomGlowPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        sheenPaint.colorFilter = colorFilter
        bottomGlowPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, cornerRadius)
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val RIM_DEPTH_FRACTION = 0.26f
        const val RIM_MAX_DEPTH_DP = 20f
    }
}

object LiquidGlass {

    /** Opacity of the tinted glass body, on top of the animated weather background. */
    internal const val BODY_ALPHA_LIGHT = 0.40f
    internal const val BODY_ALPHA_DARK = 0.28f

    /** Blur radius at 100% intensity, at BlurView's downscaled resolution. */
    internal const val MAX_BLUR_RADIUS = 25f

    /** Width of the refracting band along the glass edges. */
    internal const val REFRACTION_EDGE_DP = 16f

    /** Whether the theme surface (and so the glass body) is light. */
    fun isLightSurface(context: Context): Boolean =
        ColorUtils.calculateLuminance(context.getThemeColor(com.google.android.material.R.attr.colorSurface)) > 0.5

    private fun isLightSurface(view: View): Boolean {
        val surface = view.context.getThemeColor(com.google.android.material.R.attr.colorSurface)
        return ColorUtils.calculateLuminance(surface) > 0.5
    }

    /**
     * Real backdrop blur needs RenderEffect (Android 12+). Older versions keep the tinted
     * translucent body, which still reads as glass, just not frosted.
     */
    val isBlurSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Frosted when the device can and the user hasn't set the blur intensity to 0. */
    fun isBlurEnabled(context: Context): Boolean =
        isBlurSupported && SettingsManager.getInstance(context).glassBlurIntensity > 0

    private fun blurRadius(context: Context): Float =
        SettingsManager.getInstance(context).glassBlurIntensity / 100f * MAX_BLUR_RADIUS

    /**
     * Glass for a floating element that passes over the cards (e.g. the bottom bar buttons):
     * frosted, it blurs [blurRoot] (the whole screen content) tinted with [tint]; with blur off or
     * unsupported, it falls back to a plain [tint] body.
     */
    fun applyFloating(host: ViewGroup, cornerRadius: Float, @ColorInt tint: Int, blurRoot: ViewGroup) {
        val frosted = isBlurEnabled(host.context)
        host.background = LiquidGlassDrawable(
            cornerRadius = cornerRadius,
            fillColor = if (frosted) Color.TRANSPARENT else tint,
            isLight = isLightSurface(host)
        )
        host.setTag(R.id.tag_liquid_glass_tint, tint)

        // Without blur, the background above already has the sheen: drop the overlay copy
        val previous = host.getTag(R.id.tag_liquid_glass_overlay) as? Drawable
        previous?.let { host.overlay.remove(it) }
        if (!frosted) {
            hideBlur(host)
            host.setTag(R.id.tag_liquid_glass_overlay, null)
            return
        }

        attachBlur(host, cornerRadius) { blurRoot }
        host.clipToOutline = true
        // The background sits under the blur layer: draw the sheen above it instead
        val overlay = LiquidGlassDrawable(cornerRadius = cornerRadius, isLight = isLightSurface(host))
        overlay.setBounds(0, 0, host.width, host.height)
        host.overlay.add(overlay)
        if (host.getTag(R.id.tag_liquid_glass_floating_listener) == null) {
            // Registered once; always resizes whichever overlay and blur layer are current
            val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                (v.getTag(R.id.tag_liquid_glass_overlay) as? Drawable)?.setBounds(0, 0, v.width, v.height)
                // A wrap_content FrameLayout doesn't stretch a lone match_parent child to its final
                // size, so the blur layer would stay 0px wide: size it by hand after each layout.
                // (It must not drive the host's own measurement, or the capsule could never shrink.)
                (v.getTag(R.id.tag_liquid_glass_blur) as? View)?.let { blur ->
                    if (blur.width != v.width || blur.height != v.height) {
                        blur.measure(
                            View.MeasureSpec.makeMeasureSpec(v.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(v.height, View.MeasureSpec.EXACTLY)
                        )
                        blur.layout(0, 0, v.width, v.height)
                    }
                }
            }
            host.addOnLayoutChangeListener(listener)
            host.setTag(R.id.tag_liquid_glass_floating_listener, listener)
        }
        host.setTag(R.id.tag_liquid_glass_overlay, overlay)
    }

    /**
     * Inserts (once) a [BlurView] behind the content of [host] and binds it to the view returned by
     * [rootProvider] as soon as [host] is attached to a window (the root is looked up from the
     * window's view tree, which isn't reachable before that). Applies the current blur intensity.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun attachBlur(host: ViewGroup, cornerRadius: Float, rootProvider: (View) -> ViewGroup?) {
        val blurView = host.getTag(R.id.tag_liquid_glass_blur) as? BlurView
            ?: BlurView(host.context).also {
                host.addView(
                    it,
                    0,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                host.setTag(R.id.tag_liquid_glass_blur, it)
            }
        blurView.visibility = View.VISIBLE
        val radius = blurRadius(host.context)
        host.doOnAttach { attached ->
            val root = rootProvider(attached) ?: return@doOnAttach
            if (blurView.getTag(R.id.tag_liquid_glass_blur_root) !== root) {
                // Liquid glass: blur + vibrancy, plus lens refraction along the edges on Android 13+.
                // The refracting band is narrower on small elements (round buttons, capsule)
                val edge = minOf(cornerRadius * 0.6f, host.context.dpToPx(REFRACTION_EDGE_DP))
                blurView.setupWith(root, LiquidGlassBlur(cornerRadius, edge))
                    .setBlurAutoUpdate(true)
                blurView.setTag(R.id.tag_liquid_glass_blur_root, root)
            }
            blurView.setBlurEnabled(true)
            blurView.setBlurRadius(radius)
            (host.getTag(R.id.tag_liquid_glass_tint) as? Int)?.let { blurView.setOverlayColor(it) }
        }
    }

    /** Blur turned off (intensity 0): keep the layer for later, but stop it from drawing and capturing. */
    private fun hideBlur(host: ViewGroup) {
        (host.getTag(R.id.tag_liquid_glass_blur) as? BlurView)?.let {
            it.setBlurEnabled(false)
            it.visibility = View.GONE
        }
    }
}
