package com.example.chat

import com.example.chat.ui.media.MediaExportLineage
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source-only fixtures, NOT RUN. Does not create, delete or overwrite any file. */
class MediaExportLineageTest {
    @Test fun uuidNamesUntouchedWhileSchemaAndOrderingStayInMemory() {
        val lineage = MediaExportLineage(2)
        val a = File("/tmp/chaty-outgoing-edited-a.jpg")
        val b = File("/tmp/chaty-outgoing-edited-b.jpg")
        lineage.register(a, "content://private/source-a")
        lineage.register(b, a.path)
        assertEquals(listOf(1L, 2L), lineage.snapshot().map { it.sequence })
        assertTrue(lineage.snapshot().all { it.schemaVersion == 1 })
        assertEquals(a.canonicalPath, lineage.snapshot().first().outputPath)
        assertEquals("content://private/source-a", lineage.snapshot().first().source)
        lineage.close(); assertTrue(lineage.snapshot().isEmpty())
    }
    @Test fun sessionBoundsReplacementAndFailureForgetAreExplicit() {
        val lineage = MediaExportLineage(1)
        val a = File("/tmp/a.jpg"); val b = File("/tmp/b.jpg")
        lineage.register(a, "source"); lineage.register(b, "source")
        assertEquals(listOf(b.canonicalPath), lineage.snapshot().map { it.outputPath })
        lineage.forget(b); assertTrue(lineage.snapshot().isEmpty())
    }
    @Test(expected = IllegalStateException::class)
    fun disposedSessionCannotRetainLateSourceMetadata() {
        val lineage = MediaExportLineage()
        lineage.close()
        lineage.register(File("/tmp/late.jpg"), "content://private/late")
    }
}
