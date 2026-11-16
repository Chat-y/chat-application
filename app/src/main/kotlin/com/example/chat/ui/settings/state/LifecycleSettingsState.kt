package com.example.chat.ui.settings.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow

/** Same STARTED collection contract as lifecycle-compose, using the project's existing
 * lifecycle-runtime-ktx dependency. Collection and listener registration stop in the background. */
@Composable
internal fun <T> StateFlow<T>.collectSettingsState(): State<T> {
    val owner = LocalLifecycleOwner.current
    return produceState(value, this, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            this@collectSettingsState.collect { value = it }
        }
    }
}
