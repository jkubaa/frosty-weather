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

package org.breezyweather.ui.main.widgets

import android.text.Spanned
import android.text.style.RelativeSizeSpan
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Small graphics of the liquid glass tiles and cards, in Compose.
 */

/**
 * Horizontal color scale (e.g. UV or air quality levels) with a white dot at [fraction]
 * (0 start .. 1 end); null hides the dot.
 */
@Composable
fun ScaleBar(colors: List<Color>, fraction: Float?, modifier: Modifier = Modifier) {
    val scale = if (colors.size >= 2) colors else List(2) { colors.firstOrNull() ?: Color.White }
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val dot = 4.dp.toPx()
        val ring = 2.dp.toPx()
        val bar = 5.dp.toPx()
        val cy = size.height / 2f
        drawRoundRect(
            brush = Brush.horizontalGradient(scale, startX = dot, endX = (size.width - dot).coerceAtLeast(dot + 1f)),
            topLeft = Offset(dot - bar / 2f, cy - bar / 2f),
            size = Size(size.width - 2 * dot + bar, bar),
            cornerRadius = CornerRadius(bar / 2f)
        )
        fraction?.coerceIn(0f, 1f)?.let {
            val center = Offset(dot + it * (size.width - 2 * dot), cy)
            drawCircle(Color.Black.copy(alpha = 0.35f), dot + ring / 2f, center, style = Stroke(ring))
            drawCircle(Color.White, dot, center)
        }
    }
}

/**
 * Small bar chart: one rounded bar per value (0..1, null for no data: track only), filled from the
 * bottom with [color], lighter at the top.
 */
@Composable
fun MiniBars(values: List<Float?>, color: Color, content: Color, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val track = content.copy(alpha = 28 / 255f)
    val fill = Brush.verticalGradient(listOf(lerp(color, Color.White, 0.35f), color))
    Canvas(modifier.fillMaxWidth().height(34.dp)) {
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        if (barWidth <= 0f) return@Canvas
        val radius = min(barWidth / 2f, 3.dp.toPx())
        values.forEachIndexed { i, v ->
            val left = i * (barWidth + gap)
            drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), CornerRadius(radius))
            v?.coerceIn(0f, 1f)?.takeIf { it > 0f }?.let {
                val h = (size.height * it).coerceAtLeast(radius * 2)
                drawRoundRect(fill, Offset(left, size.height - h), Size(barWidth, h), cornerRadius = CornerRadius(radius))
            }
        }
    }
}

/** Wind compass: ticks, N/E/S/W, and an arrow towards where the wind blows ([degree] null: no arrow). */
@Composable
fun Compass(degree: Float?, content: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) { drawCompass(degree, content, measurer) }
}

private fun DrawScope.drawCompass(degree: Float?, content: Color, measurer: TextMeasurer) {
    val c = center
    val radius = size.minDimension / 2f - 2.dp.toPx()
    if (radius <= 0f) return
    for (i in 0 until 36) {
        if (i % 9 == 0) continue // letters go there
        val angle = Math.toRadians(i * 10.0 - 90)
        val inner = radius - (if (i % 3 == 0) 5.dp else 3.dp).toPx()
        drawLine(
            content.copy(alpha = if (i % 3 == 0) 160 / 255f else 90 / 255f),
            Offset(c.x + (cos(angle) * inner).toFloat(), c.y + (sin(angle) * inner).toFloat()),
            Offset(c.x + (cos(angle) * radius).toFloat(), c.y + (sin(angle) * radius).toFloat()),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
    val letterSize = radius * 0.28f
    val letterStyle = TextStyle(
        color = content.copy(alpha = 200 / 255f),
        fontSize = (letterSize / density / fontScale).let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) },
        fontWeight = FontWeight.Bold
    )
    val letterRadius = radius - letterSize * 0.55f
    listOf("N" to -90.0, "E" to 0.0, "S" to 90.0, "W" to 180.0).forEach { (letter, deg) ->
        val layout = measurer.measure(letter, letterStyle)
        val angle = Math.toRadians(deg)
        drawText(
            layout,
            topLeft = Offset(
                c.x + (cos(angle) * letterRadius).toFloat() - layout.size.width / 2f,
                c.y + (sin(angle) * letterRadius).toFloat() - layout.size.height / 2f
            )
        )
    }
    val d = degree ?: return
    val length = radius * 0.58f
    val stroke = 2.5.dp.toPx()
    rotate(d, c) {
        drawLine(content, Offset(c.x, c.y - length), Offset(c.x, c.y + length), stroke, StrokeCap.Round)
        val head = radius * 0.2f
        val path = Path().apply {
            moveTo(c.x - head * 0.7f, c.y + length - head)
            lineTo(c.x, c.y + length)
            lineTo(c.x + head * 0.7f, c.y + length - head)
        }
        drawPath(path, content, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(content, 2.5.dp.toPx(), Offset(c.x, c.y - length))
    }
}

/**
 * Watch-complication ring, open at the bottom: filled to [fraction] in [color], with a dot at the
 * end of the fill. With no [fraction], a plain decorative ring in [color].
 */
@Composable
fun RingGauge(fraction: Float?, color: Color, content: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2f + 1.dp.toPx()
        val diameter = size.minDimension - inset * 2
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        val style = Stroke(stroke, cap = StrokeCap.Round)
        val f = fraction?.coerceIn(0f, 1f)
        if (f == null) {
            drawArc(color.copy(alpha = 190 / 255f), START_ANGLE, SWEEP, false, topLeft, arcSize, style = style)
            return@Canvas
        }
        drawArc(content.copy(alpha = 50 / 255f), START_ANGLE, SWEEP, false, topLeft, arcSize, style = style)
        if (f > 0f) drawArc(color, START_ANGLE, SWEEP * f, false, topLeft, arcSize, style = style)
        val angle = Math.toRadians((START_ANGLE + SWEEP * f).toDouble())
        val r = diameter / 2f
        drawCircle(
            Color.White,
            stroke * 0.42f,
            Offset(center.x + (cos(angle) * r).toFloat(), center.y + (sin(angle) * r).toFloat())
        )
    }
}

/** From bottom-left (135°), clockwise over the top, to bottom-right: open at the bottom. */
private const val START_ANGLE = 135f
private const val SWEEP = 270f

/** Keeps the half-size units of [org.breezyweather.common.utils.UnitUtils.formatUnitsHalfSize] in Compose text. */
fun CharSequence.toAnnotatedString(): AnnotatedString {
    if (this !is Spanned) return AnnotatedString(toString())
    val spanned = this
    return buildAnnotatedString {
        append(spanned.toString())
        spanned.getSpans(0, spanned.length, RelativeSizeSpan::class.java).forEach { span ->
            addStyle(SpanStyle(fontSize = span.sizeChange.em), spanned.getSpanStart(span), spanned.getSpanEnd(span))
        }
    }
}
