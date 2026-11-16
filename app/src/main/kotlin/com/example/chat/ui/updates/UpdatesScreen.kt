package com.example.chat.ui.updates

import androidx.lifecycle.repeatOnLifecycle

import com.example.chat.core.templates.UpdatesLayoutMode

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.example.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat.data.services.StatusRecord
import com.example.chat.data.services.StatusService
import com.example.chat.data.services.StatusViewer
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.tasks.TaskAvatar
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

/** Media picked by the platform picker for a status update. The Dart
 *  screen's StatusService.pickAndPublish launches the picker itself; in
 *  Kotlin the picker is a UI concern and hands bytes in. */
data class PickedStatusMedia(
    val bytes: ByteArray,
    val displayName: String,
    val mimeType: String? = null,
    val durationSeconds: Long = 0,
)

/** Result of the WhatsApp-style story camera capture. */
data class CapturedStoryMedia(
    val bytes: ByteArray,
    val caption: String,
)

/** Exact port of lib/features/updates/updates_screen.dart.
 *
 *  Seams (integration wiring):
 *  - [statuses]: Flow equivalent of StatusService.watchActiveStatuses()
 *    (Dart Supabase realtime stream; the Kotlin StatusService port has no
 *    list/read method yet - owner wires the stream here).
 *  - [pickStatusMedia]: platform file picker for image/video/audio/document.
 *  - [captureStory]: ChatyCameraCaptureScreen story mode equivalent.
 *  - [remoteImage]: remote image renderer; the default binds the shared
 *    Coil-backed ChatyAsyncImage (ui/theme) with the Dart error fallback.
 *  - [openUrl]: url_launcher externalApplication equivalent.
 *  Preference reads are lambdas over PreferenceGroups fields. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    statusService: StatusService,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    statuses: Flow<List<StatusRecord>>,
    modifier: Modifier = Modifier,
    template: com.example.chat.core.templates.UpdatesTemplate = com.example.chat.core.templates.ChatyTemplateRegistry.messageFirst.updates,
    confirmBeforeSendingStatus: () -> Boolean = { true },
    myNameOverride: () -> String = { "" },
    contactNameColor: () -> Long? = { null },
    recentUpdatesBarColor: () -> Long? = { null },
    recentUpdatesTextColor: () -> Long? = { null },
    pickStatusMedia: suspend (mediaType: String) -> PickedStatusMedia? = { null },
    captureStory: suspend () -> CapturedStoryMedia? = { null },
    /** Remote media renderer; null selects the default Coil-backed fitted renderer below. */
    remoteImage: (@Composable (url: String, modifier: Modifier) -> Unit)? = null,
    openUrl: (String) -> Unit = {},
    /** SnackBar equivalent for the exact toast strings. */
    onNotify: (String) -> Unit = {},
    /** Custom image renderers may report decoded intrinsic pixel dimensions here.
     *  Without dimensions zoom stays centered rather than allowing unbounded letterbox pan. */
    imageSizeFor: ((String) -> androidx.compose.ui.geometry.Size?)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val paneLabel = stringResource(R.string.social_updates_title)
    val loadingLabel = stringResource(R.string.social_updates_loading)
    val ownLabel = stringResource(R.string.social_updates_own)
    var localQuery by remember(currentUser.id) { mutableStateOf("") }
    val statusAvailable = statusService.isAvailable()
    var measuredImage by remember { mutableStateOf<Pair<String, androidx.compose.ui.geometry.Size>?>(null) }
    val defaultRemoteImage: @Composable (String, Modifier) -> Unit = { url, m ->
        StatusFittedImage(url, m, colors) { size -> measuredImage = url to size }
    }
    val effectiveImageSizeFor: (String) -> androidx.compose.ui.geometry.Size? = imageSizeFor
        ?: { url -> if (remoteImage == null) measuredImage?.takeIf { it.first == url }?.second else null }
    val effectiveRemoteImage = remoteImage ?: defaultRemoteImage
    val viewSession = remember(currentUser.id) { StatusViewSession() }
    var feedError by remember(currentUser.id) { mutableStateOf(false) }
    var feedAttempt by remember(currentUser.id) { mutableStateOf(0) }
    // collectAsState kept the 60s status poll running while the app sat
    // backgrounded on this tab. Gate collection on STARTED instead: polling
    // pauses in the background and the cold flow re-polls immediately on
    // resume, so the feed is fresh when the user returns.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val statusList by androidx.compose.runtime.produceState<List<com.example.chat.data.services.StatusRecord>?>(
        initialValue = null,
        statuses,
        lifecycleOwner,
        currentUser.id,
        feedAttempt,
    ) {
        value = null
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            feedError = false
            try { statuses.collect { value = it; feedError = false } } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { feedError = true }
        }
    }
    var composerOpen by remember(currentUser.id) { mutableStateOf(false) }
    var openStatus by remember(currentUser.id) { mutableStateOf<StatusRecord?>(null) }
    var cameraBusy by remember(currentUser.id) { mutableStateOf(false) }
    var pendingStory by remember(currentUser.id) { mutableStateOf<StatusPendingStory?>(null) }
    val activeUserId by androidx.compose.runtime.rememberUpdatedState(currentUser.id)

    fun showError(error: Throwable) {
        onNotify(context.getString(R.string.social_updates_error))
    }

    fun publishStory(action: StatusPendingStory) {
        val ownerId = action.ownerId
        if (activeUserId != ownerId || !action.consumeOnce()) return
        val result = action.captured
        cameraBusy = true
        scope.launch {
            try {
                if (activeUserId != ownerId) return@launch
                statusService.publishMediaBytes(
                    bytes = result.bytes,
                    displayName = "chaty_story.jpg",
                    mediaType = "image",
                    text = result.caption,
                )
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (activeUserId == ownerId) onNotify(context.getString(R.string.social_updates_posted))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (activeUserId == ownerId) onNotify(context.getString(R.string.social_updates_post_failure))
            } finally { cameraBusy = false }
        }
    }

    fun captureAndPublishStory() {
        if (cameraBusy || pendingStory != null) return
        val ownerId = currentUser.id
        cameraBusy = true
        scope.launch {
            var handedToPublisher = false
            try {
                val result = captureStory() ?: return@launch
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (activeUserId != ownerId) return@launch
                val action = StatusPendingStory(ownerId, result)
                if (confirmBeforeSendingStatus()) pendingStory = action
                else { handedToPublisher = true; publishStory(action) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (activeUserId == ownerId) onNotify(context.getString(R.string.social_updates_capture_failure))
            } finally { if (!handedToPublisher) cameraBusy = false }
        }
    }

    pendingStory?.let { action ->
        val captured = action.captured
        UpdatesConfirmDialog(
            title = stringResource(R.string.social_updates_camera_confirm_title),
            message = stringResource(R.string.social_updates_camera_confirm_body) +
                if (captured.caption.isBlank()) "" else stringResource(R.string.social_updates_caption, captured.caption),
            confirmLabel = stringResource(R.string.social_updates_send),
            colors = colors,
            onConfirm = {
                if (pendingStory === action && !cameraBusy && activeUserId == action.ownerId) {
                    pendingStory = null
                    publishStory(action)
                }
            },
            onDismiss = { if (pendingStory === action) pendingStory = null },
        )
    }

    ProvideStatusTime {
    val titleAlreadyVisible = com.example.chat.ui.navigation.LocalDestinationTitlePresentation.current ==
        com.example.chat.core.templates.ChatyNavigationDestinationIds.updates
    Column(modifier = modifier.fillMaxSize().semantics { paneTitle = paneLabel }.verticalScroll(rememberScrollState())) {
        if (!titleAlreadyVisible) Text(
            paneLabel,
            color = colors.foreground,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.semantics { heading() }.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        )

        // My Status composer card
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceElevated)
                .clickable(enabled = statusAvailable) { composerOpen = true }
                .padding(8.dp),
        ) {
            Box {
                TaskAvatar(currentUser.avatarInitials, currentUser.avatarColorHex, 48, colors)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .border(2.dp, colors.surface, CircleShape)
                        .padding(2.dp),
                ) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.add, semanticLabel = null, color = colors.onPrimary, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    myNameOverride().ifEmpty { ownLabel },
                    color = colors.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.social_updates_composer_summary),
                    color = colors.foregroundSecondary,
                    fontSize = 12.5.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(enabled = statusAvailable && !cameraBusy && pendingStory == null) { captureAndPublishStory() }
                    .padding(8.dp),
            ) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.camera,
                    semanticLabel = stringResource(R.string.social_updates_capture_photo),
                    color = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = colors.foregroundTertiary)
        }

        // Screen-local only: this query is never passed to the feed, directory or publish service.
        OutlinedTextField(
            value = localQuery,
            onValueChange = { localQuery = it },
            label = { Text(stringResource(R.string.social_updates_search_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.foreground,
                unfocusedTextColor = colors.foreground,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.borderSubtle,
                focusedLabelColor = colors.primary,
                unfocusedLabelColor = colors.foregroundSecondary,
                cursorColor = colors.primary,
            ),
            trailingIcon = {
                if (localQuery.isNotEmpty()) androidx.compose.material3.TextButton(onClick = { localQuery = "" }) {
                    Text(stringResource(R.string.social_updates_search_clear))
                }
            },
        )

        // RECENT UPDATES header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(recentUpdatesBarColor()?.let(::Color) ?: Color.Transparent)
                .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        ) {
            Text(
                stringResource(R.string.social_updates_recent_heading),
                modifier = Modifier.semantics { heading() },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = recentUpdatesTextColor()?.let(::Color) ?: colors.primary,
                letterSpacing = 0.8.sp,
            )
        }

        val list = statusList
        val visibleStatuses = list?.let { source ->
            StatusLocalSearchRules.filter(source, localQuery) { id ->
                val person = if (id == currentUser.id) currentUser else getUser(id)
                person?.let { listOf(it.displayName, it.username) }.orEmpty()
            }
        }
        if (!statusAvailable) {
            Text(
                stringResource(R.string.social_updates_unavailable),
                color = colors.foregroundSecondary,
                modifier = Modifier.padding(24.dp),
            )
        } else if (feedError) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.social_updates_feed_failure), color = colors.foregroundSecondary)
                androidx.compose.material3.TextButton(onClick = { feedAttempt++ }) { Text(stringResource(R.string.social_updates_retry_feed)) }
            }
        } else if (list == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.2.dp, modifier = Modifier.semantics { contentDescription = loadingLabel })
            }
        } else if (list.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            ) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.autoAwesome, semanticLabel = null, color = colors.foregroundTertiary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.social_updates_empty), color = colors.foregroundSecondary, fontSize = 12.5.sp)
            }
        } else if (visibleStatuses.isNullOrEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.social_updates_search_no_matches), color = colors.foregroundSecondary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                androidx.compose.material3.TextButton(onClick = { localQuery = "" }) {
                    Text(stringResource(R.string.social_updates_search_clear))
                }
            }
        } else {
            StatusList(
                // Previous source retained: statuses = list,
                statuses = visibleStatuses,
                template = template,
                colors = colors,
                currentUser = currentUser,
                getUser = getUser,
                contactNameColor = contactNameColor,
                onOpen = { openStatus = it },
            )
        }
        Spacer(Modifier.height(80.dp))
    }

    if (composerOpen) {
        androidx.compose.runtime.key(currentUser.id) {
        UpdatesComposerSheet(
            statusService = statusService,
            colors = colors,
            confirmBeforeSendingStatus = confirmBeforeSendingStatus,
            pickStatusMedia = pickStatusMedia,
            onDismiss = { composerOpen = false },
            onError = ::showError,
        )
        }
    }

    LaunchedEffect(statusList, currentUser.id, openStatus?.id) {
        val selected = openStatus
        val current = statusList
        if (selected != null && current != null) {
            openStatus = current.firstOrNull { it.id == selected.id && it.expiresAt.isAfter(java.time.Instant.now()) }
        }
    }

    openStatus?.let { status ->
        LaunchedEffect(status.id, status.expiresAt) {
            while (true) {
                val wait = StatusRelativeTimeRules.expiryWaitMillis(status.expiresAt, java.time.Instant.now())
                if (wait == 0L) break
                kotlinx.coroutines.delay(wait)
            }
            openStatus = null
        }
        androidx.compose.runtime.key(currentUser.id, status.id) {
        StatusViewerDialog(
            status = status,
            statusService = statusService,
            colors = colors,
            currentUser = currentUser,
            getUser = getUser,
            template = template,
            viewSession = viewSession,
            remoteImage = effectiveRemoteImage,
            imageSizeFor = effectiveImageSizeFor,
            openUrl = openUrl,
            onDismiss = { openStatus = null },
        )
        }
    }
    }
}

@Composable
private fun StatusList(
    statuses: List<StatusRecord>,
    template: com.example.chat.core.templates.UpdatesTemplate,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    contactNameColor: () -> Long?,
    onOpen: (StatusRecord) -> Unit,
) {
    when (template.layoutMode) {
        UpdatesLayoutMode.circularRail -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(vertical = 5.dp),
        ) {
            items(statuses.size, key = { statuses[it].id }) { index ->
                CircularStatusCard(statuses[index], colors, currentUser, getUser, contactNameColor, onOpen)
            }
        }
        UpdatesLayoutMode.gridTiles -> {
            // Dart LayoutBuilder: columns by max width (>=840:4, >=540:3,
            // >=340:2, else 1); single column falls back to squircle cards.
            androidx.compose.foundation.layout.BoxWithConstraints {
                val columns = when {
                    maxWidth >= 840.dp -> 4
                    maxWidth >= 540.dp -> 3
                    maxWidth >= 340.dp -> 2
                    else -> 1
                }
                if (columns == 1) {
                    Column {
                        statuses.forEach { status ->
                            SquircleStatusCard(status, template, colors, currentUser, getUser, contactNameColor, onOpen)
                            Spacer(Modifier.height(9.dp))
                        }
                    }
                } else {
                    // The screen already scrolls. Content-sized rows avoid a second
                    // fixed-height viewport clipping cards at larger text scales.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        statuses.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { status ->
                                    Box(Modifier.weight(1f)) {
                                        GridStatusCard(status, template, colors, currentUser, getUser, contactNameColor, onOpen)
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        UpdatesLayoutMode.squircleCards -> Column {
            statuses.forEach { status ->
                SquircleStatusCard(status, template, colors, currentUser, getUser, contactNameColor, onOpen)
                Spacer(Modifier.height(9.dp))
            }
        }
        UpdatesLayoutMode.minimalList -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceElevated),
        ) {
            statuses.forEach { status ->
                MinimalStatusTile(status, colors, currentUser, getUser, contactNameColor, onOpen)
            }
        }
    }
}

private fun isMyStatus(status: StatusRecord, currentUser: UserProfile) =
    status.userId == currentUser.id

@Composable
private fun statusTitle(status: StatusRecord, currentUser: UserProfile, getUser: (String) -> UserProfile?): String =
    if (isMyStatus(status, currentUser)) stringResource(R.string.social_updates_own)
    else getUser(status.userId)?.displayName?.let(StatusIdentityLabelRules::display) ?: stringResource(R.string.social_updates_contact)

private fun statusSubtitle(status: StatusRecord): String =
    status.text.ifEmpty { status.mediaName?.let { StatusMediaReadRules.displayName(it) } ?: status.mediaType }

@Composable
private fun StatusAvatar(
    status: StatusRecord,
    size: Int,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
) {
    val mine = isMyStatus(status, currentUser)
    val user = if (mine) null else getUser(status.userId)
    TaskAvatar(
        initials = StatusIdentityLabelRules.display(if (mine) currentUser.avatarInitials else user?.avatarInitials ?: "U"),
        colorHex = if (mine) currentUser.avatarColorHex else user?.avatarColorHex ?: "0xFF6366F1",
        size = size,
        colors = colors,
    )
}

@Composable
private fun statusNameColor(colors: ChatyColors, contactNameColor: () -> Long?): Color =
    contactNameColor()?.let(::Color) ?: colors.foreground

/** Describes the declared type only, not decode support, playback or read state. */
@Composable
private fun statusMediaTypeLabel(status: StatusRecord): String = stringResource(when (status.mediaType) {
    "text" -> R.string.social_updates_type_text
    "image" -> R.string.social_updates_type_image
    "video" -> R.string.social_updates_type_video
    "audio" -> R.string.social_updates_type_audio
    "document" -> R.string.social_updates_type_file
    else -> R.string.social_updates_type_unknown
})

@Composable
private fun CircularStatusCard(
    status: StatusRecord,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    contactNameColor: () -> Long?,
    onOpen: (StatusRecord) -> Unit,
) {
    val mediaTypeLabel = statusMediaTypeLabel(status)
    val mine = isMyStatus(status, currentUser)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(98.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { stateDescription = mediaTypeLabel }
                .clickable { onOpen(status) },
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (mine) listOf(colors.primary, colors.primary.copy(alpha = 0.35f))
                        else listOf(colors.primary, colors.info),
                    ),
                )
                .padding(3.dp),
        ) {
            Box(
                modifier = Modifier.clip(CircleShape).background(colors.background).padding(2.dp),
            ) {
                StatusAvatar(status, 54, colors, currentUser, getUser)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            statusTitle(status, currentUser, getUser),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = statusNameColor(colors, contactNameColor),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        StatusTimeLabel(status.createdAt, colors.foregroundTertiary)
    }
}

@Composable
internal fun GridStatusCard(
    status: StatusRecord,
    template: com.example.chat.core.templates.UpdatesTemplate,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    contactNameColor: () -> Long?,
    onOpen: (StatusRecord) -> Unit,
) {
    val mediaTypeLabel = statusMediaTypeLabel(status)
    androidx.compose.material3.Surface(
        shadowElevation = template.cardElevation.dp,
        color = colors.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.heightIn(min = 174.dp),
    ) {
        Column(
            modifier = Modifier
                .semantics(mergeDescendants = true) { stateDescription = mediaTypeLabel }
                .clickable { onOpen(status) }
                .padding(12.dp),
        ) {
            Row {
                StatusAvatar(status, 42, colors, currentUser, getUser)
                Spacer(Modifier.weight(1f))
                Icon(
                    if (status.hasMedia) Icons.Rounded.PlayCircleOutline else Icons.Rounded.TextSnippet,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                statusTitle(status, currentUser, getUser),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = statusNameColor(colors, contactNameColor),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                statusSubtitle(status),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            StatusTimeLabel(status.createdAt, colors.foregroundTertiary)
        }
    }
}

@Composable
private fun SquircleStatusCard(
    status: StatusRecord,
    template: com.example.chat.core.templates.UpdatesTemplate,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    contactNameColor: () -> Long?,
    onOpen: (StatusRecord) -> Unit,
) {
    val mediaTypeLabel = statusMediaTypeLabel(status)
    androidx.compose.material3.Surface(
        shadowElevation = template.cardElevation.dp,
        color = colors.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .semantics(mergeDescendants = true) { stateDescription = mediaTypeLabel }
                .clickable { onOpen(status) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, colors.primary, RoundedCornerShape(18.dp))
                    .padding(3.dp),
            ) {
                StatusAvatar(status, 44, colors, currentUser, getUser)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    statusTitle(status, currentUser, getUser),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = statusNameColor(colors, contactNameColor),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    statusSubtitle(status),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.foregroundSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusTimeLabel(status.createdAt, colors.foregroundTertiary)
        }
    }
}

@Composable
private fun MinimalStatusTile(
    status: StatusRecord,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    contactNameColor: () -> Long?,
    onOpen: (StatusRecord) -> Unit,
) {
    val mediaTypeLabel = statusMediaTypeLabel(status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { stateDescription = mediaTypeLabel }
                .clickable { onOpen(status) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        StatusAvatar(status, 34, colors, currentUser, getUser)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                statusTitle(status, currentUser, getUser),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = statusNameColor(colors, contactNameColor),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                statusSubtitle(status),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
            )
        }
        StatusTimeLabel(status.createdAt, colors.foregroundTertiary)
    }
}
