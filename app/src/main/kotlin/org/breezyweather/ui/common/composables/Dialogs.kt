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

package org.breezyweather.ui.common.composables

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.breezyweather.R
import org.breezyweather.ui.common.frosty.FrostyAlert
import org.breezyweather.ui.common.frosty.FrostyButton
import org.breezyweather.ui.common.frosty.FrostyButtonStyle
import org.breezyweather.ui.common.frosty.FrostySheet
import org.breezyweather.ui.common.frosty.FrostyType
import org.breezyweather.ui.common.frosty.LocalFrostyDialogButtons
import org.breezyweather.ui.common.frosty.frostyColors
import org.breezyweather.ui.common.frosty.frostyDimClickable

@Composable
fun AlertDialogConfirmOnly(
    @StringRes title: Int,
    @StringRes content: Int,
    @StringRes confirmButtonText: Int,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconId: Int? = null,
    onDismiss: (() -> Unit)? = null,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = {
            onDismiss?.invoke()
        },
        confirmButton = {
            TextButton(onClick = { onConfirm() }) {
                Text(stringResource(confirmButtonText))
            }
        },
        title = {
            Text(stringResource(title))
        },
        text = {
            Text(stringResource(content))
        },
        icon = iconId?.let {
            {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    tint = frostyColors.tint,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    )
}

@Composable
fun AlertDialogLink(
    onClose: () -> Unit,
    linkToOpen: String,
) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        text = {
            Text(text = stringResource(R.string.about_open_link_message, linkToOpen))
        },
        onDismissRequest = {
            onClose()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    uriHandler.openUri(linkToOpen)
                    onClose()
                }
            ) {
                Text(text = stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onClose() }) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

/**
 * Drop-in for Material's AlertDialog, drawn as an iOS 26 alert. The colour, shape and elevation
 * parameters are accepted for compatibility and ignored.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") iconContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") titleContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") textContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) = FrostyAlert(
    onDismissRequest = onDismissRequest,
    buttons = listOfNotNull(dismissButton, confirmButton),
    modifier = modifier,
    icon = icon,
    title = title,
    text = text,
    properties = properties
)

/**
 * A dialog with free-form content (lists, preferences): drawn as an iOS sheet that slides up from
 * the bottom. The colour, shape and elevation parameters are accepted for compatibility and ignored.
 */
@Composable
fun AlertDialogNoPadding(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") iconContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") titleContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") textContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") tonalElevation: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") properties: DialogProperties = DialogProperties(),
) = FrostySheet(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    titleContent = title,
    buttons = listOfNotNull(dismissButton, confirmButton),
    scrollable = false
) {
    if (icon != null) {
        Box(
            Modifier
                .padding(bottom = 12.dp)
                .align(Alignment.CenterHorizontally)
        ) { icon() }
    }
    if (text != null) {
        CompositionLocalProvider(LocalTextStyle provides FrostyType.subheadline) {
            Box(Modifier.weight(1f, fill = false)) { text() }
        }
    }
}

/**
 * Drop-in for Material's TextButton. Inside alerts and sheets it is a full-width capsule; elsewhere
 * a borderless button that dims while pressed. Extra Material parameters are ignored.
 */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalFrostyDialogButtons.current) {
        FrostyButton(
            onClick = onClick,
            enabled = enabled,
            style = FrostyButtonStyle.Gray,
            modifier = modifier.fillMaxWidth(),
            content = content
        )
    } else {
        CompositionLocalProvider(
            LocalTextStyle provides FrostyType.body.copy(color = frostyColors.tint)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .frostyDimClickable(enabled = enabled, onClick = onClick)
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * Drop-in for Material's filled Button: an iOS filled capsule.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    content: @Composable RowScope.() -> Unit,
) = FrostyButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    style = FrostyButtonStyle.Filled,
    contentPadding = contentPadding,
    content = content
)
