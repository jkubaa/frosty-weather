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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import kotlinx.coroutines.delay
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import java.util.Date
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Clock tile: the time at the location, an analog clock with a running second hand, and the name
 * of the location's time zone. Handy for places in another time zone.
 */
class ClockViewHolder(context: Context) : ComposeTileViewHolder(context) {

    private val location = mutableStateOf<Location?>(null)
    private val now = mutableLongStateOf(System.currentTimeMillis())

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        this.location.value = location
        itemView.contentDescription = activity.getString(R.string.clock) + activity.getString(R.string.colon_separator) +
            Date().getFormattedTime(location, activity, activity.is12Hour)
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(activity, location.formattedId, location.weather!!.todayIndex, DetailScreen.TAG_CONDITIONS)
        }
    }

    @Composable
    override fun ColumnScope.Content(wide: Boolean) {
        val location = location.value ?: return
        val context = itemView.context
        // Ticks every second, on the second
        LaunchedEffect(Unit) {
            while (true) {
                now.longValue = System.currentTimeMillis()
                delay(1000 - now.longValue % 1000)
            }
        }
        val millis = now.longValue
        val timeZone: TimeZone = location.timeZone
        TileHeader(R.drawable.ic_time, context.getString(R.string.clock))
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                // Recomputed each minute only in practice: the text is the same within a minute
                TileValue(Date(millis).getFormattedTime(location, context, context.is12Hour))
                Text(
                    text = timeZone.getDisplayName(timeZone.inDaylightTime(Date(millis)), TimeZone.LONG, context.currentLocale),
                    color = contentColor.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (wide) {
                AnalogClock(millis, timeZone, Modifier.padding(start = 12.dp).fillMaxHeight().aspectRatio(1f))
            }
        }
        if (!wide) {
            AnalogClock(
                millis,
                timeZone,
                Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp)
                    .fillMaxWidth(0.42f)
                    .aspectRatio(1f)
            )
        }
    }

    @Composable
    private fun AnalogClock(millis: Long, timeZone: TimeZone, modifier: Modifier) {
        val content = contentColor
        Canvas(modifier) { drawClock(millis + timeZone.getOffset(millis), content) }
    }
}

private val SECOND_HAND = Color(0xFFFFA43A)

private fun DrawScope.drawClock(localMillis: Long, content: Color) {
    val radius = size.minDimension / 2f
    if (radius <= 0f) return
    val c = center
    val seconds = Math.floorMod(localMillis / 1000, 86_400L)
    val second = (seconds % 60).toFloat()
    val minute = (seconds / 60 % 60).toFloat() + second / 60f
    val hour = (seconds / 3600 % 12).toFloat() + minute / 60f

    drawCircle(content.copy(alpha = 0.08f), radius, c)
    for (i in 0 until 12) {
        val angle = i * PI / 6 - PI / 2
        val outer = radius * 0.92f
        val inner = radius * if (i % 3 == 0) 0.76f else 0.82f
        drawLine(
            content.copy(alpha = if (i % 3 == 0) 0.9f else 0.5f),
            Offset(c.x + (cos(angle) * inner).toFloat(), c.y + (sin(angle) * inner).toFloat()),
            Offset(c.x + (cos(angle) * outer).toFloat(), c.y + (sin(angle) * outer).toFloat()),
            strokeWidth = (if (i % 3 == 0) 2.dp else 1.dp).toPx(),
            cap = StrokeCap.Round
        )
    }
    fun hand(turns: Float, length: Float, width: Float, color: Color) {
        val angle = turns * 2 * PI - PI / 2
        drawLine(
            color,
            c,
            Offset(c.x + (cos(angle) * radius * length).toFloat(), c.y + (sin(angle) * radius * length).toFloat()),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
    }
    hand(hour / 12f, 0.5f, 3.5.dp.toPx(), content)
    hand(minute / 60f, 0.74f, 2.5.dp.toPx(), content)
    hand(second / 60f, 0.82f, 1.2.dp.toPx(), SECOND_HAND)
    drawCircle(SECOND_HAND, 2.5.dp.toPx(), c)
}
