package com.example.chat.ui.media

import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToPhotos
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import com.example.chat.ui.theme.ChatyAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/** Exact port of lib/features/media/ui/picker/custom_file_picker_screen.dart.
 *
 *  Grid/list picker over MediaLibraryService with multi-select, search,
 *  category filter pills, device browse (file_picker -> OpenMultipleDocuments),
 *  and an attachment summary sheet. Result delivery: Dart popped the route
 *  with the file list; here [onSendAttachments]. NOTE (faithful quirk): the
 *  Dart caption field is collected but never returned - the sheet only pops a
 *  bool, so the caption is dropped there too.
 *  Blur: floating dock BackdropFilter -> translucent approximation, same Haze
 *  story as the mini player (dep pending).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomFilePickerScreen(
    libraryService: MediaLibraryService,
    allowMultiple: Boolean = true,
    initialCategory: MediaCategory? = null,
    title: String = "Files & Media",
    isWorkspaceMode: Boolean = false,
    onOpenDocument: (MediaFileDescriptor) -> Unit = {},
    onSendAttachments: (List<MediaFileDescriptor>) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val searchKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(libraryService) {
        val listener: () -> Unit = { tick++ }
        libraryService.addListener(listener)
        onDispose { libraryService.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") tick

    var searchText by remember(libraryService) { mutableStateOf(boundedPickerText(libraryService.searchQuery)) }
    var selectedPaths by remember(libraryService) { mutableStateOf<Set<String>>(emptySet()) }
    var isGridView by remember { mutableStateOf(true) }
    val listScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridScrollState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var isSelectionMode by remember(libraryService) { mutableStateOf(false) }
    var summaryFiles by remember(libraryService) { mutableStateOf<List<MediaFileDescriptor>?>(null) }

    var refreshError by remember(libraryService) { mutableStateOf<String?>(null) }
    var refreshAttempt by remember { mutableIntStateOf(0) }
    var importing by remember(libraryService) { mutableStateOf(false) }
    var importProgress by remember(libraryService) { mutableStateOf("") }
    var importStopping by remember(libraryService) { mutableStateOf(false) }
    var importNotice by remember(libraryService) { mutableStateOf<String?>(null) }
    var importHelpSeen by remember(libraryService) { mutableStateOf(false) }
    var showImportHelp by remember { mutableStateOf(false) }
    var importJob by remember(libraryService) { mutableStateOf<Job?>(null) }
    val importActive = remember(libraryService) { java.util.concurrent.atomic.AtomicBoolean(true) }
    val importFiles = remember(libraryService) { mutableListOf<File>() }
    val transferredPaths = remember(libraryService) { mutableSetOf<String>() }
    DisposableEffect(libraryService) {
        onDispose {
            importActive.set(false)
            importJob?.cancel()
            if (!isWorkspaceMode) importFiles.filter { it.path !in transferredPaths }.forEach { it.delete() }
        }
    }
    val allFiles = remember(libraryService, tick) { libraryService.files }
    // val visibleFiles = remember(libraryService, tick) { libraryService.filteredFiles }
    var browseOrder by remember(libraryService) { mutableStateOf(MediaBrowseOrder.Newest) }
    var sortExpanded by remember { mutableStateOf(false) }
    var detailsFile by remember(libraryService) { mutableStateOf<MediaFileDescriptor?>(null) }
    val visibleFiles = remember(libraryService, tick, browseOrder) { orderMediaFiles(libraryService.filteredFiles, browseOrder) }
    LaunchedEffect(libraryService, initialCategory, refreshAttempt) {
        refreshError = null
        try {
            libraryService.setFilter(initialCategory)
            libraryService.refreshLibrary()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { refreshError = "Could not refresh files. Try again." }
    }

    fun toggleSelect(item: MediaFileDescriptor) {
        if (!item.exists || !File(item.path).canRead()) {
            scope.launch { snackbar.showSnackbar("This file is no longer available. Refresh the library.") }
            return
        }
        ChatyHapticsPort.selection(view)
        val paths = selectedPaths.toMutableSet()
        if (paths.contains(item.path)) {
            paths.remove(item.path)
            if (paths.isEmpty()) isSelectionMode = false
        } else {
            if (!allowMultiple) paths.clear()
            paths.add(item.path)
            isSelectionMode = true
        }
        selectedPaths = paths
    }

    fun confirmAndReturn(files: List<MediaFileDescriptor>) {
        if (files.isEmpty()) return
        summaryFiles = files
    }

    // file_picker allowMultiple -> OpenMultipleDocuments / OpenDocument
    val devicePicker = rememberLauncherForActivityResult(
        if (allowMultiple) ActivityResultContracts.OpenMultipleDocuments()
        else ActivityResultContracts.OpenDocument(),
    ) { result ->
        val uris = (when (result) {
            is List<*> -> result.filterIsInstance<android.net.Uri>()
            is android.net.Uri -> listOf(result)
            else -> emptyList()
        }).distinct()
        if (uris.isEmpty() || !importActive.get()) return@rememberLauncherForActivityResult
        if (importing) return@rememberLauncherForActivityResult
        importing = true
        importStopping = false
        importNotice = null
        importJob = scope.launch {
            val sessionFiles = mutableListOf<File>()
            val completedBatch = mutableListOf<Pair<MediaFileDescriptor, File>>()
            var itemNumber = 0
            var itemLabel = "attachment"
            try {
                val context = view.context
                val descriptors = withContext(Dispatchers.IO) {
                    uris.mapIndexed { index, uri ->
                        coroutineContext.ensureActive()
                        itemNumber = index + 1
                        itemLabel = "attachment" // A failing new provider item must not inherit the prior label.
                        requirePickerContentScheme(uri.scheme)
                        withContext(Dispatchers.Main) { importProgress = "Importing ${index + 1} of ${uris.size}" }
                        val displayName = runCatching {
                            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            }
                        }.getOrNull() ?: "attachment"
                        requirePickerProviderName(displayName)
                        val safeName = FileSanitizationService { context.cacheDir }.sanitizeFileName(displayName)
                        itemLabel = pickerFileLabel(safeName)
//                         val out = File(context.cacheDir, "devpick_${java.util.UUID.randomUUID()}_$safeName")
//                         sessionFiles += out
//                         try {
//                             context.contentResolver.openInputStream(uri)?.use { input ->
//                                 out.outputStream().use { output ->
//                                     var lastReported = 0L
//                                     copyMediaBounded(input, output, FileSanitizationService.MAX_SAFE_FILE_SIZE.toLong()) { bytes ->
//                                         if (bytes - lastReported >= 256 * 1024) {
//                                             lastReported = bytes
//                                             withContext(Dispatchers.Main) { importProgress = "Importing ${index + 1} of ${uris.size}: ${android.text.format.Formatter.formatFileSize(context, bytes)} copied" }
//                                         }
//                                     }
//                                 }
//                             } ?: error("Unavailable source")
//                             require(out.length() > 0) { "Empty source" }
                        // Old direct devpick_<uuid> file writing is replaced with an exclusive hidden
                        // .part copy. Visible filenames are assigned only after the whole batch closes.
                        var lastReported = 0L
                        val copy = copyPickerImport(context.cacheDir, FileSanitizationService.MAX_SAFE_FILE_SIZE.toLong(),
                            openSource = { context.contentResolver.openInputStream(uri) },
                            onPendingCreated = { sessionFiles += it }) { bytes ->
                            if (bytes - lastReported >= 256 * 1024) {
                                lastReported = bytes
                                withContext(Dispatchers.Main) { importProgress = "Importing ${index + 1} of ${uris.size}: ${android.text.format.Formatter.formatFileSize(context, bytes)} copied" }
                            }
                        }
                        val out = copy.file
                        try {
                            // Copy, byte cap, nonempty check and both stream closes completed in copyPickerImport.
                            // Original filePath = safeName let suffix fallback claim an unverified type.
                            // Detect from bytes only. Unknown formats stay generic, not a decoder promise.
                            val classification = out.inputStream().use { stream ->
                                FileTypeDetector.classifyBytes(readPickerHeader(stream))
                            }
                            val importName = pickerDetectedImportName(safeName, classification)
                            MediaFileDescriptor(
                                id = out.absolutePath, name = importName, path = out.absolutePath,
                                sizeBytes = copy.bytesCopied, mimeType = classification.mimeType,
                                category = classification.category,
                                modifiedAt = java.time.Instant.ofEpochMilli(out.lastModified()),
                                metadata = mapOf("source" to "Device document", "providerName" to safeName,
                                    "typeEvidence" to if (classification.mimeType == "application/octet-stream") "Unknown bytes; generic file" else "Header signature"),
                            ).also { completedBatch += it to out }
                        } catch (error: Throwable) { out.delete(); throw error }
                    }
                    coroutineContext.ensureActive()
                    completedBatch.map { (descriptor, pending) ->
                        val finalFile = File(context.cacheDir, "devpick_${java.util.UUID.randomUUID()}_${descriptor.name}")
                        check(!finalFile.exists()) { "Import output collision" }
                        if (!pending.renameTo(finalFile)) throw PickerImportFailure(PickerImportFailureKind.Storage)
                        sessionFiles += finalFile
                        descriptor.copy(id = finalFile.path, path = finalFile.path)
                    }
                }
                coroutineContext.ensureActive()
                check(importActive.get()) { "Import session ended" }
                importFiles += sessionFiles
                if (isWorkspaceMode) libraryService.refreshLibrary() else confirmAndReturn(descriptors)
                importNotice = "Imported ${descriptors.size} of ${uris.size} device documents. These are local copies, not live links."
            } catch (cancelled: CancellationException) {
                val remainingCopies = withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    sessionFiles.forEach { it.delete() }; sessionFiles.count { it.exists() }
                }
                if (importActive.get()) importNotice = if (remainingCopies == 0) "Import canceled. This batch was not added."
                    else "Import canceled. $remainingCopies temporary copies could not be removed. This batch was not added."
                throw cancelled
            } catch (failure: Exception) {
                val remainingCopies = withContext(Dispatchers.IO) { sessionFiles.forEach { it.delete() }; sessionFiles.count { it.exists() } }
                if (importActive.get()) {
                    val problem = when ((failure as? PickerImportFailure)?.kind) {
                        PickerImportFailureKind.Unavailable -> "The document provider could not open this item. Choose it again."
                        PickerImportFailureKind.Permission -> "Read access was lost. Choose the file again to grant access."
                        PickerImportFailureKind.Storage -> "The local copy could not be written. Check device storage, free space if needed, and try again."
                        PickerImportFailureKind.TooLarge -> "This file exceeds the 100 MB per-file limit. Choose a smaller file."
                        PickerImportFailureKind.Empty -> "This file returned no bytes. Choose a nonempty file."
                        PickerImportFailureKind.Read -> "The document provider stopped or could not finish reading. Choose the file again."
                        null -> "This file could not be imported. Choose it again."
                    }
                    importNotice = "Item $itemNumber of ${uris.size} ($itemLabel): $problem No files from this batch were added." +
                        if (remainingCopies > 0) " $remainingCopies temporary copies could not be removed." else ""
                }
            } finally { importing = false; importStopping = false; importProgress = "" }
        }
    }

    fun pickFromDeviceStorage() {
        if (importing) return
        ChatyHapticsPort.light(view)
        if (!importHelpSeen) showImportHelp = true else devicePicker.launch(arrayOf("*/*"))
    }

    fun openFile(item: MediaFileDescriptor) {
        if (item.path.startsWith("demo/") || !item.exists) {
            scope.launch { snackbar.showSnackbar("This file is no longer available. Refresh the library.") }
            return
        }
        onOpenDocument(item)
    }

    if (showImportHelp) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showImportHelp = false },
        title = { Text("Import device documents") },
        text = { Text("Chaty makes private local copies, not live links to the original documents. Copies are limited to 100 MB per file. If one item fails, the whole new batch is canceled; your existing selection stays unchanged.") },
        confirmButton = { TextButton(onClick = { showImportHelp = false; importHelpSeen = true; devicePicker.launch(arrayOf("*/*")) }) { Text("Choose files") } },
        dismissButton = { TextButton(onClick = { showImportHelp = false }) { Text("Cancel") } },
    )
    detailsFile?.let { file ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { detailsFile = null },
            title = { Text("File details") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pickerFileLabel(file.name), style = MaterialTheme.typography.titleMedium.copy(
                    textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrLtr))
                Text("Size: ${file.humanReadableSize} (${file.sizeBytes} bytes)")
                Text("Type: ${file.mimeType}")
                Text("Category: ${file.category.name}")
                (file.metadata["source"] as? String)?.let { Text("Source: $it") }
                (file.metadata["providerName"] as? String)?.let { Text("Provider label (unverified): ${pickerFileLabel(it)}") }
                (file.metadata["typeEvidence"] as? String)?.let { Text("Type evidence: $it") }
                Text("Modified: ${java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM).withZone(java.time.ZoneId.systemDefault()).format(file.modifiedAt)}")
                Text("Location: ${file.path}")
            } },
            confirmButton = { TextButton(onClick = { detailsFile = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close details") } },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    ),
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Back")
                        }
                    },
                    title = {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.W700, letterSpacing = (-0.2).sp,
                            ),
                        )
                    },
                    actions = {
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                                contentDescription = if (isGridView) "List view" else "Grid view",
                            )
                        }
                        IconButton(enabled = !importing, onClick = { pickFromDeviceStorage() }) {
                            Icon(Icons.Rounded.AddToPhotos, contentDescription = "Browse device files")
                        }
                        Spacer(Modifier.width(6.dp))
                    },
                )
                Text(if (isWorkspaceMode) "Open files, or select files to share" else "Select files to send", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                if (!allowMultiple) Text("Choose one file", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                // Search input
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TextField(
                        value = searchText,
                        onValueChange = {
                            val bounded = boundedPickerText(it)
                            searchText = bounded
                            libraryService.setSearchQuery(bounded)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).then(Modifier.focusRequester(searchFocus)),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { searchKeyboard?.hide() }),
                        label = { Text("Search files") },
                        placeholder = { Text("Search files, documents, photos...") },
                        leadingIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search, semanticLabel = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchText = ""
                                    libraryService.setSearchQuery("")
                                    searchFocus.requestFocus()
                                }) {
                                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.clear, semanticLabel = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Category Filter Pills
                FilterPills(
                    isDark = isDark,
                    active = libraryService.activeCategoryFilter,
                    onSelect = { cat ->
                        ChatyHapticsPort.selection(view)
                        libraryService.setFilter(cat)
                    },
                )
                importNotice?.let { notice ->
                    Text(notice, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(enabled = !importing, onClick = { pickFromDeviceStorage() }) { Text("Browse again") }
                        TextButton(onClick = { importNotice = null }) { Text("Dismiss notice") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${visibleFiles.size} ${if (searchText.isNotBlank()) "matches" else "files"} · ${libraryService.activeCategoryFilter?.name ?: "All categories"}",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                    Box {
                        TextButton(onClick = { sortExpanded = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Sort: ${browseOrder.label}") }
                        androidx.compose.material3.DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            MediaBrowseOrder.entries.forEach { order ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text(order.label) },
                                    onClick = { browseOrder = order; sortExpanded = false },
                                    modifier = Modifier.heightIn(min = 48.dp).semantics { selected = browseOrder == order })
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                importing -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(if (importStopping) "Stopping import and removing incomplete copies" else importProgress, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(enabled = !importStopping, onClick = { importStopping = true; importJob?.cancel() }) { Text(if (importStopping) "Stopping" else "Cancel import") }
                }
                refreshError != null -> Column(Modifier.align(Alignment.Center)) {
                    Text(refreshError!!)
                    TextButton(onClick = { refreshAttempt++ }) { Text("Retry") }
                }
                libraryService.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text("Scanning files", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                }
                visibleFiles.isEmpty() -> {
                    if (searchText.isNotEmpty() || libraryService.activeCategoryFilter != null) {
                        Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No matching files in ${libraryService.activeCategoryFilter?.name ?: "all categories"}")
                            Text(if (searchText.isNotBlank()) "Try another search or clear the category filter." else "This category has no files. Other categories may still contain files.")
                            TextButton(onClick = { searchText = ""; libraryService.setSearchQuery(""); libraryService.setFilter(null) }) { Text("Clear search and filters") }
                        }
                    } else EmptyState(isDark, onBrowse = { pickFromDeviceStorage() })
                }
                else -> {
                    val items = visibleFiles
                    if (isGridView) {
                        LazyVerticalGrid(
                            state = gridScrollState,
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 12.dp,
                                bottom = if (selectedPaths.isNotEmpty()) 90.dp else 20.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(items.size, key = { items[it].path }) { index ->
                                val item = items[index]
                                GridItem(
                                    item = item,
                                    isSelected = selectedPaths.contains(item.path),
                                    showCheck = isSelectionMode || selectedPaths.contains(item.path),
                                    selectsOnTap = isSelectionMode || !isWorkspaceMode,
                                    isDark = isDark,
                                    // Former fixed aspectRatio(0.85f) could not grow with larger text.
                                    modifier = Modifier.heightIn(min = 208.dp),
                                    onTap = {
                                        if (isSelectionMode || !isWorkspaceMode) toggleSelect(item)
                                        else openFile(item)
                                    },
                                    onLongPress = { toggleSelect(item) },
                                    onDetails = { detailsFile = item },
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listScrollState,
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 12.dp,
                                bottom = if (selectedPaths.isNotEmpty()) 90.dp else 20.dp,
                            ),
                        ) {
                            items(items.size, key = { items[it].path }) { index ->
                                val item = items[index]
                                ListItemRow(
                                    item = item,
                                    isSelected = selectedPaths.contains(item.path),
                                    showCheck = isSelectionMode || selectedPaths.contains(item.path),
                                    selectsOnTap = isSelectionMode || !isWorkspaceMode,
                                    isDark = isDark,
                                    onTap = {
                                        if (isSelectionMode || !isWorkspaceMode) toggleSelect(item)
                                        else openFile(item)
                                    },
                                    onLongPress = { toggleSelect(item) },
                                    onDetails = { detailsFile = item },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }

                }
            }
                    if (selectedPaths.isNotEmpty()) {
                        FloatingActionDock(
                            count = selectedPaths.size,
                            isWorkspaceMode = isWorkspaceMode,
                            isDark = isDark,
                            onDeselect = {
                                val previous = selectedPaths
                                selectedPaths = emptySet(); isSelectionMode = false
                                scope.launch {
                                    if (snackbar.showSnackbar("${previous.size} files deselected", actionLabel = "Undo") == androidx.compose.material3.SnackbarResult.ActionPerformed && selectedPaths.isEmpty()) {
                                        selectedPaths = previous.intersect(libraryService.files.map { it.path }.toSet())
                                        isSelectionMode = selectedPaths.isNotEmpty()
                                    }
                                }
                            },
                            onConfirm = {
                                // Original silently omitted vanished selected IDs:
                                // confirmAndReturn(allFiles.filter { selectedPaths.contains(it.path) })
                                val snapshot = snapshotPickerSelection(allFiles, selectedPaths.toSet())
                                when {
                                    snapshot.missingPaths.isNotEmpty() -> {
                                        selectedPaths = selectedPaths - snapshot.missingPaths
                                        isSelectionMode = selectedPaths.isNotEmpty()
                                        scope.launch { snackbar.showSnackbar("${snapshot.missingPaths.size} selected files are no longer in the library. Review the remaining selection before continuing.") }
                                    }
                                    snapshot.duplicatePaths.isNotEmpty() -> {
                                        scope.launch { snackbar.showSnackbar("The library has duplicate entries for selected files. Refresh before continuing.") }
                                    }
                                    else -> confirmAndReturn(snapshot.files)
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
                        )
                    }
        }
    }

    // Attachment summary sheet
    summaryFiles?.let { files ->
        var delivering by remember(files) { mutableStateOf(false) }
        // Caption field retained below as a comment until the attachment contract supports captions.
        ModalBottomSheet(tonalElevation = 0.dp, dragHandle = null,
            onDismissRequest = { summaryFiles = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = com.example.chat.ui.kit.ChatyModalShape,
        ) {
            Column(modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 4.dp)
                        .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${files.size} attachments, ${android.text.format.Formatter.formatFileSize(view.context, pickerTotalBytes(files))} total"
                }, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (isWorkspaceMode) "Share" else "Send"} ${files.size} ${if (files.size == 1) "Attachment" else "Attachments"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700),
                    )
                    Spacer(Modifier.weight(1f))
                    val totalSize = android.text.format.Formatter.formatFileSize(view.context, pickerTotalBytes(files))
                    Text(
                        "$totalSize total",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(min = 160.dp)) {
                    items(files.size, key = { files[it].path }) { index ->
                        val f = files[index]
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            ItemPreview(f, previewDp = 72)
                            Spacer(Modifier.height(4.dp))
                            Text(pickerFileLabel(f.name), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrLtr), fontWeight = FontWeight.W600)
                            Text(f.mimeType, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text(if (f.extension.isBlank()) "No filename suffix" else "Suffix: .${boundedPickerText(pickerFileLabel(f.extension), 24)}",
                                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            TextButton(enabled = !delivering, modifier = Modifier.heightIn(min = 48.dp), onClick = {
                                selectedPaths = selectedPaths - f.path
                                summaryFiles = files.filterIndexed { i, _ -> i != index }.takeIf { it.isNotEmpty() }
                                if (selectedPaths.isEmpty()) isSelectionMode = false
                            }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                /* Captions are not returned by the file-attachment callback. Retained for a future contract migration:
                var caption by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a message or caption...") },
                    shape = RoundedCornerShape(14.dp),
                )
                */
                Text("File attachments are sent without a caption.", style = MaterialTheme.typography.bodySmall)
                Text("Listed in library order, not selection-tap order.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { summaryFiles = null }) { Text("Change selection") }

                Spacer(Modifier.height(16.dp))
                Row {
                    OutlinedButton(
                        onClick = { summaryFiles = null },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Cancel") }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        enabled = !delivering,
                        onClick = {
                            if (delivering) return@Button
                            delivering = true
                            // Original synchronous exists/canRead preflight and handoff retained below.
                            //                             if (files.any { !it.exists || !File(it.path).canRead() }) {
                            //                                 delivering = false
                            //                                 scope.launch { snackbar.showSnackbar("An attachment is no longer available. Refresh and pick it again.") }
                            //                             } else try {
                            //                                 if (isWorkspaceMode) {
                            //                                     val uris = ArrayList(files.map { descriptor -> androidx.core.content.FileProvider.getUriForFile(view.context, "${view.context.packageName}.fileprovider", File(descriptor.path)) })
                            //                                     val intent = android.content.Intent(if (uris.size == 1) android.content.Intent.ACTION_SEND else android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                            //                                         type = files.map { it.mimeType }.distinct().singleOrNull() ?: "*/*"
                            //                                         if (uris.size == 1) putExtra(android.content.Intent.EXTRA_STREAM, uris.first()) else putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                            //                                         addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            //                                         clipData = android.content.ClipData.newRawUri("Attachments", uris.first()).also { data -> uris.drop(1).forEach { data.addItem(android.content.ClipData.Item(it)) } }
                            //                                     }
                            //                                     view.context.startActivity(android.content.Intent.createChooser(intent, "Share files"))
                            //                                 } else onSendAttachments(files)
                            //                                 transferredPaths += files.map { it.path }
                            //                                 summaryFiles = null
                            //                                 selectedPaths = emptySet()
                            //                                 isSelectionMode = false
                            //                             } catch (_: Exception) {
                            //                                 delivering = false
                            //                                 scope.launch { snackbar.showSnackbar("Could not open the attachment action. Try again.") }
                            //                             }
                            scope.launch {
                                try {
                                    val states = withContext(Dispatchers.IO) { files.map(::pickerSummaryFileState) }
                                    coroutineContext.ensureActive()
                                    // Cancel/change-selection/dispose during IO revokes this confirmation.
                                    if (!importActive.get() || summaryFiles !== files) return@launch
                                    if (states.any { it == PickerSummaryFileState.Unavailable }) {
                                        snackbar.showSnackbar("An attachment is no longer available. Refresh and pick it again.")
                                    } else if (states.any { it == PickerSummaryFileState.ChangedSize }) {
                                        snackbar.showSnackbar("An attachment changed size. Cancel this review and choose it again before sending.")
                                    } else {
                                        if (isWorkspaceMode) {
                                            val uris = ArrayList(files.map { descriptor -> androidx.core.content.FileProvider.getUriForFile(view.context, "${view.context.packageName}.fileprovider", File(descriptor.path)) })
                                            val intent = android.content.Intent(if (uris.size == 1) android.content.Intent.ACTION_SEND else android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                                type = files.map { it.mimeType }.distinct().singleOrNull() ?: "*/*"
                                                if (uris.size == 1) putExtra(android.content.Intent.EXTRA_STREAM, uris.first()) else putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                clipData = android.content.ClipData.newRawUri("Attachments", uris.first()).also { data -> uris.drop(1).forEach { data.addItem(android.content.ClipData.Item(it)) } }
                                            }
                                            view.context.startActivity(android.content.Intent.createChooser(intent, "Share files"))
                                        } else onSendAttachments(files)
                                        transferredPaths += files.map { it.path }
                                        summaryFiles = null
                                        selectedPaths = emptySet()
                                        isSelectionMode = false
                                    }
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) {
                                    snackbar.showSnackbar("Could not open the attachment action. Your selection was kept. Check the destination before trying again.")
                                } finally { delivering = false }
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(if (delivering) "Preparing" else if (isWorkspaceMode) "Share ${files.size} files" else "Send ${files.size} files") }
                }
            }
        }
    }
}

@Composable
private fun FilterPills(isDark: Boolean, active: MediaCategory?, onSelect: (MediaCategory?) -> Unit) {
    val categories = listOf<Pair<MediaCategory?, String>>(
        null to "All",
        MediaCategory.image to "Images",
        MediaCategory.video to "Videos",
        MediaCategory.audio to "Audio",
        MediaCategory.pdf to "PDFs",
        MediaCategory.document to "Docs",
        MediaCategory.archive to "Archives",
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        items(categories.size) { idx ->
            val (cat, label) = categories[idx]
            val isSelected = active == cat
            Box(
                modifier = Modifier
                    .springInteractiveScale(0.96f)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(cat) })
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else (MaterialTheme.colorScheme.surfaceVariant),
                    )
                    .border(
                        1.dp,
                        if (isSelected) Color.Transparent
                        else (MaterialTheme.colorScheme.outlineVariant),
                        RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.W700 else FontWeight.W500,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridItem(
    item: MediaFileDescriptor,
    isSelected: Boolean,
    showCheck: Boolean,
    selectsOnTap: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        modifier = modifier
            .springInteractiveScale(0.97f)
            // Original unconditional checkbox role did not describe workspace Open taps.
            .combinedClickable(onClick = onTap,
                onClickLabel = if (selectsOnTap) { if (isSelected) "Deselect file" else "Select file" } else "Open file",
                onLongClick = onLongPress, onLongClickLabel = if (isSelected) "Deselect file" else "Select file",
                role = if (selectsOnTap) Role.Checkbox else Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = "${pickerFileLabel(item.name)}, ${item.mimeType}, ${item.humanReadableSize}"
                selected = isSelected
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
            .shadow(
                8.dp, RoundedCornerShape(14.dp),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.04f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.2f else 0.04f),
            )
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else (MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.heightIn(min = 96.dp), contentAlignment = Alignment.Center) { ItemPreview(item, previewDp = 96) }
            Spacer(Modifier.height(4.dp))
            Text(
                pickerFileLabel(item.name),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrLtr),
                fontWeight = FontWeight.W600,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(item.mimeType, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                item.humanReadableSize,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDetails, modifier = Modifier.align(Alignment.TopStart).size(48.dp).semantics { contentDescription = "Details for ${pickerFileLabel(item.name)}" }) { Text("i", color = MaterialTheme.colorScheme.primary) }
        if (showCheck) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.4f),
                        CircleShape,
                    )
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.check, semanticLabel = null, color = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListItemRow(
    item: MediaFileDescriptor,
    isSelected: Boolean,
    showCheck: Boolean,
    selectsOnTap: Boolean,
    isDark: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDetails: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springInteractiveScale(0.98f)
            // Original unconditional checkbox role did not describe workspace Open taps.
            .combinedClickable(onClick = onTap,
                onClickLabel = if (selectsOnTap) { if (isSelected) "Deselect file" else "Select file" } else "Open file",
                onLongClick = onLongPress, onLongClickLabel = if (isSelected) "Deselect file" else "Select file",
                role = if (selectsOnTap) Role.Checkbox else Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = "${pickerFileLabel(item.name)}, ${item.mimeType}, ${item.humanReadableSize}"
                selected = isSelected
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else (MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { ItemPreview(item, previewDp = 44) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pickerFileLabel(item.name), maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.W600, style = MaterialTheme.typography.bodyLarge.copy(
                    textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrLtr))
            Spacer(Modifier.height(2.dp))
            Text(
                "${item.humanReadableSize} • ${item.extension.uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDetails, modifier = Modifier.size(48.dp).semantics { contentDescription = "Details for ${pickerFileLabel(item.name)}" }) { Text("i", color = MaterialTheme.colorScheme.primary) }
        if (showCheck) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        CircleShape,
                    )
                    .border(
                        1.5.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else (if (isDark) Color.White.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.38f)),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.check, semanticLabel = null, color = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ItemPreview(item: MediaFileDescriptor, previewDp: Int = 48) {
    // Previously this queried item.exists during every composition and always requested 48dp.
    // Coil now checks availability asynchronously; failures keep the category fallback.
    if (item.category == MediaCategory.image) {
        val pixels = with(androidx.compose.ui.platform.LocalDensity.current) { previewDp.dp.roundToPx() }.coerceIn(32, 512)
        coil.compose.SubcomposeAsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(File(item.path)).size(pixels).build(),
            contentDescription = null,
            modifier = Modifier.size(previewDp.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            loading = { CategoryIcon(item.category) },
            error = { CategoryIcon(item.category) },
        )
        return
    }
    CategoryIcon(item.category)
}

@Composable
private fun CategoryIcon(category: MediaCategory, size: Int = 32) {
    when (category) {
        MediaCategory.image -> com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.gallery, semanticLabel = null,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size.dp))
        MediaCategory.video -> ChatyMediaIcon(icon = ChatyIconType.video, size = size.dp, color = MaterialTheme.colorScheme.secondary)
        MediaCategory.audio -> ChatyMediaIcon(icon = ChatyIconType.audio, size = size.dp, color = MaterialTheme.colorScheme.tertiary)
        MediaCategory.pdf -> ChatyMediaIcon(icon = ChatyIconType.pdf, size = size.dp, color = MaterialTheme.colorScheme.error)
        MediaCategory.document -> ChatyMediaIcon(icon = ChatyIconType.document, size = size.dp, color = MaterialTheme.colorScheme.primary)
        MediaCategory.archive -> ChatyMediaIcon(icon = ChatyIconType.archive, size = size.dp, color = MaterialTheme.colorScheme.secondary)
        MediaCategory.other -> Icon(Icons.Rounded.InsertDriveFile, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size.dp))
    }
}

@Composable
private fun FloatingActionDock(
    count: Int,
    isWorkspaceMode: Boolean,
    isDark: Boolean,
    onDeselect: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count selected", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontWeight = FontWeight.W700, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDeselect) { Text("Deselect") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onConfirm, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (isWorkspaceMode) "Share" else "Attach")
        }
    }
}

@Composable
private fun EmptyState(isDark: Boolean, onBrowse: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("No Files Found", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700))
        Spacer(Modifier.height(6.dp))
        Text(
            "Select \"Browse device files\" to import photos, videos, or documents from your phone.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBrowse, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.FileOpen, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Browse Files")
        }
    }
}
