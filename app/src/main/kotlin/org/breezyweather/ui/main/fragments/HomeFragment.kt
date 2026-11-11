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

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import breezyweather.domain.location.model.Location
import kotlinx.coroutines.launch
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.activities.livedata.EqualtableLiveData
import org.breezyweather.common.extensions.density
import org.breezyweather.common.extensions.doOnApplyWindowInsets
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.getBlocksPerRow
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.common.extensions.isDarkMode
import org.breezyweather.common.extensions.isMotionReduced
import org.breezyweather.common.extensions.setSystemBarStyle
import org.breezyweather.common.options.appearance.BackgroundAnimationMode
import org.breezyweather.databinding.FragmentHomeBinding
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.common.glass.LiquidGlass
import org.breezyweather.ui.common.widgets.SwipeSwitchLayout
import org.breezyweather.ui.main.MainActivity
import org.breezyweather.ui.main.MainActivityViewModel
import org.breezyweather.ui.main.adapters.main.MainAdapter
import org.breezyweather.ui.main.adapters.main.ViewType
import org.breezyweather.ui.main.layouts.MainLayoutManager
import org.breezyweather.ui.main.utils.MainModuleUtils
import org.breezyweather.ui.theme.ThemeManager
import org.breezyweather.ui.theme.resource.ResourcesProviderFactory
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.ui.theme.weatherView.WeatherView
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import kotlin.math.roundToInt

class HomeFragment : MainModuleFragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var viewModel: MainActivityViewModel
    private lateinit var weatherView: WeatherView

    private var adapter: MainAdapter? = null
    private var scrollListener: OnScrollListener? = null
    private var recyclerViewAnimator: Animator? = null
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
        weatherView = ThemeManager
            .getInstance(requireContext())
            .weatherThemeDelegate
            .getWeatherView(requireContext())
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
        adapter = null
        binding.recyclerView.clearOnScrollListeners()
        scrollListener = null
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
        binding.editButton.setOnClickListener {
            callback?.onEditIconClicked()
        }
        binding.manageButton.setOnClickListener {
            callback?.onManageIconClicked()
        }

        binding.switchLayout.setOnSwitchListener(switchListener)
        binding.switchLayout.reset()
        binding.indicator.setSwitchView(binding.switchLayout)
        // Liquid glass: the page dots always live in a floating glass capsule (no auto-hide)
        binding.indicator.alpha = 1f
        updateDayNightColors()
        binding.bottomBar.doOnApplyWindowInsets { view, insets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom +
                    resources.getDimensionPixelSize(R.dimen.glass_capsule_margin_bottom)
            }
        }

        binding.refreshLayout.setOnRefreshListener {
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
        adapter = MainAdapter(
            (requireActivity() as MainActivity),
            binding.recyclerView,
            weatherView,
            null,
            resourceProvider!!,
            listAnimationEnabled,
            itemAnimationEnabled
        ).apply {
            itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        }

        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = MainLayoutManager(requireContext(), GRID_SPAN_COUNT).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (
                        ViewType.isHalfSizeableBlock(binding.recyclerView.adapter!!.getItemViewType(position)) != true
                    ) {
                        // Spreads on the full width
                        GRID_SPAN_COUNT
                    } else {
                        // This takes into account the actual available width (if drawer is open or not)
                        GRID_SPAN_COUNT.div(
                            requireContext().getBlocksPerRow(width.toFloat().div(requireContext().density))
                        )
                    }
                }
            }
        }
        binding.recyclerView.doOnApplyWindowInsets { view, insets ->
            // Leave room for the floating glass capsule
            view.updatePadding(
                bottom = insets.bottom + resources.getDimensionPixelSize(R.dimen.glass_capsule_content_clearance)
            )
        }
        binding.recyclerView.addOnScrollListener(OnScrollListener().also { scrollListener = it })

        if (isAdded && context != null) {
            updatePreviewSubviews()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentLocation.collect {
                    if (it?.location != null) {
                        binding.editButton.visibility = View.VISIBLE
                        binding.emptyText.visibility = if (it.location.weather != null) View.GONE else View.VISIBLE
                    } else {
                        // Keeps its slot so the dots stay centered
                        binding.editButton.visibility = View.INVISIBLE
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
                        binding.indicator.setSwitchView(binding.switchLayout)
                    }

                    binding.indicatorCapsule.visibility = if (it.total > 1) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun updateDayNightColors() {
        binding.refreshLayout.setProgressBackgroundColorSchemeColor(
            requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
        )
        val surface = requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
        // Frosted, the bottom bar stays readable over the cards with a light tint; without blur it
        // needs a denser body
        val bottomBarTint = ColorUtils.setAlphaComponent(
            surface,
            ((if (LiquidGlass.isBlurEnabled(requireContext())) 0.35f else 0.72f) * 255).toInt()
        )
        // Capsule and circles: all fully rounded, all blurring everything under them, cards included
        listOf(binding.editButton, binding.indicatorCapsule, binding.manageButton).forEach { host ->
            LiquidGlass.applyFloating(
                host = host,
                cornerRadius = resources.getDimension(R.dimen.glass_capsule_height) / 2f,
                tint = bottomBarTint,
                blurRoot = binding.root
            )
        }
        updateIndicatorColors()
        updateToolbarTitleAlpha()
    }

    private fun updateIndicatorColors() {
        val contentColor = requireContext().getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse)
        binding.indicator.setCurrentIndicatorColor(contentColor)
        binding.indicator.setIndicatorColor(
            ColorUtils.setAlphaComponent(contentColor, (0.35 * 255).toInt())
        )
    }

    /**
     * The location name is shown big in the hero header, so the toolbar title stays invisible
     * until the header scrolls away, then fades in as a compact "name / 22° Sunny" title.
     */
    private fun updateToolbarTitleAlpha() {
        val contentColor = requireContext().getThemeColor(com.google.android.material.R.attr.colorSurfaceInverse)
        binding.toolbar.setTitleTextColor(
            ColorUtils.setAlphaComponent(contentColor, (toolbarTitleFraction * 255).roundToInt())
        )
        binding.toolbar.setSubtitleTextColor(
            ColorUtils.setAlphaComponent(contentColor, (toolbarTitleFraction * 0.8f * 255).roundToInt())
        )
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

    @SuppressLint("ClickableViewAccessibility", "NotifyDataSetChanged")
    private fun updateContentViews(location: Location?) {
        recyclerViewAnimator?.let {
            it.cancel()
            recyclerViewAnimator = null
        }

        // The content is rebuilt from the top (hero visible): hide the compact title, which would
        // otherwise keep the visibility of the previous scroll position
        toolbarTitleFraction = 0f
        updateDayNightColors()

        binding.switchLayout.reset()

        if (location?.weather == null) {
            adapter!!.setNullWeather()
            adapter!!.notifyDataSetChanged()
            return
        }

        val listAnimationEnabled = SettingsManager
            .getInstance(requireContext())
            .isCardsFadeInEnabled
        val itemAnimationEnabled = SettingsManager
            .getInstance(requireContext())
            .isElementsAnimationEnabled
        adapter!!.update(
            (requireActivity() as MainActivity),
            binding.recyclerView,
            weatherView,
            location,
            resourceProvider!!,
            listAnimationEnabled,
            itemAnimationEnabled
        )
        adapter!!.notifyDataSetChanged()

        scrollListener!!.postReset(binding.recyclerView)

        // TODO: What's the purpose of this?
        if (!listAnimationEnabled) {
            binding.recyclerView.alpha = 0f
            recyclerViewAnimator = MainModuleUtils.getEnterAnimator(
                binding.recyclerView,
                0
            ).apply {
                startDelay = 150
            }.also { it.start() }
        }
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
        binding.toolbar.title = location?.getPlace(requireContext()) ?: ""
        // iOS style: the current location's page shows an arrow instead of a dot
        binding.indicator.arrowIndex = viewModel.validLocationList.value
            .indexOfFirst { it.isCurrentPosition }
            .takeIf { it >= 0 }

        binding.toolbar.subtitle = location?.weather?.current?.let { current ->
            val temperatureUnit = SettingsManager.getInstance(requireContext()).getTemperatureUnit(requireContext())
            listOfNotNull(
                current.temperature?.temperature?.let { "${it.toDouble(temperatureUnit).roundToInt()}°" },
                current.weatherText?.takeIf { it.isNotEmpty() }
                    ?: current.weatherCode?.let { WeatherViewController.getWeatherText(it) }
            ).joinToString("  ").takeIf { it.isNotEmpty() }
        }
        updateToolbarTitleAlpha()

        weatherView.setWeather(backgroundWeatherKind, daylight, requireContext().isDarkMode)
        binding.refreshLayout.setColorSchemeColors(
            ThemeManager
                .getInstance(requireContext())
                .weatherThemeDelegate
                .getThemeColors(
                    requireContext(),
                    WeatherViewController.getWeatherKind(location),
                    daylight
                )[0]
        )
    }

    private fun setRefreshing(isRefreshing: Boolean) {
        binding.refreshLayout.post {
            if (isFragmentViewCreated) {
                binding.refreshLayout.isRefreshing = isRefreshing
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
            viewModel.offsetLocation(
                if (swipeDirection == SwipeSwitchLayout.SWIPE_DIRECTION_LEFT) 1 else -1
            )
            previewOffset.setValue(0)
        }
    }

    // on scroll changed listener.

    private inner class OnScrollListener : RecyclerView.OnScrollListener() {

        private var mScrollY = 0

        fun postReset(recyclerView: RecyclerView) {
            recyclerView.post {
                if (!isFragmentViewCreated) {
                    return@post
                }
                mScrollY = 0
                onScrolled(recyclerView, 0, 0)
            }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            mScrollY = recyclerView.computeVerticalScrollOffset()
            weatherView.onScroll(mScrollY)

            // Fade the compact toolbar title in once the hero header has mostly scrolled away
            val fadeStart = recyclerView.context.dpToPx(TOOLBAR_TITLE_FADE_START_DP)
            val fadeLength = recyclerView.context.dpToPx(TOOLBAR_TITLE_FADE_LENGTH_DP)
            val fraction = ((mScrollY - fadeStart) / fadeLength).coerceIn(0f, 1f)
            if (fraction != toolbarTitleFraction) {
                toolbarTitleFraction = fraction
                updateToolbarTitleAlpha()
            }
            adapter?.onScroll()
        }
    }

    companion object {
        // 60 is 5 * 4 * 3, which allows us to divide from 1, 2, 3, 4 or 5 and have a whole number
        const val GRID_SPAN_COUNT = 60

        private const val TOOLBAR_TITLE_FADE_START_DP = 70f
        private const val TOOLBAR_TITLE_FADE_LENGTH_DP = 60f
    }
}
