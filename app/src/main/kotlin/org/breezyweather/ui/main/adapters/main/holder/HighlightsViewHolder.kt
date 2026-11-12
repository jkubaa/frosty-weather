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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.ui.main.utils.Highlight
import org.breezyweather.ui.main.utils.Highlights
import org.breezyweather.ui.main.widgets.RingGauge
import org.breezyweather.ui.theme.resource.providers.ResourceProvider

/**
 * Highlights, in Compose: up to four round gauges (watch-complication style) about what matters in
 * the coming hours and days (rain, temperature change, UV, gusts, next sunrise or sunset), each with
 * a short label. See [Highlights] for the rules.
 */
class HighlightsViewHolder(parent: ViewGroup) : ComposeCardViewHolder(parent) {

    private val highlights = mutableStateOf<List<Highlight>>(emptyList())
    private var separator = ", "

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        separator = activity.getString(org.breezyweather.unit.R.string.locale_separator)
        highlights.value = Highlights.build(activity, location)
    }

    @Composable
    override fun Content() {
        Column(Modifier.padding(top = 14.dp, bottom = 6.dp)) {
            GlassCardHeader(R.drawable.ic_sunshine_duration, itemView.context.getString(R.string.highlights), contentColor)
            GlassDivider(contentColor, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                highlights.value.forEach { Gauge(it, Modifier.weight(1f)) }
            }
        }
    }

    @Composable
    private fun Gauge(highlight: Highlight, modifier: Modifier) {
        Column(
            modifier = modifier
                .padding(vertical = 10.dp)
                // Screen readers get the full sentence rather than the short labels
                .clearAndSetSemantics {
                    contentDescription = listOfNotNull(highlight.title, highlight.detail).joinToString(separator)
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
                RingGauge(highlight.fraction, Color(highlight.color), contentColor, Modifier.size(62.dp))
                // A value inside the ring when there is one ("97%", "+6°"), otherwise the icon (sunset...)
                if (highlight.value != null) {
                    Text(highlight.value, color = contentColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                } else {
                    Icon(painterResource(highlight.icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                }
            }
            Text(
                text = highlight.label,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = highlight.sublabel ?: "",
                color = contentColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
