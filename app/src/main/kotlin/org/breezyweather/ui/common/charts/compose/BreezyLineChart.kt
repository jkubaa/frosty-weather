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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import breezyweather.domain.location.model.Location
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.breezyweather.common.extensions.windowHeightInDp
import java.util.Date
import kotlin.math.max

/**
 * A line chart of a day, with time as X-Axis and a formatted value on Y-Axis (see [DetailsChart]).
 *
 * @param location The location
 * @param modelProducer Model producer containing the data (the first line also gets an area fill)
 * @param maxY max Y value for this chart
 * @param endAxisValueFormatter value formatter for the value axis
 * @param colors for each line, its colour by value (a vertical gradient)
 * @param topAxisValueFormatter text labels above the chart, for [topAxisValues]; null for none
 * @param topAxisValues the x values that may get a label above the chart (skipped evenly when they don't all fit)
 * @param topIconValues the x values that may get an icon above the chart (skipped evenly when they don't all fit)
 * @param topIconProvider the icon for a given x value, or null for none
 * @param topIconTint tints the icons (for monochrome icons such as arrows); null for full-color icons
 * @param endAxisItemPlacer which values get a label on the value axis
 * @param markerVisibilityListener told which time is picked while pressing and dragging along the chart
 * @param highLowLabels marks the highest and lowest points with "H" and "L"
 */
@Composable
fun BreezyLineChart(
    location: Location,
    modelProducer: CartesianChartModelProducer,
    theDay: Date,
    maxY: Double,
    endAxisValueFormatter: ChartValueFormatter,
    colors: ImmutableList<ImmutableMap<Float, Color>>,
    modifier: Modifier = Modifier,
    topAxisValueFormatter: ChartValueFormatter? = null,
    topAxisValues: ImmutableList<Long> = persistentListOf(),
    topIconValues: ImmutableList<Long> = persistentListOf(),
    topIconProvider: (x: Long) -> ImageBitmap? = { null },
    topIconTint: Color? = null,
    endAxisItemPlacer: ChartYLabels = ChartYLabels.Auto,
    trendHorizontalLines: ImmutableMap<Double, String> = persistentMapOf(),
    minY: Double? = null,
    markerVisibilityListener: CartesianMarkerVisibilityListener? = null,
    highLowLabels: Boolean = false,
) {
    val context = LocalContext.current
    DetailsChart(
        location = location,
        modelProducer = modelProducer,
        theDay = theDay,
        minY = minY,
        maxY = maxY,
        yLabels = endAxisItemPlacer,
        endAxisValueFormatter = endAxisValueFormatter,
        lineColors = colors,
        columnColor = null,
        topAxisValueFormatter = topAxisValueFormatter,
        topAxisValues = topAxisValues,
        topIconValues = topIconValues,
        topIconProvider = topIconProvider,
        topIconTint = topIconTint,
        trendHorizontalLines = trendHorizontalLines,
        markerVisibilityListener = markerVisibilityListener,
        highLowLabels = highLowLabels,
        height = max(LINE_CHART_HEIGHT_MIN_DP.toFloat(), context.windowHeightInDp.div(3)).dp,
        modifier = modifier
    )
}

private const val LINE_CHART_HEIGHT_MIN_DP = 100
