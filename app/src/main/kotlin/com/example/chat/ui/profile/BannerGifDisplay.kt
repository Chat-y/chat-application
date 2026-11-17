package com.example.chat.ui.profile

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import coil.request.repeatCount
import com.example.chat.data.services.BannerGifPolicy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only mounted under the banner's existing selected/visible/foreground/unmetered/saver policy. */
@Composable
internal fun BannerGifDisplay(url: String, allowed: () -> Boolean, onError: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val latestAllowed = rememberUpdatedState(allowed)
    val latestError = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    val image = remember(url, context) { android.widget.ImageView(context).apply {
        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
    } }
    DisposableEffect(url, image) {
        val loader = coil.ImageLoader.Builder(context.applicationContext)
            .components { add(coil.decode.GifDecoder.Factory(enforceMinimumFrameDelay = true)) }
            .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            .diskCachePolicy(coil.request.CachePolicy.DISABLED).build()
        var loadJob: kotlinx.coroutines.Job? = null
        var request: coil.request.Disposable? = null
        var animation: android.graphics.drawable.Animatable? = null
        var alive = true
        fun stop() {
            // Attempt every cleanup independently; uncertainty propagates to the existing call guard.
            var failure: Throwable? = null
            fun attempt(block: () -> Unit) { try { block() } catch (error: Throwable) { if (failure == null) failure = error } }
            attempt { animation?.stop() }
            attempt { request?.dispose() }
            attempt { loadJob?.cancel() }
            attempt { image.setImageDrawable(null) }
            if (failure != null) throw checkNotNull(failure)
            animation = null; request = null; loadJob = null
        }
        val guard = com.example.chat.data.services.BannerGifCallGuard(
            com.example.chat.ServiceLocator.communicationMediaArbiter,
            stop = { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { stop() } },
            allowed = { alive && latestAllowed.value() },
        )
        loadJob = scope.launch {
            try {
                // Register BEFORE download. A call can now cancel the actual network request.
                if (!guard.start { check(latestAllowed.value()) }) { latestError.value(); return@launch }
                val bytes = fetchGif(url)
                if (!alive || !latestAllowed.value()) return@launch
                val model = coil.request.ImageRequest.Builder(context).data(bytes)
                    .size(BannerGifPolicy.MAX_SIDE).allowHardware(false).repeatCount(-1)
                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED).diskCachePolicy(coil.request.CachePolicy.DISABLED)
                    .target(onSuccess = { result ->
                        // Custom target does NOT auto-start animation. Every start crosses the call guard.
                        val anim = result as? android.graphics.drawable.Animatable
                        anim?.stop()
                        scope.launch {
                            try {
                                if (!guard.start {
                                    check(alive && latestAllowed.value())
                                    image.setImageDrawable(result)
                                    animation = anim
                                    anim?.start()
                                }) { anim?.stop(); latestError.value() }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { anim?.stop(); throw cancelled }
                            catch (_: Exception) { anim?.stop(); latestError.value() }
                        }
                    }, onError = { latestError.value() }).build()
                if (!guard.start { check(alive && latestAllowed.value()); request = loader.enqueue(model) }) latestError.value()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { latestError.value() }
        }
        onDispose {
            alive = false
            guard.close()
            // Synchronous stop prevents a frame running while serialized reservation cleanup waits.
            try { stop() } finally {
                try { loader.shutdown() } finally {
                    scope.launch(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.Main.immediate) { guard.dispose() }
                }
            }
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(factory = { image }, modifier = Modifier.fillMaxSize(),
        onRelease = { it.setImageDrawable(null) })
}

/** Bound the response before a decoder sees it. Cancellation closes the actual network call. */
private suspend fun fetchGif(url: String): ByteArray = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    val request = okhttp3.Request.Builder().url(url).get().build()
    val call = com.example.chat.data.api.ChatyOkHttp.shared.newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            try {
                val bytes = response.use {
                    check(it.isSuccessful) { "GIF unavailable." }
                    val body = checkNotNull(it.body)
                    require(body.contentLength() <= BannerGifPolicy.MAX_BYTES) { "GIF banner exceeds size limit." }
                    body.byteStream().use { input -> java.io.ByteArrayOutputStream().use { out ->
                        val buffer = ByteArray(16 * 1024); var total = 0
                        while (true) {
                            check(continuation.isActive)
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= BannerGifPolicy.MAX_BYTES) { "GIF banner exceeds size limit." }
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    } }
                }
                BannerGifPolicy.inspect(bytes)
                if (continuation.isActive) continuation.resume(bytes)
            } catch (failure: Exception) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
    })
}
