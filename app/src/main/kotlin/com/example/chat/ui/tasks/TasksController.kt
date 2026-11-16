package com.example.chat.ui.tasks

import com.example.chat.data.api.ApiTasksClient
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.TaskStatus
import com.example.chat.domain.models.TaskTransitionError
import com.example.chat.domain.models.TaskWorkflow
import com.example.chat.domain.models.Transition
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONArray
import org.json.JSONObject

/** Persistent store seam for the task stage history ("task tree") the
 *  tasks_screen.dart keeps under SharedPreferences key chaty_task_history_v1.
 *  The Android SharedPreferences implementation lives with the UI wiring;
 *  JVM tests use an in-memory map. */
interface TaskHistoryStore {
    fun load(): Map<String, List<String>>
    fun save(history: Map<String, List<String>>)
}

/** Pure JSON codec for chaty_task_history_v1 so storage implementations stay
 *  one-liners. Shape: {taskId: ["From → To • d/M HH:mm", ...]}. */
object TaskHistoryCodec {
    fun decode(raw: String?): Map<String, List<String>> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = LinkedHashMap<String, List<String>>()
            for (key in obj.keys()) {
                val arr = obj.getJSONArray(key)
                out[key] = (0 until arr.length()).map { arr.get(it).toString() }
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encode(history: Map<String, List<String>>): String {
        val obj = JSONObject()
        for ((key, value) in history) obj.put(key, JSONArray(value))
        return obj.toString()
    }
}

/** Tasks-plane state holder. Exact port of the ApiBackendService tasks slice
 *  (loadTasks/createTask/updateTaskStatus/updateTask/deleteTask local-state
 *  semantics) plus the tasks_screen.dart history recording (_recordMove with
 *  _historyCapPerTask = 30). Listener fan-out mirrors ApiBackendService's
 *  addListener/notifyListeners so Compose screens recompose on mutation. */
class TasksController(
    private val client: ApiTasksClient,
    private val historyStore: TaskHistoryStore,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
    /** Current signed-in account identity; load snapshots are only valid for
     *  the account they were fetched under. */
    private val accountKey: () -> String? = { null },
    /** Immutable owner token; production supplies auth scope (tenant, route and auth generation).
     * Default preserves account-key-only clients/tests. Never log this token. */
    private val ownerSnapshot: () -> Any? = accountKey,
) {
    private val lock = Any()
    private val _tasks = mutableListOf<ChatTask>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    // TAS-008: a snapshot loaded for another account is never exposed (conservative: empty list).
    // val tasks: List<ChatTask> get() = synchronized(lock) { _tasks.toList() }
    val tasks: List<ChatTask> get() = synchronized(lock) {
        // Previous key-only check missed same-user tenant/auth changes.
        if (hasSnapshot && (loadedAccountKey != accountKey() || loadedOwner != ownerSnapshot())) emptyList() else _tasks.toList()
    }

    /** Account the current snapshot was successfully loaded for; a failed
     *  load keeps the prior snapshot AND its account marker. */
    @Volatile private var loadedAccountKey: String? = null
    private var hasSnapshot = false
    private var hasCompletedLoad = false
    private var loadedOwner: Any? = null
    private data class OperationOwner(val key: String?, val token: Any?)
    private fun captureOwner() = OperationOwner(accountKey(), ownerSnapshot())
    private fun isCurrent(owner: OperationOwner) = accountKey() == owner.key && ownerSnapshot() == owner.token
    private fun requireCurrent(owner: OperationOwner) {
        check(isCurrent(owner)) { "Account changed. Reload tasks." }
    }
    private fun mutationOwner(): OperationOwner = synchronized(lock) {
        captureOwner().also { owner ->
            requireCurrent(owner)
            check(!hasSnapshot || (loadedAccountKey == owner.key && loadedOwner == owner.token)) {
                "Account changed. Reload tasks."
            }
        }
    }
    private var loadGeneration = 0L

    /** True only when a completed (200) load for the CURRENT account exists.
     *  False after account switch until the next successful load, so stale
     *  prior-account tasks can neither resolve nor dismiss restored UI state. */
    // Previous unsynchronized key-only read could accept a prior auth/tenant snapshot.
    val hasLoadedOnce: Boolean get() = synchronized(lock) {
        hasCompletedLoad && loadedAccountKey == accountKey() && loadedOwner == ownerSnapshot()
    }

    fun addListener(listener: () -> Unit) = listeners.add(listener)
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() = listeners.forEach { it() }

    /** Dart loadTasks: replace on 200, keep prior state on failure. */
    suspend fun loadTasks() {
        // TAS-007: bind the load to the account that started it.
        // Previous load captured only accountKey().
        val startedFor = captureOwner()
        // TAS-010: a newer load invalidates older in-flight ones.
        val generation = synchronized(lock) { ++loadGeneration }
        val fetched = client.listTasks(startedFor.token as? com.example.chat.data.api.ApiAccountScope) ?: return
        synchronized(lock) {
            // Previous guard compared only accountKey().
            if (generation != loadGeneration || !isCurrent(startedFor)) return
            _tasks.clear()
            _tasks.addAll(fetched)
            loadedAccountKey = startedFor.key
            loadedOwner = startedFor.token
            hasSnapshot = true
            hasCompletedLoad = true
        }
        notifyListeners()
    }

    /** Dart createTask: inserts the server task at index 0 on success;
     *  propagates the client's exception on failure. */
    suspend fun createTask(
        title: String,
        description: String?,
        assigneeIds: List<String>,
        priority: String,
        dueAtIso: String?,
    ): ChatTask {
        val owner = mutationOwner()
        val task = client.createTask(title, description, assigneeIds, priority, dueAtIso, owner.token as? com.example.chat.data.api.ApiAccountScope)
        // Previous completion unconditionally inserted into the current cache.
        synchronized(lock) {
            requireCurrent(owner)
            _tasks.add(0, task)
            // A create marks cache ownership, not a successful complete list load.
            loadedAccountKey = owner.key
            loadedOwner = owner.token
            hasSnapshot = true
        }
        notifyListeners()
        return task
    }

    /** Dart updateTaskStatus: replace at index on 200. Returns false when the
     *  server did not confirm (TAS-001), so callers can report it. */
    suspend fun updateTaskStatus(taskId: String, status: TaskStatus): Boolean {
        val owner = mutationOwner()
        val updated = client.updateTaskStatus(taskId, status, owner.token as? com.example.chat.data.api.ApiAccountScope) ?: return false
        synchronized(lock) {
            // Previously applied the response even after an account switch.
            requireCurrent(owner)
            val index = _tasks.indexOfFirst { it.id == taskId }
            if (index != -1) _tasks[index] = updated
        }
        notifyListeners()
        return true
    }

    /** Dart updateTask (full edit): replace at index on 200. */
    suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
        assigneeIds: List<String>,
        priority: String,
        dueAtIso: String?,
    ): Boolean {
        val owner = mutationOwner()
        val updated = client.updateTask(taskId, title, description, assigneeIds, priority, dueAtIso, owner.token as? com.example.chat.data.api.ApiAccountScope)
            ?: return false
        synchronized(lock) {
            // Previously applied the response even after an account switch.
            requireCurrent(owner)
            val index = _tasks.indexOfFirst { it.id == taskId }
            if (index != -1) _tasks[index] = updated
        }
        notifyListeners()
        return true
    }

    /** Dart deleteTask: remove locally only on 200. */
    suspend fun deleteTask(taskId: String): Boolean {
        val owner = mutationOwner()
        if (!client.deleteTask(taskId, owner.token as? com.example.chat.data.api.ApiAccountScope)) return false
        // Previous completion removed from whichever cache was current.
        synchronized(lock) { requireCurrent(owner); _tasks.removeAll { it.id == taskId } }
        notifyListeners()
        return true
    }

    /** The SINGLE transition use-case from tasks_screen.dart _moveTask:
     *  workflow validation -> backend persistence -> history recording.
     *  The UI maps the result to the exact Dart snackbars. */
    suspend fun moveTask(task: ChatTask, target: TaskStatus): MoveResult {
        val move: Transition?
        try {
            move = TaskWorkflow.validate(task.status, target)
        } catch (error: TaskTransitionError) {
            return MoveResult.Invalid(error.reason)
        }
        if (move == null) return MoveResult.NoOp
        return try {
            val owner = mutationOwner()
            // TAS-001: record history only when the server confirmed the move.
            if (!updateTaskStatus(task.id, target)) {
                return MoveResult.Failed(
                    "Could not move \"${task.title}\" to ${TaskWorkflow.label(target)}: the server did not confirm the change.",
                )
            }
            requireCurrent(owner)
            recordMoveOwned(task, move.from, move.to, owner)
            requireCurrent(owner)
            MoveResult.Moved
        // Previous broad catch converted coroutine cancellation to a failure result.
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            MoveResult.Failed(
                "Could not move \"${task.title}\" to ${TaskWorkflow.label(target)}: $error",
            )
        }
    }

    /** Dart _recordMove: prepend 'From → To • d/M HH:mm', cap 30 per task. */
    suspend fun recordMove(task: ChatTask, from: TaskStatus, to: TaskStatus) {
        recordMoveOwned(task, from, to, mutationOwner())
    }

    private fun recordMoveOwned(task: ChatTask, from: TaskStatus, to: TaskStatus, owner: OperationOwner) {
        requireCurrent(owner)
        val history = historyStore.load().toMutableMap()
        val entries = history.getOrPut(task.id) { emptyList() }.toMutableList()
        val stampTime = now()
        val stamp = "${stampTime.dayOfMonth}/${stampTime.monthValue} " +
            "${stampTime.hour.toString().padStart(2, '0')}:" +
            stampTime.minute.toString().padStart(2, '0')
        entries.add(0, "${TaskWorkflow.label(from)} → ${TaskWorkflow.label(to)} • $stamp")
        while (entries.size > HISTORY_CAP_PER_TASK) entries.removeAt(entries.size - 1)
        history[task.id] = entries
        try {
            // Previous history write had no owner recheck after storage load.
            synchronized(lock) { requireCurrent(owner); historyStore.save(history) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            // Preserve nonfatal history-storage behavior, but never hide account retirement.
            requireCurrent(owner)
        }
    }

    /** Dart _loadHistory + _showHistory read path. */
    fun historyFor(taskId: String): List<String> = historyStore.load()[taskId] ?: emptyList()

    sealed interface MoveResult {
        data object Moved : MoveResult
        data object NoOp : MoveResult
        data class Invalid(val reason: String) : MoveResult
        data class Failed(val message: String) : MoveResult
    }

    companion object {
        const val HISTORY_PREFS_KEY = "chaty_task_history_v1"
        const val HISTORY_CAP_PER_TASK = 30
    }
}

// Superseded lines retained inactive for source review:
//         if (hasSnapshot && loadedAccountKey != accountKey()) emptyList() else _tasks.toList()
//     val hasLoadedOnce: Boolean get() = hasSnapshot && loadedAccountKey == accountKey()
//         val startedFor = accountKey()
//             if (generation != loadGeneration || accountKey() != startedFor) return
//             loadedAccountKey = startedFor
//         synchronized(lock) { _tasks.add(0, task) }
//         synchronized(lock) { _tasks.removeAll { it.id == taskId } }
//             recordMove(task, move.from, move.to)
//         } catch (error: Exception) {
//             historyStore.save(history)
//         } catch (_: Exception) {
