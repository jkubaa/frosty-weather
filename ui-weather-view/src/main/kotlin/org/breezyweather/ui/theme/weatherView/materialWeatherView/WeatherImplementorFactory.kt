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

package org.breezyweather.ui.theme.weatherView.materialWeatherView

import android.content.Context
import android.graphics.Color
import androidx.annotation.DrawableRes
import androidx.annotation.Size
import org.breezyweather.ui.theme.weatherView.R
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.breezyweather.ui.theme.weatherView.WeatherView.WeatherKindRule
import org.breezyweather.ui.theme.weatherView.materialWeatherView.MaterialWeatherView.WeatherAnimationImplementor
import org.breezyweather.ui.theme.weatherView.materialWeatherView.implementor.LayeredImplementor
import org.breezyweather.ui.theme.weatherView.materialWeatherView.implementor.HailImplementor
import org.breezyweather.ui.theme.weatherView.materialWeatherView.implementor.MeteorShowerImplementor
import org.breezyweather.ui.theme.weatherView.materialWeatherView.implementor.RainImplementor
import org.breezyweather.ui.theme.weatherView.materialWeatherView.implementor.SnowImplementor
import org.breezyweather.ui.theme.weatherView.materialWeatherView.implementor.SoftCloudImplementor
import org.breezyweather.ui.theme.weatherView.materialWeatherView.implementor.SunGlowImplementor

object WeatherImplementorFactory {

    /**
     * iOS-weather-like animated layers on top of [SkyPalette]'s gradients: soft drifting clouds,
     * a sun glow, stars at night, and the existing rain / snow / hail particles in front.
     */
    fun getWeatherImplementor(
        context: Context,
        @WeatherKindRule weatherKind: Int,
        daytime: Boolean,
        @Size(2) sizes: IntArray,
        animate: Boolean,
    ): WeatherAnimationImplementor? {
        fun clouds(color: Int, coreAlpha: Float, count: Int, speed: Float = 1f, lightning: Boolean = false) =
            SoftCloudImplementor(
                sizes,
                animate,
                SoftCloudImplementor.Style(color, coreAlpha, count, speed = speed, lightning = lightning)
            )
        // Day clouds are light; night clouds are a lighter shade of the night sky, barely visible
        val light = Color.rgb(236, 241, 247)
        val grey = if (daytime) Color.rgb(196, 205, 216) else Color.rgb(86, 96, 112)
        val dark = if (daytime) Color.rgb(120, 131, 146) else Color.rgb(58, 65, 78)

        return when (weatherKind) {
            WeatherView.WEATHER_KIND_CLEAR -> if (daytime) {
                SunGlowImplementor(sizes, animate)
            } else {
                MeteorShowerImplementor(sizes, animate)
            }

            WeatherView.WEATHER_KIND_CLOUD -> if (daytime) {
                LayeredImplementor(listOf(SunGlowImplementor(sizes, animate), clouds(light, 0.55f, 3)))
            } else {
                LayeredImplementor(listOf(MeteorShowerImplementor(sizes, animate), clouds(grey, 0.35f, 3)))
            }

            WeatherView.WEATHER_KIND_CLOUDY -> clouds(grey, if (daytime) 0.55f else 0.45f, 6)

            WeatherView.WEATHER_KIND_FOG, WeatherView.WEATHER_KIND_HAZE -> SoftCloudImplementor(
                sizes,
                animate,
                SoftCloudImplementor.Style(
                    color = if (weatherKind == WeatherView.WEATHER_KIND_HAZE) {
                        if (daytime) Color.rgb(240, 226, 200) else Color.rgb(110, 98, 80)
                    } else {
                        if (daytime) Color.rgb(226, 230, 235) else Color.rgb(96, 104, 114)
                    },
                    coreAlpha = 0.35f,
                    clouds = 5,
                    fog = true,
                    speed = 0.6f
                )
            )

            WeatherView.WEATHER_KIND_RAINY -> LayeredImplementor(
                listOf(clouds(dark, 0.5f, 6), RainImplementor(sizes, animate, RainImplementor.TYPE_RAIN, daytime))
            )

            WeatherView.WEATHER_KIND_SLEET -> LayeredImplementor(
                listOf(clouds(grey, 0.45f, 6), RainImplementor(sizes, animate, RainImplementor.TYPE_SLEET, daytime))
            )

            WeatherView.WEATHER_KIND_SNOW -> LayeredImplementor(
                listOf(clouds(grey, 0.4f, 5), SnowImplementor(sizes, animate, daytime))
            )

            WeatherView.WEATHER_KIND_HAIL -> LayeredImplementor(
                listOf(clouds(dark, 0.5f, 6), HailImplementor(sizes, animate, daytime))
            )

            WeatherView.WEATHER_KIND_THUNDERSTORM -> LayeredImplementor(
                listOf(
                    clouds(dark, 0.6f, 7, lightning = true),
                    RainImplementor(sizes, animate, RainImplementor.TYPE_RAIN, daytime)
                )
            )

            WeatherView.WEATHER_KIND_THUNDER -> clouds(dark, 0.6f, 7, lightning = true)

            // Wind: thin, fast-moving clouds
            WeatherView.WEATHER_KIND_WIND -> clouds(if (daytime) light else grey, 0.4f, 4, speed = 4f)

            else -> null
        }
    }

    @DrawableRes
    fun getBackgroundId(
        @WeatherKindRule weatherKind: Int,
        daylight: Boolean,
    ): Int = when (weatherKind) {
        WeatherView.WEATHER_KIND_CLEAR -> if (daylight) {
            R.drawable.weather_background_clear_day
        } else {
            R.drawable.weather_background_clear_night
        }
        WeatherView.WEATHER_KIND_CLOUD -> R.drawable.weather_background_partly_cloudy
        WeatherView.WEATHER_KIND_CLOUDY -> R.drawable.weather_background_cloudy
        WeatherView.WEATHER_KIND_FOG -> R.drawable.weather_background_fog
        WeatherView.WEATHER_KIND_HAIL -> R.drawable.weather_background_hail
        WeatherView.WEATHER_KIND_HAZE -> R.drawable.weather_background_haze
        WeatherView.WEATHER_KIND_RAINY -> R.drawable.weather_background_rain
        WeatherView.WEATHER_KIND_SLEET -> R.drawable.weather_background_sleet
        WeatherView.WEATHER_KIND_SNOW -> R.drawable.weather_background_snow
        WeatherView.WEATHER_KIND_THUNDER, WeatherView.WEATHER_KIND_THUNDERSTORM -> R.drawable.weather_background_thunder
        WeatherView.WEATHER_KIND_WIND -> R.drawable.weather_background_wind
        else -> R.drawable.weather_background_default
    }
}
