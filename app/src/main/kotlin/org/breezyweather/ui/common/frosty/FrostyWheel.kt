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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

private val WheelRowHeight = 36.dp
private const val WheelVisibleRows = 5
private const val CyclicRepeats = 400

/**
 * One wheel of an iOS picker. Rows fade and shrink away from the centre; [cyclic] wheels loop.
 */
@Composable
fun FrostyWheel(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 72.dp,
    cyclic: Boolean = false,
    textAlign: TextAlign = TextAlign.Center,
) {
    val colors = frostyColors
    val haptics = LocalHapticFeedback.current
    val count = if (cyclic) items.size * CyclicRepeats else items.size
    val startIndex = if (cyclic) items.size * (CyclicRepeats / 2) + selectedIndex else selectedIndex
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val fling = rememberSnapFlingBehavior(state)
    val rowPx = with(LocalDensity.current) { WheelRowHeight.toPx() }
    val onChange by rememberUpdatedState(onSelectedIndexChange)

    // The centred row is the first visible one (content padding puts it in the middle)
    val centred by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { centred }
            .distinctUntilChanged()
            .collect {
                if (state.isScrollInProgress) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                onChange(it % items.size)
            }
    }

    val pad = WheelRowHeight * (WheelVisibleRows / 2)
    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = pad),
        modifier = modifier
            .width(width)
            .height(WheelRowHeight * WheelVisibleRows)
    ) {
        items(count) { index ->
            val label = items[index % items.size]
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight)
                    .graphicsLayer {
                        val info = state.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                        if (item != null) {
                            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val itemCentre = item.offset + item.size / 2f
                            val d = ((itemCentre - centre) / (rowPx * (WheelVisibleRows / 2 + 0.5f)))
                                .coerceIn(-1f, 1f)
                            val a = abs(d)
                            alpha = 1f - a * 0.75f
                            scaleX = 1f - a * 0.12f
                            scaleY = 1f - a * 0.25f
                            rotationX = d * -50f
                        }
                    }
            ) {
                Text(
                    text = label,
                    fontSize = 22.sp,
                    color = colors.label,
                    textAlign = textAlign,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * iOS time wheels: hours, minutes and, for 12-hour clocks, AM/PM.
 */
@Composable
fun FrostyTimeWheels(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    amPm: Pair<String, String>,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = frostyColors
    val current by rememberUpdatedState(hour to minute)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Selection band
        Box(
            Modifier
                .fillMaxWidth()
                .height(WheelRowHeight)
                .background(colors.tertiaryFill, RoundedCornerShape(10.dp))
        )
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (is24Hour) {
                FrostyWheel(
                    items = remember { (0..23).map { "%02d".format(it) } },
                    selectedIndex = hour,
                    cyclic = true,
                    onSelectedIndexChange = { onTimeChange(it, current.second) }
                )
            } else {
                val h12 = if (hour % 12 == 0) 12 else hour % 12
                FrostyWheel(
                    items = remember { (1..12).map { it.toString() } },
                    selectedIndex = h12 - 1,
                    cyclic = true,
                    onSelectedIndexChange = { idx ->
                        val pm = current.first >= 12
                        val newH12 = idx + 1
                        val h24 = (newH12 % 12) + if (pm) 12 else 0
                        onTimeChange(h24, current.second)
                    }
                )
            }
            Text(":", fontSize = 22.sp, color = colors.label)
            FrostyWheel(
                items = remember { (0..59).map { "%02d".format(it) } },
                selectedIndex = minute,
                cyclic = true,
                onSelectedIndexChange = { onTimeChange(current.first, it) }
            )
            if (!is24Hour) {
                FrostyWheel(
                    items = listOf(amPm.first, amPm.second),
                    selectedIndex = if (hour >= 12) 1 else 0,
                    width = 64.dp,
                    onSelectedIndexChange = { idx ->
                        val h = current.first % 12 + if (idx == 1) 12 else 0
                        onTimeChange(h, current.second)
                    }
                )
            }
        }
    }
}
