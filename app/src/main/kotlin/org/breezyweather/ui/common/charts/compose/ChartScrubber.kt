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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import kotlin.math.abs

/**
 * Press-and-drag along a chart to pick a point, without drawing the chart again.
 *
 * Vico draws a chart's data in software, into a bitmap, every time the chart is drawn: with its own
 * marker, that was on every frame of a drag (~10-17 ms each, so scrubbing stuttered at 120 Hz). Here
 * the chart is drawn once and kept in its own layer, and only this guideline is drawn over it as the
 * finger moves. [listener] gets the same calls as with a Vico marker (only the targets' x is set).
 *
 * Use [decoration] in the chart's decorations (it records where the points are as the chart draws),
 * and [modifier] on the chart host, outside the chart's own graphics layer.
 */
@Stable
class ChartScrubber(
    private val marker: CartesianMarker,
    private val guidelineColor: Color,
    var listener: CartesianMarkerVisibilityListener?,
) {
    // Where the points are, from the last time the chart was drawn
    private var xs = DoubleArray(0)
    private var pxs = FloatArray(0)
    private var top = 0f
    private var bottom = 0f

    /** Index in [xs] of the point shown, -1 for none. Read in draw only. */
    private var active by mutableIntStateOf(-1)

    val decoration: Decoration = object : Decoration {
        override fun drawOverLayers(context: CartesianDrawingContext) {
            with(context) {
                val values = sortedSetOf<Double>()
                model.models.forEach { layer ->
                    when (layer) {
                        is LineCartesianLayerModel -> layer.series.forEach { s -> s.forEach { values += it.x } }
                        is ColumnCartesianLayerModel -> layer.series.forEach { s -> s.forEach { values += it.x } }
                    }
                }
                val start = layerBounds.left + layerDimensions.startPadding - scroll
                xs = values.toDoubleArray()
                pxs = FloatArray(xs.size) {
                    start + ((xs[it] - ranges.minX) / ranges.xStep).toFloat() * layerDimensions.xSpacing
                }
                top = layerBounds.top
                bottom = layerBounds.bottom
            }
        }
    }

    private fun target(index: Int) = object : CartesianMarker.Target {
        override val x = xs[index]
        override val canvasX = pxs[index]
    }

    private fun show(fingerX: Float) {
        if (pxs.isEmpty()) return
        var nearest = 0
        for (i in pxs.indices) {
            if (abs(pxs[i] - fingerX) < abs(pxs[nearest] - fingerX)) nearest = i
        }
        if (nearest == active) return
        val wasShown = active >= 0
        active = nearest
        val targets = listOf(target(nearest))
        if (wasShown) listener?.onUpdated(marker, targets) else listener?.onShown(marker, targets)
    }

    private fun hide() {
        if (active < 0) return
        active = -1
        listener?.onHidden(marker)
    }

    val modifier: Modifier = Modifier
        .pointerInput(this) {
            awaitEachGesture {
                // Like Vico's show-on-press marker: shown while pressed, follows the finger (taking the
                // movement, so the pager doesn't switch days), hidden on release
                val down = awaitFirstDown(requireUnconsumed = false)
                show(down.position.x)
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChanged()) {
                            show(change.position.x)
                            change.consume()
                        }
                    }
                } finally {
                    hide()
                }
            }
        }
        .drawWithContent {
            drawContent()
            val index = active
            if (index in pxs.indices) {
                drawLine(
                    color = guidelineColor,
                    start = Offset(pxs[index], top),
                    end = Offset(pxs[index], bottom),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
}
