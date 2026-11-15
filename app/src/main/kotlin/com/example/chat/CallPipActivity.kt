package com.example.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.chat.data.api.ApiAccountScope
import com.example.chat.data.services.AudioRouteType
import com.example.chat.data.services.CallVideoSurfaceProvider
import com.example.chat.ui.core.components.calls.CallOnlySurface
import kotlinx.coroutines.launch

/** A distinct opaque task. MainActivity and its entire navigation composition stay untouched.
 * This Activity neither creates media nor owns/duplicates the process call foreground service.
 */
class CallPipActivity : ComponentActivity() {
    private var owner: ApiAccountScope? = null
    private var callId: String? = null
    private var revision by mutableIntStateOf(0)
    private var compact by mutableStateOf(false)
    private var pipUnavailable by mutableStateOf<String?>(null)
    private var enterWhenReady = false
    private var surfaceReady = false
    private var preparingPip = false
    private var internalNavigation = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val changed: () -> Unit = { handler.post { revision++; if (!valid()) finishAndRemoveTask() }; Unit }
    private val authChanged: (ApiAccountScope) -> Unit = { changed() }

    private fun valid(): Boolean = com.example.chat.ui.calls.CallPipSessionPolicy.allows(
        owner, ServiceLocator.apiBackend.authSnapshot(), callId, ServiceLocator.callSignaling.currentSession) &&
        ServiceLocator.callSignaling.hasLocalMedia

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Intent identifiers route only. Only the current in-process authorized session supplies identity/media.
        val live = ServiceLocator.callSignaling.currentSession
        val captured = ServiceLocator.apiBackend.authSnapshot()
        val requestedId = savedInstanceState?.getString(STATE_CALL) ?: intent.getStringExtra(EXTRA_CALL)
        val requestedGeneration = savedInstanceState?.getLong(STATE_GENERATION) ?: intent.getLongExtra(EXTRA_GENERATION, -1L)
        if (!com.example.chat.ui.calls.CallPipSessionPolicy.allows(captured, captured, requestedId, live) ||
            !ServiceLocator.callSignaling.hasLocalMedia || captured.generation != requestedGeneration) {
            finishAndRemoveTask(); return
        }
        owner = captured; callId = live?.callId
        compact = ServiceLocator.nativeCallPip.isActive(this)
        enterWhenReady = !compact && intent.getBooleanExtra(EXTRA_ENTER, false)
        ServiceLocator.callSignaling.addStateListener(changed)
        ServiceLocator.apiBackend.addAuthScopeListener(authChanged)
        if (ServiceLocator.preferencesController.security.protectFromScreenshots)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            @Suppress("UNUSED_EXPRESSION") revision
            val session = ServiceLocator.callSignaling.currentSession?.takeIf { valid() }
            val scope = rememberCoroutineScope()
            fun action(block: suspend () -> Unit) {
                scope.launch {
                    if (!valid()) return@launch
                    try { block() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) {
                        android.widget.Toast.makeText(this@CallPipActivity, "Unable to change the call. Return to app to check permissions.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            BackHandler { requestPip() }
            com.example.chat.ui.theme.ChatyTheme(ServiceLocator.themeController.globalTheme) {
                com.example.chat.ui.icons.ProvideChatyIconTheme(ServiceLocator.preferencesController) {
                CallOnlySurface(session, ServiceLocator.callSignaling.callDurationSeconds,
                    if (session != null) ServiceLocator.callSignaling.activeMedia as? CallVideoSurfaceProvider else null,
                    compact, pipUnavailable, ::requestPip, ::openPipSettings, ::returnToApp,
                    onMute = { action { ServiceLocator.callSignaling.toggleMute() } },
                    onSpeaker = { action {
                        val current = ServiceLocator.callSignaling.currentSession ?: return@action
                        ServiceLocator.callSignaling.setAudioRoute(if (current.audioRoute == AudioRouteType.speaker) AudioRouteType.earpiece else AudioRouteType.speaker)
                    } }, onHangup = { action { ServiceLocator.callSignaling.endCall() } },
                    onReady = { surfaceReady = true; if (enterWhenReady) requestPip() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // An old task cannot adopt a different account or call through a later Intent.
        if (!valid() || intent.getStringExtra(EXTRA_CALL) != callId || intent.getLongExtra(EXTRA_GENERATION, -1L) != owner?.generation) {
            finishAndRemoveTask(); return
        }
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ENTER, false)) requestPip()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CALL, callId); owner?.let { outState.putLong(STATE_GENERATION, it.generation) }
        super.onSaveInstanceState(outState)
    }

    private fun requestPip() {
        enterWhenReady = false
        if (!valid()) { finishAndRemoveTask(); return }
        if (ServiceLocator.nativeCallPip.isActive(this)) return
        if (!surfaceReady) { enterWhenReady = true; return }
        if (!ServiceLocator.callPresentationPrefs.value.pictureInPictureEnabled) {
            pipUnavailable = "Picture-in-picture is disabled in Call settings. The call continues."
            return
        }
        if (preparingPip) return
        preparingPip = true
        compact = true
        // Remove expanded controls and wait for the call-only compact frame before OS capture.
        window.decorView.postOnAnimation {
            window.decorView.postOnAnimation {
                preparingPip = false
                if (isFinishing || isDestroyed || !valid()) return@postOnAnimation
                val video = ServiceLocator.callSignaling.currentSession?.isVideo == true
                if (!ServiceLocator.nativeCallPip.enter(this, if (video) 9 else 1, if (video) 16 else 1)) {
                    compact = false
                    pipUnavailable = "Floating call is unavailable. If picture-in-picture is blocked, allow it in Android settings. The call continues."
                    if (intent.getBooleanExtra(EXTRA_ENTER, false)) {
                        android.widget.Toast.makeText(this, "Call continues in the background. Open the call notification to allow picture-in-picture.", android.widget.Toast.LENGTH_LONG).show()
                        moveTaskToBack(true)
                    }
                } else pipUnavailable = null
            }
        }
    }

    private fun openPipSettings() {
        if (Build.VERSION.SDK_INT < 26) return
        try { internalNavigation = true; startActivity(Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", Uri.parse("package:$packageName"))) }
        catch (_: Exception) { pipUnavailable = "Picture-in-picture settings are unavailable on this device. The call continues." }
    }

    private fun returnToApp() {
        if (valid()) ServiceLocator.callPresentationController.showFullScreen()
        internalNavigation = true
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        finishAndRemoveTask()
    }

    override fun onUserLeaveHint() { if (!internalNavigation && valid()) requestPip(); super.onUserLeaveHint() }
    override fun onResume() { super.onResume(); internalNavigation = false }
    override fun onPictureInPictureModeChanged(active: Boolean, config: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(active, config)
        compact = active
        // Do not publish this separate task's PiP mode into MainActivity's navigation presentation.
    }
    override fun onDestroy() {
        ServiceLocator.callSignaling.removeStateListener(changed)
        ServiceLocator.apiBackend.removeAuthScopeListener(authChanged)
        handler.removeCallbacksAndMessages(null)
        // UI destruction is never hangup. Foreground service retains the accepted media session.
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_CALL = "chaty.pip.call"
        private const val EXTRA_GENERATION = "chaty.pip.generation"
        private const val EXTRA_ENTER = "chaty.pip.enter"
        private const val STATE_CALL = "call"
        private const val STATE_GENERATION = "generation"
        fun intentFor(context: Context, enter: Boolean = false): Intent? {
            val api = ServiceLocator.apiBackend
            val captured = api.authSnapshot()
            var target: Intent? = null
            api.withAuthScope(captured) {
                val session = ServiceLocator.callSignaling.currentSession?.takeIf {
                    com.example.chat.ui.calls.CallPipSessionPolicy.allows(captured, captured, it.callId, it) && ServiceLocator.callSignaling.hasLocalMedia
                }
                if (session != null) target = Intent(context, CallPipActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_CALL, session.callId).putExtra(EXTRA_GENERATION, captured.generation).putExtra(EXTRA_ENTER, enter)
            }
            return target
        }
        fun launch(context: Context): Boolean {
            if (!ServiceLocator.callPresentationPrefs.value.pictureInPictureEnabled || !ServiceLocator.nativeCallPip.isSupported(context)) return false
            val intent = intentFor(context, enter = true) ?: return false
            return try { context.startActivity(intent); true } catch (_: Exception) { false }
        }
    }
}
