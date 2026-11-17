package com.example.chat.ui.settings.security

import com.example.chat.ui.settings.state.settingsSnapshotFlow
import com.example.chat.ui.settings.state.collectSettingsState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Pattern
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.TouchApp
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.data.services.LocalLockService
import com.example.chat.ui.settings.ChatyBadge
import com.example.chat.ui.settings.ChatyChoiceTile
import com.example.chat.ui.settings.ChatySettingsPage
import com.example.chat.ui.settings.ChatySettingsRowDivider
import com.example.chat.ui.settings.ChatySettingsSection
import com.example.chat.ui.settings.ChatySettingsTile
import com.example.chat.ui.settings.ChatySwitchTile
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

private val AutoLockOptions = listOf("Immediately", "15s", "30s", "1m", "5m", "15m")

private sealed interface Modal {
    data object AboutProtection : Modal
    class Verify(val result: CompletableDeferred<Boolean>) : Modal
    class Pick(val current: LockMethodType, val result: CompletableDeferred<LockMethodType?>) : Modal
    class Setup(val method: String, val pinLength: Int, val result: CompletableDeferred<Boolean>) : Modal
    class TestLock(val result: CompletableDeferred<Boolean>) : Modal
    class SecretPhrase(val result: CompletableDeferred<String?>) : Modal
}

/**
 * Native Kotlin/Compose security settings screen.
 * Flow decisions live in [SecurityFlowRunner] and are covered by JVM tests.
 * [onOpenLockedChats] is supplied by the navigation host.
 */
@Composable
fun SecurityCenterScreen(
    preferences: ChatyPreferencesController,
    lockService: LocalLockService,
    onBack: () -> Unit,
    onOpenLockedChats: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
//     var version by remember { mutableIntStateOf(0) }
//     DisposableEffect(preferences) {
//         val l: () -> Unit = { version += 1 }
//         preferences.addListener(l)
//         onDispose { preferences.removeListener(l) }
//     var version by remember { mutableIntStateOf(0) }
//     DisposableEffect(preferences) {
//         val l: () -> Unit = { version += 1 }
//         preferences.addListener(l)
//         onDispose { preferences.removeListener(l) }
//     }
//     val accountGeneration = preferences.scopeGeneration
    val selectedFlow = remember(preferences, scope) {
        settingsSnapshotFlow(scope,
            snapshot = { preferences.scopeGeneration to preferences.security },
            addListener = preferences::addListener, removeListener = preferences::removeListener)
    }
//     val accountGeneration = preferences.scopeGeneration
    val selected by selectedFlow.collectSettingsState()
    val accountGeneration = selected.first
    var pinLength by remember(preferences, accountGeneration) { mutableIntStateOf(4) }
//     var biometricAvailable by remember { mutableStateOf(false) }
    var biometricAvailable by remember(preferences, accountGeneration) { mutableStateOf(false) }
    var enrolledClasses by remember(preferences, accountGeneration) { mutableStateOf<List<String>>(emptyList()) }
    var modal by remember(preferences, accountGeneration) { mutableStateOf<Modal?>(null) }
    var active by remember(preferences, accountGeneration) { mutableStateOf(true) }
    var flowBusy by remember(preferences, accountGeneration) { mutableStateOf(false) }
    // Pending callers get a safe terminal answer if their modal or screen disappears.
    DisposableEffect(modal) {
        val pending = modal
        onDispose {
            when (pending) {
                is Modal.Verify -> pending.result.complete(false)
                is Modal.TestLock -> pending.result.complete(false)
                is Modal.Setup -> pending.result.complete(false)
                is Modal.Pick -> pending.result.complete(null)
                is Modal.SecretPhrase -> pending.result.complete(null)
                else -> Unit // Informational modals do not own a pending result.
            }
        }
    }
    DisposableEffect(preferences, accountGeneration) { onDispose { active = false } }

    suspend fun loadCapabilities() {
        val pl = lockService.getPinLength()
        val bio = lockService.canUseBiometrics()
        val classes = lockService.availableBiometrics()
        if (!active || preferences.scopeGeneration != accountGeneration) return
        pinLength = pl
        biometricAvailable = bio
        enrolledClasses = classes
    }
//     LaunchedEffect(preferences, accountGeneration) { loadCapabilities() }
    LaunchedEffect(preferences, accountGeneration) {
        try { loadCapabilities() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (active && preferences.scopeGeneration == accountGeneration) {
                snackbar.showSnackbar("Could not read device lock support. Reopen this page to retry.")
            }
        }
    }

    fun showToast(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    val host = remember(preferences, lockService, accountGeneration) {
        object : SecurityFlowHost {
            override fun currentLockMethod() = preferences.security.lockMethod
            override fun isAppLockEnabled() = preferences.security.isAppLockEnabled
            override suspend fun hasCredential(storageKey: String) = lockService.hasCredential(storageKey)
            override suspend fun authenticateBiometric(reason: String) = lockService.authenticateBiometric(reason)
            override suspend fun authenticateDeviceCredential(reason: String) = lockService.authenticateDeviceCredential(reason)
            override suspend fun showVerifyOverlay(): Boolean {
                val d = CompletableDeferred<Boolean>(); modal = Modal.Verify(d); return d.await().also { modal = null }
            }
            override suspend fun pickMethod(current: LockMethodType): LockMethodType? {
                val d = CompletableDeferred<LockMethodType?>(); modal = Modal.Pick(current, d); return d.await().also { modal = null }
            }
            override suspend fun setupCredential(storageKey: String, pinLength: Int): Boolean {
                val d = CompletableDeferred<Boolean>(); modal = Modal.Setup(storageKey, pinLength, d); return d.await().also { modal = null }
            }
            override suspend fun reloadCapabilities() = loadCapabilities()
            override fun toast(message: String) = showToast(message)
            override fun isActive() = active && preferences.scopeGeneration == accountGeneration
            override fun applySecurity(lockMethod: String?, appLockEnabled: Boolean?, logTitle: String) {
//                 val latest = preferences.security
//                 preferences.updateSecurity(
                if (!isActive()) return
                val latest = preferences.security
                preferences.updateSecurity(
                    latest.copy(
                        lockMethod = lockMethod ?: latest.lockMethod,
                        isAppLockEnabled = appLockEnabled ?: latest.isAppLockEnabled,
                    ),
                    logTitle = logTitle,
                )
            }
        }
    }

    // Test prompts share the same busy gate as lock-setting flows.
    fun testNativeMethod(biometric: Boolean) {
        if (flowBusy || modal != null || !host.isActive()) return
        flowBusy = true
        scope.launch {
            try {
                val ok = if (biometric) lockService.authenticateBiometric("Test biometric unlock for Chaty")
                    else lockService.authenticateDeviceCredential("Test your device screen lock for Chaty")
                if (host.isActive()) showToast(if (ok) "Device authentication works." else "Device authentication failed or was cancelled.")
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (host.isActive()) showToast("Device authentication is unavailable. Try again.")
            } finally { flowBusy = false }
        }
    }

    fun runFlow(intent: SecurityIntent, target: LockMethodType? = null, setupPinLength: Int? = null) {
//         if (flowBusy) return
//         if (flowBusy || !host.isActive()) return
        if (flowBusy || modal != null || !host.isActive()) return
        flowBusy = true
        scope.launch {
            try { SecurityFlowRunner.run(host, intent, target, setupPinLength, pinLength) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { showToast("Could not update the lock setting. Try again.") }
            finally { flowBusy = false; modal = null }
        }
    }

//     @Suppress("UNUSED_EXPRESSION") version
//     val security = preferences.security
    // Version counter replaced by selected lifecycle state.
    val security = selected.second
    com.example.chat.ui.kit.ProtectSensitiveWindow(active = security.isAppLockEnabled)
    val lockedChats = security.lockedConversationIds.size

    Box(Modifier.fillMaxSize()) {
        ChatySettingsPage(
            title = "Security & App Lock",
            subtitle = "Control who can open Chaty on this device",
            onBack = onBack,
        ) {
            ChatySettingsSection(
                title = "Messaging security status",
                description = "Standard one-to-one and group messages in this Kotlin release use the custom REST chat service over HTTPS. Their content is not end-to-end encrypted, and the service can read it. Standard cloud attachments are also server-readable. The RFC 9420 MLS engine is present but is not connected to the active REST login and message routes. Large peer transfers use AES-GCM encrypted chunks and require the MLS-protected invitation path; setup failures stop the transfer.",
            ) {
                Text("Messages are not end-to-end encrypted.", color = colors.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "HTTPS protects traffic in transit between Chaty and the service. It does not prevent the service from accessing standard message content. App Lock protects access to this device only; it does not encrypt server-side chat data.",
                    color = colors.foregroundSecondary,
                    fontSize = 14.sp,
                )
                TextButton(onClick = { modal = Modal.AboutProtection }) { Text("What App Lock protects") }
            }
            ChatySettingsSection(
                title = "Chaty App Lock",
// Superseded source retained below per additive-only review rule.
//                 description = "App Lock opens Chaty. Chat Lock protects selected conversations. A secret phrase finds the vault but is not a replacement for its authentication.",
                description = "App Lock opens Chaty. Chat Lock protects selected conversations. A secret phrase finds the vault but is not a replacement for its authentication. Immediately is a background timeout, not a per-screen timer. System dialogs can leave the app; lifecycle exemptions require device verification.",
            ) {
// Superseded source retained below per additive-only review rule.
//                 val rows = mutableListOf<@Composable () -> Unit>()
//                 rows += {
                // Former mutableListOf<@Composable () -> Unit>() rebuilt and replayed every pass.
                // Static keyed groups retain each setting's composition without a lambda list.
                Text("Method", color = colors.foregroundSecondary, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
                androidx.compose.runtime.key("security-setting-1") {
                    ChatySwitchTile(
                        icon = Icons.Rounded.Lock,
                        iconColor = colors.accent,
                        title = "Enable Chaty Lock",
                        subtitle = if (security.isAppLockEnabled) "App lock active (${security.lockMethod})"
                        else "Protect the application with biometrics, PIN, pattern, password, or device lock",
                        value = security.isAppLockEnabled,
                        onChanged = { v -> runFlow(if (v) SecurityIntent.enableLock else SecurityIntent.disableLock) },
                    )
                }
                if (security.isAppLockEnabled) {
// Superseded source retained below per additive-only review rule.
//                     rows += {
                    androidx.compose.runtime.key("security-setting-2") {
                        ChatySettingsTile(
                            icon = Icons.Rounded.Style,
                            title = "Lock type",
                            subtitle = "${security.lockMethod} \u2022 tap to verify and change type",
                            trailing = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = colors.foregroundTertiary, modifier = Modifier.size(20.dp)) },
                            onTap = { runFlow(SecurityIntent.changeMethod, null) },
                        )
                    }
// Superseded source retained below per additive-only review rule.
//                     if (security.lockMethod == "Biometric") rows += {
                    if (security.lockMethod == "Biometric") androidx.compose.runtime.key("security-setting-3") {
                        ChatySettingsTile(
                            icon = Icons.Rounded.Fingerprint,
                            title = "Biometric authentication",
//                             subtitle = if (biometricAvailable) "Uses enrolled fingerprint / face authentication from this device"
//                             else "No enrolled biometric is currently available",
                            subtitle = androidx.compose.ui.res.stringResource(when {
                                "strong" in enrolledClasses -> com.example.chat.R.string.security_biometric_strong_class
                                "weak" in enrolledClasses -> com.example.chat.R.string.security_biometric_weak_class
                                biometricAvailable -> com.example.chat.R.string.security_biometric_unknown_class
                                else -> com.example.chat.R.string.security_biometric_no_class
                            }),
                            badgeText = if (biometricAvailable) "READY" else "UNAVAILABLE",
                            badgeColor = if (biometricAvailable) colors.success else colors.warning,
//                             onTap = {
//                                 scope.launch {
//                                     val ok = lockService.authenticateBiometric("Test biometric unlock for Chaty")
// //                                     if (active) showToast(if (ok) "Biometric unlock works." else "Biometric failed or was cancelled.")
//                                     if (active && preferences.scopeGeneration == accountGeneration) showToast(if (ok) "Biometric unlock works." else "Biometric failed or was cancelled.")
//                                 }
//                             },
                            enabled = !flowBusy,
                            onTap = { testNativeMethod(true) },
                        )
                    }
// Superseded source retained below per additive-only review rule.
//                     if (security.lockMethod == "Device Credential") rows += {
                    if (security.lockMethod == "Device Credential") androidx.compose.runtime.key("security-setting-4") {
                        ChatySettingsTile(
                            icon = Icons.Rounded.PhonelinkLock,
                            title = "Device screen lock",
                            subtitle = "Use the PIN, pattern, password, or biometric managed by Android",
//                             onTap = {
//                                 scope.launch {
//                                     val ok = lockService.authenticateDeviceCredential("Test your device screen lock for Chaty")
// //                                     if (active) showToast(if (ok) "Device lock works." else "Device authentication failed or was cancelled.")
//                                     if (active && preferences.scopeGeneration == accountGeneration) showToast(if (ok) "Device lock works." else "Device authentication failed or was cancelled.")
//                                 }
//                             },
                            enabled = !flowBusy,
                            onTap = { testNativeMethod(false) },
                        )
                    }
                    if (security.lockMethod == "PIN") {
// Superseded source retained below per additive-only review rule.
//                         rows += {
                        androidx.compose.runtime.key("security-setting-5") {
                            ChatyChoiceTile(
                                title = "PIN Length",
                                options = listOf("4 digits", "6 digits"),
                                selectedOption = if (pinLength == 6) "6 digits" else "4 digits",
                                optionLabel = { it },
// Superseded source retained below per additive-only review rule.
//                                 onSelected = { v -> runFlow(SecurityIntent.changeCredential, LockMethodType.pin, if (v == "6 digits") 6 else 4) },
                                onSelected = { v ->
                                    val requested = if (v == "6 digits") 6 else 4
                                    if (requested != pinLength) runFlow(SecurityIntent.changeCredential, LockMethodType.pin, requested)
                                },
                            )
                        }
// Superseded source retained below per additive-only review rule.
//                         rows += {
                        androidx.compose.runtime.key("security-setting-6") {
                            ChatySettingsTile(
                                icon = Icons.Rounded.Pin,
                                title = "Change PIN Code",
                                subtitle = "$pinLength-digit PIN stored securely on this device",
                                onTap = { runFlow(SecurityIntent.changeCredential, LockMethodType.pin, pinLength) },
                            )
                        }
                    }
// Superseded source retained below per additive-only review rule.
//                     if (security.lockMethod == "Password") rows += {
                    if (security.lockMethod == "Password") androidx.compose.runtime.key("security-setting-7") {
                        ChatySettingsTile(
                            icon = Icons.Rounded.Password,
                            title = "Change Lock Password",
                            subtitle = "Password is hashed and stored in secure device storage",
                            onTap = { runFlow(SecurityIntent.changeCredential, LockMethodType.password) },
                        )
                    }
                    if (security.lockMethod == "Pattern") {
// Superseded source retained below per additive-only review rule.
//                         rows += {
                        androidx.compose.runtime.key("security-setting-8") {
                            ChatySettingsTile(
                                icon = Icons.Rounded.Pattern,
                                title = "Change Unlock Pattern",
                                subtitle = "Draw and confirm a 3\u00D73 gesture pattern",
                                onTap = { runFlow(SecurityIntent.changeCredential, LockMethodType.pattern) },
                            )
                        }
// Superseded source retained below per additive-only review rule.
//                         rows += {
                        androidx.compose.runtime.key("security-setting-9") {
                            ChatySwitchTile(
                                title = "Make Pattern Invisible",
                                subtitle = "Hide pattern lines while drawing",
                                value = security.makePatternInvisible,
// onChanged = { v -> preferences.updateSecurity(security.copy(makePatternInvisible = v), logTitle = "Pattern visibility") },
// onChanged = { v -> preferences.updateSecurity(preferences.security.copy(makePatternInvisible = v), logTitle = "Pattern visibility") },
                                onChanged = { v -> if (active && preferences.scopeGeneration == accountGeneration) preferences.updateSecurity(preferences.security.copy(makePatternInvisible = v), logTitle = "Pattern visibility") },
                            )
                        }
// Superseded source retained below per additive-only review rule.
//                         rows += {
                        androidx.compose.runtime.key("security-setting-10") {
                            ChatySwitchTile(
                                title = "Disable Pattern Vibration",
                                subtitle = "Turn off haptic feedback while drawing the pattern",
                                value = security.disablePatternVibration,
// onChanged = { v -> preferences.updateSecurity(security.copy(disablePatternVibration = v), logTitle = "Pattern vibration") },
// onChanged = { v -> preferences.updateSecurity(preferences.security.copy(disablePatternVibration = v), logTitle = "Pattern vibration") },
                                onChanged = { v -> if (active && preferences.scopeGeneration == accountGeneration) preferences.updateSecurity(preferences.security.copy(disablePatternVibration = v), logTitle = "Pattern vibration") },
                            )
                        }
                    }
// Superseded source retained below per additive-only review rule.
//                     rows += {
                    Text("Behavior", color = colors.foregroundSecondary, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
                    androidx.compose.runtime.key("security-setting-11") {
                        ChatyChoiceTile(
                            title = "Auto Lock Timeout",
                            options = AutoLockOptions,
                            selectedOption = security.autoLockTimeout,
                            optionLabel = { it },
// onSelected = { v -> preferences.updateSecurity(security.copy(autoLockTimeout = v), logTitle = "Auto Lock Timeout") },
// onSelected = { v -> preferences.updateSecurity(preferences.security.copy(autoLockTimeout = v), logTitle = "Auto Lock Timeout") },
                            onSelected = { v -> if (active && preferences.scopeGeneration == accountGeneration) preferences.updateSecurity(preferences.security.copy(autoLockTimeout = v), logTitle = "Auto Lock Timeout") },
                        )
                    }
// Superseded source retained below per additive-only review rule.
//                     rows += {
                    Text("Notifications", color = colors.foregroundSecondary, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
                    androidx.compose.runtime.key("security-setting-12") {
                        ChatySwitchTile(
                            icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.eyeOff),
                            title = "Hide Notification Content when Locked",
                            subtitle = "Conceal message body text while the application is locked",
                            value = security.hideLockNotificationContent,
// onChanged = { v -> preferences.updateSecurity(security.copy(hideLockNotificationContent = v), logTitle = "Lock notification privacy") },
// onChanged = { v -> preferences.updateSecurity(preferences.security.copy(hideLockNotificationContent = v), logTitle = "Lock notification privacy") },
                            onChanged = { v -> if (active && preferences.scopeGeneration == accountGeneration) preferences.updateSecurity(preferences.security.copy(hideLockNotificationContent = v), logTitle = "Lock notification privacy") },
                        )
                    }
// Superseded source retained below per additive-only review rule.
//                     rows += {
                    androidx.compose.runtime.key("security-setting-13") {
                        ChatySettingsTile(
                            icon = Icons.Rounded.LockOpen,
                            iconColor = colors.accent,
                            title = "Test Lock Screen",
                            trailing = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = colors.foregroundTertiary, modifier = Modifier.size(20.dp)) },
                            subtitle = "Run the same authentication gate used by the application",
//                             onTap = { modal = Modal.TestLock(CompletableDeferred()) },
                            onTap = { if (!flowBusy && host.isActive()) modal = Modal.TestLock(CompletableDeferred()) },
                        )
                    }
                }
// Superseded source retained below per additive-only review rule.
//                 rows.forEachIndexed { i, row ->
//                     row()
//                     if (i < rows.lastIndex) ChatySettingsRowDivider()
//                 }
                // Rows are composed above, not allocated into a mutable list.
            }
            ChatySettingsSection(
                title = "Chat Lock & Hidden Chats",
                description = "Locked and hidden conversations require verified local authentication. Hidden chats do not appear in normal chat lists.",
            ) {
                ChatySettingsTile(
                    icon = Icons.Outlined.Lock,
                    iconColor = colors.accent,
                    title = "Open Locked & Hidden Chats",
                    subtitle = if (lockedChats == 0) "No chats protected \u2022 Tap to manage vault"
                    else "$lockedChats conversation${if (lockedChats == 1) "" else "s"} in secure vault",
                    badgeText = "$lockedChats",
                    badgeColor = if (lockedChats > 0) colors.accent else colors.foregroundSecondary,
//                     onTap = onOpenLockedChats,
                    onTap = { if (host.isActive()) onOpenLockedChats() },
                )
                ChatySettingsRowDivider()
                ChatySwitchTile(
                    icon = Icons.Outlined.Lock, title = "Hide locked chats",
                    subtitle = "Keep protected conversations out of the normal inbox. Open them from this vault entry.",
                    value = security.hideLockedChats,
//                     onChanged = { preferences.updateSecurity(preferences.security.copy(hideLockedChats = it), logTitle = "Hide locked chats") },
                    onChanged = { if (host.isActive()) preferences.updateSecurity(preferences.security.copy(hideLockedChats = it), logTitle = "Hide locked chats") },
                )
                ChatySwitchTile(
                    icon = Icons.Rounded.TouchApp,
                    title = "Unlock via Chaty Title Tap",
// Superseded source retained below per additive-only review rule.
//                     subtitle = "Tap the main screen Chaty header to authenticate and open locked chats",
                    subtitle = if (security.hideLockedChats) "Tap the main screen Chaty header to authenticate and open locked chats"
//                         else "Only applies when Hide locked chats is enabled below",
                        else "Only applies when Hide locked chats is enabled",
                    value = security.entryByAppTitle,
                    enabled = security.hideLockedChats,
// onChanged = { v -> preferences.updateSecurity(security.copy(entryByAppTitle = v), logTitle = "Title tap vault entry") },
// onChanged = { v -> preferences.updateSecurity(preferences.security.copy(entryByAppTitle = v), logTitle = "Title tap vault entry") },
                    onChanged = { v -> if (active && preferences.scopeGeneration == accountGeneration) preferences.updateSecurity(preferences.security.copy(entryByAppTitle = v), logTitle = "Title tap vault entry") },
                )
                ChatySettingsRowDivider()
                ChatySwitchTile(
                    icon = Icons.Rounded.Key,
                    title = "Secret Code Search Discovery",
                    subtitle = "Reveal hidden locked chats only when typing your secret word or emoji in search",
                    value = security.entryBySecretPhrase,
                    onChanged = { v ->
//                         if (!flowBusy) {
                        if (!flowBusy && host.isActive()) {
                        flowBusy = true
                        scope.launch {
                            try {
                            if (v) {
                                if (preferences.security.isAppLockEnabled &&
                                    !SecurityFlowRunner.verifyCurrent(host, SecurityFlowRunner.methodTypeOf(preferences.security.lockMethod))) return@launch
                                val d = CompletableDeferred<String?>()
                                modal = Modal.SecretPhrase(d)
                                val phrase = d.await().also { modal = null }
                                if (phrase.isNullOrEmpty() || !host.isActive()) return@launch
                                lockService.setSecretPhrase(phrase)
                                if (!host.isActive()) return@launch
                                preferences.updateSecurity(preferences.security.copy(entryBySecretPhrase = true), logTitle = "Secret search phrase entry")
                            } else {
                                preferences.updateSecurity(preferences.security.copy(entryBySecretPhrase = false), logTitle = "Secret search phrase disabled")
                            }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { showToast("Could not save the secret phrase. Your choice was not changed.") }
                            finally { flowBusy = false; modal = null }
                        }
                        }
                    },
                )
                ChatySettingsRowDivider()
                ChatySwitchTile(
                    icon = Icons.Rounded.Screenshot,
                    title = "Protect Screen & App Previews",
                    subtitle = "Blank out app thumbnail in multi-tasking view and block screenshots on supported devices",
                    value = security.protectFromScreenshots,
// onChanged = { v -> preferences.updateSecurity(security.copy(protectFromScreenshots = v), logTitle = "Screen & preview security") },
// onChanged = { v -> preferences.updateSecurity(preferences.security.copy(protectFromScreenshots = v), logTitle = "Screen & preview security") },
                    onChanged = { v -> if (active && preferences.scopeGeneration == accountGeneration) preferences.updateSecurity(preferences.security.copy(protectFromScreenshots = v), logTitle = "Screen & preview security") },
                )
                ChatySettingsRowDivider()
                ChatySettingsTile(
                    icon = Icons.Outlined.VerifiedUser,
                    title = "Choose authentication method",
                    subtitle = "Uses ${security.lockMethod}. Chat Lock protects specific chats even if full App Lock is off.",
                    onTap = { runFlow(SecurityIntent.changeMethod) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    val setupAdapter = remember(lockService) { LockSetupServiceAdapter(lockService) }
    when (val m = modal) {
        Modal.AboutProtection -> AlertDialog(
            onDismissRequest = { modal = null },
            title = { Text("What App Lock protects") },
            text = { Text("App Lock asks for local authentication before opening Chaty. Chat Lock adds a gate for selected conversations. Neither feature adds end-to-end encryption to messages or removes copies from recipients, the service, galleries or backups. Screenshot protection and notification content are separate controls. A secret search phrase reveals the vault entry; it is not a replacement for authentication. Device owners with a compromised or rooted operating system may bypass app-only controls.") },
            confirmButton = { TextButton(onClick = { modal = null }) { Text("Done") } },
        )
        is Modal.Verify -> LockOverlayDialog(preferences, lockService, "Confirm it's you", "Verify your current lock to continue", m.result)
        is Modal.TestLock -> LockOverlayDialog(preferences, lockService, "Test Chaty Lock", "Authenticate to verify your Chaty Lock configuration", m.result) { modal = null }
        is Modal.Pick -> LockMethodSelectorSheet(setupAdapter, m.current) { m.result.complete(it) }
        is Modal.Setup -> LockCredentialSetupSheet(m.method, m.pinLength, setupAdapter, replaceExisting = true) { m.result.complete(it) }
        is Modal.SecretPhrase -> SecretPhraseDialog(m.result)
        null -> {}
    }
}

@Composable
private fun LockOverlayDialog(
    preferences: ChatyPreferencesController,
    lockService: LocalLockService,
    title: String,
    reason: String,
    result: CompletableDeferred<Boolean>,
    onClosed: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = { result.complete(false); onClosed() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
    ) {
        Box(Modifier.fillMaxSize()) {
            AppLockOverlay(
                preferences = preferences,
                lockService = lockService,
                onUnlocked = { result.complete(true); onClosed() },
                title = title,
                reason = reason,
            )
        }
    }
}

@Composable
private fun SecretPhraseDialog(result: CompletableDeferred<String?>) {
    val colors = LocalAppColors.current
    var text by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        properties = DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
        onDismissRequest = { result.complete(null) },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {

//Icon(Icons.Rounded.Key, null, tint = colors.accent)
                com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.key, Icons.Rounded.Key, null, tint = colors.accent)
                Spacer(Modifier.width(8.dp))
                Text("Set Secret Search Word")
            }
        },
        text = {
            Column {
                Text(
                    "Enter a secret word, phrase, or emoji. Typing this in the search bar will reveal your hidden locked chats.",
                    fontSize = 13.sp, color = colors.foregroundSecondary,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Secret Word or Emoji") },
                    placeholder = { Text("A private phrase with 6 or more characters") },
                    singleLine = true,
                    supportingText = { Text(CredentialStrength.phraseError(text) ?: "Keep this phrase private.") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = confirmation, onValueChange = { confirmation = it },
                    label = { Text("Confirm private phrase") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    isError = confirmation.isNotEmpty() && confirmation != text,
                    supportingText = { if (confirmation.isNotEmpty() && confirmation != text) Text("Phrases do not match.") })
            }
        },
        dismissButton = { TextButton(onClick = { result.complete(null) }) { Text("Cancel") } },
        confirmButton = {
            Button(enabled = CredentialStrength.phraseError(text) == null && text == confirmation, onClick = { val t = text.trim(); if (CredentialStrength.phraseError(t) == null && text == confirmation) { text = ""; confirmation = ""; result.complete(t) } }) { Text("Save Word") }
        },
    )
}
