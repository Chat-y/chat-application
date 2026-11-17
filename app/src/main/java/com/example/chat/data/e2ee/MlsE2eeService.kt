package com.example.chat.data.e2ee

import android.util.Base64
import com.example.chat.core.crypto.MlsCiphersuite
import com.example.chat.core.crypto.MlsE2eeException
import com.example.chat.core.crypto.MlsEngine
import com.example.chat.core.crypto.MlsGroupConfig
import com.example.chat.core.crypto.MlsMembershipPendingException
import com.example.chat.core.crypto.MlsNativeStatics
import com.example.chat.core.crypto.MlsStorageInitializationException
import com.example.chat.core.crypto.FlexibleCommitOptions
import com.example.chat.core.crypto.ProcessedMessageType
import com.example.chat.core.crypto.MlsEncryptedPayload
import com.example.chat.data.messaging.SupabaseRpc
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Secure-storage seam (flutter_secure_storage -> EncryptedSharedPreferences
 *  or Keystore-backed implementation). */
interface SecureKeyValueStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
    suspend fun delete(key: String)

    /** Atomic on Android implementations; compatibility default for test/platform adapters. */
    suspend fun writeAll(values: Map<String, String>) {
        values.forEach { (key, value) -> write(key, value) }
    }

    /** Atomic on Android implementations; compatibility default for test/platform adapters. */
    suspend fun deleteAll(keys: Set<String>) {
        keys.forEach { key -> delete(key) }
    }

    /** Prefix-scoped deletion so removing one account never wipes another account's secrets. */
    suspend fun deletePrefix(prefix: String) {
        // Test/in-memory stores override when key enumeration is supported.
    }
}

/**
 * Production client-side RFC 9420 Messaging Layer Security transport.
 * Exact Kotlin port of lib/data/services/mls_e2ee_service.dart (api branch).
 *
 * Security boundaries:
 * - MLS private keys and ratchet state never leave the device.
 * - Ratchet state is persisted only in OpenMLS' SQLCipher database.
 * - The SQLCipher key and signature private key are held in secure storage.
 * - Supabase receives only public device material, one-time KeyPackages,
 *   Welcome/Commit protocol messages and opaque application ciphertexts.
 * - Membership drift fails closed. Chaty never silently falls back to
 *   plaintext when an MLS conversation cannot be synchronized.
 */
class MlsE2eeService(
    private val rpc: SupabaseRpc,
    private val secureStorage: SecureKeyValueStore,
    private val currentAuthUserId: () -> String?,
    private val dbPathForUser: (String) -> String,
    private val notifyChanged: () -> Unit = {},
    private val accountNamespaceProvider: (() -> String?)? = null,
    private val assertNamespaceActive: suspend (String) -> Unit = {},
) {
    companion object {
        const val protocolSuite = "mls-rfc9420-v1"
        const val serverCiphersuite = "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519"
        const val targetKeyPackagePool = 10
        const val replenishBelow = 5

        private val ciphersuite =
            MlsCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519

        private fun zero(bytes: ByteArray?) {
            bytes?.fill(0)
        }
    }

    private val keyTrust = DeviceKeyTrustStore(secureStorage)
    val deviceKeyReviews get() = keyTrust.reviews
    val deviceKeyFingerprints get() = keyTrust.observed

    suspend fun approveDeviceKey(review: DeviceKeyTrustStore.Review, ownerIsCurrent: () -> Boolean) = initializationMutex.withLock {
        check(ownerIsCurrent()) { "Account changed during device-key approval." }
        val ns = accountNamespaceProvider?.invoke() ?: currentAuthUserId()
        check(ns == review.namespace) { "Account changed during device-key review." }
        assertNamespaceActive(review.namespace)
        check(ownerIsCurrent()) { "Account changed before device-key approval." }
        keyTrust.approve(review, ownerIsCurrent)
        check(ownerIsCurrent()) { "Account changed after device-key approval." }
    }

    private val random = SecureRandom()
    private val conversationGates = HashMap<String, Mutex>()

    private var engine: MlsEngine? = null
    private var signerBytes: ByteArray? = null
    private var signerPublicKey: ByteArray? = null
    private var userId: String? = null
    private var activeNamespace: String? = null
    private var deviceId: String? = null
    private var credentialIdentity: String? = null
    private var initializationMutex = Mutex()

    val isReady: Boolean
        get() = engine != null && signerBytes != null && signerPublicKey != null &&
            userId != null && deviceId != null
    val currentDeviceId: String? get() = deviceId
    val currentUserId: String? get() = userId

    private val groupConfig: MlsGroupConfig
        get() {
            val defaults = MlsGroupConfig.defaultConfig(ciphersuite)
            return MlsGroupConfig(
                ciphersuite = ciphersuite,
                wireFormatPolicy = com.example.chat.core.crypto.MlsWireFormatPolicy.ciphertext,
                useRatchetTreeExtension = true,
                maxPastEpochs = maxOf(defaults.maxPastEpochs, 5),
                paddingSize = defaults.paddingSize,
                senderRatchetMaxOutOfOrder = defaults.senderRatchetMaxOutOfOrder,
                senderRatchetMaxForwardDistance = defaults.senderRatchetMaxForwardDistance,
                numberOfResumptionPsks = defaults.numberOfResumptionPsks,
            )
        }

    suspend fun initializeForCurrentSession() = initializationMutex.withLock {
        initializeLocked()
    }

    private suspend fun initializeLocked() {
        val user = currentAuthUserId()
        if (user == null) {
            closeLocked()
            return
        }
        val ns = accountNamespaceProvider?.invoke()?.takeIf { it.isNotBlank() } ?: user
        assertNamespaceActive(ns)
        if (isReady && userId == user && activeNamespace == ns) return

        try {
            closeLocked()
            val uid = user
            val prefix = "chaty.mls.$ns"
            val did = loadOrCreateDeviceId(prefix)
            val dbKey = loadOrCreateSecret("$prefix.db_key.v1", 32)
            val signerMaterial = loadOrCreateSigner(prefix)

            val safeNs = ns.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val baseDir = dbPathForUser(safeNs)
            runCatching { java.io.File(baseDir).mkdirs() }
            val dbPath = "$baseDir/chaty_mls_$safeNs.db"
            val eng: MlsEngine = try {
                MlsEngine.create(dbPath = dbPath, encryptionKey = dbKey)
            } catch (error: Throwable) {
                throw MlsStorageInitializationException.fromCause(error)
            }

            val signer = MlsNativeStatics.require().serializeSigner(
                ciphersuite = ciphersuite,
                privateKey = signerMaterial.first,
                publicKey = signerMaterial.second,
            )

            engine = eng
            signerBytes = signer
            signerPublicKey = signerMaterial.second.copyOf()
            userId = uid
            activeNamespace = ns
            deviceId = did
            credentialIdentity = "$uid:$did"

            zero(dbKey)
            zero(signerMaterial.first)

            registerAndReplenishDevice()
            notifyChanged()
        } catch (e: Throwable) {
            closeLocked()
            throw e
        }
    }

    /** Retries device registration and key package replenishment if MLS is not
     *  currently ready or missing active server-side enrollment. */
    suspend fun retryDeviceEnrollment() = initializationMutex.withLock {
        val user = currentAuthUserId() ?: return@withLock
        val ns = accountNamespaceProvider?.invoke()?.takeIf { it.isNotBlank() } ?: user
        assertNamespaceActive(ns)
        if (!isReady || userId != user || activeNamespace != ns) {
            initializeLocked()
        }
        if (isReady && userId == user && activeNamespace == ns) {
            try {
                registerAndReplenishDevice()
                notifyChanged()
            } catch (_: Throwable) {
            }
        }
    }

    /** Ensures the local device is a synchronized active member of the server's
     *  MLS group for [conversationId]. Never creates a plaintext fallback;
     *  throws [MlsMembershipPendingException] when another device must publish
     *  a membership commit first. */
    suspend fun ensureConversationReady(conversationId: String): MlsConversationState = initializationMutex.withLock {
        initializeLocked()
        gate(conversationId).withLock { ensureConversationReadyLocked(conversationId) }
    }

    suspend fun assertStoredCipherReady(conversationId: String, cipher: MlsEncryptedPayload) = initializationMutex.withLock {
        initializeLocked()
        gate(conversationId).withLock {
            val state = ensureConversationReadyLocked(conversationId)
            if (state.group?.groupId != cipher.groupId || state.group?.epoch != cipher.epoch ||
                requireEngine().groupEpoch(Base64.decode(cipher.groupId, Base64.DEFAULT)).toInt() != cipher.epoch)
                throw com.example.chat.core.crypto.StoredCipherMembershipDrift("Stored ciphertext epoch changed.")
        }
    }

    suspend fun encryptPayload(conversationId: String, payload: Map<String, Any?>): MlsEncryptedPayload = initializationMutex.withLock {
        initializeLocked()
        gate(conversationId).withLock {
            val state = ensureConversationReadyLocked(conversationId)
            val group = state.group
                ?: throw MlsE2eeException("MLS group is not initialized.")
            val eng = requireEngine()
            val groupId = Base64.decode(group.groupId, Base64.DEFAULT)
            val localEpoch = eng.groupEpoch(groupIdBytes = groupId)
            if (localEpoch.toInt() != group.epoch) {
                throw MlsE2eeException(
                    "MLS epoch mismatch: local=${localEpoch.toInt()} server=${group.epoch}."
                )
            }

            val envelope = JSONObject()
                .put("version", 1)
                .put("conversation_id", conversationId)
                .put("payload", JSONObject(payload))
                .toString()
            val encrypted = eng.createMessage(
                groupIdBytes = groupId,
                signerBytes = requireSigner(),
                message = envelope.toByteArray(Charsets.UTF_8),
                aad = "chaty:mls:rfc9420:v1:$conversationId".toByteArray(Charsets.UTF_8),
            )
            MlsEncryptedPayload(
                groupId = group.groupId,
                epoch = localEpoch.toInt(),
                ciphertext = Base64.encodeToString(encrypted.ciphertext, Base64.DEFAULT),
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun decryptPayload(conversationId: String, ciphertext: String): Map<String, Any?> = initializationMutex.withLock {
        initializeLocked()
        gate(conversationId).withLock {
            val state = ensureConversationReadyLocked(conversationId, sendReady = false)
            val group = state.group
                ?: throw MlsE2eeException("MLS group is not initialized.")
            val result = requireEngine().processMessage(
                groupIdBytes = Base64.decode(group.groupId, Base64.DEFAULT),
                messageBytes = Base64.decode(ciphertext, Base64.DEFAULT),
            )
            val bytes = result.applicationMessage
                ?: throw MlsE2eeException("MLS message did not contain application plaintext.")
            val decoded = JSONObject(String(bytes, Charsets.UTF_8))
            if (decoded.optInt("version", -1) != 1 ||
                decoded.optString("conversation_id") != conversationId ||
                !decoded.has("payload")
            ) {
                throw MlsE2eeException("Encrypted payload failed Chaty context validation.")
            }
            // OpenMLS returns the credential authenticated in this message's epoch.
            // Never infer the actor from a current-epoch leaf index.
            val identity = String(result.authenticatedSenderIdentity, Charsets.UTF_8)
            val separator = identity.indexOf(':')
            check(separator > 0 && separator < identity.lastIndex) { "MLS sender identity is malformed." }
            jsonObjectToMap(decoded.getJSONObject("payload")) + mapOf(
                "_authenticated_sender_user_id" to identity.substring(0, separator),
                "_authenticated_sender_device_id" to identity.substring(separator + 1),
            )
        }
    }

    suspend fun encryptEditedPayload(conversationId: String, payload: Map<String, Any?>): MlsEncryptedPayload =
        encryptPayload(conversationId = conversationId, payload = payload)

    /** Derives an MLS exporter secret for encrypted attachment key material.
     *  Phase 2 uses this method to AES-GCM encrypt bytes before Storage upload. */
    suspend fun exportAttachmentSecret(conversationId: String, attachmentId: String): ByteArray = initializationMutex.withLock {
        initializeLocked()
        gate(conversationId).withLock {
            val state = ensureConversationReadyLocked(conversationId)
            val group = state.group
                ?: throw MlsE2eeException("MLS group is not initialized.")
            requireEngine().exportSecret(
                groupIdBytes = Base64.decode(group.groupId, Base64.DEFAULT),
                label = "chaty-attachment-v1",
                context = "$conversationId:$attachmentId".toByteArray(Charsets.UTF_8),
                keyLength = 32,
            )
        }
    }

    suspend fun close() = initializationMutex.withLock { closeLocked() }

    private suspend fun closeLocked() {
        val eng = engine
        // Retain the handle on failure so a later cleanup can retry shutdown.
        eng?.close()
        engine = null
        zero(signerBytes)
        zero(signerPublicKey)
        signerBytes = null
        signerPublicKey = null
        userId = null
        activeNamespace = null
        deviceId = null
        credentialIdentity = null
        conversationGates.clear()
        notifyChanged()
    }

    /** Irreversibly removes every local trace of [uid]'s MLS identity (for legacy userId or
     *  account namespace): closes the engine, deletes the SQLCipher DB + sidecars, and erases
     *  only that namespace's derived secrets from secure storage. */
    suspend fun purgeLocalIdentityForUser(uid: String) = purgeLocalIdentityForNamespace(uid)

// Previous unscoped shutdown retained:     suspend fun purgeLocalIdentityForNamespace(namespace: String) {
// Previous unscoped shutdown retained:         val prefix = "chaty.mls.$namespace"
// Previous unscoped shutdown retained:         close()
// Previous unscoped shutdown retained: 
// Previous unscoped shutdown retained:         val safeNs = namespace.replace(Regex("[^A-Za-z0-9_-]"), "_")
// Previous unscoped shutdown retained:         val basePath = "${dbPathForUser(safeNs)}/chaty_mls_$safeNs"
// Previous unscoped shutdown retained:         val candidates = listOf(
// Previous unscoped shutdown retained:             "$basePath.db", "$basePath.db-wal", "$basePath.db-shm",
// Previous unscoped shutdown retained:             basePath, "$basePath-wal", "$basePath-shm",
// Previous unscoped shutdown retained:         )
// Previous unscoped shutdown retained:         for (path in candidates) {
// Previous unscoped shutdown retained:             try {
// Previous unscoped shutdown retained:                 val file = java.io.File(path)
// Previous unscoped shutdown retained:                 if (file.exists()) file.delete()
// Previous unscoped shutdown retained:             } catch (_: Throwable) {
// Previous unscoped shutdown retained:             }
// Previous unscoped shutdown retained:         }
// Previous unscoped shutdown retained: 
// Previous unscoped shutdown retained:         for (suffix in listOf(".db_key.v1", ".device_id.v1", ".signer_private.v1", ".signer_public.v1")) {
// Previous unscoped shutdown retained:             try {
// Previous unscoped shutdown retained:                 secureStorage.delete("$prefix$suffix")
// Previous unscoped shutdown retained:             } catch (_: Throwable) {
// Previous unscoped shutdown retained:             }
// Previous unscoped shutdown retained:         }
// Previous unscoped shutdown retained:     }
// Previous unscoped shutdown retained: 
    suspend fun purgeLocalIdentityForNamespace(namespace: String) = initializationMutex.withLock {
        require(namespace.isNotBlank())
        // Never close a different account's live engine during recovery.
        if (activeNamespace == namespace) closeLocked()
        val safeNs = namespace.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val dir = java.io.File(dbPathForUser(safeNs))
        val base = java.io.File(dir, "chaty_mls_$safeNs").path
        for (path in listOf("$base.db", "$base.db-wal", "$base.db-shm", base, "$base-wal", "$base-shm")) {
            val file = java.io.File(path)
            check(!file.exists() || file.delete()) { "MLS identity file cleanup failed." }
        }
        secureStorage.deletePrefix("chaty.mls.$namespace.")
    }

    private suspend fun ensureConversationReadyLocked(conversationId: String, sendReady: Boolean = true): MlsConversationState {
        if (conversationId.trim().isEmpty()) {
            throw MlsE2eeException("Conversation id is required.")
        }
        val did = requireDeviceId()
        var state = fetchState(conversationId, afterEpoch = 0)

        state = if (state.group == null) {
            createInitialGroup(conversationId)
        } else {
            joinOrCatchUp(conversationId, state)
        }

        state = reconcileMembership(conversationId, state)
        // Catch-up may temporarily retain historically pinned leaves. Send/export
        // readiness is stricter and uses fresh active devices plus the actual tree.
        state = fetchState(conversationId, afterEpoch = state.group?.epoch ?: 0)
        val group = state.group
            ?: throw MlsE2eeException("MLS group initialization failed.")

        val groupId = Base64.decode(group.groupId, Base64.DEFAULT)
        val active = try {
            requireEngine().groupIsActive(groupIdBytes = groupId)
        } catch (_: Throwable) {
            false
        }
        if (!active) {
            throw MlsE2eeException("MLS group is not active for device $did.")
        }

        val epoch = requireEngine().groupEpoch(groupIdBytes = groupId)
        if (epoch.toInt() != group.epoch) {
            throw MlsE2eeException(
                "MLS synchronization incomplete: local=${epoch.toInt()} server=${group.epoch}."
            )
        }
        if (!sendReady) return state // Reading history does not require exact current active membership.
        val activeApproved = state.serverDevices.associateBy { it.key }
        val actualIdentities = HashSet<String>()
        for (member in requireEngine().groupMembers(groupId)) {
            val identity = String(MlsNativeStatics.require().credentialIdentity(member.credential), Charsets.UTF_8)
            check(actualIdentities.add(identity)) { "Duplicate native MLS identity. Sending is blocked." }
            val current = activeApproved[identity]
                ?: throw com.example.chat.core.crypto.StoredCipherMembershipDrift("A removed or revoked device remains in the actual MLS group. Sending and attachment key export are blocked until membership is repaired.")
            val currentKey = Base64.decode(current.signaturePublicKey, Base64.DEFAULT)
            check(currentKey.contentEquals(member.signatureKey)) { "Native MLS key differs from the current active device. Sending is blocked." }
            keyTrust.assertMember(checkNotNull(activeNamespace), identity, member.signatureKey)
        }
        if (actualIdentities != activeApproved.keys) throw com.example.chat.core.crypto.StoredCipherMembershipDrift(
            "Active devices are missing from the actual MLS group. Sending is blocked until membership is repaired.")
        return state
    }

    private suspend fun createInitialGroup(conversationId: String): MlsConversationState {
        fetchState(conversationId, afterEpoch = 0) // rejects known-unapproved device keys BEFORE consuming packages
        val claimed = claimPackages(conversationId)
        val packages = claimed
        // Validate the actual signed KeyPackage leaf against the approved pin
        // before createGroup/addMembers can alter any local membership.
        for (entry in packages) assertKeyPackagePinned(entry)
        val eng = requireEngine()
        val created = eng.createGroup(
            config = groupConfig,
            signerBytes = requireSigner(),
            credentialIdentity = requireCredentialIdentity().toByteArray(Charsets.UTF_8),
            signerPublicKey = requireSignerPublicKey(),
        )
        val groupId = created.groupId

        var welcome: ByteArray? = null
        if (packages.isNotEmpty()) {
            val added = eng.addMembers(
                groupIdBytes = groupId,
                signerBytes = requireSigner(),
                keyPackagesBytes = packages.map { Base64.decode(it.keyPackage, Base64.DEFAULT) },
            )
            welcome = added.welcome
        }
        val epoch = eng.groupEpoch(groupIdBytes = groupId)

        try {
            rpc.rpc(
                "publish_mls_group_v1",
                mapOf(
                    "p_conversation_id" to conversationId,
                    "p_sender_device_id" to requireDeviceId(),
                    "p_group_id" to Base64.encodeToString(groupId, Base64.DEFAULT),
                    "p_ciphersuite" to serverCiphersuite,
                    "p_epoch" to epoch.toInt(),
                    "p_welcome_payload" to (welcome?.let { Base64.encodeToString(it, Base64.DEFAULT) } ?: ""),
                    "p_recipient_packages" to packages.map { it.serverIdentityJson },
                ),
            )
        } catch (error: Throwable) {
            // Another device can legitimately win the first-group race. The
            // losing local group is cryptographically unrelated and must be
            // discarded.
            try {
                eng.deleteGroup(groupIdBytes = groupId)
            } catch (_: Throwable) {
            }
            val server = fetchState(conversationId, afterEpoch = 0)
            if (server.group == null) throw error
            return joinOrCatchUp(conversationId, server)
        }

        return fetchState(conversationId, afterEpoch = epoch.toInt())
    }

    private suspend fun joinOrCatchUp(
        conversationId: String,
        stateIn: MlsConversationState,
    ): MlsConversationState {
        var state = stateIn
        val group = state.group!!
        val groupId = Base64.decode(group.groupId, Base64.DEFAULT)
        val eng = requireEngine()
        var localGroupExists: Boolean
        var localEpoch = 0
        try {
            localGroupExists = eng.groupIsActive(groupIdBytes = groupId)
            if (localGroupExists) {
                localEpoch = eng.groupEpoch(groupIdBytes = groupId).toInt()
            }
        } catch (_: Throwable) {
            localGroupExists = false
        }

        val pinIdentities = state.serverDevices.map { it.key.toByteArray(Charsets.UTF_8) }
        val pinKeys = state.serverDevices.map { Base64.decode(it.signaturePublicKey, Base64.DEFAULT) }
        for ((index, device) in state.serverDevices.withIndex())
            keyTrust.assertMember(checkNotNull(activeNamespace), device.key, pinKeys[index])
        if (!localGroupExists) {
            val welcome = state.welcome
                ?: throw MlsE2eeException(
                    "This device has no recoverable MLS Welcome. Re-link the device to restore encrypted chat access."
                )
            val joined = eng.joinGroupFromWelcomePinned(
                config = groupConfig,
                welcomeBytes = Base64.decode(welcome.welcomePayload, Base64.DEFAULT),
                signerBytes = requireSigner(), pinIdentities = pinIdentities, pinKeys = pinKeys,
                expectedGroupId = groupId, expectedEpoch = welcome.epoch.toLong(),
            )
            if (Base64.encodeToString(joined.groupId, Base64.DEFAULT) != group.groupId) {
                throw MlsE2eeException("MLS Welcome group id mismatch.")
            }
            rpc.rpc(
                "ack_mls_welcome_v1",
                mapOf(
                    "p_welcome_id" to welcome.id,
                    "p_device_id" to requireDeviceId(),
                ),
            )
            localEpoch = eng.groupEpoch(groupIdBytes = groupId).toInt()
            registerAndReplenishDevice()
            state = fetchState(conversationId, afterEpoch = localEpoch)
        } else {
            state = fetchState(conversationId, afterEpoch = localEpoch)
        }

        for (control in state.controls) {
            if (control.epoch <= localEpoch) continue
            if (control.epoch != localEpoch + 1) {
                throw MlsE2eeException(
                    "Missing MLS membership commit before epoch ${control.epoch}."
                )
            }
            // Refresh approvals for this control step. Include still-pinned current
            // members so an intermediate removal can be applied after server revocation.
            val stepState = fetchState(conversationId, afterEpoch = localEpoch)
            val stepIdentities = mutableListOf<ByteArray>()
            val stepKeys = mutableListOf<ByteArray>()
            for (member in eng.groupMembers(groupId)) {
                val identity = String(MlsNativeStatics.require().credentialIdentity(member.credential), Charsets.UTF_8)
                keyTrust.assertCatchUpMember(checkNotNull(activeNamespace), identity, member.signatureKey)
                stepIdentities.add(identity.toByteArray(Charsets.UTF_8)); stepKeys.add(member.signatureKey)
            }
            for (device in stepState.serverDevices) {
                val key = Base64.decode(device.signaturePublicKey, Base64.DEFAULT)
                keyTrust.assertMember(checkNotNull(activeNamespace), device.key, key)
                val bytes = device.key.toByteArray(Charsets.UTF_8)
                if (stepIdentities.indices.none { stepIdentities[it].contentEquals(bytes) && stepKeys[it].contentEquals(key) }) {
                    stepIdentities.add(bytes); stepKeys.add(key)
                }
            }
            eng.processMembershipCommitPinned(groupIdBytes = groupId,
                messageBytes = Base64.decode(control.commitPayload, Base64.DEFAULT),
                pinIdentities = stepIdentities, pinKeys = stepKeys,
                expectedEpoch = control.epoch.toLong())
            localEpoch = eng.groupEpoch(groupIdBytes = groupId).toInt()
            if (localEpoch != control.epoch) {
                throw MlsE2eeException("MLS commit application failed at epoch ${control.epoch}.")
            }
        }

        if (localEpoch != group.epoch) {
            state = fetchState(conversationId, afterEpoch = localEpoch)
            if (state.controls.isNotEmpty()) {
                return joinOrCatchUp(conversationId, state)
            }
        }
        return fetchState(conversationId, afterEpoch = localEpoch)
    }

    private suspend fun reconcileMembership(
        conversationId: String,
        state: MlsConversationState,
    ): MlsConversationState {
        val group = state.group ?: return state

        val serverActive = state.serverDevices.associateBy { it.key }
        val groupActive = state.groupDevices.filter { it.removedEpoch == null }.associateBy { it.key }
        val additions = serverActive.keys.filter { !groupActive.containsKey(it) }.toSet()
        val removals = groupActive.keys.filter { !serverActive.containsKey(it) }.toSet()
        if (additions.isEmpty() && removals.isEmpty()) return state

        val eligibleCoordinators = groupActive.keys.filter { serverActive.containsKey(it) }.sorted()
        val myKey = "${requireUserId()}:${requireDeviceId()}"
        if (eligibleCoordinators.isEmpty() || eligibleCoordinators.first() != myKey) {
            throw MlsMembershipPendingException(
                "Encrypted membership is updating on another trusted device."
            )
        }

        if (additions.isNotEmpty()) keyTrust.inspect(checkNotNull(activeNamespace), state.serverDevices)
        val claimed = if (additions.isEmpty()) emptyList() else claimPackages(conversationId)
        val claimedByKey = claimed.associateBy { it.key }
        if (!additions.all { claimedByKey.containsKey(it) }) {
            throw MlsE2eeException(
                "MLS membership update is missing a KeyPackage for a new device."
            )
        }

        val groupIdBytes = Base64.decode(group.groupId, Base64.DEFAULT)
        val members = requireEngine().groupMembers(groupIdBytes = groupIdBytes)
        val removalIndices = mutableListOf<Int>()
        for (member in members) {
            val identity = String(
                MlsNativeStatics.require().credentialIdentity(member.credential),
                Charsets.UTF_8,
            )
            if (removals.contains(identity)) removalIndices.add(member.index)
        }
        if (removalIndices.size != removals.size) {
            throw MlsE2eeException("MLS local membership does not match the server removal set.")
        }

        val orderedAdditions = additions.sorted()
        for (key in orderedAdditions) assertKeyPackagePinned(claimedByKey.getValue(key))
        val commit = requireEngine().flexibleCommit(
            groupIdBytes = groupIdBytes,
            signerBytes = requireSigner(),
            options = FlexibleCommitOptions(
                addKeyPackages = orderedAdditions.map {
                    Base64.decode(claimedByKey.getValue(it).keyPackage, Base64.DEFAULT)
                },
                removeIndices = removalIndices.toIntArray(),
                forceSelfUpdate = false,
                consumePendingProposals = false,
                createGroupInfo = true,
                useRatchetTreeExtension = true,
            ),
        )
        requireEngine().mergePendingCommit(groupIdBytes = groupIdBytes)
        val newEpoch = requireEngine().groupEpoch(groupIdBytes = groupIdBytes).toInt()

        try {
            rpc.rpc(
                "publish_mls_membership_update_v1",
                mapOf(
                    "p_conversation_id" to conversationId,
                    "p_sender_device_id" to requireDeviceId(),
                    "p_group_id" to group.groupId,
                    "p_new_epoch" to newEpoch,
                    "p_commit_payload" to Base64.encodeToString(commit.commit, Base64.DEFAULT),
                    "p_welcome_payload" to (commit.welcome?.let {
                        Base64.encodeToString(it, Base64.DEFAULT)
                    } ?: ""),
                    "p_additions" to orderedAdditions.map { claimedByKey.getValue(it).serverIdentityJson },
                    "p_removals" to removals.map { key ->
                        val (uid, did) = splitDeviceKey(key)
                        mapOf("user_id" to uid, "device_id" to did)
                    },
                ),
            )
        } catch (error: Throwable) {
            // The local engine already advanced. Failing closed is safer than
            // trying to synthesize a rollback of MLS ratchet state.
            throw MlsE2eeException(
                "Server rejected the MLS membership commit after local advancement. Re-open this account on the device to resynchronize. ($error)"
            )
        }

        return fetchState(conversationId, afterEpoch = newEpoch)
    }

    private suspend fun assertKeyPackagePinned(entry: MlsKeyPackageDescriptor) {
        val actual = requireEngine().inspectKeyPackagePin(Base64.decode(entry.keyPackage, Base64.DEFAULT))
        val identity = String(MlsNativeStatics.require().credentialIdentity(actual.credential), Charsets.UTF_8)
        check(identity == entry.key) { "KeyPackage credential does not match its claimed device." }
        keyTrust.assertMember(checkNotNull(activeNamespace), identity, actual.signatureKey)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun claimPackages(conversationId: String): List<MlsKeyPackageDescriptor> {
        val raw = rpc.rpc(
            "claim_mls_conversation_key_packages_v1",
            mapOf(
                "p_conversation_id" to conversationId,
                "p_sender_device_id" to requireDeviceId(),
            ),
        )
        val map = requireMlsMap(raw)
        val rows = map["packages"]
        if (rows !is List<*>) {
            throw MlsE2eeException("Invalid MLS KeyPackage response.")
        }
        return rows.filterIsInstance<Map<String, Any?>>().map { MlsKeyPackageDescriptor.fromJson(it) }
    }

    private suspend fun fetchState(conversationId: String, afterEpoch: Int): MlsConversationState {
        val raw = rpc.rpc(
            "get_mls_conversation_state_v1",
            mapOf(
                "p_conversation_id" to conversationId,
                "p_device_id" to requireDeviceId(),
                "p_after_epoch" to maxOf(afterEpoch, 0),
            ),
        )
        val state = MlsConversationState.fromJson(requireMlsMap(raw))
        keyTrust.inspect(checkNotNull(activeNamespace), state.serverDevices)
        return state
    }

    private suspend fun registerAndReplenishDevice() {
        val base = registerDevice(emptyList())
        val available = (base["available_key_packages"] as? Number)?.toInt()
            ?: base["available_key_packages"]?.toString()?.toIntOrNull() ?: 0
        if (available >= replenishBelow) return

        val needed = maxOf(targetKeyPackagePool - available, 0)
        val packages = mutableListOf<Map<String, Any?>>()
        for (index in 0 until needed) {
            val keyPackage = requireEngine().createKeyPackage(
                ciphersuite = ciphersuite,
                signerBytes = requireSigner(),
                credentialIdentity = requireCredentialIdentity().toByteArray(Charsets.UTF_8),
                signerPublicKey = requireSignerPublicKey(),
            )
            packages.add(
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "key_package" to Base64.encodeToString(keyPackage.keyPackageBytes, Base64.DEFAULT),
                    "expires_at" to java.time.Instant.now()
                        .plus(java.time.Duration.ofDays(30)).toString(),
                )
            )
        }
        if (packages.isNotEmpty()) registerDevice(packages)
    }

    private suspend fun registerDevice(packages: List<Map<String, Any?>>): Map<String, Any?> {
        val raw = rpc.rpc(
            "register_mls_device_v1",
            mapOf(
                "p_device_id" to requireDeviceId(),
                "p_device_name" to "Chaty android MLS device",
                "p_platform" to "android",
                "p_ciphersuite" to serverCiphersuite,
                "p_credential_identity" to requireCredentialIdentity(),
                "p_signature_public_key" to Base64.encodeToString(requireSignerPublicKey(), Base64.DEFAULT),
                "p_key_packages" to packages,
            ),
        )
        return requireMlsMap(raw)
    }

    private suspend fun loadOrCreateDeviceId(prefix: String): String {
        val key = "$prefix.device_id.v1"
        var value = secureStorage.read(key)
        if (value == null || value.length < 8) {
            value = "mls_${UUID.randomUUID()}"
            secureStorage.write(key, value)
        }
        return value
    }

    private suspend fun loadOrCreateSecret(key: String, length: Int): ByteArray {
        val stored = secureStorage.read(key)
        if (!stored.isNullOrEmpty()) {
            val bytes = Base64.decode(stored, Base64.DEFAULT)
            if (bytes.size != length) {
                throw MlsE2eeException("Invalid secure MLS secret length for $key.")
            }
            return bytes
        }
        val bytes = ByteArray(length).also { random.nextBytes(it) }
        secureStorage.write(key, Base64.encodeToString(bytes, Base64.DEFAULT))
        return bytes
    }

    private suspend fun loadOrCreateSigner(prefix: String): Pair<ByteArray, ByteArray> {
        val privateKeyName = "$prefix.signer_private.v1"
        val publicKeyName = "$prefix.signer_public.v1"
        val privateStored = secureStorage.read(privateKeyName)
        val publicStored = secureStorage.read(publicKeyName)
        if (privateStored != null && publicStored != null) {
            return Pair(
                Base64.decode(privateStored, Base64.DEFAULT),
                Base64.decode(publicStored, Base64.DEFAULT),
            )
        }
        if (privateStored != null || publicStored != null) {
            throw MlsE2eeException("Incomplete MLS signing identity in secure storage.")
        }

        val keyPair = MlsNativeStatics.require().signatureKeyPairGenerate(ciphersuite)
        secureStorage.write(privateKeyName, Base64.encodeToString(keyPair.first, Base64.DEFAULT))
        secureStorage.write(publicKeyName, Base64.encodeToString(keyPair.second, Base64.DEFAULT))
        return keyPair
    }

    private fun requireEngine(): MlsEngine =
        engine ?: throw MlsE2eeException("MLS is not initialized.")

    private fun requireSigner(): ByteArray =
        signerBytes ?: throw MlsE2eeException("MLS signer is unavailable.")

    private fun requireSignerPublicKey(): ByteArray =
        signerPublicKey ?: throw MlsE2eeException("MLS signer public key is unavailable.")

    private fun requireUserId(): String =
        userId ?: throw MlsE2eeException("MLS user is unavailable.")

    private fun requireDeviceId(): String =
        deviceId ?: throw MlsE2eeException("MLS device is unavailable.")

    private fun requireCredentialIdentity(): String =
        credentialIdentity ?: throw MlsE2eeException("MLS credential is unavailable.")

    private fun gate(conversationId: String): Mutex =
        conversationGates.getOrPut(conversationId) { Mutex() }

        private fun splitDeviceKey(key: String): Pair<String, String> {
            val index = key.indexOf(':')
            if (index <= 0 || index >= key.length - 1) {
                throw MlsE2eeException("Invalid MLS device key.")
            }
            return Pair(key.substring(0, index), key.substring(index + 1))
        }

        @Suppress("UNCHECKED_CAST")
        internal fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.get(k)
                out[k] = when (v) {
                    is JSONObject -> jsonObjectToMap(v)
                    is org.json.JSONArray -> (0 until v.length()).map { i ->
                        when (val e = v.get(i)) {
                            is JSONObject -> jsonObjectToMap(e)
                            JSONObject.NULL -> null
                            else -> e
                        }
                    }
                    JSONObject.NULL -> null
                    else -> v
                }
            }
            return out
        }
}

// Previous implementation retained inactive (unified v4):
//             close()
//             close()
//             close()
//     suspend fun retryDeviceEnrollment() {
//         val user = currentAuthUserId() ?: return
//             initializeForCurrentSession()
//     suspend fun ensureConversationReady(conversationId: String): MlsConversationState {
//         initializeForCurrentSession()
//         return gate(conversationId).withLock { ensureConversationReadyLocked(conversationId) }
//     suspend fun encryptPayload(conversationId: String, payload: Map<String, Any?>): MlsEncryptedPayload {
//         initializeForCurrentSession()
//         return gate(conversationId).withLock {
//     suspend fun decryptPayload(conversationId: String, ciphertext: String): Map<String, Any?> {
//         initializeForCurrentSession()
//         return gate(conversationId).withLock {
//     suspend fun exportAttachmentSecret(conversationId: String, attachmentId: String): ByteArray {
//         initializeForCurrentSession()
//         return gate(conversationId).withLock {
//     suspend fun close() {
//         if (eng != null) {
//             try {
//                 eng.close()
//             } catch (_: Throwable) {
//             }
//         }
//     suspend fun purgeLocalIdentityForNamespace(namespace: String) {
//         val prefix = "chaty.mls.$namespace"
//         close()
// 
//         val basePath = "${dbPathForUser(safeNs)}/chaty_mls_$safeNs"
//         val candidates = listOf(
//             "$basePath.db", "$basePath.db-wal", "$basePath.db-shm",
//             basePath, "$basePath-wal", "$basePath-shm",
//         )
//         for (path in candidates) {
//             try {
//                 val file = java.io.File(path)
//                 if (file.exists()) file.delete()
//             } catch (_: Throwable) {
//             }
// 
//         for (suffix in listOf(".db_key.v1", ".device_id.v1", ".signer_private.v1", ".signer_public.v1")) {
//             try {
//                 secureStorage.delete("$prefix$suffix")
//             } catch (_: Throwable) {
//             }
//         }

// Previous implementation retained inactive (v5 review):
//             jsonObjectToMap(decoded.getJSONObject("payload"))
//         return MlsConversationState.fromJson(requireMlsMap(raw))
