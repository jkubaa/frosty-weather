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

package org.breezyweather.remoteviews

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Alert
import breezyweather.domain.weather.model.Weather
import breezyweather.domain.weather.reference.AlertSeverity
import org.breezyweather.R
import org.breezyweather.background.receiver.RainLiveUpdateReceiver
import org.breezyweather.common.extensions.cancelNotification
import org.breezyweather.common.extensions.notificationBuilder
import org.breezyweather.common.extensions.notify
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.domain.settings.ConfigStore
import org.breezyweather.domain.settings.SettingsManager
import java.util.Date
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Ongoing notification for weather warnings (alerts) at the user's location, promoted to a Live
 * Update on Android 16+ (status bar chip, lock screen, ColorOS Live Alerts).
 *
 * - Upcoming: the warning starts within [LEAD]. The pill says "in 2h" (minutes in the last hour).
 * - Active: the pill says "6h left" (minutes in the last hour).
 *
 * The pill spells out the direction: an icon next to a time didn't make clear whether it was
 * counting to the start or the end. The expanded view gives the full start / end times.
 *
 * Only warnings of the chosen severity and above, with an end time. When several apply, the most
 * severe (then the soonest) is shown, with how many more there are.
 *
 * Priority with the rain countdown: an extreme warning always gets the Live Update slot (the rain
 * countdown then stays a normal notification); other warnings give way to a rain countdown.
 */
object AlertLiveUpdate {

    const val CHANNEL_ALERT_LIVE = "alert_live"
    private const val ID_ALERT_LIVE = 3002

    /** How long before a warning starts it shows up */
    private val LEAD = 2.hours

    /** Under this, the pill counts in minutes (and the label is refreshed more often) */
    private val MINUTES_UNDER = 1.hours
    private val TICK_LAST_HOUR = 5.minutes

    // Re-check now and then while showing (the alert list may have changed)
    private val TICK = 15.minutes

    private const val PREFS = "alert_live_update"
    private const val KEY_DISMISSED_ID = "dismissed_id"
    private const val KEY_DISMISSED_UNTIL = "dismissed_until"
    private const val KEY_EXTREME_UNTIL = "extreme_until"
    private const val KEY_TEST_START = "test_start"
    private const val KEY_TEST_HOURS = "test_hours"
    private const val KEY_TEST_TYPE = "test_type"
    private const val KEY_TEST_SEVERITY = "test_severity"

    enum class Type(@DrawableRes val icon: Int, @DrawableRes val activeIcon: Int) {
        WIND(R.drawable.ic_alert_live_wind, R.drawable.ic_alert_live_wind_active),
        RAIN(R.drawable.ic_alert_live_rain, R.drawable.ic_alert_live_rain_active),
        SNOW(R.drawable.ic_alert_live_snow, R.drawable.ic_alert_live_snow_active),
        THUNDER(R.drawable.ic_alert_live_thunder, R.drawable.ic_alert_live_thunder_active),
        HEAT(R.drawable.ic_alert_live_heat, R.drawable.ic_alert_live_heat_active),
        FOG(R.drawable.ic_alert_live_fog, R.drawable.ic_alert_live_fog_active),
        GENERAL(R.drawable.ic_alert_live_general, R.drawable.ic_alert_live_general_active),
    }

    sealed interface State {
        data class Upcoming(val alert: Alert, val others: Int) : State
        data class Active(val alert: Alert, val others: Int) : State

        /** Nothing to show; [nextStart] is when the next qualifying warning starts, if any */
        data class Idle(val nextStart: Date?) : State
    }

    /**
     * Guesses the type from the headline (alerts carry no type). English keywords only, so other
     * languages fall back to the general icon.
     */
    fun typeOf(alert: Alert): Type {
        val text = (alert.headline ?: "").lowercase()
        return when {
            Regex("thunder|lightning").containsMatchIn(text) -> Type.THUNDER
            Regex("snow|ice|icy|blizzard|freez|frost").containsMatchIn(text) -> Type.SNOW
            Regex("flood|rain|downpour").containsMatchIn(text) -> Type.RAIN
            Regex("wind|gale|gust|storm|hurricane|tornado|cyclone").containsMatchIn(text) -> Type.WIND
            Regex("heat|hot|high temperature").containsMatchIn(text) -> Type.HEAT
            Regex("fog|mist|visibility").containsMatchIn(text) -> Type.FOG
            else -> Type.GENERAL
        }
    }

    /** Unknown severity is treated as moderate, so sources without levels still show */
    private val Alert.level: Int
        get() = if (severity == AlertSeverity.UNKNOWN) AlertSeverity.MODERATE.id else severity.id

    fun evaluate(alerts: List<Alert>, now: Date = Date(), minSeverity: AlertSeverity = AlertSeverity.MODERATE): State {
        val candidates = alerts.filter { it.endDate != null && it.endDate!!.after(now) && it.level >= minSeverity.id }
        val active = candidates.filter { it.startDate == null || !it.startDate!!.after(now) }
        val upcoming = candidates.filter {
            it.startDate != null && it.startDate!!.after(now) &&
                it.startDate!!.time - now.time <= LEAD.inWholeMilliseconds
        }
        val showing = active + upcoming
        if (showing.isEmpty()) {
            return State.Idle(candidates.mapNotNull { it.startDate }.filter { it.after(now) }.minOrNull())
        }
        // Most severe first, then whichever matters soonest
        val top = showing.sortedWith(
            compareByDescending<Alert> { it.level }.thenBy { (it.startDate?.takeIf { s -> s.after(now) } ?: it.endDate!!).time }
        ).first()
        val others = showing.size - 1
        return if (top in active) State.Active(top, others) else State.Upcoming(top, others)
    }

    /**
     * Time left until [target], short: whole hours ("2h") from an hour out, else minutes rounded up to
     * 5 ("40m"), so it stays right between refreshes.
     */
    fun remaining(target: Date, now: Date = Date()): String {
        val minutes = ((target.time - now.time) / 60_000.0).coerceAtLeast(0.0)
        return if (minutes >= MINUTES_UNDER.inWholeMinutes) {
            "${Math.round(minutes / 60.0)}h"
        } else {
            "${(Math.ceil(minutes / 5.0) * 5).toInt().coerceAtLeast(5)}m"
        }
    }

    fun update(context: Context, location: Location): Boolean {
        val testWeather = testWeather(context)
        val weather = testWeather ?: location.weather
        val settings = SettingsManager.getInstance(context)
        if (weather == null ||
            (testWeather == null && (!settings.isAlertPushEnabled || !settings.alertLiveEnabled))
        ) {
            cancel(context)
            cancelAlarm(context)
            return false
        }
        return updateWith(context, location, weather)
    }

    private fun updateWith(context: Context, location: Location?, weather: Weather): Boolean {
        val settings = SettingsManager.getInstance(context)
        val minSeverity = if (settings.alertLiveMinSeverity == "severe") AlertSeverity.SEVERE else AlertSeverity.MODERATE
        val now = Date()
        val state = evaluate(weather.alertList, now, minSeverity)
        val prefs = prefs(context)

        val showing = when (state) {
            is State.Upcoming, is State.Active -> {
                val alert = (state as? State.Upcoming)?.alert ?: (state as State.Active).alert
                val muted = prefs.getString(KEY_DISMISSED_ID, null) == alert.alertId &&
                    now.time < prefs.getLong(KEY_DISMISSED_UNTIL, 0)
                if (muted) {
                    cancel(context)
                    false
                } else {
                    show(context, location, state, now)
                    true
                }
            }
            is State.Idle -> {
                cancel(context)
                false
            }
        }
        scheduleNext(context, state, now)
        return showing
    }

    private fun show(context: Context, location: Location?, state: State, now: Date) {
        val (alert, others, active) = when (state) {
            is State.Upcoming -> Triple(state.alert, state.others, false)
            is State.Active -> Triple(state.alert, state.others, true)
            is State.Idle -> return
        }
        val type = typeOf(alert)
        val start = alert.startDate ?: now
        val end = alert.endDate!!
        val target = if (active) end else start
        val extreme = alert.severity == AlertSeverity.EXTREME

        val title = alert.headline?.ifEmpty { null } ?: context.getString(R.string.alert)
        var text = if (active) {
            context.getString(R.string.notification_alert_live_active, formatTime(context, end, now))
        } else {
            context.getString(
                R.string.notification_alert_live_upcoming,
                formatTime(context, start, now),
                formatTime(context, end, now)
            )
        }
        if (others > 0) text += " · " + context.getString(R.string.notification_alert_live_more, others)

        // Extreme warnings always take the Live Update slot; others give way to a rain countdown
        val promote = extreme || !RainLiveUpdate.isShowing(context)
        prefs(context).edit().putLong(KEY_EXTREME_UNTIL, if (extreme) end.time else 0L).apply()

        val notification = context.notificationBuilder(CHANNEL_ALERT_LIVE) {
            // The pill shows only this icon next to the time: the type before, the type inside a
            // warning triangle while it's in effect
            setSmallIcon(if (active) type.activeIcon else type.icon)
            iconBitmap(context, type.icon, alert.color)?.let { setLargeIcon(it) }
            setContentTitle(title)
            setContentText(text)
            location?.let { setSubText(it.getPlace(context)) }
            setStyle(NotificationCompat.BigTextStyle().bigText(text))
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setCategory(NotificationCompat.CATEGORY_STATUS)
            setOngoing(true)
            setOnlyAlertOnce(true)
            // Silent: the warning itself already got its own notification when it was issued
            setSilent(true)
            // The pill's text: which way it's counting, in words
            setShowWhen(false)
            setShortCriticalText(
                context.getString(
                    if (active) R.string.notification_alert_live_pill_left else R.string.notification_alert_live_pill_in,
                    remaining(target, now)
                )
            )
            setTimeoutAfter((end.time - now.time).coerceAtLeast(0))
            setRequestPromotedOngoing(promote)
            setContentIntent(
                PendingIntent.getActivity(
                    context,
                    ID_ALERT_LIVE,
                    IntentHelper.buildMainActivityShowAlertsIntent(location, alert.alertId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            addAction(
                0,
                context.getString(R.string.notification_rain_live_action_details),
                PendingIntent.getActivity(
                    context,
                    ID_ALERT_LIVE + 1,
                    IntentHelper.buildMainActivityShowAlertsIntent(location, alert.alertId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            // Swiping it away, or the Hide button, hides this warning until it ends. An upgraded or new
            // warning has another id, so it still shows.
            val hide = RainLiveUpdateReceiver.alertDismissedIntent(context, alert.alertId, end.time)
            addAction(0, context.getString(R.string.notification_alert_live_action_hide), hide)
            setDeleteIntent(hide)
        }.build()

        context.notify(ID_ALERT_LIVE, notification)
    }

    /** "6:00 PM", or "Tue 6:00 AM" when not today */
    private fun formatTime(context: Context, date: Date, now: Date): String {
        var flags = DateUtils.FORMAT_SHOW_TIME
        if (!DateUtils.isToday(date.time) || date.time - now.time > 20.hours.inWholeMilliseconds) {
            flags = flags or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
        }
        return DateUtils.formatDateTime(context, date.time, flags)
    }

    /** The glyph in the warning's colour, for the expanded view */
    private fun iconBitmap(context: Context, @DrawableRes res: Int, color: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, res)?.mutate() ?: return null
        DrawableCompat.setTint(drawable, color)
        val size = (48 * context.resources.displayMetrics.density).toInt()
        return drawable.toBitmap(size, size)
    }

    // Alarms: start / end, and refreshes so the pill's "in 2h" / "6h left" stays right

    private fun scheduleNext(context: Context, state: State, now: Date) {
        val nowMs = now.time
        val at = when (state) {
            is State.Upcoming -> nextChange(state.alert.startDate!!.time, nowMs)
            is State.Active -> nextChange(state.alert.endDate!!.time, nowMs)
            is State.Idle -> {
                val next = state.nextStart ?: run {
                    cancelAlarm(context)
                    return
                }
                maxOf(next.time - LEAD.inWholeMilliseconds, nowMs + 1.minutes.inWholeMilliseconds)
            }
        }
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pending = RainLiveUpdateReceiver.alertAlarmIntent(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /** The start / end, or the next label refresh: every 15 min, every 5 in the last hour */
    private fun nextChange(target: Long, nowMs: Long): Long {
        val lastHourFrom = target - MINUTES_UNDER.inWholeMilliseconds
        val step = if (nowMs >= lastHourFrom) TICK_LAST_HOUR else TICK
        return listOf(target, nowMs + step.inWholeMilliseconds, lastHourFrom).filter { it > nowMs }.min()
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(RainLiveUpdateReceiver.alertAlarmIntent(context))
    }

    fun onDismissed(context: Context, alertId: String, until: Long) {
        prefs(context).edit()
            .putString(KEY_DISMISSED_ID, alertId)
            .putLong(KEY_DISMISSED_UNTIL, until)
            .putLong(KEY_EXTREME_UNTIL, 0L)
            .apply()
    }

    /** Whether an extreme warning holds the Live Update slot, so the rain countdown should give way */
    fun isExtremeShowing(context: Context) = System.currentTimeMillis() < prefs(context).getLong(KEY_EXTREME_UNTIL, 0)

    fun cancel(context: Context) {
        context.cancelNotification(ID_ALERT_LIVE)
        prefs(context).edit().putLong(KEY_EXTREME_UNTIL, 0L).apply()
    }

    // Debug test

    /**
     * Debug helper: fakes a warning of [type] ("wind", "rain", "snow", "thunderstorm", "heat", "fog")
     * starting at [start] and lasting [hours], kept until it ends so alarms move it like a real one.
     */
    fun startTest(context: Context, start: Date, hours: Int, type: String?, severity: String?) {
        prefs(context).edit()
            .putLong(KEY_TEST_START, start.time)
            .putInt(KEY_TEST_HOURS, hours)
            .putString(KEY_TEST_TYPE, type)
            .putString(KEY_TEST_SEVERITY, severity)
            .remove(KEY_DISMISSED_ID)
            .apply()
        updateWith(context, null, testWeather(context) ?: return)
    }

    fun stopTest(context: Context) {
        prefs(context).edit().remove(KEY_TEST_START).apply()
        cancel(context)
        cancelAlarm(context)
    }

    private fun testWeather(context: Context): Weather? {
        val prefs = prefs(context)
        val start = prefs.getLong(KEY_TEST_START, 0)
        if (start == 0L) return null
        val end = start + prefs.getInt(KEY_TEST_HOURS, 6).hours.inWholeMilliseconds
        if (System.currentTimeMillis() > end) {
            prefs.edit().remove(KEY_TEST_START).apply()
            return null
        }
        val severity = when (prefs.getString(KEY_TEST_SEVERITY, null)) {
            "extreme" -> AlertSeverity.EXTREME
            "moderate" -> AlertSeverity.MODERATE
            else -> AlertSeverity.SEVERE
        }
        val colour = when (severity) {
            AlertSeverity.EXTREME -> "Red"
            AlertSeverity.SEVERE -> "Amber"
            else -> "Yellow"
        }
        val headline = when (prefs.getString(KEY_TEST_TYPE, null)) {
            "rain" -> "$colour warning of rain"
            "snow" -> "$colour warning of snow and ice"
            "thunderstorm" -> "$colour warning of thunderstorms"
            "heat" -> "$colour warning of extreme heat"
            "fog" -> "$colour warning of fog"
            "general" -> "$colour weather warning"
            else -> "$colour warning of wind"
        }
        return Weather(
            alertList = listOf(
                Alert(
                    alertId = "debug-test-alert",
                    startDate = Date(start),
                    endDate = Date(end),
                    headline = headline,
                    severity = severity,
                    color = Alert.colorFromSeverity(severity)
                )
            )
        )
    }

    private fun prefs(context: Context) = ConfigStore(context, PREFS)
}
