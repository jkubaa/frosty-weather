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
import android.graphics.Color
import android.view.ViewGroup
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.UV
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import java.util.Date
import kotlin.time.Duration.Companion.hours
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.weather.model.getContentDescription
import org.breezyweather.domain.weather.model.getLevel
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.format
import kotlin.math.roundToInt

/** UV index tile: index, level, and where it sits on the low (green) to extreme (purple) scale. */
class UvViewHolder(context: Context) : GlassTileViewHolder(
    context, R.drawable.ic_uv, R.string.uv_index_short) {

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val uv = location.weather!!.current?.uV
        setValue(uv?.index?.roundToInt()?.format(decimals = 0, locale = context.currentLocale))
        setLabel(uv?.getLevel(context))
        showScale(
            intArrayOf(
                Color.rgb(76, 217, 100), // low
                Color.rgb(255, 204, 0), // moderate
                Color.rgb(255, 149, 0), // high
                Color.rgb(255, 59, 48), // very high
                Color.rgb(175, 82, 222) // extreme
            ),
            uv?.index?.let { (it / UV.UV_INDEX_EXCESSIVE).toFloat() }
        )

        // When the UV index will be at its highest in the next 24 hours, if higher than now
        val now = Date()
        val peak = location.weather!!.nextHourlyForecast
            .filter { it.date.time < now.time + 24.hours.inWholeMilliseconds }
            .maxByOrNull { it.uV?.index ?: -1.0 }
        val peakIndex = peak?.uV?.index?.roundToInt()
        setDescription(
            if (peak != null && peakIndex != null && peakIndex > (uv?.index?.roundToInt() ?: 0)) {
                context.getString(
                    R.string.tile_uv_peak,
                    peakIndex.format(decimals = 0, locale = context.currentLocale),
                    peak.date.getFormattedTime(location, context, context.is12Hour)
                )
            } else {
                null
            }
        )

        val talkBackBuilder = StringBuilder(context.getString(R.string.uv_index))
        uv?.getContentDescription(context)?.let {
            talkBackBuilder.append(context.getString(R.string.colon_separator))
            talkBackBuilder.append(it)
        }
        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_UV_INDEX
            )
        }
    }
}
