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

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.breezyweather.R
import org.breezyweather.common.extensions.currentLocale
import org.breezyweather.common.extensions.toBitmap
import org.breezyweather.ui.common.composables.AlertDialog
import org.breezyweather.ui.common.composables.AlertDialogNoPadding
import org.breezyweather.ui.common.composables.TextButton
import org.breezyweather.ui.common.frosty.FrostyChevron
import org.breezyweather.ui.common.frosty.FrostyFollowingCell
import org.breezyweather.ui.common.frosty.FrostyGroup
import org.breezyweather.ui.common.frosty.FrostyRow
import org.breezyweather.ui.common.frosty.FrostySheet
import org.breezyweather.ui.common.frosty.FrostySwitch
import org.breezyweather.ui.common.widgets.Material3ExpressiveCardListItem
import org.breezyweather.ui.common.widgets.defaultCardListItemElevation
import org.breezyweather.ui.theme.compose.themeRipple
import java.text.Collator

data class PreferenceItem(
    val name: String,
    val value: String,
    val icon: Drawable? = null,
    val subname: String? = null,
)

@Composable
fun MultiListPreferenceViewWithCard(
    title: String,
    selectedKeys: ImmutableList<String>,
    itemsArray: Array<PreferenceItem>,
    noItemsMessage: String,
    @DrawableRes iconId: Int? = null,
    enabled: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    colors: ListItemColors = ListItemDefaults.colors(),
    onValueChanged: (List<String>) -> Unit,
) {
    Material3ExpressiveCardListItem(
        elevation = if (enabled) defaultCardListItemElevation else 0.dp,
        isFirst = isFirst,
        isLast = isLast
    ) {
        MultiListPreferenceView(
            title = title,
            selectedKeys = selectedKeys,
            itemsArray = itemsArray,
            noItemsMessage = noItemsMessage,
            iconId = iconId,
            enabled = enabled,
            colors = colors,
            card = true,
            onValueChanged = onValueChanged
        )
    }
}

@Composable
fun MultiListPreferenceView(
    title: String,
    selectedKeys: ImmutableList<String>,
    itemsArray: Array<PreferenceItem>,
    noItemsMessage: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconId: Int? = null,
    enabled: Boolean = true,
    colors: ListItemColors = ListItemDefaults.colors(),
    card: Boolean = false,
    onValueChanged: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val listSelectedState = remember {
        mutableStateListOf<String>().apply {
            addAll(selectedKeys)
        }
    }
    val dialogOpenState = remember { mutableStateOf(false) }
    val currentSummary = listSelectedState.mapNotNull { selectedItem ->
        itemsArray.firstOrNull { it.value == selectedItem }?.name
    }.sortedWith(Collator.getInstance(context.currentLocale))
        .joinToString(stringResource(org.breezyweather.unit.R.string.locale_separator))
        .ifEmpty {
            stringResource(R.string.settings_disabled)
        }

    FrostyRow(
        title = title,
        summary = currentSummary,
        iconId = iconId,
        enabled = enabled,
        modifier = modifier,
        onClick = { dialogOpenState.value = true },
        trailing = { FrostyChevron() }
    )

    if (dialogOpenState.value) {
        if (itemsArray.isNotEmpty()) {
            val cancel = {
                listSelectedState.apply {
                    clear()
                    addAll(selectedKeys)
                }
                dialogOpenState.value = false
            }
            FrostySheet(
                onDismissRequest = cancel,
                title = title,
                buttons = listOf(
                    {
                        TextButton(onClick = cancel) {
                            Text(text = stringResource(android.R.string.cancel))
                        }
                    },
                    {
                        TextButton(
                            onClick = {
                                onValueChanged(listSelectedState)
                                dialogOpenState.value = false
                            }
                        ) {
                            Text(text = stringResource(R.string.action_save))
                        }
                    }
                )
            ) {
                FrostyGroup {
                    itemsArray.forEachIndexed { i, it ->
                        FrostyFollowingCell(following = i > 0) {
                            Switch(
                                icon = it.icon,
                                selected = listSelectedState.contains(it.value),
                                onClick = {
                                    if (listSelectedState.contains(it.value)) {
                                        listSelectedState.remove(it.value)
                                    } else {
                                        listSelectedState.add(it.value)
                                    }
                                },
                                text = it.name,
                                subtext = it.subname
                            )
                        }
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = {
                    dialogOpenState.value = false
                },
                title = {
                    Text(text = title)
                },
                text = {
                    Text(text = noItemsMessage)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dialogOpenState.value = false
                        }
                    ) {
                        Text(text = stringResource(R.string.action_close))
                    }
                }
            )
        }
    }
}

/**
 * A row with a toggle, used inside the multi-select sheet.
 */
@Composable
internal fun Switch(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    @Suppress("UNUSED_PARAMETER") colors: ListItemColors = ListItemDefaults.colors(),
    icon: Drawable? = null,
    subtext: String? = null,
) {
    val bitmap = remember(icon) { icon?.toBitmap()?.asImageBitmap() }
    FrostyRow(
        title = text,
        summary = subtext,
        iconImage = bitmap,
        onClick = onClick,
        trailing = {
            FrostySwitch(checked = selected, onCheckedChange = { onClick() })
        }
    )
}

@Composable
fun PackagePreferenceView(
    title: String,
    selectedKeys: ImmutableList<String>,
    intent: String,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onValueChanged: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val packages = remember {
        context.packageManager.queryBroadcastReceivers(Intent(intent), PackageManager.GET_RESOLVED_FILTER)
    }

    MultiListPreferenceViewWithCard(
        title = title,
        selectedKeys = selectedKeys,
        itemsArray = packages
            .sortedWith { app1, app2 ->
                Collator.getInstance(context.currentLocale).compare(
                    app1.activityInfo.applicationInfo.loadLabel(context.packageManager),
                    app2.activityInfo.applicationInfo.loadLabel(context.packageManager)
                )
            }
            .map {
                PreferenceItem(
                    name = it.activityInfo.applicationInfo.loadLabel(context.packageManager).toString(),
                    subname = it.activityInfo.applicationInfo.packageName,
                    value = it.activityInfo.applicationInfo.packageName,
                    icon = it.activityInfo.applicationInfo.loadIcon(context.packageManager)
                )
            }.toTypedArray(),
        noItemsMessage = stringResource(R.string.settings_modules_broadcast_send_data_summary_empty),
        isFirst = isFirst,
        isLast = isLast,
        onValueChanged = onValueChanged
    )
}
