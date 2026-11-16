package com.example.chat

import com.example.chat.ui.chats.detail.writeEditorOutputAtomically
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class EditorAtomicOutputTest {
    @Test fun failedCompressionCannotTruncateOrDeleteExistingDestination() {
        val folder = createTempDir(prefix = "editor-atomic-")
        try {
            val target = File(folder, "existing.jpg").apply { writeText("original") }
            assertTrue(runCatching {
                writeEditorOutputAtomically(target, {}, replace = { _, _ -> fail("No rename on failure") }) {
                    it.write("partial".toByteArray()); error("synthetic compressor failure")
                }
            }.isFailure)
            assertEquals("original", target.readText())
            assertEquals(listOf("existing.jpg"), folder.list()!!.toList())
        } finally { folder.deleteRecursively() }
    }
    @Test fun failedRenameOrCancellationPreservesExistingDestination() {
        val folder = createTempDir(prefix = "editor-atomic-")
        try {
            val target = File(folder, "existing.jpg").apply { writeText("original") }
            for (cancel in listOf(false, true)) {
                assertTrue(runCatching {
                    writeEditorOutputAtomically(target, { check(!cancel) },
                        replace = { _, _ -> error("synthetic rename failure") }) { it.write("complete".toByteArray()) }
                }.isFailure)
                assertEquals("original", target.readText())
                assertEquals(1, folder.list()!!.size)
            }
        } finally { folder.deleteRecursively() }
    }
    @Test fun successfulCommitReceivesOnlyCompleteTemporarySibling() {
        val folder = createTempDir(prefix = "editor-atomic-")
        try {
            val target = File(folder, "edited.jpg")
            writeEditorOutputAtomically(target, {}, replace = { temporary, destination ->
                assertEquals(folder.canonicalFile, temporary.parentFile.canonicalFile)
                assertEquals("complete", temporary.readText())
                check(temporary.renameTo(destination))
            }) { it.write("complete".toByteArray()) }
            assertEquals("complete", target.readText())
            assertEquals(listOf("edited.jpg"), folder.list()!!.toList())
        } finally { folder.deleteRecursively() }
    }
}
