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
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.weather.index.PollenIndex
import org.breezyweather.domain.weather.model.getColor
import org.breezyweather.domain.weather.model.getIndex
import org.breezyweather.domain.weather.model.getIndexName
import org.breezyweather.domain.weather.model.getName
import org.breezyweather.domain.weather.model.pollensWithConcentration
import org.breezyweather.domain.weather.index.PollenIndex.Companion.getNegligiblePollenColor
import org.breezyweather.domain.weather.index.PollenIndex.Companion.getNegligiblePollenText
import org.breezyweather.ui.main.MainActivity
import org.breezyweather.ui.theme.resource.providers.ResourceProvider

/**
 * Pollen tile: the two most concentrated pollens (mold aside) with their level and its color,
 * or that pollen is negligible today.
 */
class PollenViewHolder(context: Context) : ComposeTileViewHolder(context) {

    /** [name] null for the "negligible" line. */
    @Immutable
    private data class Entry(val name: String?, val level: String?, val color: Int)

    private val entries = mutableStateOf<List<Entry>>(emptyList())

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        val pollen = location.weather?.today?.pollen
        val source = location.pollenSource?.let { (activity as MainActivity).sourceManager.getPollenIndexSource(it) }
        entries.value = if (pollen == null) {
            emptyList()
        } else {
            val primary = pollen.pollensWithConcentration
                .filter { it != PollenIndex.MOLD }
                .sortedByDescending { pollen.getIndex(it) }
                .take(2)
            if (primary.isEmpty() && pollen.isValid) {
                listOf(Entry(null, getNegligiblePollenText(activity, source), getNegligiblePollenColor(activity, source)))
            } else {
                primary.map { Entry(pollen.getName(activity, it), pollen.getIndexName(activity, it, source), pollen.getColor(activity, it, source)) }
            }
        }

        val separator = activity.getString(R.string.colon_separator)
        itemView.contentDescription = listOf(activity.getString(R.string.pollen))
            .plus(entries.value.map { listOfNotNull(it.name, it.level).joinToString(separator) })
            .joinToString(separator)
        itemView.setOnClickListener {
            IntentHelper.startDailyWeatherActivity(activity, location.formattedId, location.weather!!.todayIndex, DetailScreen.TAG_POLLEN)
        }
    }

    @Composable
    override fun ColumnScope.Content(wide: Boolean) {
        TileHeader(R.drawable.ic_allergy, itemView.context.getString(R.string.pollen))
        entries.value.forEach { entry ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 6.dp, end = 8.dp)
                        .size(10.dp)
                        .background(Color(entry.color), CircleShape)
                )
                Column {
                    entry.name?.let {
                        Text(it, color = contentColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    entry.level?.let {
                        Text(
                            text = it,
                            color = contentColor.copy(alpha = if (entry.name != null) 0.8f else 1f),
                            fontSize = if (entry.name != null) 14.sp else 17.sp,
                            fontWeight = if (entry.name != null) FontWeight.Normal else FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
