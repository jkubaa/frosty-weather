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

package org.breezyweather.ui.settings.preference.composables

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.breezyweather.R
import org.breezyweather.ui.common.widgets.Material3ExpressiveCardListItem
import org.breezyweather.ui.common.widgets.defaultCardListItemElevation
import kotlin.math.roundToInt

/**
 * A preference card with a title, the current value on the right, an optional summary,
 * and a slider underneath. Styled like [SwitchPreferenceView]'s card.
 *
 * The value is saved through [onValueChanged] when the user lets go of the slider, not on every
 * step of a drag, so listeners (like the main screen redrawing) only run once.
 *
 * @param valueLabel how to display a value, e.g. "40%" or "Off"
 * @param steps number of stops between the ends, as for Material [Slider]
 */
@Composable
fun SliderPreferenceView(
    @StringRes titleId: Int,
    value: Int,
    valueRange: IntRange,
    steps: Int,
    valueLabel: @Composable (Int) -> String,
    summary: String? = null,
    enabled: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onValueChanged: (Int) -> Unit,
) {
    val state = remember { mutableFloatStateOf(value.toFloat()) }
    val currentValue = state.floatValue.roundToInt()
    val currentLabel = valueLabel(currentValue)

    Material3ExpressiveCardListItem(
        elevation = if (enabled) defaultCardListItemElevation else 0.dp,
        isFirst = isFirst,
        isLast = isLast
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.5f)
                .padding(dimensionResource(R.dimen.normal_margin))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(titleId),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = currentLabel,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (!summary.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.small_margin)))
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Slider(
                value = state.floatValue,
                onValueChange = { state.floatValue = it },
                onValueChangeFinished = { onValueChanged(state.floatValue.roundToInt()) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = steps,
                enabled = enabled,
                modifier = Modifier.semantics { stateDescription = currentLabel }
            )
        }
    }
}
