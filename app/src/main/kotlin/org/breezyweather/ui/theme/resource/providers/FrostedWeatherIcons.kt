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

package org.breezyweather.ui.theme.resource.providers

import androidx.annotation.DrawableRes
import breezyweather.domain.weather.reference.WeatherCode
import org.breezyweather.R

/**
 * Frosted weather icons: the app's built-in, full-color weather art (soft gradient style).
 *
 * The files keep their Chronus / Yahoo condition numbers (frosted_weather_<code>), and the mapping
 * follows [ChronusResourceProvider]'s, except that thunder without rain and thunderstorms get
 * their own art (4 and 3). Only the conditions reachable from a [WeatherCode] are bundled.
 */
object FrostedWeatherIcons {

    @DrawableRes
    fun get(code: WeatherCode?, daytime: Boolean): Int = when (code) {
        WeatherCode.CLEAR -> if (daytime) R.drawable.frosted_weather_32 else R.drawable.frosted_weather_31
        WeatherCode.PARTLY_CLOUDY -> if (daytime) R.drawable.frosted_weather_30 else R.drawable.frosted_weather_29
        WeatherCode.CLOUDY -> R.drawable.frosted_weather_26
        WeatherCode.RAIN -> R.drawable.frosted_weather_12
        WeatherCode.SNOW -> R.drawable.frosted_weather_16
        WeatherCode.WIND -> R.drawable.frosted_weather_24
        WeatherCode.FOG -> R.drawable.frosted_weather_20
        WeatherCode.HAZE -> R.drawable.frosted_weather_21
        WeatherCode.SLEET -> R.drawable.frosted_weather_5
        WeatherCode.HAIL -> R.drawable.frosted_weather_17
        WeatherCode.THUNDER -> R.drawable.frosted_weather_4
        WeatherCode.THUNDERSTORM -> R.drawable.frosted_weather_3
        else -> R.drawable.frosted_weather_na
    }
}
