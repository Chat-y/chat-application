package com.example.chat.ui.settings.security

import com.example.chat.data.services.LocalLockService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Mirror of _AppLockOverlayModalState fields. */
data class AppLockUiState(
    val capabilitiesReady: Boolean = false,
    val capabilityError: Boolean = false,
    val enteredPin: String = "",
    val errorMessage: String = "",
    val busy: Boolean = false,
    val biometricAvailable: Boolean = false,
    val hasConfiguredCredential: Boolean = true,
    val nativePromptStarted: Boolean = false,
    val pinLength: Int = 4,
    val cooldownSeconds: Int = 0,
)

/**
 * Pure-JVM state machine behind the app lock overlay, ported line for line
 * from _AppLockOverlayModalState (app_lock_overlay.dart): capability load,
 * native biometric/device prompt, secret verification with cooldown messages,
 * PIN digit entry. The overlay calls [onUnlocked] ONLY after a successful
 * verification; there is no other path to unlock (fail closed).
 */
class AppLockController(
    private val lockService: LocalLockService,
    private val lockMethod: () -> String,
    private val reason: String,
    private val onUnlocked: () -> Unit,
) {
    @Volatile private var active = true

    /** Late native/credential responses must not unlock a dismissed or replaced overlay. */
    fun dispose() {
        active = false
        update { it.copy(enteredPin = "", errorMessage = "", busy = false) }
    }

    private val _state = MutableStateFlow(AppLockUiState())
    val state: StateFlow<AppLockUiState> get() = _state

    private fun update(f: (AppLockUiState) -> AppLockUiState) { _state.value = f(_state.value) }

    private fun tooMany(seconds: Int) = "Too many failed attempts. Try again in ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}."

    suspend fun loadCapabilities() {
        if (!active) return
        update { it.copy(capabilitiesReady = false, capabilityError = false) }
        try {
        val method = lockMethod()
        val pinLength = lockService.getPinLength()
        val biometric = lockService.canUseBiometrics()
        val cooldown = lockService.getRemainingCooldownSeconds()
        var hasCredential = true
        if (method == "PIN" || method == "Pattern" || method == "Password") {
            hasCredential = lockService.hasCredentialForSetup(method)
        }
        if (!active) return
        update {
            it.copy(
                capabilitiesReady = true,
                capabilityError = false,
                pinLength = pinLength,
                biometricAvailable = biometric,
                hasConfiguredCredential = hasCredential,
                cooldownSeconds = cooldown,
                errorMessage = if (cooldown > 0) tooMany(cooldown) else it.errorMessage,
            )
        }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (active) update { it.copy(capabilitiesReady = false, capabilityError = true,
                errorMessage = "Could not read lock settings. Retry without changing your credential.") }
        }
    }

    suspend fun startNativeMethodIfNeeded() {
        if (!active || _state.value.nativePromptStarted) return
        val method = lockMethod()
        if (method != "Biometric" && method != "Device Credential") return
        update { it.copy(nativePromptStarted = true) }
        runNativeAuthentication(method)
    }

    suspend fun runNativeAuthentication(method: String) {
        if (!active || _state.value.busy) return
        update { it.copy(busy = true, errorMessage = "") }
        try {
            val success = if (method == "Device Credential") lockService.authenticateDeviceCredential(reason)
            else lockService.authenticateBiometric(reason)
            if (success && active) onUnlocked()
            else update {
                it.copy(errorMessage = if (method == "Device Credential")
                    "Device authentication was cancelled or is unavailable."
                else "Biometric authentication was cancelled, unavailable, or no biometric is enrolled.")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { update { it.copy(errorMessage = "Authentication is unavailable. Try again.") } }
        finally { update { it.copy(busy = false, enteredPin = "") } }
    }

    suspend fun verifySecret(method: String, value: String) {
        if (!active || _state.value.busy) return
        val cooldown = lockService.getRemainingCooldownSeconds()
        if (cooldown > 0) {
            update { it.copy(cooldownSeconds = cooldown, errorMessage = tooMany(cooldown), enteredPin = "") }
            return
        }
        update { it.copy(busy = true, errorMessage = "") }
        try {
            val valid = lockService.verifyCredential(method, value)
            if (valid) { if (active) onUnlocked(); return }
            val newCooldown = lockService.getRemainingCooldownSeconds()
            update { it.copy(cooldownSeconds = newCooldown,
                errorMessage = if (newCooldown > 0) tooMany(newCooldown) else "Incorrect ${method.lowercase()}. Try again.") }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { update { it.copy(errorMessage = "Could not verify. Try again.") } }
        finally { update { it.copy(busy = false, enteredPin = "") } }
    }

    suspend fun refreshCooldown() {
        if (!active) return
        val remaining = lockService.getRemainingCooldownSeconds()
        update { current -> current.copy(
            cooldownSeconds = remaining,
            errorMessage = if (remaining > 0) tooMany(remaining)
                else if (current.cooldownSeconds > 0) "You can try again now." else current.errorMessage,
        ) }
    }

    suspend fun verifyPinDigit(digit: String) {
        if (!active || digit.length != 1 || digit[0] !in '0'..'9') return
        val s = _state.value
        if (s.busy || s.enteredPin.length >= s.pinLength || s.cooldownSeconds > 0) return
        val next = s.enteredPin + digit
        update { it.copy(enteredPin = next, errorMessage = "") }
        if (next.length == s.pinLength) verifySecret("PIN", next)
    }

    fun deleteDigit() {
        if (!active) return
        val s = _state.value
        if (s.busy || s.enteredPin.isEmpty()) return
        update { it.copy(enteredPin = it.enteredPin.dropLast(1)) }
    }
}
