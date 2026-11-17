package com.example.chat.ui.kit

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unrun source-contract fixtures, not UI tests. These guard selected wiring only.
 * Focus, IME, semantics trees, pointer behavior, clipping and Android pixels need runtime tests.
 */
class ChatyReusableSourceContractTest {
    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val root = File(dir, "app/src/main/kotlin/com/example/chat/$relative")
            val app = File(dir, "src/main/kotlin/com/example/chat/$relative")
            val candidate = if (root.isFile) root else app
            if (candidate.isFile) {
                // Exclude retained inactive line/block comments before testing executable wiring.
                return candidate.readText().replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
                    .lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
            }
            dir = dir.parentFile
        }
        error("Repository source not found: $relative")
    }

    @Test fun globalSearchKeepsQueryOwnerFocusAndIme() {
        val s = source("ui/search/GlobalSearchScreen.kt")
        assertTrue(s.contains("queueSearch(it)"))
        assertTrue(s.contains("testTag(\"global-search-input\").focusRequester(focusRequester)"))
        assertTrue(s.contains("KeyboardActions(onSearch = { keyboard?.hide() })"))
        assertTrue(s.contains("density = com.example.chat.ui.kit.ChatyFieldDensity.CompactHeader"))
        assertTrue(s.contains("requestGuard.isCurrent(requestId)"))
        assertTrue(s.contains("currentVerifier(trimmed)"))
    }

    @Test fun autoReplyUsesStableIdsAndOneMasterCallback() {
        val s = source("ui/automation/AutoReplyRulesScreen.kt")
        assertTrue(s.contains("engine.toggleRuleById(rule.id, it)"))
        assertFalse(s.contains("engine.toggleRule(index, it)"))
        assertTrue(s.contains("onChanged = { engine.setMasterEnabled(it) }"))
        assertTrue(s.contains("onTap = null"))
    }

    @Test fun folderSelectionAndLongPressStayCallerOwned() {
        val s = source("ui/chats/folders/ChatFolderChips.kt")
        assertTrue(s.contains("onClick = { onSelect(if (selectedFolderId == folder.id) null else folder.id) }"))
        assertTrue(s.contains("onLongClick = { manageFolder = folder }"))
        assertTrue(s.contains("selected = selected, onClick = onTap, role = Role.Tab"))
    }

    @Test fun newChatDiscardKeepsFocusAndDraftResetOwner() {
        val s = source("ui/chats/NewChatScreen.kt")
        assertTrue(s.contains("Modifier.focusRequester(keepEditingFocus)"))
        assertTrue(s.contains("selectedMembers = emptySet()"))
        assertTrue(s.contains("requestedGroupDetails = false"))
        assertTrue(s.contains("groupName = \"\""))
        assertTrue(s.contains("tone = com.example.chat.ui.kit.ChatyButtonTone.Destructive"))
    }

    @Test fun disabledSegmentsHaveNoPressHandlerAndUseSharedMotionTiming() {
        val s = source("ui/kit/StudioPrimitives.kt")
        assertTrue(s.contains("if (enabled) Modifier.springInteractiveScale() else Modifier"))
        assertTrue(s.contains(".widthIn(min = 48.dp)"))
        assertTrue(s.contains("if (reduceMotion) 0 else ChatyMotionTokens.BASE_MS"))
    }
}
