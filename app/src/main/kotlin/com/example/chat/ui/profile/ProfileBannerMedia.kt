package com.example.chat.ui.profile

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.chat.data.services.ProfileBannerVideoLimits as Limits
import com.example.chat.core.audio.CommunicationPlaybackGuard
import kotlinx.coroutines.launch

/** False unless the route/pager host explicitly owns the visible selected page. */
internal val LocalBannerPlaybackSelected = staticCompositionLocalOf { false }

@Composable
internal fun ProfileBannerSelection(selected: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBannerPlaybackSelected provides selected, content = content)
}

/** Requires an extension-preserving .mp4 URL until the backend supplies media-type metadata. */
internal fun isBannerVideo(url: String?): Boolean = runCatching {
    java.net.URI(url?.trim().orEmpty()).path.orEmpty().endsWith(".mp4", ignoreCase = true)
}.getOrDefault(false)

private class BannerTicket {
    var native: ExoPlayer? = null
    var guard: CommunicationPlaybackGuard? = null
    var closing by mutableStateOf(false)
    var cleanupRunning = false
    var blocked by mutableStateOf(false)
}

/** Owns failed cleanup after its composition leaves. No unowned failure and no second decoder. */
private object BannerPlaybackSlot {
    var holder: BannerTicket? by mutableStateOf(null)
    private val cleanupScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    fun close(ticket: BannerTicket) {
        ticket.closing = true
        ticket.guard?.markClosing()
        if (ticket.cleanupRunning) return
        ticket.cleanupRunning = true
        cleanupScope.launch {
            try {
                val guard = ticket.guard
                if (guard != null) guard.dispose() else ticket.native?.release()
                ticket.native = null
                ticket.guard = null
                ticket.blocked = false
                if (holder === ticket) holder = null
            } catch (_: Exception) {
                // Retain the exact native owner and its guard. Any later video banner offers Retry.
                ticket.blocked = true
            } finally { ticket.cleanupRunning = false }
        }
    }
    fun retry() { holder?.takeIf { it.blocked }?.let(::close) }
}

/** Root installs this once. Retry is bounded to root entry and actual resume, never a polling loop. */
@Composable
internal fun BannerPlaybackRecovery() {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) BannerPlaybackSlot.retry()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        BannerPlaybackSlot.retry()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
internal fun ProfileBannerMedia(url: String?, contentDescription: String?, modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null, error: (@Composable () -> Unit)? = null,
    selected: Boolean = LocalBannerPlaybackSelected.current) {
    val gif = remember(url) { runCatching {
        java.net.URI(url?.trim().orEmpty()).path.orEmpty().endsWith(".gif", true)
    }.getOrDefault(false) }
    // Previous: still-image renderer for every non-MP4 URL. GIF now has a bounded animation branch.
    if (!isBannerVideo(url) && !gif) {
        ProfileSizedImage(url, contentDescription, modifier, placeholder = placeholder, error = error)
        return
    }
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    val context = LocalContext.current
    val view = LocalView.current
    val owner = com.example.chat.ServiceLocator.apiBackend.authSnapshot()
    val base = com.example.chat.ServiceLocator.apiBackend.config.restApiBaseUrl.trimEnd('/').replace(Regex("/api/v[0-9]+$"), "")
    val resolved = remember(url, base) { com.example.chat.data.remote.ProfileMediaUrl.resolve(url, base) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cm = remember(context) { context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager }
    val power = remember(context) { context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager }
    val network by com.example.chat.ServiceLocator.networkMonitor.state.collectAsState()
    var resumed by remember(lifecycleOwner) { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var windowFocused by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    var saver by remember { mutableStateOf(true) }
    var visible by remember(url) { mutableStateOf(false) }
    var failed by remember(url, owner) { mutableStateOf(false) }
    val ticket = remember(url, owner, lifecycleOwner) { BannerTicket() }
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion() || com.example.chat.ui.theme.LocalThemeConfig.current.animationLevel <= 0.0
    fun saverNow() = cm.restrictBackgroundStatus == android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED ||
        power.isPowerSaveMode || com.example.chat.ServiceLocator.mediaAutoDownloadGate.rules.dataSaverEnabled
    // Policy loss fences queued guarded starts before requesting recomposition/cleanup.
    fun refreshPolicy() {
        val nowSaver = saverNow()
        val nowResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val nowFocus = view.hasWindowFocus()
        if (nowSaver || !nowResumed || !nowFocus) {
            ticket.closing = true
            ticket.guard?.markClosing()
            if (BannerPlaybackSlot.holder === ticket) BannerPlaybackSlot.close(ticket)
        }
        windowFocused = nowFocus
        saver = nowSaver
        resumed = nowResumed
    }
    DisposableEffect(context, lifecycleOwner, ticket) {
        // Each successful registration immediately adds its inverse. Failure unwinds only what landed.
        val undo = java.util.ArrayDeque<() -> Unit>()
        fun cleanup() {
            while (!undo.isEmpty()) runCatching { undo.removeLast().invoke() }
            // Independent attempts: a failed Android receiver removal cannot skip preferences cleanup.
        }
        try {
            val tree = view.viewTreeObserver
            val focusObserver = android.view.ViewTreeObserver.OnWindowFocusChangeListener { refreshPolicy() }
            tree.addOnWindowFocusChangeListener(focusObserver)
            undo.addLast { if (tree.isAlive) tree.removeOnWindowFocusChangeListener(focusObserver) }
            val observer = LifecycleEventObserver { _, _ -> refreshPolicy() }
            lifecycleOwner.lifecycle.addObserver(observer)
            undo.addLast { lifecycleOwner.lifecycle.removeObserver(observer) }
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: android.content.Intent?) { refreshPolicy() }
            }
            val filter = android.content.IntentFilter().apply {
                addAction(android.net.ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
                addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
            undo.addLast { context.unregisterReceiver(receiver) }
            val prefs = context.getSharedPreferences("chaty_media_rules", android.content.Context.MODE_PRIVATE)
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                android.os.Handler(android.os.Looper.getMainLooper()).post { refreshPolicy() }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            undo.addLast { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            refreshPolicy()
        } catch (_: Exception) {
            failed = true
            ticket.closing = true
            ticket.guard?.markClosing()
            if (BannerPlaybackSlot.holder === ticket) BannerPlaybackSlot.close(ticket)
            cleanup()
        }
        onDispose { cleanup() }
    }
    val eligible = selected && windowFocused && resolved != null && resumed && visible && !saver && !reduced && network.online && !network.metered && !failed
    val latestEligible = rememberUpdatedState(eligible)
    fun allowedNow(): Boolean {
        val live = com.example.chat.ServiceLocator.networkMonitor.state.value
        return latestEligible.value && view.hasWindowFocus() && !ticket.closing && !saverNow() && live.online && !live.metered &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            com.example.chat.ServiceLocator.apiBackend.authSnapshot() === owner && BannerPlaybackSlot.holder === ticket &&
            !com.example.chat.ui.kit.ChatyMotion.disableAnimations(context)
    }
    DisposableEffect(ticket) { onDispose {
        if (BannerPlaybackSlot.holder === ticket) BannerPlaybackSlot.close(ticket)
    } }
    LaunchedEffect(eligible, BannerPlaybackSlot.holder) {
        if (eligible && BannerPlaybackSlot.holder == null) {
            ticket.closing = false
            BannerPlaybackSlot.holder = ticket
        }
        if (!eligible && BannerPlaybackSlot.holder === ticket) BannerPlaybackSlot.close(ticket)
    }
    val ownsSlot = eligible && BannerPlaybackSlot.holder === ticket && !ticket.blocked && !ticket.closing
    var renderedPlayer by remember(ticket) { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(ownsSlot, resolved, ticket, gif) {
        if (ownsSlot && !gif) {
            try {
                // Allocation/configuration is transactional. Slot owns the native immediately after creation.
                check(allowedNow())
                val native = ExoPlayer.Builder(context).build()
                ticket.native = native
                native.volume = 0f
                native.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false)
                native.trackSelectionParameters = native.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                native.repeatMode = Player.REPEAT_MODE_ONE
                native.setMediaItem(MediaItem.Builder().setUri(checkNotNull(resolved))
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(Limits.MAX_DURATION_MS).build()).build())
                ticket.guard = CommunicationPlaybackGuard(
                    com.example.chat.ServiceLocator.communicationMediaArbiter,
                    pauseNative = { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { native.pause() } },
                    disposeNative = { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { native.release() } },
                    isOwnerCurrent = ::allowedNow,
                    onCallSuspended = { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { failed = true } },
                )
                renderedPlayer = native
            } catch (_: Exception) {
                failed = true
                BannerPlaybackSlot.close(ticket)
            }
        }
        onDispose {
            if (ownsSlot && !gif) {
                renderedPlayer = null
                BannerPlaybackSlot.close(ticket)
            }
        }
    }
    val native = renderedPlayer
    LaunchedEffect(native, ticket) {
        if (native != null) try {
            // Recheck inside the arbiter's serialized operation, not only during composition.
            if (ticket.guard?.runNative { check(allowedNow()); native.prepare(); check(allowedNow()); native.play() } != true) failed = true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Box(modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val nowVisible = coordinates.isAttached && bounds.width > 0 && bounds.height > 0 &&
            bounds.bottom > 0 && bounds.top < view.height && bounds.right > 0 && bounds.left < view.width
        if (!nowVisible && BannerPlaybackSlot.holder === ticket) BannerPlaybackSlot.close(ticket)
        visible = nowVisible
    }, contentAlignment = Alignment.Center) {
        val poster = remember(url, owner) { BannerVideoPosters.get(owner, url.orEmpty()) }
        if (poster != null) {
            val request = remember(poster, context) { coil.request.ImageRequest.Builder(context).data(poster).size(Limits.POSTER_WIDTH, Limits.POSTER_HEIGHT).build() }
            androidx.compose.foundation.Image(coil.compose.rememberAsyncImagePainter(request), contentDescription,
                Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else (placeholder ?: error)?.invoke()
        if (gif && ownsSlot) {
            BannerGifDisplay(checkNotNull(resolved), allowed = ::allowedNow, onError = { failed = true })
        }
        if (native != null && ownsSlot) {
            DisposableEffect(native) {
                val listener = object : Player.Listener {
                    override fun onPlayerError(e: androidx.media3.common.PlaybackException) { failed = true }
                }
                native.addListener(listener)
                onDispose { native.removeListener(listener) }
            }
            AndroidView(factory = { PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(colors.surface.toArgb())
                setBackgroundColor(colors.surface.toArgb())
                player = native
            } }, modifier = Modifier.matchParentSize(), onRelease = { it.player = null }, update = { it.player = native })
        }
        if (BannerPlaybackSlot.holder?.blocked == true) {
            com.example.chat.ui.kit.ChatyTextButton(onClick = { BannerPlaybackSlot.retry() }, label = "Retry banner cleanup")
        }
    }
}
