package com.example.chat.ui.calls

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
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExtendedFloatingActionButton
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
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

/** Port of features/calls/calls_screen.dart: the protected recent-calls log
 *  with pull-to-refresh, empty/error states, direction icon, duration and a
 *  redial action per row, plus the Add call FAB opening the contact picker. */
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
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    val theme = LocalThemeConfig.current
    val titlePresentedByShell = LocalDestinationTitlePresentation.current ==
        com.example.chat.core.templates.ChatyNavigationDestinationIds.calls
    val context = androidx.compose.ui.platform.LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    // CALLS-R1-07: honor the user's 12/24h locale clock instead of a hardcoded 24h pattern.
    val localeTimeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    // CALLS-R1-01 + R1-22: single-flight + 5s cooldown against double-tap / spam redials.
    var lastRedialTarget by remember { mutableStateOf<String?>(null) }
    var lastRedialAtMs by remember { mutableStateOf(0L) }
    fun tryRedial(id: String, name: String, initials: String?, colorHex: String?, isVideo: Boolean) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastRedialAtMs < 1000) return
        if (lastRedialTarget == id && now - lastRedialAtMs < 5000) return
        lastRedialAtMs = now
        lastRedialTarget = id
        onRedial(id, name, initials, colorHex, isVideo)
    }
    // CALLS-R1-19: per-entry delete is a soft delete with Undo; the real delete commits on snackbar timeout.
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pendingDeletes = remember { mutableStateMapOf<String, CallRecord>() }
    val visibleRecords = records.filter { it.id !in pendingDeletes }
    fun softDelete(call: CallRecord) {
        pendingDeletes[call.id] = call
        scope.launch {
            val result = snackbarHostState.showSnackbar("Call entry deleted", actionLabel = "Undo")
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
        val date = ts.atZone(zone).toLocalDate()
        val today = java.time.LocalDate.now(zone)
        return when {
            date.isEqual(today) -> "Today"
            date.isEqual(today.minusDays(1)) -> "Yesterday"
            date.isAfter(today.minusDays(7)) -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            else -> date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
        }
    }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = "Calls" },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        containerColor = com.example.chat.ui.navigation.LocalReferenceContentSurface.current ?: Color(theme.backgroundColor),
        floatingActionButton = {
            if (showCallActions) {
                if (com.example.chat.ui.navigation.LocalReferenceDefaultPresentation.current) {
                    FloatingActionButton(
                        modifier = Modifier.size(56.dp).semantics { contentDescription = "Add call" },
                        onClick = onOpenAddCall,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        containerColor = Color(theme.accentColor),
                        contentColor = Color(theme.onAccentColor),
                    ) { Icon(Icons.Rounded.Dialpad, contentDescription = null) }
                } else ExtendedFloatingActionButton(
                    modifier = Modifier.semantics { contentDescription = "Add call" },
                    onClick = onOpenAddCall,
                    containerColor = Color(theme.accentColor),
                    contentColor = Color(theme.onAccentColor),
                    icon = { Icon(Icons.Rounded.Dialpad, contentDescription = null) },
                    text = { Text("Add call") },
                )
            }
        },
    ) { padding ->
        // R2/R3: rows full width on compact, capped at 720dp centered on
        // medium/expanded (approved responsive plan 8 Nov 2026).
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            if (!titlePresentedByShell) item {
                Row(
                    Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
                ) {
                    Text(
                        "Calls",
                        color = Color(theme.primaryTextColor),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (onClearAll != null && records.isNotEmpty()) {
                        androidx.compose.material3.TextButton(onClick = { showClearConfirm = true }) {
                            Text("Clear", color = Color(theme.dangerColor), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (titlePresentedByShell && onClearAll != null && records.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { showClearConfirm = true }) {
                        Text("Clear call log", color = Color(theme.dangerColor), fontWeight = FontWeight.Bold)
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
                        title = "Call history unavailable",
                        titleColor = Color(theme.primaryTextColor),
                        message = "Chaty could not load your protected call history. Tap retry to try again.",
                        messageColor = Color(theme.secondaryTextColor),
                        actionLabel = "Retry",
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
                        title = "No recent calls",
                        titleColor = Color(theme.primaryTextColor),
                        message = "Your recent calls will appear here.",
                        messageColor = Color(theme.secondaryTextColor),
                    )
                }
                else -> {
                    var lastBucket: String? = null
                    for (call in visibleRecords) {
                        val bucket = dayLabel(call.timestamp)
                        if (bucket != lastBucket) {
                            lastBucket = bucket
                            item(key = "bucket-$bucket") {
                                Text(
                                    bucket,
                                    color = Color(theme.secondaryTextColor),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                                )
                            }
                        }
                        item(key = "${call.id}#${call.timestamp}") {
                    val swipeDelete = listOf(
                        com.example.chat.ui.chats.home.HomeSwipeAction(
                            icon = com.example.chat.ui.icons.ChatyIconId.delete,
                            label = "Delete",
                            color = Color(theme.dangerColor),
                            onTriggered = { softDelete(call) },
                        ),
                    )
                    val remoteUserId = call.participantIds.singleOrNull { it.isNotEmpty() && it != currentUserId } ?: ""
                    val isGroupRecord = call.participantIds.count { it.isNotEmpty() && it != currentUserId } > 1
                    val contact = contactFor(remoteUserId)
                    val isMissed = call.direction == CallDirection.missed
                    val isVideo = call.type == CallType.video
                    val canCall = remoteUserId.isNotEmpty()
                    val name = contact?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                        ?: call.remoteDisplayName
                        ?: contact?.username?.trim()?.takeIf { it.isNotEmpty() }
                        ?: call.remoteUsername
                        ?: "Chaty contact"
                    val initials = contact?.avatarInitials ?: name.take(1).uppercase()
                    val colorHex = contact?.avatarColorHex
                    val timeText = remember(call.id) { localeTimeFormat.format(java.util.Date(call.timestamp.toEpochMilli())) }
                    val subtitle = remember(call.id, call.durationSeconds, isGroupRecord) {
                        if (isGroupRecord) "$timeText • Group call"
                        else timeText + " • " + (if (call.durationSeconds > 0) formatCallLogDuration(call.durationSeconds) else "No connection")
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
                                    CallDirection.incoming -> "Incoming call with $name, ${dayLabel(call.timestamp)} at $timeText, $subtitle"
                                    CallDirection.outgoing -> "Outgoing call to $name, ${dayLabel(call.timestamp)} at $timeText, $subtitle"
                                    CallDirection.missed -> "Missed call from $name, ${dayLabel(call.timestamp)} at $timeText, $subtitle"
                                }
                                role = Role.Button
                                customActions = listOf(
                                    CustomAccessibilityAction(label = "Call back", action = { tryRedial(remoteUserId, name, initials, colorHex, isVideo); true }),
                                    CustomAccessibilityAction(label = "Delete call entry", action = { softDelete(call); true }),
                                )
                            }
                            .clickable(enabled = canCall) {
                                tryRedial(remoteUserId, name, initials, colorHex, isVideo)
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (call.direction) {
                                        CallDirection.incoming -> Icons.Rounded.CallReceived
                                        CallDirection.outgoing -> Icons.Rounded.CallMade
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(
                            onClick = { tryRedial(remoteUserId, name, initials, colorHex, isVideo) },
                            enabled = canCall,
                        ) {
                            Icon(
                                if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                contentDescription = if (isVideo) "Video call" else "Voice call",
                                tint = Color(theme.accentColor),
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
        }
    }
    if (showClearConfirm) {
        com.example.chat.ui.kit.ChatyConfirmationDialog(
            shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear call log?", color = Color(theme.primaryTextColor)) },
            text = { Text("This removes the call history on this account. This can't be undone.", color = Color(theme.secondaryTextColor)) },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showClearConfirm = false
                    onClearAll?.invoke()
                }) { Text("Clear", color = Color(theme.dangerColor), fontWeight = FontWeight.Bold) }
            },
        )
    }
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
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
