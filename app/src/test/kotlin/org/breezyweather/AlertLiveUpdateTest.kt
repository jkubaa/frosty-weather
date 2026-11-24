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

import breezyweather.domain.weather.model.Alert
import breezyweather.domain.weather.reference.AlertSeverity
import org.breezyweather.remoteviews.AlertLiveUpdate
import org.breezyweather.remoteviews.AlertLiveUpdate.State
import org.breezyweather.remoteviews.AlertLiveUpdate.Type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class AlertLiveUpdateTest {

    private val base = 1_800_000_000_000L
    private fun at(min: Int) = Date(base + min * 60_000L)

    private fun alert(
        id: String,
        startMin: Int?,
        endMin: Int?,
        severity: AlertSeverity = AlertSeverity.SEVERE,
        headline: String = "Amber warning of wind",
    ) = Alert(
        alertId = id,
        startDate = startMin?.let { at(it) },
        endDate = endMin?.let { at(it) },
        headline = headline,
        severity = severity,
        color = 0
    )

    @Test
    fun `warning within two hours is upcoming, later is idle with its start`() {
        assertTrue(AlertLiveUpdate.evaluate(listOf(alert("a", 100, 400)), at(0)) is State.Upcoming)
        assertEquals(State.Idle(at(200)), AlertLiveUpdate.evaluate(listOf(alert("a", 200, 400)), at(0)))
    }

    @Test
    fun `started warning is active until it ends`() {
        assertTrue(AlertLiveUpdate.evaluate(listOf(alert("a", -30, 300)), at(0)) is State.Active)
        assertEquals(State.Idle(null), AlertLiveUpdate.evaluate(listOf(alert("a", -300, -10)), at(0)))
    }

    @Test
    fun `warnings without an end or below the threshold are skipped`() {
        assertEquals(State.Idle(null), AlertLiveUpdate.evaluate(listOf(alert("a", -30, null)), at(0)))
        val minor = alert("a", -30, 300, AlertSeverity.MINOR)
        assertEquals(State.Idle(null), AlertLiveUpdate.evaluate(listOf(minor), at(0)))
        val moderate = alert("b", -30, 300, AlertSeverity.MODERATE)
        assertTrue(AlertLiveUpdate.evaluate(listOf(moderate), at(0)) is State.Active)
        assertEquals(
            State.Idle(null),
            AlertLiveUpdate.evaluate(listOf(moderate), at(0), minSeverity = AlertSeverity.SEVERE)
        )
    }

    @Test
    fun `most severe wins and the rest are counted`() {
        val state = AlertLiveUpdate.evaluate(
            listOf(
                alert("moderate", -30, 300, AlertSeverity.MODERATE),
                alert("extreme", 60, 300, AlertSeverity.EXTREME),
                alert("severe", -10, 300, AlertSeverity.SEVERE)
            ),
            at(0)
        )
        state as State.Upcoming
        assertEquals("extreme", state.alert.alertId)
        assertEquals(2, state.others)
    }

    @Test
    fun `remaining is hours from an hour out, then minutes rounded up to 5`() {
        assertEquals("2h", AlertLiveUpdate.remaining(at(100), at(0)))
        assertEquals("6h", AlertLiveUpdate.remaining(at(370), at(0)))
        assertEquals("1h", AlertLiveUpdate.remaining(at(60), at(0)))
        assertEquals("40m", AlertLiveUpdate.remaining(at(37), at(0)))
        assertEquals("5m", AlertLiveUpdate.remaining(at(1), at(0)))
    }

    @Test
    fun `type comes from the headline`() {
        fun type(headline: String) = AlertLiveUpdate.typeOf(alert("a", 0, 60, headline = headline))
        assertEquals(Type.WIND, type("Yellow warning of wind"))
        assertEquals(Type.THUNDER, type("Amber warning of thunderstorms"))
        assertEquals(Type.SNOW, type("Yellow warning of snow and ice"))
        assertEquals(Type.RAIN, type("Flood warning"))
        assertEquals(Type.HEAT, type("Red warning of extreme heat"))
        assertEquals(Type.FOG, type("Yellow warning of fog"))
        assertEquals(Type.GENERAL, type("Avertissement météo"))
    }
}
