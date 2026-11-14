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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Daily
import breezyweather.domain.weather.model.MoonPhase
import kotlinx.coroutines.launch
import org.breezyweather.R
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.formatTime
import org.breezyweather.common.extensions.getFormattedDate
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.common.extensions.toCalendarWithTimeZone
import org.breezyweather.common.extensions.toTimezoneSpecificHour
import org.breezyweather.domain.weather.model.getDescription
import org.breezyweather.ui.details.DetailsHeaderRow
import org.breezyweather.ui.details.DetailsInfoCard
import org.breezyweather.ui.details.LocalDetailsPalette
import org.breezyweather.ui.details.formatTimeSmallMarker
import org.breezyweather.ui.details.smallSuffix
import org.breezyweather.unit.distance.Distance.Companion.kilometers
import org.breezyweather.unit.formatting.UnitWidth
import org.shredzone.commons.suncalc.MoonIllumination
import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.SunPosition
import java.util.Calendar
import java.util.Date
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L
private const val SYNODIC_MONTH_DAYS = 29.530588

/**
 * The next sun event: what the Sun screen is titled after and shows in large.
 *
 * @param isSunset true when the next event is today's sunset, false for a sunrise
 * @param time time of that event, null in polar day/night
 * @param kind which case we are in, to pick the subtitle
 */
@Immutable
data class SunHeadline(
    val isSunset: Boolean,
    val time: Date?,
    val kind: Kind,
    val today: Daily?,
) {
    enum class Kind { BEFORE_SUNRISE, DAYLIGHT, AFTER_SUNSET, POLAR }
}

fun sunHeadline(location: Location, now: Date = Date()): SunHeadline {
    val weather = location.weather
    val today = weather?.today
    val sun = today?.sun
    val rise = sun?.riseDate
    val set = sun?.setDate
    return when {
        rise != null && now.before(rise) -> SunHeadline(false, rise, SunHeadline.Kind.BEFORE_SUNRISE, today)
        set != null && now.before(set) -> SunHeadline(true, set, SunHeadline.Kind.DAYLIGHT, today)
        set != null -> SunHeadline(
            isSunset = false,
            time = weather.tomorrow?.sun?.riseDate,
            kind = SunHeadline.Kind.AFTER_SUNSET,
            today = today
        )
        else -> SunHeadline(false, null, SunHeadline.Kind.POLAR, today)
    }
}

@Composable
fun DetailsSun(
    location: Location,
    headline: SunHeadline,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalDetailsPalette.current
    val today = headline.today
    val now = remember { Date() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
    ) {
        item {
            DetailsHeaderRow {
                Column {
                    Text(
                        text = headline.time?.let { formatTimeSmallMarker(it, location) }
                            ?: AnnotatedString(
                                stringResource(
                                    if (headline.kind == SunHeadline.Kind.POLAR && today?.sun?.duration != null) {
                                        R.string.ephemeris_polar_day
                                    } else {
                                        R.string.ephemeris_polar_night
                                    }
                                )
                            ),
                        style = MaterialTheme.typography.displaySmall,
                        color = palette.text
                    )
                    val subtitle = when (headline.kind) {
                        SunHeadline.Kind.DAYLIGHT -> stringResource(
                            R.string.details_daylight_remaining,
                            (headline.time!!.time - now.time).milliseconds
                                .formatTime(context, smallestUnit = DurationUnit.MINUTES)
                        )
                        SunHeadline.Kind.BEFORE_SUNRISE -> today?.twilight?.riseDate?.let {
                            stringResource(
                                R.string.details_first_light_at,
                                it.getFormattedTime(location, context, context.is12Hour)
                            )
                        }
                        SunHeadline.Kind.AFTER_SUNSET -> today?.sun?.setDate?.let {
                            stringResource(
                                R.string.details_sunset_was_at,
                                it.getFormattedTime(location, context, context.is12Hour)
                            )
                        }
                        SunHeadline.Kind.POLAR -> null
                    }
                    subtitle?.let {
                        Text(
                            text = smallSuffixAfterColon(it),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.secondaryText
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        if (today != null) {
            item {
                SunPathChart(location, today, now)
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                DetailsInfoCard(
                    rows = listOf(
                        stringResource(R.string.details_first_light) to
                            formatTimeSmallMarker(today.twilight?.riseDate, location),
                        stringResource(R.string.details_sunrise_today) to
                            formatTimeSmallMarker(today.sun?.riseDate, location),
                        stringResource(R.string.details_sunset_today) to
                            formatTimeSmallMarker(today.sun?.setDate, location),
                        stringResource(R.string.details_last_light) to
                            formatTimeSmallMarker(today.twilight?.setDate, location),
                        stringResource(R.string.details_total_daylight) to smallSuffix(
                            today.sun?.duration?.formatTime(context, smallestUnit = DurationUnit.MINUTES)
                                ?: stringResource(R.string.null_data_text)
                        )
                    )
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            DetailsSectionHeader(stringResource(R.string.ephemeris_about))
        }
        item {
            DetailsCardText(
                stringResource(R.string.ephemeris_about_rise) +
                    " " +
                    stringResource(R.string.ephemeris_about_set) +
                    "\n\n" +
                    stringResource(R.string.ephemeris_about_dawn) +
                    " " +
                    stringResource(R.string.ephemeris_about_dusk)
            )
        }
    }
}

/** "Daylight remaining: 1 hr 10 min" → the part after the colon gets small units. */
private fun smallSuffixAfterColon(text: String): AnnotatedString {
    val i = text.indexOf(':')
    if (i < 0 || i == text.lastIndex) return AnnotatedString(text)
    return AnnotatedString(text.substring(0, i + 1)) + smallSuffix(text.substring(i + 1))
}

/**
 * The sun's altitude through the day: sky above the horizon, night below, the sun where it is now.
 */
@Composable
private fun SunPathChart(
    location: Location,
    today: Daily,
    now: Date,
) {
    val context = LocalContext.current
    val palette = LocalDetailsPalette.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val dayStart = remember(today) { today.date.toTimezoneSpecificHour(location.timeZone, 0).time }
    val dayEnd = remember(today) {
        today.date.toCalendarWithTimeZone(location.timeZone).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }.time.toTimezoneSpecificHour(location.timeZone, 0).time
    }
    val samples = remember(today, location) {
        val step = 10 * 60_000L
        buildList {
            var t = dayStart
            while (t <= dayEnd) {
                add(t to altitude(location, t))
                t += step
            }
        }
    }
    val hourLabels = remember(today) {
        (0..18 step 6).map { h ->
            val t = today.date.toTimezoneSpecificHour(location.timeZone, h)
            t.time to if (context.is12Hour) {
                t.getFormattedDate("ha", location, context, withBestPattern = true)
                    .replace(" ", "").uppercase()
            } else {
                t.getFormattedDate("HH", location, context)
            }
        }
    }
    val markers = remember(today) {
        listOfNotNull(today.twilight?.riseDate, today.twilight?.setDate).map { it.time }
    }

    val skyTop = Color(0xFF8A877F)
    val skyHorizon = Color(0xFF34465F)
    val labelStyle = TextStyle(color = palette.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val isToday = now.time in dayStart until dayEnd

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clearAndSetSemantics {}
    ) {
        val labelHeight = with(density) { 30.dp.toPx() }
        val plotBottom = size.height - labelHeight
        val maxAlt = max(samples.maxOf { it.second }, 15.0) * 1.35
        val minAlt = min(samples.minOf { it.second }, -15.0) * 1.35
        fun x(t: Long) = ((t - dayStart).toFloat() / (dayEnd - dayStart)) * size.width
        fun y(alt: Double) = (((maxAlt - alt) / (maxAlt - minAlt)) * plotBottom).toFloat()
        val horizonY = y(0.0)
        val corner = with(density) { 14.dp.toPx() }

        // Sky
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(skyTop, skyHorizon), startY = 0f, endY = horizonY),
            size = Size(size.width, horizonY + corner),
            cornerRadius = CornerRadius(corner, corner)
        )
        drawRect(color = palette.sheet, topLeft = Offset(0f, horizonY), size = Size(size.width, plotBottom - horizonY + 1f))

        // Grid
        val rows = 8
        for (i in 1 until rows) {
            val gy = plotBottom * i / rows
            drawLine(
                color = if (gy < horizonY) Color.White.copy(alpha = 0.18f) else palette.grid,
                start = Offset(0f, gy),
                end = Offset(size.width, gy),
                strokeWidth = 1.dp.toPx()
            )
        }
        drawLine(palette.grid, Offset(0f, plotBottom), Offset(size.width, plotBottom), 1.dp.toPx())
        hourLabels.forEachIndexed { i, (t, label) ->
            val lx = x(t)
            if (i > 0) {
                drawLine(
                    color = palette.grid.copy(alpha = palette.grid.alpha * 2.2f),
                    start = Offset(lx, 0f),
                    end = Offset(lx, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                )
            }
            val layout = textMeasurer.measure(smallSuffix(label), labelStyle)
            drawText(layout, topLeft = Offset(lx + 6.dp.toPx(), plotBottom + (labelHeight - layout.size.height) / 2f))
        }

        // Horizon
        drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, horizonY), Offset(size.width, horizonY), 2.dp.toPx())

        // Sun path: dim below the horizon, bright above it
        val path = Path().apply {
            samples.forEachIndexed { i, (t, alt) ->
                if (i == 0) moveTo(x(t), y(alt)) else lineTo(x(t), y(alt))
            }
        }
        drawPath(path, Color.White.copy(alpha = 0.22f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        clipRect(bottom = horizonY) {
            drawPath(
                path,
                Brush.verticalGradient(
                    listOf(Color.White, Color.White.copy(alpha = 0.55f)),
                    startY = 0f,
                    endY = horizonY
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Twilight markers
        markers.forEach { t ->
            drawCircle(Color.White.copy(alpha = 0.55f), 3.dp.toPx(), Offset(x(t), y(altitude(location, t))))
        }

        // The sun now
        if (isToday) {
            val sunAlt = altitude(location, now.time)
            val c = Offset(x(now.time), y(sunAlt))
            if (sunAlt >= 0) {
                drawSunGlow(c)
            } else {
                drawCircle(Color.White.copy(alpha = 0.3f), 10.dp.toPx(), c)
                drawCircle(palette.sheet, 7.dp.toPx(), c)
                drawCircle(Color.White.copy(alpha = 0.55f), 6.dp.toPx(), c)
            }
        }
    }
}

private fun DrawScope.drawSunGlow(center: Offset) {
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)),
            center = center,
            radius = 30.dp.toPx()
        ),
        30.dp.toPx(),
        center
    )
    drawCircle(Color.White, 12.dp.toPx(), center)
}

private fun altitude(location: Location, time: Long): Double =
    SunPosition.compute().on(Date(time)).at(location.latitude, location.longitude).execute().altitude

/**
 * Moon screen: the moon as it looks at the chosen time, its phase, a timeline to scrub through the
 * days, and the moon's numbers at that time.
 */
@Composable
fun DetailsMoon(
    location: Location,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalDetailsPalette.current
    val now = remember { System.currentTimeMillis() }
    var selected by remember { mutableLongStateOf(now) }

    val phase = remember(selected) {
        MoonIllumination.compute().on(Date(selected)).execute().phase.toFloat()
    }
    val illumination = remember(selected) {
        MoonIllumination.compute().on(Date(selected)).execute().fraction
    }
    val distanceKm = remember(selected) {
        MoonPosition.compute().on(Date(selected)).at(location.latitude, location.longitude).execute().distance
    }
    // Breezy's angle: 0 new moon, 180 full, 360 new again
    val angle = (phase + 180.0)
    val phaseName = MoonPhase(angle.roundToInt().mod(360)).getDescription(context)
    val daysToFull = ceil(((180.0 - angle).mod(360.0)) / 360.0 * SYNODIC_MONTH_DAYS - 0.5).toInt().coerceAtLeast(0)
    val selectedDaily = remember(selected) {
        location.weather?.dailyForecast?.firstOrNull {
            val start = it.date.toTimezoneSpecificHour(location.timeZone, 0).time
            selected in start until start + DAY_MS
        }
    }
    val selectedHour = (selected / HOUR_MS) * HOUR_MS + if (selected % HOUR_MS >= HOUR_MS / 2) HOUR_MS else 0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            RealisticMoon(
                phase = phase,
                southern = location.latitude < 0.0,
                modifier = Modifier
                    .fillMaxWidth(0.66f)
                    .aspectRatio(1f)
                    .semantics { contentDescription = phaseName ?: "" }
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
        item {
            Text(
                text = phaseName ?: "",
                color = palette.text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            val date = Date(selectedHour)
            Text(
                text = stringResource(
                    R.string.details_date_at_time,
                    date.getFormattedDate("EEEEdMMMM", location, context, withBestPattern = true),
                    if (context.is12Hour) {
                        date.getFormattedDate("ha", location, context, withBestPattern = true).lowercase()
                    } else {
                        date.getFormattedDate("HH:mm", location, context)
                    }
                ),
                color = palette.text,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
        item {
            MoonTimeline(
                location = location,
                now = now,
                onSelect = { selected = it }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            DetailsInfoCard(
                rows = listOf(
                    stringResource(R.string.tile_moon_illumination) to
                        AnnotatedString("${(illumination * 100).roundToInt()}%"),
                    stringResource(R.string.tile_moonrise) to
                        formatTimeSmallMarker(selectedDaily?.moon?.riseDate, location),
                    stringResource(R.string.tile_moonset) to
                        formatTimeSmallMarker(selectedDaily?.moon?.setDate, location),
                    stringResource(R.string.tile_next_full_moon) to smallSuffix(
                        if (daysToFull == 0) {
                            stringResource(R.string.daily_today)
                        } else {
                            pluralStringResource(R.plurals.details_in_days, daysToFull, daysToFull)
                        }
                    ),
                    stringResource(R.string.details_moon_distance) to
                        smallSuffix(distanceKm.kilometers.formatMeasure(context, valueWidth = UnitWidth.NARROW))
                ),
                outlined = true,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * A ruler of hours and days to scrub through time: the day under the blue marker is the one shown.
 */
@Composable
private fun MoonTimeline(
    location: Location,
    now: Long,
    onSelect: (Long) -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalDetailsPalette.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    val pxPerHour = with(density) { 2.dp.toPx() }
    // Hours from now under the marker
    val offset = remember {
        Animatable(0f).apply { updateBounds(lowerBound = -7f * 24f, upperBound = 30f * 24f) }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { offset.value }.collect { onSelect(now + (it * HOUR_MS).toLong()) }
    }
    val todayStart = remember { Date(now).toTimezoneSpecificHour(location.timeZone, 0).time }
    val labelCache = remember { HashMap<Long, String>() }
    val todayLabel = stringResource(R.string.daily_today)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch { offset.snapTo(offset.value - delta / pxPerHour) }
                },
                onDragStopped = { velocity ->
                    scope.launch { offset.animateDecay(-velocity / pxPerHour, exponentialDecay(frictionMultiplier = 2f)) }
                }
            )
            .clearAndSetSemantics {}
    ) {
        val centerX = size.width / 2f
        val tickTop = 10.dp.toPx()
        val majorBottom = 40.dp.toPx()
        val minorBottom = 34.dp.toPx()
        val selectedTime = now + (offset.value * HOUR_MS).toLong()
        fun xOf(t: Long) = centerX + (t - selectedTime).toFloat() / HOUR_MS * pxPerHour
        val tickMs = 3 * HOUR_MS
        val halfSpanMs = ((size.width / 2f / pxPerHour) + 6f).toLong() * HOUR_MS
        val kStart = Math.floorDiv(selectedTime - halfSpanMs - todayStart, tickMs)
        val kEnd = Math.floorDiv(selectedTime + halfSpanMs - todayStart, tickMs) + 1
        // Ticks every 3 hours from local midnight; midnights are the tall ones, day names sit at noon
        for (k in kStart..kEnd) {
            val x = xOf(todayStart + k * tickMs)
            val slot = Math.floorMod(k, 8L)
            val major = slot == 0L
            drawLine(
                color = if (major) palette.text.copy(alpha = 0.9f) else palette.text.copy(alpha = 0.3f),
                start = Offset(x, if (major) tickTop - 4.dp.toPx() else tickTop),
                end = Offset(x, if (major) majorBottom else minorBottom),
                strokeWidth = if (major) 2.dp.toPx() else 1.5.dp.toPx()
            )
            if (slot == 4L) {
                val dayStart = todayStart + Math.floorDiv(k, 8L) * DAY_MS
                val isToday = dayStart == todayStart
                val label = if (isToday) {
                    todayLabel.uppercase()
                } else {
                    labelCache.getOrPut(dayStart) {
                        Date(dayStart + 12 * HOUR_MS).getFormattedDate("EEE", location, context).uppercase()
                    }
                }
                val layout = textMeasurer.measure(
                    label,
                    TextStyle(
                        color = if (isToday) palette.text else palette.secondaryText,
                        fontSize = 14.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                    )
                )
                drawText(layout, topLeft = Offset(x - layout.size.width / 2f, majorBottom + 4.dp.toPx()))
            }
        }
        // Marker
        val tri = Path().apply {
            moveTo(centerX - 7.dp.toPx(), tickTop - 6.dp.toPx())
            lineTo(centerX + 7.dp.toPx(), tickTop - 6.dp.toPx())
            lineTo(centerX, tickTop + 6.dp.toPx())
            close()
        }
        drawPath(tri, palette.accent)
    }
}
