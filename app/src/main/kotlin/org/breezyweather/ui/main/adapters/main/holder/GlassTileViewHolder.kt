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

package org.breezyweather.ui.main.adapters.main.holder

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import org.breezyweather.R
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.ui.main.widgets.CompassView
import org.breezyweather.ui.main.widgets.GradientScaleBarView
import org.breezyweather.ui.main.widgets.MiniBarsView

/**
 * Square liquid glass tile, iOS weather style (see container_main_glass_tile.xml):
 * header with [iconId] and [titleId], then a big value, a label, an optional graphic and description.
 * Subclasses fill the slots in their onBindView.
 */
abstract class GlassTileViewHolder(
    parent: ViewGroup,
    @DrawableRes iconId: Int,
    @StringRes titleId: Int,
) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_glass_tile, parent, false)
) {
    protected val valueView: TextView = itemView.findViewById(R.id.tile_value)
    private val labelView: TextView = itemView.findViewById(R.id.tile_label)
    protected val descriptionView: TextView = itemView.findViewById(R.id.tile_description)
    private val graphicSlot: FrameLayout = itemView.findViewById(R.id.tile_graphic)
    private val sideSlot: FrameLayout = itemView.findViewById(R.id.tile_side)
    private val content: View = itemView.findViewById(R.id.tile_content)

    init {
        itemView.findViewById<ImageView>(R.id.tile_icon).setImageResource(iconId)
        itemView.findViewById<TextView>(R.id.tile_title).setText(titleId)
    }

    protected fun setValue(value: CharSequence?) {
        valueView.text = value ?: "-"
    }

    protected fun setLabel(label: CharSequence?) {
        labelView.text = label
        labelView.visibility = if (label.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    protected fun setDescription(description: CharSequence?) {
        descriptionView.text = description
        descriptionView.visibility = if (description.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /** Color scale with a dot at [fraction] (0..1), at the bottom of the graphic area. */
    protected fun showScale(@ColorInt colors: IntArray, fraction: Float?) {
        val bar = graphicSlot.getChildAt(0) as? GradientScaleBarView
            ?: GradientScaleBarView(itemView.context).also {
                graphicSlot.removeAllViews()
                graphicSlot.addView(
                    it,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM
                    )
                )
            }
        bar.setScale(colors, fraction)
    }

    /** Compass to the right of the value; [degree] null for no arrow. */
    protected fun showCompass(degree: Float?) {
        val compass = sideSlot.getChildAt(0) as? CompassView
            ?: CompassView(itemView.context).also {
                sideSlot.removeAllViews()
                val size = itemView.context.dpToPx(58f).toInt()
                sideSlot.addView(it, FrameLayout.LayoutParams(size, size))
            }
        sideSlot.visibility = View.VISIBLE
        compass.contentColor = contentColor
        compass.degree = degree
    }

    /** Small bar chart (values 0..1, null for missing) at the bottom of the graphic area. */
    protected fun showBars(values: List<Float?>, @ColorInt color: Int) {
        val bars = graphicSlot.getChildAt(0) as? MiniBarsView
            ?: MiniBarsView(itemView.context).also {
                graphicSlot.removeAllViews()
                graphicSlot.addView(
                    it,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        itemView.context.dpToPx(34f).toInt(),
                        Gravity.BOTTOM
                    )
                )
            }
        bars.setBars(values, color, contentColor)
    }

    /**
     * Faint glow of [color] in the top corner of the tile, hinting at what it shows
     * (e.g. the UV level color). Subtle on purpose: the tile stays glass.
     */
    protected fun setAccent(@ColorInt color: Int) {
        content.background = AccentGlowDrawable(color)
    }

    private val contentColor: Int
        get() = itemView.context.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse)

    protected fun clearGraphic() {
        graphicSlot.removeAllViews()
    }

    protected fun levelColors(vararg colorIds: Int): IntArray =
        colorIds.map { ContextCompat.getColor(itemView.context, it) }.toIntArray()
}

/** Accent colors of the tiles that don't take theirs from the data (like UV or air quality levels). */
internal object TileColors {
    val WATER = Color.rgb(79, 175, 255) // humidity, precipitation
    val CLEAR_AIR = Color.rgb(207, 230, 255) // visibility
    val PRESSURE = Color.rgb(167, 139, 250)
    val WIND = Color.rgb(94, 230, 208)
}

/** Soft radial glow from the top end corner, fading out across the tile. */
private class AccentGlowDrawable(@ColorInt private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val radius = max(bounds.width(), bounds.height()) * 0.95f
        paint.shader = RadialGradient(
            bounds.right.toFloat(),
            bounds.top.toFloat(),
            radius.coerceAtLeast(1f),
            intArrayOf(
                ColorUtils.setAlphaComponent(color, (0.30f * 255).toInt()),
                ColorUtils.setAlphaComponent(color, (0.10f * 255).toInt()),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
