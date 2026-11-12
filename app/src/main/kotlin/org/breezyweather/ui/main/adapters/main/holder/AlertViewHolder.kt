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
import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import breezyweather.domain.location.model.Location
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.weather.model.getFormattedDates
import org.breezyweather.ui.theme.ThemeManager
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme
import org.breezyweather.ui.theme.compose.themeRipple
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import java.util.Date

class AlertViewHolder(
    context: Context,
) : AbstractMainCardViewHolder(context) {

    private val bound = mutableStateOf<Pair<BreezyActivity, Location>?>(null)

    // Warm-tinted liquid glass for alerts
    override val glassTint = androidx.compose.ui.graphics.Color(
        ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.alert_background), (0.62f * 255).toInt())
    )

    @SuppressLint("DefaultLocale")
    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        // The list only shows this card when an alert is still running or to come
        bound.value = activity to location
    }

    @Composable
    override fun CardContent() {
        val (activity, location) = bound.value ?: return
        BreezyWeatherTheme(!ThemeManager.isLightTheme(activity, location)) {
            if (location.weather!!.currentAlertList.isEmpty()) {
                UpcomingAlerts(location.formattedId)
            } else {
                AlertList(location)
            }
        }
    }

    @Composable
    fun UpcomingAlerts(
        formattedId: String,
        modifier: Modifier = Modifier,
    ) {
        val activity = LocalActivity.current
        ListItem(
            modifier = modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = themeRipple(),
                    onClick = {
                        activity?.let {
                            IntentHelper.startAlertActivity(it, formattedId)
                        }
                    }
                ),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            headlineContent = {
                Text(
                    stringResource(R.string.alerts_to_follow),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            leadingContent = {
                Icon(
                    painterResource(R.drawable.ic_warning),
                    contentDescription = stringResource(R.string.alerts_to_follow),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        )
    }

    @Composable
    fun AlertList(
        location: Location,
        modifier: Modifier = Modifier,
    ) {
        val activity = LocalActivity.current
        val context = LocalContext.current
        Column {
            location.weather!!.currentAlertList.forEach { currentAlert ->
                ListItem(
                    modifier = modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = themeRipple(),
                            onClick = {
                                activity?.let {
                                    IntentHelper.startAlertActivity(it, location.formattedId, currentAlert.alertId)
                                }
                            }
                        ),
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    headlineContent = {
                        Text(
                            currentAlert.headline?.ifEmpty {
                                stringResource(R.string.alert)
                            } ?: stringResource(R.string.alert),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    supportingContent = currentAlert.startDate?.let {
                        {
                            Text(
                                currentAlert.getFormattedDates(location, context),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clearAndSetSemantics {
                                        contentDescription = currentAlert.getFormattedDates(
                                            location,
                                            context,
                                            full = true
                                        )
                                    }
                            )
                        }
                    },
                    leadingContent = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(dimensionResource(R.dimen.material_icon_size))
                                .aspectRatio(ratio = 1F)
                                .clip(shape = MaterialShapes.Triangle.toShape())
                                .background(Color(currentAlert.color))
                        ) {}
                    }
                )
            }
        }
    }
}
