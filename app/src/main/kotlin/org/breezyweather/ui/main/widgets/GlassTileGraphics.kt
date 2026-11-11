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
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import org.breezyweather.common.extensions.dpToPx
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Thin capsule with a color scale and a dot marking where the value sits (iOS weather style,
 * e.g. the UV index or air quality tiles).
 */
class GradientScaleBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var colors: IntArray = intArrayOf(Color.WHITE, Color.WHITE)
    private var fraction: Float? = null

    private val barHeight = context.dpToPx(5f)
    private val dotRadius = context.dpToPx(4f)
    private val ring = context.dpToPx(2f)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ring
        color = ColorUtils.setAlphaComponent(Color.BLACK, (0.35f * 255).toInt())
    }
    private val rect = RectF()

    /** @param fraction where the dot goes, 0 (start) to 1 (end); null hides the dot */
    fun setScale(@ColorInt colors: IntArray, fraction: Float?) {
        this.colors = if (colors.size >= 2) colors else intArrayOf(colors.first(), colors.first())
        this.fraction = fraction?.coerceIn(0f, 1f)
        updateShader()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    private fun updateShader() {
        barPaint.shader = LinearGradient(
            dotRadius,
            0f,
            (width - dotRadius).coerceAtLeast(dotRadius + 1),
            0f,
            colors,
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize((dotRadius * 2 + ring * 2).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val r = barHeight / 2f
        rect.set(dotRadius - r, cy - r, width - dotRadius + r, cy + r)
        canvas.drawRoundRect(rect, r, r, barPaint)
        fraction?.let {
            val x = dotRadius + it * (width - 2 * dotRadius)
            canvas.drawCircle(x, cy, dotRadius + ring / 2f, ringPaint)
            canvas.drawCircle(x, cy, dotRadius, dotPaint)
        }
    }
}

/**
 * Row of small rounded bars (e.g. the next 12 hours of humidity or chance of precipitation):
 * a faint full-height track per bar, filled from the bottom with a vertical [color] gradient.
 */
class MiniBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** One value per bar, 0..1; null for no data (track only). */
    private var values: List<Float?> = emptyList()

    @ColorInt
    private var color: Int = Color.WHITE

    @ColorInt
    private var trackColor: Int = ColorUtils.setAlphaComponent(Color.WHITE, 30)

    private val gap = context.dpToPx(3f)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun setBars(values: List<Float?>, @ColorInt color: Int, @ColorInt contentColor: Int) {
        this.values = values.map { it?.coerceIn(0f, 1f) }
        this.color = color
        this.trackColor = ColorUtils.setAlphaComponent(contentColor, 28)
        updateShader()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    private fun updateShader() {
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat().coerceAtLeast(1f),
            ColorUtils.blendARGB(color, Color.WHITE, 0.35f),
            color,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val barWidth = (width - gap * (values.size - 1)) / values.size
        if (barWidth <= 0) return
        val radius = min(barWidth / 2f, context.dpToPx(3f))
        trackPaint.color = trackColor
        values.forEachIndexed { i, v ->
            val left = i * (barWidth + gap)
            rect.set(left, 0f, left + barWidth, height.toFloat())
            canvas.drawRoundRect(rect, radius, radius, trackPaint)
            if (v != null && v > 0f) {
                // Keep tiny values visible as a sliver
                val top = height - (height * v).coerceAtLeast(radius * 2)
                rect.set(left, top, left + barWidth, height.toFloat())
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
            }
        }
    }
}

/**
 * Horizontal capsule filled from the start to [fraction] (e.g. a day's chance of precipitation),
 * over a faint full-length track.
 */
class FillBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var fraction: Float? = null
    private val barHeight = context.dpToPx(6f)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun setFill(fraction: Float?, @ColorInt color: Int, @ColorInt contentColor: Int) {
        this.fraction = fraction?.coerceIn(0f, 1f)
        fillPaint.color = color
        trackPaint.color = ColorUtils.setAlphaComponent(contentColor, 40)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(barHeight.toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val r = barHeight / 2f
        rect.set(0f, cy - r, width.toFloat(), cy + r)
        canvas.drawRoundRect(rect, r, r, trackPaint)
        fraction?.takeIf { it > 0f }?.let {
            // Keep tiny values visible as a dot
            rect.set(0f, cy - r, (width * it).coerceAtLeast(barHeight), cy + r)
            canvas.drawRoundRect(rect, r, r, fillPaint)
        }
    }
}

/**
 * Round gauge for the highlights (watch-complication style): a 270° ring open at the bottom,
 * a faint track with the level drawn over it in [color] and a small dot at its end.
 * With no level (fraction null), the whole ring is drawn faintly in [color], as a frame for an icon.
 */
class RingGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var fraction: Float? = null

    @ColorInt
    private var color: Int = Color.WHITE

    @ColorInt
    private var contentColor: Int = Color.WHITE

    private val stroke = context.dpToPx(5f)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()

    fun setGauge(fraction: Float?, @ColorInt color: Int, @ColorInt contentColor: Int) {
        this.fraction = fraction?.coerceIn(0f, 1f)
        this.color = color
        this.contentColor = contentColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val inset = stroke / 2f + context.dpToPx(1f)
        rect.set(
            (width - size) / 2f + inset,
            (height - size) / 2f + inset,
            (width + size) / 2f - inset,
            (height + size) / 2f - inset
        )
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke

        val f = fraction
        if (f == null) {
            // Strong enough to keep its color over blue skies (a faint yellow turns olive)
            trackPaint.color = ColorUtils.setAlphaComponent(color, 190)
            canvas.drawArc(rect, START_ANGLE, SWEEP, false, trackPaint)
            return
        }
        trackPaint.color = ColorUtils.setAlphaComponent(contentColor, 50)
        canvas.drawArc(rect, START_ANGLE, SWEEP, false, trackPaint)
        if (f > 0f) {
            arcPaint.color = color
            canvas.drawArc(rect, START_ANGLE, SWEEP * f, false, arcPaint)
        }
        // Dot at the end of the level
        val angle = Math.toRadians((START_ANGLE + SWEEP * f).toDouble())
        val r = rect.width() / 2f
        canvas.drawCircle(
            rect.centerX() + (cos(angle) * r).toFloat(),
            rect.centerY() + (sin(angle) * r).toFloat(),
            stroke * 0.42f,
            dotPaint
        )
    }

    companion object {
        /** From bottom-left (135°), clockwise over the top, to bottom-right: open at the bottom. */
        private const val START_ANGLE = 135f
        private const val SWEEP = 270f
    }
}

/**
 * Small compass for the wind tile: a ring of ticks with N/E/S/W and an arrow showing where the wind
 * blows to (same convention as the former wind arrow: rotated by the wind degree).
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Wind degree; null for variable / unknown direction (no arrow). */
    var degree: Float? = null
        set(value) {
            field = value
            invalidate()
        }

    @ColorInt
    var contentColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arrowHead = Path()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - context.dpToPx(2f)
        if (radius <= 0) return

        // Ticks every 10°, longer at the cardinal points
        tickPaint.strokeWidth = context.dpToPx(1f)
        for (i in 0 until 36) {
            val cardinal = i % 9 == 0
            if (cardinal) continue // letters go there
            val angle = Math.toRadians(i * 10.0 - 90)
            val outer = radius
            val inner = radius - context.dpToPx(if (i % 3 == 0) 5f else 3f)
            tickPaint.color = ColorUtils.setAlphaComponent(contentColor, if (i % 3 == 0) 160 else 90)
            canvas.drawLine(
                cx + (cos(angle) * inner).toFloat(),
                cy + (sin(angle) * inner).toFloat(),
                cx + (cos(angle) * outer).toFloat(),
                cy + (sin(angle) * outer).toFloat(),
                tickPaint
            )
        }

        // N, E, S, W
        textPaint.textSize = radius * 0.28f
        textPaint.color = ColorUtils.setAlphaComponent(contentColor, 200)
        val letterRadius = radius - textPaint.textSize * 0.55f
        val baseline = textPaint.textSize * 0.36f
        listOf("N" to -90.0, "E" to 0.0, "S" to 90.0, "W" to 180.0).forEach { (letter, deg) ->
            val angle = Math.toRadians(deg)
            canvas.drawText(
                letter,
                cx + (cos(angle) * letterRadius).toFloat(),
                cy + (sin(angle) * letterRadius).toFloat() + baseline,
                textPaint
            )
        }

        // Arrow through the center
        val d = degree ?: return
        val length = radius * 0.58f
        arrowPaint.color = contentColor
        arrowPaint.strokeWidth = context.dpToPx(2.5f)
        canvas.save()
        canvas.rotate(d, cx, cy)
        canvas.drawLine(cx, cy - length, cx, cy + length, arrowPaint)
        val head = radius * 0.2f
        arrowHead.reset()
        arrowHead.moveTo(cx - head * 0.7f, cy + length - head)
        arrowHead.lineTo(cx, cy + length)
        arrowHead.lineTo(cx + head * 0.7f, cy + length - head)
        canvas.drawPath(arrowHead, arrowPaint)
        // Tail dot
        arrowPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy - length, context.dpToPx(2.5f), arrowPaint)
        arrowPaint.style = Paint.Style.STROKE
        canvas.restore()
    }
}
