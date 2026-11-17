package com.example.chat.ui.chats.contact_info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.KeyboardAlt
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.ContactPrivacyOverride
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** Port of contact_privacy_screen.dart (api branch anchor 3bee780):
 *  per-contact privacy override toggles with optimistic save + rollback on
 *  failure, sourced from ContactRelationshipService.privacyFor/savePrivacy
 *  (arriving here as lambdas). */
@Composable
fun ContactPrivacyScreen(
    contact: UserProfile,
    privacyFor: suspend (String) -> ContactPrivacyOverride,
    savePrivacy: suspend (ContactPrivacyOverride) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    // R1-01/R1-02/R3-01: all privacy state is owned by the contact id. A different contact
    // starts from scratch and an old contact's late result is never applied.
    var value by remember(contact.id) { mutableStateOf<ContactPrivacyOverride?>(null) }
    var loading by remember(contact.id) { mutableStateOf(true) }
    var saving by remember(contact.id) { mutableStateOf(false) }
    var error by remember(contact.id) { mutableStateOf<String?>(null) }
    var loadAttempt by remember(contact.id) { mutableStateOf(0) }
    // R5-08: always call the newest callbacks without restarting the load.
    val currentPrivacyFor by rememberUpdatedState(privacyFor)
    val currentSavePrivacy by rememberUpdatedState(savePrivacy)
    val currentContactId by rememberUpdatedState(contact.id)
    val contactName = ContactInfoLogic.safeDisplayName(contact.displayName)

    // LaunchedEffect keyed by contact id cancels the previous contact's request (R1-01).
    LaunchedEffect(contact.id, loadAttempt) {
        loading = true
        error = null
        try {
            val loaded = currentPrivacyFor(contact.id)
            // R3-15: reject a response that belongs to someone else.
            if (loaded.targetUserId != contact.id) {
                error = "Couldn't load privacy settings. Try again."
            } else {
                value = loaded
            }
            loading = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // R1-17/R2-49/R3-33: typed, redacted copy only. No raw exception text on screen.
            loading = false
            error = "Couldn't load privacy settings. Try again."
        }
    }

    // Dart _save: optimistic apply, rollback + error on failure.
    fun save(next: ContactPrivacyOverride) {
        if (saving) return
        val previous = value
        val ownerId = contact.id
        value = next
        saving = true
        error = null
        scope.launch {
            try {
                currentSavePrivacy(next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // R3-46: only roll back if the screen still shows the same contact.
                if (ownerId == currentContactId) {
                    value = previous
                    // R1-16/R3-33/R3-39: safe copy; raw message used to be shown here.
                    // was: error = (e.message ?: e.toString()).removePrefix("Exception: ")
                    error = "Couldn't save this setting. Your previous choice was restored."
                }
            } finally {
                if (ownerId == currentContactId) saving = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = "Privacy for $contactName" },
        containerColor = colors.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Back", color = colors.foreground)
                }
                Text(
                    "Privacy: $contactName",
                    color = colors.foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    // R1-45: a long name must not squeeze out the back button.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
        },
    ) { padding ->
        val current = value
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.2.dp, color = colors.primary,
                    modifier = Modifier.semantics { contentDescription = "Loading privacy settings" })
            }
            current == null -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                Text(error ?: "Unable to load contact privacy.", color = colors.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                androidx.compose.material3.TextButton(onClick = { loadAttempt += 1 }) { Text("Try again") }
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                ) {

//Icon(
//                        Icons.Rounded.Shield,
//                        contentDescription = null,
//                        tint = colors.primary,
//                        modifier = Modifier.size(22.dp),
//                    )
                    com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.shield, Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        // R1-13/R3-50: no cryptographic/server-enforcement claim is shown until it is verified.
                        // was: "These controls change what ${contact.displayName} can observe from you. They are cryptographically and server-enforced."
                        "These settings ask Chaty to hide this activity from $contactName. How fully they apply depends on the Chaty server.",
                        color = colors.foreground.copy(alpha = 0.85f),
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = colors.error, fontSize = 13.sp,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (saving) {
                    // R1-55: visible saving signal while a change is in flight.
                    Spacer(Modifier.height(8.dp))
                    Text("Saving…", color = colors.foregroundSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))

                SectionTitle("Message Receipts", colors)
                Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                    PrivacyTile(Icons.Rounded.Done, "Hide delivery receipt (second tick)",
                        "Messages received by you will not expose the delivered state to this contact.",
                        current.hideDeliveryReceipts, saving) { save(current.copy(hideDeliveryReceipts = it)) }
                    PrivacyTile(Icons.Rounded.DoneAll, "Hide read receipt (blue ticks)",
                        "Reading this contact’s messages will not expose read receipts to them.",
                        current.hideReadReceipts, saving) { save(current.copy(hideReadReceipts = it)) }
                }
                Spacer(Modifier.height(12.dp))

                SectionTitle("Live Indicators", colors)
                Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                    PrivacyTile(Icons.Rounded.KeyboardAlt, "Hide typing state",
                        "Do not publish your typing state to this contact.",
                        current.hideTyping, saving) { save(current.copy(hideTyping = it)) }
                    PrivacyTile(Icons.Rounded.MicNone, "Hide voice recording state",
                        "Do not publish \"recording…\" while recording audio messages.",
                        current.hideRecording, saving) { save(current.copy(hideRecording = it)) }
                }
                Spacer(Modifier.height(12.dp))

                SectionTitle("Presence & Timestamp", colors)
                Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                    PrivacyTile(com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.presenceDot), "Hide online status",
                        "This contact will receive an offline status projection even while active.",
                        current.hideOnline, saving) { save(current.copy(hideOnline = it)) }
                    PrivacyTile(Icons.Rounded.Schedule, "Hide last seen",
                        "This contact will not receive your last-seen timestamp.",
                        current.hideLastSeen, saving) { save(current.copy(hideLastSeen = it)) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PrivacyTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Boolean,
    saving: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        // R1-39/R4-04/R4-05/R4-11: the whole row is one labelled switch target; disabled while saving.
        Modifier.fillMaxWidth()
            .toggleable(value = value, enabled = !saving, role = Role.Switch, onValueChange = onChanged)
            .testTag("contact_privacy_$title")
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            // R1-46: was colors.foregroundSecondary.copy(alpha = 0.6f), a second alpha on an already secondary colour.
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = value,
            // The row owns the toggle (R1-39); the switch is display-only so there is one operable target.
            onCheckedChange = null,
            enabled = !saving,
            colors = com.example.chat.ui.kit.chatySwitchColors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary),
        )
    }
}
