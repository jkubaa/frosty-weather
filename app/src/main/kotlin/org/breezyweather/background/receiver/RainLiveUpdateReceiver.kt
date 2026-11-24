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

package org.breezyweather.background.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import breezyweather.data.location.LocationRepository
import breezyweather.data.weather.WeatherRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.breezyweather.background.weather.WeatherUpdateJob
import org.breezyweather.remoteviews.AlertLiveUpdate
import org.breezyweather.remoteviews.RainLiveUpdate
import javax.inject.Inject
import org.breezyweather.BuildConfig.APPLICATION_ID as ID

/**
 * Alarms and swipe-away events for [RainLiveUpdate].
 */
@AndroidEntryPoint
class RainLiveUpdateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // From a swipe (already gone) or the Dismiss / Hide button (still showing, so remove it)
            ACTION_DISMISSED -> {
                RainLiveUpdate.onDismissed(context, intent.getLongExtra(EXTRA_UNTIL, 0))
                RainLiveUpdate.cancel(context)
            }
            ACTION_ALERT_DISMISSED -> {
                AlertLiveUpdate.onDismissed(
                    context,
                    intent.getStringExtra(EXTRA_ALERT_ID) ?: "",
                    intent.getLongExtra(EXTRA_UNTIL, 0)
                )
                AlertLiveUpdate.cancel(context)
            }
            ACTION_ALERT_CHECK -> {
                val pending = goAsync()
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        RainLiveUpdate.pickLocation(locationRepository.getAllLocations())
                            ?.let { it.copy(weather = weatherRepository.getWeatherByLocationId(it.formattedId)) }
                            ?.let { AlertLiveUpdate.update(context, it) }
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_CHECK -> {
                val refresh = intent.getBooleanExtra(EXTRA_REFRESH, false)
                val pending = goAsync()
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val location = RainLiveUpdate.pickLocation(locationRepository.getAllLocations())?.let {
                            it.copy(weather = weatherRepository.getWeatherByLocationId(it.formattedId))
                        }
                        if (location != null) {
                            // Show what the cached forecast says right away
                            RainLiveUpdate.update(context, location)
                            // Rain is about to enter the window: confirm with a fresh forecast, which
                            // updates the notification again when done
                            if (refresh) WeatherUpdateJob.startNow(context, location)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val NAME = "RainLiveUpdateReceiver"
        private const val ACTION_CHECK = "$ID.$NAME.CHECK"
        private const val ACTION_DISMISSED = "$ID.$NAME.DISMISSED"
        private const val ACTION_ALERT_CHECK = "$ID.$NAME.ALERT_CHECK"
        private const val ACTION_ALERT_DISMISSED = "$ID.$NAME.ALERT_DISMISSED"
        private const val EXTRA_ALERT_ID = "alert_id"
        private const val EXTRA_REFRESH = "refresh"
        private const val EXTRA_UNTIL = "until"

        fun alarmIntent(context: Context, refresh: Boolean): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RainLiveUpdateReceiver::class.java).apply {
                action = ACTION_CHECK
                putExtra(EXTRA_REFRESH, refresh)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun alertAlarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, RainLiveUpdateReceiver::class.java).apply { action = ACTION_ALERT_CHECK },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun alertDismissedIntent(context: Context, alertId: String, until: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                3,
                Intent(context, RainLiveUpdateReceiver::class.java).apply {
                    action = ACTION_ALERT_DISMISSED
                    putExtra(EXTRA_ALERT_ID, alertId)
                    putExtra(EXTRA_UNTIL, until)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        fun dismissedIntent(context: Context, until: Long): PendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, RainLiveUpdateReceiver::class.java).apply {
                action = ACTION_DISMISSED
                putExtra(EXTRA_UNTIL, until)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
