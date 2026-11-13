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

import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

/**
 * Light shafts (crepuscular rays): the sun's light, with the clouds cut out of it, smeared outwards
 * from the sun. Where the clouds have gaps, bright shafts fan out; they shift as the clouds drift.
 *
 * Built from GPU display lists, no shader: a quarter-size layer holds the light minus the clouds,
 * and it is drawn a dozen times, each copy scaled a little further out from the sun. Android 10+
 * and hardware canvases only; elsewhere the sun simply has no shafts.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private object LightShafts {
    const val SCALE = 0.25f
    const val COPIES = 14
    const val SPREAD = 0.07f

    val source = RenderNode("SkyShaftsSource")
    val shafts = RenderNode("SkyShafts")
    val paint = android.graphics.Paint().apply { blendMode = android.graphics.BlendMode.PLUS }
}

/**
 * @param radius how far the sun's light reaches before it is cut by the clouds
 * @param clouds draws the clouds exactly as on screen, with the given blend mode
 */
internal fun DrawScope.drawLightShafts(
    center: Offset,
    radius: Float,
    color: Color,
    strength: Float,
    clouds: DrawScope.(BlendMode) -> Unit,
) {
    if (strength <= 0.01f || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val canvas = drawContext.canvas.nativeCanvas
    if (!canvas.isHardwareAccelerated) return
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    // A plain value: scopes must not hand their density to each other (recursion)
    val plainDensity = Density(density, fontScale)

    val source = LightShafts.source
    source.setPosition(0, 0, (w * LightShafts.SCALE).roundToInt().coerceAtLeast(1), (h * LightShafts.SCALE).roundToInt().coerceAtLeast(1))
    // Its own layer, so the clouds can erase the light inside it
    source.setUseCompositingLayer(true, null)
    source.alpha = 0.13f
    val sourceCanvas = source.beginRecording()
    sourceCanvas.scale(LightShafts.SCALE, LightShafts.SCALE)
    CanvasDrawScope().draw(plainDensity, layoutDirection, androidx.compose.ui.graphics.Canvas(sourceCanvas), Size(w, h)) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color,
                0.3f to color.copy(alpha = 0.45f),
                0.65f to color.copy(alpha = 0.12f),
                1f to Color.Transparent,
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        // Twice: the soft vapour should still cut the light cleanly
        clouds(BlendMode.DstOut)
        clouds(BlendMode.DstOut)
    }
    source.endRecording()

    val shafts = LightShafts.shafts
    shafts.setPosition(0, 0, w.roundToInt(), h.roundToInt())
    LightShafts.paint.alpha = (strength.coerceIn(0f, 1f) * 255f).roundToInt()
    shafts.setUseCompositingLayer(true, LightShafts.paint)
    val shaftCanvas = shafts.beginRecording()
    for (i in 0 until LightShafts.COPIES) {
        val k = (1f + i * LightShafts.SPREAD) / LightShafts.SCALE
        shaftCanvas.save()
        shaftCanvas.translate(center.x, center.y)
        shaftCanvas.scale(k, k)
        shaftCanvas.translate(-center.x * LightShafts.SCALE, -center.y * LightShafts.SCALE)
        shaftCanvas.drawRenderNode(source)
        shaftCanvas.restore()
    }
    shafts.endRecording()
    canvas.drawRenderNode(shafts)
}

/**
 * Lens flare: faint ghosts along the line from the sun through the middle of the screen, and a
 * thin horizontal streak through the sun. [visibility] fades it when a cloud covers the sun.
 */
internal fun DrawScope.drawLensFlare(sun: Offset, visibility: Float) {
    if (visibility <= 0.02f) return
    val middle = Offset(size.width / 2f, size.height / 2f)
    val unit = size.width / 300f // the ghosts were sized for a 300 px wide screen
    for ((at, radius, color, strength) in FLARE_GHOSTS) {
        val c = sun + (middle - sun) * (at * 1.4f)
        val r = radius * unit
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = strength * 0.5f),
                0.8f to color.copy(alpha = strength),
                1f to Color.Transparent,
                center = c,
                radius = r
            ),
            radius = r,
            center = c,
            alpha = visibility.coerceIn(0f, 1f),
            blendMode = BlendMode.Plus
        )
    }
    // Anamorphic streak
    scale(scaleX = 1f, scaleY = 0.035f, pivot = sun) {
        val r = size.width * 0.6f
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFFFF0DC).copy(alpha = 0.4f),
                0.3f to Color(0xFFFFF0DC).copy(alpha = 0.18f),
                1f to Color.Transparent,
                center = sun,
                radius = r
            ),
            radius = r,
            center = sun,
            alpha = visibility.coerceIn(0f, 1f),
            blendMode = BlendMode.Plus
        )
    }
}

private data class Ghost(val at: Float, val radius: Float, val color: Color, val strength: Float)

private val FLARE_GHOSTS = listOf(
    Ghost(0.8f, 30f, Color(0xFFC8B4FF), 0.06f),
    Ghost(1.15f, 14f, Color(0xFFFFE6B4), 0.11f),
    Ghost(1.5f, 44f, Color(0xFFA0C8FF), 0.05f),
)
