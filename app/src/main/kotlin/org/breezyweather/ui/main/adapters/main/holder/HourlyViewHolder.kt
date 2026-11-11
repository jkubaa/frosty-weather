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

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Hourly
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.common.extensions.getHour
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.main.utils.ForecastMetric
import org.breezyweather.ui.main.utils.ForecastMetrics
import org.breezyweather.ui.main.utils.MetricCell
import org.breezyweather.ui.main.widgets.MiniBarsView
import org.breezyweather.ui.theme.resource.ResourceHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth
import kotlin.time.Duration.Companion.days

/**
 * Hourly forecast, iOS weather style: the source's summary, a metric switcher (conditions,
 * precipitation, wind... from the user's hourly trend settings), then a horizontal strip of
 * hour / graphic / value, starting with "Now". Tapping an hour opens the details of its day.
 */
class HourlyViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_hourly_trend_card, parent, false)
) {
    private val subtitle: TextView = itemView.findViewById(R.id.hourly_block_subtitle)
    private val switcher = MetricSwitcher(itemView.findViewById(R.id.hourly_block_switcher))
    private val list: RecyclerView = itemView.findViewById(R.id.hourly_block_list)

    init {
        list.layoutManager = LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
        list.setHasFixedSize(true)
    }

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

        if (weather.current?.hourlyForecast.isNullOrEmpty()) {
            subtitle.visibility = View.GONE
        } else {
            subtitle.visibility = View.VISIBLE
            subtitle.text = weather.current?.hourlyForecast
        }

        val metrics = ForecastMetrics.hourlyMetrics(activity, weather)
        fun show(metric: ForecastMetric) {
            switcher.bind(metrics, metric) {
                setSelectedTab(activity.getString(it.nameId))
                show(it)
            }
            list.adapter = HourlyStripAdapter(activity, location, provider, weather.nextHourlyForecast, metric)
        }
        show(metrics.firstOrNull { activity.getString(it.nameId) == selectedTab } ?: ForecastMetric.CONDITIONS)
    }

    private class HourlyStripAdapter(
        private val activity: BreezyActivity,
        private val location: Location,
        private val provider: ResourceProvider,
        private val hours: List<Hourly>,
        private val metric: ForecastMetric,
    ) : RecyclerView.Adapter<HourlyStripAdapter.ItemViewHolder>() {

        private val temperatureUnit = SettingsManager.getInstance(activity).getTemperatureUnit(activity)
        private val contentColor = activity.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse)

        class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val hour: TextView = itemView.findViewById(R.id.hourly_item_hour)
            val icon: ImageView = itemView.findViewById(R.id.hourly_item_icon)
            val precipitation: TextView = itemView.findViewById(R.id.hourly_item_precipitation)
            val bar: MiniBarsView = itemView.findViewById(R.id.hourly_item_bar)
            val arrow: ImageView = itemView.findViewById(R.id.hourly_item_arrow)
            val dot: View = itemView.findViewById(R.id.hourly_item_dot)
            val value: TextView = itemView.findViewById(R.id.hourly_item_temperature)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ItemViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_glass_hourly, parent, false)
        )

        override fun getItemCount() = hours.size

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val hourly = hours[position]
            val isNow = position == 0

            val hourLabel = if (isNow) activity.getString(R.string.hourly_now) else hourly.date.getHour(location, activity)
            holder.hour.text = hourLabel

            val description = if (metric == ForecastMetric.CONDITIONS) {
                bindConditions(holder, hourly, isNow)
            } else {
                bindMetric(holder, hourly)
            }
            holder.itemView.contentDescription = listOfNotNull(hourLabel, description)
                .joinToString(activity.getString(org.breezyweather.unit.R.string.locale_separator))

            holder.itemView.setOnClickListener {
                if (activity.isActivityResumed) {
                    // Same mapping as the former trend chart: the day this hour belongs to
                    val dailyIndex = location.weather!!.dailyForecast.indexOfFirst {
                        it.date.time > hourly.date.time - 1.days.inWholeMilliseconds
                    }.takeIf { it != -1 }
                    IntentHelper.startDailyWeatherActivity(
                        activity,
                        location.formattedId,
                        dailyIndex,
                        DetailScreen.TAG_CONDITIONS
                    )
                }
            }
        }

        /** Icon, chance of precipitation under it, temperature. Returns the spoken description. */
        private fun bindConditions(holder: ItemViewHolder, hourly: Hourly, isNow: Boolean): String {
            holder.bar.visibility = View.GONE
            holder.arrow.visibility = View.GONE
            holder.dot.visibility = View.GONE
            holder.icon.visibility = View.VISIBLE
            holder.icon.setImageDrawable(
                hourly.weatherCode?.let { ResourceHelper.getWeatherIcon(provider, it, hourly.isDaylight) }
            )

            val precipitation = hourly.precipitationProbability?.total
            val precipitationLabel = GlassForecastFormat.precipitation(activity, precipitation)
            holder.precipitation.visibility = if (precipitationLabel != null) View.VISIBLE else View.GONE
            holder.precipitation.text = precipitationLabel

            // "Now" shows the observed temperature when there is one, like the hero
            val temperature = if (isNow) {
                location.weather?.current?.temperature?.temperature ?: hourly.temperature?.temperature
            } else {
                hourly.temperature?.temperature
            }
            holder.value.text = temperature?.let { GlassForecastFormat.degrees(activity, it, temperatureUnit) }
            holder.value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 19f)

            return listOfNotNull(
                hourly.weatherText,
                temperature?.formatMeasure(activity, temperatureUnit, unitWidth = UnitWidth.LONG),
                precipitation?.let {
                    activity.getString(R.string.precipitation_probability) +
                        activity.getString(R.string.colon_separator) + it.formatPercent(activity)
                }
            ).joinToString(activity.getString(org.breezyweather.unit.R.string.locale_separator))
        }

        /** Graphic for the selected metric, and its value. Returns the spoken description. */
        private fun bindMetric(holder: ItemViewHolder, hourly: Hourly): String {
            val cell = ForecastMetrics.hourlyCell(activity, metric, hourly)
            holder.icon.visibility = View.GONE
            holder.precipitation.visibility = View.GONE
            holder.bar.visibility = if (cell.graphic == MetricCell.Graphic.BAR) View.VISIBLE else View.GONE
            holder.arrow.visibility =
                if (cell.graphic == MetricCell.Graphic.ARROW && cell.degree != null) View.VISIBLE else View.GONE
            holder.dot.visibility =
                if (cell.graphic == MetricCell.Graphic.DOT && cell.text != null) View.VISIBLE else View.GONE

            when (cell.graphic) {
                MetricCell.Graphic.BAR -> holder.bar.setBars(listOf(cell.fraction), cell.color, contentColor)
                MetricCell.Graphic.ARROW -> cell.degree?.let { holder.arrow.rotation = it }
                MetricCell.Graphic.DOT -> holder.dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(cell.color)
                }
                MetricCell.Graphic.NONE -> Unit
            }
            holder.value.text = cell.text ?: "-"
            // Values with units ("1015 hPa", "12 mph") need more room than a temperature
            holder.value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if ((cell.text?.length ?: 0) > 4) 13f else 17f)
            return activity.getString(metric.nameId) + activity.getString(R.string.colon_separator) + (cell.text ?: "-")
        }
    }
}
