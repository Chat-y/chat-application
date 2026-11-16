package com.example.chat

import com.example.chat.ui.settings.HideMediaRepository
import com.example.chat.ui.settings.MediaVisibilityMarkers
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** JVM coverage for the Hide-media marker logic (HM1-33, HM3-19). */
class HideMediaRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo(root: File, scans: MutableList<File> = mutableListOf(), debug: Boolean = true) =
        HideMediaRepository(filesDir = root, cacheDir = null, debug = debug, scanMedia = { scans += it })

    @Test fun `markers are per type and independent`() {
        val repo = repo(tmp.root)
        repo.sync(HideMediaRepository.MarkerState(photos = true, videos = false, gifs = true)).getOrThrow()
        assertTrue(repo.markerFile(HideMediaRepository.MediaKind.PHOTOS).isFile)
        assertFalse(repo.markerFile(HideMediaRepository.MediaKind.VIDEOS).exists())
        assertTrue(repo.markerFile(HideMediaRepository.MediaKind.GIFS).isFile)
        // No parent marker: it would hide every type at once (HM1-01).
        assertFalse(File(repo.mediaRoot, MediaVisibilityMarkers.MARKER_FILE_NAME).exists())
    }

    @Test fun `sync is idempotent and reports in sync afterwards`() {
        val repo = repo(tmp.root)
        val state = HideMediaRepository.MarkerState(photos = true, videos = true, gifs = false)
        repo.sync(state).getOrThrow()
        assertTrue(repo.isInSync(state)) // HM3-26 fast path precondition
        repo.sync(state).getOrThrow() // HM3-25 repeated sync gives the same result
        assertTrue(repo.isInSync(state))
    }

    @Test fun `marker writes are atomic and leave no temp files`() {
        val repo = repo(tmp.root)
        repo.sync(HideMediaRepository.MarkerState(photos = true, videos = true, gifs = true)).getOrThrow()
        repo.mediaRoot.walkTopDown().forEach { assertFalse(it.name.endsWith(".tmp")) } // HM3-47
    }

    @Test fun `removing all hides deletes the markers`() {
        val repo = repo(tmp.root)
        val on = HideMediaRepository.MarkerState(photos = true, videos = true, gifs = true)
        repo.sync(on).getOrThrow()
        repo.sync(HideMediaRepository.MarkerState(photos = false, videos = false, gifs = false)).getOrThrow()
        HideMediaRepository.MediaKind.values().forEach { assertFalse(repo.markerFile(it).exists()) }
    }

    @Test fun `read only folder surfaces a failure instead of silent success`() {
        val dir = tmp.newFolder("locked")
        val repo = repo(dir)
        dir.setWritable(false)
        try {
            assertTrue(repo.sync(HideMediaRepository.MarkerState(photos = true, videos = false, gifs = false)).isFailure) // HM3-27
        } finally {
            dir.setWritable(true)
        }
    }

    @Test fun `drift report flags mismatch only`() {
        val repo = repo(tmp.root)
        val state = HideMediaRepository.MarkerState(photos = false, videos = false, gifs = false)
        assertNull(repo.driftReport(state))
        repo.markerFile(HideMediaRepository.MediaKind.PHOTOS).let {
            it.parentFile.mkdirs()
            it.createNewFile()
        }
        assertNotNull(repo.driftReport(state)) // HM3-49
    }

    @Test fun `audit log records changes and failures`() {
        val repo = repo(tmp.root)
        repo.audit("set Photos=true") // HM3-13
        repo.auditFailure("set Videos=true") // HM5-09
        val lines = File(tmp.root, MediaVisibilityMarkers.AUDIT_FILE_NAME).readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("ok set Photos=true"))
        assertTrue(lines[1].contains("failed set Videos=true"))
    }

    @Test fun `affected file count ignores marker files`() {
        val repo = repo(tmp.root)
        repo.sync(HideMediaRepository.MarkerState(photos = true, videos = false, gifs = false)).getOrThrow()
        File(repo.mediaRoot, "Photos/photo.jpg").writeText("x")
        File(repo.mediaRoot, "Videos/video.mp4").writeText("x")
        assertEquals(2, repo.affectedFileCount()) // HM1-30
    }

    @Test fun `media scan runs once per type folder`() {
        val scans = mutableListOf<File>()
        repo(tmp.root, scans).sync(HideMediaRepository.MarkerState(photos = false, videos = false, gifs = false)).getOrThrow()
        assertEquals(3, scans.size) // HM3-15 batched per folder, not per file
    }

    @Test fun `banner flag persists`() {
        val repo = repo(tmp.root)
        assertFalse(repo.isBannerSeen())
        repo.markBannerSeen()
        assertTrue(repo.isBannerSeen()) // HM1-59
    }

    @Test fun `rapid sequential toggles leave markers consistent`() {
        val repo = repo(tmp.root)
        // Sequential form of the on-then-off race (HM5-49): the write path is serialized by the
        // caller's saving guard, so the last state wins and markers match it.
        repo.sync(HideMediaRepository.MarkerState(photos = true, videos = false, gifs = false)).getOrThrow()
        repo.sync(HideMediaRepository.MarkerState(photos = false, videos = false, gifs = false)).getOrThrow()
        assertTrue(repo.isInSync(HideMediaRepository.MarkerState(photos = false, videos = false, gifs = false)))
    }
}
