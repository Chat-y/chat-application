package com.example.chat.ui.settings.privacy

import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ForwardToInbox
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.domain.models.PrivacyPreferences
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.layout.imePadding

private val AudienceOptions = listOf("Everyone", "My Contacts", "My Contacts Except\u2026", "Nobody")

/** Port of showWhoCanCallMeDialog (privacy_center_screen.dart), reused by the chats home menu. */
@Composable
fun WhoCanCallMeDialog(
    preferences: ChatyPreferencesController,
    onDismiss: () -> Unit,
    loadCallPolicy: (suspend () -> com.example.chat.data.api.ApiContactsClient.CallPolicy)? = null,
    saveCallPolicy: (suspend (String, List<String>?) -> com.example.chat.data.api.ApiContactsClient.CallPolicy)? = null,
) {
    val colors = LocalAppColors.current
    val prefs = preferences.privacy
    if (loadCallPolicy != null && saveCallPolicy != null) {
        // Route the shared dialog through the same server plane as the privacy center.
        var policy by remember { mutableStateOf<com.example.chat.data.api.ApiContactsClient.CallPolicy?>(null) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(loadCallPolicy) { policy = runCatching { loadCallPolicy() }.getOrNull() }
        val current = policy
        if (current != null) {
            ServerWhoCanCallMeDialog(
                current = current,
                busy = busy,
                onPick = { label ->
                    val picked = PrivacyOptionsLogic.audiencePolicy(label) ?: return@ServerWhoCanCallMeDialog
                    if (busy) return@ServerWhoCanCallMeDialog
                    busy = true
                    scope.launch {
                        try {
                            val saved = saveCallPolicy(picked, null)
                            if (saved.policy == picked) {
                                policy = saved
                                PrivacyOptionsLogic.audiencePreferenceValue(saved.policy)?.let {
                                    preferences.updatePrivacy(preferences.privacy.copy(whoCanCallMe = it), logTitle = "Who can call me")
                                }
                                onDismiss()
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { }
                        finally { busy = false }
                    }
                },
                onDismiss = onDismiss,
            )
        }
        return
    }
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        modifier = Modifier.widthIn(280.dp, 560.dp),
        onDismissRequest = onDismiss,
        title = { Text("Who can call me?") },
        text = {
            Column(Modifier.selectableGroup()) {
                Text("The contacts-except option needs an existing exception list.")
                AudienceOptions.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = opt == prefs.whoCanCallMe,
                            enabled = opt != "My Contacts Except…" || prefs.whoCanCallMeExceptions.isNotEmpty(),
                            role = Role.RadioButton,
                            onClick = { preferences.updatePrivacy(preferences.privacy.copy(whoCanCallMe = opt), logTitle = "Who can call me"); onDismiss() },
                        ).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = opt == prefs.whoCanCallMe,
                            onClick = null,
                            enabled = opt != "My Contacts Except…" || prefs.whoCanCallMeExceptions.isNotEmpty(),
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Text(opt, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private sealed interface PrivacyModal {
    object WhoCanCall : PrivacyModal
    object LastSeen : PrivacyModal
    object Blocked : PrivacyModal
    class Custom(val title: String) : PrivacyModal
    object Recovery : PrivacyModal
    object Reset : PrivacyModal
    object History : PrivacyModal
}

/** Last-good server privacy planes per account generation (stale-while-revalidate). */
private object PrivacyPlaneCache {
    var generation: Long = -1L
    var presence: com.example.chat.data.api.ApiPresenceClient.PresencePrivacy? = null
    var presenceLoaded: Boolean = false
    var callPolicy: com.example.chat.data.api.ApiContactsClient.CallPolicy? = null
    var blocked: List<com.example.chat.data.api.ApiContactsClient.BlockedEntry>? = null
    var blockedLoaded: Boolean = false
    var syncedAtMs: Long? = null
}

/** Stable row styling so the hoisted row composables can skip recomposition. */
private class PrivacyRowStyle(
    val accent: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val surface: Color,
    val borderSubtle: Color,
    val shadow: Color,
    val error: Color,
    val isDark: Boolean,
    val fs: Float,
)

private val PrivacyChannelOffIcon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.channelOff)
private val PrivacyRowIconShape = RoundedCornerShape(11.dp)
private val PrivacyCardShape = RoundedCornerShape(20.dp)

@Composable
private fun PrivacyRow2(
    style: PrivacyRowStyle,
    view: android.view.View,
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String?,
    iconTint: Color?,
    animate: Boolean,
    onTap: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Box(if (animate) Modifier.springInteractiveScale(0.98f) else Modifier) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClickLabel = "Open") { ChatyHapticsPort.selection(view); onTap() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(style.accent.copy(alpha = 0.12f), PrivacyRowIconShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = iconTint ?: style.accent, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = style.primaryText, fontSize = (14.5f * style.fs).sp, fontWeight = FontWeight.W600)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = style.secondaryText, fontSize = (14f * style.fs).sp)
            }
            if (badge != null) {
                Text(
                    badge,
                    Modifier.background(style.accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    color = style.secondaryText, fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun PrivacySwitchRow(
    style: PrivacyRowStyle,
    view: android.view.View,
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Boolean,
    reduceMotion: Boolean,
    enabled: Boolean = true,
    onChanged: (Boolean) -> Unit,
) {
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(flash) { if (flash) { kotlinx.coroutines.delay(1500); flash = false } }
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { stateDescription = if (value) "On" else "Off" }
            .toggleable(value = value, role = Role.Switch, enabled = enabled) {
                ChatyHapticsPort.selection(view)
                view.announceForAccessibility(title + " " + if (it) "on" else "off")
                if (!reduceMotion) flash = true
                onChanged(it)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = style.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = style.primaryText, fontSize = (16f * style.fs).sp, fontWeight = FontWeight.W600)
            Text(subtitle, color = style.secondaryText, fontSize = (14f * style.fs).sp)
        }
        if (flash) {
            Icon(Icons.Rounded.Check, null, tint = style.accent, modifier = Modifier.size(24.dp).clearAndSetSemantics { })
        } else {
            Switch(checked = value, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@Composable
private fun PrivacyChevronRow(
    style: PrivacyRowStyle,
    view: android.view.View,
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    iconTint: Color? = null,
    animate: Boolean = true,
    onTap: () -> Unit,
) {
    PrivacyRow2(style, view, icon, title, subtitle, badge, iconTint, animate, onTap) {
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = style.accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PrivacySectionDivider(style: PrivacyRowStyle, label: String) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 6.dp)) {
        Text(
            label, Modifier.fillMaxWidth().semantics { heading() }, color = style.accent, fontWeight = FontWeight.W700, fontSize = 16.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        val dividerBrush = remember(style.accent) { Brush.horizontalGradient(listOf(style.accent.copy(alpha = 0f), style.accent.copy(alpha = 0.7f), style.accent.copy(alpha = 0f))) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(dividerBrush))
    }
}

@Composable
private fun PrivacyCardContainer(style: PrivacyRowStyle, lowRam: Boolean, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(
                if (style.isDark || lowRam) 0.dp else 4.dp, PrivacyCardShape,
                ambientColor = style.shadow, spotColor = style.shadow,
            )
            .background(style.surface, PrivacyCardShape)
            .border(0.9.dp, style.borderSubtle, PrivacyCardShape)
            .clip(PrivacyCardShape),
    ) { content() }
}

/**
 * Port of PrivacyCenterScreen. Dart's `_runFlow`, `_showSecretPhraseSetupDialog` and the lock-capability
 * fields are declared (ignore_for_file: unused_*) but never reachable from build(), so they are not ported
 * as UI (the lock flow is in SecurityCenterScreen). UNCOMPILED (Compose).
 */
@Composable
fun PrivacyCenterScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    // Server privacy planes (research 5.2/5.3). Null = wire legacy local
    // behaviour (pre-plane server); the server-backed rows then keep the
    // old local-preference presentation.
    loadPresencePrivacy: (suspend () -> com.example.chat.data.api.ApiPresenceClient.PresencePrivacy?)? = null,
    savePresencePrivacy: (suspend (String?, String?, List<String>?) -> com.example.chat.data.api.ApiPresenceClient.PresencePrivacy?)? = null,
    loadCallPolicy: (suspend () -> com.example.chat.data.api.ApiContactsClient.CallPolicy)? = null,
    saveCallPolicy: (suspend (String, List<String>?) -> com.example.chat.data.api.ApiContactsClient.CallPolicy)? = null,
    loadBlocked: (suspend () -> List<com.example.chat.data.api.ApiContactsClient.BlockedEntry>)? = null,
    unblockUser: (suspend (String) -> Unit)? = null,
) {
    val config = LocalThemeConfig.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val lowRam = remember { (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true }
    val window = remember(context) {
        var c: android.content.Context? = context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        (c as? android.app.Activity)?.window
    }
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    val accountGeneration = preferences.scopeGeneration
    var modal by remember(preferences, accountGeneration) { mutableStateOf<PrivacyModal?>(null) }
    var retentionTitle by remember(preferences, accountGeneration) { mutableStateOf<String?>(null) }
    var retentionChange by remember(preferences, accountGeneration) { mutableStateOf<(() -> Unit)?>(null) }
    var offline by remember { mutableStateOf(false) }
    var tipDismissed by rememberSaveable { mutableStateOf(false) }
    var showMoreOptions by rememberSaveable { mutableStateOf(false) }
    var syncedAtMs by remember(preferences, accountGeneration) { mutableStateOf<Long?>(if (PrivacyPlaneCache.generation == accountGeneration) PrivacyPlaneCache.syncedAtMs else null) }
    DisposableEffect(context) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { offline = false }
            override fun onLost(network: android.net.Network) { offline = true }
        }
        cm?.registerDefaultNetworkCallback(cb)
        onDispose { cm?.unregisterNetworkCallback(cb) }
    }
    val secureScreen = preferences.security.protectFromScreenshots || modal is PrivacyModal.Blocked
    DisposableEffect(window, secureScreen) {
        if (secureScreen) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secureScreen) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // Server privacy planes (research 5.2/5.3). null after load = plane
    // unavailable; rows then fail closed to the legacy local behaviour.
    var presencePrivacy by remember(preferences, accountGeneration) { mutableStateOf<com.example.chat.data.api.ApiPresenceClient.PresencePrivacy?>(if (PrivacyPlaneCache.generation == accountGeneration) PrivacyPlaneCache.presence else null) }
    var presenceLoaded by remember(preferences, accountGeneration) { mutableStateOf(PrivacyPlaneCache.generation == accountGeneration && PrivacyPlaneCache.presenceLoaded) }
    var serverCallPolicy by remember(preferences, accountGeneration) { mutableStateOf<com.example.chat.data.api.ApiContactsClient.CallPolicy?>(if (PrivacyPlaneCache.generation == accountGeneration) PrivacyPlaneCache.callPolicy else null) }
    var blockedUsers by remember(preferences, accountGeneration) { mutableStateOf<List<com.example.chat.data.api.ApiContactsClient.BlockedEntry>?>(if (PrivacyPlaneCache.generation == accountGeneration) PrivacyPlaneCache.blocked else null) }
    var callBusy by remember(preferences, accountGeneration) { mutableStateOf(false) }
    var presenceBusy by remember(preferences, accountGeneration) { mutableStateOf(false) }
    var unblockBusy by remember(preferences, accountGeneration) { mutableStateOf(false) }
    var presenceError by remember(preferences, accountGeneration) { mutableStateOf<String?>(null) }
    var callError by remember(preferences, accountGeneration) { mutableStateOf<String?>(null) }
    var blockedError by remember(preferences, accountGeneration) { mutableStateOf<String?>(null) }
    val currentPresenceLoader by rememberUpdatedState(loadPresencePrivacy)
    val currentCallLoader by rememberUpdatedState(loadCallPolicy)
    val currentBlockedLoader by rememberUpdatedState(loadBlocked)
    suspend fun reloadPrivacyPlanes() = coroutineScope {
        currentPresenceLoader?.let { loader -> launch {
            try {
                val loaded = loader()
                if (preferences.scopeGeneration == accountGeneration) {
                    presencePrivacy = loaded; presenceError = null; presenceLoaded = true
                    val nowMs = System.currentTimeMillis(); syncedAtMs = nowMs
                    PrivacyPlaneCache.generation = accountGeneration; PrivacyPlaneCache.presence = loaded
                    PrivacyPlaneCache.presenceLoaded = true; PrivacyPlaneCache.syncedAtMs = nowMs
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (preferences.scopeGeneration == accountGeneration) { presenceError = "Could not load. Tap to retry."; presenceLoaded = true }
            }
        } }
        currentCallLoader?.let { loader -> launch {
            try {
                val loaded = loader()
                if (preferences.scopeGeneration == accountGeneration) {
                    serverCallPolicy = loaded; callError = null
                    val nowMs = System.currentTimeMillis(); syncedAtMs = nowMs
                    PrivacyPlaneCache.generation = accountGeneration; PrivacyPlaneCache.callPolicy = loaded
                    PrivacyPlaneCache.syncedAtMs = nowMs
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: com.example.chat.data.api.ApiContactsClient.PlaneUnavailable) {
                if (preferences.scopeGeneration == accountGeneration) callError = "Unavailable on this server. Tap to retry."
            } catch (_: Exception) {
                if (preferences.scopeGeneration == accountGeneration) callError = "Could not load. Tap to retry."
            }
        } }
        currentBlockedLoader?.let { loader -> launch {
            try {
                val loaded = loader()
                if (preferences.scopeGeneration == accountGeneration) {
                    blockedUsers = loaded; blockedError = null
                    val nowMs = System.currentTimeMillis(); syncedAtMs = nowMs
                    PrivacyPlaneCache.generation = accountGeneration; PrivacyPlaneCache.blocked = loaded
                    PrivacyPlaneCache.blockedLoaded = true; PrivacyPlaneCache.syncedAtMs = nowMs
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: com.example.chat.data.api.ApiContactsClient.PlaneUnavailable) {
                if (preferences.scopeGeneration == accountGeneration) blockedError = "Unavailable on this server. Tap to retry."
            } catch (_: Exception) {
                if (preferences.scopeGeneration == accountGeneration) blockedError = "Could not load. Tap to retry."
            }
        } }
    }
    LaunchedEffect(preferences, accountGeneration, preferences.preferencesReady) {
        if (preferences.preferencesReady) reloadPrivacyPlanes()
    }

    val accent = remember(config.accentColor) { Color(config.accentColor) }
    val bg = remember(config.backgroundColor) { Color(config.backgroundColor) }
    val primaryText = remember(config.primaryTextColor) { Color(config.primaryTextColor) }
    val secondaryText = remember(config.secondaryTextColor) { Color(config.secondaryTextColor) }
    val fs = config.fontScale.toFloat()
    val isDark = config.isDark
    val style = remember(accent, primaryText, secondaryText, appColors, isDark, fs) {
        PrivacyRowStyle(accent, primaryText, secondaryText, appColors.surface, appColors.borderSubtle, appColors.shadow, appColors.error, isDark, fs)
    }

    fun snack(msg: String, long: Boolean = false) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg, duration = if (long) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short) } }
    fun export() {
        runCatching {
            val json = org.json.JSONObject()
            preferences.privacy.toMap().forEach { (k, v) -> json.put(k, v) }
            val send = android.content.Intent(android.content.Intent.ACTION_SEND)
            send.type = "application/json"
            send.putExtra(android.content.Intent.EXTRA_TEXT, json.toString(2))
            send.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(android.content.Intent.createChooser(send, "Export privacy settings"))
        }.onFailure { snack("Couldn't export right now.", long = true) }
    }

    @Suppress("UNUSED_EXPRESSION") version
    val p = preferences.privacy

    Column(Modifier.fillMaxSize().background(bg)) {
        // AppBar: back button in 8dp padding, title 20*fontScale w700 letterSpacing -0.3.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            Text(
                "Privacy", color = primaryText, fontSize = (20f * fs).sp,
                fontWeight = FontWeight.W700, letterSpacing = (-0.3).sp,
            )
        }
        // R2/R3: grouped lists capped at 720dp centered (approved plan 8 Nov 2026).
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
                val privacyCheckup = buildList {
                    if (p.freezeLastSeen) add("Freeze last seen is on: nobody can see your live last seen, whatever the audience below.")
                    if (!p.readReceipts) add("Read receipts are off: you won't see read receipts from others either.")
                    if (p.antiViewOnce || p.antiDeleteMessages || p.antiDeleteStatus || p.antiDisappearingMessages) add("Retention features keep local copies of content the sender removed.")
                    if (p.disableForwardedLabel) add("The Forwarded tag is hidden when you resend messages.")
                    if (p.hidePrivacyOption) add("The Privacy entry is hidden from the Home menu. You can still reach it from Settings.")
                }
                if (privacyCheckup.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth()
                            .semantics(mergeDescendants = true) { }
                            .background(appColors.error.copy(alpha = if (isDark) 0.18f else 0.12f), RoundedCornerShape(16.dp))
                            .border(1.dp, appColors.error.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = appColors.error, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Privacy checkup", color = primaryText, fontSize = (13.5f * fs).sp, fontWeight = FontWeight.W700)
                            privacyCheckup.forEach { w ->
                                Text("\u2022 $w", color = primaryText, fontSize = (12.5f * fs).sp, lineHeight = (12.5f * fs * 1.5f).sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                } else if (!tipDismissed) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(accent.copy(alpha = if (isDark) 0.18f else 0.12f), RoundedCornerShape(16.dp))
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Message delays? Check your connection and Android battery restrictions. Resetting privacy choices does not repair message delivery.",
                            Modifier.weight(1f), color = primaryText, fontSize = (12.5f * fs).sp, lineHeight = (12.5f * fs * 1.5f).sp,
                        )
                        IconButton(onClick = { tipDismissed = true }) { Icon(Icons.Rounded.Close, "Dismiss tip", tint = secondaryText, modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (offline) {
                    Text("You're offline. Showing the last synced values.", Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = secondaryText, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                }
                PrivacyCardContainer(style, lowRam) {
                    val syncedLabel = syncedAtMs?.let { " · Updated " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: ""
                    Text("Last seen: ${presencePrivacy?.let { PrivacyOptionsLogic.audienceLabel(it.lastSeenPolicy) } ?: p.hideLastSeenAudience} · Calls: ${serverCallPolicy?.let { PrivacyOptionsLogic.audienceLabel(it.policy) } ?: p.whoCanCallMe}${blockedUsers?.let { " · " + it.size + " blocked" } ?: ""}$syncedLabel", Modifier.padding(16.dp), color = secondaryText, fontSize = 14.sp)
                    PrivacySectionDivider(style, "Privacy")
                    Text("Local switches affect this device. Last seen and call policies use server confirmation when supported.", Modifier.padding(16.dp), color = secondaryText, fontSize = 14.sp)
                    if (loadPresencePrivacy != null && presenceLoaded) {
                        PrivacyChevronRow(style, view,
                            Icons.Rounded.Visibility,
                            "Last seen & online",
                            presenceError ?: presencePrivacy?.let { PrivacyOptionsLogic.audienceLabel(it.lastSeenPolicy) + (if (p.freezeLastSeen) " · Frozen" else "") } ?: "Unavailable on this server",
                            badge = "Server",
                        ) {
                            if (presenceError != null) scope.launch { reloadPrivacyPlanes() }
                            else if (presencePrivacy != null) modal = PrivacyModal.LastSeen else snack("Unavailable on this server", long = true)
                        }
                    }
                    PrivacyChevronRow(style, view,
                        Icons.Outlined.Call,
                        "Who can call me?",
                        callError ?: serverCallPolicy?.let { pol -> PrivacyOptionsLogic.audienceLabel(pol.policy)?.let { lbl -> if (pol.policy == "contacts_except") "$lbl (${pol.exceptions.size})" else lbl } } ?: p.whoCanCallMe,
                        badge = if (loadCallPolicy != null) "Server" else null,
                        iconTint = if ((serverCallPolicy?.policy ?: "everyone") != "everyone") style.error else null,
                    ) { if (loadCallPolicy != null && (callError != null || serverCallPolicy == null)) scope.launch { reloadPrivacyPlanes() } else modal = PrivacyModal.WhoCanCall }
                    if (loadBlocked != null) {
                        PrivacyChevronRow(style, view,
                            Icons.Rounded.Block,
                            "Blocked contacts",
                            blockedError ?: blockedUsers?.let { if (it.isEmpty()) "None" else "${it.size} blocked" } ?: "Loading...",
                            badge = "Server",
                        ) { if (blockedError != null || blockedUsers == null) scope.launch { reloadPrivacyPlanes() } else modal = PrivacyModal.Blocked }
                    }
                    // Scoped custom controls have no publication consumer yet. Preserve saved keys, do not promise enforcement.
                    // PrivacyChevronRow(style, view, Icons.Outlined.AdminPanelSettings, "Custom privacy", "View all") { modal = PrivacyModal.Custom("Custom") }
                    PrivacySwitchRow(style, view, Icons.Rounded.AcUnit, "Freeze last seen", "Stops publishing your live last seen. Applies right away.", p.freezeLastSeen, reduceMotion) {
                        preferences.updatePrivacy(p.copy(freezeLastSeen = it), logTitle = "Freeze last seen")
                    }
                    PrivacySwitchRow(style, view, Icons.Outlined.RemoveRedEye, "Anti-view once", "Keep view-once content locally. Affects the sender too: respect their privacy and consent.", p.antiViewOnce, reduceMotion) {
                        if (it) {
                            retentionTitle = "Anti-view once"
                            retentionChange = { preferences.updatePrivacy(preferences.privacy.copy(antiViewOnce = true), logTitle = "Anti-view once") }
                        } else preferences.updatePrivacy(preferences.privacy.copy(antiViewOnce = false), logTitle = "Anti-view once")
                    }
                    PrivacySwitchRow(style, view, Icons.Rounded.ForwardToInbox, "Disable forwarded tag", "Resend messages without the Forwarded tag. Affects the receiver too.", p.disableForwardedLabel, reduceMotion) {
                        if (it) {
                            retentionTitle = "Disable forwarded tag"
                            retentionChange = { preferences.updatePrivacy(preferences.privacy.copy(disableForwardedLabel = true), logTitle = "Disable forwarded tag") }
                        } else preferences.updatePrivacy(preferences.privacy.copy(disableForwardedLabel = false), logTitle = "Disable forwarded tag")
                    }

                    PrivacySectionDivider(style, "Receipts and presence")
                    PrivacySwitchRow(style, view, Icons.Rounded.DoneAll, "Read receipts", "Let contacts know when you have read their messages.", p.readReceipts, reduceMotion) {
                        preferences.updatePrivacy(preferences.privacy.copy(readReceipts = it), logTitle = "Read receipts")
                    }
                    PrivacySwitchRow(style, view, Icons.Outlined.MarkChatRead, "Typing indicators", "Let contacts know when you are typing.", p.typingIndicators, reduceMotion) {
                        preferences.updatePrivacy(preferences.privacy.copy(typingIndicators = it), logTitle = "Typing indicators")
                    }
                    PrivacySwitchRow(style, view, Icons.Outlined.NotificationsActive, "Recording indicators", "Let contacts know when you are recording a voice message.", p.recordingIndicators, reduceMotion) {
                        preferences.updatePrivacy(preferences.privacy.copy(recordingIndicators = it), logTitle = "Recording indicators")
                    }
                    PrivacySwitchRow(style, view, Icons.Rounded.DoneAll, "Show blue ticks after reply", "Contacts only see blue ticks after you reply.", p.showBlueTicksAfterReply, reduceMotion) {
                        preferences.updatePrivacy(p.copy(showBlueTicksAfterReply = it), logTitle = "Show blue ticks after reply")
                    }

                    PrivacySectionDivider(style, "Home menu options")
                    PrivacySwitchRow(style, view, Icons.Outlined.Lock, "Hide the privacy option", "Hide the Privacy entry from the Home Screen Options menu. You can still open it from Settings.", p.hidePrivacyOption, reduceMotion) {
                        preferences.updatePrivacy(p.copy(hidePrivacyOption = it), logTitle = "Hide the privacy option")
                    }
                    PrivacySwitchRow(style, view, Icons.Rounded.History, "Hide the update option", "Hide the Chaty Update entry from the Home Screen Options menu. App updates are not affected.", p.hideUpdateOption, reduceMotion) {
                        preferences.updatePrivacy(p.copy(hideUpdateOption = it), logTitle = "Hide the update option")
                    }

                    PrivacySectionDivider(style, "Status")
                    PrivacySwitchRow(style, view, PrivacyChannelOffIcon, "Disable channels", "Hide Channels on the Status screen. Channels you follow are kept.", p.disableChannels, reduceMotion) {
                        preferences.updatePrivacy(p.copy(disableChannels = it), logTitle = "Disable channels")
                    }
                    PrivacySwitchRow(style, view, Icons.Rounded.VisibilityOff, "Hide view status", "Do not tell contacts that you have viewed their status.", p.hideViewStatus, reduceMotion) {
                        preferences.updatePrivacy(p.copy(hideViewStatus = it), logTitle = "Hide view status")
                    }
                    PrivacySwitchRow(style, view, Icons.Outlined.DeleteForever, "Anti-delete status", "Keep local copies of deleted status and stories. Affects the sender too: respect their privacy.", p.antiDeleteStatus, reduceMotion) {
                        if (it) {
                            retentionTitle = "Anti-delete status"
                            retentionChange = { preferences.updatePrivacy(preferences.privacy.copy(antiDeleteStatus = true), logTitle = "Anti-delete status") }
                        } else preferences.updatePrivacy(preferences.privacy.copy(antiDeleteStatus = false), logTitle = "Anti-delete status")
                    }
                    PrivacySwitchRow(style, view, Icons.Outlined.NotificationImportant, "Status revoke alert", "Show an immediate notification when one of your contacts deletes their status/Story.", p.statusRevocationAlert, reduceMotion) {
                        preferences.updatePrivacy(p.copy(statusRevocationAlert = it), logTitle = "Status revoke alert")
                    }

                    PrivacySectionDivider(style, "Messages")
                    PrivacySwitchRow(style, view, Icons.Outlined.MarkChatRead, "Hide the first message", "When this feature is enabled, the first message in hidden chats will not be shown, and you can only read it after opening the chat." + if (preferences.security.hiddenConversationIds.isEmpty()) " No hidden chats yet." else "", p.hideFirstMessage, reduceMotion, enabled = p.hideFirstMessage || preferences.security.hiddenConversationIds.isNotEmpty()) {
                        preferences.updatePrivacy(p.copy(hideFirstMessage = it), logTitle = "Hide the first message")
                    }
                    PrivacySwitchRow(style, view, Icons.Outlined.Timer, "Anti-disappearing messages", "If disappearing messages is enabled, messages will not delete from your side. Affects the sender too.", p.antiDisappearingMessages, reduceMotion) {
                        if (it) {
                            retentionTitle = "Anti-disappearing messages"
                            retentionChange = { preferences.updatePrivacy(preferences.privacy.copy(antiDisappearingMessages = true), logTitle = "Anti-disappearing messages") }
                        } else preferences.updatePrivacy(preferences.privacy.copy(antiDisappearingMessages = false), logTitle = "Anti-disappearing messages")
                    }
                    PrivacySwitchRow(style, view, Icons.Rounded.EditNote, "Show edited message", "Show that a message was edited when that information is available.", p.showEditedMessage, reduceMotion) {
                        preferences.updatePrivacy(p.copy(showEditedMessage = it), logTitle = "Show edited message")
                    }
                    PrivacySwitchRow(style, view, Icons.Outlined.Delete, "Anti-delete messages", "Keep a local copy when a sender deletes a message. Affects the sender too: this does not change their copy.", p.antiDeleteMessages, reduceMotion) {
                        if (it) {
                            retentionTitle = "Anti-delete messages"
                            retentionChange = { preferences.updatePrivacy(preferences.privacy.copy(antiDeleteMessages = true), logTitle = "Anti-delete messages") }
                        } else preferences.updatePrivacy(preferences.privacy.copy(antiDeleteMessages = false), logTitle = "Anti-delete messages")
                    }
                    PrivacySwitchRow(style, view, Icons.Outlined.NotificationsActive, "Message revoke alert", "Show a notification in the status bar when someone deletes a message sent to you.", p.messageRevokeAlert, reduceMotion) {
                        preferences.updatePrivacy(p.copy(messageRevokeAlert = it), logTitle = "Message revoke alert")
                    }
                    PrivacySwitchRow(style, view, Icons.Outlined.DeleteSweep, "Delete for everyone", "Request deletion for everyone when the server allows it. Time limits still apply.", p.deletionOfEveryone, reduceMotion) {
                        preferences.updatePrivacy(p.copy(deletionOfEveryone = it), logTitle = "Delete for everyone")
                    }

                    PrivacySectionDivider(style, "More options")
                    PrivacyChevronRow(style, view, Icons.Outlined.AccessTime, "Advanced", "Deleted media time and unviewed chat counters") { showMoreOptions = !showMoreOptions }
                    if (showMoreOptions) {
                        PrivacySwitchRow(style, view, Icons.Outlined.AccessTime, "Deleted media time", "Find out the exact moment a message or status was deleted.", p.deletedMediaTime, reduceMotion) {
                            preferences.updatePrivacy(p.copy(deletedMediaTime = it), logTitle = "Deleted media time")
                        }
                        PrivacySwitchRow(style, view, Icons.Outlined.MarkChatUnread, "Unviewed chats", "When enabled the unread counter stays after you open a chat, until you read the messages.", p.unviewedChats, reduceMotion) {
                            preferences.updatePrivacy(p.copy(unviewedChats = it), logTitle = "Unviewed chats")
                        }
                    }
                    // Scoped custom controls have no publication consumer yet. Preserve saved keys, do not promise enforcement.
                    // PrivacyChevronRow(style, view, Icons.Outlined.Contacts, "Contacts", "Change privacy settings") { modal = PrivacyModal.Custom("Contacts") }
                    // PrivacyChevronRow(style, view, Icons.Outlined.Groups, "Groups", "Change privacy settings") { modal = PrivacyModal.Custom("Groups") }
                }
                Spacer(Modifier.height(16.dp))
                PrivacyCardContainer(style, lowRam) {
                    PrivacySectionDivider(style, "Danger zone")
                    // Recovery has no persistence or verification implementation. Keep the row disabled, not a false recovery promise.
                    // PrivacyChevronRow(style, view, Icons.Outlined.HelpOutline, "Recovery question", "Set secret question for password reset") { modal = PrivacyModal.Recovery }
                    PrivacyChevronRow(style, view, Icons.Rounded.RestartAlt, "Reset privacy", "Restore Chaty default privacy choices on this device", iconTint = style.error) {
                        modal = PrivacyModal.Reset
                    }
                }
                Spacer(Modifier.height(24.dp))
                PrivacyCardContainer(style, lowRam) {
                    PrivacySectionDivider(style, "Diagnostics")
                    OptionalCrashReportingChoice(style)
                    PrivacyChevronRow(style, view, Icons.Rounded.History, "Recent changes", "Privacy changes made on this device") { modal = PrivacyModal.History }
                    PrivacyChevronRow(style, view, Icons.Outlined.Share, "Export privacy settings", "Share a JSON copy of this device's privacy choices") { export() }
                }
                Spacer(Modifier.height(36.dp))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (retentionChange != null) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            modifier = Modifier.widthIn(280.dp, 560.dp),
            onDismissRequest = { retentionChange = null; retentionTitle = null },
            title = { Text("Enable ${retentionTitle.orEmpty()}?") },
            text = { Text(if (retentionTitle == "Disable forwarded tag") "New messages you resend will not show the Forwarded tag, so the receiver cannot tell the message was forwarded. This does not hide the forward count." else "This can keep a local copy of content the sender intended to disappear or delete. Respect their privacy and ask for consent. This does not change the sender's copy or guarantee that every message can be recovered.") },
            dismissButton = { TextButton(onClick = { retentionChange = null; retentionTitle = null }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = {
                if (preferences.scopeGeneration == accountGeneration) retentionChange?.invoke()
                retentionChange = null; retentionTitle = null
            }) { Text("Enable local retention") } },
        )
    }

    when (val m = modal) {
        PrivacyModal.WhoCanCall -> if (loadCallPolicy != null && saveCallPolicy != null && serverCallPolicy != null) {
            ServerWhoCanCallMeDialog(
                current = serverCallPolicy!!,
                busy = callBusy,
                onPick = { label ->
                    val policy = PrivacyOptionsLogic.audiencePolicy(label) ?: return@ServerWhoCanCallMeDialog
                    if (callBusy) return@ServerWhoCanCallMeDialog
                    callBusy = true
                    scope.launch {
                        try {
                            if (preferences.scopeGeneration != accountGeneration) return@launch
                            // exceptions omitted: the server keeps the existing list.
                            val saved = saveCallPolicy(policy, null)
                            if (preferences.scopeGeneration != accountGeneration) return@launch
                            if (saved.policy != policy) {
                                callError = "The server returned a different policy. Reloading."
                                reloadPrivacyPlanes()
                                return@launch
                            }
                            serverCallPolicy = saved
                            val serverLabel = PrivacyOptionsLogic.audiencePreferenceValue(saved.policy)
                                ?: throw IllegalStateException("Unsupported call policy")
                            preferences.updatePrivacy(preferences.privacy.copy(whoCanCallMe = serverLabel), logTitle = "Who can call me")
                            modal = null
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            snack("Couldn't save the call policy. Check your connection and retry.", long = true)
                        } finally {
                            callBusy = false
                        }
                    }
                },
                onDismiss = { modal = null },
            )
        } else {
            WhoCanCallMeDialog(preferences) { modal = null }
        }
        PrivacyModal.LastSeen -> presencePrivacy?.let { pp ->
            LastSeenOnlineDialog(
                current = pp,
                busy = presenceBusy,
                onSave = { lastSeenLabel, onlineLabel ->
                    val lsPolicy = PrivacyOptionsLogic.audiencePolicy(lastSeenLabel) ?: return@LastSeenOnlineDialog
                    val olPolicy = PrivacyOptionsLogic.onlinePolicy(onlineLabel) ?: return@LastSeenOnlineDialog
                    if (presenceBusy || savePresencePrivacy == null) return@LastSeenOnlineDialog
                    presenceBusy = true
                    scope.launch {
                        try {
                            if (preferences.scopeGeneration != accountGeneration) return@launch
                            // exceptions omitted: the server keeps the existing list.
                            val saved = savePresencePrivacy(lsPolicy, olPolicy, null)
                                ?: throw IllegalStateException("Presence policy was not saved")
                            if (preferences.scopeGeneration != accountGeneration) return@launch
                            if (saved.lastSeenPolicy != lsPolicy || saved.onlinePolicy != olPolicy) {
                                presenceError = "The server returned a different policy. Reloading."
                                reloadPrivacyPlanes()
                                return@launch
                            }
                            val savedLastSeen = PrivacyOptionsLogic.audiencePreferenceValue(saved.lastSeenPolicy)
                                ?: throw IllegalStateException("Unsupported last-seen policy")
                            val savedOnline = PrivacyOptionsLogic.onlinePreferenceValue(saved.onlinePolicy)
                                ?: throw IllegalStateException("Unsupported online policy")
                            presencePrivacy = saved
                            preferences.updatePrivacy(
                                preferences.privacy.copy(hideLastSeenAudience = savedLastSeen, hideOnlineAudience = savedOnline),
                                logTitle = "Last seen & online",
                            )
                            modal = null
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            snack("Couldn't save presence privacy. Check your connection and retry.", long = true)
                        } finally {
                            presenceBusy = false
                        }
                    }
                },
                onDismiss = { modal = null },
            )
        }
        PrivacyModal.Blocked -> blockedUsers?.let { list ->
            BlockedContactsDialog(
                blocked = list,
                busy = unblockBusy,
                onUnblock = { entry ->
                    if (unblockBusy || unblockUser == null) return@BlockedContactsDialog
                    unblockBusy = true
                    scope.launch {
                        try {
                            if (preferences.scopeGeneration != accountGeneration) return@launch
                            unblockUser(entry.userId)
                            if (preferences.scopeGeneration != accountGeneration) return@launch
                            blockedUsers = blockedUsers.orEmpty().filterNot { it.userId == entry.userId }
                            snack("${entry.displayName} unblocked")
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            snack("Couldn't unblock. Check your connection and retry.", long = true)
                        } finally {
                            unblockBusy = false
                        }
                    }
                },
                onDismiss = { modal = null },
            )
        }
        is PrivacyModal.Custom -> CustomPrivacyDialog(preferences, m.title) { modal = null }
        PrivacyModal.Recovery -> RecoveryQuestionDialog(
            onCancel = { modal = null },
            onSave = { modal = null; snack("Recovery questions are not available in this version.") },
        )
        PrivacyModal.Reset -> AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            modifier = Modifier.widthIn(280.dp, 560.dp),
            onDismissRequest = { modal = null },
            title = { Text("Reset privacy?") },
            text = { Text("Reset local Chaty privacy choices to Chaty defaults? Last seen returns to My Contacts, online to Everyone and read receipts turn on. Server policies, custom privacy and the separate crash-report choice are not changed. You can change them again at any time.") },
            dismissButton = { TextButton(onClick = { modal = null }) { Text("Cancel") } },
            confirmButton = {
                Button(onClick = {
                    val previous = preferences.privacy
                    preferences.updatePrivacy(PrivacyPreferences(), logTitle = "Reset Privacy")
                    modal = null
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar(
                            "Local privacy choices reset to Chaty defaults. Server policies are unchanged.",
                            actionLabel = "Undo",
                            duration = androidx.compose.material3.SnackbarDuration.Long,
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            preferences.updatePrivacy(previous, logTitle = "Reset Privacy (undo)")
                        }
                    }
                }) { Text("Reset privacy") }
            },
        )
        PrivacyModal.History -> {
            val entries = remember(version) { preferences.history().filter { it.key == "privacy" }.takeLast(50).reversed() }
            AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
                modifier = Modifier.widthIn(280.dp, 560.dp),
                onDismissRequest = { modal = null },
                title = { Text("Recent changes") },
                text = {
                    if (entries.isEmpty()) {
                        Text("No privacy changes recorded on this device.")
                    } else {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text("Newest first. Values stay on this device.", color = appColors.foregroundSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            entries.forEach { e ->
                                Text(e.title, fontWeight = FontWeight.W600)
                                Text(java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(e.timestampEpochMs)), color = appColors.foregroundSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { modal = null }) { Text("Done") } },
            )
        }
        null -> {}
    }
}

@Composable
private fun CustomPrivacyDialog(preferences: ChatyPreferencesController, title: String, onDone: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") tick
    @Composable
    fun Item(label: String, key: String) {
        val full = "${title}_$key"
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = preferences.gbBool(full, false), role = Role.Checkbox) { preferences.updateGbFeature(full, !preferences.gbBool(full, false)); tick++ },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f).padding(start = 8.dp))
            Checkbox(checked = preferences.gbBool(full, false), onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
        }
    }
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        modifier = Modifier.widthIn(280.dp, 560.dp),
        onDismissRequest = onDone,
        title = { Text("Custom privacy ($title)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("These saved choices apply to $title conversations.")
                Item("Hide blue ticks", "hide_blue_ticks")
                Item("Hide second tick", "hide_second_tick")
                Item("Hide blue microphone", "hide_blue_mic")
                // The "Broadcast" scope was planned but never wired; the dead
                // branch is removed so it cannot hide missing work.
                Item("Hide typing\u2026", "hide_typing")
                Item("Hide recording\u2026", "hide_recording")
            }
        },
        confirmButton = { Button(onClick = onDone) { Text("Done") } },
    )
}

@Composable
private fun RecoveryQuestionDialog(onCancel: () -> Unit, onSave: () -> Unit) {
    var q by remember { mutableStateOf("What is your favorite color?") }
    var a by remember { mutableStateOf("") }
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        modifier = Modifier.widthIn(280.dp, 560.dp),
        onDismissRequest = onCancel,
        title = { Text("Recovery Question") },
        text = {
            Column(Modifier.imePadding()) {
                Text("Set a security question to help recover access if you forget your credential.", fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(q, { q = it }, label = { Text("Security Question") })
                Spacer(Modifier.height(12.dp))
                // Sensitive answer: no autocorrect or personalised keyboard learning.
                OutlinedTextField(a, { a = it }, label = { Text("Your Answer") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Password))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        confirmButton = { Button(onClick = onSave) { Text("Save") } },
    )
}

/** Server-backed Who Can Call Me (research 5.3): same four WhatsApp options
 *  as the legacy dialog, saved to the contacts plane so calls are actually
 *  enforced server-side. Choosing "My Contacts Except…" keeps the existing
 *  exception list (exception editing comes with the contacts UI). */
@Composable
private fun ServerWhoCanCallMeDialog(
    current: com.example.chat.data.api.ApiContactsClient.CallPolicy,
    busy: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val selected = PrivacyOptionsLogic.audienceLabel(current.policy)
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        modifier = Modifier.widthIn(280.dp, 560.dp),
        onDismissRequest = onDismiss,
        title = { Text("Who can call me?") },
        text = {
            Column(Modifier.selectableGroup()) {
                if (busy) Text("Saving call policy...", Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                if (current.exceptions.isEmpty()) Text("Contact exceptions need an existing list. An exceptions editor is not available here.", color = colors.foregroundSecondary)
                PrivacyOptionsLogic.audienceLabels.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = opt == selected,
                            enabled = !busy && (opt != "My Contacts Except…" || current.exceptions.isNotEmpty()),
                            role = Role.RadioButton, onClick = { onPick(opt) }).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = opt == selected,
                            onClick = null,
                            enabled = !busy && (opt != "My Contacts Except…" || current.exceptions.isNotEmpty()),
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Text(opt, Modifier.padding(start = 8.dp))
                    }
                }
                if (selected == "My Contacts Except…") {
                    Text(
                        "Your existing exceptions stay in place.",
                        Modifier.padding(start = 16.dp, top = 6.dp),
                        color = colors.foregroundSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {},
    )
}

/** Server-backed Last seen & online (research 5.2): WhatsApp's exact shape -
 *  four audiences for last seen, two for online. */
@Composable
private fun LastSeenOnlineDialog(
    current: com.example.chat.data.api.ApiPresenceClient.PresencePrivacy,
    busy: Boolean,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    var lastSeen by rememberSaveable { mutableStateOf(PrivacyOptionsLogic.audienceLabel(current.lastSeenPolicy) ?: "My Contacts") }
    var online by rememberSaveable { mutableStateOf(PrivacyOptionsLogic.onlineLabel(current.onlinePolicy) ?: "Same as last seen") }
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        modifier = Modifier.widthIn(280.dp, 560.dp),
        onDismissRequest = onDismiss,
        title = { Text("Last seen & online") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                if (current.lastSeenExceptions.isEmpty()) Text("Contact exceptions need an existing list. An exceptions editor is not available here.", color = colors.foregroundSecondary)
                if (busy) Text("Saving privacy policy...", Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                Text("Who can see my last seen", color = colors.foregroundSecondary, fontSize = 12.sp, fontWeight = FontWeight.W600)
                PrivacyOptionsLogic.audienceLabels.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = opt == lastSeen,
                            enabled = !busy && (opt != "My Contacts Except…" || current.lastSeenExceptions.isNotEmpty()),
                            role = Role.RadioButton, onClick = { lastSeen = opt }).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = opt == lastSeen,
                            onClick = null,
                            enabled = !busy && (opt != "My Contacts Except…" || current.lastSeenExceptions.isNotEmpty()),
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Text(opt, Modifier.padding(start = 8.dp))
                    }
                }
                if (lastSeen == "My Contacts Except…") {
                    Text(
                        "Your existing exceptions stay in place.",
                        Modifier.padding(start = 16.dp, top = 2.dp),
                        color = colors.foregroundSecondary,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Who can see when I'm online", color = colors.foregroundSecondary, fontSize = 12.sp, fontWeight = FontWeight.W600)
                PrivacyOptionsLogic.onlineLabels.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = opt == online, enabled = !busy,
                            role = Role.RadioButton, onClick = { online = opt }).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = opt == online,
                            onClick = null, enabled = !busy,
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Text(opt, Modifier.padding(start = 8.dp))
                    }
                }
                if (lastSeen == "Nobody") {
                    Text(
                        "You won't see other people's last seen either.",
                        Modifier.padding(start = 16.dp, top = 6.dp),
                        color = colors.foregroundSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = !busy, onClick = { if (!busy) onSave(lastSeen, online) }) { Text(if (busy) "Saving..." else "Save") } },
    )
}

/** Server-backed blocked list (research 5.3): silent unblock, no leaks. */
@Composable
private fun BlockedContactsDialog(
    blocked: List<com.example.chat.data.api.ApiContactsClient.BlockedEntry>,
    busy: Boolean,
    onUnblock: (com.example.chat.data.api.ApiContactsClient.BlockedEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    var confirmEntry by remember { mutableStateOf<com.example.chat.data.api.ApiContactsClient.BlockedEntry?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    confirmEntry?.let { entry ->
        AlertDialog(
            shape = com.example.chat.ui.kit.ChatyModalShape,
            modifier = Modifier.widthIn(280.dp, 560.dp),
            onDismissRequest = { confirmEntry = null },
            title = { Text("Unblock ${entry.displayName}?") },
            text = { Text("This person may be able to contact you again, subject to your call and messaging settings.") },
            dismissButton = { TextButton(onClick = { confirmEntry = null }) { Text("Cancel") } },
            confirmButton = { TextButton(enabled = !busy, onClick = { confirmEntry = null; onUnblock(entry) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Unblock") } },
        )
    }
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        modifier = Modifier.widthIn(280.dp, 560.dp),
        onDismissRequest = onDismiss,
        title = { Text("Blocked contacts") },
        text = {
            if (blocked.isEmpty()) {
                Text("No blocked contacts.", color = colors.foregroundSecondary)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search blocked contacts") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    val shown = blocked.filter { query.isBlank() || it.displayName.contains(query, ignoreCase = true) || it.username.contains(query, ignoreCase = true) }
                    if (shown.isEmpty()) {
                        Text("No matches.", color = colors.foregroundSecondary)
                    }
                    shown.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.displayName, fontWeight = FontWeight.W600)
                                Text(if (entry.username.isBlank()) "Account identifier unavailable" else "@${entry.username}", color = colors.foregroundSecondary, fontSize = 14.sp)
                            }
                            TextButton(enabled = !busy, onClick = { if (!busy) confirmEntry = entry }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Unblock ${entry.displayName}") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}


@Composable
private fun OptionalCrashReportingChoice(style: PrivacyRowStyle) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("chaty_diagnostics", android.content.Context.MODE_PRIVATE) }
    // Read off the main thread; StrictMode-safe on first open.
    val stored by produceState<Boolean?>(initialValue = null, prefs) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { prefs.getBoolean("crash_reports_opt_in", false) }
    }
    var local by remember { mutableStateOf<Boolean?>(null) }
    val allowed = local ?: stored ?: false
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .semantics { stateDescription = if (allowed) "On" else "Off" }
            .toggleable(value = allowed, role = Role.Switch) {
                local = it
                prefs.edit()
                    .putBoolean("crash_reports_opt_in", it)
                    .putLong("crash_reports_consent_at", System.currentTimeMillis())
                    .putInt("crash_reports_consent_version", 1)
                    .apply()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AdminPanelSettings, null, tint = style.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Allow optional crash reports", color = style.primaryText, fontSize = (16f * style.fs).sp, fontWeight = FontWeight.W600)
            Text("Off by default. No crash reporter is installed in this version; this choice alone sends nothing.", color = style.secondaryText, fontSize = (14f * style.fs).sp)
        }
        Switch(checked = allowed, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
    }
}
