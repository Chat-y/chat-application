package com.example.chat

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** PV4-03 resource-only regression cases. Authored only, uncompiled/unrun. */
class PrivacyBlockedCountPluralProposalTest {
    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.let { context ->
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }).resources
    }

    @Test fun oneContactUsesSingularWithoutGenderedWording() {
        assertEquals("1 blocked contact", resources.getQuantityString(R.plurals.privacy_blocked_contact_count, 1, 1))
    }

    @Test fun zeroAndManyUseOtherQuantityAndRetainTheCount() {
        assertEquals("0 blocked contacts", resources.getQuantityString(R.plurals.privacy_blocked_contact_count, 0, 0))
        assertEquals("23 blocked contacts", resources.getQuantityString(R.plurals.privacy_blocked_contact_count, 23, 23))
    }
}
