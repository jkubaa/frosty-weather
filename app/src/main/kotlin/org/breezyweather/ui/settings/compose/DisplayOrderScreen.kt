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

package org.breezyweather.ui.settings.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.breezyweather.R
import org.breezyweather.common.extensions.plus
import org.breezyweather.ui.common.composables.TextButton
import org.breezyweather.ui.common.frosty.FrostyCell
import org.breezyweather.ui.common.frosty.FrostyFollowingCell
import org.breezyweather.ui.common.frosty.FrostyRow
import org.breezyweather.ui.common.frosty.FrostySectionFooter
import org.breezyweather.ui.common.frosty.FrostySectionHeader
import org.breezyweather.ui.common.frosty.FrostyType
import org.breezyweather.ui.common.frosty.frostyCellColor
import org.breezyweather.ui.common.frosty.frostyColors
import org.breezyweather.ui.common.frosty.frostyDimClickable
import org.breezyweather.ui.common.widgets.Material3Scaffold
import org.breezyweather.ui.common.widgets.generateCollapsedScrollBehavior
import org.breezyweather.ui.common.widgets.insets.FitStatusBarTopAppBar

/**
 * iOS edit-mode list to choose and order items: shown items with a red remove button and a drag
 * handle, then the hidden ones with a green add button.
 *
 * @param configure optional action per item (e.g. which tabs a card shows), drawn as a gear
 */
@Composable
fun <T : Any> DisplayOrderScreen(
    title: String,
    shown: SnapshotStateList<T>,
    hidden: SnapshotStateList<T>,
    name: (T) -> String,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onChanged: () -> Unit,
    configure: ((T) -> (() -> Unit)?)? = null,
) {
    val scrollBehavior = generateCollapsedScrollBehavior()
    Material3Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FitStatusBarTopAppBar(
                title = title,
                onBackPressed = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.action_reset), style = FrostyType.body, color = frostyColors.tint)
                    }
                }
            )
        }
    ) { paddings ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddings.plus(PaddingValues(horizontal = 16.dp)))
        ) {
            FrostySectionHeader(stringResource(R.string.settings_items_shown))
            ReorderableGroup(shown, name, configure, onChanged) { item ->
                shown.remove(item)
                hidden.add(item)
                onChanged()
            }
            FrostySectionFooter(stringResource(R.string.settings_items_order_hint))
            if (hidden.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                FrostySectionHeader(stringResource(R.string.settings_items_more))
                hidden.forEachIndexed { i, item ->
                    key(item) {
                        FrostyCell(isFirst = i == 0, isLast = i == hidden.lastIndex) {
                            FrostyRow(
                                title = name(item),
                                icon = {
                                    EditBadge(add = true) {
                                        hidden.remove(item)
                                        shown.add(item)
                                        onChanged()
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun <T : Any> ReorderableGroup(
    items: SnapshotStateList<T>,
    name: (T) -> String,
    configure: ((T) -> (() -> Unit)?)?,
    onChanged: () -> Unit,
    onRemove: (T) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var dragged by remember { mutableStateOf<T?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(1f) }
    val cell = frostyCellColor()

    Column {
        items.forEachIndexed { i, item ->
            key(item) {
                val isDragged = dragged == item
                FrostyCell(
                    isFirst = i == 0,
                    isLast = i == items.lastIndex,
                    color = cell,
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .onSizeChanged { rowHeight = it.height.toFloat().coerceAtLeast(1f) }
                        .graphicsLayer {
                            translationY = if (isDragged) dragOffset else 0f
                            if (isDragged) {
                                scaleX = 1.03f
                                scaleY = 1.03f
                                shadowElevation = 12.dp.toPx()
                            }
                        }
                ) {
                    FrostyFollowingCell(following = i > 0 && !isDragged) {
                        FrostyRow(
                            title = name(item),
                            icon = { EditBadge(add = false) { onRemove(item) } },
                            trailing = {
                                configure?.invoke(item)?.let { action ->
                                    Icon(
                                        painter = painterResource(R.drawable.ic_settings),
                                        contentDescription = stringResource(R.string.action_configure_item, name(item)),
                                        tint = frostyColors.tint,
                                        modifier = Modifier
                                            .padding(end = 14.dp)
                                            .size(24.dp)
                                            .frostyDimClickable(onClick = action)
                                    )
                                }
                                DragHandle(
                                    modifier = Modifier.pointerInput(item) {
                                        detectDragGestures(
                                            onDragStart = {
                                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                                dragged = item
                                                dragOffset = 0f
                                            },
                                            onDragEnd = {
                                                dragged = null
                                                dragOffset = 0f
                                                onChanged()
                                            },
                                            onDragCancel = {
                                                dragged = null
                                                dragOffset = 0f
                                            }
                                        ) { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val index = items.indexOf(item)
                                            if (dragOffset > rowHeight / 2 && index < items.lastIndex) {
                                                items.add(index + 1, items.removeAt(index))
                                                dragOffset -= rowHeight
                                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            } else if (dragOffset < -rowHeight / 2 && index > 0) {
                                                items.add(index - 1, items.removeAt(index))
                                                dragOffset += rowHeight
                                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The red "minus" or green "plus" circle of an iOS list in edit mode.
 */
@Composable
private fun EditBadge(add: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (add) R.string.action_add else R.string.settings_item_delete)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .frostyDimClickable(onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        Box(
            Modifier
                .size(23.dp)
                .clip(CircleShape)
                .background(if (add) frostyColors.green else frostyColors.red)
        )
        Canvas(Modifier.size(11.dp)) {
            val w = size.width
            val stroke = 2.2.dp.toPx()
            drawLine(Color.White, Offset(0f, w / 2), Offset(w, w / 2), stroke, StrokeCap.Round)
            if (add) drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, w), stroke, StrokeCap.Round)
        }
    }
}

/**
 * Three-line drag handle.
 */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    val color = frostyColors.tertiaryLabel
    val description = stringResource(R.string.settings_items_drag_to_sort)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .semantics { contentDescription = description }
    ) {
        Canvas(Modifier.size(width = 20.dp, height = 12.dp)) {
            val stroke = 1.8.dp.toPx()
            for (k in 0..2) {
                val y = size.height * k / 2f
                drawLine(color, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
            }
        }
    }
}
