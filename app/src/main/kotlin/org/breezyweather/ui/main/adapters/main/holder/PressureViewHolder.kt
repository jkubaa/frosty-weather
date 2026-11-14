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
import androidx.core.graphics.ColorUtils
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatValue
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth

/** Pressure tile: value, unit, and where it sits between low and high pressure. */
class PressureViewHolder(context: Context) : GlassTileViewHolder(
    context, R.drawable.ic_gauge, R.string.pressure) {

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val pressure = location.weather!!.current?.pressure
        val pressureUnit = SettingsManager.getInstance(context).getPressureUnit(context)
        setValue(pressure?.formatValue(context))
        setLabel(pressure?.let { pressureUnit.getNominativeUnit(context) })
        // Neutral scale: pressure is neither good nor bad, just low or high
        val content = context.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse)
        showScale(
            intArrayOf(ColorUtils.setAlphaComponent(content, 60), ColorUtils.setAlphaComponent(content, 160)),
            pressure?.let { ((it.inHectopascals - LOW_HPA) / (HIGH_HPA - LOW_HPA)).toFloat() }
        )
        // Trend over the next 3 hours
        val later = location.weather!!.nextHourlyForecast.getOrNull(3)?.pressure
        setDescription(
            if (pressure != null && later != null) {
                val change = later.inHectopascals - pressure.inHectopascals
                context.getString(
                    when {
                        change > TREND_THRESHOLD_HPA -> R.string.pressure_rising
                        change < -TREND_THRESHOLD_HPA -> R.string.pressure_falling
                        else -> R.string.pressure_steady
                    }
                )
            } else {
                null
            }
        )

        val talkBackBuilder = StringBuilder(context.getString(R.string.pressure))
        pressure?.let {
            talkBackBuilder.append(context.getString(R.string.colon_separator))
                .append(it.formatMeasure(context, unitWidth = UnitWidth.LONG))
        }
        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_PRESSURE
            )
        }
    }

    companion object {
        // Same range the former gauge used
        private const val LOW_HPA = 963.0
        private const val HIGH_HPA = 1063.0

        // Change over 3 hours below which pressure counts as steady
        private const val TREND_THRESHOLD_HPA = 1.0
    }
}
