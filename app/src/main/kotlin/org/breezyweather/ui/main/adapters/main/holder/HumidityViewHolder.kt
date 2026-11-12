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
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth

/** Humidity tile: relative humidity, and the dew point underneath. */
class HumidityViewHolder(context: Context) : GlassTileViewHolder(
    context,
    R.drawable.ic_humidity_percentage,
    R.string.humidity
) {

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val current = location.weather!!.current
        val humidity = current?.relativeHumidity?.formatPercent(context, UnitWidth.NARROW)
        val dewPoint = current?.dewPoint?.formatMeasure(
            context,
            SettingsManager.getInstance(context).getTemperatureUnit(context),
            valueWidth = UnitWidth.NARROW,
            unitWidth = UnitWidth.NARROW
        )
        setValue(humidity)
        setLabel(null)
        setAccent(TileColors.WATER)
        // The next 12 hours of humidity
        showBars(
            location.weather!!.nextHourlyForecast.take(12).map { h ->
                h.relativeHumidity?.inPercent?.let { (it / 100).toFloat() }
            },
            TileColors.WATER
        )
        setDescription(
            dewPoint?.let { context.getString(R.string.dew_point) + context.getString(R.string.colon_separator) + it }
        )

        val talkBackBuilder = StringBuilder(context.getString(R.string.humidity))
        humidity?.let {
            talkBackBuilder.append(context.getString(R.string.colon_separator)).append(it)
        }
        dewPoint?.let {
            talkBackBuilder.append(context.getString(org.breezyweather.unit.R.string.locale_separator))
                .append(context.getString(R.string.dew_point))
                .append(context.getString(R.string.colon_separator))
                .append(it)
        }
        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_HUMIDITY
            )
        }
    }
}
