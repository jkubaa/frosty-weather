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

package org.breezyweather

import breezyweather.domain.weather.model.Minutely
import breezyweather.domain.weather.model.Weather
import org.breezyweather.remoteviews.RainLiveUpdate
import org.breezyweather.remoteviews.RainLiveUpdate.State
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class RainLiveUpdateTest {

    private val base = 1_800_000_000_000L
    private fun at(min: Int) = Date(base + min * 60_000L)

    /** 5-min slots from minute 0; [rainy] lists the rainy slot indices */
    private fun weather(slots: Int, vararg rainy: Int) = Weather(
        minutelyForecast = (0 until slots).map {
            Minutely(at(it * 5), 5, (if (it in rainy) 3.0 else 0.0).millimeters)
        }
    )

    @Test
    fun `no rain is idle without alarm`() {
        assertEquals(State.Idle(null), RainLiveUpdate.evaluate(weather(12), at(1)))
    }

    @Test
    fun `rain within 15 min counts down`() {
        val state = RainLiveUpdate.evaluate(weather(12, 3, 4), at(1))
        assertTrue(state is State.Upcoming)
        state as State.Upcoming
        assertEquals(at(15), state.start)
        assertEquals(at(25), state.run.end)
    }

    @Test
    fun `rain later is idle with its start for the alarm`() {
        assertEquals(State.Idle(at(40)), RainLiveUpdate.evaluate(weather(12, 8, 9), at(1)))
    }

    @Test
    fun `short shower shows raining stage`() {
        val state = RainLiveUpdate.evaluate(weather(12, 0, 1, 2), at(6))
        assertTrue(state is State.Raining)
        assertEquals(at(15), (state as State.Raining).run.end)
    }

    @Test
    fun `long rain is dropped`() {
        // Raining now until minute 45: more than 30 min left
        assertEquals(State.Idle(null), RainLiveUpdate.evaluate(weather(12, 0, 1, 2, 3, 4, 5, 6, 7, 8), at(1)))
        // Rain until the end of the data: unknown end, also dropped
        assertEquals(State.Idle(null), RainLiveUpdate.evaluate(weather(4, 0, 1, 2, 3), at(1)))
    }
}
