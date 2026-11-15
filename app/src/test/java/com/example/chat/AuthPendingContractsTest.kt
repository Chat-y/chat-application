package com.example.chat
import com.example.chat.data.auth.v2.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class AuthPendingContractsTest {
 @Test fun defaultInvitationNeverTrustsOrTransmitsClaims() = runBlocking {
  assertSame(InvitationVerification.Unavailable,AuthInvitationVerifier.unavailable.verify("arbitrary-untrusted-token","workspace"))
  assertFalse(AuthPendingContracts.inviteVerificationAvailable)
  assertFalse(AuthPendingContracts.ssoCallbackAvailable)
 }
 @Test fun consentNeedsEveryIndependentReadinessCondition() {
  for(gate in listOf(false,true)) for(published in listOf(false,true)) for(version in listOf(null,"","0.3")) {
   assertEquals(gate && published && !version.isNullOrBlank(),AuthConsentReadiness.mayCollectAcceptance(gate,version,published))
  }
  assertFalse(AuthPendingContracts.finalLegalPolicyAvailable)
  assertTrue(AuthPendingContracts.legalNotice.contains("No date of birth"))
 }
 @Test fun deferredCopyNeverClaimsVerifiedOrWorkingSso() {
  assertTrue(AuthPendingContracts.invitationNotice.contains("No invitation fields were trusted"))
  assertTrue(AuthPendingContracts.ssoNotice.contains("No sign-in page was opened"))
  assertTrue(AuthPendingContracts.revokedSessionNotice.contains("explicitly"))
 }
}
