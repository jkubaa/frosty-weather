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

package org.breezyweather.ui.theme.weatherView.materialWeatherView

import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.core.graphics.toColorInt
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.breezyweather.ui.theme.weatherView.WeatherView.WeatherKindRule

/**
 * Sky backgrounds in the spirit of iOS weather: one smooth vertical gradient per weather kind,
 * with its own day and night colors. Mid-to-dark tones throughout, so white text always reads.
 */
object SkyPalette {

    /** [top, bottom] colors of the sky. */
    private class Sky(val dayTop: String, val dayBottom: String, val nightTop: String, val nightBottom: String)

    private val CLEAR = Sky("#2C78D2", "#86BFF0", "#0A1330", "#2A3966")
    private val PARTLY_CLOUDY = Sky("#4683C2", "#A4C4E0", "#111A2E", "#35425D")
    private val CLOUDY = Sky("#6A7D91", "#A5B2BF", "#1B212B", "#3B4451")
    private val FOG = Sky("#8C97A2", "#C6CCD2", "#252A31", "#4A515A")
    private val HAZE = Sky("#A69479", "#D7C9B1", "#2A2620", "#4D453A")
    private val RAIN = Sky("#4C5B6E", "#7D8C9D", "#151B24", "#323B48")
    private val SLEET = Sky("#5C6C7E", "#98A6B4", "#19202A", "#394351")
    private val SNOW = Sky("#7B91AB", "#C1CEDC", "#1E2737", "#455269")
    private val HAIL = Sky("#546578", "#91A0B0", "#161D27", "#353F4C")
    private val THUNDER = Sky("#393F50", "#61687A", "#11131A", "#2B2F3F")
    private val WIND = Sky("#4A86C0", "#A0C1DB", "#10192B", "#323F59")
    private val DEFAULT = Sky("#3B6DA6", "#8DB1D4", "#13182A", "#2E374F")

    private fun sky(@WeatherKindRule weatherKind: Int): Sky = when (weatherKind) {
        WeatherView.WEATHER_KIND_CLEAR -> CLEAR
        WeatherView.WEATHER_KIND_CLOUD -> PARTLY_CLOUDY
        WeatherView.WEATHER_KIND_CLOUDY -> CLOUDY
        WeatherView.WEATHER_KIND_FOG -> FOG
        WeatherView.WEATHER_KIND_HAZE -> HAZE
        WeatherView.WEATHER_KIND_RAINY -> RAIN
        WeatherView.WEATHER_KIND_SLEET -> SLEET
        WeatherView.WEATHER_KIND_SNOW -> SNOW
        WeatherView.WEATHER_KIND_HAIL -> HAIL
        WeatherView.WEATHER_KIND_THUNDER, WeatherView.WEATHER_KIND_THUNDERSTORM -> THUNDER
        WeatherView.WEATHER_KIND_WIND -> WIND
        else -> DEFAULT
    }

    /** Color at the top of the sky: used where a single color stands for the background. */
    @ColorInt
    fun topColor(@WeatherKindRule weatherKind: Int, daylight: Boolean): Int =
        sky(weatherKind).let { if (daylight) it.dayTop else it.nightTop }.toColorInt()

    @ColorInt
    fun bottomColor(@WeatherKindRule weatherKind: Int, daylight: Boolean): Int =
        sky(weatherKind).let { if (daylight) it.dayBottom else it.nightBottom }.toColorInt()

    fun gradient(@WeatherKindRule weatherKind: Int, daylight: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(topColor(weatherKind, daylight), bottomColor(weatherKind, daylight))
    )
}
