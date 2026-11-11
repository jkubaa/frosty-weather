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

package org.breezyweather.ui.main.adapters.main.holder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Daily
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.weather.model.getWeek
import org.breezyweather.ui.main.utils.ForecastMetric
import org.breezyweather.ui.main.utils.ForecastMetrics
import org.breezyweather.ui.main.utils.MetricCell
import org.breezyweather.ui.main.widgets.FillBarView
import org.breezyweather.ui.main.widgets.TemperatureRangeBarView
import org.breezyweather.ui.theme.resource.ResourceHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.temperature.TemperatureUnit

/**
 * 10-day forecast, iOS weather style: one row per day with its name, condition icon (and chance of
 * precipitation), low, a temperature range bar on a shared scale, and high. Tapping a day opens its details.
 */
class DailyViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_daily_trend_card, parent, false)
) {
    private val subtitle: TextView = itemView.findViewById(R.id.daily_block_subtitle)
    private val rows: LinearLayout = itemView.findViewById(R.id.daily_block_rows)
    private val switcher = MetricSwitcher(itemView.findViewById(R.id.daily_block_switcher))

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

        if (weather.current?.dailyForecast.isNullOrEmpty()) {
            subtitle.visibility = View.GONE
        } else {
            subtitle.visibility = View.VISIBLE
            subtitle.text = weather.current?.dailyForecast
        }

        // From today (no past days), like iOS
        val firstIndex = weather.todayIndex ?: 0
        val days = weather.dailyForecast.withIndex().drop(firstIndex).take(MAX_DAYS)

        // Metric switcher: conditions (temperature range bars) or another metric from the trend settings
        val metrics = ForecastMetrics.dailyMetrics(activity, days.map { it.value })
        fun show(metric: ForecastMetric) {
            switcher.bind(metrics, metric) {
                setSelectedTab(activity.getString(it.nameId))
                show(it)
            }
            if (metric == ForecastMetric.CONDITIONS) {
                bindConditionRows(activity, location, provider, days)
            } else {
                bindMetricRows(activity, location, days, metric)
            }
        }
        show(metrics.firstOrNull { activity.getString(it.nameId) == selectedTab } ?: ForecastMetric.CONDITIONS)
    }

    /** One row per day for [metric]: day, arrow or dot, a fill bar, the value. */
    private fun bindMetricRows(
        activity: BreezyActivity,
        location: Location,
        days: List<IndexedValue<Daily>>,
        metric: ForecastMetric,
    ) {
        val weather = location.weather!!
        val contentColor = activity.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse)
        val inflater = LayoutInflater.from(activity)
        rows.removeAllViews()
        days.forEach { (dailyIndex, daily) ->
            val row = inflater.inflate(R.layout.item_glass_daily_metric_row, rows, false)
            val cell = ForecastMetrics.dailyCell(activity, metric, daily)
            val dayName = if (dailyIndex == weather.todayIndex) {
                activity.getString(R.string.daily_today_short)
            } else {
                daily.getWeek(location, activity)
            }
            row.findViewById<TextView>(R.id.daily_metric_day).text = dayName
            // Same layout on every row of a metric: the arrow column only exists for wind
            row.findViewById<View>(R.id.daily_metric_graphic).visibility =
                if (cell.graphic == MetricCell.Graphic.ARROW) View.VISIBLE else View.GONE
            row.findViewById<ImageView>(R.id.daily_metric_arrow).apply {
                visibility = if (cell.graphic == MetricCell.Graphic.ARROW && cell.degree != null) View.VISIBLE else View.GONE
                cell.degree?.let { rotation = it }
            }
            row.findViewById<View>(R.id.daily_metric_dot).visibility = View.GONE
            row.findViewById<FillBarView>(R.id.daily_metric_bar).apply {
                visibility = if (cell.fraction != null || cell.graphic == MetricCell.Graphic.BAR) View.VISIBLE else View.INVISIBLE
                setFill(cell.fraction, cell.color, contentColor)
            }
            row.findViewById<TextView>(R.id.daily_metric_value).text = cell.text ?: "-"
            row.contentDescription = listOf(
                dayName,
                activity.getString(metric.nameId) + activity.getString(R.string.colon_separator) + (cell.text ?: "-")
            ).joinToString(activity.getString(org.breezyweather.unit.R.string.locale_separator))
            row.setOnClickListener {
                if (activity.isActivityResumed) {
                    IntentHelper.startDailyWeatherActivity(
                        activity,
                        location.formattedId,
                        dailyIndex,
                        metric.detailScreen()
                    )
                }
            }
            rows.addView(row)
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

    /** Conditions: day, icon with chance of precipitation, low, temperature range bar, high. */
    private fun bindConditionRows(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        days: List<IndexedValue<Daily>>,
    ) {
        val weather = location.weather!!

        // All bars share one scale: coldest to warmest temperature of the shown days
        val celsiusRanges = days.map { it.value.celsiusRange() }
        val scaleMin = celsiusRanges.mapNotNull { it.first }.minOrNull()
        val scaleMax = celsiusRanges.mapNotNull { it.second }.maxOrNull()

        val temperatureUnit = SettingsManager.getInstance(activity).getTemperatureUnit(activity)
        val contentColor = activity.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse)
        val inflater = LayoutInflater.from(activity)

        rows.removeAllViews()
        days.forEachIndexed { rowIndex, (dailyIndex, daily) ->
            val row = inflater.inflate(R.layout.item_glass_daily_row, rows, false)
            val dayText: TextView = row.findViewById(R.id.daily_row_day)
            val icon: ImageView = row.findViewById(R.id.daily_row_icon)
            val precipitationText: TextView = row.findViewById(R.id.daily_row_precipitation)
            val lowText: TextView = row.findViewById(R.id.daily_row_low)
            val highText: TextView = row.findViewById(R.id.daily_row_high)
            val rangeBar: TemperatureRangeBarView = row.findViewById(R.id.daily_row_range)

            val isToday = dailyIndex == weather.todayIndex
            val dayName = if (isToday) {
                activity.getString(R.string.daily_today_short)
            } else {
                daily.getWeek(location, activity)
            }
            dayText.text = dayName

            val weatherCode = daily.day?.weatherCode ?: daily.night?.weatherCode
            icon.setImageDrawable(weatherCode?.let { ResourceHelper.getWeatherIcon(provider, it, true) })

            val precipitation = listOfNotNull(
                daily.day?.precipitationProbability?.total,
                daily.night?.precipitationProbability?.total
            ).maxByOrNull { it.inPercent }
            val precipitationLabel = GlassForecastFormat.precipitation(activity, precipitation)
            precipitationText.visibility = if (precipitationLabel != null) View.VISIBLE else View.GONE
            precipitationText.text = precipitationLabel

            val (low, high) = daily.lowAndHigh()
            lowText.text = low?.let { GlassForecastFormat.degrees(activity, it, temperatureUnit) }
            highText.text = high?.let { GlassForecastFormat.degrees(activity, it, temperatureUnit) }

            rangeBar.setTrackColor(contentColor)
            if (scaleMin != null && scaleMax != null) {
                rangeBar.visibility = View.VISIBLE
                rangeBar.setRange(
                    scaleMin = scaleMin,
                    scaleMax = scaleMax,
                    low = celsiusRanges[rowIndex].first,
                    high = celsiusRanges[rowIndex].second,
                    current = if (isToday) {
                        weather.current?.temperature?.temperature?.toDouble(TemperatureUnit.CELSIUS)
                    } else {
                        null
                    }
                )
            } else {
                rangeBar.visibility = View.INVISIBLE
            }

            row.contentDescription = listOfNotNull(
                dayName,
                high?.let {
                    activity.getString(R.string.daytime) + activity.getString(R.string.colon_separator) +
                        it.formatMeasure(activity, temperatureUnit, unitWidth = UnitWidth.LONG)
                },
                low?.let {
                    activity.getString(R.string.nighttime) + activity.getString(R.string.colon_separator) +
                        it.formatMeasure(activity, temperatureUnit, unitWidth = UnitWidth.LONG)
                },
                precipitation?.let {
                    activity.getString(R.string.precipitation_probability) +
                        activity.getString(R.string.colon_separator) + it.formatPercent(activity)
                }
            ).joinToString(activity.getString(org.breezyweather.unit.R.string.locale_separator))

            row.setOnClickListener {
                if (activity.isActivityResumed) {
                    IntentHelper.startDailyWeatherActivity(
                        activity,
                        location.formattedId,
                        dailyIndex,
                        DetailScreen.TAG_CONDITIONS
                    )
                }
            }
            rows.addView(row)
        }
    }

    /** Low and high of the day: Breezy sources give daytime and nighttime temperatures. */
    private fun Daily.lowAndHigh() = listOfNotNull(
        day?.temperature?.temperature,
        night?.temperature?.temperature
    ).let { temperatures ->
        temperatures.minByOrNull { it.toDouble(TemperatureUnit.CELSIUS) } to
            temperatures.maxByOrNull { it.toDouble(TemperatureUnit.CELSIUS) }
    }

    private fun Daily.celsiusRange(): Pair<Double?, Double?> = lowAndHigh().let { (low, high) ->
        low?.toDouble(TemperatureUnit.CELSIUS) to high?.toDouble(TemperatureUnit.CELSIUS)
    }

    companion object {
        private const val MAX_DAYS = 10
    }
}
