package com.example.chat.data.services

/** Fixed debug codes only. Never include identities, URLs, credentials or SDP. */
enum class CallFailureStage {
    UNKNOWN, ICE_CONFIG, PERMISSION, CLEANUP, FOREGROUND, AUDIO_FOCUS,
    MEDIA_ARBITER, NATIVE_FACTORY, LOCAL_CAPTURE, AUDIO_SESSION, INITIAL_ROUTE,
    SDP_OFFER, SDP_ANSWER, REMOTE_DESCRIPTION, SIGNALING, ICE_NEGOTIATION, PEER_CONNECTION,
    REMOTE_SIGNAL_HANDLER, ICE_RESTART, ICE_RESTART_REFUSED, CANDIDATE_CALLBACK,
    WS_SIGNAL_NOT_QUEUED, REST_SIGNAL_UNAUTHORIZED, REST_SIGNAL_UNAVAILABLE,
    REST_SIGNAL_STALE, REST_SIGNAL_FAILED, REST_SIGNAL_EXCEPTION,
// Previous implementation retained:     CONNECTION_TIMEOUT, INCOMING_SDP_MISSING
    CONNECTION_TIMEOUT, INCOMING_SDP_MISSING, SETUP_DEADLINE, MICROPHONE_UNAVAILABLE
}

/** Weak keys avoid retaining failures. The original throwable and its type/message are unchanged. */
internal object CallFailureDiagnostics {
    /** Fixed enum only. Logging must never alter callback/cleanup/error behavior. */
    fun report(stage: CallFailureStage) {
        if (!com.example.chat.BuildConfig.DEBUG) return
        runCatching { android.util.Log.w("ChatyCallStage", "Call failure [${stage.name}]") }
    }
    private val stages = java.util.WeakHashMap<Throwable, CallFailureStage>()
    fun record(failure: Throwable, stage: CallFailureStage) {
        if (!com.example.chat.BuildConfig.DEBUG || failure is kotlinx.coroutines.CancellationException) return
        synchronized(stages) { if (!stages.containsKey(failure)) stages[failure] = stage }
        report(stage)
    }
    fun stageOf(failure: Throwable): CallFailureStage =
        synchronized(stages) { stages[failure] ?: CallFailureStage.UNKNOWN }
}

internal suspend fun <T> atCallStage(stage: CallFailureStage, block: suspend () -> T): T = try {
    block()
} catch (failure: Throwable) {
    CallFailureDiagnostics.record(failure, stage)
    throw failure
}

internal fun <T> atCallStageSync(stage: CallFailureStage, block: () -> T): T = try {
    block()
} catch (failure: Throwable) {
    CallFailureDiagnostics.record(failure, stage)
    throw failure
}
