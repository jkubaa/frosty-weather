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

package org.breezyweather.ui.common.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import eightbitlab.com.blurview.BlurAlgorithm

/**
 * "Liquid glass" backdrop for BlurView, in the spirit of the iOS 26 material (and of Compose
 * libraries like Haze or Kyant's backdrop, which can't be used here since the home screen is made
 * of Android Views):
 *
 * 1. blur,
 * 2. vibrancy: the blurred colors are saturated, so glass picks up the color of what is behind it
 *    instead of greying it out,
 * 3. lens refraction (Android 13+): near the rounded edges the backdrop is bent towards the inside,
 *    like light through the thick rim of a glass lens. Deep inside the panel nothing moves.
 *
 * Everything runs on the GPU in a RenderNode, on BlurView's downscaled snapshot ([SCALE_FACTOR]).
 *
 * @param cornerRadiusPx corner radius of the panel on screen, to shape the refraction
 * @param edgePx width of the refracting band along the edges, on screen
 */
@RequiresApi(Build.VERSION_CODES.S)
class LiquidGlassBlur(
    private val cornerRadiusPx: Float,
    private val edgePx: Float,
) : BlurAlgorithm {

    private val node = RenderNode("LiquidGlass")
    private var width = -1
    private var height = -1
    private var lastRadius = -1f

    override fun blur(bitmap: Bitmap, blurRadius: Float): Bitmap {
        if (bitmap.width != width || bitmap.height != height || blurRadius != lastRadius) {
            width = bitmap.width
            height = bitmap.height
            lastRadius = blurRadius
            node.setPosition(0, 0, width, height)
            node.setRenderEffect(buildEffect(blurRadius))
        }
        val canvas = node.beginRecording()
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        node.endRecording()
        return bitmap
    }

    private fun buildEffect(blurRadius: Float): RenderEffect {
        val blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
        val vibrancy = RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(SATURATION) }),
            blur
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || width <= 0 || height <= 0) {
            return vibrancy
        }
        return refraction(vibrancy)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun refraction(input: RenderEffect): RenderEffect {
        val shader = RuntimeShader(REFRACTION_SHADER).apply {
            setFloatUniform("size", width.toFloat(), height.toFloat())
            // Snapshot coordinates are downscaled
            setFloatUniform("cornerRadius", (cornerRadiusPx / SCALE_FACTOR).coerceAtMost(minOf(width, height) / 2f))
            setFloatUniform("edge", (edgePx / SCALE_FACTOR).coerceAtLeast(1f))
            setFloatUniform("strength", (edgePx / SCALE_FACTOR) * REFRACTION_STRENGTH)
        }
        return RenderEffect.createRuntimeShaderEffect(shader, "content").let {
            RenderEffect.createChainEffect(it, input)
        }
    }

    override fun render(canvas: Canvas, bitmap: Bitmap) {
        if (canvas.isHardwareAccelerated) {
            canvas.drawRenderNode(node)
        } else {
            // Software canvas (e.g. a screenshot): plain snapshot, no effect
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }
    }

    override fun destroy() {
        node.discardDisplayList()
    }

    override fun canModifyBitmap() = true

    override fun getSupportedBitmapConfig(): Bitmap.Config = Bitmap.Config.ARGB_8888

    override fun scaleFactor() = SCALE_FACTOR

    companion object {
        /** Same downscale as BlurView's own RenderEffect algorithm. */
        private const val SCALE_FACTOR = 6f

        /** Vibrancy: 1 = unchanged, iOS-like glass sits around 1.5 to 1.8. */
        internal const val SATURATION = 1.6f

        /** How far content is pulled at the very edge, relative to the band width. */
        internal const val REFRACTION_STRENGTH = 0.9f

        /**
         * Lens refraction near the edges of a rounded rectangle. For each pixel, the signed distance
         * to the rounded edge says how deep inside the panel it is; in the outer band, the backdrop
         * is sampled further inside (towards the center), increasingly so at the very edge.
         */
        internal const val REFRACTION_SHADER = """
            uniform shader content;
            uniform float2 size;
            uniform float cornerRadius;
            uniform float edge;
            uniform float strength;

            half4 main(float2 p) {
                float2 halfSize = size * 0.5;
                float2 fromCenter = p - halfSize;
                float2 q = abs(fromCenter) - (halfSize - cornerRadius);
                // Signed distance to the rounded edge: negative inside
                float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius;
                // 0 deep inside, 1 at the edge
                float t = clamp(1.0 + d / edge, 0.0, 1.0);
                float bend = t * t * strength;
                float len = max(length(fromCenter), 0.001);
                float2 towardCenter = -fromCenter / len;
                return content.eval(p + towardCenter * bend);
            }
        """
    }
}
