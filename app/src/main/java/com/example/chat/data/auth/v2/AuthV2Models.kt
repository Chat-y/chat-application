package com.example.chat.data.auth.v2

import org.json.JSONObject

/** Typed models for the additive login/register v2 contracts
 *  (login-register-v2-contracts). Parsing is defensive: every field is
 *  optional unless the contract marks it load-bearing, unknown fields are
 *  ignored, and no endpoint or capability is inferred from response strings.
 *  Nothing here performs network I/O or session adoption. */

/** Workspace branding block from GET /organization/onboarding/{workspace}. */
data class AuthV2Branding(
    val displayName: String?,
    val logoUrl: String?,
    val accentColorHex: String?,
    val backgroundColorHex: String?,
    val loginBackgroundUrl: String?,
) {
    /** Parsed #RRGGBB accent as ARGB, or null when absent/malformed. The UI
     *  falls back to the active theme accent; branding never replaces tokens. */
    fun accentColorArgb(): Long? = parseHexColor(accentColorHex)
    fun backgroundColorArgb(): Long? = parseHexColor(backgroundColorHex)

    companion object {
        internal fun parseHexColor(raw: String?): Long? {
            val value = raw?.trim()?.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: return null
            return runCatching { 0xFF000000L or value.substring(1).toLong(16) }.getOrNull()
        }
    }
}

/** Contact-admin block (approved item 37): how a locked-out user reaches the
 *  workspace administrator. All fields optional; the UI renders only what
 *  the server provides. */
data class AuthV2ContactAdmin(
    val available: Boolean,
    val email: String?,
    val url: String?,
    val message: String?,
)

/** Registration policy block from onboarding. */
data class AuthV2RegistrationPolicy(
    val accountApprovalRequired: Boolean,
    val discoverable: Boolean,
    val allowedEmailDomains: List<String>,
)

/** Terms block (approved item 56): version is required on register when
 *  `required` is true; urls open in an in-app Custom Tab. */
data class AuthV2Terms(
    val required: Boolean,
    val version: String?,
    val url: String?,
    val privacyUrl: String?,
)

/** SSO provider scaffold (approved item 43). Callback exchange stays disabled
 *  server-side (501) until a real IdP is configured. */
data class AuthV2SsoProvider(
    val id: String,
    val type: String,
    val name: String,
    val enabled: Boolean,
)

/** Everything the login/register UI needs for one workspace. */
data class AuthV2Onboarding(
    val workspaceName: String,
    val tenantId: String,
    val allowUserRegistration: Boolean,
    val branding: AuthV2Branding,
    val contactAdmin: AuthV2ContactAdmin,
    val registration: AuthV2RegistrationPolicy,
    val terms: AuthV2Terms,
    val ssoEnabled: Boolean,
    val ssoProviders: List<AuthV2SsoProvider>,
)

/** One discoverable workspace row (approved items 31/32). */
data class AuthV2WorkspaceSummary(
    val name: String,
    val tenantId: String,
    val allowUserRegistration: Boolean,
    val branding: AuthV2Branding,
)

/** One of the signed-in account's workspace accounts (approved item 35). */
data class AuthV2WorkspaceAccount(
    val name: String,
    val tenantId: String,
    val username: String,
    val current: Boolean,
    val accountStatus: String,
    val branding: AuthV2Branding,
) {
    val switchable: Boolean get() = !current && accountStatus == "active"
}

/** Approval-state read for the waiting-for-approval screen (item 42). */
data class AuthV2Approval(
    val approvalId: String,
    val state: State,
    val reason: String?,
) {
    enum class State { PENDING, APPROVED, REJECTED, UNKNOWN }
}

/** Session metadata returned by session-issuing v2 routes (item 41). */
data class AuthV2SessionInfo(
    val rememberMe: Boolean,
    val refreshExpiresInDays: Long,
)

/** Registration-received acknowledgement from POST /auth/register/v2. The
 *  shape is identical whether or not the email already belongs to an account
 *  (privacy-safe, item 40); the UI must not infer account existence. */
data class AuthV2RegisterStart(
    val expiresInMinutes: Int,
    val usernameAlternatives: List<String>,
    val message: String?,
)

/** Result of POST /auth/sso/start: open [authorizationUrl] in a Custom Tab. */
data class AuthV2SsoStart(
    val providerId: String,
    val state: String,
    val authorizationUrl: String,
    val expiresInSeconds: Int,
)

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() && it != "null" }

internal fun JSONObject.optStringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}

internal fun JSONObject.child(name: String): JSONObject = optJSONObject(name) ?: JSONObject()

internal fun parseBranding(obj: JSONObject?) = AuthV2Branding(
    displayName = obj?.optStringOrNull("display_name"),
    logoUrl = obj?.optStringOrNull("logo_url"),
    accentColorHex = obj?.optStringOrNull("accent_color"),
    backgroundColorHex = obj?.optStringOrNull("background_color"),
    loginBackgroundUrl = obj?.optStringOrNull("login_background_url"),
)

object AuthV2Models {

    fun onboarding(data: JSONObject): AuthV2Onboarding {
        val workspace = data.child("workspace")
        val tenantId = workspace.optStringOrNull("tenant_id")
            ?: throw AuthV2ContractException("Workspace onboarding is missing its workspace id.")
        val contact = data.child("contact_admin")
        val registration = data.child("registration")
        val terms = data.child("terms")
        val sso = data.child("sso")
        val providers = sso.optJSONArray("providers")
        return AuthV2Onboarding(
            workspaceName = workspace.optStringOrNull("name") ?: tenantId,
            tenantId = tenantId,
            allowUserRegistration = workspace.optBoolean("allow_user_registration", false),
            branding = parseBranding(data.optJSONObject("branding")),
            contactAdmin = AuthV2ContactAdmin(
                available = contact.optBoolean("available", false),
                email = contact.optStringOrNull("email"),
                url = contact.optStringOrNull("url"),
                message = contact.optStringOrNull("message"),
            ),
            registration = AuthV2RegistrationPolicy(
                accountApprovalRequired = registration.optBoolean("account_approval_required", false),
                discoverable = registration.optBoolean("discoverable", false),
                allowedEmailDomains = registration.optStringList("allowed_email_domains"),
            ),
            terms = AuthV2Terms(
                required = terms.optBoolean("required", false),
                version = terms.optStringOrNull("version"),
                url = terms.optStringOrNull("url"),
                privacyUrl = terms.optStringOrNull("privacy_url"),
            ),
            ssoEnabled = sso.optBoolean("enabled", false),
            ssoProviders = providers?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { provider ->
                        val id = provider.optStringOrNull("id") ?: return@let null
                        AuthV2SsoProvider(
                            id = id,
                            type = provider.optStringOrNull("type") ?: "oidc",
                            name = provider.optStringOrNull("name") ?: id,
                            enabled = provider.optBoolean("enabled", false),
                        )
                    }
                }
            } ?: emptyList(),
        )
    }

    fun workspaceSummaries(data: JSONObject): List<AuthV2WorkspaceSummary> {
        val array = data.optJSONArray("workspaces") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { workspace ->
                val tenantId = workspace.optStringOrNull("tenant_id") ?: return@let null
                AuthV2WorkspaceSummary(
                    name = workspace.optStringOrNull("name") ?: tenantId,
                    tenantId = tenantId,
                    allowUserRegistration = workspace.optBoolean("allow_user_registration", false),
                    branding = parseBranding(workspace.optJSONObject("branding")),
                )
            }
        }
    }

    fun workspaceAccounts(data: JSONObject): List<AuthV2WorkspaceAccount> {
        val array = data.optJSONArray("workspaces") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { workspace ->
                val tenantId = workspace.optStringOrNull("tenant_id") ?: return@let null
                AuthV2WorkspaceAccount(
                    name = workspace.optStringOrNull("name") ?: tenantId,
                    tenantId = tenantId,
                    username = workspace.optStringOrNull("username") ?: "",
                    current = workspace.optBoolean("current", false),
                    accountStatus = workspace.optStringOrNull("account_status") ?: "active",
                    branding = parseBranding(workspace.optJSONObject("branding")),
                )
            }
        }
    }

    /** Domain suggestion (item 32): the suggested workspace plus all matches. */
    fun domainSuggestion(data: JSONObject): Pair<AuthV2WorkspaceSummary?, List<AuthV2WorkspaceSummary>> {
        val suggested = data.optJSONObject("suggested_workspace")?.let { workspace ->
            val tenantId = workspace.optStringOrNull("tenant_id") ?: return@let null
            AuthV2WorkspaceSummary(
                name = workspace.optStringOrNull("name") ?: tenantId,
                tenantId = tenantId,
                allowUserRegistration = workspace.optBoolean("allow_user_registration", true),
                branding = parseBranding(workspace.optJSONObject("branding")),
            )
        }
        return suggested to workspaceSummaries(data)
    }

    fun approval(data: JSONObject): AuthV2Approval {
        val id = data.optStringOrNull("approval_id")
            ?: throw AuthV2ContractException("Approval response is missing its id.")
        val state = when (data.optString("approval_state")) {
            "pending" -> AuthV2Approval.State.PENDING
            "approved" -> AuthV2Approval.State.APPROVED
            "rejected" -> AuthV2Approval.State.REJECTED
            else -> AuthV2Approval.State.UNKNOWN
        }
        return AuthV2Approval(id, state, data.optStringOrNull("reason"))
    }

    fun registerStart(data: JSONObject): AuthV2RegisterStart {
        if (data.optString("status") != "registration_received") {
            throw AuthV2ContractException("Unexpected registration response.")
        }
        return AuthV2RegisterStart(
            expiresInMinutes = data.optInt("expires_in_minutes", 10),
            usernameAlternatives = data.optStringList("username_alternatives"),
            message = data.optStringOrNull("message"),
        )
    }

    fun sessionInfo(data: JSONObject): AuthV2SessionInfo {
        val session = data.child("session")
        return AuthV2SessionInfo(
            rememberMe = session.optBoolean("remember_me", false),
            refreshExpiresInDays = session.optLong("refresh_expires_in_days", 1L),
        )
    }

    fun ssoStart(data: JSONObject): AuthV2SsoStart {
        val url = data.optStringOrNull("authorization_url")
            ?: throw AuthV2ContractException("SSO did not return a sign-in page.")
        return AuthV2SsoStart(
            providerId = data.optStringOrNull("provider_id") ?: "",
            state = data.optStringOrNull("state") ?: "",
            authorizationUrl = url,
            expiresInSeconds = data.optInt("expires_in_seconds", 600),
        )
    }

    fun terms(data: JSONObject) = AuthV2Terms(
        required = data.optBoolean("required", false),
        version = data.optStringOrNull("version"),
        url = data.optStringOrNull("url"),
        privacyUrl = data.optStringOrNull("privacy_url"),
    )
}

/** The v2 server returned a 2xx whose body does not match its contract. */
class AuthV2ContractException(message: String) : IllegalStateException(message)
