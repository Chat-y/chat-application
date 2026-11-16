package com.example.chat

import com.example.chat.ui.media.isStaleOwnedGalleryRow
import org.junit.Assert.*
import org.junit.Test

/** Source fixtures NOT RUN. Provider query/delete races need instrumented tests. */
class PendingGallerySweepPolicyTest {
    private fun stale(owner: String? = "chaty", pending: Int = 1, path: String? = "Pictures/Chaty/", added: Long = 1_000, now: Long = 4_600) =
        isStaleOwnedGalleryRow(owner, "chaty", pending, path, added, now)
    @Test fun onlyOldOwnedPendingExpectedFoldersMatch() {
        assertTrue(stale()); assertTrue(stale(path = "Movies/Chaty"))
        assertFalse(stale(added = 1_001)); assertFalse(stale(added = 0)); assertFalse(stale(now = 100))
    }
    @Test fun publishedForeignUnknownAndOtherFoldersNeverMatch() {
        assertFalse(stale(pending = 0)); assertFalse(stale(owner = "other")); assertFalse(stale(owner = null))
        assertFalse(stale(path = "Pictures/Chaty-other/")); assertFalse(stale(path = null)); assertFalse(stale(path = "Pictures/"))
    }
}
