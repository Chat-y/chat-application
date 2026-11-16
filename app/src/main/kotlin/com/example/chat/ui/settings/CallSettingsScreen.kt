package com.example.chat.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import com.example.chat.ui.kit.ChatySingleChoiceModal
import com.example.chat.ui.kit.ChatyConfirmationDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Videocam
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.calls.CallPresentationPreferences
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** Contact row for the exceptions dialog (dataStore.contacts id + displayName). */
data class CallContactOption(val id: String, val displayName: String)

/** Call presentation state mirrored from CallPresentationPreferencesStore.value;
 *  the caller maps its store into this and wires the setters below.
 *  CS1-59: defaults reference CallPresentationPreferences.defaults (single
 *  definition) instead of a second set of literals that could drift. */
data class CallPresentationState(
    val dynamicIslandEnabled: Boolean = CallPresentationPreferences.defaults.dynamicIslandEnabled,
    val pictureInPictureEnabled: Boolean = CallPresentationPreferences.defaults.pictureInPictureEnabled,
    val lowDataUsageEnabled: Boolean = CallPresentationPreferences.defaults.lowDataUsageEnabled,
    val incomingCallTemplate: String,
    val ongoingCallTemplate: String,
    val callExperienceBundleId: String = CallPresentationPreferences.defaults.callExperienceBundleId,
)

// CS3-16: internal (was private) so the audience-mapping unit test can pin the picker contract.
internal val audienceOptions = listOf("Everyone", "My Contacts", "My Contacts Except…", "Nobody")
private const val exceptAudience = "My Contacts Except…"
private const val everyoneAudience = "Everyone"
private const val myContactsAudience = "My Contacts"

/** CS1-19 / CS3-01: dialogs as an enum instead of the retired magic ints 0/1/4. */
private enum class CallSettingsDialog { None, WhoCanCall, ExcludedContacts, ConfirmEveryone, ResetCallSettings }

/** CS1-01: count-aware label for the Excluded contacts tile.
 *  CS5-37 (resource plurals) stays open: this codebase keeps strings inline,
 *  so the English plural rule lives here. */
internal fun excludedContactsLabel(count: Int): String = when (count) {
    0 -> "No contacts excluded"
    1 -> "1 contact excluded"
    else -> "$count contacts excluded"
}

/** CS1-50 / CS3-25: one shared chevron instead of an inline lambda per tile. */
@Composable
private fun CallSettingsChevron() {
    com.example.chat.ui.icons.ChatyIcon(
        com.example.chat.ui.icons.ChatyIconId.chevronRight,
        semanticLabel = null,
        color = LocalAppColors.current.foregroundSecondary,
    )
}

/** Call settings expose one global bundle choice.
 *  CS1-02: the legacy per-screen template parameters (templates, onIncomingTemplate,
 *  onOngoingTemplate) were never used by the body and are removed; the persisted
 *  template values stay in CallPresentationPreferences / CallPresentationState,
 *  owned by the Calls worker.
 *
 *  CS1-11: dynamicIslandEnabled / pictureInPictureEnabled default ON in the
 *  store; a release-notes note about that behaviour change remains a release
 *  process task (not code). */
@Composable
fun CallSettingsScreen(
    preferences: ChatyPreferencesController,
    call: CallPresentationState,
    contacts: List<CallContactOption>,
    onBack: () -> Unit,
    onDynamicIsland: (Boolean) -> Unit,
    onPictureInPicture: (Boolean) -> Unit,
    onLowData: (Boolean) -> Unit,
    onOpenAudioSettings: () -> Unit,
    // CS1-17: required, no default no-op (was: onOpenCallBundles: () -> Unit = {}).
    onOpenCallBundles: () -> Unit,
    // CS1-10: server-side audience label when the host loaded it; null hides the line (no sync claim).
    serverCallAudience: String? = null,
    // CS1-42 / CS2-04: nullable so unhosted callers still compile; the row is hidden when absent
    // (same nullable-loader pattern as PrivacyCenterScreen's loadCallPolicy/loadBlocked).
    onOpenBlockedContacts: (() -> Unit)? = null,
    // CS4-49: nullable so unhosted callers still compile; the reset row is hidden when absent.
    onResetCallSettings: (suspend () -> Unit)? = null,
) {
// Previous implementation retained:     rememberPreferencesVersion(preferences)
    // CS1-21: observe only the privacy group read by this screen, not every preference.
    var observedPrivacy by remember(preferences) { mutableStateOf(preferences.privacy) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    DisposableEffect(preferences) {
        var active = true
        val listener: () -> Unit = {
            mainHandler.post { if (active) observedPrivacy = preferences.privacy }
        }
        preferences.addListener(listener)
        observedPrivacy = preferences.privacy
        onDispose { active = false; preferences.removeListener(listener) }
    }
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val view = LocalView.current
// Previous implementation retained:     val privacy = preferences.privacy
    val privacy = observedPrivacy
    val accountGeneration = remember(preferences) { preferences.scopeGeneration }
    fun ownsCallSettings(): Boolean = preferences.scopeGeneration == accountGeneration
    // CS1-19 + CS1-20: enum dialog state that survives rotation (was: var dialog by remember { mutableStateOf(0) }).
    var dialog by rememberSaveable { mutableStateOf(CallSettingsDialog.None) }
    // CS1-04: audience before "My Contacts Except…" was chosen, so cancelling an
    // empty exception list can restore it instead of silently keeping "Except…".
    var audienceBeforeExceptions by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var resetBusy by remember { mutableStateOf(false) }

    // CS1-12 / CS1-13 / CS1-58: runtime capability checks (recomputed each composition).
    val pipSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    val overlayAllowed = Settings.canDrawOverlays(context)
    fun openOverlaySettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
    }
    fun openAppSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            })
        }
    }

    fun applyAudience(chosen: String) {
        if (!ownsCallSettings()) return
        preferences.updateCallPrivacy(whoCanCallMe = chosen, logTitle = "Who Can Call Me") // CS3-49
        if (chosen == exceptAudience) {
            audienceBeforeExceptions = privacy.whoCanCallMe
            dialog = CallSettingsDialog.ExcludedContacts
        } else {
            dialog = CallSettingsDialog.None
        }
    }

    Box(Modifier.fillMaxSize()) {
    // was: ChatySettingsPage("Calls", "Incoming call privacy, background audio & visual island", onBack = onBack)
    ChatySettingsPage("Calls", "Incoming call privacy and call appearance", onBack = onBack) {
        // CS1-39 / CS4-01: sentence-case section and tile titles.
        ChatySettingsSection("Incoming call privacy", "Control who is permitted to initiate voice and video calls with you.") {
// Previous implementation retained:             ChatySettingsTile(
            CallSettingsListTile(
                title = "Who can call me", // was: "Who Can Call Me"
                icon = Icons.Rounded.RingVolume,
                iconColor = colors.primary,
// Previous implementation retained:                 subtitle = privacy.whoCanCallMe,
// Previous implementation retained:                 trailing = { CallSettingsChevron() },
                // Prior: subtitle = "Choose your call audience", trailing row showed privacy.whoCanCallMe + chevron.
                // CS4-35: keep the stored audience in wrapping supporting text, not a fixed-width trailing slot.
                // This is the local preference, not a claim about server enforcement.
                // CS1-10: local choice first; server copy appended only when the host actually loaded it.
                subtitle = if (serverCallAudience != null) privacy.whoCanCallMe + " \u00b7 On the server: " + serverCallAudience else privacy.whoCanCallMe,
                onTap = { dialog = CallSettingsDialog.WhoCanCall },
                // CS4-33: announce audience changes to TalkBack.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (privacy.whoCanCallMe == exceptAudience) {
                ChatySettingsRowDivider()
// Previous implementation retained:                 ChatySettingsTile(
                CallSettingsListTile(
                    title = "Excluded contacts", // was: "Excluded Contacts"
                    icon = Icons.Outlined.PersonOff,
                    // CS4-27: not an error state; one icon colour family (CS4-26).
                    iconColor = colors.primary, // was: colors.error
                    // CS1-01: real count instead of the literal " contacts excluded".
                    subtitle = excludedContactsLabel(privacy.whoCanCallMeExceptions.size),
                    onTap = {
                        audienceBeforeExceptions = null
                        dialog = CallSettingsDialog.ExcludedContacts
                    },
                )
            }
            // CS1-42 / CS2-04: blocked accounts live in Privacy Center; the host owns the route.
            if (onOpenBlockedContacts != null) {
                ChatySettingsRowDivider()
                CallSettingsListTile(
                    title = "Blocked contacts",
                    icon = Icons.Outlined.Block,
                    iconColor = colors.primary,
                    subtitle = "View and manage blocked accounts",
                    onTap = onOpenBlockedContacts,
                )
            }
        }
        // Prior sections: "Call experience bundles" / "In-app call presentation".
        // CS4-03: keep the existing appearance controls together without changing their actions.
        ChatySettingsSection("Appearance", "Call screen style and floating call controls.") {
            val bundle = com.example.chat.ui.calls.bundles.CallBundleId.fromId(call.callExperienceBundleId)
            // CS1-18: fromId falls back silently on unknown ids; surface "Default" instead.
            val knownBundle = com.example.chat.ui.calls.bundles.CallBundleId.all.any { it.id == call.callExperienceBundleId }
// Previous implementation retained:             ChatySettingsTile(
            CallSettingsListTile(
                title = "Call screen style", // CS4-47 plain words (was: "Selected Call Bundle")
                icon = Icons.Rounded.Style,
                iconColor = colors.primary,
                subtitle = if (knownBundle) bundle.displayName else "Default",
                onTap = onOpenCallBundles,
            )
            ChatySettingsRowDivider()
            ChatySwitchTile(
                title = "Floating call bubble", // CS1-38 / CS4-02 (was: "Dynamic Call Island")
                icon = Icons.Rounded.BubbleChart,
                iconColor = colors.primary,
                // CS1-35: shorter subtitle; CS1-13/CS1-58: blocked-state reason + deep link (CS2-33 rationale).
                subtitle = if (overlayAllowed) {
                    "Show a floating capsule when you leave an active call."
                } else {
                    "Android is blocking the bubble. Tap to allow 'Display over other apps'."
                },
                value = call.dynamicIslandEnabled && overlayAllowed,
                onChanged = { wanted ->
                    if (overlayAllowed) onDynamicIsland(wanted) else openOverlaySettings()
                },
            )
            ChatySettingsRowDivider()
            ChatySwitchTile(
                title = "Picture-in-picture video", // was: "Picture-in-Picture Video"
                icon = Icons.Rounded.PictureInPicture,
                iconColor = colors.primary,
                subtitle = if (pipSupported) {
                    "Minimize video into a small window when switching apps."
                } else {
                    "Not supported on this device."
                },
                enabled = pipSupported,
                value = call.pictureInPictureEnabled && pipSupported,
                onChanged = onPictureInPicture,
            )
        }
        // Prior: ChatySettingsSection("Audio & data") {
        ChatySettingsSection("Quality", "Audio routing and mobile data use.") {
// Previous implementation retained:             ChatySettingsTile(
            CallSettingsListTile(
                title = "Audio & Speakers", // CS1-16: one name with the destination screen (was: "Dual-Speaker & Output Routing")
                icon = Icons.Rounded.SpeakerGroup,
                iconColor = colors.primary, // CS4-26 (was: colors.accent)
                subtitle = "Receiver, speaker, Bluetooth and output routing",
                onTap = onOpenAudioSettings,
            )
            ChatySettingsRowDivider()
            ChatySwitchTile(
                title = "Low data usage for calls", // was: "Low Data Usage for Calls"
                icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.callDataSaver),
                iconColor = colors.primary, // CS4-26 (was: colors.success)
                // CS1-14: plain words instead of WebRTC jargon (was: "Optimize WebRTC bandwidth consumption on cellular connections.").
                subtitle = "Uses less data on mobile networks. Video may look softer.",
                value = call.lowDataUsageEnabled,
                onChanged = onLowData,
            )
            // CS4-49: user-confirmed reset of the presentation options this screen exposes.
            // Privacy choices (who can call, exceptions) are NOT touched by the host callback.
            if (onResetCallSettings != null) {
                ChatySettingsRowDivider()
                CallSettingsListTile(
                    title = "Reset call settings",
                    icon = Icons.Rounded.RestartAlt,
                    iconColor = colors.primary,
                    subtitle = "Restore call appearance and data options to their defaults",
                    onTap = { if (!resetBusy) dialog = CallSettingsDialog.ResetCallSettings },
                    trailing = null,
                )
            }
        }
        // CS2-15 / CS2-16: permission status rows with a dead-end recovery link.
        ChatySettingsSection("Permissions") {
            val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val camGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            // CS2-51: summary derived only from the three permission checks in this section;
            // it is not a call-service, connectivity or "calls are working" guarantee.
            Text(
                if (micGranted && camGranted && contactsGranted) "Microphone, camera and contacts are all allowed."
                else "Some permissions below need attention.",
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp, end = 14.dp, bottom = 6.dp),
            )
// Previous implementation retained:             ChatySettingsTile(
            CallSettingsListTile(
                title = "Microphone",
                icon = Icons.Rounded.Mic,
                iconColor = colors.primary,
                subtitle = if (micGranted) "Granted" else "Not granted - calls will have no audio. Tap to open Settings.",
                onTap = { openAppSettings() },
            )
            ChatySettingsRowDivider()
// Previous implementation retained:             ChatySettingsTile(
            CallSettingsListTile(
                title = "Camera",
                icon = Icons.Rounded.Videocam,
                iconColor = colors.primary,
                subtitle = if (camGranted) "Granted" else "Not granted - video calls need it. Tap to open Settings.",
                onTap = { openAppSettings() },
            )
            ChatySettingsRowDivider()
// Previous implementation retained:             ChatySettingsTile(
            CallSettingsListTile(
                title = "Contacts",
                icon = Icons.Rounded.Contacts,
                iconColor = colors.primary,
                subtitle = if (contactsGranted) "Granted" else "Not granted - needed for call exceptions. Tap to open Settings.",
                onTap = { openAppSettings() },
            )
        }
        // CS5-46: emergency-calls disclaimer.
        Text(
            "Chaty calls can't make emergency calls.",
            color = colors.foregroundSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 8.dp),
        )
    }
    // CS1-09 / CS4-18: save feedback lives here.
    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    when (dialog) {
        CallSettingsDialog.WhoCanCall -> ChatySingleChoiceModal(
            title = "Who can call me",
            // CS1-48: say when the change takes effect. updatePrivacy persists locally only
            // (ChatyPreferencesController.updatePrivacy); the server copy is shown in Privacy Center.
            description = "Choose who can reach you via audio and video calls. Your choice is saved on this device right away; Privacy Center shows the copy stored on the server.",
            value = privacy.whoCanCallMe,
            options = audienceOptions,
            labelBuilder = { it },
            // CS1-46: consequence note on the strictest option.
            descriptionBuilder = { option ->
                if (option == "Nobody") "Nobody can call you in Chaty - including expected calls from deliveries or doctors." else ""
            },
            onApply = { chosen ->
                // CS1-47 / CS2-50: widening to Everyone asks once; narrowing does not.
                if (chosen == everyoneAudience && privacy.whoCanCallMe != everyoneAudience) {
                    dialog = CallSettingsDialog.ConfirmEveryone
                } else {
                    applyAudience(chosen)
                }
            },
            onDismiss = { if (dialog == CallSettingsDialog.WhoCanCall) dialog = CallSettingsDialog.None },
        )
        CallSettingsDialog.ConfirmEveryone -> ChatyConfirmationDialog(
            shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { dialog = CallSettingsDialog.None },
            title = { Text("Allow calls from everyone?", color = colors.foreground) },
            text = { Text("Anyone on Chaty will be able to call you, including people you don't know.", color = colors.foregroundSecondary) },
            dismissButton = {
                TextButton(onClick = { dialog = CallSettingsDialog.None }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel", color = colors.primary)
                }
            },
            confirmButton = {
                TextButton(onClick = { applyAudience(everyoneAudience) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Allow everyone", color = colors.primary)
                }
            },
        )
        CallSettingsDialog.ExcludedContacts -> {
            // CS1-05 (keyed on the pref), CS1-29 / CS3-03 (Set lookup), CS3-02 (saveable).
            var selectedIds by rememberSaveable(
                privacy.whoCanCallMeExceptions,
                stateSaver = androidx.compose.runtime.saveable.Saver<Set<String>, java.util.ArrayList<String>>(save = { java.util.ArrayList(it) }, restore = { it.toSet() }),
            ) { mutableStateOf(privacy.whoCanCallMeExceptions.toSet()) }
            var query by rememberSaveable { mutableStateOf("") }
// Previous implementation retained:             // CS1-07: search + alphabetical order for long contact lists.
// Previous implementation retained:             val shownContacts = remember(contacts, query) {
// Previous implementation retained:                 contacts
// Previous implementation retained:                     .filter { query.isBlank() || it.displayName.contains(query.trim(), ignoreCase = true) }
// Previous implementation retained:                     .sortedBy { it.displayName.lowercase() }
            var appliedQuery by rememberSaveable { mutableStateOf(query) }
            LaunchedEffect(query) {
                if (query.isNotEmpty()) kotlinx.coroutines.delay(250)
                appliedQuery = query
            }
            // Prior: val shownContacts = remember(contacts, appliedQuery) { filterCallContacts(contacts, appliedQuery) }
            // CS3-09: derive presentation from the debounced query, never from staged selections.
            val shownContacts by remember(contacts) {
                derivedStateOf { filterCallContacts(contacts, appliedQuery) }
            }
            val selectionChanged = selectedIds != privacy.whoCanCallMeExceptions.toSet()
            val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* host reloads contacts on resume */ }

            fun closeExceptions(save: Boolean) {
                // CS1-04: cancelling straight after choosing "My Contacts Except…"
                // with an empty list restores the previous audience.
                val previous = audienceBeforeExceptions
                if (ownsCallSettings() && !save && previous != null && preferences.privacy.whoCanCallMe == exceptAudience && preferences.privacy.whoCanCallMeExceptions.isEmpty()) {
                    preferences.updateCallPrivacy(whoCanCallMe = previous, logTitle = "Who Can Call Me") // CS3-49
                }
                audienceBeforeExceptions = null
                dialog = CallSettingsDialog.None
            }

// Previous implementation retained:             ChatyConfirmationDialog(
// Previous implementation retained:                 shape = com.example.chat.ui.kit.ChatyModalShape,
            CallExceptionDialog(
                fullScreen = callExceptionsUseFullScreen(contacts.size),
                onDismissRequest = { closeExceptions(save = false) },
                containerColor = colors.surfaceElevated,
                // CS1-53: one name for the list (was: "Contacts who may not call you").
// Previous implementation retained:                 title = { Text("Excluded contacts", color = colors.foreground) },
                title = { Text("People who can't call you", color = colors.foreground) },
                text = {
// Previous implementation retained:                     Column {
                    Column(if (callExceptionsUseFullScreen(contacts.size)) Modifier.fillMaxSize() else Modifier) {
                        if (contacts.isNotEmpty()) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                placeholder = { Text("Search contacts") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            // CS1-54: visible selection count.
                            Text("${selectedIds.size} selected", color = colors.foregroundSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            val selectedContacts = selectedCallContacts(contacts, selectedIds)
                            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                items(selectedContacts, key = { it.id }) { selected ->
                                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                        color = colors.primary.copy(alpha = 0.12f)) {
                                        Text(selected.displayName, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            color = colors.foreground, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (contacts.isEmpty()) {
                            // CS1-08 / CS4-13: explain WHY the list is empty; CS1-25: request in context.
                            if (contactsGranted) {
                                Text("No contacts found on this device.", color = colors.foreground, modifier = Modifier.padding(vertical = 18.dp))
                            } else {
                                Text(
                                    "Chaty can't see your contacts. Allow contact access to choose who may not call you.",
                                    color = colors.foreground,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                                TextButton(onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                                    Text("Allow contacts", color = colors.primary)
                                }
                            }
                        } else {
                            // CS1-51: LazyColumn supplies collection semantics; CS1-28: stable keys.
// Previous implementation retained:                             LazyColumn(Modifier.heightIn(max = 320.dp)) {
// Previous implementation retained:                             LazyColumn(if (callExceptionsUseFullScreen(contacts.size)) Modifier.weight(1f)
                            val contactListState = androidx.compose.foundation.lazy.rememberLazyListState()
                            val sectionStarts = remember(shownContacts) { callContactSectionStarts(shownContacts) }
                            if (callExceptionsUseFullScreen(contacts.size)) {
                                LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                                    items(sectionStarts, key = { it.first }) { (letter, index) ->
                                        TextButton(onClick = { scope.launch { contactListState.scrollToItem(index) } },
                                            modifier = Modifier.heightIn(min = 48.dp)) { Text(letter) }
                                    }
                                }
                            }
                            LazyColumn(state = contactListState,
                                modifier = if (callExceptionsUseFullScreen(contacts.size)) Modifier.weight(1f)
                                else Modifier.heightIn(max = 320.dp)) {
                                items(shownContacts, key = { it.id }) { c ->
                                    val checked = selectedIds.contains(c.id)
// Previous implementation retained:                                     Row(
// Previous implementation retained:                                         // CS1-32: 48dp target; CS1-06 / CS1-52: one toggleable row, null Checkbox handler.
// Previous implementation retained:                                         Modifier.fillMaxWidth().heightIn(min = 48.dp)
// Previous implementation retained:                                             .toggleable(value = checked, role = Role.Checkbox, onValueChange = { on ->
// Previous implementation retained:                                                 ChatyHapticsPort.selection(view) // CS4-19
// Previous implementation retained:                                                 selectedIds = if (on) selectedIds + c.id else selectedIds - c.id
// Previous implementation retained:                                             }),
// Previous implementation retained:                                         verticalAlignment = Alignment.CenterVertically,
// Previous implementation retained:                                     ) {
// Previous implementation retained:                                         Checkbox(checked = checked, onCheckedChange = null)
// Previous implementation retained:                                         Spacer(Modifier.width(4.dp))
// Previous implementation retained:                                         // CS4-12: recognition avatar; CS4-41: decorative (name is adjacent text).
// Previous implementation retained:                                         Box(
// Previous implementation retained:                                             Modifier.size(32.dp).background(colors.primary.copy(alpha = 0.12f), CircleShape),
// Previous implementation retained:                                             contentAlignment = Alignment.Center,
// Previous implementation retained:                                         ) {
// Previous implementation retained:                                             Text(
// Previous implementation retained:                                                 c.displayName.trim().take(1).uppercase(),
// Previous implementation retained:                                                 color = colors.primary,
// Previous implementation retained:                                                 fontSize = 13.sp,
// Previous implementation retained:                                                 fontWeight = FontWeight.SemiBold,
// Previous implementation retained:                                             )
// Previous implementation retained:                                         }
// Previous implementation retained:                                         Spacer(Modifier.width(8.dp))
// Previous implementation retained:                                         // CS4-16: up to two lines instead of truncation.
// Previous implementation retained:                                         Text(c.displayName, color = colors.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    CallExceptionContactRow(c, checked) { on ->
                                        ChatyHapticsPort.selection(view)
                                        selectedIds = if (on) selectedIds + c.id else selectedIds - c.id
                                    }
                                }
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { closeExceptions(save = false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Cancel", color = colors.primary) // CS1-33: explicit theme colour
                    }
                },
                confirmButton = {
                    // CS1-55 / CS4-14: Save disabled until the selection changes.
                    TextButton(
                        enabled = selectionChanged,
                        modifier = Modifier.heightIn(min = 48.dp),
                        onClick = {
                            if (!ownsCallSettings()) return@TextButton
                            val previousIds = preferences.privacy.whoCanCallMeExceptions
                            // CS1-45: log counts, not ids.
                            preferences.updateCallPrivacy( // CS3-49
                                whoCanCallMeExceptions = selectedIds.toList(),
                                logTitle = "Who Can Call Me Exceptions (${selectedIds.size} contacts)",
                            )
                            audienceBeforeExceptions = null
                            dialog = CallSettingsDialog.None
                            // CS1-09 / CS4-18: confirmation snackbar with Undo.
                            scope.launch {
                                val result = snackbarHostState.showSnackbar("Call exceptions updated", actionLabel = "Undo")
                                if (result == SnackbarResult.ActionPerformed && ownsCallSettings()) {
                                    preferences.updateCallPrivacy( // CS3-49
                                        whoCanCallMeExceptions = previousIds,
                                        logTitle = "Who Can Call Me Exceptions (undo)",
                                    )
                                }
                            }
                        },
                    ) { Text("Save", color = colors.primary) }
                },
            )
        }
        // CS4-49: confirm before any reset; the host performs the actual writes.
        CallSettingsDialog.ResetCallSettings -> ChatyConfirmationDialog(
            shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { dialog = CallSettingsDialog.None },
            title = { Text("Reset call settings?", color = colors.foreground) },
            text = { Text("This resets call appearance and data options on this device to their defaults. Your call privacy choices are not changed.", color = colors.foregroundSecondary) },
            dismissButton = {
                TextButton(onClick = { dialog = CallSettingsDialog.None }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel", color = colors.primary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = CallSettingsDialog.None
                    if (!resetBusy && ownsCallSettings()) {
                        resetBusy = true
                        scope.launch {
                            try {
                                onResetCallSettings?.invoke()
                                snackbarHostState.showSnackbar("Call settings reset")
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { snackbarHostState.showSnackbar("Could not reset call settings. Some options may have changed. Try again.") }
                            finally { resetBusy = false }
                        }
                    }
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Reset", color = colors.primary)
                }
            },
        )
        CallSettingsDialog.None -> Unit
    }
}

/** Presentation only: do not mutate staged IDs, persisted exceptions or contact identity. */
internal fun selectedCallContacts(contacts: List<CallContactOption>, selectedIds: Set<String>): List<CallContactOption> =
    contacts.filter { it.id in selectedIds }.distinctBy { it.id }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

/** Local Material anatomy only. The host supplies the same existing action and displayed value. */
@Composable
internal fun CallSettingsListTile(
    title: String, modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconColor: androidx.compose.ui.graphics.Color? = null,
    subtitle: String? = null,
    // Prior: trailing: (@Composable () -> Unit)? = null,
    // Consolidated call-site argument: trailing = { CallSettingsChevron() },
    // CS3-24: all local navigation tiles share this slot; callers keep their existing actions.
    trailing: (@Composable () -> Unit)? = { CallSettingsChevron() },
    onTap: () -> Unit,
) {
    val colors = LocalAppColors.current
    var focused by remember { mutableStateOf(false) }
    androidx.compose.material3.ListItem(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, colors.primary,
                androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) else Modifier)
            // Prior: .clickable(role = Role.Button, onClickLabel = "Change $title", onClick = onTap),
            .clickable(role = Role.Button, onClickLabel = title, onClick = onTap),
        headlineContent = { Text(title, color = colors.foreground) },
// Previous implementation retained:         supportingContent = subtitle?.let { value -> { Text(value, color = colors.foregroundSecondary) } },
// Previous implementation retained:         leadingContent = icon?.let { glyph -> { Icon(glyph, null, Modifier.size(24.dp), tint = iconColor ?: colors.primary) } },
        supportingContent = { if (subtitle != null) Text(subtitle, color = colors.foregroundSecondary) },
        leadingContent = { if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = iconColor ?: colors.primary) },
        trailingContent = trailing,
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = colors.surface),
    )
}

/** Matching/sorting presentation helper; never changes selected IDs or contact identity. */
internal fun filterCallContacts(contacts: List<CallContactOption>, query: String): List<CallContactOption> {
    val needle = query.trim()
    return contacts.filter { needle.isEmpty() || it.displayName.contains(needle, ignoreCase = true) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
}

internal fun callExceptionsUseFullScreen(contactCount: Int): Boolean = contactCount > 20

/** Only exception-picker presentation. Caller still owns staged IDs, save and cancel semantics. */
@Composable
internal fun CallExceptionDialog(
    fullScreen: Boolean, onDismissRequest: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    title: @Composable () -> Unit, text: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
) {
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = if (fullScreen) Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
                else Modifier.padding(24.dp).widthIn(max = 560.dp).fillMaxWidth()
                    .heightIn(max = (config.screenHeightDp * 0.85f).dp).imePadding(),
            color = containerColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(if (fullScreen) 0.dp else 24.dp),
        ) {
            Column(Modifier.semantics { paneTitle = "People who can't call you" }) {
                Box(Modifier.fillMaxWidth().padding(20.dp)) { title() }
                // Prior: Box(Modifier.weight(1f, fill = fullScreen).fillMaxWidth().padding(horizontal = 20.dp)) { text() }
                // CS4-22: compact dialog content can scroll independently of the action row.
                // Full-screen content keeps its bounded, weighted LazyColumn instead.
                Box(Modifier.weight(1f, fill = fullScreen).fillMaxWidth().padding(horizontal = 20.dp)
                    .then(if (fullScreen) Modifier else Modifier.verticalScroll(rememberScrollState()))) { text() }
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    dismissButton()
                    Spacer(Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}

/** Quick index uses actual filtered-list initials/indices, not an assumed Latin alphabet. */
internal fun callContactSectionStarts(contacts: List<CallContactOption>): List<Pair<String, Int>> {
    val seen = linkedSetOf<String>()
    return contacts.mapIndexedNotNull { index, contact ->
        val letter = com.example.chat.ui.calls.callInitials(contact.displayName)
        if (seen.add(letter)) letter to index else null
    }
}


/** One accessible checkbox row. Identity and staged selection remain owned by the picker. */
@Composable
internal fun CallExceptionContactRow(contact: CallContactOption, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(32.dp).background(colors.primary.copy(alpha = 0.12f), CircleShape)
            , contentAlignment = Alignment.Center) {
            Text(com.example.chat.ui.calls.callInitials(contact.displayName), color = colors.primary,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clearAndSetSemantics { })
        }
        Spacer(Modifier.width(8.dp))
        // Prior: Text(contact.displayName, color = colors.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        // CS4-35: let long names wrap and increase the row height at large font sizes.
        Text(contact.displayName, color = colors.foreground, modifier = Modifier.weight(1f))
    }
}


/** CS3-20: design-time exception-content fixtures only. No permissions, stores or real contacts.
 * They do not exercise the page host, persistence, permission flow or dialog window behavior. */
@Composable
private fun CallSettingsContactPreviewFixture(count: Int) {
    val contacts = remember(count) {
        List(count) { index -> CallContactOption("preview-$index", "Sample contact ${index + 1}") }
    }
    com.example.chat.ui.theme.ChatyTheme(
        com.example.chat.core.theme.ThemePresets.getSystemDefaultTheme(false),
    ) {
        val colors = LocalAppColors.current
        Surface(color = colors.surface) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Exception content preview ($count sample contacts)", color = colors.foreground)
                Spacer(Modifier.height(8.dp))
                if (contacts.isEmpty()) {
                    Text("No contacts found on this device.", color = colors.foreground)
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(contacts, key = { it.id }) { contact ->
                            CallExceptionContactRow(contact, checked = false, onToggle = {})
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Call exceptions: 0 sample contacts", showBackground = true)
@Composable
private fun CallSettingsEmptyContactsPreview() = CallSettingsContactPreviewFixture(0)

@androidx.compose.ui.tooling.preview.Preview(name = "Call exceptions: 5 sample contacts", showBackground = true)
@Composable
private fun CallSettingsFiveContactsPreview() = CallSettingsContactPreviewFixture(5)

@androidx.compose.ui.tooling.preview.Preview(name = "Call exceptions: 500 sample contacts", showBackground = true)
@Composable
private fun CallSettingsManyContactsPreview() = CallSettingsContactPreviewFixture(500)

// Superseded call-settings source retained inactive:
// /** Call-template choice: [name] is the stored value (CallScreenTemplate.name /
//  *  wireName per the Calls worker contract), [label] its displayName. */
// data class CallTemplateOption(val name: String, val label: String)
// private val audienceOptions = listOf("Everyone", "My Contacts", "My Contacts Except…", "Nobody")
// private enum class CallSettingsDialog { None, WhoCanCall, ExcludedContacts, ConfirmEveryone }
// /** Call settings expose one global bundle choice. Legacy template parameters remain
//  *  source-compatible while their persisted consumers are migrated separately.
//     templates: List<CallTemplateOption>,
//     onIncomingTemplate: (String) -> Unit,
//     onOngoingTemplate: (String) -> Unit,
//         preferences.updatePrivacy(privacy.copy(whoCanCallMe = chosen), logTitle = "Who Can Call Me")
//                 subtitle = privacy.whoCanCallMe,
//             description = "Choose who can reach you via audio and video calls.",
//                     preferences.updatePrivacy(privacy.copy(whoCanCallMe = previous), logTitle = "Who Can Call Me")
//                             preferences.updatePrivacy(
//                                 preferences.privacy.copy(whoCanCallMeExceptions = selectedIds.toList()),
//                                     preferences.updatePrivacy(
//                                         preferences.privacy.copy(whoCanCallMeExceptions = previousIds),
