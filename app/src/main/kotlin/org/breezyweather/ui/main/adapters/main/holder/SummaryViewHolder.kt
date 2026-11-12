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

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.recyclerview.widget.RecyclerView
import breezyweather.domain.location.model.Location
import com.google.android.material.card.MaterialCardView
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.ui.main.utils.ForecastSummary
import org.breezyweather.ui.theme.resource.providers.ResourceProvider

/**
 * iOS-style summary under the hero: one or two plain sentences about the hours ahead, in a liquid
 * glass card ("Rain expected around 7 PM. Wind gusts are up to 25 mph."). See [ForecastSummary].
 *
 * The card is the item's root, like every other glass card: the glass blur layer it gets is clipped
 * to the card's rounded outline.
 */
class SummaryViewHolder private constructor(
    card: MaterialCardView,
) : AbstractMainCardViewHolder(card) {

    private val text = mutableStateOf<String?>(null)

    constructor(parent: ViewGroup) : this(
        MaterialCardView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            strokeWidth = 0
        }
    )

    init {
        val content = Color(card.context.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse))
        card.addView(
            ComposeView(card.context).apply {
                setContent {
                    text.value?.let {
                        Text(
                            text = it,
                            color = content,
                            fontSize = 16.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        )
    }

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        text.value = ForecastSummary.build(activity, location)
    }
}
