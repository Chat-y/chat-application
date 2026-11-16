package com.example.chat.ui.chats.starred

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Source-only guards. Actual save/restore and TalkBack acceptance need Android. */
class StarredScrollSourceTest {
    private fun source() = File("src/main/kotlin/com/example/chat/ui/chats/starred/StarredMessagesScreen.kt").readText()
    @Test fun usesSaveableListStateWithStableDisplayKey() {
        val s = source()
        assertTrue(s.contains("rememberSaveable(savedQueryStateKey,"))
        assertTrue(s.contains("LazyListState.Saver"))
        assertTrue(s.contains("state = starredListState"))
    }
    @Test fun sharedZoneAndDeterministicTieBreakRemain() {
        val s = source()
        assertTrue(s.contains("val zone = clock.zone"))
        assertTrue(s.contains(".thenBy { it.first.id }.thenBy { it.second.id }"))
    }
    @Test fun emptyStateGroupingAndHeadingRemain() {
        val s = source()
        assertTrue(s.contains("fillParentMaxWidth().semantics(mergeDescendants = true)"))
        assertTrue(s.contains("Modifier.semantics { heading() }"))
    }
}
