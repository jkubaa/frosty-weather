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

package org.breezyweather.ui.common.glass

import android.content.Context
import android.graphics.Canvas
import android.widget.FrameLayout

/**
 * Holds the content (icon, text) of a floating glass element whose blur root contains the element
 * itself (see [LiquidGlass.applyFloating]).
 *
 * The blur takes its snapshot of the root on a software canvas: skipping that pass keeps the
 * element's own content out of its glass (otherwise it shows as a smudged copy behind itself), while
 * the real, hardware accelerated drawing on screen is unchanged.
 */
class GlassContentLayout(context: Context) : FrameLayout(context) {

    override fun draw(canvas: Canvas) {
        if (!canvas.isHardwareAccelerated) return
        super.draw(canvas)
    }
}
