package com.example.chat.ui.core.components.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.auth.AuthFormPolicy

@Composable
fun EmailSuggestion(value: String, enabled: Boolean, theme: ThemeConfig, onUse: (String) -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val suggested = remember(value) { AuthFormPolicy.emailSuggestion(value) }
    if (suggested != null) ChatyAuthLink(com.example.chat.core.i18n.authFormatted("Use %1\$s?",suggested), theme, { onUse(suggested) }, enabled)
}

@Composable
fun AuthErrorNotice(message: String?, theme: ThemeConfig, enteredIdentifier: String = "") {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    if (message.isNullOrBlank()) return
    val prefix = when (AuthFormPolicy.errorKind(message)) {
        AuthFormPolicy.ErrorKind.Connection -> authCopy("Connection problem. Your form is kept. ")
        AuthFormPolicy.ErrorKind.Server -> authCopy("Server unavailable. Your form is kept. ")
        AuthFormPolicy.ErrorKind.RateLimit -> authCopy("Please wait before trying again. ")
        AuthFormPolicy.ErrorKind.Other -> ""
    }
    Row(Modifier.padding(vertical = ChatyUiTokens.space8).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.errorOutline,
            color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor), size = ChatyUiTokens.icon)
        Spacer(Modifier.width(ChatyUiTokens.space8))
        Text(authCopy(prefix) + authCopy(AuthFormPolicy.privateError(message, enteredIdentifier)), color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor))
    }
}

@Composable
fun CheckEmailActions(theme: ThemeConfig, onWrongAddress: () -> Unit, enabled: Boolean) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val context = LocalContext.current
    var unavailable by remember { mutableStateOf(false) }
    Text(authCopy("Check your email for the code. Enter it below to continue."), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
    ChatyAuthLink(authCopy("Open mail app"), theme, {
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_APP_EMAIL))
        } catch (_: Exception) { unavailable = true }
    }, enabled)
    if (unavailable) Text(authCopy("No mail app is available. Open your email in your usual app or browser."), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
    ChatyAuthLink(authCopy("Wrong email address?"), theme, onWrongAddress, enabled)
}

/** Process lifecycle remasks visible passwords without retaining their content or changing auth state. */
@Composable
fun RemaskPasswordOnBackground(onRemask: () -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val latest by rememberUpdatedState(onRemask)
    DisposableEffect(Unit) {
        val lifecycle = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) latest()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** Confirmation stays in the current screen; no form text is persisted or included in the dialog. */
@Composable
fun rememberAuthBackAction(hasDraft: Boolean, onBack: () -> Unit): () -> Unit {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    var confirming by remember { mutableStateOf(false) }
    val latestBack by rememberUpdatedState(onBack)
    androidx.activity.compose.PredictiveBackHandler(enabled = hasDraft) {progress->
        progress.collect { }
        // A cancelled gesture throws before here: it never discards or opens the dialog.
        confirming = true
    }
    if (confirming && hasDraft) AlertDialog(onDismissRequest = { confirming = false },
        title = { Text(authCopy("Leave this form?")) },
        text = { Text(authCopy("Unsaved input on this screen will be discarded.")) },
        confirmButton = { TextButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = { confirming = false; latestBack() }) { Text(authCopy("Leave")) } },
        dismissButton = { TextButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = { confirming = false }) { Text(authCopy("Keep editing")) } })
    return { if (hasDraft) confirming = true else latestBack() }
}

/** Passive OS connectivity observation only. Never retries an auth request or probes a server. */
@Composable
fun rememberAuthOnline(): Boolean {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val context = LocalContext.current.applicationContext
    val connectivity = remember(context) {
        context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    }
    fun connected(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    var online by remember(connectivity) { mutableStateOf(connected()) }
    DisposableEffect(connectivity) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var disposed = false
        val update = Runnable { if (!disposed) online = connected() }
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { handler.post(update) }
            override fun onLost(network: android.net.Network) { handler.post(update) }
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) { handler.post(update) }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        handler.post(update)
        onDispose {
            disposed = true
            handler.removeCallbacks(update)
            connectivity.unregisterNetworkCallback(callback)
        }
    }
    return online
}

@Composable
fun AuthOfflineNotice(online: Boolean, theme: ThemeConfig) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    if (!online) Text(authCopy("You're offline. Your form is kept. Reconnect to continue."),
        color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
        modifier = Modifier.padding(vertical = ChatyUiTokens.space8).semantics { liveRegion = LiveRegionMode.Polite })
}

/** Covered pre-auth layers stay composed to retain drafts. Their requests must not stay active. */
@Composable
fun rememberAuthRequestScope(active: Boolean): kotlinx.coroutines.CoroutineScope {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val parent = rememberCoroutineScope()
    val scope = remember(active, parent) {
        kotlinx.coroutines.CoroutineScope(parent.coroutineContext + kotlinx.coroutines.SupervisorJob(parent.coroutineContext[kotlinx.coroutines.Job]))
    }
    DisposableEffect(scope, active) {
        if (!active) scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        onDispose { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    }
    return scope
}


@Composable
fun AuthLookupProgress(visible: Boolean, theme: ThemeConfig) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    if (!visible) return
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
    Column(Modifier.fillMaxWidth().padding(vertical = ChatyUiTokens.space8)) {
        Text(authCopy("Checking workspace..."), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (reduced) LinearProgressIndicator(progress = { .5f }, modifier = Modifier.fillMaxWidth())
        else {
            val transition=androidx.compose.animation.core.rememberInfiniteTransition(label=authCopy("auth-lookup"))
            val progress by transition.animateFloat(initialValue=0f,targetValue=1f,
                animationSpec=androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100),androidx.compose.animation.core.RepeatMode.Restart),label=authCopy("auth-lookup-sweep"))
            Box(Modifier.fillMaxWidth().height(8.dp).background(Color(theme.surfaceColor))) {
                Box(Modifier.fillMaxWidth(.3f).fillMaxHeight().offset(x=(progress*220).dp)
                    .background(Color(theme.secondaryTextColor).copy(alpha=.18f),ChatyUiTokens.cardShape))
            }
        }
    }
}


@Composable
fun rememberAuthRetryWait(deadline: Long): Int {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    var left by remember(deadline) { mutableIntStateOf(0) }
    LaunchedEffect(deadline) {
        do {
            left = ((deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 999L).div(1000).toInt()
            if (left > 0) kotlinx.coroutines.delay(250)
        } while (left > 0)
    }
    return left
}

@Composable
fun AuthRetryNotice(seconds: Int, theme: ThemeConfig) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    if (seconds > 0) Text(com.example.chat.core.i18n.authFormatted("Try again in %1\$ds.",seconds), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
}

@Composable
fun AuthValidationSummary(errors: List<String?>, theme: ThemeConfig) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val messages = errors.filterNotNull().filter { it.isNotBlank() }.distinct()
    if (messages.isEmpty()) return
    Surface(color = Color(theme.surfaceColor), shape = ChatyUiTokens.cardShape,
        modifier = Modifier.fillMaxWidth().padding(vertical = ChatyUiTokens.space8)) {
        Column(Modifier.padding(ChatyUiTokens.space16).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(com.example.chat.core.i18n.authFormatted("Check %1\$d fields to continue.",messages.size), color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor))
            messages.forEach { Text(authCopy(it), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor)) }
        }
    }
}
