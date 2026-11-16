package com.example.chat.ui.settings.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/** UI adapter: listen only while a screen collects and emit only changed selected values.
 * Register before the fresh snapshot so resubscription cannot miss changes made while stopped. */
internal fun <T> settingsSnapshotFlow(
    scope: CoroutineScope,
    snapshot: () -> T,
    addListener: (() -> Unit) -> Unit,
    removeListener: (() -> Unit) -> Unit,
): StateFlow<T> = callbackFlow {
    val listener: () -> Unit = { trySend(snapshot()); Unit }
    addListener(listener)
    try {
        trySend(snapshot())
        awaitClose { }
    } finally {
        removeListener(listener)
    }
}.distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), snapshot())
