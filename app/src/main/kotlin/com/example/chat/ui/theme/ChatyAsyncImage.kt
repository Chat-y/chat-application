package com.example.chat.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter

/** Shared remote-image loader for the port (Coil). All screens load avatar /
 *  media URLs through this wrapper so caching and placeholders stay uniform;
 *  [placeholder]/[error] are simple color boxes supplied by the caller until
 *  the media slice ports the original placeholder assets. */
@Composable
fun ChatyAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null,
    // Settings profile surfaces know their target decode size; other callers keep existing behavior.
    requestSize: coil.size.Size? = null,
    // Opt-in only. Disabled by both app reduced motion and Android Remove animations.
    crossfadeMillis: Int = 0,
    // Profile-only opt-in. Other media/local-file consumers keep their existing model semantics.
    profileMediaBase: String? = null,
) {
    if (url.isNullOrBlank()) {
        error?.invoke() ?: placeholder?.invoke()
        return
    }
    val resolvedUrl = androidx.compose.runtime.remember(url, profileMediaBase) {
        // Local drafts remain local; only remote profile references use HTTP validation.
        val localScheme = runCatching { java.net.URI(url.trim()).scheme?.lowercase(java.util.Locale.ROOT) }.getOrNull()
        if (profileMediaBase == null) url
        else if (localScheme == "file" || localScheme == "content") url.trim()
        else com.example.chat.data.remote.ProfileMediaUrl.resolve(url, profileMediaBase)
    }
    androidx.compose.runtime.LaunchedEffect(url, profileMediaBase) {
        if (profileMediaBase != null && com.example.chat.BuildConfig.DEBUG) {
            // DEBUG TEMPORARY: enum-like form/validity only, never URL, query, path or auth.
            android.util.Log.d("ProfileMediaImage", "image-url-form=${com.example.chat.data.remote.ProfileMediaUrl.form(url)} usable=${resolvedUrl != null}")
        }
    }
    if (resolvedUrl.isNullOrBlank()) {
        error?.invoke() ?: placeholder?.invoke()
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Previous full-resolution request: rememberAsyncImagePainter(model = url)
    val wantsFade = crossfadeMillis > 0 && LocalThemeConfig.current.animationLevel > 0.0
    val mayAnimate = if (wantsFade) com.example.chat.ui.kit.rememberSystemAnimationsAllowed() else false
    val fadeMillis = if (mayAnimate) crossfadeMillis.coerceAtLeast(0) else 0
    val model = androidx.compose.runtime.remember(resolvedUrl, requestSize, fadeMillis, context) {
        // Previous model: if (requestSize == null && fadeMillis == 0) url else ImageRequest.Builder(context).data(url)
        if (requestSize == null && fadeMillis == 0) resolvedUrl else coil.request.ImageRequest.Builder(context)
            .data(resolvedUrl)
            .apply { if (requestSize != null) size(requestSize) }
            .crossfade(fadeMillis)
            .build()
    }
    // Previous size-only request retained inactive:
    // val model = androidx.compose.runtime.remember(url, requestSize, context) {
    // if (requestSize == null) url else coil.request.ImageRequest.Builder(context)
    // .data(url).size(requestSize).build()
    // }
    val painter = rememberAsyncImagePainter(model = model)
    val imageState = painter.state
    androidx.compose.runtime.LaunchedEffect(imageState, resolvedUrl, profileMediaBase) {
        if (profileMediaBase != null && com.example.chat.BuildConfig.DEBUG && imageState is AsyncImagePainter.State.Error) {
            // DEBUG TEMPORARY: exception type only. Throwable messages can contain signed URLs.
            val failure = imageState.result.throwable
            val status = (failure as? coil.network.HttpException)?.response?.code
            android.util.Log.d("ProfileMediaImage", "image-load-failed type=${failure.javaClass.simpleName} http=${status ?: -1}")
        }
    }
    // Previous: when (painter.state)
    when (imageState) {
        is AsyncImagePainter.State.Loading -> placeholder?.invoke()
        is AsyncImagePainter.State.Error -> (error ?: placeholder)?.invoke()
        else -> Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
