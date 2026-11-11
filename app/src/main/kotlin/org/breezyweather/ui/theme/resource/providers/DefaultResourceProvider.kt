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

package org.breezyweather.ui.theme.resource.providers

import android.animation.Animator
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.Size
import androidx.core.content.res.ResourcesCompat
import breezyweather.domain.weather.reference.WeatherCode
import org.breezyweather.BreezyWeather
import org.breezyweather.R
import org.breezyweather.ui.common.images.MoonDrawable
import org.breezyweather.ui.common.images.SunDrawable
import org.breezyweather.ui.theme.resource.utils.Constants
import org.breezyweather.ui.theme.resource.utils.XmlHelper
import java.util.Objects

class DefaultResourceProvider : ResourceProvider() {
    private val mContext: Context
    override var providerName: String? = null
    override val providerIcon: Drawable?
    private var mDrawableFilter: Map<String, String>? = null
    private var mShortcutFilter: Map<String, String>? = null

    init {
        mContext = BreezyWeather.instance
        providerName = mContext.getString(R.string.brand_name)
        providerIcon = mContext.applicationInfo.loadIcon(mContext.packageManager)
        val res = mContext.resources
        try {
            mDrawableFilter = XmlHelper.getFilterMap(res.getXml(R.xml.icon_provider_drawable_filter))
            mShortcutFilter = XmlHelper.getFilterMap(res.getXml(R.xml.icon_provider_shortcut_filter))
        } catch (e: Exception) {
            mDrawableFilter = HashMap()
            mShortcutFilter = HashMap()
        }
    }

    override val packageName: String
        get() = mContext.packageName

    // weather icon: the bundled frosted art (see FrostedWeatherIcons).
    override fun getWeatherIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return ResourcesCompat.getDrawable(mContext.resources, FrostedWeatherIcons.get(code, dayTime), null)!!
    }

    override fun getWeatherIconUri(code: WeatherCode?, dayTime: Boolean): Uri {
        return getDrawableUri(mContext.resources.getResourceEntryName(FrostedWeatherIcons.get(code, dayTime)))
    }

    /** The frosted art is a single flat image: no layers to animate separately. */
    @Size(3)
    override fun getWeatherIcons(code: WeatherCode?, dayTime: Boolean): Array<Drawable?> {
        return arrayOf(getWeatherIcon(code, dayTime), null, null)
    }

    private fun getDrawable(resName: String): Drawable? {
        return try {
            ResourcesCompat.getDrawable(
                mContext.resources,
                getResId(mContext, resName, "drawable"),
                null
            )
        } catch (e: Exception) {
            null
        }
    }

    // animator: none for the single-layer frosted art.
    @Size(3)
    override fun getWeatherAnimators(code: WeatherCode?, dayTime: Boolean): Array<Animator?> {
        return arrayOf(null, null, null)
    }

    // minimal.
    override fun getMinimalLightIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getMiniLightIconName(code, dayTime))!!
    }

    override fun getMinimalLightIconUri(code: WeatherCode?, dayTime: Boolean): Uri {
        return Objects.requireNonNull(
            getDrawableUri(getMiniLightIconName(code, dayTime))
        )
    }

    override fun getMinimalGreyIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getMiniGreyIconName(code, dayTime))!!
    }

    override fun getMinimalGreyIconUri(code: WeatherCode?, dayTime: Boolean): Uri {
        return Objects.requireNonNull(
            getDrawableUri(getMiniGreyIconName(code, dayTime))
        )
    }

    override fun getMinimalDarkIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getMiniDarkIconName(code, dayTime))!!
    }

    override fun getMinimalDarkIconUri(code: WeatherCode?, dayTime: Boolean): Uri {
        return Objects.requireNonNull(
            getDrawableUri(getMiniDarkIconName(code, dayTime))
        )
    }

    override fun getMinimalXmlIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getMiniXmlIconName(code, dayTime))!!
    }

    override fun getMinimalIcon(code: WeatherCode?, dayTime: Boolean): Icon {
        return Objects.requireNonNull(
            Icon.createWithResource(
                mContext,
                getMinimalXmlIconId(code, dayTime)
            )
        )
    }

    @DrawableRes
    fun getMinimalXmlIconId(code: WeatherCode?, dayTime: Boolean): Int {
        return getResId(mContext, getMiniXmlIconName(code, dayTime), "drawable")
    }

    private fun getMiniLightIconName(code: WeatherCode?, daytime: Boolean): String {
        return getFilterResource(
            mDrawableFilter,
            innerGetMiniIconName(code, daytime) + Constants.SEPARATOR + Constants.LIGHT
        )
    }

    private fun getMiniGreyIconName(code: WeatherCode?, daytime: Boolean): String {
        return getFilterResource(
            mDrawableFilter,
            innerGetMiniIconName(code, daytime) + Constants.SEPARATOR + Constants.GREY
        )
    }

    private fun getMiniDarkIconName(code: WeatherCode?, daytime: Boolean): String {
        return getFilterResource(
            mDrawableFilter,
            innerGetMiniIconName(code, daytime) + Constants.SEPARATOR + Constants.DARK
        )
    }

    private fun getMiniXmlIconName(code: WeatherCode?, daytime: Boolean): String {
        return getFilterResource(
            mDrawableFilter,
            innerGetMiniIconName(code, daytime) + Constants.SEPARATOR + Constants.XML
        )
    }

    // shortcut.
    override fun getShortcutsIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getShortcutsIconName(code, dayTime))!!
    }

    override fun getShortcutsForegroundIcon(code: WeatherCode?, dayTime: Boolean): Drawable {
        return getDrawable(getShortcutsForegroundIconName(code, dayTime))!!
    }

    private fun getShortcutsIconName(code: WeatherCode?, daytime: Boolean): String {
        return getFilterResource(
            mShortcutFilter,
            innerGetShortcutsIconName(code, daytime)
        )
    }

    private fun getShortcutsForegroundIconName(code: WeatherCode?, daytime: Boolean): String {
        return getFilterResource(
            mShortcutFilter,
            getShortcutsIconName(code, daytime) + Constants.SEPARATOR + Constants.FOREGROUND
        )
    }

    override val sunDrawable: Drawable
        // sun and moon.
        get() = SunDrawable()
    override val moonDrawable: Drawable
        get() = MoonDrawable()

    companion object {
        fun isDefaultIconProvider(packageName: String): Boolean {
            return packageName == BreezyWeather.instance.packageName
        }

        private fun getFilterResource(filter: Map<String, String>?, key: String): String {
            val value = filter?.getOrElse(key) { null }
            return if (value.isNullOrEmpty()) key else value
        }

        private fun innerGetWeatherIconName(code: WeatherCode?, daytime: Boolean): String {
            return Constants.getResourcesName(code) +
                Constants.SEPARATOR +
                if (daytime) Constants.DAY else Constants.NIGHT
        }

        private fun innerGetMiniIconName(code: WeatherCode?, daytime: Boolean): String {
            return innerGetWeatherIconName(code, daytime) +
                Constants.SEPARATOR +
                Constants.MINI
        }

        private fun innerGetShortcutsIconName(code: WeatherCode?, daytime: Boolean): String {
            return Constants.getShortcutsName(code) +
                Constants.SEPARATOR +
                if (daytime) Constants.DAY else Constants.NIGHT
        }
    }
}
