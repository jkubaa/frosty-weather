/*
 * This file is part of Frosty Weather, a modified version of Breezy Weather.
 *
 * Frosty Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Frosty Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Frosty Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.ui.main.fragments

import android.content.Context
import android.util.AttributeSet
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import breezyweather.domain.location.model.Location
import kotlinx.coroutines.delay
import org.breezyweather.common.extensions.density
import org.breezyweather.common.extensions.getBlocksPerRow
import org.breezyweather.ui.main.adapters.main.MainCards
import org.breezyweather.ui.main.adapters.main.ViewType
import org.breezyweather.ui.main.adapters.main.holder.AbstractMainCardViewHolder
import org.breezyweather.ui.main.adapters.main.holder.AbstractMainViewHolder
import org.breezyweather.ui.main.adapters.main.holder.HorizontalRegionsComposeView
import org.breezyweather.ui.main.adapters.main.holder.LocalHorizontalRegions
import org.breezyweather.ui.main.adapters.main.holder.LocalSkyBackdrop
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.ui.theme.weatherView.sky.SkyBackdrop

/**
 * The home screen's cards, in Compose: a grid where tiles go two per row (more on wide screens)
 * and everything else spans the width, each card on liquid glass blurring the [backdrop] sky.
 *
 * Cards fade in when first shown (if enabled), can be reordered by touching and holding then
 * dragging (saved like the card order in the settings), and the scroll position is reported for the
 * toolbar title and the sky ([onScroll]).
 */
class HomeCardsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : HorizontalRegionsComposeView(context, attrs, defStyleAttr) {

    @Immutable
    private class Data(
        val location: Location,
        val provider: ResourceProvider,
        val listAnimationEnabled: Boolean,
        val itemAnimationEnabled: Boolean,
        val version: Int,
    )

    var cards: MainCards? by mutableStateOf(null)
    var backdrop: SkyBackdrop? by mutableStateOf(null)

    /** Room left at the bottom for the floating glass bar, in px. */
    var bottomPadding by mutableIntStateOf(0)

    /** Scroll distance from the top, in px (only precise over the first cards, which is what matters). */
    var onScroll: ((Int) -> Unit)? = null

    private var data by mutableStateOf<Data?>(null)
    private var version = 0

    /** Shows the cards of [location]; null shows nothing. Starts again from the top. */
    fun show(location: Location?, provider: ResourceProvider, listAnimationEnabled: Boolean, itemAnimationEnabled: Boolean) {
        data = location?.takeIf { it.weather != null }?.let {
            Data(it, provider, listAnimationEnabled, itemAnimationEnabled, ++version)
        }
    }

    @Composable
    override fun Content() {
        val cards = cards ?: return
        val data = data ?: return
        CompositionLocalProvider(
            LocalSkyBackdrop provides backdrop,
            LocalHorizontalRegions provides regions
        ) {
            CardGrid(cards, data)
        }
    }

    @Composable
    private fun CardGrid(cards: MainCards, data: Data) {
        val view = LocalView.current
        val haptics = LocalHapticFeedback.current
        val types = remember(data.version) { cards.types(data.location).toMutableStateList() }
        val gridState = remember(data.version) { LazyGridState() }
        // Cards keep their state while scrolled away (no re-bind, no animations played again)
        val holders = remember(data.version) { mutableMapOf<Int, Pair<Float, AbstractMainViewHolder>>() }
        val appeared = remember(data.version) { mutableSetOf<Int>() }
        var dragging by remember(data.version) { mutableStateOf<Int?>(null) }
        var dragOffset by remember(data.version) { mutableStateOf(Offset.Zero) }

        // Scroll position, from the heights of the first cards
        LaunchedEffect(gridState) {
            val heights = mutableMapOf<Int, Int>()
            snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                .collect { (first, offset) ->
                    gridState.layoutInfo.visibleItemsInfo.forEach { heights[it.index] = it.size.height }
                    onScroll?.invoke((0 until first).sumOf { heights[it] ?: 0 } + offset)
                }
        }

        fun moveDragged(type: Int) {
            val items = gridState.layoutInfo.visibleItemsInfo
            val me = items.firstOrNull { it.key == type } ?: return
            val center = me.offset.toOffset() + dragOffset +
                Offset(me.size.width / 2f, me.size.height / 2f)
            val target = items.firstOrNull {
                it.key != type &&
                    cards.isDraggable(it.key as Int) &&
                    Rect(it.offset.toOffset(), it.size.toSize()).contains(center)
            } ?: return
            if (cards.swap(types, types.indexOf(type), types.indexOf(target.key as Int))) {
                // The card takes the target's place: keep it under the finger
                dragOffset += (me.offset - target.offset).toOffset()
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Same measure as the rest of the app: width in px over the app's density extension
            val blocksPerRow = view.context.getBlocksPerRow(constraints.maxWidth.toFloat() / view.context.density)
            val bottom = with(androidx.compose.ui.platform.LocalDensity.current) { bottomPadding.toDp() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(SPANS),
                state = gridState,
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = bottom),
                modifier = Modifier
                    .fillMaxSize()
                    // Lets pull-to-refresh and the location pager see the scrolling
                    .nestedScroll(rememberNestedScrollInteropConnection())
            ) {
                itemsIndexed(
                    items = types,
                    key = { _, type -> type },
                    span = { index, _ -> GridItemSpan(SPANS / MainCards.tilesInRow(types, index, blocksPerRow).coerceAtLeast(1)) }
                ) { index, type ->
                    val isTile = ViewType.isHalfSizeableBlock(type) == true
                    val widthFactor = if (isTile) blocksPerRow.toFloat() / MainCards.tilesInRow(types, index, blocksPerRow) else 1f
                    val holder = holders[type]?.takeIf { it.first == widthFactor }?.second
                        ?: cards.create(type).also {
                            (it as? AbstractMainCardViewHolder)?.widthFactor = widthFactor
                            cards.bind(it, data.location, data.provider, data.listAnimationEnabled, data.itemAnimationEnabled)
                            holders[type] = widthFactor to it
                        }

                    // Fade and rise in, the first time each card shows up
                    val appear = remember { Animatable(if (!data.listAnimationEnabled || type in appeared) 1f else 0f) }
                    LaunchedEffect(Unit) {
                        if (appear.value < 1f) {
                            val visibleRank = (index - gridState.firstVisibleItemIndex).coerceIn(0, 6)
                            delay(visibleRank * 45L)
                            appear.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
                        }
                        appeared += type
                    }

                    val isDragged = dragging == type
                    val draggable = cards.isDraggable(type)
                    holder.Render(
                        Modifier
                            .then(if (isDragged) Modifier else Modifier.animateItem(placementSpec = spring(stiffness = 600f)))
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                alpha = appear.value
                                translationY = (1f - appear.value) * 24.dp.toPx() + if (isDragged) dragOffset.y else 0f
                                translationX = if (isDragged) dragOffset.x else 0f
                            }
                            .scale(if (isDragged) 1.03f else 1f)
                            .then(
                                if (draggable) {
                                    Modifier.pointerInput(type, data.version) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragging = type
                                                dragOffset = Offset.Zero
                                                // Keep the page and the location pager still while dragging
                                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragEnd = {
                                                dragging = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                dragging = null
                                                dragOffset = Offset.Zero
                                            }
                                        ) { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                            moveDragged(type)
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            // Space between cards; the hero and the footer bring their own
                            .padding(if (type == ViewType.HEADER || type == ViewType.FOOTER) 0.dp else 8.dp)
                    )
                }
            }
        }
    }

    private companion object {
        /** 60 divides by 1, 2, 3, 4 and 5: any number of tiles per row gets a whole span. */
        const val SPANS = 60
    }
}
