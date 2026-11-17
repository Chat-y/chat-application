package com.example.chat.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.chat.ServiceLocator
import com.example.chat.data.demo.DemoMode
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Recovers registration after an accepted token/login wake. Never stores a token in work data. */
class PushRegistrationRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val deadline = inputData.getLong(DEADLINE, 0L)
        if (System.currentTimeMillis() >= deadline || DemoMode.active) return@withContext Result.success()
        try {
            ServiceLocator.ensurePushReady(applicationContext)
            val api = ServiceLocator.apiBackend
            api.initialize()
            if (DemoMode.active || api.multiAccount.accounts().none { !it.sessionExpired }) return@withContext Result.success()
            if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                if (FirebaseApp.initializeApp(applicationContext) == null) return@withContext Result.success()
            }
            // Fetch on every attempt: a queued wake cannot re-register a superseded captured platform token.
            val token = Tasks.await(FirebaseMessaging.getInstance().token, TOKEN_WAIT_SECONDS, TimeUnit.SECONDS)
            val complete = !token.isNullOrBlank() && ServiceLocator.registerRetainedPushAccounts(token, deadline)
            if (complete) Result.success() else retryOrFinish(deadline)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { retryOrFinish(deadline) }
    }

    private fun retryOrFinish(deadline: Long): Result =
        if (runAttemptCount < MAX_RETRIES && System.currentTimeMillis() < deadline) Result.retry() else Result.success()

    companion object {
        private const val DEADLINE = "registration_deadline_ms"
        private const val MAX_RETRIES = 5
        private const val TOKEN_WAIT_SECONDS = 30L
        private const val RETENTION_HOURS = 24L
        private const val UNIQUE_NAME = "chaty_retained_push_registration"

        fun enqueue(context: Context) {
            if (DemoMode.active) return
            val request = OneTimeWorkRequestBuilder<PushRegistrationRecoveryWorker>()
                .setInputData(workDataOf(DEADLINE to (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(RETENTION_HOURS))))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build()
            // Serialize accepted token/login wakes; terminal exhaustion must not poison appended work.
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request).result.get()
        }
    }
}
