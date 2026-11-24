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
import org.breezyweather.common.bus.EventBus
import org.breezyweather.common.options.appearance.CardDisplay
import org.breezyweather.domain.settings.SettingsChangedMessage
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.settings.SettingsManager.Companion.DEFAULT_CARD_DISPLAY
import org.breezyweather.ui.settings.compose.DisplayOrderScreen
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme

/**
 * Choose and order the main screen cards (iOS edit-mode list).
 */
class CardDisplayManageActivity : BreezyActivity() {
    private val shown = mutableStateListOf<CardDisplay>()
    private val hidden = mutableStateListOf<CardDisplay>()

    private fun load(list: List<CardDisplay>) {
        shown.clear()
        shown.addAll(list)
        hidden.clear()
        hidden.addAll(CardDisplay.entries.filter { it !in list })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load(SettingsManager.getInstance(this).cardDisplayList)
        setContent {
            BreezyWeatherTheme {
                DisplayOrderScreen(
                    title = stringResource(R.string.settings_main_cards_title),
                    shown = shown,
                    hidden = hidden,
                    name = { it.getName(this) },
                    onBack = { finish() },
                    onReset = {
                        load(CardDisplay.toCardDisplayList(DEFAULT_CARD_DISPLAY))
                        setResult(RESULT_OK)
                    },
                    onChanged = { setResult(RESULT_OK) },
                    configure = { card -> card.configure?.let { { it(this) } } }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val oldList = SettingsManager.getInstance(this).cardDisplayList
        val newList = shown.toList()
        if (oldList != newList) {
            SettingsManager.getInstance(this).cardDisplayList = newList
            // We need to do this manually, because the setter above can’t do it, or the main screen would reload
            // on main screen drag & drops
            EventBus
                .instance
                .with(SettingsChangedMessage::class.java)
                .postValue(SettingsChangedMessage())
        }
    }
}
