package com.example.chat.ui.calls

import android.content.Context
import androidx.compose.runtime.Composable
import com.example.chat.ui.settings.state.collectSettingsState
import androidx.compose.runtime.getValue

/** A passive OS signal, not a call-quality probe or proof the peer/server is reachable. */
internal fun callHasValidatedNetwork(context: Context): Boolean =
    com.example.chat.data.services.CallNetworkAdmission.online(context)

@Composable
internal fun rememberCallNetworkOnline(): Boolean {
    val status by com.example.chat.ServiceLocator.networkMonitor.state.collectSettingsState()
    return status.online
}
