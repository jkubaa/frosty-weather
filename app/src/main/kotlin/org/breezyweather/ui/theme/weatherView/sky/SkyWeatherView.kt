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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.breezyweather.BuildConfig
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.shredzone.commons.suncalc.MoonIllumination
import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.SunPosition
import java.util.Date
import kotlin.math.abs

/**
 * Home screen sky, drawn with Compose: painterly drifting clouds, stars and moon at night, sun
 * glow by day, and the weather itself (rain, snow, sleet, hail, lightning, fog, haze, wind).
 *
 * Given the location's coordinates ([setLocation]), the sky follows the real sun and moon:
 * day and night skies blend through twilight, sunrise and sunset warm the horizon and the clouds,
 * and the moon shows its current phase, only while it is above the horizon. These are computed on
 * the device, so they work even when the weather source sends no sun or moon data.
 *
 * Plugs into the existing View-based home screen through [WeatherView]. Everything is drawn with
 * plain canvas operations (no runtime shaders), because the liquid glass cards blur this view by
 * drawing it into a software canvas, where shaders would not render.
 */
class SkyWeatherView(context: Context) : AbstractComposeView(context), WeatherView {

    private var kind by mutableIntStateOf(WeatherView.WEATHER_KIND_NULL)
    private var daytime by mutableStateOf(true)
    private var coordinates by mutableStateOf<Pair<Double, Double>?>(null)
    private var drawing by mutableStateOf(false)
    private var animating by mutableStateOf(true)
    private var scroll by mutableFloatStateOf(0f)
    private var tiltEnabled by mutableStateOf(false)
    private val tilt = TiltSensor(context)

    /** This sky, for the glass cards to draw behind them. */
    val backdrop = SkyBackdrop()

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override val weatherKind: Int
        get() = kind

    override fun setWeather(weatherKind: Int, daytime: Boolean, darkMode: Boolean) {
        kind = weatherKind
        this.daytime = daytime
    }

    /** Coordinates of the location shown, for the sun and moon; null falls back to day/night only. */
    fun setLocation(latitude: Double?, longitude: Double?) {
        coordinates = if (latitude != null && longitude != null && !(latitude == 0.0 && longitude == 0.0)) {
            latitude to longitude
        } else {
            null
        }
    }

    override fun onScroll(scrollY: Int) {
        val range = SCROLL_RANGE_DP * resources.displayMetrics.density
        scroll = (scrollY / range).coerceIn(0f, 1f)
    }

    override fun setDrawable(drawable: Boolean) {
        drawing = drawable
    }

    override fun setDoAnimate(animate: Boolean) {
        animating = animate
    }

    override fun setGravitySensorEnabled(enabled: Boolean) {
        tiltEnabled = enabled
    }

    @Composable
    override fun Content() {
        // Listen to the tilt only while the sky is on screen and moving, and the setting allows it
        val listen = tiltEnabled && drawing && animating
        DisposableEffect(listen) {
            if (listen) tilt.start()
            onDispose { tilt.stop() }
        }
        var debug by remember { mutableStateOf(DebugSky()) }
        if (BuildConfig.DEBUG) {
            LaunchedEffect(Unit) {
                while (true) {
                    debug = DebugSky.read()
                    delay(1000)
                }
            }
        }
        SkyScene(
            weatherKind = debug.kind ?: kind,
            daytime = debug.daytime ?: daytime,
            coordinates = coordinates,
            running = drawing && animating,
            scroll = { scroll },
            tilt = { tilt.value },
            debugSunAltitude = debug.sunAltitude,
            debugMoonPhase = debug.moonPhase,
            debugAurora = debug.aurora,
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize()
        )
    }

    private companion object {
        /** Scroll distance over which the sky settles behind the cards. */
        const val SCROLL_RANGE_DP = 260f
    }
}

/**
 * Debug builds only, to check any sky without waiting for the weather or the time of day:
 * - `adb shell setprop debug.frosty.sky 4` forces a WeatherView kind, optionally `4,night`
 * - `adb shell setprop debug.frosty.sun -2` forces the sun altitude in degrees (sunset is around 0)
 * - `adb shell setprop debug.frosty.moon 90` forces the moon phase, -180 new / 0 full / 180 new
 * - `adb shell setprop debug.frosty.aurora 1` forces the aurora strength (0..1) on clear nights
 * An empty value (`setprop debug.frosty.sky ""`) goes back to the real sky.
 */
@Immutable
private data class DebugSky(
    val kind: Int? = null,
    val daytime: Boolean? = null,
    val sunAltitude: Double? = null,
    val moonPhase: Double? = null,
    val aurora: Float? = null,
) {
    companion object {
        @SuppressLint("PrivateApi")
        private fun prop(name: String): String = try {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, name) as String
        } catch (_: Exception) {
            ""
        }

        fun read(): DebugSky {
            val sky = prop("debug.frosty.sky").split(",")
            return DebugSky(
                kind = sky[0].trim().toIntOrNull(),
                daytime = sky[0].trim().toIntOrNull()?.let { sky.getOrNull(1)?.trim() != "night" },
                sunAltitude = prop("debug.frosty.sun").trim().toDoubleOrNull(),
                moonPhase = prop("debug.frosty.moon").trim().toDoubleOrNull(),
                aurora = prop("debug.frosty.aurora").trim().toFloatOrNull()
            )
        }
    }
}

/** Where the sun and moon are now, in degrees. */
@Immutable
private data class Astro(
    val sunAltitude: Double,
    val moonAltitude: Double,
    val moonPhase: Double,
    val southern: Boolean,
)

/** Both skies of a weather kind: they blend through twilight. */
private class SkyPair(val kind: Int, val day: SkyAssets, val night: SkyAssets)

/**
 * @param daytime used when [coordinates] are unknown
 * @param running animate; when false the sky holds its current frame
 * @param scroll read at draw time only, so scrolling redraws without recomposing
 */
@Composable
private fun SkyScene(
    weatherKind: Int,
    daytime: Boolean,
    coordinates: Pair<Double, Double>?,
    running: Boolean,
    scroll: () -> Float,
    tilt: () -> Offset,
    debugSunAltitude: Double?,
    debugMoonPhase: Double?,
    debugAurora: Float?,
    backdrop: SkyBackdrop,
    modifier: Modifier = Modifier,
) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val base = time.floatValue
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { time.floatValue = base + (it - start) / 1_000_000_000f }
        }
    }

    // Sun and moon move slowly: once a minute is plenty
    val astro by produceState<Astro?>(null, coordinates) {
        val (latitude, longitude) = coordinates ?: run {
            value = null
            return@produceState
        }
        while (true) {
            value = withContext(Dispatchers.Default) { computeAstro(latitude, longitude) }
            delay(20_000)
        }
    }

    val sunAltitude = debugSunAltitude ?: astro?.sunAltitude
    val targetDay = when {
        sunAltitude != null -> smoothstep(-9f, 3f, sunAltitude.toFloat())
        daytime -> 1f
        else -> 0f
    }
    val targetGolden = if (sunAltitude != null) {
        val a = sunAltitude.toFloat()
        smoothstep(-9f, -1f, a) * (1f - smoothstep(3f, 10f, a))
    } else {
        0f
    }
    val dayAmount by animateFloatAsState(targetDay, tween(1200), label = "day")
    val golden by animateFloatAsState(targetGolden, tween(1200), label = "golden")
    val sunAlpha = sunAltitude?.let { smoothstep(-4f, 0f, it.toFloat()) } ?: 1f
    val moonPhase = (debugMoonPhase ?: astro?.moonPhase ?: 0.0).toFloat()
    val moonAlpha = when {
        debugMoonPhase != null || astro == null -> 1f
        else -> smoothstep(-3f, 3f, astro!!.moonAltitude.toFloat())
    }
    val southern = astro?.southern ?: false
    // Without space weather data, aurora shows where it is common: clear nights from about 55° of latitude
    val aurora = debugAurora ?: coordinates?.let { smoothstep(55f, 62f, abs(it.first.toFloat())) } ?: 0f

    // Cross-fade between weather kinds. The new sky only fades in once its textures are ready.
    var shown by remember { mutableStateOf<SkyPair?>(null) }
    var leaving by remember { mutableStateOf<SkyPair?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(weatherKind) {
        if (shown?.kind == weatherKind) return@LaunchedEffect
        val next = withContext(Dispatchers.Default) {
            SkyPair(
                weatherKind,
                SkyAssets.create(SkyStyles.of(weatherKind, daytime = true)),
                SkyAssets.create(SkyStyles.of(weatherKind, daytime = false))
            )
        }
        leaving = shown
        shown = next
        fade.snapTo(0f)
        fade.animateTo(1f, tween(if (leaving == null) 600 else 2400, easing = FastOutSlowInEasing))
        leaving = null
    }
    val placeholder = remember(weatherKind) {
        SkyStyles.of(weatherKind, daytime = true) to SkyStyles.of(weatherKind, daytime = false)
    }

    // Shared with the glass cards (see SkyBackdrop): they draw the same sky, blurred, behind them
    // Rainy weather is seen through a wet window (Android 10+), which fades in and out with the weather
    val glassTarget = when (weatherKind) {
        WeatherView.WEATHER_KIND_RAINY -> GlassWeather.RAIN
        WeatherView.WEATHER_KIND_SLEET -> GlassWeather.SLEET
        WeatherView.WEATHER_KIND_THUNDERSTORM -> GlassWeather.STORM
        else -> null
    }
    var glassWeather by remember { mutableStateOf(GlassWeather.RAIN) }
    if (glassTarget != null) glassWeather = glassTarget
    val glassAmount by animateFloatAsState(if (glassTarget != null) 1f else 0f, tween(2000), label = "glass")
    val rainGlass = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) RainGlass() else null }

    val sky: DrawScope.() -> Unit = {
        val light = SkyLight(dayAmount, golden, sunAlpha, moonAlpha, moonPhase, southern, sunAltitude?.toFloat(), aurora)
        val t = time.floatValue
        val s = scroll()
        val tl = tilt()
        val current = shown
        val previous = leaving
        val p = fade.value
        when {
            current == null -> {
                drawSkyGradient(placeholder.second)
                drawSkyGradient(placeholder.first, dayAmount)
            }
            previous != null -> {
                // The weather arrives: new clouds drift in from the side as the old ones drift away
                val eased = p * p * (3f - 2f * p)
                drawPair(previous, light, t, s, backgroundAlpha = 1f, effectsAlpha = 1f - p, tilt = tl, slide = -eased * 0.5f)
                drawPair(current, light, t, s, backgroundAlpha = p, effectsAlpha = p, tilt = tl, slide = (1f - eased) * 0.6f)
            }
            else -> drawPair(current, light, t, s, backgroundAlpha = 1f, effectsAlpha = p, tilt = tl)
        }
    }
    val painter: DrawScope.() -> Unit = {
        val glass = rainGlass
        if (glass != null && glassAmount > 0.01f && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            glass.draw(this, time.floatValue, glassAmount, glassWeather, sky)
            // A storm's bolts stay readable through the fogged glass
            if (weatherKind == WeatherView.WEATHER_KIND_THUNDERSTORM) {
                drawStrikeBolts(strikesAt(time.floatValue, bolts = true), time.floatValue, glassAmount, coreOnly = true)
            }
        } else {
            sky()
        }
        // Cards scrolled over the sky: it settles back and dims a little, so they read well
        val settled = scroll()
        if (settled > 0.01f) drawRect(Color.Black, alpha = 0.12f * settled)
    }
    SideEffect { backdrop.painter = painter }
    val density = LocalDensity.current
    Canvas(
        modifier.onGloballyPositioned {
            backdrop.origin = it.positionInWindow()
            backdrop.size = it.size.toSize()
        }
    ) {
        val canvas = drawContext.canvas.nativeCanvas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated) {
            // Recorded once per frame, drawn here and referenced by the glass cards
            val node = backdrop.obtainNode()
            node.setPosition(0, 0, size.width.toInt(), size.height.toInt())
            val recording = node.beginRecording()
            CanvasDrawScope().draw(density, layoutDirection, androidx.compose.ui.graphics.Canvas(recording), size) { painter() }
            node.endRecording()
            canvas.drawRenderNode(node)
        } else {
            // Software canvas (e.g. the bottom bar capturing the screen to blur it)
            painter()
        }
    }
}

private fun DrawScope.drawPair(
    pair: SkyPair,
    light: SkyLight,
    time: Float,
    scroll: Float,
    backgroundAlpha: Float,
    effectsAlpha: Float,
    tilt: Offset = Offset.Zero,
    slide: Float = 0f,
) {
    val day = light.dayAmount
    if (day < 1f) drawSkyBackground(pair.night, backgroundAlpha)
    if (day > 0f) drawSkyBackground(pair.day, backgroundAlpha * if (day < 1f) day else 1f)
    val golden = light.golden * pair.day.style.goldenStrength
    drawGoldenHour(golden * effectsAlpha, light.sunAltitude)
    drawSkyEffects(pair.night, light, time, scroll, effectsAlpha * (1f - day), warmth = golden, tilt = tilt, slide = slide)
    drawSkyEffects(pair.day, light, time, scroll, effectsAlpha * day, warmth = golden, tilt = tilt, slide = slide)
}

private fun computeAstro(latitude: Double, longitude: Double): Astro {
    val now = Date()
    val sun = SunPosition.compute().on(now).at(latitude, longitude).execute()
    val moon = MoonPosition.compute().on(now).at(latitude, longitude).execute()
    val illumination = MoonIllumination.compute().on(now).execute()
    return Astro(
        sunAltitude = sun.altitude,
        moonAltitude = moon.altitude,
        moonPhase = illumination.phase,
        southern = latitude < 0.0
    )
}
