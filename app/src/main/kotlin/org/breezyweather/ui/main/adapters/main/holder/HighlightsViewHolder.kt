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
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.ui.main.utils.Highlights
import org.breezyweather.ui.main.widgets.RingGaugeView
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
        val contentColor = activity.getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse)
        rows.removeAllViews()
        // Hairline under the header, like the other glass cards, then one row of round gauges
        inflater.inflate(R.layout.item_glass_divider, rows, true)
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        Highlights.build(activity, location).forEach { highlight ->
            val cell = inflater.inflate(R.layout.item_glass_highlight, row, false)
            cell.findViewById<RingGaugeView>(R.id.highlight_gauge)
                .setGauge(highlight.fraction, highlight.color, contentColor)
            // A value inside the ring when there is one ("97%", "+6°"), otherwise the icon (sunset...)
            cell.findViewById<TextView>(R.id.highlight_value).apply {
                visibility = if (highlight.value != null) View.VISIBLE else View.GONE
                text = highlight.value
            }
            cell.findViewById<ImageView>(R.id.highlight_icon).apply {
                visibility = if (highlight.value == null) View.VISIBLE else View.GONE
                setImageResource(highlight.icon)
            }
            cell.findViewById<TextView>(R.id.highlight_label).text = highlight.label
            cell.findViewById<TextView>(R.id.highlight_sublabel).apply {
                visibility = if (highlight.sublabel != null) View.VISIBLE else View.INVISIBLE
                text = highlight.sublabel
            }
            // Screen readers get the full sentence rather than the short labels
            cell.contentDescription = listOfNotNull(highlight.title, highlight.detail)
                .joinToString(activity.getString(org.breezyweather.unit.R.string.locale_separator))
            row.addView(cell)
        }
        rows.addView(row)
    }
}
