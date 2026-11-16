package com.example.chat.ui.media

import com.example.chat.BuildConfig
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exact port of lib/features/media/services/media_library_service.dart.
 *
 *  path_provider's application documents + temporary directories are the
 *  [libraryDirectories] seam (Android: context.filesDir + context.cacheDir).
 *  ChangeNotifier is ported as the same listener pattern used by
 *  ChatyPreferencesController. The demo-asset fallback list is preserved
 *  verbatim from the Dart source.
 */
/** Map-compatible result so existing category readers remain source-compatible.
 * incomplete/unknown totals must not be presented as a measured empty library. */
data class MediaStorageBreakdown(
    val sizes: Map<MediaCategory, Long>,
    val unavailableDirectories: Int,
    val scanCompleted: Boolean,
) : Map<MediaCategory, Long> by sizes {
    val isComplete: Boolean get() = scanCompleted && unavailableDirectories == 0
}

class MediaLibraryService(
    private val libraryDirectories: () -> List<File>,
    private val now: () -> Instant = { Instant.now() },
) {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    private val _files = mutableListOf<MediaFileDescriptor>()
    private var _isLoading = false
    private var refreshGeneration = 0L
    private var storageUnavailableDirectories = 0
    private var storageScanCompleted = false
    private var _searchQuery = ""
    private var _activeCategoryFilter: MediaCategory? = null

    val files: List<MediaFileDescriptor> get() = _files.toList()
    val isLoading: Boolean get() = _isLoading
    val searchQuery: String get() = _searchQuery
    val activeCategoryFilter: MediaCategory? get() = _activeCategoryFilter

    val filteredFiles: List<MediaFileDescriptor>
        get() = _files.filter { f ->
            if (_activeCategoryFilter != null && f.category != _activeCategoryFilter) {
                return@filter false
            }
            if (_searchQuery.isNotEmpty() &&
                !f.name.lowercase().contains(_searchQuery.lowercase())
            ) {
                return@filter false
            }
            true
        }

    fun setFilter(category: MediaCategory?) {
        _activeCategoryFilter = category
        notifyListeners()
    }

    fun setSearchQuery(query: String) {
        _searchQuery = query
        notifyListeners()
    }

    suspend fun refreshLibrary() {
        val generation = ++refreshGeneration
        // Keep prior sizes as a stale snapshot, never as a completed latest scan after cancellation.
        storageScanCompleted = false
        _isLoading = true
        notifyListeners()

        try {
            // Directory enumeration and file-header inspection can touch thousands
            // of files. Keep all disk I/O off the UI dispatcher.
            var unavailableDirectories = 0
            val scannedFiles = withContext(Dispatchers.IO) {
            val result = mutableListOf<MediaFileDescriptor>()
            try {
                for (dir in libraryDirectories()) {
                    if (!dir.exists() || !dir.isDirectory) { unavailableDirectories++; continue }
                    val entries = dir.listFiles()
                    if (entries == null) { unavailableDirectories++; continue }
                    entries.forEach entityLoop@{ entity ->
                        if (!entity.isFile) return@entityLoop
                        val name = entity.name
                        // Ignore hidden entries and Chaty transient transfer/cache
                        // files; they are implementation data, not user media.
                        // Legacy exclusion kept for reference, disabled: it missed unsent staging.
                        // if (name.startsWith(".") || name.startsWith("chaty-outgoing-") ||
                        //     name.endsWith(".part", ignoreCase = true) ||
                        //     name.endsWith(".tmp", ignoreCase = true)
                        // ) return@entityLoop
                        // Media-worker parity (relayed by parent): also exclude unsent
                        // chaty_capture_ / chaty_picked_ staging from the broad cache listing;
                        // those are implementation data, not user media.
                        if (name.startsWith(".") || name.startsWith("chaty-outgoing-") ||
                            name.startsWith("chaty_capture_") || name.startsWith("chaty_picked_") ||
                            name.endsWith(".part", ignoreCase = true) ||
                            name.endsWith(".tmp", ignoreCase = true)
                        ) return@entityLoop
                        val classification = FileTypeDetector.classifyFile(
                            headerBytesOf = {
                                try {
                                    entity.inputStream().use { stream ->
                                        val buffer = ByteArray(FileTypeDetector.HEADER_BYTES_TO_READ)
                                        // Media-worker parity (relayed by parent): a single read() is
                                        // not guaranteed to fill the buffer; loop until full or EOF so
                                        // short reads cannot misclassify a file. Legacy single read:
                                        // val read = stream.read(buffer)
                                        // if (read <= 0) ByteArray(0) else buffer.copyOf(read)
                                        var off = 0
                                        while (off < buffer.size) {
                                            val read = stream.read(buffer, off, buffer.size - off)
                                            if (read <= 0) break
                                            off += read
                                        }
                                        if (off <= 0) ByteArray(0) else buffer.copyOf(off)
                                    }
                                } catch (_: Exception) {
                                    ByteArray(0)
                                }
                            },
                            filePath = entity.path,
                        )
                        result.add(
                            MediaFileDescriptor(
                                id = entity.path,
                                name = name,
                                path = entity.path,
                                sizeBytes = entity.length(),
                                mimeType = classification.mimeType,
                                category = classification.category,
                                modifiedAt = Instant.ofEpochMilli(entity.lastModified()),
                            ),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A directory can disappear or become inaccessible during a scan.
                unavailableDirectories++
            }

            if (result.isEmpty() && BuildConfig.DEBUG) {
                val n = now()
                result.addAll(
                    listOf(
                        MediaFileDescriptor(
                            id = "demo_doc_spec",
                            name = "Project_Architecture_Spec.pdf",
                            path = "demo/Project_Architecture_Spec.pdf",
                            sizeBytes = 2569011,
                            mimeType = "application/pdf",
                            category = MediaCategory.document,
                            modifiedAt = n.minusMillis(2 * 60 * 60 * 1000),
                            pageCount = 14,
                        ),
                        MediaFileDescriptor(
                            id = "demo_doc_sheet",
                            name = "Q4_Financial_Roadmap.xlsx",
                            path = "demo/Q4_Financial_Roadmap.xlsx",
                            sizeBytes = 1145728,
                            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            category = MediaCategory.document,
                            modifiedAt = n.minusMillis(5 * 60 * 60 * 1000),
                            pageCount = 3,
                        ),
                        MediaFileDescriptor(
                            id = "demo_audio_anirudh",
                            name = "Hangova - Anirudh.mp3",
                            path = "demo/Hangova - Anirudh.mp3",
                            sizeBytes = 5033164,
                            mimeType = "audio/mpeg",
                            category = MediaCategory.audio,
                            modifiedAt = n.minusMillis(8 * 60 * 60 * 1000),
                            durationSeconds = 225,
                        ),
                        MediaFileDescriptor(
                            id = "demo_audio_meeting",
                            name = "Design_Review_Audio.m4a",
                            path = "demo/Design_Review_Audio.m4a",
                            sizeBytes = 1843200,
                            mimeType = "audio/mp4",
                            category = MediaCategory.audio,
                            modifiedAt = n.minusMillis(24 * 60 * 60 * 1000),
                            durationSeconds = 98,
                        ),
                    ),
                )
            }
            result.sortByDescending { it.modifiedAt }
            result
        }

            // A later refresh supersedes this result; never let an older scan
            // overwrite a newer directory snapshot.
            if (generation != refreshGeneration) return
            _files.clear()
            _files.addAll(scannedFiles)
            storageUnavailableDirectories = unavailableDirectories
            storageScanCompleted = true
        } finally {
            if (generation == refreshGeneration) {
                _isLoading = false
                notifyListeners()
            }
        }
    }

    // Previous Map-only result hid unreadable/missing roots as zero bytes.
    fun getStorageBreakdown(): MediaStorageBreakdown {
        val map = mutableMapOf<MediaCategory, Long>()
        for (f in _files) {
            // Debug presentation fixtures are not measured disk usage.
            if (f.path.startsWith("demo/")) continue
            val current = map[f.category] ?: 0L
            val size = f.sizeBytes.coerceAtLeast(0L)
            map[f.category] = if (size > Long.MAX_VALUE - current) Long.MAX_VALUE else current + size
        }
        return MediaStorageBreakdown(map.toMap(), storageUnavailableDirectories,
            storageScanCompleted && !_isLoading)
    }
}
