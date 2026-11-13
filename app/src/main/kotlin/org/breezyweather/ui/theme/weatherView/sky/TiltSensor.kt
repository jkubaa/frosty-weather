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

package org.breezyweather.ui.theme.weatherView.sky

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * How the phone is tilted, for the sky's parallax: -1..1 on each axis, relative to how it has been
 * held over the last couple of seconds. So whatever the usual angle, the sky rests centered, and a
 * turn of the wrist shifts its layers by their depth before easing back.
 */
internal class TiltSensor(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Read at draw time only */
    var value by mutableStateOf(Offset.Zero)
        private set

    private var listening = false
    private var restX = Float.NaN
    private var restY = 0f

    fun start() {
        if (listening || sensor == null) return
        listening = manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME) == true
    }

    fun stop() {
        if (!listening) return
        manager?.unregisterListener(this)
        listening = false
        restX = Float.NaN
        value = Offset.Zero
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Sideways: gravity along the screen's x axis; forwards and back: along the axis out of the screen
        val gx = event.values[0]
        val gz = event.values[2]
        if (restX.isNaN()) {
            restX = gx
            restY = gz
        }
        // The resting angle follows slowly, so the sky re-centers on however the phone is held
        restX += (gx - restX) * 0.015f
        restY += (gz - restY) * 0.015f
        val target = Offset(
            (-(gx - restX) / 2.5f).coerceIn(-1f, 1f),
            ((gz - restY) / 2.5f).coerceIn(-1f, 1f)
        )
        // Smoothed, so sensor jitter never shows
        value += (target - value) * 0.2f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
