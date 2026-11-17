package com.example.chat.data.e2ee

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** TOFU is first-seen, never identity-verified. Later new/changed signing keys
 * fail closed until an explicit approval of the exact displayed fingerprint.
 * Namespace and credential identity are included in local storage identity.
 */
class DeviceKeyTrustStore(private val storage: SecureKeyValueStore) {
    data class Review(val namespace: String, val deviceKey: String, val fingerprint: String, val previous: String?)
    data class Observation(val namespace: String, val deviceKey: String, val fingerprint: String, val label: String)
    private val _observed = MutableStateFlow<List<Observation>>(emptyList())
    val observed: StateFlow<List<Observation>> = _observed
    private val mutex = Mutex()
    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews
    private fun storageKey(ns: String) = "chaty.mls.$ns.device_key_pins.v1"
    private suspend fun read(ns: String): JSONObject = storage.read(storageKey(ns))?.let { JSONObject(it) } ?: JSONObject()
    fun fingerprint(device: MlsDeviceDescriptor): String {
        require(device.userId.isNotBlank() && device.deviceId.isNotBlank() && device.credentialIdentity == device.key)
        val key = Base64.getDecoder().decode(device.signaturePublicKey.replace("\n", "").replace("\r", ""))
        require(key.size == 32) { "Invalid MLS signing public key." }
        return MessageDigest.getInstance("SHA-256").digest(
            "chaty-device-key-v1\u0000${device.key}\u0000".toByteArray() + key
        ).joinToString("") { "%02x".format(it) }.chunked(4).joinToString(" ")
    }
    suspend fun inspect(ns: String, devices: List<MlsDeviceDescriptor>) = mutex.withLock {
        require(ns.isNotBlank() && devices.isNotEmpty())
        require(devices.map { it.key }.distinct().size == devices.size) { "Duplicate MLS device identity." }
        val pins = read(ns)
        val firstSeen = pins.length() == 0
        val pending = mutableListOf<Review>()
        for (device in devices) {
            val value = fingerprint(device)
            val previous = pins.optJSONObject(device.key)?.optString("fingerprint")?.takeIf { it.isNotBlank() }
            if (previous == value) continue
            if (firstSeen) pins.put(device.key, JSONObject().put("fingerprint", value).put("label", "first-seen"))
            else pending.add(Review(ns, device.key, value, previous))
        }
        if (firstSeen) storage.write(storageKey(ns), pins.toString())
        _observed.value = _observed.value.filterNot { it.namespace == ns } + devices.map { device ->
            Observation(ns, device.key, fingerprint(device),
                if (pending.any { it.deviceKey == device.key }) "approval required" else pins.getJSONObject(device.key).optString("label", "first-seen"))
        }
        _reviews.value = _reviews.value.filterNot { it.namespace == ns } + pending
        check(pending.isEmpty()) { "Encryption paused: review new or changed device keys before sending." }
    }
    suspend fun approve(review: Review, ownerIsCurrent: () -> Boolean = { true }) = mutex.withLock {
        check(ownerIsCurrent()) { "Account changed before key approval." }
        check(_reviews.value.contains(review)) { "Device-key review changed. Reopen it." }
        val pins = read(review.namespace)
        val current = pins.optJSONObject(review.deviceKey)?.optString("fingerprint")?.takeIf { it.isNotBlank() }
        check(current == review.previous) { "Previous device key changed during review." }
        val original = pins.toString()
        val superseded = pins.optJSONObject(review.deviceKey)?.optJSONArray("superseded") ?: org.json.JSONArray()
        review.previous?.let { superseded.put(it) }
        pins.put(review.deviceKey, JSONObject().put("fingerprint", review.fingerprint).put("label", "explicitly-approved").put("superseded", superseded))
        try {
            storage.write(storageKey(review.namespace), pins.toString())
            check(ownerIsCurrent()) { "Account changed while saving key approval." }
        } catch (error: Throwable) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { storage.write(storageKey(review.namespace), original) }
            throw error
        }
        _reviews.value = _reviews.value - review
        _observed.value = _observed.value.map { if (it.namespace == review.namespace && it.deviceKey == review.deviceKey)
            it.copy(fingerprint = review.fingerprint, label = "explicitly-approved") else it }
    }
    /** Historical keys are catch-up only, never send-ready. Only a locally
     * reviewed replacement may supersede the old key; server claims cannot. */
    suspend fun assertCatchUpMember(ns: String, identity: String, oldKey: ByteArray) = mutex.withLock {
        val split = identity.indexOf(':'); require(split > 0 && split < identity.lastIndex)
        val actual = fingerprint(MlsDeviceDescriptor(identity.substring(0, split), identity.substring(split + 1), identity,
            Base64.getEncoder().encodeToString(oldKey)))
        val pin = read(ns).optJSONObject(identity) ?: error("Unpinned historical device. Catch-up blocked.")
        val old = pin.optJSONArray("superseded")
        check(pin.optString("fingerprint") == actual || (pin.optString("label") == "explicitly-approved" && old != null &&
            (0 until old.length()).any { old.optString(it) == actual })) {
            "Historical key was not explicitly superseded after review. Catch-up blocked."
        }
    }

    suspend fun assertMember(ns: String, identity: String, signatureKey: ByteArray) = mutex.withLock {
        val split = identity.indexOf(':'); require(split > 0 && split < identity.lastIndex)
        val actual = fingerprint(MlsDeviceDescriptor(identity.substring(0, split), identity.substring(split + 1), identity,
            Base64.getEncoder().encodeToString(signatureKey)))
        check(read(ns).optJSONObject(identity)?.optString("fingerprint") == actual) {
            "MLS member signing key does not match its pinned device key. Encryption is blocked."
        }
    }
}
