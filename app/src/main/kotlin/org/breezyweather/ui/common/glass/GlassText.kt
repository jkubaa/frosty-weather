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

package org.breezyweather.ui.common.glass

import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/**
 * "Glassy" text for the hero (location name, temperature): the glyphs are filled with a vertical
 * gradient, bright at the top and slightly see-through at the bottom, so the sky shows through a
 * little, like frosted glass catching light from above.
 *
 * The gradient follows the text color and is rebuilt when the view is resized, so it keeps working
 * with auto-sizing and animated numbers. Safe to call again on rebind.
 */
object GlassText {

    private const val BOTTOM_OPACITY = 0.62f

    fun apply(textView: TextView) {
        update(textView)
        if (textView.getTag(org.breezyweather.R.id.tag_glass_text) == null) {
            val listener = View.OnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top != oldBottom - oldTop) update(v as TextView)
            }
            textView.addOnLayoutChangeListener(listener)
            textView.setTag(org.breezyweather.R.id.tag_glass_text, listener)
        }
    }

    private fun update(textView: TextView) {
        val height = textView.height.toFloat()
        if (height <= 0f) return
        val color = textView.currentTextColor
        textView.paint.shader = LinearGradient(
            0f,
            textView.paddingTop.toFloat(),
            0f,
            height - textView.paddingBottom,
            intArrayOf(
                color,
                ColorUtils.setAlphaComponent(color, (0.92f * 255).toInt()),
                ColorUtils.setAlphaComponent(color, (BOTTOM_OPACITY * 255).toInt())
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        textView.invalidate()
    }
}
