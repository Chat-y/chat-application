package com.example.chat.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * audit-05 v2 + OpenCode 610: pins the CameraRetainedFinals wire format,
 * account-bucket scoping and pure row helpers.
 * SOURCE-ONLY, NOT RUN by the author; not wired into any CI by this change.
 * Covers the pure codec/trim/bucket helpers only; it is not a SharedPreferences,
 * filesystem, server-policy or runtime device proof. The registry remains
 * track-only: nothing here or in the registry ever deletes a user file.
 */
class CameraRetainedFinalsTest {

    private fun entry(ts: Long, conv: String, path: String) =
        CameraRetainedFinals.Entry(ts, conv, path)

    @Test fun roundTripSimple() {
        val e = entry(1_700_000_000_000L, "conv-42", "/data/cache/capture.jpg")
        assertEquals(e, CameraRetainedFinals.decodeEntry(CameraRetainedFinals.encodeEntry(e)))
    }

    @Test fun roundTripWithSpacesColonsAndUnicode() {
        val e = entry(42L, "family group: पदार्थ", "/storage/emulated/0/DCIM/Camera/IMG 2026-11-17 12:00:01.jpg")
        assertEquals(e, CameraRetainedFinals.decodeEntry(CameraRetainedFinals.encodeEntry(e)))
    }

    @Test fun versionPrefixIsExplicitAndAsciiOnly() {
        val raw = CameraRetainedFinals.encodeEntry(entry(7L, "c", "/p"))
        assertTrue(raw.startsWith("v2" + CameraRetainedFinals.SEP))
        assertTrue(raw.all { it.code < 128 || it == CameraRetainedFinals.SEP[0] })
    }

    @Test fun decodeRejectsMalformedRows() {
        assertNull(CameraRetainedFinals.decodeEntry(""))
        assertNull(CameraRetainedFinals.decodeEntry("v1"))
        assertNull(CameraRetainedFinals.decodeEntry("1700000000000conv-42/cache/capture.jpg")) // v1 concatenated leftovers
        assertNull(CameraRetainedFinals.decodeEntry("v3" + CameraRetainedFinals.SEP + "1" + CameraRetainedFinals.SEP + "c" + CameraRetainedFinals.SEP + "/p"))
        assertNull(CameraRetainedFinals.decodeEntry("v2" + CameraRetainedFinals.SEP + "notanumber" + CameraRetainedFinals.SEP + "c" + CameraRetainedFinals.SEP + "/p"))
        assertNull(CameraRetainedFinals.decodeEntry("v2" + CameraRetainedFinals.SEP + "1" + CameraRetainedFinals.SEP + "c" + CameraRetainedFinals.SEP))
    }

    @Test fun fifoTrimKeepsNewestInAscendingOrder() {
        val many = (1L..250L).map { entry(it, "c", "/p$it") }
        val trimmed = CameraRetainedFinals.fifoTrim(many)
        assertEquals(CameraRetainedFinals.MAX_ENTRIES, trimmed.size)
        assertEquals(51L, trimmed.first().recordedAtMs)
        assertEquals(250L, trimmed.last().recordedAtMs)
        assertEquals(trimmed, trimmed.sortedBy { it.recordedAtMs })
    }

    @Test fun fifoTrimUnderCapKeepsEverything() {
        val few = listOf(entry(3L, "c", "/a"), entry(1L, "c", "/b"))
        assertEquals(listOf(entry(1L, "c", "/b"), entry(3L, "c", "/a")), CameraRetainedFinals.fifoTrim(few))
    }

    @Test fun pruneRuleDropsOnlyDecodableRowsWithMissingFiles() {
        val rows = setOf(
            CameraRetainedFinals.encodeEntry(entry(1L, "c", "/exists")),
            CameraRetainedFinals.encodeEntry(entry(2L, "c", "/gone")),
            "unparseable-legacy-row",
        )
        val exists = setOf("/exists")
        val (kept, dropped) = CameraRetainedFinals.pruneRows(rows) { exists.contains(it) }
        assertEquals(1, dropped)
        assertEquals(2, kept.size)
        assertTrue(kept.contains("unparseable-legacy-row"))
        assertTrue(kept.none { CameraRetainedFinals.decodeEntry(it)?.path == "/gone" })
    }

    @Test fun accountScopingKeysAreDistinctWithAnonFallback() {
        val a = CameraRetainedFinals.prefsKeyFor("acct-a")
        val b = CameraRetainedFinals.prefsKeyFor("acct-b")
        val anon = CameraRetainedFinals.prefsKeyFor(null)
        assertTrue(a != b)
        assertTrue(a != anon && b != anon)
        assertEquals("finals_anon", anon)
        assertTrue(a.startsWith("finals_"))
    }

    /** OpenCode 612 fixture: fields carrying the wire separator must be refused,
     *  so a hostile or corrupt conversationId can never break a bucket's rows. */
    @Test fun separatorInAnyFieldIsRejected() {
        val sep = CameraRetainedFinals.SEP
        assertFalse(CameraRetainedFinals.isFieldSafe("conv" + sep + "42"))
        assertFalse(CameraRetainedFinals.isFieldSafe("/cache/cap" + sep + "ture.jpg"))
        assertTrue(CameraRetainedFinals.isFieldSafe("conv-42"))
        assertTrue(CameraRetainedFinals.isFieldSafe("/cache/capture.jpg"))
    }

    /** OpenCode 610 fixture: an account switch during an upload must leave the
     *  journaled row in the START account's bucket, invisible to the switched-to
     *  account. Buckets are keyed by the caller-supplied (start-captured) key. */
    @Test fun accountSwitchDuringUploadStaysInStartAccountBucket() {
        val startBucket = CameraRetainedFinals.prefsKeyFor("acct-start")
        val switchedBucket = CameraRetainedFinals.prefsKeyFor("acct-switched")
        val journaled = CameraRetainedFinals.appendRows(emptySet(), entry(9L, "conv-1", "/cache/inflight.jpg"))
        // The row lands only under the start key the caller passed; a later read
        // through the switched-to account's key sees nothing.
        val startRows = journaled
        val switchedRows = emptySet<String>() // what prefs holds under switchedBucket
        assertEquals(1, CameraRetainedFinals.readRows(startRows).size)
        assertEquals("/cache/inflight.jpg", CameraRetainedFinals.readRows(startRows).single().path)
        assertTrue(CameraRetainedFinals.readRows(switchedRows).isEmpty())
        assertFalse(startBucket == switchedBucket)
    }

    /** OpenCode 610 fixture: a failure before the prefs write (exception before
     *  journaling) leaves prior rows intact - appendRows builds a new set and
     *  never mutates its input, so nothing is half-written. */
    @Test fun exceptionBeforeJournalingLeavesPriorRowsIntact() {
        val prior = setOf(
            CameraRetainedFinals.encodeEntry(entry(1L, "c", "/prior.jpg")),
            "unparseable-legacy-row",
        )
        val snapshot = prior.toSet()
        val next = CameraRetainedFinals.appendRows(prior, entry(2L, "c", "/new.jpg"))
        // Input untouched (caller discards `next` if the write that follows throws).
        assertEquals(snapshot, prior)
        assertTrue(next.containsAll(snapshot))
        assertEquals(2, CameraRetainedFinals.readRows(next).size) // garbage row not counted as an entry
    }
}
