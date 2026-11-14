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

package org.breezyweather.ui.main.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import breezyweather.domain.location.model.Location
import kotlinx.coroutines.launch
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.activities.livedata.EqualtableLiveData
import org.breezyweather.common.extensions.doOnApplyWindowInsets
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.isDarkMode
import org.breezyweather.common.extensions.isMotionReduced
import org.breezyweather.common.extensions.setSystemBarStyle
import org.breezyweather.common.options.appearance.BackgroundAnimationMode
import org.breezyweather.common.snackbar.SnackbarContainer
import org.breezyweather.databinding.FragmentHomeBinding
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.common.widgets.SwipeSwitchLayout
import org.breezyweather.ui.main.MainActivity
import org.breezyweather.ui.main.MainActivityViewModel
import org.breezyweather.ui.main.adapters.main.MainCards
import org.breezyweather.ui.main.cards.LayeredBackdrop
import org.breezyweather.ui.theme.ThemeManager
import org.breezyweather.ui.theme.resource.ResourcesProviderFactory
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import org.breezyweather.ui.theme.weatherView.sky.SkyWeatherView
import kotlin.math.roundToInt

class HomeFragment : MainModuleFragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var viewModel: MainActivityViewModel
    private lateinit var weatherView: WeatherView

    private var cards: MainCards? = null
    private var resourceProvider: ResourceProvider? = null

    private val previewOffset = EqualtableLiveData(0)
    private var callback: Callback? = null
    private var lastCurrentLocation: Location? = null
    private var toolbarTitleFraction = 0f

    interface Callback {
        fun onEditIconClicked()
        fun onManageIconClicked()
        fun onOpenInOtherAppIconClicked()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentHomeBinding.inflate(layoutInflater, container, false)

        initModel()

        // attach weather view.
        // Compose sky (painterly clouds and weather effects), in place of the theme's View-based one
        weatherView = SkyWeatherView(requireContext())
        // The weather view lives in its own container: it is the backdrop the liquid glass cards blur
        val glassBackdrop = FrameLayout(requireContext()).apply {
            id = R.id.glass_backdrop
            addView(
                weatherView as View,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        (binding.switchLayout.parent.parent.parent as CoordinatorLayout).addView(
            glassBackdrop,
            0,
            CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        initView()
        setCallback(requireActivity() as Callback)

        return binding.root
    }

    /**
     * Snackbars float above the glass bottom bar instead of covering its buttons.
     * The snackbar already keeps the navigation bar inset free, so only the bar itself, its own
     * margin and a small gap are added.
     */
    override val snackbarContainer: SnackbarContainer
        get() {
            val bar = binding.bottomBar
            val offset = if (bar.visibility == View.GONE) {
                0
            } else {
                (bar.height.takeIf { it > 0 } ?: resources.getDimensionPixelSize(R.dimen.glass_capsule_height)) +
                    resources.getDimensionPixelSize(R.dimen.glass_capsule_margin_bottom) +
                    resources.getDimensionPixelSize(R.dimen.small_margin)
            }
            return SnackbarContainer(this, binding.root, true, offset)
        }

    private fun isBackgroundAnimationEnabled() =
        when (SettingsManager.getInstance(requireContext()).backgroundAnimationMode) {
            BackgroundAnimationMode.SYSTEM -> !requireContext().isMotionReduced
            BackgroundAnimationMode.ENABLED -> true
            BackgroundAnimationMode.DISABLED -> false
        }

    override fun onResume() {
        super.onResume()
        weatherView.setDrawable(!isHidden)
    }

    override fun onPause() {
        super.onPause()
        weatherView.setDrawable(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cards = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        weatherView.setDrawable(!hidden)
    }

    override fun setSystemBarStyle() {
        val delegate = ThemeManager
            .getInstance(requireContext())
            .weatherThemeDelegate
        val location = if (::viewModel.isInitialized) viewModel.currentLocation.value?.location else null
        val isLightBackground = location?.let {
            delegate.isLightBackground(
                requireContext(),
                WeatherViewController.getWeatherKind(it),
                WeatherViewController.isDaylight(it)
            )
        } ?: false

        requireActivity().window.setSystemBarStyle(isLightBackground)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateDayNightColors()
        updateViews()
    }

    // init.

    private fun initModel() {
        viewModel = ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
    }

    @SuppressLint("ClickableViewAccessibility", "NonConstantResourceId", "NotifyDataSetChanged")
    private fun initView() {
        ensureResourceProvider()

        weatherView.setGravitySensorEnabled(
            SettingsManager.getInstance(requireContext()).isGravitySensorEnabled
        )

        weatherView.setDoAnimate(
            isBackgroundAnimationEnabled()
        )

        binding.appBar.doOnApplyWindowInsets { view, insets ->
            view.updatePadding(
                top = insets.top
            )
        }

        // iOS weather style: no buttons up top. The toolbar only carries the compact title that fades in
        // on scroll; the actions live in the floating glass bottom bar ("Open in another app" moved to
        // the edit location dialog).
        binding.bottomBarContent.onEdit = { callback?.onEditIconClicked() }
        binding.bottomBarContent.onManage = { callback?.onManageIconClicked() }
        // The bar's glass blurs both the sky and the cards scrolling under it
        binding.bottomBarContent.backdrop = LayeredBackdrop(
            *listOfNotNull((weatherView as? SkyWeatherView)?.backdrop, binding.cardsView.cardsBackdrop).toTypedArray()
        )

        binding.switchLayout.setOnSwitchListener(switchListener)
        // Glass over the cards follows them while a swipe moves them sideways
        binding.switchLayout.onContentMoved = { binding.cardsView.cardsBackdrop.moved() }
        binding.switchLayout.reset()
        binding.bottomBarContent.setSwitchView(binding.switchLayout)
        updateDayNightColors()
        binding.bottomBar.doOnApplyWindowInsets { view, insets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom +
                    resources.getDimensionPixelSize(R.dimen.glass_capsule_margin_bottom)
            }
        }

        binding.cardsView.onRefresh = {
            viewModel.updateWithUpdatingChecking(
                triggeredByUser = true,
                checkPermissions = true
            )
        }

        val listAnimationEnabled = SettingsManager
            .getInstance(requireContext())
            .isCardsFadeInEnabled
        val itemAnimationEnabled = SettingsManager
            .getInstance(requireContext())
            .isElementsAnimationEnabled
        cards = MainCards(requireActivity() as MainActivity)
        binding.cardsView.cards = cards
        // The glass cards blur this sky behind them
        binding.cardsView.backdrop = (weatherView as? SkyWeatherView)?.backdrop
        binding.cardsView.onScroll = ::onCardsScrolled

        if (isAdded && context != null) {
            updatePreviewSubviews()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentLocation.collect {
                    if (it?.location != null) {
                        binding.bottomBarContent.editVisible = true
                        binding.emptyText.visibility = if (it.location.weather != null) View.GONE else View.VISIBLE
                    } else {
                        binding.bottomBarContent.editVisible = false
                        binding.emptyText.visibility = View.GONE
                    }
                    if (it?.location != lastCurrentLocation) {
                        updateViews(it?.location)
                        lastCurrentLocation = it?.location
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loading.collect {
                    setRefreshing(it)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.indicator.collect {
                    binding.switchLayout.isEnabled = it.total > 1

                    if (binding.switchLayout.totalCount != it.total ||
                        binding.switchLayout.position != it.index
                    ) {
                        binding.switchLayout.setData(it.index, it.total)
                    }
                    // Also tells the dots how many locations there are (none shown for a single one)
                    binding.bottomBarContent.setSwitchView(binding.switchLayout)
                }
            }
        }
    }

    private fun updateDayNightColors() {
        // Theme or glass setting may have changed
        binding.bottomBarContent.refreshGlass()
        updateToolbarTitleAlpha()
    }

    /**
     * The location name is shown big in the hero header, so the top bar title stays invisible
     * until the header scrolls away, then fades in as a compact "name / 22° Sunny" title.
     */
    private fun updateToolbarTitleAlpha() {
        binding.topBar.fraction = toolbarTitleFraction
    }

    // control.
    fun updateViews(location: Location? = viewModel.currentLocation.value?.location) {
        ensureResourceProvider()
        updateDarkMode(location)
        updateContentViews(location = location)
        if (isAdded && context != null) {
            updatePreviewSubviews()
        }
    }

    private fun updateDarkMode(location: Location?) {
        val expectedLightTheme = ThemeManager.isLightTheme(requireContext(), location)

        (activity as BreezyActivity).updateLocalNightMode(expectedLightTheme)
    }

    private fun updateContentViews(location: Location?) {
        // The content is rebuilt from the top (hero visible): hide the compact title, which would
        // otherwise keep the visibility of the previous scroll position
        toolbarTitleFraction = 0f
        updateDayNightColors()
        weatherView.onScroll(0)

        binding.switchLayout.reset()

        val settings = SettingsManager.getInstance(requireContext())
        binding.cardsView.show(
            location,
            resourceProvider!!,
            listAnimationEnabled = settings.isCardsFadeInEnabled,
            itemAnimationEnabled = settings.isElementsAnimationEnabled
        )
    }

    private fun ensureResourceProvider() {
        val iconProvider = SettingsManager
            .getInstance(requireContext())
            .iconProvider
        if (resourceProvider == null || resourceProvider!!.packageName != iconProvider) {
            resourceProvider = ResourcesProviderFactory.newInstance
        }
    }

    private fun updatePreviewSubviews() {
        val location = viewModel.getValidLocation(previewOffset.value)
        val daylight = WeatherViewController.isDaylight(location)
        val backgroundWeatherKind = if (SettingsManager.getInstance(requireContext()).mainScreenBackgroundEnabled) {
            WeatherViewController.getWeatherKind(location)
        } else {
            WeatherView.WEATHER_KIND_NULL
        }

        // Compact title, name only: the current location is marked by the arrow in the hero and in the
        // page dots (no more "⊙" prefix)
        binding.topBar.title = location?.getPlace(requireContext()) ?: ""
        // iOS style: the current location's page shows an arrow instead of a dot
        binding.bottomBarContent.arrowIndex = viewModel.validLocationList.value
            .indexOfFirst { it.isCurrentPosition }
            .takeIf { it >= 0 }

        val temperatureUnit = SettingsManager.getInstance(requireContext()).getTemperatureUnit(requireContext())
        binding.topBar.caption = location?.takeIf { it.isCurrentPosition }?.let { getString(R.string.location_current) }
        binding.topBar.subtitle = location?.weather?.current?.let { current ->
            listOfNotNull(
                current.temperature?.temperature?.let { "${it.toDouble(temperatureUnit).roundToInt()}°" },
                current.weatherText?.takeIf { it.isNotEmpty() }
                    ?: current.weatherCode?.let { WeatherViewController.getWeatherText(it) },
                // Feels like, only when it differs from the actual temperature (as in the hero)
                current.temperature?.feelsLikeTemperature?.toDouble(temperatureUnit)?.roundToInt()
                    ?.takeIf { it != current.temperature?.temperature?.toDouble(temperatureUnit)?.roundToInt() }
                    ?.let { getString(R.string.temperature_feels_like_with_unit, "${it}°") }
            ).joinToString("  ·  ").takeIf { it.isNotEmpty() }
        }
        updateToolbarTitleAlpha()

        // Real sun and moon positions: twilight, sunrise/sunset colors, moon phase
        (weatherView as? SkyWeatherView)?.setLocation(location?.latitude, location?.longitude)
        weatherView.setWeather(backgroundWeatherKind, daylight, requireContext().isDarkMode)
    }

    private fun setRefreshing(isRefreshing: Boolean) {
        binding.cardsView.post {
            if (isFragmentViewCreated) {
                binding.cardsView.refreshing = isRefreshing
                binding.emptyText.visibility = if (isRefreshing) {
                    View.GONE
                } else {
                    viewModel.currentLocation.value?.location.let { loc ->
                        if (loc == null) {
                            View.GONE
                        } else if (loc.weather == null) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }
            }
        }
    }

    // interface.

    private fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    // on swipe listener (swipe switch layout).

    private val switchListener = object : SwipeSwitchLayout.OnSwitchListener {

        override fun onSwiped(swipeDirection: Int, progress: Float) {
            if (progress >= 1) {
                previewOffset.setValue(
                    if (swipeDirection == SwipeSwitchLayout.SWIPE_DIRECTION_LEFT) 1 else -1
                )
            } else {
                previewOffset.setValue(0)
            }
        }

        override fun onSwitched(swipeDirection: Int) {
            // Back to "no preview" first: switching redraws the sky right away, from this offset
            previewOffset.setValue(0)
            viewModel.offsetLocation(
                if (swipeDirection == SwipeSwitchLayout.SWIPE_DIRECTION_LEFT) 1 else -1
            )
        }
    }

    // on scroll changed listener.

    private fun onCardsScrolled(scrollY: Int) {
        if (!isFragmentViewCreated) return
        weatherView.onScroll(scrollY)

        // Fade the compact toolbar title in once the hero header has mostly scrolled away
        val fadeStart = requireContext().dpToPx(TOOLBAR_TITLE_FADE_START_DP)
        val fadeLength = requireContext().dpToPx(TOOLBAR_TITLE_FADE_LENGTH_DP)
        val linear = ((scrollY - fadeStart) / fadeLength).coerceIn(0f, 1f)
        // Eased like the hero's fade, so the hand-off doesn't move at a constant, mechanical rate
        val fraction = linear * linear * (3f - 2f * linear)
        if (fraction != toolbarTitleFraction) {
            toolbarTitleFraction = fraction
            updateToolbarTitleAlpha()
        }
    }

    companion object {
        // Overlaps the hero's own fade (40dp, see HomeCardsView): the title is fully in once the hero is gone
        private const val TOOLBAR_TITLE_FADE_START_DP = 20f
        private const val TOOLBAR_TITLE_FADE_LENGTH_DP = 30f
    }
}
