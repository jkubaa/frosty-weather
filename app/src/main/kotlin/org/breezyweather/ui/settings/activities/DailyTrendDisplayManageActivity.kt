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

package org.breezyweather.ui.settings.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.res.stringResource
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.settings.compose.DisplayOrderScreen
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme
import org.breezyweather.common.options.appearance.DailyTrendDisplay
import org.breezyweather.domain.settings.SettingsManager.Companion.DEFAULT_DAILY_TREND_DISPLAY

/**
 * Choose and order the tabs of the daily forecast card (iOS edit-mode list).
 */
class DailyTrendDisplayManageActivity : BreezyActivity() {
    private val shown = mutableStateListOf<DailyTrendDisplay>()
    private val hidden = mutableStateListOf<DailyTrendDisplay>()

    private fun load(list: List<DailyTrendDisplay>) {
        shown.clear()
        shown.addAll(list)
        hidden.clear()
        hidden.addAll(DailyTrendDisplay.entries.filter { it !in list })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load(SettingsManager.getInstance(this).dailyTrendDisplayList)
        setContent {
            BreezyWeatherTheme {
                DisplayOrderScreen(
                    title = stringResource(R.string.settings_main_daily_trends_title),
                    shown = shown,
                    hidden = hidden,
                    name = { it.getName(this) },
                    onBack = { finish() },
                    onReset = {
                        load(DailyTrendDisplay.toDailyTrendDisplayList(DEFAULT_DAILY_TREND_DISPLAY))
                        setResult(RESULT_OK)
                    },
                    onChanged = { setResult(RESULT_OK) }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val oldList = SettingsManager.getInstance(this).dailyTrendDisplayList
        val newList = shown.toList()
        if (oldList != newList) {
            SettingsManager.getInstance(this).dailyTrendDisplayList = newList
        }
    }
}
