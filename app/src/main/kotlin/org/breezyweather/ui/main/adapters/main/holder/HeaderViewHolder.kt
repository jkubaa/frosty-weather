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

import android.animation.Animator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.weather.model.getTemperatureRangeSummary
import org.breezyweather.ui.common.glass.GlassText
import org.breezyweather.unit.temperature.TemperatureUnit
import org.breezyweather.ui.common.widgets.NumberAnimTextView
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.formatting.format
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class HeaderViewHolder(parent: ViewGroup) : AbstractMainViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_header, parent, false)
) {
    private val mLocationCaption: TextView = itemView.findViewById(R.id.container_main_header_location_caption)
    private val mLocationName: TextView = itemView.findViewById(R.id.container_main_header_location_name)
    private val mTemperatureContainer: LinearLayout = itemView.findViewById(R.id.container_main_header_temperature)
    private val mTemperature: NumberAnimTextView = itemView.findViewById(R.id.container_main_header_temperature_value)
    private val mTemperatureUnitView: TextView = itemView.findViewById(R.id.container_main_header_temperature_unit)
    private val mWeatherText: TextView = itemView.findViewById(R.id.container_main_header_weather_condition_description)
    private val mFeelsLike: TextView = itemView.findViewById(R.id.container_main_header_feels_like)
    private val mTemperatureRange: TextView = itemView.findViewById(R.id.container_main_header_temperature_range)
    private var mTemperatureFrom = 0
    private var mTemperatureTo = 0

    @SuppressLint("SetTextI18n")
    override fun onBindView(
        context: Context,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(context, location, provider, listAnimationEnabled, itemAnimationEnabled)

        // Location
        mLocationName.text = location.getPlace(context)
        mLocationCaption.visibility = if (location.isCurrentPosition) View.VISIBLE else View.GONE

        // Frosted-glass look for the two big pieces of text
        GlassText.apply(mLocationName)
        GlassText.apply(mTemperature)
        GlassText.apply(mTemperatureUnitView)

        val temperatureUnit = SettingsManager.getInstance(context).getTemperatureUnit(context)
        location.weather?.current?.let { current ->
            // Apple-style: always show a condition, falling back to the generic label of the weather code
            val conditionText = current.weatherText?.takeIf { it.isNotEmpty() }
                ?: current.weatherCode?.let { WeatherViewController.getWeatherText(it) }
            if (!conditionText.isNullOrEmpty()) {
                mWeatherText.visibility = View.VISIBLE
                mWeatherText.text = conditionText
            } else {
                mWeatherText.visibility = View.GONE
            }
            current.temperature?.temperature?.let {
                mTemperatureContainer.visibility = View.VISIBLE
                mTemperatureContainer.contentDescription = it.formatMeasure(
                    context,
                    temperatureUnit,
                    unitWidth = UnitWidth.LONG
                )
                mTemperatureFrom = mTemperatureTo
                mTemperatureTo = it.toDouble(temperatureUnit).roundToInt()
                mTemperature.isAnimEnabled = itemAnimationEnabled
                mTemperature.duration = (abs(mTemperatureTo - mTemperatureFrom) * 20.0)
                    .coerceAtLeast(1000.0) // at least 1 second
                    .coerceAtMost(2000.0) // no longer than 2 seconds
                    .milliseconds
                // Apple-style: a lone degree sign, the unit is set in the settings anyway
                mTemperatureUnitView.text = "°"
            } ?: run {
                mTemperatureContainer.visibility = View.GONE
            }
            current.temperature?.feelsLikeTemperature?.let { feelsLike ->
                if (current.temperature!!.temperature?.toDouble(temperatureUnit)?.roundToInt() !=
                    feelsLike.toDouble(temperatureUnit).roundToInt()
                ) {
                    mFeelsLike.visibility = View.VISIBLE
                    mFeelsLike.text = context.getString(
                        R.string.temperature_feels_like_with_unit,
                        feelsLike.formatMeasure(
                            context,
                            temperatureUnit,
                            valueWidth = UnitWidth.NARROW,
                            unitWidth = UnitWidth.NARROW
                        )
                    )
                    mFeelsLike.contentDescription = context.getString(R.string.temperature_feels_like) +
                        context.getString(R.string.colon_separator) +
                        feelsLike.formatMeasure(
                            context,
                            temperatureUnit,
                            unitWidth = UnitWidth.LONG
                        )
                } else {
                    mFeelsLike.visibility = View.GONE
                }
            } ?: run {
                mFeelsLike.visibility = View.GONE
            }
        }

        // Today's high and low, iOS style: "H:59°  L:46°"
        val todayTemperatures = listOfNotNull(
            location.weather!!.today?.day?.temperature?.temperature,
            location.weather!!.today?.night?.temperature?.temperature
        )
        if (todayTemperatures.isNotEmpty()) {
            val high = todayTemperatures.maxBy { it.toDouble(TemperatureUnit.CELSIUS) }
            val low = todayTemperatures.minBy { it.toDouble(TemperatureUnit.CELSIUS) }
            mTemperatureRange.visibility = View.VISIBLE
            mTemperatureRange.text = context.getString(
                R.string.hero_high_low,
                GlassForecastFormat.degrees(context, high, temperatureUnit),
                GlassForecastFormat.degrees(context, low, temperatureUnit)
            )
            // Spoken: keep the full sentence from the summary when there is one
            mTemperatureRange.contentDescription =
                location.weather!!.getTemperatureRangeSummary(context, location, temperatureUnit)?.second
        } else {
            mTemperatureRange.visibility = View.GONE
        }
    }

    override fun getEnterAnimator(pendingAnimatorList: List<Animator>): Animator {
        val a: Animator = ObjectAnimator.ofFloat(itemView, "alpha", 0f, 1f)
        a.duration = 300
        a.startDelay = 100
        a.interpolator = FastOutSlowInInterpolator()
        return a
    }

    @SuppressLint("DefaultLocale")
    override fun onEnterScreen() {
        super.onEnterScreen()
        mTemperature.setNumberString(
            mTemperatureFrom.format(decimals = 0, locale = context.currentLocale),
            mTemperatureTo.format(decimals = 0, locale = context.currentLocale)
        )
    }
}
