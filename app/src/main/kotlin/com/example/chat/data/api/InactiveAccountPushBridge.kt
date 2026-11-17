package com.example.chat.data.api

import android.content.Context
import com.example.chat.data.demo.DemoMode
import com.example.chat.notifications.ChatyNotifications
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** One bounded directory check per eligible inactive session on an existing
 * push wake. No timer, credential refresh, socket or automatic account switch. */
class InactiveAccountPushBridge(context: Context, private val api: ApiBackendService,
    private val secureStorage: com.example.chat.data.e2ee.SecureKeyValueStore) {
    private val runLock = kotlinx.coroutines.sync.Mutex()
    private var onlyAccountKey: String? = null
    private val alertEnabled = mutableMapOf<String, Boolean>()
    private val unreadChats = mutableMapOf<String, Int>()
    private val unreadMessages = mutableMapOf<String, Int>()
    private var vaultIds: Map<String, Set<String>> = emptyMap()
    private val context = context.applicationContext
    private val state = this.context.getSharedPreferences("inactive_push_fingerprints", Context.MODE_PRIVATE)
    private val local = this.context.getSharedPreferences("chaty_api_backend", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private fun current(t: InactiveAccountCatchUp.Target): Boolean = !DemoMode.active &&
        api.currentAccountKey() != t.key && api.multiAccount.accountByKey(t.key)?.sessionExpired == false &&
        api.multiAccount.credentialsFor(t.key)?.accessToken == t.token
    private val catchUp = InactiveAccountCatchUp(
        targets = {
            if (DemoMode.active) emptyList() else api.multiAccount.accounts().filter {
                it.accountKey != api.currentAccountKey() && !it.sessionExpired && (onlyAccountKey == null || it.accountKey == onlyAccountKey)
            }.mapNotNull { meta -> api.multiAccount.credentialsFor(meta.accountKey)?.let { credentials ->
                InactiveAccountCatchUp.Target(meta.accountKey, meta.accountHash, meta.baseUrl, meta.tenantId,
                    credentials.accessToken, credentials.expiresAtEpochMs ?: JwtExpiryDecoder.decodeExpiryEpochMs(credentials.accessToken), meta.displayName, meta.userId)
            } }
        }, isCurrent = ::current, fetch = ::fetchFingerprint,
        previous = { state.getString(it, null) },
        remember = { hash, fingerprint -> state.edit().putString(hash, fingerprint).apply() },
        // Disabled alerts still publish badges; retire any old alert and treat delivery as intentionally handled.
        alert = { t -> if (!current(t)) false else if (alertEnabled[t.hash] == false) {
            ChatyNotifications.cancelInactiveAccountMessage(context, t.hash); true
        } else ChatyNotifications.showInactiveAccountMessage(context, t.hash, t.label) },
        clear = { t -> ChatyNotifications.cancelInactiveAccountMessage(context, t.hash) },
        publish = { t, summary ->
            if (current(t)) api.publishInactiveUnread(t.key, t.token, unreadChats[t.hash] ?: 0, unreadMessages[t.hash] ?: 0)
        },
        budgetMillis = 25_000,

    )
    suspend fun run(accountKey: String? = null): Boolean {
        if (DemoMode.active) return true
        if (!runLock.tryLock()) return false
        onlyAccountKey = accountKey
        try { return runLocked() } finally { onlyAccountKey = null; vaultIds = emptyMap(); unreadChats.clear(); unreadMessages.clear(); alertEnabled.clear(); runLock.unlock() }
    }
    private suspend fun runLocked(): Boolean {
        val retained = api.multiAccount.accounts()
        val inactiveHashes = retained.filter { it.accountKey != api.currentAccountKey() && !it.sessionExpired }
            .map { it.accountHash }.toSet()
        // These prefs contain only catch-up digests. Remove stale alerts for
        // an account that was selected, signed out or expired since last wake.
        state.all.keys.filter { it !in inactiveHashes }.forEach { hash ->
            ChatyNotifications.cancelInactiveAccountMessage(context, hash)
            state.edit().remove(hash).apply()
        }
        // Read inactive vault registries without switching the active vault.
        // Legacy unscoped entries are also excluded until migration is complete.
        vaultIds = try {
            val legacy = secureStorage.read("chaty.hidden_vault.locked_conversations")
                .orEmpty().split(',').filter { it.isNotEmpty() }.toSet()
            retained.associate { meta -> meta.accountHash to (legacy +
                secureStorage.read("chaty.hidden_vault.locked_conversations.${meta.accountHash}")
                    .orEmpty().split(',').filter { it.isNotEmpty() }) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { return false } // Cannot verify privacy: no alert.
        return catchUp.run()
    }

    private fun fetchFingerprint(t: InactiveAccountCatchUp.Target): InactiveAccountCatchUp.Summary? {
        val uri = URI(t.base)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        val base = t.base.trimEnd('/')
        fun request(path: String): Pair<Int, String>? {
            if (!current(t)) return null
            val request = Request.Builder().url("$base/$path")
                .header("Authorization", "Bearer ${t.token}").header("x-tenant-id", t.tenant).get().build()
            return http.newCall(request).execute().use { it.code to (it.body?.string().orEmpty()) }
        }
        var response = request("chat/rooms?include_tasks=false") ?: return null
        if (response.first == 404) response = request("api/v1/chat/rooms?include_tasks=false") ?: return null
        if (!current(t) || response.first != 200) return null
        val rows = JSONObject(response.second).optJSONArray("data") ?: return null
        val overrides = JSONObject(local.getString("api_local_conv_${t.hash}", "{}") ?: "{}")
        val settings = context.getSharedPreferences("chaty_local_preferences", Context.MODE_PRIVATE)
        // Previous strict-blob implementation retained: val profile = JSONObject(settings.getString(
        // Previous strict-blob implementation retained: com.example.chat.core.preferences.PreferenceKeys.scopedPreferences(t.userId), null) ?: return null)
        // Previous strict-blob implementation retained: val notification = profile.optJSONObject("notification") ?: return null
        // Previous strict-blob implementation retained: val notificationsEnabled = notification.opt("enableGlobalNotifications") as? Boolean ?: return null
        // Previous strict-blob implementation retained: alertEnabled[t.hash] = notificationsEnabled
        // Previous strict-blob implementation retained: // Notification opt-out affects alerts, not unread counts.
        // Previous strict-blob implementation retained:
        // Previous strict-blob implementation retained: val security = profile.optJSONObject("security") ?: return null
        // Previous strict-blob implementation retained: val protectedIds = (vaultIds[t.hash] ?: return null).toMutableSet()
        // Previous strict-blob implementation retained: for (key in listOf("lockedConversationIds", "hiddenConversationIds")) {
        // Previous strict-blob implementation retained: val ids = security.optJSONArray(key) ?: return null
        // Previous strict-blob implementation retained: for (i in 0 until ids.length()) {
        // Previous strict-blob implementation retained: val id = ids.opt(i) as? String ?: return null
        // Previous strict-blob implementation retained: protectedIds += id
        // Previous strict-blob implementation retained: }
        // Previous strict-blob implementation retained: }
        val prefKey = com.example.chat.core.preferences.PreferenceKeys.scopedPreferences(t.userId)
        // Missing blob is the fresh-account default, not corrupted settings. Never borrow the active account's prefs.
        // A legacy blob may still contain an opt-out/protection not adopted into this namespace.
        // Do not infer fresh defaults or borrow another account's legacy settings.
        if (!settings.contains(prefKey) && settings.contains(com.example.chat.core.preferences.PreferenceKeys.preferencesBase)) return null
        val raw = if (settings.contains(prefKey)) settings.getString(prefKey, null) ?: return null else null
        fun unwrap(value: Any?): Any? = when (value) {
            is JSONObject -> value.keys().asSequence().associateWith { unwrap(value.opt(it)) }
            is org.json.JSONArray -> (0 until value.length()).map { unwrap(value.opt(it)) }
            JSONObject.NULL -> null
            else -> value
        }
        @Suppress("UNCHECKED_CAST")
        val map = raw?.let { unwrap(JSONObject(it)) as? Map<String, Any?> ?: return null }
        val policy = InactiveNotificationPreferences.decode(map) ?: return null
        alertEnabled[t.hash] = policy.enabled
        val protectedIds = (vaultIds[t.hash] ?: return null) + policy.protectedIds
        val markers = mutableListOf<String>()
        var chats = 0
        var unread = 0
        var badgeUnread = 0
        var latest = 0L
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val id = row.optString("uid").ifBlank { row.optString("id") }
            val own = overrides.optJSONObject(id)
            if (id.isBlank() || id in protectedIds || row.optString("room_type") == "task" ||
                (!row.isNull("task_id") && row.optString("task_id").isNotBlank()) ||
                own?.optBoolean("archived", false) == true) continue
            val count = com.example.chat.domain.models.parseApiUnreadCount(row.opt("unread_count"))
            if (count <= 0) continue
            // Match active-account badges: mute suppresses interruption, not unread state.
            chats++
            badgeUnread = (badgeUnread.toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (own?.optBoolean("muted", false) == true) continue
            val last = row.optJSONObject("last_message")
            unread = (unread.toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val time = last?.optString("created_at").orEmpty()
            latest = maxOf(latest, runCatching { java.time.Instant.parse(time).toEpochMilli() }.getOrDefault(0L))
            markers += "$id:$count:${last?.optString("id").orEmpty()}:${last?.optString("created_at").orEmpty()}"
        }
        unreadChats[t.hash] = chats
        unreadMessages[t.hash] = badgeUnread
        if (markers.isEmpty()) return InactiveAccountCatchUp.Summary(0, 0, "")
        // Persist only a digest, never private text, room IDs, tokens or usernames.
        val digest = MessageDigest.getInstance("SHA-256").digest(markers.sorted().joinToString("|").toByteArray())
            .joinToString("") { "%02x".format(it) }
        return InactiveAccountCatchUp.Summary(unread, latest, digest)
    }
}

// Replaced source retained for review (inactive; do not restore without its surrounding implementation).
// Prior source:     private val http = OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS)
// Prior source:         alert = { t -> if (current(t)) ChatyNotifications.showInactiveAccountMessage(context, t.hash, t.label) else false },
// Prior source:     suspend fun run(accountKey: String? = null) {
// Prior source:         if (DemoMode.active || !runLock.tryLock()) return
// Prior source:         try { runLocked() } finally { onlyAccountKey = null; vaultIds = emptyMap(); runLock.unlock() }
// Prior source:     private suspend fun runLocked() {
// Prior source:         } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { return } // Cannot verify privacy: no alert.
// Prior source:         catchUp.run()
// Prior source:         val profile = JSONObject(settings.getString(
// Prior source:             com.example.chat.core.preferences.PreferenceKeys.scopedPreferences(t.userId), null) ?: return null)
// Prior source:         val notification = profile.optJSONObject("notification") ?: return null
// Prior source:         val notificationsEnabled = notification.opt("enableGlobalNotifications") as? Boolean ?: return null
// Prior source:         if (!notificationsEnabled) return InactiveAccountCatchUp.Summary(0, 0, "")
// Prior source:         val security = profile.optJSONObject("security") ?: return null
// Prior source:         val protectedIds = (vaultIds[t.hash] ?: return null).toMutableSet()
// Prior source:         for (key in listOf("lockedConversationIds", "hiddenConversationIds")) {
// Prior source:             val ids = security.optJSONArray(key) ?: return null
// Prior source:             for (i in 0 until ids.length()) {
// Prior source:                 val id = ids.opt(i) as? String ?: return null
// Prior source:                 protectedIds += id
// Prior source:             }
// Prior source:                 own?.optBoolean("muted", false) == true || own?.optBoolean("archived", false) == true) continue
// Prior source:             val count = row.optInt("unread_count", 0)
