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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/* ---------------------------------------------------------------------------------------------- */
/* Alert                                                                                          */
/* ---------------------------------------------------------------------------------------------- */

@Composable
private fun alertBackground(): Color = if (frostyColors.isDark) Color(0xFF242426) else Color.White

/**
 * iOS 26 alert: a rounded card with a left-aligned bold title and message, and capsule buttons
 * (side by side when there are two, stacked otherwise).
 *
 * @param buttons each entry is one button; use [org.breezyweather.ui.common.composables.TextButton]
 * or [FrostyButton] inside.
 */
@Composable
fun FrostyAlert(
    onDismissRequest: () -> Unit,
    buttons: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    stackButtons: Boolean = buttons.size > 2,
    properties: DialogProperties = DialogProperties(),
) {
    val colors = frostyColors
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = properties.decorFitsSystemWindows
        )
    ) {
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
        ProvideFrostyTypography {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 24.dp)
            ) {
                Column(
                    modifier = modifier
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val s = 1.12f - 0.12f * appear.value
                            scaleX = s
                            scaleY = s
                            alpha = appear.value.coerceIn(0f, 1f)
                            shadowElevation = 24.dp.toPx()
                            shape = RoundedCornerShape(34.dp)
                            clip = true
                        }
                        .background(alertBackground())
                        .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp)
                ) {
                    if (icon != null) {
                        CompositionLocalProvider(LocalContentColor provides colors.label) {
                            Box(Modifier.padding(bottom = 12.dp)) { icon() }
                        }
                    }
                    if (title != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides colors.label,
                            LocalTextStyle provides FrostyType.headline
                        ) {
                            Box(Modifier.padding(bottom = 6.dp)) { title() }
                        }
                    }
                    if (text != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides colors.label,
                            LocalTextStyle provides FrostyType.subheadline
                        ) {
                            Box(
                                Modifier
                                    .weight(1f, fill = false)
                                    .padding(bottom = 4.dp)
                            ) { text() }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FrostyDialogButtons(buttons, stackButtons)
                }
            }
        }
    }
}

/**
 * Lays out dialog buttons as capsules.
 */
@Composable
fun FrostyDialogButtons(
    buttons: List<@Composable () -> Unit>,
    stack: Boolean = buttons.size > 2,
    modifier: Modifier = Modifier,
) {
    if (buttons.isEmpty()) return
    CompositionLocalProvider(LocalFrostyDialogButtons provides true) {
        if (stack) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = modifier.fillMaxWidth()
            ) {
                buttons.forEach { button -> Box(Modifier.fillMaxWidth()) { button() } }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                buttons.forEach { button -> Box(Modifier.weight(1f)) { button() } }
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------- */
/* Sheet                                                                                          */
/* ---------------------------------------------------------------------------------------------- */

/**
 * iOS sheet: attached to the bottom edge with rounded top corners (like the detail sheets); it slides up, with a grabber, a glass
 * close button, a centered title, scrollable content and optional capsule buttons at the bottom.
 * Drag the header down to dismiss.
 */
@Composable
fun FrostySheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    buttons: List<@Composable () -> Unit> = emptyList(),
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = frostyColors
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val offset = remember { Animatable(1f) } // fraction of the sheet height pushed down
        var sheetHeight by remember { mutableFloatStateOf(0f) }
        var dragPx by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) { offset.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 420f)) }

        fun dismissAnimated() {
            scope.launch {
                offset.animateTo(1f, tween(220))
                onDismissRequest()
            }
        }

        ProvideFrostyTypography {
            BoxWithConstraints(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.ime))
                    .padding(top = 24.dp)
            ) {
                val maxSheet = maxHeight
                Column(
                    modifier = modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxSheet)
                        .onSizeChanged { sheetHeight = it.height.toFloat() }
                        .graphicsLayer {
                            translationY = offset.value * (size.height + 40.dp.toPx()) + dragPx
                            shadowElevation = 30.dp.toPx()
                            // Attached to the bottom edge like the detail sheets
                            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
                            clip = true
                        }
                        .background(colors.elevatedBackground)
                ) {
                    // Header: grabber, close button, title
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    dragPx = (dragPx + delta).coerceAtLeast(0f)
                                },
                                onDragStopped = { velocity ->
                                    val threshold = with(density) { 110.dp.toPx() }
                                    if (dragPx > threshold || velocity > 1800f) {
                                        val start = dragPx
                                        dragPx = 0f
                                        scope.launch {
                                            offset.snapTo(if (sheetHeight > 0f) start / sheetHeight else 0f)
                                            dismissAnimated()
                                        }
                                    } else {
                                        val start = dragPx
                                        scope.launch {
                                            Animatable(start).animateTo(0f, spring(0.8f, 500f)) { dragPx = value }
                                        }
                                    }
                                }
                            )
                    ) {
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .align(Alignment.CenterHorizontally)
                                .size(36.dp, 5.dp)
                                .clip(CircleShape)
                                .background(colors.tertiaryLabel)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .padding(horizontal = 12.dp)
                        ) {
                            FrostyCloseButton(onClick = {
                                dismissAnimated()
                            }, modifier = Modifier.align(Alignment.CenterStart))
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 58.dp)
                            ) {
                                if (titleContent != null) {
                                    CompositionLocalProvider(
                                        LocalTextStyle provides FrostyType.headline,
                                        LocalContentColor provides colors.label
                                    ) { titleContent() }
                                } else if (title != null) {
                                    Text(
                                        text = title,
                                        style = FrostyType.headline,
                                        color = colors.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (trailingAction != null) {
                                Box(Modifier.align(Alignment.CenterEnd)) { trailingAction() }
                            }
                        }
                        // Breathing room between the header and the first options
                        Spacer(Modifier.height(14.dp))
                    }
                    CompositionLocalProvider(LocalFrostyElevated provides true) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                                .padding(horizontal = 16.dp)
                        ) {
                            content()
                            Spacer(Modifier.height(if (buttons.isEmpty()) 20.dp else 12.dp))
                        }
                    }
                    if (buttons.isNotEmpty()) {
                        FrostyDialogButtons(
                            buttons = buttons,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
                        )
                    }
                    // The sheet runs behind the navigation bar
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------- */
/* Picker sheet                                                                                   */
/* ---------------------------------------------------------------------------------------------- */

/**
 * An option of a [FrostyPickerSheet]. A [header] entry starts a new group titled [name].
 */
data class FrostyPickerItem(
    val key: String,
    val name: String,
    val enabled: Boolean = true,
    val header: Boolean = false,
    val trailing: (@Composable () -> Unit)? = null,
)

/**
 * A sheet listing options in inset groups with a checkmark on the selected one, like an iOS
 * settings selection screen. Picking an option closes the sheet.
 */
@Composable
fun FrostyPickerSheet(
    title: String,
    items: List<FrostyPickerItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
    buttons: List<@Composable () -> Unit> = emptyList(),
) {
    val colors = frostyColors
    // Split into groups
    val groups = remember(items) {
        val result = mutableListOf<Pair<String?, MutableList<FrostyPickerItem>>>()
        items.forEach { item ->
            if (item.header) {
                result.add(item.name to mutableListOf())
            } else {
                if (result.isEmpty()) result.add(null to mutableListOf())
                result.last().second.add(item)
            }
        }
        result.filter { it.second.isNotEmpty() }
    }
    val flat = remember(groups) {
        buildList {
            groups.forEachIndexed { g, (header, list) ->
                if (header != null) add(PickerEntry.Header(header, g == 0))
                list.forEachIndexed { i, item ->
                    add(PickerEntry.Option(item, i == 0, i == list.lastIndex))
                }
                add(PickerEntry.Gap)
            }
        }
    }
    val initialIndex = remember(flat) {
        flat.indexOfFirst { it is PickerEntry.Option && it.item.key == selectedKey }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialIndex - 3).coerceAtLeast(0)
    )

    FrostySheet(
        onDismissRequest = onDismissRequest,
        title = title,
        buttons = buttons,
        scrollable = false
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(flat) { _, entry ->
                when (entry) {
                    is PickerEntry.Header -> FrostySectionHeader(
                        entry.title,
                        Modifier.padding(top = if (entry.first) 0.dp else 8.dp)
                    )
                    is PickerEntry.Gap -> Spacer(Modifier.height(16.dp))
                    is PickerEntry.Option -> FrostyCell(isFirst = entry.first, isLast = entry.last) {
                        val selected = entry.item.key == selectedKey
                        FrostyRow(
                            title = entry.item.name,
                            enabled = entry.item.enabled,
                            titleColor = colors.label,
                            onClick = if (entry.item.enabled) {
                                { onSelect(entry.item.key) }
                            } else {
                                null
                            },
                            trailing = if (selected || entry.item.trailing != null) {
                                {
                                    entry.item.trailing?.invoke()
                                    if (selected) FrostyCheckmark()
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

private sealed interface PickerEntry {
    data class Header(val title: String, val first: Boolean) : PickerEntry
    data class Option(val item: FrostyPickerItem, val first: Boolean, val last: Boolean) : PickerEntry
    data object Gap : PickerEntry
}

/* ---------------------------------------------------------------------------------------------- */
/* Text field                                                                                     */
/* ---------------------------------------------------------------------------------------------- */

/**
 * iOS rounded text field with a clear button.
 */
@Composable
fun FrostyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    autoFocus: Boolean = false,
) {
    val colors = frostyColors
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        textStyle = FrostyType.body.copy(color = colors.label),
        cursorBrush = SolidColor(colors.tint),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.tertiaryFill)
                    .then(
                        if (isError) {
                            Modifier.background(colors.red.copy(alpha = 0.12f))
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = FrostyType.body, color = colors.tertiaryLabel, maxLines = 1)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colors.tertiaryLabel)
                            .frostyDimClickable { onValueChange("") }
                    ) {
                        FrostyXMark(color = alertBackground(), size = 9.dp)
                    }
                }
            }
        }
    )
}

/* ---------------------------------------------------------------------------------------------- */
/* Misc                                                                                           */
/* ---------------------------------------------------------------------------------------------- */

/**
 * A secondary text block with the iOS footnote look, for helper and error text in dialogs.
 */
@Composable
fun FrostyFootnote(text: String, modifier: Modifier = Modifier, color: Color = frostyColors.secondaryLabel) {
    Text(text = text, style = FrostyType.footnote, color = color, modifier = modifier)
}

/**
 * Applies the dialog content style (body text in the label colour).
 */
@Composable
fun ProvideFrostyDialogText(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides frostyColors.label,
        LocalTextStyle provides MaterialTheme.typography.bodyMedium,
        content = content
    )
}
