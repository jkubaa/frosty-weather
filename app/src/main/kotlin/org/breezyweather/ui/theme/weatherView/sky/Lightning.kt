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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

/**
 * Lightning that lights the storm from inside the clouds.
 *
 * Strikes are not simulated: every few-second slot decides from its own seed whether it strikes,
 * where, how it flickers and the shape of its bolt. So the sky and anything drawn over it (the bolt
 * kept sharp on the wet glass) always agree, with no state to share.
 */
internal class Strike(
    /** Where it flashes inside the clouds, in screen fractions */
    val x: Float,
    val y: Float,
    val start: Float,
    /** Brightness: 1 for a strike with a visible bolt, less for one hidden in the clouds */
    val strength: Float,
    /** Bolt shape seed, or null for a flash inside the clouds only */
    val bolt: Int?,
    /** Flickers: delay, brightness, length (s) */
    val pulses: FloatArray,
) {
    /** How bright it is at [time], 0..1 */
    fun brightness(time: Float): Float {
        val local = time - start
        var b = 0f
        var i = 0
        while (i < pulses.size) {
            val q = local - pulses[i]
            val length = pulses[i + 2]
            if (q >= 0f && q < length * 3f) b = max(b, pulses[i + 1] * exp(-q / (length * 0.45f)))
            i += 3
        }
        return b * strength
    }

    /** A faint glow left in the eye after the bolt */
    fun afterglow(time: Float): Float = (1f - (time - start) / 0.9f).coerceIn(0f, 1f) * 0.12f
}

private const val SLOT = 3f
private const val LIFETIME = 2.2f

/** Strikes that may be visible at [time] (at most the current slot's and the previous one's). */
internal fun strikesAt(time: Float, bolts: Boolean): List<Strike> {
    val slot = floor(time / SLOT).toInt()
    return listOfNotNull(strikeIn(slot - 1, bolts), strikeIn(slot, bolts)).filter {
        time >= it.start && time - it.start < LIFETIME
    }
}

private fun strikeIn(slot: Int, bolts: Boolean): Strike? {
    val random = Random(slot * 7919 + 13)
    if (random.nextFloat() < 0.2f) return null // a quiet moment
    val x = 0.15f + random.nextFloat() * 0.7f
    val y = 0.08f + random.nextFloat() * 0.22f
    val start = slot * SLOT + random.nextFloat() * 1.5f
    val withBolt = bolts && random.nextFloat() < 0.55f
    val strength = if (withBolt) 1f else if (random.nextFloat() < 0.5f) 0.35f else 0.7f
    val pulses = if (random.nextFloat() < 0.5f) {
        floatArrayOf(0f, 1f, 0.12f, 0.09f, 0.45f, 0.08f, 0.2f, 0.85f, 0.25f, 0.42f, 0.5f, 0.3f)
    } else {
        floatArrayOf(0f, 1f, 0.12f, 0.09f, 0.45f, 0.08f, 0.2f, 0.85f, 0.25f)
    }
    return Strike(x, y, start, strength, if (withBolt) random.nextInt() else null, pulses)
}

/** The flash lighting the sky behind the clouds, so it glows through their gaps. */
internal fun DrawScope.drawStrikeGlow(strikes: List<Strike>, time: Float, alpha: Float) {
    strikes.forEach { strike ->
        val b = strike.brightness(time) * alpha
        if (b < 0.01f) return@forEach
        val center = Offset(strike.x * size.width, strike.y * size.height)
        val radius = size.width * 0.8f
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFA0AFFF).copy(alpha = 0.55f),
                0.3f to Color(0xFFA0AFFF).copy(alpha = 0.25f),
                0.65f to Color(0xFFA0AFFF).copy(alpha = 0.07f),
                1f to Color.Transparent,
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center,
            alpha = b.coerceIn(0f, 1f),
            blendMode = BlendMode.Plus
        )
    }
}

/**
 * The clouds lit from inside where it flashes. Draw into a layer holding only the clouds: the
 * light lands on them alone.
 */
internal fun DrawScope.drawStrikeInClouds(strikes: List<Strike>, time: Float, alpha: Float) {
    strikes.forEach { strike ->
        val b = strike.brightness(time) * alpha
        if (b < 0.01f) return@forEach
        val center = Offset(strike.x * size.width, strike.y * size.height)
        val radius = size.width * 0.6f
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFD2DAFF).copy(alpha = 0.85f),
                0.3f to Color(0xFFD2DAFF).copy(alpha = 0.38f),
                0.65f to Color(0xFFD2DAFF).copy(alpha = 0.1f),
                1f to Color.Transparent,
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center,
            alpha = b.coerceIn(0f, 1f),
            blendMode = BlendMode.SrcAtop
        )
    }
}

/**
 * Branching bolts from the clouds to below the screen, with a soft halo, and the whole sky
 * brightening for an instant.
 *
 * @param coreOnly just the thin bright core and a lighter flash (drawn over the wet glass)
 */
internal fun DrawScope.drawStrikeBolts(strikes: List<Strike>, time: Float, alpha: Float, coreOnly: Boolean = false) {
    var flash = 0f
    strikes.forEach { strike ->
        val b = strike.brightness(time)
        flash = max(flash, b)
        val seed = strike.bolt ?: return@forEach
        val shown = (if (coreOnly) b else max(b, strike.afterglow(time))) * alpha
        if (shown < 0.02f) return@forEach
        boltPaths(seed, strike.x * size.width, strike.y * size.height, size.height * 1.05f).forEach { (path, weight) ->
            if (!coreOnly) {
                drawPath(path, Color(0xFF96AAFF), alpha = (0.16f * shown).coerceIn(0f, 1f), style = boltStroke(9f * weight), blendMode = BlendMode.Plus)
                drawPath(path, Color(0xFFC8D2FF), alpha = (0.5f * shown).coerceIn(0f, 1f), style = boltStroke(3.5f * weight), blendMode = BlendMode.Plus)
                drawPath(path, Color(0xFFF5F7FF), alpha = shown.coerceIn(0f, 1f), style = boltStroke(1.4f * weight), blendMode = BlendMode.Plus)
            } else {
                drawPath(path, Color(0xFFEBF0FF), alpha = (0.55f * shown).coerceIn(0f, 1f), style = boltStroke(1.2f * weight), blendMode = BlendMode.Plus)
            }
        }
    }
    val flashAlpha = (if (coreOnly) 0.1f else 0.16f) * flash * alpha
    if (flashAlpha > 0.005f) drawRect(Color(0xFFBEC8FF), alpha = flashAlpha.coerceIn(0f, 1f), blendMode = BlendMode.Plus)
}

private fun DrawScope.boltStroke(width: Float) = Stroke(width = width * density, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** The main channel by midpoint displacement, then a few branches off it. Same seed, same bolt. */
private fun DrawScope.boltPaths(seed: Int, x0: Float, y0: Float, bottom: Float): List<Pair<Path, Float>> {
    val random = Random(seed)
    val unit = size.width / 300f
    val main = displace(x0, y0 + 20f * unit, x0 + (random.nextFloat() - 0.5f) * 140f * unit, bottom, 110f * unit, random, 7)
    val paths = mutableListOf(main.toPath() to 1f)
    repeat(4) {
        val i = (main.size / 2 * (0.15f + random.nextFloat() * 0.55f)).toInt() * 2
        val bx = main[i]
        val by = main[i + 1]
        val branch = displace(
            bx, by,
            bx + (random.nextFloat() - 0.5f) * 220f * unit,
            by + (70f + random.nextFloat() * 150f) * unit,
            50f * unit, random, 5
        )
        paths += branch.toPath() to 0.45f
    }
    return paths
}

/** Points x0, y0, x1, y1, ... from (ax, ay) down to (bx, by), jagged sideways. */
private fun displace(ax: Float, ay: Float, bx: Float, by: Float, spread: Float, random: Random, depth: Int): FloatArray {
    var points = floatArrayOf(ax, ay, bx, by)
    var disp = spread
    repeat(depth) {
        val next = FloatArray(points.size * 2 - 2)
        var o = 0
        var i = 0
        while (i < points.size - 2) {
            next[o++] = points[i]
            next[o++] = points[i + 1]
            next[o++] = (points[i] + points[i + 2]) / 2f + (random.nextFloat() - 0.5f) * disp
            next[o++] = (points[i + 1] + points[i + 3]) / 2f
            i += 2
        }
        next[o++] = points[points.size - 2]
        next[o] = points[points.size - 1]
        points = next
        disp *= 0.55f
    }
    return points
}

private fun FloatArray.toPath() = Path().apply {
    moveTo(this@toPath[0], this@toPath[1])
    var i = 2
    while (i < size) {
        lineTo(this@toPath[i], this@toPath[i + 1])
        i += 2
    }
}
