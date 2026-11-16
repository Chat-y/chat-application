package com.example.chat.data.services

/** One client ringing bound for both directions. The server may end a call sooner. */
object CallRingPolicy {
    const val TIMEOUT_SECONDS = 30
    const val TIMEOUT_MILLIS = TIMEOUT_SECONDS * 1_000L
}
