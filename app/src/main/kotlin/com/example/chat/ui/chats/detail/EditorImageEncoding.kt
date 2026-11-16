package com.example.chat.ui.chats.detail

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import java.io.File
import kotlinx.coroutines.CancellationException

/** Local/provider header read only. Do not add a second unauthenticated network fetch. Runs on IO. */
internal fun editorSourceMime(context: Context, source: String): String? {
    val uri = Uri.parse(source)
    val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT)
    val headerMime = try {
        val stream = when (scheme) {
            null, "" -> File(source).inputStream()
            "file" -> File(requireNotNull(uri.path)).inputStream()
            "content" -> context.contentResolver.openInputStream(uri)
            else -> null
        }
        stream?.use { input ->
            val header = ByteArray(12)
            var count = 0
            while (count < header.size) {
                val read = input.read(header, count, header.size - count)
                if (read < 0) break
                if (read == 0) {
                    val one = input.read()
                    if (one < 0) break
                    header[count++] = one.toByte()
                } else count += read
            }
            editorHeaderMime(header.copyOf(count))
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }
    if (headerMime != null) return headerMime
    return if (scheme == "content") try { context.contentResolver.getType(uri) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    else null
}

/** The app already bounds the working bitmap; encoding never touches the original file. */
@Suppress("DEPRECATION")
internal fun compressEditorImage(bitmap: Bitmap, format: EditorOutputFormat, output: java.io.OutputStream): Boolean =
    when (format) {
        EditorOutputFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        EditorOutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30)
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)
            else bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output)
        EditorOutputFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
    }
