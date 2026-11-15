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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.breezyweather.R
import org.breezyweather.ui.details.detailsGlass

private val InlineBarHeight = 56.dp
private val LargeTitleHeight = 52.dp
private val GlassButtonSize = 44.dp

/**
 * A round liquid glass button for navigation bars (iOS 26).
 */
@Composable
fun FrostyGlassButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(GlassButtonSize)
            .detailsGlass(GlassButtonSize / 2, interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        content = content
    )
}

/**
 * The glass back button with a chevron.
 */
@Composable
fun FrostyBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FrostyGlassButton(
        onClick = onClick,
        contentDescription = stringResource(R.string.action_back),
        modifier = modifier
    ) {
        FrostyChevron(
            direction = ChevronDirection.Left,
            color = frostyColors.label,
            size = 20.dp,
            strokeWidth = 2.4.dp,
            modifier = Modifier.padding(end = 2.dp)
        )
    }
}

/**
 * The glass close button with an xmark.
 */
@Composable
fun FrostyCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FrostyGlassButton(
        onClick = onClick,
        contentDescription = stringResource(R.string.action_close),
        modifier = modifier
    ) {
        FrostyXMark(color = frostyColors.label, size = 15.dp)
    }
}

/**
 * iOS navigation bar with a large title that collapses into the inline title as content scrolls
 * (driven by an exit-until-collapsed [TopAppBarScrollBehavior]).
 */
@Composable
fun FrostyLargeTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
) {
    val colors = frostyColors
    val largePx = with(LocalDensity.current) { LargeTitleHeight.toPx() }
    val state = scrollBehavior?.state
    SideEffect {
        if (state != null && state.heightOffsetLimit != -largePx) {
            state.heightOffsetLimit = -largePx
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.groupedBackground)
            .windowInsetsPadding(windowInsets)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(InlineBarHeight)
                .padding(horizontal = 12.dp)
        ) {
            if (onBack != null) {
                FrostyBackButton(onBack, Modifier.align(Alignment.CenterStart))
            }
            Text(
                text = title,
                style = FrostyType.headline,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 60.dp)
                    .graphicsLayer {
                        alpha = if (state == null) {
                            0f
                        } else {
                            ((state.collapsedFraction - 0.55f) / 0.45f).coerceIn(0f, 1f)
                        }
                    }
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    val height = (largePx + (state?.heightOffset ?: 0f)).coerceAtLeast(0f).toInt()
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = 0, maxHeight = largePx.toInt())
                    )
                    layout(placeable.width, height) {
                        placeable.place(0, height - placeable.height)
                    }
                }
        ) {
            Text(
                text = title,
                style = FrostyType.largeTitle,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .height(LargeTitleHeight)
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp)
                    .graphicsLayer {
                        alpha = 1f - ((state?.collapsedFraction ?: 0f) * 1.4f).coerceIn(0f, 1f)
                    }
            )
        }
        // Breathing room between the title and the first group of options
        Spacer(Modifier.height(14.dp))
    }
}
