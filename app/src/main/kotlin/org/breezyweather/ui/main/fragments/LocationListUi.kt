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

package org.breezyweather.ui.main.fragments

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.viewinterop.AndroidView
import org.breezyweather.ui.common.glass.GlassContentLayout
import android.graphics.Canvas
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withTranslation
import androidx.recyclerview.widget.RecyclerView
import org.breezyweather.R
import org.breezyweather.ui.common.glass.LiquidGlass
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.spToPx
import org.breezyweather.ui.main.adapters.location.LocationHolder

/**
 * Colors of the location list, iOS weather style: white on black, or black on light gray when the
 * user picked "Always light". The glass surfaces are a faint veil of the text color over the backdrop.
 */
internal class LocationListColors(isLight: Boolean) {
    val background = if (isLight) Color(0xFFF2F2F7) else Color.Black
    val content = if (isLight) Color.Black else Color.White
    val secondary = content.copy(alpha = 0.6f)
    val glass = content.copy(alpha = if (isLight) 0.07f else 0.14f)
}

/**
 * Tint of the liquid glass elements of the location list and search (see [LiquidGlass.applyFloating]).
 * Frosted, a faint veil over the blurred content (matching the other glass of the screens while nothing
 * is under it); without blur, an opaque equivalent so the content passing under doesn't show through.
 */
internal fun locationGlassTint(context: Context, isLight: Boolean): Int {
    val veil = ColorUtils.setAlphaComponent(
        if (isLight) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
        ((if (isLight) 0.07f else 0.14f) * 255).toInt()
    )
    return if (LiquidGlass.isBlurEnabled(context)) {
        veil
    } else {
        ColorUtils.compositeColors(
            veil,
            if (isLight) android.graphics.Color.rgb(0xF2, 0xF2, 0xF7) else android.graphics.Color.BLACK
        )
    }
}

/**
 * Real liquid glass host (blur, refraction, sheen: see [LiquidGlass.applyFloating]) around [content],
 * blurring what lies under it in [blurRoot]. The content is kept out of its own glass
 * (see [GlassContentLayout]).
 */
internal fun liquidGlassHost(
    context: Context,
    isLight: Boolean,
    cornerRadius: Float,
    blurRoot: ViewGroup,
    contentDescription: String?,
    onClick: () -> Unit,
    content: View,
): FrameLayout = FrameLayout(context).apply {
    this.contentDescription = contentDescription
    isClickable = true
    isFocusable = true
    foreground = AppCompatResources.getDrawable(context, R.drawable.selectable_item_background)
    setOnClickListener { onClick() }
    addView(
        GlassContentLayout(context).apply {
            addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        },
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    )
    LiquidGlass.applyFloating(
        host = this,
        cornerRadius = cornerRadius,
        tint = locationGlassTint(context, isLight),
        blurRoot = blurRoot
    )
}

private fun glassIcon(context: Context, @DrawableRes icon: Int, isLight: Boolean, sizeDp: Float) =
    ImageView(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(
            if (isLight) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        )
        val size = context.dpToPx(sizeDp).toInt()
        layoutParams = LinearLayout.LayoutParams(size, size)
    }

/** Round liquid glass button, like the ones of the home screen's bottom bar. */
@Composable
internal fun LiquidGlassIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    isLight: Boolean,
    blurRoot: ViewGroup,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    AndroidView(
        modifier = Modifier.size(GLASS_BUTTON_SIZE),
        factory = { context ->
            liquidGlassHost(
                context = context,
                isLight = isLight,
                cornerRadius = context.dpToPx(GLASS_BUTTON_SIZE.value / 2f),
                blurRoot = blurRoot,
                contentDescription = contentDescription,
                onClick = { currentOnClick() },
                content = glassIcon(context, icon, isLight, 22f).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        context.dpToPx(22f).toInt(),
                        context.dpToPx(22f).toInt(),
                        Gravity.CENTER
                    )
                }
            )
        }
    )
}

/** Liquid glass capsule with an icon and a label (current location, debug location). */
@Composable
internal fun LiquidGlassChip(
    @DrawableRes icon: Int,
    text: String,
    isLight: Boolean,
    blurRoot: ViewGroup,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    AndroidView(
        modifier = Modifier.height(36.dp),
        factory = { context ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPaddingRelative(context.dpToPx(12f).toInt(), 0, context.dpToPx(14f).toInt(), 0)
                addView(glassIcon(context, icon, isLight, 18f))
                addView(
                    TextView(context).apply {
                        this.text = text
                        textSize = 15f
                        maxLines = 1
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(if (isLight) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = context.dpToPx(6f).toInt() }
                    }
                )
            }
            liquidGlassHost(
                context = context,
                isLight = isLight,
                cornerRadius = context.dpToPx(18f),
                blurRoot = blurRoot,
                contentDescription = text,
                onClick = { currentOnClick() },
                content = row
            )
        }
    )
}

internal val GLASS_BUTTON_SIZE = 44.dp

/** Glass card for the notification / update check prompts, with an action and a close button. */
@Composable
internal fun GlassNotificationCard(
    colors: LocationListColors,
    title: String,
    summary: String,
    action: String,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.glass)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 6.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_notifications),
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            GlassHeader(title, colors)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = colors.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = summary,
            color = colors.content,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = action,
            color = colors.content,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

/** Small uppercase header, like `@style/glass_card_header`. */
@Composable
private fun RowScope.GlassHeader(text: String, colors: LocationListColors) {
    Text(
        text = text.uppercase(),
        color = colors.secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = Modifier.weight(1f)
    )
}

/**
 * Spacing between the location cards, and a discreet hint under the last one explaining how to
 * reorder (touch and hold) and edit or delete (swipe) them, as the cards have no visible handles.
 */
internal class LocationListDecoration(
    context: Context,
    textColor: Int,
) : RecyclerView.ItemDecoration() {

    private val horizontal = context.dpToPx(16f).toInt()
    private val spacing = context.dpToPx(10f).toInt()
    private val hintPadding = context.dpToPx(14f).toInt()
    private val hint = context.getString(R.string.location_list_hint)
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(textColor, (0.45f * 255).toInt())
        textSize = context.spToPx(13)
    }
    private var layout: StaticLayout? = null

    private fun hintLayout(width: Int): StaticLayout {
        val textWidth = (width - 4 * horizontal).coerceAtLeast(1)
        return layout?.takeIf { it.width == textWidth }
            ?: StaticLayout.Builder.obtain(hint, 0, hint.length, paint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
                .also { layout = it }
    }

    override fun getItemOffsets(outRect: Rect, view: android.view.View, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.getChildViewHolder(view) !is LocationHolder) {
            outRect.setEmpty() // the header
            return
        }
        val position = parent.getChildAdapterPosition(view)
        val isLast = position == state.itemCount - 1
        outRect.set(
            horizontal,
            0,
            horizontal,
            if (isLast) spacing + hintLayout(parent.width).height + 2 * hintPadding else spacing
        )
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val count = parent.adapter?.itemCount ?: 0
        if (count <= 1) return // header only
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (parent.getChildAdapterPosition(child) == count - 1) {
                val hintLayout = hintLayout(parent.width)
                // Follows the card while it settles, but not while it's dragged around
                val top = child.bottom + spacing + hintPadding
                c.withTranslation((parent.width - hintLayout.width) / 2f, top.toFloat()) {
                    hintLayout.draw(this)
                }
            }
        }
    }
}

/** The list header (a [ComposeView]) as the first row of the list, so it scrolls with the cards. */
internal class LocationListHeaderAdapter(
    private val header: ComposeView,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount() = 1

    override fun getItemViewType(position: Int) = R.id.location_list_header

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        (header.parent as? ViewGroup)?.removeView(header)
        header.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return object : RecyclerView.ViewHolder(header) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
}
