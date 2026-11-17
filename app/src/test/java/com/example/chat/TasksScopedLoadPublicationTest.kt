package com.example.chat

import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiTasksClient
import com.example.chat.ui.tasks.TaskHistoryStore
import com.example.chat.ui.tasks.TasksController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** UNRUN fixtures for owner/generation-scoped load publication, not live backend proof. */
class TasksScopedLoadPublicationTest {
    private class History : TaskHistoryStore {
        override fun load(): Map<String, List<String>> = emptyMap()
        override fun save(history: Map<String, List<String>>) = Unit
    }
    @Test fun staleFailureCannotPublishAfterNewSuccess() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val client = ApiTasksClient({ "https://tasks.example.test" }, { "tenant" }, { "fixture" },
            ApiHttpTransport { _, _, _, _ ->
                if (calls.incrementAndGet() == 1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    500 to "{}"
                } else 200 to """{"data":[{"uid":"fresh"}]}"""
            })
        val c = TasksController(client, History())
        val stale = async(start = CoroutineStart.UNDISPATCHED) { c.loadTasks() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            c.loadTasks()
        } finally { release.countDown() }
        stale.await()
        assertNull(c.lastLoadError)
        assertEquals(listOf("fresh"), c.tasks.map { it.id })
    }
    @Test fun staleSuccessCannotClearNewFailure() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val client = ApiTasksClient({ "https://tasks.example.test" }, { "tenant" }, { "fixture" },
            ApiHttpTransport { _, _, _, _ ->
                if (calls.incrementAndGet() == 1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    200 to """{"data":[]}"""
                } else 500 to "{}"
            })
        val c = TasksController(client, History())
        val stale = async(start = CoroutineStart.UNDISPATCHED) { c.loadTasks() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            c.loadTasks()
            assertTrue(c.lastLoadError != null)
        } finally { release.countDown() }
        stale.await()
        assertTrue(c.lastLoadError != null)
    }
    @Test fun priorOwnerErrorIsNotExposedAfterSwitch() = runBlocking {
        var owner = "first"
        val client = ApiTasksClient({ "https://tasks.example.test" }, { "tenant" }, { "fixture" },
            ApiHttpTransport { _, _, _, _ -> 500 to "{}" })
        val c = TasksController(client, History(), accountKey = { owner })
        c.loadTasks()
        assertTrue(c.lastLoadError != null)
        owner = "second"
        assertNull(c.lastLoadError)
    }
}
