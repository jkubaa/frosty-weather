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
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.theme.resource.ResourceHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth
import kotlin.time.Duration.Companion.days

/**
 * Hourly forecast, iOS weather style: the source's summary, then a horizontal strip of
 * hour / condition icon (and chance of precipitation) / temperature, starting with "Now".
 * Tapping an hour opens the details of its day.
 */
class HourlyViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_hourly_trend_card, parent, false)
) {
    private val subtitle: TextView = itemView.findViewById(R.id.hourly_block_subtitle)
    private val list: RecyclerView = itemView.findViewById(R.id.hourly_block_list)

    init {
        list.layoutManager = LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
        list.setHasFixedSize(true)
    }

    // selectedTab: the old chart had metric tabs; the strip only shows conditions and temperatures
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

        list.adapter = HourlyStripAdapter(activity, location, provider, weather.nextHourlyForecast)
    }

    private class HourlyStripAdapter(
        private val activity: BreezyActivity,
        private val location: Location,
        private val provider: ResourceProvider,
        private val hours: List<Hourly>,
    ) : RecyclerView.Adapter<HourlyStripAdapter.ItemViewHolder>() {

        private val temperatureUnit = SettingsManager.getInstance(activity).getTemperatureUnit(activity)

        class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val hour: TextView = itemView.findViewById(R.id.hourly_item_hour)
            val icon: ImageView = itemView.findViewById(R.id.hourly_item_icon)
            val precipitation: TextView = itemView.findViewById(R.id.hourly_item_precipitation)
            val temperature: TextView = itemView.findViewById(R.id.hourly_item_temperature)
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
            holder.temperature.text = temperature?.let { GlassForecastFormat.degrees(activity, it, temperatureUnit) }

            holder.itemView.contentDescription = listOfNotNull(
                hourLabel,
                hourly.weatherText,
                temperature?.formatMeasure(activity, temperatureUnit, unitWidth = UnitWidth.LONG),
                precipitation?.let {
                    activity.getString(R.string.precipitation_probability) +
                        activity.getString(R.string.colon_separator) + it.formatPercent(activity)
                }
            ).joinToString(activity.getString(org.breezyweather.unit.R.string.locale_separator))

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
    }
}
