package com.example.chat.ui.settings.security

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.data.services.LocalLockService
import kotlinx.coroutines.launch

/**
 * Port of AppLockOverlayModal (app_lock_overlay.dart). Full-screen, back is
 * swallowed (PopScope canPop:false). [onUnlocked] is invoked only by
 * [AppLockController] after a verified credential or native auth success.
 * [onSetupMissingCredential] hosts the LockCredentialSetupModal flow (returns
 * true when a credential was configured, then capabilities reload).
 * Pending visual check: key press scale animation, haptics, Material icons.
 */
@Composable
fun AppLockOverlay(
    preferences: ChatyPreferencesController,
    lockService: LocalLockService,
    onUnlocked: () -> Unit,
    title: String = "Chaty Lock",
    reason: String = "Authenticate to unlock Chaty",
    onSetupMissingCredential: (suspend (method: String, pinLength: Int) -> Boolean)? = null,
) {
    com.example.chat.ui.kit.ProtectSensitiveWindow()
    val scope = rememberCoroutineScope()
    val security = preferences.security
    val method = security.lockMethod
    val owner = preferences.scopeGeneration
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)
    // The prior remember(lockService) retained an old callback and authentication owner.
    val controller = remember(lockService, preferences, owner, method, reason) {
        AppLockController(lockService, { method }, reason) {
            if (preferences.scopeGeneration == owner && preferences.security.lockMethod == method) {
                currentOnUnlocked()
            }
        }
    }
    DisposableEffect(controller) { onDispose { controller.dispose() } }
    val ui by controller.state.collectAsState()
    var password by remember(controller) { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    val locked = !ui.capabilitiesReady || ui.busy || ui.cooldownSeconds > 0

    BackHandler(enabled = true) { /* canPop: false */ }
    // Dart _setupMissingCredential: LockCredentialSetupModal.show(method, pinLength), then reload capabilities.
    // When the caller supplies no host hook (all current call sites), the overlay hosts the sheet itself.
    var setupRequest by remember(controller) { mutableStateOf<Pair<String, Int>?>(null) }
    setupRequest?.let { (setupMethod, setupPinLength) ->
        LockCredentialSetupSheet(setupMethod, setupPinLength, remember(lockService) { LockSetupServiceAdapter(lockService) }) { configured ->
            setupRequest = null
            if (configured) scope.launch { controller.loadCapabilities() }
        }
    }
    LaunchedEffect(controller) {
        controller.loadCapabilities()
        if (controller.state.value.capabilitiesReady) controller.startNativeMethodIfNeeded()
    }
    LaunchedEffect(controller, ui.cooldownSeconds > 0) {
        while (controller.state.value.cooldownSeconds > 0) {
            delay(1_000)
            controller.refreshCooldown()
        }
    }
    LaunchedEffect(ui.errorMessage) { if (ui.errorMessage.isNotEmpty() && method == "Password") password = "" }

    Box(Modifier.fillMaxSize().semantics { paneTitle = title; isTraversalGroup = true }.background(cs.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().widthIn(max = 430.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(74.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                // Former Unicode glyph varied by system font and could be announced as a symbol.
                com.example.chat.ui.icons.ChatyIcon(
                    com.example.chat.ui.icons.ChatyIconId.lock,
                    semanticLabel = null,
                    modifier = Modifier.size(32.dp),
                    color = cs.primary,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(title, modifier = Modifier.semantics { heading() }, color = cs.onBackground, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (method == "Device Credential") "Use your device screen lock to continue" else "Use $method to continue",
                color = cs.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            if (ui.errorMessage.isNotEmpty()) {
                Text(
                    ui.errorMessage, color = cs.onErrorContainer, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.clip(RoundedCornerShape(12.dp)).background(cs.errorContainer).padding(12.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
            val credentialMethod = method == "PIN" || method == "Pattern" || method == "Password"
            if (!ui.capabilitiesReady) {
                if (ui.capabilityError) {
                    Button(onClick = { scope.launch { controller.loadCapabilities() } }) { Text("Retry reading lock settings") }
                } else {
                    CircularProgressIndicator()
                    Text("Checking lock settings...", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            } else if (!ui.hasConfiguredCredential && credentialMethod) {
                Text("This lock method has not been configured securely on this device yet.", color = cs.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    scope.launch {
                        // A missing/corrupt credential is not authorization to replace the lock.
                        if (!lockService.authenticateDeviceCredential("Verify your device screen lock to repair Chaty Lock")) return@launch
                        val host = onSetupMissingCredential
                        if (host == null) setupRequest = method to ui.pinLength
                        else if (host(method, ui.pinLength)) controller.loadCapabilities()
                    }
                }) { Text("Set up $method") }
            } else when (method) {
                "PIN" -> {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { PinDots(ui.pinLength, ui.enteredPin.length) }
                    if (ui.cooldownSeconds > 0) Text("Try again in ${ui.cooldownSeconds / 60}:${(ui.cooldownSeconds % 60).toString().padStart(2, '0')}", modifier = Modifier.semantics { stateDescription = "Retry available in ${ui.cooldownSeconds} seconds" })
                    Spacer(Modifier.height(24.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    PinKeypad(
                        cooldownSeconds = ui.cooldownSeconds,
                        enabled = !locked,
                        biometricEnabled = !ui.busy && ui.biometricAvailable,
                        deleteEnabled = !ui.busy && ui.enteredPin.isNotEmpty(),
                        onDigit = { d -> scope.launch { controller.verifyPinDigit(d) } },
                        onBiometric = { scope.launch { controller.runNativeAuthentication("Biometric") } },
                        onDelete = { controller.deleteDigit() },
                    )
                    }
                    Spacer(Modifier.height(12.dp))
                    NativeFallbacks(ui.biometricAvailable, ui.busy, controller, scope, "Use fingerprint / Face unlock")
                }
                "Pattern" -> {
                    PatternLockPad(
                        hideTrace = security.makePatternInvisible,
                        enableHaptics = !security.disablePatternVibration,
                        onPatternComplete = { p -> scope.launch { controller.verifySecret("Pattern", p) } },
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("Cannot draw a pattern? Use device screen lock below.", color = cs.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
                    NativeFallbacks(ui.biometricAvailable, ui.busy, controller, scope, "Use fingerprint / Face unlock")
                }
                "Password" -> {
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        enabled = !locked, singleLine = true, label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (!locked) {
                                val submitted = password
                                password = ""
                                scope.launch { controller.verifySecret("Password", submitted) }
                            }
                        }),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val submitted = password
                            password = ""
                            scope.launch { controller.verifySecret("Password", submitted) }
                        },
                        enabled = !locked, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (ui.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Unlock")
                    }
                    Spacer(Modifier.height(10.dp))
                    NativeFallbacks(ui.biometricAvailable, ui.busy, controller, scope, "Use biometric instead")
                }
                else -> {
                    Spacer(Modifier.height(8.dp))
                    if (ui.busy) CircularProgressIndicator() else Button(onClick = { scope.launch { controller.runNativeAuthentication(method) } }) {
                        Text(if (method == "Device Credential") "Authenticate with device lock" else "Scan biometric")
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeFallbacks(
    biometricAvailable: Boolean,
    busy: Boolean,
    controller: AppLockController,
    scope: kotlinx.coroutines.CoroutineScope,
    biometricLabel: String,
) {
    val cs = MaterialTheme.colorScheme
    if (biometricAvailable) {
        TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { scope.launch { controller.runNativeAuthentication("Biometric") } }, enabled = !busy) {
            Text(biometricLabel, color = cs.primary)
        }
    }
    TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { scope.launch { controller.runNativeAuthentication("Device Credential") } }, enabled = !busy) {
        Text("Can't remember? Use device screen lock", fontSize = 14.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun PinDots(length: Int, filledCount: Int) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.semantics { stateDescription = "$filledCount of $length digits entered"; liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(length) { i ->
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .background(if (i < filledCount) cs.primary else Color.Transparent)
                    .border(2.dp, cs.primary, CircleShape),
            )
        }
    }
}

private val keySub = mapOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+")

@Composable
private fun PinKeypad(
    cooldownSeconds: Int,
    enabled: Boolean,
    biometricEnabled: Boolean,
    deleteEnabled: Boolean,
    onDigit: (String) -> Unit,
    onBiometric: () -> Unit,
    onDelete: () -> Unit,
) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(Modifier.semantics { if (cooldownSeconds > 0) stateDescription = "Locked for $cooldownSeconds seconds" }.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                r.forEach { d -> Box(Modifier.weight(1f)) { NumberKey(d, keySub[d] ?: "", enabled) { onDigit(d) } } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { ActionKey(Icons.Rounded.Fingerprint, "Use biometric", biometricEnabled, onBiometric) }
            Box(Modifier.weight(1f)) { NumberKey("0", "+", enabled) { onDigit("0") } }
            Box(Modifier.weight(1f)) { ActionKey(Icons.AutoMirrored.Rounded.Backspace, "Delete last digit", deleteEnabled, onDelete) }
        }
    }
}

@Composable
private fun NumberKey(label: String, sub: String, enabled: Boolean, onTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).aspectRatio(1.3f).clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.55f))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Digit $label", onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = cs.onSurface, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        if (sub.isNotEmpty()) Text(sub, modifier = Modifier.clearAndSetSemantics { }, color = cs.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun ActionKey(icon: ImageVector, label: String, enabled: Boolean, onTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).aspectRatio(1.3f).clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        // Previously a Unicode glyph with no stable action name.
        androidx.compose.material3.Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp),
            tint = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.3f))
    }
}
