package com.example.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.CreatePollDialog
import com.example.chat.ui.chats.home.HomeLocalOrder
import com.example.chat.ui.chats.home.HomeQuickView
import com.example.chat.ui.chats.home.HomeQuickViewDialog
import com.example.chat.ui.chats.home.HomeSavedViewsDialog
import com.example.chat.ui.chats.home.HomeSnapshotSummary
import com.example.chat.ui.settings.SettingsRadioDialog
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test

/**
 * Regression for the infinite-height measure crash (IllegalStateException:
 * "Vertically scrollable component was measured with an infinity maximum
 * height") seen on device 11-14 and 11-16: ChatyConfirmationDialog's body
 * already scrolls, so a caller text slot with its own unbounded
 * verticalScroll/LazyColumn is measured with infinite max height and throws
 * at first composition. Pre-fix, each setContent below throws that ISE and
 * the test fails; post-fix the dialogs render and their titles are found.
 */
class ChatyDialogScrollContractTest {
    @get:Rule
    val rule = createComposeRule()

    private val theme get() = ThemePresets.getSystemDefaultTheme(true)

    @Test
    fun homeQuickViewDialogRendersWithoutNestedScrollCrash() {
        rule.setContent {
            ChatyTheme(theme) {
                HomeQuickViewDialog(
                    view = HomeQuickView.All,
                    order = HomeLocalOrder.Existing,
                    summary = HomeSnapshotSummary(chats = 3, pinned = 1, muted = 1, drafts = 0),
                    onView = {}, onOrder = {}, onReset = {}, onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("Home view options").assertIsDisplayed()
    }

    @Test
    fun homeSavedViewsDialogRendersWithoutNestedScrollCrash() {
        rule.setContent {
            ChatyTheme(theme) {
                HomeSavedViewsDialog(
                    saved = emptyList(),
                    onSave = {}, onPreview = {}, onDelete = {}, onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("Saved Home views").assertIsDisplayed()
    }

    @Test
    fun settingsRadioDialogRendersWithoutNestedScrollCrash() {
        rule.setContent {
            ChatyTheme(theme) {
                SettingsRadioDialog(
                    title = "Chat font size",
                    options = listOf("Small", "Medium", "Large"),
                    selected = "Medium",
                    onSelect = {}, onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("Chat font size").assertIsDisplayed()
    }

    @Test
    fun createPollDialogRendersWithoutNestedScrollCrash() {
        rule.setContent {
            ChatyTheme(theme) {
                CreatePollDialog(onCancel = {}, onCreate = {})
            }
        }
        rule.onNodeWithText("Create poll").assertIsDisplayed()
    }
}
