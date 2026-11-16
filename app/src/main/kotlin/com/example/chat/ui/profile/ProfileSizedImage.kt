package com.example.chat.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/** Do not request an original-size remote bitmap before layout establishes a bounded target.
 * The target is presentation-only. URL identity/cache keys remain Coil's responsibility. */
@Composable
internal fun ProfileSizedImage(
    url: String?, contentDescription: String?, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null,
) {
    var pixels by remember(url) { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { pixels = it }, contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (pixels.width > 0 && pixels.height > 0) {
            com.example.chat.ui.theme.ChatyAsyncImage(
                url = url, contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(), contentScale = contentScale,
                placeholder = placeholder, error = error,
                requestSize = coil.size.Size(pixels.width, pixels.height),
                // The shared loader gates this opt-in on app and current Android animation settings.
                crossfadeMillis = 120,
            )
        } else placeholder?.invoke()
    }
}
