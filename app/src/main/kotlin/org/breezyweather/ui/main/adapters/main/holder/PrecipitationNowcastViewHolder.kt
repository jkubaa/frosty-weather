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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Precipitation
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.formatMeasureIntensity
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.weather.model.getContentDescription
import org.breezyweather.domain.weather.model.getMinutelyDescription
import org.breezyweather.domain.weather.model.getMinutelyTitle
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import java.util.Date
import kotlin.math.max
import kotlin.time.Duration.Companion.minutes

/**
 * Precipitation in the next hour, iOS weather style, in Compose: the source's title and sentence,
 * then a bar every 5 minutes against light / medium / heavy guide lines, with the start, middle and
 * end times. Touch and drag along the chart to read a bar.
 */
class PrecipitationNowcastViewHolder(context: Context) : ComposeCardViewHolder(context) {

    @Immutable
    private class Bar(val start: Long, val micrometers: Double, val label: String)

    @Immutable
    private class Model(
        val title: String,
        val subtitle: String?,
        val bars: List<Bar>,
        val maxMicrometers: Double,
        val guides: List<Pair<Double, String>>,
        val times: List<String>,
        val description: String,
    )

    private val model = mutableStateOf<Model?>(null)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        val weather = location.weather ?: return
        val minutely = weather.minutelyForecast
        if (minutely.isEmpty()) return
        fun time(millis: Long) = Date(millis).getFormattedTime(location, activity, activity.is12Hour)

        val by5 = weather.minutelyForecastBy5Minutes
        val values = by5.map { (it.precipitationIntensity?.inMicrometers ?: 0.0) }
        // Bars whose values are only the thresholds read better by name than by amount
        val onlyThresholds = values.all {
            (it / 1000.0) in setOf(
                0.0,
                Precipitation.PRECIPITATION_HOURLY_LIGHT,
                Precipitation.PRECIPITATION_HOURLY_MEDIUM,
                Precipitation.PRECIPITATION_HOURLY_HEAVY
            )
        }
        val heavyMicrometers = Precipitation.PRECIPITATION_HOURLY_HEAVY.millimeters.inMicrometers
        val maxMicrometers = max(heavyMicrometers, values.maxOrNull() ?: 0.0)

        val bars = by5.mapIndexed { i, it ->
            val amount = when {
                onlyThresholds -> when (values[i] / 1000.0) {
                    0.0 -> activity.getString(R.string.precipitation_none)
                    Precipitation.PRECIPITATION_HOURLY_LIGHT -> activity.getString(R.string.precipitation_intensity_light)
                    Precipitation.PRECIPITATION_HOURLY_MEDIUM -> activity.getString(R.string.precipitation_intensity_medium)
                    else -> activity.getString(R.string.precipitation_intensity_heavy)
                }
                // "No precipitation" reads better than "0 in"
                values[i] <= 0.0 -> activity.getString(R.string.precipitation_none)
                else -> it.precipitationIntensity?.formatMeasureIntensity(activity) ?: activity.getString(R.string.precipitation_none)
            }
            Bar(
                start = it.date.time,
                micrometers = values[i],
                label = time(it.date.time) + "–" + time(it.date.time + 5.minutes.inWholeMilliseconds) +
                    activity.getString(R.string.colon_separator) + amount
            )
        }

        val guides = if (SettingsManager.getInstance(activity).isTrendHorizontalLinesEnabled) {
            listOf(
                Precipitation.PRECIPITATION_HOURLY_LIGHT to activity.getString(R.string.precipitation_intensity_light),
                Precipitation.PRECIPITATION_HOURLY_MEDIUM to activity.getString(R.string.precipitation_intensity_medium),
                Precipitation.PRECIPITATION_HOURLY_HEAVY to activity.getString(R.string.precipitation_intensity_heavy)
            ).map { (mm, label) -> mm * 1000.0 to label }
        } else {
            emptyList()
        }

        val start = minutely.first().date.time
        val end = minutely.last().endingDate.time
        model.value = Model(
            title = weather.getMinutelyTitle(activity),
            subtitle = weather.getMinutelyDescription(activity, location),
            bars = bars,
            maxMicrometers = maxMicrometers,
            guides = guides,
            times = listOf(time(start), time(start + (end - start) / 2), time(end)),
            description = minutely.getContentDescription(activity, location)
        )
    }

    @Composable
    override fun Content() {
        val m = model.value ?: return
        Column(Modifier.padding(top = 14.dp, bottom = 12.dp)) {
            GlassCardHeader(R.drawable.ic_precipitation, m.title, contentColor)
            m.subtitle?.takeIf { it.isNotEmpty() }?.let {
                Text(it, color = contentColor, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            }
            Chart(
                m,
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .fillMaxWidth()
                    .horizontalScrollRegion("chart")
                    .clearAndSetSemantics { contentDescription = m.description }
            )
        }
    }

    /**
     * Height of a bar, 0..1: light, medium and heavy each get a third of the chart (iOS style), so
     * light rain and drizzle show as real bars instead of slivers under a scale made for downpours.
     * Within the light band, small amounts are lifted a little more; past heavy, bars stay full.
     */
    private fun intensityHeight(micrometers: Double): Float {
        val light = Precipitation.PRECIPITATION_HOURLY_LIGHT * 1000.0
        val medium = Precipitation.PRECIPITATION_HOURLY_MEDIUM * 1000.0
        val heavy = Precipitation.PRECIPITATION_HOURLY_HEAVY * 1000.0
        val fraction = when {
            micrometers <= 0.0 -> 0.0
            micrometers <= light -> Math.pow(micrometers / light, 0.35) / 3.0
            micrometers <= medium -> 1.0 / 3.0 + (micrometers - light) / (medium - light) / 3.0
            micrometers <= heavy -> 2.0 / 3.0 + (micrometers - medium) / (heavy - medium) / 3.0
            else -> 1.0
        }
        return fraction.toFloat().coerceIn(0f, 1f)
    }

    @Composable
    private fun Chart(m: Model, modifier: Modifier) {
        var selected by remember(m) { mutableStateOf<Int?>(null) }
        val measurer = rememberTextMeasurer()
        val content = contentColor
        val labelStyle = TextStyle(color = content.copy(alpha = 0.6f), fontSize = 11.sp)
        Column(modifier) {
            // Reading of the touched bar, in place of nothing (keeps the chart from jumping)
            Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.Center) {
                selected?.let { m.bars.getOrNull(it) }?.let {
                    Text(
                        text = it.label,
                        color = content,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .background(content.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .pointerInput(m) {
                        fun pick(x: Float) {
                            selected = ((x / size.width) * m.bars.size).toInt().coerceIn(0, m.bars.lastIndex)
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { pick(it.x) },
                            onDragEnd = { selected = null },
                            onDragCancel = { selected = null }
                        ) { change, _ -> pick(change.position.x) }
                    }
                    .pointerInput(m) {
                        detectTapGestures(
                            onPress = {
                                selected = ((it.x / size.width) * m.bars.size).toInt().coerceIn(0, m.bars.lastIndex)
                                tryAwaitRelease()
                                selected = null
                            }
                        )
                    }
            ) {
                val slot = size.width / m.bars.size.coerceAtLeast(1)
                val barWidth = slot * 0.62f
                fun y(micrometers: Double) = size.height * (1f - intensityHeight(micrometers))

                // Guide lines, labelled at the end
                m.guides.forEach { (micrometers, label) ->
                    val gy = y(micrometers)
                    drawLine(
                        content.copy(alpha = 0.22f),
                        Offset(0f, gy),
                        Offset(size.width, gy),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    )
                    val layout = measurer.measure(label, labelStyle)
                    drawText(layout, topLeft = Offset(size.width - layout.size.width, gy - layout.size.height - 1.dp.toPx()))
                }
                // Baseline
                drawLine(content.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())

                val fill = Brush.verticalGradient(listOf(lerp(GlassPrecipitation, androidx.compose.ui.graphics.Color.White, 0.35f), GlassPrecipitation))
                m.bars.forEachIndexed { i, bar ->
                    if (bar.micrometers <= 0.0) return@forEachIndexed
                    val top = y(bar.micrometers).coerceAtMost(size.height - 3.dp.toPx())
                    drawRoundRect(
                        brush = fill,
                        topLeft = Offset(i * slot + (slot - barWidth) / 2f, top),
                        size = Size(barWidth, size.height - top),
                        cornerRadius = CornerRadius(barWidth / 3f),
                        alpha = if (selected == null || selected == i) 1f else 0.45f
                    )
                }
                selected?.let {
                    val x = it * slot + slot / 2f
                    drawLine(content.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                m.times.forEachIndexed { i, time ->
                    Text(
                        text = time,
                        color = content.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        textAlign = when (i) {
                            0 -> androidx.compose.ui.text.style.TextAlign.Start
                            m.times.lastIndex -> androidx.compose.ui.text.style.TextAlign.End
                            else -> androidx.compose.ui.text.style.TextAlign.Center
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
