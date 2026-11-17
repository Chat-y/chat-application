package com.example.chat.conversation

import com.example.chat.core.theme.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source contract only: checks active dispatch, never claims pixel uniqueness or runtime rendering. */
class DeliveryRendererContractTest {
    private fun renderer(): String {
        val root = if (File("app/src/main").exists()) File("app/src/main") else File("src/main")
        val source = File(root, "kotlin/com/example/chat/ui/chats/detail/DeliveryStatusIcon.kt").readText()
        return withoutComments(source).substringAfter("private fun DrawScope.drawDeliveryStatus(")
            .substringBefore("private const val DEG")
    }

    // Kotlin permits nested block comments: rollback renderer copies must never count as live branches.
    private fun withoutComments(source: String): String {
        val out = StringBuilder(); var i = 0; var depth = 0; var line = false; var quoted = false
        while (i < source.length) {
            val pair = source.substring(i, minOf(i + 2, source.length))
            if (line) { if (source[i] == '\n') { line = false; out.append('\n') }; i++; continue }
            if (depth > 0) {
                if (pair == "/*") { depth++; i += 2 }
                else if (pair == "*/") { depth--; i += 2 } else i++
                continue
            }
            if (quoted) {
                out.append(source[i])
                if (source[i] == '\\' && i + 1 < source.length) { out.append(source[i + 1]); i += 2; continue }
                if (source[i] == '"') quoted = false
                i++; continue
            }
            if (pair == "//") { line = true; i += 2; continue }
            if (pair == "/*") { depth = 1; i += 2; continue }
            if (source[i] == '"') quoted = true
            out.append(source[i++])
        }
        check(depth == 0) { "Unbalanced rollback comment" }
        return out.toString()
    }

    @Test fun everyCanonicalStyleHasExactlyOneExplicitActiveDrawing() {
        val source = renderer()
        val branches = Regex("DeliveryIconStyle\\.([A-Za-z0-9_]+) ->").findAll(source).map { it.groupValues[1] }.toList()
        assertEquals(19, branches.size)
        assertEquals(uniqueDeliveryStyles.map { it.name }.toSet(), branches.toSet())
        assertTrue(source.contains("canonicalDeliveryStyle(style)"))
        assertTrue(source.contains("error(\"Canonical delivery style is not mapped\")"))
    }

    @Test fun canonicalBranchBodiesAreNotDuplicateGenericDrawings() {
        val source = renderer()
        val starts = Regex("DeliveryIconStyle\\.([A-Za-z0-9_]+) ->").findAll(source).toList()
        val bodies = starts.mapIndexed { index, match ->
            source.substring(match.range.last + 1, starts.getOrNull(index + 1)?.range?.first ?: source.indexOf("else -> error"))
                .filterNot { it.isWhitespace() }
        }
        assertEquals(19, bodies.toSet().size)
    }

    @Test fun readUsesItsThemeTintAndNeverAddsTheRemovedUnderline() {
        val source = renderer()
        assertTrue(source.contains("val read = state == DeliveryState.read"))
        assertTrue(source.contains("val color = if (read) readColor else unreadColor"))
        assertFalse(source.contains("h * .96f"))
        assertFalse(source.contains("if (read) drawLine(readColor"))
        assertTrue(source.contains("if (state == DeliveryState.failed) { paintFailed(failureColor); return }"))
        assertTrue(source.contains("DeliveryState.queued || state == DeliveryState.sending"))
    }
}
