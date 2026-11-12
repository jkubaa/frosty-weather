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

package org.breezyweather.ui.main.adapters.main.holder

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.CallSuper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import breezyweather.domain.location.model.Location
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.ui.main.cards.liquidGlass
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.ui.theme.weatherView.sky.SkyBackdrop

/** The sky the glass cards blur behind them. */
val LocalSkyBackdrop = compositionLocalOf<SkyBackdrop?> { null }

/** A liquid glass card: the card's [CardContent] on a glass surface, clickable as a whole if it sets a click. */
@SuppressLint("ObjectAnimatorBinding")
abstract class AbstractMainCardViewHolder(
    context: Context,
) : AbstractMainViewHolder(context) {
    protected var mLocation: Location? = null

    /**
     * Width of this card compared to a regular tile: 1 for a tile sharing its row as usual, 2 for a
     * tile left alone in the last row of two (it then spreads over the whole width). Set by the
     * list before binding; tiles use it to keep the same height as the others.
     */
    var widthFactor: Float = 1f

    /** Glass body color; null for the theme's default glass. */
    protected open val glassTint: Color? = null

    @CallSuper
    open fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        mLocation = location
    }

    @CallSuper
    open fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
        selectedTab: String?,
        setSelectedTab: (String?) -> Unit,
    ) {
        onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
    }

    @SuppressLint("MissingSuperCall")
    override fun onBindView(
        context: Context,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        throw RuntimeException("Deprecated method.")
    }

    @Composable
    override fun Render(modifier: Modifier) {
        val description = itemView.contentDescription?.toString()
        val click = itemView.onClick
        Box(
            modifier
                .liquidGlass(LocalSkyBackdrop.current, glassTint)
                .then(if (click != null) Modifier.clickable { click() } else Modifier)
                .then(
                    if (description != null) {
                        // A card that describes itself is read out as one item
                        Modifier.clearAndSetSemantics {
                            contentDescription = description
                            if (click != null) {
                                onClick {
                                    click()
                                    true
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            CardContent()
        }
    }

    @Composable
    protected abstract fun CardContent()
}
