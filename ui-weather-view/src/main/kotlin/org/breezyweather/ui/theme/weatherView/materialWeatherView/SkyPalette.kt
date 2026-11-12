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
 * Sky colors per weather kind, day and night: a three-stop vertical gradient (top, middle, bottom).
 * Night skies lean towards deep indigo with a violet/magenta horizon on fair weather, grey-blue
 * when the weather turns. Mid-to-dark tones throughout, so white text always reads.
 *
 * Single source for the animated home sky, the location cards and every place where one color
 * stands for the background.
 */
object SkyPalette {

    private class Sky(
        val dayTop: String,
        val dayMiddle: String,
        val dayBottom: String,
        val nightTop: String,
        val nightMiddle: String,
        val nightBottom: String,
    )

    private val CLEAR = Sky("#1C64C8", "#3F8BDD", "#8EC3F0", "#0E0B2E", "#2E1F5E", "#6A3A80")
    private val PARTLY_CLOUDY = Sky("#3573BE", "#5F97D4", "#A9CBEA", "#120F33", "#36246A", "#7E4588")
    private val CLOUDY = Sky("#5B6880", "#7A879E", "#A7B1C3", "#181A25", "#2A2D3D", "#454A5C")
    private val FOG = Sky("#858E9A", "#A3AAB4", "#C6CBD1", "#1E2229", "#333841", "#4E545E")
    private val HAZE = Sky("#9B876A", "#B9A584", "#D8C8AB", "#28221B", "#3B3329", "#594D3D")
    private val RAIN = Sky("#3B4759", "#536077", "#788497", "#0F131B", "#1D232F", "#313947")
    private val SLEET = Sky("#536378", "#74859B", "#A4B2C2", "#141A25", "#252E3D", "#3D4859")
    private val SNOW = Sky("#6A82A4", "#8FA6C2", "#C8D5E4", "#182035", "#2C3853", "#535F7D")
    private val HAIL = Sky("#475669", "#627389", "#909EAF", "#121822", "#212A37", "#364051")
    private val THUNDER = Sky("#282B3B", "#3C3F53", "#585A70", "#0A0B13", "#161825", "#282A3B")
    private val WIND = Sky("#3877BC", "#6799CE", "#AAC7E4", "#0F182E", "#213158", "#43557E")
    private val DEFAULT = Sky("#3B6DA6", "#5F8DC0", "#8DB1D4", "#13182A", "#222A40", "#2E374F")

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
    fun middleColor(@WeatherKindRule weatherKind: Int, daylight: Boolean): Int =
        sky(weatherKind).let { if (daylight) it.dayMiddle else it.nightMiddle }.toColorInt()

    @ColorInt
    fun bottomColor(@WeatherKindRule weatherKind: Int, daylight: Boolean): Int =
        sky(weatherKind).let { if (daylight) it.dayBottom else it.nightBottom }.toColorInt()

    fun gradient(@WeatherKindRule weatherKind: Int, daylight: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            topColor(weatherKind, daylight),
            middleColor(weatherKind, daylight),
            bottomColor(weatherKind, daylight)
        )
    )
}
