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

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.ui.main.utils.ForecastMetric

/**
 * iOS-weather-like segmented control made of icons (conditions, precipitation, wind...), in a
 * translucent tray with a lighter pill behind the selected one. [container] is the tray; it is
 * hidden when there is only one metric to choose from.
 */
internal class MetricSwitcher(private val container: LinearLayout) {

    fun bind(metrics: List<ForecastMetric>, selected: ForecastMetric, onSelect: (ForecastMetric) -> Unit) {
        val context = container.context
        container.removeAllViews()
        if (metrics.size < 2) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE

        val content = context.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse)
        // Compact: it shares the card header line with the title
        val itemHeight = context.dpToPx(28f)
        container.background = GradientDrawable().apply {
            cornerRadius = itemHeight / 2f + context.dpToPx(3f)
            setColor(ColorUtils.setAlphaComponent(content, 22))
        }

        metrics.forEach { metric ->
            val isSelected = metric == selected
            val item = FrameLayout(context).apply {
                contentDescription = context.getString(metric.nameId)
                this.isSelected = isSelected
                background = if (isSelected) {
                    GradientDrawable().apply {
                        cornerRadius = itemHeight / 2f
                        setColor(ColorUtils.setAlphaComponent(content, 56))
                    }
                } else {
                    null
                }
                setOnClickListener { if (!isSelected) onSelect(metric) }
            }
            val icon = ImageView(context).apply {
                setImageResource(metric.icon)
                setColorFilter(content)
                alpha = if (isSelected) 1f else 0.7f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val iconSize = context.dpToPx(16f).toInt()
            item.addView(icon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            container.addView(
                item,
                LinearLayout.LayoutParams(context.dpToPx(38f).toInt(), itemHeight.toInt())
            )
        }
    }
}
