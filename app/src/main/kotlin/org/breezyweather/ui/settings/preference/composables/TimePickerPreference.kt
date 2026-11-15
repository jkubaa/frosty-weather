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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.breezyweather.R
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.ui.common.composables.AlertDialog
import org.breezyweather.ui.common.composables.TextButton
import org.breezyweather.ui.common.frosty.FrostyRow
import org.breezyweather.ui.common.frosty.FrostyTimeWheels
import org.breezyweather.ui.common.frosty.FrostyValueText
import org.breezyweather.ui.common.widgets.Material3ExpressiveCardListItem
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun TimePickerPreferenceView(
    @StringRes titleId: Int,
    currentTime: String,
    enabled: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onValueChanged: (String) -> Unit,
) = TimePickerPreferenceView(
    title = stringResource(titleId),
    currentTime = currentTime,
    enabled = enabled,
    isFirst = isFirst,
    isLast = isLast,
    onValueChanged = onValueChanged
)

/**
 * An iOS cell showing a time on the right; tapping it opens time wheels in an alert.
 */
@Composable
private fun TimePickerPreferenceView(
    title: String,
    currentTime: String,
    enabled: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onValueChanged: (String) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val currentTimeState = remember { mutableStateOf(currentTime) }
    val context = LocalContext.current
    val is12Hour = context.is12Hour
    val time = SimpleDateFormat("HH:mm", Locale.ENGLISH).parse(currentTimeState.value)

    Material3ExpressiveCardListItem(
        isFirst = isFirst,
        isLast = isLast
    ) {
        FrostyRow(
            title = title,
            enabled = enabled,
            onClick = { showTimePicker = true },
            trailing = {
                time?.getFormattedTime(null, context, is12Hour)?.let { FrostyValueText(it) }
            }
        )
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance()
        time?.let { cal.setTime(it) }
        var hour by remember { mutableIntStateOf(cal[Calendar.HOUR_OF_DAY]) }
        var minute by remember { mutableIntStateOf(cal[Calendar.MINUTE]) }
        val amPm = remember {
            DateFormatSymbols.getInstance().amPmStrings.let { it[0] to it[1] }
        }

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.dialog_time_picker_select_time)) },
            text = {
                FrostyTimeWheels(
                    hour = hour,
                    minute = minute,
                    is24Hour = !is12Hour,
                    amPm = amPm,
                    onTimeChange = { h, m ->
                        hour = h
                        minute = m
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        currentTimeState.value = timeToString(hour = hour, minute = minute)
                        showTimePicker = false
                        onValueChanged(currentTimeState.value)
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        )
    }
}

private fun timeToString(
    hour: Int,
    minute: Int,
): String {
    return Calendar.getInstance()
        .also {
            it.set(Calendar.HOUR_OF_DAY, hour)
            it.set(Calendar.MINUTE, minute)
        }
        .time
        .getFormattedTime(null, null, false)
}
