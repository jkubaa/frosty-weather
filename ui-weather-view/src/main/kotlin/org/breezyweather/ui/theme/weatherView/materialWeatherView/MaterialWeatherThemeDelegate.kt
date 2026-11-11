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
import androidx.core.graphics.ColorUtils
import org.breezyweather.ui.theme.weatherView.WeatherThemeDelegate
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.breezyweather.ui.theme.weatherView.WeatherView.WeatherKindRule

class MaterialWeatherThemeDelegate : WeatherThemeDelegate {

    companion object {

        fun getBrighterColor(color: Int): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[1] = hsv[1] - 0.25f
            hsv[2] = hsv[2] + 0.25f
            return Color.HSVToColor(hsv)
        }

        /** The top of the sky gradient stands for the background wherever a single color is needed. */
        private fun innerGetBackgroundColor(
            @WeatherKindRule weatherKind: Int,
            daytime: Boolean,
        ): Int = if (weatherKind == WeatherView.WEATHER_KIND_NULL) {
            Color.TRANSPARENT
        } else {
            SkyPalette.topColor(weatherKind, daytime)
        }
    }

    override fun getWeatherView(context: Context): WeatherView = MaterialWeatherView(context)

    override fun getThemeColors(
        context: Context,
        weatherKind: Int,
        daylight: Boolean,
    ): IntArray {
        // Accent colors (chart lines...) sit on dark glass on every sky: always use the brighter shade
        val color = getBrighterColor(innerGetBackgroundColor(weatherKind, daylight))
        return intArrayOf(
            color,
            color,
            ColorUtils.setAlphaComponent(color, (0.5 * 255).toInt())
        )
    }

    override fun isLightBackground(
        context: Context,
        weatherKind: Int,
        daylight: Boolean,
    ): Boolean {
        // The iOS-weather-like skies are mid-to-dark tones, day and night: white text and system bar icons
        return false
    }

    override fun getBackgroundColor(
        context: Context,
        weatherKind: Int,
        daylight: Boolean,
    ): Int {
        return innerGetBackgroundColor(weatherKind, daylight)
    }

    override fun getOnBackgroundColor(
        context: Context,
        weatherKind: Int,
        daylight: Boolean,
    ): Int {
        return if (isLightBackground(context, weatherKind, daylight)) Color.BLACK else Color.WHITE
    }
}
