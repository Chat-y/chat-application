package com.example.chat.ui.calls

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App-level keypad-sound choice, ON unless a stored choice turned it off
 *  (Bandi 2026-11-17: dialpad keys sound on by default). System/ringer rules remain separate gates. */
class KeypadSoundPreferences(
    scope: CoroutineScope,
    private val read: suspend () -> Boolean,
    private val write: suspend (Boolean) -> Unit,
) {
    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed = _saveFailed.asStateFlow()
    private val revision = java.util.concurrent.atomic.AtomicLong()
    private val requests = Channel<Pair<Long, Boolean>>(Channel.CONFLATED)
    init {
        scope.launch {
            try { _enabled.value = read() }
            catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                _enabled.value = false
            }
            _ready.value = true
            for ((ticket, choice) in requests) {
                // Disabling silences immediately, even if disk fails. Enabling waits for a saved opt-in.
                if (!choice) _enabled.value = false
                try {
                    write(choice)
                    if (ticket == revision.get()) {
                        _enabled.value = choice
                        _saveFailed.value = false
                    }
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    if (ticket == revision.get()) {
                        _enabled.value = false
                        _saveFailed.value = true
                    }
                }
            }
        }
    }
    fun choose(enabled: Boolean) {
        if (!_ready.value) return
        if (!enabled) _enabled.value = false
        requests.trySend(revision.incrementAndGet() to enabled)
    }
    companion object {
        fun onDevice(context: Context, scope: CoroutineScope): KeypadSoundPreferences {
            val app = context.applicationContext
            return KeypadSoundPreferences(scope,
                read = { withContext(Dispatchers.IO) {
                    // Unset means ON (Bandi 2026-11-17); any saved choice still wins.
                    // Prior opt-in default: .all["keypad_sounds"] == true
                    app.getSharedPreferences("chaty_audio_preferences", Context.MODE_PRIVATE)
                        .all["keypad_sounds"] != false
                } },
                write = { value -> withContext(Dispatchers.IO) {
                    check(app.getSharedPreferences("chaty_audio_preferences", Context.MODE_PRIVATE)
                        .edit().putBoolean("keypad_sounds", value).commit()) { "Keypad sound choice could not be saved" }
                } })
        }
    }
}
