package com.example.chat.ui.settings

import androidx.lifecycle.repeatOnLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.chat.ui.theme.LocalAppColors
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.ServiceLocator
import com.example.chat.data.network.TransferDirection
import com.example.chat.data.network.TransferItem
import com.example.chat.data.network.TransferStatus

@Composable
fun NetworkStatusScreen(onBack: () -> Unit) {
    val network by ServiceLocator.networkMonitor.state.collectAsState()

    ChatySettingsPage("Network", "Live connection status", onBack = onBack) {
    Column(Modifier.widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // ND1-46: icon plus text for the connection state.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (network.online) androidx.compose.material.icons.Icons.Rounded.CheckCircle else androidx.compose.material.icons.Icons.Rounded.Warning,
                        contentDescription = null,
                    )
                    Text(if (network.online) "Internet connected" else "Connected, but no internet access", style = MaterialTheme.typography.titleMedium)
                }
                Text("Connection: ${transportLabel(network.transport)} · ${if (network.metered) "Metered" else "Unmetered"}")
                if (network.online) {
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Downlink estimate")
                        Text(if (network.downstreamKbps > 0) formatKbps(network.downstreamKbps.toLong()) else "Measuring…")
                    }
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Uplink estimate")
                        Text(if (network.upstreamKbps > 0) formatKbps(network.upstreamKbps.toLong()) else "Measuring…")
                    }
                }
                Text("These are Android link estimates. Transfer speeds on the Data Usage page are measured from Chaty traffic.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
    }
}

@Composable
fun DataUsageScreen(onBack: () -> Unit) {
    val monitor = ServiceLocator.transferMonitor
    val transfers by monitor.items.collectAsState()
    val totals by monitor.totals.collectAsState()
    // Do not keep the subscriber-gated 1s traffic sampler awake off-screen.
    val trafficOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val trafficState = ServiceLocator.appTrafficMonitor.state
    val appTraffic by androidx.compose.runtime.produceState(trafficState.value, trafficState, trafficOwner) {
        trafficOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            trafficState.collect { value = it }
        }
    }
    // ND1-27/28: split once per list change instead of filtering on every recomposition.
    val (active, recent) = remember(transfers) {
        val activeList = transfers.filter { it.status == TransferStatus.active || it.status == TransferStatus.paused || it.status == TransferStatus.queued }
        val recentList = transfers.filter { it.status == TransferStatus.completed || it.status == TransferStatus.failed || it.status == TransferStatus.cancelled }
        activeList to recentList
    }
    var confirmResetTotals by remember { mutableStateOf(false) }
    var confirmResetTraffic by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current
    val context = LocalContext.current

    ChatySettingsPage("Data Usage", "Usage totals and transfer monitor", onBack = onBack) {
    Column(Modifier.widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data used", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                // ND1-61: plain-text summary first, for screen readers.
                Text(
                    // Prior summary calls omitted Context after the formatter migrated to Android SI units.
                    "Chaty has used " + formatBytes(context, totals.uploadedBytes + totals.downloadedBytes) +
                        " in total: " + formatBytes(context, totals.uploadedBytes) + " uploaded, " +
                        formatBytes(context, totals.downloadedBytes) + " downloaded.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Counts media and messages sent through Chaty's transfer monitor, since install or the last reset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Uploaded")
                    Text(formatBytes(context, totals.uploadedBytes))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Downloaded")
                    Text(formatBytes(context, totals.downloadedBytes))
                }
                Text("By media type", style = MaterialTheme.typography.labelLarge)
                // ND1-43: rows with no data are hidden.
                listOf(
                    "Messages" to totals.messageBytes,
                    "Images" to totals.imageBytes,
                    "Videos" to totals.videoBytes,
                    "Audio" to totals.audioBytes,
                    "Documents" to totals.documentBytes,
                    "Calls" to totals.callBytes,
                    "Other" to totals.otherBytes,
                ).filter { it.second > 0L }.forEach { (label, bytes) -> UsageRow(label, bytes) }
                if (totals.messageBytes + totals.imageBytes + totals.videoBytes + totals.audioBytes + totals.documentBytes + totals.callBytes + totals.otherBytes == 0L) {
                    Text("No data used yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // ND1-25: nothing on this screen leaves the device.
                Text(
                    "This information stays on your device and is not sent anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // ND1-02/41: confirm first; quiet text button instead of a full-width outlined button.
                TextButton(onClick = { confirmResetTotals = true }) {
                    Text("Reset usage totals", color = colors.error)
                }
            }
        }


        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Total Chaty network traffic", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                if (!appTraffic.supported) {
                    Text("Android does not expose per-app traffic counters on this device. You can see overall usage in Android Settings > Network > Data usage.")
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("All uploaded data")
                        Text(formatBytes(context, appTraffic.uploadedBytes))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("All downloaded data")
                        Text(formatBytes(context, appTraffic.downloadedBytes))
                    }
                    Text(
                        "Includes realtime sockets, call signaling, API traffic, media and protocol overhead. Counters are measured by Android for Chaty's app UID.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { confirmResetTraffic = true }) {
                        Text("Reset total app traffic", color = colors.error)
                    }
                }
            }
        }


        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Active transfers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (active.isEmpty()) {
                Text("No active uploads or downloads.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                active.forEach { item -> androidx.compose.runtime.key(item.id) { TransferCard(item) } }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent transfers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (recent.isEmpty()) Text("Completed and interrupted transfers will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            // ND1-08: "Show all" instead of a silent cut-off at 10.
            var showAllRecent by remember { mutableStateOf(false) }
            (if (showAllRecent) recent else recent.take(10)).forEach { item -> androidx.compose.runtime.key(item.id) { TransferCard(item) } }
            if (recent.size > 10) {
                TextButton(onClick = { showAllRecent = !showAllRecent }) {
                    Text(if (showAllRecent) "Show fewer" else "Show all ${recent.size}", color = colors.primary)
                }
            }
        }
        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
    }

    if (confirmResetTotals) {
        com.example.chat.ui.kit.ChatyConfirmationDialog(
            onDismissRequest = { confirmResetTotals = false },
            title = { Text("Reset usage totals?") },
            text = { Text("The uploaded, downloaded and per-type totals go back to zero. This cannot be undone.") },
            dismissButton = { TextButton(onClick = { confirmResetTotals = false }) { Text("Cancel", color = colors.primary) } },
            confirmButton = { TextButton(onClick = { confirmResetTotals = false; monitor.resetTotals() }) { Text("Reset", color = colors.error) } },
        )
    }
    if (confirmResetTraffic) {
        com.example.chat.ui.kit.ChatyConfirmationDialog(
            onDismissRequest = { confirmResetTraffic = false },
            title = { Text("Reset total app traffic?") },
            text = { Text("The total Chaty network traffic counters go back to zero. This cannot be undone.") },
            dismissButton = { TextButton(onClick = { confirmResetTraffic = false }) { Text("Cancel", color = colors.primary) } },
            confirmButton = { TextButton(onClick = { confirmResetTraffic = false; ServiceLocator.appTrafficMonitor.resetTotals() }) { Text("Reset", color = colors.error) } },
        )
    }
}

@Composable
private fun UsageRow(label: String, bytes: Long) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatBytes(context, bytes))
    }
}

@Composable
private fun TransferCard(item: TransferItem) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name.filter { !it.isISOControl() },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // ND1-12/37: direction and outcome as icons with descriptions, beside the words.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        if (item.direction == TransferDirection.upload) androidx.compose.material.icons.Icons.Rounded.ArrowUpward else androidx.compose.material.icons.Icons.Rounded.ArrowDownward,
                        // ND1-38: decorative - direction is announced once by the adjacent
                        // direction label; contentDescription null approved by coordinator.
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    if (item.status == TransferStatus.completed || item.status == TransferStatus.failed) {
                        Icon(
                            if (item.status == TransferStatus.completed) androidx.compose.material.icons.Icons.Rounded.CheckCircle else androidx.compose.material.icons.Icons.Rounded.Error,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    // ND1-38: direction and status are separate, individually labeled elements
                    // instead of one mixed "Upload · In progress" string, so screen readers
                    // announce each on its own; the visual separator stays out of the semantics tree.
                    Text(
                        if (item.direction == TransferDirection.upload) "Upload" else "Download",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    Text(
                        statusLabel(item.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            val total = item.totalBytes?.let { formatBytes(context, it) } ?: "Unknown"
            Text("${formatBytes(context, item.transferredBytes)} of $total transferred")
            Text(
                buildString {
                    append("Remaining: ")
                    append(item.remainingBytes?.let { formatBytes(context, it) } ?: "Unknown")
                    append(" · ")
                    append(formatBytes(context, item.bytesPerSecond))
                    append("/s")
                    item.etaSeconds?.let { append(" · ETA "); append(formatDuration(it)) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ND1-06: match Android Settings, which shows SI (1000-based) units; Formatter is also
// locale-aware, so the old default-locale "%.1f %s" formatting goes away with it.
private fun formatBytes(context: android.content.Context, value: Long): String =
    android.text.format.Formatter.formatShortFileSize(context, value)

// ND1-06: superseded 1024-based implementation retained per owner convention (commented out,
// not deleted); replaced by Formatter.formatShortFileSize above.
// private fun formatBytes(value: Long): String {
//     if (value < 1024) return "$value B"
//     val units = listOf("KB", "MB", "GB", "TB")
//     var amount = value.toDouble()
//     var index = -1
//     while (amount >= 1024 && index < units.lastIndex) {
//         amount /= 1024.0
//         index++
//     }
//     return String.format(java.util.Locale.getDefault(), "%.1f %s", amount, units[index])
// }

private fun formatDuration(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes < 60) "${minutes}m ${rest}s" else "${minutes / 60}h ${minutes % 60}m"
}

private fun transportLabel(transport: String): String = when (transport) {
    "wifi" -> "Wi-Fi"
    "cellular" -> "Mobile data"
    "ethernet" -> "Ethernet"
    "vpn" -> "VPN"
    else -> "Unknown"
}

private fun statusLabel(status: TransferStatus): String = when (status) {
    TransferStatus.queued -> "Waiting"
    TransferStatus.active -> "In progress"
    TransferStatus.paused -> "Paused"
    TransferStatus.completed -> "Completed"
    TransferStatus.failed -> "Failed"
    TransferStatus.cancelled -> "Cancelled"
}

private fun formatKbps(kbps: Long): String =
    if (kbps >= 1000) String.format(java.util.Locale.getDefault(), "%.1f Mbps", kbps / 1000.0) else "$kbps kbps"
