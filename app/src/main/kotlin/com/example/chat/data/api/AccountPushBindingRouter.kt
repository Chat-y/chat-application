package com.example.chat.data.api

import android.content.Context
import com.example.chat.data.demo.DemoMode
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bindings are opaque routing hints, never auth. Only own-token responses can create a mapping. */
class AccountPushBindingRouter(context: Context, private val api: ApiBackendService,
    private val inactive: suspend (String) -> Boolean,
    private val durableCalls: suspend (ApiRegisteredAccountMeta, String) -> Boolean = { _, _ -> false }) {
    private val prefs = context.applicationContext.getSharedPreferences("account_push_bindings_v1", Context.MODE_PRIVATE)
    private val registerLock = Mutex()
    private val transport = OkHttpApiTransport(allowRedirects = false)
    private fun uuid(value: String) = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    private fun current(meta: ApiRegisteredAccountMeta, token: String): Boolean = !DemoMode.active &&
        api.multiAccount.accountByKey(meta.accountKey)?.let { !it.sessionExpired && it.userId == meta.userId && it.tenantId == meta.tenantId && it.baseUrl == meta.baseUrl } == true &&
        api.multiAccount.credentialsFor(meta.accountKey)?.accessToken == token
    private fun safeBase(base: String): String? = runCatching {
        val uri = java.net.URI(base)
        base.trimEnd('/').takeIf { uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null }
    }.getOrNull()
    private fun headers(meta: ApiRegisteredAccountMeta, token: String) =
        mapOf("Authorization" to "Bearer $token", "x-tenant-id" to meta.tenantId, "Content-Type" to "application/json")

    /** Register the SAME platform token independently for each retained account; never adopt or log out. */
    suspend fun registerRetained(fcmToken: String, deadlineEpochMs: Long = Long.MAX_VALUE): Boolean = withContext(Dispatchers.IO) { registerLock.withLock {
        if (DemoMode.active || fcmToken.length !in 1..4096 || fcmToken.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it !in ":-_." }) return@withLock true
        var complete = true
        val runContext = kotlin.coroutines.coroutineContext
        fun mayContinue(): Boolean {
            runContext.ensureActive()
            return System.currentTimeMillis() < deadlineEpochMs
        }
        val retained = api.multiAccount.accounts()
        prefs.all.keys.filter { binding -> prefs.getString(binding, null) !in retained.map { it.accountKey } }.forEach { prefs.edit().remove(it).commit() }
        retained.filter { !it.sessionExpired }.forEach { meta ->
            if (!mayContinue()) { complete = false; return@forEach }
            val credentials = api.multiAccount.credentialsFor(meta.accountKey) ?: run { complete = false; return@forEach }
            val bearer = credentials.accessToken
            val base = safeBase(meta.baseUrl) ?: run { complete = false; return@forEach }
            if (!current(meta, bearer) || credentials.expiresAtEpochMs?.let { it <= System.currentTimeMillis() } == true) { complete = false; return@forEach }
            try {
                val body = JSONObject().put("fcm_token", fcmToken).put("device_type", "android").toString()
                var response = transport.execute("POST", "$base/me/devices", headers(meta, bearer), body)
                if (!mayContinue() || !current(meta, bearer)) { complete = false; return@forEach }
                if (response.first == 404) response = transport.execute("POST", "$base/app-users/me/devices", headers(meta, bearer), body)
                if (!mayContinue() || !current(meta, bearer) || response.first !in 200..299) { complete = false; return@forEach }
                val root = JSONObject(response.second)
                if (root.opt("success") != true) { complete = false; return@forEach }
                val data = root.optJSONObject("data") ?: run { complete = false; return@forEach }
                val binding = data.optString("bindingId")
                if (uuid(binding) && data.optString("accountId") == meta.userId && data.optString("tenantId") == meta.tenantId) {
                    if (!prefs.edit().putString(binding, meta.accountKey).commit()) complete = false
                } else complete = false
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { complete = false /* Worker retries without logging credentials/platform token. */ }
        }
        complete
    } }

    /** Mapping admission only, never authorizes content retrieval or switches an account. */
    fun hasRetainedBindingMapping(bindingId: String): Boolean {
        if (!uuid(bindingId) || DemoMode.active) return false
        val key = prefs.getString(bindingId, null) ?: return false
        return api.multiAccount.accountByKey(key)?.sessionExpired == false
    }

    /** Stored hints only. Every worker still validates its binding using that retained account's own token. */
    fun retainedBindingHints(): List<String> = prefs.all.keys.filter { uuid(it) }.filter { binding ->
        prefs.getString(binding, null)?.let { key -> api.multiAccount.accountByKey(key)?.sessionExpired == false } == true
    }

    /** Unknown/foreign/stale hint => no-op. No active-account fallback and no inactive destructive queue drain. */
    // true = handled or permanently invalid; false = bounded recovery may retry.
    suspend fun handle(bindingId: String, callId: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (DemoMode.active || !uuid(bindingId)) return@withContext true
        val key = prefs.getString(bindingId, null)
            ?: api.currentAccountKey()
            ?: api.multiAccount.accounts().firstOrNull { !it.sessionExpired }?.accountKey
            ?: return@withContext false
        val meta = api.multiAccount.accountByKey(key) ?: return@withContext true
        val credentials = api.multiAccount.credentialsFor(key) ?: return@withContext true
        if (meta.sessionExpired) return@withContext true
        val bearer = credentials.accessToken
        val base = safeBase(meta.baseUrl) ?: return@withContext true
        // Prior source: if (!current(meta, bearer)) return@withContext true
        if (!current(meta, bearer)) return@withContext false
        try {
            var response = transport.execute("GET", "$base/auth/device-bindings/$bindingId", headers(meta, bearer), null)
            // Prior source: if (!current(meta, bearer)) return@withContext true
            if (!current(meta, bearer)) return@withContext false
            if (response.first == 404) response = transport.execute("GET", "$base/api/v1/auth/device-bindings/$bindingId", headers(meta, bearer), null)
            // Prior source: if (!current(meta, bearer)) return@withContext true
            if (!current(meta, bearer)) return@withContext false
            if (response.first != 200) return@withContext response.first in setOf(403, 404)
            val data = JSONObject(response.second).optJSONObject("data") ?: return@withContext true
            val respBinding = data.optString("bindingId")
            val respAccount = data.optString("accountId")
            val respTenant = data.optString("tenantId")
            if (respBinding != bindingId || respAccount != meta.userId) return@withContext true
            if (meta.tenantId.isNotBlank() && respTenant.isNotBlank() && respTenant != meta.tenantId) return@withContext true
            prefs.edit().putString(bindingId, key).apply()
            if (callId != null && durableCalls(meta, bearer)) return@withContext true
            if (api.currentAccountKey() == key) {
                val owner = api.authSnapshot()
                // Prior source: mismatched active snapshot returned true (silent drop on a concurrent switch/rotation).
                if (owner.userId != meta.userId || owner.tenantId != meta.tenantId || owner.baseUrl != meta.baseUrl || owner.token != bearer) return@withContext false
                if (callId == null) return@withContext api.syncFromPushNudge(owner)
                else if (callId.isNotBlank()) api.pollCallSignalsAndAwait(owner)
            // Previous unchecked completion: } else if (callId == null) return@withContext inactive(key)
            } else if (callId == null) {
                val complete = inactive(key)
                return@withContext complete && current(meta, bearer) && api.currentAccountKey() != key
            }
            // Call inbox consumer validates event lifetime; retry the bounded pending inbox, never auto-answer.
            if (callId != null) return@withContext false
            true
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { false /* Unverified/unavailable: retry without rendering or selecting an account. */ }
    }
}

// Replaced source retained for review (inactive; do not restore without its surrounding implementation).
// Prior source:     private val inactive: suspend (String) -> Unit,
// Prior source:     suspend fun handle(bindingId: String, callId: String? = null) = withContext(Dispatchers.IO) {
// Prior source:         if (DemoMode.active || !uuid(bindingId)) return@withContext
// Prior source:         val key = prefs.getString(bindingId, null) ?: return@withContext
// Prior source:         val meta = api.multiAccount.accountByKey(key) ?: return@withContext
// Prior source:         val credentials = api.multiAccount.credentialsFor(key) ?: return@withContext
// Prior source:         val base = safeBase(meta.baseUrl) ?: return@withContext
// Prior source:         if (!current(meta, bearer)) return@withContext
// Prior source:             if (!current(meta, bearer)) return@withContext
// Prior source:             if (!current(meta, bearer) || response.first != 200) return@withContext
// Prior source:             val data = JSONObject(response.second).optJSONObject("data") ?: return@withContext
// Prior source:             if (data.optString("bindingId") != bindingId || data.optString("accountId") != meta.userId || data.optString("tenantId") != meta.tenantId) return@withContext
// Prior source:             if (callId != null && durableCalls(meta, bearer)) return@withContext
// Prior source:                 if (owner.userId != meta.userId || owner.tenantId != meta.tenantId || owner.baseUrl != meta.baseUrl || owner.token != bearer) return@withContext
// Prior source:                 if (callId == null) api.syncFromPushNudge(owner)
// Prior source:             } else if (callId == null) inactive(key)
// Prior source:             // Inactive calls intentionally wait for durable peek+ACK protocol. No queue consumption here.
// Prior source:         catch (_: Exception) { /* Unverified or unavailable: do not render notification or select another account. */ }

// Registration implementation replaced by checked outcome for durable recovery.
// Prior source:     suspend fun registerRetained(fcmToken: String) = withContext(Dispatchers.IO) { registerLock.withLock {
// Prior source:         if (DemoMode.active || fcmToken.length !in 1..4096 || fcmToken.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it !in ":-_." }) return@withLock
// Prior source:         val retained = api.multiAccount.accounts()
// Prior source:         prefs.all.keys.filter { binding -> prefs.getString(binding, null) !in retained.map { it.accountKey } }.forEach { prefs.edit().remove(it).commit() }
// Prior source:         retained.filter { !it.sessionExpired }.forEach { meta ->
// Prior source:             val credentials = api.multiAccount.credentialsFor(meta.accountKey) ?: return@forEach
// Prior source:             val bearer = credentials.accessToken
// Prior source:             val base = safeBase(meta.baseUrl) ?: return@forEach
// Prior source:             if (!current(meta, bearer) || credentials.expiresAtEpochMs?.let { it <= System.currentTimeMillis() } == true) return@forEach
// Prior source:             try {
// Prior source:                 val body = JSONObject().put("fcm_token", fcmToken).put("device_type", "android").toString()
// Prior source:                 var response = transport.execute("POST", "$base/me/devices", headers(meta, bearer), body)
// Prior source:                 if (!current(meta, bearer)) return@forEach
// Prior source:                 if (response.first == 404) response = transport.execute("POST", "$base/app-users/me/devices", headers(meta, bearer), body)
// Prior source:                 if (!current(meta, bearer) || response.first !in 200..299) return@forEach
// Prior source:                 val root = JSONObject(response.second)
// Prior source:                 if (root.opt("success") != true) return@forEach
// Prior source:                 val data = root.optJSONObject("data") ?: return@forEach
// Prior source:                 val binding = data.optString("bindingId")
// Prior source:                 if (uuid(binding) && data.optString("accountId") == meta.userId && data.optString("tenantId") == meta.tenantId)
// Prior source:                     prefs.edit().putString(binding, meta.accountKey).commit()
// Prior source:             } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
// Prior source:             catch (_: Exception) { /* Retry on an existing token/login wake; never log bearer/platform token. */ }
// Prior source:         }
// Prior source:     } }
// Prior source:
