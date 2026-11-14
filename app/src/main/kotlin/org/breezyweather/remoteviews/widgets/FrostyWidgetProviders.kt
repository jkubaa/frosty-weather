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

package org.breezyweather.remoteviews.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import breezyweather.data.location.LocationRepository
import breezyweather.data.weather.WeatherRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Draws the widgets from the stored weather of the first location when the launcher asks
 * (placed, resized, reboot). Weather refreshes redraw them through [FrostyWidgets.updateAll].
 */
abstract class FrostyWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        redraw(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Resized: the sky, hours and days are laid out for the size
        redraw(context)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun redraw(context: Context) {
        val pending = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val location = locationRepository.getFirstLocation(withParameters = false)
                FrostyWidgets.updateAll(
                    context,
                    location?.copy(
                        weather = weatherRepository.getWeatherByLocationId(
                            location.formattedId,
                            withDaily = true,
                            withHourly = true,
                            withMinutely = false,
                            withAlerts = false,
                            withNormals = false
                        )
                    )
                )
            } finally {
                pending.finish()
            }
        }
    }
}

@AndroidEntryPoint
class FrostySmallWidgetProvider : FrostyWidgetProvider()

@AndroidEntryPoint
class FrostyMediumWidgetProvider : FrostyWidgetProvider()

@AndroidEntryPoint
class FrostyLargeWidgetProvider : FrostyWidgetProvider()
