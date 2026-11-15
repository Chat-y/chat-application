package com.example.chat.data.api

import kotlinx.coroutines.Job
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Carries only cancellation ownership across synchronous auth API calls on IO threads. */
object AuthHttpCallContext {
    private val owner=ThreadLocal<Job?>()
    suspend fun <T> owned(block:suspend ()->T):T {
        val job=currentCoroutineContext()[Job]
        return withContext(owner.asContextElement(job)) {block().also {currentCoroutineContext().ensureActive()}}
    }
    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    fun onCancellation(cancel:()->Unit):kotlinx.coroutines.DisposableHandle? =
        owner.get()?.invokeOnCompletion(onCancelling=true,invokeImmediately=true) {cause->if(cause!=null) cancel()}
}
