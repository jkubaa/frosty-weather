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
 * One band of drifting vapour. Positions and sizes are fractions of the screen.
 *
 * @param top top of the band, as a fraction of the screen height
 * @param height band height, as a fraction of the screen height
 * @param widthScale width of one texture tile, in screen widths (tiles repeat horizontally)
 * @param speed drift, in screen widths per second
 * @param cover how much of the band is cloud, 0 (wisps) to 1 (overcast)
 * @param edge where the deck ends, as a fraction of the band height (its lower edge is ragged)
 * @param parallax how far the band moves up when the page scrolls, relative to the sky
 * @param topFade fade in the top of the band too, for banks that float lower down (fog)
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
    val edge: Float,
    val parallax: Float,
    val topFade: Boolean = false,
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

    // Kept close to the sky's own colours: the vapour reads as part of the sky, not cut out of it
    private val CLEAR = Look("#FFFFFF", "#9FB4D2", "#B3A8DA", "#3E376C")
    private val PARTLY = Look("#FFFFFF", "#8FA6C8", "#A9A0D2", "#3A3468")
    private val CLOUDY = Look("#D6DCE4", "#6C778A", "#7A7F94", "#2A2D3C")
    private val FOG = Look("#E6E9EC", "#B9BFC6", "#6C727C", "#3A3F47")
    private val HAZE = Look("#EBDDC4", "#B6A182", "#7A6C55", "#3E3529")
    private val RAIN = Look("#9AA6B6", "#3A4555", "#5A6374", "#1E232D")
    private val SLEET = Look("#B0BAC6", "#4A5566", "#5E6776", "#1F2530")
    private val SNOW = Look("#F4F7FB", "#8C99AE", "#9AA5BE", "#2E3752")
    private val HAIL = Look("#A9B3C0", "#3E495A", "#565F6E", "#1C212B")
    private val THUNDER = Look("#5E6480", "#14161F", "#4A4E66", "#0E0F16")
    private val WIND = Look("#FFFFFF", "#A9BFDA", "#8C9CC4", "#2A385E")

    private fun hex(value: String) = Color(android.graphics.Color.parseColor(value))

    /**
     * A vapour deck in the top part of the sky, Apple style: a slow wide layer at the back and a
     * thinner, faster one in front for depth. The rest of the sky stays a clean gradient.
     *
     * @param depth band height of the back layer: how far down the sky the clouds reach
     * @param edge where the back deck ends within its band (the front one ends a little higher)
     */
    private fun vapor(
        cover: Float,
        opacity: Float,
        speed: Float = 1f,
        depth: Float = 0.44f,
        edge: Float = 0.8f,
    ): List<CloudLayer> = listOf(
        CloudLayer(23, -0.02f, depth, 1.85f, 0.004f * speed, opacity * 0.95f, cover, edge, 0.45f),
        CloudLayer(37, -0.04f, depth * 0.92f, 1.55f, 0.008f * speed, opacity * 0.8f, cover * 0.8f, edge - 0.12f, 0.7f),
    )

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
                listOf(CloudLayer(23, -0.02f, 0.36f, 1.9f, 0.004f, if (daytime) 0.5f else 0.6f, 0.3f, 0.55f, 0.45f)),
                stars = 1f,
                light = 1f,
                horizon = if (daytime) dayHorizon else violetHorizon
            )
            WeatherView.WEATHER_KIND_CLOUD -> style(
                PARTLY,
                vapor(cover = 0.5f, opacity = 0.95f),
                stars = 0.7f,
                light = 0.9f,
                horizon = if (daytime) dayHorizon else violetHorizon
            )
            WeatherView.WEATHER_KIND_CLOUDY -> style(
                CLOUDY,
                vapor(cover = 0.78f, opacity = 1f, depth = 0.52f, edge = 0.9f)
            )
            WeatherView.WEATHER_KIND_RAINY -> style(
                RAIN,
                vapor(cover = 0.75f, opacity = 1f, speed = 1.4f, depth = 0.5f, edge = 0.88f),
                precipitation = Precipitation.RAIN,
                intensity = 0.8f,
                wind = 0.3f
            )
            WeatherView.WEATHER_KIND_SLEET -> style(
                SLEET,
                vapor(cover = 0.72f, opacity = 1f, speed = 1.2f, depth = 0.5f, edge = 0.86f),
                precipitation = Precipitation.SLEET,
                intensity = 0.75f,
                wind = 0.25f
            )
            WeatherView.WEATHER_KIND_SNOW -> style(
                SNOW,
                vapor(cover = 0.7f, opacity = 0.95f, speed = 0.8f, depth = 0.48f, edge = 0.82f),
                precipitation = Precipitation.SNOW,
                intensity = 0.85f,
                wind = 0.15f
            )
            WeatherView.WEATHER_KIND_HAIL -> style(
                HAIL,
                vapor(cover = 0.75f, opacity = 1f, speed = 1.4f, depth = 0.5f, edge = 0.88f),
                precipitation = Precipitation.HAIL,
                intensity = 0.8f,
                wind = 0.3f
            )
            WeatherView.WEATHER_KIND_FOG -> style(
                FOG,
                vapor(cover = 0.5f, opacity = 0.6f, speed = 0.6f),
                fog = 0.95f,
                fogColor = hex(if (daytime) "#D3D7DC" else "#6B717B")
            )
            WeatherView.WEATHER_KIND_HAZE -> style(
                HAZE,
                listOf(CloudLayer(23, -0.02f, 0.38f, 2f, 0.003f, 0.5f, 0.3f, 0.6f, 0.45f)),
                light = 0.55f,
                fog = 0.65f,
                fogColor = hex(if (daytime) "#E8D9BE" else "#75684F")
            )
            WeatherView.WEATHER_KIND_THUNDER -> style(
                THUNDER,
                vapor(cover = 0.8f, opacity = 1f, speed = 1.6f, depth = 0.56f, edge = 0.9f),
                wind = 0.4f,
                lightning = true,
                bolts = true
            )
            WeatherView.WEATHER_KIND_THUNDERSTORM -> style(
                THUNDER,
                vapor(cover = 0.82f, opacity = 1f, speed = 1.8f, depth = 0.56f, edge = 0.9f),
                precipitation = Precipitation.RAIN,
                intensity = 1f,
                wind = 0.5f,
                lightning = true,
                bolts = true
            )
            WeatherView.WEATHER_KIND_WIND -> style(
                WIND,
                vapor(cover = 0.4f, opacity = 0.85f, speed = 5f, edge = 0.7f),
                stars = 0.8f,
                light = 0.85f,
                wind = 1f
            )
            else -> style(PARTLY, emptyList())
        }
    }
}
