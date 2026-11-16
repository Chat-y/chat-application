package com.example.chat

import com.example.chat.data.api.ApiChatClient
import com.example.chat.data.api.ApiHttpTransport
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

/** Offline typed-response fixtures. No authentication/session mutation or network. */
class ApiMessagesReadResultTest {
    private fun client(code: Int, body: String = "fixture") = ApiChatClient(
        { "https://fixture.invalid" }, { "fixture" }, { "fixture" }, ApiHttpTransport { _, _, _, _ -> code to body })

    @Test fun unauthorizedIsNotOutageAndForbiddenIsNotExpiry() {
        assertEquals(ApiChatClient.MessagesReadResult.AuthenticationRequired, client(401).readMessagesChecked("room"))
        assertEquals(ApiChatClient.MessagesReadResult.Forbidden, client(403).readMessagesChecked("room"))
        assertEquals(ApiChatClient.MessagesReadResult.Unavailable, client(500).readMessagesChecked("room"))
    }
    @Test fun successfulEmptyMessagesIsVerifiedSuccess() {
        val result = client(200, """{"data":[]}""").readMessagesChecked("room")
        assertTrue(result is ApiChatClient.MessagesReadResult.Success)
        assertTrue((result as ApiChatClient.MessagesReadResult.Success).messages.isEmpty())
    }
    @Test fun malformedSuccessNeverBecomesEmptySuccess() {
        assertEquals(ApiChatClient.MessagesReadResult.Unavailable, client(200, "{}").readMessagesChecked("room"))
    }
    @Test fun diagnosticTextDoesNotControlClassification() {
        assertEquals(ApiChatClient.MessagesReadResult.Unavailable,
            client(500, "401 session expired sign in again").readMessagesChecked("room"))
    }
    @Test fun missingCredentialsMakeZeroRequests() {
        var calls = 0
        val c = ApiChatClient({ "https://fixture.invalid" }, { "fixture" }, { null },
            ApiHttpTransport { _, _, _, _ -> calls++; error("Unexpected request") })
        assertEquals(ApiChatClient.MessagesReadResult.AuthenticationRequired, c.readMessagesChecked("room"))
        assertEquals(0, calls)
    }
    @Test fun transportFailureIsUnavailable() {
        val c = ApiChatClient({ "https://fixture.invalid" }, { "fixture" }, { "fixture" },
            ApiHttpTransport { _, _, _, _ -> throw java.io.IOException("fixture") })
        assertEquals(ApiChatClient.MessagesReadResult.Unavailable, c.readMessagesChecked("room"))
    }
    @Test(expected = CancellationException::class) fun cancellationPropagates() {
        val c = ApiChatClient({ "https://fixture.invalid" }, { "fixture" }, { "fixture" },
            ApiHttpTransport { _, _, _, _ -> throw CancellationException("fixture") })
        c.readMessagesChecked("room")
    }
    @Test fun legacyNullableReadStillHasItsExistingFailureShape() {
        assertNull(client(401).listMessages("room"))
        assertNull(client(500).listMessages("room"))
    }
    @Test fun fallbackKeepsTypedUnauthorizedOutcome() {
        val urls = mutableListOf<String>()
        val c = ApiChatClient({ "https://fixture.invalid" }, { "fixture" }, { "fixture" },
            ApiHttpTransport { _, url, _, _ -> urls += url; if (urls.size == 1) 404 to "missing" else 401 to "expired" })
        assertEquals(ApiChatClient.MessagesReadResult.AuthenticationRequired, c.readMessagesChecked("room"))
        assertEquals(listOf("https://fixture.invalid/chat/rooms/room/messages",
            "https://fixture.invalid/api/v1/chat/rooms/room/messages"), urls)
    }
    @Test fun invalidPathMakesZeroRequests() {
        var calls = 0
        val c = ApiChatClient({ "https://fixture.invalid" }, { "fixture" }, { "fixture" },
            ApiHttpTransport { _, _, _, _ -> calls++; error("Unexpected request") })
        assertEquals(ApiChatClient.MessagesReadResult.Unavailable, c.readMessagesChecked("../room"))
        assertEquals(0, calls)
    }

}
