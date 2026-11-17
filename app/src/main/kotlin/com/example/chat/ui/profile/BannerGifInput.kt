package com.example.chat.ui.profile

import android.content.Context
import android.net.Uri
import com.example.chat.data.services.BannerGifPolicy
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal object BannerGifInput {
    suspend fun isGif(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(6)
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) break
                offset += count
            }
            BannerGifPolicy.isGif(header)
        } ?: false
    }
    suspend fun copy(context: Context, uri: Uri, files: ProfilePickFiles): File = withContext(Dispatchers.IO) {
        val file = File.createTempFile("chaty_banner_", ".gif", context.cacheDir)
        try {
            files.track(file); files.begin(file)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024); var total = 0
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= BannerGifPolicy.MAX_BYTES) { "Choose a GIF banner under 4 MB." }
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("Could not read GIF banner.")
                BannerGifPolicy.inspect(file.readBytes())
            } finally { files.end(file) }
            file
        } catch (failure: Throwable) { files.remove(file); throw failure }
    }
    fun poster(bytes: ByteArray): ByteArray? {
        // Canvas bound was checked before decoding. BitmapFactory produces a static first frame.
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try { java.io.ByteArrayOutputStream().use { out ->
            if (bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)) out.toByteArray() else null
        } } finally { bitmap.recycle() }
    }
}
