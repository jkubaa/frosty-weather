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
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Northern (or southern) lights: curtains with a sharp, bright green lower edge and a faint pink
 * fringe under it, thin rays rising from it and fading into dim red-violet high up. Each curtain is
 * hundreds of rays standing along a foot line that folds back on itself, so it glows brightest in
 * its folds, as real aurora does. Slow ripples run along the curtains; the rays flicker.
 *
 * On Android 12+ (hardware canvas) a blurred copy adds the soft glow around the curtains.
 */
private object AuroraParts {
    val noise = PeriodicNoise(77)

    /** One ray, its foot at the bottom of the bitmap */
    val ray: Bitmap by lazy {
        Bitmap.createBitmap(4, 256, Bitmap.Config.ARGB_8888).also {
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, 256f,
                    intArrayOf(
                        argb(0f, 150, 50, 120),
                        argb(0.22f, 150, 60, 140),
                        argb(0.45f, 70, 190, 140),
                        argb(0.95f, 100, 255, 165),
                        argb(1f, 210, 255, 225),
                        argb(0.55f, 255, 120, 190),
                        argb(0f, 255, 90, 180)
                    ),
                    floatArrayOf(0f, 0.3f, 0.55f, 0.86f, 0.95f, 0.975f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            Canvas(it).drawRect(0f, 0f, 4f, 256f, paint)
        }
    }

    val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    val rayPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = add }
    val rect = RectF()

    private fun argb(a: Float, r: Int, g: Int, b: Int) = android.graphics.Color.argb((a * 255f).roundToInt(), r, g, b)
}

private class Curtain(val base: Float, val amplitude: Float, val length: Float, val strength: Float, val seed: Float, val rays: Int)

/** Back curtain higher and fainter, front one lower and brighter */
private val CURTAINS = listOf(
    Curtain(base = 0.2f, amplitude = 0.03f, length = 0.2f, strength = 0.5f, seed = 5f, rays = 220),
    Curtain(base = 0.3f, amplitude = 0.045f, length = 0.28f, strength = 1f, seed = 0f, rays = 340),
)

/** Android 12+: the curtains are recorded once, then drawn sharp and blurred */
private object AuroraNodes {
    val rays by lazy { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderNode("AuroraRays") else null }
    val halo by lazy { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderNode("AuroraHalo") else null }
    val layerPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
}

/**
 * @param strength 0..1, already including the night and the weather
 * @param lift how far the sky has moved up with the scroll
 */
internal fun DrawScope.drawAurora(time: Float, strength: Float, lift: Float) {
    if (strength <= 0.01f) return
    val w = size.width
    val h = size.height
    // Aurora nights are clear, dark nights: the sky deepens towards navy behind the curtains
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFF02050F).copy(alpha = 0.7f),
            0.45f to Color(0xFF061531).copy(alpha = 0.5f),
            1f to Color(0xFF0B2238).copy(alpha = 0.25f)
        ),
        alpha = strength.coerceIn(0f, 1f)
    )
    // The air below glows faintly green
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFF1E8C64).copy(alpha = 0.16f),
            1f to Color.Transparent,
            center = Offset(w * 0.5f, h * 0.45f),
            radius = w * 1.1f
        ),
        radius = w * 1.1f,
        center = Offset(w * 0.5f, h * 0.45f),
        alpha = strength.coerceIn(0f, 1f)
    )

    val canvas = drawContext.canvas.nativeCanvas
    val rays = AuroraNodes.rays
    val halo = AuroraNodes.halo
    if (rays != null && halo != null && canvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rays.setPosition(0, 0, w.roundToInt(), h.roundToInt())
        rays.setUseCompositingLayer(true, AuroraNodes.layerPaint)
        drawCurtains(rays.beginRecording(), time, strength, lift)
        rays.endRecording()
        halo.setPosition(0, 0, w.roundToInt(), h.roundToInt())
        halo.setUseCompositingLayer(true, AuroraNodes.layerPaint)
        halo.setRenderEffect(RenderEffect.createBlurEffect(14f * density, 14f * density, Shader.TileMode.DECAL))
        halo.beginRecording().drawRenderNode(rays)
        halo.endRecording()
        canvas.drawRenderNode(halo)
        canvas.drawRenderNode(rays)
    } else {
        drawCurtains(canvas, time, strength, lift)
    }
}

private fun DrawScope.drawCurtains(canvas: Canvas, time: Float, strength: Float, lift: Float) {
    val w = size.width
    val h = size.height
    val rayWidth = 2f * w / 300f
    val noise = AuroraParts.noise
    val paint = AuroraParts.rayPaint
    val rect = AuroraParts.rect
    for (c in CURTAINS) {
        for (i in 0 until c.rays) {
            val u = i / c.rays.toFloat()
            // The curtain's foot: a curve that doubles back on itself in places (folds)
            val x = w * (-0.12f + 1.24f * u) +
                sin(u * 7f + time * 0.22f + c.seed) * w * 0.13f +
                sin(u * 23f - time * 0.35f + c.seed) * w * 0.012f
            val y = h * c.base +
                sin(u * 3.1f + time * 0.1f + c.seed) * h * c.amplitude +
                sin(u * 13f + time * 0.27f) * h * 0.02f -
                sin(u * PI.toFloat()) * h * 0.035f - lift
            val band = noise.fbm(u * 4f + time * 0.04f + c.seed, time * 0.03f, 256, 3)
            val flicker = 0.55f + 0.45f * noise.fbm(u * 120f + time * 1.4f, c.seed * 3f + time * 0.7f, 256, 1)
            val a = smoothstep(0.28f, 0.6f, band) * flicker * c.strength * 0.42f * strength
            if (a < 0.01f) continue
            val length = h * c.length * (0.65f + 0.7f * band)
            paint.alpha = (a * 255f).roundToInt().coerceIn(0, 255)
            rect.set(x, y - length, x + rayWidth, y + length * 0.04f)
            canvas.drawBitmap(AuroraParts.ray, null, rect, paint)
        }
    }
}
