package com.example.chat.ui.calls

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.ensureActive
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.chat.data.services.CallPermissionGate
import com.example.chat.ui.kit.ChatyConfirmationDialog

/** Asks only after a call action, before signaling or camera/microphone activation. */
@Composable
internal fun rememberCallPermissionCheck(): suspend (Boolean) -> Boolean {
    val context = LocalContext.current
    var denied by remember { mutableStateOf<List<String>>(emptyList()) }
    var settingsFailed by remember { mutableStateOf(false) }
    val latestContext by rememberUpdatedState(context)
    if (denied.isNotEmpty()) {
        val activity = remember(context) { callPermissionActivity(context) }
        val settingsNeeded = activity == null || denied.any {
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        val cameraMissing = Manifest.permission.CAMERA in denied
        ChatyConfirmationDialog(
            onDismissRequest = { denied = emptyList(); settingsFailed = false },
            title = { Text(if (cameraMissing) "Call permissions needed" else "Microphone permission needed") },
            text = { Text(if (settingsFailed) "App settings could not be opened. Open Chaty's app permissions from Android Settings."
                else if (settingsNeeded) "The call was not started. Allow the microphone${if (cameraMissing) " and camera" else ""} in app settings, then tap Call again."
                else "The call was not started. The microphone${if (cameraMissing) " and camera are" else " is"} needed for this call. You can allow access in app settings or try calling again.") },
            confirmButton = { TextButton(onClick = {
                settingsFailed = runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)))
                }.isFailure
                if (!settingsFailed) denied = emptyList()
            }) { Text("App settings") } },
            dismissButton = { TextButton(onClick = { denied = emptyList(); settingsFailed = false }) { Text("Cancel") } },
        )
    }
    // Prior nested literal was inferred as a non-suspend Function1 by the compiler.
    return remember {
        val checkPermission: suspend (Boolean) -> Boolean = { video ->
            val ctx = latestContext
            fun missing() = CallPermissionGate.missing({ permission ->
                ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
            }, video)
            val need = missing()
            if (need.isEmpty()) true else {
                try { CallPermissionGate.requester?.invoke(need) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { /* Re-read the OS state below, never trust an exception/result map. */ }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val remaining = missing()
                denied = remaining
                remaining.isEmpty()
            }
        }
        checkPermission
    }
}

private fun callPermissionActivity(context: Context): android.app.Activity? {
    var candidate = context
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Context, Boolean>())
    while (seen.add(candidate)) {
        if (candidate is android.app.Activity) return candidate
        candidate = (candidate as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}
