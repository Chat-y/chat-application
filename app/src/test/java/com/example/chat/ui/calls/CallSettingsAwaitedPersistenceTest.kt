package com.example.chat.ui.calls

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Source regression fixtures only. Not compiled or run by the author. */
class CallSettingsAwaitedPersistenceTest {
    private class Storage : CallPresentationPrefsStorage {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var accept = true
        override suspend fun getBool(key: String): Boolean? = null
        override suspend fun getString(key: String): String? = null
        override suspend fun putString(key: String, value: String) = true
        override suspend fun putBool(key: String, value: Boolean): Boolean {
            entered.complete(Unit)
            release.await()
            return accept
        }
    }

    @Test fun saveWaitsForStorageBeforePublishing() = runBlocking {
        val storage = Storage()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = CallPresentationPreferencesStore(storage, owner)
            val save = async(start = CoroutineStart.UNDISPATCHED) { store.saveLowDataUsageEnabled(true) }
            storage.entered.await()
            assertFalse(save.isCompleted)
            assertFalse(store.value.lowDataUsageEnabled)
            storage.release.complete(Unit)
            save.await()
            assertTrue(store.value.lowDataUsageEnabled)
        } finally { owner.cancel() }
    }

    @Test fun hideHistoryChoiceWaitsForReceiptAndIsNotAppearanceReset() = runBlocking {
        val storage = Storage()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = CallPresentationPreferencesStore(storage, owner)
            val save = async(start = CoroutineStart.UNDISPATCHED) { store.saveHideCallHistoryOnLockScreen(true) }
            storage.entered.await()
            assertFalse(store.value.hideCallHistoryOnLockScreen)
            storage.release.complete(Unit); save.await()
            assertTrue(store.value.hideCallHistoryOnLockScreen)
            // This adapter has no atomic snapshot implementation. Reset must fail closed,
            // rather than clearing the separate privacy value or reporting false success.
            assertTrue(runCatching { store.resetPresentationDefaults() }.isFailure)
            assertTrue(store.value.hideCallHistoryOnLockScreen)
        } finally { owner.cancel() }
    }

    @Test fun rejectedSaveThrowsToCallerWithoutPublishingRequestedValue() = runBlocking {
        val storage = Storage().apply { accept = false; release.complete(Unit) }
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = CallPresentationPreferencesStore(storage, owner)
            val result = runCatching { store.saveLowDataUsageEnabled(true) }
            assertTrue(result.isFailure)
            assertFalse(store.value.lowDataUsageEnabled)
        } finally { owner.cancel() }
    }
}
