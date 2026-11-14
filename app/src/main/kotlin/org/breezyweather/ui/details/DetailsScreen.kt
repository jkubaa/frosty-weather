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

package org.breezyweather.ui.details

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import breezyweather.domain.location.model.Location
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.breezyweather.R
import org.breezyweather.common.extensions.getCalendarMonth
import org.breezyweather.common.extensions.setSystemBarStyle
import org.breezyweather.common.extensions.toCalendarWithTimeZone
import org.breezyweather.common.extensions.toTimezoneSpecificHour
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.source.PollenIndexSource
import org.breezyweather.domain.weather.index.PollutantIndex
import org.breezyweather.domain.weather.model.getConcentration
import org.breezyweather.domain.weather.model.isToday
import org.breezyweather.ui.details.components.DetailsAirQuality
import org.breezyweather.ui.details.components.DetailsCloudCover
import org.breezyweather.ui.details.components.DetailsConditions
import org.breezyweather.ui.details.components.DetailsHumidity
import org.breezyweather.ui.details.components.DetailsMoon
import org.breezyweather.ui.details.components.DetailsPollen
import org.breezyweather.ui.details.components.DetailsPrecipitation
import org.breezyweather.ui.details.components.DetailsPressure
import org.breezyweather.ui.details.components.DetailsSun
import org.breezyweather.ui.details.components.DetailsUV
import org.breezyweather.ui.details.components.DetailsVisibility
import org.breezyweather.ui.details.components.DetailsWind
import org.breezyweather.ui.details.components.sunHeadline
import org.breezyweather.ui.theme.ThemeManager
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Detail screens, presented as an Apple Weather-style sheet over the home screen: a close button and
 * the metric's title, a week strip to pick the day, then the metric's page. Drag the sheet down (or
 * pull a page down from its top) to dismiss it.
 */
@Composable
internal fun DailyWeatherScreen(
    onBackPressed: () -> Unit,
    detailsViewModel: DetailsViewModel = viewModel(),
) {
    val detailsUiState by detailsViewModel.uiState.collectAsState()

    val context = LocalContext.current
    val activity = LocalActivity.current

    val isLightTheme = ThemeManager.isLightTheme(context, detailsUiState.location)
    LaunchedEffect(Unit) {
        // The status bar sits over the dimmed home screen
        activity?.window?.setSystemBarStyle(false)
    }

    val scope = rememberCoroutineScope()
    // 0 = fully presented, 1 = fully below the screen
    val sheetPosition = remember { Animatable(1f) }
    var dismissing by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                sheetPosition.animateTo(1f, tween(durationMillis = 240))
                onBackPressed()
            }
        }
    }
    BackHandler { dismiss() }
    LaunchedEffect(Unit) {
        sheetPosition.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
    }

    val palette = remember(isLightTheme, detailsUiState.selectedChart) {
        if (detailsUiState.selectedChart == DetailScreen.TAG_MOON) {
            // The moon is shown on a night sky black, whatever the theme
            DetailsPalette.Dark.copy(sheet = Color.Black, card = Color(0xFF1C1C1E))
        } else if (isLightTheme) {
            DetailsPalette.Light
        } else {
            DetailsPalette.Dark
        }
    }

    BreezyWeatherTheme(!isLightTheme) {
        DetailsSheetTheme(palette) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                fun settle(velocity: Float) {
                    if (sheetPosition.value > 0.25f || velocity > 2200f) {
                        dismiss()
                    } else {
                        scope.launch { sheetPosition.animateTo(0f, spring(dampingRatio = 0.86f)) }
                    }
                }

                val nestedScrollConnection = remember(heightPx) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            // While the sheet is pulled down, scrolling up first lifts it back
                            if (available.y < 0f && sheetPosition.value > 0f) {
                                val before = sheetPosition.value
                                val after = (before + available.y / heightPx).coerceAtLeast(0f)
                                scope.launch { sheetPosition.snapTo(after) }
                                return Offset(0f, (after - before) * heightPx)
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            // Pulling down past the top of a page drags the sheet down
                            if (available.y > 0f && source == NestedScrollSource.UserInput) {
                                scope.launch { sheetPosition.snapTo(sheetPosition.value + available.y / heightPx) }
                                return Offset(0f, available.y)
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            if (sheetPosition.value > 0f) {
                                settle(available.y)
                                return available
                            }
                            return Velocity.Zero
                        }
                    }
                }

                // Dimmed home screen behind the sheet
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f * (1f - sheetPosition.value)))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = dismiss
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp)
                        .offset { IntOffset(0, (sheetPosition.value * heightPx).roundToInt()) }
                        .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                        .background(palette.sheet)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .nestedScroll(nestedScrollConnection)
                ) {
                    detailsUiState.location?.let { loc ->
                        DetailsSheetContent(
                            location = loc,
                            detailsUiState = detailsUiState,
                            detailsViewModel = detailsViewModel,
                            onClose = dismiss,
                            dragModifier = Modifier.draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    scope.launch {
                                        sheetPosition.snapTo(
                                            (sheetPosition.value + delta / heightPx).coerceAtLeast(0f)
                                        )
                                    }
                                },
                                onDragStopped = { velocity -> settle(velocity) }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsSheetContent(
    location: Location,
    detailsUiState: DetailsUiState,
    detailsViewModel: DetailsViewModel,
    onClose: () -> Unit,
    dragModifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedChart = detailsUiState.selectedChart
    val weather = location.weather!!
    val todayIndex = weather.todayIndex

    val detailScreenEntries = remember(location) { DetailScreen.toDetailScreenList(location) }
    val metricSwitcher: @Composable () -> Unit = {
        DetailsMetricPill(
            entries = detailScreenEntries,
            selected = selectedChart,
            onSelect = { detailsViewModel.setSelectedChart(it) }
        )
    }

    when (selectedChart) {
        DetailScreen.TAG_SUN -> {
            val headline = remember(location) { sunHeadline(location) }
            Column(dragModifier) {
                DetailsTopBar(
                    title = context.getString(
                        if (headline.isSunset) R.string.details_sunset else R.string.details_sunrise
                    ),
                    icon = R.drawable.ic_twilight,
                    onClose = onClose
                )
            }
            DetailsSun(location, headline)
        }
        DetailScreen.TAG_MOON -> {
            Column(dragModifier) {
                DetailsTopBar(title = null, icon = null, onClose = onClose)
            }
            DetailsMoon(location)
        }
        else -> {
            val pages = remember(weather.dailyForecast) {
                weather.dailyForecast.map { it.date }.toImmutableList()
            }
            val pagerState = rememberPagerState(initialPage = detailsUiState.initialIndex) {
                weather.dailyForecast.size
            }
            val pagerPage by remember { derivedStateOf { pagerState.currentPage } }
            Column(dragModifier) {
                DetailsTopBar(
                    title = selectedChart.getName(context),
                    icon = if (selectedChart == DetailScreen.TAG_FEELS_LIKE) {
                        DetailScreen.TAG_CONDITIONS.iconId
                    } else {
                        selectedChart.iconId
                    },
                    onClose = onClose
                )
                DetailsWeekStrip(
                    location = location,
                    dates = pages,
                    selected = pagerPage,
                    todayIndex = todayIndex,
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } }
                )
            }
            CompositionLocalProvider(LocalDetailsMetricSwitcher provides metricSwitcher) {
                HorizontalPager(state = pagerState) { page ->
                    DailyPagerContent(
                        location = location,
                        selected = page,
                        selectedChart = selectedChart,
                        setSelectedChart = { chart -> detailsViewModel.setSelectedChart(chart) },
                        selectedPollutant = detailsUiState.selectedPollutant,
                        setSelectedPollutant = { pollutant -> detailsViewModel.setSelectedPollutant(pollutant) },
                        pollenIndexSource = detailsViewModel.getPollenIndexSource(location)
                    )
                }
            }
        }
    }
}

@Composable
fun DailyPagerContent(
    location: Location,
    selected: Int,
    selectedChart: DetailScreen,
    setSelectedChart: (DetailScreen) -> Unit,
    selectedPollutant: PollutantIndex?,
    setSelectedPollutant: (PollutantIndex?) -> Unit,
    pollenIndexSource: PollenIndexSource?,
    modifier: Modifier = Modifier,
) {
    val daily = remember(location, selected) {
        location.weather!!.dailyForecast[selected]
    }

    val hourlyList = remember(location, selected) {
        val startingDate = daily.date.toTimezoneSpecificHour(location.timeZone, 0)
        val endingDate = daily.date.toCalendarWithTimeZone(location.timeZone).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }.time.toTimezoneSpecificHour(location.timeZone, 0)

        val firstHourlyIndex = location.weather!!.hourlyForecast.indexOfFirst {
            it.date >= startingDate
        }.let {
            if (it > 0 && location.weather!!.hourlyForecast[it].date > startingDate) it - 1 else it
        }
        if (firstHourlyIndex == -1) return@remember persistentListOf()

        val lastHourlyIndex = location.weather!!.hourlyForecast.indexOfFirst {
            it.date >= endingDate
        }.let { if (it == -1) location.weather!!.hourlyForecast.size - 1 else it }

        // We are doing a subList to take into account 3-hourly/6-hourly
        // For example, a complete chart can be from 02:00 to 02:00 the next day
        location.weather!!.hourlyForecast.subList(firstHourlyIndex, lastHourlyIndex + 1).toImmutableList()
    }

    val currentUpdateTime = remember(location) {
        location.weather!!.base.currentUpdateTime
            ?: location.weather!!.base.forecastUpdateTime
            ?: location.weather!!.base.refreshTime!!
    }

    val current = remember(location, selected, currentUpdateTime) {
        if (daily.isToday(location) && currentUpdateTime > daily.date) location.weather!!.current else null
    }

    Column(
        modifier = modifier
    ) {
        when (selectedChart) {
            DetailScreen.TAG_CONDITIONS, DetailScreen.TAG_FEELS_LIKE -> {
                DetailsConditions(
                    location,
                    hourlyList,
                    daily,
                    location.weather?.normals?.getOrElse(daily.date.getCalendarMonth(location)) { null },
                    selectedChart,
                    { setSelectedChart(if (it) DetailScreen.TAG_CONDITIONS else DetailScreen.TAG_FEELS_LIKE) }
                )
            }
            DetailScreen.TAG_PRECIPITATION -> {
                DetailsPrecipitation(location, hourlyList, daily)
            }
            DetailScreen.TAG_WIND -> {
                DetailsWind(
                    location,
                    hourlyList,
                    daily,
                    current?.wind?.let {
                        if (it.isValid) Pair(currentUpdateTime, it) else null
                    }
                )
            }
            DetailScreen.TAG_AIR_QUALITY -> {
                val supportedPollutants = remember(location) {
                    PollutantIndex.entries
                        .filter { pollutant ->
                            location.weather!!.dailyForecast.any {
                                it.airQuality?.getConcentration(pollutant) != null
                            } ||
                                location.weather!!.hourlyForecast.any {
                                    it.airQuality?.getConcentration(pollutant) != null
                                } ||
                                location.weather!!.current?.airQuality?.getConcentration(pollutant) != null
                        }.toImmutableList()
                }

                DetailsAirQuality(
                    location,
                    supportedPollutants,
                    selectedPollutant,
                    setSelectedPollutant,
                    hourlyList,
                    daily,
                    current?.airQuality?.let {
                        if (it.isValid) Pair(currentUpdateTime, it) else null
                    }
                )
            }
            DetailScreen.TAG_POLLEN -> {
                DetailsPollen(daily.pollen, pollenIndexSource)
            }
            DetailScreen.TAG_UV_INDEX -> {
                DetailsUV(
                    location,
                    hourlyList,
                    daily,
                    current?.uV?.let {
                        if (it.isValid) Pair(currentUpdateTime, it) else null
                    }
                )
            }
            DetailScreen.TAG_HUMIDITY -> {
                DetailsHumidity(
                    location,
                    hourlyList,
                    daily,
                    current?.relativeHumidity?.let { relativeHumidity ->
                        Pair(currentUpdateTime, relativeHumidity)
                    },
                    current?.dewPoint?.let { dewPoint ->
                        Pair(currentUpdateTime, dewPoint)
                    }
                )
            }
            DetailScreen.TAG_PRESSURE -> {
                DetailsPressure(
                    location,
                    hourlyList,
                    daily,
                    current?.pressure?.let { pressure ->
                        Pair(currentUpdateTime, pressure)
                    }
                )
            }
            DetailScreen.TAG_CLOUD_COVER -> {
                DetailsCloudCover(
                    location,
                    hourlyList,
                    daily,
                    current?.cloudCover?.let { cloudCover ->
                        Pair(currentUpdateTime, cloudCover)
                    }
                )
            }
            DetailScreen.TAG_VISIBILITY -> {
                DetailsVisibility(
                    location,
                    hourlyList,
                    daily,
                    current?.visibility?.let { visibility ->
                        Pair(currentUpdateTime, visibility)
                    }
                )
            }
            DetailScreen.TAG_SUN, DetailScreen.TAG_MOON -> Unit // Shown without the day pager
        }
    }
}
