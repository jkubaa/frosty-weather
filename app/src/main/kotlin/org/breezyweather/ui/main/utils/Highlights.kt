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
import androidx.annotation.DrawableRes
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Daily
import breezyweather.domain.weather.model.Hourly
import breezyweather.domain.weather.reference.WeatherCode
import org.breezyweather.R
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.weather.model.getLevel
import org.breezyweather.domain.weather.model.getWeek
import org.breezyweather.ui.main.adapters.main.holder.GlassForecastFormat
import org.breezyweather.unit.formatting.format
import org.breezyweather.unit.speed.SpeedUnit
import org.breezyweather.unit.temperature.Temperature
import org.breezyweather.unit.temperature.TemperatureUnit
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours

/**
 * One line of the Highlights card: a plain-language sentence and an optional detail line.
 */
data class Highlight(
    @DrawableRes val icon: Int,
    val title: String,
    val detail: String? = null,
)

/**
 * Picks what is worth knowing about the coming hours and days, most useful first:
 * precipitation, temperature change, UV peak, strong gusts, then the next sunrise or sunset as filler.
 * Each rule only speaks up when the data supports it, so a calm day gives a short card.
 */
object Highlights {

    const val MAX_HIGHLIGHTS = 4

    /** A chance of precipitation from which it is called "likely". */
    private const val LIKELY_PRECIPITATION_PERCENT = 50.0

    /** Day-to-day change of the high, in °C, from which it is worth mentioning. */
    private const val NOTABLE_TEMPERATURE_CHANGE_CELSIUS = 3.0

    /** UV index from which sun protection is recommended. */
    private const val NOTABLE_UV_INDEX = 3.0

    /** Gust speed, in km/h, from which wind is worth a warning ("strong breeze" and up). */
    private const val NOTABLE_GUSTS_KMH = 50.0

    private const val DAYS_AHEAD_FOR_PRECIPITATION = 6

    fun build(context: Context, location: Location, now: Date = Date()): List<Highlight> {
        val weather = location.weather ?: return emptyList()
        val next24Hours = weather.nextHourlyForecast.filter {
            it.date.time < now.time + 24.hours.inWholeMilliseconds
        }
        val todayIndex = weather.todayIndex
        val upcomingDays = todayIndex?.let { weather.dailyForecast.drop(it) } ?: emptyList()

        val highlights = listOfNotNull(
            precipitation(context, location, next24Hours, upcomingDays),
            temperature(context, location, upcomingDays),
            uvPeak(context, location, next24Hours),
            gusts(context, location, next24Hours)
        )
        val filler = if (highlights.size < MAX_HIGHLIGHTS) nextSunEvent(context, location, upcomingDays, now) else null
        return (highlights + listOfNotNull(filler)).take(MAX_HIGHLIGHTS)
    }

    private fun precipitationKind(context: Context, weatherCode: WeatherCode?): String = context.getString(
        if (weatherCode == WeatherCode.SNOW || weatherCode == WeatherCode.SLEET) {
            R.string.highlight_snow
        } else {
            R.string.highlight_rain
        }
    )

    /** Within 24 hours: when it starts. Otherwise: the next likely day. Otherwise, a dry spell. */
    private fun precipitation(
        context: Context,
        location: Location,
        next24Hours: List<Hourly>,
        upcomingDays: List<Daily>,
    ): Highlight? {
        next24Hours.firstOrNull { it.precipitationChance() >= LIKELY_PRECIPITATION_PERCENT }?.let { hourly ->
            return Highlight(
                icon = R.drawable.ic_umbrella,
                title = context.getString(
                    R.string.highlight_precipitation_around,
                    precipitationKind(context, hourly.weatherCode),
                    hourly.date.getFormattedTime(location, context, context.is12Hour)
                ),
                detail = hourly.precipitationProbability?.total?.let {
                    context.getString(R.string.highlight_chance, it.formatPercent(context))
                }
            )
        }

        val days = upcomingDays.drop(1).take(DAYS_AHEAD_FOR_PRECIPITATION)
        days.withIndex().firstOrNull { it.value.precipitationChance() >= LIKELY_PRECIPITATION_PERCENT }?.let { (i, daily) ->
            val kind = precipitationKind(context, daily.day?.weatherCode ?: daily.night?.weatherCode)
            val chance = listOfNotNull(
                daily.day?.precipitationProbability?.total,
                daily.night?.precipitationProbability?.total
            ).maxByOrNull { it.inPercent }
            return Highlight(
                icon = R.drawable.ic_umbrella,
                title = if (i == 0) {
                    context.getString(R.string.highlight_precipitation_tomorrow, kind)
                } else {
                    context.getString(R.string.highlight_precipitation_on, kind, daily.getWeek(location, context, full = true))
                },
                detail = chance?.let { context.getString(R.string.highlight_chance, it.formatPercent(context)) }
            )
        }

        // Only claim it stays dry when the source actually gives chances of precipitation
        val hasProbabilities = next24Hours.any { it.precipitationProbability?.total != null } ||
            days.any { it.day?.precipitationProbability?.total != null }
        return if (hasProbabilities) {
            Highlight(icon = R.drawable.ic_sunshine_duration, title = context.getString(R.string.highlight_dry))
        } else {
            null
        }
    }

    /** Tomorrow noticeably warmer or cooler than today; otherwise a notably warm day ahead. */
    private fun temperature(context: Context, location: Location, upcomingDays: List<Daily>): Highlight? {
        val unit = SettingsManager.getInstance(context).getTemperatureUnit(context)
        val todayHigh = upcomingDays.getOrNull(0)?.high() ?: return null
        val tomorrowHigh = upcomingDays.getOrNull(1)?.high()

        if (tomorrowHigh != null) {
            val changeCelsius = tomorrowHigh.celsius() - todayHigh.celsius()
            if (abs(changeCelsius) >= NOTABLE_TEMPERATURE_CHANGE_CELSIUS) {
                val change = abs(tomorrowHigh.toDouble(unit) - todayHigh.toDouble(unit)).roundToInt()
                    .format(decimals = 0, locale = context.currentLocale) + "°"
                return Highlight(
                    icon = if (changeCelsius > 0) R.drawable.ic_mode_heat else R.drawable.ic_mode_cool,
                    title = context.getString(
                        if (changeCelsius > 0) R.string.highlight_warmer_tomorrow else R.string.highlight_cooler_tomorrow,
                        change
                    ),
                    detail = context.getString(
                        R.string.highlight_highs,
                        degrees(context, todayHigh, unit),
                        degrees(context, tomorrowHigh, unit)
                    )
                )
            }
        }

        val warmest = upcomingDays.drop(1).maxByOrNull { it.high()?.celsius() ?: Double.NEGATIVE_INFINITY }
        val warmestHigh = warmest?.high() ?: return null
        if (warmestHigh.celsius() - todayHigh.celsius() < NOTABLE_TEMPERATURE_CHANGE_CELSIUS) return null
        return Highlight(
            icon = R.drawable.ic_device_thermostat,
            title = context.getString(
                R.string.highlight_warmest_day,
                warmest.getWeek(location, context, full = true),
                degrees(context, warmestHigh, unit)
            )
        )
    }

    private fun uvPeak(context: Context, location: Location, next24Hours: List<Hourly>): Highlight? {
        val peak = next24Hours.maxByOrNull { it.uV?.index ?: -1.0 } ?: return null
        val uv = peak.uV?.takeIf { (it.index ?: 0.0) >= NOTABLE_UV_INDEX } ?: return null
        return Highlight(
            icon = R.drawable.ic_uv,
            title = context.getString(
                R.string.highlight_uv_peak,
                uv.index!!.roundToInt().format(decimals = 0, locale = context.currentLocale),
                peak.date.getFormattedTime(location, context, context.is12Hour)
            ),
            detail = uv.getLevel(context)
        )
    }

    private fun gusts(context: Context, location: Location, next24Hours: List<Hourly>): Highlight? {
        val peak = next24Hours.maxByOrNull {
            it.wind?.gusts?.toDouble(SpeedUnit.KILOMETER_PER_HOUR) ?: -1.0
        } ?: return null
        val gusts = peak.wind?.gusts
            ?.takeIf { it.toDouble(SpeedUnit.KILOMETER_PER_HOUR) >= NOTABLE_GUSTS_KMH }
            ?: return null
        return Highlight(
            icon = R.drawable.ic_wind,
            title = context.getString(
                R.string.highlight_gusts,
                gusts.formatMeasure(context),
                peak.date.getFormattedTime(location, context, context.is12Hour)
            )
        )
    }

    /** Whichever of today's sunset or tomorrow's sunrise (or today's, before dawn) comes next. */
    private fun nextSunEvent(context: Context, location: Location, upcomingDays: List<Daily>, now: Date): Highlight? {
        val events = upcomingDays.take(2).flatMap { daily ->
            listOfNotNull(
                daily.sun?.riseDate?.let { it to R.string.highlight_sunrise },
                daily.sun?.setDate?.let { it to R.string.highlight_sunset }
            )
        }
        val (date, stringId) = events.filter { it.first.after(now) }.minByOrNull { it.first.time } ?: return null
        return Highlight(
            icon = R.drawable.ic_twilight,
            title = context.getString(stringId, date.getFormattedTime(location, context, context.is12Hour))
        )
    }

    private fun Hourly.precipitationChance(): Double = precipitationProbability?.total?.inPercent ?: 0.0

    private fun Daily.precipitationChance(): Double = maxOf(
        day?.precipitationProbability?.total?.inPercent ?: 0.0,
        night?.precipitationProbability?.total?.inPercent ?: 0.0
    )

    /** Daytime and nighttime temperatures are what sources give; the high is the warmer of the two. */
    private fun Daily.high(): Temperature? = listOfNotNull(day?.temperature?.temperature, night?.temperature?.temperature)
        .maxByOrNull { it.celsius() }

    private fun Temperature.celsius() = toDouble(TemperatureUnit.CELSIUS)

    private fun degrees(context: Context, temperature: Temperature, unit: TemperatureUnit): String =
        GlassForecastFormat.degrees(context, temperature, unit)
}
