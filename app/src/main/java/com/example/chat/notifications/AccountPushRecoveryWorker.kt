package com.example.chat.notifications

import android.content.Context
import androidx.work.*
import com.example.chat.ServiceLocator
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Durable, bounded continuation for a received content-free push. No bearer/content in WorkManager. */
class AccountPushRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val binding = inputData.getString(BINDING) ?: return@withContext Result.failure()
        val callId = inputData.getString(CALL)
        val until = inputData.getLong(DEADLINE, 0L)
        if (!validBinding(binding) || (callId != null && (callId.isBlank() || callId.length > 256)) || System.currentTimeMillis() >= until) return@withContext Result.success()
        try {
            val complete = withTimeoutOrNull(45_000L) {
                ServiceLocator.ensurePushReady(applicationContext)
                ServiceLocator.apiBackend.initialize()
                ServiceLocator.recoverAccountPush(binding, callId)
            } == true
            if (complete || runAttemptCount >= 4 || System.currentTimeMillis() >= until) Result.success() else Result.retry()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (runAttemptCount < 4 && System.currentTimeMillis() < until) Result.retry() else Result.success()
        }
    }
    companion object {
        private const val BINDING = "binding_id"
        private const val CALL = "call_id"
        private const val DEADLINE = "deadline_ms"
        internal fun validBinding(value: String): Boolean = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
        /** Persist before networking. A killed FCM callback cannot lose the accepted work. */
        fun enqueue(context: Context, binding: String, callId: String?, urgent: Boolean) {
            if (!validBinding(binding) || (callId != null && (callId.isBlank() || callId.length > 256))) return
            val deadline = System.currentTimeMillis() + if (callId == null) 15 * 60_000L else 60_000L
            val builder = OneTimeWorkRequestBuilder<AccountPushRecoveryWorker>()
                .setInputData(workDataOf(BINDING to binding, CALL to callId, DEADLINE to deadline))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            // Before Android 12 expedited work needs a foreground-service notification. Keep those versions on bounded normal work.
            if (urgent && android.os.Build.VERSION.SDK_INT >= 31) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            // Append instead of KEEP: a nudge arriving during a running sweep needs its own later check.
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "account_push_${binding}_${if (callId == null) "sync" else "call_$callId"}", ExistingWorkPolicy.APPEND_OR_REPLACE, builder.build(),
            ).result.get()
        }
    }
}
