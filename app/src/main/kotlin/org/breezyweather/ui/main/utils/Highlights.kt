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
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Daily
import breezyweather.domain.weather.model.Hourly
import breezyweather.domain.weather.model.UV
import breezyweather.domain.weather.reference.WeatherCode
import org.breezyweather.R
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.weather.model.getLevel
import org.breezyweather.domain.weather.model.getUVColor
import org.breezyweather.domain.weather.model.getWeek
import org.breezyweather.ui.main.adapters.main.holder.GlassForecastFormat
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.formatting.format
import org.breezyweather.unit.speed.SpeedUnit
import org.breezyweather.unit.temperature.Temperature
import org.breezyweather.unit.temperature.TemperatureUnit
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours

/**
 * One highlight, shown as a round gauge (watch-complication style):
 * [value] (or the [icon] when there is no value) inside a ring filled to [fraction] in [color],
 * with a short [label] and [sublabel] underneath, e.g. "Rain" / "Fri" around "97%",
 * or "Sunset" / "5:01 PM" around a sunset icon.
 *
 * [title] and [detail] are the full plain-language sentence, read out by screen readers.
 */
data class Highlight(
    @DrawableRes val icon: Int,
    val title: String,
    val detail: String? = null,
    val label: String,
    val sublabel: String? = null,
    val value: String? = null,
    /** Ring level, 0..1; null for a plain decorative ring around an icon or value */
    val fraction: Float? = null,
    @ColorInt val color: Int = Color.WHITE,
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

    /** Gust speed, in km/h, at which the gusts ring is full (storm). */
    private const val FULL_GUSTS_KMH = 100.0

    private const val DAYS_AHEAD_FOR_PRECIPITATION = 6

    private val WATER = Color.rgb(79, 175, 255)
    private val WARM = Color.rgb(255, 149, 0)
    private val COOL = Color.rgb(100, 190, 255)
    private val SUN = Color.rgb(255, 204, 64)
    private val WIND = Color.rgb(94, 230, 208)

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

    private fun isSnow(weatherCode: WeatherCode?) = weatherCode == WeatherCode.SNOW || weatherCode == WeatherCode.SLEET

    private fun precipitationKind(context: Context, weatherCode: WeatherCode?): String =
        context.getString(if (isSnow(weatherCode)) R.string.highlight_snow else R.string.highlight_rain)

    private fun time(context: Context, location: Location, date: Date) =
        date.getFormattedTime(location, context, context.is12Hour)

    /** Within 24 hours: when it starts. Otherwise: the next likely day. Otherwise, a dry spell. */
    private fun precipitation(
        context: Context,
        location: Location,
        next24Hours: List<Hourly>,
        upcomingDays: List<Daily>,
    ): Highlight? {
        next24Hours.firstOrNull { it.precipitationChance() >= LIKELY_PRECIPITATION_PERCENT }?.let { hourly ->
            val kind = precipitationKind(context, hourly.weatherCode)
            val chance = hourly.precipitationProbability?.total
            return Highlight(
                icon = R.drawable.ic_umbrella,
                title = context.getString(R.string.highlight_precipitation_around, kind, time(context, location, hourly.date)),
                detail = chance?.let { context.getString(R.string.highlight_chance, it.formatPercent(context)) },
                label = kind,
                sublabel = time(context, location, hourly.date),
                value = chance?.formatPercent(context, UnitWidth.NARROW),
                fraction = chance?.inPercent?.div(100)?.toFloat(),
                color = WATER
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
                detail = chance?.let { context.getString(R.string.highlight_chance, it.formatPercent(context)) },
                label = kind,
                sublabel = if (i == 0) context.getString(R.string.daily_tomorrow) else daily.getWeek(location, context),
                value = chance?.formatPercent(context, UnitWidth.NARROW),
                fraction = chance?.inPercent?.div(100)?.toFloat(),
                color = WATER
            )
        }

        // Only claim it stays dry when the source actually gives chances of precipitation
        val hasProbabilities = next24Hours.any { it.precipitationProbability?.total != null } ||
            days.any { it.day?.precipitationProbability?.total != null }
        return if (hasProbabilities) {
            Highlight(
                icon = R.drawable.ic_sunshine_duration,
                title = context.getString(R.string.highlight_dry),
                label = context.getString(R.string.highlight_label_dry),
                sublabel = context.getString(R.string.highlight_label_coming_days),
                color = SUN
            )
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
                val warmer = changeCelsius > 0
                return Highlight(
                    icon = if (warmer) R.drawable.ic_mode_heat else R.drawable.ic_mode_cool,
                    title = context.getString(
                        if (warmer) R.string.highlight_warmer_tomorrow else R.string.highlight_cooler_tomorrow,
                        change
                    ),
                    detail = context.getString(
                        R.string.highlight_highs,
                        degrees(context, todayHigh, unit),
                        degrees(context, tomorrowHigh, unit)
                    ),
                    label = context.getString(R.string.daily_tomorrow),
                    sublabel = context.getString(if (warmer) R.string.highlight_label_warmer else R.string.highlight_label_cooler),
                    value = (if (warmer) "+" else "−") + change,
                    color = if (warmer) WARM else COOL
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
            ),
            label = context.getString(R.string.highlight_label_warmest),
            sublabel = warmest.getWeek(location, context),
            value = degrees(context, warmestHigh, unit),
            color = WARM
        )
    }

    private fun uvPeak(context: Context, location: Location, next24Hours: List<Hourly>): Highlight? {
        val peak = next24Hours.maxByOrNull { it.uV?.index ?: -1.0 } ?: return null
        val uv = peak.uV?.takeIf { (it.index ?: 0.0) >= NOTABLE_UV_INDEX } ?: return null
        val index = uv.index!!.roundToInt().format(decimals = 0, locale = context.currentLocale)
        return Highlight(
            icon = R.drawable.ic_uv,
            title = context.getString(R.string.highlight_uv_peak, index, time(context, location, peak.date)),
            detail = uv.getLevel(context),
            label = context.getString(R.string.highlight_label_uv),
            sublabel = time(context, location, peak.date),
            value = index,
            fraction = (uv.index!! / UV.UV_INDEX_EXCESSIVE).toFloat(),
            color = uv.getUVColor(context)
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
                time(context, location, peak.date)
            ),
            label = context.getString(R.string.wind_gusts_short),
            sublabel = time(context, location, peak.date),
            value = gusts.formatMeasure(context, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW),
            fraction = (gusts.toDouble(SpeedUnit.KILOMETER_PER_HOUR) / FULL_GUSTS_KMH).toFloat(),
            color = WIND
        )
    }

    /** Whichever of today's sunset or tomorrow's sunrise (or today's, before dawn) comes next. */
    private fun nextSunEvent(context: Context, location: Location, upcomingDays: List<Daily>, now: Date): Highlight? {
        val events = upcomingDays.take(2).flatMap { daily ->
            listOfNotNull(
                daily.sun?.riseDate?.let { it to true },
                daily.sun?.setDate?.let { it to false }
            )
        }
        val (date, isSunrise) = events.filter { it.first.after(now) }.minByOrNull { it.first.time } ?: return null
        return Highlight(
            icon = R.drawable.ic_twilight,
            title = context.getString(
                if (isSunrise) R.string.highlight_sunrise else R.string.highlight_sunset,
                time(context, location, date)
            ),
            label = context.getString(if (isSunrise) R.string.highlight_label_sunrise else R.string.highlight_label_sunset),
            sublabel = time(context, location, date),
            color = SUN
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
