package com.example.chat.ui.chats.detail

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** A bounded decode plus viewport transform. Zoom never changes the exported media. */
@Composable
// MR-R3-010 previous signature: internal fun MediaReviewImage(source: String, description: String, modifier: Modifier = Modifier) {
// MR-R1-036 previous signature: internal fun MediaReviewImage(source: String, description: String, modifier: Modifier = Modifier, itemActions: (@Composable () -> Unit)? = null) {
internal fun MediaReviewImage(source: String, description: String, modifier: Modifier = Modifier, itemActions: (@Composable () -> Unit)? = null, fullSizePreview: Boolean = true) {
    var scale by remember(source) { mutableFloatStateOf(1f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    // MR-R3-007: source-keyed synchronously, so the old item's ready state cannot hide feedback.
    val previewFeedback = remember(source) { mutableStateOf("loading") }
    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            fun clamp(value: Offset, zoom: Float) = Offset(
                value.x.coerceIn((1f - zoom) * width, 0f),
                value.y.coerceIn((1f - zoom) * height, 0f))
            fun zoomTo(next: Float, center: Offset = Offset(width / 2, height / 2)) {
                val newScale = next.coerceIn(1f, 5f)
                offset = clamp(center - (center - offset) * (newScale / scale), newScale)
                scale = newScale
            }
            LaunchedEffect(width, height) { offset = clamp(offset, scale) }
            // MR-R4-043 prior: if (previewFeedback.value == "loading") ReviewPreviewLoadingFrame("Loading photo")
            if (previewFeedback.value == "loading") ReviewPreviewLoadingFrame(stringResource(com.example.chat.R.string.media_released_review_photo_loading))
            if (previewFeedback.value == "error") Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // MR-R4-043 prior: Text("Could not load this photo", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                Text(stringResource(com.example.chat.R.string.media_released_review_photo_error), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            val decodeSize = reviewPreviewDecodeSize(fullSizePreview, width.toInt(), height.toInt())
            val zoomState = stringResource(com.example.chat.R.string.media_released_review_zoom_state, "%.1f".format(scale))
            AsyncImage(
                onLoading = { previewFeedback.value = "loading" },
                onSuccess = { previewFeedback.value = "ready" },
                onError = { previewFeedback.value = "error" },
                // MR-R4-030: decode bounded by the actual preview viewport (still capped at
                // 2048 px) instead of one blanket size for every screen density.
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(source)
                    // MR-R1-036 old size: .size(width.toInt().coerceIn(1, 2048), height.toInt().coerceIn(1, 2048)).build(),
                    .size(decodeSize.first, decodeSize.second).build(),
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(source, width, height) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            zoomTo(scale * zoom, centroid)
                            offset = clamp(offset + pan, scale)
                        }
                    }
                    .pointerInput(source, width, height) {
                        detectTapGestures(onDoubleTap = { point -> zoomTo(if (scale > 1f) 1f else 2.5f, point) })
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y; transformOrigin = TransformOrigin(0f, 0f) }
                    // MR-R4-043 prior: .semantics { stateDescription = "Zoom ${"%.1f".format(scale)} times" },
                    .semantics { stateDescription = zoomState },
            )
            // MR-R3-010: measure action + zoom footer together, never guess an image inset.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            itemActions?.let { content -> Box(Modifier.padding(horizontal = 12.dp)) { content() } }
            // Previous zoom footer: Row(Modifier.align(Alignment.BottomCenter), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // MR-R4-043 prior: FilledTonalButton(onClick = { zoomTo(scale - 0.5f) }, enabled = scale > 1f, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Zoom out") }
                FilledTonalButton(onClick = { zoomTo(scale - 0.5f) }, enabled = scale > 1f, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(com.example.chat.R.string.media_released_review_zoom_out)) }
                // MR-R4-043 prior: TextButton(onClick = { scale = 1f; offset = Offset.Zero }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Reset zoom") }
                TextButton(onClick = { scale = 1f; offset = Offset.Zero }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(com.example.chat.R.string.media_released_review_zoom_reset)) }
                // MR-R4-043 prior: FilledTonalButton(onClick = { zoomTo(scale + 0.5f) }, enabled = scale < 5f, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Zoom in") }
                FilledTonalButton(onClick = { zoomTo(scale + 0.5f) }, enabled = scale < 5f, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(com.example.chat.R.string.media_released_review_zoom_in)) }
            }
            } // Measured preview footer.
        }
    }
}
