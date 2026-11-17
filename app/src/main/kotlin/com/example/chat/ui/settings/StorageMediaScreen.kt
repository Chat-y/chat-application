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
import com.example.chat.ui.settings.state.collectSettingsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    onClearCache: suspend () -> Unit, // awaited completion, not a launch-and-return callback
    cacheLimitMb: Int,
    onCacheLimitChange: (Int) -> Unit,
    cacheUsedBytes: Long = 0L,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    // ST1-20 / ST1-47: collect just the two rendered preferences, only while STARTED.
    val selectedFlow = remember(preferences, scope) {
        com.example.chat.ui.settings.state.settingsSnapshotFlow(
            scope,
            { preferences.universal.sendImagesFullResolutionMb.coerceIn(1, 6) to preferences.gbInt("Img_share_limit", 30).coerceIn(1, 100) },
            preferences::addListener, preferences::removeListener,
        )
    }
    val selected by selectedFlow.collectSettingsState()
    var confirmClear by remember { mutableStateOf(false) }
    // ST1-30: spoken/visible result after clearing the cache.
    var clearedNotice by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    var cacheClearError by remember { mutableStateOf(false) }
    fun clearCache() {
        if (clearingCache) return
        clearingCache = true
        clearedNotice = false
        cacheClearError = false
        scope.launch {
            try {
                onClearCache()
                clearedNotice = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                cacheClearError = true
            } finally { clearingCache = false }
        }
    }
    var pendingLimitMb by remember { mutableStateOf<Int?>(null) }
    // Superseded broad listener retained inactive:
    // DisposableEffect(preferences) {
    // val listener: () -> Unit = { revision += 1 }
    // preferences.addListener(listener)
    // onDispose { preferences.removeListener(listener) }
    // }
    // @Suppress("UNUSED_EXPRESSION") revision
    //
    val serverLimitMb = ChatMediaService.STORAGE_LIMIT_MB
    val fullResolutionMb = selected.first // prior inline controller read
    val highResolutionEnabled = fullResolutionMb >= 4
    val imageShareLimit = selected.second // prior inline controller read
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
                // ST1-04: pipeline trace - the 1/6 MB value only feeds a fallback in
                // ChatMediaService.maxBytes("image") that never applies, because gbFeatures always
                // carries "Up_size_limit" (catalog default 50); the picker (ChatAttachmentsBinding)
                // and the service both gate images at the client's 50 MB storage limit
                // (ChatMediaService.STORAGE_LIMIT_MB; no server-side enforcement is visible in
                // source, so the UI must not call it a server limit). "Up to" stays truthful as an
                // upper bound when a lower stored Up_size_limit applies. State the true limit
                // instead of implying this switch sets it.
                subtitle = "Per-image upload limit: up to $serverLimitMb MB. This switch does not change that limit.",
                // Superseded subtitle retained per owner convention (commented out, not deleted):
                // subtitle = "Image allowance: up to $fullResolutionMb MB (server limit $serverLimitMb MB)",
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
                // Superseded wording kept (see ST1-04 note above: no server-side enforcement is visible in source):
                // subtitle = "Up to $serverLimitMb MB per file right now (server limit)",
                subtitle = "Up to $serverLimitMb MB per file (app limit)",
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
                            enabled = !clearingCache,
                            role = Role.RadioButton,
                            onClick = {
                                if (mb != cacheLimitMb) {
                                    clearedNotice = false // stale "cleared" notice must not outlive a limit change
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
                // ST1-36: destructive icon uses the same error token as its action.
                iconColor = colors.error,
                subtitle = "Free local disk space without deleting server messages",
                trailing = { Text(if (clearingCache) "Clearing..." else "Clear", color = colors.error) },
                enabled = !clearingCache,
                onTap = { confirmClear = true },
            )
            if (clearingCache || cacheClearError) {
                Text(
                    if (clearingCache) "Clearing local media cache..." else "Could not clear all local media. Try again.",
                    color = if (cacheClearError) colors.error else colors.foregroundSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                )
            }
            if (clearedNotice) {
                Text(
                    "Local media cache cleared.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                )
            }
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
                // Previous optimistic result: confirmClear = false; onClearCache(); clearedNotice = true
                TextButton(onClick = { confirmClear = false; clearCache() }, enabled = !clearingCache) { Text("Clear", color = colors.error) }
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
