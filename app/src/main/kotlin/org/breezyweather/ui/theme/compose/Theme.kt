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

package org.breezyweather.ui.theme.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.breezyweather.ui.common.frosty.FrostyColors
import org.breezyweather.ui.common.frosty.LocalFrostyColors
import org.breezyweather.ui.common.frosty.toColorScheme
import org.breezyweather.ui.details.DetailsPalette
import org.breezyweather.ui.details.LocalDetailsPalette

/**
 * The app theme: iOS system colours (no Material You dynamic colour) and no ripples, iOS-style.
 */
@Composable
fun BreezyWeatherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) FrostyColors.Dark else FrostyColors.Light

    MaterialTheme(
        colorScheme = colors.toColorScheme(),
        typography = Typography
    ) {
        CompositionLocalProvider(
            LocalFrostyColors provides colors,
            LocalDetailsPalette provides if (darkTheme) DetailsPalette.Dark else DetailsPalette.Light,
            LocalRippleConfiguration provides null,
            content = content
        )
    }
}

@Composable
fun themeRipple(
    bounded: Boolean = true,
) = ripple(
    color = MaterialTheme.colorScheme.primary,
    bounded = bounded
)
