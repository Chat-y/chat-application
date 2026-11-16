package com.example.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
// Invalid package-level import retained: import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.search.GlobalSearchScreen
import com.example.chat.ui.theme.ChatyTheme
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Fake seams only. Authored source coverage, not device evidence until executed. */
class GlobalSearchAsyncBoundaryTest {
    @get:Rule val rule = createComposeRule()
    private val person = UserProfile("person", "Alice", "alice", "A", "0xFF6366F1", "", lastSeenAt = Instant.EPOCH)
    private val room = Conversation("room", ConversationType.direct, "Alice", listOf("person"),
        lastMessageText = "", lastMessageTime = Instant.EPOCH, lastMessageSenderId = "person")
    private val action = "Message Alice (@alice)"

    @Composable
    private fun Screen(
        session: Any = "session",
        hidden: (String) -> Boolean = { false },
        isProtected: (String) -> Boolean = { false },
        conversations: List<Conversation> = emptyList(),
        open: suspend (UserProfile) -> Conversation = { room },
        navigate: (String) -> Unit = {},
        notify: (String) -> Unit = {},
        verify: suspend (String) -> Boolean = { false },
        search: suspend (String) -> List<UserProfile> = { listOf(person) },
        unlock: () -> Unit = {},
    ) {
        val theme = ThemePresets.getSystemDefaultTheme(true)
        ChatyTheme(theme) {
            GlobalSearchScreen(colors = ChatyColors(theme), conversations = conversations,
                searchUsers = search, openDirectConversation = open, verifySecretPhrase = verify,
                isConversationHidden = hidden, isConversationProtected = isProtected,
                searchSessionIdentity = session, isSearchSessionCurrent = { true },
                onOpenConversationReplace = navigate, onNotify = notify, onOpenLockedChats = unlock)
        }
    }

    private fun findAlice() {
        rule.onNodeWithTag("global-search-input").performTextInput("alice")
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
    }

    @Test fun navigationExceptionReleasesBusyRowAndAllowsRetry() {
        var attempts = 0
        var notices = 0
        rule.setContent { Screen(navigate = { attempts++; error("fake navigation failure") }, notify = { notices++ }) }
        findAlice()
        rule.onNodeWithContentDescription(action).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(action).assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(2, attempts); assertEquals(2, notices) }
    }

    @Test fun delayedOpenReadsReplacementPrivacyPredicate() {
        val result = CompletableDeferred<Conversation>()
        val hide = mutableStateOf(false)
        var navigations = 0
        rule.setContent {
            // Capture value, not mutable state: resumption must see the new callback.
            val hiddenNow = hide.value
            Screen(hidden = { hiddenNow }, open = { result.await() }, navigate = { navigations++ })
        }
        findAlice()
        rule.onNodeWithContentDescription(action).performClick()
        rule.runOnIdle { hide.value = true }
        rule.waitForIdle()
        rule.runOnIdle { result.complete(room) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, navigations) }
        rule.onNodeWithContentDescription(action).assertIsEnabled()
    }

    @Test fun delayedOpenUsesReplacementNavigationCallback() {
        val result = CompletableDeferred<Conversation>()
        val replaced = mutableStateOf(false)
        var oldCalls = 0
        var newCalls = 0
        rule.setContent {
            val callback: (String) -> Unit = if (replaced.value) { { newCalls++ } } else { { oldCalls++ } }
            Screen(open = { result.await() }, navigate = callback)
        }
        findAlice()
        rule.onNodeWithContentDescription(action).performClick()
        rule.runOnIdle { replaced.value = true }
        rule.waitForIdle()
        rule.runOnIdle { result.complete(room) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, oldCalls); assertEquals(1, newCalls) }
    }

    @Test fun sessionReplacementCancelsPendingOpenWithoutNavigation() {
        val result = CompletableDeferred<Conversation>()
        val session = mutableStateOf("first")
        var navigations = 0
        rule.setContent { Screen(session = session.value, open = { result.await() }, navigate = { navigations++ }) }
        findAlice()
        rule.onNodeWithContentDescription(action).performClick()
        rule.runOnIdle { session.value = "second" }
        rule.waitForIdle()
        rule.runOnIdle { result.complete(room) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, navigations) }
    }

    @Test fun verifierFailureNeverSendsQueryToDirectory() {
        var directoryCalls = 0
        rule.setContent { Screen(verify = { error("fake verifier failure") }, search = { directoryCalls++; listOf(person) }) }
        findAlice()
        rule.runOnIdle { assertEquals(0, directoryCalls) }
    }
    @Test fun removedScreenRejectsNonCooperativeOpenResult() {
        val result = CompletableDeferred<Conversation>()
        val visible = mutableStateOf(true)
        var navigations = 0
        rule.setContent {
            if (visible.value) Screen(open = {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { result.await() }
            }, navigate = { navigations++ })
        }
        findAlice()
        rule.onNodeWithContentDescription(action).performClick()
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        rule.runOnIdle { result.complete(room) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, navigations) }
    }

    @Test fun settledPrivateMatchUsesReplacementUnlockCallback() {
        val verdict = CompletableDeferred<Boolean>()
        val replaced = mutableStateOf(false)
        var oldCalls = 0
        var newCalls = 0
        var directoryCalls = 0
        rule.setContent {
            val callback: () -> Unit = if (replaced.value) { { newCalls++ } } else { { oldCalls++ } }
            Screen(verify = { verdict.await() }, unlock = callback,
                search = { directoryCalls++; listOf(person) })
        }
        rule.onNodeWithTag("global-search-input").performTextInput("private fixture")
        rule.mainClock.advanceTimeBy(400)
        rule.runOnIdle { replaced.value = true }
        rule.waitForIdle()
        rule.runOnIdle { verdict.complete(true) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, oldCalls); assertEquals(1, newCalls); assertEquals(0, directoryCalls) }
    }

    @Test fun clearRejectsLateNonCooperativeDirectoryResult() {
        val result = CompletableDeferred<List<UserProfile>>()
        var calls = 0
        rule.setContent { Screen(search = {
            calls++
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { result.await() }
        }) }
        rule.onNodeWithTag("global-search-input").performTextInput("alice")
        rule.mainClock.advanceTimeBy(400)
        rule.runOnIdle { assertEquals(1, calls) }
        rule.onNodeWithContentDescription("Clear").performClick()
        rule.runOnIdle { result.complete(listOf(person)) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(action).assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun replacedSessionRejectsLateDirectoryResultWithPermissiveNewPredicate() {
        val result = CompletableDeferred<List<UserProfile>>()
        val session = mutableStateOf("first")
        var calls = 0
        rule.setContent { Screen(session = session.value, search = {
            calls++
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { result.await() }
        }) }
        rule.onNodeWithTag("global-search-input").performTextInput("alice")
        rule.mainClock.advanceTimeBy(400)
        rule.runOnIdle { assertEquals(1, calls); session.value = "second" }
        rule.waitForIdle()
        rule.runOnIdle { result.complete(listOf(person)) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(action).assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun privateMatchInvalidatesEarlierNonCooperativeDirectoryResult() {
        val result = CompletableDeferred<List<UserProfile>>()
        var directoryCalls = 0
        var unlocks = 0
        rule.setContent { Screen(verify = { it == "private fixture" }, unlock = { unlocks++ }, search = {
            directoryCalls++
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { result.await() }
        }) }
        rule.onNodeWithTag("global-search-input").performTextInput("alice")
        rule.mainClock.advanceTimeBy(400)
        rule.runOnIdle { assertEquals(1, directoryCalls) }
        rule.onNodeWithTag("global-search-input").performTextReplacement("private fixture")
        rule.mainClock.advanceTimeBy(400)
        rule.runOnIdle { assertEquals(1, unlocks); assertEquals(1, directoryCalls); result.complete(listOf(person)) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(action).assertDoesNotExist()
    }

    @Test fun removedScreenRejectsLateOperationalOpenFailure() {
        val result = CompletableDeferred<Conversation>()
        val visible = mutableStateOf(true)
        var notices = 0
        var navigations = 0
        rule.setContent {
            if (visible.value) Screen(open = {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { result.await() }
            }, notify = { notices++ }, navigate = { navigations++ })
        }
        findAlice()
        rule.onNodeWithContentDescription(action).performClick()
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        rule.runOnIdle { result.completeExceptionally(IllegalStateException("synthetic late failure")) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, notices); assertEquals(0, navigations) }
    }

    @Test fun knownHiddenRoomStopsBeforeCreateCallback() {
        var creates = 0
        var navigations = 0
        rule.setContent { Screen(conversations = listOf(room), hidden = { it == room.id },
            open = { creates++; room }, navigate = { navigations++ }) }
        findAlice()
        rule.onNodeWithContentDescription(action).performClick()
        rule.runOnIdle { assertEquals(0, creates); assertEquals(0, navigations) }
    }
    @Test fun knownProtectedRoomStopsBeforeCreateCallback() {
        var creates = 0
        rule.setContent { Screen(conversations = listOf(room), isProtected = { it == room.id }, open = { creates++; room }) }
        findAlice()
        rule.onNodeWithContentDescription(action).performClick()
        rule.runOnIdle { assertEquals(0, creates) }
    }
    @Test fun blankRoomCompletionCannotNavigate() {
        var navigations = 0
        var notices = 0
        rule.setContent { Screen(open = { room.copy(id = "") }, navigate = { navigations++ }, notify = { notices++ }) }
        findAlice()
        rule.onNodeWithContentDescription(action).performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, navigations); assertEquals(1, notices) }
    }

}
