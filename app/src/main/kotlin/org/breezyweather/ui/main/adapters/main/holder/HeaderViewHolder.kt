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

package org.breezyweather.ui.main.adapters.main.holder

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.formatMeasure
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.weather.model.getTemperatureRangeSummary
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.formatting.format
import org.breezyweather.unit.temperature.TemperatureUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Top of the home screen, in Compose: location, condition and "feels like" on the left, a big thin
 * temperature counting up to its value with today's high / low on the right, in frosted "glassy" text.
 * The summary card under it is its own item, see [SummaryViewHolder].
 *
 */
class HeaderViewHolder(
    context: Context,
) : AbstractMainViewHolder(context) {

    private val state = mutableStateOf<HeroState?>(null)
    private val content = Color(context.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse))

    override fun onBindView(
        context: Context,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(context, location, provider, listAnimationEnabled, itemAnimationEnabled)
        state.value = HeroState.from(context, location, itemAnimationEnabled)
    }

    @Composable
    override fun Render(modifier: Modifier) {
        state.value?.let {
            Box(modifier) { Hero(it, content) }
        }
    }
}

/** Everything the header shows, already formatted. */
@Immutable
private data class HeroState(
    val isCurrentPosition: Boolean,
    val captionText: String,
    val name: String,
    val condition: String?,
    val feelsLike: String?,
    val feelsLikeDescription: String?,
    val temperature: Int?,
    val temperatureDescription: String?,
    val highLow: String?,
    val highLowDescription: String?,
    val animate: Boolean,
    val formatNumber: (Int) -> String,
) {
    companion object {
        fun from(context: Context, location: Location, animate: Boolean): HeroState {
            val unit = SettingsManager.getInstance(context).getTemperatureUnit(context)
            val current = location.weather?.current
            val temperature = current?.temperature?.temperature
            val feelsLike = current?.temperature?.feelsLikeTemperature?.takeIf {
                // Only worth showing when it differs from the actual temperature
                temperature?.toDouble(unit)?.roundToInt() != it.toDouble(unit).roundToInt()
            }

            val todayTemperatures = listOfNotNull(
                location.weather?.today?.day?.temperature?.temperature,
                location.weather?.today?.night?.temperature?.temperature
            )
            val highLow = if (todayTemperatures.isNotEmpty()) {
                context.getString(
                    R.string.hero_high_low,
                    GlassForecastFormat.degrees(context, todayTemperatures.maxBy { it.toDouble(TemperatureUnit.CELSIUS) }, unit),
                    GlassForecastFormat.degrees(context, todayTemperatures.minBy { it.toDouble(TemperatureUnit.CELSIUS) }, unit)
                )
            } else {
                null
            }

            val locale = context.currentLocale
            return HeroState(
                isCurrentPosition = location.isCurrentPosition,
                captionText = context.getString(R.string.location_current),
                name = location.getPlace(context),
                // Apple-style: always show a condition, falling back to the generic label of the weather code
                condition = current?.weatherText?.takeIf { it.isNotEmpty() }
                    ?: current?.weatherCode?.let { WeatherViewController.getWeatherText(it) },
                feelsLike = feelsLike?.let {
                    context.getString(
                        R.string.temperature_feels_like_with_unit,
                        it.formatMeasure(context, unit, valueWidth = UnitWidth.NARROW, unitWidth = UnitWidth.NARROW)
                    )
                },
                feelsLikeDescription = feelsLike?.let {
                    context.getString(R.string.temperature_feels_like) +
                        context.getString(R.string.colon_separator) +
                        it.formatMeasure(context, unit, unitWidth = UnitWidth.LONG)
                },
                temperature = temperature?.toDouble(unit)?.roundToInt(),
                temperatureDescription = temperature?.formatMeasure(context, unit, unitWidth = UnitWidth.LONG),
                highLow = highLow,
                // Spoken: the full sentence from the summary when there is one
                highLowDescription = location.weather?.getTemperatureRangeSummary(context, location, unit)?.second,
                animate = animate,
                formatNumber = { it.format(decimals = 0, locale = locale) }
            )
        }
    }
}

/** Frosted text: bright at the top, slightly see-through at the bottom, so the sky shows through a little. */
private fun glassBrush(color: Color) = Brush.verticalGradient(
    0f to color,
    0.45f to color.copy(alpha = 0.92f),
    1f to color.copy(alpha = 0.62f)
)

private val TightText = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
)

@Composable
private fun Hero(state: HeroState, content: Color) {
    val softShadow = Shadow(Color(0x26000000), blurRadius = 16f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 22.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Left: where and what
        Column(modifier = Modifier.weight(1f)) {
            if (state.isCurrentPosition) {
                Row(
                    modifier = Modifier.alpha(0.8f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_location_arrow),
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = state.captionText.uppercase(),
                        color = content,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.06.em,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = state.name,
                style = TightText.copy(
                    brush = glassBrush(content),
                    fontSize = 32.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = Shadow(Color(0x33000000), blurRadius = 28f)
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            state.condition?.let {
                Text(
                    text = it,
                    color = content,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(shadow = Shadow(Color(0x26000000), blurRadius = 16f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            state.feelsLike?.let {
                Text(
                    text = it,
                    color = content.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .semantics { state.feelsLikeDescription?.let { d -> contentDescription = d } }
                )
            }
        }

        // Right: how warm
        Column(
            modifier = Modifier.padding(start = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            state.temperature?.let { target ->
                val shown = remember { Animatable(if (state.animate) 0f else target.toFloat()) }
                LaunchedEffect(target, state.animate) {
                    if (state.animate) {
                        val millis = (abs(target - shown.value) * 20f).roundToInt().coerceIn(1000, 2000)
                        shown.animateTo(target.toFloat(), tween(millis, easing = FastOutSlowInEasing))
                    } else {
                        shown.snapTo(target.toFloat())
                    }
                }
                Row(
                    modifier = Modifier.clearAndSetSemantics {
                        state.temperatureDescription?.let { contentDescription = it }
                    },
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = state.formatNumber(shown.value.roundToInt()),
                        style = TightText.copy(
                            brush = glassBrush(content),
                            fontSize = 80.sp,
                            lineHeight = 80.sp,
                            fontWeight = FontWeight.ExtraLight,
                            letterSpacing = (-0.03).em,
                            shadow = softShadow
                        ),
                        maxLines = 1
                    )
                    // Apple-style: a lone degree sign, the unit is set in the settings anyway
                    Text(
                        text = "°",
                        style = TightText.copy(
                            brush = glassBrush(content),
                            fontSize = 60.sp,
                            lineHeight = 60.sp,
                            fontWeight = FontWeight.ExtraLight,
                            shadow = softShadow
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            state.highLow?.let {
                Text(
                    text = it,
                    color = content,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(shadow = Shadow(Color(0x26000000), blurRadius = 16f)),
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .semantics { state.highLowDescription?.let { d -> contentDescription = d } }
                )
            }
        }
    }
}
