package com.example.chat.data.auth.v2

import com.example.chat.data.api.ApiAuthV2Client
import com.example.chat.data.api.ApiScopeChangedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** User-safe failure of a v2 flow step. [message] is the server's own human
 *  readable message when it sent one (approved: friendly copy only if the
 *  server already returns a message), otherwise a fixed generic fallback.
 *  Typed identifiers (email, username, workspace) are never added here. */
class AuthV2FlowException(
    val status: Int,
    val code: String?,
    message: String,
    val retryAfterSeconds: Int? = null,
) : Exception(message)

class AuthV2PendingApprovalException(val approvalId:String,val email:String):Exception("Waiting for workspace approval.")

/** Orchestrates the additive login/register v2 flow on top of
 *  [ApiAuthV2Client]. Behaviour rules carried over from the existing gates:
 *
 *  - Device proof: session-issuing steps obtain fresh proof headers from the
 *    injected [deviceProof] (production: DeviceIdentityManager.challengeHeaders
 *    against the current routing, which enforces https, one-use challenges and
 *    scope staleness). No proof, no request.
 *  - Adoption: sessions are adopted only through [adoptionSink], the seam to
 *    the existing registered-account gate. This controller never stores a
 *    token itself.
 *  - Scope: [isCurrent] (authSnapshot identity) is re-checked right before
 *    adoption; a stale account aborts with [ApiScopeChangedException].
 *  - Idempotency: register start/complete reuse one client-generated
 *    [ApiAuthV2Client.RegistrationKey] held in memory; retrying start resends
 *    the exact same payload with the same key, never a new registration.
 *  - No automatic retries, no endpoint discovery, no legacy fallback. The
 *    whole flow is inert while ReleaseFeatures.AUTH_V2_CONTRACTS is false
 *    (the client throws [ApiAuthV2Client.Disabled] before any request).
 */
class AuthV2FlowController(
    private val client: ApiAuthV2Client,
    private val deviceProof: suspend () -> Map<String, String>,
    private val adoptionSink: AuthV2AdoptionSink,
    private val isCurrent: () -> Boolean = { true },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val workspaceAdoption: suspend (com.example.chat.data.api.ApiAuthResult, String) -> Unit = {result, _ -> adoptionSink.adopt(result)},
) {

    /** In-memory register retry context. The password lives only here and is
     *  dropped on completion or [discardRegistration]; never persisted. */
    private class RegisterContext(
        val key: ApiAuthV2Client.RegistrationKey,
        val email: String,
        val username: String,
        val password: String,
        val phoneNumber: String,
        val rememberMe: Boolean,
        val termsVersion: String?,
        val fullName: String?,
        val inviteToken:String?,
    ) {
        override fun toString(): String = "RegisterContext(redacted)"
    }

    @Volatile
    private var registerContext: RegisterContext? = null

    /** Email of the in-flight registration, for process-death recovery copy
     *  (item 6: email may be re-shown, the code never is). Null when idle. */
    val pendingRegisterEmail: String? get() = registerContext?.email

    sealed class AuthV2RegisterCompletion {
        data class Adopted(val userId: String, val session: AuthV2SessionInfo) : AuthV2RegisterCompletion()
        data class PendingApproval(val approvalId: String, val email: String) : AuthV2RegisterCompletion()
    }

    sealed class AuthV2ResetResult {
        /** Password changed, all old sessions revoked, no new session. */
        data class PasswordUpdated(val sessionsRevoked: Boolean) : AuthV2ResetResult()
        /** Password changed and a fresh session was adopted (item 38). */
        data class SignedIn(val userId: String, val session: AuthV2SessionInfo) : AuthV2ResetResult()
    }

    /** Public verify is read-only. A valid result is only registration prefill, not a session. */
    suspend fun verifyInvitation(token:String,workspaceHint:String?):InvitationVerification = guard {
        require(token.length in 16..4096 && token.none {it.isISOControl() || it.isWhitespace()}) { "This invitation is invalid." }
        val data=client.verifyInvite(token).data
        if(!data.optBoolean("valid",false)) return@guard when(data.optString("reason")) {
            "expired"->InvitationVerification.Expired
            "redeemed"->InvitationVerification.Redeemed
            else->InvitationVerification.ExpiredOrRejected
        }
        check(data.optString("status")=="valid" && !data.optBoolean("redeemed",false)) {"Invalid invitation response."}
        val tenant=data.optJSONObject("tenant") ?: error("Invitation workspace is missing.")
        val workspace=tenant.optString("tenant_id").takeIf {it.matches(Regex("[A-Za-z0-9_-]{1,128}"))} ?: error("Invalid invitation workspace.")
        check(workspaceHint.isNullOrBlank() || workspace==workspaceHint) {"Invitation workspace does not match."}
        val expiry=java.time.Instant.parse(data.optString("expires_at"))
        if(!expiry.isAfter(java.time.Instant.now())) return@guard InvitationVerification.Expired
        val email=if(data.isNull("bound_email")) null else data.optString("bound_email").takeIf {it.contains('@')} ?: error("Invalid invitation email.")
        InvitationVerification.Valid(workspace,tenant.optString("name").ifBlank {workspace},email,expiry,data.optBoolean("one_use",true))
    }

    // ---------------------------------------------------------------- workspace

    /** Onboarding block for one workspace (branding, terms, SSO, policy). */
    suspend fun onboarding(workspace: String): AuthV2Onboarding = guard {
        AuthV2Models.onboarding(client.onboarding(workspace.trim()).data)
    }

    /** Find-your-workspace discovery (item 31). */
    suspend fun discoverWorkspaces(query: String, limit: Int = 20): List<AuthV2WorkspaceSummary> = guard {
        AuthV2Models.workspaceSummaries(client.discover(query.trim(), limit).data)
    }

    /** Email-domain workspace suggestion (item 32). Input is a full email;
     *  only the domain leaves the device. */
    suspend fun suggestWorkspaceForEmail(email: String): AuthV2WorkspaceSummary? = guard {
        val domain = email.trim().substringAfter('@', "").lowercase()
        require(domain.isNotBlank() && '.' in domain) { "Enter a full email address first." }
        AuthV2Models.domainSuggestion(client.domainSuggestion(domain).data).first
    }

    /** Username-taken alternatives (item 13). */
    suspend fun usernameAlternatives(username: String): List<String> = guard {
        client.usernameAlternatives(username.trim()).data.optStringList("username_alternatives")
    }

    // ---------------------------------------------------------------- register

    /** Starts registration. Always the same 202 shape whether or not the
     *  email is already registered; callers must not treat this as an
     *  account-exists signal (item 40). */
    suspend fun startRegister(
        email: String,
        username: String,
        password: String,
        phoneNumber: String,
        rememberMe: Boolean = false,
        termsVersion: String? = null,
        fullName: String? = null,
        inviteToken:String?=null,
    ): AuthV2RegisterStart {
        val context = registerContext?.takeIf {
            it.email==email.trim() && it.username==username.trim() && it.password==password &&
                it.phoneNumber==phoneNumber.trim() && it.rememberMe==rememberMe && it.termsVersion==termsVersion &&
                it.fullName==fullName?.trim()?.takeIf {name->name.isNotBlank()} && it.inviteToken==inviteToken
        } ?: RegisterContext(
            key = ApiAuthV2Client.RegistrationKey.create(),
            email = email.trim(),
            username = username.trim(),
            password = password,
            phoneNumber = phoneNumber.trim(),
            rememberMe = rememberMe,
            termsVersion = termsVersion,
            fullName = fullName?.trim()?.takeIf { it.isNotBlank() },
            inviteToken=inviteToken,
        )
        registerContext = context // Retain exact request/key even after a timeout; retry must not create a new request.
        val start = guard {
            AuthV2Models.registerStart(
                client.registerStart(context.email, context.username, context.password,
                    context.phoneNumber, context.key, context.rememberMe, context.termsVersion,
                    context.fullName,context.inviteToken).data,
            )
        }
        registerContext = context
        return start
    }

    /** Safe retry of the current registration start: same key, same payload
     *  (item 2). Throws if no registration is in flight. */
    suspend fun retryRegisterStart(): AuthV2RegisterStart {
        val context = registerContext
            ?: throw AuthV2FlowException(0, "NO_REGISTRATION", "Start registration again.")
        return guard {
            AuthV2Models.registerStart(
                client.registerStart(context.email, context.username, context.password,
                    context.phoneNumber, context.key, context.rememberMe, context.termsVersion,
                    context.fullName,context.inviteToken).data,
            )
        }
    }

    /** Completes registration with the emailed code (spaces/hyphens stripped,
     *  item 50). Adopts the session on success; returns PendingApproval when
     *  the workspace requires approval (item 42) without adopting anything. */
    suspend fun completeRegister(
        code: String,
        phoneChallengeId: String? = null,
        phoneVerificationToken: String? = null,
    ): AuthV2RegisterCompletion {
        val context = registerContext
            ?: throw AuthV2FlowException(0, "NO_REGISTRATION", "Start registration again.")
        val cleaned = code.replace(Regex("[\\s-]"), "")
        val proof = deviceProof()
        val result = guard {
            client.registerComplete(context.email, cleaned, context.username, context.key,
                proof, phoneChallengeId, phoneVerificationToken)
        }
        if (!isCurrent()) throw ApiScopeChangedException()
        return when (val outcome = AuthV2SessionAdoption.outcomeOf(result, context.email)) {
            is AuthV2AuthOutcome.Session -> {
                adoptionSink.adopt(outcome.result)
                registerContext = null
                AuthV2RegisterCompletion.Adopted(outcome.result.user.id, outcome.info)
            }
            is AuthV2AuthOutcome.PendingApproval -> {
                registerContext = null
                AuthV2RegisterCompletion.PendingApproval(outcome.approvalId, outcome.email)
            }
        }
    }

    /** Drops the in-flight registration (user left the flow, item 4). */
    fun discardRegistration() {
        registerContext = null
    }

    // ---------------------------------------------------------------- login

    /** v2 login with remember-me (item 41). Adopts the session through the
     *  existing gate and returns the signed-in user id. */
    suspend fun login(username: String, password: String, rememberMe: Boolean): AuthV2SessionInfo {
        val proof = deviceProof()
        val result = guard { client.login(username.trim(), password, rememberMe, proof) }
        if (!isCurrent()) throw ApiScopeChangedException()
        val outcome = AuthV2SessionAdoption.outcomeOf(result, username.trim().takeIf {it.contains('@')})
        if(outcome is AuthV2AuthOutcome.PendingApproval) throw AuthV2PendingApprovalException(outcome.approvalId,outcome.email)
        val session = (outcome as? AuthV2AuthOutcome.Session)
            ?: throw AuthV2AdoptionException("The server did not complete sign-in. Try again.")
        adoptionSink.adopt(session.result)
        return session.info
    }

    // ---------------------------------------------------------------- reset

    /** v2 password reset. With [signIn], old sessions are revoked and a fresh
     *  one is adopted (item 38); without it, no session is created. */
    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
        signIn: Boolean,
        rememberMe: Boolean = false,
    ): AuthV2ResetResult {
        val cleaned = code.replace(Regex("[\\s-]"), "")
        val proof = if (signIn) deviceProof() else emptyMap()
        val result = guard {
            client.resetPassword(email.trim(), cleaned, newPassword, signIn, rememberMe, proof)
        }
        if (!signIn) {
            if (!result.data.optBoolean("password_updated", false)) {
                throw AuthV2ContractException("Unexpected reset response.")
            }
            return AuthV2ResetResult.PasswordUpdated(result.data.optBoolean("sessions_revoked", false))
        }
        if (!isCurrent()) throw ApiScopeChangedException()
        val session = (AuthV2SessionAdoption.outcomeOf(result) as? AuthV2AuthOutcome.Session)
            ?: throw AuthV2AdoptionException("The server did not complete sign-in. Try again.")
        adoptionSink.adopt(session.result)
        return AuthV2ResetResult.SignedIn(session.result.user.id, session.info)
    }

    // ---------------------------------------------------------------- recovery

    /** Forgot username (item 39). Always the same server message. */
    suspend fun forgotUsername(email: String): String = guard {
        client.forgotUsername(email.trim()).data.optStringOrNull("message")
            ?: "If the address is registered, the username is on its way."
    }

    /** Sends the email verification link (item 54). */
    suspend fun sendVerificationLink(email: String): String = guard {
        client.sendVerificationLink(email.trim()).data.optStringOrNull("message")
            ?: "If the address is registered, a verification link is on its way."
    }

    /** Confirms a one-time verification link token from the deep link. */
    suspend fun confirmVerificationLink(oneTimeToken: String): Boolean = guard {
        client.confirmVerificationLink(oneTimeToken.trim()).data.optBoolean("email_verified", false)
    }

    // ---------------------------------------------------------------- approval

    /** Approval-state read for the waiting-for-approval screen (item 42).
     *  Single shot; the UI decides when to re-ask. */
    suspend fun approvalState(approvalId: String, email: String): AuthV2Approval = guard {
        AuthV2Models.approval(client.approval(approvalId.trim(), email.trim()).data)
    }

    // ---------------------------------------------------------------- terms

    /** Current terms version/urls (item 56). */
    suspend fun terms(): AuthV2Terms = guard { AuthV2Models.terms(client.terms().data) }

    /** Authenticated terms acceptance. */
    suspend fun acceptTerms(version: String): Boolean = guard {
        client.acceptTerms(version.trim()).data.optBoolean("accepted", false)
    }

    // ---------------------------------------------------------------- workspaces

    /** The signed-in account's workspace accounts (item 35). */
    suspend fun myWorkspaces(): List<AuthV2WorkspaceAccount> = guard {
        AuthV2Models.workspaceAccounts(client.workspaces().data)
    }

    /** Switches to another active workspace account with the same email:
     *  fresh proof, fresh session, adopted through the existing gate. */
    suspend fun switchWorkspace(workspace: String): AuthV2SessionInfo {
        val proof = deviceProof()
        val result = guard { client.switchWorkspace(workspace.trim(), proof) }
        if (!isCurrent()) throw ApiScopeChangedException()
        val session = (AuthV2SessionAdoption.outcomeOf(result) as? AuthV2AuthOutcome.Session)
            ?: throw AuthV2AdoptionException("The workspace switch did not complete. Try again.")
        workspaceAdoption(session.result,workspace.trim())
        return session.info
    }

    // ---------------------------------------------------------------- sso

    /** SSO providers scaffold (item 43). */
    suspend fun ssoProviders(): List<AuthV2SsoProvider> = guard {
        client.providers().data.optJSONArray("providers")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { provider ->
                    val id = provider.optStringOrNull("id") ?: return@let null
                    AuthV2SsoProvider(id, provider.optStringOrNull("type") ?: "oidc",
                        provider.optStringOrNull("name") ?: id, provider.optBoolean("enabled", false))
                }
            }
        } ?: emptyList()
    }

    /** Starts SSO: open the returned url in a Custom Tab. No callback
     *  exchange exists yet (server returns 501 by design). */
    suspend fun startSso(providerId: String, redirectUri: String = "chaty://sso"): AuthV2SsoStart = guard {
        AuthV2Models.ssoStart(client.startSso(providerId.trim(), redirectUri).data)
    }

    // ---------------------------------------------------------------- internals

    private suspend fun <T> guard(block: () -> T): T = withContext(io) {
        try {
            block().also { currentCoroutineContext().ensureActive() }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (scope: ApiScopeChangedException) {
            throw scope
        } catch (disabled: ApiAuthV2Client.Disabled) {
            throw disabled
        } catch (rate: com.example.chat.data.api.AuthRateLimitException) {
            throw AuthV2FlowException(429, "RATE_LIMITED", rate.message ?: "Please wait before trying again.",rate.retryAfterSeconds)
        } catch (failure: ApiAuthV2Client.Failure) {
            throw AuthV2FlowException(failure.status, failure.code, userMessageFor(failure))
        }
    }

    private fun userMessageFor(failure: ApiAuthV2Client.Failure): String =
        AuthV2ErrorPolicy.message(failure.code,failure.status,failure.message)
}
