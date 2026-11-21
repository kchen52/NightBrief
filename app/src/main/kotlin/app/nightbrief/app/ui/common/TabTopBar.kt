package app.nightbrief.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.nightbrief.app.R
import app.nightbrief.app.ui.icons.NightBriefIcons

/**
 * The top bar every tab shares: night vision, an optional refresh and share, then settings.
 *
 * [share] is a slot because the button owns its own dialog. A tab without a share action passes
 * null and the icon is omitted, so the remaining actions stay in the same order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(
    title: @Composable () -> Unit,
    nightVision: Boolean,
    onToggleNightVision: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    share: (@Composable () -> Unit)? = null,
) {
    Box {
        GlassTopBarBackdrop(Modifier.matchParentSize())
        TopAppBar(
            title = title,
            actions = {
                GlassActionCluster {
                    Action(highlighted = nightVision) {
                        IconButton(onClick = onToggleNightVision) {
                            Icon(
                                NightBriefIcons.Visibility,
                                contentDescription = stringResource(
                                    if (nightVision) R.string.night_vision_on else R.string.night_vision_off,
                                ),
                                tint = if (nightVision) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (onRefresh != null) {
                        Action {
                            IconButton(onClick = onRefresh) {
                                Icon(NightBriefIcons.Refresh, stringResource(R.string.refresh_forecast))
                            }
                        }
                    }
                    if (share != null) Action { share() }
                    Action {
                        IconButton(onClick = onOpenSettings) {
                            Icon(NightBriefIcons.Settings, stringResource(R.string.settings))
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }
}
