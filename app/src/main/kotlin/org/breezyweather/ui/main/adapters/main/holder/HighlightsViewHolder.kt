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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.ui.main.utils.Highlights
import org.breezyweather.ui.theme.resource.providers.ResourceProvider

/**
 * Highlights: a few plain-language lines about what matters in the coming hours and days
 * (rain, temperature change, UV, gusts, next sunrise or sunset). See [Highlights] for the rules.
 */
class HighlightsViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_highlights_card, parent, false)
) {
    private val rows: LinearLayout = itemView.findViewById(R.id.highlights_rows)

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val inflater = LayoutInflater.from(activity)
        rows.removeAllViews()
        Highlights.build(activity, location).forEach { highlight ->
            val row = inflater.inflate(R.layout.item_glass_highlight, rows, false)
            row.findViewById<ImageView>(R.id.highlight_icon).setImageResource(highlight.icon)
            row.findViewById<TextView>(R.id.highlight_title).text = highlight.title
            row.findViewById<TextView>(R.id.highlight_detail).apply {
                visibility = if (highlight.detail != null) View.VISIBLE else View.GONE
                text = highlight.detail
            }
            rows.addView(row)
        }
    }
}
