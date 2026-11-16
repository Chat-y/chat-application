package com.example.chat

import com.example.chat.ui.settings.HideMediaRepository
import com.example.chat.ui.settings.mediaDiskIo
import com.example.chat.ui.settings.HideMediaDiskQueue
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class HideMediaIoTest {
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    @Test fun repositoryWritesAndScanCallbackRunOffCallerAndReturnToIt() = runBlocking {
        newSingleThreadContext("test-screen").use { caller ->
            withContext(caller) {
                val callerId = Thread.currentThread().id
                val root = Files.createTempDirectory("marker-io-test").toFile()
                try {
                    var scanId: Long? = null
                    val repository = HideMediaRepository(root, scanMedia = { scanId = Thread.currentThread().id })
                    val count = mediaDiskIo {
                        assertNotEquals(callerId, Thread.currentThread().id)
                        repository.sync(HideMediaRepository.MarkerState(true, false, false)).getOrThrow()
                        repository.audit("test")
                        repository.markBannerSeen()
                        repository.affectedFileCount()
                    }
                    assertNotEquals(callerId, scanId)
                    assertEquals(callerId, Thread.currentThread().id)
                    assertTrue(repository.markerFile(HideMediaRepository.MediaKind.PHOTOS).exists())
                    assertTrue(repository.isBannerSeen()); assertTrue(count >= 0)
                } finally { root.deleteRecursively() }
            }
        }
    }
    @Test fun repositoryFailureReturnsToCallerWithoutFalseSuccess() = runBlocking {
        val root = Files.createTempDirectory("marker-io-fail").toFile()
        try {
            val invalid = root.resolve("file").apply { writeText("not a directory") }
            val result = runCatching { mediaDiskIo { HideMediaRepository(invalid).sync(HideMediaRepository.MarkerState(true, false, false)).getOrThrow() } }
            assertTrue(result.isFailure)
        } finally { root.deleteRecursively() }
    }
    @Test fun entrySyncAndSubsequentWritesStayOrdered() = runBlocking {
        val queue = HideMediaDiskQueue()
        val entered = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val first = launch { queue.run {
            events.add("entry-start"); entered.complete(Unit)
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            events.add("entry-finish")
        } }
        entered.await()
        val second = launch(start = CoroutineStart.UNDISPATCHED) { queue.run { events.add("write") } }
        assertEquals(listOf("entry-start"), events.toList())
        release.countDown(); first.join(); second.join()
        assertEquals(listOf("entry-start", "entry-finish", "write"), events.toList())
    }

    @Test fun startedWritePublishesBeforeCancellationCanSplitTheCommit() = runBlocking {
        val queue = HideMediaDiskQueue(); val entered = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1); var published = false
        val job = launch { queue.transaction<Boolean>(action = {
            entered.complete(Unit)
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            true
        }, publish = { published = it }) }
        entered.await(); job.cancel(); release.countDown(); job.join()
        assertTrue(published)
    }

}
