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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    /** x, y, radius (dp), phase, twinkle speed */
    val stars: FloatArray,
    /** x, phase, speed (screen heights/s), length (dp), width (dp), alpha */
    val drops: FloatArray,
    /** x, phase, speed (screen heights/s), radius (dp), sway (dp), sway frequency, alpha */
    val flakes: FloatArray,
    /** y, phase, length (screen widths), speed (screen widths/s), alpha */
    val streaks: FloatArray,
) {
    companion object {
        const val STAR_STRIDE = 5
        const val DROP_STRIDE = 6
        const val FLAKE_STRIDE = 7
        const val STREAK_STRIDE = 5

        /** Heavy work (cloud textures): call off the main thread. */
        fun create(style: SkyStyle): SkyAssets {
            val random = Random(style.weatherKind * 31 + if (style.daytime) 1 else 0)
            val clouds = style.clouds.map { it to CloudTextures.get(it, style.cloudLight, style.cloudShadow) }
            val fog = if (style.fog > 0f) {
                listOf(
                    CloudLayer(71, 0.30f, 0.55f, 2.3f, 0.006f, 1f, 0.7f, true, 0.3f),
                    CloudLayer(83, 0.52f, 0.62f, 1.9f, 0.010f, 1f, 0.85f, true, 0.5f)
                ).map { it to CloudTextures.get(it, style.fogColor, style.fogColor, shaded = false) }
            } else {
                emptyList()
            }

            val starCount = if (style.starOpacity > 0f) 150 else 0
            val stars = FloatArray(starCount * STAR_STRIDE) { 0f }.also { a ->
                for (i in 0 until starCount) {
                    val o = i * STAR_STRIDE
                    a[o] = random.nextFloat()
                    // Mostly high in the sky, thinning out towards the horizon
                    a[o + 1] = random.nextFloat().let { it * it } * 0.7f
                    a[o + 2] = if (random.nextFloat() < 0.08f) 1.5f + random.nextFloat() * 0.6f else 0.5f + random.nextFloat() * 0.8f
                    a[o + 3] = random.nextFloat() * 2f * PI.toFloat()
                    a[o + 4] = 0.6f + random.nextFloat() * 2.2f
                }
            }

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

            val snow = style.precipitation == Precipitation.SNOW || style.precipitation == Precipitation.SLEET
            val flakeCount = if (snow) {
                (170 * style.precipitationIntensity * if (style.precipitation == Precipitation.SLEET) 0.45f else 1f).toInt()
            } else {
                0
            }
            val sleet = style.precipitation == Precipitation.SLEET
            val flakes = FloatArray(flakeCount * FLAKE_STRIDE).also { a ->
                for (i in 0 until flakeCount) {
                    val o = i * FLAKE_STRIDE
                    val depth = random.nextFloat()
                    a[o] = random.nextFloat()
                    a[o + 1] = random.nextFloat()
                    a[o + 2] = (0.05f + depth * 0.1f) * if (sleet) 2.5f else 1f
                    a[o + 3] = 1.2f + depth * 2.8f
                    a[o + 4] = 6f + depth * 20f
                    a[o + 5] = 0.4f + random.nextFloat() * 0.8f
                    a[o + 6] = 0.45f + depth * 0.5f
                }
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

            return SkyAssets(style, clouds, fog, stars, drops, flakes, streaks)
        }
    }
}

/** Where the sun or the moon sits: top end, clear of the location name and the temperature below. */
private const val LIGHT_X = 0.80f
private const val LIGHT_Y = 0.085f

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
)

private val SUNSET_HORIZON = Color(0xFFFF8A4C)
private val SUNSET_PINK = Color(0xFFF0607A)
private val DUSK_VIOLET = Color(0xFF5B3C8C)
private val SUNLIT_CLOUD = Color(0xFFFFA98A)
private val SUNSET_SUN = Color(0xFFFFB070)

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

/** Sunrise / sunset colors over the gradients, under the clouds. */
internal fun DrawScope.drawGoldenHour(golden: Float) {
    if (golden <= 0.01f) return
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.radialGradient(
            0f to SUNSET_HORIZON.copy(alpha = 0.85f),
            0.45f to SUNSET_PINK.copy(alpha = 0.45f),
            1f to Color.Transparent,
            center = Offset(w * 0.5f, h * 1.02f),
            radius = h * 0.95f
        ),
        alpha = golden.coerceIn(0f, 1f)
    )
    // Violet high up, pink then orange towards the horizon: visible above the cards, not only under them
    drawRect(
        brush = Brush.verticalGradient(
            0f to DUSK_VIOLET.copy(alpha = 0.45f),
            0.28f to SUNSET_PINK.copy(alpha = 0.32f),
            0.6f to SUNSET_HORIZON.copy(alpha = 0.5f),
            1f to SUNSET_HORIZON.copy(alpha = 0.7f)
        ),
        alpha = golden.coerceIn(0f, 1f)
    )
}

/**
 * Everything over the gradient: stars, sun or moon, clouds, fog, header shade, weather in the air.
 *
 * @param time seconds
 * @param scroll 0 at rest, 1 once the page has scrolled past the header
 * @param alpha opacity of these effects (cross-fades between skies, day/night blend)
 * @param warmth sun-lit tint of the clouds (sunrise/sunset)
 */
internal fun DrawScope.drawSkyEffects(
    assets: SkyAssets,
    light: SkyLight,
    time: Float,
    scroll: Float,
    alpha: Float,
    warmth: Float,
) {
    if (alpha <= 0.002f) return
    val style = assets.style
    val w = size.width
    val h = size.height
    val parallax = scroll * h * 0.08f

    if (style.starOpacity > 0f) {
        // A bright moon washes out the faintest stars
        val moonWash = 1f - 0.3f * light.moonAlpha * moonFraction(light.moonPhase)
        drawStars(assets.stars, time, style.starOpacity * alpha * moonWash, parallax * 0.4f)
    }
    if (style.lightOpacity > 0f) {
        val center = Offset(w * LIGHT_X, h * LIGHT_Y - parallax * 0.6f)
        if (style.daytime) {
            if (light.sunAlpha > 0f) drawSun(center, style, time, style.lightOpacity * alpha * light.sunAlpha, light.golden)
        } else if (light.moonAlpha > 0f) {
            drawMoon(center, style, style.lightOpacity * alpha * light.moonAlpha, light.moonPhase, light.southern)
        }
    }

    val cloudSpeed = 1f + style.wind * 0.6f
    val sunlit = if (warmth > 0.01f) ColorFilter.tint(SUNLIT_CLOUD, BlendMode.SrcIn) else null
    assets.clouds.forEach { (layer, texture) ->
        val layerAlpha = alpha * layer.opacity
        drawCloudBand(texture, layer, time, layerAlpha, parallax * layer.parallax, cloudSpeed)
        if (sunlit != null) {
            drawCloudBand(texture, layer, time, layerAlpha * warmth * 0.7f, parallax * layer.parallax, cloudSpeed, sunlit)
        }
    }

    if (style.fog > 0f) {
        assets.fog.forEach { (layer, texture) ->
            drawCloudBand(texture, layer, time, alpha * style.fog * 0.85f, parallax * layer.parallax, 1f)
        }
        drawRect(
            brush = Brush.verticalGradient(
                0.15f to Color.Transparent,
                1f to style.fogColor.copy(alpha = 0.6f * style.fog)
            ),
            alpha = alpha
        )
    }

    // Gentle shade behind the header, so white text reads even over bright clouds
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = if (style.daytime) 0.2f else 0.1f),
            0.45f to Color.Transparent
        ),
        alpha = alpha
    )

    // Weather in the air fades a little once the cards scroll over it
    val airAlpha = alpha * (1f - 0.5f * scroll)
    if (style.lightning) drawLightning(style, time, alpha)
    if (assets.drops.isNotEmpty()) {
        drawDrops(assets.drops, time, style, airAlpha, hail = style.precipitation == Precipitation.HAIL)
    }
    if (assets.flakes.isNotEmpty()) drawFlakes(assets.flakes, time, style.wind, airAlpha)
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
) {
    if (alpha <= 0.002f) return
    val tileWidth = (size.width * layer.widthScale).roundToInt().coerceAtLeast(1)
    val bandHeight = (size.height * layer.height).roundToInt()
    val top = (size.height * layer.top - lift).roundToInt()
    val travel = (time * layer.speed * speedFactor * size.width) % tileWidth
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
            filterQuality = FilterQuality.Low
        )
        x += tileWidth
    }
}

private fun DrawScope.drawStars(stars: FloatArray, time: Float, opacity: Float, lift: Float) {
    val w = size.width
    val h = size.height
    var i = 0
    while (i < stars.size) {
        val radius = stars[i + 2] * density
        val twinkle = 0.55f + 0.45f * sin(time * stars[i + 4] + stars[i + 3])
        val center = Offset(stars[i] * w, stars[i + 1] * h - lift)
        val alpha = (opacity * twinkle * if (stars[i + 2] > 1.4f) 1f else 0.75f).coerceIn(0f, 1f)
        drawCircle(Color.White, radius, center, alpha)
        if (stars[i + 2] > 1.4f) {
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

private fun DrawScope.drawSun(center: Offset, style: SkyStyle, time: Float, opacity: Float, golden: Float) {
    val w = size.width
    // Warmer and bigger as the sun gets low
    val glowColor = lerp(style.lightGlow, SUNSET_SUN, golden.coerceIn(0f, 1f))
    val bloom = w * (0.95f + 0.25f * golden)
    val breathe = 1f + 0.03f * sin(time * 0.5f)
    // Wide bloom filling the top of the sky, then a bright soft core
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.55f), glowColor.copy(alpha = 0.12f), Color.Transparent),
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
    // Earthshine
    drawCircle(Color(0xFF2A2547), radius, center, opacity * 0.55f)
    drawCircle(Color(0xFFB9B2D6), radius, center, opacity * 0.08f)

    clipPath(moonLitPath(center, radius, phase, southern)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFDF6), Color(0xFFE6E0F4)),
                center = center + Offset(-radius * 0.3f, -radius * 0.3f),
                radius = radius * 1.4f
            ),
            radius = radius,
            center = center,
            alpha = opacity
        )
        // A few soft "seas" give it a face
        val sea = Color(0xFF9C95B5)
        drawCircle(sea, radius * 0.32f, center + Offset(radius * 0.25f, -radius * 0.2f), opacity * 0.18f)
        drawCircle(sea, radius * 0.22f, center + Offset(-radius * 0.3f, radius * 0.25f), opacity * 0.14f)
        drawCircle(sea, radius * 0.15f, center + Offset(radius * 0.05f, radius * 0.45f), opacity * 0.12f)
    }
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

private fun DrawScope.drawFlakes(flakes: FloatArray, time: Float, wind: Float, opacity: Float) {
    val w = size.width
    val h = size.height
    val margin = 12f * density
    var i = 0
    while (i < flakes.size) {
        val radius = flakes[i + 3] * density
        val travel = (flakes[i + 1] + time * flakes[i + 2]) % 1f
        val y = travel * (h + margin * 2f) - margin
        val sway = sin(time * flakes[i + 5] + flakes[i + 1] * 2f * PI.toFloat()) * flakes[i + 4] * density
        val x = ((flakes[i] * w + sway + time * wind * 24f * density) % w + w) % w
        val alpha = (flakes[i + 6] * opacity).coerceIn(0f, 1f)
        drawCircle(Color.White, radius, Offset(x, y), alpha)
        if (flakes[i + 3] > 2.8f) drawCircle(Color.White, radius * 1.6f, Offset(x, y), alpha * 0.15f)
        i += SkyAssets.FLAKE_STRIDE
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

/** A strike every few seconds: a double flash lighting the whole sky, sometimes with a visible bolt. */
private fun DrawScope.drawLightning(style: SkyStyle, time: Float, opacity: Float) {
    val period = 7f
    val cycle = floor(time / period).toInt()
    val random = Random(cycle * 7919 + 13)
    val strikeAt = cycle * period + 1.5f + random.nextFloat() * 4f
    val dt = time - strikeAt
    if (dt < 0f || dt > 1.2f) return
    val flash = (exp(-dt * 9f) + if (dt > 0.18f) 0.75f * exp(-(dt - 0.18f) * 8f) else 0f).coerceIn(0f, 1f)

    if (style.lightningBolts && random.nextFloat() < 0.75f) {
        val path = Path()
        var x = size.width * (0.15f + random.nextFloat() * 0.7f)
        var y = -size.height * 0.02f
        val bottom = size.height * (0.32f + random.nextFloat() * 0.18f)
        val step = (bottom - y) / 11f
        path.moveTo(x, y)
        var branch: Path? = null
        for (segment in 0 until 11) {
            x += (random.nextFloat() - 0.5f) * size.width * 0.07f
            y += step * (0.7f + random.nextFloat() * 0.6f)
            path.lineTo(x, y)
            if (segment == 4) {
                branch = Path().apply {
                    moveTo(x, y)
                    var bx = x
                    var by = y
                    repeat(4) {
                        bx += size.width * (0.02f + random.nextFloat() * 0.04f)
                        by += step * 0.8f
                        lineTo(bx, by)
                    }
                }
            }
        }
        val glow = Color(0xFFC9CCFF)
        listOfNotNull(path, branch).forEach { p ->
            drawPath(p, glow, alpha = flash * opacity * 0.35f, style = Stroke(width = 9f * density, cap = StrokeCap.Round))
            drawPath(p, Color.White, alpha = flash * opacity, style = Stroke(width = 2.2f * density, cap = StrokeCap.Round))
        }
    }
    drawRect(Color(0xFFE6E9FF), alpha = (0.38f * flash * opacity).coerceIn(0f, 1f))
}
