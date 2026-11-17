package com.example.chat

import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiTasksClient
import com.example.chat.ui.tasks.TaskHistoryStore
import com.example.chat.ui.tasks.TasksController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UNRUN deterministic concurrency fixtures, not live-server creation proof. */
class TasksCreateLoadRaceTest {
    private class History : TaskHistoryStore {
        override fun load(): Map<String, List<String>> = emptyMap()
        override fun save(history: Map<String, List<String>>) = Unit
    }

    @Test fun olderLoadCannotEraseConfirmedCreate() = runBlocking {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val transport = ApiHttpTransport { method, _, _, _ ->
            if (method == "GET") {
                started.countDown()
                check(finish.await(5, TimeUnit.SECONDS)) { "Fixture release timed out" }
                200 to """{"data":[]}"""
            } else 200 to """{"data":{"uid":"created","title":"New"}}"""
        }
        val c = TasksController(ApiTasksClient({ "https://tasks.example.test" }, { "tenant" }, { "fixture" }, transport), History())
        val staleLoad = async(start = CoroutineStart.UNDISPATCHED) { c.loadTasks() }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            c.createTask("New", null, listOf("me"), "medium", null)
            assertEquals(listOf("created"), c.tasks.map { it.id })
            assertFalse(c.hasLoadedOnce)
        } finally { finish.countDown() }
        staleLoad.await()
        assertEquals(listOf("created"), c.tasks.map { it.id })
        // A rejected stale load must not pretend that the complete list was loaded.
        assertFalse(c.hasLoadedOnce)
    }

    @Test fun laterExplicitLoadStillReconcilesNormally() = runBlocking {
        val transport = ApiHttpTransport { method, _, _, _ ->
            if (method == "GET") 200 to """{"data":[{"uid":"server","title":"Server"}]}"""
            else 200 to """{"data":{"uid":"created","title":"New"}}"""
        }
        val c = TasksController(ApiTasksClient({ "https://tasks.example.test" }, { "tenant" }, { "fixture" }, transport), History())
        c.createTask("New", null, listOf("me"), "medium", null)
        c.loadTasks()
        assertTrue(c.hasLoadedOnce)
        assertEquals(listOf("server"), c.tasks.map { it.id })
    }
}
