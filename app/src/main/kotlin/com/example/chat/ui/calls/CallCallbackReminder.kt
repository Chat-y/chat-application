package com.example.chat.ui.calls

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.chat.ServiceLocator
import com.example.chat.data.api.ApiAccountScope
import com.example.chat.data.api.ApiAccountRoutingCanonicalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Generic reminder only. Account hash + random process nonce, never credentials or derived credentials.
 * The in-memory binding cannot be restored after process death: delayed work fails closed. */
object CallCallbackReminder {
    const val TAG = "chaty_call_callback_reminders_v1"
    private const val OWNER = "account_hash"
    private const val SESSION = "process_session_nonce"
    private val processBinding = CallReminderProcessBinding()
    fun ownerHash(owner: ApiAccountScope): String? =
        ApiAccountRoutingCanonicalizer.accountKey(owner.tenantId, owner.baseUrl, owner.userId.orEmpty())
            ?.let { ApiAccountRoutingCanonicalizer.accountHash(it) }

    suspend fun schedule(context: Context, owner: ApiAccountScope): kotlin.Result<Unit> = try {
        check(ServiceLocator.apiBackend.authSnapshot() === owner && !owner.token.isNullOrBlank())
        check(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            "Allow notifications before scheduling a call-back reminder."
        }
        val key = checkNotNull(ownerHash(owner))
        val request = OneTimeWorkRequestBuilder<CallCallbackReminderWorker>()
            .setInitialDelay(15, TimeUnit.MINUTES).addTag(TAG)
            .setInputData(workDataOf(OWNER to key, SESSION to processBinding.bind(owner))).build()
        withContext(Dispatchers.IO) {
            check(ServiceLocator.apiBackend.authSnapshot() === owner)
            // One pending generic reminder per account; choosing this again replaces it.
            WorkManager.getInstance(context).enqueueUniqueWork("$TAG:$key", ExistingWorkPolicy.REPLACE, request)
                .result.get()
        }
        if (ServiceLocator.apiBackend.authSnapshot() !== owner) {
            WorkManager.getInstance(context).cancelWorkById(request.id)
            error("The call account changed.")
        }
        kotlin.Result.success(Unit)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (failure: Exception) { kotlin.Result.failure(failure) }

    internal fun matches(worker: CoroutineWorker, owner: ApiAccountScope): Boolean =
        !owner.token.isNullOrBlank() && ownerHash(owner) == worker.inputData.getString(OWNER) &&
            processBinding.matches(owner, worker.inputData.getString(SESSION))
}

class CallCallbackReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val owner = ServiceLocator.apiBackend.authSnapshot()
        if (!CallCallbackReminder.matches(this, owner))
            return Result.success()
        // The generation-scoped serial notification lane owns final admission and cleanup.
        ServiceLocator.notificationCoordinator.postCallbackReminder(owner, id.toString())
        return Result.success()
    }
}
