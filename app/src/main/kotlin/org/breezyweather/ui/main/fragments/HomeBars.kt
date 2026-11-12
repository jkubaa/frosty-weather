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
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import org.breezyweather.R
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.ui.common.glass.LiquidGlass
import org.breezyweather.ui.common.widgets.SwipeSwitchLayout
import org.breezyweather.ui.main.cards.GlassBackdrop
import org.breezyweather.ui.main.cards.liquidGlass
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Top of the home screen: no buttons, iOS weather style, only the compact title
 * ("Cupertino" / "22°  Sunny") that fades in once the hero has scrolled away ([fraction]).
 */
class HomeTopBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var title by mutableStateOf("")
    var subtitle by mutableStateOf<String?>(null)

    /** 0 hidden, 1 fully shown. */
    var fraction by mutableFloatStateOf(0f)

    @Composable
    override fun Content() {
        val content = Color(context.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .alpha(fraction),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(title, color = content, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, color = content.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Floating liquid glass bottom bar, iOS weather style: edit location (start), location page dots
 * (center), location list (end). The glass blurs what is under it, the sky and the cards
 * scrolling past ([backdrop]). The dots follow location swipes as they happen.
 */
class HomeBottomBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr), SwipeSwitchLayout.OnPagerSwipeListener {

    var backdrop: GlassBackdrop? by mutableStateOf(null)
    var onEdit: (() -> Unit)? = null
    var onManage: (() -> Unit)? = null

    /** The edit button keeps its place when hidden, so the dots stay centered. */
    var editVisible by mutableStateOf(false)

    /** Page of the current (GPS) location: an arrow instead of a dot. */
    var arrowIndex by mutableStateOf<Int?>(null)

    private var pageCount by mutableIntStateOf(0)
    private var current by mutableIntStateOf(0)

    // During a swipe: the neighbour page being moved to, and how far (0..1)
    private var swipeTarget by mutableIntStateOf(-1)
    private var swipeFraction by mutableFloatStateOf(0f)

    /** Bumped to redraw the glass (theme or blur setting changed). */
    private var glassVersion by mutableIntStateOf(0)

    fun setSwitchView(switchView: SwipeSwitchLayout) {
        switchView.setOnPageSwipeListener(this)
        pageCount = switchView.totalCount
        current = switchView.position.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        swipeTarget = -1
        swipeFraction = 0f
    }

    fun refreshGlass() {
        glassVersion++
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        // ViewPager convention: [position] is the left page of the pair, [positionOffset] how far
        // towards the right one. Turned into "moving towards which neighbour, and how far".
        when {
            position == current - 1 && position >= 0 -> {
                swipeTarget = position
                swipeFraction = (1f - positionOffset).coerceIn(0f, 1f)
            }
            position == current && current + 1 < pageCount -> {
                swipeTarget = current + 1
                swipeFraction = positionOffset.coerceIn(0f, 1f)
            }
            else -> {
                swipeTarget = -1
                swipeFraction = 0f
            }
        }
    }

    override fun onPageSelected(position: Int) {
        current = position.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        swipeTarget = -1
        swipeFraction = 0f
    }

    @Composable
    override fun Content() {
        @Suppress("UNUSED_VARIABLE")
        val version = glassVersion
        val content = Color(context.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse))
        val surface = context.getThemeColor(com.google.android.material.R.attr.colorSurface)
        // Frosted, the bar stays readable over the cards with a light tint; without blur it needs a denser body
        val tint = Color(
            ColorUtils.setAlphaComponent(surface, ((if (LiquidGlass.isBlurEnabled(context)) 0.35f else 0.72f) * 255).toInt())
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
        ) {
            if (editVisible) {
                GlassButton(R.drawable.ic_edit, stringResource(R.string.action_edit_location), tint, content, Modifier.align(Alignment.CenterStart)) {
                    onEdit?.invoke()
                }
            }
            if (pageCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp)
                        .fillMaxHeight()
                        .widthIn(min = 96.dp)
                        .liquidGlass(backdrop, tint, cornerRadius = BAR_HEIGHT / 2)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PageDots(content)
                }
            }
            GlassButton(R.drawable.ic_list, stringResource(R.string.action_location_list), tint, content, Modifier.align(Alignment.CenterEnd)) {
                onManage?.invoke()
            }
        }
    }

    @Composable
    private fun GlassButton(
        @DrawableRes icon: Int,
        description: String,
        tint: Color,
        content: Color,
        modifier: Modifier,
        onClick: () -> Unit,
    ) {
        Box(
            modifier = modifier
                .size(BAR_HEIGHT)
                .liquidGlass(backdrop, tint, cornerRadius = BAR_HEIGHT / 2)
                .clip(CircleShape)
                .clickable(onClickLabel = description, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = description, tint = content, modifier = Modifier.size(22.dp))
        }
    }

    /**
     * Plain dots, the current one bright, the highlight sliding between dots during a swipe; the
     * current (GPS) location is an arrow. Past [MAX_VISIBLE] locations it becomes a sliding window
     * that follows the current page, with smaller edge dots when more lie beyond.
     */
    @Composable
    private fun PageDots(content: Color) {
        val slots = min(pageCount, MAX_VISIBLE)
        val selected = content
        val unselected = content.copy(alpha = 0.35f)
        // Plus room at both ends for the location arrow, which is wider than a dot
        val width = DOT * slots + DOT_GAP * (slots - 1).coerceAtLeast(0) + DOT * (ARROW_SCALE - 1)
        Canvas(Modifier.size(width, DOT * ARROW_SCALE)) {
            val diameter = DOT.toPx()
            val step = diameter + DOT_GAP.toPx()
            val cy = size.height / 2f
            val position = if (swipeTarget >= 0) current + (swipeTarget - current) * swipeFraction else current.toFloat()
            // First page shown in the window: keeps the highlight centered, clamped at both ends
            val windowStart = (position - (slots - 1) / 2f).coerceIn(0f, (pageCount - slots).toFloat())
            val rowWidth = slots * diameter + (slots - 1) * DOT_GAP.toPx()
            val firstX = (size.width - rowWidth) / 2f + diameter / 2f

            for (i in (floor(windowStart).toInt() - 1)..ceil(windowStart + slots).toInt()) {
                if (i !in 0 until pageCount) continue
                val slot = i - windowStart
                val scale = edgeScale(slot, slots, moreBefore = i > 0, moreAfter = i < pageCount - 1)
                if (scale <= 0f) continue
                val highlight = when (i) {
                    current -> 1f - if (swipeTarget >= 0) swipeFraction else 0f
                    swipeTarget -> swipeFraction
                    else -> 0f
                }
                val color = lerp(unselected, selected, highlight).let { it.copy(alpha = it.alpha * min(1f, scale / EDGE_SCALE)) }
                val x = firstX + slot * step
                if (i == arrowIndex) {
                    drawArrow(x, cy, diameter * ARROW_SCALE * scale, color)
                } else {
                    drawCircle(color, diameter / 2f * scale, Offset(x, cy))
                }
            }
        }
    }

    /**
     * Size of a dot depending on where it sits in the window: full size inside, [EDGE_SCALE] in an
     * edge slot when more pages lie beyond it, shrinking to nothing as it slides out of the window.
     */
    private fun edgeScale(slot: Float, slots: Int, moreBefore: Boolean, moreAfter: Boolean): Float {
        fun side(distance: Float, more: Boolean): Float = when {
            distance >= 1f -> 1f
            !more -> if (distance >= 0f) 1f else 0f
            distance >= 0f -> EDGE_SCALE + (1f - EDGE_SCALE) * distance
            else -> (EDGE_SCALE * (1f + distance)).coerceAtLeast(0f)
        }
        return min(side(slot, moreBefore), side((slots - 1) - slot, moreAfter))
    }

    /** Navigation arrow (pointing up-right) fitting a [size] square centered on (cx, cy). */
    private fun DrawScope.drawArrow(cx: Float, cy: Float, size: Float, color: Color) {
        val h = size / 2f
        val path = Path().apply {
            moveTo(cx + h, cy - h)
            lineTo(cx - h, cy - h * 0.1f)
            lineTo(cx - h * 0.05f, cy + h * 0.05f)
            lineTo(cx + h * 0.1f, cy + h)
            close()
        }
        drawPath(path, color)
    }

    private companion object {
        val BAR_HEIGHT = 48.dp
        val DOT = 8.dp
        val DOT_GAP = 9.dp

        /** Dots shown at most; past that, the row becomes a sliding window. */
        const val MAX_VISIBLE = 7

        /** Size of an edge dot when more locations lie beyond it. */
        const val EDGE_SCALE = 0.5f

        /** The arrow is a bit bigger than a dot, so it reads as an arrow. */
        const val ARROW_SCALE = 1.6f
    }
}
