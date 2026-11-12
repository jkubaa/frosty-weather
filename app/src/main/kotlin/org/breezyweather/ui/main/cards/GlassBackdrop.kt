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

package org.breezyweather.ui.main.cards

import android.graphics.Canvas
import android.graphics.RenderNode
import android.os.Build
import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * What a liquid glass surface blurs: something on screen that can be drawn again, shifted, under
 * the glass. Implementations reference GPU recordings, so the glass follows them without redrawing.
 */
@Stable
interface GlassBackdrop {

    /** Read at draw time: snapshot state, so glass is redrawn when this changes. */
    val isReady: Boolean

    /** Draws what is on screen under a glass surface placed at [at] (window coordinates). */
    fun drawBackdrop(canvas: Canvas, at: Offset)
}

/** Several backdrops on top of each other (e.g. the sky, then the cards scrolling over it). */
class LayeredBackdrop(private vararg val layers: GlassBackdrop) : GlassBackdrop {
    override val isReady: Boolean
        get() = layers.map { it.isReady }.any { it }

    override fun drawBackdrop(canvas: Canvas, at: Offset) {
        layers.forEach { if (it.isReady) it.drawBackdrop(canvas, at) }
    }
}

/**
 * A view's own drawing as a backdrop (Android 10+): the view records what it draws into a
 * RenderNode ([record], from its dispatchDraw) and draws that. Its children's own recordings are
 * referenced, not copied, so when they change (scrolling...) the glass sees it without the view or
 * the glass drawing again.
 *
 * Call [moved] when the view moves without a redraw (e.g. translated by a swipe), so glass placed
 * over it shifts its copy along.
 */
class ViewBackdrop(private val view: View) : GlassBackdrop {

    private var node: RenderNode? = null
    private var version by mutableIntStateOf(0)
    private val location = IntArray(2)

    override val isReady: Boolean
        get() = version > 0

    fun moved() {
        if (version > 0) version++
    }

    /**
     * Records [draw] (the view's children) and draws the recording into [canvas].
     * @return false when it can't record (older Android, software canvas): then draw as usual
     */
    fun record(canvas: Canvas, draw: (Canvas) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !canvas.isHardwareAccelerated) return false
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return false
        val recording = node ?: RenderNode("ViewBackdrop").also { node = it }
        recording.setPosition(0, 0, width, height)
        val recordingCanvas = recording.beginRecording(width, height)
        try {
            draw(recordingCanvas)
        } finally {
            recording.endRecording()
        }
        canvas.drawRenderNode(recording)
        // First recording: glass waiting for it can draw now (not from within this draw)
        if (version == 0) view.post { if (version == 0) version = 1 }
        return true
    }

    override fun drawBackdrop(canvas: Canvas, at: Offset) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !canvas.isHardwareAccelerated) return
        val recording = node?.takeIf { it.hasDisplayList() } ?: return
        // Where the view is now, its translation included (it moves during location swipes)
        view.getLocationInWindow(location)
        canvas.save()
        canvas.translate(location[0] - at.x, location[1] - at.y)
        canvas.drawRenderNode(recording)
        canvas.restore()
    }
}
