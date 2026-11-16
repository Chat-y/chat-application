package com.example.chat

import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiStatusClient
import com.example.chat.data.services.StatusIdentityUnboundException
import com.example.chat.data.services.StatusService
import com.example.chat.domain.models.PrivacyPreferencesSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StatusViewerFailureRegressionTest {
    private fun service(transport: ApiHttpTransport, bound: () -> Boolean = { true }) = StatusService(
        ApiStatusClient({ "https://fixture.invalid" }, { "fixture" }, { "fixture" }, transport),
        { _, _, _ -> error("Upload not expected") },
        { "owner" }, { PrivacyPreferencesSnapshot() }, identityBound = bound,
    )

    @Test fun successfulEmptyViewersRemainsEmpty() = runBlocking {
        val s = service(ApiHttpTransport { _, _, _, _ ->
            200 to """{"data":{"viewers":[]}}"""
        })
        assertTrue(s.viewersFor("status1").isEmpty())
    }

    @Test fun failedResponseIsNotAZeroCount() = runBlocking {
        val s = service(ApiHttpTransport { _, _, _, _ -> 500 to "failure" })
        try { s.viewersFor("status1"); fail("Expected a load failure") }
        catch (_: IllegalStateException) { }
    }

    @Test fun transportFailureIsNotAZeroCount() = runBlocking {
        val s = service(ApiHttpTransport { _, _, _, _ -> throw java.io.IOException("fixture") })
        try { s.viewersFor("status1"); fail("Expected a transport failure") }
        catch (_: java.io.IOException) { }
    }

    @Test fun cancellationIsNotConvertedIntoEmpty() = runBlocking {
        val s = service(ApiHttpTransport { _, _, _, _ -> throw CancellationException("fixture") })
        try { s.viewersFor("status1"); fail("Expected cancellation") }
        catch (_: CancellationException) { }
    }

    @Test fun identityIsCheckedAgainAfterNetworkCompletion() = runBlocking {
        var bound = true
        val s = service(ApiHttpTransport { _, _, _, _ ->
            bound = false
            200 to """{"data":{"viewers":[]}}"""
        }, { bound })
        try { s.viewersFor("status1"); fail("Expected stale identity rejection") }
        catch (_: StatusIdentityUnboundException) { }
    }
}
