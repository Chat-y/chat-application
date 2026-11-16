package com.example.chat

import com.example.chat.ui.settings.cleanupPrivateMediaOnLogout
import org.junit.Assert.*
import org.junit.Test

/** Actual cleanup coordinator, uncompiled/unrun. No file deletion or real account operations. */
class PrivateMediaLogoutCleanupProposalTest {
    @Test fun transientFailureDoesNotSkipIncompleteDownloadOwner() {
        val events = mutableListOf<String>()
        val result = cleanupPrivateMediaOnLogout(
            { events += "transient"; error("synthetic") }, { events += "downloads" })
        assertEquals(listOf("transient", "downloads"), events)
        assertFalse(result.transientReturned)
        assertTrue(result.downloadsReturned)
    }
    @Test fun downloadFailureDoesNotUndoTransientInvocation() {
        val result = cleanupPrivateMediaOnLogout({}, { error("synthetic") })
        assertTrue(result.transientReturned)
        assertFalse(result.downloadsReturned)
    }
    @Test fun bothOwnersInvokedOnceWithoutOriginalDeletionCallback() {
        var transient = 0
        var downloads = 0
        val result = cleanupPrivateMediaOnLogout({ transient++ }, { downloads++ })
        assertEquals(1, transient)
        assertEquals(1, downloads)
        assertTrue(result.transientReturned && result.downloadsReturned)
    }
}
