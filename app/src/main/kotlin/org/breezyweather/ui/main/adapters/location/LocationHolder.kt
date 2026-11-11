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

package org.breezyweather.ui.main.adapters.location

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.databinding.ItemLocationCardBinding
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.common.glass.GlassText
import org.breezyweather.ui.main.adapters.main.holder.GlassForecastFormat
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import org.breezyweather.ui.theme.weatherView.materialWeatherView.SkyPalette
import org.breezyweather.unit.temperature.TemperatureUnit
import java.util.Date

/**
 * A location of the list, as an iOS weather style card painted with the location's own sky.
 * Long-press anywhere on the card to reorder it (see [org.breezyweather.ui.main.widgets.LocationItemTouchCallback]),
 * swipe it to delete or edit it.
 */
class LocationHolder(
    private val mBinding: ItemLocationCardBinding,
    private val mClickListener: (String) -> Unit,
) : RecyclerView.ViewHolder(mBinding.root) {

    fun onBindView(context: Context, model: LocationModel) {
        val location = model.location
        val daylight = WeatherViewController.isDaylight(location)

        mBinding.root.clipToOutline = true
        mBinding.item.background = SkyPalette.gradient(WeatherViewController.getWeatherKind(location), daylight)

        // Swipe actions, iOS colors: delete in red, edit in gray
        mBinding.container.apply {
            swipe(0f)
            iconResStart = R.drawable.ic_delete
            iconResEnd = R.drawable.ic_settings
            backgroundColorStart = DELETE_COLOR
            backgroundColorEnd = EDIT_COLOR
            tintColorStart = Color.WHITE
            tintColorEnd = Color.WHITE
        }

        // Name, with the iOS arrow for the current location
        mBinding.title.text = model.title
        val arrow = if (model.currentPosition) {
            AppCompatResources.getDrawable(context, R.drawable.ic_location_arrow)?.mutate()?.apply {
                val size = context.dpToPx(15f).toInt()
                setBounds(0, 0, size, size)
            }
        } else {
            null
        }
        mBinding.title.setCompoundDrawablesRelative(null, null, arrow, null)

        // Local time of the location
        // Local time of the location, after a "Current location" label for the current one
        val time = Date().getFormattedTime(location, context, context.is12Hour)
        mBinding.subtitle.text = if (model.currentPosition) {
            context.getString(R.string.location_current) + " · " + time
        } else {
            time
        }

        // Condition, or what's wrong (not located yet, outdated, active alerts)
        mBinding.condition.text = model.body
        mBinding.condition.visibility = if (model.body.isEmpty()) View.GONE else View.VISIBLE

        val temperatureUnit = SettingsManager.getInstance(context).getTemperatureUnit(context)
        val temperature = location.weather?.current?.temperature?.temperature
        if (temperature != null) {
            mBinding.temperature.visibility = View.VISIBLE
            mBinding.temperature.text = GlassForecastFormat.degrees(context, temperature, temperatureUnit)
            GlassText.apply(mBinding.temperature)
        } else {
            mBinding.temperature.visibility = View.INVISIBLE
        }

        val highLow = highLow(context, location, temperatureUnit)
        mBinding.highLow.visibility = if (highLow == null) View.GONE else View.VISIBLE
        mBinding.highLow.text = highLow

        mBinding.item.setOnClickListener { mClickListener(location.formattedId) }

        itemView.contentDescription = listOfNotNull(
            if (model.currentPosition) context.getString(R.string.location_current) else null,
            model.title,
            mBinding.temperature.text?.takeIf { temperature != null },
            model.body.takeIf { it.isNotEmpty() },
            highLow,
            context.getString(R.string.location_swipe_to_delete)
        ).joinToString(context.getString(org.breezyweather.unit.R.string.locale_separator))
    }

    /** Today's "H:24°  L:13°", like the home screen hero. */
    private fun highLow(context: Context, location: Location, unit: TemperatureUnit): String? {
        val today = location.weather?.today ?: return null
        val temperatures = listOfNotNull(
            today.day?.temperature?.temperature,
            today.night?.temperature?.temperature
        )
        if (temperatures.isEmpty()) return null
        return context.getString(
            R.string.hero_high_low,
            GlassForecastFormat.degrees(context, temperatures.maxBy { it.toDouble(TemperatureUnit.CELSIUS) }, unit),
            GlassForecastFormat.degrees(context, temperatures.minBy { it.toDouble(TemperatureUnit.CELSIUS) }, unit)
        )
    }

    companion object {
        private val DELETE_COLOR = Color.rgb(0xFF, 0x45, 0x3A)
        private val EDIT_COLOR = Color.rgb(0x63, 0x63, 0x66)
    }
}
