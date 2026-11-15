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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.breezyweather.R
import org.breezyweather.ui.common.frosty.FrostyChevron
import org.breezyweather.ui.common.frosty.FrostyRow
import org.breezyweather.ui.common.frosty.FrostyXMark
import org.breezyweather.ui.common.frosty.frostyColors
import org.breezyweather.ui.common.frosty.frostyDimClickable
import org.breezyweather.ui.common.widgets.Material3ExpressiveCardListItem

@Composable
fun PreferenceViewWithCard(
    @StringRes titleId: Int,
    modifier: Modifier = Modifier,
    @StringRes summaryId: Int? = null,
    @DrawableRes iconId: Int? = null,
    enabled: Boolean = true,
    colors: ListItemColors = ListItemDefaults.colors(),
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onClick: () -> Unit,
) = PreferenceViewWithCard(
    title = stringResource(titleId),
    modifier = modifier,
    summary = if (summaryId != null) stringResource(summaryId) else null,
    iconId = iconId,
    enabled = enabled,
    colors = colors,
    isFirst = isFirst,
    isLast = isLast,
    onClick = onClick
)

@Composable
fun PreferenceView(
    @StringRes titleId: Int,
    modifier: Modifier = Modifier,
    @StringRes summaryId: Int? = null,
    @DrawableRes iconId: Int? = null,
    enabled: Boolean = true,
    colors: ListItemColors = ListItemDefaults.colors(),
    onClick: () -> Unit,
) = PreferenceView(
    title = stringResource(titleId),
    modifier = modifier,
    summary = if (summaryId != null) stringResource(summaryId) else null,
    iconId = iconId,
    enabled = enabled,
    colors = colors,
    onClick = onClick
)

@Composable
fun PreferenceViewWithCard(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconId: Int? = null,
    enabled: Boolean = true,
    colors: ListItemColors = ListItemDefaults.colors(),
    surface: Color = MaterialTheme.colorScheme.surface,
    onSurface: Color = MaterialTheme.colorScheme.onSurface,
    onClose: (() -> Unit)? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onClick: () -> Unit,
) {
    Material3ExpressiveCardListItem(
        surface = surface,
        onSurface = onSurface,
        isFirst = isFirst,
        isLast = isLast,
        modifier = modifier
    ) {
        PreferenceView(
            title = title,
            summary = summary,
            iconId = iconId,
            enabled = enabled,
            card = true,
            colors = colors,
            onClose = onClose,
            onClick = onClick
        )
    }
}

/**
 * An iOS list cell that opens something (a screen or a dialog): title, summary and a chevron.
 * [card] and [colors] are kept for compatibility; the cell is transparent so it sits on its group.
 */
@Composable
fun PreferenceView(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconId: Int? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") card: Boolean = false,
    @Suppress("UNUSED_PARAMETER") colors: ListItemColors = ListItemDefaults.colors(),
    onClose: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val closeDescription = stringResource(R.string.action_close)
    FrostyRow(
        title = title,
        summary = summary,
        iconId = iconId,
        enabled = enabled,
        modifier = modifier,
        onClick = onClick,
        trailing = {
            if (onClose != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(frostyColors.tertiaryFill)
                        .frostyDimClickable(onClick = onClose)
                        .semantics { contentDescription = closeDescription }
                ) {
                    FrostyXMark(color = frostyColors.secondaryLabel, size = 10.dp)
                }
            } else {
                FrostyChevron(modifier = Modifier.padding(start = 2.dp))
            }
        }
    )
}
