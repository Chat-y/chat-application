package com.example.chat

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** HM5-39 quantity cases, authored only and uncompiled/unrun. */
class HideMediaCountPluralProposalTest {
    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.let { context ->
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }).resources
    }

    @Test fun singleFileUsesSingularAndKeepsLocalScopeWording() {
        assertEquals("1 file currently in private chat media folders.",
            resources.getQuantityString(R.plurals.privacy_private_media_file_count, 1, 1))
    }

    @Test fun zeroAndManyRetainCountAndNeutralWording() {
        for (count in listOf(0, 27)) assertEquals("$count files currently in private chat media folders.",
            resources.getQuantityString(R.plurals.privacy_private_media_file_count, count, count))
    }
}
