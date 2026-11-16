package com.example.chat

import com.example.chat.ui.updates.StatusExternalOpenPolicy
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusExternalOpenPolicyTest {
    private val now = Instant.parse("2026-11-15T12:00:00Z")
    private val future = now.plusSeconds(60)

    @Test fun permitsOnlyUnexpiredHierarchicalHttps() {
        assertTrue(StatusExternalOpenPolicy.canOpen("https://media.example.invalid/file?token=fixture", future, now))
        assertTrue(StatusExternalOpenPolicy.canOpen("HTTPS://media.example.invalid/file", future, now))
    }

    @Test fun rejectsNonHttpsAndRelativeUrls() {
        for (url in listOf("http://media.example.invalid/file", "file:///private/file", "content://private/file", "javascript:alert(1)", "//media.example.invalid/file", "/file")) {
            assertFalse(url, StatusExternalOpenPolicy.canOpen(url, future, now))
        }
    }

    @Test fun rejectsOpaqueMalformedAndCredentialBearingUrls() {
        for (url in listOf("https:opaque", "https:///missing-host", "https://name:password@media.example.invalid/file", "not a url")) {
            assertFalse(url, StatusExternalOpenPolicy.canOpen(url, future, now))
        }
    }

    @Test fun expiresExactlyAtBoundary() {
        assertFalse(StatusExternalOpenPolicy.canOpen("https://media.example.invalid/file", now, now))
        assertFalse(StatusExternalOpenPolicy.canOpen("https://media.example.invalid/file", now.minusSeconds(1), now))
    }
}
