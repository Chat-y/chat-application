package com.example.chat

import com.example.chat.ui.calls.bundles.CallBundleId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Existing parser fallback only. No persisted bundle migration or default change. NOT RUN. */
class CallBundleFallbackGapTest {
    @Test fun knownIdsRoundTrip() {
        CallBundleId.all.forEach { bundle -> assertEquals(bundle, CallBundleId.fromId(bundle.id)) }
    }
    @Test fun unknownBlankAndNullUseExistingFallback() {
        val fallback = CallBundleId.fromId(null)
        listOf("", "not-an-existing-bundle-id", " ").forEach { assertEquals(fallback, CallBundleId.fromId(it)) }
    }
}
