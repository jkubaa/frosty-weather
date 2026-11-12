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
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.ui.theme.resource.providers.ResourceProvider

/**
 * Liquid glass tile drawn in Compose, matching the View-based [GlassTileViewHolder] tiles: small
 * uppercase header, then the subclass' content. Square, or wider when alone in its row
 * ([widthFactor]), so it keeps the height of the other tiles.
 *
 */
abstract class ComposeTileViewHolder(
    context: Context,
) : AbstractMainCardViewHolder(context) {

    private val tileWidth = mutableFloatStateOf(1f)

    /** Text and drawing color on glass. */
    protected val contentColor: Color = Color(context.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse))

    @Composable
    override fun CardContent() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(tileWidth.floatValue)
                .drawBehind { tileBackground() }
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)
        ) {
            Content(tileWidth.floatValue > 1.2f)
        }
    }

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        tileWidth.floatValue = widthFactor
    }

    /** Drawn under the whole tile, padding included (e.g. an accent glow). */
    protected open fun DrawScope.tileBackground() {}

    /** @param wide the tile spreads over a whole row */
    @Composable
    protected abstract fun ColumnScope.Content(wide: Boolean)

    @Composable
    protected fun TileHeader(@DrawableRes icon: Int, title: String) {
        Row(
            modifier = Modifier.alpha(0.6f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title.uppercase(),
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 5.dp)
                    .semantics { heading() }
            )
        }
        Spacer(Modifier.height(6.dp))
    }

    /** Big value, like the other tiles' (32sp). */
    @Composable
    protected fun TileValue(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 32.sp,
            lineHeight = 34.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}
