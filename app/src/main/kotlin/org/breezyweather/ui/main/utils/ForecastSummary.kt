/*
 * This file is part of Frosty Weather, a modified version of Breezy Weather.
 *
 * Frosty Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Frosty Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Frosty Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.ui.main.utils

import android.content.Context
import androidx.annotation.StringRes
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Hourly
import breezyweather.domain.weather.reference.WeatherCode
import org.breezyweather.R
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.domain.location.model.isDaylight
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.speed.SpeedUnit
import java.util.Date
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * One or two plain sentences about the hours ahead, iOS weather style, shown at the top of the
 * home screen:
 *
 * - what changes next, and when ("Rain expected around 7 PM.", "Rain will end around 9 PM."),
 *   or that the current conditions hold ("Cloudy conditions will continue for the rest of the day."),
 * - then the wind, when there is some ("Wind gusts are up to 25 mph.").
 *
 * Built from the hourly forecast of the next [HOURS_AHEAD] hours only, so it stays about "now".
 */
object ForecastSummary {

    private const val HOURS_AHEAD = 12

    /** Below these, wind is not worth a sentence. */
    private const val NOTABLE_GUSTS_KMH = 15.0
    private const val NOTABLE_WIND_KMH = 10.0

    /** Weather codes that read the same in a sentence fall into one group. */
    private enum class Kind(@StringRes val noun: Int, val isPrecipitation: Boolean) {
        CLEAR(R.string.summary_kind_clear, false),
        PARTLY_CLOUDY(R.string.summary_kind_partly_cloudy, false),
        CLOUDY(R.string.summary_kind_cloudy, false),
        FOG(R.string.summary_kind_fog, false),
        HAZE(R.string.summary_kind_haze, false),
        WIND(R.string.summary_kind_wind, false),
        RAIN(R.string.summary_kind_rain, true),
        SNOW(R.string.summary_kind_snow, true),
        SLEET(R.string.summary_kind_sleet, true),
        HAIL(R.string.summary_kind_hail, true),
        STORM(R.string.summary_kind_storm, true),
    }

    private fun kindOf(code: WeatherCode): Kind = when (code) {
        WeatherCode.CLEAR -> Kind.CLEAR
        WeatherCode.PARTLY_CLOUDY -> Kind.PARTLY_CLOUDY
        WeatherCode.CLOUDY -> Kind.CLOUDY
        WeatherCode.FOG -> Kind.FOG
        WeatherCode.HAZE -> Kind.HAZE
        WeatherCode.WIND -> Kind.WIND
        WeatherCode.RAIN -> Kind.RAIN
        WeatherCode.SNOW -> Kind.SNOW
        WeatherCode.SLEET -> Kind.SLEET
        WeatherCode.HAIL -> Kind.HAIL
        WeatherCode.THUNDER, WeatherCode.THUNDERSTORM -> Kind.STORM
    }

    fun build(context: Context, location: Location, now: Date = Date()): String? {
        val weather = location.weather ?: return null
        val hours = weather.nextHourlyForecast.filter {
            it.date.time >= now.time - 30.minutes.inWholeMilliseconds &&
                it.date.time < now.time + HOURS_AHEAD.hours.inWholeMilliseconds
        }
        val currentCode = weather.current?.weatherCode
            ?: hours.firstOrNull { it.weatherCode != null }?.weatherCode
            ?: return null

        return listOfNotNull(
            conditions(context, location, kindOf(currentCode), hours, now),
            wind(context, hours)
        ).joinToString(" ").takeIf { it.isNotEmpty() }
    }

    private fun conditions(context: Context, location: Location, current: Kind, hours: List<Hourly>, now: Date): String {
        val noun = context.getString(current.noun)
        val change = hours.firstOrNull { hourly ->
            hourly.date.after(now) && hourly.weatherCode?.let { kindOf(it) != current } == true
        }
        if (change == null) {
            val res = when {
                current.isPrecipitation && location.isDaylight -> R.string.summary_precipitation_continues_day
                current.isPrecipitation -> R.string.summary_precipitation_continues_night
                location.isDaylight -> R.string.summary_conditions_continue_day
                else -> R.string.summary_conditions_continue_night
            }
            return context.getString(res, noun)
        }

        val next = kindOf(change.weatherCode!!)
        val time = change.date.getFormattedTime(location, context, context.is12Hour)
        return when {
            // Rain stopping matters more than what the sky does after it
            current.isPrecipitation && !next.isPrecipitation ->
                context.getString(R.string.summary_precipitation_ends, noun, time)
            next.isPrecipitation ->
                context.getString(R.string.summary_precipitation_expected, context.getString(next.noun), time)
            else ->
                context.getString(R.string.summary_conditions_expected, context.getString(next.noun), time)
        }
    }

    private fun wind(context: Context, hours: List<Hourly>): String? {
        val gusts = hours.mapNotNull { it.wind?.gusts }.maxByOrNull { it.toDouble(SpeedUnit.KILOMETER_PER_HOUR) }
        if (gusts != null && gusts.toDouble(SpeedUnit.KILOMETER_PER_HOUR) >= NOTABLE_GUSTS_KMH) {
            return context.getString(R.string.summary_gusts, gusts.formatMeasure(context, valueWidth = UnitWidth.NARROW))
        }
        val speed = hours.mapNotNull { it.wind?.speed }.maxByOrNull { it.toDouble(SpeedUnit.KILOMETER_PER_HOUR) }
        if (speed != null && speed.toDouble(SpeedUnit.KILOMETER_PER_HOUR) >= NOTABLE_WIND_KMH) {
            return context.getString(R.string.summary_wind, speed.formatMeasure(context, valueWidth = UnitWidth.NARROW))
        }
        return null
    }
}
