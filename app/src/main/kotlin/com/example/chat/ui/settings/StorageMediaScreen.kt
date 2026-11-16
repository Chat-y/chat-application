package com.example.chat.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.UploadFile
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.chat.data.services.ChatMediaService
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.data.local.MediaCacheSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import com.example.chat.ui.theme.LocalAppColors

/** Cache limit choices, built once (ST1-21). */
private val CACHE_OPTIONS: List<Pair<Int, String>> = listOf(500 to "500 MB", 1024 to "1 GB", 2048 to "2 GB")

private fun storageSizeLabel(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) String.format(java.util.Locale.getDefault(), "%.1f GB", mb / 1024.0)
    else String.format(java.util.Locale.getDefault(), "%d MB", mb.toLong())
}

/**
 * Storage controls backed by the same preference keys consumed by the media
 * pipeline. Unsupported automatic-download switches are deliberately not
 * rendered until a real background-download policy is available.
 */
@Composable
fun StorageMediaScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    onClearCache: () -> Unit,
    cacheLimitMb: Int,
    onCacheLimitChange: (Int) -> Unit,
    cacheUsedBytes: Long = 0L,
) {
    val colors = LocalAppColors.current
    var revision by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingLimitMb by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(preferences) {
        val listener: () -> Unit = { revision += 1 }
        preferences.addListener(listener)
        onDispose { preferences.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") revision

    val serverLimitMb = ChatMediaService.STORAGE_LIMIT_MB
    val fullResolutionMb = preferences.universal.sendImagesFullResolutionMb.coerceIn(1, 6)
    val highResolutionEnabled = fullResolutionMb >= 4
    val imageShareLimit = preferences.gbInt("Img_share_limit", 30).coerceIn(1, 100)
    val batchShareEnabled = imageShareLimit >= 100

    ChatySettingsPage("Storage & Data", "Upload preferences and local media retention", onBack = onBack) {
        ChatySettingsSection(
            "Media upload & quality",
            "These controls update the limits used when you send media.",
        ) {
            ChatySwitchTile(
                icon = Icons.Rounded.Hd,
                iconColor = colors.primary,
                title = "High-resolution images",
                subtitle = "Image allowance: up to $fullResolutionMb MB (server limit $serverLimitMb MB)",
                value = highResolutionEnabled,
                onChanged = { enabled ->
                    preferences.updateUniversal(
                        preferences.universal.copy(sendImagesInFullResolutionMb = if (enabled) 6.0 else 1.0),
                        logTitle = "Image upload resolution",
                    )
                },
            )
            ChatySettingsRowDivider()
            // ST1-01/02/03: the 700 MB switch promised more than the pipeline allows (maxBytes clamps to the
            // server limit), so it is commented out and replaced by a read-only row with the real limit.
            // ChatySwitchTile(
            //     icon = Icons.Rounded.UploadFile, iconColor = colors.primary,
            //     title = "Large uploads (up to 700 MB)",
            //     subtitle = if (largeUploadsEnabled) "Large-file upload limit enabled" else "Upload limit set to 100 MB",
            //     value = largeUploadsEnabled,
            //     onChanged = { enabled -> preferences.updateGbFeature("Up_size_limit", if (enabled) 700.0 else 100.0, logTitle = "Maximum media upload size") },
            // )
            ChatySettingsTile(
                icon = Icons.Rounded.UploadFile,
                iconColor = colors.primary,
                title = "Upload size limit",
                subtitle = "Up to $serverLimitMb MB per file right now (server limit)",
            )
            ChatySettingsRowDivider()
            ChatySwitchTile(
                icon = Icons.Outlined.PhotoLibrary,
                iconColor = colors.primary,
                title = "Send many images at once",
                subtitle = "Image selection limit: $imageShareLimit (switch sets 30 or 100)",
                value = batchShareEnabled,
                onChanged = { enabled ->
                    preferences.updateGbFeature(
                        "Img_share_limit",
                        if (enabled) 100 else 30,
                        logTitle = "Image batch selection limit",
                    )
                },
            )
        }
        ChatySettingsSection("Automatic media download") {
            ChatySettingsTile(
                icon = Icons.Outlined.PhotoLibrary,
                iconColor = colors.primary,
                title = "Media is downloaded when you tap it",
                subtitle = "Automatic Wi-Fi and mobile download rules are not in this build.",
            )
        }
        // ST1-12: the empty section was replaced by the row above.
        // ChatySettingsSection("Automatic Media Download", "Automatic background-download controls are not available in this build. Media is downloaded only through an explicit user action.") { }
        ChatySettingsSection(
            "Local storage",
            "Cache holds encrypted media you opened or downloaded, including profile pictures, stickers and voice notes. Lowering the limit removes the oldest items. Server copies are never deleted.",
        ) {
            ChatySettingsTile(
                icon = Icons.Rounded.Storage,
                iconColor = colors.primary,
                title = "Cache in use",
                subtitle = "Using ${storageSizeLabel(cacheUsedBytes)} of ${storageSizeLabel(cacheLimitMb * 1024L * 1024L)}",
            )
            ChatySettingsRowDivider()
            Column(Modifier.selectableGroup()) {
                CACHE_OPTIONS.forEach { (mb, label) ->
                    val isSelected = cacheLimitMb == mb
                    Box(
                        Modifier.selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                if (mb != cacheLimitMb) {
                                    if (mb * 1024L * 1024L < cacheUsedBytes) pendingLimitMb = mb else onCacheLimitChange(mb)
                                }
                            },
                        ),
                    ) {
                        ChatySettingsTile(
                            title = "Cache limit: $label",
                            icon = Icons.Rounded.Storage,
                            iconColor = colors.primary,
                            badgeText = if (mb == MediaCacheSettings.DEFAULT_MB) "Default" else null,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            trailing = {
                                Box(
                                    Modifier.size(20.dp).clip(CircleShape)
                                        .border(BorderStroke(2.dp, if (isSelected) colors.primary else colors.border), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { if (isSelected) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.primary)) }
                            },
                        )
                    }
                    if (mb != CACHE_OPTIONS.last().first) ChatySettingsRowDivider()
                }
            }
            ChatySettingsRowDivider()
            ChatySettingsTile(
                title = "Clear local media cache",
                icon = Icons.Rounded.CleaningServices,
                iconColor = colors.primary,
                subtitle = "Free local disk space without deleting server messages",
                trailing = { Text("Clear", color = colors.error) },
                onTap = { confirmClear = true },
            )
        }
    }

    if (confirmClear) {
        com.example.chat.ui.kit.ChatyConfirmationDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear local media cache?") },
            text = {
                Text(
                    "This frees ${storageSizeLabel(cacheUsedBytes)} on this device. Media saved only here, such as expiring or view-once items, is removed too. Server copies are untouched.",
                )
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = colors.primary) } },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearCache() }) { Text("Clear", color = colors.error) }
            },
        )
    }
    pendingLimitMb?.let { newMb ->
        com.example.chat.ui.kit.ChatyConfirmationDialog(
            onDismissRequest = { pendingLimitMb = null },
            title = { Text("Lower the cache limit?") },
            text = {
                Text(
                    "The cache uses ${storageSizeLabel(cacheUsedBytes)}. Setting ${storageSizeLabel(newMb * 1024L * 1024L)} removes the oldest local media now. Server copies are untouched.",
                )
            },
            dismissButton = { TextButton(onClick = { pendingLimitMb = null }) { Text("Cancel", color = colors.primary) } },
            confirmButton = {
                TextButton(onClick = { pendingLimitMb = null; onCacheLimitChange(newMb) }) { Text("Lower limit", color = colors.error) }
            },
        )
    }
}
