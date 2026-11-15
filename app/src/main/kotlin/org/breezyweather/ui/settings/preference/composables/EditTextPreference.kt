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
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import org.breezyweather.ui.common.composables.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import org.breezyweather.ui.common.composables.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.breezyweather.R
import org.breezyweather.ui.common.frosty.FrostyChevron
import org.breezyweather.ui.common.frosty.FrostyFootnote
import org.breezyweather.ui.common.frosty.FrostyRow
import org.breezyweather.ui.common.frosty.FrostyTextField
import org.breezyweather.ui.common.frosty.frostyColors
import org.breezyweather.ui.common.widgets.Material3ExpressiveCardListItem
import org.breezyweather.ui.common.widgets.defaultCardListItemElevation
import org.breezyweather.ui.theme.compose.themeRipple

@Composable
fun EditTextPreferenceViewWithCard(
    @StringRes titleId: Int,
    content: String?,
    placeholder: String?,
    modifier: Modifier = Modifier,
    summary: ((Context, String) -> String?)? = null,
    enabled: Boolean = true,
    regex: Regex? = null,
    regexError: String? = null,
    keyboardType: KeyboardType? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onValueChanged: (String) -> Unit,
) {
    Material3ExpressiveCardListItem(
        modifier = modifier,
        elevation = if (enabled) defaultCardListItemElevation else 0.dp,
        isFirst = isFirst,
        isLast = isLast
    ) {
        EditTextPreferenceView(
            title = stringResource(titleId),
            summary = { context, value ->
                summary?.let { it(context, value) }
            },
            content = content ?: "",
            placeholder = placeholder,
            enabled = enabled,
            regex = regex,
            regexError = regexError,
            keyboardType = keyboardType,
            onValueChanged = onValueChanged
        )
    }
}

@Composable
fun EditTextPreferenceView(
    title: String,
    summary: (Context, String) -> String?, // content -> summary.
    content: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    regex: Regex? = null,
    regexError: String? = null,
    keyboardType: KeyboardType? = null,
    onValueChanged: (String) -> Unit,
) {
    val contentState = remember { mutableStateOf(content) }
    val dialogOpenState = remember { mutableStateOf(false) }

    val currentSummary = summary(LocalContext.current, contentState.value)
    FrostyRow(
        title = title,
        summary = currentSummary,
        enabled = enabled,
        modifier = modifier,
        onClick = { dialogOpenState.value = true },
        trailing = { FrostyChevron() }
    )

    if (dialogOpenState.value) {
        val inputState = remember { mutableStateOf(contentState.value) }
        val invalid = regex != null &&
            !inputState.value.matches(regex) &&
            (placeholder.isNullOrEmpty() || inputState.value.isNotEmpty())
        AlertDialog(
            onDismissRequest = { dialogOpenState.value = false },
            title = {
                Text(text = title)
            },
            text = {
                Column {
                    FrostyTextField(
                        value = inputState.value,
                        onValueChange = { inputState.value = it },
                        placeholder = placeholder,
                        isError = invalid,
                        autoFocus = true,
                        keyboardOptions = if (keyboardType != null) {
                            KeyboardOptions(keyboardType = keyboardType)
                        } else {
                            KeyboardOptions.Default
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (invalid) {
                        FrostyFootnote(
                            text = if (!regexError.isNullOrEmpty()) {
                                regexError
                            } else {
                                stringResource(R.string.settings_invalid_syntax)
                            },
                            color = frostyColors.red,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = regex == null || inputState.value.isEmpty() || inputState.value.matches(regex),
                    onClick = {
                        contentState.value = inputState.value
                        dialogOpenState.value = false
                        onValueChanged(contentState.value)
                    }
                ) {
                    Text(text = stringResource(R.string.action_done))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { dialogOpenState.value = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
