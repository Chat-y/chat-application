package com.example.chat.ui.chats.contact_info

import androidx.compose.ui.layout.onSizeChanged
import com.example.chat.ui.navigation.ReferenceLayoutMetrics as IdentityMetrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.ContactConnectionStatus
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalAppColors
import java.time.Instant
import kotlinx.coroutines.launch

/** Port of contact_info_screen.dart (api branch anchor 3bee780): profile
 *  header with presence label, quick actions, connection/accept state,
 *  privacy & safety actions (block, delete chat, delete contact+chat),
 *  shared media/links/documents, and the (Dart-static) groups in common.
 *
 *  The Dart screen sources relationship/block state from
 *  ContactRelationshipService and presence from RichChatRealtimeService and
 *  hydrates messages through the realtime service; those seams arrive here
 *  as lambdas/state so the screen stays free of shared-file edits. On the
 *  REST session plane those Supabase-plane services report the same load
 *  error the Dart screen surfaces, which is the exact current behavior. */
@Composable
fun ContactInfoScreen(
    conversation: Conversation,
    contact: UserProfile,
    messages: List<ChatMessage>,
    connection: ContactConnectionStatus,
    blocked: Boolean,
    loading: Boolean,
    loadError: String?,
    busy: Boolean,
    isOnline: (String) -> Boolean,
    lastSeenFor: (String) -> Instant?,
    activityFor: (conversationId: String, userId: String) -> com.example.chat.data.realtime.ContactActivityState,
    hydrateMessage: (ChatMessage) -> ChatMessage,
    onAccept: suspend () -> Unit,
    onSetBlocked: suspend (Boolean) -> Unit,
    onDeleteConversation: () -> Unit,
    onDeleteUser: suspend () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenMedia: (ChatMessage) -> Unit,
    onSearch: () -> Unit,
    onStartCall: (Boolean) -> Unit,
    onShareContact: () -> Unit,
    onDeletedPopToRoot: () -> Unit,
    onBack: () -> Unit,
    presenceLabelOverride: String? = null,
    /** R1-05/R2-41: optional result-bearing conversation delete. Returns true only after the delete
     *  is acknowledged. When null, the old fire-and-forget [onDeleteConversation] path is used. */
    onDeleteConversationAwaited: (suspend () -> Boolean)? = null,
    bannerPlaybackSelected: Boolean = false,
) {
    var showAllMedia by remember(conversation.id, contact.id) { mutableStateOf(false) }
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    // R1-36: a new notice replaces the current one, so failures cannot build a queue.
    suspend fun showNotice(message: String) {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(message)
    }

    // R1-03/R1-35: the hydrator is part of the cache key, so a policy or hydrator change recomputes.
    val hydrated = remember(messages, hydrateMessage) { messages.map(hydrateMessage) }
    // R1-04: the dialog reuses the same hydrated snapshot as the summary (was messages.map(hydrateMessage) again).
    if (showAllMedia) ConversationMediaDialog(hydrated, onOpenMedia, onClose = { showAllMedia = false })
    val contactName = ContactInfoLogic.safeDisplayName(contact.displayName)
    val infoScroll = rememberScrollState()
    // Previous fixed collapse distances: 132.dp / 276.dp.
    var measuredIdentityPx by remember(conversation.id, contact.id) { mutableStateOf(0f) }
    val collapseFraction = infoHeaderFraction(infoScroll, measuredIdentityPx)

    // R1-07/R5-45: one contact action at a time, independent of the parent's busy flag.
    var actionInFlight by remember(conversation.id, contact.id) { mutableStateOf(false) }
    // R1-06: remember a finished first step so a retry does not repeat it.
    var contactRemoved by remember(conversation.id, contact.id) { mutableStateOf(false) }
    val media = remember(hydrated) { ContactInfoLogic.sharedMedia(hydrated) }
    val documents = remember(hydrated) { ContactInfoLogic.sharedDocuments(hydrated) }
    val links = remember(hydrated) { ContactInfoLogic.extractLinks(hydrated) }
    val activity = activityFor(conversation.id, contact.id)
    val online = isOnline(contact.id)

    // Keep the route toolbar outside the body subcomposition so it cannot be
    // covered by the full-height scrolling details. Dart ChatyScaffold owns
    // this toolbar and SafeArea separately from its ListView body.
    Column(Modifier.fillMaxSize().background(colors.background)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .semantics { paneTitle = "Contact info for $contactName" }) {
        // GB contact-info top bar: back chevron only, no title (user reference 2026-11-03).
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Back", color = colors.foreground)
            }
            InfoToolbarIdentity(contactName, ContactInfoLogic.usernameLine(contact.username), contact.avatarUrl,
                contact.avatarInitials, Color(parseAvatarColorArgbKit(contact.avatarColorHex, com.example.chat.ui.theme.LocalThemeConfig.current.accentColor)), collapseFraction,
                Modifier.weight(1f))
        }
        Scaffold(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            containerColor = colors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.2.dp, color = colors.primary,
                    modifier = Modifier.semantics { contentDescription = "Loading contact info" })
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Previous scrolling state retained: .verticalScroll(rememberScrollState())
                .verticalScroll(infoScroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.fillMaxWidth().onSizeChanged { measuredIdentityPx = it.height.toFloat() }) {
            // Use the same image loader and fallback as profile/home.
            if (!contact.bannerUrl.isNullOrBlank()) {
                // Previous banner renderer retained: com.example.chat.ui.theme.ChatyAsyncImage
                com.example.chat.ui.profile.ProfileBannerMedia(
                    url = contact.bannerUrl,
                    selected = bannerPlaybackSelected && !showAllMedia && confirmAction == null,
                    contentDescription = "Contact banner",
                    modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(18.dp)),
                    placeholder = { Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(18.dp)).background(colors.surface)) },
                    error = { Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(18.dp)).background(colors.surface)) },
                )
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                InfoAvatarHero(conversation.id to contact.id, contact.avatarUrl, contact.avatarInitials,
                    Color(parseAvatarColorArgbKit(contact.avatarColorHex, com.example.chat.ui.theme.LocalThemeConfig.current.accentColor)), contactName, IdentityMetrics.profileAvatarOuterDp.dp, collapseFraction,
                    badge = if (online) ({
                        Box(Modifier.padding(2.dp).size(16.dp).background(colors.background, CircleShape)
                            .padding(2.5.dp).background(colors.success, CircleShape))
                    }) else null)
            }
// Previous fixed avatar presentation retained inactive:
//             Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
//                 Box {
//                     com.example.chat.ui.chats.home.HomeAvatar(
//                         initials = contact.avatarInitials,
//                         color = Color(parseAvatarColorArgbKit(contact.avatarColorHex, com.example.chat.ui.theme.LocalThemeConfig.current.accentColor)),
//                         size = 92f, shape = "circle", url = contact.avatarUrl,
//                     )
//                     if (online) {
//                         Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(16.dp)
//                             .background(colors.background, CircleShape).padding(2.5.dp)
//                             .background(colors.success, CircleShape))
//                     }
//                 }
//             }
            Spacer(Modifier.height(12.dp))
            Text(
                // R3-20/R3-21/R3-22/R4-29: untrusted name, cleaned and bounded.
                contactName,
                color = colors.foreground,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Spacer(Modifier.height(2.dp))
            // R1-09: no bare "@" when the username is missing (was "@${contact.username}").
            ContactInfoLogic.usernameLine(contact.username)?.let { handle ->
                Text(
                    handle,
                    color = colors.foregroundSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                presenceLabelOverride ?: ContactInfoLogic.presenceLabel(
                    isRecording = activity.isRecording,
                    isTyping = activity.isTyping,
                    isOnline = online,
                    lastSeen = lastSeenFor(contact.id),
                ),
                color = if (online) colors.success else colors.foregroundSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            } // Measured identity header includes banner, hero, name, handle and presence.
            Spacer(Modifier.height(12.dp))

            // Quick action row: Call / Video / Search / Share.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                QuickAction(Icons.Outlined.Call, "Call") {
                    onStartCall(false)
                }
                QuickAction(Icons.Outlined.Videocam, "Video") {
                    onStartCall(true)
                }
                QuickAction(Icons.Rounded.Search, "Search") { onSearch() }
                QuickAction(Icons.Outlined.Share, "Share") {
                    onShareContact()
                }
            }

            if (contact.about.trim().isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(12.dp),
                ) {
                    Text(contact.about, color = colors.foreground, fontSize = 14.5.sp, lineHeight = 20.sp)
                }
            }
            if (loadError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    loadError,
                    color = colors.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))

            // GB row order: per-contact privacy sits right under the identity card (GB "Custom Privacy" row).
            // Hidden until the backend enforces overrides server-side (ReleaseFeatures.CONTACT_PRIVACY_OVERRIDES).
            if (com.example.chat.ReleaseFeatures.CONTACT_PRIVACY_OVERRIDES) {
                Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                    InfoTile(
                        icon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.tune, semanticLabel = null, color = colors.primary) },
                        title = "Individual Privacy Controls",
                        titleColor = colors.foreground,
                        subtitle = "Read receipts, typing indicator, recording & online presence",
                        trailing = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = colors.foregroundSecondary) },
                        onClick = onOpenPrivacy,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Calling is authorized by server block/privacy policy at signaling time.
            // Contact acceptance is not a second, client-only call permission gate.
            Text(
                "Calls follow this contact's privacy settings. Blocked contacts cannot call.",
                color = colors.foregroundSecondary, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(12.dp))

            // GB "Media, links, and docs" row: label left, total count + chevron right.
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button) { showAllMedia = true }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Media, links, and docs, ${ContactInfoLogic.loadedItemCount(media.size, documents.size, links.size)} loaded items"
                    }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Media, links, and docs", color = colors.foregroundSecondary, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("${ContactInfoLogic.loadedItemCount(media.size, documents.size, links.size)}", color = colors.foregroundSecondary, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = colors.foregroundSecondary, modifier = Modifier.size(18.dp))
            }
            // R1-53: the count covers loaded chat data only, not the full history.
            Text("Counts include loaded messages only.", color = colors.foregroundSecondary, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                if (media.isEmpty() && links.isEmpty() && documents.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            // R1-54: scoped wording (was "No shared media, links or documents yet.")
                            "No shared media, links or documents loaded.",
                            color = colors.foregroundSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (media.isNotEmpty()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${media.size} Photos & Videos",
                            color = colors.foreground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        // R1-25: lazy row so a long history is not composed eagerly (was Row + horizontalScroll over every item).
                        LazyRow(Modifier.height(80.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            items(media, key = { it.id }) { message ->
                                val attachment = message.attachment!!
                                val kindLabel = if (attachment.type == "video") "Video" else "Photo"
                                Column(
                                    Modifier
                                        .width(110.dp)
                                        .height(80.dp)
                                        .background(colors.surfaceSecondary, RoundedCornerShape(12.dp))
                                        .clickable(onClickLabel = "Open $kindLabel", role = Role.Button) { onOpenMedia(message) }
                                        .semantics(mergeDescendants = true) { contentDescription = "$kindLabel, ${attachment.name.ifBlank { kindLabel }}" }
                                        .padding(8.dp),
                                ) {
                                    Icon(
                                        if (attachment.type == "video") Icons.Outlined.PlayCircle else Icons.Outlined.Image,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        attachment.name,
                                        color = colors.foreground,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                links.take(5).forEach { link ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {

//Icon(Icons.Rounded.Link, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.link, Icons.Rounded.Link, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            link,
                            color = colors.primary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                documents.take(5).forEach { message ->
                    val attachment = message.attachment!!
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                            .clickable(onClickLabel = "Open document", role = Role.Button) { onOpenMedia(message) }
                            .semantics(mergeDescendants = true) {}
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {

//Icon(Icons.Outlined.Description, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                        com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.document, Icons.Outlined.Description, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                attachment.name.ifBlank { "Document" }, // R1-57
                                color = colors.foreground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(attachment.size, color = colors.foregroundSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Per-chat media auto-download (existing MediaAutoDownloadGate override; no new screen).
            var dlOverride by remember(conversation.id) { mutableStateOf(com.example.chat.ServiceLocator.mediaAutoDownloadGate.overrideFor(conversation.id)) }
            var dlPicker by remember(conversation.id) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                InfoTile(
                    icon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.cloudDownload, semanticLabel = null, color = colors.primary) },
                    title = "Media auto-download",
                    titleColor = colors.foreground,
                    subtitle = when (dlOverride) {
                        com.example.chat.data.media.AutoDownloadPolicy.Override.ALWAYS -> "Always download media from this chat"
                        com.example.chat.data.media.AutoDownloadPolicy.Override.NEVER -> "Never. Tap a photo or video to download it"
                        com.example.chat.data.media.AutoDownloadPolicy.Override.DEFAULT -> "Follow Storage & Media settings"
                    },
                    trailing = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = colors.foregroundSecondary, modifier = Modifier.size(18.dp)) },
                    onClick = { dlPicker = true },
                )
            }
            if (dlPicker) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { dlPicker = false },
                    title = { Text("Media auto-download") },
                    text = {
                        Column(Modifier.selectableGroup()) {
                            listOf(
                                com.example.chat.data.media.AutoDownloadPolicy.Override.DEFAULT to "Follow default",
                                com.example.chat.data.media.AutoDownloadPolicy.Override.ALWAYS to "Always download",
                                com.example.chat.data.media.AutoDownloadPolicy.Override.NEVER to "Never (tap to download)",
                            ).forEach { (value, label) ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                        selected = dlOverride == value, role = Role.RadioButton,
                                        onClick = {
                                            com.example.chat.ServiceLocator.mediaAutoDownloadGate.setOverride(conversation.id, value)
                                            dlOverride = value; dlPicker = false
                                        },
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    androidx.compose.material3.RadioButton(selected = dlOverride == value, onClick = null) // row owns the click (R1-41)
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, color = colors.foreground)
                                }
                            }
                        }
                    },
                    confirmButton = { // Previous stock text action:
                    // androidx.compose.material3.TextButton(onClick = { dlPicker = false }) { Text("Close") }
                    com.example.chat.ui.kit.ChatyTextButton(
                        label = "Close",
                        onClick = { dlPicker = false },
                    ) },
                )
            }
            Spacer(Modifier.height(12.dp))

            // Destructive actions stay flat at the bottom like the GB reference (no section header).
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))) {
                InfoTile(
                    icon = {
                        Icon(
                            if (blocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                            contentDescription = null,
                            tint = colors.error,
                        )
                    },
                    title = if (blocked) "Unblock $contactName" else "Block $contactName",
                    titleColor = colors.error,
                    subtitle = if (blocked) "Allow messages from this contact again." else "Stop receiving messages from this contact.",
                    onClick = {
                        // Dart _toggleBlock: the confirm dialog only gates
                        // blocking; unblocking applies directly.
                        if (busy || actionInFlight) return@InfoTile
                        if (blocked) {
                            actionInFlight = true
                            scope.launch {
                                try {
                                    onSetBlocked(false)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    // R1-16/R3-39: no raw exception text (was showNotice("$error")).
                                    showNotice("Couldn't unblock this contact. Try again.")
                                } finally {
                                    actionInFlight = false
                                }
                            }
                        } else {
                            confirmAction = "block"
                        }
                    },
                )
                InfoTile(
                    icon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.deleteOutline, semanticLabel = null, color = colors.error) },
                    title = "Delete Conversation",
                    titleColor = colors.error,
                    subtitle = "Remove this chat and all message history.",
                    onClick = { if (!busy && !actionInFlight) confirmAction = "deleteConversation" },
                )
                InfoTile(
                    icon = {
//Icon(Icons.Rounded.PersonRemove, contentDescription = null, tint = colors.error)
                    com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.personRemove, Icons.Rounded.PersonRemove, contentDescription = null, tint = colors.error) },
                    title = "Delete Contact & Chat",
                    titleColor = colors.error,
                    subtitle = "Remove $contactName and all conversations.",
                    onClick = { if (!busy && !actionInFlight) confirmAction = "deleteUser" },
                )
            }
            Spacer(Modifier.height(12.dp))

            Spacer(Modifier.height(24.dp))
        }
    }

    }

    // ChatyConfirmDialog equivalents for block / delete conversation /
    // delete user, with the exact Dart copy.
    when (confirmAction) {
        "block" -> ConfirmDialog(
            title = "Block $contactName?",
            message = "You will stop receiving new messages from this person, and they cannot message you until you unblock them.",
            confirmLabel = "Block",
            onConfirm = {
                confirmAction = null
                if (actionInFlight) return@ConfirmDialog
                actionInFlight = true
                scope.launch {
                    try {
                        onSetBlocked(true)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        showNotice("Couldn't block this contact. Try again.")
                    } finally {
                        actionInFlight = false
                    }
                }
            },
            onDismiss = { confirmAction = null },
        )
        "deleteConversation" -> ConfirmDialog(
            title = "Delete chat with $contactName?",
            message = "This removes this conversation and all message history.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmAction = null
                val awaited = onDeleteConversationAwaited
                if (awaited == null) {
                    // Dart: deleteConversation is fire-and-forget, then pop to root.
                    onDeleteConversation()
                    onDeletedPopToRoot()
                } else if (!actionInFlight) {
                    // R1-05: navigate only after the delete is acknowledged.
                    actionInFlight = true
                    scope.launch {
                        try {
                            if (awaited()) onDeletedPopToRoot()
                            else showNotice("Couldn't delete this chat. Try again.")
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            showNotice("Couldn't delete this chat. Try again.")
                        } finally {
                            actionInFlight = false
                        }
                    }
                }
            },
            onDismiss = { confirmAction = null },
        )
        "deleteUser" -> ConfirmDialog(
            title = "Delete $contactName?",
            message = "This removes $contactName from your contacts, directory, and deletes the conversation.",
            confirmLabel = "Delete User",
            onConfirm = {
                confirmAction = null
                if (actionInFlight) return@ConfirmDialog
                actionInFlight = true
                scope.launch {
                    try {
                        // R1-06/R2-42: if removing the contact fails, stop here (no conversation delete, no navigation).
                        if (!contactRemoved) {
                            onDeleteUser()
                            contactRemoved = true
                        }
                        val awaited = onDeleteConversationAwaited
                        if (awaited != null) {
                            if (!awaited()) {
                                showNotice("Contact removed, but the chat wasn't deleted. Try again to finish.")
                                return@launch
                            }
                        } else {
                            onDeleteConversation()
                        }
                        onDeletedPopToRoot()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        // Was "Failed to delete user: $error" (raw exception text).
                        showNotice(
                            if (contactRemoved) "Contact removed, but the chat wasn't deleted. Try again to finish."
                            else "Couldn't delete this contact. Try again.",
                        )
                    } finally {
                        actionInFlight = false
                    }
                }
            },
            onDismiss = { confirmAction = null },
        )
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onTap: () -> Unit) {
    val colors = LocalAppColors.current
    // Round icon button with its label underneath, GB/WhatsApp contact-info style.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            // R1-44: at least 48dp in both directions, exposed as a button.
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .background(colors.surfaceSecondary, CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = colors.foregroundSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun SectionTitle(title: String, colors: com.example.chat.ui.theme.AppColors) {
    Text(
        title,
        color = colors.foregroundSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).semantics { heading() }, // R1-40
    )
}

@Composable
internal fun InfoTile(
    icon: @Composable () -> Unit,
    title: String,
    titleColor: Color,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.sp)
        }
        if (trailing != null) trailing()
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, color = colors.foreground) },
        text = { Text(message, color = colors.foregroundSecondary) },
        confirmButton = {
            // Previous stock text action:
            // TextButton(onClick = onConfirm) {
            //                 Text(confirmLabel, color = colors.error, fontWeight = FontWeight.Bold)
            //             }
            com.example.chat.ui.kit.ChatyTextButton(
                label = confirmLabel,
                onClick = onConfirm,
                color = colors.error,
                fontWeight = FontWeight.Bold,
            )
        },
        dismissButton = {
            // Previous stock text action:
            // TextButton(onClick = onDismiss) {
            //                 Text("Cancel", color = colors.foregroundSecondary)
            //             }
            com.example.chat.ui.kit.ChatyTextButton(
                label = "Cancel",
                onClick = onDismiss,
                color = colors.foregroundSecondary,
            )
        },
    )
}

// Complete original-line retention for review (inactive).
                    //                     confirmButton = { androidx.compose.material3.TextButton(onClick = { dlPicker = false }) { Text("Close") } },
