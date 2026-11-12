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

import android.content.Context
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import breezyweather.domain.location.model.Location
import org.breezyweather.ui.theme.resource.providers.ResourceProvider

/**
 * One card of the home screen. Cards are plain objects drawn in Compose by the card list
 * (see HomeCardsView): [onBindView] gives them a location, [Render] draws them.
 *
 * They were RecyclerView view holders; [itemView] keeps the few calls they made on their view
 * (description for screen readers, click), now applied to the Compose card.
 */
abstract class AbstractMainViewHolder(
    protected val context: Context,
) {
    val itemView = CardItemView(context)
    protected var provider: ResourceProvider? = null
    protected var itemAnimationEnabled = false

    @CallSuper
    open fun onBindView(
        context: Context,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        this.provider = provider
        this.itemAnimationEnabled = itemAnimationEnabled
    }

    @Composable
    abstract fun Render(modifier: Modifier)
}

/** What the cards set on their former view: what screen readers say, and what a tap does. */
class CardItemView(val context: Context) {
    var contentDescription: CharSequence? by mutableStateOf(null)

    var onClick: (() -> Unit)? by mutableStateOf(null)
        private set

    fun setOnClickListener(listener: ((Any?) -> Unit)?) {
        onClick = listener?.let { { it(null) } }
    }
}
