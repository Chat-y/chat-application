package com.example.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-policy regression. This does not establish MLS or receipt runtime safety. */
class ExperimentalSecurityGateTest {
    @Test fun localRoutingOptInDoesNotEnableUnverifiedReceiptOrTamperWork() {
        // Previous OFF-policy assertion retained inactive: assertFalse(ReleaseFeatures.EXPERIMENTAL_MLS_CHAT_ROUTING)
        assertTrue(ReleaseFeatures.EXPERIMENTAL_MLS_CHAT_ROUTING)
        assertFalse(ReleaseFeatures.SCOPED_DELIVERY_RECEIPTS)
        assertFalse(ReleaseFeatures.ENFORCE_TAMPER_GUARD)
    }
}
