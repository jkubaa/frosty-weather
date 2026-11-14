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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import breezyweather.domain.location.model.Location
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import kotlinx.collections.immutable.persistentListOf
import org.breezyweather.common.extensions.windowHeightInDp
import java.util.Date
import kotlin.math.max

/**
 * A column chart of a day, with time as X-Axis and a formatted value on Y-Axis (see [DetailsChart]).
 *
 * @param location The location
 * @param modelProducer Model producer containing the data (column series)
 * @param maxY max Y value for this chart
 * @param endAxisValueFormatter value formatter for the value axis
 * @param barColor the columns' colour
 * @param markerVisibilityListener told which time is picked while pressing and dragging along the chart
 */
@Composable
fun BreezyBarChart(
    location: Location,
    modelProducer: CartesianChartModelProducer,
    theDay: Date,
    maxY: Double,
    endAxisValueFormatter: ChartValueFormatter,
    barColor: Color,
    modifier: Modifier = Modifier,
    endAxisItemPlacer: ChartYLabels = ChartYLabels.Auto,
    trendHorizontalLines: Map<Double, String> = emptyMap(),
    markerVisibilityListener: CartesianMarkerVisibilityListener? = null,
) {
    val context = LocalContext.current
    DetailsChart(
        location = location,
        modelProducer = modelProducer,
        theDay = theDay,
        minY = null,
        maxY = maxY,
        yLabels = endAxisItemPlacer,
        endAxisValueFormatter = endAxisValueFormatter,
        lineColors = persistentListOf(),
        columnColor = barColor,
        topAxisValueFormatter = null,
        topAxisValues = persistentListOf(),
        topIconValues = persistentListOf(),
        topIconProvider = { null },
        topIconTint = null,
        trendHorizontalLines = trendHorizontalLines,
        markerVisibilityListener = markerVisibilityListener,
        highLowLabels = false,
        height = max(BAR_CHART_HEIGHT_MIN_DP.toFloat(), context.windowHeightInDp.div(3)).dp,
        modifier = modifier
    )
}

private const val BAR_CHART_HEIGHT_MIN_DP = 100
