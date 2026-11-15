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

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import org.breezyweather.ui.common.composables.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import org.breezyweather.R
import org.breezyweather.ui.common.frosty.ChevronDirection
import org.breezyweather.ui.common.frosty.FrostyCheckmark
import org.breezyweather.ui.common.frosty.FrostyChevron
import org.breezyweather.ui.common.frosty.FrostyMenu
import org.breezyweather.ui.common.frosty.FrostyPickerItem
import org.breezyweather.ui.common.frosty.FrostyPickerSheet
import org.breezyweather.ui.common.frosty.FrostyRow
import org.breezyweather.ui.common.frosty.FrostyValueText
import androidx.compose.foundation.layout.widthIn
import org.breezyweather.common.options.appearance.CalendarHelper
import org.breezyweather.common.options.appearance.LocaleHelper
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.source.SelectableSource
import org.breezyweather.ui.common.composables.AlertDialogNoPadding
import org.breezyweather.ui.common.widgets.Material3ExpressiveCardListItem
import org.breezyweather.ui.common.widgets.defaultCardListItemElevation
import org.breezyweather.ui.theme.compose.themeRipple
import java.util.Date

@Composable
fun ListPreferenceView(
    @StringRes titleId: Int,
    @ArrayRes valueArrayId: Int,
    @ArrayRes nameArrayId: Int,
    selectedKey: String,
    @ArrayRes summaryArrayId: Int? = null,
    @DrawableRes iconId: Int? = null,
    enabled: Boolean = true,
    card: Boolean = false,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    colors: ListItemColors = ListItemDefaults.colors(),
    withState: Boolean = true,
    onValueChanged: (String) -> Unit,
) {
    val values = stringArrayResource(valueArrayId)
    val names = stringArrayResource(nameArrayId)
    val summaries = if (summaryArrayId == null) names else stringArrayResource(summaryArrayId)
    if (card) {
        ListPreferenceViewWithCard(
            title = stringResource(titleId),
            iconId = iconId,
            summary = { _, value -> summaries[values.indexOfFirst { it == value }] },
            selectedKey = selectedKey,
            valueArray = values,
            nameArray = names,
            enabled = enabled,
            isFirst = isFirst,
            isLast = isLast,
            colors = colors,
            withState = withState,
            onValueChanged = onValueChanged
        )
    } else {
        ListPreferenceView(
            title = stringResource(titleId),
            iconId = iconId,
            summary = { _, value -> summaries[values.indexOfFirst { it == value }] },
            selectedKey = selectedKey,
            valueArray = values,
            nameArray = names,
            enabled = enabled,
            colors = colors,
            withState = withState,
            onValueChanged = onValueChanged
        )
    }
}

@Composable
fun ListPreferenceViewWithCard(
    title: String,
    summary: (Context, String) -> String?, // value -> summary.
    selectedKey: String,
    valueArray: Array<String>,
    nameArray: Array<String>,
    @DrawableRes iconId: Int? = null,
    enableArray: Array<Boolean>? = null,
    enabled: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    colors: ListItemColors = ListItemDefaults.colors(),
    withState: Boolean = true,
    dismissButton: @Composable (() -> Unit)? = null,
    onValueChanged: (String) -> Unit,
) {
    Material3ExpressiveCardListItem(
        elevation = if (enabled) defaultCardListItemElevation else 0.dp,
        isFirst = isFirst,
        isLast = isLast
    ) {
        ListPreferenceView(
            title = title,
            summary = summary,
            selectedKey = selectedKey,
            valueArray = valueArray,
            nameArray = nameArray,
            iconId = iconId,
            enableArray = enableArray,
            enabled = enabled,
            card = true,
            colors = colors,
            withState = withState,
            dismissButton = dismissButton,
            onValueChanged = onValueChanged
        )
    }
}

@Composable
fun ListPreferenceView(
    title: String,
    summary: (Context, String) -> String?, // value -> summary.
    selectedKey: String,
    valueArray: Array<String>,
    nameArray: Array<String>,
    modifier: Modifier = Modifier,
    @DrawableRes iconId: Int? = null,
    enableArray: Array<Boolean>? = null,
    enabled: Boolean = true,
    card: Boolean = false,
    colors: ListItemColors = ListItemDefaults.colors(),
    withState: Boolean = true,
    dismissButton: @Composable (() -> Unit)? = null,
    trailingContent: @Composable ((String) -> Unit)? = null, // value -> trailing content
    onValueChanged: (String) -> Unit,
) {
    val listSelectedState = remember { mutableStateOf(selectedKey) }
    val dialogOpenState = remember { mutableStateOf(false) }
    val currentKey = if (withState) listSelectedState.value else selectedKey
    val currentSummary = summary(LocalContext.current, currentKey)
    // Short values sit on the right like an iOS pop-up button; longer ones go below the title
    val inline = currentSummary != null && currentSummary.length <= 22 && trailingContent == null

    val pickerItems = valueArray.zip(nameArray).mapIndexed { i, (value, name) ->
        // Special case with groups: an empty name marks a group title
        if (name.isEmpty()) {
            FrostyPickerItem(key = value, name = value, header = true)
        } else {
            FrostyPickerItem(key = value, name = name, enabled = enableArray?.getOrNull(i) ?: true)
        }
    }
    val select: (String) -> Unit = {
        if (withState) {
            listSelectedState.value = it
        }
        dialogOpenState.value = false
        onValueChanged(it)
    }

    // iOS pop-up button: the options open in a menu next to the cell
    Box {
        FrostyRow(
            title = title,
            summary = if (inline) null else currentSummary,
            iconId = iconId,
            enabled = enabled,
            modifier = modifier,
            onClick = { dialogOpenState.value = true },
            trailing = {
                if (trailingContent != null) trailingContent(currentKey)
                if (inline) {
                    FrostyValueText(currentSummary!!, Modifier.widthIn(max = 180.dp))
                    Spacer(Modifier.width(6.dp))
                }
                FrostyChevron(direction = ChevronDirection.UpDown, size = 14.dp)
            }
        )
        if (dialogOpenState.value && dismissButton == null) {
            FrostyMenu(
                items = pickerItems,
                selectedKey = currentKey,
                onSelect = select,
                onDismissRequest = { dialogOpenState.value = false }
            )
        }
    }

    // An extra action button doesn't fit in a menu: use a sheet then
    if (dialogOpenState.value && dismissButton != null) {
        FrostyPickerSheet(
            title = title,
            items = pickerItems,
            selectedKey = currentKey,
            onSelect = select,
            onDismissRequest = { dialogOpenState.value = false },
            buttons = listOf(dismissButton)
        )
    }
}

@Composable
fun ListPreferenceWithGroupsView(
    title: String,
    summary: (Context, String) -> String?, // value -> summary.
    selectedKey: String,
    values: ImmutableMap<String?, ImmutableList<SelectableSource>>,
    modifier: Modifier = Modifier,
    @DrawableRes iconId: Int? = null,
    enabled: Boolean = true,
    colors: ListItemColors = ListItemDefaults.colors(),
    withState: Boolean = true,
    dismissButton: @Composable (() -> Unit)? = null,
    trailingContent: @Composable ((String) -> Unit)? = null, // value -> trailing content
    onValueChanged: (String) -> Unit,
) {
    val keyList = mutableListOf<String>()
    val nameList = mutableListOf<String>()
    val enableList = mutableListOf<Boolean>()
    for ((k1, v1) in values) {
        if (k1 != null) {
            // Typed array, so no null; we use an empty value
            // Since it's possible to have empty keys, we must do it the other way around for the
            // ListPreferenceView to recognize our group
            keyList.add(k1)
            nameList.add("")
            enableList.add(false)
        }
        for (selectableSource in v1) {
            keyList.add(selectableSource.id)
            nameList.add(selectableSource.displayName)
            enableList.add(selectableSource.enabled)
        }
    }

    return ListPreferenceView(
        modifier = modifier,
        title = title,
        iconId = iconId,
        summary = summary,
        selectedKey = selectedKey,
        valueArray = keyList.toTypedArray(),
        nameArray = nameList.toTypedArray(),
        enableArray = enableList.toTypedArray(),
        enabled = enabled,
        colors = colors,
        withState = withState,
        dismissButton = dismissButton,
        trailingContent = trailingContent,
        onValueChanged = onValueChanged
    )
}

/**
 * A selectable option row with a checkmark, iOS-style.
 */
@Composable
internal fun RadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    enabled: Boolean = true,
) {
    FrostyRow(
        title = text,
        enabled = enabled,
        onClick = onClick,
        trailing = if (selected) {
            { FrostyCheckmark() }
        } else {
            null
        }
    )
}

@Composable
fun LanguagePreferenceView(
    @StringRes titleId: Int,
    isFirst: Boolean = false,
    isLast: Boolean = false,
) {
    val context = LocalContext.current

    val langs = remember { LocaleHelper.getLangs(context) }
    val currentLanguage by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() ?: "")
    }

    ListPreferenceViewWithCard(
        title = stringResource(titleId),
        summary = { _, value -> langs.firstOrNull { value == it.langTag }?.displayName ?: "" },
        selectedKey = langs.firstOrNull { currentLanguage == it.langTag }?.langTag ?: "",
        valueArray = langs.map { it.langTag }.toTypedArray(),
        nameArray = langs.map { it.displayName }.toTypedArray(),
        isFirst = isFirst,
        isLast = isLast
    ) {
        val locale = if (it.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(it)
        }
        // Since Android 13, if a night mode is persisted for the app, a new app-specific language is
        // only persisted if it was previously reset to "follow system". See BreezyWeather.updateDayNightMode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !locale.isEmpty) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locale)
        SettingsManager.getInstance(context).languageUpdateLastTimestamp = Date().time
    }
}

@Composable
fun CalendarPreferenceView(
    @StringRes titleId: Int,
    isFirst: Boolean = false,
    isLast: Boolean = false,
) {
    val context = LocalContext.current

    val calendars = remember { CalendarHelper.getCalendars(context) }
    val currentCalendar by remember {
        mutableStateOf(SettingsManager.getInstance(context).alternateCalendar)
    }

    ListPreferenceViewWithCard(
        title = stringResource(titleId),
        summary = { _, value -> calendars.firstOrNull { value == it.id }?.displayName ?: "" },
        selectedKey = calendars.firstOrNull { currentCalendar == it.id }?.id ?: "",
        valueArray = calendars.map { it.id }.toTypedArray(),
        nameArray = calendars.map { it.displayName }.toTypedArray(),
        isFirst = isFirst,
        isLast = isLast
    ) {
        SettingsManager.getInstance(context).alternateCalendar = it
    }
}
