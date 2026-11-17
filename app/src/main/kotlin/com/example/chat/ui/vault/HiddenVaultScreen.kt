package com.example.chat.ui.vault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.Conversation
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.example.chat.ui.kit.ChatyBackButton

/** Exact port of lib/features/vault/hidden_vault_screen.dart.
 *
 *  Dedicated Biometric and PBKDF2 PIN Hidden Chat Vault Screen.
 *  Features shake-on-error physics, decoy mode support, and an isolated
 *  encrypted thread list.
 *
 *  Seams:
 *  - ChatyDataStore.conversations -> [conversationsProvider].
 *  - Navigation into ChatDetailScreen -> [onOpenConversation]; the
 *    integration layer routes to its own ChatDetail host.
 *  - SpringInteractiveScale / BentoCard / AppAvatar are design-system
 *    widgets owned by the shared theme layer; simple Compose equivalents are
 *    used here and can be swapped when that layer lands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenVaultScreen(
    controller: HiddenVaultController,
    conversationsProvider: () -> List<Conversation>,
    onOpenConversation: (conversationId: String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    com.example.chat.ui.kit.ProtectSensitiveWindow()
    val reduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // AnimatedBuilder port: recompose whenever the controller notifies.
    var listenTick by remember { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val listener: () -> Unit = { listenTick++ }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") listenTick

    val owner = controller.sessionOwner
    var enteredPin by remember(controller, owner) { mutableStateOf("") }
    var pinLength by remember(controller, owner) { mutableIntStateOf(4) }
    var credentialReady by remember(controller, owner) { mutableStateOf(false) }
    var errorMessage by remember(controller, owner) { mutableStateOf<String?>(null) }
    var isAuthenticating by remember(controller, owner) { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val shakeDistancePx = with(LocalDensity.current) { 12.dp.toPx() }

    fun triggerBiometricAuth() {
        if (controller.sessionOwner != owner || isAuthenticating) return
        isAuthenticating = true
        scope.launch {
            try {
                if (controller.sessionOwner != owner) return@launch
                val verified = controller.unlockVault()
                if (controller.sessionOwner != owner) return@launch
                if (verified) { errorMessage = null; ChatyHapticsPort.light(view) }
                else errorMessage = "Biometric authentication was not completed. Use your PIN or try again."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { errorMessage = "Authentication unavailable. Try again." }
            finally { isAuthenticating = false; enteredPin = "" }
        }
    }

    fun verifyPinAttempt() {
        if (controller.sessionOwner != owner || isAuthenticating || !credentialReady || enteredPin.length != pinLength) return
        val pin = enteredPin
        isAuthenticating = true
        scope.launch {
            try {
                if (controller.sessionOwner != owner) return@launch
                val verified = controller.verifyPin(pin)
                if (controller.sessionOwner != owner) return@launch
                enteredPin = ""
                if (verified) {
                    ChatyHapticsPort.threshold(view)
                    errorMessage = null
                } else {
                    ChatyHapticsPort.error(view)
                    errorMessage = "PIN not accepted. Check your PIN or wait before retrying."
                    shake.snapTo(0f)
                    if (!reduceMotion) shake.animateTo(1f, animationSpec = tween(durationMillis = 200))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { errorMessage = "Could not verify the PIN. Try again." }
            finally { enteredPin = ""; isAuthenticating = false }
        }
    }

    fun onDigitPressed(digit: String) {
        if (controller.sessionOwner != owner || digit.length != 1 || digit[0] !in '0'..'9' || isAuthenticating || !credentialReady || enteredPin.length >= pinLength) return
        enteredPin += digit
        errorMessage = null
        if (enteredPin.length == pinLength) verifyPinAttempt()
    }

    fun onBackspacePressed() {
        if (controller.sessionOwner != owner || isAuthenticating || enteredPin.isEmpty()) return
        enteredPin = enteredPin.dropLast(1)
        errorMessage = null
    }

    LaunchedEffect(controller, owner) {
        try {
            val length = controller.getPinLength()
            if (controller.sessionOwner == owner) { pinLength = length; credentialReady = true }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { errorMessage = "Could not load PIN settings. Reopen the vault to retry." }
        // Biometrics are user initiated; opening the screen never launches an unexpected prompt.
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                enteredPin = ""
                controller.lockVault()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.lockVault()
        }
    }
    LaunchedEffect(controller, owner, controller.isUnlocked) {
        while (controller.isUnlocked) {
            delay(1_000)
            listenTick++ // Re-check the service's inactivity deadline without resetting it.
        }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    if (controller.isUnlocked) {
        UnlockedVaultView(
            controller = controller,
            conversationsProvider = conversationsProvider,
            onOpenConversation = onOpenConversation,
            onBack = onBack,
            isDark = isDark,
            onLock = {
                ChatyHapticsPort.threshold(view) // Dart: HapticFeedback.mediumImpact()
                controller.lockVault()
            },
        )
    } else {
        LockScreenView(
            isDark = isDark,
            onBack = onBack,
            enteredPinLength = enteredPin.length,
            pinLength = pinLength,
            authenticating = isAuthenticating || !credentialReady,
            errorMessage = errorMessage,
            shakeOffsetPx = { sin(shake.value * Math.PI * 6) * shakeDistancePx }, // 12 logical px shake
            onDigit = ::onDigitPressed,
            onBackspace = ::onBackspacePressed,
            onBiometric = ::triggerBiometricAuth,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LockScreenView(
    isDark: Boolean,
    onBack: (() -> Unit)?,
    enteredPinLength: Int,
    pinLength: Int,
    authenticating: Boolean,
    errorMessage: String?,
    shakeOffsetPx: () -> Double,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Scaffold(
        modifier = Modifier.semantics { paneTitle = "Hidden Vault"; isTraversalGroup = true },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Hidden Vault", modifier = Modifier.semantics { heading() }, fontWeight = FontWeight.W700) },
                navigationIcon = { onBack?.let { ChatyBackButton(onPressed = it) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            // Vault icon with ambient glow
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = CircleShape,
                        ambientColor = primary.copy(alpha = 0.25f),
                        spotColor = primary.copy(alpha = 0.25f),
                    )
                    .background(primary.copy(alpha = 0.14f), CircleShape)
                    .border(1.5.dp, primary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.lock,
                    semanticLabel = null,
                    modifier = Modifier.size(34.dp),
                    color = primary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Unlock Vault",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Enter your secure PIN or use biometrics",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            // Same PIN indicator order and dots, wrapping only when the row cannot fit.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.semantics { stateDescription = "$enteredPinLength of $pinLength digits entered" }
                    .offset { IntOffset(shakeOffsetPx().roundToInt(), 0) },
            ) {
                repeat(pinLength) { index ->
                    val isFilled = index < enteredPinLength
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(16.dp)
                            .background(
                                if (isFilled) primary else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            ),
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    errorMessage,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (authenticating) Text("Checking authentication...", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            NumericPad(
                isDark = isDark,
                enabled = !authenticating,
                onDigit = onDigit,
                onBackspace = onBackspace,
                onBiometric = onBiometric,
            )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NumericPad(
    isDark: Boolean,
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("bio", "0", "back"),
    )

    BoxWithConstraints(Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
    val keySize = (maxWidth / 3).coerceIn(48.dp, 72.dp)
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { cell ->
                    val keyInteractions = remember(cell) { MutableInteractionSource() }
                    val pressed by keyInteractions.collectIsPressedAsState()
                    val focused by keyInteractions.collectIsFocusedAsState()
                    // Static state colors give feedback without adding motion at any animation scale.
                    val highlighted = enabled && (pressed || focused)
                    when (cell) {
                        "bio" -> Box(
                            modifier = Modifier
                                .size(keySize)
                                .testTag("vault-key-" + cell)
                                .clip(CircleShape)
                                // .background(primary.copy(alpha = 0.12f))
                                .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else primary.copy(alpha = 0.12f))
                                .clickable(
                                    // interactionSource = remember { MutableInteractionSource() },
                                    interactionSource = keyInteractions,
                                    indication = null,
                                    enabled = enabled,
                                    role = Role.Button,
                                    onClick = onBiometric,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Fingerprint,
                                contentDescription = "Unlock with biometrics",
                                modifier = Modifier.size(32.dp),
                                // tint = primary,
                                tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else primary,
                            )
                        }
                        "back" -> Box(
                            modifier = Modifier
                                .size(keySize)
                                .testTag("vault-key-" + cell)
                                .clip(CircleShape)
                                .background(
                                    // MaterialTheme.colorScheme.surfaceVariant,
                                    if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable(
                                    // interactionSource = remember { MutableInteractionSource() },
                                    interactionSource = keyInteractions,
                                    indication = null,
                                    enabled = enabled,
                                    role = Role.Button,
                                    onClick = onBackspace,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Backspace,
                                contentDescription = "Backspace",
                                modifier = Modifier.size(22.dp),
                                // tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> Box(
                            modifier = Modifier
                                .size(keySize)
                                .testTag("vault-key-" + cell)
                                .clip(CircleShape)
                                .background(
                                    // MaterialTheme.colorScheme.surface,
                                    if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                )
                                .clickable(
                                    // interactionSource = remember { MutableInteractionSource() },
                                    interactionSource = keyInteractions,
                                    indication = null,
                                    enabled = enabled,
                                    role = Role.Button,
                                    onClick = { onDigit(cell) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                cell,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.W600,
                                // color = MaterialTheme.colorScheme.onSurface,
                                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
    } // Measured keypad, same row order and callbacks.
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockedVaultView(
    controller: HiddenVaultController,
    conversationsProvider: () -> List<Conversation>,
    onOpenConversation: (String) -> Unit,
    onBack: (() -> Unit)?,
    isDark: Boolean,
    onLock: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val owner = controller.sessionOwner
    var removalCandidate by remember(controller, owner) { mutableStateOf<String?>(null) }
    var removalBusy by remember(controller, owner) { mutableStateOf(false) }
    var removalError by remember(controller, owner) { mutableStateOf<String?>(null) }
    removalCandidate?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!removalBusy) removalCandidate = null },
            title = { Text("Remove chat protection?") },
            text = { Text(removalError ?: "This chat will return to the inbox without vault protection.") },
            dismissButton = { TextButton(enabled = !removalBusy, onClick = { removalCandidate = null }) { Text("Cancel") } },
            confirmButton = { TextButton(enabled = !removalBusy, onClick = {
                if (removalBusy || controller.sessionOwner != owner || !controller.isUnlocked || controller.isDecoyMode) return@TextButton
                removalBusy = true
                scope.launch {
                    try {
                        if (controller.sessionOwner != owner || !controller.isUnlocked || controller.isDecoyMode) return@launch
                        controller.removeConversationLock(id)
                        if (controller.sessionOwner == owner) removalCandidate = null
                    }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { if (controller.sessionOwner == owner) removalError = "Could not remove protection. Try again." }
                    finally { removalBusy = false }
                }
            }) { Text(if (removalBusy) "Saving..." else "Remove protection") } },
        )
    }
    val lockedIds = controller.lockedConversationIds
    val lockedConversations = conversationsProvider().filter { lockedIds.contains(it.id) }.distinctBy { it.id }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = "Hidden Vault"; isTraversalGroup = true },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.unlock, semanticLabel = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (controller.isDecoyMode) "Decoy Vault" else "Hidden Vault",
                            fontWeight = FontWeight.W700,
                        )
                    }
                },
                navigationIcon = { onBack?.let { ChatyBackButton(onPressed = it) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = onLock) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.lock, semanticLabel = "Lock Vault")
                    }
                },
            )
        },
    ) { padding ->
        if (controller.registryError != null || controller.registryLoading) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(if (controller.registryLoading) "Loading protected chats..." else controller.registryError.orEmpty())
                TextButton(onClick = { scope.launch { controller.retryLoad() } }, enabled = !controller.registryLoading) { Text("Retry") }
            }
        } else if (lockedConversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (controller.isDecoyMode) "No active chats in decoy mode" else "No Hidden Conversations",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "To protect a conversation, select it from the inbox and choose \"Lock to Vault\". Vault protection is an access lock, not a separate encryption guarantee.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(lockedConversations, key = { it.id }, contentType = { "vault-conversation" }) { conversation ->
                    Card(
                        onClick = { if (!removalBusy && controller.sessionOwner == owner && controller.isUnlocked && !controller.isDecoyMode &&
                            conversation.id in controller.lockedConversationIds) { controller.recordActivity(); onOpenConversation(conversation.id) } },
                        enabled = !removalBusy && controller.isUnlocked && !controller.isDecoyMode,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // AppAvatar equivalent
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    conversation.avatarInitials?.take(4)
                                        ?: conversation.title.firstOrNull()?.uppercase() ?: "?",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    conversation.title.take(256).ifBlank { "Unnamed conversation" },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.W700,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Protected conversation",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(enabled = !removalBusy && controller.isUnlocked && !controller.isDecoyMode, onClick = {
                                if (removalBusy || controller.sessionOwner != owner || !controller.isUnlocked || controller.isDecoyMode ||
                                    conversation.id !in controller.lockedConversationIds) return@IconButton
                                controller.recordActivity()
                                removalError = null
                                removalCandidate = conversation.id
                            }) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.unlock,
                                    semanticLabel = "Remove vault protection from ${conversation.title.take(256).ifBlank { "unnamed conversation" }}",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Previous click-state source retained for review:
//     var removalCandidate by remember(controller) { mutableStateOf<String?>(null) }
//     var removalBusy by remember(controller) { mutableStateOf(false) }
//     var removalError by remember(controller) { mutableStateOf<String?>(null) }
//                     try { controller.removeConversationLock(id); removalCandidate = null }
//                         onClick = { if (controller.isUnlocked && !controller.isDecoyMode) { controller.recordActivity(); onOpenConversation(conversation.id) } },
//                             IconButton(onClick = {

// Previous click-state source retained for review:
//         if (isAuthenticating) return
//         if (isAuthenticating || !credentialReady || enteredPin.length != pinLength) return
//         if (digit.length != 1 || digit[0] !in '0'..'9' || isAuthenticating || !credentialReady || enteredPin.length >= pinLength) return
//         if (isAuthenticating || enteredPin.isEmpty()) return

// Previous visual source retained:
//             shakeOffsetPx = { sin(shake.value * Math.PI * 6) * 12 * 3 }, // 12 logical px shake
// @OptIn(ExperimentalMaterial3Api::class)
//             // PIN Indicator dots with shake animation
//             Row(
//     Column(Modifier.widthIn(max = 360.dp)) {
//                                 .size(72.dp)
//                                 .size(72.dp)
//                                 .size(72.dp)
