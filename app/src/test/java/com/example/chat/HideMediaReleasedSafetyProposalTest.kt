package com.example.chat

import com.example.chat.ui.settings.HideMediaRepository
import com.example.chat.ui.settings.MediaVisibilityMarkers
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** b84 source-only declarations. Not compiled/run; temporary fixtures, never user media. */
class HideMediaReleasedSafetyProposalTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun unchangedValidStateDoesNotResubmitScans() {
        var submissions = 0
        val repo = HideMediaRepository(temp.root, scanMedia = { submissions++ })
        val state = HideMediaRepository.MarkerState(true, false, true)
        repo.sync(state).getOrThrow()
        assertEquals(3, submissions)
        repo.sync(state).getOrThrow()
        assertEquals(3, submissions)
    }

    @Test fun missingFoldersAreRepairedEvenWhenAllMarkersOff() {
        val repo = HideMediaRepository(temp.root)
        val state = HideMediaRepository.MarkerState(false, false, false)
        assertFalse(repo.isInSync(state))
        repo.sync(state).getOrThrow()
        assertTrue(repo.isInSync(state))
        MediaVisibilityMarkers.KIND_FOLDER_NAMES.forEach { assertTrue(File(repo.mediaRoot, it).isDirectory) }
    }

    @Test fun markerDirectoryIsRejectedWithoutDeletingIt() {
        val repo = HideMediaRepository(temp.root)
        val marker = repo.markerFile(HideMediaRepository.MediaKind.PHOTOS)
        assertTrue(marker.mkdirs())
        assertFalse(repo.isInSync(HideMediaRepository.MarkerState(true, false, false)))
        assertTrue(repo.sync(HideMediaRepository.MarkerState(false, false, false)).isFailure)
        assertTrue(marker.isDirectory)
    }

    @Test fun repeatedFailuresLogOnePathFreeDiagnostic() {
        val logs = mutableListOf<String>()
        val invalid = temp.newFile("not-a-directory")
        val repo = HideMediaRepository(invalid, debug = true, debugLog = { logs += it })
        repeat(3) { assertTrue(repo.sync(HideMediaRepository.MarkerState(true, false, false)).isFailure) }
        assertEquals(listOf("hide-media marker sync failed"), logs)
    }

    @Test fun mediaCountExcludesTempMarkersAndNonMediaFiles() {
        val repo = HideMediaRepository(temp.root)
        repo.sync(HideMediaRepository.MarkerState(false, false, false)).getOrThrow()
        val folder = File(repo.mediaRoot, "Photos")
        File(folder, "image.JPG").writeText("fixture")
        File(folder, ".nomedia.tmp").writeText("")
        File(folder, "notes.txt").writeText("fixture")
        assertEquals(1, repo.affectedFileCount())
    }
    @Test fun invalidLaterMarkerDoesNotRemoveEarlierProtection() {
        val root = temp.newFolder("preflight")
        MediaVisibilityMarkers.sync(root, true, false, false)
        val photos = File(root, "Photos/.nomedia")
        val invalidVideos = File(root, "Videos/.nomedia")
        assertTrue(invalidVideos.mkdir())
        assertTrue(runCatching { MediaVisibilityMarkers.sync(root, false, false, false) }.isFailure)
        assertTrue(photos.isFile)
        assertTrue(invalidVideos.isDirectory)
    }

    @Test fun invalidTypeFolderDoesNotRemoveLegacyParentMarker() {
        val root = temp.newFolder("invalid-type")
        val parentMarker = File(root, ".nomedia").apply { writeText("") }
        File(root, "Videos").writeText("not a directory")
        assertTrue(runCatching { MediaVisibilityMarkers.sync(root, false, false, false) }.isFailure)
        assertTrue(parentMarker.isFile)
    }
    @Test fun externalCacheMediaWithoutMarkerIsReportedWithoutMutation() {
        val cache = temp.newFolder("external-cache")
        val image = File(cache, "photo.jpg").apply { writeText("fixture") }
        val repo = HideMediaRepository(temp.root, additionalCacheDirs = { listOf(cache) })
        val report = repo.driftReport(HideMediaRepository.MarkerState(false, false, false)).orEmpty()
        assertTrue(report.contains("media cache has no .nomedia file"))
        assertTrue(image.isFile)
        assertFalse(File(cache, ".nomedia").exists())
    }

    @Test fun cacheMarkerPresenceIsCheckedWithoutClaimingFilesAreHidden() {
        val cache = temp.newFolder("marked-cache")
        File(cache, "photo.jpg").writeText("fixture")
        File(cache, ".nomedia").writeText("")
        val repo = HideMediaRepository(temp.root, cacheDir = cache)
        val report = repo.driftReport(HideMediaRepository.MarkerState(false, false, false)).orEmpty()
        assertTrue(report.contains("media files present in cache dir"))
        assertFalse(report.contains("media cache has no .nomedia file"))
        assertFalse(report.contains("hidden", ignoreCase = true))
    }
    @Test fun markerWriteDoesNotDeleteUnrelatedLegacyTempFile() {
        val root = temp.newFolder("temp-ownership")
        val photos = File(root, "Photos").apply { mkdirs() }
        val legacyTemp = File(photos, ".nomedia.tmp").apply { writeText("unowned fixture") }
        MediaVisibilityMarkers.sync(root, true, false, false)
        assertTrue(File(photos, ".nomedia").isFile)
        assertEquals("unowned fixture", legacyTemp.readText())
        assertEquals(emptyList<String>(), photos.listFiles().orEmpty().filter { it.name.startsWith(".nomedia-") }.map { it.name })
    }
}
