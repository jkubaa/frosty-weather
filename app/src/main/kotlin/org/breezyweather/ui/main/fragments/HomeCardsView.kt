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
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import org.breezyweather.ui.main.cards.GlassCornerRadius
import org.breezyweather.ui.main.cards.GlassEdgeRefraction
import org.breezyweather.ui.main.cards.GlassPainter
import org.breezyweather.ui.main.cards.GlassVividSaturation
import org.breezyweather.ui.main.cards.LocalGlassHidden
import org.breezyweather.ui.main.cards.rememberGlassPainter
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.extensions.density
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.ui.main.cards.ViewBackdrop
import org.breezyweather.ui.main.cards.liquidGlass
import org.breezyweather.common.extensions.getBlocksPerRow
import org.breezyweather.ui.main.adapters.main.MainCards
import org.breezyweather.ui.main.adapters.main.ViewType
import org.breezyweather.ui.main.adapters.main.holder.AbstractMainCardViewHolder
import org.breezyweather.ui.main.adapters.main.holder.AbstractMainViewHolder
import org.breezyweather.ui.main.adapters.main.holder.HorizontalRegionsComposeView
import org.breezyweather.ui.main.adapters.main.holder.LocalCardHeader
import org.breezyweather.ui.main.adapters.main.holder.LocalCardHeaderPin
import org.breezyweather.ui.main.adapters.main.holder.LocalHorizontalRegions
import org.breezyweather.ui.main.adapters.main.holder.LocalSkyBackdrop
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.ui.theme.weatherView.sky.SkyBackdrop
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntOffset
import org.breezyweather.ui.main.adapters.main.holder.CardHeaderPin

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

    /** Shows the refresh indicator. */
    var refreshing by mutableStateOf(false)

    /** Called when the user pulls down from the top to refresh. */
    var onRefresh: (() -> Unit)? = null

    /** Scroll distance from the top, in px (only precise over the first cards, which is what matters). */
    var onScroll: ((Int) -> Unit)? = null

    private var data by mutableStateOf<Data?>(null)

    /** The cards as drawn, for the floating glass bar to blur what scrolls under it. */
    val cardsBackdrop = ViewBackdrop(this)

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        if (!cardsBackdrop.record(canvas) { super.dispatchDraw(it) }) super.dispatchDraw(canvas)
    }
    private var version = 0

    /** Shows the cards of [location]; null shows nothing. Starts again from the top. */
    fun show(location: Location?, provider: ResourceProvider, listAnimationEnabled: Boolean, itemAnimationEnabled: Boolean) {
        data = location?.takeIf { it.weather != null }?.let {
            Data(it, provider, listAnimationEnabled, itemAnimationEnabled, ++version)
        }
        // The glass blurs a recording of these cards, which only the glass re-reads when told: after a
        // location swipe the new cards are drawn first, then the glass is redrawn from them
        post { cardsBackdrop.moved() }
    }

    @Composable
    override fun Content() {
        val cards = cards ?: return
        val data = data
        val pullState = rememberPullToRefreshState()
        CompositionLocalProvider(
            LocalSkyBackdrop provides backdrop,
            LocalHorizontalRegions provides regions
        ) {
            // Pulling only refreshes from the very top of the cards: the list tells pull-to-refresh
            // itself whether it can still scroll up
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { onRefresh?.invoke() },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = { GlassRefreshIndicator(pullState, refreshing, Modifier.align(Alignment.TopCenter)) }
            ) {
                if (data != null) {
                    // The cards come down with the pull, opening room above them for the indicator
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = pullState.distanceFraction.coerceIn(0f, 1.5f) * PULL_ROOM.toPx()
                            }
                    ) {
                        CardGrid(cards, data)
                    }
                } else {
                    // Nothing to show yet ("pull down to refresh"): still something to pull
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }

    /**
     * Refresh indicator in the app's style: a small liquid glass bubble with a light spinner, coming
     * down with the pull (filling up as it goes), spinning while refreshing.
     */
    @Composable
    private fun GlassRefreshIndicator(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier) {
        val content = Color(context.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse))
        Box(
            modifier = modifier
                .graphicsLayer {
                    val f = state.distanceFraction
                    // From hidden above the top, down to its resting place, a little further when pulled hard
                    translationY = f.coerceAtMost(1.5f) * PULL_ROOM.toPx() - 52.dp.toPx()
                    alpha = (f * 2f).coerceIn(0f, 1f)
                    val scale = 0.7f + 0.3f * f.coerceIn(0f, 1f)
                    scaleX = scale
                    scaleY = scale
                }
                .size(44.dp)
                .liquidGlass(LocalSkyBackdrop.current, cornerRadius = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    color = content,
                    trackColor = content.copy(alpha = 0.18f),
                    strokeWidth = 2.5.dp,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                CircularProgressIndicator(
                    progress = { state.distanceFraction.coerceIn(0f, 1f) },
                    color = content,
                    trackColor = content.copy(alpha = 0.18f),
                    strokeWidth = 2.5.dp,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.size(22.dp)
                )
            }
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

        /**
         * How far a card has scrolled up past the top of the cards area, in px (its top edge, negative).
         * Null for cards fully on screen, the hero and the footer.
         */
        fun collapseOf(type: Int): Float? {
            if (type == ViewType.HEADER || type == ViewType.FOOTER) return null
            val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == type } ?: return null
            return info.offset.y.toFloat().takeIf { it < 0f }
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
            // Room for the floating glass bar, above the navigation bar, so the last card (the
            // sources and "last updated" line) can scroll clear of it
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                dimensionResource(R.dimen.glass_capsule_content_clearance)
            LazyVerticalGrid(
                columns = EvenCells,
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

                    val isDragged = dragging == type
                    val draggable = cards.isDraggable(type)
                    // Where the card's header ends, reported by the header itself (none for cards without one)
                    val itemCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
                    val headerCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
                    val reportHeader = remember { { header: LayoutCoordinates -> headerCoords[0] = header } }
                    // The header, kept at the top while the card collapses: laid out there (for taps), and
                    // drawn there by drawCollapsed from what it records
                    val headerLayer = rememberGraphicsLayer()
                    // How far down it is kept: set when the card is drawn (where the exact scroll position is
                    // known), and read by the header's layout, which follows in the next frame if it changed
                    val pinPx = remember(type) { mutableIntStateOf(0) }
                    val headerPin = remember(type, headerLayer) { CardHeaderPin(offset = { pinPx.intValue }, layer = headerLayer) }
                    // Where the header is in the card, without its pinning (it reports where it is laid out)
                    fun headerAt(): Offset? {
                        val item = itemCoords[0]?.takeIf { it.isAttached } ?: return null
                        val header = headerCoords[0]?.takeIf { it.isAttached } ?: return null
                        return item.localPositionOf(header, Offset.Zero) - Offset(0f, headerPin.applied.toFloat())
                    }
                    fun headerBottom(gap: Float): Float? =
                        headerAt()?.let { it.y + (headerCoords[0]?.size?.height ?: 0) + gap }
                    // While the card collapses, the list draws its glass (see drawCollapsed), the card only its content
                    // Same glass as the card itself, so the header doesn't change look while collapsing
                    val cardGlass = holder as? AbstractMainCardViewHolder
                    val glass = rememberGlassPainter(
                        LocalSkyBackdrop.current,
                        cardGlass?.glassTint,
                        refractionStrength = cardGlass?.glassRefraction ?: GlassEdgeRefraction,
                        saturation = cardGlass?.glassSaturation ?: GlassVividSaturation,
                        rim = cardGlass?.glassRim ?: false,
                    )
                    // Observable state: when the card stops collapsing, the glass that reads this redraws
                    // (a plain read at draw time didn't, so the card's background stayed missing)
                    // Keyed on the location's data too: a new location gives the glass a new state, so it redraws
                    val collapsingNow = remember(type, data.version) {
                        androidx.compose.runtime.derivedStateOf {
                            dragging != type && collapseOf(type) != null
                        }
                    }
                    val glassHidden = remember(type, data.version) { { collapsingNow.value } }
                    CompositionLocalProvider(
                        LocalCardHeader provides reportHeader,
                        LocalCardHeaderPin provides headerPin,
                        LocalGlassHidden provides glassHidden
                    ) {
                        holder.Render(
                            Modifier
                                .then(if (isDragged) Modifier else Modifier.animateItem(placementSpec = spring(stiffness = 600f)))
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    translationX = if (isDragged) dragOffset.x else 0f
                                    translationY = if (isDragged) dragOffset.y else 0f
                                    // A collapsing card fades out under the next card as that card covers it.
                                    // Its header is drawn outside its bounds by then: fade each drawing instead
                                    // of through a layer, which would be cut off at the bounds
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                    val top = if (isDragged) null else collapseOf(type)
                                    alpha = top?.let {
                                        val pad = CARD_SPACING.toPx()
                                        // Bottom of the header text (or a stand-in for cards without a header)
                                        val text = headerBottom(0f) ?: (pad + MIN_STRIP.toPx())
                                        // Already fading as the last of the card scrolls away under the header
                                        // ([FADE_LEAD] before the next card reaches the header text), evenly,
                                        // gone once the next card has covered [FADE_COVER] of the header
                                        val header = text - pad
                                        val nextTop = it + size.height + pad
                                        val start = header + FADE_LEAD.toPx()
                                        val end = header * (1f - FADE_COVER)
                                        ((nextTop - pad - end) / (start - end)).coerceIn(0f, 1f)
                                    } ?: 1f
                                }
                                .drawWithContent {
                                    val top = if (isDragged) null else collapseOf(type)
                                    val item = itemCoords[0]?.takeIf { it.isAttached }
                                    val headerAt = headerAt()
                                    val pinNow = if (top != null && headerAt != null) (-top).roundToInt() else 0
                                    if (pinPx.intValue != pinNow) pinPx.intValue = pinNow
                                    if (top == null) {
                                        drawContent()
                                    } else {
                                        drawCollapsed(
                                            top = top,
                                            headerBottom = headerBottom(HEADER_GAP.toPx()),
                                            header = headerAt?.let { headerPin.layer to it },
                                            glass = glass,
                                            itemWindow = item?.positionInWindow() ?: Offset.Zero
                                        )
                                    }
                                }
                                .onGloballyPositioned { itemCoords[0] = it }
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
                                .padding(if (type == ViewType.HEADER || type == ViewType.FOOTER) 0.dp else CARD_SPACING)
                        )
                    }
                }
            }
        }
    }

    /**
     * Draws a card that has scrolled [top] px (negative) past the top of the cards area, iOS weather
     * style: the card's rounded top and its header stay at the top, the rest of the card scrolls up
     * underneath the header and the bottom edge rises, until only the header strip is left; it stays,
     * fading, while the next card scrolls up over it. [headerBottom]: where the header strip ends, in px
     * from the top of the item (spacing included); null for cards without a header, which simply shrink
     * as a rounded box. [header]: the header's recording and its place in the card (unpinned), drawn
     * at the top.
     */
    private fun ContentDrawScope.drawCollapsed(
        top: Float,
        headerBottom: Float?,
        header: Pair<GraphicsLayer, Offset>?,
        glass: GlassPainter,
        itemWindow: Offset,
    ) {
        val pad = CARD_SPACING.toPx()
        val right = size.width - pad
        val glassBottom = size.height - pad
        val radius = GlassCornerRadius.toPx()
        // The card's bottom edge, in the cards area's coordinates: it keeps rising as the card scrolls
        val bottomEdge = top + glassBottom
        // The header stays at the top; no header, nothing stays
        val stripBottom = headerBottom?.coerceIn(pad, glassBottom) ?: pad
        val hasHeader = stripBottom > pad
        // What is left of the card, a rounded box from the top of the area down to its bottom edge. With
        // a header, it never gets shorter than the header plus the gap to the next card: that card meets
        // it and slides up over it, with no gap in between
        val boxBottom = if (hasHeader) maxOf(bottomEdge, stripBottom + 2 * pad) else bottomEdge
        if (boxBottom <= pad) return
        val r = radius.coerceAtMost((boxBottom - pad) / 2f)
        val box = Path().apply { addRoundRect(RoundRect(pad, pad, right, boxBottom, CornerRadius(r))) }

        // Drawing in the area's coordinates, with the card's top kept at the top of the area
        translate(top = -top) {
            clipPath(box) {
                // The glass, one piece where the box really is, with the sky really behind it (the card
                // itself draws its content only meanwhile, see LocalGlassHidden)
                with(glass) {
                    drawGlass(Rect(pad, pad, right, boxBottom), itemWindow + Offset(pad, pad - top), r)
                }
                // The rest where it is, scrolling up under the header (always drawn, even when there is
                // nothing left of it to show: that is also when the header records itself)
                translate(top = top) {
                    clipRect(0f, stripBottom - top, size.width, maxOf(stripBottom - top, bottomEdge - top + pad)) {
                        this@drawCollapsed.drawContent()
                    }
                }
            }
        }
        // The header, at the top (it is laid out there too, so taps reach it), from its recording. Placed
        // by the layer's own position: drawn under a canvas translation, it didn't show
        if (hasHeader && header != null) {
            val (layer, at) = header
            layer.topLeft = IntOffset(at.x.roundToInt(), (at.y - top).roundToInt())
            drawLayer(layer)
        }
    }

    /**
     * [SPANS] columns like GridCells.Fixed, but with the pixels left over from dividing the width
     * spread along the row, instead of all given to the first columns (which made the left tile of a
     * pair wider than the right one).
     */
    private object EvenCells : GridCells {
        override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
            val free = availableSize - spacing * (SPANS - 1)
            return List(SPANS) { i -> free * (i + 1) / SPANS - free * i / SPANS }
        }
    }

    private companion object {
        /** How far the cards come down when pulled to refresh: room for the indicator. */
        val PULL_ROOM = 64.dp

        /** 60 divides by 1, 2, 3, 4 and 5: any number of tiles per row gets a whole span. */
        const val SPANS = 60

        /** Space around each card (between cards, and above a collapsing card). */
        val CARD_SPACING = 8.dp

        /** Room under a card's header that stays with it when the card collapses. */
        val HEADER_GAP = 8.dp

        /** Fade length of a collapsing card without a header. */
        val MIN_STRIP = 40.dp

        /** How far before the next card reaches a collapsing header it starts fading. */
        val FADE_LEAD = 40.dp

        /** How much of a collapsed header the next card has covered when the header is gone. */
        const val FADE_COVER = 0.6f
    }
}
