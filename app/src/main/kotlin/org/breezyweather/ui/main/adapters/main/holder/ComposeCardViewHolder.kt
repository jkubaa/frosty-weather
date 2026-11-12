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

package org.breezyweather.ui.main.adapters.main.holder

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.ui.main.utils.ForecastMetric
import kotlin.math.abs

/**
 * Full-width liquid glass card drawn in Compose. Like every glass card, the card is the item's
 * root, so the glass blur layer stays clipped to it. Subclasses provide [Content] and feed it
 * state on bind.
 */
abstract class ComposeCardViewHolder private constructor(
    card: MaterialCardView,
) : AbstractMainCardViewHolder(card) {

    constructor(parent: ViewGroup) : this(
        MaterialCardView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            strokeWidth = 0
        }
    )

    /** Text and drawing color on glass. */
    protected val contentColor: Color =
        Color(card.context.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse))

    private val composeView = HorizontalRegionsComposeView(card.context)

    init {
        // Qualified: inside the view, a bare Content() would be the view's own and recurse forever
        composeView.setContent { this@ComposeCardViewHolder.Content() }
        card.addView(composeView)
    }

    @Composable
    protected abstract fun Content()

    /**
     * Marks an area that scrolls sideways (hour strip, metric switcher): drags starting there scroll
     * it instead of switching location, see [HorizontalRegionsComposeView].
     */
    protected fun Modifier.horizontalScrollRegion(key: String): Modifier = onGloballyPositioned {
        composeView.regions[key] = it.boundsInRoot()
    }
}

/**
 * ComposeView for cards in the location pager. [SwipeSwitchLayout] switches locations on
 * horizontal drags and doesn't see Compose scrolling, so drags starting in one of the [regions]
 * claim the gesture right away, and give it back if they turn out vertical (the page still scrolls
 * up and down from there). Elsewhere in the card, horizontal swipes still switch locations.
 */
internal class HorizontalRegionsComposeView(context: Context) : AbstractComposeView(context) {

    val regions = mutableMapOf<String, Rect>()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var claimed = false
    private var decided = false
    private var body: (@Composable () -> Unit)? by mutableStateOf(null)

    fun setContent(content: @Composable () -> Unit) {
        body = content
    }

    @Composable
    override fun Content() {
        body?.invoke()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                decided = false
                claimed = regions.values.any { it.contains(Offset(ev.x, ev.y)) }
                if (claimed) parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (claimed && !decided) {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    decided = true
                    if (dy > dx) parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                claimed = false
                decided = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

/** Light blue of chances of precipitation, as in the rest of the glass cards. */
internal val GlassPrecipitation = Color(0xFF6FD3FF)

/** Card header: small icon and uppercase title, with room for a control at the end. */
@Composable
internal fun GlassCardHeader(
    @DrawableRes icon: Int,
    title: String,
    content: Color,
    modifier: Modifier = Modifier,
    end: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The title keeps its room; the control gets what's left (the switcher scrolls in it)
        Row(
            modifier = Modifier.alpha(0.6f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Text(
                text = title.uppercase(),
                color = content,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .semantics { heading() }
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            end()
        }
    }
}

/** Hairline between rows inside a glass card. */
@Composable
internal fun GlassDivider(content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(content.copy(alpha = 0.2f))
    )
}

private val SwitcherItemWidth = 38.dp
private val SwitcherItemHeight = 28.dp

/**
 * iOS-weather-like segmented control made of icons (conditions, precipitation, wind...), in a
 * translucent tray; a lighter pill slides to the selected one. Scrolls when there are many.
 * Nothing is shown with fewer than two metrics.
 */
@Composable
internal fun MetricSwitcher(
    metrics: List<ForecastMetric>,
    selected: ForecastMetric,
    content: Color,
    nameOf: (ForecastMetric) -> String,
    modifier: Modifier = Modifier,
    onSelect: (ForecastMetric) -> Unit,
) {
    if (metrics.size < 2) return
    val index = metrics.indexOf(selected).coerceAtLeast(0)
    val pillOffset by animateDpAsState(
        SwitcherItemWidth * index,
        spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "pill"
    )
    Box(
        modifier
            .horizontalScroll(rememberScrollState())
            .background(content.copy(alpha = 0.086f), RoundedCornerShape(50))
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .offset(x = pillOffset)
                .size(SwitcherItemWidth, SwitcherItemHeight)
                .background(content.copy(alpha = 0.22f), RoundedCornerShape(50))
        )
        Row {
            metrics.forEach { metric ->
                val isSelected = metric == selected
                Box(
                    modifier = Modifier
                        .size(SwitcherItemWidth, SwitcherItemHeight)
                        .semantics {
                            contentDescription = nameOf(metric)
                            this.selected = isSelected
                        }
                        .clickable(role = Role.Tab) { if (!isSelected) onSelect(metric) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(metric.icon),
                        contentDescription = null,
                        tint = content.copy(alpha = if (isSelected) 1f else 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** Weather icons come as Drawables from the icon provider: rasterized once at a fixed size. */
@Composable
internal fun rememberDrawablePainter(drawable: Drawable?, key: Any?): Painter? = remember(key) {
    drawable?.let { BitmapPainter(it.toBitmap(144, 144).asImageBitmap()) }
}
