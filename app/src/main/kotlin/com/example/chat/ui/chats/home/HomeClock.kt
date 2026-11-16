package com.example.chat.ui.chats.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.repeatOnLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate

/** One lifecycle-aware Home clock. The minute is passed as a value across
 * strong-skipping boundaries, rather than only invalidating a parent scope. */
@Composable
fun rememberHomeMinute(): Long {
    val context=LocalContext.current.applicationContext
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val minute by produceState(System.currentTimeMillis()/60000,lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            // A correction cancels the old delay, samples wall time immediately,
            // then calculates the next boundary again. Resume also emits once.
            homeClockChanges(context).collectLatest {
            while(true) {
                val now=System.currentTimeMillis()
                value=now/60000
                kotlinx.coroutines.delay((60000-now%60000).coerceAtLeast(1))
            }
            }
        }
    }
    return minute
}

/** A calendar key separate from the minute used by row-relative labels.
 * Use this for date partitions/cutoffs, not for unrelated search or folder work.
 * The revision also invalidates formatting after configuration changes whose
 * result cannot be represented by an epoch-minute value alone. */
data class HomeDateContext(
    val zoneId: ZoneId,
    val localDate: LocalDate,
    val configurationRevision: Long,
)

/** Pass the existing shared Home minute; this does not start another ticker.
 * Consumers must include this value in date-dependent remember keys and pass
 * an appropriate invalidation across skipped rendering boundaries. Merely
 * observing rememberHomeMinute() cannot refresh a same-minute timezone change. */
@Composable
fun rememberHomeDateContext(clockMinute: Long): HomeDateContext {
    val context=LocalContext.current.applicationContext
    val configuration=LocalConfiguration.current
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val revision by produceState(0L,lifecycle,context,configuration) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            homeClockChanges(context).collect { value=value+1 }
        }
    }
    // Configuration is a key as well as the broadcast revision, so an Activity
    // configuration update does not depend on receiver delivery order.
    val zone=remember(configuration,revision) { ZoneId.systemDefault() }
    val date=remember(clockMinute,zone) {
        Instant.ofEpochMilli(clockMinute*60000).atZone(zone).toLocalDate()
    }
    return remember(zone,date,revision) { HomeDateContext(zone,date,revision) }
}

/** Re-base a date grouping captured in UI state on the current clock/date
 * context. A HomeGroupMode.Date built in a click handler keeps the `now`/`zone`
 * of that moment, so Today/Yesterday/Earlier cutoffs drift after midnight or a
 * timezone change even when the caller re-derives on every shared-minute tick.
 * Pure and ticker-free: call it where the grouping runs, inside a remember keyed
 * on the shared minute and the date context's configurationRevision. */
fun HomeGroupMode.Date.withCurrentDateContext(
    dateContext: HomeDateContext,
    clockMinute: Long,
): HomeGroupMode.Date = copy(
    now = Instant.ofEpochMilli(clockMinute * 60000),
    zone = dateContext.zoneId,
)

/** Registered only while a Home clock consumer is STARTED. No alarm, service,
 * persistence, network access or per-row receiver is needed. System protected
 * broadcasts are observed with a non-exported dynamic receiver. */
private fun homeClockChanges(context: Context): Flow<Unit> = callbackFlow {
    val receiver=object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            trySend(Unit)
        }
    }
    val filter=IntentFilter().apply {
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_CONFIGURATION_CHANGED)
    }
    ContextCompat.registerReceiver(context,receiver,filter,ContextCompat.RECEIVER_NOT_EXPORTED)
    // Register first, then sample, so a correction during setup is not missed.
    trySend(Unit)
    awaitClose { context.unregisterReceiver(receiver) }
}.conflate()
