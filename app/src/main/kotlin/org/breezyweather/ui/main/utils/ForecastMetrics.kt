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
import androidx.annotation.StringRes
import breezyweather.domain.weather.model.Daily
import breezyweather.domain.weather.model.Hourly
import breezyweather.domain.weather.model.Weather
import breezyweather.domain.weather.model.Wind
import org.breezyweather.R
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.common.extensions.formatTime
import org.breezyweather.common.options.appearance.DailyTrendDisplay
import org.breezyweather.common.options.appearance.HourlyTrendDisplay
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.weather.index.PollutantIndex
import org.breezyweather.domain.weather.model.getColor
import org.breezyweather.domain.weather.model.getIndex
import org.breezyweather.domain.weather.model.getUVColor
import org.breezyweather.ui.main.adapters.main.holder.GlassForecastFormat
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.formatting.format
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.breezyweather.unit.speed.SpeedUnit
import org.breezyweather.unit.temperature.Temperature
import kotlin.math.roundToInt

/**
 * What the hourly and daily forecast cards can show, picked with the icon switcher in their header.
 * The available metrics and their order come from the user's trend settings
 * ([DailyTrendDisplay] / [HourlyTrendDisplay]); conditions (temperatures) is always first.
 */
enum class ForecastMetric(@DrawableRes val icon: Int, @StringRes val nameId: Int) {
    CONDITIONS(R.drawable.ic_device_thermostat, R.string.conditions),
    PRECIPITATION(R.drawable.ic_precipitation, R.string.precipitation),
    WIND(R.drawable.ic_wind, R.string.wind),
    AIR_QUALITY(R.drawable.ic_factory, R.string.air_quality),
    UV_INDEX(R.drawable.ic_uv, R.string.uv_index),
    FEELS_LIKE(R.drawable.ic_mode_heat, R.string.temperature_feels_like),
    HUMIDITY(R.drawable.ic_humidity_percentage, R.string.humidity),
    PRESSURE(R.drawable.ic_gauge, R.string.pressure),
    CLOUD_COVER(R.drawable.ic_cloud, R.string.cloud_cover),
    VISIBILITY(R.drawable.ic_eye, R.string.visibility),
    SUNSHINE(R.drawable.ic_sunshine_duration, R.string.sunshine),
}

/**
 * How one hour or one day looks for a metric (other than conditions):
 * the value [text], and a [graphic] to draw above / beside it.
 */
data class MetricCell(
    val text: String?,
    val graphic: Graphic,
    /** BAR: fill, 0..1 */
    val fraction: Float? = null,
    @ColorInt val color: Int = Color.WHITE,
    /** ARROW: wind degree (where it comes from) */
    val degree: Float? = null,
) {
    enum class Graphic { BAR, ARROW, DOT, NONE }
}

object ForecastMetrics {

    private val WATER = Color.rgb(79, 175, 255)
    private val CLOUD = Color.rgb(176, 190, 206)
    private val SUN = Color.rgb(255, 204, 64)

    /** Strongest wind the bars scale to (km/h). */
    private const val WIND_SCALE_KMH = 60.0

    fun hourlyMetrics(context: Context, weather: Weather): List<ForecastMetric> {
        val hours = weather.nextHourlyForecast
        return listOf(ForecastMetric.CONDITIONS) +
            SettingsManager.getInstance(context).hourlyTrendDisplayList.mapNotNull {
                when (it) {
                    HourlyTrendDisplay.TAG_TEMPERATURE -> null // conditions, always first
                    HourlyTrendDisplay.TAG_AIR_QUALITY -> ForecastMetric.AIR_QUALITY
                        .takeIf { hours.any { h -> h.airQuality?.getIndex() != null } }
                    HourlyTrendDisplay.TAG_WIND -> ForecastMetric.WIND
                        .takeIf { hours.any { h -> h.wind?.speed != null } }
                    HourlyTrendDisplay.TAG_UV_INDEX -> ForecastMetric.UV_INDEX
                        .takeIf { hours.any { h -> h.uV?.index != null } }
                    HourlyTrendDisplay.TAG_PRECIPITATION -> ForecastMetric.PRECIPITATION
                        .takeIf { hours.any { h -> h.precipitationProbability?.total != null } }
                    HourlyTrendDisplay.TAG_FEELS_LIKE -> ForecastMetric.FEELS_LIKE
                        .takeIf { hours.any { h -> h.temperature?.feelsLikeTemperature != null } }
                    HourlyTrendDisplay.TAG_HUMIDITY -> ForecastMetric.HUMIDITY
                        .takeIf { hours.any { h -> h.relativeHumidity != null } }
                    HourlyTrendDisplay.TAG_PRESSURE -> ForecastMetric.PRESSURE
                        .takeIf { hours.any { h -> h.pressure != null } }
                    HourlyTrendDisplay.TAG_CLOUD_COVER -> ForecastMetric.CLOUD_COVER
                        .takeIf { hours.any { h -> h.cloudCover != null } }
                    HourlyTrendDisplay.TAG_VISIBILITY -> ForecastMetric.VISIBILITY
                        .takeIf { hours.any { h -> h.visibility != null } }
                }
            }.distinct()
    }

    fun dailyMetrics(context: Context, days: List<Daily>): List<ForecastMetric> =
        listOf(ForecastMetric.CONDITIONS) +
            SettingsManager.getInstance(context).dailyTrendDisplayList.mapNotNull {
                when (it) {
                    DailyTrendDisplay.TAG_TEMPERATURE -> null // conditions, always first
                    DailyTrendDisplay.TAG_AIR_QUALITY -> ForecastMetric.AIR_QUALITY
                        .takeIf { days.any { d -> d.airQuality?.getIndex() != null } }
                    DailyTrendDisplay.TAG_WIND -> ForecastMetric.WIND
                        .takeIf { days.any { d -> d.strongestWind()?.speed != null } }
                    DailyTrendDisplay.TAG_UV_INDEX -> ForecastMetric.UV_INDEX
                        .takeIf { days.any { d -> d.uV?.index != null } }
                    DailyTrendDisplay.TAG_PRECIPITATION -> ForecastMetric.PRECIPITATION
                        .takeIf { days.any { d -> d.precipitationChance() != null } }
                    DailyTrendDisplay.TAG_SUNSHINE -> ForecastMetric.SUNSHINE
                        .takeIf { days.any { d -> d.sunshineDuration != null } }
                    DailyTrendDisplay.TAG_FEELS_LIKE -> ForecastMetric.FEELS_LIKE
                        .takeIf { days.any { d -> d.feelsLikeHigh() != null } }
                }
            }.distinct()

    fun hourlyCell(context: Context, metric: ForecastMetric, hourly: Hourly): MetricCell {
        val unit = SettingsManager.getInstance(context).getTemperatureUnit(context)
        return when (metric) {
            ForecastMetric.CONDITIONS -> MetricCell(
                hourly.temperature?.temperature?.let { GlassForecastFormat.degrees(context, it, unit) },
                MetricCell.Graphic.NONE
            )
            ForecastMetric.PRECIPITATION -> hourly.precipitationProbability?.total.let {
                MetricCell(it?.formatPercent(context, UnitWidth.NARROW), MetricCell.Graphic.BAR, it?.inPercent?.div(100)?.toFloat(), WATER)
            }
            ForecastMetric.HUMIDITY -> hourly.relativeHumidity.let {
                MetricCell(it?.formatPercent(context, UnitWidth.NARROW), MetricCell.Graphic.BAR, it?.inPercent?.div(100)?.toFloat(), WATER)
            }
            ForecastMetric.CLOUD_COVER -> hourly.cloudCover.let {
                MetricCell(it?.formatPercent(context, UnitWidth.NARROW), MetricCell.Graphic.BAR, it?.inPercent?.div(100)?.toFloat(), CLOUD)
            }
            ForecastMetric.WIND -> windCell(context, hourly.wind)
            ForecastMetric.UV_INDEX -> hourly.uV.let {
                MetricCell(
                    it?.index?.roundToInt()?.format(decimals = 0, locale = context.currentLocale),
                    MetricCell.Graphic.DOT,
                    color = it?.getUVColor(context) ?: Color.TRANSPARENT
                )
            }
            ForecastMetric.AIR_QUALITY -> hourly.airQuality.let {
                MetricCell(
                    it?.getIndex()?.format(decimals = 0, locale = context.currentLocale),
                    MetricCell.Graphic.DOT,
                    color = it?.getColor(context) ?: Color.TRANSPARENT
                )
            }
            ForecastMetric.FEELS_LIKE -> MetricCell(
                hourly.temperature?.feelsLikeTemperature?.let { GlassForecastFormat.degrees(context, it, unit) },
                MetricCell.Graphic.NONE
            )
            ForecastMetric.PRESSURE -> MetricCell(
                hourly.pressure?.formatMeasure(context, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW),
                MetricCell.Graphic.NONE
            )
            ForecastMetric.VISIBILITY -> MetricCell(
                hourly.visibility?.formatMeasure(context, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW),
                MetricCell.Graphic.NONE
            )
            ForecastMetric.SUNSHINE -> MetricCell(null, MetricCell.Graphic.NONE)
        }
    }

    fun dailyCell(context: Context, metric: ForecastMetric, daily: Daily): MetricCell {
        val unit = SettingsManager.getInstance(context).getTemperatureUnit(context)
        return when (metric) {
            ForecastMetric.PRECIPITATION -> {
                val chance = daily.precipitationChance()
                val total = listOfNotNull(daily.day?.precipitation?.total, daily.night?.precipitation?.total)
                    .takeIf { it.isNotEmpty() }
                    ?.sumOf { it.inMillimeters }
                    ?.millimeters
                MetricCell(
                    listOfNotNull(
                        // Traces would round to "0 in": only show amounts of at least 1 mm
                        total?.takeIf { it.inMillimeters >= 1.0 }?.formatMeasure(context, valueWidth = UnitWidth.NARROW),
                        chance?.formatPercent(context, UnitWidth.NARROW)
                    ).joinToString("  ").takeIf { it.isNotEmpty() },
                    MetricCell.Graphic.BAR,
                    chance?.inPercent?.div(100)?.toFloat(),
                    WATER
                )
            }
            ForecastMetric.WIND -> windCell(context, daily.strongestWind())
            ForecastMetric.UV_INDEX -> daily.uV.let {
                MetricCell(
                    it?.index?.roundToInt()?.format(decimals = 0, locale = context.currentLocale),
                    MetricCell.Graphic.BAR,
                    it?.index?.let { i -> (i / 11.0).toFloat() },
                    it?.getUVColor(context) ?: Color.TRANSPARENT
                )
            }
            ForecastMetric.AIR_QUALITY -> daily.airQuality.let {
                MetricCell(
                    it?.getIndex()?.format(decimals = 0, locale = context.currentLocale),
                    MetricCell.Graphic.BAR,
                    it?.getIndex()?.let { i -> i.toFloat() / PollutantIndex.indexExcessivePollution },
                    it?.getColor(context) ?: Color.TRANSPARENT
                )
            }
            ForecastMetric.SUNSHINE -> MetricCell(
                daily.sunshineDuration?.formatTime(context),
                MetricCell.Graphic.BAR,
                daily.sunshineDuration?.let { (it.inWholeMinutes / (12 * 60f)) },
                SUN
            )
            ForecastMetric.FEELS_LIKE -> MetricCell(
                listOfNotNull(daily.feelsLikeHigh(), daily.feelsLikeLow())
                    .map { GlassForecastFormat.degrees(context, it, unit) }
                    .distinct()
                    .joinToString(" / ")
                    .takeIf { it.isNotEmpty() },
                MetricCell.Graphic.NONE
            )
            else -> MetricCell(null, MetricCell.Graphic.NONE)
        }
    }

    private fun windCell(context: Context, wind: Wind?): MetricCell = MetricCell(
        wind?.speed?.formatMeasure(context, valueWidth = UnitWidth.NARROW),
        MetricCell.Graphic.ARROW,
        fraction = wind?.speed?.let { (it.toDouble(SpeedUnit.KILOMETER_PER_HOUR) / WIND_SCALE_KMH).toFloat() },
        color = Color.rgb(94, 230, 208),
        degree = wind?.degree?.takeIf { it in 0.0..360.0 }?.toFloat()
    )

    private fun Daily.strongestWind(): Wind? = listOfNotNull(day?.wind, night?.wind)
        .maxByOrNull { it.speed?.toDouble(SpeedUnit.KILOMETER_PER_HOUR) ?: -1.0 }

    private fun Daily.precipitationChance() = listOfNotNull(
        day?.precipitationProbability?.total,
        night?.precipitationProbability?.total
    ).maxByOrNull { it.inPercent }

    private fun Daily.feelsLikeHigh(): Temperature? = listOfNotNull(
        day?.temperature?.feelsLikeTemperature,
        night?.temperature?.feelsLikeTemperature
    ).maxByOrNull { it.value }

    private fun Daily.feelsLikeLow(): Temperature? = listOfNotNull(
        day?.temperature?.feelsLikeTemperature,
        night?.temperature?.feelsLikeTemperature
    ).minByOrNull { it.value }
}
