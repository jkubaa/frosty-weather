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

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Night sky details: the Milky Way, shooting stars, the moon's surface and its halo.
 *
 * Everything heavy is baked once into small bitmaps (off the main thread for the Milky Way, which
 * comes with the night sky's assets) and drawn scaled, so a frame stays a handful of draws.
 */
internal object NightSky {

    /** Screen height over width the Milky Way is laid out for (most phones are close). */
    private const val ASPECT = 2.2f

    /** The band crosses the sky from low on the left to high on the right, in screen fractions. */
    internal val BAND_START = Offset(-0.1f, 0.78f)
    internal val BAND_END = Offset(1.1f, 0.02f)

    /** The Milky Way covers a bit more than the screen, so the slow sky rotation never shows its edges. */
    const val MARGIN_X = 0.15f
    const val MARGIN_Y = 0.1f

    /** Glowing band with dark dust lanes, warmer towards its core. */
    val milkyWay: ImageBitmap by lazy { bakeMilkyWay() }

    /** Moon surface: seas, craters and limb darkening, fully lit (the phase is a clip on top). */
    val moonSurface: ImageBitmap by lazy { bakeMoonSurface() }

    private fun bakeMilkyWay(): ImageBitmap {
        val width = 240
        val height = (width * ASPECT * (1f + 2f * MARGIN_Y) / (1f + 2f * MARGIN_X)).roundToInt()
        val clouds = PeriodicNoise(3)
        val dust = PeriodicNoise(4)
        val p0x = BAND_START.x
        val p0y = BAND_START.y * ASPECT
        var dx = BAND_END.x - p0x
        var dy = BAND_END.y * ASPECT - p0y
        val len = hypot(dx, dy)
        dx /= len
        dy /= len
        val pixels = IntArray(width * height)
        for (j in 0 until height) {
            // Width units on both axes, so the band keeps its width along its slope
            val y = (-MARGIN_Y + j / (height - 1f) * (1f + 2f * MARGIN_Y)) * ASPECT
            for (i in 0 until width) {
                val x = -MARGIN_X + i / (width - 1f) * (1f + 2f * MARGIN_X)
                val rx = x - p0x
                val ry = y - p0y
                val along = rx * dx + ry * dy
                val perp = rx * -dy + ry * dx + (clouds.fbm(along * 3f, 7.7f, 256, 3) - 0.5f) * 0.14f
                val core = exp(-(perp / 0.17f) * (perp / 0.17f))
                val inner = exp(-(perp / 0.06f) * (perp / 0.06f))
                val cl = clouds.fbm(x * 5f, y * 5f, 256, 5)
                val lane = smoothstep(0.48f, 0.66f, dust.fbm(x * 9f + along * 2f, y * 9f, 256, 4)) * inner
                val intensity = (core * (0.2f + 1.2f * (cl - 0.3f)) + inner * 0.22f - lane * 0.6f).coerceIn(0f, 1f)
                if (intensity <= 0.004f) continue
                val warm = exp(-((along - 1f) / 0.45f) * ((along - 1f) / 0.45f))
                pixels[j * width + i] = argb(
                    (intensity * 0.95f).coerceAtMost(1f),
                    (196f + (255f - 196f) * warm) / 255f,
                    (186f + (224f - 186f) * warm) / 255f,
                    (255f + (204f - 255f) * warm) / 255f
                )
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private fun bakeMoonSurface(): ImageBitmap {
        val size = 160
        val noise = PeriodicNoise(17)
        val random = Random(5)
        val craters = List(18) {
            val a = random.nextFloat() * 2f * PI.toFloat()
            val q = sqrt(random.nextFloat()) * 0.85f
            Triple(cos(a) * q, sin(a) * q, 0.04f + random.nextFloat() * 0.12f)
        }
        val radius = size / 2f - 0.5f
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val nx = (x + 0.5f - size / 2f) / radius
                val ny = (y + 0.5f - size / 2f) / radius
                val rr = nx * nx + ny * ny
                if (rr > 1.04f) continue
                val edge = ((1f - sqrt(rr)) * radius + 0.5f).coerceIn(0f, 1f)
                var albedo = 0.95f - smoothstep(0.5f, 0.68f, noise.fbm(nx * 2.2f + 3f, ny * 2.2f + 3f, 256, 4)) * 0.22f
                for ((cx, cy, cr) in craters) {
                    val d = hypot(nx - cx, ny - cy) / cr
                    if (d < 0.8f) albedo -= 0.055f else if (d < 1.05f) albedo += 0.04f
                }
                // Limb darkening: the sphere dims a little towards its edge
                val limb = 0.78f + 0.22f * sqrt(max(0f, 1f - rr))
                val b = (albedo * limb).coerceIn(0f, 1f)
                pixels[y * size + x] = argb(edge, b * 0.98f + 0.02f, b * 0.96f + 0.03f, b)
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private fun argb(a: Float, r: Float, g: Float, b: Float): Int =
        ((a * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or
            ((r * 255f + 0.5f).toInt().coerceIn(0, 255) shl 16) or
            ((g * 255f + 0.5f).toInt().coerceIn(0, 255) shl 8) or
            (b * 255f + 0.5f).toInt().coerceIn(0, 255)
}

/** The Milky Way, added onto the sky (it is light, so it brightens what is under it). */
internal fun DrawScope.drawMilkyWay(image: ImageBitmap, alpha: Float, lift: Float) {
    if (alpha <= 0.01f) return
    val w = size.width
    val h = size.height
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset((-NightSky.MARGIN_X * w).roundToInt(), (-NightSky.MARGIN_Y * h - lift).roundToInt()),
        dstSize = IntSize((w * (1f + 2f * NightSky.MARGIN_X)).roundToInt(), (h * (1f + 2f * NightSky.MARGIN_Y)).roundToInt()),
        alpha = alpha.coerceIn(0f, 1f),
        blendMode = BlendMode.Plus,
        filterQuality = FilterQuality.Low
    )
}

/**
 * Now and then a shooting star: a short bright streak with a fading tail. Each few-second slot
 * decides from its own seed whether one crosses, where and which way, so nothing is simulated.
 */
internal fun DrawScope.drawShootingStar(time: Float, alpha: Float, lift: Float) {
    if (alpha <= 0.01f) return
    val period = 6f
    val slot = floor(time / period).toInt()
    val random = Random(slot * 4111 + 7)
    if (random.nextFloat() < 0.35f) return // some slots stay quiet
    val start = slot * period + random.nextFloat() * 3f
    val duration = 0.7f + random.nextFloat() * 0.5f
    val p = (time - start) / duration
    if (p < 0f || p > 1f) return
    val w = size.width
    val h = size.height
    val direction = if (random.nextBoolean()) 1f else -1f
    val dx = direction * cos(0.5f)
    val dy = sin(0.5f)
    val length = w * (0.35f + random.nextFloat() * 0.25f)
    val x0 = w * (0.2f + random.nextFloat() * 0.6f)
    val y0 = h * (0.05f + random.nextFloat() * 0.25f) - lift
    val head = Offset(x0 + dx * length * p, y0 + dy * length * p)
    val tailLength = (40f + 70f * p) * density
    val tail = Offset(head.x - dx * tailLength, head.y - dy * tailLength)
    val fade = sin(PI.toFloat() * p) * alpha
    drawLine(
        brush = Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFFFAF0)), start = tail, end = head),
        start = tail,
        end = head,
        strokeWidth = 1.4f * density,
        cap = StrokeCap.Round,
        alpha = (fade * 0.9f).coerceIn(0f, 1f)
    )
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xCCFFFAF0), Color.Transparent), center = head, radius = 5f * density),
        radius = 5f * density,
        center = head,
        alpha = fade.coerceIn(0f, 1f)
    )
}

/** The faint 22° halo ice crystals draw around the moon: a thin ring, slightly pink outside. */
internal fun DrawScope.drawMoonHalo(center: Offset, moonRadius: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val outer = moonRadius * 5.6f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            0.8f to Color.Transparent,
            0.88f to Color(0xFFD7CDFF).copy(alpha = 0.1f),
            0.93f to Color(0xFFFFD7EB).copy(alpha = 0.06f),
            1f to Color.Transparent,
            center = center,
            radius = outer
        ),
        radius = outer,
        center = center,
        alpha = alpha.coerceIn(0f, 1f)
    )
}
