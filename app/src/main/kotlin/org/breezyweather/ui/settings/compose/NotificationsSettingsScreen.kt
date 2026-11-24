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

package org.breezyweather.ui.settings.compose

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import org.breezyweather.BreezyWeather
import org.breezyweather.R
import org.breezyweather.background.forecast.TodayForecastNotificationJob
import org.breezyweather.background.forecast.TomorrowForecastNotificationJob
import org.breezyweather.common.extensions.plus
import org.breezyweather.common.options.UpdateInterval
import kotlinx.collections.immutable.toImmutableList
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.remoteviews.AlertLiveUpdate
import org.breezyweather.remoteviews.RainLiveUpdate
import org.breezyweather.ui.common.composables.AnimatedVisibilitySlideVertically
import org.breezyweather.ui.common.widgets.Material3Scaffold
import org.breezyweather.ui.common.widgets.generateCollapsedScrollBehavior
import org.breezyweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import org.breezyweather.ui.settings.preference.bottomInsetItem
import org.breezyweather.ui.settings.preference.composables.PreferenceScreen
import org.breezyweather.ui.settings.preference.composables.ListPreferenceViewWithCard
import org.breezyweather.ui.settings.preference.composables.MultiListPreferenceViewWithCard
import org.breezyweather.ui.settings.preference.composables.PreferenceItem
import org.breezyweather.ui.settings.preference.composables.PreferenceViewWithCard
import org.breezyweather.ui.settings.preference.composables.SwitchPreferenceView
import org.breezyweather.ui.settings.preference.composables.TimePickerPreferenceView
import org.breezyweather.ui.settings.preference.largeSeparatorItem
import org.breezyweather.ui.settings.preference.listPreferenceItem
import org.breezyweather.ui.settings.preference.sectionFooterItem
import org.breezyweather.ui.settings.preference.sectionHeaderItem
import org.breezyweather.ui.settings.preference.smallSeparatorItem
import org.breezyweather.ui.settings.preference.switchPreferenceItem
import org.breezyweather.ui.settings.preference.timePickerPreferenceItem

@Composable
fun NotificationsSettingsScreen(
    context: Context,
    onNavigateBack: () -> Unit,
    hasNotificationPermission: Boolean,
    postNotificationPermissionEnsurer: (succeedCallback: () -> Unit) -> Unit,
    todayForecastEnabled: Boolean,
    tomorrowForecastEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = generateCollapsedScrollBehavior()
    val settings = SettingsManager.getInstance(context)
    // The rain countdown options only apply while precipitation notifications are on
    var precipitationEnabled by remember { mutableStateOf(settings.isPrecipitationPushEnabled) }
    var quietHoursEnabled by remember { mutableStateOf(settings.rainLiveQuietHours) }
    var alertsEnabled by remember { mutableStateOf(settings.isAlertPushEnabled) }
    var alertLiveEnabled by remember { mutableStateOf(settings.alertLiveEnabled) }

    Material3Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FitStatusBarTopAppBar(
                title = stringResource(R.string.settings_notifications),
                onBackPressed = onNavigateBack,
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddings ->
        PreferenceScreen(
            paddingValues = paddings.plus(PaddingValues(horizontal = dimensionResource(R.dimen.normal_margin)))
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listPreferenceItem(R.string.settings_notifications_permission) { title ->
                    AnimatedVisibilitySlideVertically(
                        visible = !hasNotificationPermission
                    ) {
                        PreferenceViewWithCard(
                            iconId = R.drawable.ic_about,
                            title = stringResource(title),
                            summary = stringResource(
                                R.string.settings_notifications_permission_summary,
                                stringResource(R.string.action_grant_permission)
                            ),
                            surface = MaterialTheme.colorScheme.primaryContainer,
                            onSurface = MaterialTheme.colorScheme.onPrimaryContainer,
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            isFirst = true,
                            isLast = true,
                            modifier = Modifier.padding(bottom = dimensionResource(R.dimen.normal_margin)),
                            onClick = {
                                postNotificationPermissionEnsurer { /* no callback */ }
                            }
                        )
                    }
                }
            }

            if (BreezyWeather.instance.isGitHubUpdateCheckerEnabled) {
                sectionHeaderItem(R.string.notification_channel_app_updates)
                switchPreferenceItem(R.string.settings_notifications_app_updates_check) { id ->
                    SwitchPreferenceView(
                        titleId = id,
                        summaryOnId = R.string.settings_enabled,
                        summaryOffId = R.string.settings_disabled,
                        checked = SettingsManager.getInstance(context).isAppUpdateCheckEnabled,
                        enabled = hasNotificationPermission,
                        isFirst = true,
                        isLast = true,
                        onValueChanged = {
                            SettingsManager.getInstance(context).isAppUpdateCheckEnabled = it
                        }
                    )
                }
                sectionFooterItem(R.string.notification_channel_app_updates)
            }

            largeSeparatorItem()

            sectionHeaderItem(R.string.settings_notifications_section_general)
            switchPreferenceItem(R.string.settings_notifications_alerts_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = if (SettingsManager.getInstance(context).updateInterval !=
                        UpdateInterval.INTERVAL_NEVER
                    ) {
                        R.string.settings_disabled
                    } else {
                        R.string.settings_unavailable_no_background_updates
                    },
                    checked = SettingsManager.getInstance(context).isAlertPushEnabled &&
                        SettingsManager.getInstance(context).updateInterval != UpdateInterval.INTERVAL_NEVER,
                    enabled = SettingsManager.getInstance(context).updateInterval != UpdateInterval.INTERVAL_NEVER &&
                        hasNotificationPermission,
                    isFirst = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isAlertPushEnabled = it
                        alertsEnabled = it
                        if (!it) AlertLiveUpdate.cancel(context)
                    }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_notifications_precipitations_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = if (SettingsManager.getInstance(context).updateInterval !=
                        UpdateInterval.INTERVAL_NEVER
                    ) {
                        R.string.settings_disabled
                    } else {
                        R.string.settings_unavailable_no_background_updates
                    },
                    checked = SettingsManager.getInstance(context).isPrecipitationPushEnabled &&
                        SettingsManager.getInstance(context).updateInterval != UpdateInterval.INTERVAL_NEVER,
                    enabled = SettingsManager.getInstance(context).updateInterval != UpdateInterval.INTERVAL_NEVER &&
                        hasNotificationPermission,
                    isLast = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isPrecipitationPushEnabled = it
                        precipitationEnabled = it
                        if (!it) RainLiveUpdate.cancel(context)
                    }
                )
            }
            sectionFooterItem(R.string.settings_notifications_section_general)

            largeSeparatorItem()

            // Rain countdown (Live Update) options
            val rainLiveEnabled = precipitationEnabled &&
                settings.updateInterval != UpdateInterval.INTERVAL_NEVER &&
                hasNotificationPermission
            sectionHeaderItem(R.string.settings_notifications_section_rain_live)
            listPreferenceItem(R.string.settings_rain_live_lead) { id ->
                val values = arrayOf("5", "10", "15", "30")
                ListPreferenceViewWithCard(
                    title = stringResource(id),
                    summary = { ctx, value -> ctx.getString(R.string.settings_rain_live_lead_value, value.toInt()) },
                    selectedKey = settings.rainLiveLeadMinutes.toString(),
                    valueArray = values,
                    nameArray = values.map {
                        stringResource(R.string.settings_rain_live_lead_value, it.toInt())
                    }.toTypedArray(),
                    enabled = rainLiveEnabled,
                    isFirst = true,
                    onValueChanged = { settings.rainLiveLeadMinutes = it.toInt() }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_rain_live_skip_light) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_rain_live_skip_light_on,
                    summaryOffId = R.string.settings_rain_live_skip_light_off,
                    checked = settings.rainLiveSkipLight,
                    enabled = rainLiveEnabled,
                    onValueChanged = { settings.rainLiveSkipLight = it }
                )
            }
            smallSeparatorItem()
            listPreferenceItem(R.string.settings_rain_live_types) { id ->
                val types = SettingsManager.RAIN_LIVE_TYPES_ALL.toList()
                val names = mapOf(
                    "rain" to stringResource(R.string.notification_rain_live_rain),
                    "snow" to stringResource(R.string.notification_rain_live_snow),
                    "sleet_hail" to stringResource(R.string.settings_rain_live_type_sleet_hail),
                    "thunderstorm" to stringResource(R.string.notification_rain_live_thunderstorm)
                )
                MultiListPreferenceViewWithCard(
                    title = stringResource(id),
                    selectedKeys = settings.rainLiveTypes.filter { it in types }.toImmutableList(),
                    itemsArray = types.map { PreferenceItem(name = names.getValue(it), value = it) }.toTypedArray(),
                    noItemsMessage = stringResource(R.string.settings_rain_live_types_none),
                    enabled = rainLiveEnabled,
                    onValueChanged = { settings.rainLiveTypes = it.toSet() }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_rain_live_quiet) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_rain_live_quiet_on,
                    summaryOffId = R.string.settings_disabled,
                    checked = settings.rainLiveQuietHours,
                    enabled = rainLiveEnabled,
                    onValueChanged = {
                        settings.rainLiveQuietHours = it
                        quietHoursEnabled = it
                    }
                )
            }
            smallSeparatorItem()
            timePickerPreferenceItem(R.string.settings_rain_live_quiet_start) { id ->
                TimePickerPreferenceView(
                    titleId = id,
                    currentTime = settings.rainLiveQuietStart,
                    enabled = rainLiveEnabled && quietHoursEnabled,
                    onValueChanged = { settings.rainLiveQuietStart = it }
                )
            }
            smallSeparatorItem()
            timePickerPreferenceItem(R.string.settings_rain_live_quiet_end) { id ->
                TimePickerPreferenceView(
                    titleId = id,
                    currentTime = settings.rainLiveQuietEnd,
                    enabled = rainLiveEnabled && quietHoursEnabled,
                    isLast = true,
                    onValueChanged = { settings.rainLiveQuietEnd = it }
                )
            }
            sectionFooterItem(R.string.settings_notifications_section_rain_live)

            largeSeparatorItem()

            // Weather warnings (Live Update) options
            val alertSectionEnabled = alertsEnabled &&
                settings.updateInterval != UpdateInterval.INTERVAL_NEVER &&
                hasNotificationPermission
            sectionHeaderItem(R.string.settings_notifications_section_alert_live)
            switchPreferenceItem(R.string.settings_alert_live) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_alert_live_on,
                    summaryOffId = R.string.settings_disabled,
                    checked = settings.alertLiveEnabled,
                    enabled = alertSectionEnabled,
                    isFirst = true,
                    onValueChanged = {
                        settings.alertLiveEnabled = it
                        alertLiveEnabled = it
                        if (!it) AlertLiveUpdate.cancel(context)
                    }
                )
            }
            smallSeparatorItem()
            listPreferenceItem(R.string.settings_alert_live_severity) { id ->
                val values = arrayOf("moderate", "severe")
                val names = arrayOf(
                    stringResource(R.string.settings_alert_live_severity_moderate),
                    stringResource(R.string.settings_alert_live_severity_severe)
                )
                ListPreferenceViewWithCard(
                    title = stringResource(id),
                    summary = { _, value -> names[values.indexOf(value).coerceAtLeast(0)] },
                    selectedKey = settings.alertLiveMinSeverity,
                    valueArray = values,
                    nameArray = names,
                    enabled = alertSectionEnabled && alertLiveEnabled,
                    isLast = true,
                    onValueChanged = { settings.alertLiveMinSeverity = it }
                )
            }
            sectionFooterItem(R.string.settings_notifications_section_alert_live)

            largeSeparatorItem()

            sectionHeaderItem(R.string.settings_notifications_section_forecast)
            switchPreferenceItem(R.string.settings_notifications_forecast_today_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = todayForecastEnabled,
                    withState = false,
                    enabled = hasNotificationPermission,
                    isFirst = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isTodayForecastEnabled = it
                        TodayForecastNotificationJob.setupTask(context, false)
                    }
                )
            }
            smallSeparatorItem()
            timePickerPreferenceItem(R.string.settings_notifications_forecast_time_today_title) { id ->
                TimePickerPreferenceView(
                    titleId = id,
                    currentTime = SettingsManager.getInstance(context).todayForecastTime,
                    enabled = todayForecastEnabled && hasNotificationPermission,
                    onValueChanged = {
                        SettingsManager.getInstance(context).todayForecastTime = it
                        TodayForecastNotificationJob.setupTask(context, false)
                    }
                )
            }
            smallSeparatorItem()
            switchPreferenceItem(R.string.settings_notifications_forecast_tomorrow_title) { id ->
                SwitchPreferenceView(
                    titleId = id,
                    summaryOnId = R.string.settings_enabled,
                    summaryOffId = R.string.settings_disabled,
                    checked = tomorrowForecastEnabled,
                    withState = false,
                    enabled = hasNotificationPermission,
                    onValueChanged = {
                        SettingsManager.getInstance(context).isTomorrowForecastEnabled = it
                        TomorrowForecastNotificationJob.setupTask(context, false)
                    }
                )
            }
            smallSeparatorItem()
            timePickerPreferenceItem(R.string.settings_notifications_forecast_time_tomorrow_title) { id ->
                TimePickerPreferenceView(
                    titleId = id,
                    currentTime = SettingsManager.getInstance(context).tomorrowForecastTime,
                    enabled = tomorrowForecastEnabled && hasNotificationPermission,
                    isLast = true,
                    onValueChanged = {
                        SettingsManager.getInstance(context).tomorrowForecastTime = it
                        TomorrowForecastNotificationJob.setupTask(context, false)
                    }
                )
            }
            sectionFooterItem(R.string.settings_notifications_section_forecast)

            bottomInsetItem()
        }
    }
}
