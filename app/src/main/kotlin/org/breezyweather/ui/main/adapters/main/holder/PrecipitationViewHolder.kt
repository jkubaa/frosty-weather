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

import android.content.Context
import android.view.ViewGroup
import android.widget.TextView
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.areBlocksSquished
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.toCalendarWithTimeZone
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.UnitUtils
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth
import java.util.Calendar
import java.util.Date

/** Precipitation tile: total for the current half of the day, and which half (and kind) it is. */
class PrecipitationViewHolder(context: Context) : GlassTileViewHolder(
    context,
    R.drawable.ic_precipitation,
    R.string.precipitation
) {
    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        setLabel(null)
        setAccent(TileColors.WATER)
        // Chance of precipitation over the next 12 hours
        showBars(
            location.weather?.nextHourlyForecast.orEmpty().take(12).map { h ->
                h.precipitationProbability?.total?.inPercent?.let { (it / 100).toFloat() }
            },
            TileColors.WATER
        )

        val talkBackBuilder = StringBuilder(context.getString(R.string.precipitation))
        location.weather?.let { weather ->
            val cal = Date().toCalendarWithTimeZone(location.timeZone)
            val currentHour = cal[Calendar.HOUR_OF_DAY]

            // Early morning
            val precipitation = if (currentHour < 5) {
                weather.dailyForecast.getOrElse(weather.todayIndex!!.minus(1)) { null }?.night?.precipitation
            } else if (currentHour < 17) {
                weather.today?.day?.precipitation
            } else {
                weather.today?.night?.precipitation
            }

            val isDay = currentHour in 5..16

            var isSnow = false
            val amountText = if ((precipitation?.rain?.value ?: 0) > 0 &&
                (precipitation?.snow?.value ?: 0) > 0
            ) {
                context.getString(
                    if (isDay) R.string.precipitation_total_day else R.string.precipitation_total_night
                )
            } else if ((precipitation?.snow?.value ?: 0) > 0) {
                context.getString(
                    if ((precipitation?.snow ?: 0.0) == (precipitation?.total ?: 0.0)) {
                        isSnow = true
                        if (isDay) R.string.precipitation_snow_total_day else R.string.precipitation_snow_total_night
                    } else {
                        if (isDay) R.string.precipitation_total_day else R.string.precipitation_total_night
                    }
                )
            } else if ((precipitation?.rain?.value ?: 0) > 0) {
                context.getString(
                    if ((precipitation?.rain ?: 0.0) == (precipitation?.total ?: 0.0)) {
                        if (isDay) R.string.precipitation_rain_total_day else R.string.precipitation_rain_total_night
                    } else {
                        if (isDay) R.string.precipitation_total_day else R.string.precipitation_total_night
                    }
                )
            } else {
                context.getString(
                    if (isDay) R.string.precipitation_total_day else R.string.precipitation_total_night
                )
            }
            setDescription(amountText, maxLines = if (itemView.context.areBlocksSquished) 2 else 3)

            precipitation?.total?.let { total ->
                setValue(UnitUtils.formatUnitsHalfSize(total.formatMeasure(context)))

                talkBackBuilder.append(context.getString(R.string.colon_separator))
                talkBackBuilder.append(amountText)
                talkBackBuilder.append(context.getString(R.string.colon_separator))
                talkBackBuilder.append(total.formatMeasure(context, unitWidth = UnitWidth.LONG))
            } ?: run {
                setValue(null)
            }
        }

        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_PRECIPITATION
            )
        }
    }
}
