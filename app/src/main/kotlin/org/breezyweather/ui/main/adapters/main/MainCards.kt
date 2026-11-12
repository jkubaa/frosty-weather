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

package org.breezyweather.ui.main.adapters.main

import breezyweather.domain.location.model.Location
import org.breezyweather.common.extensions.toCalendarWithTimeZone
import org.breezyweather.common.options.appearance.CardDisplay
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.weather.model.hasMinutelyPrecipitation
import org.breezyweather.domain.weather.model.isIndexValid
import org.breezyweather.domain.weather.model.validAirQuality
import org.breezyweather.ui.main.MainActivity
import org.breezyweather.ui.main.adapters.main.holder.AbstractMainCardViewHolder
import org.breezyweather.ui.main.adapters.main.holder.AbstractMainViewHolder
import org.breezyweather.ui.main.adapters.main.holder.AirQualityViewHolder
import org.breezyweather.ui.main.adapters.main.holder.AlertViewHolder
import org.breezyweather.ui.main.adapters.main.holder.HighlightsViewHolder
import org.breezyweather.ui.main.utils.Highlights
import org.breezyweather.ui.main.adapters.main.holder.ClockViewHolder
import org.breezyweather.ui.main.adapters.main.holder.DailyViewHolder
import org.breezyweather.ui.main.adapters.main.holder.FooterViewHolder
import org.breezyweather.ui.main.adapters.main.holder.HeaderViewHolder
import org.breezyweather.ui.main.adapters.main.holder.SummaryViewHolder
import org.breezyweather.ui.main.utils.ForecastSummary
import org.breezyweather.ui.main.adapters.main.holder.HourlyViewHolder
import org.breezyweather.ui.main.adapters.main.holder.HumidityViewHolder
import org.breezyweather.ui.main.adapters.main.holder.MoonViewHolder
import org.breezyweather.ui.main.adapters.main.holder.PollenViewHolder
import org.breezyweather.ui.main.adapters.main.holder.PrecipitationNowcastViewHolder
import org.breezyweather.ui.main.adapters.main.holder.PrecipitationViewHolder
import org.breezyweather.ui.main.adapters.main.holder.PressureViewHolder
import org.breezyweather.ui.main.adapters.main.holder.SunViewHolder
import org.breezyweather.ui.main.adapters.main.holder.UvViewHolder
import org.breezyweather.ui.main.adapters.main.holder.VisibilityViewHolder
import org.breezyweather.ui.main.adapters.main.holder.WindViewHolder
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import java.util.Calendar
import java.util.Collections
import java.util.Date

/**
 * The cards of the home screen: which ones show for a location (from the user's card settings and
 * the data available), the card object for each, and their order, which the user can change by
 * dragging them. Drawn by HomeCardsView.
 */
class MainCards(private val activity: MainActivity) {

    var selectedDailyTab: String? = null
    var selectedHourlyTab: String? = null

    /** View types of the cards to show for [location], in order. */
    fun types(location: Location?): List<Int> {
        val types = mutableListOf<Int>()
        location?.weather?.let { weather ->
            val cardDisplayList = SettingsManager.getInstance(activity).cardDisplayList
            types.add(ViewType.HEADER)
            if (location.weather?.alertList?.any { it.endDate == null || it.endDate!!.time > Date().time } == true) {
                types.add(ViewType.ALERT)
            }
            // iOS style: a sentence about the hours ahead, right under the hero (and any alert)
            if (ForecastSummary.build(activity, location) != null) {
                types.add(ViewType.SUMMARY)
            }
            for (c in cardDisplayList) {
                if (c === CardDisplay.CARD_NOWCAST &&
                    (!weather.hasMinutelyPrecipitation || weather.minutelyForecast.size < 3)
                ) {
                    continue
                }
                if (c === CardDisplay.CARD_HIGHLIGHTS && Highlights.build(activity, location).isEmpty()) {
                    continue
                }
                if (c === CardDisplay.CARD_DAILY_FORECAST && weather.dailyForecast.isEmpty()) {
                    continue
                }
                if (c === CardDisplay.CARD_HOURLY_FORECAST && weather.nextHourlyForecast.isEmpty()) {
                    continue
                }
                if (c === CardDisplay.CARD_PRECIPITATION) {
                    val cal = Date().toCalendarWithTimeZone(location.timeZone)
                    val currentHour = cal[Calendar.HOUR_OF_DAY]
                    val precipitation = if (currentHour < 5) {
                        weather.todayIndex?.let { todayIndex ->
                            weather.dailyForecast.getOrElse(todayIndex.minus(1)) { null }?.night?.precipitation
                        }
                    } else if (currentHour < 17) {
                        weather.today?.day?.precipitation
                    } else {
                        weather.today?.night?.precipitation
                    }
                    if (precipitation?.total == null) {
                        continue
                    }
                }
                if (c === CardDisplay.CARD_WIND && weather.current?.wind?.isValid != true) {
                    continue
                }
                if (c === CardDisplay.CARD_AIR_QUALITY && weather.validAirQuality == null) {
                    continue
                }
                if (c === CardDisplay.CARD_POLLEN &&
                    (weather.dailyForecast.isEmpty() || weather.today?.pollen?.isIndexValid != true)
                ) {
                    continue
                }
                if (c === CardDisplay.CARD_HUMIDITY && weather.current?.relativeHumidity == null) {
                    continue
                }
                if (c === CardDisplay.CARD_UV && weather.current?.uV?.index == null) {
                    continue
                }
                if (c === CardDisplay.CARD_VISIBILITY && weather.current?.visibility == null) {
                    continue
                }
                if (c === CardDisplay.CARD_PRESSURE && weather.current?.pressure == null) {
                    continue
                }
                if (c === CardDisplay.CARD_SUN &&
                    (weather.dailyForecast.isEmpty() || weather.today?.sun == null)
                ) {
                    continue
                }
                if (c === CardDisplay.CARD_MOON &&
                    (weather.dailyForecast.isEmpty() || weather.today?.moon?.isValid != true)
                ) {
                    continue
                }
                types.add(getViewType(c))
            }
            types.add(ViewType.FOOTER)
        }
        return types
    }

    fun create(viewType: Int): AbstractMainViewHolder = when (viewType) {
        ViewType.HEADER -> HeaderViewHolder(activity)
        ViewType.SUMMARY -> SummaryViewHolder(activity)
        ViewType.ALERT -> AlertViewHolder(activity)
        ViewType.PRECIPITATION_NOWCAST -> PrecipitationNowcastViewHolder(activity)
        ViewType.DAILY -> DailyViewHolder(activity)
        ViewType.HOURLY -> HourlyViewHolder(activity)
        ViewType.PRECIPITATION -> PrecipitationViewHolder(activity)
        ViewType.WIND -> WindViewHolder(activity)
        ViewType.HUMIDITY -> HumidityViewHolder(activity)
        ViewType.UV -> UvViewHolder(activity)
        ViewType.AIR_QUALITY -> AirQualityViewHolder(activity)
        ViewType.POLLEN -> PollenViewHolder(activity)
        ViewType.VISIBILITY -> VisibilityViewHolder(activity)
        ViewType.PRESSURE -> PressureViewHolder(activity)
        ViewType.SUN -> SunViewHolder(activity)
        ViewType.MOON -> MoonViewHolder(activity)
        ViewType.CLOCK -> ClockViewHolder(activity)
        ViewType.HIGHLIGHTS -> HighlightsViewHolder(activity)
        else -> FooterViewHolder(activity)
    }

    fun bind(
        holder: AbstractMainViewHolder,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        when (holder) {
            is HourlyViewHolder -> holder.onBindView(
                activity, location, provider, listAnimationEnabled, itemAnimationEnabled, selectedHourlyTab
            ) { selectedHourlyTab = it }
            is DailyViewHolder -> holder.onBindView(
                activity, location, provider, listAnimationEnabled, itemAnimationEnabled, selectedDailyTab
            ) { selectedDailyTab = it }
            is AbstractMainCardViewHolder -> holder.onBindView(
                activity, location, provider, listAnimationEnabled, itemAnimationEnabled
            )
            else -> holder.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        }
    }

    /** The hero, summary, alerts and footer stay in place; every other card can be dragged. */
    fun isDraggable(viewType: Int): Boolean =
        viewType !in arrayOf(ViewType.HEADER, ViewType.SUMMARY, ViewType.ALERT, ViewType.FOOTER)

    /** Swaps two cards in [types] and in the saved card order. Returns false when they can't be swapped. */
    fun swap(types: MutableList<Int>, from: Int, to: Int): Boolean {
        val fromCard = getCardDisplay(types.getOrNull(from) ?: return false) ?: return false
        val toCard = getCardDisplay(types.getOrNull(to) ?: return false) ?: return false
        val cardDisplayList = SettingsManager.getInstance(activity).cardDisplayList
        val fromCardPosition = cardDisplayList.indexOf(fromCard)
        val toCardPosition = cardDisplayList.indexOf(toCard)
        if (fromCardPosition == -1 || toCardPosition == -1) return false
        Collections.swap(types, from, to)
        Collections.swap(cardDisplayList, fromCardPosition, toCardPosition)
        SettingsManager.getInstance(activity).cardDisplayList = cardDisplayList
        return true
    }

    companion object {
        private fun getViewType(cardDisplay: CardDisplay): Int = when (cardDisplay) {
            CardDisplay.CARD_NOWCAST -> ViewType.PRECIPITATION_NOWCAST
            CardDisplay.CARD_DAILY_FORECAST -> ViewType.DAILY
            CardDisplay.CARD_HOURLY_FORECAST -> ViewType.HOURLY
            CardDisplay.CARD_PRECIPITATION -> ViewType.PRECIPITATION
            CardDisplay.CARD_WIND -> ViewType.WIND
            CardDisplay.CARD_AIR_QUALITY -> ViewType.AIR_QUALITY
            CardDisplay.CARD_POLLEN -> ViewType.POLLEN
            CardDisplay.CARD_HUMIDITY -> ViewType.HUMIDITY
            CardDisplay.CARD_UV -> ViewType.UV
            CardDisplay.CARD_VISIBILITY -> ViewType.VISIBILITY
            CardDisplay.CARD_PRESSURE -> ViewType.PRESSURE
            CardDisplay.CARD_SUN -> ViewType.SUN
            CardDisplay.CARD_MOON -> ViewType.MOON
            CardDisplay.CARD_CLOCK -> ViewType.CLOCK
            CardDisplay.CARD_HIGHLIGHTS -> ViewType.HIGHLIGHTS
        }

        private fun getCardDisplay(viewType: Int): CardDisplay? = when (viewType) {
            ViewType.PRECIPITATION_NOWCAST -> CardDisplay.CARD_NOWCAST
            ViewType.DAILY -> CardDisplay.CARD_DAILY_FORECAST
            ViewType.HOURLY -> CardDisplay.CARD_HOURLY_FORECAST
            ViewType.PRECIPITATION -> CardDisplay.CARD_PRECIPITATION
            ViewType.WIND -> CardDisplay.CARD_WIND
            ViewType.AIR_QUALITY -> CardDisplay.CARD_AIR_QUALITY
            ViewType.POLLEN -> CardDisplay.CARD_POLLEN
            ViewType.HUMIDITY -> CardDisplay.CARD_HUMIDITY
            ViewType.UV -> CardDisplay.CARD_UV
            ViewType.VISIBILITY -> CardDisplay.CARD_VISIBILITY
            ViewType.PRESSURE -> CardDisplay.CARD_PRESSURE
            ViewType.SUN -> CardDisplay.CARD_SUN
            ViewType.MOON -> CardDisplay.CARD_MOON
            ViewType.CLOCK -> CardDisplay.CARD_CLOCK
            ViewType.HIGHLIGHTS -> CardDisplay.CARD_HIGHLIGHTS
            else -> null
        }

        /**
         * How many tiles share the row of the tile at [position]: [blocksPerRow] in full rows, fewer in
         * the last row of a run of tiles, where they then share the whole width (no lonely tile with an
         * empty space next to it). 1 for full-width cards.
         */
        fun tilesInRow(types: List<Int>, position: Int, blocksPerRow: Int): Int {
            fun isTile(i: Int) = ViewType.isHalfSizeableBlock(types[i]) == true
            if (position !in types.indices || !isTile(position)) return 1
            var start = position
            while (start > 0 && isTile(start - 1)) start--
            var end = position
            while (end < types.lastIndex && isTile(end + 1)) end++
            val count = end - start + 1
            val fullRowsTiles = count / blocksPerRow * blocksPerRow
            return if (position - start >= fullRowsTiles) count - fullRowsTiles else blocksPerRow
        }
    }
}
