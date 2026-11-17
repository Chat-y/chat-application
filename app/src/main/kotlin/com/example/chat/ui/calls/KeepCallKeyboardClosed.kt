package com.example.chat.ui.calls

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Suppress inherited composer IME only while an opaque full-screen call owns the UI. */
@Composable
fun KeepCallKeyboardClosed(enabled: Boolean) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(enabled, imeBottom) {
        if (enabled) {
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}
