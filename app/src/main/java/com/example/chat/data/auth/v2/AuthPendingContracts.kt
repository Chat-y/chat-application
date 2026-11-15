package com.example.chat.data.auth.v2

/** Client state for missing deployment/legal inputs. No endpoint or business fact is guessed. */
object AuthPendingContracts {
    const val inviteVerificationAvailable=false
    const val ssoCallbackAvailable=false
    const val finalLegalPolicyAvailable=false
    const val termsDraftVersion="0.3"
    const val invitationNotice="Invitation verification is not available for this workspace. Ask your administrator for a Workspace ID. No invitation fields were trusted or locked."
    const val ssoNotice="SSO is deferred. No sign-in page was opened. Use an available sign-in method or contact your workspace administrator."
    const val legalNotice="Legal policy is a review draft. Minimum age and any parent or guardian consent requirements have not been set. No date of birth or acceptance is collected from this draft."
    const val revokedSessionNotice="This session is no longer valid. Sign in again explicitly. Your password is not stored for automatic sign-in."
}

/** Explicit fail-closed seam. An adapter must be supplied from a documented backend contract.
 * The default never transmits a token and cannot create a trusted invitation or session. */
fun interface AuthInvitationVerifier {
    suspend fun verify(token:String,workspaceHint:String?):InvitationVerification
    companion object {
        val unavailable=AuthInvitationVerifier {_,_->InvitationVerification.Unavailable}
    }
}
sealed class InvitationVerification {
    data object Unavailable:InvitationVerification()
    data object ExpiredOrRejected:InvitationVerification()
    data object Expired:InvitationVerification()
    data object Redeemed:InvitationVerification()
    class Valid(val workspace:String,val workspaceName:String,val boundEmail:String?,val expiresAt:java.time.Instant,val oneUse:Boolean):InvitationVerification() {override fun toString()="ValidInvitation(redacted)"}
    /** This is only a navigation prefill. It does NOT establish signup authority or ownership. */
    class WorkspacePrefill(val workspace:String):InvitationVerification() {
        override fun toString()="InvitationWorkspacePrefill(redacted)"
    }
}

/** No age threshold may be inferred from country, draft text or a guessed default. */
object AuthConsentReadiness {
    enum class State { DraftOnly, NeedsPublishedVersion, Ready }
    fun state(gateEnabled:Boolean,version:String?,publishedPolicyConfirmed:Boolean):State = when {
        !gateEnabled || !publishedPolicyConfirmed->State.DraftOnly
        version.isNullOrBlank()->State.NeedsPublishedVersion
        else->State.Ready
    }
    fun mayCollectAcceptance(gateEnabled:Boolean,version:String?,publishedPolicyConfirmed:Boolean)=
        state(gateEnabled,version,publishedPolicyConfirmed)==State.Ready
}

/** Backend invitation contract: unpadded URL-safe base64, capped at 512 characters. */
object AuthInvitationTokenPolicy {
    const val MAX_LENGTH = 512
    fun valid(token: String): Boolean = token.matches(Regex("[A-Za-z0-9_-]{16,$MAX_LENGTH}"))
}
