/*
 * This file is part of Frosty Weather, a modified version of Breezy Weather.
 *
 * Frosty Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Frosty Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Frosty Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.ui.common.frosty

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.breezyweather.BuildConfig
import org.breezyweather.R

/**
 * Distribution tier of the installed build, matching its app icon sky tint.
 */
enum class BuildTier(
    @StringRes val labelRes: Int,
    val lightAccent: Color,
    val darkAccent: Color,
) {
    RELEASE(
        labelRes = R.string.about_build_tier_release,
        lightAccent = Color(0xFF1F78D8),
        darkAccent = Color(0xFF5CB6FF)
    ),
    PRERELEASE(
        labelRes = R.string.about_build_tier_prerelease,
        lightAccent = Color(0xFF6B44D6),
        darkAccent = Color(0xFFA88BFF)
    ),
    DEBUG(
        labelRes = R.string.about_build_tier_debug,
        lightAccent = Color(0xFFC2344B),
        darkAccent = Color(0xFFFF687D)
    ),
    ;

    @Composable
    fun accentColor(): Color = if (frostyColors.isDark) darkAccent else lightAccent

    companion object {
        val current: BuildTier
            get() = resolve(BuildConfig.BUILD_TIER, BuildConfig.DEBUG)

        fun resolve(tier: String?, isDebugFallback: Boolean = false): BuildTier = when (tier?.lowercase()) {
            "debug" -> DEBUG
            "prerelease" -> PRERELEASE
            "release" -> RELEASE
            else -> if (isDebugFallback) DEBUG else RELEASE
        }

        fun formatVersion(
            tier: BuildTier,
            versionName: String,
            commitSha: String,
        ): String = when {
            tier == DEBUG && commitSha.isNotBlank() -> "$versionName · $commitSha"
            else -> versionName
        }

        fun currentVersionLabel(): String = formatVersion(
            tier = current,
            versionName = BuildConfig.VERSION_NAME,
            commitSha = BuildConfig.COMMIT_SHA
        )
    }
}

/**
 * Compact color-coded pill indicating the installed build tier (Release, Pre-release, or Debug).
 */
@Composable
fun FrostyBuildTierBadge(
    tier: BuildTier = BuildTier.current,
    modifier: Modifier = Modifier,
) {
    val isDark = frostyColors.isDark
    val accent = tier.accentColor()
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = if (isDark) 0.22f else 0.13f))
            .border(
                width = 0.75.dp,
                color = accent.copy(alpha = if (isDark) 0.45f else 0.30f),
                shape = CircleShape
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = stringResource(tier.labelRes),
            style = FrostyType.caption.copy(fontWeight = FontWeight.SemiBold),
            color = accent,
            maxLines = 1
        )
    }
}

/**
 * Inset Frosty settings card showing the tinted app icon, version, and build tier badge.
 */
@Composable
fun FrostyBuildTierCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tier: BuildTier = BuildTier.current,
) {
    FrostyCell(
        modifier = modifier,
        isFirst = true,
        isLast = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = FrostyCellPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_round),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.brand_name),
                    style = FrostyType.headline,
                    color = frostyColors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = BuildTier.currentVersionLabel(),
                    style = FrostyType.footnote,
                    color = frostyColors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            FrostyBuildTierBadge(tier = tier)
            Spacer(Modifier.width(8.dp))
            FrostyChevron()
        }
    }
}
