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
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Daily
import org.breezyweather.R
import org.breezyweather.common.extensions.getHour
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.domain.weather.model.getWeek
import org.breezyweather.remoteviews.presenters.AbstractRemoteViewsPresenter
import org.breezyweather.ui.main.adapters.main.holder.DailyViewHolder
import org.breezyweather.ui.main.adapters.main.holder.GlassForecastFormat
import org.breezyweather.ui.theme.resource.providers.FrostedWeatherIcons
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import org.breezyweather.ui.theme.weatherView.materialWeatherView.SkyPalette
import org.breezyweather.unit.temperature.TemperatureUnit
import kotlin.math.roundToInt

/**
 * Home screen widgets in the iOS Weather style: the location's sky gradient (the same one as the
 * home screen and the locations list), white text, frosted condition icons.
 *
 * - Small (2×2): place, temperature, icon, condition, high and low.
 * - Medium (4×2): the same header, then the next hours.
 * - Large (4×4): the medium widget plus the next days with temperature range bars.
 */
object FrostyWidgets {

    enum class Size(
        val provider: Class<*>,
        val layout: Int,
        val requestCode: Int,
        val defaultWidthDp: Int,
        val defaultHeightDp: Int,
    ) {
        SMALL(FrostySmallWidgetProvider::class.java, R.layout.widget_frosty_small, 141, 170, 170),
        MEDIUM(FrostyMediumWidgetProvider::class.java, R.layout.widget_frosty_medium, 142, 360, 170),
        LARGE(FrostyLargeWidgetProvider::class.java, R.layout.widget_frosty_large, 143, 360, 380),
    }

    /** Redraws every placed widget with [location] (the first location), or an empty state without one. */
    fun updateAll(context: Context, location: Location?) {
        val manager = AppWidgetManager.getInstance(context)
        Size.entries.forEach { size ->
            ids(context, size).forEach { id ->
                manager.updateAppWidget(id, build(context, manager, id, size, location))
            }
        }
    }

    private fun ids(context: Context, size: Size): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, size.provider))

    private fun build(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        size: Size,
        location: Location?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, size.layout)
        val (widthDp, heightDp) = sizeDp(manager, id, size)
        val weather = location?.weather
        val daylight = WeatherViewController.isDaylight(location)
        val unit = SettingsManager.getInstance(context).getTemperatureUnit(context)

        views.setImageViewBitmap(
            R.id.widget_sky,
            skyBitmap(context, WeatherViewController.getWeatherKind(location), daylight, widthDp, heightDp)
        )
        views.setOnClickPendingIntent(
            R.id.widget_root,
            AbstractRemoteViewsPresenter.getWeatherPendingIntent(context, location, size.requestCode)
        )

        // Header, shared by all sizes
        views.setTextViewText(R.id.widget_place, location?.getPlace(context) ?: context.getString(R.string.brand_name))
        views.setTextViewCompoundDrawablesRelative(
            R.id.widget_place,
            0,
            0,
            if (location?.isCurrentPosition == true) R.drawable.ic_widget_location_arrow else 0,
            0
        )
        val current = weather?.current
        views.setTextViewText(
            R.id.widget_temperature,
            current?.temperature?.temperature?.let { GlassForecastFormat.degrees(context, it, unit) } ?: "--"
        )
        val code = current?.weatherCode
        if (code != null) {
            views.setImageViewResource(R.id.widget_icon, FrostedWeatherIcons.get(code, daylight))
            views.setViewVisibility(R.id.widget_icon, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_icon, View.INVISIBLE)
        }
        views.setTextViewText(R.id.widget_condition, current?.weatherText ?: "")
        val today = weather?.today?.lowAndHigh()
        views.setTextViewText(
            R.id.widget_high_low,
            if (today?.first != null && today.second != null) {
                context.getString(
                    R.string.hero_high_low,
                    GlassForecastFormat.degrees(context, today.second!!, unit),
                    GlassForecastFormat.degrees(context, today.first!!, unit)
                )
            } else {
                ""
            }
        )

        if (location != null && weather != null) {
            if (size != Size.SMALL) {
                bindHours(context, views, location, unit, columns = ((widthDp - 24) / 52).coerceIn(4, 7))
            }
            if (size == Size.LARGE) {
                bindDays(context, views, location, unit, widthDp, rows = ((heightDp - 196) / 30).coerceIn(3, 7))
            }
        }
        return views
    }

    private fun bindHours(
        context: Context,
        views: RemoteViews,
        location: Location,
        unit: TemperatureUnit,
        columns: Int,
    ) {
        val weather = location.weather!!
        views.removeAllViews(R.id.widget_hours)
        weather.nextHourlyForecast.take(columns).forEachIndexed { index, hourly ->
            val item = RemoteViews(context.packageName, R.layout.widget_frosty_hour)
            item.setTextViewText(
                R.id.widget_hour_time,
                if (index == 0) context.getString(R.string.hourly_now) else hourly.date.getHour(location, context)
            )
            val code = hourly.weatherCode
            if (code != null) {
                item.setImageViewResource(R.id.widget_hour_icon, FrostedWeatherIcons.get(code, hourly.isDaylight))
            }
            // "Now" shows the observed temperature when there is one, like the home screen
            val temperature = (if (index == 0) weather.current?.temperature?.temperature else null)
                ?: hourly.temperature?.temperature
            item.setTextViewText(
                R.id.widget_hour_temperature,
                temperature?.let { GlassForecastFormat.degrees(context, it, unit) } ?: "-"
            )
            views.addView(R.id.widget_hours, item)
        }
    }

    private fun bindDays(
        context: Context,
        views: RemoteViews,
        location: Location,
        unit: TemperatureUnit,
        widthDp: Int,
        rows: Int,
    ) {
        val weather = location.weather!!
        val days = weather.dailyForecastStartingToday.take(rows)
        val ranges = days.map { it.lowAndHigh() }
        val scaleMin = ranges.mapNotNull { it.first?.toDouble(TemperatureUnit.CELSIUS) }.minOrNull()
        val scaleMax = ranges.mapNotNull { it.second?.toDouble(TemperatureUnit.CELSIUS) }.maxOrNull()
        // Row: padding 16 + day 46 + icon 34 + low 36 + bar margins 16 + high 36 + padding 16
        val barWidthDp = (widthDp - 200).coerceAtLeast(40)

        views.removeAllViews(R.id.widget_days)
        days.forEachIndexed { row, daily ->
            val item = RemoteViews(context.packageName, R.layout.widget_frosty_day)
            item.setTextViewText(
                R.id.widget_day_name,
                if (row == 0) context.getString(R.string.daily_today_short) else daily.getWeek(location, context)
            )
            val code = daily.day?.weatherCode ?: daily.night?.weatherCode
            if (code != null) {
                item.setImageViewResource(R.id.widget_day_icon, FrostedWeatherIcons.get(code, true))
            }
            val (low, high) = ranges[row]
            item.setTextViewText(R.id.widget_day_low, low?.let { GlassForecastFormat.degrees(context, it, unit) } ?: "")
            item.setTextViewText(
                R.id.widget_day_high,
                high?.let { GlassForecastFormat.degrees(context, it, unit) } ?: ""
            )
            if (scaleMin != null && scaleMax != null) {
                item.setImageViewBitmap(
                    R.id.widget_day_bar,
                    rangeBarBitmap(
                        context,
                        barWidthDp,
                        scaleMin,
                        scaleMax,
                        low?.toDouble(TemperatureUnit.CELSIUS),
                        high?.toDouble(TemperatureUnit.CELSIUS),
                        current = if (row == 0) {
                            weather.current?.temperature?.temperature?.toDouble(TemperatureUnit.CELSIUS)
                        } else {
                            null
                        }
                    )
                )
            }
            views.addView(R.id.widget_days, item)
        }
    }

    /** Current widget size in dp: portrait width and height, or the defaults of the size before the launcher tells. */
    private fun sizeDp(manager: AppWidgetManager, id: Int, size: Size): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(id)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return (width.takeIf { it > 0 } ?: size.defaultWidthDp) to (height.takeIf { it > 0 } ?: size.defaultHeightDp)
    }

    /**
     * The sky, drawn at the widget's size with its rounded corners baked in (launchers before
     * Android 12 don't clip widgets). Capped at 2× density: it is a soft gradient.
     */
    private fun skyBitmap(context: Context, weatherKind: Int, daylight: Boolean, widthDp: Int, heightDp: Int): Bitmap {
        val scale = context.resources.displayMetrics.density.coerceAtMost(2f)
        val width = (widthDp * scale).roundToInt().coerceAtLeast(1)
        val height = (heightDp * scale).roundToInt().coerceAtLeast(1)
        val radius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
        } else {
            context.resources.getDimension(R.dimen.frosty_widget_radius)
        } / context.resources.displayMetrics.density * scale

        val bitmap = createBitmap(width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                intArrayOf(
                    SkyPalette.topColor(weatherKind, daylight),
                    SkyPalette.middleColor(weatherKind, daylight),
                    SkyPalette.bottomColor(weatherKind, daylight)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        Canvas(bitmap).drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        return bitmap
    }

    /**
     * Like the home screen's 10-day forecast: a faint track for the coldest to the warmest
     * temperature of the list, the day's low to high colored on the temperature scale, and a dot
     * at the current temperature for today. All in °C.
     */
    private fun rangeBarBitmap(
        context: Context,
        widthDp: Int,
        scaleMin: Double,
        scaleMax: Double,
        low: Double?,
        high: Double?,
        current: Double?,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = (10 * density).roundToInt()
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val barHeight = 4 * density
        val radius = barHeight / 2f
        val cy = height / 2f
        val span = (scaleMax - scaleMin).takeIf { it > 0 } ?: 1.0
        fun x(t: Double) = radius + ((t - scaleMin) / span).coerceIn(0.0, 1.0).toFloat() * (width - barHeight)

        paint.color = 0x2EFFFFFF
        canvas.drawRoundRect(RectF(0f, cy - radius, width.toFloat(), cy + radius), radius, radius, paint)

        val l = low ?: high
        val h = high ?: low
        if (l != null && h != null) {
            // Opaque: the paint's alpha would otherwise fade the gradient like the track
            paint.color = 0xFFFFFFFF.toInt()
            paint.shader = LinearGradient(
                x(scaleMin),
                0f,
                x(scaleMax),
                0f,
                IntArray(13) { DailyViewHolder.temperatureColor(scaleMin + (scaleMax - scaleMin) * it / 12) },
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(
                RectF(x(minOf(l, h)) - radius, cy - radius, x(maxOf(l, h)) + radius, cy + radius),
                radius,
                radius,
                paint
            )
            paint.shader = null
        }
        current?.let {
            val dot = x(it)
            paint.color = 0x59000000
            canvas.drawCircle(dot, cy, 4.5f * density, paint)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(dot, cy, 3f * density, paint)
        }
        return bitmap
    }

    /** Low and high of the day: sources give daytime and nighttime temperatures. */
    private fun Daily.lowAndHigh() = listOfNotNull(
        day?.temperature?.temperature,
        night?.temperature?.temperature
    ).let { temperatures ->
        temperatures.minByOrNull { it.toDouble(TemperatureUnit.CELSIUS) } to
            temperatures.maxByOrNull { it.toDouble(TemperatureUnit.CELSIUS) }
    }
}
