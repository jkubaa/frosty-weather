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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.math.floor
import kotlin.random.Random

/**
 * Procedural, painterly cloud textures.
 *
 * Domain-warped fractal noise gives soft, flowing shapes; a light from above makes cloud tops
 * bright and undersides darker. The noise repeats exactly across the texture width, so a band of
 * clouds can drift forever by tiling the texture side by side without a visible seam.
 *
 * Textures are small (they are upscaled with filtering, which only adds to the soft look) and are
 * made off the main thread, then cached.
 */
internal object CloudTextures {

    private const val WIDTH = 384
    private const val HEIGHT = 192
    private const val OCTAVES = 5

    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 16
    }

    /**
     * @param shaded false for flat fog: a single color, no lit tops or dark undersides
     */
    fun get(layer: CloudLayer, light: Color, shadow: Color, shaded: Boolean = true): ImageBitmap {
        val key = "${layer.seed}|${layer.cover}|${layer.wispy}|${light.toArgb()}|${shadow.toArgb()}|$shaded"
        synchronized(cache) { cache[key] }?.let { return it }
        val texture = generate(layer, light, shadow, shaded)
        synchronized(cache) { cache[key] = texture }
        return texture
    }

    private fun generate(layer: CloudLayer, light: Color, shadow: Color, shaded: Boolean): ImageBitmap {
        val noise = PeriodicNoise(layer.seed)
        // Wispy: few cells across, many down, so features stretch into horizontal streaks
        // Puffy: big soft masses, little swirl, fine detail kept faint (low persistence)
        val cellsX = if (layer.wispy) 3 else 4
        val cellsY = if (layer.wispy) 6f else 2.4f
        val warpX = if (layer.wispy) 1.6f else 0.5f
        val warpY = if (layer.wispy) 0.5f else 0.35f
        val persistence = if (layer.wispy) 0.5f else 0.42f
        val softness = if (layer.wispy) 0.1f else 0.16f
        // fbm clusters around 0.5: map cover 0..1 to a threshold from "only the densest peaks" to "nearly all"
        val threshold = 0.70f - layer.cover * 0.42f

        val lr = light.red
        val lg = light.green
        val lb = light.blue
        val sr = shadow.red
        val sg = shadow.green
        val sb = shadow.blue

        val pixels = IntArray(WIDTH * HEIGHT)
        for (y in 0 until HEIGHT) {
            val v = y / (HEIGHT - 1f)
            // Soft top and bottom edges, so bands blend into the sky
            val envelope = smoothstep(0f, 0.28f, v) * (1f - smoothstep(0.62f, 1f, v))
            val fy = v * cellsY
            for (x in 0 until WIDTH) {
                val fx = x / WIDTH.toFloat() * cellsX
                val warp = noise.fbm(fx + 5.2f, fy + 1.3f, cellsX, OCTAVES - 2, persistence) - 0.5f
                val wx = fx + warp * warpX * 2f
                val wy = fy + warp * warpY * 2f
                val density = noise.fbm(wx, wy, cellsX, OCTAVES, persistence)
                val alpha = smoothstep(threshold - softness, threshold + 0.28f + softness, density) * envelope
                if (alpha <= 0.003f) continue

                val t = if (shaded) {
                    // Brighter where the cloud thins out above (its lit top), darker lower in the band
                    val above = noise.fbm(wx, wy - 0.22f, cellsX, OCTAVES, persistence)
                    ((density - above) * 2.2f + 0.62f + (density - 0.5f) * 0.3f - (v - 0.45f) * 0.45f)
                        .coerceIn(0f, 1f)
                } else {
                    1f
                }
                val r = sr + (lr - sr) * t
                val g = sg + (lg - sg) * t
                val b = sb + (lb - sb) * t
                pixels[y * WIDTH + x] = argb(alpha, r, g, b)
            }
        }
        return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private fun argb(a: Float, r: Float, g: Float, b: Float): Int =
        ((a * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or
            ((r * 255f + 0.5f).toInt().coerceIn(0, 255) shl 16) or
            ((g * 255f + 0.5f).toInt().coerceIn(0, 255) shl 8) or
            (b * 255f + 0.5f).toInt().coerceIn(0, 255)
}

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Value noise that repeats every `period` cells horizontally (vertically it doesn't need to). */
private class PeriodicNoise(seed: Int) {

    private val perm = IntArray(512).also { p ->
        val shuffled = (0 until 256).shuffled(Random(seed))
        for (i in 0 until 512) p[i] = shuffled[i and 255]
    }

    private fun lattice(ix: Int, iy: Int, period: Int): Float {
        val x = Math.floorMod(ix, period) and 255
        return perm[perm[x] + (iy and 255)] / 255f
    }

    private fun noise(x: Float, y: Float, period: Int): Float {
        val x0 = floor(x)
        val y0 = floor(y)
        val ix = x0.toInt()
        val iy = y0.toInt()
        val tx = fade(x - x0)
        val ty = fade(y - y0)
        val a = lattice(ix, iy, period)
        val b = lattice(ix + 1, iy, period)
        val c = lattice(ix, iy + 1, period)
        val d = lattice(ix + 1, iy + 1, period)
        val top = a + (b - a) * tx
        val bottom = c + (d - c) * tx
        return top + (bottom - top) * ty
    }

    /** Fractal sum of octaves; each octave doubles the frequency and the period, so tiling holds. */
    fun fbm(x: Float, y: Float, period: Int, octaves: Int, persistence: Float = 0.5f): Float {
        var sum = 0f
        var norm = 0f
        var amplitude = 0.5f
        var frequency = 1
        repeat(octaves) {
            sum += amplitude * noise(x * frequency, y * frequency, period * frequency)
            norm += amplitude
            amplitude *= persistence
            frequency *= 2
        }
        return sum / norm
    }

    private fun fade(t: Float) = t * t * (3f - 2f * t)
}
