package com.example.chat

import com.example.chat.ui.media.MediaPlaybackFileLeases
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source-only fixtures, NOT RUN. These hooks perform no filesystem writes or deletions. */
class MediaPlaybackFileLeasesTest {
    @Test fun sharedCurrentAndQueuePinsNeedAllOwnersToRelease() {
        val file = File("/tmp/lease-test-${java.util.UUID.randomUUID()}.mp3")
        val a = MediaPlaybackFileLeases.acquire(listOf(file.path, file.path))
        val b = MediaPlaybackFileLeases.acquire(listOf(file.path))
        try {
            assertTrue(MediaPlaybackFileLeases.isProtected(file))
            a.close(); a.close()
            assertTrue(MediaPlaybackFileLeases.isProtected(file))
            b.close(); assertFalse(MediaPlaybackFileLeases.isProtected(file))
        } finally { a.close(); b.close() }
    }
    @Test fun canonicalAliasesAndQueuedFilesShareProtection() {
        val root = "/tmp/lease-${java.util.UUID.randomUUID()}"
        val a = File("$root/a.mp3"); val queued = File("$root/b.mp3")
        val lease = MediaPlaybackFileLeases.acquire(listOf("$root/sub/../a.mp3", queued.path))
        try { assertTrue(MediaPlaybackFileLeases.isProtected(a)); assertTrue(MediaPlaybackFileLeases.isProtected(queued)) }
        finally { lease.close() }
    }
}
