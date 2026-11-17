package com.example.chat.data.services

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallOperationDeadlineTest {
    @Test fun missingCallbackTimesOutAndLateOrDuplicateCompletionIsIgnored() = runTest {
        lateinit var complete: (Result<String>) -> Unit
        var cleaned = false
        val operation = async {
            try { awaitCallNativeCallback<String> { complete = it } }
            catch (_: CallOperationTimedOut) { "timed out" }
            finally { cleaned = true }
        }
        runCurrent(); advanceTimeBy(CallOperationDeadline.SDP_MILLIS);runCurrent()
        assertEquals("timed out",operation.await());assertTrue(cleaned)
        complete(Result.success("late"));complete(Result.success("duplicate"))
    }
    @Test fun callerCancellationRemainsCancellationAndNullableSuccessIsNotTimeout() = runTest {
        assertNull(callOperationDeadline<String?>(1000) { null })
        val operation=async { callOperationDeadline(1000) { awaitCancellation() } }
        runCurrent();operation.cancel();runCurrent();assertTrue(operation.isCancelled)
    }
}
