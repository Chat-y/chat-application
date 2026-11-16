package com.example.chat.ui.settings.security

/** Narrow service seam over LocalLockService (hasCredential/verifyCredential/setCredential/authenticate*). */
interface LockSetupService {
    suspend fun hasCredential(method: String): Boolean
    suspend fun verifyCredential(method: String, secret: String): Boolean
    suspend fun setCredential(method: String, secret: String, pinLength: Int?)
    suspend fun authenticateBiometric(reason: String): Boolean
    suspend fun authenticateDeviceCredential(reason: String): Boolean
}

enum class SetupStep { enterCurrent, enterNew, confirmNew }

/** What the UI must do after a flow action (focus/clear/reset/close are view concerns). */
enum class SetupOutcome { none, clearPin, clearText, resetPad, saved }

/**
 * Port of the logic in _LockCredentialSetupModalState (lock_credential_setup_modal.dart).
 * Pure state machine: every error string, validation and step transition matches the Dart source.
 */
class LockCredentialSetupFlow(
    val method: String,
    val pinLength: Int,
    private val service: LockSetupService,
    private val replaceExisting: Boolean = false,
) {
    private var generation = 0L
    var initialized = false; private set
    var step: SetupStep = SetupStep.enterNew; private set
    var hasExistingSecret = false; private set
    var error = ""; private set
    var busy = false; private set
    var firstPatternDrawn: String? = null; private set
    var confirmPatternDrawn: String? = null; private set
    private var newSecret = ""

    val isPin get() = method == "PIN"
    val isPattern get() = method == "Pattern"
    val isPassword get() = method == "Password"
    val isBiometric get() = method == "Biometric"
    val isDeviceCredential get() = method == "Device Credential"

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun changed() = listeners.forEach { it() }

    suspend fun init() {
        val owner = ++generation
        initialized = false
        busy = true
        error = ""
        changed()
        try {
            val hasCred = service.hasCredential(method)
            if (owner != generation) return
            hasExistingSecret = hasCred && !replaceExisting
            step = if (hasExistingSecret) SetupStep.enterCurrent else SetupStep.enterNew
            initialized = true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (owner == generation) error = "Could not read lock settings. Close and reopen setup to retry."
        } finally {
            if (owner == generation) { busy = false; changed() }
        }
    }

    fun setPatternDrawnClear(isConfirm: Boolean) {
        // onPatternReset callback of the pad
        if (isConfirm) confirmPatternDrawn = null else firstPatternDrawn = null
        changed()
    }

    /** Drop in-progress secrets when the sheet closes or the app backgrounds. */
    fun clearSensitiveState() {
        generation++
        initialized = false
        newSecret = ""
        firstPatternDrawn = null
        confirmPatternDrawn = null
        error = ""
        busy = false
        step = if (hasExistingSecret) SetupStep.enterCurrent else SetupStep.enterNew
        changed()
    }

    fun onPatternReset() = setPatternDrawnClear(step == SetupStep.confirmNew)

    /** PIN and Password share Dart's _handleContinue. [input] is the joined PIN or the raw password field. */
    suspend fun handleContinue(input: String): SetupOutcome {
        if (!initialized || busy) return SetupOutcome.none
        val owner = generation
        error = ""; changed()
        if (isPin) {
            if (input.any { it !in '0'..'9' }) { error = "Use digits 0 to 9."; changed(); return SetupOutcome.none }
            val pin = input
            if (pin.length != pinLength) { error = "Enter all $pinLength digits."; changed(); return SetupOutcome.none }
            when (step) {
                SetupStep.enterCurrent -> {
                    busy = true; changed()
                    val ok = verifySafely("PIN", pin)
                    if (owner != generation) return SetupOutcome.none
                    return if (ok) {
                        step = SetupStep.enterNew; error = ""; changed(); SetupOutcome.clearPin
                    } else {
                        error = "Current PIN is incorrect."; changed(); SetupOutcome.clearPin
                    }
                }
                SetupStep.enterNew -> {
                    CredentialStrength.pinError(pin)?.let { error = it; changed(); return SetupOutcome.none }
                    newSecret = pin
                    step = SetupStep.confirmNew; error = ""; changed()
                    return SetupOutcome.clearPin
                }
                SetupStep.confirmNew -> {
                    if (pin != newSecret) {
                        error = "PINs do not match. Try entering again."; changed(); return SetupOutcome.clearPin
                    }
                    return saveFinalCredential(pin)
                }
            }
        } else if (isPassword) {
            val pass = input // Preserve spaces, including existing credentials.
            if (pass.isBlank()) { error = "Please enter a password."; changed(); return SetupOutcome.none }
            when (step) {
                SetupStep.enterCurrent -> {
                    busy = true; changed()
                    val ok = verifySafely("Password", pass)
                    if (owner != generation) return SetupOutcome.none
                    return if (ok) {
                        step = SetupStep.enterNew; error = ""; changed(); SetupOutcome.clearText
                    } else {
// Superseded source retained below per additive-only review rule.
//                         error = "Current password is incorrect."; changed(); SetupOutcome.none
                        error = "Current password is incorrect."; changed(); SetupOutcome.clearText
                    }
                }
                SetupStep.enterNew -> {
                    CredentialStrength.passwordError(pass)?.let { error = it; changed(); return SetupOutcome.none }
                    newSecret = pass
                    step = SetupStep.confirmNew; error = ""; changed()
                    return SetupOutcome.clearText
                }
                SetupStep.confirmNew -> {
                    if (pass != newSecret) { error = "Passwords do not match. Try again."; changed(); return SetupOutcome.none }
                    return saveFinalCredential(pass)
                }
            }
        }
        return SetupOutcome.none
    }

    private suspend fun verifySafely(method: String, secret: String): Boolean {
        val owner = generation
        return try {
        service.verifyCredential(method, secret)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    } finally {
        if (owner == generation) { busy = false; changed() }
    }
    }

    private suspend fun saveFinalCredential(secret: String): SetupOutcome {
        val owner = generation
        busy = true; error = ""; changed()
        return try {
            service.setCredential(method, secret, if (isPin) pinLength else null)
            if (owner == generation) { newSecret = ""; SetupOutcome.saved } else SetupOutcome.none
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (owner == generation) error = "Could not save the credential. Try again."
            SetupOutcome.none
        } finally {
            if (owner == generation) { busy = false; changed() }
        }
    }

    fun onPatternComplete(pattern: String) {
        if (!initialized || busy) return
        // New enrollment only. Existing credential verification stays backward compatible.
        val nodes = if (pattern.length <= 17) pattern.split('-') else emptyList()
        val valid = if (step == SetupStep.enterCurrent) {
            // Historical verifier accepts normalized legacy node encodings.
            val legacy = pattern.split('-').filter { it.isNotEmpty() }.map { it.toIntOrNull() }
            legacy.size >= 4 && legacy.all { it != null && it in 0..8 } && legacy.toSet().size == legacy.size
// Superseded source retained below per additive-only review rule.
//         } else nodes.size in 4..9 && nodes.all { it.length == 1 && it[0] in '0'..'8' } && nodes.toSet().size == nodes.size
        } else nodes.size in 5..9 && nodes.all { it.length == 1 && it[0] in '0'..'8' } && nodes.toSet().size == nodes.size
        if (!valid) {
            if (step == SetupStep.confirmNew) confirmPatternDrawn = null else firstPatternDrawn = null
// Superseded source retained below per additive-only review rule.
//             error = "Connect at least 4 dots using each dot once."
            error = if (step == SetupStep.enterCurrent) "Connect at least 4 dots using each dot once." else "Use at least 5 different dots for a new pattern. Avoid simple letter shapes."
            changed(); return
        }
        error = ""
        when (step) {
            SetupStep.enterCurrent, SetupStep.enterNew -> firstPatternDrawn = pattern
            SetupStep.confirmNew -> confirmPatternDrawn = pattern
        }
        changed()
    }

    val canContinuePattern: Boolean
        get() = (if (step == SetupStep.confirmNew) confirmPatternDrawn != null else firstPatternDrawn != null) && initialized && !busy

    suspend fun handlePatternContinue(): SetupOutcome {
        if (!initialized || busy) return SetupOutcome.none
        val owner = generation
        val pattern = if (step == SetupStep.confirmNew) confirmPatternDrawn else firstPatternDrawn
        if (pattern == null) {
            error = if (step == SetupStep.confirmNew) "Draw the confirmation pattern first." else "Draw your pattern first."
            changed(); return SetupOutcome.none
        }
        when (step) {
            SetupStep.enterCurrent -> {
                busy = true; changed()
                val ok = verifySafely("Pattern", pattern)
                if (owner != generation) return SetupOutcome.none
                if (ok) { firstPatternDrawn = null; step = SetupStep.enterNew; error = "" }
                else { firstPatternDrawn = null; error = "Current pattern is incorrect." }
                changed(); return SetupOutcome.resetPad
            }
            SetupStep.enterNew -> {
                newSecret = pattern
                firstPatternDrawn = null; confirmPatternDrawn = null
                step = SetupStep.confirmNew; error = ""; changed()
                return SetupOutcome.resetPad
            }
            SetupStep.confirmNew -> {
                if (pattern != newSecret) {
                    confirmPatternDrawn = null
                    error = "Patterns do not match. Draw again to confirm."; changed()
                    return SetupOutcome.resetPad
                }
                return saveFinalCredential(newSecret)
            }
        }
    }

    suspend fun verifyNativeMethod(): SetupOutcome {
        if (!initialized || busy) return SetupOutcome.none
        val owner = generation
        busy = true; error = ""; changed()
        return try {
            val success = if (isBiometric) service.authenticateBiometric("Verify your biometric to enable Chaty Lock")
            else service.authenticateDeviceCredential("Verify your device screen lock to enable Chaty Lock")
            if (owner != generation) SetupOutcome.none
            else if (success) SetupOutcome.saved else {
                error = if (isBiometric) "Biometric verification was cancelled or unavailable."
                else "Device credential verification was cancelled or unavailable."
                SetupOutcome.none
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (owner == generation) error = "Authentication unavailable. Try again."
            SetupOutcome.none
        } finally { if (owner == generation) { busy = false; changed() } }
    }

    fun stepTitle(): String = when (step) {
        SetupStep.enterCurrent -> "Enter Current $method"
        SetupStep.enterNew -> if (hasExistingSecret) "Enter New $method" else "Create $method"
        SetupStep.confirmNew -> "Confirm New $method"
    }

    fun stepSubtitle(): String = when (step) {
        SetupStep.enterCurrent -> "Please authenticate with your existing $method to continue."
        SetupStep.enterNew -> when {
            isPin -> "Choose a $pinLength-digit PIN code."
// Superseded source retained below per additive-only review rule.
//             isPattern -> "Draw an unlock pattern connecting at least 4 dots."
            isPattern -> "Connect at least 5 different dots. Avoid simple letter shapes."
            isPassword -> "Enter a password (min 8 characters). Spaces are preserved."
            else -> "Choose your credential."
        }
        SetupStep.confirmNew -> "Re-enter the same $method to ensure accuracy."
    }
}
