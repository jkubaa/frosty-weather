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

package org.breezyweather.ui.common.frosty

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.breezyweather.R

/** Corner radius of an inset grouped list (iOS 26). */
val FrostyGroupRadius = 26.dp

/** Horizontal inset of cell content. */
val FrostyCellPadding = 16.dp

private val IconTileSize = 30.dp
private val IconTileGap = 14.dp

/**
 * Where a cell sits in its group, so it can draw the separator above it. Cells outside a group
 * count as first (no separator).
 */
val LocalFrostyCellIsFirst = compositionLocalOf { true }

/**
 * Whether cells are drawn inside a sheet (lighter cells in dark mode).
 */
val LocalFrostyElevated = compositionLocalOf { false }

@Composable
fun frostyCellColor(): Color = if (LocalFrostyElevated.current) frostyColors.elevatedCell else frostyColors.cell

/**
 * A cell of an inset grouped list: the group's rounded corners on its first/last cells, square in
 * between. Content cells draw their own separators (see [LocalFrostyCellIsFirst]).
 */
@Composable
fun FrostyCell(
    modifier: Modifier = Modifier,
    isFirst: Boolean = true,
    isLast: Boolean = true,
    color: Color = frostyCellColor(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val top = if (isFirst) FrostyGroupRadius else 0.dp
    val bottom = if (isLast) FrostyGroupRadius else 0.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(top, top, bottom, bottom))
            .background(color)
    ) {
        CompositionLocalProvider(LocalFrostyCellIsFirst provides isFirst) {
            content()
        }
    }
}

/**
 * A whole inset group. Wrap every cell after the first in [FrostyFollowingCell] so it draws the
 * separator above it.
 */
@Composable
fun FrostyGroup(
    modifier: Modifier = Modifier,
    color: Color = frostyCellColor(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FrostyGroupRadius))
            .background(color)
    ) {
        CompositionLocalProvider(LocalFrostyCellIsFirst provides true) {
            content()
        }
    }
}

/**
 * Marks [content] as a cell that follows another in its group.
 */
@Composable
fun FrostyFollowingCell(following: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFrostyCellIsFirst provides !following, content = content)
}

/**
 * Draws the hairline separator above a cell (unless it is the first of its group), starting at
 * [inset] like iOS, where the cell's text begins.
 */
@Composable
fun Modifier.frostySeparator(inset: Dp = FrostyCellPadding): Modifier {
    if (LocalFrostyCellIsFirst.current) return this
    val color = frostyColors.separator
    return this.drawBehind {
        val y = 0f
        drawLine(
            color = color,
            start = Offset(inset.toPx(), y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
    }
}

/**
 * The colour of the tile behind a settings icon, iOS Settings-style.
 */
@Composable
fun frostyIconTileColor(@DrawableRes iconId: Int): Color {
    val c = frostyColors
    return when (iconId) {
        R.drawable.ic_sync -> c.green
        R.drawable.ic_palette -> c.tint
        R.drawable.ic_home -> c.indigo
        R.drawable.ic_notifications -> c.red
        R.drawable.ic_widgets -> c.purple
        R.drawable.ic_location -> c.tint
        R.drawable.ic_factory -> c.orange
        R.drawable.ic_bug_report -> c.gray
        R.drawable.ic_settings -> c.gray
        R.drawable.ic_about -> c.tint
        R.drawable.ic_shield_lock -> c.tint
        R.drawable.ic_contract -> c.gray
        R.drawable.ic_code -> c.indigo
        R.drawable.ic_forum -> c.green
        R.drawable.ic_list -> c.orange
        else -> c.gray
    }
}

/**
 * A rounded square tile with a white glyph, as in iOS Settings.
 */
@Composable
fun FrostyIconTile(
    @DrawableRes iconId: Int,
    modifier: Modifier = Modifier,
    color: Color = frostyIconTileColor(iconId),
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(IconTileSize)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * An iOS list cell: optional icon tile, title, optional subtitle and a trailing accessory.
 */
@Composable
fun FrostyRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconId: Int? = null,
    icon: (@Composable () -> Unit)? = null,
    iconImage: ImageBitmap? = null,
    enabled: Boolean = true,
    titleColor: Color = frostyColors.label,
    summaryMaxLines: Int = Int.MAX_VALUE,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val hasIcon = iconId != null || icon != null || iconImage != null
    val separatorInset = if (hasIcon) FrostyCellPadding + IconTileSize + IconTileGap else FrostyCellPadding
    Row(
        modifier = modifier
            .fillMaxWidth()
            .frostySeparator(separatorInset)
            .then(if (onClick != null) Modifier.frostyClickable(enabled = enabled, onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 52.dp)
            .padding(start = FrostyCellPadding, end = FrostyCellPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasIcon) {
            Box(Modifier.alpha(if (enabled) 1f else 0.4f)) {
                when {
                    icon != null -> icon()
                    iconImage != null -> Image(
                        bitmap = iconImage,
                        contentDescription = null,
                        modifier = Modifier
                            .size(IconTileSize)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    else -> FrostyIconTile(iconId!!)
                }
            }
            Spacer(Modifier.width(IconTileGap))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = if (summary.isNullOrEmpty()) 13.dp else 10.dp)
                .alpha(if (enabled) 1f else 0.4f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = title, style = FrostyType.body, color = titleColor)
            if (!summary.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = FrostyType.subheadline,
                    color = frostyColors.secondaryLabel,
                    maxLines = summaryMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (enabled) 1f else 0.4f)
            ) {
                trailing()
            }
        }
    }
}

/**
 * Trailing value text, as in "Units    Metric ›".
 */
@Composable
fun FrostyValueText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = FrostyType.body,
        color = frostyColors.secondaryLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * Section header above a group.
 */
@Composable
fun FrostySectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = FrostyType.footnote,
        color = frostyColors.secondaryLabel,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = FrostyCellPadding, end = FrostyCellPadding, top = 6.dp, bottom = 7.dp)
    )
}

/**
 * Explanatory text below a group.
 */
@Composable
fun FrostySectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = FrostyType.footnote,
        color = frostyColors.secondaryLabel,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = FrostyCellPadding, end = FrostyCellPadding, top = 7.dp)
    )
}

/**
 * A plain cell height spacer used to keep min touch heights in custom cells.
 */
@Composable
fun FrostyCellMinHeight(modifier: Modifier = Modifier) = Spacer(modifier.defaultMinSize(minHeight = 52.dp))

/**
 * An inset group that stacks whatever cells it is given and draws the hairline separators between
 * them itself, for content whose cells do not know their position.
 */
@Composable
fun FrostyAutoGroup(
    modifier: Modifier = Modifier,
    color: Color = frostyCellColor(),
    separatorInset: Dp = FrostyCellPadding,
    content: @Composable () -> Unit,
) {
    val separator = frostyColors.separator
    val boundaries = remember { IntArrayHolder() }
    Layout(
        content = content,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FrostyGroupRadius))
            .background(color)
            .drawWithContent {
                drawContent()
                val inset = separatorInset.toPx()
                for (i in 0 until boundaries.count) {
                    val y = boundaries.values[i].toFloat()
                    drawLine(separator, Offset(inset, y), Offset(size.width, y), strokeWidth = 1f)
                }
            }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val height = placeables.sumOf { it.height }
        layout(constraints.maxWidth, height) {
            var y = 0
            val lines = IntArray(placeables.size)
            var n = 0
            placeables.forEachIndexed { i, p ->
                if (i > 0 && p.height > 0) lines[n++] = y
                p.place(0, y)
                y += p.height
            }
            boundaries.values = lines
            boundaries.count = n
        }
    }
}

private class IntArrayHolder {
    var values = IntArray(0)
    var count = 0
}
