package com.example.chat.ui.chats.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.unit.dp
import com.example.chat.domain.models.ChatMessage

@Composable
internal fun CompactAlbumPreview(
    messages: List<ChatMessage>, binding: AttachmentConsumerBinding?, remoteImage: RemoteImageSlot?,
    resolveUrl: (suspend (String) -> String)?,
    onOpen: (ChatMessage, Rect?) -> Unit, onMenu: (ChatMessage, Rect?) -> Unit,
    onReply: (ChatMessage) -> Unit = {}, onReact: ((ChatMessage) -> Unit)? = null,
) {
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnMenu by rememberUpdatedState(onMenu)
    val currentOnReply by rememberUpdatedState(onReply)
    val currentOnReact by rememberUpdatedState(onReact)
    // Cap visible tiles at 4 (2x2); larger albums keep every message in the row for
    // the viewer and show a +N scrim on the last visible tile.
    val visible = AlbumNavigationPolicy.preview(messages)
    val hiddenCount = AlbumNavigationPolicy.remainingCount(messages)
    Column(Modifier.widthIn(max = 300.dp).fillMaxWidth().clip(RoundedCornerShape(9.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var start = 0
        for (columns in mediaAlbumColumns(visible.size)) {
            val members = visible.subList(start, start + columns)
            start += columns
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (message in members) key(message.id) {
                    var bounds by remember { mutableStateOf<Rect?>(null) }
                    val currentMessage by rememberUpdatedState(message)
                    val isCappedTail = hiddenCount > 0 && message.id == visible.last().id
                    Box(Modifier.weight(1f).aspectRatio(if (columns == 1) 2f else 1f)
                        .onGloballyPositioned { bounds = it.boundsInWindow() }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Album photo ${messages.indexOf(message) + 1} of ${messages.size}"
                            onClick("Open photo") { currentOnOpen(currentMessage, bounds); true }
                            onLongClick("Photo options") { currentOnMenu(currentMessage, bounds); true }
                        }
                        .pointerInput(message.id, onReact != null) { detectTapGestures(
                            onTap = { currentOnOpen(currentMessage, bounds) }, onLongPress = { currentOnMenu(currentMessage, bounds) },
                            onDoubleTap = if (onReact != null) ({ currentOnReact?.invoke(currentMessage); Unit }) else null) }
                        .pointerInput(message.id) {
                            var distance = 0f
                            detectHorizontalDragGestures(onDragStart = { distance = 0f },
                                onDragEnd = { if (kotlin.math.abs(distance) >= 56.dp.toPx()) currentOnReply(currentMessage) },
                                onHorizontalDrag = { change, amount -> change.consume(); distance += amount })
                        }) {
                        message.attachment?.let { attachment ->
                            MediaAttachmentPreview(attachment, remoteImage, resolveUrl, null, false,
                                message, binding, fixedRatio = if (columns == 1) 2f else 1f)
                        }
                        if (isCappedTail) {
                            Box(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                                contentAlignment = androidx.compose.ui.Alignment.TopEnd) {
                                // Keep the count away from centred loading/error content.
                                androidx.compose.material3.Text("+$hiddenCount",
                                    modifier = Modifier.padding(6.dp)
                                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Previous source retained for source-only media polish:
//                             Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x66000000)),

// Previous source retained for source-only media polish:
//                         .semantics { contentDescription = "Album photo ${messages.indexOf(message) + 1} of ${messages.size}" }
//                         .pointerInput(message.id) { detectTapGestures(
//                             onTap = { onOpen(message, bounds) }, onLongPress = { onMenu(message, bounds) },
//                             onDoubleTap = onReact?.let { callback -> { callback(message) } }) }
//                                 onDragEnd = { if (kotlin.math.abs(distance) >= 56.dp.toPx()) onReply(message) },
