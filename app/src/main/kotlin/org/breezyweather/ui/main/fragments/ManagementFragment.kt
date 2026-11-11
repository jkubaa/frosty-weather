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

import android.Manifest
import android.graphics.drawable.GradientDrawable
import org.breezyweather.ui.theme.weatherView.WeatherViewController
import org.breezyweather.ui.theme.weatherView.materialWeatherView.SkyPalette
import android.graphics.Typeface
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import org.breezyweather.common.extensions.dpToPx
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.core.graphics.ColorUtils
import org.breezyweather.ui.common.glass.FrostedBar
import org.breezyweather.ui.common.glass.LiquidGlass
import kotlin.math.roundToInt
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.breezyweather.ui.theme.ThemeManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import breezyweather.domain.location.model.Location
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.breezyweather.BreezyWeather
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.setSystemBarStyle
import org.breezyweather.common.source.getName
import org.breezyweather.common.utils.helpers.IntentHelper
import org.breezyweather.common.utils.helpers.PermissionHelper
import org.breezyweather.common.utils.helpers.SnackbarHelper
import org.breezyweather.domain.location.model.applyDefaultPreset
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.sources.SourceManager
import org.breezyweather.ui.common.composables.AlertDialogNoPadding
import org.breezyweather.ui.common.composables.AnimatedVisibilitySlideVertically
import org.breezyweather.ui.common.composables.SecondarySourcesPreference
import org.breezyweather.ui.common.widgets.Material3ExpressiveCardListItem
import org.breezyweather.ui.common.widgets.defaultCardListItemElevation
import org.breezyweather.ui.main.MainActivity
import org.breezyweather.ui.main.MainActivityViewModel
import org.breezyweather.ui.main.adapters.location.LocationAdapter
import org.breezyweather.ui.main.widgets.LocationItemTouchCallback
import org.breezyweather.ui.main.widgets.LocationItemTouchCallback.TouchReactor
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme
import org.breezyweather.ui.theme.compose.themeRipple

private const val COMPACT_TITLE_FADE_DP = 40f

/** Height of the row of floating buttons at the top of the list. */
private val TOP_ROW_HEIGHT = 60.dp

class PushedManagementFragment : ManagementFragment() {

    companion object {
        fun getInstance() = PushedManagementFragment()
    }

    override fun setSystemBarStyle() {
        requireActivity().window.setSystemBarStyle(isLightList())
    }
}

open class ManagementFragment : MainModuleFragment(), TouchReactor {

    protected lateinit var viewModel: MainActivityViewModel

    private lateinit var layout: LinearLayoutManager
    private lateinit var adapter: LocationAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchBar: View

    /** Top color of the list backdrop (see [updateBackdrop]), continued behind the status bar and top fade. */
    private val backdropTopColor = mutableIntStateOf(android.graphics.Color.BLACK)

    /** Height of the status bar (and cutout) over the list, in px. */
    private val topInset = mutableIntStateOf(0)

    /** Frosted bar behind the top buttons and the compact title (see [FrostedBar]). */
    private lateinit var topBar: FrostedBar

    /** Tint of the frosted top bar: the backdrop color, dense enough for the title to stay readable. */
    private fun topBarTint(backdropTop: Int): Int =
        ColorUtils.setAlphaComponent(backdropTop, ((if (isLightList()) 0.72f else 0.62f) * 255).toInt())

    /** Vertical position of the search capsule over the list, in px. */
    private val searchBarOffset = mutableIntStateOf(0)

    /** Opacity of the compact title between the top buttons: shown once the large title is scrolled away. */
    private val compactTitleAlpha = mutableFloatStateOf(0f)

    /** Top of the search slot in the list header, in px from the top of the header (-1: not laid out yet). */
    private var searchSlotTop = -1
    private lateinit var itemTouchHelper: ItemTouchHelper

    private var callback: Callback? = null

    interface Callback {
        fun onSearchBarClicked()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        initModel()
        initView()
        setCallback(requireActivity() as Callback)

        return ComposeView(requireContext()).apply {
            setContent {
                BreezyWeatherTheme {
                    ContentView()
                }
            }
        }
    }

    @Composable
    private fun ContentView() {
        val dialogChooseWeatherSourcesOpenState = viewModel.dialogChooseWeatherSourcesOpen.collectAsState()
        val selectedLocationState = viewModel.selectedLocation.collectAsState()

        val dialogChooseDebugLocationOpenState = viewModel.dialogChooseDebugLocationOpen.collectAsState()

        val colors = remember { LocationListColors(isLightList()) }
        val navigationBarHeight = WindowInsets.navigationBars.getBottom(LocalDensity.current)
        // The list and the frosted top bar go under the status bar: the bar's content starts below it
        val density = LocalDensity.current
        val topInsetPx = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).getTop(density)
        if (topInset.intValue != topInsetPx) topInset.intValue = topInsetPx
        val topInsetDp = with(density) { topInsetPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(backdropTopColor.intValue))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .clipToBounds()
        ) {
            // The list scrolls with its header (title, prompts), like iOS weather, and under the
            // floating glass elements below
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    recyclerView
                },
                update = {
                    if (it.paddingBottom != navigationBarHeight) it.setPadding(0, 0, 0, navigationBarHeight)
                }
            )
            // Frosted top bar behind the buttons and the compact title: blurs the cards scrolling under
            // it (tinted, so the title stays readable), with a soft bottom edge
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topInsetDp + TOP_ROW_HEIGHT + FrostedBar.FADE_DP.dp),
                factory = { topBar }
            )
            // Search, over the list: follows its slot in the header, then stays pinned under the top buttons
            // (moved by a layout offset, not a View translation: Compose routes touches by layout
            // position, so a translated view wouldn't receive the taps where it's drawn)
            AndroidView(
                modifier = Modifier
                    .offset { IntOffset(0, searchBarOffset.intValue) }
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.location_search_bar_height)),
                factory = {
                    searchBar
                }
            )
            // Liquid glass buttons, floating over the list: back (when the list is pushed over the home
            // screen) and settings
            Row(
                modifier = Modifier
                    .padding(top = topInsetDp)
                    .fillMaxWidth()
                    .height(TOP_ROW_HEIGHT)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!((requireActivity() as MainActivity).isDrawerLayoutVisible)) {
                    LiquidGlassIconButton(
                        icon = R.drawable.ic_toolbar_back,
                        contentDescription = stringResource(R.string.action_back),
                        isLight = isLightList(),
                        blurRoot = recyclerView,
                        onClick = { (requireActivity() as MainActivity).setManagementFragmentVisibility(false) }
                    )
                } else {
                    Spacer(modifier = Modifier.size(GLASS_BUTTON_SIZE)) // keeps the compact title centered
                }
                // Compact title, faded in once the large one has scrolled away
                Text(
                    text = stringResource(R.string.locations),
                    color = colors.content,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = compactTitleAlpha.floatValue }
                )
                LiquidGlassIconButton(
                    icon = R.drawable.ic_settings,
                    contentDescription = stringResource(R.string.action_settings),
                    isLight = isLightList(),
                    blurRoot = recyclerView,
                    onClick = { activity?.let { IntentHelper.startSettingsActivity(it) } }
                )
            }
        }

        if (dialogChooseWeatherSourcesOpenState.value) {
            selectedLocationState.value?.let {
                SecondarySourcesPreference(
                    sourceManager = (requireActivity() as MainActivity).sourceManager,
                    location = it,
                    onClose = { newLocation: Location? ->
                        viewModel.closeChooseWeatherSourcesDialog()

                        if (newLocation != null) {
                            if (viewModel.locationExists(it)) { // Updating
                                // If main source was changed, we need to check first that it doesn't create
                                // a duplicate
                                if (it.forecastSource != newLocation.forecastSource &&
                                    viewModel.locationExists(newLocation)
                                ) {
                                    SnackbarHelper.showSnackbar(getString(R.string.location_message_already_exists))
                                } else {
                                    viewModel.updateLocation(newLocation, it)
                                    SnackbarHelper.showSnackbar(getString(R.string.location_message_updated))
                                }
                            } else { // Adding new location
                                if (viewModel.locationExists(newLocation)) {
                                    SnackbarHelper.showSnackbar(getString(R.string.location_message_already_exists))
                                } else {
                                    viewModel.addLocation(newLocation, null, requireContext())
                                    SnackbarHelper.showSnackbar(getString(R.string.location_message_added))
                                }
                            }
                        }
                    },
                    locationExists = { loc: Location ->
                        viewModel.locationExists(loc)
                    }
                )
            }
        }

        if (BreezyWeather.instance.debugMode && dialogChooseDebugLocationOpenState.value) {
            DebugLocationScreen(
                sourceManager = (requireActivity() as MainActivity).sourceManager,
                onClose = { addedLocation: Location? ->
                    viewModel.closeChooseDebugLocationDialog()

                    if (addedLocation != null) {
                        if (viewModel.locationExists(addedLocation)) {
                            SnackbarHelper.showSnackbar(getString(R.string.location_message_already_exists))
                        } else {
                            viewModel.addLocation(addedLocation, null, requireContext())
                            SnackbarHelper.showSnackbar(getString(R.string.location_message_added))
                        }
                    }
                }
            )
        }
    }

    /**
     * Header of the list, scrolling with the cards: large title, search, other ways to add a location,
     * loading line and the notification / update check prompts.
     */
    @Composable
    private fun ListHeader() {
        val validLocationListState = viewModel.validLocationList.collectAsState()
        var notificationDismissed by remember {
            mutableStateOf(viewModel.statementManager.isPostNotificationDialogAlreadyShown)
        }
        var notificationAppUpdateCheckDismissed by remember {
            mutableStateOf(viewModel.statementManager.isAppUpdateCheckDialogAlreadyShown)
        }
        val locationLoadingState = viewModel.locationListLoading.collectAsState()
        val colors = remember { LocationListColors(isLightList()) }
        val hasCurrentLocation = validLocationListState.value.any { it.isCurrentPosition }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Room for the floating top buttons
            Spacer(modifier = Modifier.height(with(LocalDensity.current) { topInset.intValue.toDp() } + TOP_ROW_HEIGHT))
            Text(
                text = stringResource(R.string.locations),
                color = colors.content,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)
            )

            // Room for the liquid glass search capsule, which lies over the list (see [searchBar]) so it
            // can stay pinned at the top while the cards scroll under it
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.location_search_bar_height))
                    .onGloballyPositioned {
                        searchSlotTop = it.positionInRoot().y.roundToInt()
                        updateSearchBarPosition()
                    }
            )

            // Other ways to add a location (were floating action buttons)
            if (!hasCurrentLocation || BreezyWeather.instance.debugMode) {
                Row(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!hasCurrentLocation) {
                        LiquidGlassChip(
                            icon = R.drawable.ic_location_arrow,
                            text = stringResource(R.string.action_add_current_location),
                            isLight = isLightList(),
                            blurRoot = recyclerView,
                            onClick = {
                                viewModel.openChooseWeatherSourcesDialog(
                                    Location(isCurrentPosition = true)
                                        .applyDefaultPreset((requireActivity() as MainActivity).sourceManager)
                                )
                            }
                        )
                    }
                    if (BreezyWeather.instance.debugMode) {
                        LiquidGlassChip(
                            icon = R.drawable.ic_bug_report,
                            text = stringResource(R.string.location_list_debug),
                            isLight = isLightList(),
                            blurRoot = recyclerView,
                            onClick = { viewModel.openChooseDebugLocationDialog() }
                        )
                    }
                }
            }

            // Thin loading line, keeps its room so the list doesn't jump
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(2.dp)
            ) {
                if (locationLoadingState.value) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = colors.content,
                        trackColor = colors.glass
                    )
                }
            }

            var hasNotificationPermission: Boolean? = null
            if (!notificationDismissed || !notificationAppUpdateCheckDismissed) {
                val notificationPermissionState = rememberMultiplePermissionsState(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        listOf(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // permission not needed
                        emptyList()
                    }
                )
                hasNotificationPermission = notificationPermissionState.permissions.isEmpty() ||
                    notificationPermissionState.permissions[0].status == PermissionStatus.Granted
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AnimatedVisibilitySlideVertically(
                    hasNotificationPermission == false && !notificationDismissed
                ) {
                    GlassNotificationCard(
                        colors = colors,
                        title = stringResource(R.string.dialog_permissions_notification_title),
                        summary = stringResource(R.string.dialog_permissions_notification_content),
                        action = stringResource(R.string.action_allow),
                        onClick = {
                            viewModel.statementManager.setPostNotificationDialogAlreadyShown()
                            notificationDismissed = true

                            PermissionHelper.requestPermissionWithFallback(
                                activity = requireActivity(),
                                permission = Manifest.permission.POST_NOTIFICATIONS,
                                fallback = {
                                    IntentHelper.startNotificationSettingsActivity(requireActivity())
                                }
                            )
                        },
                        onClose = {
                            viewModel.statementManager.setPostNotificationDialogAlreadyShown()
                            notificationDismissed = true
                            /*
                             * We could turn off alert notification from SettingsManager, but
                             * it’s best not to, as the user can still enable notification
                             * permission again from Android settings, and there is a
                             * permission check before sending any notification even if
                             * preference is enabled.
                             */
                        }
                    )
                }
            }
            if (BreezyWeather.instance.isGitHubUpdateCheckerEnabled) {
                AnimatedVisibilitySlideVertically(
                    hasNotificationPermission == true && !notificationAppUpdateCheckDismissed
                ) {
                    GlassNotificationCard(
                        colors = colors,
                        title = stringResource(R.string.dialog_app_update_check_title),
                        summary = stringResource(R.string.dialog_app_update_check_content),
                        action = stringResource(R.string.action_enable),
                        onClick = {
                            viewModel.statementManager.setAppUpdateCheckDialogAlreadyShown()
                            notificationAppUpdateCheckDismissed = true
                            SettingsManager.getInstance(requireContext()).isAppUpdateCheckEnabled = true
                        },
                        onClose = {
                            viewModel.statementManager.setAppUpdateCheckDialogAlreadyShown()
                            notificationAppUpdateCheckDismissed = true
                        }
                    )
                }
            }

        }
    }

    override fun setSystemBarStyle() {
        // do nothing.
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    private fun initModel() {
        viewModel = ViewModelProvider(requireActivity())[MainActivityViewModel::class.java]
    }

    private fun initView() {
        adapter =
            LocationAdapter(
                requireActivity(),
                ArrayList(),
                null,
                (requireActivity() as MainActivity).sourceManager,
                mClickListener = { formattedId ->
                    viewModel.setLocation(formattedId)
                    parentFragmentManager.popBackStack()
                }
            )
        val header = ComposeView(requireContext()).apply {
            setContent {
                BreezyWeatherTheme {
                    ListHeader()
                }
            }
        }
        initSearchBar()
        recyclerView = RecyclerView(requireContext())
        recyclerView.adapter = ConcatAdapter(LocationListHeaderAdapter(header), adapter)
        recyclerView.setPadding(0, 0, 0, 0)
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        recyclerView.layoutManager = LinearLayoutManager(
            requireActivity(),
            RecyclerView.VERTICAL,
            false
        ).also { layout = it }
        while (recyclerView.itemDecorationCount > 0) {
            recyclerView.removeItemDecorationAt(0)
        }
        recyclerView.addItemDecoration(
            LocationListDecoration(requireContext(), if (isLightList()) Color.Black.toArgb() else Color.White.toArgb())
        )
        // Cards scroll under the navigation bar, but the last one can still scroll above it
        recyclerView.clipToPadding = false
        topBar = FrostedBar(requireContext())
        topBar.setup(recyclerView, topBarTint(android.graphics.Color.BLACK))
        updateBackdrop(viewModel.currentLocation.value?.location)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = updateSearchBarPosition()
        })
        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSearchBarPosition() }
        // Blurs the cards scrolling under it: needs the list, so it is set up once both exist
        LiquidGlass.applyFloating(
            host = searchBar as ViewGroup,
            cornerRadius = resources.getDimension(R.dimen.location_search_bar_height) / 2f,
            tint = locationGlassTint(requireContext(), isLightList()),
            blurRoot = recyclerView
        )

        itemTouchHelper = ItemTouchHelper(
            LocationItemTouchCallback(
                requireActivity() as BreezyActivity,
                viewModel,
                this
            )
        )
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Start a coroutine in the lifecycle scope
        // FIXME: Race condition
        lifecycleScope.launch {
            // repeatOnLifecycle launches the block in a new coroutine every time the
            // lifecycle is in the STARTED state (or above) and cancels it when it's STOPPED.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Trigger the flow and start listening for values.
                // Note that this happens when lifecycle is STARTED and stops
                // collecting when the lifecycle is STOPPED
                viewModel.validLocationList.collect {
                    // New value received
                    adapter.update(it, viewModel.currentLocation.value?.location?.formattedId)
                }
            }
        }
        lifecycleScope.launch {
            // repeatOnLifecycle launches the block in a new coroutine every time the
            // lifecycle is in the STARTED state (or above) and cancels it when it's STOPPED.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Trigger the flow and start listening for values.
                // Note that this happens when lifecycle is STARTED and stops
                // collecting when the lifecycle is STOPPED
                viewModel.currentLocation.collect {
                    // New value received
                    adapter.update(viewModel.validLocationList.value, it?.location?.formattedId)
                    updateBackdrop(it?.location)
                }
            }
        }
        lifecycleScope.launch {
            // Keep the local times on the cards current while the list is open
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
                while (true) {
                    delay(60_000L - System.currentTimeMillis() % 60_000L)
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                }
            }
        }
    }

    private fun initSearchBar() {
        val isLight = isLightList()
        val content = if (isLight) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        val secondary = ColorUtils.setAlphaComponent(content, (0.72f * 255).toInt())
        searchBar = LayoutInflater.from(requireContext()).inflate(R.layout.view_location_search_bar, null).apply {
            visibility = View.INVISIBLE // until its slot in the header is known
            setOnClickListener { callback?.onSearchBarClicked() }
            findViewById<ImageView>(R.id.search_icon).imageTintList = ColorStateList.valueOf(secondary)
            findViewById<TextView>(R.id.search_hint).apply {
                setTextColor(secondary)
                setTypeface(typeface, Typeface.BOLD)
            }
        }
    }

    /**
     * Backdrop of the list: a dimmed tint of the selected location's sky at the top, fading to the plain
     * background further down. It is the list's own background so the liquid glass (which blurs the list)
     * has colors to frost even when no card is under it.
     */
    private fun updateBackdrop(location: Location?) {
        val isLight = isLightList()
        val base = if (isLight) android.graphics.Color.rgb(0xF2, 0xF2, 0xF7) else android.graphics.Color.BLACK
        val daylight = WeatherViewController.isDaylight(location)
        val kind = WeatherViewController.getWeatherKind(location)
        val top = ColorUtils.blendARGB(SkyPalette.topColor(kind, daylight), base, if (isLight) 0.55f else 0.35f)
        val middle = ColorUtils.blendARGB(SkyPalette.bottomColor(kind, daylight), base, if (isLight) 0.8f else 0.7f)
        recyclerView.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(top, middle, base, base)
        )
        backdropTopColor.intValue = top
        topBar.setTint(topBarTint(top))
    }

    /** The search capsule sits on its slot in the header, and stays at the top once that is scrolled away. */
    private fun updateSearchBarPosition() {
        if (!::searchBar.isInitialized || searchSlotTop < 0) return
        val header = recyclerView.findViewHolderForAdapterPosition(0)?.itemView
        val slotY = header?.let { it.top + searchSlotTop }
        val pinnedY = topInset.intValue + requireContext().dpToPx(TOP_ROW_HEIGHT.value).roundToInt()
        searchBarOffset.intValue = slotY?.coerceAtLeast(pinnedY) ?: pinnedY
        // The large title sits right above the search slot: fade the compact one in over the last
        // stretch before the search bar gets pinned
        val fadeRange = requireContext().dpToPx(COMPACT_TITLE_FADE_DP)
        compactTitleAlpha.floatValue = slotY?.let { (1f - (it - pinnedY) / fadeRange).coerceIn(0f, 1f) } ?: 1f
        searchBar.visibility = View.VISIBLE
    }

    fun prepareReenterTransition() {
        // TODO
        postponeEnterTransition()
        startPostponedEnterTransition()
    }

    // interface.
    private fun setCallback(l: Callback?) {
        callback = l
    }

    // location item touch reactor.
    override fun resetViewHolderAt(position: Int) {
        adapter.notifyItemChanged(position)
    }

    override fun reorderByDrag(from: Int, to: Int) {
        adapter.update(from, to)
    }

    /**
     * The list follows the location screens: dark (white text on black, like iOS weather) day and night,
     * unless the user picked "Always light".
     */
    protected fun isLightList(): Boolean = ThemeManager.isLightTheme(
        requireContext(),
        viewModel.currentLocation.value?.location
    )

    @Composable
    fun DebugLocationScreen(
        sourceManager: SourceManager,
        onClose: ((location: Location?) -> Unit),
        modifier: Modifier = Modifier,
    ) {
        val context = LocalContext.current
        val sourcesWithTestingLocations = sourceManager.getWeatherSources().filter { it.testingLocations.isNotEmpty() }

        AlertDialogNoPadding(
            modifier = modifier,
            onDismissRequest = {
                onClose(null)
            },
            title = {
                Text(
                    text = stringResource(R.string.action_add_debug_location),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.small_margin)),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(sourcesWithTestingLocations) { sourceIndex, source ->
                        Text(
                            source.name,
                            modifier = Modifier.padding(
                                start = dimensionResource(R.dimen.normal_margin),
                                bottom = dimensionResource(R.dimen.small_margin)
                            )
                        )

                        source.testingLocations.forEachIndexed { locationIndex, location ->
                            val enabled = !viewModel.locationExists(location)
                            Material3ExpressiveCardListItem(
                                elevation = if (enabled) defaultCardListItemElevation else 0.dp,
                                isFirst = locationIndex == 0,
                                isLast = locationIndex == source.testingLocations.lastIndex
                            ) {
                                ListItem(
                                    tonalElevation = if (enabled) defaultCardListItemElevation else 0.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(if (enabled) 1f else 0.5f)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = themeRipple(),
                                            onClick = { onClose(location) },
                                            enabled = enabled
                                        ),
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    ),
                                    headlineContent = {
                                        Text(
                                            source.getName(context),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            location.getPlace(context),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                            if (locationIndex != source.testingLocations.lastIndex) {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                        if (sourceIndex != sourcesWithTestingLocations.lastIndex) {
                            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.normal_margin)))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClose(null)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_close),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}
