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

import android.view.ViewGroup
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Hourly
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.getHour
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.main.utils.ForecastMetric
import org.breezyweather.ui.main.utils.ForecastMetrics
import org.breezyweather.ui.main.utils.MetricCell
import org.breezyweather.ui.theme.resource.ResourceHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth
import java.util.Date
import kotlin.time.Duration.Companion.days

/**
 * Hourly forecast, iOS weather style, in Compose: the source's summary, a metric switcher
 * (conditions, precipitation, wind... from the user's hourly trend settings), then a horizontal
 * strip of hour / graphic / value starting with "Now". On conditions, sunrise and sunset show up
 * in the strip at their time. Tapping an hour opens the details of its day.
 */
class HourlyViewHolder(parent: ViewGroup) : ComposeCardViewHolder(parent) {

    private class Bound(
        val activity: BreezyActivity,
        val location: Location,
        val provider: ResourceProvider,
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
        val metrics = ForecastMetrics.hourlyMetrics(activity, weather)
        bound.value = Bound(
            activity = activity,
            location = location,
            provider = provider,
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

        Column(Modifier.padding(top = 14.dp, bottom = 12.dp)) {
            GlassCardHeader(R.drawable.ic_time, b.activity.getString(R.string.hourly_forecast), contentColor) {
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
            weather.current?.hourlyForecast?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    color = contentColor,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }
            GlassDivider(contentColor, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))
            Crossfade(metric, animationSpec = tween(250), label = "metric") { shown ->
                val entries = remember(b, shown) { entries(b, shown) }
                LazyRow(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .horizontalScrollRegion("strip"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is Entry.Hour -> HourItem(b, shown, entry)
                            is Entry.SunEvent -> SunEventItem(b, entry)
                        }
                    }
                }
            }
        }
    }

    private sealed interface Entry {
        val key: String

        class Hour(val hourly: Hourly, val isNow: Boolean) : Entry {
            override val key = "h${hourly.date.time}"
        }

        class SunEvent(val date: Date, val isSunrise: Boolean) : Entry {
            override val key = "s${date.time}"
        }
    }

    /** The hours, with sunrises and sunsets slotted in after the hour they happen in (conditions only). */
    private fun entries(b: Bound, metric: ForecastMetric): List<Entry> {
        val weather = b.location.weather ?: return emptyList()
        val hours = weather.nextHourlyForecast
        if (hours.isEmpty()) return emptyList()
        val events = if (metric == ForecastMetric.CONDITIONS) {
            weather.dailyForecast.drop(weather.todayIndex ?: 0).take(3).flatMap { daily ->
                val sun = daily.sun
                // Polar day / night have no real sunrise or sunset
                if (sun?.isValid != true || sun.setDate!!.time - sun.riseDate!!.time >= 1.days.inWholeMilliseconds - 1) {
                    emptyList()
                } else {
                    listOf(Entry.SunEvent(sun.riseDate!!, true), Entry.SunEvent(sun.setDate!!, false))
                }
            }.filter { it.date.time > hours.first().date.time && it.date.time < hours.last().date.time }
        } else {
            emptyList()
        }
        return buildList {
            hours.forEachIndexed { i, hourly ->
                add(Entry.Hour(hourly, i == 0))
                val next = hours.getOrNull(i + 1)?.date?.time ?: Long.MAX_VALUE
                events.filter { it.date.time >= hourly.date.time && it.date.time < next }
                    .sortedBy { it.date.time }
                    .forEach { add(it) }
            }
        }
    }

    @Composable
    private fun HourItem(b: Bound, metric: ForecastMetric, entry: Entry.Hour) {
        val activity = b.activity
        val hourly = entry.hourly
        val hourLabel = if (entry.isNow) activity.getString(R.string.hourly_now) else hourly.date.getHour(b.location, activity)
        val temperatureUnit = remember { SettingsManager.getInstance(activity).getTemperatureUnit(activity) }

        val cell = if (metric == ForecastMetric.CONDITIONS) null else ForecastMetrics.hourlyCell(activity, metric, hourly)
        // "Now" shows the observed temperature when there is one, like the hero
        val temperature = if (entry.isNow) {
            b.location.weather?.current?.temperature?.temperature ?: hourly.temperature?.temperature
        } else {
            hourly.temperature?.temperature
        }
        val precipitation = hourly.precipitationProbability?.total
        val value = cell?.let { it.text ?: "-" }
            ?: temperature?.let { GlassForecastFormat.degrees(activity, it, temperatureUnit) }
            ?: "-"
        val description = listOfNotNull(
            hourLabel,
            if (cell != null) {
                activity.getString(metric.nameId) + activity.getString(R.string.colon_separator) + value
            } else {
                listOfNotNull(
                    hourly.weatherText,
                    temperature?.formatMeasure(activity, temperatureUnit, unitWidth = UnitWidth.LONG),
                    precipitation?.let {
                        activity.getString(R.string.precipitation_probability) +
                            activity.getString(R.string.colon_separator) + it.formatPercent(activity)
                    }
                ).joinToString(activity.getString(org.breezyweather.unit.R.string.locale_separator))
            }
        ).joinToString(activity.getString(org.breezyweather.unit.R.string.locale_separator))

        StripColumn(
            label = hourLabel,
            value = value,
            // Values with units ("1015 hPa", "12 mph") need more room than a temperature
            valueSize = when {
                cell == null -> 19
                value.length > 4 -> 13
                else -> 17
            },
            description = description,
            onClick = {
                if (activity.isActivityResumed) {
                    // The day this hour belongs to
                    val dailyIndex = b.location.weather!!.dailyForecast.indexOfFirst {
                        it.date.time > hourly.date.time - 1.days.inWholeMilliseconds
                    }.takeIf { it != -1 }
                    IntentHelper.startDailyWeatherActivity(activity, b.location.formattedId, dailyIndex, DetailScreen.TAG_CONDITIONS)
                }
            }
        ) {
            if (cell == null) {
                val code = hourly.weatherCode
                val painter = rememberDrawablePainter(
                    code?.let { ResourceHelper.getWeatherIcon(b.provider, it, hourly.isDaylight) },
                    code to hourly.isDaylight
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // The frosted art has built-in padding, so it is drawn larger than a glyph would be
                    painter?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) }
                    GlassForecastFormat.precipitation(activity, precipitation)?.let {
                        Text(it, color = GlassPrecipitation, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                MetricGraphic(cell)
            }
        }
    }

    @Composable
    private fun SunEventItem(b: Bound, entry: Entry.SunEvent) {
        val activity = b.activity
        val time = entry.date.getFormattedTime(b.location, activity, activity.is12Hour)
        val name = activity.getString(if (entry.isSunrise) R.string.highlight_label_sunrise else R.string.highlight_label_sunset)
        StripColumn(
            label = time,
            labelSize = 13,
            value = name,
            valueSize = 13,
            description = activity.getString(
                if (entry.isSunrise) R.string.ephemeris_sunrise_at else R.string.ephemeris_sunset_at,
                time
            ),
            width = 66,
            onClick = null
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_twilight),
                contentDescription = null,
                tint = Color(0xFFFFD36B),
                modifier = Modifier.size(26.dp)
            )
        }
    }

    /** One column of the strip: label on top, graphic in the middle, value at the bottom. */
    @Composable
    private fun StripColumn(
        label: String,
        value: String,
        valueSize: Int,
        description: String,
        onClick: (() -> Unit)?,
        labelSize: Int = 15,
        width: Int = 58,
        graphic: @Composable () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .width(width.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .clearAndSetSemantics { contentDescription = description }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = contentColor, fontSize = labelSize.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .height(50.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                graphic()
            }
            Text(
                text = value,
                color = contentColor,
                fontSize = valueSize.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }

    /** Bar, wind arrow or colored dot of a metric's hour. */
    @Composable
    private fun MetricGraphic(cell: MetricCell) {
        when (cell.graphic) {
            MetricCell.Graphic.BAR -> {
                val track = contentColor.copy(alpha = 0.18f)
                val fill = Color(cell.color)
                Canvas(Modifier.size(14.dp, 44.dp)) {
                    val radius = CornerRadius(size.width / 2f)
                    drawRoundRect(track, cornerRadius = radius)
                    cell.fraction?.coerceIn(0f, 1f)?.takeIf { it > 0f }?.let { f ->
                        // At least a round cap, so small values still show
                        val h = (size.height * f).coerceAtLeast(size.width)
                        drawRoundRect(fill, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = radius)
                    }
                }
            }
            MetricCell.Graphic.ARROW -> cell.degree?.let {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_downward_alt),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(it)
                )
            }
            MetricCell.Graphic.DOT -> if (cell.text != null) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(Color(cell.color), CircleShape)
                )
            }
            MetricCell.Graphic.NONE -> Unit
        }
    }
}
