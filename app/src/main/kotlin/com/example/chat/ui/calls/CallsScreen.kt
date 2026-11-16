package com.example.chat.ui.calls

import kotlinx.coroutines.ensureActive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
// Previous implementation retained: import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.rounded.CallMissed
// Previous implementation retained: import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExtendedFloatingActionButton
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import com.example.chat.ui.navigation.LocalDestinationTitlePresentation
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.CallDirection
import com.example.chat.domain.models.CallRecord
import com.example.chat.domain.models.CallType
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalThemeConfig
import java.time.ZoneId
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

// Previous implementation retained: /** Port of features/calls/calls_screen.dart: the protected recent-calls log
// Previous implementation retained:  *  with pull-to-refresh, empty/error states, direction icon, duration and a
// Previous implementation retained:  *  redial action per row, plus the Add call FAB opening the contact picker. */
/** Protected recent-calls log with explicit retry, empty/error states and redial.
 * [onRefresh] asks the host to reload its history snapshot. The callback is fire-and-forget;
 * the host owns refresh lifetime/results and must publish updated [records]/[historyUnavailable].
 * The current UI exposes Retry after failure, not a pull-to-refresh gesture. It promises neither
 * background/process-death completion nor a full-vs-delta server strategy.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CallsScreen(
    records: List<CallRecord>,
    historyUnavailable: Boolean,
    currentUserId: String,
    // CALLS-R1-27: this lookup runs per row per composition - it must be O(1) (map-backed).
    contactFor: (String) -> UserProfile?,
    showCallActions: Boolean = true,
    onRefresh: () -> Unit,
    onOpenAddCall: () -> Unit,
    onRedial: (remoteUserId: String, displayName: String, initials: String?, colorHex: String?, isVideo: Boolean) -> Unit,
    // Call history plane (research 5.4): per-viewer delete + clear all.
    onDeleteEntry: (CallRecord) -> Unit = {},
    onClearAll: (() -> Unit)? = null,
) {
    // Account changes dispose pending Undo jobs and owner-specific confirmation state.
    androidx.compose.runtime.key(currentUserId) {
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    // CALLS-R1-39: visible delete path in addition to swipe/custom accessibility actions.
    var openEntryMenu by rememberSaveable { mutableStateOf<String?>(null) }
    val theme = LocalThemeConfig.current
    val titlePresentedByShell = LocalDestinationTitlePresentation.current ==
        com.example.chat.core.templates.ChatyNavigationDestinationIds.calls
    val context = androidx.compose.ui.platform.LocalContext.current
    val callsScreenOwner = remember(currentUserId) { com.example.chat.ServiceLocator.apiBackend.authSnapshot() }
    androidx.compose.runtime.LaunchedEffect(callsScreenOwner) {
        // Previous legacy-only clear: ChatyNotifications.clearMissedCallNotifications(context)
        // The screen never clears a newer account's alerts if dispatch follows an account switch.
        if (callsScreenOwner.userId == currentUserId)
            com.example.chat.ServiceLocator.notificationCoordinator.clearMissedCallAlerts(callsScreenOwner)
    }
// Previous implementation retained:     val zone = remember { ZoneId.systemDefault() }
    // CALLS-R2-40: one zone for grouping and displayed times, refreshed after travel.
    var clockRevision by remember { mutableStateOf(0) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    androidx.compose.runtime.DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                clockRevision++
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_LOCALE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val zone = remember(clockRevision, configuration) { ZoneId.systemDefault() }
    val today = java.time.LocalDate.now(zone)
    // CALLS-R1-07: honor the user's 12/24h locale clock instead of a hardcoded 24h pattern.
// Previous implementation retained:     val localeTimeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val localeTimeFormat = remember(clockRevision, configuration, zone) {
        android.text.format.DateFormat.getTimeFormat(context).apply {
            timeZone = java.util.TimeZone.getTimeZone(zone)
        }
    }
    // CALLS-R1-01 + R1-22: single-flight + 5s cooldown against double-tap / spam redials.
// Previous implementation retained:     var lastRedialTarget by remember { mutableStateOf<String?>(null) }
// Previous implementation retained:     var lastRedialAtMs by remember { mutableStateOf(0L) }
    val redialCooldown = remember(currentUserId) { CallRedialCooldown() }
    val redialScope = rememberCoroutineScope()
    val redialOwner = remember(currentUserId) { com.example.chat.ServiceLocator.apiBackend.authSnapshot() }
    val latestContactFor by androidx.compose.runtime.rememberUpdatedState(contactFor)
    val latestOnRedial by androidx.compose.runtime.rememberUpdatedState(onRedial)
    val checkCallPermission = rememberCallPermissionCheck()
    val callNetworkOnline = rememberCallNetworkOnline()
    var redialPermissionPending by remember { mutableStateOf(false) }
    var cooldownHint by remember { mutableStateOf<android.widget.Toast?>(null) }
    androidx.compose.runtime.DisposableEffect(currentUserId) {
        onDispose { cooldownHint?.cancel() }
    }
// Previous implementation retained:     fun tryRedial(id: String, name: String, initials: String?, colorHex: String?, isVideo: Boolean) {
    fun tryRedial(call: CallRecord) {
        if (redialPermissionPending || !callHasValidatedNetwork(context)) return
        val id = call.participantIds.singleOrNull { it.isNotBlank() && it != currentUserId } ?: return
        // Resolve presentation at tap time, not from the composed row's captured contact.
        val currentContact = contactFor(id)
        val name = callLogDisplayName(currentContact, call, context.getString(com.example.chat.R.string.calls_ui_15))
        val initials = currentContact?.avatarInitials?.takeIf { it.isNotBlank() } ?: callInitials(name)
        val colorHex = currentContact?.avatarColorHex
        val isVideo = call.type == CallType.video
        val now = android.os.SystemClock.elapsedRealtime()
// Previous implementation retained:         if (now - lastRedialAtMs < 1000) return
// Previous implementation retained:         if (lastRedialTarget == id && now - lastRedialAtMs < 5000) return
// Previous implementation retained:         lastRedialAtMs = now
// Previous implementation retained:         lastRedialTarget = id
        if (redialCooldown.acquire(id, now) > 0L) {
            cooldownHint?.cancel()
            cooldownHint = android.widget.Toast.makeText(context,
                context.getString(com.example.chat.R.string.call_log_redial_wait), android.widget.Toast.LENGTH_SHORT).also { it.show() }
            return
        }
// Previous implementation retained:         onRedial(id, name, initials, colorHex, isVideo)
        redialPermissionPending = true
        redialScope.launch {
            // Previous captured callback/presentation: if (checkCallPermission(isVideo)) onRedial(id, name, initials, colorHex, isVideo)
            try {
                if (checkCallPermission(isVideo) && com.example.chat.ServiceLocator.apiBackend.authSnapshot() === redialOwner) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val fresh = latestContactFor(id)
                    val freshName = callLogDisplayName(fresh, call, context.getString(com.example.chat.R.string.calls_ui_15))
                    latestOnRedial(id, freshName, fresh?.avatarInitials?.takeIf { it.isNotBlank() } ?: callInitials(freshName), fresh?.avatarColorHex, isVideo)
                }
            }
            finally { redialPermissionPending = false }
        }
    }
    // CALLS-R1-19: per-entry delete is a soft delete with Undo; the real delete commits on snackbar timeout.
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pendingDeletes = remember { mutableStateMapOf<String, CallRecord>() }
    val visibleRecords = records.filter { it.id !in pendingDeletes }
    fun softDelete(call: CallRecord) {
        if (call.id in pendingDeletes) return
        pendingDeletes[call.id] = call
        scope.launch {
// Previous implementation retained:             val result = snackbarHostState.showSnackbar("Call entry deleted", actionLabel = "Undo")
            val result = snackbarHostState.showSnackbar(context.getString(com.example.chat.R.string.calls_ui_1), actionLabel = context.getString(com.example.chat.R.string.calls_ui_2))
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                pendingDeletes.remove(call.id)
            } else {
                pendingDeletes.remove(call.id)
                onDeleteEntry(call)
            }
        }
    }
    // CALLS-R1-06: day buckets for timestamps (Today / Yesterday / weekday / date).
    fun dayLabel(ts: java.time.Instant): String {
// Previous implementation retained:         val date = ts.atZone(zone).toLocalDate()
// Previous implementation retained:         val today = java.time.LocalDate.now(zone)
        val date = callLogLocalDate(ts, zone)
        return when {
// Previous implementation retained: // Previous implementation retained:             date.isEqual(today) -> "Today"
            date == null -> context.getString(com.example.chat.R.string.call_log_unknown_date)
// Previous implementation retained:             date.isEqual(today) -> "Today"
// Previous implementation retained:             date.isEqual(today.minusDays(1)) -> "Yesterday"
            date.isEqual(today) -> context.getString(com.example.chat.R.string.calls_ui_3)
            date.isEqual(today.minusDays(1)) -> context.getString(com.example.chat.R.string.calls_ui_4)
            date.isAfter(today.minusDays(7)) -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            else -> date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
        }
    }

    Scaffold(
// Previous implementation retained:         modifier = Modifier.semantics { paneTitle = "Calls" },
        modifier = Modifier.semantics { paneTitle = context.getString(com.example.chat.R.string.calls_ui_5) },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        containerColor = com.example.chat.ui.navigation.LocalReferenceContentSurface.current ?: Color(theme.backgroundColor),
        floatingActionButton = {
            if (showCallActions) {
// Previous implementation retained:                 if (com.example.chat.ui.navigation.LocalReferenceDefaultPresentation.current) {
// Previous implementation retained:                     FloatingActionButton(
// Previous implementation retained: // Previous implementation retained:                         modifier = Modifier.size(56.dp).semantics { contentDescription = "Add call" },
// Previous implementation retained:                         modifier = Modifier.size(56.dp).semantics { contentDescription = context.getString(com.example.chat.R.string.calls_ui_6) },
// Previous implementation retained:                         onClick = onOpenAddCall,
// Previous implementation retained:                         shape = androidx.compose.foundation.shape.CircleShape,
// Previous implementation retained:                         containerColor = Color(theme.accentColor),
// Previous implementation retained:                         contentColor = Color(theme.onAccentColor),
// Previous implementation retained:                     ) { Icon(Icons.Rounded.Dialpad, contentDescription = null) }
// Previous implementation retained:                 } else ExtendedFloatingActionButton(
// Previous implementation retained: // Previous implementation retained:                     modifier = Modifier.semantics { contentDescription = "Add call" },
// Previous implementation retained:                     modifier = Modifier.semantics { contentDescription = context.getString(com.example.chat.R.string.calls_ui_6) },
// Previous implementation retained:                     onClick = onOpenAddCall,
// Previous implementation retained:                     containerColor = Color(theme.accentColor),
// Previous implementation retained:                     contentColor = Color(theme.onAccentColor),
// Previous implementation retained:                     icon = { Icon(Icons.Rounded.Dialpad, contentDescription = null) },
// Previous implementation retained: // Previous implementation retained:                     text = { Text("Add call") },
// Previous implementation retained:                     text = { Text(context.getString(com.example.chat.R.string.calls_ui_6)) },
// Previous implementation retained:                 )
                // CALLS-R1-48: same primary-action geometry/placement for both presentations.
                FloatingActionButton(
                    modifier = Modifier.size(56.dp).semantics { contentDescription = context.getString(com.example.chat.R.string.calls_ui_6) },
                    onClick = onOpenAddCall,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    containerColor = Color(theme.accentColor),
                    contentColor = Color(theme.onAccentColor),
                ) { Icon(Icons.Rounded.Dialpad, contentDescription = null) }
            }
        },
    ) { padding ->
        // R2/R3: rows full width on compact, capped at 720dp centered on
        // medium/expanded (approved responsive plan 8 Nov 2026).
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            // Prior fillMaxWidth before widthIn forced the parent width.
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            if (!callNetworkOnline) item {
                Text(context.getString(com.example.chat.R.string.call_network_unavailable),
                    color = Color(theme.secondaryTextColor), modifier = Modifier.padding(16.dp)
                        .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
            }
            if (!titlePresentedByShell) item {
                Row(
                    Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
                ) {
                    Text(
// Previous implementation retained:                         "Calls",
                        context.getString(com.example.chat.R.string.calls_ui_5),
                        color = Color(theme.primaryTextColor),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (onClearAll != null && records.isNotEmpty()) {
                        androidx.compose.material3.TextButton(onClick = { showClearConfirm = true }) {
// Previous implementation retained:                             Text("Clear", color = Color(theme.dangerColor), fontWeight = FontWeight.Bold)
                            Text(context.getString(com.example.chat.R.string.calls_ui_7), color = Color(theme.dangerColor), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (titlePresentedByShell && onClearAll != null && records.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { showClearConfirm = true }) {
// Previous implementation retained:                         Text("Clear call log", color = Color(theme.dangerColor), fontWeight = FontWeight.Bold)
                        Text(context.getString(com.example.chat.R.string.calls_ui_8), color = Color(theme.dangerColor), fontWeight = FontWeight.Bold)
                    }
                }
            }

            when {
                historyUnavailable && visibleRecords.isEmpty() -> item {
                    CallEmptyState(
                        icon = {
                            Icon(
                                Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = Color(theme.dangerColor),
                                modifier = Modifier.size(42.dp),
                            )
                        },
// Previous implementation retained:                         title = "Call history unavailable",
                        title = context.getString(com.example.chat.R.string.calls_ui_9),
                        titleColor = Color(theme.primaryTextColor),
// Previous implementation retained:                         message = "Chaty could not load your protected call history. Tap retry to try again.",
                        message = context.getString(com.example.chat.R.string.calls_ui_10),
                        messageColor = Color(theme.secondaryTextColor),
// Previous implementation retained:                         actionLabel = "Retry",
                        actionLabel = context.getString(com.example.chat.R.string.calls_ui_11),
                        onAction = onRefresh,
                    )
                }
                visibleRecords.isEmpty() -> item {
                    CallEmptyState(
                        icon = {
                            Icon(
                                Icons.Rounded.PhoneCallback,
                                contentDescription = null,
                                tint = Color(theme.accentColor),
                                modifier = Modifier.size(42.dp),
                            )
                        },
// Previous implementation retained:                         title = "No recent calls",
                        title = context.getString(com.example.chat.R.string.calls_ui_12),
                        titleColor = Color(theme.primaryTextColor),
// Previous implementation retained:                         message = "Your recent calls will appear here.",
                        message = context.getString(com.example.chat.R.string.calls_ui_13),
                        messageColor = Color(theme.secondaryTextColor),
                    )
                }
                else -> {
// Previous implementation retained:                     var lastBucket: String? = null
// Previous implementation retained:                     for (call in visibleRecords) {
// Previous implementation retained:                         val bucket = dayLabel(call.timestamp)
// Previous implementation retained:                         if (bucket != lastBucket) {
// Previous implementation retained:                             lastBucket = bucket
// Previous implementation retained:                             item(key = "bucket-$bucket") {
// Previous implementation retained:                                 Text(
// Previous implementation retained:                                     bucket,
// Previous implementation retained:                                     color = Color(theme.secondaryTextColor),
// Previous implementation retained:                                     fontSize = 13.sp,
                    for ((day, dayRecords) in callLogDayGroups(visibleRecords, zone)) {
                        val bucket = dayLabel(dayRecords.first().timestamp)
                        stickyHeader(key = "bucket-$day") {
                            androidx.compose.material3.Surface(
                                color = com.example.chat.ui.navigation.LocalReferenceContentSurface.current ?: Color(theme.backgroundColor),
                                modifier = Modifier.fillMaxWidth()) {
                                Text(bucket, color = Color(theme.secondaryTextColor), fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
// Previous implementation retained:                                     modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
// Previous implementation retained:                                 )
                                    modifier = Modifier.semantics { heading() }
                                        .padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
                            }
                        }
// Previous loop/key: for (call in dayRecords) item(key = "${call.id}#${call.timestamp}")
                        for ((rowIndex, call) in dayRecords.withIndex()) {
                        item(key = callLogRenderKey(call, rowIndex)) {
                    val swipeDelete = listOf(
                        com.example.chat.ui.chats.home.HomeSwipeAction(
                            icon = com.example.chat.ui.icons.ChatyIconId.delete,
// Previous implementation retained:                             label = "Delete",
                            label = context.getString(com.example.chat.R.string.calls_ui_14),
                            color = Color(theme.dangerColor),
                            onTriggered = { softDelete(call) },
                        ),
                    )
                    val remoteUserId = call.participantIds.singleOrNull { it.isNotEmpty() && it != currentUserId } ?: ""
                    val isGroupRecord = call.participantIds.count { it.isNotEmpty() && it != currentUserId } > 1
                    val contact = contactFor(remoteUserId)
                    val isMissed = call.direction == CallDirection.missed
                    val isVideo = call.type == CallType.video
                    // Previous: val canCall = remoteUserId.isNotEmpty()
                    val canCall = remoteUserId.isNotEmpty() && callNetworkOnline
// Previous implementation retained:                     val name = contact?.displayName?.trim()?.takeIf { it.isNotEmpty() }
// Previous implementation retained:                         ?: call.remoteDisplayName
// Previous implementation retained:                         ?: contact?.username?.trim()?.takeIf { it.isNotEmpty() }
// Previous implementation retained:                         ?: call.remoteUsername
// Previous implementation retained: // Previous implementation retained:                         ?: "Chaty contact"
// Previous implementation retained:                         ?: context.getString(com.example.chat.R.string.calls_ui_15)
// Previous implementation retained: // Previous implementation retained:                     val initials = contact?.avatarInitials ?: name.take(1).uppercase()
                    val name = callLogDisplayName(contact, call, context.getString(com.example.chat.R.string.calls_ui_15))
                    val initials = contact?.avatarInitials ?: callInitials(name)
                    val colorHex = contact?.avatarColorHex
// Previous implementation retained:                     val timeText = remember(call.id) { localeTimeFormat.format(java.util.Date(call.timestamp.toEpochMilli())) }
                    val timeText = remember(call.timestamp, localeTimeFormat) {
// Previous implementation retained:                         localeTimeFormat.format(java.util.Date(call.timestamp.toEpochMilli()))
                        callLogDateForDisplay(call.timestamp)?.let(localeTimeFormat::format)
                            ?: context.getString(com.example.chat.R.string.call_log_unknown_time)
                    }
// Previous implementation retained:                     val subtitle = remember(call.id, call.durationSeconds, isGroupRecord) {
// Previous implementation retained:                     val subtitle = remember(call.id, call.durationSeconds, isGroupRecord, timeText, configuration, clockRevision) {
                    val subtitle = remember(call.id, call.durationSeconds, call.outcome, call.wasConnected, isGroupRecord, timeText, configuration, clockRevision) {
// Previous implementation retained:                         if (isGroupRecord) "$timeText • Group call"
// Previous implementation retained:                         else timeText + " • " + (if (call.durationSeconds > 0) formatCallLogDuration(call.durationSeconds) else "No connection")
                        if (isGroupRecord) context.getString(com.example.chat.R.string.call_log_group_time, timeText)
// Previous implementation retained:                         else timeText + " • " + (if (call.durationSeconds > 0) formatCallLogDuration(call.durationSeconds) else context.getString(com.example.chat.R.string.calls_ui_16))
                        else timeText + " • " + callLogOutcomeText(call) { context.getString(it) }
                    }

                    com.example.chat.ui.chats.home.HomeSwipeActions(
                        actions = swipeDelete,
                        backgroundColor = com.example.chat.ui.navigation.LocalReferenceContentSurface.current ?: Color(theme.backgroundColor),
                    ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = when (call.direction) {
// Previous implementation retained:                                     CallDirection.incoming -> "Incoming call with $name, ${dayLabel(call.timestamp)} at $timeText, $subtitle"
// Previous implementation retained:                                     CallDirection.outgoing -> "Outgoing call to $name, ${dayLabel(call.timestamp)} at $timeText, $subtitle"
// Previous implementation retained:                                     CallDirection.missed -> "Missed call from $name, ${dayLabel(call.timestamp)} at $timeText, $subtitle"
                                    CallDirection.incoming -> context.getString(com.example.chat.R.string.call_log_incoming_description, name, dayLabel(call.timestamp), timeText, subtitle)
                                    CallDirection.outgoing -> context.getString(com.example.chat.R.string.call_log_outgoing_description, name, dayLabel(call.timestamp), timeText, subtitle)
                                    CallDirection.missed -> context.getString(com.example.chat.R.string.call_log_missed_description, name, dayLabel(call.timestamp), timeText, subtitle)
                                }
                                role = Role.Button
                                customActions = listOf(
// Previous implementation retained: // Previous implementation retained:                                     CustomAccessibilityAction(label = "Call back", action = { tryRedial(remoteUserId, name, initials, colorHex, isVideo); true }),
// Previous implementation retained:                                     CustomAccessibilityAction(label = "Call back", action = { tryRedial(call); true }),
// Previous implementation retained:                                     CustomAccessibilityAction(label = "Delete call entry", action = { softDelete(call); true }),
// Previous implementation retained:                                     CustomAccessibilityAction(label = context.getString(com.example.chat.R.string.calls_ui_17), action = { tryRedial(remoteUserId, name, initials, colorHex, isVideo); true }),
                                    CustomAccessibilityAction(label = context.getString(com.example.chat.R.string.calls_ui_17), action = { tryRedial(call); true }),
                                    CustomAccessibilityAction(label = context.getString(com.example.chat.R.string.calls_ui_18), action = { softDelete(call); true }),
                                )
                            }
                            .clickable(enabled = canCall) {
// Previous implementation retained:                                 tryRedial(remoteUserId, name, initials, colorHex, isVideo)
                                tryRedial(call)
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        CallAvatar(initials = initials, colorHex = colorHex, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                name,
                                color = if (isMissed) Color(theme.dangerColor) else Color(theme.primaryTextColor),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                letterSpacing = (-0.2).sp,
// Previous implementation retained:                                 maxLines = 1,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (call.direction) {
// Previous implementation retained:                                         CallDirection.incoming -> Icons.Rounded.CallReceived
// Previous implementation retained:                                         CallDirection.outgoing -> Icons.Rounded.CallMade
                                        CallDirection.incoming -> Icons.AutoMirrored.Rounded.CallReceived
                                        CallDirection.outgoing -> Icons.AutoMirrored.Rounded.CallMade
                                        CallDirection.missed -> Icons.Rounded.CallMissed
                                    },
                                    contentDescription = null,
                                    tint = if (isMissed) Color(theme.dangerColor) else Color(theme.secondaryTextColor),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    subtitle,
                                    color = Color(theme.secondaryTextColor),
                                    fontSize = 13.sp,
// Previous implementation retained:                                     maxLines = 1,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(
// Previous implementation retained:                             onClick = { tryRedial(remoteUserId, name, initials, colorHex, isVideo) },
                            onClick = { tryRedial(call) },
                            enabled = canCall,
                        ) {
                            Icon(
                                if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
// Previous implementation retained:                                 contentDescription = if (isVideo) "Video call" else "Voice call",
                                contentDescription = if (isVideo) context.getString(com.example.chat.R.string.calls_ui_19) else context.getString(com.example.chat.R.string.calls_ui_20),
                                tint = Color(theme.accentColor),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Box {
                            IconButton(onClick = { openEntryMenu = call.id }) {
// Previous implementation retained:                                 Icon(Icons.Rounded.MoreVert, contentDescription = "More actions for $name", tint = Color(theme.secondaryTextColor))
                                Icon(Icons.Rounded.MoreVert, contentDescription = context.getString(com.example.chat.R.string.call_log_more_actions, name), tint = Color(theme.secondaryTextColor))
                            }
                            DropdownMenu(expanded = openEntryMenu == call.id, onDismissRequest = { openEntryMenu = null }) {
                                DropdownMenuItem(
// Previous implementation retained:                                     text = { Text("Delete call entry") },
                                    text = { Text(context.getString(com.example.chat.R.string.calls_ui_18)) },
                                    onClick = { openEntryMenu = null; softDelete(call) },
                                )
                            }
                        }
                    }
                    }
                }
                    }
                    }
                }
            }
        }
        }
    }
    if (showClearConfirm) {
        com.example.chat.ui.kit.ChatyConfirmationDialog(
            shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showClearConfirm = false },
// Previous implementation retained:             title = { Text("Clear call log?", color = Color(theme.primaryTextColor)) },
// Previous implementation retained:             text = { Text("This removes the call history on this account. This can't be undone.", color = Color(theme.secondaryTextColor)) },
            title = { Text(context.getString(com.example.chat.R.string.calls_ui_21), color = Color(theme.primaryTextColor)) },
            text = { Text(context.getString(com.example.chat.R.string.calls_ui_22), color = Color(theme.secondaryTextColor)) },
            dismissButton = {
// Previous implementation retained:                 androidx.compose.material3.TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
                androidx.compose.material3.TextButton(onClick = { showClearConfirm = false }) { Text(context.getString(com.example.chat.R.string.calls_ui_23)) }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showClearConfirm = false
                    onClearAll?.invoke()
// Previous implementation retained:                 }) { Text("Clear", color = Color(theme.dangerColor), fontWeight = FontWeight.Bold) }
                }) { Text(context.getString(com.example.chat.R.string.calls_ui_7), color = Color(theme.dangerColor), fontWeight = FontWeight.Bold) }
            },
        )
    }
} // Account identity boundary.
}

@Composable
private fun CallEmptyState(
    icon: @Composable () -> Unit,
    title: String,
    titleColor: Color,
    message: String,
    messageColor: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        // This helper is a LazyColumn item (empty and unavailable history).
        // Lazy items are measured with unbounded height; the parent list owns scrolling.
        // Prior: Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)
        modifier = Modifier.fillMaxWidth().padding(28.dp),
    ) {
        icon()
        Spacer(Modifier.height(12.dp))
        Text(title, color = titleColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(5.dp))
        Text(message, color = messageColor, fontSize = 13.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/** CALLS-R1-53: m:ss like every mainstream phone app, not raw seconds. */
private fun formatCallLogDuration(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)


/** Shared zone conversion for day buckets, independent of the device default in tests. */
// Previous implementation retained: internal fun callLogLocalDate(timestamp: java.time.Instant, zone: ZoneId): java.time.LocalDate =
// Previous implementation retained:     timestamp.atZone(zone).toLocalDate()
internal fun callLogLocalDate(timestamp: java.time.Instant, zone: ZoneId): java.time.LocalDate? =
    runCatching { timestamp.atZone(zone).toLocalDate() }.getOrNull()


/** Display labels only. Does not select a recipient or change stored contact identity. */
internal fun callLogDisplayName(contact: UserProfile?, call: CallRecord, unknownLabel: String): String =
    contact?.displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: contact?.username?.trim()?.takeIf { it.isNotEmpty() }
        ?: call.remoteDisplayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: call.remoteUsername?.trim()?.takeIf { it.isNotEmpty() }
        ?: unknownLabel


/** Stable, newest-first local-day groups, without assuming repository result order. */
// Previous implementation retained: internal fun callLogDayGroups(records: List<CallRecord>, zone: ZoneId): List<Pair<java.time.LocalDate, List<CallRecord>>> =
internal fun callLogDayGroups(records: List<CallRecord>, zone: ZoneId): List<Pair<java.time.LocalDate?, List<CallRecord>>> =
    records.sortedByDescending { it.timestamp }.groupBy { callLogLocalDate(it.timestamp, zone) }.toList()


/** Unrepresentable timestamps stay explicitly unknown instead of crashing or inventing a time. */
internal fun callLogDateForDisplay(timestamp: java.time.Instant): java.util.Date? =
    runCatching { java.util.Date(timestamp.toEpochMilli()) }.getOrNull()

// Superseded source retained inactive:
//             modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),

/** UI-only key, does not change server/delete identity. */
internal fun callLogRenderKey(call: CallRecord, rowIndex: Int): String =
    "row:${call.id.length}:${call.id}:${call.timestamp}:$rowIndex"
