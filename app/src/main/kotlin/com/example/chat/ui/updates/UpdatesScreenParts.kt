package com.example.chat.ui.updates

import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Image
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.chat.R
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.collectionInfo
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Send
import com.example.chat.ui.kit.ChatyActionButton
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import com.example.chat.ui.kit.ChatyTextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

/** Port of the Dart ChatyConfirmDialog usage in the updates slice:
 *  title/message/confirmLabel (+ destructive). */
@Composable
internal fun UpdatesConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    colors: ChatyColors,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            ChatyTextButton(
                onClick = onConfirm,
                label = confirmLabel,
                color = if (destructive) colors.error else colors.primary,
            )
        },
        dismissButton = { ChatyTextButton(onClick = onDismiss, label = "Cancel") },
    )
}

/** Exact port of _openComposer's modal sheet: drag handle, 'New Update',
 *  expiry caption, 3-line input, Photo/Video/Audio/File actions and the
 *  'Publish Update' text button with per-action busy spinners and the
 *  confirm-before-send gate on every publish path. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdatesComposerSheet(
    statusService: StatusService,
    colors: ChatyColors,
    confirmBeforeSendingStatus: () -> Boolean,
    pickStatusMedia: suspend (mediaType: String) -> PickedStatusMedia?,
    onDismiss: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var work by remember { mutableStateOf<StatusPublicationWork?>(null) }
    val busyType = work?.type
    var pendingConfirm by remember { mutableStateOf<StatusPendingPublication?>(null) }

    fun submit(action: StatusPendingPublication) {
        if (work != null || !action.consumeOnce()) return
        work = StatusPublicationWork.Publishing(action)
        scope.launch {
            try {
                when (action) {
                    is StatusPendingPublication.Text -> statusService.publishText(action.value)
                    is StatusPendingPublication.Media -> statusService.publishMediaBytes(
                        bytes = action.picked.bytes, displayName = action.picked.displayName,
                        mediaType = action.type, text = action.caption, mimeType = action.picked.mimeType,
                        durationSeconds = action.picked.durationSeconds,
                    )
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                onDismiss()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                onError(error)
            }
            finally { work = null }
        }
    }

    fun publishMedia(type: String) {
        if (work != null || pendingConfirm != null) return
        val caption = text
        val selection = StatusPublicationWork.Picking(type)
        work = selection
        scope.launch {
            try {
                val picked = pickStatusMedia(type) ?: return@launch
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val action = StatusPendingPublication.Media(type, picked, caption)
                if (work !== selection) return@launch
                work = null
                if (confirmBeforeSendingStatus()) pendingConfirm = action else submit(action)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                onError(error)
            }
            finally { if (work === selection) work = null }
        }
    }

    fun publishText() {
        if (work != null || pendingConfirm != null || text.isBlank()) return
        val action = StatusPendingPublication.Text(text.trim())
        if (confirmBeforeSendingStatus()) pendingConfirm = action else submit(action)
    }

    val publishingLabel = stringResource(R.string.social_status_publishing)
    val publicationState = when (work) {
        is StatusPublicationWork.Picking -> stringResource(R.string.social_status_picking_media)
        is StatusPublicationWork.Publishing -> if (busyType == "text") stringResource(R.string.social_status_posting_text)
            else stringResource(R.string.social_status_uploading_media)
        null -> null
    }

    ModalBottomSheet(tonalElevation = 0.dp, containerColor = com.example.chat.ui.theme.LocalAppColors.current.surfaceElevated, dragHandle = null, shape = com.example.chat.ui.kit.ChatyModalShape, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(colors.foreground.copy(alpha = 0.2f), CircleShape),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.social_status_composer_title), color = colors.foreground, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.social_status_composer_expiry),
                color = colors.foreground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            publicationState?.let { label ->
                Text(label, color = colors.foregroundSecondary,
                    modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.social_status_composer_input)) },
                placeholder = { Text(stringResource(R.string.social_status_composer_hint)) },
                enabled = busyType == null && pendingConfirm == null,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row {
                ComposerAction(Icons.Rounded.Image, stringResource(R.string.social_status_photo), busyType == "image", colors, Modifier.weight(1f), enabled = busyType == null && pendingConfirm == null) {
                    if (busyType == null) publishMedia("image")
                }
                Spacer(Modifier.width(8.dp))
                ComposerAction(Icons.Rounded.Videocam, stringResource(R.string.social_status_video), busyType == "video", colors, Modifier.weight(1f), enabled = busyType == null && pendingConfirm == null) {
                    if (busyType == null) publishMedia("video")
                }
                Spacer(Modifier.width(8.dp))
                ComposerAction(Icons.Rounded.GraphicEq, stringResource(R.string.social_status_audio), busyType == "audio", colors, Modifier.weight(1f), enabled = busyType == null && pendingConfirm == null) {
                    if (busyType == null) publishMedia("audio")
                }
                Spacer(Modifier.width(8.dp))
                ComposerAction(Icons.Rounded.Description, stringResource(R.string.social_status_file), busyType == "document", colors, Modifier.weight(1f), enabled = busyType == null && pendingConfirm == null) {
                    if (busyType == null) publishMedia("document")
                }
            }
            Spacer(Modifier.height(20.dp))
            ChatyActionButton(
                onClick = { if (busyType == null) publishText() },
                enabled = busyType == null && pendingConfirm == null && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busyType == "text") {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp).semantics { contentDescription = publishingLabel }, strokeWidth = 2.dp)
                } else {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.send, semanticLabel = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.social_status_publish))
                }
            }
        }
    }

    pendingConfirm?.let { action ->
        val message = when (action) {
            is StatusPendingPublication.Text -> stringResource(R.string.social_status_confirm_text, action.value)
            is StatusPendingPublication.Media -> stringResource(R.string.social_status_confirm_media,
                StatusMediaReadRules.displayName(action.picked.displayName)) +
                if (action.caption.isBlank()) "" else stringResource(R.string.social_status_confirm_caption, action.caption)
        }
        UpdatesConfirmDialog(
            title = stringResource(if (action is StatusPendingPublication.Text) R.string.social_status_confirm_text_title else R.string.social_status_confirm_media_title),
            message = message,
            confirmLabel = stringResource(R.string.social_status_send),
            colors = colors,
            onConfirm = {
                // Check identity as well as the one-shot ticket: an old dismissed dialog is not approval.
                if (pendingConfirm === action && work == null) {
                    pendingConfirm = null
                    submit(action)
                }
            },
            onDismiss = { if (pendingConfirm === action) pendingConfirm = null },
        )
    }
}

@Composable
private fun ComposerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    busy: Boolean,
    colors: ChatyColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onTap: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceSecondary)
            .clickable(enabled = enabled, onClick = onTap)
            .padding(vertical = 10.dp, horizontal = 8.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.foreground)
    }
}

/** Exact port of _openStatus's fullscreen dialog: header (back, avatar,
 *  title, delete for own), centered content, caption text, views control
 *  for own statuses. The StatusViewSession view recording with rollback
 *  ports _recordStatusView exactly. */
@Composable
internal fun StatusViewerDialog(
    status: StatusRecord,
    statusService: StatusService,
    colors: ChatyColors,
    currentUser: UserProfile,
    getUser: (String) -> UserProfile?,
    template: com.example.chat.core.templates.UpdatesTemplate,
    viewSession: StatusViewSession,
    remoteImage: @Composable (url: String, modifier: Modifier) -> Unit,
    imageSizeFor: (String) -> androidx.compose.ui.geometry.Size?,
    openUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewerPane = stringResource(R.string.social_status_viewer_pane)
    val mediaLoadingLabel = stringResource(R.string.social_status_media_loading)
    val mediaFailureLabel = stringResource(R.string.social_status_media_failure)
    var signedUrl by remember(status.id, currentUser.id) { mutableStateOf<String?>(null) }
    var urlLoading by remember(status.id, currentUser.id) { mutableStateOf(status.hasMedia) }
    var urlAttempt by remember(status.id, currentUser.id) { mutableStateOf(0) }
    var deleteError by remember(status.id) { mutableStateOf<String?>(null) }
    var deleting by remember(status.id) { mutableStateOf(false) }
    var viewersRevision by remember(status.id) { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewersOpen by remember { mutableStateOf(false) }

    LaunchedEffect(statusService, status.id, status.mediaPath, status.expiresAt, currentUser.id, urlAttempt) {
        if (status.hasMedia) {
            urlLoading = true
            signedUrl = null
            try {
                val resolved = status.mediaPath?.let { statusService.createSignedUrl(it) }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // A returned URL is data, not permission to load a local file or arbitrary scheme.
                signedUrl = resolved?.takeIf { StatusExternalOpenPolicy.canOpen(it, status.expiresAt) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                signedUrl = null
            } finally { urlLoading = false }
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val viewerId = currentUser.id
        if (viewSession.tryBegin(status, viewerId)) {
            launch {
                try {
                    val published = statusService.markViewed(status)
                    if (!published) viewSession.rollback(status, viewerId)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    viewSession.rollback(status, viewerId)
                    throw cancelled
                } catch (error: Exception) {
                    viewSession.rollback(status, viewerId)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding().semantics { paneTitle = viewerPane }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = stringResource(R.string.social_status_viewer_back), color = colors.foreground)
                }
                Spacer(Modifier.width(8.dp))
                val owner = getUser(status.userId)
                TaskAvatar(StatusIdentityLabelRules.display(owner?.avatarInitials ?: "U"), owner?.avatarColorHex ?: "0xFF6366F1", 38, colors)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (status.userId == currentUser.id) stringResource(R.string.social_status_viewer_own) else owner?.displayName?.let(StatusIdentityLabelRules::display) ?: stringResource(R.string.social_status_viewer_contact),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                if (status.userId == currentUser.id) {
                    IconButton(onClick = { confirmDelete = true }, enabled = !deleting) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.deleteOutline, semanticLabel = "Delete status", color = colors.error)
                    }
                }
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    urlLoading -> CircularProgressIndicator(
                        modifier = Modifier.semantics { contentDescription = mediaLoadingLabel })
                    status.hasMedia && signedUrl == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(mediaFailureLabel, color = colors.foregroundSecondary,
                            modifier = Modifier.semantics { error(mediaFailureLabel) })
                        ChatyTextButton(onClick = { urlAttempt++ }, label = stringResource(R.string.social_status_viewers_retry))
                    }
                    else -> StatusContent(status, signedUrl, colors, template, remoteImage, openUrl, imageSizeFor)
                }
            }
            deleteError?.let { Text(it, color = colors.error, modifier = Modifier.padding(16.dp)) }
            if (deleting) CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = "Deleting status" })
            if (status.mediaType != "text" && status.text.isNotEmpty()) {
                Text(
                    status.text,
                    textAlign = TextAlign.Center,
                    color = colors.foreground,
                    fontSize = 16.sp,
                    lineHeight = 22.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
                )
            }
            if (status.userId == currentUser.id) {
                ViewsControl(statusService, status, colors, viewersRevision) { viewersOpen = true }
            }
        }
    }

    if (confirmDelete) {
        UpdatesConfirmDialog(
            title = "Delete this update?",
            message = "This update will be deleted for everyone and removed from your status.",
            confirmLabel = "Delete",
            destructive = true,
            colors = colors,
            onConfirm = {
                confirmDelete = false
                if (!deleting) {
                    deleting = true
                    deleteError = null
                    scope.launch {
                        try {
                            statusService.deleteStatus(status)
                            onDismiss()
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            deleteError = "Unable to delete this update. Try again."
                        } finally { deleting = false }
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (viewersOpen) {
        StatusViewersSheet(status.id, statusService, colors) { viewersOpen = false; viewersRevision++ }
    }
}

@Composable
internal fun StatusContent(
    status: StatusRecord,
    signedUrl: String?,
    colors: ChatyColors,
    template: com.example.chat.core.templates.UpdatesTemplate,
    remoteImage: @Composable (url: String, modifier: Modifier) -> Unit,
    openUrl: (String) -> Unit,
    imageSizeFor: (String) -> androidx.compose.ui.geometry.Size? = { null },
) {
    val mediaFailureLabel = stringResource(R.string.social_status_media_failure)
    val photoDescription = stringResource(R.string.social_status_photo_description)
    if (status.mediaType == "text") {
        Text(
            status.text,
            textAlign = TextAlign.Center,
            color = colors.foreground,
            fontSize = 26.sp,
            lineHeight = 35.1.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
        return
    }
    if (signedUrl == null) {
        Text(
            mediaFailureLabel,
            modifier = Modifier.semantics { error(mediaFailureLabel) },
            color = colors.foreground.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
        return
    }
    if (status.mediaType == "audio" && template.enableStatusAudio) {
        StatusAudioPlayer(url = signedUrl, name = status.mediaName?.let { StatusMediaReadRules.displayName(it) } ?: stringResource(R.string.social_status_audio_name), colors = colors)
        return
    }
    if (status.mediaType == "image") {
        var scale by remember(status.id, signedUrl) { mutableStateOf(1f) }
        var offsetX by remember(status.id, signedUrl) { mutableStateOf(0f) }
        var offsetY by remember(status.id, signedUrl) { mutableStateOf(0f) }
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
                val intrinsic = imageSizeFor(signedUrl)
                val viewportWidth = constraints.maxWidth.toFloat()
                val viewportHeight = constraints.maxHeight.toFloat()
                val bounds = StatusZoomRules.bounds(viewportWidth, viewportHeight,
                    intrinsic?.width ?: 0f, intrinsic?.height ?: 0f, scale)
                val maxPanX = bounds.first
                val maxPanY = bounds.second
                LaunchedEffect(maxPanX, maxPanY) {
                    offsetX = offsetX.coerceIn(-maxPanX, maxPanX)
                    offsetY = offsetY.coerceIn(-maxPanY, maxPanY)
                }
                remoteImage(
                    signedUrl,
                    Modifier.fillMaxSize()
                        .pointerInput(signedUrl, intrinsic, viewportWidth, viewportHeight) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                val nextBounds = StatusZoomRules.bounds(size.width.toFloat(), size.height.toFloat(),
                                    intrinsic?.width ?: 0f, intrinsic?.height ?: 0f, scale)
                                val boundX = nextBounds.first
                                val boundY = nextBounds.second
                                offsetX = (offsetX + pan.x).coerceIn(-boundX, boundX)
                                offsetY = (offsetY + pan.y).coerceIn(-boundY, boundY)
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX.coerceIn(-maxPanX, maxPanX)
                            translationY = offsetY.coerceIn(-maxPanY, maxPanY)
                        }
                        .semantics { contentDescription = photoDescription },
                )
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                ChatyTextButton(onClick = { scale = (scale - 0.5f).coerceAtLeast(1f); offsetX = 0f; offsetY = 0f }, label = stringResource(R.string.social_status_zoom_out))
                ChatyTextButton(onClick = { scale = (scale + 0.5f).coerceAtMost(5f) }, label = stringResource(R.string.social_status_zoom_in))
                ChatyTextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }, label = stringResource(R.string.social_status_zoom_reset))
            }
        }
        return
    }
    val icon = when (status.mediaType) {
        "video" -> Icons.Rounded.PlayCircleFilled
        "audio" -> Icons.Rounded.GraphicEq
        else -> Icons.Rounded.Description
    }
    Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(84.dp))
        if (status.mediaType !in setOf("video", "audio", "document")) {
            Text(stringResource(R.string.social_status_unsupported_media), color = colors.foregroundSecondary)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            StatusMediaReadRules.displayName(status.mediaName ?: status.mediaType),
            textAlign = TextAlign.Center,
            color = colors.foreground,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.social_status_external_disclosure),
            color = colors.foregroundSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        ChatyActionButton(
            onClick = {
                if (StatusExternalOpenPolicy.canOpen(signedUrl, status.expiresAt)) {
                    openUrl(signedUrl)
                }
            },
            icon = Icons.Rounded.OpenInNew, label = stringResource(R.string.social_status_external_open),
        )
    }
}

/** Port of _buildViewsControl: swipe-up or tap pill showing last-loaded viewer
 *  count, opening the viewers sheet. */
@Composable
private fun ViewsControl(
    statusService: StatusService,
    status: StatusRecord,
    colors: ChatyColors,
    revision: Int,
    onOpen: () -> Unit,
) {
    val dragThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
    var count by remember(statusService, status.id) { mutableStateOf<Int?>(null) }
    var countLoading by remember(statusService, status.id) { mutableStateOf(true) }
    var countAttempt by remember(statusService, status.id) { mutableStateOf(0) }
    LaunchedEffect(statusService, status.id, revision, countAttempt) {
        countLoading = true
        count = null
        val loaded = try {
            val result = statusService.viewersFor(status.id).distinctBy { it.userId }.size
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            result
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            null
        }
        count = loaded
        countLoading = false
    }
    Box(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceElevated.copy(alpha = 0.85f))
                .border(0.8.dp, colors.borderSubtle, RoundedCornerShape(24.dp))
                .pointerInput(status.id, dragThreshold) {
                    // Dart onVerticalDragEnd: primaryVelocity < -150 opens
                    // the sheet. Compose reports no fling velocity here, so
                    // accumulate the drag and open on a clear upward swipe.
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -dragThreshold) onOpen() },
                    ) { _, dragAmount -> total += dragAmount }
                }
                .clickable(onClick = onOpen)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {

//Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.chevronUp, Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.eye, semanticLabel = null, color = colors.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (countLoading) stringResource(R.string.social_status_views_loading) else count?.let { pluralStringResource(R.plurals.social_status_view_count, it, it) } ?: stringResource(R.string.social_status_views_unavailable),
                    color = colors.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!countLoading && count == null) {
                ChatyTextButton(
                    onClick = { if (!countLoading) { countLoading = true; countAttempt++ } },
                    label = stringResource(R.string.social_status_viewers_retry),
                )
            }
        }
    }
}

/** Port of _StatusViewersSheet: 'Viewed by' sheet with loading, error +
 *  retry, empty state and the viewer list (avatar, name, @username,
 *  relative time with 'ago'). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusViewersSheet(
    statusId: String,
    statusService: StatusService,
    colors: ChatyColors,
    onDismiss: () -> Unit,
) {
    val viewersLoadingLabel = stringResource(R.string.social_status_viewers_loading)
    val viewersPaneLabel = stringResource(R.string.social_status_viewers_title)
    var viewers by remember(statusService, statusId) { mutableStateOf<List<StatusViewer>?>(null) }
    var failed by remember(statusService, statusId) { mutableStateOf(false) }

    fun load() {
        failed = false
        viewers = null
    }
    LaunchedEffect(statusService, statusId, viewers, failed) {
        if (viewers == null && !failed) {
            viewers = try {
                val loaded = statusService.viewersFor(statusId)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                loaded.distinctBy { it.userId }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                failed = true
                null
            }
        }
    }

    ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp, containerColor = com.example.chat.ui.theme.LocalAppColors.current.surfaceElevated, shape = com.example.chat.ui.kit.ChatyModalShape, onDismissRequest = onDismiss) {
        Column(Modifier.heightIn(max = 480.dp).semantics { paneTitle = viewersPaneLabel }) {
            Text(
                stringResource(R.string.social_status_viewers_title),
                color = colors.foreground,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp).semantics { heading() },
            )
            val list = viewers
            when {
                failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.eye, semanticLabel = null, color = colors.foregroundTertiary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.social_status_viewers_failure_title), color = colors.foreground, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.social_status_viewers_failure_body),
                        color = colors.foregroundSecondary,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                    )
                    ChatyTextButton(onClick = ::load, label = stringResource(R.string.social_status_viewers_retry))
                }
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.semantics { contentDescription = viewersLoadingLabel })
                }
                list.isEmpty() -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.eye, semanticLabel = null, color = colors.foregroundTertiary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.social_status_viewers_empty_title), color = colors.foreground, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.social_status_viewers_empty_body),
                        color = colors.foregroundSecondary,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(Modifier.padding(bottom = 16.dp).semantics { collectionInfo = androidx.compose.ui.semantics.CollectionInfo(list.size, 1) }) {
                    items(list.size, key = { index -> list[index].userId }, contentType = { "viewer" }) { index ->
                        val viewer = list[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            TaskAvatar(StatusIdentityLabelRules.display(viewer.avatarInitials), viewer.avatarColorHex, 40, colors)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    StatusIdentityLabelRules.display(viewer.displayName),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.foreground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                )
                                Text(
                                    "@${StatusIdentityLabelRules.display(viewer.username)}",
                                    style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.foregroundSecondary,
                                    fontSize = 14.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                StatusTimeLabel(viewer.viewedAt, colors.foregroundTertiary)
                            }
                        }
                        if (index < list.size - 1) {
                            Box(
                                Modifier
                                    .padding(start = 66.dp)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.divider),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Exact port of _StatusAudioPlayer over Android MediaPlayer: loading and
 *  failed states with the exact copy, slider seek, mm:ss labels, play/pause
 *  stadium button, seek-to-start replay at the end. */
@Composable
internal fun StatusAudioPlayer(url: String, name: String, colors: ChatyColors) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val statusApi = com.example.chat.ServiceLocator.apiBackend
    val statusOwner = remember { statusApi.authSnapshot() }
    val player = remember(url, lifecycleOwner, statusOwner) { MediaPlayer() }
    val playbackScope = rememberCoroutineScope()
    var loading by remember(player) { mutableStateOf(true) }
    var failed by remember(player) { mutableStateOf(false) }
    var playing by remember(player) { mutableStateOf(false) }
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(0L) }
    var seekPreview by remember(player) { mutableStateOf<Float?>(null) }
    var sourceGeneration by remember(player) { androidx.compose.runtime.mutableIntStateOf(0) }
    var released by remember(player) { mutableStateOf(false) }
    var focusUnavailable by remember(player) { mutableStateOf(false) }
    var outputDisconnected by remember(player) { mutableStateOf(false) }
    val playbackGuard = remember(player, statusOwner) {
        com.example.chat.core.audio.CommunicationPlaybackGuard(
            arbiter = com.example.chat.ServiceLocator.communicationMediaArbiter,
            pauseNative = {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    if (player.isPlaying) player.pause()
                    playing = false
                }
            },
            disposeNative = {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    player.setOnPreparedListener(null)
                    player.setOnCompletionListener(null)
                    player.setOnErrorListener(null)
                    player.release()
                    playing = false
                }
            },
            isOwnerCurrent = { !released && statusApi.authSnapshot() === statusOwner &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) },
            onCallSuspended = {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    playing = false
                    focusUnavailable = true
                    seekPreview = null
                }
            },
        )
    }
    fun pausePlayback() {
        playbackScope.launch {
            try { playbackGuard.pause() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
    }
    val audioManager = remember(context) {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
    }
    val audioAttributes = remember {
        android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build()
    }
    val focusListener = remember(player) {
        android.media.AudioManager.OnAudioFocusChangeListener { change ->
            if (!released && (change == android.media.AudioManager.AUDIOFOCUS_LOSS ||
                    change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)) {
                pausePlayback()
                playing = false
                focusUnavailable = true
            }
        }
    }
    val focusRequest = remember(player, audioAttributes, focusListener) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                // Pause spoken clips rather than ducking or resuming without an explicit Play.
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(focusListener, android.os.Handler(android.os.Looper.getMainLooper()))
                .build()
        } else null
    }
    @Suppress("DEPRECATION")
    fun requestFocus(): Boolean = if (android.os.Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
        audioManager?.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else {
        audioManager?.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.AUDIOFOCUS_GAIN) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    @Suppress("DEPRECATION")
    fun abandonFocus() {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 26 && focusRequest != null) audioManager?.abandonAudioFocusRequest(focusRequest)
            else audioManager?.abandonAudioFocus(focusListener)
        }
    }

    fun loadSource() {
        if (released) return
        val expectedGeneration = ++sourceGeneration
        playing = false
        abandonFocus()
        focusUnavailable = false
        outputDisconnected = false
        loading = true
        failed = false
        positionMs = 0L
        durationMs = 0L
        seekPreview = null
        playbackScope.launch {
            try {
                playbackGuard.serializeNativeCleanup {
                if (released || expectedGeneration != sourceGeneration) return@serializeNativeCleanup
                player.reset()
                player.setAudioAttributes(audioAttributes)
                player.setDataSource(url)
                player.setOnPreparedListener {
                    if (!released && expectedGeneration == sourceGeneration) {
                        durationMs = it.duration.toLong().coerceAtLeast(0L)
                        loading = false
                    }
                }
                player.setOnCompletionListener {
                    if (!released && expectedGeneration == sourceGeneration) {
                        playing = false
                        abandonFocus()
                        positionMs = durationMs
                    }
                }
                player.setOnErrorListener { _, _, _ ->
                    if (!released && expectedGeneration == sourceGeneration) {
                        playing = false
                        abandonFocus()
                        loading = false
                        seekPreview = null
                        failed = true
                    }
                    true
                }
                player.prepareAsync()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                playing = false
                abandonFocus()
                loading = false
                failed = true
            }
        }
    }

    LaunchedEffect(player) { loadSource() }
    LaunchedEffect(player, playing) {
        if (!playing) abandonFocus()
        while (playing && !released) {
            try { positionMs = player.currentPosition.toLong().coerceAtLeast(0L) }
            catch (_: IllegalStateException) { playing = false; failed = true; break }
            delay(200)
        }
    }
    // Keep this listener for the player's lifetime so an output change cannot race
    // registration after Play. It never starts/resumes playback or inspects devices.
    DisposableEffect(context, player) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                if (intent.action != android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY || released || !playing) return
                pausePlayback()
                playing = false
                seekPreview = null
                outputDisconnected = true
                abandonFocus()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
            android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                pausePlayback()
                playing = false
                abandonFocus()
            }
        }
        val accountListener: (com.example.chat.data.api.ApiAccountScope) -> Unit = { next ->
            if (next !== statusOwner) { pausePlayback() }
        }
        statusApi.addAuthScopeListener(accountListener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            statusApi.removeAuthScopeListener(accountListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            released = true
            sourceGeneration++
            abandonFocus()
            playbackGuard.markClosing()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.Main.immediate).launch {
                // If native release fails, guard retains its reservation for call-side recovery.
                runCatching { playbackGuard.dispose() }
            }
        }
    }

    fun togglePlayback() {
        if (released || loading || failed || !lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        playbackScope.launch {
            try {
                if (playing) {
                    playbackGuard.pause()
                    playing = false
                    abandonFocus()
                } else {
                    val allowed = playbackGuard.runNative {
                        if (!requestFocus()) { focusUnavailable = true; return@runNative }
                        focusUnavailable = false
                        outputDisconnected = false
                        if (durationMs > 0 && positionMs >= durationMs - 250) {
                            player.seekTo(0)
                            positionMs = 0L
                        }
                        player.start()
                        playing = true
                    }
                    if (!allowed) { playing = false; focusUnavailable = true; abandonFocus() }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                playing = false
                abandonFocus()
                failed = true
            }
        }
    }

    fun timeLabel(ms: Long): String {
        val totalSeconds = ms / 1000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    val currentSpokenTime = audioSpokenDuration((seekPreview?.toLong() ?: positionMs).coerceAtLeast(0L))
    val totalSpokenTime = if (durationMs > 0) audioSpokenDuration(durationMs) else stringResource(R.string.social_status_audio_duration_unavailable)
    val sliderDescription = stringResource(R.string.social_status_audio_seek_description, currentSpokenTime, totalSpokenTime)
    val sliderLabel = stringResource(R.string.social_status_audio_position)
    val loadingLabel = stringResource(R.string.social_status_audio_loading)
    Box(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {

//Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
                com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.waveform, Icons.Rounded.GraphicEq, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                name,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(10.dp))
            if ((focusUnavailable || outputDisconnected) && !failed) Text(
                stringResource(if (outputDisconnected) R.string.social_status_audio_output_disconnected else R.string.social_status_audio_interrupted),
                color = colors.foregroundSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            )
            if (failed) {
                Text(
                    stringResource(R.string.social_status_audio_failure),
                    textAlign = TextAlign.Center,
                    color = colors.error,
                    fontSize = 12.sp,
                )
                ChatyTextButton(onClick = ::loadSource, icon = Icons.Rounded.Refresh, label = stringResource(R.string.social_status_audio_retry))
            } else {
                Slider(
                    value = (seekPreview ?: positionMs.toFloat()).coerceIn(0f, (if (durationMs > 0) durationMs else 1).toFloat()),
                    onValueChange = { value ->
                        if (!released && !loading && !failed && durationMs > 0 &&
                            lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                            seekPreview = value.coerceIn(0f, durationMs.toFloat())
                        }
                    },
                    onValueChangeFinished = {
                        val target = seekPreview
                        seekPreview = null
                        if (target != null && !released && !loading && !failed && durationMs > 0 &&
                            lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                            playbackScope.launch {
                                try {
                                    val boundedTarget = target.toLong().coerceIn(0L, durationMs)
                                    val allowed = playbackGuard.runNative {
                                        player.seekTo(boundedTarget.toInt())
                                        positionMs = boundedTarget
                                    }
                                    if (!allowed) { playing = false; focusUnavailable = true; abandonFocus() }
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) { playing = false; failed = true; abandonFocus() }
                            }
                        }
                    },
                    valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                    enabled = !loading && !failed && durationMs > 0,
                    modifier = Modifier.semantics { contentDescription = sliderLabel; stateDescription = sliderDescription },
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(timeLabel(positionMs), color = colors.foregroundSecondary, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (durationMs > 0) timeLabel(durationMs) else stringResource(R.string.social_status_audio_duration_unavailable), color = colors.foregroundSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                ChatyActionButton(onClick = ::togglePlayback, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp).semantics { contentDescription = loadingLabel }, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (playing) stringResource(R.string.social_status_audio_pause) else stringResource(R.string.social_status_audio_play))
                    }
                }
            }
        }
    }
}

@Composable
private fun audioSpokenDuration(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000).coerceAtMost(Int.MAX_VALUE.toLong())
    val minutes = (seconds / 60).toInt()
    val remainder = (seconds % 60).toInt()
    val secondLabel = pluralStringResource(R.plurals.social_status_audio_seconds, remainder, remainder)
    return if (minutes == 0) secondLabel else stringResource(R.string.social_status_audio_minutes_seconds,
        pluralStringResource(R.plurals.social_status_audio_minutes, minutes, minutes), secondLabel)
}

/** Default viewer renderer reports decoded drawable dimensions, including orientation applied by Coil.
 *  The size is UI-only, never persisted and never treated as a media-authorization signal. */
@Composable
internal fun StatusFittedImage(
    url: String,
    modifier: Modifier,
    colors: ChatyColors,
    onSize: (androidx.compose.ui.geometry.Size) -> Unit,
) {
    val painter = coil.compose.rememberAsyncImagePainter(model = url)
    val state = painter.state
    val currentOnSize by androidx.compose.runtime.rememberUpdatedState(onSize)
    LaunchedEffect(url, state) {
        if (state is coil.compose.AsyncImagePainter.State.Success) {
            val width = state.result.drawable.intrinsicWidth
            val height = state.result.drawable.intrinsicHeight
            if (width > 0 && height > 0) currentOnSize(androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat()))
        }
    }
    when (state) {
        is coil.compose.AsyncImagePainter.State.Error -> Box(modifier, contentAlignment = Alignment.Center) {
            val message = stringResource(R.string.social_status_media_failure)
            Text(message, color = colors.foregroundSecondary, modifier = Modifier.semantics { error(message) })
        }
        is coil.compose.AsyncImagePainter.State.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primary)
        }
        else -> androidx.compose.foundation.Image(painter = painter, contentDescription = null,
            modifier = modifier, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
    }
}

// Status audio previous consumer paths retained for source history:
//     val player = remember(url, lifecycleOwner) { MediaPlayer() }
//                 runCatching { if (player.isPlaying) player.pause() }
//         } catch (_: Exception) {
//                 try { player.pause() } catch (_: IllegalStateException) { failed = true }
//                 runCatching { if (player.isPlaying) player.pause() }
//             player.setOnPreparedListener(null)
//             player.setOnCompletionListener(null)
//             player.setOnErrorListener(null)
//             runCatching { player.release() }
//         try {
//             if (playing) {
//                 player.pause()
//             } else {
//                 val granted = requestFocus()
//                 if (!granted) { focusUnavailable = true; return }
//                 focusUnavailable = false
//                 outputDisconnected = false
//                 if (durationMs > 0 && positionMs >= durationMs - 250) {
//                     player.seekTo(0)
//                     positionMs = 0L
//                 }
//                 player.start()
//                 playing = true
//             }
//         } catch (_: Exception) {
//             playing = false
//             abandonFocus()
//             failed = true
//                             try {
//                                 val boundedTarget = target.toLong().coerceIn(0L, durationMs)
//                                 player.seekTo(boundedTarget.toInt())
//                                 positionMs = boundedTarget
//                             } catch (_: IllegalStateException) {
//                                 playing = false
//                                 failed = true
