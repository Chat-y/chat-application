package com.example.chat.ui.settings.universal

import org.junit.Assert.*
import org.junit.Test

/** No backup/restore/file execution. Pure inclusion policy source cases, unrun. */
class ConversationBackupInclusionTest {
    @Test fun checkedKeepsExistingPreferencesSnapshot() {
        val values = mapOf<String,Any?>("conversation" to mapOf("hideContactName" to true),"universal" to emptyMap<String,Any?>())
        assertEquals(values,conversationBackupPreferenceSnapshot(values,true))
    }
    @Test fun uncheckedOmitsCompleteSettingsNotJustOneTypedGroup() {
        assertTrue(conversationBackupPreferenceSnapshot(mapOf("conversation" to true,"gbFeatures" to true),false).isEmpty())
    }
    @Test fun uncheckedOmitsAllThreeAppearanceSections() {
        listOf("preferences","wallpapers","customization").forEach { assertFalse(includeConversationAppearanceBackup(false,it)) }
    }
    @Test fun uncheckedDoesNotExcludeDownloadedMedia() {
        assertTrue(includeConversationAppearanceBackup(false,"media.001.zip"))
    }
    @Test fun checkedKeepsEveryExistingSection() {
        listOf("preferences","wallpapers","customization","media.001.zip").forEach { assertTrue(includeConversationAppearanceBackup(true,it)) }
    }
}
