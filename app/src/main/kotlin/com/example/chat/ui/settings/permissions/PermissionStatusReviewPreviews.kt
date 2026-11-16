package com.example.chat.ui.settings.permissions

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme

/** Offline status-only fixtures. No permission APIs, capability probes, settings intents or accounts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionStatusesFixture(dark: Boolean = false) {
    ChatyTheme(config = if (dark) ThemePresets.monochromeDark else ThemePresets.monochromeLight) {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Permission status review")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip("Camera", PermStatus.Granted, Icons.Rounded.CameraAlt)
                    StatusChip("Location", PermStatus.Approximate, Icons.Rounded.LocationOn)
                    StatusChip("Photos", PermStatus.Limited, Icons.Rounded.PermMedia)
                    StatusChip("Camera", PermStatus.Denied, Icons.Rounded.CameraAlt)
                    StatusChip("Location", PermStatus.PermanentlyDenied, Icons.Rounded.LocationOn)
                    StatusChip("Photos", PermStatus.Checking, Icons.Rounded.PermMedia)
                }
                PermissionSystemSettingsButton {}
            }
        }
    }
}

@Preview(name = "Permission statuses light", widthDp = 360, showBackground = true)
@Composable private fun PermissionStatusesLightReview() = PermissionStatusesFixture()

@Preview(name = "Permission statuses dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable private fun PermissionStatusesDarkReview() = PermissionStatusesFixture(dark = true)

@Preview(name = "Permission statuses 200 percent", widthDp = 320, fontScale = 2f, showBackground = true)
@Composable private fun PermissionStatusesLargeTextReview() = PermissionStatusesFixture()
