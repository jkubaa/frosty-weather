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
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Procedural cloud textures in the style of Apple's weather skies: soft, low-contrast vapour that
 * hangs from the top of its band and ends in a ragged, feathered lower edge.
 *
 * Twice domain-warped fractal noise gives swirly, streaky structure, stretched sideways. Denser
 * streaks catch more light and faces turned towards the light (from above) brighten, while the
 * contrast stays low so the clouds keep close to the sky's own colours. The noise repeats exactly
 * across the texture width, so a band of clouds can drift forever by tiling the texture side by
 * side without a visible seam.
 *
 * Textures are upscaled with filtering, which only adds to the soft look, and are made off the
 * main thread, then cached.
 */
internal object CloudTextures {

    private const val WIDTH = 512
    private const val HEIGHT = 256

    /** Noise cells across the texture: the period of the tiling. */
    private const val CELLS = 4

    /** Vertical stretch of the noise: higher gives flatter, streakier vapour. */
    private const val ANISO = 2.6f

    /** How much the noise bends itself: higher swirls more. */
    private const val WARP = 1.2f

    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 16
    }

    /**
     * @param shaded false for fog: a single colour, the structure only shows in its transparency
     */
    fun get(layer: CloudLayer, light: Color, shadow: Color, shaded: Boolean = true): ImageBitmap {
        val key = "${layer.seed}|${layer.cover}|${layer.edge}|${layer.topFade}|" +
            "${light.toArgb()}|${shadow.toArgb()}|$shaded"
        synchronized(cache) { cache[key] }?.let { return it }
        val texture = generate(layer, light, shadow, shaded)
        synchronized(cache) { cache[key] = texture }
        return texture
    }

    private fun generate(layer: CloudLayer, light: Color, shadow: Color, shaded: Boolean): ImageBitmap {
        val base = PeriodicNoise(layer.seed)
        val warpA = PeriodicNoise(layer.seed * 7 + 3)
        val warpB = PeriodicNoise(layer.seed * 3 + 11)
        val ragged = PeriodicNoise(layer.seed * 13 + 5)
        // fbm clusters around 0.5: cover 0..1 maps to a threshold from "only the densest" to "nearly all"
        val threshold = 0.6f - layer.cover * 0.3f
        val softness = 0.45f
        val raggedness = if (layer.topFade) 0.3f else 0.22f

        val density = FloatArray(WIDTH * HEIGHT)
        val detail = FloatArray(WIDTH * HEIGHT)
        // The lower edge of the deck wanders across the texture
        val edges = FloatArray(WIDTH) { x ->
            val u = x / WIDTH.toFloat() * CELLS
            layer.edge + (ragged.fbm(u * 1.5f, 3.3f, CELLS * 3 / 2, 3) - 0.5f) * raggedness * 2f
        }
        for (y in 0 until HEIGHT) {
            val yn = y / HEIGHT.toFloat()
            val v = y / WIDTH.toFloat() * CELLS * ANISO
            for (x in 0 until WIDTH) {
                val u = x / WIDTH.toFloat() * CELLS
                val qx = warpA.fbm(u, v, CELLS, 4)
                val qy = warpA.fbm(u + 5.2f, v + 1.3f, CELLS, 4)
                val rx = warpB.fbm(u + WARP * qx + 1.7f, v + WARP * qy + 9.2f, CELLS, 4)
                val ry = warpB.fbm(u + WARP * qx + 8.3f, v + WARP * qy + 2.8f, CELLS, 4)
                val f = base.fbm(u + WARP * rx, v + WARP * ry, CELLS, 6)
                val edge = edges[x]
                var mask = (1f - smoothstep(edge - 0.22f, edge + 0.04f, yn)) * (0.75f + 0.25f * (1f - yn))
                if (layer.topFade) mask *= smoothstep(0f, 0.3f, yn)
                val i = y * WIDTH + x
                detail[i] = f
                density[i] = smoothstep(threshold, threshold + softness, f + mask * 0.25f - 0.12f) * mask
            }
        }

        // Soft relief: compare each point with a softened copy of the cloud a little towards the light
        val radius = (HEIGHT * 0.01f).roundToInt().coerceAtLeast(1)
        val soft = boxBlur(boxBlur(density, radius), radius)
        val lightX = 0.33f
        val lightY = -0.94f
        val reach = HEIGHT * 0.02f

        val lr = light.red
        val lg = light.green
        val lb = light.blue
        val sr = shadow.red
        val sg = shadow.green
        val sb = shadow.blue
        val opacityNorm = 1f / (1f - exp(-3.2f))

        val pixels = IntArray(WIDTH * HEIGHT)
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val i = y * WIDTH + x
                val a = density[i]
                if (a < 0.004f) continue
                val t = if (shaded) {
                    val sx = Math.floorMod((x + lightX * reach).roundToInt(), WIDTH)
                    val sy = (y + lightY * reach).roundToInt().coerceIn(0, HEIGHT - 1)
                    val relief = soft[i] - soft[sy * WIDTH + sx]
                    (0.42f + (detail[i] - 0.5f) * 0.6f + a * 0.3f + relief - y / HEIGHT.toFloat() * 0.12f)
                        .coerceIn(0f, 1f)
                } else {
                    1f
                }
                val r = sr + (lr - sr) * t
                val g = sg + (lg - sg) * t
                val b = sb + (lb - sb) * t
                val alpha = (1f - exp(-a * 3.2f)) * opacityNorm * 0.72f
                pixels[i] = argb(alpha, r, g, b)
            }
        }
        return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** Box blur that wraps horizontally (the texture tiles) and clamps vertically. */
    private fun boxBlur(src: FloatArray, r: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val k = 2f * r + 1f
        for (y in 0 until HEIGHT) {
            val row = y * WIDTH
            var sum = 0f
            for (i in -r..r) sum += src[row + Math.floorMod(i, WIDTH)]
            for (x in 0 until WIDTH) {
                tmp[row + x] = sum / k
                sum += src[row + (x + r + 1) % WIDTH] - src[row + Math.floorMod(x - r, WIDTH)]
            }
        }
        for (x in 0 until WIDTH) {
            var sum = 0f
            for (i in -r..r) sum += tmp[i.coerceIn(0, HEIGHT - 1) * WIDTH + x]
            for (y in 0 until HEIGHT) {
                out[y * WIDTH + x] = sum / k
                sum += tmp[(y + r + 1).coerceAtMost(HEIGHT - 1) * WIDTH + x] - tmp[(y - r).coerceAtLeast(0) * WIDTH + x]
            }
        }
        return out
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
