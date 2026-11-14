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

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Fades the left and/or right edge of a sideways scroller out over [length], when there is more to
 * scroll to on that side (so the first and last items stay fully visible).
 */
fun Modifier.fadeHorizontalEdges(start: Boolean, end: Boolean, length: Dp): Modifier {
    if (!start && !end) return this
    return composed {
        val px = with(LocalDensity.current) { length.toPx() }
        graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (start) {
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = px),
                        size = Size(px, size.height),
                        blendMode = BlendMode.DstIn
                    )
                }
                if (end) {
                    val left = size.width - px
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = left, endX = size.width),
                        topLeft = Offset(left, 0f),
                        size = Size(px, size.height),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
    }
}
