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

package org.breezyweather.ui.search

import android.content.Intent
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.ui.common.glass.GlassContentLayout
import org.breezyweather.ui.common.glass.LiquidGlass
import org.breezyweather.ui.main.fragments.LiquidGlassIconButton
import org.breezyweather.ui.main.fragments.locationGlassTint
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.breezyweather.common.extensions.setSystemBarStyle
import org.breezyweather.common.options.DarkMode
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.main.fragments.LocationListColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import breezyweather.domain.location.model.Location
import dagger.hilt.android.AndroidEntryPoint
import org.breezyweather.BreezyWeather
import org.breezyweather.BuildConfig
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.inputMethodManager
import org.breezyweather.common.source.ConfigurableSource
import org.breezyweather.common.source.NonFreeNetSource
import org.breezyweather.common.source.RemovedSource
import org.breezyweather.common.source.getName
import org.breezyweather.domain.location.model.getPlace
import org.breezyweather.sources.SourceManager
import org.breezyweather.ui.common.composables.AlertDialogLink
import org.breezyweather.ui.common.composables.AlertDialogNoPadding
import org.breezyweather.ui.common.composables.SecondarySourcesPreference
import org.breezyweather.ui.common.widgets.Material3ExpressiveCardListItem
import org.breezyweather.ui.settings.preference.LargeSeparatorItem
import org.breezyweather.ui.settings.preference.composables.RadioButton
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme
import javax.inject.Inject

@AndroidEntryPoint
class SearchActivity : BreezyActivity() {
    private lateinit var viewModel: SearchViewModel

    @Inject
    lateinit var sourceManager: SourceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initModel()
        setContent {
            BreezyWeatherTheme {
                ContentView()
            }
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        window.setSystemBarStyle(isLightSearch())
    }

    /** Same backdrop as the location list: black, unless the user picked "Always light". */
    private fun isLightSearch() = SettingsManager.getInstance(this).darkMode == DarkMode.LIGHT

    @Composable
    private fun ContentView() {
        val context = LocalContext.current
        val dialogLinkOpenState = remember { mutableStateOf(false) }
        val dialogLocationSearchSourceOpenState = viewModel.dialogLocationSearchSourceOpenState.collectAsState()
        val dialogLocationSourcesOpenState = viewModel.dialogLocationSourcesOpenState.collectAsState()
        val selectedLocation = viewModel.selectedLocation.collectAsState()
        val isLoading = viewModel.isLoading.collectAsState()
        var text by viewModel.query
        var latestTextSearch by viewModel.latestSearch
        val locationSearchSourceState = viewModel.locationSearchSource.collectAsState()
        val locationSearchSource = sourceManager.getLocationSearchSourceOrDefault(locationSearchSourceState.value)

        val colors = remember { LocationListColors(isLightSearch()) }
        val listResourceState = viewModel.listResource.collectAsState()
        // Nominatim's usage policy forbids searching while typing: with it, search on the keyboard action only
        val searchAsYouType = locationSearchSource.id != NOMINATIM_ID

        // Search as you type, once the user pauses (and not for a single letter)
        LaunchedEffect(text, locationSearchSource.id) {
            val query = text.trim()
            if (query.length < MIN_QUERY_LENGTH) {
                viewModel.clearLocationList()
                latestTextSearch = ""
                return@LaunchedEffect
            }
            if (!searchAsYouType || query == latestTextSearch.trim()) return@LaunchedEffect
            delay(SEARCH_DEBOUNCE_MS)
            viewModel.requestLocationList(query)
            latestTextSearch = query
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                ResultsContent()

                // iOS style floating bar: liquid glass search field and close button, the results
                // scrolling under them are blurred and refracted
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        factory = { searchFieldHost }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    AndroidView(
                        modifier = Modifier.size(44.dp),
                        factory = { closeButtonHost }
                    )
                }
            }

            // Source of the results, and the button to change it
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.location_results_by,
                        locationSearchSource.locationSearchAttribution
                    ),
                    color = colors.secondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassIconButton(
                    icon = R.drawable.ic_tune,
                    contentDescription = stringResource(R.string.location_search_change_source),
                    isLight = isLightSearch(),
                    blurRoot = blurRoot,
                    onClick = { viewModel.openDialogLocationSearchSource() }
                )
            }
        }

        if (dialogLocationSourcesOpenState.value && selectedLocation.value != null) {
            SecondarySourcesPreference(
                sourceManager = sourceManager,
                location = selectedLocation.value!!,
                onClose = { location: Location? ->
                    if (location != null) {
                        finishSelf(location)
                    }
                    viewModel.closeDialogLocationSources()
                }
            )
        }

        if (dialogLocationSearchSourceOpenState.value) {
            AlertDialogNoPadding(
                onDismissRequest = { viewModel.closeDialogLocationSearchSource() },
                title = {
                    Text(
                        text = stringResource(R.string.location_search_source),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (BuildConfig.FLAVOR == "freenet") {
                            item {
                                Material3ExpressiveCardListItem(
                                    surface = MaterialTheme.colorScheme.secondaryContainer,
                                    onSurface = MaterialTheme.colorScheme.onSecondaryContainer,
                                    isFirst = true,
                                    isLast = true,
                                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.small_margin))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(
                                            top = dimensionResource(R.dimen.normal_margin),
                                            start = dimensionResource(R.dimen.normal_margin),
                                            end = dimensionResource(R.dimen.normal_margin)
                                        )
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_weather_source_freenet_disclaimer),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (BuildConfig.INSTALL_INSTRUCTIONS_LINK.startsWith("https://") &&
                                            (
                                                !BuildConfig.INSTALL_INSTRUCTIONS_LINK.contains(
                                                    "breezy",
                                                    ignoreCase = true
                                                ) ||
                                                    BreezyWeather.instance.isSignedByBreezy ||
                                                    BreezyWeather.instance.debugMode
                                                )
                                        ) {
                                            TextButton(
                                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                                onClick = {
                                                    dialogLinkOpenState.value = true
                                                }
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.action_learn_more)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                LargeSeparatorItem()
                            }
                        }
                        items(sourceManager.getLocationSearchSources()) {
                            val enabled = it !is RemovedSource &&
                                (it !is ConfigurableSource || it.isConfigured) &&
                                (BuildConfig.FLAVOR != "freenet" || it !is NonFreeNetSource)
                            RadioButton(
                                selected = locationSearchSource.id == it.id,
                                enabled = enabled,
                                onClick = {
                                    if (enabled) {
                                        viewModel.closeDialogLocationSearchSource()
                                        viewModel.setEnabledSource(it.id)
                                        if (text.isNotEmpty()) {
                                            viewModel.requestLocationList(text)
                                        }
                                    }
                                },
                                text = it.getName(context)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.closeDialogLocationSearchSource() }
                    ) {
                        Text(
                            text = stringResource(android.R.string.cancel),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            )
        }

        if (dialogLinkOpenState.value) {
            AlertDialogLink(
                onClose = { dialogLinkOpenState.value = false },
                linkToOpen = BuildConfig.INSTALL_INSTRUCTIONS_LINK
            )
        }
    }

    /** Results, no-results message and search hint, under the floating bar. */
    @Composable
    private fun ResultsContent() {
        val context = LocalContext.current
        val colors = remember { LocationListColors(isLightSearch()) }
        val listResourceState = viewModel.listResource.collectAsState()
        val text by viewModel.query
        val latestTextSearch by viewModel.latestSearch
        val locationSearchSourceState = viewModel.locationSearchSource.collectAsState()
        val locationSearchSource = sourceManager.getLocationSearchSourceOrDefault(locationSearchSourceState.value)
        val searchAsYouType = locationSearchSource.id != NOMINATIM_ID

        Box(modifier = Modifier.fillMaxSize()) {
            val results = listResourceState.value.first
            if (results.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = TOP_BAR_HEIGHT, bottom = 8.dp)
                ) {
                    itemsIndexed(results) { index, location ->
                        SearchResultRow(
                            text = listOf(location.getPlace(context), location.administrationLevels())
                                .filter { it.isNotEmpty() }
                                .distinct()
                                .joinToString(", "),
                            query = text.trim(),
                            colors = colors,
                            showDivider = index != results.lastIndex,
                            onClick = {
                                hideKeyboard()
                                viewModel.setSelectedLocation(location, locationSearchSource)
                            }
                        )
                    }
                }
            } else if (latestTextSearch.isNotEmpty() &&
                latestTextSearch == text.trim() &&
                listResourceState.value.second == LoadableLocationStatus.SUCCESS
            ) {
                // No results, centered like iOS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp, top = TOP_BAR_HEIGHT + 48.dp, bottom = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        tint = colors.secondary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.location_search_no_results_title),
                        color = colors.content,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.location_search_no_results,
                            locationSearchSource.name,
                            latestTextSearch
                        ) + "\n" + stringResource(R.string.location_search_no_results_advice),
                        color = colors.secondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (!searchAsYouType && text.trim().length >= MIN_QUERY_LENGTH) {
                Text(
                    text = stringResource(R.string.location_search_press_search),
                    color = colors.secondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp, top = TOP_BAR_HEIGHT + 32.dp, bottom = 48.dp)
                )
            }
        }
    }

    /** The search field, composed inside its liquid glass host (see [searchFieldHost]). */
    @Composable
    private fun SearchField(colors: LocationListColors) {
        var text by viewModel.query
        val listResourceState = viewModel.listResource.collectAsState()
        val isLoading = viewModel.isLoading.collectAsState()
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.location_list_search_hint),
                        color = colors.secondary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.content, fontSize = 17.sp),
                    cursorBrush = SolidColor(colors.content),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val query = text.trim()
                            if (query.isNotEmpty()) {
                                hideKeyboard()
                                viewModel.requestLocationList(query)
                                viewModel.latestSearch.value = query
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            val loading = listResourceState.value.second == LoadableLocationStatus.LOADING || isLoading.value
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.secondary,
                        strokeWidth = 2.dp
                    )
                } else if (text.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = stringResource(R.string.location_search_clear),
                        tint = colors.secondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(role = Role.Button) {
                                text = ""
                                focusRequester.requestFocus()
                            }
                    )
                }
            }
        }
    }

    /** Whole screen content, blurred by the glass of the floating bar. */
    private val blurRoot: ViewGroup
        get() = findViewById(android.R.id.content)

    private val searchFieldHost: FrameLayout by lazy {
        FrameLayout(this).apply {
            addView(
                GlassContentLayout(this@SearchActivity).apply {
                    addView(
                        ComposeView(this@SearchActivity).apply {
                            setContent {
                                BreezyWeatherTheme {
                                    SearchField(LocationListColors(isLightSearch()))
                                }
                            }
                        },
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    )
                },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            LiquidGlass.applyFloating(
                host = this,
                cornerRadius = dpToPx(22f),
                tint = locationGlassTint(this@SearchActivity, isLightSearch()),
                blurRoot = blurRoot
            )
        }
    }

    private val closeButtonHost: FrameLayout by lazy {
        FrameLayout(this).apply {
            contentDescription = getString(android.R.string.cancel)
            isClickable = true
            isFocusable = true
            foreground = getDrawable(R.drawable.selectable_item_background)
            setOnClickListener { finishSelf(null) }
            addView(
                GlassContentLayout(this@SearchActivity).apply {
                    addView(
                        ImageView(this@SearchActivity).apply {
                            setImageResource(R.drawable.ic_close)
                            imageTintList = ColorStateList.valueOf(
                                if (isLightSearch()) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                            )
                        },
                        FrameLayout.LayoutParams(dpToPx(20f).toInt(), dpToPx(20f).toInt(), Gravity.CENTER)
                    )
                },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            LiquidGlass.applyFloating(
                host = this,
                cornerRadius = dpToPx(22f),
                tint = locationGlassTint(this@SearchActivity, isLightSearch()),
                blurRoot = blurRoot
            )
        }
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            finishSelf(null)
        }
    }

    // init.
    private fun initModel() {
        viewModel = ViewModelProvider(this)[SearchViewModel::class.java]
    }

    // control.
    private fun finishSelf(location: Location?) {
        setResult(RESULT_OK, Intent().putExtra(KEY_LOCATION, location))
        ActivityCompat.finishAfterTransition(this)
    }

    private fun hideKeyboard() {
        currentFocus?.let {
            inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    fun openDialogLocationSearchSource() {
        viewModel.openDialogLocationSearchSource()
    }

    companion object {
        const val KEY_LOCATION = "location"
        private const val NOMINATIM_ID = "nominatim"
        private const val MIN_QUERY_LENGTH = 2
        private const val SEARCH_DEBOUNCE_MS = 300L
        // Bar height plus room for its edge refraction, so the first result isn't pulled into the glass at rest
        private val TOP_BAR_HEIGHT = 80.dp
    }
}

/**
 * A search result, iOS weather style: the place on one line, the part matching the query in bold and
 * full color, the rest dimmed, with a hairline under it.
 */
@Composable
private fun SearchResultRow(
    text: String,
    query: String,
    colors: LocationListColors,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val annotated = buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = colors.secondary), 0, text.length)
        query.split(" ").filter { it.isNotBlank() }.forEach { word ->
            val start = text.indexOf(word, ignoreCase = true)
            if (start >= 0) {
                addStyle(
                    SpanStyle(color = colors.content, fontWeight = FontWeight.SemiBold),
                    start,
                    start + word.length
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp)
    ) {
        Text(
            text = annotated,
            fontSize = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 20.dp, top = 14.dp, bottom = 14.dp)
        )
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.content.copy(alpha = 0.15f))
            )
        }
    }
}
