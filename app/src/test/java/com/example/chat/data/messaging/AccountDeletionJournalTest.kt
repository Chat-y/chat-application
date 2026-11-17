package com.example.chat.data.messaging

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Authored only: execution is gated with the app's no-build restriction. */
class AccountDeletionJournalTest {
    private class Cache : SnapshotCacheStore {
        var saved: Any? = null
        override suspend fun readJson(userId: String, scope: String): Any? = saved
        override suspend fun writeJson(userId: String, scope: String, value: Any?) { saved = value }
        override suspend fun readJsonStrict(userId: String, scope: String) = saved
        override suspend fun writeJsonStrict(userId: String, scope: String, value: Any?) { saved = value }
    }
    @Test fun unknownOutcomeSurvivesRestartAndDoesNotBecomeConfirmed() = runBlocking {
        val cache = Cache(); val j = AccountDeletionJournal(cache)
        j.record(AccountDeletionJournal.Entry("a", "uid", "session", false))
        assertFalse(AccountDeletionJournal(cache).entries().single().confirmed)
    }
    @Test fun confirmedReceiptCannotBeDowngradedByAStalePendingWrite() = runBlocking {
        val cache = Cache(); val j = AccountDeletionJournal(cache)
        j.record(AccountDeletionJournal.Entry("a", "uid", "session", true))
        j.record(AccountDeletionJournal.Entry("a", "uid", "session", false))
        assertTrue(AccountDeletionJournal(cache).entries().single().confirmed)
    }
    @Test fun confirmationAbsorbsAStalePendingRelease() = runBlocking {
        val cache = Cache(); val journal = AccountDeletionJournal(cache)
        val pending = AccountDeletionJournal.Entry("a", "uid", null, false,
            operationId = "op", dispatched = true)
        journal.record(pending.copy(confirmed = true))
        journal.record(pending.copy(releasedPending = true))
        val saved = journal.entries().single()
        assertTrue(saved.confirmed); assertFalse(saved.releasedPending)
    }
    @Test fun olderOperationCannotReplaceCurrentDispatchedOperation() = runBlocking {
        val cache = Cache(); val journal = AccountDeletionJournal(cache)
        journal.record(AccountDeletionJournal.Entry("a", "uid", null, false, operationId = "new", dispatched = true))
        var rejected = false
        try { journal.record(AccountDeletionJournal.Entry("a", "uid", null, false, operationId = "old", releasedPending = true)) }
        catch (_: Exception) { rejected = true }
        assertTrue(rejected); assertEquals("new", journal.entries().single().operationId)
    }

    @Test fun freshOperationAfterReleasedPriorRevisionIsAtomic() = runBlocking {
        val cache = Cache(); val journal = AccountDeletionJournal(cache)
        journal.beginNextOperation(AccountDeletionJournal.Entry("a", "uid", null, false, operationId = "first"))
        val first = journal.entries().single()
        journal.record(first.copy(releasedPending = true, notDeletedStatus = "expired"))
        val priorRevision = journal.entries().single().revision
        journal.beginNextOperation(AccountDeletionJournal.Entry("a", "uid", null, false, operationId = "next"))
        val next = journal.entries().single()
        assertEquals("next", next.operationId); assertEquals(priorRevision + 1, next.revision)
        assertFalse(next.releasedPending)
        var rejected = false
        try { journal.record(first.copy(releasedPending = true)) } catch (_: Exception) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun dispatchedRejectedAndExpiredOperationsCanStartFresh() = runBlocking {
        for (status in listOf("rejected", "expired")) {
            val journal = AccountDeletionJournal(Cache())
            journal.beginNextOperation(AccountDeletionJournal.Entry("a", "uid", null, false, operationId = "first"))
            journal.record(journal.entries().single().copy(dispatched = true))
            journal.record(journal.entries().single().copy(releasedPending = true, notDeletedStatus = status))
            val restarted = journal.entries().single()
            assertTrue(restarted.dispatched); assertTrue(restarted.releasedPending)
            journal.beginNextOperation(AccountDeletionJournal.Entry("a", "uid", null, false, operationId = "next"))
            assertEquals("next", journal.entries().single().operationId)
        }
    }
    @Test fun pendingCannotReleaseEvenWhenNotDispatched() = runBlocking {
        val journal = AccountDeletionJournal(Cache())
        journal.beginNextOperation(AccountDeletionJournal.Entry("a", "uid", null, false, operationId = "first"))
        var blocked = false
        try { journal.record(journal.entries().single().copy(releasedPending = true)) } catch (_: IllegalStateException) { blocked = true }
        assertTrue(blocked); assertFalse(journal.entries().single().releasedPending)
    }

}

// Row51 v10 originals retained byte-for-byte after the comment prefix.
// Original segment 1:
//         journal.record(first.copy(releasedPending = true))
