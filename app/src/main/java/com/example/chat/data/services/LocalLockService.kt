package com.example.chat.data.services

import com.example.chat.core.preferences.LockCredentialStore
import com.example.chat.data.e2ee.SecureKeyValueStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.chat.ui.settings.security.CredentialStrength
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Biometric / device-credential seam (Dart: local_auth LocalAuthentication).
 *  The Android adapter (BiometricPrompt) is wired by the integration owner;
 *  platform errors return false/empty, while coroutine cancellation propagates like the
 *  Dart try/catch around PlatformException. */
interface LockBiometrics {
    suspend fun canCheckBiometrics(): Boolean
    suspend fun availableBiometrics(): List<String>
    suspend fun isDeviceSupported(): Boolean
    /** True only when Android device PIN/pattern/password is configured and available. */
    suspend fun isDeviceCredentialSupported(): Boolean = isDeviceSupported()
    /** biometricOnly=true, stickyAuth, sensitiveTransaction. */
    suspend fun authenticateBiometricOnly(reason: String): Boolean
    /** biometricOnly=false (device PIN/pattern/password allowed). */
    suspend fun authenticateWithDeviceCredential(reason: String): Boolean
}

/**
 * Exact port of lib/data/services/local_lock_service.dart (full file).
//  * PBKDF2-HMAC-SHA256 (120,000 rounds), 16-byte random salt per credential,
 * PBKDF2-HMAC-SHA256 (600,000 rounds for new/upgraded verifiers, legacy 120,000 supported),
 * 16-byte random salt per credential,
 * 256-bit derived key, credentials only in platform secure storage,
 * constant-time comparison, failed-attempt cooldown (5 -> 30s, 10 -> 60s,
 * 15 -> 5min), secret phrase, biometric/device-credential via [biometrics].
 * Replaces the earlier credential-only subset (same class and members).
 */
class LocalLockService(
    private val secureStorage: SecureKeyValueStore,
    private val biometrics: LockBiometrics? = null,
    // Optional only for existing deterministic JVM callers. Production supplies AndroidLockCooldownClock.
    private val cooldownClock: LockCooldownClock? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) : LockCredentialStore {

    private val attemptMutex = Mutex()
    private val cooldownMutex = Mutex()
    @Volatile
    private var accountNamespace: String = ""
    @Volatile private var ownerGeneration = 0L

    fun setAccountNamespace(namespaceHash: String) {
        ownerGeneration++
        accountNamespace = namespaceHash.trim()
    }

    private fun scopedPrefix(): String =
        if (accountNamespace.isEmpty()) PREFIX else "$PREFIX.$accountNamespace"

    private fun pinLengthKey() = "${scopedPrefix()}.pin_length"
    private fun secretPhraseHashKey() = "${scopedPrefix()}.secret_phrase.hash"
    private fun secretPhraseSaltKey() = "${scopedPrefix()}.secret_phrase.salt"
    private fun failedAttemptsKey() = "${scopedPrefix()}.failed_attempts"
    private fun lockoutUntilKey() = "${scopedPrefix()}.lockout_until"
    private fun phraseAttemptsKey() = "${scopedPrefix()}.secret_phrase.failed_attempts"
    private fun phraseLockoutKey() = "${scopedPrefix()}.secret_phrase.lockout_until"
    private fun scopedHashKey(method: String) = "${scopedPrefix()}.${normalizeMethod(method)}.hash"
    private fun scopedSaltKey(method: String) = "${scopedPrefix()}.${normalizeMethod(method)}.salt"

    companion object {
        private const val PREFIX = "chaty.local_lock.v2"
        private const val PIN_LENGTH_KEY = "$PREFIX.pin_length"
        private const val SECRET_PHRASE_HASH_KEY = "$PREFIX.secret_phrase.hash"
        private const val SECRET_PHRASE_SALT_KEY = "$PREFIX.secret_phrase.salt"
        private const val FAILED_ATTEMPTS_KEY = "$PREFIX.failed_attempts"
        private const val LOCKOUT_UNTIL_KEY = "$PREFIX.lockout_until"
//         private const val ITERATIONS = 120_000
        private const val LEGACY_ITERATIONS = 120_000
        private const val CURRENT_ITERATIONS = 600_000
        private const val VERIFIER_VERSION = "v2"
        private data class StoredVerifier(val iterations: Int, val hash: ByteArray)
        private fun encodeVerifier(hash: ByteArray): String = "$VERIFIER_VERSION:$CURRENT_ITERATIONS:${b64.encodeToString(hash)}"
        private fun decodeVerifier(encoded: String): StoredVerifier {
            if (':' !in encoded) return StoredVerifier(LEGACY_ITERATIONS, b64dec.decode(encoded))
            val parts = encoded.split(':')
            require(parts.size == 3 && parts[0] == VERIFIER_VERSION) { "Unsupported credential format" }
            val iterations = parts[1].toIntOrNull()
            require(iterations == LEGACY_ITERATIONS || iterations == CURRENT_ITERATIONS) { "Unsupported credential work factor" }
            return StoredVerifier(iterations, b64dec.decode(parts[2]))
        }

        fun normalizeSecretPhrase(phrase: String): String =
            phrase.trim().replace(Regex("\\s+"), " ").lowercase()
        private const val BITS = 256
        private const val SALT_LENGTH = 16

        private fun normalizeMethod(method: String): String = when (method.lowercase()) {
            "pin" -> "pin"
            "pattern" -> "pattern"
            "password" -> "password"
            else -> throw IllegalArgumentException("Unsupported local credential method: $method")
        }

        private fun hashKey(method: String) = "$PREFIX.${normalizeMethod(method)}.hash"
        private fun saltKey(method: String) = "$PREFIX.${normalizeMethod(method)}.salt"

        internal fun isValidPattern(pattern: String): Boolean {
            val values = pattern.split('-').filter { it.isNotEmpty() }
            if (values.size < 4) return false
            val parsed = values.map { it.toIntOrNull() }
            if (parsed.any { it == null || it < 0 || it > 8 }) return false
            return parsed.toSet().size == parsed.size
        }

//         private suspend fun pbkdf2(secret: String, salt: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        private suspend fun pbkdf2(secret: String, salt: ByteArray, iterations: Int = LEGACY_ITERATIONS): ByteArray = withContext(Dispatchers.Default) {
            val chars = secret.toCharArray()
//             val spec = PBEKeySpec(chars, salt, ITERATIONS, BITS)
            val spec = PBEKeySpec(chars, salt, iterations, BITS)
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
                chars.fill('\u0000')
            }
        }

        private val b64 = java.util.Base64.getEncoder()
        private val b64dec = java.util.Base64.getDecoder()
    }

    /** Enrollment must distinguish absent data from storage failure or a partial verifier.
     * Never translate a corrupt/inaccessible existing credential into permission to replace it.
     */
    suspend fun hasCredentialForSetup(method: String): Boolean {
        val generation = ownerGeneration
        val prefix = scopedPrefix()
        val normalized = normalizeMethod(method)
        val hash = secureStorage.read("$prefix.$normalized.hash")
        val salt = secureStorage.read("$prefix.$normalized.salt")
        check(generation == ownerGeneration) { "Account changed. Reopen setup." }
        if (hash == null && salt == null) return false
        check(!hash.isNullOrBlank() && !salt.isNullOrBlank()) { "Credential storage is incomplete." }
//         val decodedHash = b64dec.decode(hash)
        val decodedHash = decodeVerifier(hash).hash
        try {
            val decodedSalt = b64dec.decode(salt)
            try {
                check(decodedHash.size == BITS / 8 && decodedSalt.size == SALT_LENGTH) { "Credential storage is invalid." }
            } finally { decodedSalt.fill(0) }
        } finally { decodedHash.fill(0) }
        return true
    }

    override suspend fun hasCredential(method: String): Boolean = try {
        !secureStorage.read(scopedHashKey(method)).isNullOrEmpty() &&
            !secureStorage.read(scopedSaltKey(method)).isNullOrEmpty()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    suspend fun setPinLength(length: Int) {
        val safeLength = if (length == 6) 6 else 4
        secureStorage.write(pinLengthKey(), safeLength.toString())
    }

    suspend fun getPinLength(): Int =
        secureStorage.read(pinLengthKey())?.toIntOrNull()?.let { if (it == 6) 6 else 4 } ?: 4

    /** Capture the requesting owner before waiting for another proof/upgrade to finish. */
    private suspend fun <T> mutateForOwner(requestedOwner: Long, action: suspend () -> T): T = attemptMutex.withLock {
        check(requestedOwner == ownerGeneration) { "Account changed. Retry from the current account." }
        action()
    }

    /** Fail-safe overwrite rule lives in the caller (migration checks
     *  hasCredential first); this mirrors Dart setCredential exactly,
     *  including validation failures throwing so invalid legacy secrets are
     *  dropped by the migrator's catch. */
//     override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {
    override suspend fun setCredential(method: String, secret: String, pinLength: Int?) = mutateForOwner(ownerGeneration) {
        val generation = ownerGeneration
        val prefix = scopedPrefix()
        val normalized = normalizeMethod(method)
        require(secret.isNotEmpty()) { "Credential must not be empty." }
        if (normalized == "pin") {
            val expectedLength = if (pinLength == 6) 6 else 4
            require(Regex("^\\d+$").matches(secret) && secret.length == expectedLength) {
                "PIN must contain exactly $expectedLength digits."
            }
        }
        if (normalized == "pattern" && !isValidPattern(secret)) {
            throw IllegalArgumentException("Pattern must connect at least 4 unique points.")
        }
        // Keep legacy credential migrations valid. New enrollment strength is enforced by setup UI.
        if (normalized == "password" && secret.length < 6) throw IllegalArgumentException("Password must contain at least 6 characters.")

        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
//         val derived = pbkdf2(secret, salt)
        val derived = pbkdf2(secret, salt, CURRENT_ITERATIONS)
        val values = linkedMapOf(
            "$prefix.$normalized.salt" to b64.encodeToString(salt),
//             "$prefix.$normalized.hash" to b64.encodeToString(derived),
            "$prefix.$normalized.hash" to encodeVerifier(derived),
        )
        if (normalized == "pin") values["$prefix.pin_length"] = (if (pinLength == 6) 6 else 4).toString()
        try {
            check(generation == ownerGeneration) { "Account changed. Retry from the current account." }
            secureStorage.writeAll(values)
        } finally {
            salt.fill(0)
            derived.fill(0)
        }
    }

    /** Constant-time verification with failed-attempt cooldown. */
    suspend fun verifyCredential(method: String, secret: String): Boolean = verifyCredentialInternal(method, secret, recordAttempt = true)

    /** For authenticated enrollment collision checks only. Never an unlock or public authentication path. */
    internal suspend fun matchesCredentialForEnrollment(method: String, secret: String): Boolean =
        verifyCredentialInternal(method, secret, recordAttempt = false)

    private suspend fun verifyCredentialInternal(method: String, secret: String, recordAttempt: Boolean): Boolean = attemptMutex.withLock {
        if (recordAttempt && getRemainingCooldownSeconds() > 0) return@withLock false
        val generation = ownerGeneration
        val prefix = scopedPrefix()
        val normalized = normalizeMethod(method)
        try {
            val encodedHash = secureStorage.read("$prefix.$normalized.hash")
            val encodedSalt = secureStorage.read("$prefix.$normalized.salt")
            if (encodedHash.isNullOrBlank() || encodedSalt.isNullOrBlank()) return@withLock false
            val salt = b64dec.decode(encodedSalt)
            val isMatch = try {
//                 val expected = b64dec.decode(encodedHash)
                val verifier = decodeVerifier(encodedHash)
                try {
//                     val candidate = pbkdf2(secret, salt)
//                     try { MessageDigest.isEqual(expected, candidate) }
//                     finally { candidate.fill(0) }
//                 } finally { expected.fill(0) }
                    val candidate = pbkdf2(secret, salt, verifier.iterations)
                    val matches = try { MessageDigest.isEqual(verifier.hash, candidate) }
                        finally { candidate.fill(0) }
                    if (matches && recordAttempt && verifier.iterations < CURRENT_ITERATIONS)
                        upgradeVerifierAfterProof(secret, salt, "$prefix.$normalized.hash", generation)
                    matches
                } finally { verifier.hash.fill(0) }
            } finally { salt.fill(0) }
            if (generation != ownerGeneration) return@withLock false
            if (recordAttempt) {
                if (isMatch) resetFailedAttemptsLocked() else recordFailedAttemptLocked()
            }
            isMatch
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    /** Called only after proof, under attemptMutex, which also guards enrollment/clearing.
     * The count and hash share one field: a failed upgrade cannot leave mismatched metadata.
     * An unavailable best-effort upgrade never turns a verified legacy credential into a lockout. */
    private suspend fun upgradeVerifierAfterProof(secret: String, salt: ByteArray, key: String, generation: Long) {
        val upgraded = pbkdf2(secret, salt, CURRENT_ITERATIONS)
        try {
            if (generation == ownerGeneration) {
                try { secureStorage.write(key, encodeVerifier(upgraded)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Retain the valid legacy verifier and retry next proof. */ }
            }
        } finally { upgraded.fill(0) }
    }

    suspend fun hasSecretPhrase(): Boolean = try {
        !secureStorage.read(secretPhraseHashKey()).isNullOrEmpty() &&
            !secureStorage.read(secretPhraseSaltKey()).isNullOrEmpty()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

//     suspend fun setSecretPhrase(rawPhrase: String) {
    suspend fun setSecretPhrase(rawPhrase: String) = mutateForOwner(ownerGeneration) {
        val generation = ownerGeneration
        val prefix = scopedPrefix()
        val normalized = normalizeSecretPhrase(rawPhrase)
        CredentialStrength.phraseError(normalized)?.let { throw IllegalArgumentException(it) }
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
//         val bytes = pbkdf2(normalized, salt)
        val bytes = pbkdf2(normalized, salt, CURRENT_ITERATIONS)
        try {
            check(generation == ownerGeneration) { "Account changed. Retry from the current account." }
            secureStorage.writeAll(
                mapOf(
                    "$prefix.secret_phrase.salt" to b64.encodeToString(salt),
//                     "$prefix.secret_phrase.hash" to b64.encodeToString(bytes),
                    "$prefix.secret_phrase.hash" to encodeVerifier(bytes),
                ),
            )
        } finally {
            salt.fill(0)
            bytes.fill(0)
        }
    }

    suspend fun verifySecretPhrase(rawQuery: String): Boolean = attemptMutex.withLock {
        val generation = ownerGeneration
        val prefix = scopedPrefix()
        val normalized = normalizeSecretPhrase(rawQuery)
        if (normalized.isEmpty() || getSecretPhraseRemainingCooldownSeconds() > 0) return@withLock false
        try {
            // Approved removal: verification uses the salted hash for every candidate.
            // // New enrollments can skip PBKDF2 for impossible matches. Legacy verifiers with no
            // // length metadata still verify normally; never infer a different normalization.
            // val enrolledLength = secureStorage.read("$prefix.secret_phrase.length")?.toIntOrNull()
            // if (enrolledLength != null && enrolledLength != normalized.length) {
            // if (generation == ownerGeneration) recordFailedAttemptLocked(phrase = true)
            // return@withLock false
            // }
            val encodedHash = secureStorage.read("$prefix.secret_phrase.hash")
            val encodedSalt = secureStorage.read("$prefix.secret_phrase.salt")
            if (encodedHash.isNullOrBlank() || encodedSalt.isNullOrBlank()) return@withLock false
            val salt = b64dec.decode(encodedSalt)
            val matches = try {
//                 val expected = b64dec.decode(encodedHash)
                val verifier = decodeVerifier(encodedHash)
                try {
//                     val candidate = pbkdf2(normalized, salt)
//                     try { MessageDigest.isEqual(expected, candidate) }
//                     finally { candidate.fill(0) }
//                 } finally { expected.fill(0) }
                    val candidate = pbkdf2(normalized, salt, verifier.iterations)
                    val matches = try { MessageDigest.isEqual(verifier.hash, candidate) }
                        finally { candidate.fill(0) }
                    if (matches && verifier.iterations < CURRENT_ITERATIONS)
                        upgradeVerifierAfterProof(normalized, salt, "$prefix.secret_phrase.hash", generation)
                    matches
                } finally { verifier.hash.fill(0) }
            } finally { salt.fill(0) }
            if (generation != ownerGeneration) return@withLock false
            // Discovery has its own throttle. Ordinary searches must never lock App Lock.
            // if (matches) secureStorage.deleteAll(setOf(phraseAttemptsKey(), phraseLockoutKey()))
            if (matches) cooldownMutex.withLock { check(generation == ownerGeneration); secureStorage.deleteAll(setOf(phraseAttemptsKey(), phraseLockoutKey())) }
            else recordFailedAttemptLocked(phrase = true)
            matches
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    // suspend fun clearSecretPhrase() {
    suspend fun clearSecretPhrase() {
        val generation = ownerGeneration
        cooldownMutex.withLock {
            check(generation == ownerGeneration) { "Account changed during phrase reset." }
            secureStorage.deleteAll(setOf(secretPhraseHashKey(), secretPhraseSaltKey(), phraseAttemptsKey(), phraseLockoutKey()))
        }
    }

    suspend fun getFailedAttempts(): Int = try {
        secureStorage.read(failedAttemptsKey())?.toIntOrNull() ?: 0
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        0
    }

//     suspend fun getRemainingCooldownSeconds(): Int = cooldownSeconds(lockoutUntilKey())
//     suspend fun getSecretPhraseRemainingCooldownSeconds(): Int = cooldownSeconds(phraseLockoutKey())
    suspend fun getRemainingCooldownSeconds(): Int = cooldownSeconds(lockoutUntilKey(), failedAttemptsKey())
    suspend fun getSecretPhraseRemainingCooldownSeconds(): Int = cooldownSeconds(phraseLockoutKey(), phraseAttemptsKey())

//     private suspend fun cooldownSeconds(key: String): Int = try {
//         val lockoutTime = secureStorage.read(key)?.toLongOrNull() ?: 0L
//         val current = now()
//         if (lockoutTime > current) Math.ceil((lockoutTime - current) / 1000.0).toInt() else 0
//     } catch (cancelled: CancellationException) {
//         throw cancelled
//     } catch (_: Exception) {
//         0
    private fun cooldownInstant(): LockCooldownInstant =
        (cooldownClock?.read() ?: LockCooldownInstant(now(), 0)).also {
            check(it.elapsedMillis >= 0 && it.bootCount >= 0) { "Cooldown clock unavailable." }
        }

    private fun cooldownDuration(attempts: Int): Long = when {
        attempts >= 15 -> 300_000L
        attempts >= 10 -> 60_000L
        attempts >= 5 -> 30_000L
        else -> 0L
    }

    private fun encodedDeadline(instant: LockCooldownInstant, duration: Long): String {
        check(instant.elapsedMillis <= Long.MAX_VALUE - duration)
        return "v3:${instant.bootCount}:${instant.elapsedMillis + duration}:$duration"
    }

    private suspend fun cooldownSeconds(key: String, attemptsKey: String, generation: Long = ownerGeneration): Int = cooldownMutex.withLock {
        try {
            check(generation == ownerGeneration)
            val instant = cooldownInstant()
            val stored = secureStorage.read(key)
            check(generation == ownerGeneration)
            if (stored == null) return@withLock 0
            val fields = stored.split(':')
            val deadline: Long
            if (fields.size == 4 && fields[0] == "v3") {
                val boot = fields[1].toLongOrNull() ?: error("Invalid cooldown boot.")
                val until = fields[2].toLongOrNull() ?: error("Invalid cooldown deadline.")
                val duration = fields[3].toLongOrNull() ?: error("Invalid cooldown duration.")
                check(boot >= 0 && until >= 0 && duration in setOf(30_000L, 60_000L, 300_000L))
                if (boot != instant.bootCount || until - instant.elapsedMillis > duration) {
                    // A reboot/monotonic rollback cannot consume the waiting period.
                    check(generation == ownerGeneration)
                    secureStorage.write(key, encodedDeadline(instant, duration))
                    return@withLock (duration / 1000).toInt()
                }
                deadline = until
            } else {
                // Legacy wall deadlines cannot establish whether the clock was changed.
                // Restart one bounded tier even when the old civil deadline appears expired.
                check(stored.toLongOrNull() != null) { "Invalid legacy cooldown." }
                val duration = cooldownDuration(secureStorage.read(attemptsKey)?.toIntOrNull() ?: 15)
                    .takeIf { it > 0 } ?: 300_000L
                check(generation == ownerGeneration)
                secureStorage.write(key, encodedDeadline(instant, duration))
                return@withLock (duration / 1000).toInt()
            }
            if (generation != ownerGeneration) return@withLock 300
            val remaining = (deadline - instant.elapsedMillis).coerceAtLeast(0L)
            ((remaining + 999) / 1000).coerceAtMost(300).toInt()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Read, clock or migration-write failure must never become permission to guess.
            300
        }
    }

    // suspend fun recordFailedAttempt() = attemptMutex.withLock { recordFailedAttemptLocked() }
    suspend fun recordFailedAttempt() {
        val generation = ownerGeneration
        attemptMutex.withLock { recordFailedAttemptLocked(generation = generation) }
    }

//     private suspend fun recordFailedAttemptLocked(phrase: Boolean = false) {
    private suspend fun recordFailedAttemptLocked(phrase: Boolean = false, generation: Long = ownerGeneration) = cooldownMutex.withLock {
        check(generation == ownerGeneration) { "Account changed during cooldown update." }
        val attemptsKey = if (phrase) phraseAttemptsKey() else failedAttemptsKey()
        val deadlineKey = if (phrase) phraseLockoutKey() else lockoutUntilKey()
//         val attempts = (secureStorage.read(attemptsKey)?.toIntOrNull() ?: 0) + 1
        val attempts = ((secureStorage.read(attemptsKey)?.toIntOrNull() ?: 0).coerceIn(0, Int.MAX_VALUE - 1)) + 1
        val values = linkedMapOf(attemptsKey to attempts.toString())
//         val lockoutMs = when {
//             attempts >= 15 -> 5 * 60_000L
//             attempts >= 10 -> 60_000L
//             attempts >= 5 -> 30_000L
//             else -> null
//         }
//         if (lockoutMs != null) values[deadlineKey] = (now() + lockoutMs).toString()
        val duration = cooldownDuration(attempts)
        if (duration > 0) values[deadlineKey] = encodedDeadline(cooldownInstant(), duration)
        check(generation == ownerGeneration) { "Account changed during cooldown update." }
        secureStorage.writeAll(values)
    }

    // suspend fun resetFailedAttempts() = attemptMutex.withLock { resetFailedAttemptsLocked() }
    suspend fun resetFailedAttempts() {
        val generation = ownerGeneration
        attemptMutex.withLock { resetFailedAttemptsLocked(generation) }
    }

//     private suspend fun resetFailedAttemptsLocked() {
    private suspend fun resetFailedAttemptsLocked(generation: Long = ownerGeneration) = cooldownMutex.withLock {
        check(generation == ownerGeneration) { "Account changed during cooldown reset." }
        secureStorage.deleteAll(setOf(failedAttemptsKey(), lockoutUntilKey()))
    }

    suspend fun canUseBiometrics(): Boolean = try {
        val b = biometrics
        b != null && b.canCheckBiometrics() && b.availableBiometrics().isNotEmpty()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    suspend fun availableBiometrics(): List<String> = try {
        biometrics?.availableBiometrics() ?: emptyList()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun authenticateBiometric(reason: String = "Authenticate to unlock Chaty"): Boolean = try {
        if (!canUseBiometrics()) false else biometrics!!.authenticateBiometricOnly(reason)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) {
        false
    }

    suspend fun canUseDeviceCredential(): Boolean = try {
        biometrics?.isDeviceCredentialSupported() == true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    suspend fun authenticateDeviceCredential(reason: String = "Use your device lock to unlock Chaty"): Boolean = try {
        val b = biometrics
        if (!canUseDeviceCredential() || b == null) false else b.authenticateWithDeviceCredential(reason)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) {
        false
    }

//     suspend fun clearCredential(method: String) {
    suspend fun clearCredential(method: String) = mutateForOwner(ownerGeneration) {
        val normalized = normalizeMethod(method)
        secureStorage.deleteAll(setOf(scopedHashKey(normalized), scopedSaltKey(normalized)))
    }

    suspend fun clearAll() {
        clearCredential("pin")
        clearCredential("pattern")
        clearCredential("password")
        clearSecretPhrase()
        resetFailedAttempts()
        secureStorage.delete(pinLengthKey())
    }
}
