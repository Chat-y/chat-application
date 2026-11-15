package com.example.chat.ui.core.components.auth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.chat.ui.auth.AuthPresentationPolicy

/** Once per locally retained account. No server state or analytics; dismissal never blocks features. */
@Composable
fun AuthWelcomeTour(accountKey: String, displayName: String) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("chaty_onboarding", android.content.Context.MODE_PRIVATE) }
    val key = remember(accountKey) { "tour_v1_" + java.security.MessageDigest.getInstance("SHA-256")
        .digest(accountKey.toByteArray()).joinToString("") { "%02x".format(it) } }
    var visible by remember(key) { mutableStateOf(accountKey.isNotBlank() && !prefs.getBoolean(key,false)) }
    var step by remember(key) { mutableIntStateOf(0) }
    val finish = { prefs.edit().putBoolean(key,true).apply(); visible = false }
    if (visible) AlertDialog(onDismissRequest = finish,
        title = { Text(if (step == 0) authCopy(AuthPresentationPolicy.greeting(java.time.LocalTime.now().hour)) else authCopy("Make yourself at home")) },
        text = { Text(when(step) {
            0 -> if (displayName.isBlank()) authCopy("Welcome to Chaty.") else authFormat("Welcome to Chaty, %1\$s.",displayName)
            1 -> authCopy("Open a chat to send messages. Use search to find a conversation.")
            2 -> authCopy("Open Calls to see your calls. Start a voice or video call from a chat.")
            else -> authCopy("Your settings hold appearance, notifications and privacy controls. You can change them any time.")
        }) },
        confirmButton = { TextButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = { if(step < 3) step++ else finish() }) { Text(if(step < 3) authCopy("Next") else authCopy("Done")) } },
        dismissButton = { TextButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = finish) { Text(authCopy("Skip tour")) } })
}
