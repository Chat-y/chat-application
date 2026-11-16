package com.example.chat

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
// b35: invalid top-level import retained as comment: import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.chat.ui.settings.permissions.SystemPermissionsScreen
import org.junit.Rule
import org.junit.Test

/** PM3-28/PM5-39 source-preserve cases, uncompiled/unrun. No requests are invoked. */
class PermissionFooterProposalTest {
    @get:Rule val compose = createComposeRule()
    private val footer get() = "Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}"

    private fun showScreen() {
        compose.setContent { MaterialTheme {
            SystemPermissionsScreen({}, {}, { false }, { false })
        } }
    }

    @Test fun footerNamesActualAndroidVersionAndApi() {
        showScreen()
        compose.onNodeWithText(footer).assertExists()
    }

    @Test fun supplementaryFooterIsNotExposedAsHeading() {
        showScreen()
        compose.onNodeWithText(footer).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
    }
}
