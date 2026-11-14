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

package org.breezyweather.ui.common.charts.compose

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration

/**
 * Apple Weather-style chart overlay:
 * - the part of the day that is already over is dimmed,
 * - a thin vertical line marks the current time, with a dot where the first line series crosses it,
 * - optionally, the highest and lowest points of the first line series get an "H" / "L" label.
 *
 * @param now current time in ms, or null when the chart is not for today (nothing time-related is drawn)
 */
class NowDecoration(
    private val now: Long?,
    private val dimColor: Color,
    private val lineColor: Color,
    private val dotColor: Color,
    private val dotRingColor: Color,
    private val labelColor: Color,
    private val highLabel: String? = null,
    private val lowLabel: String? = null,
) : Decoration {

    private fun CartesianDrawingContext.xToPx(x: Double): Float {
        val start = layerBounds.left + layerDimensions.startPadding - scroll
        return start + ((x - ranges.minX) / ranges.xStep).toFloat() * layerDimensions.xSpacing
    }

    private fun CartesianDrawingContext.yToPx(y: Double): Float {
        val yRange = ranges.getYRange(null)
        return layerBounds.bottom - ((y - yRange.minY) / yRange.length).toFloat() * layerBounds.height
    }

    private fun CartesianDrawingContext.firstSeries(): List<LineCartesianLayerModel.Entry>? =
        model.models.filterIsInstance<LineCartesianLayerModel>().firstOrNull()?.series?.firstOrNull()

    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            val series = firstSeries()
            val canvas = canvas

            if (now != null && now > ranges.minX && now < ranges.maxX) {
                val nowX = xToPx(now.toDouble()).coerceIn(layerBounds.left, layerBounds.right)

                // Dim the past
                canvas.drawRect(
                    left = layerBounds.left,
                    top = layerBounds.top,
                    right = nowX,
                    bottom = layerBounds.bottom,
                    paint = androidx.compose.ui.graphics.Paint().apply { color = dimColor }
                )
                // "Now" line
                canvas.drawLine(
                    Offset(nowX, layerBounds.top),
                    Offset(nowX, layerBounds.bottom),
                    androidx.compose.ui.graphics.Paint().apply {
                        color = lineColor
                        strokeWidth = 1.dp.pixels
                    }
                )
                // Dot on the line
                series?.let { entries ->
                    val y = interpolate(entries, now.toDouble())
                    if (y != null) {
                        drawDot(this, Offset(nowX, yToPx(y)))
                    }
                }
            }

            if (series != null && series.size > 1 && (highLabel != null || lowLabel != null)) {
                val max = series.maxBy { it.y }
                val min = series.minBy { it.y }
                if (max.y != min.y) {
                    highLabel?.let { drawExtreme(this, max, it) }
                    lowLabel?.let { drawExtreme(this, min, it) }
                }
            }
        }
    }

    private fun drawDot(context: CartesianDrawingContext, center: Offset) {
        with(context) {
            canvas.drawCircle(
                center,
                5.dp.pixels,
                androidx.compose.ui.graphics.Paint().apply { color = dotRingColor }
            )
            canvas.drawCircle(
                center,
                3.dp.pixels,
                androidx.compose.ui.graphics.Paint().apply { color = dotColor }
            )
        }
    }

    private fun drawExtreme(context: CartesianDrawingContext, entry: LineCartesianLayerModel.Entry, label: String) {
        with(context) {
            val center = Offset(xToPx(entry.x), yToPx(entry.y))
            canvas.drawCircle(
                center,
                5.dp.pixels,
                androidx.compose.ui.graphics.Paint().apply { color = dotRingColor }
            )
            canvas.drawCircle(
                center,
                2.dp.pixels,
                androidx.compose.ui.graphics.Paint().apply { color = labelColor }
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = labelColor.toArgb()
                textSize = 12.dp.pixels
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val textY = (center.y - 12.dp.pixels).coerceAtLeast(layerBounds.top + paint.textSize)
            canvas.nativeCanvas.drawText(label, center.x, textY, paint)
        }
    }

    private fun interpolate(entries: List<LineCartesianLayerModel.Entry>, x: Double): Double? {
        if (entries.isEmpty()) return null
        if (x <= entries.first().x) return entries.first().y
        if (x >= entries.last().x) return entries.last().y
        for (i in 1 until entries.size) {
            val a = entries[i - 1]
            val b = entries[i]
            if (x <= b.x) {
                val t = if (b.x == a.x) 0.0 else (x - a.x) / (b.x - a.x)
                return a.y + (b.y - a.y) * t
            }
        }
        return null
    }

    override fun equals(other: Any?): Boolean =
        other is NowDecoration &&
            now == other.now &&
            dimColor == other.dimColor &&
            lineColor == other.lineColor &&
            highLabel == other.highLabel &&
            lowLabel == other.lowLabel

    override fun hashCode(): Int {
        var result = now.hashCode()
        result = 31 * result + dimColor.hashCode()
        result = 31 * result + lineColor.hashCode()
        result = 31 * result + (highLabel?.hashCode() ?: 0)
        result = 31 * result + (lowLabel?.hashCode() ?: 0)
        return result
    }
}
