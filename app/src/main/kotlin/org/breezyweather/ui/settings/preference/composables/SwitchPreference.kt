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

package org.breezyweather.ui.settings.preference.composables

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.breezyweather.ui.common.frosty.FrostyRow
import org.breezyweather.ui.common.frosty.FrostySwitch
import org.breezyweather.ui.common.widgets.Material3ExpressiveCardListItem

@Composable
fun SwitchPreferenceView(
    @StringRes titleId: Int,
    @StringRes summaryOnId: Int,
    @StringRes summaryOffId: Int,
    checked: Boolean,
    @DrawableRes iconId: Int? = null,
    withState: Boolean = true,
    enabled: Boolean = true,
    card: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    colors: ListItemColors = ListItemDefaults.colors(),
    onValueChanged: (Boolean) -> Unit,
) = SwitchPreferenceView(
    title = stringResource(titleId),
    iconId = iconId,
    summary = { context, it ->
        context.getString(if (it) summaryOnId else summaryOffId)
    },
    checked = checked,
    withState = withState,
    enabled = enabled,
    card = card,
    isFirst = isFirst,
    isLast = isLast,
    colors = colors,
    onValueChanged = onValueChanged
)

/**
 * An iOS list cell with a toggle.
 */
@Composable
fun SwitchPreferenceView(
    title: String,
    summary: (Context, Boolean) -> String?,
    checked: Boolean,
    @DrawableRes iconId: Int? = null,
    withState: Boolean = true,
    enabled: Boolean = true,
    card: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    @Suppress("UNUSED_PARAMETER") colors: ListItemColors = ListItemDefaults.colors(),
    onValueChanged: (Boolean) -> Unit,
) {
    val state = remember { mutableStateOf(checked) }
    val current = if (withState) state.value else checked
    val currentSummary = summary(LocalContext.current, current)

    val toggle: (Boolean) -> Unit = {
        state.value = it
        onValueChanged(it)
    }
    val row = @Composable {
        FrostyRow(
            title = title,
            summary = currentSummary,
            iconId = iconId,
            enabled = enabled,
            onClick = { toggle(!current) },
            trailing = {
                FrostySwitch(checked = current, onCheckedChange = toggle, enabled = enabled)
            }
        )
    }

    if (card) {
        Material3ExpressiveCardListItem(isFirst = isFirst, isLast = isLast) { row() }
    } else {
        row()
    }
}
