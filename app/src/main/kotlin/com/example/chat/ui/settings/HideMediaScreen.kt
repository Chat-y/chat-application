package com.example.chat.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.chat.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.BuildConfig
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.ThemeController
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import com.example.chat.ui.kit.GbCardContainer

/**
 * Port of lib/features/settings/universal/hide_media_screen.dart. The scaffold is the shared
 * ChatySettingsPage (HM1-13), matching the Privacy and Security settings chrome; the switch rows
 * keep a single accessible toggle target with an explicit state description because the GB kit
 * tile has no equivalent (HM1-34 partially addressed: shared page chrome, own accessible rows).
 * The .nomedia marker lives in ChatyMedia under the app files dir (Dart
 * getApplicationDocumentsDirectory is the app's files dir on Android). Uncompiled; no pixel check.
 */
@Composable
fun HideMediaScreen(
    preferences: ChatyPreferencesController,
    themeController: ThemeController,
    onBack: () -> Unit,
) {
    // Recomposition is keyed on the controller's version; reads below are narrowed to the three
    // hide values so unrelated preference changes do not re-read them (HM1-12, HM1-45).
// Superseded source retained below per additive-only review rule.
//     val prefsVersion = rememberPreferencesVersion(preferences)
//     val markerState = remember(preferences, preferences.scopeGeneration) { androidx.compose.runtime.mutableStateOf(
//         Triple(preferences.universal.hidePhotosFromGallery, preferences.universal.hideVideosFromGallery, preferences.universal.hideGifsFromGallery)) }
//     androidx.compose.runtime.DisposableEffect(preferences, preferences.scopeGeneration) {
//         val listener: () -> Unit = { markerState.value = Triple(preferences.universal.hidePhotosFromGallery, preferences.universal.hideVideosFromGallery, preferences.universal.hideGifsFromGallery) }
    // Include the owner even when two accounts have identical marker choices.
    fun markerSnapshot() = preferences.scopeGeneration to Triple(
        preferences.universal.hidePhotosFromGallery,
        preferences.universal.hideVideosFromGallery,
        preferences.universal.hideGifsFromGallery,
    )
    val markerState = remember(preferences) { androidx.compose.runtime.mutableStateOf(markerSnapshot()) }
    val accountGeneration = markerState.value.first
    androidx.compose.runtime.DisposableEffect(preferences) {
//         val listener: () -> Unit = { markerState.value = markerSnapshot() }
//         preferences.addListener(listener)
        val listener: () -> Unit = { markerState.value = markerSnapshot() }
        preferences.addListener(listener)
        // A scope change between remember and effect registration must not be missed.
        markerState.value = markerSnapshot()
        onDispose { preferences.removeListener(listener) }
    }
    val theme = themeController.globalTheme
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
// Superseded source retained below per additive-only review rule.
//     val accent = Color(theme.accentColor)
    val accent = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current

    val repository = remember(context) {
        HideMediaRepository(
            filesDir = context.filesDir,
            cacheDir = context.cacheDir,
            additionalCacheDirs = { context.externalCacheDirs.filterNotNull() },
            debug = BuildConfig.DEBUG,
            // Prior comment: One batched scan per type folder; the callback confirms completion (HM1-09, HM3-15, HM3-16).
            // Submit one scan per folder. The null listener below does not confirm scan completion.
            scanMedia = { dir ->
                MediaScannerConnection.scanFile(context, arrayOf(dir.absolutePath), null, null)
            },
            debugLog = { msg -> if (BuildConfig.DEBUG) Log.d("HideMedia", msg) },
        )
    }
    val scope = rememberCoroutineScope()
// Superseded source retained below per additive-only review rule.
//     val state = remember(preferences, repository, scope, snackbar) {
//     val state = remember(preferences, preferences.scopeGeneration, repository, scope, snackbar) {
    val state = remember(preferences, accountGeneration, repository, scope, snackbar) {
        HideMediaState(preferences, repository, scope, snackbar)
    }

// Superseded source retained below per additive-only review rule.
//     val hidePhotos = remember(prefsVersion) { preferences.universal.hidePhotosFromGallery }
//     val hideVideos = remember(prefsVersion) { preferences.universal.hideVideosFromGallery }
//     val hideGifs = remember(prefsVersion) { preferences.universal.hideGifsFromGallery }
//     val hidePhotos = markerState.value.first
//     val hideVideos = markerState.value.second
//     val hideGifs = markerState.value.third
    val hidePhotos = markerState.value.second.first
    val hideVideos = markerState.value.second.second
    val hideGifs = markerState.value.second.third
    val onCount = listOf(hidePhotos, hideVideos, hideGifs).count { it }

    LaunchedEffect(state) { state.onScreenOpen() }

    if (state.showHelp) {
        AlertDialog(
            onDismissRequest = { state.dismissHelp() },
            title = { Text("Where to find your media") },
            text = {
                Column {
                    Text("Open the original chat to view received media inside Chaty. This page does not move or delete chat files.")
                    Text("Local markers are a hint to gallery scanners, not a security boundary. Gallery apps, Files and cloud backups can keep previously exported copies.")
                    Text("To remove an exported copy, use that gallery or backup service. Resetting these preferences does not remove any copy.")
                }
            },
            confirmButton = { TextButton(onClick = { state.dismissHelp() }) { Text("Done") } },
            // HM1-39: a way out when media still appears - open the device gallery to check.
            dismissButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        state.dismissHelp()
                    }
                }) { Text("Open gallery app") }
            },
        )
    }

    if (state.confirmReset) {
        AlertDialog(
            onDismissRequest = { state.confirmReset = false },
            title = { Text("Reset gallery settings?") },
            text = { Text("Turn off all three hide preferences? This does not move, delete or change media already saved to a gallery or cloud backup.") },
            // dismissButton = { TextButton(onClick = { state.confirmReset = false }) { Text("Cancel") } },
            dismissButton = {
                val cancelFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(cancelFocus) {
                    androidx.compose.runtime.withFrameNanos { }
                    cancelFocus.requestFocus()
                }
                TextButton(modifier = Modifier.focusRequester(cancelFocus),
                    onClick = { state.confirmReset = false }) { Text("Cancel") }
            },
            confirmButton = { TextButton(onClick = { state.confirmResetNow() }) { Text("Reset") } },
        )
    }

    Box(Modifier.fillMaxSize()) {
        // ChatySettingsPage(title = "Hide media", onBack = onBack) {
        ChatySettingsPage(title = "Hide media", onBack = onBack,
            titleModifier = Modifier.semantics { heading() }) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 600.dp)) {
                    if (state.bannerVisible) {
                        // First-visit explainer (HM1-59) with the three "what happens" bullets (HM2-50).
                        GbCardContainer {
                            Column(Modifier.padding(16.dp)) {
                                Text("What hide from gallery does", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(6.dp))
// Superseded source retained below per additive-only review rule.
//                                 // Text("Keeps chat media out of gallery scans for the types you choose.", color = Color(theme.secondaryTextColor), fontSize = 14.sp, lineHeight = 20.sp)
                                Text(stringResource(R.string.privacy_media_banner_effect), color = Color(theme.secondaryTextColor), fontSize = 14.sp, lineHeight = 20.sp)
//                                 Text("Files stay inside Chaty; nothing is moved or deleted.", color = Color(theme.secondaryTextColor), fontSize = 14.sp, lineHeight = 20.sp)
//                                 Text("Existing gallery and cloud copies are not changed.", color = Color(theme.secondaryTextColor), fontSize = 14.sp, lineHeight = 20.sp)
                                Text("Maintains markers in app-private folders. Private files are not normally scanned by gallery apps.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
                                Text("Files stay inside Chaty; nothing is moved or deleted.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
// Text("Existing gallery and cloud copies are not changed.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
                                Text(stringResource(R.string.privacy_media_cloud_backup_limit), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
                                TextButton(onClick = { state.dismissBanner() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Got it") }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    // Explanatory text in a card tone directly under the title (HM4-04).
                    GbCardContainer {
                        Text(
                            "These preferences maintain local media markers. Private chat files already stay inside Chaty; existing gallery copies, camera captures and cloud backups are not changed. Hiding is not encryption. Explicit Save to gallery can create a visible copy.",
                            Modifier.padding(16.dp),
// Superseded source retained below per additive-only review rule.
//                             color = Color(theme.secondaryTextColor), fontSize = 14.sp, lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // State summary at the top (HM1-56) as a count row (HM4-09), plus an honest
                    // status line: local markers only, some gallery apps may still show files (HM2-40).
                    Text(
                        if (state.saving) "Saving..." else "$onCount of 3 markers on. Photo marker ${if (hidePhotos) "on" else "off"}, video marker ${if (hideVideos) "on" else "off"}, GIF marker ${if (hideGifs) "on" else "off"}. Existing gallery copies are unchanged.",
// Superseded source retained below per additive-only review rule.
//                         color = Color(theme.secondaryTextColor), modifier = Modifier.padding(8.dp), fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp), fontSize = 14.sp,
                    )
                    Text(
                        "Local markers only; some gallery apps may still show files.",
// Superseded source retained below per additive-only review rule.
//                         color = Color(theme.secondaryTextColor), modifier = Modifier.padding(horizontal = 8.dp), fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp), fontSize = 14.sp,
                    )
                    if (state.affectedCount >= 0) {
                        // HM1-30: how many items the markers currently cover.
                        Text(
                            // "${state.affectedCount} files currently in private chat media folders.",
                            pluralStringResource(R.plurals.privacy_private_media_file_count,
                                state.affectedCount, state.affectedCount),
// Superseded source retained below per additive-only review rule.
//                             color = Color(theme.secondaryTextColor), modifier = Modifier.padding(8.dp), fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp), fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    GbCardContainer {
                        MediaVisibilitySwitch(
                            icon = Icons.Rounded.PhotoLibrary, title = "Photos",
// Superseded source retained below per additive-only review rule.
//                             subtitle = "Keep chat photos out of gallery scans",
                            // subtitle = "Maintain a private photo-folder marker; exports stay visible",
                            subtitle = stringResource(R.string.privacy_media_photos_effect),
//                             value = hidePhotos,
                            value = hidePhotos,
                            enabled = !state.saving,
                            onChanged = { v ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress) // HM4-41
                                state.setHidden(HideMediaRepository.MediaKind.PHOTOS, v)
                            },
                        )
                        MediaVisibilitySwitch(
                            icon = Icons.Rounded.VideoLibrary, title = "Videos",
// Superseded source retained below per additive-only review rule.
//                             subtitle = "Keep chat videos out of gallery scans",
                            // subtitle = "Maintain a private video-folder marker; exports stay visible",
                            subtitle = stringResource(R.string.privacy_media_videos_effect),
//                             value = hideVideos,
                            value = hideVideos,
                            enabled = !state.saving,
                            onChanged = { v ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                state.setHidden(HideMediaRepository.MediaKind.VIDEOS, v)
                            },
                        )
                        MediaVisibilitySwitch(
                            icon = Icons.Rounded.GifBox, title = "GIFs",
// Superseded source retained below per additive-only review rule.
//                             subtitle = "Keep chat GIFs and animated stickers out of gallery scans",
                            // subtitle = "Maintain a private GIF-folder marker; exports stay visible",
                            subtitle = stringResource(R.string.privacy_media_gifs_effect),
//                             value = hideGifs,
                            value = hideGifs,
                            enabled = !state.saving,
                            onChanged = { v ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                state.setHidden(HideMediaRepository.MediaKind.GIFS, v)
                            },
                        )
                    }
                    TextButton(onClick = { state.requestHelp() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("How to find hidden media") }
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = { state.requestReset() },
                            enabled = !state.saving && (hidePhotos || hideVideos || hideGifs),
                            // modifier = Modifier.heightIn(min = 48.dp),
                            modifier = Modifier.minimumInteractiveComponentSize().heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = accent),
                            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                        ) {

//Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp), tint = accent)
                            com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.reset, Icons.Rounded.RestartAlt, null, Modifier.size(18.dp), tint = accent)
                            Spacer(Modifier.width(8.dp))
                            Text("Reset gallery settings", color = accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding().windowInsetsPadding(WindowInsets.navigationBars))
    }
}

/** Single accessible switch target; no duplicate child toggle action. */
@Composable
// private fun MediaVisibilitySwitch(icon: ...)
// internal fun MediaVisibilitySwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, value: Boolean, onChanged: (Boolean) -> Unit) {
internal fun MediaVisibilitySwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, value: Boolean, onChanged: (Boolean) -> Unit) {
    MediaVisibilitySwitch(icon, title, subtitle, value, enabled = true, onChanged = onChanged)
}

@Composable
internal fun MediaVisibilitySwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, value: Boolean, enabled: Boolean, onChanged: (Boolean) -> Unit) {
    val actionLabel = stringResource(if (value) R.string.privacy_media_turn_off else R.string.privacy_media_turn_on)
    var keyboardFocused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .onFocusChanged { keyboardFocused = it.isFocused }
            .border(2.dp, if (keyboardFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(12.dp))
            // .semantics { stateDescription = if (value) "On" else "Off" }
            .semantics {
                stateDescription = if (value) "On" else "Off"
                // Label the existing toggle action; do not add a second action or bypass toggleable.
                onClick(label = actionLabel, action = null)
            }
//             .toggleable(value = value, role = Role.Switch, onValueChange = onChanged)
            .toggleable(value = value, enabled = enabled, role = Role.Switch, onValueChange = onChanged)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
//             checked = value, onCheckedChange = null,
            checked = value, onCheckedChange = null, enabled = enabled,
            // Thumb icon so ON/OFF is not colour-only (HM1-55).
            thumbContent = if (value) {
                {
//Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize))
                com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.check, Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/* Superseded by the ChatySettingsPage migration (HM1-13) and the HideMediaState holder
 * (HM1-46/HM1-20); kept for reference per the comment-out-don't-delete rule.
 *
 * Original layout: custom 56 dp Row title bar (theme.surfaceColor) with a padded
 * ChatyBackButton and a 20.sp bold "Hide media" title over a verticalScroll Column, and a
 * composable-local syncNoMedia/update pair with the same generation guards now living in
 * HideMediaState.update. Behaviour preserved: per-type markers, failure snackbar, reset
 * confirm + Undo.
 *
 * Original header block:
 * Row(
 *     Modifier.fillMaxWidth().background(Color(theme.surfaceColor))
 *         .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
 *     verticalAlignment = Alignment.CenterVertically,
 * ) {
 *     Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
 *     Spacer(Modifier.width(8.dp))
 *     Text("Hide media", modifier = Modifier.weight(1f).semantics { heading() },
 *         color = Color(theme.primaryTextColor), fontSize = 20.sp, fontWeight = FontWeight.Bold)
 * }
 *
 * Original scroll body: Column(Modifier.weight(1f)
 *     .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
 *     .widthIn(max = 600.dp).align(Alignment.CenterHorizontally)
 *     .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp)) { ... }
 */
