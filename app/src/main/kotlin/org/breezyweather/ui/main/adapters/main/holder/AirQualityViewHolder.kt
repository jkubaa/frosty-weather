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
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.weather.index.PollutantIndex
import org.breezyweather.domain.weather.model.getColor
import org.breezyweather.domain.weather.model.getIndex
import org.breezyweather.domain.weather.model.getName
import org.breezyweather.domain.weather.model.validAirQuality
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.format

/** Air quality tile: index, its name, and where it sits on the good-to-hazardous color scale. */
class AirQualityViewHolder(context: Context) : GlassTileViewHolder(
    context,
    R.drawable.ic_factory,
    R.string.air_quality
) {

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val airQuality = location.weather!!.validAirQuality
        val index = airQuality?.getIndex()
        setValue(index?.format(decimals = 0, locale = context.currentLocale))
        setLabel(airQuality?.getName(context))
        showScale(
            levelColors(
                R.color.colorLevel_1,
                R.color.colorLevel_2,
                R.color.colorLevel_3,
                R.color.colorLevel_4,
                R.color.colorLevel_5,
                R.color.colorLevel_6
            ),
            index?.let { it.toFloat() / PollutantIndex.indexExcessivePollution }
        )
        airQuality?.let { setAccent(it.getColor(context)) }
        setDescription(null)

        val talkBackBuilder = StringBuilder()
        if (airQuality != null && index != null) {
            talkBackBuilder
                .append(context.getString(R.string.air_quality_index))
                .append(context.getString(R.string.colon_separator))
                .append(index.format(decimals = 0, locale = context.currentLocale))
                .append(context.getString(org.breezyweather.unit.R.string.locale_separator))
                .append(airQuality.getName(context))
        }
        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_AIR_QUALITY
            )
        }
    }
}
