package com.example.chat

import com.example.chat.ui.media.isAllowedViewerSource
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source-only fixtures NOT RUN. Provider grants, symlinks/device and URL redirects unverified. */
class MediaSourcePolicyTest {
    private val roots = listOf(File("/private/app/cache"), File("/private/app/files"))
    @Test fun secureRemoteHostAllowed() { assertTrue(isAllowedViewerSource("https://cdn.example/a?token=x", roots)) }
    @Test fun insecureAndExecutableSchemesRejected() {
        for (s in listOf("http://cdn.example/a", "javascript:alert(1)", "data:image/png,x", "ftp://example/a")) assertFalse(isAllowedViewerSource(s, roots))
    }
    @Test fun malformedHostAndUserInfoRejected() {
        assertFalse(isAllowedViewerSource("https:///a", roots)); assertFalse(isAllowedViewerSource("https://name:pass@example/a", roots))
    }
    @Test fun contentProviderNeedsAuthority() {
        assertTrue(isAllowedViewerSource("content://media/external/1", roots)); assertFalse(isAllowedViewerSource("content:/missing", roots))
    }
    @Test fun appPrivateFileAllowed() {
        assertTrue(isAllowedViewerSource("file:///private/app/cache/item.bin", roots)); assertTrue(isAllowedViewerSource("/private/app/files/item.bin", roots))
    }
    @Test fun siblingPrefixAndTraversalRejected() {
        for (s in listOf("/private/app/cache-other/a", "file:///private/app/cache/../../outside/a", "file://remote/private/app/cache/a", "/sdcard/a", "relative/a", "")) assertFalse(isAllowedViewerSource(s, roots))
    }
    @Test fun initialLoadHasNoRetryDelay() {
        assertEquals(0L, com.example.chat.ui.media.viewerRetryDelayMillis(0))
    }
    @Test fun explicitRetriesBackOffOneTwoFourSeconds() {
        assertEquals(1_000L, com.example.chat.ui.media.viewerRetryDelayMillis(1))
        assertEquals(2_000L, com.example.chat.ui.media.viewerRetryDelayMillis(2))
        assertEquals(4_000L, com.example.chat.ui.media.viewerRetryDelayMillis(3))
    }
    @Test fun fourthRetryAndInvalidCountersAreRejected() {
        assertNull(com.example.chat.ui.media.viewerRetryDelayMillis(4))
        assertNull(com.example.chat.ui.media.viewerRetryDelayMillis(-1))
        assertNull(com.example.chat.ui.media.viewerRetryDelayMillis(Int.MAX_VALUE))
    }
    @Test fun rawAbsoluteNamesKeepLiteralSpacesAndUriPunctuation() {
        for (name in listOf("a photo.jpg", "what?#.jpg", "100% private.jpg")) {
            assertTrue(isAllowedViewerSource("/private/app/cache/$name", roots))
            assertFalse(isAllowedViewerSource("/sdcard/$name", roots))
        }
    }
    @Test fun rawNamesDoNotBypassCanonicalContainment() {
        assertFalse(isAllowedViewerSource("/private/app/cache/../../outside/a photo.jpg", roots))
        assertFalse(isAllowedViewerSource("/private/app/cache-other/a photo.jpg", roots))
        assertFalse(isAllowedViewerSource("/private/app/cache", roots))
    }
}
