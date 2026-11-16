package com.example.chat.ui.media

import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import com.example.chat.ui.theme.ChatyAsyncImage
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

/** Exact port of lib/features/media/ui/workspace/media_workspace_screen.dart.
 *
 *  Navigation: Dart pushed CustomFilePickerScreen / PdfWorkspaceScreen /
 *  DocumentReaderScreen via Navigator; here the host supplies [onOpenFileExplorer],
 *  [onOpenPdfStudio], [onOpenDocument]. Return-from-push refresh is handled by
 *  the host calling [MediaLibraryService.refreshLibrary].
 *  Service wiring: Dart used the GetIt locator; the owner wires ServiceLocator.
 *  Image.file thumbnails use the shared Coil-backed ChatyAsyncImage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaWorkspaceScreen(
    playerCoordinator: MediaPlayerCoordinator,
    library: MediaLibraryService,
    sanitizer: FileSanitizationService,
    onOpenFileExplorer: () -> Unit = {},
    onOpenPdfStudio: () -> Unit = {},
    onOpenDocument: (MediaFileDescriptor) -> Unit = {},
    onExpandPlayer: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val view = androidx.compose.ui.platform.LocalView.current

    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(library) {
        val listener: () -> Unit = { tick++ }
        library.addListener(listener)
        onDispose { library.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    LaunchedEffect(library) { library.refreshLibrary() }

    val colors = com.example.chat.ui.theme.LocalAppColors.current
    val isDark = colors.isDark
    var cleanupConfirm by remember { mutableStateOf(false) }
    var cleanupEstimate by remember { mutableStateOf<Long?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    // DOC-R4-190: measurement includes mini-player content and its own inner padding.
    var miniPlayerHeightPx by remember { mutableIntStateOf(0) }
    val miniPlayerHeight = with(LocalDensity.current) { miniPlayerHeightPx.toDp() }

    if (cleanupConfirm) AlertDialog(
        onDismissRequest = { cleanupConfirm = false },
        title = { Text("Clean temporary media?") },
        text = {
            // DOC-R4-201 / DOC-R1-052 / DOC-R4-202: scope, exclusions, estimate, no undo promise.
            Text(
                "This deletes files in the Chaty temporary media cache (chaty_media) that are older than 24 hours. " +
                    (cleanupEstimate?.let { "About %.1f MB can be reclaimed now. ".format(it / (1024.0 * 1024.0)) } ?: "Estimating reclaimable space... ") +
                    "Files newer than 24 hours are kept, and your library documents are not touched. This cannot be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                cleanupConfirm = false
                scope.launch {
                    val result = sanitizer.cleanExpiredTempFiles()
                    library.refreshLibrary()
                    // DOC-R4-167: name the exact scope cleaned and report its freed bytes, not library-wide totals.
                    val freedMb = "%.1f MB".format(result.freedBytes / (1024.0 * 1024.0))
                    snackbar.showSnackbar("Cleared the Chaty temporary media cache (chaty_media, files older than 24 hours only): ${result.deleted} deleted ($freedMb freed), ${result.skipped} kept, ${result.failed} failed.")
                }
            }) { Text("Delete old files", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { cleanupConfirm = false }) { Text("Keep files") } },
    )
    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                ),
                title = { Text("Media Studio & Files", fontWeight = FontWeight.W700, fontSize = 16.sp) },
                actions = {
                    IconButton(onClick = {
                        ChatyHapticsPort.light(view)
                        // DOC-R4-201 / DOC-R1-052: confirm with scope, exclusions and an estimate first.
                        cleanupEstimate = null
                        cleanupConfirm = true
                        scope.launch { cleanupEstimate = sanitizer.estimateExpiredTempBytes() }
                    }) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = "Clean temporary cache")
                    }
                    // DOC-R4-187: refresh exposes its updating state instead of allowing redundant scans.
                    IconButton(onClick = { scope.launch { refreshing = true; try { library.refreshLibrary() } finally { refreshing = false } } }, enabled = !refreshing) {
                        Icon(Icons.Rounded.Refresh, contentDescription = if (refreshing) "Refreshing library" else "Refresh library")
                    }
                    Spacer(Modifier.width(6.dp))
                },
            )
        },
    ) { padding ->
        val files = library.files
        // DOC-R4-168 / DOC-R4-205: total and breakdown come from one service snapshot, and an
        // incomplete scan is shown as partial or unmeasured, never as a measured empty library.
        val breakdown = library.getStorageBreakdown()
        val totalBytes = breakdown.values.fold(0L) { sum, v -> if (v > Long.MAX_VALUE - sum) Long.MAX_VALUE else sum + v }
        // DOC-R4-162: audio taps enqueue every audio file in this list - make that scope visible on the row.
        val audioCount = files.count { it.category == MediaCategory.audio }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                // DOC-R4-185: read library before pinned playback without extra focus nodes.
                modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = 0f },
                // DOC-R4-190 prior fixed reserve: contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 90.dp),
                // Scaffold padding above already accounts for its system-inset contract.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = miniPlayerHeight + 16.dp),
            ) {
                item { StorageOverviewCard(isDark, totalBytes, breakdown, measured = breakdown.scanCompleted, complete = breakdown.isComplete) }
                item { Spacer(Modifier.height(20.dp)) }

                item {
                    Text(
                        "STUDIO WORKSPACES",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.W800,
                            color = colors.foregroundSecondary,
                        ),
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    // DOC-R4-194 / DOC-R5-256: below 340dp the two studio cards stack vertically
                    // so neither card clips its subtitle in split-screen.
                    androidx.compose.foundation.layout.BoxWithConstraints {
                        val explorer = @Composable {
                            ActionCard(
                                icon = ChatyIconType.folder,
                                title = "File Explorer",
                                subtitle = "Browse all media",
                                color = colors.info,
                                isDark = isDark,
                                onTap = onOpenFileExplorer,
                            )
                        }
                        val studio = @Composable {
                            ActionCard(
                                icon = ChatyIconType.pdf,
                                title = "PDF Studio",
                                subtitle = "Convert & manage",
                                color = colors.error,
                                isDark = isDark,
                                onTap = onOpenPdfStudio,
                            )
                        }
                        if (maxWidth < 340.dp) {
                            Column(Modifier.fillMaxWidth()) {
                                explorer()
                                Spacer(Modifier.height(12.dp))
                                studio()
                            }
                        } else {
                            Row {
                                Box(Modifier.weight(1f)) { explorer() }
                                Spacer(Modifier.width(12.dp))
                                Box(Modifier.weight(1f)) { studio() }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }

                item {
                    Text(
                        // DOC-R4-161: the library sorts by modifiedAt, not last opened - label it accurately.
                        "RECENTLY MODIFIED FILES",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.W800,
                            color = colors.foregroundSecondary,
                        ),
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }

                if (files.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceSecondary)
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("No files or media found in Chaty storage. Convert or import a file and it will show up here.") }
                    }
                } else {
                    items(files.size, key = { files[it].id }) { idx ->
                        val file = files[idx]
                        // DOC-R4-188: the row names its action - Play for audio, Open otherwise.
                        val rowAction = if (file.category == MediaCategory.audio) "Play ${file.name}" else "Open ${file.name}"
                        Box(
                            Modifier
                                .springInteractiveScale(0.98f)
                                .semantics(mergeDescendants = true) { contentDescription = rowAction }
                                .clickable {
                                    // DOC-R4-164: a descriptor can outlive its file - verify at the
                                    // play/open boundary and refresh instead of failing silently.
                                    if (!file.exists) {
                                        scope.launch {
                                            snackbar.showSnackbar("${file.name} is no longer on disk. Refreshing the library.")
                                            library.refreshLibrary()
                                        }
                                    } else if (file.category == MediaCategory.audio) {
                                        scope.launch {
                                            // DOC-R4-163: a failed play must surface, not vanish into the coroutine.
                                            try {
                                                playerCoordinator.playTrack(
                                                    file,
                                                    queue = files.filter { it.category == MediaCategory.audio },
                                                )
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                                snackbar.showSnackbar("Playback: could not play ${file.name}. Try opening it in an external app.")
                                            }
                                        }
                                    } else {
                                        onOpenDocument(file)
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surfaceSecondary)
                                    .border(
                                        width = 1.dp,
                                        color = colors.borderSubtle,
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FileThumb(file)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        file.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.W700,
                                        fontSize = 13.sp,
                                    )
                                    // DOC-R1-046: 12sp readable metadata baseline (was 11sp).
                                    // DOC-R4-169: explicit type label instead of a bare generic icon category.
                                    // DOC-R4-206: say why the item is recent (its modified time).
                                    Text(
                                        buildString {
                                            append(file.humanReadableSize)
                                            append(" • ")
                                            append(file.extension.uppercase().ifEmpty { file.category.name.uppercase() })
                                            append(" • ")
                                            append(describeModified(file.modifiedAt))
                                            if (file.category == MediaCategory.audio) {
                                                append(" • queues with ")
                                                append(audioCount)
                                                append(" audio")
                                            }
                                        },
                                        fontSize = 12.sp,
                                        color = colors.foregroundSecondary,
                                    )
                                }
                                // DOC-R4-208: audio rows play immediately rather than open, so they get a
                                // play affordance with an action-specific label instead of a generic chevron.
                                if (file.category == MediaCategory.audio) {
                                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.playArrow, semanticLabel = "Play ${file.name}", color = colors.success)
                                } else {
                                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = colors.foregroundSecondary)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Mini Player pinned at bottom if active
            // DOC-R4-190 prior unmeasured wrapper: Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)
                .onSizeChanged { miniPlayerHeightPx = it.height }
                .semantics { isTraversalGroup = true; traversalIndex = 1f }) {
                ChatyMiniPlayer(coordinator = playerCoordinator, onExpand = onExpandPlayer)
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(isDark: Boolean, totalBytes: Long, breakdown: Map<MediaCategory, Long>, measured: Boolean = true, complete: Boolean = true) {
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    val mb = totalBytes / (1024.0 * 1024.0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = colors.shadow,
                spotColor = colors.shadow,
            )
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceSecondary)
            .border(
                width = 1.dp,
                color = colors.borderSubtle,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(20.dp),
    ) {
        // DOC-R4-195: only the header stacks, using card-content width or large text.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f) {
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "Chaty Media Storage",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                        )
                        // DOC-R4-210: this is Chaty usage, never device capacity.
                        Text(
                            "Storage used by Chaty on this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.foregroundSecondary,
                        )
                        // DOC-R4-205: an unreadable directory is not zero storage - say so.
                        if (!measured) {
                            Text("Measuring storage...", style = MaterialTheme.typography.bodySmall, color = colors.foregroundSecondary)
                        } else if (!complete) {
                            Text(
                                "Some folders could not be read. These totals are partial.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${"%.1f".format(mb)} MB Used",
                        fontWeight = FontWeight.W700,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Chaty Media Storage",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                        )
                        // DOC-R4-210: this is Chaty usage, never device capacity.
                        Text(
                            "Storage used by Chaty on this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.foregroundSecondary,
                        )
                        // DOC-R4-205: an unreadable directory is not zero storage - say so.
                        if (!measured) {
                            Text("Measuring storage...", style = MaterialTheme.typography.bodySmall, color = colors.foregroundSecondary)
                        } else if (!complete) {
                            Text(
                                "Some folders could not be read. These totals are partial.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    Text(
                        "${"%.1f".format(mb)} MB Used",
                        fontWeight = FontWeight.W700,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // Multi-segmented bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            StorageSegment(breakdown[MediaCategory.image] ?: 0, totalBytes, colors.info)
            StorageSegment(breakdown[MediaCategory.video] ?: 0, totalBytes, colors.accent)
            StorageSegment(breakdown[MediaCategory.audio] ?: 0, totalBytes, colors.success)
            StorageSegment(breakdown[MediaCategory.pdf] ?: 0, totalBytes, colors.error)
            StorageSegment(breakdown[MediaCategory.document] ?: 0, totalBytes, colors.warning)
            StorageSegment(breakdown[MediaCategory.archive] ?: 0, totalBytes, MaterialTheme.colorScheme.secondary)
            StorageSegment(breakdown[MediaCategory.other] ?: 0, totalBytes, MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(14.dp))
        // DOC-R4-184 original bytes-only text:
        // Text(breakdown.entries.joinToString("; ") { "${it.key.name}: ${it.value} bytes" }, style = MaterialTheme.typography.bodySmall)
        Column {
            breakdown.forEach { (category, bytes) ->
                val share = storageCategoryShare(bytes, totalBytes)
                val proportion = if (!measured || share == null) "proportion unavailable" else
                    java.text.NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(share)
                Text("${category.name}: $bytes bytes, $proportion", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot("Photos", colors.info)
            LegendDot("Videos", colors.accent)
            LegendDot("Audio", colors.success)
            LegendDot("PDFs", colors.error)
            LegendDot("Docs", colors.warning)
            LegendDot("Archives", MaterialTheme.colorScheme.secondary)
            LegendDot("Other", MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StorageSegment(bytes: Long, total: Long, color: Color) {
    if (total == 0L || bytes == 0L) return
    Box(modifier = Modifier.weight(bytes.toFloat()).fillMaxSize().background(color))
}

@Composable
private fun LegendDot(label: String, color: Color) {
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = colors.foregroundSecondary)
    }
}

@Composable
private fun ActionCard(
    icon: ChatyIconType,
    title: String,
    subtitle: String,
    color: Color,
    isDark: Boolean,
    onTap: () -> Unit,
) {
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    var cardFocused by remember { mutableStateOf(false) }
    // DOC-R4-181: one merged button node so the icon, title and subtitle read as a single command.
    // DOC-R4-181 original: Box(Modifier.springInteractiveScale(0.96f).semantics(mergeDescendants = true) { role = Role.Button }.clickable(onClick = onTap)) {
    Box(Modifier.springInteractiveScale(0.96f)
        .onFocusChanged { cardFocused = it.isFocused }
        .then(if (cardFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier)
        .semantics(mergeDescendants = true) {
        role = Role.Button
        contentDescription = "$title. $subtitle"
    }.clickable(onClickLabel = title, onClick = onTap)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = colors.shadow,
                    spotColor = colors.shadow,
                )
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceSecondary)
                .border(
                    width = 1.dp,
                    color = colors.borderSubtle,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { ChatyMediaIcon(icon = icon, size = 20.dp, color = color) }
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.W700, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            // DOC-R1-046: 12sp readable metadata baseline (was 11sp).
            Text(subtitle, fontSize = 12.sp, color = colors.foregroundSecondary)
        }
    }
}

@Composable
private fun FileThumb(file: MediaFileDescriptor) {
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    if (file.category == MediaCategory.image && file.exists) {
        ChatyAsyncImage(
            url = File(file.path).toURI().toString(),
            contentDescription = file.name,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            error = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.gallery, semanticLabel = null) },
        )
        return
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(colors.foregroundSecondary.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ChatyMediaIcon(
            icon = when (file.category) {
                MediaCategory.audio -> ChatyIconType.audio
                MediaCategory.pdf -> ChatyIconType.pdf
                else -> ChatyIconType.document
            },
            size = 18.dp,
            color = colors.info, // Colors.blueAccent
        )
    }
}


/** DOC-R4-206: relative "modified" label for the recently-modified list (DOC-R4-161). */
private fun describeModified(instant: java.time.Instant): String {
    val age = java.time.Duration.between(instant, java.time.Instant.now())
    return when {
        age.isNegative || age.toMinutes() < 1 -> "Modified just now"
        age.toHours() < 1 -> "Modified ${age.toMinutes()} min ago"
        age.toDays() < 1 -> "Modified ${age.toHours()} h ago"
        age.toDays() < 30 -> "Modified ${age.toDays()} d ago"
        else -> "Modified " + java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(java.time.ZoneId.systemDefault()).format(instant)
    }
}

// Superseded source retained inactive:
//     val isDark = isSystemInDarkTheme()
//         containerColor = if (isDark) Color(0xFF0F131A) else Color(0xFFF8FAFC),
//                     containerColor = if (isDark) Color(0xFF131822) else Color.White,
//                             color = Color.Gray,
//                                 color = Color(0xFF38BDF8),
//                                 color = Color(0xFFEF4444),
//                             color = Color.Gray,
//                                 .background(if (isDark) Color(0xFF161C26) else Color.White)
//                                     .background(if (isDark) Color(0xFF161C26) else Color.White)
//                                         color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
//                                         color = if (isDark) Color.White.copy(alpha = 0.54f) else Color.Black.copy(alpha = 0.54f),
//                                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.playArrow, semanticLabel = "Play ${file.name}", color = Color(0xFF10B981))
//                                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = Color.Gray)
//                 ambientColor = Color.Black.copy(alpha = if (isDark) 0.25f else 0.04f),
//                 spotColor = Color.Black.copy(alpha = if (isDark) 0.25f else 0.04f),
//             .background(if (isDark) Color(0xFF161C26) else Color.White)
//                 color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
//                             color = Color.Gray,
//                             Text("Measuring storage...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
//                             color = Color.Gray,
//                             Text("Measuring storage...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
//             StorageSegment(breakdown[MediaCategory.image] ?: 0, totalBytes, Color(0xFF38BDF8))
//             StorageSegment(breakdown[MediaCategory.video] ?: 0, totalBytes, Color(0xFFA855F7))
//             StorageSegment(breakdown[MediaCategory.audio] ?: 0, totalBytes, Color(0xFF10B981))
//             StorageSegment(breakdown[MediaCategory.pdf] ?: 0, totalBytes, Color(0xFFEF4444))
//             StorageSegment(breakdown[MediaCategory.document] ?: 0, totalBytes, Color(0xFFF59E0B))
//             LegendDot("Photos", Color(0xFF38BDF8))
//             LegendDot("Videos", Color(0xFFA855F7))
//             LegendDot("Audio", Color(0xFF10B981))
//             LegendDot("PDFs", Color(0xFFEF4444))
//             LegendDot("Docs", Color(0xFFF59E0B))
//         Text(label, fontSize = 10.sp, color = Color.Gray)
//                     ambientColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.04f),
//                     spotColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.04f),
//                 .background(if (isDark) Color(0xFF161C26) else Color.White)
//                     color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
//             Text(subtitle, fontSize = 12.sp, color = Color.Gray)
//             .background(Color.Gray.copy(alpha = 0.15f), CircleShape),
//             color = Color(0xFF448AFF), // Colors.blueAccent
