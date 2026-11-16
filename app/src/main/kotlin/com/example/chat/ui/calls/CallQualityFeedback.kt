package com.example.chat.ui.calls

import com.example.chat.data.api.ApiAccountScope
import com.example.chat.data.services.CallSignalingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Opt-in, device-local aggregate only. No call/contact IDs, notes or network uploads are stored. */
class CallQualityFeedback(
    private val storage: CallPresentationPrefsStorage,
    private val calls: CallSignalingService,
    private val scope: CoroutineScope,
    private val account: () -> ApiAccountScope,
    observeAccount: ((ApiAccountScope) -> Unit) -> Unit,
) {
    companion object {
        private const val ENABLED = "chaty.calls.quality_feedback.enabled.v1"
        private const val COUNTS = "chaty.calls.quality_feedback.counts.v1"
    }
    data class ViewState(val ready: Boolean = false, val enabled: Boolean = false,
        val completed: Long = 0, val good: Long = 0, val poor: Long = 0, val error: Boolean = false)
    class Prompt internal constructor(internal val owner: ApiAccountScope)
    private val mutable = MutableStateFlow(ViewState())
    val state: StateFlow<ViewState> = mutable
    private val mutablePrompt = MutableStateFlow<Prompt?>(null)
    val prompt: StateFlow<Prompt?> = mutablePrompt
    private val mutex = Mutex()
    private val seen = LinkedHashSet<String>()
    private val initialized = kotlinx.coroutines.CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                val restored = withContext(Dispatchers.IO) {
                    val enabled = storage.getBool(ENABLED) ?: false
                    val raw = storage.getString(COUNTS)
                    val counts = raw?.split(',')?.map { it.toLongOrNull() }
                    val valid = counts != null && counts.size == 3 && counts.all { it != null && it >= 0 }
                    check(raw == null || valid) { "Feedback counters could not be restored." }
                    ViewState(ready = true, enabled = enabled,
                        completed = if (valid) counts!![0]!! else 0,
                        good = if (valid) counts!![1]!! else 0,
                        poor = if (valid) counts!![2]!! else 0)
                }
                mutable.value = restored
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = ViewState(ready = true, error = true) }
            finally { initialized.complete(Unit) }
        }
        observeAccount {
            mutablePrompt.value = null
            synchronized(seen) { seen.clear() }
        }
        calls.addStateListener {
            val owner = account()
            val receiptId = calls.completedCallIdFor(owner)
            val enabledAtReceipt = mutable.value.let { !it.ready || (it.enabled && !it.error) }
            if (receiptId != null && enabledAtReceipt &&
                !owner.userId.isNullOrBlank() && !owner.token.isNullOrBlank()) {
                val fresh = synchronized(seen) {
                    if (!seen.add(receiptId)) false else {
                        while (seen.size > 512) seen.remove(seen.first())
                        true
                    }
                }
                if (fresh) scope.launch { count(owner) }
            }
        }
    }
    private suspend fun save(next: ViewState) {
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
            check(storage.putString(COUNTS, "${next.completed},${next.good},${next.poor}"))
            mutable.value = next
        }
    }
    private suspend fun count(owner: ApiAccountScope) {
        initialized.await()
        mutex.withLock {
            val current = mutable.value
            if (account() !== owner || !current.enabled || current.error || current.completed == Long.MAX_VALUE) return
            try {
                val next = current.copy(completed = current.completed + 1)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                save(next)
                if (account() === owner && next.completed % 20L == 0L) mutablePrompt.value = Prompt(owner)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = current.copy(error = true); mutablePrompt.value = null }
        }
    }
    suspend fun setEnabled(enabled: Boolean, expectedOwner: ApiAccountScope = account()): Result<Unit> = try {
        initialized.await()
        mutex.withLock {
            check(account() === expectedOwner) { "The call account changed." }
            check(!mutable.value.error) { "Call feedback storage is unavailable." }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                check(storage.putBool(ENABLED, enabled))
                mutable.value = mutable.value.copy(enabled = enabled)
                if (!enabled) mutablePrompt.value = null
            }
        }
        Result.success(Unit)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (failure: Exception) { Result.failure(failure) }

    suspend fun rate(expected: Prompt, good: Boolean): Result<Unit> = try {
        initialized.await()
        mutex.withLock {
            check(mutablePrompt.value === expected && account() === expected.owner) { "The call account changed." }
            val current = mutable.value
            check(current.enabled && !current.error)
            check(current.good < Long.MAX_VALUE && current.poor < Long.MAX_VALUE)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            save(current.copy(good = current.good + if (good) 1 else 0,
                poor = current.poor + if (good) 0 else 1))
            if (mutablePrompt.value === expected) mutablePrompt.value = null
        }
        Result.success(Unit)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (failure: Exception) { Result.failure(failure) }

    fun skip(expected: Prompt) { if (mutablePrompt.value === expected) mutablePrompt.value = null }
}
