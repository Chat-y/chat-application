package com.example.chat.data.api

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Additive local contract adapter. Legacy auth never calls this while AUTH_V2_CONTRACTS is false.
 * No endpoint is discovered from response strings, no automatic retries/fallbacks, no session adoption.
 * Callers must use existing registered-device/session-adoption gates after explicit v2 flow selection.
 */
class ApiAuthV2Client(
    private val baseUrl: String,
    private val tenantId: String,
    private val transport: ApiHttpTransport,
    private val token: String? = null,
    private val isCurrent: () -> Boolean = { true },
    private val enabled: () -> Boolean = { com.example.chat.ReleaseFeatures.AUTH_V2_CONTRACTS },
) {
    init {
        val uri = java.net.URI(baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Secure sign-in endpoint required."
        }
    }
    class Disabled : IllegalStateException("The new sign-in service is not enabled.")
    class Failure(val status: Int, val code: String?, message: String) : Exception(message)
    class Result(val status: Int, val data: JSONObject) {
        val pendingApproval: Boolean get() = data.optString("status") == "pending_approval"
        override fun toString(): String = "AuthV2Result(status=$status, contents=redacted)"
    }
    /** Kept only in memory by the caller. Reuse for retries of this exact registration only. */
    class RegistrationKey private constructor(val value: String) {
        companion object { fun create() = RegistrationKey(java.util.UUID.randomUUID().toString()) }
        override fun toString(): String = "RegistrationKey(redacted)"
    }
    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun proof(headers: Map<String, String>): Map<String, String> {
        val required = listOf("x-device-id", "x-device-public-key", "x-device-challenge-id", "x-device-proof")
        require(required.all { name -> headers.entries.any { it.key.equals(name, true) && it.value.isNotBlank() } }) {
            "Installation verification is required. Start sign-in again."
        }
        return headers.filterKeys { key -> required.any { it.equals(key, true) } }
    }
    private fun call(method: String, path: String, payload: JSONObject? = null,
        authenticated: Boolean = false, extraHeaders: Map<String, String> = emptyMap()): Result {
        if (!enabled()) throw Disabled()
        if (!isCurrent()) throw ApiScopeChangedException()
        val headers = mutableMapOf("Content-Type" to "application/json", "x-tenant-id" to tenantId)
        if (authenticated) headers["Authorization"] = "Bearer ${token?.takeIf { it.isNotBlank() } ?: error("Not authenticated") }"
        headers.putAll(extraHeaders)
        val response = transport.execute(method, baseUrl.trimEnd('/') + "/" + path, headers, payload?.toString())
        if (!isCurrent()) throw ApiScopeChangedException()
        val parsed = runCatching { JSONObject(response.second) }.getOrElse {
            throw Failure(response.first, "INVALID_RESPONSE", "The server returned an unreadable response.")
        }
        if (response.first !in 200..299) {
            val detail = parsed.optJSONObject("detail")
            throw Failure(response.first, detail?.optString("code")?.takeIf { it.isNotBlank() },
                detail?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: parsed.optString("detail").takeIf { it.isNotBlank() }
                    ?: "The request could not be completed (${response.first}).")
        }
        return Result(response.first, parsed.optJSONObject("data") ?: parsed)
    }
    fun verifyInvite(inviteToken:String): Result {
        require(com.example.chat.data.auth.v2.AuthInvitationTokenPolicy.valid(inviteToken)) { "This invitation is invalid." }
        return call("GET","auth/invites/${enc(inviteToken)}/verify")
    }
    fun onboarding(workspace: String) = call("GET", "organization/onboarding/${enc(workspace)}")
    fun discover(query: String, limit: Int = 20) = call("GET", "organization/discover?query=${enc(query)}&limit=${limit.coerceIn(1,20)}")
    fun domainSuggestion(domain: String): Result {
        require(domain.isNotBlank() && '@' !in domain && '/' !in domain) { "Provide an email domain, not an address." }
        return call("GET", "organization/domain-suggestion?email_domain=${enc(domain)}")
    }
    fun workspaces() = call("GET", "organization/mine", authenticated = true)
    fun switchWorkspace(workspace: String, deviceProof: Map<String,String>) = call("POST", "organization/switch",
        JSONObject().put("workspace", workspace), authenticated = true, extraHeaders = proof(deviceProof))
    fun usernameAlternatives(username: String) = call("GET", "auth/username-alternatives?username=${enc(username)}")
    fun terms() = call("GET", "auth/terms/current")
    fun acceptTerms(version: String) = call("POST", "auth/terms/accept", JSONObject().put("terms_version", version), authenticated = true)
    fun providers() = call("GET", "auth/sso/providers")
    fun startSso(providerId: String, redirectUri: String = "chaty://sso") = call("POST", "auth/sso/start",
        JSONObject().put("provider_id", providerId).put("redirect_uri", redirectUri))
    // No callback exchange: the local server explicitly returns501 until a real IdP is configured.
    fun forgotUsername(email: String) = call("POST", "auth/forgot-username", JSONObject().put("email", email))
    fun sendVerificationLink(email: String) = call("POST", "auth/email/verification-link/send", JSONObject().put("email", email))
    fun confirmVerificationLink(oneTimeToken: String) = call("POST", "auth/email/verification-link/confirm?token=${enc(oneTimeToken)}")
    fun approval(approvalId: String, email: String) = call("GET", "auth/account-approval/${enc(approvalId)}?email=${enc(email)}")
    fun login(username: String, password: String, rememberMe: Boolean, deviceProof: Map<String,String>) = call("POST", "auth/login/v2",
        JSONObject().put("username",username).put("password",password).put("remember_me",rememberMe), extraHeaders = proof(deviceProof))
    fun registerStart(email: String, username: String, password: String, phoneNumber: String,
        key: RegistrationKey, rememberMe: Boolean = false, termsVersion: String? = null, fullName: String? = null, inviteToken:String?=null): Result {
        // Previous V2-only 8-character / 72-byte ceiling retained inactive:
        // require(password.length >= 8 && password.toByteArray(StandardCharsets.UTF_8).size <= 72) { "Use at least 8 characters and at most 72 UTF-8 bytes." }
        com.example.chat.ui.auth.registrationPasswordError(password)?.let { throw IllegalArgumentException(it) }
        requireNotNull(RegistrationPhonePolicy.normalize(phoneNumber)) { "Enter an international contact number." }
        val body = JSONObject().put("email",email).put("username",username).put("password",password)
            .put("phoneNumber",phoneNumber).put("remember_me",rememberMe)
        inviteToken?.let {
            require(com.example.chat.data.auth.v2.AuthInvitationTokenPolicy.valid(it)) { "This invitation is invalid." }
            body.put("invite_token",it)
        }
        termsVersion?.let { body.put("terms_version",it) }; fullName?.let { body.put("full_name",it) }
        return call("POST", "auth/register/v2", body, extraHeaders = mapOf("Idempotency-Key" to key.value))
    }
    fun registerComplete(email: String, code: String, username: String, key: RegistrationKey,
        deviceProof: Map<String,String>, phoneChallengeId: String? = null, phoneVerificationToken: String? = null): Result {
        require((phoneChallengeId == null) == (phoneVerificationToken == null)) { "Phone proof must be provided as a pair." }
        val body = JSONObject().put("email",email).put("code",code).put("username",username)
        phoneChallengeId?.let { body.put("phoneChallengeId",it) }; phoneVerificationToken?.let { body.put("phoneVerificationToken",it) }
        return call("POST", "auth/register/v2/complete", body,
            extraHeaders = proof(deviceProof) + ("Idempotency-Key" to key.value))
    }
    fun resetPassword(email: String, code: String, password: String, signIn: Boolean,
        rememberMe: Boolean = false, deviceProof: Map<String,String> = emptyMap()) = call("POST", "auth/reset-password/v2",
        JSONObject().put("email",email).put("code",code).put("new_password",password)
            .put("sign_in",signIn).put("remember_me",rememberMe), extraHeaders = if (signIn) proof(deviceProof) else emptyMap())
}
