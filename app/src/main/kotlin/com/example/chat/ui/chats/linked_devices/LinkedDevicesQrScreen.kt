package com.example.chat.ui.chats.linked_devices

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.data.services.LinkedDevice
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.contact_info.ConfirmDialog
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Port of linked_devices_qr_screen.dart (api branch anchor 3bee780):
 *  devices list (register current device, revoke others), My QR
 *  (chaty://contact/<username>, EC level H, center brand badge), and the ML
 *  Kit scanner that resolves a scanned contact into a direct conversation.
 *
 *  Tab shapes follow the Dart constructor flags: devicesOnly -> single
 *  "Linked Devices" page, qrOnly -> My QR + Scan, otherwise all three.
 *  Pull-to-refresh matches the Dart RefreshIndicator. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesQrScreen(
    currentUserDisplayName: String,
    currentUserUsername: String,
    devices: List<LinkedDevice>,
    loadingDevices: Boolean,
    devicesError: String?,
    initialIndex: Int = 0,
    qrOnly: Boolean = false,
    devicesOnly: Boolean = false,
    onRefreshDevices: suspend () -> Unit,
    onRevokeDevice: suspend (LinkedDevice) -> Unit,
    searchUsersRemote: suspend (String) -> List<UserProfile>,
    openDirectConversation: suspend (UserProfile) -> Conversation,
    onOpenConversation: (Conversation) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val tabCount = if (devicesOnly) 0 else if (qrOnly) 2 else 3
    var selectedTab by rememberSaveable {
        mutableIntStateOf(
            when {
                devicesOnly -> 0
                qrOnly -> initialIndex.coerceIn(0, 1)
                else -> initialIndex.coerceIn(0, 2)
            },
        )
      }
    // Coerce against the CURRENT tab shape on every composition: the host can
    // change qrOnly/devicesOnly dynamically, and a stale index would point at
    // the wrong content.
    selectedTab = when {
        devicesOnly -> 0
        qrOnly -> selectedTab.coerceIn(0, 1)
        else -> selectedTab.coerceIn(0, 2)
    }
    var scanBusy by remember { mutableStateOf(false) }
    var revokeCandidate by remember { mutableStateOf<LinkedDevice?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    // Single current snackbar on the scan path: repeated scan failures must
    // not queue a long tail of stale messages.
    suspend fun showScanMessage(message: String) {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(message)
    }

    // _handleBarcode: validate the Chaty URI, reject self, resolve the user
    // remotely, then open the direct conversation.
    fun handleBarcode(raw: String?) {
        if (scanBusy) return
        when (val parsed = QrPayloads.parseScanned(raw)) {
            QrPayloads.ScanResult.EmptyUsername -> {
                // Dart returned silently, but a malformed Chaty code must not
                // be indistinguishable from a dead scanner.
                scope.launch { showScanMessage("This Chaty QR code has no username.") }
            }
            QrPayloads.ScanResult.NotChatyQr -> {
                scope.launch { showScanMessage("This is not a Chaty contact QR code.") }
            }
            is QrPayloads.ScanResult.Contact -> {
                val username = parsed.username
                if (QrPayloads.isSelfUsername(username, currentUserUsername)) {
                    scope.launch { showScanMessage("This QR belongs to your current account.") }
                    return
                }
                scanBusy = true
                scope.launch {
                    try {
                        val results = searchUsersRemote(username)
                        val user = results.firstOrNull { it.username.lowercase() == username.lowercase() }
                        if (user == null) {
                            showScanMessage("Chaty user @$username could not be found.")
                        } else {
                            val conversation = openDirectConversation(user)
                            onOpenConversation(conversation)
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        // Curated copy only: raw exception text (URLs, backend
                        // internals) never reaches the snackbar.
                        showScanMessage(scanErrorMessage(error))
                    } finally {
                        scanBusy = false
                    }
                }
            }
        }
    }

    val title = when {
        devicesOnly -> "Linked Devices"
        qrOnly -> "QR Code & Scanner"
        else -> "Linked Devices & QR"
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = {
            // Scan errors are the ONLY feedback on the Scan tab: pin the
            // polite live region on the host explicitly instead of relying
            // on the kit default.
            SnackbarHost(snackbar, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        },
        topBar = {
            Column {
                // Standard TopAppBar (as Starred messages) instead of a
                // hand-rolled header Row: insets, elevation and title
                // alignment stay consistent with every other screen.
                TopAppBar(
                    title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Back", color = colors.foreground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.foreground,
                        navigationIconContentColor = colors.foreground,
                    ),
                )
                if (tabCount > 0) {
                    // Dart TabBar(indicatorColor: primary, labelColor: primary,
                    // unselectedLabelColor: onSurface 0.55). Unselected labels
                    // deliberately use foregroundSecondary instead: 55% alpha
                    // drops under WCAG 4.5:1 in several theme packs.
                    // PrimaryTabRow's
                    // DEFAULT indicator is the supported way to land that exact
                    // visual: it positions the bar under the selected tab
                    // (TabRowScope.tabIndicatorOffset internally) and colors it
                    // with MaterialTheme.colorScheme.primary, which ChatyTheme
                    // maps to the theme accent. A custom indicator slot broke
                    // the build 22 compile (tabIndicatorOffset is
                    // TabRowScope-scoped, not a top-level Modifier import).
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = colors.background,
                        contentColor = colors.primary,
                    ) {
                        if (!qrOnly) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.desktop, semanticLabel = null) },
                                text = { Text("Devices") },
                                selectedContentColor = colors.primary,
                                unselectedContentColor = colors.foregroundSecondary,
                            )
                        }
                        val myQrIndex = if (qrOnly) 0 else 1
                        val scanIndex = if (qrOnly) 1 else 2
                        Tab(
                            selected = selectedTab == myQrIndex,
                            onClick = { selectedTab = myQrIndex },
                            icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null) },
                            text = { Text("My QR") },
                            selectedContentColor = colors.primary,
                            unselectedContentColor = colors.foregroundSecondary,
                        )
                        Tab(
                            selected = selectedTab == scanIndex,
                            onClick = { selectedTab = scanIndex },
                            icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
                            text = { Text("Scan") },
                            selectedContentColor = colors.primary,
                            unselectedContentColor = colors.foregroundSecondary,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // TalkBack announces the screen name on entry, matching the
                // visible title per mode.
                .semantics { paneTitle = title }
                // Hardware-keyboard tab switching (ChromeOS): Ctrl+1/2/3
                // selects the tabs in their current shape.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && tabCount > 0) {
                        when (event.key) {
                            Key.One -> { selectedTab = 0; true }
                            Key.Two -> if (tabCount > 1) { selectedTab = 1; true } else false
                            Key.Three -> if (tabCount > 2) { selectedTab = 2; true } else false
                            else -> false
                        }
                    } else {
                        false
                    }
                },
        ) {
            when {
                devicesOnly -> DevicesView(
                    devices, loadingDevices, devicesError, refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            try {
                                onRefreshDevices()
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                snackbar.showSnackbar("Could not refresh devices. Try again.")
                            } finally {
                                refreshing = false
                            }
                        }
                    },
                    onRevoke = { revokeCandidate = it },
                    linkTabAvailable = false,
                    onGoToLinkTab = null,
                )
                qrOnly && selectedTab == 0 -> MyQrView(
                    currentUserDisplayName,
                    currentUserUsername,
                    onShowMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                )
                qrOnly -> ScanTab(scanBusy, onDetected = { handleBarcode(it) })
                selectedTab == 0 -> DevicesView(
                    devices, loadingDevices, devicesError, refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            try {
                                onRefreshDevices()
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                snackbar.showSnackbar("Could not refresh devices. Try again.")
                            } finally {
                                refreshing = false
                            }
                        }
                    },
                    onRevoke = { revokeCandidate = it },
                    linkTabAvailable = true,
                    onGoToLinkTab = { selectedTab = 1 },
                )
                selectedTab == 1 -> MyQrView(
                    currentUserDisplayName,
                    currentUserUsername,
                    onShowMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                )
                else -> ScanTab(scanBusy, onDetected = { handleBarcode(it) })
            }
        }
    }

    // _revoke confirm dialog (skipped for the current device).
    val revokeTarget = revokeCandidate
    if (revokeTarget != null) {
        ConfirmDialog(
            title = "Log out ${revokeTarget.deviceName}?",
            message = "This device will be marked revoked and removed from your active device list.",
            confirmLabel = "Log out",
            onConfirm = {
                revokeCandidate = null
                scope.launch {
                    try {
                        onRevokeDevice(revokeTarget)
                        // End the revoke flow with confirmation naming the
                        // device, not silence.
                        snackbar.showSnackbar("Logged out ${revokeTarget.deviceName}.")
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Curated copy only: never print the exception class
                        // name/message to a non-technical user.
                        snackbar.showSnackbar("Could not log out ${revokeTarget.deviceName}. Check your connection and try again.")
                    }
                }
            },
            onDismiss = { revokeCandidate = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesView(
    devices: List<LinkedDevice>,
    loading: Boolean,
    error: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRevoke: (LinkedDevice) -> Unit,
    linkTabAvailable: Boolean,
    onGoToLinkTab: (() -> Unit)?,
) {
    val colors = LocalAppColors.current
    // 'Active now' must age while the screen stays open: recompute the
    // relative labels every 30s; reformatting the timestamps is cheap.
    var labelTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            labelTick++
        }
    }
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.2.dp, color = colors.primary)
        }
        error != null && devices.isEmpty() -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // One TalkBack stop for the whole message: the decorative
                // icon, title and detail merge into a single announcement;
                // the retry action stays its own node.
                Column(
                    Modifier.semantics(mergeDescendants = true) { heading() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.errorOutline, semanticLabel = null, color = colors.foregroundSecondary, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Could not load devices", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = colors.foregroundSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRefresh) {
                    Text("Try again", color = colors.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        devices.isEmpty() -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.desktop, semanticLabel = null, color = colors.foregroundSecondary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                Text("No linked devices", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(6.dp))
                Text(
                    // The tabs are Devices / My QR / Scan: there is no 'Link
                    // Device' tab, and in devices-only mode there are no tabs.
                    if (linkTabAvailable)
                        // Previous misleading copy: "Link your computer or tablet by scanning your QR code in the My QR tab."
                        "Your contact QR code is in the My QR tab. It lets others find your account."
                    else
                        "Devices you link will appear here.",
                    color = colors.foregroundSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                if (linkTabAvailable && onGoToLinkTab != null) {
                    Spacer(Modifier.height(12.dp))
                    // Single primary action under the copy: filled tonal,
                    // 48dp, in the screen's 12%-primary-wash idiom.
                    FilledTonalButton(
                        onClick = onGoToLinkTab,
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.primary.copy(alpha = 0.12f),
                            contentColor = colors.primary,
                        ),
                    ) {
                        // Previous action label: "Link new device"
                        Text("Show contact QR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                // A transient refresh failure must not hide a perfectly good
                // cached list: banner above the rows (as Starred messages
                // does); the full error pane only shows when nothing is
                // cached.
                if (error != null) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .semantics { liveRegion = LiveRegionMode.Assertive },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.errorOutline, semanticLabel = null, color = colors.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(error, color = colors.foreground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = onRefresh) {
                                Text("Try again", color = colors.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Active Devices",
                        color = colors.foregroundSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        // 4dp label-to-card gap on the 8dp grid (proximity).
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }
                // One item per device (keyed) so LazyColumn actually recycles;
                // per-position corner shapes keep the single-card look of the
                // old eager Column.
                itemsIndexed(devices, key = { _, device -> device.id }) { index, device ->
                    val cardShape = when {
                        devices.size == 1 -> RoundedCornerShape(16.dp)
                        index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        index == devices.lastIndex -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                        else -> RectangleShape
                    }
                    DeviceRow(device, cardShape, labelTick, onRevoke)
                }
            }
        }
    }
}

/** Curated scan-failure copy: known failures map to actionable text and
 *  everything else falls back to a generic message, so backend exception
 *  text (URLs, SQL hints, English-only internals) never lands verbatim in
 *  the snackbar. */
private fun scanErrorMessage(error: Exception): String = when (error) {
    is java.net.UnknownHostException, is java.net.SocketTimeoutException ->
        "No connection. Check your internet and try again."
    is java.io.IOException ->
        "Network problem. Check your connection and try again."
    else -> "Could not open this contact. Try again."
}

@Composable
private fun DeviceRow(
    device: LinkedDevice,
    cardShape: androidx.compose.ui.graphics.Shape,
    labelTick: Int,
    onRevoke: (LinkedDevice) -> Unit,
) {
    val colors = LocalAppColors.current
    // Re-formats when the 30s ticker fires, so 'Active now' ages while the
    // screen stays open.
    val now = remember(labelTick) { java.time.Instant.now() }
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, cardShape)
            .semantics {
                // TalkBack power users get 'Log out <device>' as a row
                // custom action without reaching the icon.
                if (!device.isCurrentDevice) {
                    customActions = listOf(
                        CustomAccessibilityAction("Log out ${device.deviceName}") { onRevoke(device); true },
                    )
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.background(colors.primary.copy(alpha = 0.12f), CircleShape).padding(8.dp)) {
            Icon(
                Icons.Rounded.DevicesOther,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.deviceName,
                color = colors.foreground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Text(
                "${device.platform}${if (device.location.isEmpty()) "" else " • ${device.location}"}\n${QrPayloads.lastActiveLabel(device.lastActiveAt, now = now)}",
                color = colors.foregroundSecondary /* Previous: colors.foregroundSecondary.copy(alpha = 0.6f) */,
                fontSize = 12.sp,
            )
        }
        if (device.isCurrentDevice) {
            Box(
                Modifier
                    .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    "This Device",
                    color = colors.primary,
                    // 12sp floor for small-text legibility; per-pack contrast
                    // of primary on the 12%-alpha wash stays unverified.
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            IconButton(onClick = { onRevoke(device) }) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.logout,
                    // TalkBack must hear WHICH device this row logs out.
                    semanticLabel = "Log out ${device.deviceName}",
                    color = colors.error,
                )
            }
        }
    }
}

@Composable
private fun MyQrView(displayName: String, username: String, onShowMessage: (String) -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val qrBitmap = remember(username) {
        QrCodeRenderer.render(
            QrPayloads.myQrPayload(username),
            sizePx = 230 * 3,
            foregroundArgb = android.graphics.Color.BLACK,
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .background(colors.surface, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Dark modules on a white ground in EVERY theme: inverted
                // (light-on-dark) QRs fail on many scanners.
                // QR bitmap moved to MyQrView scope so Share and Save can use the same image.
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "My Chaty contact QR",
                    modifier = Modifier.size(230.dp),
                )
                // Center quiet-zone badge with the Chaty brand mark.
                Box(
                    Modifier
                        .size(44.dp)
                        .background(colors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.QrCode2,
                        contentDescription = "Chaty",
                        tint = colors.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(displayName, color = colors.foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text("@$username", color = colors.foregroundSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            // Previous unsupported claim: "Scan this code to start an instant encrypted chat without phone numbers."
            "Scan this contact code to find my Chaty account.",
            color = colors.foregroundSecondary,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp),
        )
        Spacer(Modifier.height(16.dp))
        // A contact QR exists to reach another person: Share (image + chaty://
        // link via the system sheet) and Save to gallery, the actions
        // WhatsApp users expect (Jakob's law).
        Row {
            TextButton(onClick = {
                scope.launch { shareQrImage(context, qrBitmap, username, onShowMessage) }
            }) {
                Text("Share", color = colors.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = {
                scope.launch { saveQrToGallery(context, qrBitmap, username, onShowMessage) }
            }) {
                Text("Save", color = colors.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Shares the QR PNG through the existing ${applicationId}.fileprovider
 *  (cache-path) with the chaty://contact link as accompanying text. */
private suspend fun shareQrImage(
    context: Context,
    bitmap: Bitmap,
    username: String,
    onShowMessage: (String) -> Unit,
) {
    val shared = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, "chaty-qr-$username.png")
            // Previous encode ignored its Boolean success result.
            check(file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }) { "PNG compress failed" }
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }
    if (shared == null) {
        onShowMessage("Could not share the QR code. Try again.")
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, shared)
        putExtra(Intent.EXTRA_TEXT, QrPayloads.myQrPayload(username))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share your Chaty QR")) }
        .onFailure { onShowMessage("Could not share the QR code. Try again.") }
}

/** Saves the QR PNG to Pictures/Chaty via MediaStore. The manifest declares
 *  no WRITE_EXTERNAL_STORAGE, so on API < 29 the insert would fail; degrade
 *  to an honest message there. */
private suspend fun saveQrToGallery(
    context: Context,
    bitmap: Bitmap,
    username: String,
    onShowMessage: (String) -> Unit,
) {
    if (Build.VERSION.SDK_INT < 29) {
        onShowMessage("Saving to the gallery needs Android 10 or newer.")
        return
    }
    val saved = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "chaty-qr-$username.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Chaty")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) error("PNG compress failed")
            } ?: error("MediaStore open failed")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrNull()
    }
    onShowMessage(
        if (saved != null) "QR code saved to your gallery." else "Could not save the QR code. Try again.",
    )
}

/** How long the Scan tab must stay settled before the camera stack is
 *  composed: rapid Devices->Scan->Devices toggling must not create
 *  overlapping CameraX bind/unbind cycles (the top crash class for camera
 *  features). The skeleton below covers the delay. */
private const val SCAN_BIND_DEBOUNCE_MS = 150L

/** Scan-tab entry: show the viewfinder skeleton (dark box + 'Starting
 *  camera...') INSTANTLY on tab select, then compose the camera only after
 *  the tab has settled for [SCAN_BIND_DEBOUNCE_MS]. Entries shorter than
 *  that never bind the camera at all. */
@Composable
private fun ScanTab(scanBusy: Boolean, onDetected: (String) -> Unit) {
    val colors = LocalAppColors.current
    var cameraSettled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SCAN_BIND_DEBOUNCE_MS)
        cameraSettled = true
    }
    if (cameraSettled) {
        ScannerView(scanBusy, onDetected)
    } else {
        Box(
            Modifier.fillMaxSize().background(colors.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Prior skeleton fill: Color.Black
                Box(Modifier.size(240.dp).background(colors.surfaceSecondary, RoundedCornerShape(20.dp)))
                Spacer(Modifier.height(12.dp))
                Text("Starting camera...", color = colors.foregroundSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ScannerView(scanBusy: Boolean, onDetected: (String) -> Unit) {
    val colors = LocalAppColors.current
    var cameraActive by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        QrScannerView(
            modifier = Modifier.fillMaxSize(),
            onDetected = onDetected,
            enableTorchToggle = true,
            onPermissionState = { cameraActive = it },
        )
        // Viewfinder overlays belong to the live camera only: while the
        // permission primer is showing they would sit on top of it.
        if (cameraActive) {
            // 40% black scrim OUTSIDE the viewfinder box (standard scanner
            // pattern) so the primary border always sits on a darkened
            // field, whatever the scene behind it.
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f).background(androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f) /* Prior: Color.Black.copy(alpha = 0.4f) */))
                Row(Modifier.fillMaxWidth().height(240.dp)) {
                    Box(Modifier.fillMaxHeight().weight(1f).background(androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f) /* Prior: Color.Black.copy(alpha = 0.4f) */))
                    Spacer(Modifier.width(240.dp))
                    Box(Modifier.fillMaxHeight().weight(1f).background(androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f) /* Prior: Color.Black.copy(alpha = 0.4f) */))
                }
                Box(Modifier.fillMaxWidth().weight(1f).background(androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f) /* Prior: Color.Black.copy(alpha = 0.4f) */))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp)
                    .background(colors.surfaceElevated.copy(alpha = 0.85f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "Point camera at a Chaty contact QR code",
                    color = colors.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Dart: Border.all(color: primary, width: 2.5) over 240x240, radius xl.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .border(2.5.dp, colors.primary, RoundedCornerShape(20.dp)),
            )
        }
        if (scanBusy) {
            // The overlay pops with no animated transition, so
            // reduced-motion settings are inherently respected; the camera
            // preview itself is a live viewfinder and motion-exempt.
            Box(
                Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 2.2.dp, color = colors.primary)
            }
        }
    }
}
