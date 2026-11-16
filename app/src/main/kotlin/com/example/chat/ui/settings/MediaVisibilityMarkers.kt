package com.example.chat.ui.settings

import java.io.File
import java.io.IOException

/** Local marker maintenance only. Does not erase or hide already exported MediaStore copies. */
object MediaVisibilityMarkers {
    // Shared marker layout constants (HM3-46): every place that saves or hides chat media must
    // agree on these names.
    const val MEDIA_DIR_NAME = "ChatyMedia"
    const val MARKER_FILE_NAME = ".nomedia"
    const val AUDIT_FILE_NAME = "hide_media_audit.log"
    const val BANNER_FLAG_FILE_NAME = "hide_media_intro_seen"
    val KIND_FOLDER_NAMES = listOf("Photos", "Videos", "GIFs")

    fun sync(root: File, photos: Boolean, videos: Boolean, gifs: Boolean) {
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Media directory unavailable")
        // Preflight every marker location before changing any marker. An invalid later type
        // must not remove an earlier type's protection before the operation reports failure.
        val parentMarker = File(root, MARKER_FILE_NAME)
        if (parentMarker.exists() && !parentMarker.isFile) throw IOException("Media marker is not a file")
        for (name in KIND_FOLDER_NAMES) {
            val folder = File(root, name)
            if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Media directory unavailable")
            val marker = File(folder, MARKER_FILE_NAME)
            if (marker.exists() && !marker.isFile) throw IOException("Media marker is not a file")
        }
        // A parent marker would hide all types and defeat the independent choices.
        // Existing files remain private; marker changes do not export them.
        marker(File(root, MARKER_FILE_NAME), false)
        for ((name, hidden) in listOf("Photos" to photos, "Videos" to videos, "GIFs" to gifs)) {
            val folder = File(root, name)
            if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Media directory unavailable")
            marker(File(folder, MARKER_FILE_NAME), hidden)
        }
    }

    private fun marker(file: File, hidden: Boolean) {
        if (hidden) {
            if (file.exists()) {
                if (!file.isFile) throw IOException("Media marker is not a file")
                return
            }
            // Prior claim: Atomic create (HM3-47): write a temp file then rename so an interrupted
            // write can never leave a partial marker behind.
            // Temp+rename narrows exposure but is not proof of filesystem crash durability.
            // Original direct create, replaced per HM3-47:
            // if (!file.exists() && !file.createNewFile()) throw IOException("Media marker could not be created")
            val parent = file.parentFile ?: throw IOException("Media marker could not be created")
//             val tmp = File(parent, file.name + ".tmp")
//             if (tmp.exists() && !tmp.delete()) throw IOException("Media marker could not be created")
//             if (!tmp.createNewFile()) throw IOException("Media marker could not be created")
//             if (!tmp.renameTo(file)) {
//                 tmp.delete()
//                 throw IOException("Media marker could not be created")
//             }
            // Unique temp path: another repository instance must not delete our in-flight file.
            // Flush the file before same-directory rename; directory/group crash durability is
            // still not guaranteed by File.renameTo and is not claimed here.
            val tmp = File.createTempFile(".nomedia-", ".tmp", parent)
            try {
                java.io.FileOutputStream(tmp).use { it.fd.sync() }
                if (!tmp.renameTo(file)) throw IOException("Media marker could not be created")
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        // } else if (file.exists() && !file.delete()) throw IOException("Media marker could not be removed")
        } else if (file.exists()) {
            // Never treat a directory named .nomedia as an owned marker to delete.
            if (!file.isFile) throw IOException("Media marker is not a file")
            if (!file.delete()) throw IOException("Media marker could not be removed")
        }
    }
}
