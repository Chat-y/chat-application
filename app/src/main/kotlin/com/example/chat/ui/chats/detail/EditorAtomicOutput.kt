package com.example.chat.ui.chats.detail

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** Write only an owned temporary sibling until compression/flush/cancellation checks succeed.
 * The destination is never opened/truncated/deleted by the failure path. Same-directory Android
 * rename replaces it atomically only after the new complete copy is ready. */
internal fun writeEditorOutputAtomically(
    destination: File,
    checkActive: () -> Unit,
    replace: (File, File) -> Unit = { temporary, target ->
        android.system.Os.rename(temporary.absolutePath, target.absolutePath)
    },
    write: (OutputStream) -> Unit,
) {
    val target = destination.absoluteFile
    val parent = requireNotNull(target.parentFile)
    require(parent.isDirectory) { "Editor output directory is unavailable." }
    val temporary = File.createTempFile(".chaty-edit-", ".partial", parent)
    try {
        FileOutputStream(temporary).use { stream ->
            write(stream)
            stream.flush()
            stream.fd.sync()
        }
        checkActive()
        replace(temporary, target)
    } finally {
        temporary.delete() // Only the temporary sibling belongs to this failure cleanup.
    }
}
