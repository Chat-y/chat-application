package com.example.chat.data.api

import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.TaskStatus
import org.json.JSONArray
import org.json.JSONObject

/** REST tasks plane of the api branch's ApiBackendService
 *  (lib/data/services/api_backend_service.dart lines 1798-1945): loadTasks,
 *  createTask, updateTaskStatus, updateTask, deleteTask against
 *  `{restApiBaseUrl}/tasks`. Exact URL, header and status handling parity:
 *  unlike the chat plane, the Dart tasks paths have NO /api/v1 404 fallback
 *  and no 402 subscription handling. Context-free so it is JVM-testable;
 *  state ownership lives in the caller (TasksController), exactly like Dart
 *  where ApiBackendService owns _tasks. */
class ApiTasksClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: () -> String?,
    private val transport: ApiHttpTransport,
    private val scopeSnapshot: (() -> ApiAccountScope)? = null,
) {

    private data class RequestScope(val base: String, val tenant: String, val token: String?, val owner: ApiAccountScope?)
    private fun singleAssignee(ids: List<String>): Any {
        val unique = ids.distinct()
        require(unique.size <= 1 && unique.none { it.isBlank() }) { "Choose one assignee for this task." }
        return unique.firstOrNull() ?: JSONObject.NULL
    }
    private fun capture(expected: ApiAccountScope?): RequestScope {
        val current = scopeSnapshot?.invoke()
        if (expected != null && current != null && current !== expected) throw ApiScopeChangedException()
        val owner = expected ?: current
        // Prior separate ambient base()/authHeaders() reads could mix accounts mid-request.
        return if (owner != null) RequestScope(owner.baseUrl.trimEnd('/'), owner.tenantId, owner.token, owner)
        else RequestScope(baseUrl().trimEnd('/'), tenantId(), token(), null)
    }
    private fun request(scope: RequestScope, method: String, path: String, body: String? = null): Pair<Int, String> {
        fun requireCurrent() {
            if (scope.owner != null && scopeSnapshot != null && scopeSnapshot.invoke() !== scope.owner)
                throw ApiScopeChangedException()
        }
        requireCurrent()
        check(!scope.token.isNullOrBlank()) { "Sign in before changing tasks." }
        val headers = mutableMapOf("Authorization" to "Bearer ${scope.token}", "x-tenant-id" to scope.tenant)
        if (body != null) headers["Content-Type"] = "application/json"
        val response = transport.execute(method, "${scope.base}/$path", headers, body)
        requireCurrent()
        return response
    }

    /** loadTasks: GET /tasks -> ChatTask.fromApi list, or null on failure
     *  (Dart catches and keeps prior state; null tells the caller to keep
     *  what it has). Returns null without a token, mirroring Dart's early
     *  return. */
    fun listTasks(expectedScope: ApiAccountScope? = null): List<ChatTask>? {
        return try {
            val captured = capture(expectedScope)
            if (captured.token.isNullOrBlank()) return null
            val response = request(captured, "GET", "tasks")
            if (response.first != 200) return null
            val data = JSONObject(response.second).getJSONArray("data")
            val tasks = mutableListOf<ChatTask>()
            for (i in 0 until data.length()) {
                tasks.add(ChatTask.fromApi(ApiChatClient.deepMap(data.getJSONObject(i))))
            }
            tasks
        } catch (_: Exception) {
            null
        }
    }

    /** createTask: POST /tasks {title, description, priority, due_date,
     *  assigned_to_ids} -> created task on 200. Throws on non-200, matching
     *  Dart's `throw Exception('Failed to create task')`; the modal surfaces
     *  the message after stripping an 'Exception: ' prefix. */
    fun createTask(
        title: String,
        description: String?,
        assigneeIds: List<String>,
        priority: String,
        dueAtIso: String?,
        expectedScope: ApiAccountScope? = null,
    ): ChatTask {
        val captured = capture(expectedScope)
        val payload = JSONObject()
            .put("title", title)
            .put("description", description)
            .put("priority", priority)
            // Prior legacy contract discarded dueAtIso (no due_date field).
            // New contract forwards the caller's due date; server acceptance is unverified.
            .put("due_date", dueAtIso ?: JSONObject.NULL)
            // Prior firstOrNull() silently discarded additional selected assignees.
            .put("assigned_to_id", singleAssignee(assigneeIds))
            .toString()
        val response = request(captured, "POST", "tasks", payload)
        if (response.first == 200) {
            val data = JSONObject(response.second).getJSONObject("data")
            return ChatTask.fromApi(ApiChatClient.deepMap(data))
        }
        throw Exception("Failed to create task")
    }

    /** updateTaskStatus: PUT /tasks/<id> {status}. Dart maps the enum to the
     *  server string ('todo' for inbox) and only replaces local state on 200. */
    fun updateTaskStatus(taskId: String, status: TaskStatus, expectedScope: ApiAccountScope? = null): ChatTask? {
        val taskPath = ApiPathSegments.safe(taskId) ?: return null
        val statusStr = when (status) {
            TaskStatus.inbox -> "todo"
            TaskStatus.assigned -> "assigned"
            TaskStatus.inProgress -> "in_progress"
            TaskStatus.blocked -> "blocked"
            TaskStatus.completed -> "completed"
            TaskStatus.archived -> "archived"
        }
        return try {
            val captured = capture(expectedScope)
            val payload = JSONObject().put("status", statusStr).toString()
            val response = request(captured, "PUT", "tasks/$taskPath", payload)
            if (response.first != 200) return null
            ChatTask.fromApi(ApiChatClient.deepMap(JSONObject(response.second).getJSONObject("data")))
        } catch (_: Exception) {
            null
        }
    }

    /** updateTask: PUT /tasks/<id> {title, description, priority, due_date,
     *  assigned_to_ids}; returns the updated task on 200, null otherwise. */
    fun updateTask(
        taskId: String,
        title: String,
        description: String,
        assigneeIds: List<String>,
        priority: String,
        dueAtIso: String?,
        expectedScope: ApiAccountScope? = null,
    ): ChatTask? {
        val taskPath = ApiPathSegments.safe(taskId) ?: return null
        return try {
            val captured = capture(expectedScope)
            val payload = JSONObject()
                .put("title", title)
                .put("description", description)
                .put("priority", priority)
                // Prior legacy contract discarded dueAtIso (no due_date field).
                .put("due_date", dueAtIso ?: JSONObject.NULL)
                // Prior firstOrNull() silently discarded additional selected assignees.
            .put("assigned_to_id", singleAssignee(assigneeIds))
                .toString()
            val response = request(captured, "PUT", "tasks/$taskPath", payload)
            if (response.first != 200) return null
            ChatTask.fromApi(ApiChatClient.deepMap(JSONObject(response.second).getJSONObject("data")))
        } catch (_: Exception) {
            null
        }
    }

    /** deleteTask: DELETE /tasks/<id>; Dart only removes local state on 200. */
    fun deleteTask(taskId: String, expectedScope: ApiAccountScope? = null): Boolean {
        val taskPath = ApiPathSegments.safe(taskId) ?: return false
        return try {
            val captured = capture(expectedScope)
            val response = request(captured, "DELETE", "tasks/$taskPath")
            response.first == 200
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /** Dart updateTaskStatus's reverse map for callers that need the
         *  wire value (inbox -> 'todo'). Kept beside the client so the UI
         *  never re-derives it. */
        fun taskStatusToWire(status: TaskStatus): String = when (status) {
            TaskStatus.inbox -> "todo"
            TaskStatus.assigned -> "assigned"
            TaskStatus.inProgress -> "in_progress"
            TaskStatus.blocked -> "blocked"
            TaskStatus.completed -> "completed"
            TaskStatus.archived -> "archived"
        }
    }
}
