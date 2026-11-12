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

package org.breezyweather.ui.theme.weatherView.sky

import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The sky, drawable from elsewhere: liquid glass cards draw the part of the sky that is behind
 * them, then blur it, so they look frosted without capturing the screen.
 *
 * The sky view records itself each frame into a shared GPU display list and keeps [origin] and
 * [size] up to date; cards draw that recording, shifted to their place on screen.
 */
@Stable
class SkyBackdrop {

    /** Draws the whole sky in a scope of the sky's [size]. */
    internal var painter: (DrawScope.() -> Unit)? by mutableStateOf(null)

    /** Where the sky view is, in window coordinates. */
    internal var origin by mutableStateOf(Offset.Zero)

    internal var size by mutableStateOf(Size.Zero)

    val isReady: Boolean
        get() = painter != null && size.width > 0f

    /**
     * The sky as recorded for the current frame (Android 10+). The sky view records into it once per
     * frame; the cards only reference it, so when the sky animates the GPU redraws them from the new
     * recording without any of them drawing again.
     */
    private var node: RenderNode? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    internal fun obtainNode(): RenderNode = node ?: RenderNode("SkyBackdrop").also { node = it }

    /**
     * Draws the sky behind something placed at [at] (window coordinates) into [canvas], a hardware
     * canvas: the sky appears exactly as it is on screen under that spot.
     */
    fun drawBackdrop(canvas: android.graphics.Canvas, at: Offset) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !canvas.isHardwareAccelerated) return
        val skyNode = node?.takeIf { it.hasDisplayList() } ?: return
        val shift = origin - at
        canvas.save()
        canvas.translate(shift.x, shift.y)
        canvas.drawRenderNode(skyNode)
        canvas.restore()
    }
}
