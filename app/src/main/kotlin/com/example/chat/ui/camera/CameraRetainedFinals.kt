package com.example.chat.ui.camera

import android.content.Context

/** Registry of locally captured final files that outlived their send call.
 *
 * Submission is not delivery: once a capture is handed to the send pipeline
 * the local file must not be deleted until a retention policy allows it. That
 * policy (retention window, close-on-durable-ack signal, any UI surfacing) is
 * still an open owner decision, so this registry ONLY tracks files. It never
 * deletes a file; [pruneAllMissing] only drops registry rows whose file
 * already vanished by other means, which is bookkeeping hygiene, not deletion.
 *
 * Entries are scoped per account so one account never sees another account's
 * captures, matching the lens/effect prefs scoping. The account key is ALWAYS
 * supplied by the caller, captured at attachment start (OpenCode 610): this
 * object never reads the ambient current account, so an account switch during
 * an upload can never journal a row into the wrong account's bucket.
 *
 * Wire format v2 (audit-05 v2): "v2" + SEP + epochMs + SEP + conversationId +
 * SEP + path, where SEP is written in source as the explicit escape below so
 * no invisible character can be lost in transport (v1's separator byte was
 * stripped in transit, leaving SEP empty and every row unreadable). Rows that
 * fail to decode - including any v1 leftovers - are preserved untouched:
 * track-only means never discarding data we cannot parse. */
object CameraRetainedFinals {

    data class Entry(val recordedAtMs: Long, val conversationId: String, val path: String)

    private const val PREFS = "chaty_camera_retained_finals"
    private const val KEY_PREFIX = "finals_"
    internal const val FORMAT_VERSION = "v2"
    internal const val SEP = "\u001F"
    internal const val MAX_ENTRIES = 200

    /** Records a kept local final under [accountKey]'s bucket. Never touches
     *  the file itself. [accountKey] must be the key captured at attachment
     *  start, not the ambient account at record time. */
    @Synchronized
    fun record(context: Context, accountKey: String?, conversationId: String, path: String, nowMs: Long = System.currentTimeMillis()) {
        // OpenCode 612: refuse fields that contain the wire separator - a
        // conversationId carrying U+001F would corrupt every row in the bucket.
        if (path.isBlank() || !isFieldSafe(conversationId) || !isFieldSafe(path)) return
        val prefs = prefs(context) ?: return
        val bucket = prefsKeyFor(accountKey)
        val rawRows = prefs.getStringSet(bucket, emptySet())!!.toSet()
        prefs.edit().putStringSet(bucket, appendRows(rawRows, Entry(nowMs, conversationId, path))).apply()
    }

    @Synchronized
    fun entries(context: Context, accountKey: String?): List<Entry> {
        val prefs = prefs(context) ?: return emptyList()
        return readRows(prefs.getStringSet(prefsKeyFor(accountKey), emptySet())!!.toSet())
    }

    /** Drops decodable rows whose file no longer exists, in EVERY account
     *  bucket. Needs no account key, so no call site ever resolves the ambient
     *  account for hygiene. Never deletes files, and never drops rows it
     *  cannot parse. Returns the dropped count. */
    @Synchronized
    fun pruneAllMissing(context: Context): Int {
        val prefs = prefs(context) ?: return 0
        var removed = 0
        val editor = prefs.edit()
        for ((bucket, value) in prefs.all) {
            if (!bucket.startsWith(KEY_PREFIX) || value !is Set<*>) continue
            @Suppress("UNCHECKED_CAST")
            val rawRows = (value as Set<String>).toSet()
            val (kept, dropped) = pruneRows(rawRows) { java.io.File(it).exists() }
            if (dropped > 0) {
                editor.putStringSet(bucket, kept)
                removed += dropped
            }
        }
        if (removed > 0) editor.apply()
        return removed
    }

    // ------------------------------------------------------------ pure core

    internal fun encodeEntry(e: Entry): String =
        "$FORMAT_VERSION$SEP${e.recordedAtMs}$SEP${e.conversationId}$SEP${e.path}"

    internal fun decodeEntry(raw: String): Entry? {
        val first = raw.indexOf(SEP)
        if (first <= 0 || raw.substring(0, first) != FORMAT_VERSION) return null
        val second = raw.indexOf(SEP, first + 1)
        if (second <= first + 1) return null
        val third = raw.indexOf(SEP, second + 1)
        if (third <= second + 1) return null
        val ts = raw.substring(first + 1, second).toLongOrNull() ?: return null
        val path = raw.substring(third + 1)
        if (path.isEmpty()) return null
        return Entry(ts, raw.substring(second + 1, third), path)
    }

    /** Newest [max] entries in ascending time order. */
    internal fun fifoTrim(entries: List<Entry>, max: Int = MAX_ENTRIES): List<Entry> =
        entries.sortedBy { it.recordedAtMs }.takeLast(max)

    internal fun prefsKeyFor(accountKey: String?): String = "$KEY_PREFIX${accountKey ?: "anon"}"

    /** A field is safe to journal only when it cannot carry the wire separator. */
    internal fun isFieldSafe(value: String): Boolean = !value.contains(SEP)

    /** Appends [e] to a bucket's raw rows: FIFO-trims decodable rows, preserves
     *  undecodable rows untouched. Returns a NEW set; the input is never
     *  mutated, so a failure before the prefs write leaves prior rows intact. */
    internal fun appendRows(rawRows: Set<String>, e: Entry): Set<String> {
        val decoded = rawRows.mapNotNull { decodeEntry(it) }
        val undecodable = rawRows.filter { decodeEntry(it) == null }
        return (fifoTrim(decoded + e).map { encodeEntry(it) } + undecodable).toSet()
    }

    internal fun readRows(rawRows: Set<String>): List<Entry> =
        rawRows.mapNotNull { decodeEntry(it) }.sortedBy { it.recordedAtMs }

    /** Keep-rule for hygiene: decodable rows survive only while their file
     *  exists; undecodable rows always survive. Returns kept rows + dropped
     *  count. Never touches the filesystem itself beyond the existence check. */
    internal fun pruneRows(rawRows: Set<String>, fileExists: (String) -> Boolean): Pair<Set<String>, Int> {
        val kept = rawRows.filter { raw ->
            val entry = decodeEntry(raw)
            entry == null || fileExists(entry.path)
        }
        return kept.toSet() to (rawRows.size - kept.size)
    }

    private fun prefs(context: Context) = runCatching {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }.getOrNull()
}

// ---------------------------------------------------------------------------
// audit-05 v1 implementation, replaced by the v3 code above (kept inactive per
// standing rule: removals are commented out, never deleted). Do not re-enable: v1
// had the empty-separator transport defect and read the ambient account.
// package com.example.chat.ui.camera
//
// import android.content.Context
//
// /** Registry of locally captured final files that outlived their send call.
//  *
//  * Submission is not delivery: once a capture is handed to the send pipeline
//  * the local file must not be deleted until a retention policy allows it. That
//  * policy (retention window, close-on-durable-ack signal, any UI surfacing) is
//  * still an open owner decision, so this registry ONLY tracks files. It never
//  * deletes a file; [pruneMissing] only drops registry rows whose file already
//  * vanished by other means, which is bookkeeping hygiene, not deletion.
//  *
//  * Entries are scoped per account so one account never sees another account's
//  * captures, matching the lens/effect prefs scoping. */
// object CameraRetainedFinals {
//
//     data class Entry(val recordedAtMs: Long, val conversationId: String, val path: String)
//
//     private const val PREFS = "chaty_camera_retained_finals"
//     private const val SEP = ""
//     private const val MAX_ENTRIES = 200
//
//     /** Records a kept local final. Never touches the file itself. */
//     @Synchronized
//     fun record(context: Context, conversationId: String, path: String, nowMs: Long = System.currentTimeMillis()) {
//         if (path.isBlank()) return
//         val prefs = runCatching {
//             context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
//         }.getOrNull() ?: return
//         val current = prefs.getStringSet(key(), emptySet())!!.mapNotNull { decode(it) }
//         // FIFO-trim only the registry's own rows; files are never touched.
//         val next = (current + Entry(nowMs, conversationId, path))
//             .sortedBy { it.recordedAtMs }
//             .takeLast(MAX_ENTRIES)
//         prefs.edit().putStringSet(key(), next.map { encode(it) }.toSet()).apply()
//     }
//
//     @Synchronized
//     fun entries(context: Context): List<Entry> {
//         val prefs = runCatching {
//             context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
//         }.getOrNull() ?: return emptyList()
//         return prefs.getStringSet(key(), emptySet())!!.mapNotNull { decode(it) }.sortedBy { it.recordedAtMs }
//     }
//
//     /** Drops registry rows whose file no longer exists. Never deletes files. */
//     @Synchronized
//     fun pruneMissing(context: Context): Int {
//         val prefs = runCatching {
//             context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
//         }.getOrNull() ?: return 0
//         val kept = entries(context).filter { java.io.File(it.path).exists() }
//         val removed = prefs.getStringSet(key(), emptySet())!!.size - kept.size
//         if (removed > 0) prefs.edit().putStringSet(key(), kept.map { encode(it) }.toSet()).apply()
//         return removed
//     }
//
//     private fun key(): String {
//         val account = runCatching {
//             com.example.chat.ServiceLocator.apiBackend.currentAccountKey()
//         }.getOrNull() ?: "anon"
//         return "finals_$account"
//     }
//
//     private fun encode(e: Entry) = "${e.recordedAtMs}$SEP${e.conversationId}$SEP${e.path}"
//
//     private fun decode(raw: String): Entry? {
//         val first = raw.indexOf(SEP)
//         if (first <= 0) return null
//         val second = raw.indexOf(SEP, first + 1)
//         if (second <= first) return null
//         val ts = raw.substring(0, first).toLongOrNull() ?: return null
//         return Entry(ts, raw.substring(first + 1, second), raw.substring(second + 1))
//     }
// }
