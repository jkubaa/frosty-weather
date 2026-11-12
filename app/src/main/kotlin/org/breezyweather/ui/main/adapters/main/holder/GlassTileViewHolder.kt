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

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.breezyweather.ui.main.widgets.Compass
import org.breezyweather.ui.main.widgets.MiniBars
import org.breezyweather.ui.main.widgets.ScaleBar
import org.breezyweather.ui.main.widgets.toAnnotatedString
import kotlin.math.max

/**
 * Square liquid glass tile, iOS weather style, drawn in Compose: header with [iconId] and
 * [titleId], then a big value, a label, an optional graphic (scale, bars, compass) and description.
 * Subclasses fill the slots in their onBindView, through the same calls as when the tiles were Views.
 */
abstract class GlassTileViewHolder(
    parent: ViewGroup,
    @DrawableRes private val iconId: Int,
    @StringRes private val titleId: Int,
) : ComposeTileViewHolder(parent) {

    private sealed interface Graphic {
        class Scale(val colors: List<Color>, val fraction: Float?) : Graphic
        class Bars(val values: List<Float?>, val color: Color) : Graphic
    }

    private val value = mutableStateOf<CharSequence>("-")
    private val label = mutableStateOf<CharSequence?>(null)
    private val description = mutableStateOf<CharSequence?>(null)
    private val graphic = mutableStateOf<Graphic?>(null)
    private val compass = mutableStateOf<Float?>(null)
    private val hasCompass = mutableStateOf(false)
    private val accent = mutableStateOf<Color?>(null)
    private val descriptionLines = mutableIntStateOf(3)

    protected fun setValue(value: CharSequence?) {
        this.value.value = value ?: "-"
    }

    protected fun setLabel(label: CharSequence?) {
        this.label.value = label?.takeIf { it.isNotEmpty() }
    }

    protected fun setDescription(description: CharSequence?, maxLines: Int = 3) {
        this.description.value = description?.takeIf { it.isNotEmpty() }
        descriptionLines.intValue = maxLines
    }

    /** Color scale with a dot at [fraction] (0..1), at the bottom of the graphic area. */
    protected fun showScale(@ColorInt colors: IntArray, fraction: Float?) {
        graphic.value = Graphic.Scale(colors.map { Color(it) }, fraction)
    }

    /** Compass to the right of the value; [degree] null for no arrow. */
    protected fun showCompass(degree: Float?) {
        hasCompass.value = true
        compass.value = degree
    }

    /** Small bar chart (values 0..1, null for missing) at the bottom of the graphic area. */
    protected fun showBars(values: List<Float?>, @ColorInt color: Int) {
        graphic.value = Graphic.Bars(values, Color(color))
    }

    /**
     * Faint glow of [color] in the top corner of the tile, hinting at what it shows
     * (e.g. the UV level color). Subtle on purpose: the tile stays glass.
     */
    protected fun setAccent(@ColorInt color: Int) {
        accent.value = Color(color)
    }

    protected fun clearGraphic() {
        graphic.value = null
    }

    protected fun levelColors(vararg colorIds: Int): IntArray =
        colorIds.map { ContextCompat.getColor(itemView.context, it) }.toIntArray()

    /** Soft radial glow from the top end corner, fading out across the tile. */
    override fun DrawScope.tileBackground() {
        val color = accent.value ?: return
        val radius = max(size.width, size.height) * 0.95f
        drawRect(
            Brush.radialGradient(
                0f to color.copy(alpha = 0.30f),
                0.45f to color.copy(alpha = 0.10f),
                1f to Color.Transparent,
                center = Offset(size.width, 0f),
                radius = radius.coerceAtLeast(1f)
            )
        )
    }

    @Composable
    override fun ColumnScope.Content(wide: Boolean) {
        TileHeader(iconId, itemView.context.getString(titleId))
        // Value and label, with the wind compass to their right
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = value.value.toAnnotatedString(),
                    color = contentColor,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    maxLines = 1
                )
                label.value?.let {
                    Text(
                        text = it.toAnnotatedString(),
                        color = contentColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (hasCompass.value) {
                Compass(compass.value, contentColor, Modifier.padding(start = 6.dp).size(58.dp))
            }
        }
        // Graphic: fills the remaining space, drawn at its bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            when (val g = graphic.value) {
                is Graphic.Scale -> ScaleBar(g.colors, g.fraction)
                is Graphic.Bars -> MiniBars(g.values, g.color, contentColor)
                null -> Unit
            }
        }
        description.value?.let {
            Text(
                text = it.toAnnotatedString(),
                color = contentColor.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = descriptionLines.intValue,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** Accent colors of the tiles that don't take theirs from the data (like UV or air quality levels). */
internal object TileColors {
    val WATER = AndroidColor.rgb(79, 175, 255) // humidity, precipitation
    val CLEAR_AIR = AndroidColor.rgb(207, 230, 255) // visibility
    val PRESSURE = AndroidColor.rgb(167, 139, 250)
    val WIND = AndroidColor.rgb(94, 230, 208)
}
