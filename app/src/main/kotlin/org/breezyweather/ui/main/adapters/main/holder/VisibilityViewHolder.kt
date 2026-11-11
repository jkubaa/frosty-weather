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

import android.graphics.Color
import android.view.ViewGroup
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.getVisibilityDescription
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.UnitUtils
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth

/** Visibility tile: distance, and how good that is. */
class VisibilityViewHolder(parent: ViewGroup) : GlassTileViewHolder(parent, R.drawable.ic_eye, R.string.visibility) {

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val visibility = location.weather!!.current?.visibility
        val description = visibility?.getVisibilityDescription(context)
        setValue(visibility?.let { UnitUtils.formatUnitsHalfSize(it.formatMeasure(context)) })
        setLabel(description)
        setAccent(TileColors.CLEAR_AIR)
        // From fog (grey) to crystal clear (white), up to 20 km
        showScale(
            intArrayOf(Color.rgb(120, 128, 140), Color.rgb(196, 214, 232), Color.WHITE),
            visibility?.let { (it.inKilometers / 20.0).toFloat() }
        )
        setDescription(null)

        val talkBackBuilder = StringBuilder(context.getString(R.string.visibility))
        visibility?.let {
            talkBackBuilder.append(context.getString(R.string.colon_separator))
                .append(it.formatMeasure(context, unitWidth = UnitWidth.LONG))
        }
        description?.let {
            talkBackBuilder.append(context.getString(org.breezyweather.unit.R.string.locale_separator)).append(it)
        }
        itemView.contentDescription = talkBackBuilder.toString()
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                context as BreezyActivity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_VISIBILITY
            )
        }
    }
}
