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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

private val MenuWidth = 290.dp
private val MenuRadius = 26.dp
private val MenuItemHeight = 46.dp

@Composable
private fun menuBackground(): Color = if (frostyColors.isDark) Color(0xFF3A3A3C) else Color(0xFFFFFFFF)

/**
 * Places a menu next to the cell it was opened from, aligned to the cell's end: below it when it
 * fits, otherwise above, otherwise as low as the window allows.
 */
private class MenuPositionProvider(
    private val marginPx: Int,
    private val gapPx: Int,
    private val onPlacedBelow: (Boolean) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val preferredX = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - w - marginPx
        } else {
            anchorBounds.left + marginPx
        }
        val x = preferredX.coerceIn(marginPx, (windowSize.width - w - marginPx).coerceAtLeast(marginPx))
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - gapPx - h
        val y = when {
            below + h <= windowSize.height - marginPx -> below.also { onPlacedBelow(true) }
            above >= marginPx -> above.also { onPlacedBelow(false) }
            else -> (windowSize.height - marginPx - h).coerceAtLeast(marginPx).also { onPlacedBelow(true) }
        }
        return IntOffset(x, y)
    }
}

/**
 * iOS pull-down menu anchored to the composable it is placed in: a rounded card with a checkmark
 * on the selected option, section titles for groups, and a thick separator between groups.
 * Long lists scroll. A [FrostyPickerItem] with `header = true` starts a section.
 */
@Composable
fun FrostyMenu(
    items: List<FrostyPickerItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val colors = frostyColors
    val density = LocalDensity.current
    val placedBelow = remember { booleanArrayOf(true) }
    val provider = remember(density) {
        with(density) {
            MenuPositionProvider(
                marginPx = 12.dp.roundToPx(),
                gapPx = 4.dp.roundToPx(),
                onPlacedBelow = { placedBelow[0] = it }
            )
        }
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 650f)) }

    val initialIndex = remember(items, selectedKey) {
        items.indexOfFirst { !it.header && it.key == selectedKey }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (initialIndex - 2).coerceAtLeast(0))

    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        ProvideFrostyTypography {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(MenuWidth)
                    .heightIn(max = maxHeight)
                    .graphicsLayer {
                        val a = appear.value
                        transformOrigin = TransformOrigin(1f, if (placedBelow[0]) 0f else 1f)
                        scaleX = 0.6f + 0.4f * a
                        scaleY = 0.6f + 0.4f * a
                        alpha = a.coerceIn(0f, 1f)
                        shadowElevation = 24.dp.toPx()
                        shape = RoundedCornerShape(MenuRadius)
                        clip = true
                    }
                    .background(menuBackground())
                    .padding(vertical = 6.dp)
            ) {
                itemsIndexed(items) { index, item ->
                    if (item.header) {
                        if (index > 0) {
                            // A soft hairline between groups, inset from the menu's edges
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
                                    .height(0.5.dp)
                                    .background(
                                        if (colors.isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.12f)
                                    )
                            )
                        }
                        Text(
                            text = item.name,
                            style = FrostyType.footnote,
                            color = colors.secondaryLabel,
                            modifier = Modifier.padding(start = 44.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                        )
                    } else {
                        val selected = item.key == selectedKey
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MenuItemHeight)
                                .frostyClickable(enabled = item.enabled) { onSelect(item.key) }
                                .padding(start = 14.dp, end = 16.dp)
                                .alpha(if (item.enabled) 1f else 0.35f)
                        ) {
                            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                                if (selected) FrostyCheckmark(color = colors.label, size = 16.dp)
                            }
                            Text(
                                text = item.name,
                                style = FrostyType.body,
                                color = colors.label,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 10.dp)
                            )
                            item.trailing?.let {
                                Spacer(Modifier.width(8.dp))
                                it()
                            }
                        }
                    }
                }
            }
        }
    }
}
