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

package org.breezyweather.ui.common.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import org.breezyweather.common.extensions.dpToPx
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Location page dots, iOS weather style: plain dots, the current one bright, the highlight
 * sliding between dots as you swipe; the current (GPS) location is an arrow instead of a dot.
 *
 * With more than [MAX_VISIBLE] locations it becomes a sliding window, like iOS page controls:
 * only [MAX_VISIBLE] dots are shown, the window follows the current page as you swipe, and a dot at
 * the edge of the window is drawn smaller when more locations lie beyond it.
 * So the capsule never grows past [MAX_VISIBLE] dots, however many locations there are.
 */
class PageDotsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), SwipeSwitchLayout.OnPagerSwipeListener {

    private var switchView: SwipeSwitchLayout? = null
    private var pageCount = 0
    private var current = 0

    // During a swipe: the neighbour page being moved to, and how far (0..1)
    private var swipeTarget = -1
    private var swipeFraction = 0f

    private val diameter = context.dpToPx(8f)
    private val gap = context.dpToPx(9f)

    @ColorInt
    private var selectedColor = Color.WHITE

    @ColorInt
    private var unselectedColor = ColorUtils.setAlphaComponent(Color.WHITE, 90)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPath = Path()

    /**
     * Page of the current (GPS) location: drawn as a small navigation arrow instead of a dot.
     * Null when there is no current location.
     */
    var arrowIndex: Int? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val visibleSlots: Int
        get() = min(pageCount, MAX_VISIBLE)

    fun setSwitchView(switchView: SwipeSwitchLayout) {
        this.switchView = switchView
        switchView.setOnPageSwipeListener(this)
        pageCount = switchView.totalCount
        current = switchView.position.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        swipeTarget = -1
        requestLayout()
        invalidate()
    }

    fun setCurrentIndicatorColor(@ColorInt color: Int) {
        selectedColor = color
        invalidate()
    }

    fun setIndicatorColor(@ColorInt color: Int) {
        unselectedColor = color
        invalidate()
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        // ViewPager convention: [position] is the left page of the pair, [positionOffset] how far
        // towards the right one. Turn that into "moving towards which neighbour, and how far".
        when {
            position == current - 1 && position >= 0 -> {
                swipeTarget = position
                swipeFraction = (1f - positionOffset).coerceIn(0f, 1f)
            }
            position == current && current + 1 < pageCount -> {
                swipeTarget = current + 1
                swipeFraction = positionOffset.coerceIn(0f, 1f)
            }
            else -> {
                swipeTarget = -1
                swipeFraction = 0f
            }
        }
        invalidate()
    }

    override fun onPageSelected(position: Int) {
        current = position.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        swipeTarget = -1
        swipeFraction = 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val slots = visibleSlots
        // Plus room at both ends for the location arrow, which is wider than a dot
        val contentWidth = slots * diameter + (slots - 1).coerceAtLeast(0) * gap + diameter * (ARROW_SCALE - 1)
        val contentHeight = diameter * ARROW_SCALE
        setMeasuredDimension(
            resolveSize((contentWidth + paddingLeft + paddingRight).toInt(), widthMeasureSpec),
            resolveSize((contentHeight + paddingTop + paddingBottom).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (pageCount <= 0) return
        val slots = visibleSlots
        val step = diameter + gap
        val cy = height / 2f

        // Where the highlight is, as a fractional page (moves smoothly during a swipe)
        val position = if (swipeTarget >= 0) current + (swipeTarget - current) * swipeFraction else current.toFloat()
        // First page shown in the window: keeps the highlight centered, clamped at both ends
        val windowStart = (position - (slots - 1) / 2f).coerceIn(0f, (pageCount - slots).toFloat())

        val rowWidth = slots * diameter + (slots - 1) * gap
        val firstX = (width - rowWidth) / 2f + diameter / 2f

        val from = floor(windowStart).toInt() - 1
        val to = ceil(windowStart + slots).toInt()
        for (i in from..to) {
            if (i !in 0 until pageCount) continue
            val slot = i - windowStart // 0 = leftmost slot, slots - 1 = rightmost
            val scale = edgeScale(slot, slots, moreBefore = i > 0, moreAfter = i < pageCount - 1)
            if (scale <= 0f) continue

            val highlight = when (i) {
                current -> 1f - if (swipeTarget >= 0) swipeFraction else 0f
                swipeTarget -> swipeFraction
                else -> 0f
            }
            dotPaint.color = ColorUtils.blendARGB(unselectedColor, selectedColor, highlight)
            dotPaint.alpha = (Color.alpha(dotPaint.color) * min(1f, scale / EDGE_SCALE)).toInt()

            val x = firstX + slot * step
            if (i == arrowIndex) {
                drawArrow(canvas, x, cy, diameter * ARROW_SCALE * scale)
            } else {
                canvas.drawCircle(x, cy, diameter / 2f * scale, dotPaint)
            }
        }
    }

    /**
     * Size of a dot depending on where it sits in the window: full size inside, [EDGE_SCALE] in an
     * edge slot when more pages lie beyond it (a hint that the list goes on), shrinking to nothing
     * as it slides out of the window.
     */
    private fun edgeScale(slot: Float, slots: Int, moreBefore: Boolean, moreAfter: Boolean): Float {
        val fromStart = slot // 0 at the first slot
        val fromEnd = (slots - 1) - slot // 0 at the last slot
        fun side(distance: Float, more: Boolean): Float = when {
            distance >= 1f -> 1f
            !more -> if (distance >= 0f) 1f else 0f
            distance >= 0f -> EDGE_SCALE + (1f - EDGE_SCALE) * distance
            else -> (EDGE_SCALE * (1f + distance)).coerceAtLeast(0f) // leaving the window
        }
        return min(side(fromStart, moreBefore), side(fromEnd, moreAfter))
    }

    /** Navigation arrow (pointing up-right) fitting a [size] square centered on (cx, cy). */
    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val h = size / 2f
        arrowPath.reset()
        arrowPath.moveTo(cx + h, cy - h) // tip, top right
        arrowPath.lineTo(cx - h, cy - h * 0.1f) // left wing
        arrowPath.lineTo(cx - h * 0.05f, cy + h * 0.05f) // notch
        arrowPath.lineTo(cx + h * 0.1f, cy + h) // bottom wing
        arrowPath.close()
        canvas.drawPath(arrowPath, dotPaint)
    }

    companion object {
        /** Dots shown at most; past that, the row becomes a sliding window. */
        private const val MAX_VISIBLE = 7

        /** Size of an edge dot when more locations lie beyond it. */
        private const val EDGE_SCALE = 0.5f

        /** The arrow is a bit bigger than a dot, so it reads as an arrow. */
        private const val ARROW_SCALE = 1.6f
    }
}
