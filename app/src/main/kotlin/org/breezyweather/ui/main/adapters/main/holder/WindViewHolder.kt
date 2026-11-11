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

import android.view.ViewGroup
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.UnitUtils
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.weather.model.getContentDescription
import org.breezyweather.domain.weather.model.getDirection
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth

/** Wind tile: speed, gusts, where it comes from, and a compass. */
class WindViewHolder(parent: ViewGroup) : GlassTileViewHolder(parent, R.drawable.ic_wind, R.string.wind) {

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val wind = location.weather?.current?.wind
        setValue(
            wind?.speed?.let { UnitUtils.formatUnitsHalfSize(it.formatMeasure(context, valueWidth = UnitWidth.NARROW)) }
        )
        val speed = wind?.speed
        setLabel(
            wind?.gusts?.takeIf { speed == null || it > speed }?.let {
                context.getString(R.string.wind_gusts_short) +
                    context.getString(R.string.colon_separator) +
                    it.formatMeasure(context, valueWidth = UnitWidth.NARROW)
            }
        )
        // -1 (or anything out of range): variable direction, no arrow
        val degree = wind?.degree?.takeIf { it in 0.0..360.0 }
        showCompass(degree?.toFloat())
        setAccent(TileColors.WIND)
        setDescription(
            wind?.getDirection(context, short = true)?.let {
                if (degree != null) context.getString(R.string.wind_origin, it) else it
            }
        )

        val talkBackBuilder = StringBuilder(context.getString(R.string.wind))
        wind?.let {
            talkBackBuilder.append(context.getString(R.string.colon_separator)).append(it.getContentDescription(context))
        }
        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_WIND
            )
        }
    }
}
