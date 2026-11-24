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

package org.breezyweather

import org.breezyweather.ui.common.frosty.BuildTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildTierTest {

    @Test
    fun resolveMapsKnownTiersCaseInsensitively() {
        assertEquals(BuildTier.RELEASE, BuildTier.resolve("release"))
        assertEquals(BuildTier.PRERELEASE, BuildTier.resolve("prerelease"))
        assertEquals(BuildTier.PRERELEASE, BuildTier.resolve("PreRelease"))
        assertEquals(BuildTier.DEBUG, BuildTier.resolve("debug"))
    }

    @Test
    fun resolveFallsBackToDebugFlagWhenUnknown() {
        assertEquals(BuildTier.DEBUG, BuildTier.resolve("", isDebugFallback = true))
        assertEquals(BuildTier.RELEASE, BuildTier.resolve("", isDebugFallback = false))
        assertEquals(BuildTier.RELEASE, BuildTier.resolve(null, isDebugFallback = false))
    }

    @Test
    fun formatVersionIncludesCommitShaOnlyForDebug() {
        assertEquals(
            "1.0.0-r42 · 06fe8785",
            BuildTier.formatVersion(BuildTier.DEBUG, "1.0.0-r42", "06fe8785")
        )
        assertEquals(
            "1.0.0-r42",
            BuildTier.formatVersion(BuildTier.DEBUG, "1.0.0-r42", "")
        )
        assertEquals(
            "1.0.0",
            BuildTier.formatVersion(BuildTier.PRERELEASE, "1.0.0", "06fe8785")
        )
        assertEquals(
            "1.0.0",
            BuildTier.formatVersion(BuildTier.RELEASE, "1.0.0", "06fe8785")
        )
    }
}
