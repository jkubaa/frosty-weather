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

package org.breezyweather.ui.main.adapters.main.holder

import android.content.Context
import android.view.ViewGroup
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Daily
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.weather.model.getWeek
import org.breezyweather.ui.main.utils.ForecastMetric
import org.breezyweather.ui.main.utils.ForecastMetrics
import org.breezyweather.ui.main.utils.MetricCell
import org.breezyweather.ui.theme.resource.ResourceHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.temperature.TemperatureUnit

/**
 * 10-day forecast, iOS weather style, in Compose: one row per day with its name, condition icon
 * (and chance of precipitation), low, a temperature range bar on a shared scale, and high; or,
 * with another metric selected, the day's value with a fill bar. Tapping a day opens its details.
 */
class DailyViewHolder(context: Context) : ComposeCardViewHolder(context) {

    private class Bound(
        val activity: BreezyActivity,
        val location: Location,
        val provider: ResourceProvider,
        val days: List<IndexedValue<Daily>>,
        val metrics: List<ForecastMetric>,
        val initial: ForecastMetric,
        val onSelect: (ForecastMetric) -> Unit,
    )

    private val bound = mutableStateOf<Bound?>(null)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
        selectedTab: String?,
        setSelectedTab: (String?) -> Unit,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        val weather = location.weather ?: return
        // From today (no past days), like iOS
        val days = weather.dailyForecast.withIndex().drop(weather.todayIndex ?: 0).take(MAX_DAYS)
        val metrics = ForecastMetrics.dailyMetrics(activity, days.map { it.value })
        bound.value = Bound(
            activity = activity,
            location = location,
            provider = provider,
            days = days,
            metrics = metrics,
            initial = metrics.firstOrNull { activity.getString(it.nameId) == selectedTab } ?: ForecastMetric.CONDITIONS,
            onSelect = { setSelectedTab(activity.getString(it.nameId)) }
        )
    }

    @Composable
    override fun Content() {
        val b = bound.value ?: return
        val weather = b.location.weather ?: return
        var metric by remember(b) { mutableStateOf(b.initial) }

        Column(Modifier.padding(top = 14.dp, bottom = 6.dp)) {
            GlassCardHeader(R.drawable.ic_calendar, b.activity.getString(R.string.daily_forecast), contentColor) {
                MetricSwitcher(
                    b.metrics,
                    metric,
                    contentColor,
                    { b.activity.getString(it.nameId) },
                    Modifier.horizontalScrollRegion("switcher")
                ) {
                    metric = it
                    b.onSelect(it)
                }
            }
            weather.current?.dailyForecast?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    color = contentColor,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }
            Crossfade(metric, animationSpec = tween(250), label = "metric", modifier = Modifier.padding(top = 6.dp)) { shown ->
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (shown == ForecastMetric.CONDITIONS) {
                        ConditionRows(b)
                    } else {
                        MetricRows(b, shown)
                    }
                }
            }
        }
    }

    private fun dayName(b: Bound, index: Int, daily: Daily): String =
        if (index == b.location.weather?.todayIndex) {
            b.activity.getString(R.string.daily_today_short)
        } else {
            daily.getWeek(b.location, b.activity)
        }

    private fun open(b: Bound, index: Int, screen: DetailScreen) {
        if (b.activity.isActivityResumed) {
            IntentHelper.startDailyWeatherActivity(b.activity, b.location.formattedId, index, screen)
        }
    }

    /** Conditions: day, icon with chance of precipitation, low, temperature range bar, high. */
    @Composable
    private fun ConditionRows(b: Bound) {
        val activity = b.activity
        val weather = b.location.weather!!
        val unit = remember { SettingsManager.getInstance(activity).getTemperatureUnit(activity) }
        // All bars share one scale: coldest to warmest temperature of the shown days
        val ranges = remember(b) { b.days.map { it.value.lowAndHigh() } }
        val scaleMin = ranges.mapNotNull { it.first?.toDouble(TemperatureUnit.CELSIUS) }.minOrNull()
        val scaleMax = ranges.mapNotNull { it.second?.toDouble(TemperatureUnit.CELSIUS) }.maxOrNull()

        b.days.forEachIndexed { row, (index, daily) ->
            if (row > 0) GlassDivider(contentColor)
            val name = dayName(b, index, daily)
            val (low, high) = ranges[row]
            val precipitation = listOfNotNull(
                daily.day?.precipitationProbability?.total,
                daily.night?.precipitationProbability?.total
            ).maxByOrNull { it.inPercent }
            val code = daily.day?.weatherCode ?: daily.night?.weatherCode
            val isToday = index == weather.todayIndex
            val description = listOfNotNull(
                name,
                high?.let {
                    activity.getString(R.string.daytime) + activity.getString(R.string.colon_separator) +
                        it.formatMeasure(activity, unit, unitWidth = UnitWidth.LONG)
                },
                low?.let {
                    activity.getString(R.string.nighttime) + activity.getString(R.string.colon_separator) +
                        it.formatMeasure(activity, unit, unitWidth = UnitWidth.LONG)
                },
                precipitation?.let {
                    activity.getString(R.string.precipitation_probability) +
                        activity.getString(R.string.colon_separator) + it.formatPercent(activity)
                }
            ).joinToString(activity.getString(org.breezyweather.unit.R.string.locale_separator))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable { open(b, index, DetailScreen.TAG_CONDITIONS) }
                    .clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, color = contentColor, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.width(64.dp))
                Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val painter = rememberDrawablePainter(code?.let { ResourceHelper.getWeatherIcon(b.provider, it, true) }, code)
                    // The frosted art has built-in padding, so it is drawn larger than a glyph would be
                    painter?.let { Image(it, contentDescription = null, modifier = Modifier.size(34.dp)) }
                    GlassForecastFormat.precipitation(activity, precipitation)?.let {
                        Text(it, color = GlassPrecipitation, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    text = low?.let { GlassForecastFormat.degrees(activity, it, unit) } ?: "",
                    color = contentColor.copy(alpha = 0.6f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp)
                )
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (scaleMin != null && scaleMax != null) {
                        RangeBar(
                            scaleMin = scaleMin,
                            scaleMax = scaleMax,
                            low = low?.toDouble(TemperatureUnit.CELSIUS),
                            high = high?.toDouble(TemperatureUnit.CELSIUS),
                            current = if (isToday) weather.current?.temperature?.temperature?.toDouble(TemperatureUnit.CELSIUS) else null
                        )
                    }
                }
                Text(
                    text = high?.let { GlassForecastFormat.degrees(activity, it, unit) } ?: "",
                    color = contentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp)
                )
            }
        }
    }

    /** One row per day for [metric]: day, wind arrow, a fill bar, the value. */
    @Composable
    private fun MetricRows(b: Bound, metric: ForecastMetric) {
        val activity = b.activity
        val cells = remember(b, metric) { b.days.map { ForecastMetrics.dailyCell(activity, metric, it.value) } }
        // Same layout on every row of a metric: the arrow column only exists when some day has one
        val hasArrows = cells.any { it.graphic == MetricCell.Graphic.ARROW }
        b.days.forEachIndexed { row, (index, daily) ->
            if (row > 0) GlassDivider(contentColor)
            val cell = cells[row]
            val name = dayName(b, index, daily)
            val value = cell.text ?: "-"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { open(b, index, metric.detailScreen()) }
                    .clearAndSetSemantics {
                        contentDescription = name + activity.getString(org.breezyweather.unit.R.string.locale_separator) +
                            activity.getString(metric.nameId) + activity.getString(R.string.colon_separator) + value
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, color = contentColor, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.width(64.dp))
                if (hasArrows) {
                    Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                        cell.degree?.let {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_downward_alt),
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp).rotate(it)
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (cell.fraction != null || cell.graphic == MetricCell.Graphic.BAR) {
                        FillBar(cell.fraction, Color(cell.color))
                    }
                }
                Text(
                    text = value,
                    color = contentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 56.dp)
                )
            }
        }
    }

    /**
     * A faint capsule track spans the coldest to the warmest temperature of the list; a colored
     * capsule spans the day's low to high, colored from an absolute temperature scale (blue when
     * freezing to red when hot); today also gets a dot at the current temperature. All in °C.
     */
    @Composable
    private fun RangeBar(scaleMin: Double, scaleMax: Double, low: Double?, high: Double?, current: Double?) {
        val track = contentColor.copy(alpha = 0.18f)
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val barHeight = 5.dp.toPx()
            val radius = barHeight / 2f
            val cy = size.height / 2f
            val span = (scaleMax - scaleMin).takeIf { it > 0 } ?: 1.0
            fun x(t: Double) = radius + ((t - scaleMin) / span).coerceIn(0.0, 1.0).toFloat() * (size.width - barHeight)

            drawRoundRect(track, Offset(0f, cy - radius), Size(size.width, barHeight), CornerRadius(radius))
            val l = low ?: high
            val h = high ?: low
            if (l != null && h != null) {
                // Gradient laid over the whole track, so the segment shows the colors of the temperatures it covers
                val brush = Brush.horizontalGradient(
                    colors = (0..12).map { Color(temperatureColor(scaleMin + (scaleMax - scaleMin) * it / 12)) },
                    startX = x(scaleMin),
                    endX = x(scaleMax)
                )
                val left = x(minOf(l, h)) - radius
                val right = x(maxOf(l, h)) + radius
                drawRoundRect(brush, Offset(left, cy - radius), Size(right - left, barHeight), CornerRadius(radius))
            }
            current?.let {
                val dot = Offset(x(it), cy)
                drawCircle(Color.Black.copy(alpha = 0.35f), 3.5.dp.toPx() + 1.dp.toPx(), dot, style = Stroke(2.dp.toPx()))
                drawCircle(Color.White, 3.5.dp.toPx(), dot)
            }
        }
    }

    @Composable
    private fun FillBar(fraction: Float?, color: Color) {
        val track = contentColor.copy(alpha = 0.18f)
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(track, cornerRadius = radius)
            fraction?.coerceIn(0f, 1f)?.takeIf { it > 0f }?.let {
                drawRoundRect(color, size = Size((size.width * it).coerceAtLeast(size.height), size.height), cornerRadius = radius)
            }
        }
    }

    /** Opens the details on the tab matching the metric. */
    private fun ForecastMetric.detailScreen(): DetailScreen = when (this) {
        ForecastMetric.PRECIPITATION -> DetailScreen.TAG_PRECIPITATION
        ForecastMetric.WIND -> DetailScreen.TAG_WIND
        ForecastMetric.AIR_QUALITY -> DetailScreen.TAG_AIR_QUALITY
        ForecastMetric.UV_INDEX -> DetailScreen.TAG_UV_INDEX
        ForecastMetric.HUMIDITY -> DetailScreen.TAG_HUMIDITY
        ForecastMetric.PRESSURE -> DetailScreen.TAG_PRESSURE
        ForecastMetric.VISIBILITY -> DetailScreen.TAG_VISIBILITY
        else -> DetailScreen.TAG_CONDITIONS
    }

    /** Low and high of the day: Breezy sources give daytime and nighttime temperatures. */
    private fun Daily.lowAndHigh() = listOfNotNull(
        day?.temperature?.temperature,
        night?.temperature?.temperature
    ).let { temperatures ->
        temperatures.minByOrNull { it.toDouble(TemperatureUnit.CELSIUS) } to
            temperatures.maxByOrNull { it.toDouble(TemperatureUnit.CELSIUS) }
    }

    companion object {
        private const val MAX_DAYS = 10

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

        private fun temperatureColor(celsius: Double): Int {
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
