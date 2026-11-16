package com.example.chat.ui.media

import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Local-only failure categories. No exception text, raw provider URI or path is shown to users. */
internal enum class PickerImportFailureKind { Unavailable, Permission, Storage, TooLarge, Empty, Read }
internal class PickerImportFailure(val kind: PickerImportFailureKind, cause: Throwable? = null) : IOException(kind.name, cause)
internal data class PickerImportCopy(val file: File, val bytesCopied: Long)

/**
 * Caller runs on IO and owns session rollback. Both close operations must succeed before return.
 * An exclusive .part file is hidden from normal library scans until the entire batch is accepted.
 * This helper does not promote, share, classify, open externally or infer a destination.
 */
internal suspend fun copyPickerImport(
    directory: File,
    maxBytes: Long,
    openSource: () -> InputStream?,
    onPendingCreated: (File) -> Unit = {},
    onProgress: suspend (Long) -> Unit = {},
): PickerImportCopy {
    require(maxBytes > 0)
    val pending = try { File.createTempFile(".devpick_", ".part", directory) }
        catch (failure: IOException) { throw PickerImportFailure(PickerImportFailureKind.Storage, failure) }
    var completed = false
    try {
        onPendingCreated(pending)
        val input = try { openSource() ?: throw PickerImportFailure(PickerImportFailureKind.Unavailable) }
        catch (failure: SecurityException) { throw PickerImportFailure(PickerImportFailureKind.Permission, failure) }
        catch (failure: PickerImportFailure) { throw failure }
        catch (failure: IOException) { throw PickerImportFailure(PickerImportFailureKind.Unavailable, failure) }
        val copied = try {
            val source = object : java.io.FilterInputStream(input) {
                override fun read(): Int = try { input.read() } catch (failure: IOException) { throw PickerImportFailure(PickerImportFailureKind.Read, failure) }
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int = try { input.read(bytes, offset, length) } catch (failure: IOException) { throw PickerImportFailure(PickerImportFailureKind.Read, failure) }
                override fun close() { try { input.close() } catch (failure: IOException) { throw PickerImportFailure(PickerImportFailureKind.Read, failure) } }
            }
            source.use {
                val rawOutput = try { pending.outputStream() }
                    catch (failure: IOException) { throw PickerImportFailure(PickerImportFailureKind.Storage, failure) }
                val output = object : java.io.FilterOutputStream(rawOutput) {
                    override fun write(bytes: ByteArray, offset: Int, length: Int) { try { rawOutput.write(bytes, offset, length) } catch (failure: IOException) { throw PickerImportFailure(PickerImportFailureKind.Storage, failure) } }
                    override fun write(value: Int) { try { rawOutput.write(value) } catch (failure: IOException) { throw PickerImportFailure(PickerImportFailureKind.Storage, failure) } }
                    override fun close() { try { rawOutput.close() } catch (failure: IOException) { throw PickerImportFailure(PickerImportFailureKind.Storage, failure) } }
                }
                output.use { destination -> copyMediaBounded(source, destination, maxBytes, onProgress) }
            }
        } catch (failure: CancellationException) { throw failure }
        catch (failure: SecurityException) { throw PickerImportFailure(PickerImportFailureKind.Permission, failure) }
        catch (failure: IllegalArgumentException) { throw PickerImportFailure(PickerImportFailureKind.TooLarge, failure) }
        currentCoroutineContext().ensureActive()
        if (copied <= 0) throw PickerImportFailure(PickerImportFailureKind.Empty)
        check(pending.length() == copied) { "Import byte count changed before validation" }
        completed = true
        return PickerImportCopy(pending, copied)
    } finally { if (!completed) pending.delete() }
}
