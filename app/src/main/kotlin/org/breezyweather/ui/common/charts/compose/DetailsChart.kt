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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.common.extensions.toCalendarWithTimeZone
import org.breezyweather.common.extensions.toTimezoneSpecificHour
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.details.LocalDetailsPalette
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The detail screens' chart: a day of values over time, as a line (with an area fill under the first
 * series) or as columns, drawn with the Compose canvas.
 *
 * It replaced Vico, which draws a chart's data in software into a bitmap the size of the chart on every
 * draw (20-50 ms on a 120 Hz phone): opening a sheet, switching days and scrubbing all stuttered. Here the
 * chart is laid out (text measured, paths built) once per data or size change, drawn on the GPU, and kept
 * in its own layer; scrubbing only draws a guideline over it.
 *
 * Its look follows the Vico charts it replaced: value axis at the end, times every 6 hours at the bottom,
 * dashed guidelines, the past dimmed with a "now" line and dot, optional H/L labels, icons or labels above.
 */
@Composable
internal fun DetailsChart(
    location: Location,
    modelProducer: CartesianChartModelProducer,
    theDay: Date,
    minY: Double?,
    maxY: Double,
    yLabels: ChartYLabels,
    endAxisValueFormatter: ChartValueFormatter,
    lineColors: List<Map<Float, Color>>,
    columnColor: Color?,
    topAxisValueFormatter: ChartValueFormatter?,
    topAxisValues: List<Long>,
    topIconValues: List<Long>,
    topIconProvider: (x: Long) -> ImageBitmap?,
    topIconTint: Color?,
    trendHorizontalLines: Map<Double, String>,
    markerVisibilityListener: CartesianMarkerVisibilityListener?,
    highLowLabels: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalDetailsPalette.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val trendLinesEnabled = remember { SettingsManager.getInstance(context).isTrendHorizontalLinesEnabled }
    val is12Hour = remember { context.is12Hour }

    val minX = remember(theDay) { theDay.toTimezoneSpecificHour(location.timeZone, 0).time.toDouble() }
    val maxX = remember(theDay) {
        theDay.toCalendarWithTimeZone(location.timeZone).apply { add(Calendar.DAY_OF_MONTH, 1) }
            .time.toTimezoneSpecificHour(location.timeZone, 0).time.toDouble()
    }
    // Every 6 hours, from the location's clock (DST-aware)
    val timeTicks = remember(theDay) {
        (6..18 step 6).map { theDay.toTimezoneSpecificHour(location.timeZone, it).time.toDouble() }
    }
    val now = remember(theDay) { System.currentTimeMillis().toDouble() }
    // Icons are rasterized once each
    val icons = remember(topIconValues, topIconProvider) { HashMap<Long, ImageBitmap?>() }

    // Where the points are, for scrubbing (written when the chart is laid out)
    val geometry = remember { ChartGeometry() }
    var active by remember { mutableIntStateOf(-1) }
    val marker = remember { object : CartesianMarker {} }
    val listener by rememberUpdatedState(markerVisibilityListener)

    val labelStyle = TextStyle(color = palette.secondaryText, fontSize = 12.sp)

    fun show(fingerX: Float) {
        val pxs = geometry.pxs
        if (pxs.isEmpty()) return
        var nearest = 0
        for (i in pxs.indices) if (abs(pxs[i] - fingerX) < abs(pxs[nearest] - fingerX)) nearest = i
        if (nearest == active) return
        val wasShown = active >= 0
        active = nearest
        val target = object : CartesianMarker.Target {
            override val x = geometry.xs[nearest]
            override val canvasX = pxs[nearest]
        }
        if (wasShown) {
            listener?.onUpdated(marker, listOf(target))
        } else {
            listener?.onShown(marker, listOf(target))
        }
    }

    fun hide() {
        if (active < 0) return
        active = -1
        listener?.onHidden(marker)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(geometry) {
                awaitEachGesture {
                    // Shown while pressed, following the finger (taking the movement, so the pager doesn't
                    // switch days meanwhile), hidden on release
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
                // The scrub guideline: the only thing drawn again while scrubbing
                val index = active
                if (index in geometry.pxs.indices) {
                    drawLine(
                        color = palette.secondaryText,
                        start = Offset(geometry.pxs[index], geometry.top),
                        end = Offset(geometry.pxs[index], geometry.bottom),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
    ) {
        Spacer(
            Modifier
                .fillMaxSize()
                // Its own layer: drawn again only when its data or size changes
                .graphicsLayer()
                .drawWithCache {
                    val model = modelProducer.model
                    val lines = model?.lines.orEmpty().filter { it.x.isNotEmpty() }
                    val columns = model?.columns.orEmpty().filter { it.x.isNotEmpty() }
                    val all = lines + columns

                    // Value range
                    val dataMin = all.minOfOrNull { s -> s.y.min() }
                    val yMin = minY ?: min(0.0, dataMin ?: 0.0)
                    val yMax = if (maxY > yMin) maxY else yMin + 1.0
                    val yValues = labelValues(yLabels, yMin, yMax)

                    // Text, measured once
                    fun measure(text: CharSequence, style: TextStyle = labelStyle) =
                        measurer.measure(AnnotatedString(text.toString()), style)
                    val yTexts = yValues.map { measure(endAxisValueFormatter.format(context, it, null)) }
                    val timeTexts = timeTicks.map {
                        measure(Date(it.toLong()).getFormattedTime(location, context, is12Hour))
                    }
                    val topTexts = topAxisValueFormatter?.let { formatter ->
                        topAxisValues.associateWith { measure(formatter.format(context, it.toDouble(), null)) }
                    }

                    val tick = 4.dp.toPx()
                    val pad = 4.dp.toPx()
                    val lineWidth = 1.dp.toPx()
                    val iconSize = ChartIconSize.toPx()
                    val labelHeight = yTexts.maxOfOrNull { it.size.height } ?: measure("0").size.height
                    val axisWidth = tick + pad + (yTexts.maxOfOrNull { it.size.width } ?: 0)
                    val bottomHeight = tick + pad + (timeTexts.maxOfOrNull { it.size.height } ?: 0)
                    val topLabelsHeight = topTexts?.values?.maxOfOrNull { it.size.height + pad } ?: 0f
                    val topIconsHeight = if (topIconValues.isNotEmpty()) iconSize + 5.dp.toPx() else 0f

                    // The data area
                    val plotLeft = if (rtl) axisWidth else 0f
                    val plotRight = if (rtl) size.width else size.width - axisWidth
                    val plotTop = topIconsHeight + topLabelsHeight + labelHeight / 2f
                    val plotBottom = size.height - bottomHeight
                    val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
                    val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

                    // Columns are centered on their x value, with room for half a column at each end
                    val xStep = all.flatMap { s -> s.x.toList() }.sorted().zipWithNext { a, b -> b - a }
                        .filter { it > 0 }.minOrNull() ?: 3_600_000.0
                    val slot = if (columns.isEmpty()) 0f else plotWidth / ((maxX - minX) / xStep + 1).toFloat()
                    val innerStart = slot / 2f
                    val innerWidth = (plotWidth - slot).coerceAtLeast(1f)
                    fun xToPx(x: Double): Float {
                        val f = ((x - minX) / (maxX - minX)).toFloat()
                        return if (rtl) plotRight - innerStart - f * innerWidth else plotLeft + innerStart + f * innerWidth
                    }
                    fun yToPx(y: Double): Float =
                        plotBottom - ((y - yMin) / (yMax - yMin)).toFloat() * plotHeight

                    // Colour by value, as a vertical gradient over the chart
                    fun valueBrush(colors: Map<Float, Color>, alpha: Float = 1f): Brush {
                        val stops = colors.entries.sortedByDescending { it.key }
                            .map { yToPx(it.key.toDouble()) to it.value.copy(alpha = it.value.alpha * alpha) }
                        if (stops.isEmpty()) return SolidColor(Color.Transparent)
                        val startY = stops.minOf { it.first }
                        val endY = stops.maxOf { it.first }
                        if (stops.size == 1 || endY - startY < 0.5f) return SolidColor(stops.first().second)
                        return Brush.verticalGradient(
                            *stops.map { ((it.first - startY) / (endY - startY)) to it.second }.toTypedArray(),
                            startY = startY,
                            endY = endY
                        )
                    }

                    val baseline = yToPx(0.0).coerceIn(plotTop, plotBottom)
                    val linePaths = lines.mapIndexed { index, s ->
                        val order = s.x.indices.sortedBy { s.x[it] }
                        val path = Path()
                        order.forEachIndexed { i, p ->
                            val px = xToPx(s.x[p])
                            val py = yToPx(s.y[p])
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        val area = if (index == 0 && order.isNotEmpty()) {
                            Path().apply {
                                addPath(path)
                                lineTo(xToPx(s.x[order.last()]), baseline)
                                lineTo(xToPx(s.x[order.first()]), baseline)
                                close()
                            }
                        } else {
                            null
                        }
                        val colors = lineColors.getOrElse(index) { lineColors.lastOrNull() ?: emptyMap() }
                        LinePlot(path, area, valueBrush(colors), area?.let { valueBrush(colors, AREA_ALPHA) })
                    }

                    // Points, for scrubbing
                    val xs = all.flatMap { s -> s.x.toList() }.distinct().sorted().toDoubleArray()
                    geometry.xs = xs
                    geometry.pxs = FloatArray(xs.size) { xToPx(xs[it]) }
                    geometry.top = plotTop
                    geometry.bottom = plotBottom

                    // Icons and labels above, where they fit (TimeTopAxisItemPlacer's spacing)
                    val iconXs = if (topIconValues.isNotEmpty()) {
                        fittingValues(topIconValues, minX, maxX, plotWidth, iconSize)
                    } else {
                        emptyList()
                    }
                    val topLabelXs = topTexts?.let { texts ->
                        fittingValues(topAxisValues, minX, maxX, plotWidth, texts.values.maxOf { it.size.width }.toFloat())
                    }.orEmpty()

                    // "Now", highest and lowest
                    val first = lines.firstOrNull()
                    val nowX = if (now > minX && now < maxX) xToPx(now) else null
                    val nowY = if (nowX != null && first != null) interpolate(first, now)?.let { yToPx(it) } else null
                    val extremes = if (highLowLabels && first != null && first.y.size > 1) {
                        val hi = first.y.indices.maxBy { first.y[it] }
                        val lo = first.y.indices.minBy { first.y[it] }
                        if (first.y[hi] != first.y[lo]) {
                            val style = labelStyle.copy(fontSize = 12.dp.toSp(), fontWeight = FontWeight.Bold)
                            listOf(
                                Offset(xToPx(first.x[hi]), yToPx(first.y[hi])) to measurer.measure("H", style),
                                Offset(xToPx(first.x[lo]), yToPx(first.y[lo])) to measurer.measure("L", style)
                            )
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                    val trends = if (trendLinesEnabled) {
                        trendHorizontalLines.filterKeys { it in yMin..yMax }
                            .map { (y, label) -> yToPx(y) to measure(label) }
                    } else {
                        emptyList()
                    }

                    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                    val stroke = Stroke(width = LINE_THICKNESS.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val grid = palette.grid
                    val columnWidth = slot * 0.9f
                    val columnRadius = CornerRadius(columnWidth * 0.15f)

                    onDrawBehind {
                        // Guidelines
                        yValues.forEach { v ->
                            val py = yToPx(v)
                            if (abs(py - plotBottom) > 0.5f) {
                                drawLine(grid, Offset(plotLeft, py), Offset(plotRight, py), lineWidth, pathEffect = dash)
                            }
                        }
                        timeTicks.forEach { t ->
                            val px = xToPx(t)
                            drawLine(grid, Offset(px, plotTop), Offset(px, plotBottom), lineWidth, pathEffect = dash)
                        }

                        // Data
                        clipRect(plotLeft, plotTop - stroke.width, plotRight, plotBottom) {
                            linePaths.forEach { plot ->
                                if (plot.area != null && plot.areaBrush != null) drawPath(plot.area, plot.areaBrush)
                            }
                            columns.forEach { s ->
                                for (i in s.x.indices) {
                                    val top = yToPx(s.y[i])
                                    if (abs(top - baseline) < 0.5f) continue
                                    drawRoundRect(
                                        color = columnColor ?: palette.text,
                                        topLeft = Offset(xToPx(s.x[i]) - columnWidth / 2f, min(top, baseline)),
                                        size = Size(columnWidth, abs(baseline - top)),
                                        cornerRadius = columnRadius
                                    )
                                }
                            }
                            linePaths.forEach { plot -> drawPath(plot.line, plot.lineBrush, style = stroke) }
                        }

                        // Trend lines, labelled under the line at the start
                        trends.forEach { (py, text) ->
                            drawLine(grid, Offset(plotLeft, py), Offset(plotRight, py), lineWidth)
                            val x = if (rtl) plotRight - text.size.width - pad else plotLeft + pad
                            drawText(text, topLeft = Offset(x, py + 2.dp.toPx()))
                        }

                        // The past dimmed, the "now" line and dot
                        if (nowX != null) {
                            drawRect(
                                palette.sheet.copy(alpha = 0.5f),
                                topLeft = Offset(if (rtl) nowX else plotLeft, plotTop),
                                size = Size(if (rtl) plotRight - nowX else nowX - plotLeft, plotHeight)
                            )
                            drawLine(
                                palette.secondaryText.copy(alpha = 0.6f),
                                Offset(nowX, plotTop),
                                Offset(nowX, plotBottom),
                                lineWidth
                            )
                            if (nowY != null) {
                                drawCircle(palette.sheet, 5.dp.toPx(), Offset(nowX, nowY))
                                drawCircle(palette.text, 3.dp.toPx(), Offset(nowX, nowY))
                            }
                        }
                        extremes.forEach { (center, text) ->
                            drawCircle(palette.sheet, 5.dp.toPx(), center)
                            drawCircle(palette.secondaryText, 2.dp.toPx(), center)
                            val baselineY = (center.y - 12.dp.toPx()).coerceAtLeast(plotTop + text.firstBaseline)
                            drawText(text, topLeft = Offset(center.x - text.size.width / 2f, baselineY - text.firstBaseline))
                        }

                        // Axes: values at the end, times at the bottom
                        val axisX = if (rtl) plotLeft else plotRight
                        drawLine(grid, Offset(axisX, plotTop), Offset(axisX, plotBottom), lineWidth)
                        drawLine(grid, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), lineWidth)
                        yValues.forEachIndexed { i, v ->
                            val py = yToPx(v)
                            val text = yTexts[i]
                            drawLine(grid, Offset(axisX, py), Offset(axisX + if (rtl) -tick else tick, py), lineWidth)
                            val x = if (rtl) axisX - tick - pad - text.size.width else axisX + tick + pad
                            drawText(text, topLeft = Offset(x, py - text.size.height / 2f))
                        }
                        timeTicks.forEachIndexed { i, t ->
                            val px = xToPx(t)
                            val text = timeTexts[i]
                            drawLine(grid, Offset(px, plotBottom), Offset(px, plotBottom + tick), lineWidth)
                            drawText(text, topLeft = Offset(px - text.size.width / 2f, plotBottom + tick + pad))
                        }

                        // Labels and icons above
                        if (topTexts != null) {
                            drawLine(grid, Offset(plotLeft, plotTop), Offset(plotRight, plotTop), lineWidth)
                            topLabelXs.forEach { x ->
                                val text = topTexts[x] ?: return@forEach
                                val labelTop = plotTop - labelHeight / 2f - pad - text.size.height
                                drawText(text, topLeft = Offset(xToPx(x.toDouble()) - text.size.width / 2f, labelTop))
                            }
                        }
                        val iconFilter = topIconTint?.let { ColorFilter.tint(it, BlendMode.SrcAtop) }
                        iconXs.forEach { x ->
                            val bitmap = icons.getOrPut(x) { topIconProvider(x) } ?: return@forEach
                            val iconTop = plotTop - labelHeight / 2f - topLabelsHeight - iconSize
                            drawImage(
                                bitmap,
                                dstOffset = IntOffset(
                                    (xToPx(x.toDouble()) - iconSize / 2f).roundToInt(),
                                    iconTop.roundToInt()
                                ),
                                dstSize = IntSize(iconSize.roundToInt(), iconSize.roundToInt()),
                                colorFilter = iconFilter
                            )
                        }
                    }
                }
        )
    }
}

private class LinePlot(val line: Path, val area: Path?, val lineBrush: Brush, val areaBrush: Brush?)

/** Where the points are drawn, for scrubbing. Plain fields: written when laid out, read on touch and in draw. */
private class ChartGeometry {
    var xs = DoubleArray(0)
    var pxs = FloatArray(0)
    var top = 0f
    var bottom = 0f
}

private val LINE_THICKNESS = 3.5.dp

/**
 * Size of the icons above a chart. Rasterize icons at this size (in px) rather than at their intrinsic
 * size: scaled down when drawn, thin strokes fade into a greyish blur.
 */
val ChartIconSize = 18.dp
private const val AREA_ALPHA = 0.45f

/** Values that get a label on the value axis. */
private fun labelValues(labels: ChartYLabels, min: Double, max: Double): List<Double> {
    val epsilon = (max - min) * 1e-6
    return when (labels) {
        is ChartYLabels.Values -> labels.values.filter { it >= min - epsilon && it <= max + epsilon }
        is ChartYLabels.Step -> stepValues(labels.step(), min, max)
        ChartYLabels.Auto -> stepValues(niceStep((max - min) / 4.0), min, max)
    }
}

private fun stepValues(step: Double, min: Double, max: Double): List<Double> {
    if (step <= 0.0 || step.isNaN()) return listOf(min, max)
    val epsilon = step * 1e-6
    val values = mutableListOf<Double>()
    var v = ceil((min - epsilon) / step) * step
    while (v <= max + epsilon && values.size < 50) {
        values += v
        v += step
    }
    return values
}

private fun niceStep(raw: Double): Double {
    if (raw <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(raw)))
    val f = raw / magnitude
    return magnitude * when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 5.0 -> 5.0
        else -> 10.0
    }
}

/**
 * The values that get a label or icon above the chart, skipping hours evenly when they don't all fit
 * (as TimeTopAxisItemPlacer did for Vico).
 */
private fun fittingValues(
    possible: List<Long>,
    minX: Double,
    maxX: Double,
    width: Float,
    itemWidth: Float,
): List<Long> {
    val hoursDisplayed = ((maxX - minX) / 3_600_000.0).roundToInt()
    val fit = floor(width / itemWidth.coerceAtLeast(1f))
    val hoursToSkip = ceil(hoursDisplayed / fit).coerceAtLeast(0f).toInt()
    val margin = (hoursToSkip - 1).coerceAtLeast(1) * 3_600_000L
    val first = minX.toLong() + margin
    val last = maxX.toLong() - margin
    val result = mutableListOf<Long>()
    var previous = 0L
    possible.filter { it in first..last }.sorted().forEach { value ->
        if (value >= previous + hoursToSkip * 3_600_000L) {
            result += value
            previous = value
        }
    }
    return result
}

/** The series' value at [x], interpolated between its points. */
private fun interpolate(series: ChartSeries, x: Double): Double? {
    val order = series.x.indices.sortedBy { series.x[it] }
    if (order.isEmpty()) return null
    if (x <= series.x[order.first()]) return series.y[order.first()]
    if (x >= series.x[order.last()]) return series.y[order.last()]
    for (k in 1 until order.size) {
        val a = order[k - 1]
        val b = order[k]
        if (x <= series.x[b]) {
            val t = if (series.x[b] == series.x[a]) 0.0 else (x - series.x[a]) / (series.x[b] - series.x[a])
            return series.y[a] + (series.y[b] - series.y[a]) * t
        }
    }
    return null
}
