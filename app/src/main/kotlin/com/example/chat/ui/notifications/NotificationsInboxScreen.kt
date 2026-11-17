package com.example.chat.ui.notifications

import com.example.chat.ui.kit.chatyPredictiveBack

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chat.data.services.ChatyEventNotification
import com.example.chat.data.services.ChatyNotificationService
import java.time.Clock
import com.example.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

/**
 * Dedicated in-app activity feed. This deliberately displays only events
 * emitted by ChatyNotificationService; it does not pretend to be a server
 * notification archive or duplicate Android message/call notifications.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotificationsInboxScreen(
    service: ChatyNotificationService,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val paneLabel = stringResource(R.string.social_activity_title)
    var revision by remember { mutableIntStateOf(0) }
    var clearCandidates by remember(service) { mutableStateOf<List<String>?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(service) {
        val listener: () -> Unit = { scope.launch { revision++ } }
        service.addListener(listener)
        listener() // Reconcile any event arriving between the first snapshot and registration.
        onDispose { service.removeListener(listener) }
    }
    val entries = remember(service, revision) { service.notifications }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val context = LocalContext.current
    var calendarRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        // Runtime-only receiver: no event content is retained or broadcast by this screen.
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                calendarRevision++
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_TICK)
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            addAction(android.content.Intent.ACTION_LOCALE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Reconcile a change between the first composition and registration.
        calendarRevision++
        onDispose { context.unregisterReceiver(receiver) }
    }
    val clock = remember(calendarRevision, configuration) { Clock.systemDefaultZone() }
    val zone = clock.zone
    val eventDays = remember(entries, zone) {
        entries.groupBy { NotificationDateRules.localDayOrNull(it.timestamp, zone) }
            .toSortedMap(compareBy<java.time.LocalDate?> { it == null }.thenByDescending { it })
    }

    // Shared per configuration/calendar revision, used on the UI thread only.
    val dateFormatter = remember(configuration, calendarRevision) { android.text.format.DateFormat.getMediumDateFormat(context) }
    val timeFormatter = remember(configuration, calendarRevision) { android.text.format.DateFormat.getTimeFormat(context) }
    val unavailableDate = stringResource(R.string.social_activity_time_unavailable)

    val predictiveBack = com.example.chat.ui.kit.rememberChatyBackPresentation(Unit)
    // Previous registration retained inactive: BackHandler(onBack = onBack)
    com.example.chat.ui.kit.ChatyPredictiveBackHandler(presentation = predictiveBack, onBack = onBack)
    Column(
        modifier = Modifier.chatyPredictiveBack(predictiveBack)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background).semantics { paneTitle = paneLabel },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
    // Previous source retained:
    //             IconButton(onClick = onBack) {
            IconButton(modifier = Modifier.size(48.dp), onClick = onBack) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = stringResource(R.string.social_activity_back))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.social_activity_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text(
                    pluralStringResource(R.plurals.social_activity_session_count, entries.size, entries.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (entries.isNotEmpty()) {
                // Previous source retained (superseded below):
                // IconButton(onClick = { clearCandidates = entries.map { it.id } }) {
                IconButton(modifier = Modifier.size(48.dp), onClick = { clearCandidates = entries.map { it.id } }) {

//Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.social_activity_clear_action))
                    com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.deleteSweep, Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.social_activity_clear_action))
                }
            }
            IconButton(onClick = onOpenSettings) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.settings, semanticLabel = stringResource(R.string.social_activity_settings))
            }
        }

        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {

//Icon(Icons.Outlined.NotificationsNone,
//                            contentDescription = null,
//                            modifier = Modifier.size(30.dp),
//                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
//                        )
                        com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.notifications, Icons.Outlined.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.social_activity_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.social_activity_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.social_activity_settings)) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                eventDays.forEach { (day, dayEntries) ->
                    stickyHeader(key = "activity-day:$day") {
                        Text(
                            if (day == null) unavailableDate else when (NotificationDateRules.relativeDay(day, clock)) {
                                NotificationDateRules.RelativeDay.TODAY -> stringResource(R.string.social_activity_today)
                                NotificationDateRules.RelativeDay.YESTERDAY -> stringResource(R.string.social_activity_yesterday)
                                NotificationDateRules.RelativeDay.ABSOLUTE -> try {
                                    dateFormatter.format(java.util.Date.from(day.atStartOfDay(zone).toInstant()))
                                } catch (_: Exception) { unavailableDate }
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 20.dp, vertical = 10.dp).semantics { heading() },
                        )
                    }
                    items(dayEntries, key = { it.id }, contentType = { "activity" }) { entry ->
                        NotificationActivityRow(entry, dateFormatter, timeFormatter, onDismiss = { service.clear(setOf(entry.id)) })
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(start = 68.dp, end = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        )
                    }
                }

            }
        }
    }

    clearCandidates?.let { reviewedIds ->
        AlertDialog(
            onDismissRequest = { clearCandidates = null },
            title = { Text(stringResource(R.string.social_activity_clear_title)) },
            text = { Text(stringResource(R.string.social_activity_clear_body)) },
            confirmButton = {
                Button(onClick = { service.clear(reviewedIds.toSet()); clearCandidates = null }) { Text(stringResource(R.string.social_activity_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { clearCandidates = null }) { Text(stringResource(R.string.social_activity_cancel)) }
            },
        )
    }
}

@Composable
private fun NotificationActivityRow(
    entry: ChatyEventNotification,
    dateFormatter: java.text.DateFormat,
    timeFormatter: java.text.DateFormat,
    onDismiss: () -> Unit,
) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val timeUnavailable = stringResource(R.string.social_activity_time_unavailable)
    val time = remember(entry.timestamp, dateFormatter, timeFormatter, timeUnavailable) {
        try {
            val date = java.util.Date.from(entry.timestamp)
            "${dateFormatter.format(date)} ${timeFormatter.format(date)}"
        } catch (_: Exception) { timeUnavailable }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {

//Icon(Icons.Outlined.NotificationsNone,
//                contentDescription = null,
//                tint = MaterialTheme.colorScheme.onSecondaryContainer,
//            )
            com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.notifications, Icons.Outlined.NotificationsNone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                entry.title.ifBlank { stringResource(R.string.social_activity_fallback_title) },
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
            )
            if (entry.body.isNotBlank()) Text(
                entry.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
    // Previous source retained:
    //                 maxLines = if (expanded) Int.MAX_VALUE else 4,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                if (entry.body.isNotBlank() || entry.title.isNotBlank()) TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.social_activity_show_less else R.string.social_activity_read_full))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.social_activity_dismiss)) }
            }
        }
    }
}
