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

package org.breezyweather.ui.details

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import org.breezyweather.ui.main.cards.GlassCardRimBrush
import org.breezyweather.ui.main.cards.rememberGlassPainter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import kotlinx.collections.immutable.ImmutableList
import org.breezyweather.R
import org.breezyweather.common.extensions.getFormattedDate
import org.breezyweather.common.extensions.getFormattedDayOfTheMonth
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.common.options.appearance.DetailScreen
import java.util.Date

/**
 * Colors of the Apple Weather-style detail sheet.
 */
@Immutable
data class DetailsPalette(
    val isDark: Boolean,
    val sheet: Color,
    val card: Color,
    val chip: Color,
    val segmentThumb: Color,
    val text: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val grid: Color,
    val accent: Color,
    val onAccent: Color,
) {
    companion object {
        val Dark = DetailsPalette(
            isDark = true,
            sheet = Color(0xFF1C1C1E),
            card = Color(0xFF2C2C2E),
            chip = Color(0xFF3A3A3C),
            segmentThumb = Color(0xFF636366),
            text = Color.White,
            secondaryText = Color(0xFF98989F),
            tertiaryText = Color(0xFF636366),
            grid = Color.White.copy(alpha = 0.13f),
            accent = Color(0xFF64C8F5),
            onAccent = Color(0xFF0B1A22)
        )
        val Light = DetailsPalette(
            isDark = false,
            sheet = Color(0xFFF2F2F7),
            card = Color.White,
            chip = Color(0xFFE3E3E8),
            segmentThumb = Color.White,
            text = Color.Black,
            secondaryText = Color(0xFF6E6E73),
            tertiaryText = Color(0xFFAEAEB2),
            grid = Color.Black.copy(alpha = 0.1f),
            accent = Color(0xFF32ADE6),
            onAccent = Color.White
        )
    }
}

val LocalDetailsPalette = staticCompositionLocalOf { DetailsPalette.Dark }

/**
 * The metric switcher pill shown at the end of each screen's header row. Provided by the sheet.
 */
val LocalDetailsMetricSwitcher = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * Applies the sheet palette to Material components (cards, menus, dividers, typography).
 */
@Composable
fun DetailsSheetTheme(
    palette: DetailsPalette,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme
    val colorScheme = base.copy(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        background = palette.sheet,
        onBackground = palette.text,
        surface = palette.sheet,
        onSurface = palette.text,
        surfaceVariant = palette.card,
        onSurfaceVariant = palette.secondaryText,
        surfaceContainerLowest = palette.card,
        surfaceContainerLow = palette.card,
        surfaceContainer = palette.card,
        surfaceContainerHigh = palette.card,
        surfaceContainerHighest = palette.chip,
        surfaceTint = Color.Transparent,
        outline = palette.grid,
        outlineVariant = palette.grid
    )
    val t = MaterialTheme.typography
    val typography = Typography(
        displayLarge = t.displayLarge,
        displayMedium = t.displayMedium,
        displaySmall = t.displaySmall.copy(fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.Medium),
        headlineLarge = t.headlineLarge,
        headlineMedium = t.headlineMedium,
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium,
        titleSmall = t.titleSmall,
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
        bodyMedium = t.bodyMedium,
        bodySmall = t.bodySmall.copy(fontSize = 15.sp, lineHeight = 20.sp, color = palette.secondaryText),
        labelLarge = t.labelLarge.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
        labelMedium = t.labelMedium.copy(
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = palette.secondaryText
        ),
        labelSmall = t.labelSmall
    )
    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalDetailsPalette provides palette,
            androidx.compose.material3.LocalContentColor provides palette.text,
            content = content
        )
    }
}

@Composable
fun DetailsCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalDetailsPalette.current
    val description = stringResource(R.string.action_close)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(46.dp)
            .detailsGlass(23.dp, interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = null,
            tint = palette.text,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun DetailsTopBar(
    title: String?,
    @DrawableRes icon: Int?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalDetailsPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 12.dp)
    ) {
        DetailsCloseButton(onClose, Modifier.align(Alignment.CenterStart))
        if (title != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp)
            ) {
                if (icon != null) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = palette.text,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = title,
                    color = palette.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Week strip: one-letter weekday over the day of the month, the selected day in a filled circle,
 * then the full date of the selected day.
 */
@Composable
fun DetailsWeekStrip(
    location: Location,
    dates: ImmutableList<Date>,
    selected: Int,
    todayIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalDetailsPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            val cellWidth = maxWidth / 7
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0)
            )
            LaunchedEffect(selected) {
                val visible = listState.layoutInfo.visibleItemsInfo
                val first = visible.firstOrNull()?.index ?: 0
                val last = visible.lastOrNull()?.index ?: 0
                if (selected <= first || selected >= last) {
                    listState.animateScrollToItem((selected - 3).coerceAtLeast(0))
                }
            }
            LazyRow(state = listState) {
                itemsIndexed(dates) { i, date ->
                    val isPast = todayIndex != null && i < todayIndex
                    val isSelected = i == selected
                    val fullDate = remember(date) {
                        date.getFormattedDate("EEEEdMMMM", location, context, withBestPattern = true)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(cellWidth)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab
                            ) { onSelect(i) }
                            .semantics(mergeDescendants = true) {
                                this.selected = isSelected
                                contentDescription = fullDate
                            }
                    ) {
                        Text(
                            text = remember(date) { date.getFormattedDate("EEEEE", location, context) },
                            color = if (isPast) palette.secondaryText else palette.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) palette.accent else Color.Transparent)
                        ) {
                            Text(
                                text = remember(date) { date.getFormattedDayOfTheMonth(location, context) },
                                color = when {
                                    isSelected -> palette.onAccent
                                    isPast -> palette.secondaryText
                                    else -> palette.text
                                },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        dates.getOrNull(selected)?.let { date ->
            Text(
                text = remember(date) {
                    date.getFormattedDate("EEEEdMMMMyyyy", location, context, withBestPattern = true)
                },
                color = palette.text,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp)
            )
        }
        HorizontalDivider(
            color = palette.grid,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/**
 * The pill with the current metric's icon and a chevron, opening a menu of all metrics.
 */
@Composable
fun DetailsMetricPill(
    entries: ImmutableList<DetailScreen>,
    selected: DetailScreen,
    onSelect: (DetailScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalDetailsPalette.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.action_toggle_data_type_menu)
    val pillInteraction = remember { MutableInteractionSource() }
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .detailsGlass(24.dp, pillInteraction)
                .clickable(interactionSource = pillInteraction, indication = null, role = Role.Button) {
                    expanded = true
                }
                .semantics { contentDescription = description }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                painter = painterResource(selected.menuIconId),
                contentDescription = null,
                tint = palette.text,
                modifier = Modifier.size(22.dp)
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = palette.text,
                modifier = Modifier.size(22.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.card.copy(alpha = 0.97f),
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, GlassCardRimBrush)
        ) {
            entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.getName(context), color = palette.text) },
                    leadingIcon = {
                        if (item == selected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = palette.text)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(item.menuIconId),
                            contentDescription = null,
                            tint = palette.text
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    }
                )
            }
        }
    }
}

private val DetailScreen.menuIconId: Int
    get() = if (this == DetailScreen.TAG_FEELS_LIKE) DetailScreen.TAG_CONDITIONS.iconId else iconId

/**
 * Header row of a screen: the screen's own header, and the metric switcher pill at the end.
 */
@Composable
fun DetailsHeaderRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Row(modifier = Modifier.weight(1f), content = content)
        LocalDetailsMetricSwitcher.current?.let {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.padding(top = 4.dp)) { it() }
        }
    }
}

/**
 * iOS-style segmented control.
 */
@Composable
fun DetailsSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalDetailsPalette.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(palette.card)
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "segmentThumb"
        )
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .detailsGlass(17.dp, tint = palette.segmentThumb)
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab
                        ) { onSelect(i) }
                        .semantics { selected = i == selectedIndex }
                ) {
                    Text(
                        text = option,
                        color = palette.text,
                        fontSize = 16.sp,
                        fontWeight = if (i == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * A rounded table of label / value rows, like the Sunset and Moon screens.
 */
@Composable
fun DetailsInfoCard(
    rows: List<Pair<String, AnnotatedString>>,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
) {
    val palette = LocalDetailsPalette.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (outlined) {
                    Modifier.border(BorderStroke(1.dp, palette.grid), shape)
                } else {
                    Modifier.background(palette.card)
                }
            )
    ) {
        rows.forEachIndexed { i, (label, value) ->
            if (i > 0) {
                HorizontalDivider(color = palette.grid)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 20.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                Text(
                    text = label,
                    color = palette.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = value,
                    color = palette.secondaryText,
                    fontSize = 18.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Formats a time with a smaller AM/PM marker, like "4:04PM".
 */
@Composable
fun formatTimeSmallMarker(
    date: Date?,
    location: Location,
): AnnotatedString {
    val context = LocalContext.current
    if (date == null) return AnnotatedString(stringResource(R.string.null_data_text))
    val formatted = date.getFormattedTime(location, context, context.is12Hour)
    return smallSuffix(formatted)
}

/**
 * Shrinks letters (units, AM/PM markers) next to the digits.
 */
fun smallSuffix(raw: String): AnnotatedString = buildAnnotatedString {
    // "8 hr, 11 min" reads as "8HR 11MIN"
    val text = raw.replace(Regex("""(\p{L}),\s"""), "$1 ")
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c.isLetter()) {
            withStyle(SpanStyle(fontSize = 0.78.em)) { append(c.uppercaseChar()) }
        } else if (c.isWhitespace() && i + 1 < text.length && text[i + 1].isLetter() && i > 0 && text[i - 1].isDigit()) {
            // Drop the space between the time and its marker
        } else {
            append(c)
        }
        i++
    }
}

/**
 * Liquid glass for the sheet's buttons: the home screen's glass material (tinted body, rims and
 * sheen of light), which swells slightly and brightens while pressed, like iOS glass controls.
 */
fun Modifier.detailsGlass(
    cornerRadius: Dp,
    interactionSource: MutableInteractionSource? = null,
    tint: Color? = null,
): Modifier = composed {
    val palette = LocalDetailsPalette.current
    val pressed = interactionSource?.collectIsPressedAsState()?.value == true
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "glassPress"
    )
    val body = (tint ?: if (palette.isDark) Color(0xFF48484C) else Color.White)
        .copy(alpha = if (pressed) 0.95f else 0.78f)
    val glass = rememberGlassPainter(backdrop = null, tint = body, cornerRadius = cornerRadius, rim = true)
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = 6.dp.toPx()
            shape = RoundedCornerShape(cornerRadius)
            clip = true
            ambientShadowColor = Color.Black.copy(alpha = 0.3f)
            spotShadowColor = Color.Black.copy(alpha = 0.3f)
        }
        .drawWithContent {
            with(glass) {
                drawGlass(Rect(Offset.Zero, size), Offset.Zero) { this@drawWithContent.drawContent() }
            }
        }
}
