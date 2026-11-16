package com.example.chat.ui.settings.conversation

import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resource formatting regression cases. Source only, not run. */
class ConversationProposalCopyTest {
    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources
    @Test fun round1SummaryIncludesAllFourVisibilityValues() {
        val text = resources.getString(R.string.conv_proposal_header_summary, "NAME", "AVATAR", "CALLS", "STATUS")
        listOf("NAME", "AVATAR", "CALLS", "STATUS").forEach { assertTrue(text.contains(it)) }
    }
    @Test fun round2SummaryDoesNotExposeUnresolvedPlaceholders() {
        assertFalse(resources.getString(R.string.conv_proposal_header_summary, "a", "b", "c", "d").contains("%"))
    }
    @Test fun round3PresenceSamplesAreNotEmpty() {
        listOf(R.string.conv_proposal_typing, R.string.conv_proposal_last_seen,
            R.string.conv_proposal_cycle_presence).forEach { assertTrue(resources.getString(it).isNotBlank()) }
    }
    @Test fun round4SliderUnitHasASeparatingSpace() {
        assertTrue(resources.getString(R.string.conv_proposal_points_unit).startsWith(" "))
    }
    @Test fun round5VisibilityStatesHaveDifferentLabels() {
        assertFalse(resources.getString(R.string.conv_proposal_hidden) == resources.getString(R.string.conv_proposal_shown))
    }
}
