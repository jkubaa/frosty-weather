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

package org.breezyweather.ui.main.adapters.main.holder

import android.content.Context
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import java.util.Date
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.time.Duration.Companion.days

/**
 * Sun tile, iOS weather style: the next sunrise or sunset as the big value, the day's path of the
 * sun as a curve crossing the horizon at sunrise and sunset, the sun on it at the current time,
 * and the other event of the day underneath.
 */
class SunViewHolder(context: Context) : ComposeTileViewHolder(context) {

    private val model = mutableStateOf<SunModel?>(null)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        model.value = SunModel.from(activity, location, itemAnimationEnabled)
        // Screen readers: both times in one sentence
        itemView.contentDescription = model.value?.description
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(
                activity,
                location.formattedId,
                location.weather!!.todayIndex,
                DetailScreen.TAG_SUN_MOON
            )
        }
    }

    @Composable
    override fun ColumnScope.Content(wide: Boolean) {
        val sun = model.value ?: return
        TileHeader(R.drawable.weather_clear_day_mini_xml, sun.title)
        TileValue(sun.value)
        SunPath(
            sun = sun,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp)
        )
        sun.other?.let {
            Text(
                text = it,
                color = contentColor.copy(alpha = 0.85f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    private fun SunPath(sun: SunModel, modifier: Modifier) {
        // The sun travels from sunrise (or midnight) to now when the tile first shows up
        val position = remember { Animatable(if (sun.animate) sun.startFraction else sun.nowFraction) }
        LaunchedEffect(sun.nowFraction, sun.animate) {
            if (sun.animate) {
                position.animateTo(sun.nowFraction, tween(1400, easing = FastOutSlowInEasing))
            } else {
                position.snapTo(sun.nowFraction)
            }
        }
        val line = contentColor
        Canvas(modifier) {
            drawSunPath(sun, position.value, line)
        }
    }
}

/**
 * @param riseFraction, setFraction, nowFraction times of day, 0 at local midnight, 1 at the next one
 * @param horizon height of the horizon on the curve, -1..1 (beyond for polar day / night)
 */
@Immutable
private data class SunModel(
    val title: String,
    val value: String,
    val other: String?,
    val description: String,
    val noonFraction: Float,
    val horizon: Float,
    val nowFraction: Float,
    val startFraction: Float,
    val animate: Boolean,
) {
    companion object {
        private const val DAY_MS = 86_400_000L

        private fun dayFraction(date: Date, timeZone: TimeZone): Float {
            val local = date.time + timeZone.getOffset(date.time)
            return (Math.floorMod(local, DAY_MS)).toFloat() / DAY_MS
        }

        fun from(activity: BreezyActivity, location: Location, animate: Boolean): SunModel? {
            val weather = location.weather ?: return null
            val today = weather.today ?: return null
            val sun = today.sun ?: return null
            val tz = location.timeZone
            val now = Date()
            val nowFraction = dayFraction(now, tz)
            fun time(date: Date) = date.getFormattedTime(location, activity, activity.is12Hour)

            val rise = sun.riseDate
            val set = sun.setDate
            val polarDay = rise != null && set != null && set.time == rise.time + 1.days.inWholeMilliseconds - 1
            if (rise == null || set == null || polarDay) {
                // Polar day: the whole curve above the horizon; polar night: all of it below
                val text = activity.getString(if (polarDay) R.string.ephemeris_polar_day else R.string.ephemeris_polar_night)
                return SunModel(
                    title = activity.getString(R.string.ephemeris_sun),
                    value = text,
                    other = null,
                    description = text,
                    noonFraction = 0.5f,
                    horizon = if (polarDay) -1.3f else 1.3f,
                    nowFraction = nowFraction,
                    startFraction = 0f,
                    animate = animate
                )
            }

            val riseFraction = dayFraction(rise, tz)
            val setFraction = dayFraction(set, tz).let { if (it < riseFraction) it + 1f else it }
            val noon = (riseFraction + setFraction) / 2f
            val halfDay = (setFraction - riseFraction) / 2f
            val tomorrowSun = weather.dailyForecast.getOrNull((weather.todayIndex ?: 0) + 1)?.sun

            val (title, value, other) = when {
                now.before(rise) -> Triple(
                    activity.getString(R.string.highlight_label_sunrise),
                    time(rise),
                    activity.getString(R.string.tile_sunset_at, time(set))
                )
                now.before(set) -> Triple(
                    activity.getString(R.string.highlight_label_sunset),
                    time(set),
                    activity.getString(R.string.tile_sunrise_at, time(rise))
                )
                else -> Triple(
                    activity.getString(R.string.highlight_label_sunrise),
                    time(tomorrowSun?.riseDate ?: rise),
                    activity.getString(R.string.tile_sunset_at, time(set))
                )
            }
            return SunModel(
                title = title,
                value = value,
                other = other,
                description = listOfNotNull(
                    activity.getString(R.string.ephemeris_sunrise_at, time(rise)),
                    activity.getString(R.string.ephemeris_sunset_at, time(set))
                ).joinToString(". "),
                noonFraction = noon,
                horizon = cos(2.0 * PI * halfDay).toFloat(),
                nowFraction = nowFraction,
                startFraction = if (now.after(rise)) riseFraction else 0f,
                animate = animate
            )
        }
    }
}

private val SUN_GLOW = Color(0xFFFFD36B)

/**
 * The sun's height over the day as a cosine curve peaking at solar noon, a hairline horizon where
 * it rises and sets, bright above the horizon and faint under it, and the sun at [position].
 */
private fun DrawScope.drawSunPath(sun: SunModel, position: Float, line: Color) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val margin = 7.dp.toPx()
    fun heightAt(fraction: Float) = cos(2.0 * PI * (fraction - sun.noonFraction)).toFloat()
    // -1..1 mapped to the drawing area, top = highest
    fun y(value: Float) = margin + (1f - (value + 1f) / 2f) * (h - margin * 2f)

    val path = Path()
    val steps = 72
    for (i in 0..steps) {
        val f = i / steps.toFloat()
        val point = Offset(f * w, y(heightAt(f)))
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    val horizonY = y(sun.horizon.coerceIn(-1.2f, 1.2f))
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)

    clipRect(bottom = horizonY) { drawPath(path, line.copy(alpha = 0.9f), style = stroke) }
    clipRect(top = horizonY) { drawPath(path, line.copy(alpha = 0.3f), style = stroke) }
    if (horizonY in 0f..h) {
        drawLine(line.copy(alpha = 0.35f), Offset(0f, horizonY), Offset(w, horizonY), strokeWidth = 1.dp.toPx())
    }

    val sunPoint = Offset(position.coerceIn(0f, 1f) * w, y(heightAt(position)))
    val up = sunPoint.y <= horizonY
    val radius = 5.5.dp.toPx()
    if (up) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SUN_GLOW.copy(alpha = 0.55f), Color.Transparent),
                center = sunPoint,
                radius = radius * 4f
            ),
            radius = radius * 4f,
            center = sunPoint
        )
    }
    drawCircle(if (up) Color.White else line.copy(alpha = 0.55f), radius, sunPoint)
}
