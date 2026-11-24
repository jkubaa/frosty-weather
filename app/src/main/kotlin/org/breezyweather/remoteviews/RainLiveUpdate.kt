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
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Hourly
import breezyweather.domain.weather.model.Minutely
import breezyweather.domain.weather.model.Weather
import breezyweather.domain.weather.reference.WeatherCode
import org.breezyweather.R
import org.breezyweather.background.receiver.RainLiveUpdateReceiver
import org.breezyweather.common.extensions.cancelNotification
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.common.extensions.notificationBuilder
import org.breezyweather.common.extensions.notify
import org.breezyweather.common.options.appearance.DetailScreen
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.domain.settings.ConfigStore
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.details.DetailsActivity
import org.breezyweather.unit.precipitation.Precipitation.Companion.millimeters
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Ongoing notification about rain in the next minutes, built from the minutely (nowcast) forecast.
 *
 * - Upcoming: rain starts within [WINDOW] → countdown to the start.
 * - Raining: only for short showers (ending within [SHORT_SHOWER]) → silent countdown to the end.
 *   Longer rain just removes the notification, so it never hangs around for hours.
 *
 * On Android 16+ it asks to be promoted to a Live Update (status bar chip / lock screen, and OEM
 * surfaces built on it such as ColorOS Live Alerts).
 *
 * Background refreshes are usually an hour or more apart, so after each evaluation an alarm is set
 * for the next moment that matters (15 min before forecast rain, the start, the end, or a periodic
 * one-minute refresh of the pill's label while showing). The alarm before rain also asks for a fresh forecast to confirm it.
 */
object RainLiveUpdate {

    const val CHANNEL_RAIN_LIVE = "rain_live"
    private const val ID_RAIN_LIVE = 3001

    // Default lead time; the user can pick another in settings
    private val WINDOW = 15.minutes
    private val SHORT_SHOWER = 30.minutes

    // Refresh the pill's "in 8m" / "8m left" every minute while showing
    private val TICK = 1.minutes
    private val LINGER = 2.minutes

    // Rain rate thresholds (mm/h)
    private const val LIGHT_MAX = 2.5
    private const val MODERATE_MAX = 7.6

    private const val PREFS = "rain_live_update"
    private const val KEY_DISMISSED_UNTIL = "dismissed_until"
    private const val KEY_SHOWING_UNTIL = "showing_until"
    private const val KEY_TEST_START = "test_start"
    private const val KEY_TEST_DURATION = "test_duration"
    private const val KEY_TEST_RATE = "test_rate"
    private const val KEY_TEST_KIND = "test_kind"

    private enum class Kind { RAIN, SNOW, SLEET, HAIL, THUNDER }
    private enum class Level { LIGHT, MODERATE, HEAVY }

    sealed interface State {
        /** Rain starts at [start] (within the window) */
        data class Upcoming(val start: Date, val run: RainRun) : State

        /** Short shower going on now */
        data class Raining(val run: RainRun) : State

        /** Nothing to show now; rain is forecast later at [start] (null = none in the data) */
        data class Idle(val start: Date?) : State
    }

    /**
     * A continuous run of rainy minutely slots.
     * @param reachesDataEnd the forecast ends while still raining, so [end] is only a lower bound
     */
    data class RainRun(val slots: List<Minutely>, val reachesDataEnd: Boolean) {
        val start: Date get() = slots.first().date
        val end: Date get() = slots.last().endingDate
        val maxIntensityMm: Double get() = slots.maxOf { it.precipitationIntensity?.inMillimeters ?: 0.0 }
    }

    /**
     * @param minMm rain rate (mm/h) a slot needs to count; above 0 skips drizzle
     */
    private fun Minutely.isRainy(minMm: Double) =
        (dbz ?: 0) > 0 && (minMm <= 0 || (precipitationIntensity?.inMillimeters ?: 0.0) >= minMm)

    private fun runFrom(minutely: List<Minutely>, index: Int, minMm: Double): RainRun {
        var last = index
        while (last + 1 < minutely.size && minutely[last + 1].isRainy(minMm)) last++
        return RainRun(minutely.subList(index, last + 1), reachesDataEnd = last == minutely.lastIndex)
    }

    /**
     * @param window how long before rain the countdown starts
     * @param skipLight ignore light rain (drizzle)
     */
    fun evaluate(
        weather: Weather,
        now: Date = Date(),
        window: Duration = WINDOW,
        skipLight: Boolean = false,
    ): State {
        val minMm = if (skipLight) LIGHT_MAX else 0.0
        val minutely = weather.minutelyForecast.sortedBy { it.date }
        val currentIndex = minutely.indexOfFirst { !now.before(it.date) && now.before(it.endingDate) }

        if (currentIndex >= 0 && minutely[currentIndex].isRainy(minMm)) {
            // Walk back to the start of this run, so intensity covers the whole shower
            var first = currentIndex
            while (first > 0 && minutely[first - 1].isRainy(minMm)) first--
            val run = runFrom(minutely, first, minMm)
            val isShort = !run.reachesDataEnd && run.end.time - now.time <= SHORT_SHOWER.inWholeMilliseconds
            return if (isShort) State.Raining(run) else State.Idle(null)
        }

        val nextIndex = minutely.indexOfFirst { it.isRainy(minMm) && it.date.after(now) }
        if (nextIndex < 0) return State.Idle(null)
        val run = runFrom(minutely, nextIndex, minMm)
        return if (run.start.time - now.time <= window.inWholeMilliseconds) {
            State.Upcoming(run.start, run)
        } else {
            State.Idle(run.start)
        }
    }

    /**
     * The location rain is tracked for: where the user is (the current-position location) when they
     * have one, otherwise their first location.
     */
    fun pickLocation(locations: List<Location>): Location? =
        locations.firstOrNull { it.isCurrentPosition } ?: locations.firstOrNull()

    /**
     * Shows, refreshes or removes the notification for [location] and schedules the next check.
     * @return true if the notification is showing
     */
    fun update(context: Context, location: Location): Boolean {
        val testWeather = testWeather(context)
        val weather = testWeather ?: location.weather
        if (weather == null ||
            (testWeather == null && !SettingsManager.getInstance(context).isPrecipitationPushEnabled)
        ) {
            cancel(context)
            cancelAlarm(context)
            return false
        }
        return updateWith(context, location, weather)
    }

    private fun updateWith(context: Context, location: Location?, weather: Weather): Boolean {
        val settings = SettingsManager.getInstance(context)
        val window = settings.rainLiveLeadMinutes.minutes
        val now = Date()
        var state = evaluate(weather, now, window, settings.rainLiveSkipLight)

        // Types the user turned off are treated as no rain
        val run = (state as? State.Upcoming)?.run ?: (state as? State.Raining)?.run
        if (run != null && kindAt(weather, run.start).typeKey !in settings.rainLiveTypes) {
            state = State.Idle(null)
        }

        val dismissedUntil = prefs(context).getLong(KEY_DISMISSED_UNTIL, 0)
        val showing = when (state) {
            is State.Upcoming, is State.Raining -> {
                if (now.time < dismissedUntil) {
                    cancel(context)
                    false
                } else {
                    show(context, location, weather, state, now, inQuietHours(settings, now))
                    true
                }
            }
            is State.Idle -> {
                cancel(context)
                false
            }
        }

        scheduleNext(context, state, now, window)
        return showing
    }

    private val Kind.typeKey: String
        get() = when (this) {
            Kind.RAIN -> "rain"
            Kind.SNOW -> "snow"
            Kind.SLEET, Kind.HAIL -> "sleet_hail"
            Kind.THUNDER -> "thunderstorm"
        }

    /** Quiet hours may cross midnight (e.g. 22:00 to 07:00) */
    private fun inQuietHours(settings: SettingsManager, now: Date): Boolean {
        if (!settings.rainLiveQuietHours) return false
        fun minutesOf(hhmm: String) = hhmm.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
        val start = minutesOf(settings.rainLiveQuietStart)
        val end = minutesOf(settings.rainLiveQuietEnd)
        val cal = Calendar.getInstance().apply { time = now }
        val current = cal[Calendar.HOUR_OF_DAY] * 60 + cal[Calendar.MINUTE]
        return if (start <= end) current in start until end else current >= start || current < end
    }

    /**
     * Debug helper: fakes rain from [start] for [durationMinutes] at a [mmPerHour] rate. It is kept
     * until that rain is over, so the minute ticks keep moving it like real rain.
     */
    fun startTest(context: Context, start: Date, durationMinutes: Int, mmPerHour: Double, kind: String? = null) {
        prefs(context).edit()
            .putLong(KEY_TEST_START, start.time)
            .putInt(KEY_TEST_DURATION, durationMinutes)
            .putFloat(KEY_TEST_RATE, mmPerHour.toFloat())
            .putString(KEY_TEST_KIND, kind)
            .remove(KEY_DISMISSED_UNTIL)
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
        val duration = prefs.getInt(KEY_TEST_DURATION, 20)
        if (System.currentTimeMillis() > start + (duration + 5).minutes.inWholeMilliseconds) {
            prefs.edit().remove(KEY_TEST_START).apply()
            return null
        }
        val rate = prefs.getFloat(KEY_TEST_RATE, 3f).toDouble()
        // Dry before and after, like a real nowcast
        val first = start - 30.minutes.inWholeMilliseconds
        // Optional weather type (a WeatherCode id such as "snow" or "thunderstorm") for the icons
        val code = WeatherCode.getInstance(prefs.getString(KEY_TEST_KIND, null))
        return Weather(
            hourlyForecast = (0 until 3).map {
                Hourly(Date(first + it.hours.inWholeMilliseconds), weatherCode = code)
            },
            minutelyForecast = (0 until duration + 60 step 5).map {
                val t = first + it.minutes.inWholeMilliseconds
                val rainy = t >= start && t < start + duration.minutes.inWholeMilliseconds
                Minutely(Date(t), 5, (if (rainy) rate else 0.0).millimeters)
            }
        )
    }

    private fun show(
        context: Context,
        location: Location?,
        weather: Weather?,
        state: State,
        now: Date,
        quiet: Boolean,
    ) {
        val run = when (state) {
            is State.Upcoming -> state.run
            is State.Raining -> state.run
            is State.Idle -> return
        }
        val kind = kindAt(weather, run.start)
        val level = levelOf(run.maxIntensityMm)
        val kindLevel = kindLevelText(context, kind, level)
        val iconRes = weatherIcon(kind, level)
        fun time(date: Date) = location?.let { date.getFormattedTime(it, context, context.is12Hour) }
            ?: android.text.format.DateFormat.getTimeFormat(context).format(date)
        val durationMin = ((run.end.time - run.start.time) / 60_000.0).roundToInt()

        val title: String
        val text: String
        val countdownTo: Date
        when (state) {
            is State.Upcoming -> {
                title = context.getString(R.string.notification_rain_live_title_soon, kindLevel)
                text = if (run.reachesDataEnd) {
                    context.getString(R.string.notification_rain_live_desc_long, time(run.start), durationMin)
                } else {
                    context.getString(
                        R.string.notification_rain_live_desc_until,
                        time(run.start),
                        durationMin,
                        time(run.end)
                    )
                }
                countdownTo = run.start
            }
            else -> {
                title = context.getString(R.string.notification_rain_live_title_now, kindLevel)
                text = context.getString(R.string.notification_rain_live_desc_stops, time(run.end))
                countdownTo = run.end
            }
        }

        val notification = context.notificationBuilder(CHANNEL_RAIN_LIVE) {
            // The small icon is all the collapsed capsule / status bar chip shows next to the
            // countdown: the weather before it starts, the crossed-out drop / flake while counting
            // down to the end. The coloured weather icon is the large icon in the expanded view.
            setSmallIcon(
                when {
                    state is State.Upcoming -> iconRes
                    kind == Kind.SNOW -> R.drawable.ic_live_flake_off
                    else -> R.drawable.ic_live_drop_off
                }
            )
            iconBitmap(context, iconRes)?.let { setLargeIcon(it) }
            setContentTitle(title)
            setContentText(text)
            location?.let { setSubText(it.getPlace(context)) }
            setStyle(NotificationCompat.BigTextStyle().bigText(text))
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setCategory(NotificationCompat.CATEGORY_STATUS)
            setOngoing(true)
            setOnlyAlertOnce(true)
            // Only the "starting soon" stage may alert, and not during quiet hours
            setSilent(state is State.Raining || quiet)
            // The pill's text says which way it's counting, in words (a bare countdown next to an
            // icon didn't make clear whether rain was starting or stopping)
            setShowWhen(false)
            setShortCriticalText(
                context.getString(
                    if (state is State.Upcoming) {
                        R.string.notification_alert_live_pill_in
                    } else {
                        R.string.notification_alert_live_pill_left
                    },
                    "${minutesUntil(countdownTo, now)}m"
                )
            )
            setTimeoutAfter((run.end.time - now.time + LINGER.inWholeMilliseconds).coerceAtLeast(0))
            // An extreme weather warning takes the Live Update slot; rain stays a normal notification
            setRequestPromotedOngoing(!AlertLiveUpdate.isExtremeShowing(context))
            setContentIntent(
                PendingIntent.getActivity(
                    context,
                    ID_RAIN_LIVE,
                    IntentHelper.buildMainActivityIntent(location),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            // Text-only button: the weather icon is already shown once
            addAction(
                0,
                context.getString(R.string.notification_rain_live_action_details),
                PendingIntent.getActivity(
                    context,
                    ID_RAIN_LIVE + 1,
                    Intent(context, DetailsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(DetailsActivity.KEY_FORMATTED_LOCATION_ID, location?.formattedId)
                        putExtra(DetailsActivity.KEY_CURRENT_PAGE, DetailScreen.TAG_PRECIPITATION.id)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            // Swiping it away, or the Dismiss button (the only way once it sits in a Live Update capsule),
            // mutes it until this rain is over
            val dismiss = RainLiveUpdateReceiver.dismissedIntent(context, run.end.time + LINGER.inWholeMilliseconds)
            addAction(0, context.getString(R.string.notification_live_action_dismiss), dismiss)
            setDeleteIntent(dismiss)
        }.build()

        context.notify(ID_RAIN_LIVE, notification)
        prefs(context).edit().putLong(KEY_SHOWING_UNTIL, run.end.time + LINGER.inWholeMilliseconds).apply()
    }

    /** Whole minutes until [target], rounded up, at least 1 */
    fun minutesUntil(target: Date, now: Date = Date()): Int =
        Math.ceil((target.time - now.time) / 60_000.0).toInt().coerceAtLeast(1)

    /** Whether the countdown is up, so lesser weather warnings give it the Live Update slot */
    fun isShowing(context: Context) = System.currentTimeMillis() < prefs(context).getLong(KEY_SHOWING_UNTIL, 0)

    private fun levelOf(mmPerHour: Double) = when {
        mmPerHour < LIGHT_MAX -> Level.LIGHT
        mmPerHour < MODERATE_MAX -> Level.MODERATE
        else -> Level.HEAVY
    }

    /** Minutely data has no type, so use the hourly weather code around the start. */
    private fun kindAt(weather: Weather?, date: Date): Kind {
        val hourly = weather?.hourlyForecast
            ?.lastOrNull { !it.date.after(date) && date.time - it.date.time < 60.minutes.inWholeMilliseconds }
        return when (hourly?.weatherCode) {
            WeatherCode.SNOW -> Kind.SNOW
            WeatherCode.SLEET -> Kind.SLEET
            WeatherCode.HAIL -> Kind.HAIL
            WeatherCode.THUNDER, WeatherCode.THUNDERSTORM -> Kind.THUNDER
            else -> Kind.RAIN
        }
    }

    private fun weatherIcon(kind: Kind, level: Level): Int = when (kind) {
        Kind.RAIN -> when (level) {
            Level.LIGHT -> R.drawable.ic_live_rain_light
            Level.MODERATE -> R.drawable.ic_live_rain
            Level.HEAVY -> R.drawable.ic_live_rain_heavy
        }
        Kind.SNOW -> when (level) {
            Level.LIGHT -> R.drawable.ic_live_snow_light
            Level.MODERATE -> R.drawable.ic_live_snow
            Level.HEAVY -> R.drawable.ic_live_snow_heavy
        }
        Kind.SLEET -> R.drawable.ic_live_sleet
        Kind.HAIL -> R.drawable.ic_live_hail
        Kind.THUNDER -> R.drawable.ic_live_thunder
    }

    private fun iconBitmap(context: Context, @DrawableRes res: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, res) ?: return null
        val size = (48 * context.resources.displayMetrics.density).toInt()
        return drawable.toBitmap(size, size)
    }

    private fun kindLevelText(context: Context, kind: Kind, level: Level): String = context.getString(
        when (kind) {
            Kind.RAIN -> when (level) {
                Level.LIGHT -> R.string.notification_rain_live_light_rain
                Level.MODERATE -> R.string.notification_rain_live_rain
                Level.HEAVY -> R.string.notification_rain_live_heavy_rain
            }
            Kind.SNOW -> when (level) {
                Level.LIGHT -> R.string.notification_rain_live_light_snow
                Level.MODERATE -> R.string.notification_rain_live_snow
                Level.HEAVY -> R.string.notification_rain_live_heavy_snow
            }
            Kind.SLEET -> R.string.notification_rain_live_sleet
            Kind.HAIL -> R.string.notification_rain_live_hail
            Kind.THUNDER -> R.string.notification_rain_live_thunderstorm
        }
    )

    // Alarms

    private fun scheduleNext(context: Context, state: State, now: Date, window: Duration) {
        val nowMs = now.time
        val tick = nowMs + TICK.inWholeMilliseconds
        val (at, refresh) = when (state) {
            is State.Upcoming -> minOf(state.start.time, tick) to false
            is State.Raining -> minOf(state.run.end.time, tick) to false
            is State.Idle -> {
                val start = state.start ?: run {
                    cancelAlarm(context)
                    return
                }
                // Wake up as the rain enters the window, and get a fresh forecast to confirm it
                maxOf(start.time - window.inWholeMilliseconds, nowMs + 1.minutes.inWholeMilliseconds) to true
            }
        }
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pending = RainLiveUpdateReceiver.alarmIntent(context, refresh)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(RainLiveUpdateReceiver.alarmIntent(context, false))
    }

    fun onDismissed(context: Context, until: Long) {
        prefs(context).edit().putLong(KEY_DISMISSED_UNTIL, until).apply()
    }

    private fun prefs(context: Context) = ConfigStore(context, PREFS)

    fun cancel(context: Context) {
        context.cancelNotification(ID_RAIN_LIVE)
        prefs(context).edit().putLong(KEY_SHOWING_UNTIL, 0L).apply()
    }
}
