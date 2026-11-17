package com.example.chat.ui.chats.detail

import androidx.compose.material3.Text
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.chat.data.e2ee.DeviceKeyTrustStore
import com.example.chat.ui.kit.ChatyConfirmationDialog

/** Review is explicit and fingerprint-specific. It never says TOFU verifies identity. */
@Composable
fun DeviceKeyReviewDialog(review: DeviceKeyTrustStore.Review, busy: Boolean, error: String?,
                          onApprove: () -> Unit, onDismiss: () -> Unit) {
    ChatyConfirmationDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (review.previous == null) "New encrypted device" else "Device signing key changed") },
        text = { Text("Sending is paused for this device. Compare this fingerprint with the person over another channel before approving. First-seen keys are not identity-verified.\n\nDevice: ${review.deviceKey}\n\nFingerprint:\n${review.fingerprint}" +
            (review.previous?.let { "\n\nPrevious fingerprint:\n$it" } ?: "") + (error?.let { "\n\n$it" } ?: "")) },
        confirmButton = { TextButton(onClick = onApprove, enabled = !busy) { Text("Approve this exact key") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Keep blocked") } },
    )
}


@Composable
fun DeviceKeySafetyDialog(devices: List<DeviceKeyTrustStore.Observation>, onDismiss: () -> Unit) {
    ChatyConfirmationDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encrypted device fingerprints") },
        text = {
            // ChatyConfirmationDialog owns the bounded scrolling body.
            androidx.compose.foundation.layout.Column {
                Text("Compare device fingerprints over another channel. First-seen is not verified identity. Approval records your choice, not an independent identity check. A replacement device gets future messages; old history needs an existing trusted device. No server key escrow is used.")
                if (devices.isEmpty()) Text("No device keys have been loaded. Reopen the chat while connected to check them.")
                devices.forEach { Text("\nDevice: ${it.deviceKey}\n${it.label}\n${it.fingerprint}") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
