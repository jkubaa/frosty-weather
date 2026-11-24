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

package org.breezyweather.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.breezyweather.remoteviews.RainLiveUpdate
import java.util.Date

/**
 * Debug builds only (callable from adb, which holds DUMP): fakes rain for the Live Update.
 * Int extras: `minutes` until it starts (default 5, 0 = raining now, negative = dismiss),
 * `duration` in minutes (default 20), `rate` in tenths of mm/h (default 30 = 3 mm/h, moderate).
 * String extra `kind`: snow, sleet, hail or thunderstorm (default rain).
 */
class RainLiveTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val minutes = intent.getIntExtra("minutes", 5)
        if (minutes < 0) {
            RainLiveUpdate.stopTest(context)
            return
        }
        val now = System.currentTimeMillis()
        // 0 = started 2 minutes ago, to show the raining stage
        val start = if (minutes == 0) now - 2 * 60_000L else now + minutes * 60_000L
        RainLiveUpdate.startTest(
            context,
            Date(start),
            durationMinutes = intent.getIntExtra("duration", 20),
            mmPerHour = intent.getIntExtra("rate", 30) / 10.0,
            kind = intent.getStringExtra("kind")
        )
    }
}
