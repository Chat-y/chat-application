package com.example.chat.ui.auth

/** Temporary app settings, not a statement of legal requirements. Review before production use. */
object AuthTemporaryPolicy {
    const val configuredMinimumAge=13
    const val ageRuleConfirmed=false
    const val temporaryTermsVersion="temp-0.4"
    const val companyPlaceholder="[Company name not configured]"
    const val contactPlaceholder="[Contact email not configured]"
    // A configured gate cannot be bypassed just because its published policy is missing.
    fun registrationBlockedByMissingPolicy() =
        com.example.chat.ReleaseFeatures.LEGAL_ACCEPTANCE_GATE && !acceptanceReady()
    fun acceptanceReady()=com.example.chat.data.auth.v2.AuthConsentReadiness.mayCollectAcceptance(
        com.example.chat.ReleaseFeatures.LEGAL_ACCEPTANCE_GATE,
        temporaryTermsVersion,com.example.chat.data.auth.v2.AuthPendingContracts.finalLegalPolicyAvailable)
}
