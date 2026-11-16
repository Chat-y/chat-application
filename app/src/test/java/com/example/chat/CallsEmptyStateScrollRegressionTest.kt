package com.example.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Narrow source-structure guard only, not a device measure/launch acceptance test. */
class CallsEmptyStateScrollRegressionTest {
    @Test fun lazyListEmptyItemDoesNotAddAnotherUnboundedVerticalScroller() {
        val source = listOf("src/main/kotlin/com/example/chat/ui/calls/CallsScreen.kt",
            "app/src/main/kotlin/com/example/chat/ui/calls/CallsScreen.kt").map(::File).first { it.isFile }.readText()
        val helper = source.substringAfter("private fun CallEmptyState(").substringBefore("private fun formatCallLogDuration")
        val active = helper.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        assertTrue(source.contains("visibleRecords.isEmpty() -> item"))
        assertFalse(active.contains("verticalScroll("))
        assertFalse(active.contains("LazyColumn("))
        assertTrue(active.contains("Modifier.fillMaxWidth().padding(28.dp)"))
    }
}
