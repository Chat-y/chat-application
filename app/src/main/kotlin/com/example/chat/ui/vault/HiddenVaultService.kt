package com.example.chat.ui.vault

import com.example.chat.data.e2ee.SecureKeyValueStore
import com.example.chat.data.services.LocalLockService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Exact port of lib/core/services/hidden_vault_service.dart.
 *
 *  Service coordinating PBKDF2 verification, biometric gate, auto-lock
 *  timeouts, decoy mode, and encrypted locked-conversation IDs.
 *  ChangeNotifier is ported as the same listener pattern used by
 *  ChatyPreferencesController.
 *
 *  Seams:
 *  - FlutterSecureStorage -> [SecureKeyValueStore] (existing encrypted store).
 *  - local_auth biometric -> [authenticateBiometric]. The Kotlin
 *    LocalLockService does not yet expose the BiometricPrompt bridge the Dart
 *    local_auth plugin provided; the integration layer must supply it
 *    (androidx.biometric, free) for biometric unlock to succeed. PIN unlock
 *    is fully functional without it.
 */
class HiddenVaultService(
    private val lockService: LocalLockService,
    private val secureStorage: SecureKeyValueStore,
    private val authenticateBiometric: suspend (reason: String) -> Boolean = { false },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val REGISTRY_KEY = "chaty.hidden_vault.locked_conversations"
        private const val DECOY_PIN_KEY = "chaty.hidden_vault.decoy_pin"
        const val AUTO_LOCK_DURATION_MS: Long = 2 * 60 * 1000 // Duration(minutes: 2)
    }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val mutationMutex = Mutex()
    private val authMutex = Mutex()
    @Volatile private var ownerGeneration = 0L
    @Volatile var registryError: String? = null
        private set
    @Volatile var registryLoading: Boolean = false
        private set
    val sessionOwner: Long get() = ownerGeneration
    suspend fun getPinLength(): Int = lockService.getPinLength()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    @Volatile
    private var accountNamespace: String = ""

    private fun registryKey(): String =
        if (accountNamespace.isEmpty()) REGISTRY_KEY else "$REGISTRY_KEY.$accountNamespace"

    private fun decoyPinKey(): String =
        if (accountNamespace.isEmpty()) DECOY_PIN_KEY else "$DECOY_PIN_KEY.$accountNamespace"

    private val _lockedConversationIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var _isUnlocked = false
    @Volatile private var _isDecoyMode = false
    @Volatile private var _lastActivityTime: Long? = null

    val isUnlocked: Boolean get() {
        if (_isUnlocked && _lastActivityTime?.let { now() - it >= AUTO_LOCK_DURATION_MS || now() < it } == true) lockVault()
        return _isUnlocked
    }
    val isDecoyMode: Boolean get() = _isDecoyMode
    val lastActivityTime: Long? get() = _lastActivityTime
    val autoLockDurationMs: Long get() = AUTO_LOCK_DURATION_MS
    val lockedConversationIds: Set<String>
        get() = if (_isDecoyMode) emptySet() else synchronized(_lockedConversationIds) { _lockedConversationIds.toSet() }

    /** Synchronously locks and clears in-memory vault state, then switches storage namespace and reloads. */
    suspend fun switchAccountNamespace(namespaceHash: String, migrateLegacyIfEmpty: Boolean = false) {
        ownerGeneration++
        val generation = ownerGeneration
        val clean = namespaceHash.trim()
        synchronized(_lockedConversationIds) {
            _lockedConversationIds.clear()
        }
        _isUnlocked = false
        _isDecoyMode = false
        _lastActivityTime = null
        accountNamespace = clean
        notifyListeners()
        if (migrateLegacyIfEmpty && clean.isNotEmpty()) {
            val targetRegistry = registryKey()
            val targetDecoy = decoyPinKey()
            try {
                if (secureStorage.read(targetRegistry) == null && generation == ownerGeneration) {
                    val legacyReg = secureStorage.read(REGISTRY_KEY)
                    if (!legacyReg.isNullOrEmpty() && generation == ownerGeneration) secureStorage.write(targetRegistry, legacyReg)
                }
                if (secureStorage.read(targetDecoy) == null && generation == ownerGeneration) {
                    val legacyDecoy = secureStorage.read(DECOY_PIN_KEY)
                    if (!legacyDecoy.isNullOrEmpty() && generation == ownerGeneration) secureStorage.write(targetDecoy, legacyDecoy)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == ownerGeneration) registryError = "Could not migrate protected-chat settings. Try again."
            }
        }
        if (generation == ownerGeneration) loadVaultRegistry()
    }

    /** Resets in-memory vault state synchronously without disk I/O (safe inside account switch barriers). */
    fun resetForAccountSwitchSync(namespaceHash: String) {
        ownerGeneration++
        registryError = null
        registryLoading = false
        synchronized(_lockedConversationIds) {
            _lockedConversationIds.clear()
        }
        _isUnlocked = false
        _isDecoyMode = false
        _lastActivityTime = null
        accountNamespace = namespaceHash.trim()
        notifyListeners()
    }

    /** Port of the Dart constructor's _loadVaultRegistry() kickoff. Call from
     *  a coroutine after construction. */
    suspend fun loadVaultRegistry() = mutationMutex.withLock {
        val generation = ownerGeneration
        val key = registryKey()
        registryLoading = true
        registryError = null
        notifyListeners()
        try {
            val raw = secureStorage.read(key)
            val ids = raw.orEmpty().split(',').filter { it.isNotEmpty() }.toSet()
            if (generation == ownerGeneration) synchronized(_lockedConversationIds) {
                _lockedConversationIds.clear()
                _lockedConversationIds.addAll(ids)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (generation == ownerGeneration) registryError = "Could not load protected chats. Try again."
        } finally {
            if (generation == ownerGeneration) {
                registryLoading = false
                notifyListeners()
            }
        }
    }

    private suspend fun updateMembership(conversationId: String, toggle: Boolean) {
        val generation = ownerGeneration
        mutationMutex.withLock {
        check(generation == ownerGeneration && isUnlocked && !isDecoyMode) { "Session changed. Authenticate again" }
        require(conversationId.isNotBlank() && ',' !in conversationId) { "Invalid conversation ID" }
        val key = registryKey()
        val next = synchronized(_lockedConversationIds) { _lockedConversationIds.toMutableSet() }
        if (!toggle || !next.add(conversationId)) next.remove(conversationId)
        try {
            // Persist before exposing success. A failed write leaves the previous snapshot intact.
            secureStorage.write(key, next.sorted().joinToString(","))
            if (generation != ownerGeneration) return@withLock
            synchronized(_lockedConversationIds) { _lockedConversationIds.clear(); _lockedConversationIds.addAll(next) }
            registryError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (generation == ownerGeneration) registryError = "Could not save protected chats. Try again."
            throw IllegalStateException("Could not save protected chats")
        } finally {
            if (generation == ownerGeneration) notifyListeners()
        }
        }
    }

    /** Idempotent removal, so repeat taps cannot re-lock a chat. Requires an authenticated vault. */
    suspend fun removeConversationLock(conversationId: String) {
        check(isUnlocked && !isDecoyMode) { "Unlock the vault first" }
        updateMembership(conversationId, toggle = false)
    }

    fun isConversationLocked(conversationId: String): Boolean {
        return synchronized(_lockedConversationIds) { _lockedConversationIds.contains(conversationId) }
    }

    suspend fun toggleConversationLock(conversationId: String) {
        check(isUnlocked && !isDecoyMode) { "Unlock the vault first" }
        updateMembership(conversationId, toggle = true)
    }

    /** Authentication results are scoped to the account generation that started them. */
    suspend fun unlockWithBiometrics(): Boolean {
        val generation = ownerGeneration
        return authMutex.withLock {
        if (generation != ownerGeneration) return@withLock false
        try {
            val success = authenticateBiometric("Authenticate to access Hidden Vault")
            if (success && generation == ownerGeneration) {
                _isUnlocked = true
                _isDecoyMode = false
                _lastActivityTime = now()
                notifyListeners()
                return@withLock true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Fail closed. No platform exception or credential is exposed.
        }
        false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val generation = ownerGeneration
        return authMutex.withLock {
        if (generation != ownerGeneration) return@withLock false
        if (pin.length != lockService.getPinLength() || pin.any { it !in '0'..'9' } || lockService.getRemainingCooldownSeconds() > 0)
            return@withLock false
        val decoyKey = decoyPinKey()
        // The historical default 0000 and no-credential acceptance path are intentionally disabled.
        val hasPin = lockService.hasCredential("pin")
        if (!hasPin || generation != ownerGeneration) return@withLock false
        val valid = lockService.verifyCredential("pin", pin)
        if (generation != ownerGeneration) return@withLock false
        if (valid) {
            _isUnlocked = true
            _isDecoyMode = false
        } else {
            val stored = secureStorage.read("$decoyKey.hash")
            // Preserve explicitly enrolled legacy decoys until a migration is approved.
            // There is no implicit 0000 credential. New enrollment always writes a salted verifier.
            val legacy = if (stored == null) secureStorage.read(decoyKey) else null
            val accepted = if (stored != null) verifyDecoy(pin, stored)
                else legacy != null && MessageDigest.isEqual(legacy.toByteArray(Charsets.UTF_8), pin.toByteArray(Charsets.UTF_8))
            if (!accepted || generation != ownerGeneration) return@withLock false
            _isUnlocked = true
            _isDecoyMode = true
        }
        _lastActivityTime = now()
        notifyListeners()
        true
        }
    }

    private suspend fun decoyHash(pin: String, salt: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, 120_000, 256)
        try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword(); chars.fill('\u0000') }
    }

    private suspend fun verifyDecoy(pin: String, encoded: String): Boolean {
        return try {
            val parts = encoded.split(':')
            if (parts.size != 2) return false
            val decoder = java.util.Base64.getDecoder()
            val salt = decoder.decode(parts[0])
            try {
                val expected = decoder.decode(parts[1])
                try {
                    val candidate = decoyHash(pin, salt)
                    try { MessageDigest.isEqual(expected, candidate) }
                    finally { candidate.fill(0) }
                } finally { expected.fill(0) }
            } finally { salt.fill(0) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    /** Sets a new custom vault PIN with PBKDF2-HMAC-SHA256 salt & stretch. */
    suspend fun setVaultPin(newPin: String) {
        val generation = ownerGeneration
        check(isUnlocked && !isDecoyMode) { "Authenticate before changing the PIN" }
        val stored = secureStorage.read("${decoyPinKey()}.hash")
        val legacy = if (stored == null) secureStorage.read(decoyPinKey()) else null
        require((stored == null || !verifyDecoy(newPin, stored)) && legacy != newPin) { "Choose a different PIN from the decoy PIN" }
        check(generation == ownerGeneration && isUnlocked && !isDecoyMode) { "Session changed. Authenticate again" }
        com.example.chat.ui.settings.security.CredentialStrength.pinError(newPin)?.let { throw IllegalArgumentException(it) }
        lockService.setCredential("pin", newPin, newPin.length)
    }

    /** Sets a decoy mode PIN. */
    suspend fun setDecoyPin(decoyPin: String) {
        val generation = ownerGeneration
        check(isUnlocked && !isDecoyMode) { "Authenticate before changing the decoy PIN" }
        require(decoyPin.length == lockService.getPinLength() && decoyPin.all { it in '0'..'9' }) { "Use the configured PIN length" }
        require(!lockService.matchesCredentialForEnrollment("pin", decoyPin)) { "Choose a different PIN from the real PIN" }
        val key = decoyPinKey()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = decoyHash(decoyPin, salt)
        try {
            val encoder = java.util.Base64.getEncoder()
            check(generation == ownerGeneration && isUnlocked && !isDecoyMode) { "Session changed. Authenticate again" }
            secureStorage.write("$key.hash", "${encoder.encodeToString(salt)}:${encoder.encodeToString(hash)}")
            secureStorage.delete(key) // Remove a legacy plaintext value only after the hash is saved.
        } finally { salt.fill(0); hash.fill(0) }
    }

    // Unauthenticated session bypass disabled. Original implementation retained for review:
    // fun forceUnlockForSession() {
    //     _isUnlocked = true
    //     _isDecoyMode = false
    //     _lastActivityTime = now()
    //     notifyListeners()
    // }

    fun lockVault() {
        ownerGeneration++ // Invalidate any prompt or PIN attempt that began before the lock.
        _isUnlocked = false
        _isDecoyMode = false
        _lastActivityTime = null
        notifyListeners()
    }

    /** Auto-lock observer called on app lifecycle backgrounding. */
    fun onAppPaused() {
        lockVault()
    }

    fun recordActivity() {
        _lastActivityTime = now()
    }
}
