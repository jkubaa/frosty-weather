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

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * Data and options for the detail screens' charts (see DetailsChart), shaped like Vico's so the screens
 * barely changed when the charts stopped using Vico: `modelProducer.runTransaction { lineModel { series(x, y) } }`.
 */

/** One series: x values (times, in ms) and their y values. */
@Immutable
class ChartSeries(val x: DoubleArray, val y: DoubleArray)

/** What a chart shows: lines (the first one also gets an area fill), and columns. */
@Immutable
class ChartModel(val lines: List<ChartSeries>, val columns: List<ChartSeries>)

/** Holds a chart's data. Set it from a coroutine, as with Vico's producer of the same name. */
@Stable
class CartesianChartModelProducer {
    var model by mutableStateOf<ChartModel?>(null)
        private set

    suspend fun runTransaction(block: Transaction.() -> Unit) {
        model = Transaction().apply(block).build()
    }

    class Transaction internal constructor() {
        internal val lines = mutableListOf<ChartSeries>()
        internal val columns = mutableListOf<ChartSeries>()

        internal fun build() = ChartModel(lines.toList(), columns.toList())
    }
}

class ChartSeriesScope internal constructor(private val into: MutableList<ChartSeries>) {
    fun series(x: Collection<Number>, y: Collection<Number>) {
        into += ChartSeries(
            x.map { it.toDouble() }.toDoubleArray(),
            y.map { it.toDouble() }.toDoubleArray()
        )
    }
}

/** Adds line series. */
fun CartesianChartModelProducer.Transaction.lineModel(block: ChartSeriesScope.() -> Unit) {
    ChartSeriesScope(lines).block()
}

/** Adds column series. */
fun CartesianChartModelProducer.Transaction.columnModel(block: ChartSeriesScope.() -> Unit) {
    ChartSeriesScope(columns).block()
}

/** Formats an axis value (the first and last parameters are only there to keep Vico's lambda shape). */
fun interface ChartValueFormatter {
    fun format(context: Context, value: Double, unused: Unit?): CharSequence
}

/** Which values get a label (and a guideline) on the value axis. */
sealed interface ChartYLabels {
    /** Every multiple of [step] in the chart's range. */
    class Step(val step: () -> Double) : ChartYLabels

    /** These values, those in the chart's range. */
    class Values(val values: List<Double>) : ChartYLabels

    /** A few round values. */
    data object Auto : ChartYLabels

    companion object {
        fun step(step: () -> Double): ChartYLabels = Step(step)
    }
}
