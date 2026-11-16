package com.example.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.chat.ui.settings.ChatySettingsSection
import org.junit.Rule
import org.junit.Test

/** PM3-26 shared source-preserve cases. Uncompiled/unrun, no device heading traversal claim. */
class PermissionSectionHeadingProposalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sectionTitleIsHeadingWithoutChangingBodySemantics() {
        compose.setContent { MaterialTheme {
            ChatySettingsSection(title = "Camera access") { Text("Permission action") }
        } }
        compose.onNodeWithText("CAMERA ACCESS")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Permission action")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
    }

    @Test fun supplementarySectionDescriptionIsNotHeading() {
        compose.setContent { MaterialTheme {
            ChatySettingsSection(title = "Camera access", description = "Used for calls") { Text("Permission action") }
        } }
        compose.onNodeWithText("Used for calls")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
    }
}
