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
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.formatPercent
import org.breezyweather.unit.formatting.UnitWidth
import org.breezyweather.unit.formatting.format
import org.breezyweather.unit.ratio.Ratio
import org.breezyweather.unit.temperature.Temperature
import org.breezyweather.unit.temperature.TemperatureUnit
import kotlin.math.roundToInt

/**
 * Formatting shared by the liquid glass forecast cards.
 */
internal object GlassForecastFormat {

    /** Chances of precipitation below this are noise and not shown under the icons, like iOS weather. */
    private const val PRECIPITATION_THRESHOLD_PERCENT = 20.0

    /** Compact "21°": the unit is a setting, so the cards only show the degree sign. */
    fun degrees(context: Context, temperature: Temperature, unit: TemperatureUnit): String {
        return temperature.toDouble(unit).roundToInt().format(decimals = 0, locale = context.currentLocale) + "°"
    }

    /** "40%" when the chance of precipitation is worth showing, null otherwise. */
    fun precipitation(context: Context, probability: Ratio?): String? {
        return probability
            ?.takeIf { it.inPercent >= PRECIPITATION_THRESHOLD_PERCENT }
            ?.formatPercent(context, UnitWidth.NARROW)
    }
}
