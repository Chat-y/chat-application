package com.example.chat.ui.media

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

private const val PENDING_GALLERY_AGE_SECONDS = 3_600L
private const val PENDING_GALLERY_SCAN_LIMIT = 128

internal fun isStaleOwnedGalleryRow(
    owner: String?, packageName: String, pending: Int, relativePath: String?,
    dateAddedSeconds: Long, nowSeconds: Long,
): Boolean = owner == packageName && pending == 1 &&
    relativePath in setOf("Pictures/Chaty", "Pictures/Chaty/", "Movies/Chaty", "Movies/Chaty/") &&
    dateAddedSeconds > 0 && nowSeconds >= PENDING_GALLERY_AGE_SECONDS &&
    dateAddedSeconds <= nowSeconds - PENDING_GALLERY_AGE_SECONDS

/** MV-R5-011: one best-effort startup sweep, not a broad gallery cleaner.
 * Requires provider-attributed owner package, exact Chaty folder, pending flag and age.
 * Every delete repeats the ownership/pending/folder/age predicate atomically at the provider.
 * Published rows are excluded even if they changed after the cursor was read. No name inference.
 * At most 128 rows per image/video collection are examined. OEM query limits may be ignored;
 * the cursor loop is bounded too. Provider query latency and actual OEM behavior need testing. */
internal suspend fun sweepStalePendingGalleryRows(context: Context) = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < 29) return@withContext
    val app = context.applicationContext
    val resolver = app.contentResolver
    val now = System.currentTimeMillis() / 1_000L
    val cutoff = now - PENDING_GALLERY_AGE_SECONDS
    if (cutoff <= 0) return@withContext
    val selection = "is_pending = ? AND owner_package_name = ? AND date_added > ? AND date_added <= ? AND (relative_path = ? OR relative_path = ? OR relative_path = ? OR relative_path = ?)"
    val arguments = arrayOf("1", app.packageName, "0", cutoff.toString(),
        "Pictures/Chaty", "Pictures/Chaty/", "Movies/Chaty", "Movies/Chaty/")
    for (baseCollection in listOf(
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
    )) {
        coroutineContext.ensureActive()
        // API29-compatible pending inclusion; the SQL predicate still excludes published rows.
        @Suppress("DEPRECATION")
        val collection = MediaStore.setIncludePending(baseCollection)
        try {
            val query = Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arguments)
                putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf("date_added"))
                putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, PENDING_GALLERY_SCAN_LIMIT)
            }
            resolver.query(collection, arrayOf("_id", "owner_package_name", "is_pending", "relative_path", "date_added"), query, null)?.use { cursor ->
                var examined = 0
                while (examined < PENDING_GALLERY_SCAN_LIMIT && cursor.moveToNext()) {
                    examined++
                    coroutineContext.ensureActive()
                    if (!isStaleOwnedGalleryRow(cursor.getString(1), app.packageName, cursor.getInt(2), cursor.getString(3), cursor.getLong(4), now)) continue
                    val id = cursor.getLong(0)
                    if (id <= 0) continue
                    resolver.delete(ContentUris.withAppendedId(collection, id), selection, arguments)
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            // Unsupported/denied owner metadata fails closed. No relaxed retry, prompts or raw logs.
        }
    }
}
