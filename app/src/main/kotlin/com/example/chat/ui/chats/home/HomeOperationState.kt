package com.example.chat.ui.chats.home

import com.example.chat.data.api.ApiChatClient
import com.example.chat.data.api.ApiScopeChangedException
import com.example.chat.data.api.AuthRateLimitException
import com.example.chat.data.auth.SupabaseAuthClient
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException

/** Empty [chatId] is reserved for account-wide operations, never the selected chat. */
data class HomeOpKey(val action: String, val chatId: String)

/**
 * Transient request state, not a persisted mutation queue or a server-sync indicator.
 * Keep the entire map in the captured account session (for example, remember(owner)).
 * Request ids must increase within that session, including after pruning a target.
 * Terminal states retain their token so an old Start cannot resurrect completed work.
 */
sealed interface HomeOpState {
    val requestId: Long

    data class Pending(
        override val requestId: Long,
        val desiredValue: Boolean? = null,
    ) : HomeOpState

    /** The adapter returned. This does not, by itself, mean the server persisted a write. */
    data class Succeeded(override val requestId: Long) : HomeOpState

    data class Failed(
        override val requestId: Long,
        val kind: HomeFailureKind,
        val retryIntent: HomeRetryIntent? = null,
    ) : HomeOpState

    data class Cancelled(override val requestId: Long) : HomeOpState
}

sealed interface HomeOpEvent {
    data class Start(
        val key: HomeOpKey,
        val requestId: Long,
        val desiredValue: Boolean? = null,
    ) : HomeOpEvent

    data class Succeed(val key: HomeOpKey, val requestId: Long) : HomeOpEvent

    data class Fail(
        val key: HomeOpKey,
        val requestId: Long,
        val kind: HomeFailureKind,
        val retryIntent: HomeRetryIntent? = null,
    ) : HomeOpEvent

    data class Cancel(val key: HomeOpKey, val requestId: Long) : HomeOpEvent
    data class Prune(val validChatIds: Set<String>) : HomeOpEvent
    object Reset : HomeOpEvent
}

/**
 * Repeated taps for a pending key are ignored; unrelated keys can run independently.
 * The dispatcher must launch work ONLY if its Start was accepted. Capture a toggle's
 * desired Boolean before launch, rather than toggling a possibly stale summary again.
 * Serialize dispatch on the UI thread or under the host's lock, not concurrent read/copy/write.
 *
 * Completions must also pass the host's account-generation check. Resetting this map
 * is not a replacement for cancelling old jobs and guarding their feedback/navigation.
 * No automatic retries are scheduled here: one dispatch means one adapter invocation.
 * Repository-managed backoff remains the repository's responsibility.
 */
fun reduce(
    state: Map<HomeOpKey, HomeOpState>,
    event: HomeOpEvent,
): Map<HomeOpKey, HomeOpState> = when (event) {
    is HomeOpEvent.Start -> {
        val previous = state[event.key]
        if (previous is HomeOpState.Pending ||
            (previous != null && event.requestId <= previous.requestId)
        ) state else state + (event.key to HomeOpState.Pending(event.requestId, event.desiredValue))
    }
    is HomeOpEvent.Succeed -> {
        val pending = state[event.key] as? HomeOpState.Pending
        if (pending?.requestId != event.requestId) state
        else state + (event.key to HomeOpState.Succeeded(event.requestId))
    }
    is HomeOpEvent.Fail -> {
        val pending = state[event.key] as? HomeOpState.Pending
        if (pending == null || pending.requestId != event.requestId) state
        else {
            val retry = event.retryIntent?.takeIf {
                it.key == event.key && it.desiredValue == pending.desiredValue
            }
            val result: HomeOpState = if (event.kind == HomeFailureKind.Cancelled) {
                HomeOpState.Cancelled(event.requestId)
            } else {
                HomeOpState.Failed(event.requestId, event.kind, retry)
            }
            state + (event.key to result)
        }
    }
    is HomeOpEvent.Cancel -> {
        val pending = state[event.key] as? HomeOpState.Pending
        if (pending?.requestId != event.requestId) state
        else state + (event.key to HomeOpState.Cancelled(event.requestId))
    }
    is HomeOpEvent.Prune -> state.filterKeys { it.chatId.isEmpty() || it.chatId in event.validChatIds }
    HomeOpEvent.Reset -> emptyMap()
}

/**
 * An in-memory snapshot of the ORIGINAL action, not a closure over current selection.
 * Store only parameters needed to revalidate the action; never persist or log this object.
 * The account identity is opaque and compared by reference, just like authSnapshot().
 * The dispatcher must recheck authorization/protection and current capabilities too.
 * Destructive/ambiguous server outcomes should omit a retry and require refresh instead.
 */
class HomeRetryIntent(
    val key: HomeOpKey,
    val accountSessionIdentity: Any,
    val desiredValue: Boolean? = null,
    parameters: Map<String, String> = emptyMap(),
) {
    val parameters: Map<String, String> = parameters.toMap()

    fun isValidFor(accountSessionIdentity: Any, validChatIds: Set<String>): Boolean =
        this.accountSessionIdentity === accountSessionIdentity &&
            (key.chatId.isEmpty() || key.chatId in validChatIds)

    override fun toString(): String = "HomeRetryIntent(redacted)"
}

/**
 * Lookup lifecycle seam. Loading disables repeated submits. Closing the dialog must
 * cancel its Job AND invalidate its token (return to Idle); both success and failure
 * must match the captured token and account before feedback, creation or navigation.
 * Cancellation cannot undo a get-or-create already accepted by the server: check
 * ownership immediately before invoking it, then check again after it returns.
 * The host owns these effects; these values alone do not cancel a request.
 */
sealed interface HomeLookupState {
    object Idle : HomeLookupState
    data class Loading(val requestId: Long) : HomeLookupState
    data class Resolved(val requestId: Long, val recipientId: String) : HomeLookupState
    data class Failed(val requestId: Long, val kind: HomeFailureKind) : HomeLookupState
}

enum class HomeFailureKind {
    Offline,
    ServerRejected,
    Cancelled,
    Unknown;

    /** Safe, content-free copy. Never render exception.message in Home feedback. */
    fun feedback(): String = when (this) {
        Offline -> "Could not reach the server. Check your connection and try again."
        ServerRejected -> "The server did not accept this action. Check your access or plan before trying again."
        Cancelled -> ""
        Unknown -> "Could not confirm this action. Refresh before trying again."
    }
}

/**
 * Classify types only, never HTTP-looking strings or raw server text. An IOException
 * can also mean storage failure or an unknown outcome, so it is not generally Offline.
 * A timeout can follow a committed write, so it remains Unknown rather than inviting
 * an unsafe retry. Some existing adapters erase typed causes; those remain Unknown.
 * Cancellation wins even when wrapped, and is always rethrown, never shown as failure.
 */
fun classifyFailure(t: Throwable): HomeFailureKind {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val causes = mutableListOf<Throwable>()
    var cause: Throwable? = t
    while (cause != null && seen.add(cause)) {
        causes.add(cause)
        cause = cause.cause
    }
    causes.filterIsInstance<CancellationException>().firstOrNull()?.let { throw it }
    if (causes.any { it is ApiScopeChangedException }) return HomeFailureKind.Cancelled
    if (causes.any {
        it is ApiChatClient.SubscriptionRequiredException || it is AuthRateLimitException ||
            (it is SupabaseAuthClient.AuthException && it.statusCode?.let { code -> code in 400..599 } == true)
    }) return HomeFailureKind.ServerRejected
    if (causes.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException }) {
        return HomeFailureKind.Offline
    }
    return HomeFailureKind.Unknown
}

/**
 * Source-only authority notes for the Home adapters at the pinned baseline + Home patch:
 *
 * Action             | What currently drives the UI                 | Not promised
 * pin/mute/archive   | ApiBackendService local state/cache           | server sync or durable-write acknowledgment
 * read               | ConversationsRepository RPC + reload, or      | universal server acknowledgment (receipt policy varies)
 *                    | local count when receipts are suppressed      |
 * unread             | repository local count, best-effort RPC        | server acknowledgment (RPC failure is swallowed)
 * mark all read      | local counters + best-effort RPC/reload        | confirmation of every server write
 * folder membership  | account-keyed ChatFolderStore cache/preferences| durable commit or server sync
 *
 * A Succeeded request means only that the adapter returned under its existing contract.
 * In particular, do not label unread/mark-all as server-confirmed. Folder writes use
 * SharedPreferences.apply(), not a durable commit acknowledgment. No action is granted
 * an offline queue by this model, and Pending means in-flight, never pending-local-sync.
 * Host integrations must expire queued feedback on owner change/target removal, using
 * Prune/Reset for this map and equivalent checks for any separate snackbar queue.
 */
internal object HomeOperationContract {}
