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

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.MoonPhase
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.weather.model.getDescription
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.ui.theme.weatherView.sky.drawMoonDisc
import org.breezyweather.ui.theme.weatherView.sky.moonFraction
import org.shredzone.commons.suncalc.MoonIllumination
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Moon tile: the current phase, drawn (the same moon as in the sky) and named, how much of it is
 * lit, and when it rises and sets; with the whole row to itself, also the next full moon.
 *
 * The phase comes from the weather source when it has one, otherwise it is computed on the device.
 */
class MoonViewHolder(parent: ViewGroup) : ComposeTileViewHolder(parent) {

    private val model = mutableStateOf<MoonModel?>(null)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        model.value = MoonModel.from(activity, location)
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
        val moon = model.value ?: return
        TileHeader(R.drawable.weather_clear_night_mini_xml, moon.title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = moon.phaseName ?: "-",
                color = contentColor,
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Canvas(
                Modifier
                    .padding(start = 6.dp)
                    .size(if (wide) 64.dp else 50.dp)
            ) {
                drawMoonDisc(center, size.minDimension / 2f, moon.phase, moon.southern)
            }
        }
        Spacer(Modifier.weight(1f))
        InfoRow(R.string.tile_moon_illumination, moon.illumination)
        InfoRow(R.string.tile_moonrise, moon.rise)
        InfoRow(R.string.tile_moonset, moon.set)
        if (wide) InfoRow(R.string.tile_next_full_moon, moon.nextFullMoon)
    }

    @Composable
    private fun InfoRow(label: Int, value: String?) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = itemView.context.getString(label),
                color = contentColor.copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value ?: "-",
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * @param phase -180 (new, waxing) .. 0 (full) .. 180 (new, waning), as drawn by the sky
 */
@Immutable
private data class MoonModel(
    val title: String,
    val phase: Float,
    val southern: Boolean,
    val phaseName: String?,
    val illumination: String,
    val rise: String?,
    val set: String?,
    val nextFullMoon: String,
    val description: String,
) {
    companion object {
        /** Mean length of a lunar cycle. */
        private const val SYNODIC_MONTH_DAYS = 29.530588

        fun from(activity: BreezyActivity, location: Location): MoonModel? {
            val weather = location.weather ?: return null
            val now = Date()
            // Breezy's angle: 0 new moon, 180 full, 360 new again
            val angle = weather.today?.moonPhase?.angle?.toDouble()
                ?: (MoonIllumination.compute().on(now).execute().phase + 180.0)
            val phase = (angle - 180.0).toFloat()
            fun time(date: Date?) = date?.getFormattedTime(location, activity, activity.is12Hour)

            // Before today's moonrise, yesterday's moon may still be up: show that one
            val todayMoon = weather.today?.moon
            val moon = if (todayMoon?.riseDate == null || now.before(todayMoon.riseDate)) {
                val yesterday = weather.todayIndex?.let { weather.dailyForecast.getOrNull(it - 1) }?.moon
                if (yesterday?.isValid == true && yesterday.setDate!!.after(now)) yesterday else todayMoon
            } else {
                todayMoon
            }

            val daysToFull = ((180.0 - angle).mod(360.0)) / 360.0 * SYNODIC_MONTH_DAYS
            val fullMoon = Date(now.time + (daysToFull * 86_400_000L).roundToLong())
            val locale = activity.currentLocale
            val dateFormat = SimpleDateFormat(
                android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"),
                locale
            ).apply { timeZone = location.timeZone }

            val phaseName = MoonPhase(angle.roundToInt().mod(360)).getDescription(activity)
            val illumination = "${(moonFraction(phase) * 100f).roundToInt()}%"
            val rise = time(moon?.riseDate)
            val set = time(moon?.setDate)
            return MoonModel(
                title = activity.getString(R.string.ephemeris_moon),
                phase = phase,
                southern = location.latitude < 0.0,
                phaseName = phaseName,
                illumination = illumination,
                rise = rise,
                set = set,
                nextFullMoon = dateFormat.format(fullMoon),
                description = listOfNotNull(
                    phaseName,
                    activity.getString(R.string.tile_moon_illumination) + " " + illumination,
                    rise?.let { activity.getString(R.string.ephemeris_moonrise_at, it) },
                    set?.let { activity.getString(R.string.ephemeris_moonset_at, it) }
                ).joinToString(". ")
            )
        }
    }
}
