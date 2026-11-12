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

package org.breezyweather.ui.theme.weatherView.sky

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.breezyweather.ui.theme.weatherView.materialWeatherView.SkyPalette

enum class Precipitation { NONE, RAIN, SNOW, SLEET, HAIL }

/**
 * One band of drifting clouds. Positions and sizes are fractions of the screen.
 *
 * @param top top of the band, as a fraction of the screen height
 * @param height band height, as a fraction of the screen height
 * @param widthScale width of one texture tile, in screen widths (tiles repeat horizontally)
 * @param speed drift, in screen widths per second
 * @param cover how much of the band is cloud, 0 (wisps) to 1 (overcast)
 * @param wispy streaky, stretched clouds (cirrus-like) instead of puffy ones
 * @param parallax how far the band moves up when the page scrolls, relative to the sky
 */
@Immutable
data class CloudLayer(
    val seed: Int,
    val top: Float,
    val height: Float,
    val widthScale: Float,
    val speed: Float,
    val opacity: Float,
    val cover: Float,
    val wispy: Boolean,
    val parallax: Float,
)

/** Everything that makes one sky: colors, light sources, clouds and weather effects. */
@Immutable
data class SkyStyle(
    val weatherKind: Int,
    val daytime: Boolean,
    val top: Color,
    val middle: Color,
    val bottom: Color,
    /** Soft light rising from the horizon (e.g. the violet glow of fair nights). */
    val horizonGlow: Color,
    val cloudLight: Color,
    val cloudShadow: Color,
    val clouds: List<CloudLayer>,
    val starOpacity: Float,
    /** Sun by day, moon by night: 0 when hidden behind the clouds. */
    val lightOpacity: Float,
    val lightGlow: Color,
    val precipitation: Precipitation,
    val precipitationIntensity: Float,
    val precipitationColor: Color,
    /** 0 calm, 1 windy: cloud speed, rain slant, wind streaks. */
    val wind: Float,
    val lightning: Boolean,
    val lightningBolts: Boolean,
    val fog: Float,
    val fogColor: Color,
    /** How much sunrise/sunset colors show through: full on open skies, faint under thick cloud. */
    val goldenStrength: Float,
)

object SkyStyles {

    private class Look(
        val dayLight: String,
        val dayShadow: String,
        val nightLight: String,
        val nightShadow: String,
    )

    private val CLEAR = Look("#FFFFFF", "#C3D6EE", "#C7A9E0", "#4A3478")
    private val PARTLY = Look("#FFFFFF", "#A8BAD2", "#D0B0E4", "#4E3678")
    private val CLOUDY = Look("#D0D6DF", "#6C778A", "#6D7286", "#262938")
    private val FOG = Look("#E6E9EC", "#B9BFC6", "#6C727C", "#3A3F47")
    private val HAZE = Look("#EBDDC4", "#B6A182", "#7A6C55", "#3E3529")
    private val RAIN = Look("#9AA3B1", "#434E64", "#4B5464", "#1A1F28")
    private val SLEET = Look("#B5BEC9", "#56647A", "#57606F", "#1E2430")
    private val SNOW = Look("#E2E8F0", "#93A3BA", "#8996B2", "#2C3550")
    private val HAIL = Look("#B5BECA", "#4F5C70", "#4F5869", "#1B212B")
    private val THUNDER = Look("#7D8096", "#252838", "#3E4157", "#10111A")
    private val WIND = Look("#FFFFFF", "#A9BFDA", "#8C9CC4", "#2A385E")

    private fun hex(value: String) = Color(android.graphics.Color.parseColor(value))

    /** Back to front: a high thin band, a middle band, and a big low band in front. */
    private fun layers(
        cover: Float,
        opacity: Float,
        wispy: Boolean,
        speed: Float = 1f,
        overcast: Boolean = false,
    ): List<CloudLayer> = buildList {
        if (overcast) {
            // Fills the whole sky behind the bands, so no blue shows through
            add(CloudLayer(11, -0.08f, 1.15f, 2.2f, 0.0025f * speed, opacity, 0.92f, false, 0.25f))
        }
        add(CloudLayer(23, -0.04f, 0.42f, 1.9f, 0.0040f * speed, opacity * 0.8f, cover, wispy, 0.45f))
        add(CloudLayer(37, 0.16f, 0.5f, 1.7f, 0.0065f * speed, opacity * 0.9f, cover, wispy, 0.7f))
        add(CloudLayer(51, 0.42f, 0.62f, 1.5f, 0.0100f * speed, opacity, cover * 0.9f, false, 1f))
    }

    fun of(weatherKind: Int, daytime: Boolean): SkyStyle {
        val top = Color(SkyPalette.topColor(weatherKind, daytime))
        val middle = Color(SkyPalette.middleColor(weatherKind, daytime))
        val bottom = Color(SkyPalette.bottomColor(weatherKind, daytime))
        val sun = hex("#FFF1CC")
        val moon = hex("#B9A6F0")
        val violetHorizon = hex("#C8559A").copy(alpha = 0.45f)
        val dayHorizon = Color.White.copy(alpha = 0.12f)
        val rainColor = if (daytime) hex("#DCE6F2") else hex("#A9B6CC")

        fun style(
            look: Look,
            clouds: List<CloudLayer>,
            stars: Float = 0f,
            light: Float = 0f,
            horizon: Color = Color.Transparent,
            precipitation: Precipitation = Precipitation.NONE,
            intensity: Float = 0f,
            wind: Float = 0f,
            lightning: Boolean = false,
            bolts: Boolean = false,
            fog: Float = 0f,
            fogColor: Color = Color.Transparent,
        ) = SkyStyle(
            weatherKind = weatherKind,
            daytime = daytime,
            top = top,
            middle = middle,
            bottom = bottom,
            horizonGlow = horizon,
            cloudLight = hex(if (daytime) look.dayLight else look.nightLight),
            cloudShadow = hex(if (daytime) look.dayShadow else look.nightShadow),
            clouds = clouds,
            starOpacity = if (daytime) 0f else stars,
            lightOpacity = light,
            lightGlow = if (daytime) sun else moon,
            precipitation = precipitation,
            precipitationIntensity = intensity,
            precipitationColor = if (precipitation == Precipitation.RAIN) rainColor else Color.White,
            wind = wind,
            lightning = lightning,
            lightningBolts = bolts,
            fog = fog,
            fogColor = fogColor,
            goldenStrength = when (weatherKind) {
                WeatherView.WEATHER_KIND_CLEAR,
                WeatherView.WEATHER_KIND_CLOUD,
                WeatherView.WEATHER_KIND_WIND,
                WeatherView.WEATHER_KIND_HAZE,
                -> 1f
                WeatherView.WEATHER_KIND_CLOUDY -> 0.45f
                WeatherView.WEATHER_KIND_FOG -> 0.35f
                WeatherView.WEATHER_KIND_THUNDER, WeatherView.WEATHER_KIND_THUNDERSTORM -> 0.15f
                else -> 0.3f
            },
        )

        return when (weatherKind) {
            WeatherView.WEATHER_KIND_CLEAR -> style(
                CLEAR,
                // A few high wisps only: the sky stays open
                listOf(CloudLayer(23, -0.02f, 0.4f, 2f, 0.004f, if (daytime) 0.45f else 0.6f, 0.22f, true, 0.45f)),
                stars = 1f,
                light = 1f,
                horizon = if (daytime) dayHorizon else violetHorizon
            )
            WeatherView.WEATHER_KIND_CLOUD -> style(
                PARTLY,
                layers(cover = 0.5f, opacity = 0.9f, wispy = !daytime),
                stars = 0.7f,
                light = 0.9f,
                horizon = if (daytime) dayHorizon else violetHorizon
            )
            WeatherView.WEATHER_KIND_CLOUDY -> style(
                CLOUDY,
                layers(cover = 0.82f, opacity = 0.95f, wispy = false, overcast = true)
            )
            WeatherView.WEATHER_KIND_RAINY -> style(
                RAIN,
                layers(cover = 0.86f, opacity = 0.95f, wispy = false, speed = 1.4f, overcast = true),
                precipitation = Precipitation.RAIN,
                intensity = 0.8f,
                wind = 0.3f
            )
            WeatherView.WEATHER_KIND_SLEET -> style(
                SLEET,
                layers(cover = 0.82f, opacity = 0.95f, wispy = false, speed = 1.2f, overcast = true),
                precipitation = Precipitation.SLEET,
                intensity = 0.75f,
                wind = 0.25f
            )
            WeatherView.WEATHER_KIND_SNOW -> style(
                SNOW,
                layers(cover = 0.76f, opacity = 0.9f, wispy = false, speed = 0.8f, overcast = true),
                precipitation = Precipitation.SNOW,
                intensity = 0.85f,
                wind = 0.15f
            )
            WeatherView.WEATHER_KIND_HAIL -> style(
                HAIL,
                layers(cover = 0.86f, opacity = 0.95f, wispy = false, speed = 1.4f, overcast = true),
                precipitation = Precipitation.HAIL,
                intensity = 0.8f,
                wind = 0.3f
            )
            WeatherView.WEATHER_KIND_FOG -> style(
                FOG,
                layers(cover = 0.6f, opacity = 0.6f, wispy = true, speed = 0.6f),
                fog = 0.95f,
                fogColor = hex(if (daytime) "#D3D7DC" else "#6B717B")
            )
            WeatherView.WEATHER_KIND_HAZE -> style(
                HAZE,
                listOf(CloudLayer(23, -0.02f, 0.45f, 2f, 0.003f, 0.5f, 0.3f, true, 0.45f)),
                light = 0.55f,
                fog = 0.65f,
                fogColor = hex(if (daytime) "#E8D9BE" else "#75684F")
            )
            WeatherView.WEATHER_KIND_THUNDER -> style(
                THUNDER,
                layers(cover = 0.9f, opacity = 1f, wispy = false, speed = 1.6f, overcast = true),
                wind = 0.4f,
                lightning = true,
                bolts = true
            )
            WeatherView.WEATHER_KIND_THUNDERSTORM -> style(
                THUNDER,
                layers(cover = 0.92f, opacity = 1f, wispy = false, speed = 1.8f, overcast = true),
                precipitation = Precipitation.RAIN,
                intensity = 1f,
                wind = 0.5f,
                lightning = true,
                bolts = true
            )
            WeatherView.WEATHER_KIND_WIND -> style(
                WIND,
                layers(cover = 0.4f, opacity = 0.85f, wispy = true, speed = 5f),
                stars = 0.8f,
                light = 0.85f,
                wind = 1f
            )
            else -> style(PARTLY, emptyList())
        }
    }
}
