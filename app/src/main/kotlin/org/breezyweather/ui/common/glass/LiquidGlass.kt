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
import com.google.android.material.card.MaterialCardView
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur
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

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, cornerRadius)
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
        setTint(card, bodyColor(card))
        if (isBlurSupported) {
            // Blur the weather background only: cards never overlap each other, and blurring
            // the whole screen for every card would mean redrawing all cards once per card.
            attachBlur(card) { it.rootView.findViewById(R.id.glass_backdrop) }
            card.clipToOutline = true
        }
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
     * Real backdrop blur needs RenderEffect (Android 12+). Older versions keep the tinted
     * translucent body, which still reads as glass, just not frosted.
     */
    val isBlurSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private const val BLUR_RADIUS = 18f

    /**
     * Sets the tint of a glass card body. When the card is frosted, the tint is painted on top of
     * the blur (the card background itself sits underneath the blur layer, so it stays clear).
     */
    fun setTint(card: MaterialCardView, @ColorInt tint: Int) {
        card.setTag(R.id.tag_liquid_glass_tint, tint)
        val blurView = card.getTag(R.id.tag_liquid_glass_blur) as? BlurView
        if (blurView != null || isBlurSupported) {
            card.setCardBackgroundColor(ColorStateList.valueOf(Color.TRANSPARENT))
            blurView?.setOverlayColor(tint)
        } else {
            card.setCardBackgroundColor(ColorStateList.valueOf(tint))
        }
    }

    /**
     * Frosted glass for a floating element that passes over the cards (e.g. the page dots capsule):
     * it blurs [blurRoot] (the whole screen content), tinted with [tint], and gets a glass rim.
     * Below Android 12, falls back to the tinted [background].
     */
    fun applyFloating(host: ViewGroup, cornerRadius: Float, @ColorInt tint: Int, blurRoot: ViewGroup) {
        val glass = LiquidGlassDrawable(
            cornerRadius = cornerRadius,
            rimWidth = host.context.dpToPx(1f),
            fillColor = if (isBlurSupported) Color.TRANSPARENT else tint,
            isLight = isLightSurface(host)
        )
        host.background = glass
        if (isBlurSupported) {
            host.setTag(R.id.tag_liquid_glass_tint, tint)
            attachBlur(host) { blurRoot }
            host.clipToOutline = true
            // The background sits under the blur layer: draw the sheen and rim above it instead
            val previous = host.getTag(R.id.tag_liquid_glass_overlay) as? Drawable
            previous?.let { host.overlay.remove(it) }
            val overlay = LiquidGlassDrawable(
                cornerRadius = cornerRadius,
                rimWidth = host.context.dpToPx(1f),
                isLight = isLightSurface(host)
            )
            overlay.setBounds(0, 0, host.width, host.height)
            host.overlay.add(overlay)
            if (previous == null) {
                // Registered once; always resizes whichever overlay is current
                host.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
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
            }
            host.setTag(R.id.tag_liquid_glass_overlay, overlay)
        }
    }

    /**
     * Inserts (once) a [BlurView] behind the content of [host] and binds it to the view returned by
     * [rootProvider] as soon as [host] is attached to a window (the root is looked up from the
     * window's view tree, which isn't reachable before that).
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun attachBlur(host: ViewGroup, rootProvider: (View) -> ViewGroup?) {
        val blurView = host.getTag(R.id.tag_liquid_glass_blur) as? BlurView
            ?: BlurView(host.context).also {
                host.addView(
                    it,
                    0,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                host.setTag(R.id.tag_liquid_glass_blur, it)
            }
        host.doOnAttach { attached ->
            val root = rootProvider(attached) ?: return@doOnAttach
            if (blurView.getTag(R.id.tag_liquid_glass_blur_root) !== root) {
                blurView.setupWith(root, RenderEffectBlur())
                    .setBlurRadius(BLUR_RADIUS)
                    .setBlurAutoUpdate(true)
                blurView.setTag(R.id.tag_liquid_glass_blur_root, root)
            }
            (host.getTag(R.id.tag_liquid_glass_tint) as? Int)?.let { blurView.setOverlayColor(it) }
        }
    }

    /**
     * Standalone glass background (e.g. for pills and capsules).
     *
     * @param bodyAlpha opacity of the tinted body; floating elements that pass over content
     * (like the page dots capsule) need a denser body than chips sitting on the background.
     */
    fun background(view: View, cornerRadius: Float, bodyAlpha: Float? = null): Drawable {
        val fill = bodyAlpha?.let {
            ColorUtils.setAlphaComponent(
                view.context.getThemeColor(com.google.android.material.R.attr.colorSurface),
                (it * 255).toInt()
            )
        } ?: bodyColor(view)
        return LiquidGlassDrawable(
            cornerRadius = cornerRadius,
            rimWidth = view.context.dpToPx(1f),
            fillColor = fill,
            isLight = isLightSurface(view)
        )
    }
}
