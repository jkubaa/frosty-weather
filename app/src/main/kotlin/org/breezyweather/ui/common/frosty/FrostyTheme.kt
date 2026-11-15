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

package org.breezyweather.ui.common.frosty

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The iOS system colours the app's non-home screens (settings, dialogs, sheets) are drawn with.
 */
@Immutable
data class FrostyColors(
    val isDark: Boolean,
    /** Screen background behind inset grouped lists. */
    val groupedBackground: Color,
    /** Background of a cell in an inset grouped list. */
    val cell: Color,
    /** Background of sheets and alerts, which sit above the screen. */
    val elevatedBackground: Color,
    /** Cell colour inside a sheet. */
    val elevatedCell: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    /** Toggle track when off, slider track. */
    val fill: Color,
    /** Buttons and text fields inside alerts and sheets. */
    val tertiaryFill: Color,
    /** Cell highlight while pressed. */
    val highlight: Color,
    val tint: Color,
    val red: Color,
    val green: Color,
    val orange: Color,
    val yellow: Color,
    val teal: Color,
    val indigo: Color,
    val purple: Color,
    val pink: Color,
    val gray: Color,
) {
    companion object {
        val Light = FrostyColors(
            isDark = false,
            groupedBackground = Color(0xFFF2F2F7),
            cell = Color.White,
            elevatedBackground = Color(0xFFF2F2F7),
            elevatedCell = Color.White,
            label = Color.Black,
            secondaryLabel = Color(0x993C3C43),
            tertiaryLabel = Color(0x4D3C3C43),
            separator = Color(0x4A3C3C43),
            fill = Color(0x33787880),
            tertiaryFill = Color(0x1F767680),
            highlight = Color(0xFFD1D1D6),
            tint = Color(0xFF007AFF),
            red = Color(0xFFFF3B30),
            green = Color(0xFF34C759),
            orange = Color(0xFFFF9500),
            yellow = Color(0xFFFFCC00),
            teal = Color(0xFF30B0C7),
            indigo = Color(0xFF5856D6),
            purple = Color(0xFFAF52DE),
            pink = Color(0xFFFF2D55),
            gray = Color(0xFF8E8E93)
        )
        val Dark = FrostyColors(
            isDark = true,
            groupedBackground = Color.Black,
            cell = Color(0xFF1C1C1E),
            elevatedBackground = Color(0xFF1C1C1E),
            elevatedCell = Color(0xFF2C2C2E),
            label = Color.White,
            secondaryLabel = Color(0x99EBEBF5),
            tertiaryLabel = Color(0x4DEBEBF5),
            separator = Color(0x99545458),
            fill = Color(0x5C787880),
            tertiaryFill = Color(0x3D767680),
            highlight = Color(0xFF3A3A3C),
            tint = Color(0xFF0A84FF),
            red = Color(0xFFFF453A),
            green = Color(0xFF30D158),
            orange = Color(0xFFFF9F0A),
            yellow = Color(0xFFFFD60A),
            teal = Color(0xFF40C8E0),
            indigo = Color(0xFF5E5CE6),
            purple = Color(0xFFBF5AF2),
            pink = Color(0xFFFF375F),
            gray = Color(0xFF8E8E93)
        )
    }
}

val LocalFrostyColors = staticCompositionLocalOf { FrostyColors.Light }

val frostyColors: FrostyColors
    @Composable get() = LocalFrostyColors.current

/**
 * Material colour scheme mapped onto the iOS system colours, so any Material component left in
 * the app picks up the same palette (no Material You tints).
 */
fun FrostyColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = tint,
        onPrimary = Color.White,
        primaryContainer = tint.copy(alpha = 0.18f),
        onPrimaryContainer = tint,
        inversePrimary = tint,
        secondary = tint,
        onSecondary = Color.White,
        secondaryContainer = tertiaryFill,
        onSecondaryContainer = label,
        tertiary = indigo,
        onTertiary = Color.White,
        tertiaryContainer = indigo.copy(alpha = 0.18f),
        onTertiaryContainer = label,
        background = groupedBackground,
        onBackground = label,
        surface = groupedBackground,
        onSurface = label,
        surfaceVariant = cell,
        onSurfaceVariant = secondaryLabel,
        surfaceTint = Color.Transparent,
        surfaceBright = elevatedCell,
        surfaceDim = groupedBackground,
        surfaceContainerLowest = cell,
        surfaceContainerLow = cell,
        surfaceContainer = cell,
        surfaceContainerHigh = elevatedCell,
        surfaceContainerHighest = elevatedCell,
        inverseSurface = label,
        inverseOnSurface = groupedBackground,
        error = red,
        onError = Color.White,
        errorContainer = red.copy(alpha = 0.18f),
        onErrorContainer = red,
        outline = separator,
        outlineVariant = separator,
        scrim = Color.Black
    )
}

/**
 * iOS text styles (Dynamic Type "Large" sizes).
 */
object FrostyType {
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.1).sp)
    val subheadline = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.1).sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
}

/**
 * Material typography mapped onto iOS sizes, applied locally to settings screens, alerts and
 * sheets (the home and detail screens keep their own type scale).
 */
@Composable
fun frostyMaterialTypography(): Typography {
    val t = MaterialTheme.typography
    return t.copy(
        displaySmall = t.displaySmall.merge(FrostyType.largeTitle),
        headlineLarge = t.headlineLarge.merge(FrostyType.largeTitle),
        headlineMedium = t.headlineMedium.merge(FrostyType.title2),
        headlineSmall = t.headlineSmall.merge(FrostyType.headline),
        titleLarge = t.titleLarge.merge(FrostyType.title3),
        titleMedium = t.titleMedium.merge(FrostyType.body),
        titleSmall = t.titleSmall.merge(FrostyType.subheadline.copy(fontWeight = FontWeight.SemiBold)),
        bodyLarge = t.bodyLarge.merge(FrostyType.body),
        bodyMedium = t.bodyMedium.merge(FrostyType.subheadline),
        bodySmall = t.bodySmall.merge(FrostyType.footnote),
        labelLarge = t.labelLarge.merge(FrostyType.headline.copy(fontWeight = FontWeight.Medium)),
        labelMedium = t.labelMedium.merge(FrostyType.footnote),
        labelSmall = t.labelSmall.merge(FrostyType.caption)
    )
}

/**
 * Applies the iOS type scale to [content].
 */
@Composable
fun ProvideFrostyTypography(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = frostyMaterialTypography(),
        content = content
    )
}
