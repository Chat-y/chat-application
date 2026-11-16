package com.example.chat.data.api

import org.json.JSONObject

/** Prepared email-code auth contract. Captured route/tenant, no password or bearer
 *  fallback; unavailable deployment is an error, never a fabricated session.
 *  Server patch must deploy before this client can authenticate real users. */
class ApiEmailCodeClient(
    private val baseUrl: String,
    private val tenantId: String,
    private val transport: ApiHttpTransport,
    private val isCurrent: () -> Boolean = { true },
) {
    // A local attempt identity and UI resend/resume limits, not a server challenge or expiry.
    // The server remains the authority on code validity and request throttling.
    data class Challenge(val id: String, val email: String, val resendAfterSeconds: Int, val expiresInSeconds: Int, val mayResend: Boolean = true)

    private fun request(path: String, payload: JSONObject, deviceHeaders: Map<String, String> = emptyMap()): Pair<Int, String> {
        check(isCurrent()) { "Account or workspace changed. Start sign-in again." }
        val uri = java.net.URI(baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Secure sign-in endpoint required." }
        val headers = mapOf("Content-Type" to "application/json", "x-tenant-id" to tenantId) +
            deviceHeaders.filterKeys { it in setOf("x-device-id", "x-device-public-key", "x-device-challenge-id", "x-device-proof") }
        val body = payload.toString()
        var response = transport.execute("POST", "${baseUrl.trimEnd('/')}/$path", headers, body)
        check(isCurrent()) { "Account or workspace changed. Start sign-in again." }
        if (response.first == 404) {
            response = transport.execute("POST", "${baseUrl.trimEnd('/')}/api/v1/$path", headers, body)
            check(isCurrent()) { "Account or workspace changed. Start sign-in again." }
        }
        return response
    }

    fun requestCode(email: String): Challenge {
        val normalized = email.trim().lowercase(java.util.Locale.ROOT)
        require(normalized.length <= 254 && normalized.contains('@')) { "Enter a valid email address." }
        // Prior contract expected server challenge_id/resend_after_seconds/expires_in_seconds.
        // code-login/send deliberately returns only an enumeration-safe message.
        val sendNumber = EmailCodeSendLimiter.reserve(baseUrl, tenantId, normalized)
        val response = request("auth/email/code-login/send", JSONObject().put("email", normalized))
        if (response.first != 200) throw responseError(response)
        return Challenge(java.util.UUID.randomUUID().toString(), normalized,
            EmailCodeSendLimiter.delayAfter(sendNumber), CODE_EXPIRY_SECONDS, sendNumber < 5)
    }

    fun verifyCode(challenge: Challenge, code: String, deviceHeaders: Map<String, String> = emptyMap()): ApiAuthResult {
        require(code.length == 6 && code.all { it in '0'..'9' }) { "Enter the six-digit code." }
        // Prior: auth/email-code/verify {challenge_id, code}.
        val response = request("auth/email/code-login/verify", JSONObject()
            .put("email", challenge.email).put("code", code).put("remember_me", false), deviceHeaders)
        if (response.first != 200) throw responseError(response)
        val result = ApiAuthClient({ baseUrl }, { tenantId }, transport).parseAuthResponse(response.first, response.second)
        check(result.token.isNotBlank() && result.user.id.isNotBlank() && !result.refreshToken.isNullOrBlank()) {
            "The server did not return a complete sign-in session."
        }
        check(result.user.email.equals(challenge.email, ignoreCase = true)) { "The code signed in a different email. Start again." }
        return result
    }

    private fun responseError(response: Pair<Int, String>): Exception {
        // Never display arbitrary server details (which can include identifiers or infrastructure).
        return IllegalStateException(when (response.first) {
            401 -> "Invalid or expired code. Request a new code if needed."
            429 -> "Too many attempts. Wait before trying again."
            503 -> "Email-code sign-in is unavailable. Try again later."
            428 -> "Installation verification is required. Start sign-in again."
            else -> "Email-code sign-in is unavailable (${response.first})."
        })
    }
    companion object {
        // Local UX guardrails only. These constants do not assert server expiry or rate limits.
        const val LOCAL_RESEND_DELAY_SECONDS = 60
        // PR #3 code lifetime. The server may still invalidate a code earlier.
        const val CODE_EXPIRY_SECONDS = 600
    }
}

/** In-process UX limits. Server enforcement still applies across process death/devices.
 * Failed sends consume a local slot conservatively, rather than enable rapid retries. */
internal object EmailCodeSendLimiter {
    private data class Attempt(val at: Long, val delaySeconds: Int)
    private val attempts = mutableMapOf<List<String>, MutableList<Attempt>>()
    private fun nowSeconds() = System.nanoTime() / 1_000_000_000L
    fun delayAfter(sendNumber: Int) = when (sendNumber) { 1 -> 60; 2 -> 120; else -> 300 }
    @Synchronized fun reserve(base: String, tenant: String, email: String, now: Long = nowSeconds()): Int {
        attempts.values.forEach { history -> history.removeAll { now - it.at >= 3600L } }
        attempts.entries.removeAll { it.value.isEmpty() }
        val key = listOf(base.trimEnd('/'), tenant, email)
        check(key in attempts || attempts.size < 128) { "Too many sign-in requests. Try again later." }
        val history = attempts.getOrPut(key) { mutableListOf() }
        check(history.size < 5) { "The sign-in code request limit was reached. Try again in an hour." }
        check(history.lastOrNull()?.let { now - it.at >= it.delaySeconds } != false) {
            "Wait for the resend timer before requesting another code."
        }
        val number = history.size + 1
        history.add(Attempt(now, delayAfter(number)))
        return number
    }
}
