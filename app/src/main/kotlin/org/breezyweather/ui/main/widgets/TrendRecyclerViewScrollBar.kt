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

package org.breezyweather.ui.main.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import org.breezyweather.common.extensions.getThemeColor

class TrendRecyclerViewScrollBar : ItemDecoration() {
    private val mPaint = Paint().apply {
        isAntiAlias = true
    }
    private var mScrollBarWidth = 0
    private var mScrollBarHeight = 0
    private var mThemeChanged = false

    @ColorInt
    private var mEndPointsColor = 0

    @ColorInt
    private var mCenterColor = 0
    fun resetColor(context: Context) {
        mThemeChanged = true
        // Liquid glass: the cards are translucent, so the highlight must be too (an opaque card color
        // would show up as a solid column). A faint content-colored wash fading out at both ends.
        val contentColor = context.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse)
        mEndPointsColor = ColorUtils.setAlphaComponent(contentColor, 0)
        mCenterColor = ColorUtils.setAlphaComponent(contentColor, (0.10 * 255).toInt())
    }

    override fun onDraw(
        c: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        if (parent.isNotEmpty()) {
            if (mScrollBarWidth == 0) mScrollBarWidth = parent.getChildAt(0).measuredWidth
            if (mScrollBarHeight == 0) mScrollBarHeight = parent.getChildAt(0).measuredHeight
        }
        if (consumedThemeChanged()) {
            mPaint.setShader(
                LinearGradient(
                    0f,
                    0f,
                    0f,
                    mScrollBarHeight / 2f,
                    mEndPointsColor,
                    mCenterColor,
                    Shader.TileMode.MIRROR
                )
            )
        }
        val extent = parent.computeHorizontalScrollExtent()
        val range = parent.computeHorizontalScrollRange()
        val offset = parent.computeHorizontalScrollOffset()
        val offsetPercent = 1f * offset / (range - extent)
        val scrollBarOffsetX = offsetPercent * (parent.measuredWidth - mScrollBarWidth)
        c.drawRect(
            scrollBarOffsetX,
            0f,
            mScrollBarWidth + scrollBarOffsetX,
            mScrollBarHeight.toFloat(),
            mPaint
        )
    }

    private fun consumedThemeChanged(): Boolean {
        return if (mThemeChanged) {
            mThemeChanged = false
            true
        } else {
            false
        }
    }
}
