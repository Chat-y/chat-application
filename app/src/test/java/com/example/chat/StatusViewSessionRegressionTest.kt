package com.example.chat

import com.example.chat.data.services.StatusRecord
import com.example.chat.ui.updates.StatusViewSession
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusViewSessionRegressionTest {
    private val now = Instant.parse("2026-11-15T12:00:00Z")
    private fun record(id: String = "status", expires: Instant = now.plusSeconds(60), deleted: Instant? = null) =
        StatusRecord(id, "owner", "", "text", null, null, 0, 0, now, expires, deleted)

    @Test fun excludesOwnStatus() { assertFalse(StatusViewSession().tryBegin(record(), "owner", now)) }
    @Test fun excludesExpiredStatusAtExactBoundary() { assertFalse(StatusViewSession().tryBegin(record(expires = now), "viewer", now)) }
    @Test fun excludesDeletedStatus() { assertFalse(StatusViewSession().tryBegin(record(deleted = now), "viewer", now)) }
    @Test fun excludesBlankIdentities() {
        assertFalse(StatusViewSession().tryBegin(record(id = ""), "viewer", now))
        assertFalse(StatusViewSession().tryBegin(record(), "", now))
    }
    @Test fun suppressesDuplicateViews() {
        val session = StatusViewSession()
        assertTrue(session.tryBegin(record(), "viewer", now))
        assertFalse(session.tryBegin(record(), "viewer", now))
    }
    @Test fun permitsRetryAfterRollback() {
        val session = StatusViewSession(); val status = record()
        assertTrue(session.tryBegin(status, "viewer", now))
        session.rollback(status, "viewer")
        assertTrue(session.tryBegin(status, "viewer", now))
    }
    @Test fun keepsViewersIsolated() {
        val session = StatusViewSession()
        assertTrue(session.tryBegin(record(), "first", now))
        assertTrue(session.tryBegin(record(), "second", now))
        assertFalse(session.tryBegin(record(), "first", now))
    }
    @Test fun delimiterContainingIdsDoNotCollideAcrossPairs() {
        val session = StatusViewSession()
        assertTrue(session.tryBegin(record(id = "a:b"), "c", now))
        assertTrue(session.tryBegin(record(id = "a"), "b:c", now))
        assertFalse(session.tryBegin(record(id = "a:b"), "c", now))
    }
    @Test fun rollbackOnlyReleasesTheExactPairWithDelimiterIds() {
        val session = StatusViewSession()
        val first = record(id = "a:b")
        val second = record(id = "a")
        assertTrue(session.tryBegin(first, "c", now))
        assertTrue(session.tryBegin(second, "b:c", now))
        session.rollback(first, "c")
        assertTrue(session.tryBegin(first, "c", now))
        assertFalse(session.tryBegin(second, "b:c", now))
    }

}
