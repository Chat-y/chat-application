package com.example.chat.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import com.example.chat.ui.kit.ChatySingleChoiceModal
import com.example.chat.ui.kit.ChatyConfirmationDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureInPicture
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.LiveRegionMode
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

/** Call-template choice: [name] is the stored value (CallScreenTemplate.name /
 *  wireName per the Calls worker contract), [label] its displayName. */
data class CallTemplateOption(val name: String, val label: String)

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

private val audienceOptions = listOf("Everyone", "My Contacts", "My Contacts Except…", "Nobody")
private const val exceptAudience = "My Contacts Except…"
private const val everyoneAudience = "Everyone"
private const val myContactsAudience = "My Contacts"

/** CS1-19 / CS3-01: dialogs as an enum instead of the retired magic ints 0/1/4. */
private enum class CallSettingsDialog { None, WhoCanCall, ExcludedContacts, ConfirmEveryone }

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

/** Call settings expose one global bundle choice. Legacy template parameters remain
 *  source-compatible while their persisted consumers are migrated separately.
 *
 *  CS1-11: dynamicIslandEnabled / pictureInPictureEnabled default ON in the
 *  store; a release-notes note about that behaviour change remains a release
 *  process task (not code). */
@Composable
fun CallSettingsScreen(
    preferences: ChatyPreferencesController,
    call: CallPresentationState,
    templates: List<CallTemplateOption>,
    contacts: List<CallContactOption>,
    onBack: () -> Unit,
    onIncomingTemplate: (String) -> Unit,
    onOngoingTemplate: (String) -> Unit,
    onDynamicIsland: (Boolean) -> Unit,
    onPictureInPicture: (Boolean) -> Unit,
    onLowData: (Boolean) -> Unit,
    onOpenAudioSettings: () -> Unit,
    // CS1-17: required, no default no-op (was: onOpenCallBundles: () -> Unit = {}).
    onOpenCallBundles: () -> Unit,
) {
    rememberPreferencesVersion(preferences)
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val view = LocalView.current
    val privacy = preferences.privacy
    // CS1-19 + CS1-20: enum dialog state that survives rotation (was: var dialog by remember { mutableStateOf(0) }).
    var dialog by rememberSaveable { mutableStateOf(CallSettingsDialog.None) }
    // CS1-04: audience before "My Contacts Except…" was chosen, so cancelling an
    // empty exception list can restore it instead of silently keeping "Except…".
    var audienceBeforeExceptions by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
        preferences.updatePrivacy(privacy.copy(whoCanCallMe = chosen), logTitle = "Who Can Call Me")
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
            ChatySettingsTile(
                title = "Who can call me", // was: "Who Can Call Me"
                icon = Icons.Rounded.RingVolume,
                iconColor = colors.primary,
                subtitle = privacy.whoCanCallMe,
                trailing = { CallSettingsChevron() },
                onTap = { dialog = CallSettingsDialog.WhoCanCall },
                // CS4-33: announce audience changes to TalkBack.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (privacy.whoCanCallMe == exceptAudience) {
                ChatySettingsRowDivider()
                ChatySettingsTile(
                    title = "Excluded contacts", // was: "Excluded Contacts"
                    icon = Icons.Outlined.PersonOff,
                    // CS4-27: not an error state; one icon colour family (CS4-26).
                    iconColor = colors.primary, // was: colors.error
                    // CS1-01: real count instead of the literal " contacts excluded".
                    subtitle = excludedContactsLabel(privacy.whoCanCallMeExceptions.size),
                    trailing = { CallSettingsChevron() },
                    onTap = {
                        audienceBeforeExceptions = null
                        dialog = CallSettingsDialog.ExcludedContacts
                    },
                )
            }
        }
        ChatySettingsSection(
            "Call experience bundles", // was: "Call Experience Bundles"
            "One-tap redesign of incoming, ongoing, group and compact call screens.",
        ) {
            val bundle = com.example.chat.ui.calls.bundles.CallBundleId.fromId(call.callExperienceBundleId)
            // CS1-18: fromId falls back silently on unknown ids; surface "Default" instead.
            val knownBundle = com.example.chat.ui.calls.bundles.CallBundleId.all.any { it.id == call.callExperienceBundleId }
            ChatySettingsTile(
                title = "Call screen style", // CS4-47 plain words (was: "Selected Call Bundle")
                icon = Icons.Rounded.Style,
                iconColor = colors.primary,
                subtitle = if (knownBundle) bundle.displayName else "Default",
                trailing = { CallSettingsChevron() },
                onTap = onOpenCallBundles,
            )
        }
        ChatySettingsSection("In-app call presentation") { // was: "In-App Call Presentation"
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
        ChatySettingsSection("Audio & data") { // CS1-37 / CS4-04 (was: "Audio & Connectivity")
            ChatySettingsTile(
                title = "Audio & Speakers", // CS1-16: one name with the destination screen (was: "Dual-Speaker & Output Routing")
                icon = Icons.Rounded.SpeakerGroup,
                iconColor = colors.primary, // CS4-26 (was: colors.accent)
                subtitle = "Receiver, speaker, Bluetooth and output routing",
                trailing = { CallSettingsChevron() },
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
        }
        // CS2-15 / CS2-16: permission status rows with a dead-end recovery link.
        ChatySettingsSection("Permissions") {
            val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val camGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            ChatySettingsTile(
                title = "Microphone",
                icon = Icons.Rounded.Mic,
                iconColor = colors.primary,
                subtitle = if (micGranted) "Granted" else "Not granted - calls will have no audio. Tap to open Settings.",
                trailing = { CallSettingsChevron() },
                onTap = { openAppSettings() },
            )
            ChatySettingsRowDivider()
            ChatySettingsTile(
                title = "Camera",
                icon = Icons.Rounded.Videocam,
                iconColor = colors.primary,
                subtitle = if (camGranted) "Granted" else "Not granted - video calls need it. Tap to open Settings.",
                trailing = { CallSettingsChevron() },
                onTap = { openAppSettings() },
            )
            ChatySettingsRowDivider()
            ChatySettingsTile(
                title = "Contacts",
                icon = Icons.Rounded.Contacts,
                iconColor = colors.primary,
                subtitle = if (contactsGranted) "Granted" else "Not granted - needed for call exceptions. Tap to open Settings.",
                trailing = { CallSettingsChevron() },
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
            description = "Choose who can reach you via audio and video calls.",
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
            // CS1-07: search + alphabetical order for long contact lists.
            val shownContacts = remember(contacts, query) {
                contacts
                    .filter { query.isBlank() || it.displayName.contains(query.trim(), ignoreCase = true) }
                    .sortedBy { it.displayName.lowercase() }
            }
            val selectionChanged = selectedIds != privacy.whoCanCallMeExceptions.toSet()
            val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* host reloads contacts on resume */ }

            fun closeExceptions(save: Boolean) {
                // CS1-04: cancelling straight after choosing "My Contacts Except…"
                // with an empty list restores the previous audience.
                val previous = audienceBeforeExceptions
                if (!save && previous != null && privacy.whoCanCallMe == exceptAudience && privacy.whoCanCallMeExceptions.isEmpty()) {
                    preferences.updatePrivacy(privacy.copy(whoCanCallMe = previous), logTitle = "Who Can Call Me")
                }
                audienceBeforeExceptions = null
                dialog = CallSettingsDialog.None
            }

            ChatyConfirmationDialog(
                shape = com.example.chat.ui.kit.ChatyModalShape,
                onDismissRequest = { closeExceptions(save = false) },
                containerColor = colors.surfaceElevated,
                // CS1-53: one name for the list (was: "Contacts who may not call you").
                title = { Text("Excluded contacts", color = colors.foreground) },
                text = {
                    Column {
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
                            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                                items(shownContacts, key = { it.id }) { c ->
                                    val checked = selectedIds.contains(c.id)
                                    Row(
                                        // CS1-32: 48dp target; CS1-06 / CS1-52: one toggleable row, null Checkbox handler.
                                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { on ->
                                                ChatyHapticsPort.selection(view) // CS4-19
                                                selectedIds = if (on) selectedIds + c.id else selectedIds - c.id
                                            }),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(checked = checked, onCheckedChange = null)
                                        Spacer(Modifier.width(4.dp))
                                        // CS4-12: recognition avatar; CS4-41: decorative (name is adjacent text).
                                        Box(
                                            Modifier.size(32.dp).background(colors.primary.copy(alpha = 0.12f), CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                c.displayName.trim().take(1).uppercase(),
                                                color = colors.primary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        // CS4-16: up to two lines instead of truncation.
                                        Text(c.displayName, color = colors.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                            val previousIds = privacy.whoCanCallMeExceptions
                            // CS1-45: log counts, not ids.
                            preferences.updatePrivacy(
                                preferences.privacy.copy(whoCanCallMeExceptions = selectedIds.toList()),
                                logTitle = "Who Can Call Me Exceptions (${selectedIds.size} contacts)",
                            )
                            audienceBeforeExceptions = null
                            dialog = CallSettingsDialog.None
                            // CS1-09 / CS4-18: confirmation snackbar with Undo.
                            scope.launch {
                                val result = snackbarHostState.showSnackbar("Call exceptions updated", actionLabel = "Undo")
                                if (result == SnackbarResult.ActionPerformed) {
                                    preferences.updatePrivacy(
                                        preferences.privacy.copy(whoCanCallMeExceptions = previousIds),
                                        logTitle = "Who Can Call Me Exceptions (undo)",
                                    )
                                }
                            }
                        },
                    ) { Text("Save", color = colors.primary) }
                },
            )
        }
        CallSettingsDialog.None -> Unit
    }
}
