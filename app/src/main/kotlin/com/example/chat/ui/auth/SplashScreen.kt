package com.example.chat.ui.auth

import com.example.chat.ui.core.components.auth.ChatyUiTokens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chat.ui.theme.LocalThemeConfig

/** Port of lib/features/auth/splash_screen.dart: instant (~120ms) startup
 *  handoff with a light fade-in. Never blocks the first frame on heavy work.
 *  The routing decision (authenticated?) is injected by AppRoot, which owns
 *  the backend/session seams. */
@Composable
fun SplashScreen(routeToHome: suspend () -> Boolean, onRoute: (Boolean) -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val theme = LocalThemeConfig.current
    var started by remember { mutableStateOf(false) }
    var retryAttempt by remember { mutableStateOf(0) }
    var storageError by remember { mutableStateOf(false) }
    val fade by animateFloatAsState(
        targetValue = if (started) 1.0f else 0.8f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = authCopy("splashFade"),
    )

    LaunchedEffect(retryAttempt) {
        started = true
        storageError = false
        // Previously a failure/timeout routed signed out. Preserve the stores
        // and let the owner retry; never turn a storage failure into logout.
        // val route = try { withTimeoutOrNull(20_000L) { routeToHome() } ?: false }
        val route = try {
            kotlinx.coroutines.withTimeoutOrNull(20_000L) { routeToHome() }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // false // Failure is not evidence of a signed-out session.
            null
        }
        if (route == null) storageError = true else onRoute(route)
    }

    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (storageError) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                ) {
                    androidx.compose.material3.Text(
                        text = androidx.compose.ui.res.stringResource(com.example.chat.R.string.startup_secure_storage_error),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    )
                    androidx.compose.material3.Button(onClick = { storageError = false; retryAttempt++ }) {
                        androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.startup_retry))
                    }
                }
            } else Box(
                modifier = Modifier
                    .alpha(fade)
                    .size(80.dp)
                    .clip(ChatyUiTokens.cardShape),
                contentAlignment = Alignment.Center,
            ) {
                // ChatyBrandIcon placeholder: the brand-asset slice swaps in the
                // real icon; shape/scale/radius already match the Dart layout.

//Icon(
//                    Icons.Rounded.Forum,
//                    contentDescription = "Chaty",
//                    tint = Color(theme.accentColor),
//                    modifier = Modifier.size(64.dp),
//                )
                com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.chats, Icons.Rounded.Forum,
                    contentDescription = "Chaty",
                    tint = Color(theme.accentColor),
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}
