package com.example.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import com.example.chat.ui.settings.ChatySettingsPage
import org.junit.Rule
import org.junit.Test

/** HM1-51 regression cases. Source-only, not compiled or executed in this delivery. */
class HideMediaHeadingProposalTest {
    @get:Rule val compose = createComposeRule()
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test fun optedInMediaTitleIsAHeading() {
        compose.setContent {
            MaterialTheme {
                ChatySettingsPage(title = "Hide media", titleModifier = Modifier.semantics { heading() }) {}
            }
        }
        compose.onNodeWithText("Hide media").assert(heading)
        compose.onAllNodes(heading).assertCountEquals(1)
    }

    @Test fun defaultDoesNotChangeOtherSettingsTitleSemantics() {
        compose.setContent { MaterialTheme { ChatySettingsPage(title = "Other settings") {} } }
        compose.onAllNodes(heading).assertCountEquals(0)
    }
}
