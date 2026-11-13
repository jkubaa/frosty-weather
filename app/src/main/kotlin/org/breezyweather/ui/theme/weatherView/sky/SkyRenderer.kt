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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Everything a sky needs to be drawn: its style, its cloud textures and its particles.
 *
 * Particles are not simulated: each one is a set of fixed random parameters, and its position is
 * a pure function of time. Nothing is allocated or updated per frame, and a paused sky simply
 * keeps its last frame.
 */
internal class SkyAssets private constructor(
    val style: SkyStyle,
    val clouds: List<Pair<CloudLayer, ImageBitmap>>,
    val fog: List<Pair<CloudLayer, ImageBitmap>>,
    /** x, y, radius (dp), phase, twinkle speed, color index into [STAR_COLORS] */
    val stars: FloatArray,
    /** Night skies with stars only */
    val milkyWay: ImageBitmap?,
    /** Low deck on the horizon, shown at sunrise and sunset (day skies that can see the sun) */
    val horizon: Pair<CloudLayer, ImageBitmap>?,
    /** x, phase, speed (screen heights/s), length (dp), width (dp), alpha */
    val drops: FloatArray,
    /** Snow and sleet only */
    val flakes: Snowfall?,
    /** y, phase, length (screen widths), speed (screen widths/s), alpha */
    val streaks: FloatArray,
) {
    companion object {
        const val STAR_STRIDE = 6
        const val DROP_STRIDE = 6
        const val STREAK_STRIDE = 5

        /** Heavy work (cloud textures): call off the main thread. */
        fun create(style: SkyStyle): SkyAssets {
            val random = Random(style.weatherKind * 31 + if (style.daytime) 1 else 0)
            val clouds = style.clouds.map { it to CloudTextures.get(it, style.cloudLight, style.cloudShadow) }
            val fog = if (style.fog > 0f) {
                listOf(
                    CloudLayer(71, 0.22f, 0.42f, 2.0f, 0.006f, 1f, 0.55f, 0.85f, 0.3f, topFade = true),
                    CloudLayer(83, 0.44f, 0.42f, 1.7f, 0.010f, 1f, 0.6f, 0.85f, 0.5f, topFade = true)
                ).map { it to CloudTextures.get(it, style.fogColor, style.fogColor, shaded = false) }
            } else {
                emptyList()
            }

            val starCount = if (style.starOpacity > 0f) 260 else 0
            val band = NightSky.BAND_END - NightSky.BAND_START
            val stars = FloatArray(starCount * STAR_STRIDE) { 0f }.also { a ->
                for (i in 0 until starCount) {
                    val o = i * STAR_STRIDE
                    if (random.nextFloat() < 0.4f) {
                        // Crowding along the Milky Way
                        val k = random.nextFloat()
                        val spread = (random.nextFloat() + random.nextFloat() + random.nextFloat() - 1.5f) * 0.07f
                        a[o] = NightSky.BAND_START.x + band.x * k + spread * 0.9f
                        a[o + 1] = NightSky.BAND_START.y + band.y * k + spread * 0.5f
                    } else {
                        // A little beyond the screen, so the slowly turning sky never runs out of stars
                        a[o] = -0.15f + random.nextFloat() * 1.3f
                        // Mostly high in the sky, thinning out towards the horizon
                        a[o + 1] = random.nextFloat().let { it * it } * 0.8f
                    }
                    a[o + 2] = if (random.nextFloat() < 0.05f) 1.5f + random.nextFloat() * 0.7f else 0.45f + random.nextFloat() * 0.8f
                    a[o + 3] = random.nextFloat() * 2f * PI.toFloat()
                    a[o + 4] = 0.6f + random.nextFloat() * 2.4f
                    a[o + 5] = random.nextInt(STAR_COLORS.size).toFloat()
                }
            }
            val milkyWay = if (style.starOpacity >= 0.5f) NightSky.milkyWay else null

            val rain = style.precipitation == Precipitation.RAIN || style.precipitation == Precipitation.SLEET
            val hail = style.precipitation == Precipitation.HAIL
            val dropCount = when {
                rain -> (240 * style.precipitationIntensity * if (style.precipitation == Precipitation.SLEET) 0.5f else 1f).toInt()
                hail -> (90 * style.precipitationIntensity).toInt()
                else -> 0
            }
            val drops = FloatArray(dropCount * DROP_STRIDE).also { a ->
                for (i in 0 until dropCount) {
                    val o = i * DROP_STRIDE
                    val depth = random.nextFloat() // 0 far, 1 near
                    a[o] = random.nextFloat()
                    a[o + 1] = random.nextFloat()
                    if (hail) {
                        a[o + 2] = 1.2f + depth * 0.8f
                        a[o + 3] = 4f + depth * 5f
                        a[o + 4] = 2.2f + depth * 2f
                        a[o + 5] = 0.55f + depth * 0.4f
                    } else {
                        a[o + 2] = 1.3f + depth * 1.4f
                        a[o + 3] = 14f + depth * 26f
                        a[o + 4] = 0.8f + depth * 1.1f
                        a[o + 5] = 0.14f + depth * 0.3f
                    }
                }
            }

            val sleet = style.precipitation == Precipitation.SLEET
            val flakes = if (style.precipitation == Precipitation.SNOW || sleet) {
                Snowfall((520 * style.precipitationIntensity * if (sleet) 0.4f else 1f).toInt(), sleet, random.nextInt())
            } else {
                null
            }

            val streakCount = if (style.wind >= 0.8f) 14 else 0
            val streaks = FloatArray(streakCount * STREAK_STRIDE).also { a ->
                for (i in 0 until streakCount) {
                    val o = i * STREAK_STRIDE
                    a[o] = 0.08f + random.nextFloat() * 0.75f
                    a[o + 1] = random.nextFloat()
                    a[o + 2] = 0.2f + random.nextFloat() * 0.35f
                    a[o + 3] = 0.35f + random.nextFloat() * 0.5f
                    a[o + 4] = 0.10f + random.nextFloat() * 0.18f
                }
            }

            val horizon = if (style.daytime && style.goldenStrength >= 0.45f) {
                val deck = CloudLayer(91, HORIZON_Y - 0.08f, 0.32f, 1.5f, 0.005f, 1f, 0.6f, 0.55f, 0.9f, topFade = true)
                deck to CloudTextures.get(deck, Color(0xFFD9C2CF), Color(0xFF4E3F5E))
            } else {
                null
            }
            return SkyAssets(style, clouds, fog, stars, milkyWay, horizon, drops, flakes, streaks)
        }
    }
}

/** Where the sun or the moon sits: top end, clear of the location name and the temperature below. */
private const val LIGHT_X = 0.80f
private const val LIGHT_Y = 0.055f

/** Just the sky gradient, while a sky's assets are still being made. */
internal fun DrawScope.drawSkyGradient(style: SkyStyle, alpha: Float = 1f) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to style.top,
            0.55f to style.middle,
            1f to style.bottom
        ),
        alpha = alpha
    )
}

/**
 * Light of the moment, from the sun and moon positions (or just day/night when they are unknown).
 *
 * @param dayAmount 0 night, 1 day; in between during twilight, where both skies blend
 * @param golden 0..1 around sunrise and sunset: warm horizon, violet dusk, sun-lit cloud edges
 * @param sunAlpha 0 once the sun has set
 * @param moonAlpha 0 when the moon is below the horizon
 * @param moonPhase -180 (new, waxing) .. 0 (full) .. 180 (new, waning)
 * @param southern lit side mirrored in the southern hemisphere
 */
internal class SkyLight(
    val dayAmount: Float,
    val golden: Float,
    val sunAlpha: Float,
    val moonAlpha: Float,
    val moonPhase: Float,
    val southern: Boolean,
    /** Degrees above the horizon, null when unknown (the sun then stays high) */
    val sunAltitude: Float? = null,
    /** Northern or southern lights tonight, 0..1 */
    val aurora: Float = 0f,
)

private val SUNLIT_CLOUD = Color(0xFFFFA98A)
private val AFTERGLOW_CLOUD = Color(0xFFFF7FA6)
private val SUNSET_SUN = Color(0xFFFFB070)

/** Where the horizon would be: behind the cards, so a setting sun shows through their glass. */
private const val HORIZON_Y = 0.55f

/** One step of the sunset: sky top, middle and horizon colors, and the sun's color, at an altitude. */
private class SunsetKey(val altitude: Float, val top: Color, val middle: Color, val bottom: Color, val sun: Color)

private val SUNSET_KEYS = listOf(
    SunsetKey(12f, Color(0xFF3F74C4), Color(0xFF8FB0D8), Color(0xFFF6D6AE), Color(0xFFFFF0CD)),
    SunsetKey(6f, Color(0xFF4470BC), Color(0xFFD9A994), Color(0xFFFFC98A), Color(0xFFFFE2B0)),
    SunsetKey(1f, Color(0xFF3C5094), Color(0xFFDE806E), Color(0xFFFFA05C), Color(0xFFFFAF5F)),
    SunsetKey(-3f, Color(0xFF2C306E), Color(0xFFA05088), Color(0xFFF27A68), Color(0xFFFF7D46)),
    SunsetKey(-8f, Color(0xFF181A46), Color(0xFF502E70), Color(0xFF924E7C), Color(0xFFCD5FA5)),
)

/** The sunset palette at an altitude, blended between its steps. */
private fun sunsetAt(altitude: Float): SunsetKey {
    val keys = SUNSET_KEYS
    if (altitude >= keys.first().altitude) return keys.first()
    for (i in 0 until keys.size - 1) {
        val a = keys[i]
        val b = keys[i + 1]
        if (altitude >= b.altitude) {
            val f = (a.altitude - altitude) / (a.altitude - b.altitude)
            return SunsetKey(
                altitude,
                lerp(a.top, b.top, f),
                lerp(a.middle, b.middle, f),
                lerp(a.bottom, b.bottom, f),
                lerp(a.sun, b.sun, f)
            )
        }
    }
    return keys.last()
}

/** How far the sun has sunk from its usual place towards the horizon, 0..1. */
private fun sunSink(altitude: Float?): Float = altitude?.let { smoothstep(6f, -1f, it) } ?: 0f

/** Sky gradient and horizon glow. */
internal fun DrawScope.drawSkyBackground(assets: SkyAssets, alpha: Float) {
    if (alpha <= 0f) return
    val style = assets.style
    drawSkyGradient(style, alpha)
    if (style.horizonGlow.alpha > 0f) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(style.horizonGlow, Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 1.05f),
                radius = size.height * 0.75f
            ),
            alpha = alpha
        )
    }
}

/**
 * Sunrise / sunset over the gradients, under the clouds: one continuous palette that follows the
 * sun's altitude, from warm afternoon light through orange and pink to violet dusk, with a glow
 * on the horizon under the sun.
 */
internal fun DrawScope.drawGoldenHour(golden: Float, altitude: Float?) {
    if (golden <= 0.01f) return
    val w = size.width
    val h = size.height
    val key = sunsetAt(altitude ?: 2f)
    val a = golden.coerceIn(0f, 1f)
    drawRect(
        brush = Brush.verticalGradient(
            0f to key.top,
            0.3f to key.middle,
            HORIZON_Y to key.bottom,
            1f to lerp(key.bottom, key.middle, 0.35f)
        ),
        alpha = a
    )
    drawRect(
        brush = Brush.radialGradient(
            0f to key.sun.copy(alpha = 0.7f),
            0.4f to key.sun.copy(alpha = 0.22f),
            1f to Color.Transparent,
            center = Offset(w * LIGHT_X, h * HORIZON_Y),
            radius = h * 0.55f
        ),
        alpha = a
    )
}

/**
 * Everything over the gradient: stars, sun or moon, clouds, fog, header shade, weather in the air.
 *
 * @param time seconds
 * @param scroll 0 at rest, 1 once the page has scrolled past the header
 * @param alpha opacity of these effects (cross-fades between skies, day/night blend)
 * @param warmth sun-lit tint of the clouds (sunrise/sunset)
 * @param tilt how the phone is tilted, -1..1 on each axis: nearer layers shift more (depth)
 * @param slide clouds drifting in or out when the weather changes, in screen widths
 */
internal fun DrawScope.drawSkyEffects(
    assets: SkyAssets,
    light: SkyLight,
    time: Float,
    scroll: Float,
    alpha: Float,
    warmth: Float,
    tilt: Offset = Offset.Zero,
    slide: Float = 0f,
) {
    if (alpha <= 0.002f) return
    val style = assets.style
    val w = size.width
    val h = size.height
    val parallax = scroll * h * 0.12f
    // Each cloud layer shifts with the tilt by its depth, and drifts with the weather change
    fun shiftOf(layer: CloudLayer) = Offset(
        tilt.x * 16f * density * layer.parallax + slide * w * (0.6f + 0.6f * layer.parallax),
        tilt.y * 10f * density * layer.parallax
    )

    if (style.starOpacity > 0f) {
        // A bright moon washes out the faintest stars and the Milky Way
        val moonLight = light.moonAlpha * moonFraction(light.moonPhase)
        val starAlpha = style.starOpacity * alpha * (1f - 0.3f * moonLight)
        // The whole star field turns very slowly about a point below the screen, and barely tilts (it is far)
        translate(left = tilt.x * 3f * density, top = tilt.y * 2f * density) {
            rotate(degrees = sin(time * 0.02f) * 1.7f, pivot = Offset(w * 0.5f, h * 1.2f)) {
                assets.milkyWay?.let { drawMilkyWay(it, starAlpha * (1f - 0.35f * moonLight), parallax * 0.3f) }
                drawStars(assets.stars, time, starAlpha, parallax * 0.4f)
                if (style.starOpacity >= 0.7f) drawShootingStar(time, starAlpha, parallax * 0.4f)
            }
        }
    }
    if (!style.daytime && light.aurora > 0f && style.starOpacity > 0f) {
        drawAurora(time, light.aurora * alpha * style.starOpacity, parallax * 0.4f)
    }
    // The sun sinks from its usual place towards the horizon as it sets; the moon stays put
    val sink = if (style.daytime) sunSink(light.sunAltitude) else 0f
    val lightCenter = Offset(w * (LIGHT_X - 0.18f * sink), h * (LIGHT_Y + (HORIZON_Y - LIGHT_Y) * sink) - parallax * 0.6f) +
        tilt * (6f * density)
    // On its way down the sun dims over the whole header area (it can be tall with large text), until it
    // goes behind the cards, so the location and temperature stay readable at any sun height
    val behindHeader = if (sink > 0f) smoothstep(0.05f, 0.1f, lightCenter.y / h) * (1f - smoothstep(0.42f, 0.5f, lightCenter.y / h)) else 0f
    val moonGlow = if (!style.daytime && style.lightOpacity > 0f) alpha * light.moonAlpha * moonFraction(light.moonPhase) else 0f
    if (style.lightOpacity > 0f) {
        if (style.daytime) {
            if (light.sunAlpha > 0f) {
                val flatten = light.sunAltitude?.let { smoothstep(6f, 0f, it) } ?: 0f
                drawSun(lightCenter, style, time, style.lightOpacity * alpha * light.sunAlpha * (1f - 0.45f * sink) * (1f - 0.8f * behindHeader), light.golden, flatten)
            }
        } else if (light.moonAlpha > 0f) {
            drawMoon(lightCenter, style, style.lightOpacity * alpha * light.moonAlpha, light.moonPhase, light.southern)
        }
    }

    val cloudSpeed = 1f + style.wind * 0.6f
    // Sun-lit clouds: orange while the sun is up, pink afterglow once it has set
    val afterglow = light.sunAltitude?.let { smoothstep(2f, -4f, it) } ?: 0f
    val sunlit = if (warmth > 0.01f) ColorFilter.tint(lerp(SUNLIT_CLOUD, AFTERGLOW_CLOUD, afterglow), BlendMode.SrcIn) else null
    // Lightning flashes behind the clouds, glowing through their gaps
    val strikes = if (style.lightning) strikesAt(time, style.lightningBolts) else emptyList()
    val flashing = strikes.any { it.brightness(time) > 0.01f }
    if (flashing) drawStrikeGlow(strikes, time, alpha)
    // Clouds passing the moon get a silver lining, and a flash lights them from inside: the clouds
    // go into a layer, then are lit only where they are
    val silver = moonGlow > 0.02f && assets.clouds.isNotEmpty()
    val litClouds = silver || (flashing && assets.clouds.isNotEmpty())
    if (litClouds) drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
    // On aurora nights the sky is clear: the clouds thin out
    val clearing = if (!style.daytime) 1f - 0.7f * light.aurora.coerceIn(0f, 1f) else 1f
    assets.clouds.forEach { (layer, texture) ->
        val layerAlpha = alpha * layer.opacity * clearing
        drawCloudBand(texture, layer, time, layerAlpha, parallax * layer.parallax, cloudSpeed, shift = shiftOf(layer))
        if (sunlit != null) {
            drawCloudBand(texture, layer, time, layerAlpha * warmth * 0.7f, parallax * layer.parallax, cloudSpeed, sunlit, shift = shiftOf(layer))
        }
    }
    if (flashing && litClouds) drawStrikeInClouds(strikes, time, alpha)
    if (litClouds && !silver) drawContext.canvas.restore()
    if (silver) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFF8F0FF).copy(alpha = 0.6f),
                0.35f to Color(0xFFE8DCFF).copy(alpha = 0.22f),
                1f to Color.Transparent,
                center = lightCenter,
                radius = w * 0.6f
            ),
            radius = w * 0.6f,
            center = lightCenter,
            alpha = moonGlow.coerceIn(0f, 1f),
            blendMode = BlendMode.SrcAtop
        )
        drawContext.canvas.restore()
    }

    // At sunrise and sunset a low cloud deck lines the horizon: lit from below by the sun on its
    // upper edge, dark underneath, and the sun sinks behind it
    val horizonDeck = assets.horizon?.takeIf { warmth > 0.01f }
    val deckKey = sunsetAt(light.sunAltitude ?: 2f)
    if (horizonDeck != null) {
        val (layer, texture) = horizonDeck
        val deckTop = h * layer.top - parallax * layer.parallax
        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawCloudBand(texture, layer, time, alpha * warmth.coerceIn(0f, 1f), parallax * layer.parallax, cloudSpeed, shift = shiftOf(layer))
        drawRect(
            brush = Brush.verticalGradient(
                0f to lerp(deckKey.bottom, deckKey.sun, 0.5f),
                1f to lerp(deckKey.middle, Color(0xFF141028), 0.6f),
                startY = deckTop + h * layer.height * 0.15f,
                endY = deckTop + h * layer.height * 0.75f
            ),
            alpha = 0.85f,
            blendMode = BlendMode.SrcAtop
        )
        drawContext.canvas.restore()
    }

    // The sun's light through gaps in the clouds, and a lens flare while it is not hidden
    if (style.daytime && style.lightOpacity > 0f && light.sunAlpha > 0f && (assets.clouds.isNotEmpty() || horizonDeck != null)) {
        val strength = style.lightOpacity * alpha * light.sunAlpha
        val color = lerp(Color(0xFFFFF0CD), deckKey.sun, light.golden.coerceIn(0f, 1f))
        drawLightShafts(lightCenter, w * (0.62f + 0.15f * light.golden), color, strength * (0.45f - 0.2f * sink) * (1f - 0.7f * behindHeader)) { mode ->
            assets.clouds.forEach { (layer, texture) ->
                drawCloudBand(texture, layer, time, layer.opacity, parallax * layer.parallax, cloudSpeed, blendMode = mode, shift = shiftOf(layer))
            }
            horizonDeck?.let { (layer, texture) ->
                drawCloudBand(texture, layer, time, warmth.coerceIn(0f, 1f), parallax * layer.parallax, cloudSpeed, blendMode = mode, shift = shiftOf(layer))
            }
        }
        val behindDeck = horizonDeck?.let { smoothstep(HORIZON_Y - 0.07f, HORIZON_Y - 0.01f, lightCenter.y / h) * warmth } ?: 0f
        val open = (1f - cloudCover(assets, time, lightCenter, parallax, cloudSpeed, ::shiftOf)) * (1f - behindDeck.coerceIn(0f, 1f))
        drawLensFlare(lightCenter, strength * open * 0.6f)
    }

    if (style.fog > 0f) {
        assets.fog.forEach { (layer, texture) ->
            drawCloudBand(texture, layer, time, alpha * style.fog * 0.85f, parallax * layer.parallax, 1f, shift = shiftOf(layer))
        }
        drawRect(
            brush = Brush.verticalGradient(
                0.15f to Color.Transparent,
                1f to style.fogColor.copy(alpha = 0.6f * style.fog)
            ),
            alpha = alpha
        )
    }

    // Shade behind the header, so white text reads even over bright clouds or a low sun
    val sunGlare = if (style.daytime) style.lightOpacity * light.sunAlpha else 0f
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = if (style.daytime) 0.2f + 0.08f * sunGlare else 0.1f),
            0.3f to Color.Black.copy(alpha = if (style.daytime) 0.1f + 0.06f * sunGlare else 0.04f),
            0.5f to Color.Transparent
        ),
        alpha = alpha
    )

    // Weather in the air fades a little once the cards scroll over it
    val airAlpha = alpha * (1f - 0.5f * scroll)
    if (strikes.isNotEmpty()) drawStrikeBolts(strikes, time, alpha)
    if (assets.drops.isNotEmpty()) {
        drawDrops(assets.drops, time, style, airAlpha, hail = style.precipitation == Precipitation.HAIL)
    }
    assets.flakes?.let { drawSnowfall(it, time, style.wind, airAlpha, parallax) }
    if (assets.streaks.isNotEmpty()) drawStreaks(assets.streaks, time, airAlpha)
}

/** Lit fraction of the disc: 0 new moon, 1 full moon. */
internal fun moonFraction(phase: Float): Float = ((1.0 + cos(Math.toRadians(phase.toDouble()))) / 2.0).toFloat()

private fun DrawScope.drawCloudBand(
    texture: ImageBitmap,
    layer: CloudLayer,
    time: Float,
    alpha: Float,
    lift: Float,
    speedFactor: Float,
    colorFilter: ColorFilter? = null,
    blendMode: BlendMode = BlendMode.SrcOver,
    shift: Offset = Offset.Zero,
) {
    if (alpha <= 0.002f) return
    val tileWidth = (size.width * layer.widthScale).roundToInt().coerceAtLeast(1)
    val bandHeight = (size.height * layer.height).roundToInt()
    val top = (size.height * layer.top - lift + shift.y).roundToInt()
    val travel = ((time * layer.speed * speedFactor * size.width - shift.x) % tileWidth + tileWidth) % tileWidth
    var x = -travel.roundToInt()
    while (x < size.width) {
        drawImage(
            image = texture,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(texture.width, texture.height),
            // One extra pixel hides the seam between tiles
            dstOffset = IntOffset(x, top),
            dstSize = IntSize(tileWidth + 1, bandHeight),
            alpha = alpha.coerceIn(0f, 1f),
            colorFilter = colorFilter,
            blendMode = blendMode,
            filterQuality = FilterQuality.Low
        )
        x += tileWidth
    }
}

/**
 * How much cloud is in front of a point on screen, 0 clear to 1 hidden: the cloud textures are
 * sampled exactly where [drawCloudBand] puts them.
 */
private fun DrawScope.cloudCover(
    assets: SkyAssets,
    time: Float,
    point: Offset,
    lift: Float,
    speedFactor: Float,
    shiftOf: (CloudLayer) -> Offset,
): Float {
    var open = 1f
    assets.clouds.forEach { (layer, texture) ->
        val shift = shiftOf(layer)
        val tileWidth = (size.width * layer.widthScale).roundToInt().coerceAtLeast(1)
        val bandHeight = (size.height * layer.height).roundToInt().coerceAtLeast(1)
        val top = (size.height * layer.top - lift * layer.parallax + shift.y).roundToInt()
        if (point.y < top || point.y >= top + bandHeight) return@forEach
        val travel = time * layer.speed * speedFactor * size.width - shift.x
        val u = (((point.x + travel) % tileWidth) + tileWidth) % tileWidth / tileWidth
        val bitmap = texture.asAndroidBitmap()
        val px = (u * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val py = ((point.y - top) / bandHeight * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        open *= 1f - (bitmap.getPixel(px, py) ushr 24) / 255f * layer.opacity
    }
    return 1f - open
}

/** Real stars are not all white: blue-white, white, warm, a touch of violet. */
private val STAR_COLORS = listOf(
    Color.White, Color(0xFFCFE0FF), Color(0xFFFFE9D6), Color(0xFFE6DAFF), Color(0xFFBFD3FF), Color.White
)

private fun DrawScope.drawStars(stars: FloatArray, time: Float, opacity: Float, lift: Float) {
    val w = size.width
    val h = size.height
    var i = 0
    while (i < stars.size) {
        val radius = stars[i + 2] * density
        val twinkle = 0.55f + 0.45f * sin(time * stars[i + 4] + stars[i + 3])
        val center = Offset(stars[i] * w, stars[i + 1] * h - lift)
        val alpha = (opacity * twinkle * if (stars[i + 2] > 1.4f) 1f else 0.75f).coerceIn(0f, 1f)
        val color = STAR_COLORS[stars[i + 5].toInt()]
        drawCircle(color, radius, center, alpha)
        if (stars[i + 2] > 1.4f) {
            // Diffraction spikes on the brightest
            val spike = radius * 5f
            val thin = 0.6f * density
            drawLine(color, center - Offset(spike, 0f), center + Offset(spike, 0f), thin, alpha = alpha * 0.45f)
            drawLine(color, center - Offset(0f, spike), center + Offset(0f, spike), thin, alpha = alpha * 0.45f)
            // Soft glow, fading out (a flat translucent disc would read as a grey blob)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = radius * 4f
                ),
                radius = radius * 4f,
                center = center,
                alpha = alpha
            )
        }
        i += SkyAssets.STAR_STRIDE
    }
}

/** @param flatten 0..1 near the horizon, where the thick air squashes the sun's disc */
private fun DrawScope.drawSun(center: Offset, style: SkyStyle, time: Float, opacity: Float, golden: Float, flatten: Float) {
    if (flatten > 0.01f) {
        scale(scaleX = 1f, scaleY = 1f - 0.22f * flatten, pivot = center) { drawSun(center, style, time, opacity, golden, 0f) }
        return
    }
    val w = size.width
    // Warmer and bigger as the sun gets low
    val glowColor = lerp(style.lightGlow, SUNSET_SUN, golden.coerceIn(0f, 1f))
    val bloom = w * (0.7f + 0.25f * golden)
    val breathe = 1f + 0.03f * sin(time * 0.5f)
    // Wide bloom filling the top of the sky, then a bright soft core
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.38f), glowColor.copy(alpha = 0.08f), Color.Transparent),
            center = center,
            radius = bloom * breathe
        ),
        radius = bloom * breathe,
        center = center,
        alpha = opacity
    )
    val core = 34f * density
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White,
            0.45f to Color.White.copy(alpha = 0.95f),
            1f to glowColor.copy(alpha = 0f),
            center = center,
            radius = core * 1.6f
        ),
        radius = core * 1.6f,
        center = center,
        alpha = opacity
    )
}

/**
 * Moon in its current phase: the lit part is a half disc closed by the terminator, an ellipse whose
 * width follows the phase; the dark part stays faintly visible (earthshine).
 */
private fun DrawScope.drawMoon(center: Offset, style: SkyStyle, opacity: Float, phase: Float, southern: Boolean) {
    val radius = 21f * density
    val fraction = moonFraction(phase)
    // Glow follows how much of the moon is lit
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(style.lightGlow.copy(alpha = 0.5f), style.lightGlow.copy(alpha = 0.12f), Color.Transparent),
            center = center,
            radius = radius * 8f
        ),
        radius = radius * 8f,
        center = center,
        alpha = opacity * (0.2f + 0.8f * fraction)
    )
    drawMoonHalo(center, radius, opacity * fraction)
    drawMoonDisc(center, radius, phase, southern, opacity)
}

/**
 * The moon disc alone, in its phase: dark part faintly visible (earthshine), lit part closed by the
 * terminator. Shared by the sky and the Moon tile.
 *
 * @param phase -180 (new, waxing) .. 0 (full) .. 180 (new, waning)
 */
internal fun DrawScope.drawMoonDisc(center: Offset, radius: Float, phase: Float, southern: Boolean, opacity: Float = 1f) {
    drawCircle(Color(0xFF2A2547), radius, center, opacity * 0.55f)
    drawCircle(Color(0xFFB9B2D6), radius, center, opacity * 0.08f)
    // Earthshine: the dark part shows its seas very faintly
    drawMoonSurface(center, radius, opacity * 0.12f)

    clipPath(moonLitPath(center, radius, phase, southern)) {
        drawMoonSurface(center, radius, opacity)
    }
}

/** The baked surface (seas, craters, limb darkening), fully lit. */
private fun DrawScope.drawMoonSurface(center: Offset, radius: Float, alpha: Float) {
    val surface = NightSky.moonSurface
    drawImage(
        image = surface,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(surface.width, surface.height),
        dstOffset = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()),
        dstSize = IntSize((radius * 2f).roundToInt(), (radius * 2f).roundToInt()),
        alpha = alpha.coerceIn(0f, 1f),
        filterQuality = FilterQuality.Medium
    )
}

private fun moonLitPath(center: Offset, radius: Float, phase: Float, southern: Boolean): Path {
    val disc = Rect(center, radius)
    // Waxing (phase < 0): lit on the right in the northern hemisphere
    val litOnRight = (phase < 0f) != southern
    // 1 full, 0 quarter, -1 new: half-width of the terminator ellipse
    val k = cos(Math.toRadians(phase.toDouble())).toFloat()
    val rx = radius * abs(k)
    val terminator = Rect(center.x - rx, center.y - radius, center.x + rx, center.y + radius)
    val gibbous = k > 0f
    return Path().apply {
        if (litOnRight) {
            // Top, right edge, bottom; then back up along the terminator
            arcTo(disc, -90f, 180f, true)
            arcTo(terminator, 90f, if (gibbous) 180f else -180f, false)
        } else {
            // Bottom, left edge, top; then back down along the terminator
            arcTo(disc, 90f, 180f, true)
            arcTo(terminator, 270f, if (gibbous) 180f else -180f, false)
        }
        close()
    }
}

private fun DrawScope.drawDrops(drops: FloatArray, time: Float, style: SkyStyle, opacity: Float, hail: Boolean) {
    val w = size.width
    val h = size.height
    val slant = 0.08f + style.wind * 0.22f
    val color = style.precipitationColor
    var i = 0
    while (i < drops.size) {
        val length = drops[i + 3] * density
        val travel = (drops[i + 1] + time * drops[i + 2]) % 1f
        val y = travel * (h + length * 2f) - length
        val x = ((drops[i] * w + y * slant) % w + w) % w
        // Direction of fall, normalized, times the streak length
        val norm = 1f / kotlin.math.sqrt(1f + slant * slant)
        val dx = slant * norm * length
        val dy = norm * length
        drawLine(
            color = color,
            start = Offset(x - dx, y - dy),
            end = Offset(x, y),
            strokeWidth = drops[i + 4] * density,
            cap = StrokeCap.Round,
            alpha = (drops[i + 5] * opacity * if (hail) 1f else 1.1f).coerceIn(0f, 1f)
        )
        i += SkyAssets.DROP_STRIDE
    }
}

private fun DrawScope.drawStreaks(streaks: FloatArray, time: Float, opacity: Float) {
    val w = size.width
    val h = size.height
    var i = 0
    while (i < streaks.size) {
        val length = streaks[i + 2] * w
        val travel = (streaks[i + 1] + time * streaks[i + 3] * w / (w + length * 2f)) % 1f
        val start = travel * (w + length * 2f) - length * 2f
        val y = streaks[i] * h + sin(time * 0.8f + streaks[i + 1] * 6f) * 6f * density
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = streaks[i + 4]), Color.Transparent),
                startX = start,
                endX = start + length
            ),
            start = Offset(start, y),
            end = Offset(start + length, y),
            strokeWidth = 1.4f * density,
            cap = StrokeCap.Round,
            alpha = opacity
        )
        i += SkyAssets.STREAK_STRIDE
    }
}
