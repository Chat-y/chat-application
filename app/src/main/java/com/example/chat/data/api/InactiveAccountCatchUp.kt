package com.example.chat.data.api

/** Content-free catch-up. Never adopts credentials, polls call queues, marks
 * messages read, or writes another account's message/preferences stores. */
class InactiveAccountCatchUp(
    private val targets: () -> List<Target>,
    private val isCurrent: (Target) -> Boolean,
    private val fetch: (Target) -> Summary?,
    private val previous: (String) -> String?,
    private val remember: (String, String) -> Unit,
    private val alert: (Target) -> Boolean,
    private val clear: (Target) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = { System.nanoTime() / 1_000_000 },
    private val publish: (Target, Summary) -> Unit = { _, _ -> },
    private val budgetMillis: Long = 3_000,

) {
    // Not a data class: never generate a toString containing bearer credentials.
    class Target(val key: String, val hash: String, val base: String, val tenant: String,
        val token: String, val expiresAt: Long?, val label: String, val userId: String)
    data class Summary(val unread: Int, val latest: Long, val fingerprint: String) {
        fun encoded(): String = "$unread:$latest:$fingerprint"
        companion object {
            fun decode(value: String?): Summary? {
                val parts = value?.split(":", limit = 3) ?: return null
                if (parts.size != 3) return null
                return Summary(parts[0].toIntOrNull() ?: return null, parts[1].toLongOrNull() ?: return null, parts[2])
            }
        }
    }
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)

    // Previous implementation retained: fun run() { if (!running.compareAndSet(false, true)) return
    fun run(): Boolean {
        if (!running.compareAndSet(false, true)) return false
        var complete = true
        val started = elapsed()
        try {
            targets().distinctBy { it.key }.take(3).forEach { target ->
                if (elapsed() - started >= budgetMillis) { complete = false; return@forEach }
                // Previous stale-target skip: if (!isCurrent(target)) return@forEach
                // A concurrent switch/rotation is not proof that this hint was handled.
                if (!isCurrent(target)) { complete = false; return@forEach }
                if (target.token.isBlank() || target.expiresAt?.let { it <= now() + 5_000 } == true) { complete = false; return@forEach }
                try {
                    val summary = fetch(target) ?: run { complete = false; return@forEach }
                    // A concurrent switch/rotation is not proof that this hint was handled.
                    if (!isCurrent(target)) { complete = false; return@forEach }
                    // Badge publication does not depend on OS notification permission or alert dedupe.
                    publish(target, summary)
                    // A concurrent switch/rotation is not proof that this hint was handled.
                    if (!isCurrent(target)) { complete = false; return@forEach }
                    val old = Summary.decode(previous(target.hash))
                    val changed = old == null || (old.fingerprint != summary.fingerprint &&
                        (summary.unread > old.unread || summary.latest > old.latest))
                    if (summary.unread <= 0) {
                        clear(target)
                        remember(target.hash, summary.encoded())
                    } else if (!changed || alert(target)) {
                        if (isCurrent(target)) remember(target.hash, summary.encoded())
                    }
                    if (!isCurrent(target)) complete = false
                } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
                catch (_: Exception) { complete = false /* No credentials, room content or account details in logs. */ }
            }
        } finally { running.set(false) }
        return complete
    }
}

// Replaced source retained for review (inactive; do not restore without its surrounding implementation).
// Prior source:     fun run() {
// Prior source:         if (!running.compareAndSet(false, true)) return
// Prior source:                 if (elapsed() - started >= 3_000) return@forEach
// Prior source:                 if (target.token.isBlank() || target.expiresAt?.let { it <= now() + 5_000 } == true || !isCurrent(target)) return@forEach
// Prior source:                     val summary = fetch(target) ?: return@forEach
// Prior source:                 catch (_: Exception) { /* No credentials, room content or account details in logs. */ }
