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

package org.breezyweather.ui.main.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import org.breezyweather.common.extensions.dpToPx

/**
 * Temperature range bar of a 10-day forecast row, iOS weather style.
 *
 * A faint capsule track spans the coldest to the warmest temperature of the whole list; a colored
 * capsule spans the day's low to high. Its colors come from an absolute temperature scale (blue when
 * freezing, through green, to red when hot), so a mild day and a hot day look different even when
 * they sit at the same place on the track. Today can also show a dot at the current temperature.
 *
 * All temperatures are in °C: the bar only compares them, the labels around it use the user's unit.
 */
class TemperatureRangeBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var scaleMin = 0.0
    private var scaleMax = 0.0
    private var low: Double? = null
    private var high: Double? = null
    private var current: Double? = null

    private val barHeight = context.dpToPx(5f)
    private val dotRadius = context.dpToPx(3.5f)
    private val dotStroke = context.dpToPx(2f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dotStroke
    }
    private val rect = RectF()

    init {
        setTrackColor(Color.WHITE)
    }

    /** The track takes the content color at low opacity; the dot ring is cut out of it. */
    fun setTrackColor(@ColorInt contentColor: Int) {
        trackPaint.color = ColorUtils.setAlphaComponent(contentColor, (0.18f * 255).toInt())
        dotRingPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, (0.35f * 255).toInt())
        invalidate()
    }

    /**
     * @param scaleMin coldest temperature of the whole list, in °C
     * @param scaleMax warmest temperature of the whole list, in °C
     * @param low this day's low, in °C (null when the source doesn't provide it)
     * @param high this day's high, in °C
     * @param current current temperature for today's row, in °C, or null
     */
    fun setRange(scaleMin: Double, scaleMax: Double, low: Double?, high: Double?, current: Double? = null) {
        this.scaleMin = scaleMin
        this.scaleMax = scaleMax
        this.low = low
        this.high = high
        this.current = current
        updateRangeShader()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateRangeShader()
    }

    private fun xOf(temperature: Double): Float {
        val span = (scaleMax - scaleMin).takeIf { it > 0 } ?: 1.0
        val fraction = ((temperature - scaleMin) / span).coerceIn(0.0, 1.0).toFloat()
        val usable = width - paddingLeft - paddingRight - barHeight
        return paddingLeft + barHeight / 2f + fraction * usable
    }

    /**
     * The gradient is laid over the whole track, with each scale color at the x of its temperature,
     * then only the day's segment is painted: the segment shows the colors of the temperatures it covers.
     */
    private fun updateRangeShader() {
        if (width == 0) return
        val left = xOf(scaleMin)
        val right = xOf(scaleMax)
        if (right - left < 1f) {
            rangePaint.shader = null
            rangePaint.color = colorAt(scaleMin)
            return
        }
        // Sample the scale across the track (the track can span any range of temperatures)
        val samples = 12
        val colors = IntArray(samples + 1)
        val positions = FloatArray(samples + 1)
        for (i in 0..samples) {
            val t = scaleMin + (scaleMax - scaleMin) * i / samples
            colors[i] = colorAt(t)
            positions[i] = i.toFloat() / samples
        }
        rangePaint.shader = LinearGradient(left, 0f, right, 0f, colors, positions, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val radius = barHeight / 2f

        // Track
        rect.set(paddingLeft.toFloat(), cy - radius, (width - paddingRight).toFloat(), cy + radius)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        // Day range
        val l = low ?: high
        val h = high ?: low
        if (l != null && h != null) {
            rect.set(xOf(minOf(l, h)) - radius, cy - radius, xOf(maxOf(l, h)) + radius, cy + radius)
            canvas.drawRoundRect(rect, radius, radius, rangePaint)
        }

        // Current temperature dot
        current?.let {
            val x = xOf(it)
            canvas.drawCircle(x, cy, dotRadius + dotStroke / 2f, dotRingPaint)
            canvas.drawCircle(x, cy, dotRadius, dotPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (dotRadius * 2 + dotStroke * 2).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    companion object {
        /** °C → color, interpolated in between. */
        private val COLOR_SCALE = listOf(
            -10.0 to 0xFF6E7CF0.toInt(), // indigo
            0.0 to 0xFF4FB8F5.toInt(), // blue
            8.0 to 0xFF4FD6C8.toInt(), // teal
            15.0 to 0xFF8EDB5A.toInt(), // green
            21.0 to 0xFFF5D547.toInt(), // yellow
            27.0 to 0xFFFFA43A.toInt(), // orange
            34.0 to 0xFFFF5B4A.toInt() // red
        )

        @ColorInt
        fun colorAt(celsius: Double): Int {
            val first = COLOR_SCALE.first()
            val last = COLOR_SCALE.last()
            if (celsius <= first.first) return first.second
            if (celsius >= last.first) return last.second
            val upper = COLOR_SCALE.indexOfFirst { it.first >= celsius }
            val (t0, c0) = COLOR_SCALE[upper - 1]
            val (t1, c1) = COLOR_SCALE[upper]
            return ColorUtils.blendARGB(c0, c1, ((celsius - t0) / (t1 - t0)).toFloat())
        }
    }
}
