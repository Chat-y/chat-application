package com.example.chat.ui.media

import com.example.chat.ui.kit.ChatyHapticsPort
import android.content.ActivityNotFoundException
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.chat.ui.kit.ChatyAdaptiveLogic
import com.example.chat.ui.kit.rememberAdaptiveWindowSize
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import com.example.chat.ui.theme.ChatyAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Exact port of lib/features/media/ui/document/document_reader_screen.dart.
 *
 *  In-app reader for pdf/text/image/video + fallback for office/archive.
 *  PDFs use PdfRenderer page counts and rendered pages. Text is paged in explicit
 *  45-line chunks; search offsets resolve to those same chunks.
 *  Platform seams: share_plus/url_launcher -> ACTION_SEND / ACTION_VIEW with
 *  FileProvider (owner: manifest + file_paths, same entry as PdfWorkspace).
 *
 *  Search policy (DOC-R2-072 / DOC-R2-074 / DOC-R2-075 / DOC-R2-076): matching is literal,
 *  case-insensitive and non-overlapping (Regex leftmost semantics); there is no accent folding
 *  or Unicode normalization, so every match offset always refers to the original decoded text.
 *  Text is normalized CRLF/CR -> LF at load for display and pagination (DOC-R5-218); blank
 *  final pages are possible when a file ends with many newlines and are shown, not dropped
 *  (DOC-R5-220).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DocumentReaderScreen(
    file: MediaFileDescriptor,
    pdfService: PdfProcessingService,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    val isDark = com.example.chat.ui.theme.LocalThemeConfig.current.isDark

    var isSearching by remember(file.path) { mutableStateOf(false) }
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable(file.path) { mutableStateOf("") }
    var currentPage by androidx.compose.runtime.saveable.rememberSaveable(file.path) { mutableIntStateOf(1) }
    var totalPages by remember(file.path) { mutableIntStateOf(1) }
    var textContent by remember(file.path) { mutableStateOf("") }
    var isLoadingContent by remember(file.path) { mutableStateOf(true) }
    var contentError by remember(file.path) { mutableStateOf<String?>(null) }
    var searchMatches by remember(file.path) { mutableStateOf<List<DocumentSearchMatch>>(emptyList()) }
    var currentMatchIndex by remember(file.path) { mutableIntStateOf(0) }
    val textScrollState = rememberScrollState()
    val density = LocalDensity.current
    var showDetails by remember(file.path) { mutableStateOf(false) }
    var showJumpDialog by remember(file.path) { mutableStateOf(false) }

    val isTextFile = file.extension in setOf("txt", "md", "json", "csv", "log")
    // DOC-R2-103: explicit reading-size setting for long text sessions (session-scoped,
    // 16sp scalable baseline per the proposed guidance; Small 14 / Normal 16 / Large 18).
    var readingSizeSp by androidx.compose.runtime.saveable.rememberSaveable(file.path) { mutableIntStateOf(16) }
    var retryAttempt by remember(file.path) { mutableIntStateOf(0) }
    var pdfBitmap by remember(file.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pdfEmpty by remember(file.path) { mutableStateOf(false) }
    var fileMissing by remember(file.path) { mutableStateOf(false) }
    var textPreviewLimited by remember(file.path) { mutableStateOf(false) }
    LaunchedEffect(file.path, retryAttempt) {
        isLoadingContent = true
        contentError = null
        textContent = ""
        searchMatches = emptyList()
        currentMatchIndex = 0
        currentPage = 1
        totalPages = 1
        pdfEmpty = false
        fileMissing = false
        textPreviewLimited = false
        try {
            val diskFile = File(file.path)
            if (!(diskFile.isFile && diskFile.canRead())) { fileMissing = true; contentError = "This file is no longer available."; return@LaunchedEffect }
            if (file.category == MediaCategory.pdf) {
                totalPages = withContext(Dispatchers.IO) {
                    android.os.ParcelFileDescriptor.open(diskFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        android.graphics.pdf.PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
                    }
                }
                // DOC-R2-067: an empty page tree is an explicit state, never a 1-of-0 navigation.
                if (totalPages <= 0) { pdfEmpty = true; contentError = "This PDF has no pages to display."; return@LaunchedEffect }
            } else if (isTextFile) {
                val decoded = withContext(Dispatchers.IO) {
                    require(diskFile.length() <= FileSanitizationService.MAX_SAFE_FILE_SIZE) { "Document exceeds safe read limit" }
                    decodeTextFileBounded(diskFile)
                }
                when {
                    decoded.unsupported -> { contentError = "This does not look like a readable text file."; return@LaunchedEffect }
                    else -> {
                        // DOC-R5-218: CRLF/CR normalize to LF at load so pagination and search offsets agree.
                        textContent = decoded.text.replace("\r\n", "\n").replace('\r', '\n')
                        textPreviewLimited = decoded.truncated
                        totalPages = max(1, ceil(textContent.split("\n").size / 45.0).toInt())
                    }
                }
            }
            currentPage = currentPage.coerceIn(1, totalPages)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) {
            // DOC-R2-062: an encrypted PDF is a distinct state, not a generic read failure.
            contentError = if (e.message?.let { it.contains("password", true) || it.contains("encrypt", true) } == true)
                "This PDF is password-protected. This reader cannot unlock it; open it in a viewer that supports passwords."
            else "Could not read this file. Check access, file format and the 100 MB file limit."
        }
        finally { isLoadingContent = false }
    }
    LaunchedEffect(file.path, currentPage, retryAttempt, isLoadingContent) {
        if (file.category != MediaCategory.pdf || isLoadingContent || contentError != null) return@LaunchedEffect
        pdfBitmap = null
        var unpublishedBitmap: android.graphics.Bitmap? = null
        try {
            val rendered = withContext(Dispatchers.IO) {
                android.os.ParcelFileDescriptor.open(File(file.path), android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage((currentPage - 1).coerceIn(0, renderer.pageCount - 1)).use { page ->
                            val factor = minOf(2048f / page.width.coerceAtLeast(1), 2048f / page.height.coerceAtLeast(1))
                            val bitmap = android.graphics.Bitmap.createBitmap((page.width * factor).toInt().coerceAtLeast(1), (page.height * factor).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
                            unpublishedBitmap = bitmap
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            pdfBitmap = rendered
            unpublishedBitmap = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { contentError = "Could not display this PDF page. Try opening it in an external app." }
        finally { unpublishedBitmap?.let { if (!it.isRecycled) it.recycle() } }
    }

    val textLines = remember(textContent) { textContent.split("\n") }
    val textLineStarts = remember(textContent) {
        buildList { add(0); textContent.forEachIndexed { index, char -> if (char == '\n') add(index + 1) } }.toIntArray()
    }
    var announcedInitialPage by androidx.compose.runtime.saveable.rememberSaveable(file.path) { mutableStateOf(false) }
    // DOC-R4-173: page changes are confirmed once the page state settles; the initial page is not announced.
    LaunchedEffect(currentPage, isLoadingContent, contentError) {
        if (isLoadingContent || contentError != null) return@LaunchedEffect
        if (!announcedInitialPage) { announcedInitialPage = true; return@LaunchedEffect }
        view.announceForAccessibility("Page $currentPage of $totalPages")
    }
    var searchTruncated by remember(file.path) { mutableStateOf(false) }
    var searchBusy by remember(file.path) { mutableStateOf(false) }
    /** DOC-R1-004: bring the focused search match into view (approximate line-height scroll). */
    fun scrollToFocusedMatch(match: DocumentSearchMatch) {
        if (!isTextFile || textContent.isEmpty()) return
        val found = textLineStarts.binarySearch(match.startOffset)
        val line = (if (found >= 0) found else -found - 2).coerceAtLeast(0)
        val lineInPage = (line - (match.pageNumber - 1) * 45).coerceAtLeast(0)
        // Line height tracks the reading-size setting (DOC-R2-103), not a fixed 14sp body.
        val targetPx = (with(density) { (readingSizeSp * 1.6).sp.toPx() } * lineInPage).toInt()
        scope.launch { textScrollState.animateScrollTo(targetPx) }
    }

    fun onSearchChanged(query: String) {
        searchQuery = query
        currentMatchIndex = 0
    }
    LaunchedEffect(file.path, searchQuery, textContent) {
        searchMatches = emptyList()
        searchTruncated = false
        if (searchQuery.isEmpty() || textContent.isEmpty() || !isTextFile) { searchBusy = false; return@LaunchedEffect }
        searchBusy = true
        try {
            delay(200)
            val result = withContext(Dispatchers.Default) {
                val matches = ArrayList<DocumentSearchMatch>()
                var truncated = false
                for (match in Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(textContent)) {
                    coroutineContext.ensureActive()
                    if (matches.size >= 1000) { truncated = true; break }
                    val foundLine = textLineStarts.binarySearch(match.range.first)
                    val line = if (foundLine >= 0) foundLine else -foundLine - 2
                    matches += DocumentSearchMatch((line.coerceAtLeast(0) / 45 + 1).coerceIn(1, totalPages), matches.size, "", match.range.first, match.range.last + 1)
                }
                matches.toList() to truncated
            }
            searchMatches = result.first
            searchTruncated = result.second
            searchMatches.firstOrNull()?.let { currentPage = it.pageNumber; scrollToFocusedMatch(it) }
        } finally { searchBusy = false }
    }

    /** DOC-R4-172: after an explicit next/previous, speak the position and a short snippet. */
    fun announceMatch(match: DocumentSearchMatch) {
        if (textContent.isEmpty()) return
        val from = (match.startOffset - 40).coerceAtLeast(0)
        val to = (match.endOffset + 40).coerceAtMost(textContent.length)
        val snippet = textContent.substring(from, to).replace('\n', ' ').trim()
        view.announceForAccessibility("Match ${currentMatchIndex + 1} of ${searchMatches.size}: $snippet")
    }

    fun nextMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
        currentPage = searchMatches[currentMatchIndex].pageNumber
        scrollToFocusedMatch(searchMatches[currentMatchIndex])
        announceMatch(searchMatches[currentMatchIndex])
    }

    fun prevMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
        currentPage = searchMatches[currentMatchIndex].pageNumber
        scrollToFocusedMatch(searchMatches[currentMatchIndex])
        announceMatch(searchMatches[currentMatchIndex])
    }

//      Legacy unguarded FileProvider setup retained:
//     fun shareFile() {
//         ChatyHapticsPort.light(view)
//         val diskFile = File(file.path)
//         val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", diskFile)
//         val intent = Intent(Intent.ACTION_SEND).apply {
//             type = file.mimeType.ifEmpty { "*" }
//             putExtra(Intent.EXTRA_STREAM, uri)
//             putExtra(Intent.EXTRA_SUBJECT, file.name)
//             addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//         }
//         context.startActivity(Intent.createChooser(intent, null))
//     }
//
//     fun openExternal() {
//         ChatyHapticsPort.light(view)
//         val diskFile = File(file.path)
//         val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", diskFile)
//         val intent = Intent(Intent.ACTION_VIEW).apply {
//             setDataAndType(uri, file.mimeType.ifEmpty { "*" })
//             addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//         }
//         try {
//             context.startActivity(intent)
//         } catch (e: ActivityNotFoundException) {
//             scope.launch { snackbar.showSnackbar("No external application found to open this file.") }
//         }
//     }
//
    fun launchFile(share: Boolean) {
        ChatyHapticsPort.light(view)
        try {
            val diskFile = File(file.path)
            require(diskFile.isFile && diskFile.canRead())
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", diskFile)
            val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
                // DOC-R2-086: an unknown type falls back to a binary stream, never */* which
                // would offer irrelevant apps.
                val safeMime = file.mimeType.ifEmpty { "application/octet-stream" }
                if (share) { type = safeMime; putExtra(Intent.EXTRA_STREAM, uri) }
                else setDataAndType(uri, safeMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("Document", uri)
            }
            context.startActivity(Intent.createChooser(intent, if (share) "Share document" else "Open document"))
        } catch (_: Exception) { scope.launch { snackbar.showSnackbar("Could not open this file action. Check that the file and a compatible app are available.") } }
    }
    fun shareFile() = launchFile(true)
    fun openExternal() = launchFile(false)

    // DOC-R4-193: short (phone landscape) or very narrow windows get a dedicated search row under
    // the bar so the field, counter and prev/next are not squeezed into the title slot.
    val windowSize = rememberAdaptiveWindowSize()
    val compactSearch = isSearching &&
        (windowSize.heightDp < ChatyAdaptiveLogic.HEIGHT_MEDIUM_MIN_DP || windowSize.widthDp < 360)

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Back")
                    }
                },
                title = {
                    // DOC-R4-193 prior: if (isSearching) {
                    if (isSearching && !compactSearch) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { onSearchChanged(it) },
                            placeholder = { Text("Search text in document...") },
                            supportingText = {
                                when {
                                    searchBusy -> Text("Searching")
                                    searchTruncated -> Text("Showing first 1,000 matches. Refine your search.")
                                    searchQuery.isNotEmpty() && searchMatches.isEmpty() -> Text("No matches")
                                }
                            },
                            singleLine = true,
                            trailingIcon = {
                                if (searchMatches.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${currentMatchIndex + 1}/${searchMatches.size}",
                                            fontSize = 12.sp, color = colors.foregroundSecondary,
                                        )
                                        IconButton(onClick = { prevMatch() }) {

//Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
                                            com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.chevronUp, Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
                                        }
                                        IconButton(onClick = { nextMatch() }) {

//Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
                                            com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.chevronDown, Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
                                        }
                                    }
                                }
                            },
                        )
                    } else {
                        Text(file.name, fontSize = 15.sp, fontWeight = FontWeight.W700,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() })
                    }
                },
                actions = {
                    IconButton(enabled = isTextFile, onClick = {
                        isSearching = !isSearching
                        if (!isSearching) {
                            searchQuery = ""
                            searchMatches = emptyList()
                        }
                    }) {
                        Icon(
                            if (isSearching) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (isSearching) "Close search" else "Search text",
                        )
                    }
                    IconButton(onClick = { shareFile() }) {
                        // DOC-R4-179: identify the document being handed off, not a bare "Share".
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.share, semanticLabel = "Share ${file.name}")
                    }
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.moreOptions, semanticLabel = "More")
                        }
                        DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape, expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (totalPages > 1) {
                                DropdownMenuItem(text = { Text("Jump to page...") }, onClick = {
                                    menuOpen = false; showJumpDialog = true
                                })
                            }
                            if (isTextFile) {
                                // DOC-R2-103: reading-size choices only affect the text reader.
                                DropdownMenuItem(text = { Text("Reading size: Small${if (readingSizeSp == 14) " (current)" else ""}") }, onClick = { readingSizeSp = 14 })
                                DropdownMenuItem(text = { Text("Reading size: Normal${if (readingSizeSp == 16) " (current)" else ""}") }, onClick = { readingSizeSp = 16 })
                                DropdownMenuItem(text = { Text("Reading size: Large${if (readingSizeSp == 18) " (current)" else ""}") }, onClick = { readingSizeSp = 18 })
                            }
                            DropdownMenuItem(text = { Text("Open in external app") }, onClick = {
                                menuOpen = false; openExternal()
                            })
                            DropdownMenuItem(text = { Text("File Details") }, onClick = {
                                menuOpen = false; showDetails = true
                            })
                        }
                    }
                },
            )
            if (compactSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { onSearchChanged(it) },
                        placeholder = { Text("Search text in document...") },
                        // DOC-R4-193: preserve the wide field's status priority and cap disclosure.
                        supportingText = {
                            when {
                                searchBusy -> Text("Searching")
                                searchTruncated -> Text("Showing first 1,000 matches. Refine your search.")
                                searchQuery.isNotEmpty() && searchMatches.isEmpty() -> Text("No matches")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    // Same visibility and handlers as the wide field, not disabled empty controls.
                    if (searchMatches.isNotEmpty()) {
                        Text(
                            "${currentMatchIndex + 1}/${searchMatches.size}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { prevMatch() }) {

//Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
                            com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.chevronUp, Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
                        }
                        IconButton(onClick = { nextMatch() }) {

//Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
                            com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.chevronDown, Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
                        }
                    }
                }
            }
            }
        },
        bottomBar = {
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .border(
                            1.dp,
                            colors.borderSubtle,
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { currentPage-- }, enabled = currentPage > 1) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Previous page")
                    }
                    Box(
                        modifier = Modifier
                            .springInteractiveScale(0.95f)
                            .clickable { showJumpDialog = true }
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceSecondary)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text("${if (file.category == MediaCategory.pdf) "Page" else "Section"} $currentPage of $totalPages", fontWeight = FontWeight.W700, fontSize = 13.sp)
                    }
                    IconButton(onClick = { currentPage++ }, enabled = currentPage < totalPages) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = "Next page")
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                contentError != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.errorOutline, semanticLabel = null,
                            color = colors.error, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(contentError!!, textAlign = TextAlign.Center)
                        if (fileMissing) {
                            // DOC-R2-109: a missing file cannot be retried or opened; offer the way out only.
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = onClose) { Text("Back to Library") }
                        } else {
                        TextButton(onClick = { retryAttempt++ }) { Text("Retry") }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { openExternal() }) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.openInNew, semanticLabel = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Try External App")
                        }
                        }
                    }
                }
                isLoadingContent -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                file.category == MediaCategory.image -> {
//                     // InteractiveViewer minScale 0.8 maxScale 5.0
//                     var zoom by remember(file.path) { mutableStateOf(1f) }
//                     var offsetX by remember(file.path) { mutableStateOf(0f) }
//                     var offsetY by remember(file.path) { mutableStateOf(0f) }
//                     val transformState = rememberTransformableState { zoomChange, panChange, _ ->
//                         zoom = (zoom * zoomChange).coerceIn(0.8f, 5f)
//                         offsetX += panChange.x
//                         offsetY += panChange.y
//                     }
//                     Box(
//                         modifier = Modifier.fillMaxSize().transformable(transformState),
//                         contentAlignment = Alignment.Center,
//                     ) {
//                         ChatyAsyncImage(
//                             url = File(file.path).toURI().toString(),
//                             contentDescription = file.name,
//                             modifier = Modifier.graphicsLayer {
//                                 scaleX = zoom; scaleY = zoom
//                                 translationX = offsetX; translationY = offsetY
//                             },
//                             contentScale = ContentScale.Fit,
//                         )
//                     }
//                 }
                    com.example.chat.ui.chats.detail.MediaReviewImage(File(file.path).toURI().toString(), file.name, Modifier.fillMaxSize())
                }
                file.category == MediaCategory.video -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ChatyVideoPlayerWidget(
                            videoPath = file.path,
                            title = file.name,
                            // DOC-R2-106: no autoplay in a reader - playback is an explicit user choice.
                            autoPlay = false,
                        )
                    }
                }
                file.category == MediaCategory.pdf -> {
                    val page = pdfBitmap
                    if (page == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else Image(page.asImageBitmap(), "Page $currentPage of $totalPages in ${file.name}", modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                }
                isTextFile -> {
                    val highlightStyle = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column(Modifier.fillMaxSize()) {
                        if (textPreviewLimited) {
                            // DOC-R1-015: the preview is capped; say so instead of silently truncating.
                            Text(
                                "Showing the first 2 MB of this large document. Open in an external app for the full file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(textScrollState)
                            .padding(20.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                // DOC-R4-191: constrain reading width on wide screens so lines stay trackable.
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                                .shadow(
                                    12.dp, RoundedCornerShape(16.dp),
                                    ambientColor = MaterialTheme.colorScheme.scrim.copy(alpha = if (isDark) 0.3f else 0.04f),
                                    spotColor = MaterialTheme.colorScheme.scrim.copy(alpha = if (isDark) 0.3f else 0.04f),
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surface)
                                .border(
                                    1.dp,
                                    colors.borderSubtle,
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(20.dp),
                        ) {
                            SelectionContainer {
                                Text(
                                    buildAnnotatedString {
                                        val pageText = textLines.drop((currentPage - 1) * 45).take(45).joinToString("\n").ifEmpty { "Document is empty." }
                                        append(pageText)
                                        if (searchQuery.isNotEmpty()) Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(pageText).forEach { match ->
                                            addStyle(highlightStyle, match.range.first, match.range.last + 1)
                                        }
                                    },
                                    fontSize = readingSizeSp.sp,
                                    lineHeight = (readingSizeSp * 1.6).sp,
                                    fontFamily = if (file.extension == "json" || file.extension == "csv")
                                        FontFamily.Monospace else null,
                                    color = colors.foreground,
                                )
                            }
                        }
                    }
                    }
                }
                file.category == MediaCategory.audio -> {
                    // DOC-R1-060: this reader has no audio branch; route honestly instead of a blank preview.
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(file.name, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("This reader does not play audio. Open it from the media viewer or an external player.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { openExternal() }) { Text("Open in External App") }
                    }
                }
                else -> {
                    // Office / Archive / Unsupported fallback
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    colors.surfaceSecondary,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ChatyMediaIcon(
                                icon = if (file.category == MediaCategory.archive) ChatyIconType.archive
                                else ChatyIconType.document,
                                size = 38.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            file.name,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${file.humanReadableSize} • ${file.mimeType}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "This document format is best viewed with an external application or productivity suite.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.foregroundSecondary,
                            ),
                        )
                        Spacer(Modifier.height(24.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = { openExternal() }) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.openInNew, semanticLabel = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Open External")
                            }
                            Spacer(Modifier.width(14.dp))
                            Button(onClick = { shareFile() }) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.share, semanticLabel = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Share File")
                            }
                        }
                    }
                }
            }
        }
    }

    // Page jump dialog
    if (showJumpDialog) {
        var jumpText by remember { mutableStateOf("$currentPage") }
        val jumpTarget = jumpText.toIntOrNull()
        val jumpValid = jumpTarget != null && jumpTarget in 1..totalPages
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                OutlinedTextField(
                    value = jumpText,
                    onValueChange = { jumpText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    // DOC-R4-174: persistent label plus explicit error semantics, not placeholder-only.
                    label = { Text("Page number") },
                    placeholder = { Text("Enter page (1 - $totalPages)") },
                    suffix = { Text("of $totalPages") },
                    singleLine = true,
                    isError = !jumpValid,
                    supportingText = { if (!jumpValid) Text("Enter a page from 1 to $totalPages") },
                )
            },
            confirmButton = {
                Button(enabled = jumpValid, onClick = {
                    jumpTarget?.takeIf { it in 1..totalPages }?.let { currentPage = it; showJumpDialog = false }
                }) { Text("Jump") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            },
        )
    }

    // File details sheet
    if (showDetails) {
        ModalBottomSheet(tonalElevation = 0.dp, dragHandle = null,
            onDismissRequest = { showDetails = false },
            containerColor = colors.surfaceElevated,
            shape = com.example.chat.ui.kit.ChatyModalShape,
        ) {
            Column(modifier = Modifier.fillMaxWidth().then(Modifier.heightIn(max = 560.dp))
                .verticalScroll(rememberScrollState()).padding(22.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .background(colors.foregroundTertiary.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "File Information",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                )
                Spacer(Modifier.height(14.dp))
                // DOC-R2-108: name, type and size come first; the implementation path is last.
                InfoRow("File Name", file.name)
                InfoRow("Category", file.category.name.uppercase())
                InfoRow("MIME Type", file.mimeType)
                InfoRow("Size", file.humanReadableSize)
                InfoRow(
                    "Modified",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault()).format(file.modifiedAt),
                )
                InfoRow("Path", file.path)
                Spacer(Modifier.height(14.dp))
                // DOC-R4-195 / DOC-R5-246: below 340dp the two actions stack so a 200% font
                // scale never squeezes them into collision.
                androidx.compose.foundation.layout.BoxWithConstraints {
                    // DOC-R4-179: the generic action names the document it hands off.
                    val openBtn = @Composable { m: Modifier ->
                        OutlinedButton(onClick = { showDetails = false; openExternal() }, modifier = m.semantics { contentDescription = "Open ${file.name} in an external app" }) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.openInNew, semanticLabel = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Open External")
                        }
                    }
                    val shareBtn = @Composable { m: Modifier ->
                        Button(onClick = { showDetails = false; shareFile() }, modifier = m.semantics { contentDescription = "Share ${file.name}" }) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.share, semanticLabel = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share File")
                        }
                    }
                    if (maxWidth < 340.dp) {
                        Column(Modifier.fillMaxWidth()) {
                            openBtn(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            shareBtn(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row {
                            openBtn(Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            shareBtn(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    // DOC-R1-048 / DOC-R2-108: label-above-value stacking wraps safely at large font
    // scale, and long paths never crowd a fixed-width label column.
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            fontWeight = FontWeight.W600,
            fontSize = 12.sp,
            color = com.example.chat.ui.theme.LocalAppColors.current.foregroundSecondary,
        )
        Text(value, fontSize = 12.sp)
    }
}

/** Decoded preview of a text document: bounded content plus honesty flags. */
private class TextDecodeResult(val text: String, val truncated: Boolean, val unsupported: Boolean)

/** DOC-R1-015: preview cap so multi-hundred-MB logs never load fully into memory. */
private const val TEXT_PREVIEW_BYTES = 2 * 1024 * 1024

/**
 * DOC-R1-015 / DOC-R2-069 / DOC-R2-070: bounded, BOM-aware text decode.
 * Reads at most TEXT_PREVIEW_BYTES of source (flagging truncation), honors UTF-8 /
 * UTF-16 BOMs, refuses UTF-32 and binary content mislabeled as text, and never
 * loads the whole file into memory.
 */
private fun decodeTextFileBounded(file: File): TextDecodeResult {
    file.inputStream().buffered().use { stream ->
        stream.mark(4)
        val bom = ByteArray(4)
        val bomLen = stream.read(bom)
        stream.reset()
        var charset: java.nio.charset.Charset = Charsets.UTF_8
        var skip = 0L
        when {
            // UTF-32 BOMs first: FF FE 00 00 would otherwise be misread as UTF-16LE.
            bomLen >= 4 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() && bom[2] == 0.toByte() && bom[3] == 0.toByte() ->
                return TextDecodeResult("", truncated = false, unsupported = true) // UTF-32 LE
            bomLen >= 4 && bom[0] == 0.toByte() && bom[1] == 0.toByte() && bom[2] == 0xFE.toByte() && bom[3] == 0xFF.toByte() ->
                return TextDecodeResult("", truncated = false, unsupported = true) // UTF-32 BE
            bomLen >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte() -> skip = 3L // UTF-8 BOM
            bomLen >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() -> { charset = Charsets.UTF_16LE; skip = 2L }
            bomLen >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte() -> { charset = Charsets.UTF_16BE; skip = 2L }
        }
        // DOC-R2-069: with no BOM, sniff the first chunk for binary content.
        stream.mark(8192)
        val sniff = ByteArray(8192)
        val sniffLen = stream.read(sniff)
        stream.reset()
        if (skip == 0L && sniffLen > 0) {
            var controls = 0
            for (i in 0 until sniffLen) {
                val b = sniff[i].toInt() and 0xFF
                if (b == 0) return TextDecodeResult("", truncated = false, unsupported = true)
                if (b < 0x09 || b in 0x0E..0x1F) controls++
            }
            if (controls > sniffLen / 4) return TextDecodeResult("", truncated = false, unsupported = true)
        }
        stream.skip(skip)
        val buffer = ByteArray(TEXT_PREVIEW_BYTES + 1)
        var total = 0
        while (total < buffer.size) {
            val read = stream.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        val truncated = total > TEXT_PREVIEW_BYTES
        val text = String(buffer, 0, min(total, TEXT_PREVIEW_BYTES), charset)
        return TextDecodeResult(text, truncated, unsupported = false)
    }
}

/* Pre-audit DocumentReaderScreen retained (not active):
package com.example.chat.ui.media

import com.example.chat.ui.kit.ChatyHapticsPort
import android.content.ActivityNotFoundException
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.chat.ui.kit.ChatyAdaptiveLogic
import com.example.chat.ui.kit.rememberAdaptiveWindowSize
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import com.example.chat.ui.theme.ChatyAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/ ** Exact port of lib/features/media/ui/document/document_reader_screen.dart.
 *
 *  In-app reader for pdf/text/image/video + fallback for office/archive.
 *  PDFs use PdfRenderer page counts and rendered pages. Text is paged in explicit
 *  45-line chunks; search offsets resolve to those same chunks.
 *  Platform seams: share_plus/url_launcher -> ACTION_SEND / ACTION_VIEW with
 *  FileProvider (owner: manifest + file_paths, same entry as PdfWorkspace).
 *
 *  Search policy (DOC-R2-072 / DOC-R2-074 / DOC-R2-075 / DOC-R2-076): matching is literal,
 *  case-insensitive and non-overlapping (Regex leftmost semantics); there is no accent folding
 *  or Unicode normalization, so every match offset always refers to the original decoded text.
 *  Text is normalized CRLF/CR -> LF at load for display and pagination (DOC-R5-218); blank
 *  final pages are possible when a file ends with many newlines and are shown, not dropped
 *  (DOC-R5-220).
 * /
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReaderScreen(
    file: MediaFileDescriptor,
    pdfService: PdfProcessingService,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()

    var isSearching by remember(file.path) { mutableStateOf(false) }
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable(file.path) { mutableStateOf("") }
    var currentPage by androidx.compose.runtime.saveable.rememberSaveable(file.path) { mutableIntStateOf(1) }
    var totalPages by remember(file.path) { mutableIntStateOf(1) }
    var textContent by remember(file.path) { mutableStateOf("") }
    var isLoadingContent by remember(file.path) { mutableStateOf(true) }
    var contentError by remember(file.path) { mutableStateOf<String?>(null) }
    var searchMatches by remember(file.path) { mutableStateOf<List<DocumentSearchMatch>>(emptyList()) }
    var currentMatchIndex by remember(file.path) { mutableIntStateOf(0) }
    val textScrollState = rememberScrollState()
    val density = LocalDensity.current
    var showDetails by remember(file.path) { mutableStateOf(false) }
    var showJumpDialog by remember(file.path) { mutableStateOf(false) }

    val isTextFile = file.extension in setOf("txt", "md", "json", "csv", "log")
    // DOC-R2-103: explicit reading-size setting for long text sessions (session-scoped,
    // 16sp scalable baseline per the proposed guidance; Small 14 / Normal 16 / Large 18).
    var readingSizeSp by androidx.compose.runtime.saveable.rememberSaveable(file.path) { mutableIntStateOf(16) }
    var retryAttempt by remember(file.path) { mutableIntStateOf(0) }
    var pdfBitmap by remember(file.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pdfEmpty by remember(file.path) { mutableStateOf(false) }
    var fileMissing by remember(file.path) { mutableStateOf(false) }
    var textPreviewLimited by remember(file.path) { mutableStateOf(false) }
    LaunchedEffect(file.path, retryAttempt) {
        isLoadingContent = true
        contentError = null
        textContent = ""
        searchMatches = emptyList()
        currentMatchIndex = 0
        currentPage = 1
        totalPages = 1
        pdfEmpty = false
        fileMissing = false
        textPreviewLimited = false
        try {
            val diskFile = File(file.path)
            if (!(diskFile.isFile && diskFile.canRead())) { fileMissing = true; contentError = "This file is no longer available."; return@LaunchedEffect }
            if (file.category == MediaCategory.pdf) {
                totalPages = withContext(Dispatchers.IO) {
                    android.os.ParcelFileDescriptor.open(diskFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        android.graphics.pdf.PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
                    }
                }
                // DOC-R2-067: an empty page tree is an explicit state, never a 1-of-0 navigation.
                if (totalPages <= 0) { pdfEmpty = true; contentError = "This PDF has no pages to display."; return@LaunchedEffect }
            } else if (isTextFile) {
                val decoded = withContext(Dispatchers.IO) {
                    require(diskFile.length() <= FileSanitizationService.MAX_SAFE_FILE_SIZE) { "Document exceeds safe read limit" }
                    decodeTextFileBounded(diskFile)
                }
                when {
                    decoded.unsupported -> { contentError = "This does not look like a readable text file."; return@LaunchedEffect }
                    else -> {
                        // DOC-R5-218: CRLF/CR normalize to LF at load so pagination and search offsets agree.
                        textContent = decoded.text.replace("\r\n", "\n").replace('\r', '\n')
                        textPreviewLimited = decoded.truncated
                        totalPages = max(1, ceil(textContent.split("\n").size / 45.0).toInt())
                    }
                }
            }
            currentPage = currentPage.coerceIn(1, totalPages)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) {
            // DOC-R2-062: an encrypted PDF is a distinct state, not a generic read failure.
            contentError = if (e.message?.let { it.contains("password", true) || it.contains("encrypt", true) } == true)
                "This PDF is password-protected. This reader cannot unlock it; open it in a viewer that supports passwords."
            else "Could not read this file. Check access, file format and the 100 MB file limit."
        }
        finally { isLoadingContent = false }
    }
    LaunchedEffect(file.path, currentPage, retryAttempt, isLoadingContent) {
        if (file.category != MediaCategory.pdf || isLoadingContent || contentError != null) return@LaunchedEffect
        pdfBitmap = null
        try {
            pdfBitmap = withContext(Dispatchers.IO) {
                android.os.ParcelFileDescriptor.open(File(file.path), android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage((currentPage - 1).coerceIn(0, renderer.pageCount - 1)).use { page ->
                            val factor = minOf(2048f / page.width.coerceAtLeast(1), 2048f / page.height.coerceAtLeast(1))
                            val bitmap = android.graphics.Bitmap.createBitmap((page.width * factor).toInt().coerceAtLeast(1), (page.height * factor).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { contentError = "Could not display this PDF page. Try opening it in an external app." }
    }

    val textLines = remember(textContent) { textContent.split("\n") }
    val textLineStarts = remember(textContent) {
        buildList { add(0); textContent.forEachIndexed { index, char -> if (char == '\n') add(index + 1) } }.toIntArray()
    }
    var announcedInitialPage by androidx.compose.runtime.saveable.rememberSaveable(file.path) { mutableStateOf(false) }
    // DOC-R4-173: page changes are confirmed once the page state settles; the initial page is not announced.
    LaunchedEffect(currentPage, isLoadingContent, contentError) {
        if (isLoadingContent || contentError != null) return@LaunchedEffect
        if (!announcedInitialPage) { announcedInitialPage = true; return@LaunchedEffect }
        view.announceForAccessibility("Page $currentPage of $totalPages")
    }
    var searchTruncated by remember(file.path) { mutableStateOf(false) }
    var searchBusy by remember(file.path) { mutableStateOf(false) }
    / ** DOC-R1-004: bring the focused search match into view (approximate line-height scroll). * /
    fun scrollToFocusedMatch(match: DocumentSearchMatch) {
        if (!isTextFile || textContent.isEmpty()) return
        val found = textLineStarts.binarySearch(match.startOffset)
        val line = (if (found >= 0) found else -found - 2).coerceAtLeast(0)
        val lineInPage = (line - (match.pageNumber - 1) * 45).coerceAtLeast(0)
        // Line height tracks the reading-size setting (DOC-R2-103), not a fixed 14sp body.
        val targetPx = (with(density) { (readingSizeSp * 1.6).sp.toPx() } * lineInPage).toInt()
        scope.launch { textScrollState.animateScrollTo(targetPx) }
    }

    fun onSearchChanged(query: String) {
        searchQuery = query
        currentMatchIndex = 0
    }
    LaunchedEffect(file.path, searchQuery, textContent) {
        searchMatches = emptyList()
        searchTruncated = false
        if (searchQuery.isEmpty() || textContent.isEmpty() || !isTextFile) { searchBusy = false; return@LaunchedEffect }
        searchBusy = true
        try {
            delay(200)
            val result = withContext(Dispatchers.Default) {
                val matches = ArrayList<DocumentSearchMatch>()
                var truncated = false
                for (match in Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(textContent)) {
                    coroutineContext.ensureActive()
                    if (matches.size >= 1000) { truncated = true; break }
                    val foundLine = textLineStarts.binarySearch(match.range.first)
                    val line = if (foundLine >= 0) foundLine else -foundLine - 2
                    matches += DocumentSearchMatch((line.coerceAtLeast(0) / 45 + 1).coerceIn(1, totalPages), matches.size, "", match.range.first, match.range.last + 1)
                }
                matches.toList() to truncated
            }
            searchMatches = result.first
            searchTruncated = result.second
            searchMatches.firstOrNull()?.let { currentPage = it.pageNumber; scrollToFocusedMatch(it) }
        } finally { searchBusy = false }
    }

    / ** DOC-R4-172: after an explicit next/previous, speak the position and a short snippet. * /
    fun announceMatch(match: DocumentSearchMatch) {
        if (textContent.isEmpty()) return
        val from = (match.startOffset - 40).coerceAtLeast(0)
        val to = (match.endOffset + 40).coerceAtMost(textContent.length)
        val snippet = textContent.substring(from, to).replace('\n', ' ').trim()
        view.announceForAccessibility("Match ${currentMatchIndex + 1} of ${searchMatches.size}: $snippet")
    }

    fun nextMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
        currentPage = searchMatches[currentMatchIndex].pageNumber
        scrollToFocusedMatch(searchMatches[currentMatchIndex])
        announceMatch(searchMatches[currentMatchIndex])
    }

    fun prevMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
        currentPage = searchMatches[currentMatchIndex].pageNumber
        scrollToFocusedMatch(searchMatches[currentMatchIndex])
        announceMatch(searchMatches[currentMatchIndex])
    }

//      Legacy unguarded FileProvider setup retained:
//     fun shareFile() {
//         ChatyHapticsPort.light(view)
//         val diskFile = File(file.path)
//         val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", diskFile)
//         val intent = Intent(Intent.ACTION_SEND).apply {
//             type = file.mimeType.ifEmpty { "*" }
//             putExtra(Intent.EXTRA_STREAM, uri)
//             putExtra(Intent.EXTRA_SUBJECT, file.name)
//             addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//         }
//         context.startActivity(Intent.createChooser(intent, null))
//     }
//
//     fun openExternal() {
//         ChatyHapticsPort.light(view)
//         val diskFile = File(file.path)
//         val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", diskFile)
//         val intent = Intent(Intent.ACTION_VIEW).apply {
//             setDataAndType(uri, file.mimeType.ifEmpty { "*" })
//             addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//         }
//         try {
//             context.startActivity(intent)
//         } catch (e: ActivityNotFoundException) {
//             scope.launch { snackbar.showSnackbar("No external application found to open this file.") }
//         }
//     }
//
    fun launchFile(share: Boolean) {
        ChatyHapticsPort.light(view)
        try {
            val diskFile = File(file.path)
            require(diskFile.isFile && diskFile.canRead())
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", diskFile)
            val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
                // DOC-R2-086: an unknown type falls back to a binary stream, never * / * which
                // would offer irrelevant apps.
                val safeMime = file.mimeType.ifEmpty { "application/octet-stream" }
                if (share) { type = safeMime; putExtra(Intent.EXTRA_STREAM, uri) }
                else setDataAndType(uri, safeMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("Document", uri)
            }
            context.startActivity(Intent.createChooser(intent, if (share) "Share document" else "Open document"))
        } catch (_: Exception) { scope.launch { snackbar.showSnackbar("Could not open this file action. Check that the file and a compatible app are available.") } }
    }
    fun shareFile() = launchFile(true)
    fun openExternal() = launchFile(false)

    // DOC-R4-193: short (phone landscape) or very narrow windows get a dedicated search row under
    // the bar so the field, counter and prev/next are not squeezed into the title slot.
    val windowSize = rememberAdaptiveWindowSize()
    val compactSearch = isSearching &&
        (windowSize.heightDp < ChatyAdaptiveLogic.HEIGHT_MEDIUM_MIN_DP || windowSize.widthDp < 360)

    Scaffold(
        containerColor = if (isDark) Color(0xFF0F131A) else Color(0xFFF8FAFC),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) Color(0xFF131822) else Color.White,
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Back")
                    }
                },
                title = {
                    // DOC-R4-193 prior: if (isSearching) {
                    if (isSearching && !compactSearch) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { onSearchChanged(it) },
                            placeholder = { Text("Search text in document...") },
                            supportingText = {
                                when {
                                    searchBusy -> Text("Searching")
                                    searchTruncated -> Text("Showing first 1,000 matches. Refine your search.")
                                    searchQuery.isNotEmpty() && searchMatches.isEmpty() -> Text("No matches")
                                }
                            },
                            singleLine = true,
                            trailingIcon = {
                                if (searchMatches.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${currentMatchIndex + 1}/${searchMatches.size}",
                                            fontSize = 12.sp, color = Color.Gray,
                                        )
                                        IconButton(onClick = { prevMatch() }) {
                                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
                                        }
                                        IconButton(onClick = { nextMatch() }) {
                                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
                                        }
                                    }
                                }
                            },
                        )
                    } else {
                        Text(file.name, fontSize = 15.sp, fontWeight = FontWeight.W700,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() })
                    }
                },
                actions = {
                    IconButton(enabled = isTextFile, onClick = {
                        isSearching = !isSearching
                        if (!isSearching) {
                            searchQuery = ""
                            searchMatches = emptyList()
                        }
                    }) {
                        Icon(
                            if (isSearching) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (isSearching) "Close search" else "Search text",
                        )
                    }
                    IconButton(onClick = { shareFile() }) {
                        // DOC-R4-179: identify the document being handed off, not a bare "Share".
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.share, semanticLabel = "Share ${file.name}")
                    }
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.moreOptions, semanticLabel = "More")
                        }
                        DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape, expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (totalPages > 1) {
                                DropdownMenuItem(text = { Text("Jump to page...") }, onClick = {
                                    menuOpen = false; showJumpDialog = true
                                })
                            }
                            if (isTextFile) {
                                // DOC-R2-103: reading-size choices only affect the text reader.
                                DropdownMenuItem(text = { Text("Reading size: Small${if (readingSizeSp == 14) " (current)" else ""}") }, onClick = { readingSizeSp = 14 })
                                DropdownMenuItem(text = { Text("Reading size: Normal${if (readingSizeSp == 16) " (current)" else ""}") }, onClick = { readingSizeSp = 16 })
                                DropdownMenuItem(text = { Text("Reading size: Large${if (readingSizeSp == 18) " (current)" else ""}") }, onClick = { readingSizeSp = 18 })
                            }
                            DropdownMenuItem(text = { Text("Open in external app") }, onClick = {
                                menuOpen = false; openExternal()
                            })
                            DropdownMenuItem(text = { Text("File Details") }, onClick = {
                                menuOpen = false; showDetails = true
                            })
                        }
                    }
                },
            )
            if (compactSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { onSearchChanged(it) },
                        placeholder = { Text("Search text in document...") },
                        // DOC-R4-193: preserve the wide field's status priority and cap disclosure.
                        supportingText = {
                            when {
                                searchBusy -> Text("Searching")
                                searchTruncated -> Text("Showing first 1,000 matches. Refine your search.")
                                searchQuery.isNotEmpty() && searchMatches.isEmpty() -> Text("No matches")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    // Same visibility and handlers as the wide field, not disabled empty controls.
                    if (searchMatches.isNotEmpty()) {
                        Text(
                            "${currentMatchIndex + 1}/${searchMatches.size}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { prevMatch() }) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
                        }
                        IconButton(onClick = { nextMatch() }) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
                        }
                    }
                }
            }
            }
        },
        bottomBar = {
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF131822) else Color.White)
                        .border(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.12f),
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { currentPage-- }, enabled = currentPage > 1) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Previous page")
                    }
                    Box(
                        modifier = Modifier
                            .springInteractiveScale(0.95f)
                            .clickable { showJumpDialog = true }
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color(0xFF1A2230) else Color(0xFFEDF2F7))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text("${if (file.category == MediaCategory.pdf) "Page" else "Section"} $currentPage of $totalPages", fontWeight = FontWeight.W700, fontSize = 13.sp)
                    }
                    IconButton(onClick = { currentPage++ }, enabled = currentPage < totalPages) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = "Next page")
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                contentError != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.errorOutline, semanticLabel = null,
                            color = Color(0xFFFF5252), modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(contentError!!, textAlign = TextAlign.Center)
                        if (fileMissing) {
                            // DOC-R2-109: a missing file cannot be retried or opened; offer the way out only.
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = onClose) { Text("Back to Library") }
                        } else {
                        TextButton(onClick = { retryAttempt++ }) { Text("Retry") }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { openExternal() }) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.openInNew, semanticLabel = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Try External App")
                        }
                        }
                    }
                }
                isLoadingContent -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                file.category == MediaCategory.image -> {
//                     // InteractiveViewer minScale 0.8 maxScale 5.0
//                     var zoom by remember(file.path) { mutableStateOf(1f) }
//                     var offsetX by remember(file.path) { mutableStateOf(0f) }
//                     var offsetY by remember(file.path) { mutableStateOf(0f) }
//                     val transformState = rememberTransformableState { zoomChange, panChange, _ ->
//                         zoom = (zoom * zoomChange).coerceIn(0.8f, 5f)
//                         offsetX += panChange.x
//                         offsetY += panChange.y
//                     }
//                     Box(
//                         modifier = Modifier.fillMaxSize().transformable(transformState),
//                         contentAlignment = Alignment.Center,
//                     ) {
//                         ChatyAsyncImage(
//                             url = File(file.path).toURI().toString(),
//                             contentDescription = file.name,
//                             modifier = Modifier.graphicsLayer {
//                                 scaleX = zoom; scaleY = zoom
//                                 translationX = offsetX; translationY = offsetY
//                             },
//                             contentScale = ContentScale.Fit,
//                         )
//                     }
//                 }
                    com.example.chat.ui.chats.detail.MediaReviewImage(File(file.path).toURI().toString(), file.name, Modifier.fillMaxSize())
                }
                file.category == MediaCategory.video -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ChatyVideoPlayerWidget(
                            videoPath = file.path,
                            title = file.name,
                            // DOC-R2-106: no autoplay in a reader - playback is an explicit user choice.
                            autoPlay = false,
                        )
                    }
                }
                file.category == MediaCategory.pdf -> {
                    val page = pdfBitmap
                    if (page == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else Image(page.asImageBitmap(), "Page $currentPage of $totalPages in ${file.name}", modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                }
                isTextFile -> {
                    val highlightStyle = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column(Modifier.fillMaxSize()) {
                        if (textPreviewLimited) {
                            // DOC-R1-015: the preview is capped; say so instead of silently truncating.
                            Text(
                                "Showing the first 2 MB of this large document. Open in an external app for the full file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(textScrollState)
                            .padding(20.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                // DOC-R4-191: constrain reading width on wide screens so lines stay trackable.
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                                .shadow(
                                    12.dp, RoundedCornerShape(16.dp),
                                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.3f else 0.04f),
                                    spotColor = Color.Black.copy(alpha = if (isDark) 0.3f else 0.04f),
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color(0xFF151B26) else Color.White)
                                .border(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(20.dp),
                        ) {
                            SelectionContainer {
                                Text(
                                    buildAnnotatedString {
                                        val pageText = textLines.drop((currentPage - 1) * 45).take(45).joinToString("\n").ifEmpty { "Document is empty." }
                                        append(pageText)
                                        if (searchQuery.isNotEmpty()) Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(pageText).forEach { match ->
                                            addStyle(highlightStyle, match.range.first, match.range.last + 1)
                                        }
                                    },
                                    fontSize = readingSizeSp.sp,
                                    lineHeight = (readingSizeSp * 1.6).sp,
                                    fontFamily = if (file.extension == "json" || file.extension == "csv")
                                        FontFamily.Monospace else null,
                                    color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                                )
                            }
                        }
                    }
                    }
                }
                file.category == MediaCategory.audio -> {
                    // DOC-R1-060: this reader has no audio branch; route honestly instead of a blank preview.
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(file.name, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("This reader does not play audio. Open it from the media viewer or an external player.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { openExternal() }) { Text("Open in External App") }
                    }
                }
                else -> {
                    // Office / Archive / Unsupported fallback
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    if (isDark) Color(0xFF1E2638) else Color(0xFFEDF2F7),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ChatyMediaIcon(
                                icon = if (file.category == MediaCategory.archive) ChatyIconType.archive
                                else ChatyIconType.document,
                                size = 38.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            file.name,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${file.humanReadableSize} • ${file.mimeType}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "This document format is best viewed with an external application or productivity suite.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xDD000000),
                            ),
                        )
                        Spacer(Modifier.height(24.dp))
                        Row {
                            OutlinedButton(onClick = { openExternal() }) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.openInNew, semanticLabel = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Open External")
                            }
                            Spacer(Modifier.width(14.dp))
                            Button(onClick = { shareFile() }) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.share, semanticLabel = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Share File")
                            }
                        }
                    }
                }
            }
        }
    }

    // Page jump dialog
    if (showJumpDialog) {
        var jumpText by remember { mutableStateOf("$currentPage") }
        val jumpTarget = jumpText.toIntOrNull()
        val jumpValid = jumpTarget != null && jumpTarget in 1..totalPages
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                OutlinedTextField(
                    value = jumpText,
                    onValueChange = { jumpText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    // DOC-R4-174: persistent label plus explicit error semantics, not placeholder-only.
                    label = { Text("Page number") },
                    placeholder = { Text("Enter page (1 - $totalPages)") },
                    suffix = { Text("of $totalPages") },
                    singleLine = true,
                    isError = !jumpValid,
                    supportingText = { if (!jumpValid) Text("Enter a page from 1 to $totalPages") },
                )
            },
            confirmButton = {
                Button(enabled = jumpValid, onClick = {
                    jumpTarget?.takeIf { it in 1..totalPages }?.let { currentPage = it; showJumpDialog = false }
                }) { Text("Jump") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            },
        )
    }

    // File details sheet
    if (showDetails) {
        ModalBottomSheet(tonalElevation = 0.dp, dragHandle = null,
            onDismissRequest = { showDetails = false },
            containerColor = if (isDark) Color(0xFF141923) else Color.White,
            shape = com.example.chat.ui.kit.ChatyModalShape,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "File Information",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                )
                Spacer(Modifier.height(14.dp))
                // DOC-R2-108: name, type and size come first; the implementation path is last.
                InfoRow("File Name", file.name)
                InfoRow("Category", file.category.name.uppercase())
                InfoRow("MIME Type", file.mimeType)
                InfoRow("Size", file.humanReadableSize)
                InfoRow(
                    "Modified",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault()).format(file.modifiedAt),
                )
                InfoRow("Path", file.path)
                Spacer(Modifier.height(14.dp))
                // DOC-R4-195 / DOC-R5-246: below 340dp the two actions stack so a 200% font
                // scale never squeezes them into collision.
                androidx.compose.foundation.layout.BoxWithConstraints {
                    // DOC-R4-179: the generic action names the document it hands off.
                    val openBtn = @Composable { m: Modifier ->
                        OutlinedButton(onClick = { showDetails = false; openExternal() }, modifier = m.semantics { contentDescription = "Open ${file.name} in an external app" }) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.openInNew, semanticLabel = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Open External")
                        }
                    }
                    val shareBtn = @Composable { m: Modifier ->
                        Button(onClick = { showDetails = false; shareFile() }, modifier = m.semantics { contentDescription = "Share ${file.name}" }) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.share, semanticLabel = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share File")
                        }
                    }
                    if (maxWidth < 340.dp) {
                        Column(Modifier.fillMaxWidth()) {
                            openBtn(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            shareBtn(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row {
                            openBtn(Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            shareBtn(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    // DOC-R1-048 / DOC-R2-108: label-above-value stacking wraps safely at large font
    // scale, and long paths never crowd a fixed-width label column.
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            fontWeight = FontWeight.W600,
            fontSize = 12.sp,
            color = Color.Gray,
        )
        Text(value, fontSize = 12.sp)
    }
}

/ ** Decoded preview of a text document: bounded content plus honesty flags. * /
private class TextDecodeResult(val text: String, val truncated: Boolean, val unsupported: Boolean)

/ ** DOC-R1-015: preview cap so multi-hundred-MB logs never load fully into memory. * /
private const val TEXT_PREVIEW_BYTES = 2 * 1024 * 1024

/ **
 * DOC-R1-015 / DOC-R2-069 / DOC-R2-070: bounded, BOM-aware text decode.
 * Reads at most TEXT_PREVIEW_BYTES of source (flagging truncation), honors UTF-8 /
 * UTF-16 BOMs, refuses UTF-32 and binary content mislabeled as text, and never
 * loads the whole file into memory.
 * /
private fun decodeTextFileBounded(file: File): TextDecodeResult {
    file.inputStream().buffered().use { stream ->
        stream.mark(4)
        val bom = ByteArray(4)
        val bomLen = stream.read(bom)
        stream.reset()
        var charset: java.nio.charset.Charset = Charsets.UTF_8
        var skip = 0L
        when {
            // UTF-32 BOMs first: FF FE 00 00 would otherwise be misread as UTF-16LE.
            bomLen >= 4 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() && bom[2] == 0.toByte() && bom[3] == 0.toByte() ->
                return TextDecodeResult("", truncated = false, unsupported = true) // UTF-32 LE
            bomLen >= 4 && bom[0] == 0.toByte() && bom[1] == 0.toByte() && bom[2] == 0xFE.toByte() && bom[3] == 0xFF.toByte() ->
                return TextDecodeResult("", truncated = false, unsupported = true) // UTF-32 BE
            bomLen >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte() -> skip = 3L // UTF-8 BOM
            bomLen >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() -> { charset = Charsets.UTF_16LE; skip = 2L }
            bomLen >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte() -> { charset = Charsets.UTF_16BE; skip = 2L }
        }
        // DOC-R2-069: with no BOM, sniff the first chunk for binary content.
        stream.mark(8192)
        val sniff = ByteArray(8192)
        val sniffLen = stream.read(sniff)
        stream.reset()
        if (skip == 0L && sniffLen > 0) {
            var controls = 0
            for (i in 0 until sniffLen) {
                val b = sniff[i].toInt() and 0xFF
                if (b == 0) return TextDecodeResult("", truncated = false, unsupported = true)
                if (b < 0x09 || b in 0x0E..0x1F) controls++
            }
            if (controls > sniffLen / 4) return TextDecodeResult("", truncated = false, unsupported = true)
        }
        stream.skip(skip)
        val buffer = ByteArray(TEXT_PREVIEW_BYTES + 1)
        var total = 0
        while (total < buffer.size) {
            val read = stream.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        val truncated = total > TEXT_PREVIEW_BYTES
        val text = String(buffer, 0, min(total, TEXT_PREVIEW_BYTES), charset)
        return TextDecodeResult(text, truncated, unsupported = false)
    }
}

*/
