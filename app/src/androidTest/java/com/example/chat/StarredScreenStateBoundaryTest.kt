package com.example.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.chats.starred.StarredMessagesScreen
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Fake snapshots and callbacks only. All assertions are unexecuted until a device run is performed. */
class StarredScreenStateBoundaryTest {
    @get:Rule val rule = createComposeRule()
    private val room = Conversation("room", ConversationType.direct, "Fixture room", emptyList(),
        lastMessageText = "", lastMessageTime = Instant.EPOCH, lastMessageSenderId = "fixture")
    private fun message(id: String, text: String, deleted: Boolean = false) = ChatMessage(
        id = id, conversationId = room.id, senderId = "fixture", text = text,
        createdAt = Instant.EPOCH, isStarred = true, isDeletedForEveryone = deleted)

    @Composable private fun Screen(
        session: Any? = "synthetic-account",
        messages: List<ChatMessage> = emptyList(),
        revision: Int = 0,
        refresh: suspend () -> Boolean = { true },
        open: (String, String?) -> Unit = { _, _ -> },
    ) {
        val theme = ThemePresets.getSystemDefaultTheme(true)
        ChatyTheme(theme) {
            StarredMessagesScreen(conversations = listOf(room), messagesFor = { messages },
                messagesRevision = revision, onRefresh = refresh, onToggleStar = { _, _ -> },
                onOpenConversation = open, onBack = {}, queryStateKey = session)
        }
    }

    @Test fun loadingFailureAndSuccessfulEmptyAreDifferentStates() {
        val result = CompletableDeferred<Boolean>()
        var calls = 0
        rule.setContent { Screen(refresh = { calls++; if (calls == 1) result.await() else true }) }
        rule.onNodeWithText("Loading starred messages").assertIsDisplayed()
        rule.onNodeWithText("No starred messages yet").assertDoesNotExist()
        rule.runOnIdle { result.complete(false) }
        rule.waitForIdle()
        rule.onNodeWithText("Starred messages unavailable").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("No starred messages yet").assertIsDisplayed()
        rule.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun imeAppliesCurrentSearchWithoutWaitingForDebounce() {
        rule.setContent { Screen(messages = listOf(message("one", "alpha fixture"), message("two", "beta fixture"))) }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("starred-search-input").performTextInput("beta")
            rule.onNodeWithTag("starred-search-input").performImeAction()
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag("starred-message:room:one").assertDoesNotExist()
            rule.onNodeWithTag("starred-message:room:two").assertIsDisplayed()
        } finally { rule.mainClock.autoAdvance = true }
    }

    @Test fun deletedForEveryoneOpensRoomWithoutDeletedMessageTarget() {
        var opened: Pair<String, String?>? = null
        rule.setContent { Screen(messages = listOf(message("deleted", "", true)), open = { id, target -> opened = id to target }) }
        rule.onNodeWithTag("starred-message:room:deleted").performClick()
        rule.runOnIdle { assertEquals("room" to null, opened) }
    }

    @Test fun newMessageSnapshotRemovesUnstarredRowWithoutQueryEdit() {
        val rows = mutableStateOf(listOf(message("one", "fixture")))
        rule.setContent { Screen(messages = rows.value, revision = rows.value.size) }
        rule.onNodeWithTag("starred-message:room:one").assertIsDisplayed()
        rule.runOnIdle { rows.value = emptyList() }
        rule.waitForIdle()
        rule.onNodeWithTag("starred-message:room:one").assertDoesNotExist()
        rule.onNodeWithText("No starred messages yet").assertIsDisplayed()
    }
    @Test fun replacedAccountDoesNotReuseLateManualRefreshResult() {
        val session = mutableStateOf("first")
        val late = CompletableDeferred<Boolean>()
        var firstCalls = 0
        var secondCalls = 0
        rule.setContent {
            val account = session.value
            Screen(session = account, refresh = {
                if (account == "first") {
                    firstCalls++
                    if (firstCalls == 1) true else kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { late.await() }
                } else { secondCalls++; true }
            })
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Refresh starred messages").performClick()
        rule.runOnIdle { session.value = "second" }
        rule.waitForIdle()
        rule.runOnIdle { late.complete(false) }
        rule.waitForIdle()
        rule.onNodeWithText("No starred messages yet").assertIsDisplayed()
        rule.onNodeWithText("Starred messages unavailable").assertDoesNotExist()
        rule.runOnIdle { assertEquals(2, firstCalls); assertEquals(1, secondCalls) }
    }

    @Test fun restoredSearchForAccountIsAppliedWithoutAnotherTextEdit() {
        val session = mutableStateOf("first")
        // SaveableStateProvider models returning to an existing account destination.
        rule.setContent {
            val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            holder.SaveableStateProvider(session.value) {
                Screen(session = session.value, messages = listOf(message("one", "alpha fixture"), message("two", "beta fixture")))
            }
        }
        rule.onNodeWithTag("starred-search-input").performTextInput("beta")
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithTag("starred-message:room:one").assertDoesNotExist()
        rule.onNodeWithTag("starred-message:room:two").assertIsDisplayed()
        rule.runOnIdle { session.value = "second" }
        rule.waitForIdle()
        rule.onNodeWithTag("starred-search-input").performTextReplacement("alpha")
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithTag("starred-message:room:one").assertIsDisplayed()
        rule.onNodeWithTag("starred-message:room:two").assertDoesNotExist()
        rule.runOnIdle { session.value = "first" }
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithTag("starred-message:room:one").assertDoesNotExist()
        rule.onNodeWithTag("starred-message:room:two").assertIsDisplayed()
    }

}
