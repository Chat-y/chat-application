package com.example.chat.ui.settings.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-only regression fixtures; not run. */
class SettingsSearchCaseRegressionTest {
    private fun search(query: String) = SettingsSearchLogic.filter(
        query, listOf("Istanbul", "Privacy"), { it }, { "" }, { "" }, { emptyList() },
    )
    @Test fun capitalDottedIUsesCaseInsensitiveComparisonWithoutExpansion() {
        assertEquals(listOf("Istanbul"), search("\u0130ST"))
    }
    @Test fun ordinaryCaseAndTrimStillMatch() {
        assertEquals(listOf("Privacy"), search("  PRIVACY  "))
    }
}
