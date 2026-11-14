/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.ui.details.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.breezyweather.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The moon as it looks at a given phase: NASA's lunar map (LRO imagery, from the NASA Scientific
 * Visualization Studio CGI Moon Kit, public domain) wrapped on a sphere and lit by the sun from the
 * phase's angle. Lit on the GPU (Android 13+), so scrubbing through time costs nothing; older
 * versions light a smaller image on the CPU.
 *
 * @param phase -180 (new, waxing) .. 0 (full) .. 180 (new, waning), as suncalc gives it
 * @param southern the moon is seen upside down from the southern hemisphere
 */
@Composable
fun RealisticMoon(
    phase: Float,
    southern: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surface by produceState(MoonSurface.cached, context) {
        if (value == null) value = withContext(Dispatchers.IO) { MoonSurface.load(context) }
    }
    val illumination = ((1.0 + cos(Math.toRadians(phase.toDouble()))) / 2.0).toFloat()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val gpu = remember { GpuMoon() }
        Canvas(modifier) {
            val radius = size.minDimension / 2f
            drawGlow(radius, illumination)
            val bitmap = surface ?: return@Canvas
            drawIntoCanvas { canvas ->
                gpu.draw(canvas.nativeCanvas, bitmap, center.x, center.y, radius, phase, southern)
            }
        }
    } else {
        var image by remember { mutableStateOf<ImageBitmap?>(null) }
        val currentPhase by rememberUpdatedState(phase)
        LaunchedEffect(surface) {
            val bitmap = surface ?: return@LaunchedEffect
            snapshotFlow { currentPhase }.collectLatest { p ->
                image = withContext(Dispatchers.Default) { CpuMoon.render(bitmap, p, southern) }
            }
        }
        Canvas(modifier) {
            val radius = size.minDimension / 2f
            drawGlow(radius, illumination)
            image?.let {
                val side = (radius * 2f).roundToInt()
                drawImage(
                    it,
                    dstOffset = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()),
                    dstSize = IntSize(side, side),
                    filterQuality = FilterQuality.High
                )
            }
        }
    }
}

private fun DrawScope.drawGlow(radius: Float, illumination: Float) {
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.09f * illumination), Color.Transparent),
            center = center,
            radius = radius * 1.35f
        ),
        radius * 1.35f,
        center
    )
}

/** Sun direction for a phase, in the disc's frame as seen from the north (x right, y up, z to us). */
private fun lightFor(phase: Float): FloatArray {
    val elongation = Math.toRadians(180.0 - abs(phase.toDouble()))
    val side = if (phase < 0f) 1.0 else -1.0 // waxing: lit from the right
    return floatArrayOf((side * sin(elongation)).toFloat(), 0f, (-cos(elongation)).toFloat())
}

private object MoonSurface {
    @Volatile
    var cached: Bitmap? = null
        private set

    fun load(context: Context): Bitmap {
        cached?.let { return it }
        val options = BitmapFactory.Options().apply { inScaled = false }
        return BitmapFactory.decodeResource(context.resources, R.drawable.moon_surface, options)
            .also { cached = it }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GpuMoon {
    private val shader = RuntimeShader(SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@GpuMoon.shader }
    private var boundBitmap: Bitmap? = null

    fun draw(
        canvas: android.graphics.Canvas,
        bitmap: Bitmap,
        cx: Float,
        cy: Float,
        radius: Float,
        phase: Float,
        southern: Boolean,
    ) {
        if (boundBitmap !== bitmap) {
            boundBitmap = bitmap
            shader.setInputShader(
                "surface",
                BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP).apply {
                    filterMode = BitmapShader.FILTER_MODE_LINEAR
                }
            )
            shader.setFloatUniform("surfaceSize", bitmap.width.toFloat(), bitmap.height.toFloat())
        }
        val light = lightFor(phase)
        shader.setFloatUniform("center", cx, cy)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("light", light[0], light[1], light[2])
        shader.setFloatUniform("flip", if (southern) -1f else 1f)
        canvas.drawCircle(cx, cy, radius + 1f, paint)
    }

    companion object {
        private const val SHADER = """
            uniform shader surface;
            uniform float2 surfaceSize;
            uniform float2 center;
            uniform float radius;
            uniform float3 light;
            uniform float flip;

            // The map is bright, flat and colour-enhanced: mostly grey, with its contrast stretched
            float3 grade(float3 c) {
                float l = dot(c, float3(0.299, 0.587, 0.114));
                c = mix(float3(l), c, 0.18);
                c = clamp((c - 0.38) / 0.55, 0.0, 1.0);
                return pow(c, float3(1.15)) * 0.95 + 0.03;
            }

            half4 main(float2 p) {
                float2 q = (p - center) / radius;
                q.y = -q.y;
                q *= flip;
                float rr = dot(q, q);
                if (rr > 1.0) return half4(0.0);
                float z = sqrt(1.0 - rr);
                float lat = asin(clamp(q.y, -1.0, 1.0));
                float lon = atan(q.x, z);
                float2 uv = float2(0.5 + lon / 6.2831853, 0.5 - lat / 3.1415927) * surfaceSize;
                float3 albedo = grade(surface.eval(uv).rgb);

                float cosI = q.x * light.x + q.y * light.y + z * light.z;
                float cosE = max(z, 0.05);
                // Lommel-Seeliger: the moon shows almost no limb darkening
                float lit = cosI > 0.0 ? 2.0 * cosI / (cosI + cosE) : 0.0;
                float terminator = smoothstep(-0.015, 0.06, cosI);
                float3 color = albedo * min(lit, 1.2) * terminator * 1.05;
                color += albedo * 0.035 * (1.0 - terminator); // earthshine
                color *= float3(1.0, 0.985, 0.96);

                float edge = clamp((1.0 - sqrt(rr)) * radius, 0.0, 1.0);
                return half4(half3(color * edge), half(edge));
            }
        """
    }
}

/** The same lighting on the CPU, for Android 12 and older. */
private object CpuMoon {
    private const val N = 360

    @Volatile
    private var texture: IntArray? = null

    fun render(surface: Bitmap, phase: Float, southern: Boolean): ImageBitmap {
        val w = surface.width
        val h = surface.height
        val tex = texture ?: IntArray(w * h).also {
            surface.getPixels(it, 0, w, 0, 0, w, h)
            texture = it
        }
        val light = lightFor(phase)
        val pixels = IntArray(N * N)
        val flip = if (southern) -1f else 1f
        for (j in 0 until N) {
            for (i in 0 until N) {
                val x = (2f * (i + 0.5f) / N - 1f) * flip
                val y = (1f - 2f * (j + 0.5f) / N) * flip
                val rr = x * x + y * y
                if (rr > 1f) continue
                val z = sqrt(1f - rr)
                val lat = asin(y.coerceIn(-1f, 1f))
                val lon = atan2(x, z)
                val u = ((0.5f + lon / (2f * PI.toFloat())) * w).toInt().coerceIn(0, w - 1)
                val v = ((0.5f - lat / PI.toFloat()) * h).toInt().coerceIn(0, h - 1)
                val c = tex[v * w + u]
                val cosI = x * light[0] + y * light[1] + z * light[2]
                val cosE = max(z, 0.05f)
                val lit = if (cosI > 0f) 2f * cosI / (cosI + cosE) else 0f
                val t = ((cosI + 0.015f) / 0.075f).coerceIn(0f, 1f)
                val terminator = t * t * (3f - 2f * t)
                val k = min(lit, 1.2f) * terminator * 1.05f + 0.035f * (1f - terminator)
                val edge = ((1f - sqrt(rr)) * N / 2f).coerceIn(0f, 1f)
                val l = (((c shr 16) and 0xFF) * 0.299f + ((c shr 8) and 0xFF) * 0.587f + (c and 0xFF) * 0.114f) / 255f
                fun channel(shift: Int, tint: Float): Int {
                    val raw = ((c shr shift) and 0xFF) / 255f
                    val g = ((l + (raw - l) * 0.18f - 0.38f) / 0.55f).coerceIn(0f, 1f).pow(1.15f) * 0.95f + 0.03f
                    return (g * k * tint * edge * 255f).toInt().coerceIn(0, 255)
                }
                pixels[j * N + i] = ((edge * 255f).toInt() shl 24) or
                    (channel(16, 1f) shl 16) or (channel(8, 0.985f) shl 8) or channel(0, 0.96f)
            }
        }
        val bitmap = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, N, 0, 0, N, N)
        return bitmap.asImageBitmap()
    }
}
