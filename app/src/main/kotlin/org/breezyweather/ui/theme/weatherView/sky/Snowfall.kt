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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Snow with depth: soft round flakes at every distance, most of them far (small, slow, faint),
 * a few close (bigger, faster), and a handful right in front of the eye, big and out of focus.
 * Flakes sway and tumble, and gusts sweep them all sideways (the nearer, the faster), then settle.
 *
 * Falling is a pure function of time; the wind's drift is the only thing carried from frame to
 * frame, so gusts push the flakes along instead of teleporting them.
 *
 * Sizes and speeds are in "units" of 1/300 of the screen width, as in the design mock-up.
 */
internal class Snowfall(count: Int, private val sleet: Boolean, seed: Int) {

    /** x, phase, depth, size, speed, sway, sway frequency, sway phase, alpha, near (1/0) */
    val flakes: FloatArray

    private var drift = 0f
    private var lastTime = Float.NaN

    init {
        val random = Random(seed)
        val list = List(count) {
            val d = random.nextFloat().pow(1.7f)
            val near = !sleet && d > 0.93f
            floatArrayOf(
                random.nextFloat(),
                random.nextFloat(),
                d,
                if (near) 22f + random.nextFloat() * 18f else 2.2f + 7.8f * d * d * (if (sleet) 0.7f else 1f),
                (16f + 59f * d) * (0.85f + random.nextFloat() * 0.3f) * (if (sleet) 2.6f else 1f),
                3f + 15f * d,
                0.4f + random.nextFloat() * 1.1f,
                random.nextFloat() * 2f * PI.toFloat(),
                if (near) 0.34f else 0.6f + 0.35f * d,
                if (near) 1f else 0f
            )
        }.sortedBy { it[2] } // far first, so near flakes pass in front
        flakes = FloatArray(count * STRIDE).also { a -> list.forEachIndexed { i, f -> f.copyInto(a, i * STRIDE) } }
    }

    /** Sideways drift so far, in units: calm most of the time, gusts now and then. */
    fun drift(time: Float, wind: Float): Float {
        val dt = if (lastTime.isNaN()) 0f else (time - lastTime).coerceIn(0f, 0.1f)
        lastTime = time
        val g = max(0f, sin(time * 0.29f) * 0.6f + sin(time * 0.71f + 1.3f) * 0.4f)
        drift += (6f + wind * 20f + g * g * 110f) * dt
        return drift
    }

    companion object {
        const val STRIDE = 10
    }
}

private object FlakeSprites {
    /** Soft round flake, solid in the middle */
    val crisp: Bitmap by lazy { soft(32, floatArrayOf(0f, 0.3f, 0.6f, 1f), floatArrayOf(1f, 0.85f, 0.35f, 0f)) }

    /** A flake right in front of the eye: big, faint, out of focus */
    val blurred: Bitmap by lazy { soft(64, floatArrayOf(0f, 0.45f, 0.8f, 1f), floatArrayOf(0.6f, 0.4f, 0.12f, 0f)) }

    private fun soft(size: Int, stops: FloatArray, alphas: FloatArray): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            val half = size / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    half, half, half,
                    IntArray(alphas.size) { i -> android.graphics.Color.argb((alphas[i] * 255f).roundToInt(), 255, 255, 255) },
                    stops,
                    Shader.TileMode.CLAMP
                )
            }
            Canvas(it).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        }
}

private val flakePaint = Paint(Paint.FILTER_BITMAP_FLAG)
private val flakeRect = RectF()

/**
 * @param wind 0 calm .. 1 windy (the style's wind)
 * @param lift how far the sky has moved up with the scroll; near flakes move more
 */
internal fun DrawScope.drawSnowfall(snow: Snowfall, time: Float, wind: Float, opacity: Float, lift: Float) {
    if (opacity <= 0.01f) return
    val w = size.width
    val h = size.height
    val unit = w / 300f
    val drift = snow.drift(time, wind) * unit
    val margin = 30f * unit
    val spanY = h + margin * 2f
    val spanX = w + margin * 2f
    val canvas = drawContext.canvas.nativeCanvas
    val a = snow.flakes
    var i = 0
    while (i < a.size) {
        val d = a[i + 2]
        val sf = a[i + 6]
        val ph = a[i + 7]
        val y = (a[i + 1] * spanY + time * a[i + 4] * unit) % spanY - margin - lift * d
        val sway = sin(time * sf + ph) * a[i + 5] / sf * unit
        val x = ((a[i] * spanX + drift * (0.25f + d) + sway) % spanX + spanX) % spanX - margin
        // Tumbling: the flake turns, so it looks a little bigger and smaller
        val half = a[i + 3] * unit * (0.9f + 0.1f * sin(time * sf * 2f + ph)) / 2f
        flakePaint.alpha = (a[i + 8] * opacity * 255f).roundToInt().coerceIn(0, 255)
        flakeRect.set(x - half, y - half, x + half, y + half)
        canvas.drawBitmap(if (a[i + 9] > 0f) FlakeSprites.blurred else FlakeSprites.crisp, null, flakeRect, flakePaint)
        i += Snowfall.STRIDE
    }
}
