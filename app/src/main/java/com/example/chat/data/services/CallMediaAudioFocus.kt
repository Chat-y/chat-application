package com.example.chat.data.services

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Transient loss resumes only the same live call; permanent loss never auto-resumes. */
internal class CallFocusInterruptionPolicy {
    var mayPlay: Boolean = true
        private set
    private var permanentlyLost = false
    fun update(change: Int): Boolean {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { permanentlyLost = true; mayPlay = false }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mayPlay = false
            AudioManager.AUDIOFOCUS_GAIN -> if (!permanentlyLost) mayPlay = true
        }
        return mayPlay
    }
}

/** Request only after foreground readiness; no delayed focus starts a call later. */
internal class CallMediaAudioFocus(
    private val audioManager: AudioManager,
    private val onMayPlay: (Boolean) -> Unit,
) : AutoCloseable {
    private val policy = CallFocusInterruptionPolicy()
    @Volatile private var closed = false
    private var requested = false
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        synchronized(this) { if (!closed) onMayPlay(policy.update(change)) }
    }
    private val request: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
            .build()
    } else null

    @Synchronized fun acquire(): Boolean {
        if (closed) return false
        requested = true // Abandon even after denied request or an exception during acquisition.
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(checkNotNull(request))
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        if (!requested) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(checkNotNull(request))
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
    }
}
