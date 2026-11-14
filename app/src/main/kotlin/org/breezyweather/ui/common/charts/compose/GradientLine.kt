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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill

/**
 * A line coloured by value (like `LineFill.colorScale`), drawn straight onto the chart's canvas.
 *
 * Vico draws colour-scale lines in software: into two bitmaps the size of the chart, combined with a
 * blend mode, on every draw (~17 ms on a 120 Hz phone, so dragging the marker along a chart stuttered).
 * A line with a single solid fill takes Vico's fast path instead, which strokes the path on the
 * (GPU-rendered) canvas with the line's paint. This line is one of those, and gives that paint a
 * vertical gradient shader with the same stops Vico's colour scale would use, right before each draw.
 *
 * @param colors the colour for each y value, as for `LineFill.colorScale`
 * @param areaFill drawn under the line as usual (Vico's area fills are already drawn on the canvas)
 */
class GradientLine(
    colors: Map<out Number, Color>,
    verticalAxisPosition: Axis.Position.Vertical?,
    stroke: LineCartesianLayer.LineStroke,
    areaFill: LineCartesianLayer.AreaFill?,
    interpolator: LineCartesianLayer.Interpolator = LineCartesianLayer.Interpolator.Sharp,
) : LineCartesianLayer.Line(
    fill = LineCartesianLayer.LineFill.single(Fill(Color.Black)), // Opaque: the shader's colours show as they are
    stroke = stroke,
    areaFill = ShaderSettingAreaFill(colors, verticalAxisPosition, areaFill),
    interpolator = interpolator
) {
    init {
        // The area fill is drawn right before the line, with the drawing context: it hands the shader over
        (this.areaFill as ShaderSettingAreaFill).onShader = { linePaint.shader = it }
    }

    private class ShaderSettingAreaFill(
        colors: Map<out Number, Color>,
        private val verticalAxisPosition: Axis.Position.Vertical?,
        private val areaFill: LineCartesianLayer.AreaFill?,
    ) : LineCartesianLayer.AreaFill {
        // Top of the chart first, as Vico sorts them
        private val stops = colors.entries.sortedByDescending { it.key.toDouble() }
        var onShader: ((Shader) -> Unit)? = null

        override fun draw(
            context: CartesianDrawingContext,
            linePath: Path,
            halfLineThickness: Float,
            verticalAxisPosition: Axis.Position.Vertical?,
        ) {
            onShader?.invoke(shader(context))
            areaFill?.draw(context, linePath, halfLineThickness, verticalAxisPosition)
        }

        /** Same gradient as Vico's ColorScaleShader: from the top to the bottom of the layer, stops by y value. */
        private fun shader(context: CartesianDrawingContext): Shader = with(context) {
            val yRange = ranges.getYRange(verticalAxisPosition)
            val length = yRange.length
            val positions = if (length == 0.0) {
                List(stops.size) { if (stops.size > 1) it / (stops.size - 1f) else 0f }
            } else {
                stops.map { 1f - ((it.key.toDouble() - yRange.minY) / length).toFloat() }
            }
            val colors = stops.map { it.value }
            val (safeColors, safePositions) = when {
                colors.size >= 2 -> colors to positions
                colors.size == 1 -> listOf(colors[0], colors[0]) to listOf(positions[0], positions[0])
                else -> listOf(Color.Transparent, Color.Transparent) to listOf(0f, 1f)
            }
            LinearGradientShader(
                from = Offset(0f, layerBounds.top),
                to = Offset(0f, layerBounds.bottom),
                colors = safeColors,
                colorStops = safePositions
            )
        }
    }
}
