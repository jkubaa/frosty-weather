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
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How wet the window is.
 *
 * @param beads beads on the glass at rest, per 100 000 dp²
 * @param landEvery seconds between new drops landing, for a 400 dp wide screen
 * @param runners heavy drops sliding down at once, at most
 * @param runnerEvery seconds between new runners, at least
 * @param speed how fast runners slide
 * @param maxBead radius of the biggest beads, in dp
 * @param wind sideways push on the runners, dp per dp of fall
 */
internal class GlassWeather(
    val beads: Int,
    val landEvery: Float,
    val runners: Int,
    val runnerEvery: Float,
    val speed: Float,
    val maxBead: Float,
    val wind: Float = 0f,
) {
    companion object {
        val RAIN = GlassWeather(beads = 80, landEvery = 0.03f, runners = 12, runnerEvery = 0.45f, speed = 1f, maxBead = 7.5f)
        val SLEET = GlassWeather(beads = 50, landEvery = 0.06f, runners = 6, runnerEvery = 0.9f, speed = 0.8f, maxBead = 6f)
        val STORM = GlassWeather(beads = 130, landEvery = 0.01f, runners = 22, runnerEvery = 0.15f, speed = 1.7f, maxBead = 8.5f, wind = 0.25f)
    }
}

/**
 * A wet window in front of the sky, for rainy weather.
 *
 * The sky (the "world" outside) is recorded once per frame into a GPU layer. The glass is fogged,
 * so the world shows blurred, except along the trails that sliding drops have wiped clear, which
 * slowly fog over again. Every drop on the glass is a small lens: a clipped, upside-down view of
 * the world behind it, with a dark rim, a bright lower crescent and a glint.
 *
 * The drops are simulated (they land, slide, stall, merge and leave beads behind), stepped by the
 * frame time. Android 10+ on a hardware canvas; the fogging needs Android 12 (on older versions
 * the world stays sharp, under a milky film).
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class RainGlass {

    private class Drop(var x: Float, var y: Float, var r: Float, val sx: Float, val sy: Float, val trail: Boolean)

    private class Runner(var x: Float, var y: Float, var r: Float, val wobble: Float) {
        var v = 0f
        var stall = 0.2f + Random.nextFloat() * 0.8f
        var trail = 0f
    }

    private val beads = ArrayList<Drop>(800)
    private val runners = ArrayList<Runner>(32)
    private var landClock = 0f
    private var runnerClock = 0.2f
    private var fadeClock = 0f
    private var lastTime = Float.NaN
    private var width = 0
    private var height = 0

    private val world = RenderNode("RainWorld")
    private val fogged = RenderNode("RainFogged")
    private val wiped = RenderNode("RainWiped")

    /** Where runners wiped the glass, at a quarter of the screen size */
    private var wipe: Bitmap? = null
    private var wipeCanvas: Canvas? = null
    private val wipeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val wipeFade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val filmPaint = Paint().apply { color = android.graphics.Color.argb(13, 210, 220, 232) }
    private val lensPath = Path()
    private val rect = RectF()

    /**
     * @param amount 0..1: how much of the glass shows (it fades in and out with the weather)
     * @param sky draws the world outside
     */
    fun draw(scope: DrawScope, time: Float, amount: Float, weather: GlassWeather, sky: DrawScope.() -> Unit) {
        val canvas = scope.drawContext.canvas.nativeCanvas
        val w = scope.size.width
        val h = scope.size.height
        val density = scope.density
        if (w < 1f || h < 1f) return
        resize(w.roundToInt(), h.roundToInt(), density, weather)

        // The world outside, rendered once into a layer that every lens reuses
        world.setPosition(0, 0, width, height)
        world.setUseCompositingLayer(true, null)
        val worldCanvas = world.beginRecording()
        CanvasDrawScope().draw(Density(density, scope.fontScale), scope.layoutDirection, androidx.compose.ui.graphics.Canvas(worldCanvas), Size(w, h)) { sky() }
        world.endRecording()

        val dt = if (lastTime.isNaN()) 0f else (time - lastTime).coerceIn(0f, 0.05f)
        lastTime = time
        if (dt > 0f) step(dt, density, weather)

        val fogs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (!fogs || amount < 0.999f) canvas.drawRenderNode(world)
        if (fogs) {
            fogged.setPosition(0, 0, width, height)
            fogged.setRenderEffect(RenderEffect.createBlurEffect(14f * density, 14f * density, Shader.TileMode.CLAMP))
            fogged.alpha = amount
            fogged.beginRecording().drawRenderNode(world)
            fogged.endRecording()
            canvas.drawRenderNode(fogged)

            // Wiped trails show the world sharp again
            wipe?.let { mask ->
                wiped.setPosition(0, 0, width, height)
                wiped.setUseCompositingLayer(true, null)
                wiped.alpha = amount
                val c = wiped.beginRecording()
                c.drawRenderNode(world)
                c.drawBitmap(mask, null, Rect(0, 0, width, height), maskPaint)
                wiped.endRecording()
                canvas.drawRenderNode(wiped)
            }
        }
        filmPaint.alpha = (13 * amount).roundToInt()
        canvas.drawRect(0f, 0f, w, h, filmPaint)

        // The drops themselves
        spritePaint.alpha = (255 * amount).roundToInt()
        val small = Sprites.bead
        for (b in beads) {
            if (b.r < 3f * density) {
                val hw = b.r * 1.15f * b.sx
                val hh = b.r * 1.15f * b.sy
                rect.set(b.x - hw, b.y - hh, b.x + hw, b.y + hh)
                canvas.drawBitmap(small, null, rect, spritePaint)
            } else {
                drawLens(canvas, b.x, b.y, b.r, b.sx, b.sy, tear = false, amount)
            }
        }
        for (r in runners) drawLens(canvas, r.x, r.y, r.r, 1f, 1f, tear = true, amount)
    }

    private fun drawLens(canvas: Canvas, x: Float, y: Float, r: Float, sx: Float, sy: Float, tear: Boolean, amount: Float) {
        val top = if (tear) r * 1.55f else r * sy
        canvas.save()
        canvas.translate(x, y)
        lensPath.reset()
        if (tear) {
            lensPath.moveTo(0f, -r * 1.55f)
            lensPath.cubicTo(r * 0.35f, -r * 1.1f, r, -r * 0.55f, r, 0f)
            lensPath.arcTo(-r, -r, r, r, 0f, 180f, false)
            lensPath.cubicTo(-r, -r * 0.55f, -r * 0.35f, -r * 1.1f, 0f, -r * 1.55f)
        } else {
            lensPath.addOval(-r * sx, -r * sy, r * sx, r * sy, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(lensPath)
        // A wide, upside-down view of the world behind the drop
        val k = 1f / LENS_ZOOM
        canvas.scale(-k, -k)
        canvas.translate(-x, -y)
        canvas.drawRenderNode(world)
        canvas.restore()
        val shade = if (tear) Sprites.tear else Sprites.lens
        rect.set(-r * sx, -top, r * sx, r * sy)
        canvas.drawBitmap(shade, null, rect, spritePaint)
        canvas.restore()
    }

    private fun resize(w: Int, h: Int, density: Float, weather: GlassWeather) {
        if (w == width && h == height) return
        width = w
        height = h
        wipe?.recycle()
        wipe = Bitmap.createBitmap(max(1, w / 4), max(1, h / 4), Bitmap.Config.ALPHA_8).also {
            wipeCanvas = Canvas(it).apply { scale(0.25f, 0.25f) }
        }
        beads.clear()
        runners.clear()
        val count = (weather.beads * (w / density) * (h / density) / 100_000f).roundToInt()
        repeat(count) { beads += newBead(density, weather, trail = false) }
    }

    private fun newBead(density: Float, weather: GlassWeather, trail: Boolean): Drop {
        val q = Random.nextFloat()
        val r = if (trail) {
            (0.6f + Random.nextFloat() * 1.3f) * density
        } else {
            (0.7f + q * q * sqrt(q) * weather.maxBead) * density
        }
        return Drop(
            Random.nextFloat() * width,
            Random.nextFloat() * height,
            r,
            0.88f + Random.nextFloat() * 0.24f,
            0.88f + Random.nextFloat() * 0.24f,
            trail
        )
    }

    private fun step(dt: Float, density: Float, weather: GlassWeather) {
        val cap = (weather.beads * (width / density) * (height / density) / 100_000f * 1.45f).roundToInt()
        // New drops land (more often on a wider screen)
        landClock -= dt
        val every = weather.landEvery * 400f / (width / density)
        while (landClock < 0f) {
            landClock += every
            beads += newBead(density, weather, trail = false)
        }
        while (beads.size > cap) beads.removeAt(0)

        // The fog creeps back over wiped trails, in steps so 8-bit alpha rounding cannot stall it
        fadeClock += dt
        val wc = wipeCanvas
        if (wc != null && fadeClock > 0.25f) {
            wipeFade.alpha = (fadeClock * 0.16f * 255f).roundToInt().coerceIn(1, 255)
            wc.drawRect(0f, 0f, width.toFloat(), height.toFloat(), wipeFade)
            fadeClock = 0f
        }

        runnerClock -= dt
        if (runnerClock <= 0f && runners.size < weather.runners) {
            runnerClock = weather.runnerEvery * (1f + Random.nextFloat() * 2f)
            runners += Runner(
                Random.nextFloat() * width,
                Random.nextFloat() * height * 0.55f,
                (5f + Random.nextFloat() * 5f) * density,
                Random.nextFloat() * 10f
            )
        }
        val maxR = 12f * density
        val iterator = runners.listIterator()
        while (iterator.hasNext()) {
            val d = iterator.next()
            val px = d.x
            val py = d.y
            d.stall -= dt
            if (d.stall > 0f) {
                d.v *= 0.8f
            } else {
                d.v += (d.r * 16f * weather.speed - d.v) * min(1f, dt * 3f)
                if (Random.nextFloat() < dt * 0.6f / weather.speed) d.stall = 0.15f + Random.nextFloat() * 0.8f
            }
            val dy = d.v * dt
            d.y += dy
            d.x += sin(d.y / density * 0.04f + d.wobble) * dy * 0.22f + weather.wind * dy * d.r / density * 0.3f
            d.trail += dy
            if (dy > 0f && wc != null) {
                wipeStroke.strokeWidth = d.r * 1.5f
                wc.drawLine(px, py - d.r * 0.4f, d.x, d.y - d.r * 0.4f, wipeStroke)
            }
            if (d.trail > 7f * density) {
                d.trail = 0f
                beads += newBead(density, weather, trail = true).also {
                    it.x = d.x + (Random.nextFloat() - 0.5f) * d.r * 0.6f
                    it.y = d.y - d.r * 1.3f
                }
                d.r = max(3.4f * density, d.r - 0.03f * density)
            }
            // Swallow the beads it runs into
            var i = beads.size - 1
            while (i >= 0) {
                val b = beads[i]
                if (!b.trail) {
                    val ddx = b.x - d.x
                    val ddy = b.y - d.y
                    val reach = d.r + b.r
                    if (ddx * ddx + ddy * ddy < reach * reach * 0.7f) {
                        d.r = min(maxR, sqrt(d.r * d.r + b.r * b.r * 0.6f))
                        beads.removeAt(i)
                    }
                }
                i--
            }
            if (d.y > height + d.r * 3f || d.x < -d.r * 3f || d.x > width + d.r * 3f) iterator.remove()
        }
    }

    private companion object {
        /** How much wider than itself a drop sees */
        const val LENS_ZOOM = 4f
    }
}

/** Shading drawn over every drop, made once. */
private object Sprites {

    /** A small bead: faint fill, dark rim, light gathered low, a glint up left */
    val bead: Bitmap by lazy {
        val s = 48
        Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888).also { bmp ->
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(
                24f, 26f, 24f,
                intArrayOf(argb(0.10f, 255, 255, 255), argb(0.04f, 255, 255, 255), argb(0.32f, 10, 16, 26), argb(0.18f, 10, 16, 26), 0),
                floatArrayOf(0f, 0.6f, 0.84f, 0.97f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
            p.shader = RadialGradient(24f, 33f, 11f, argb(0.38f, 255, 255, 255), 0, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
            p.shader = null
            p.color = argb(0.85f, 255, 255, 255)
            c.save()
            c.rotate(-28f, 19f, 15f)
            c.drawOval(15.8f, 13f, 22.2f, 17f, p)
            c.restore()
        }
    }

    /** Shading for a round lens drop (drawn over the refracted view) */
    val lens: Bitmap by lazy { lensShade(tear = false) }

    /** Shading for a teardrop runner: 1 radius below its center, 1.55 above */
    val tear: Bitmap by lazy { lensShade(tear = true) }

    private fun lensShade(tear: Boolean): Bitmap {
        val r = 48f
        val top = if (tear) r * 1.55f else r
        val bmp = Bitmap.createBitmap((r * 2).roundToInt(), (top + r).roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(r, top)
        val shape = Path()
        if (tear) {
            shape.moveTo(0f, -r * 1.55f)
            shape.cubicTo(r * 0.35f, -r * 1.1f, r, -r * 0.55f, r, 0f)
            shape.arcTo(-r, -r, r, r, 0f, 180f, false)
            shape.cubicTo(-r, -r * 0.55f, -r * 0.35f, -r * 1.1f, 0f, -r * 1.55f)
        } else {
            shape.addCircle(0f, 0f, r, Path.Direction.CW)
        }
        c.clipPath(shape)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val all = RectF(-r * 2, -top - r, r * 2, r * 2)
        // A touch brighter than the world: water gathers light
        p.color = argb(0.06f, 255, 255, 255)
        c.drawRect(all, p)
        // Top of the drop is dark (light bends away), the rim darkens, the bottom gathers light
        p.shader = LinearGradient(0f, -top, 0f, r * 0.2f, argb(0.38f, 8, 12, 20), 0, Shader.TileMode.CLAMP)
        c.drawRect(all, p)
        p.shader = RadialGradient(
            0f, r * 0.1f, r * 1.15f,
            intArrayOf(0, 0, argb(0.26f, 8, 12, 20), argb(0.5f, 8, 12, 20)),
            floatArrayOf(0f, 0.62f, 0.86f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(all, p)
        p.shader = RadialGradient(0f, r * 0.6f, r * 0.75f, argb(0.3f, 255, 255, 255), 0, Shader.TileMode.CLAMP)
        c.drawRect(all, p)
        p.shader = null
        p.color = argb(0.9f, 255, 255, 255)
        c.save()
        c.rotate(-28f, -r * 0.32f, -r * 0.48f)
        c.drawOval(-r * 0.49f, -r * 0.58f, -r * 0.15f, -r * 0.38f, p)
        c.restore()
        p.color = argb(0.5f, 255, 255, 255)
        c.drawCircle(r * 0.28f, r * 0.42f, r * 0.07f, p)
        return bmp
    }

    private fun argb(a: Float, r: Int, g: Int, b: Int) = android.graphics.Color.argb((a * 255f).roundToInt(), r, g, b)
}
