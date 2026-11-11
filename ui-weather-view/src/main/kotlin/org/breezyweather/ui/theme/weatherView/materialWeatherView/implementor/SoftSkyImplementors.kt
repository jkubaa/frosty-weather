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

package org.breezyweather.ui.theme.weatherView.materialWeatherView.implementor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.annotation.ColorInt
import androidx.annotation.Size
import androidx.core.graphics.ColorUtils
import org.breezyweather.ui.theme.weatherView.materialWeatherView.MaterialWeatherView.WeatherAnimationImplementor
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/*
 * Soft sky layers for the iOS-weather-like backgrounds. Everything is drawn with radial gradients
 * fading to transparent, so nothing has a hard edge, and everything moves slowly.
 */

/** A feathered disc: [color] at the center, fading to nothing at [radius]. */
private fun softDiscPaint(@ColorInt color: Int, radius: Float, coreAlpha: Float): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f,
            0f,
            radius.coerceAtLeast(1f),
            intArrayOf(
                ColorUtils.setAlphaComponent(color, (coreAlpha * 255).toInt()),
                ColorUtils.setAlphaComponent(color, (coreAlpha * 0.55f * 255).toInt()),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

/**
 * Slowly drifting soft clouds (or fog banks with [Style.fog]), optionally with lightning flashes.
 */
class SoftCloudImplementor(
    @Size(2) canvasSizes: IntArray,
    private val animate: Boolean,
    private val style: Style,
) : WeatherAnimationImplementor() {

    /**
     * @param color cloud color
     * @param coreAlpha opacity at the center of each puff
     * @param clouds number of clouds
     * @param fog wide, low banks spread over the screen instead of clouds in the upper sky
     * @param speed drift speed multiplier
     * @param lightning occasional flashes lighting up the sky
     */
    data class Style(
        @ColorInt val color: Int,
        val coreAlpha: Float,
        val clouds: Int,
        val fog: Boolean = false,
        val speed: Float = 1f,
        val lightning: Boolean = false,
    )

    private class Puff(
        var x: Float,
        val y: Float,
        val radius: Float,
        val speed: Float, // px per ms
        val paint: Paint,
    )

    private val random = Random(style.hashCode())
    private val width = canvasSizes[0].toFloat()
    private val height = canvasSizes[1].toFloat()
    private val puffs = mutableListOf<Puff>()

    private var flashTimer = nextFlashDelay()
    private var flashProgress = -1f // < 0: no flash running
    private val flashPaint = Paint()

    init {
        repeat(style.clouds) { i ->
            if (style.fog) {
                // Fog: a few very wide, low-contrast banks across the whole screen
                val radius = width * (0.55f + random.nextFloat() * 0.35f)
                puffs += Puff(
                    x = random.nextFloat() * width,
                    y = height * (0.15f + 0.75f * i / max(1, style.clouds - 1)),
                    radius = radius,
                    speed = width / 160_000f * style.speed * (0.6f + random.nextFloat() * 0.8f),
                    paint = softDiscPaint(style.color, radius, style.coreAlpha)
                )
            } else {
                // Cloud: a cluster of 4 to 6 overlapping puffs, in the upper half of the sky
                val centerX = random.nextFloat() * width
                val centerY = height * (0.04f + random.nextFloat() * 0.42f)
                val size = width * (0.22f + random.nextFloat() * 0.18f)
                val speed = width / 120_000f * style.speed * (0.6f + random.nextFloat() * 0.8f)
                repeat(4 + random.nextInt(3)) {
                    val radius = size * (0.55f + random.nextFloat() * 0.5f)
                    puffs += Puff(
                        x = centerX + (random.nextFloat() - 0.5f) * size * 1.6f,
                        y = centerY + (random.nextFloat() - 0.5f) * size * 0.5f,
                        radius = radius,
                        speed = speed,
                        paint = softDiscPaint(style.color, radius, style.coreAlpha)
                    )
                }
            }
        }
    }

    private fun nextFlashDelay() = 5_000f + random.nextFloat() * 9_000f

    override fun updateData(
        @Size(2) canvasSizes: IntArray,
        interval: Long,
        rotation2D: Float,
        rotation3D: Float,
    ) {
        if (!animate) return
        for (p in puffs) {
            p.x += p.speed * interval
            if (p.x - p.radius > width) p.x = -p.radius // drift back in from the left
        }
        if (style.lightning) {
            if (flashProgress >= 0f) {
                flashProgress += interval
                if (flashProgress > FLASH_DURATION) flashProgress = -1f
            } else {
                flashTimer -= interval
                if (flashTimer <= 0f) {
                    flashProgress = 0f
                    flashTimer = nextFlashDelay()
                }
            }
        }
    }

    override fun draw(
        @Size(2) canvasSizes: IntArray,
        canvas: Canvas,
        scrollRate: Float,
        rotation2D: Float,
        rotation3D: Float,
    ) {
        if (scrollRate >= 1) return
        val fade = 1 - scrollRate
        for (p in puffs) {
            p.paint.alpha = (fade * 255).toInt()
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.drawCircle(0f, 0f, p.radius, p.paint)
            canvas.restore()
        }
        if (flashProgress >= 0f) {
            // Two quick pulses, like a real flash
            val t = flashProgress / FLASH_DURATION
            val intensity = (sin(t * 2 * PI * 2).toFloat().coerceAtLeast(0f)) * (1 - t)
            flashPaint.color = Color.argb((fade * intensity * 0.22f * 255).toInt(), 235, 240, 255)
            canvas.drawRect(0f, 0f, width, height, flashPaint)
        }
    }

    companion object {
        private const val FLASH_DURATION = 600f
    }
}

/** A warm sun glow in the upper corner, gently breathing. */
class SunGlowImplementor(
    @Size(2) canvasSizes: IntArray,
    private val animate: Boolean,
) : WeatherAnimationImplementor() {

    private val width = canvasSizes[0].toFloat()
    private val radius = width * 1.1f
    private val paint = softDiscPaint(Color.rgb(255, 244, 214), radius, 0.38f)
    private val corePaint = softDiscPaint(Color.rgb(255, 252, 238), width * 0.32f, 0.45f)
    private var time = 0f

    override fun updateData(
        @Size(2) canvasSizes: IntArray,
        interval: Long,
        rotation2D: Float,
        rotation3D: Float,
    ) {
        if (animate) time += interval
    }

    override fun draw(
        @Size(2) canvasSizes: IntArray,
        canvas: Canvas,
        scrollRate: Float,
        rotation2D: Float,
        rotation3D: Float,
    ) {
        if (scrollRate >= 1) return
        val breathe = 0.85f + 0.15f * sin(time / 9_000f * 2 * PI).toFloat()
        val alpha = ((1 - scrollRate) * breathe * 255).toInt()
        paint.alpha = alpha
        corePaint.alpha = alpha
        canvas.save()
        canvas.translate(width * 0.82f, -width * 0.08f)
        canvas.drawCircle(0f, 0f, radius, paint)
        canvas.drawCircle(0f, 0f, width * 0.32f, corePaint)
        canvas.restore()
    }
}

/** Several layers drawn on top of each other (e.g. clouds, then rain). */
class LayeredImplementor(
    private val layers: List<WeatherAnimationImplementor>,
) : WeatherAnimationImplementor() {

    override fun updateData(
        @Size(2) canvasSizes: IntArray,
        interval: Long,
        rotation2D: Float,
        rotation3D: Float,
    ) {
        layers.forEach { it.updateData(canvasSizes, interval, rotation2D, rotation3D) }
    }

    override fun draw(
        @Size(2) canvasSizes: IntArray,
        canvas: Canvas,
        scrollRate: Float,
        rotation2D: Float,
        rotation3D: Float,
    ) {
        layers.forEach {
            // Some layers rotate the canvas (rain falls at an angle): isolate them
            canvas.save()
            it.draw(canvasSizes, canvas, scrollRate, rotation2D, rotation3D)
            canvas.restore()
        }
    }
}
