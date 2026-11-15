package com.example.chat
import com.example.chat.ui.auth.AuthFormPolicy
import org.junit.Assert.*
import org.junit.Test
class AuthFormPolicyTest {
 @Test fun suggestionsAreAdvisoryAndRetainPlusAddressLocalPart() {
  assertEquals("name+tag@gmail.com", AuthFormPolicy.emailSuggestion(" name+tag@gmial.com "))
  assertNull(AuthFormPolicy.emailSuggestion("name@custom.example"))
  assertNull(AuthFormPolicy.emailSuggestion("name"))
 }
 @Test fun errorsDoNotRepeatTypedEmails() {
  assertEquals("Already registered: your email address",AuthFormPolicy.privateError("Already registered: name+tag@example.com", "name+tag@example.com"))
  assertEquals("Incorrect credentials",AuthFormPolicy.privateError("Incorrect credentials", "name+tag@example.com"))
 }
 @Test fun supportEmailIsNotErased() {
  assertEquals("Contact support@chaty.app", AuthFormPolicy.privateError("Contact support@chaty.app", "name@example.com"))
  assertEquals("Contact support@chaty.app", AuthFormPolicy.privateError("Contact support@chaty.app", ""))
 }
 @Test fun clipboardClearRequiresExactCodeOrUnchangedExplicitPaste() {
  assertTrue(AuthFormPolicy.shouldClearCodeClipboard("123456", "123456", null))
  assertTrue(AuthFormPolicy.shouldClearCodeClipboard("123-456", "123456", "123-456"))
  assertFalse(AuthFormPolicy.shouldClearCodeClipboard("123-456", "123456", null))
  assertFalse(AuthFormPolicy.shouldClearCodeClipboard("123 456", "123456", "123-456"))
  assertFalse(AuthFormPolicy.shouldClearCodeClipboard(null, "123456", null))
 }
 @Test fun strengthIsAdvisoryNotAValidationRule() {
  assertEquals("",AuthFormPolicy.strengthLabel(""))
  assertEquals("Consider a longer, unique password",AuthFormPolicy.strengthLabel("abcdef"))
  assertEquals("Stronger password",AuthFormPolicy.strengthLabel("LongUnique!Pass23"))
 }
 @Test fun errorKindsRequireActualEvidence() {
  assertTrue(AuthFormPolicy.connectionProblem("Unable to connect to server"))
  assertFalse(AuthFormPolicy.connectionProblem("Incorrect credentials"))
  assertTrue(AuthFormPolicy.rateLimited("Too many attempts"))
  assertFalse(AuthFormPolicy.rateLimited("Incorrect code"))
 }
 @Test fun serverNetworkAndRateLimitRemainDistinctAndNeverInventAccountState() {
  assertEquals(AuthFormPolicy.ErrorKind.Connection, AuthFormPolicy.errorKind("Unable to reach server"))
  assertEquals(AuthFormPolicy.ErrorKind.Server, AuthFormPolicy.errorKind("Authentication failed (503)"))
  assertEquals(AuthFormPolicy.ErrorKind.RateLimit, AuthFormPolicy.errorKind("HTTP 429 Too many attempts"))
  assertEquals(AuthFormPolicy.ErrorKind.Other, AuthFormPolicy.errorKind("Incorrect credentials"))
  assertEquals(AuthFormPolicy.ErrorKind.Other, AuthFormPolicy.errorKind("Username maya500 is taken"))
 }
 @Test fun otpResumeCannotCrossWorkspaceServerOrVerifiedAccount() {
  fun can(pending:Boolean=true, email:String="maya@example.com",tenant:String="qa",base:String="https://test.invalid",verify:Boolean=true,authed:Boolean=true,user:String?="u1") =
   AuthFormPolicy.canResumeOtp(pending,email,"qa",tenant,"https://test.invalid",base,verify,authed,"u1",user)
  assertTrue(can());assertFalse(can(pending=false));assertFalse(can(email=""))
  assertFalse(can(tenant="other"));assertFalse(can(base="https://other.invalid"))
  assertFalse(can(user="u2"));assertFalse(can(authed=false))
  assertTrue(can(verify=false,authed=false,user=null)) // Reset OTP doesn't manufacture a signed-in session.
 }
 @org.junit.Test fun confirmationMustBePresentAndMatch() {
  org.junit.Assert.assertFalse(com.example.chat.ui.auth.AuthFormPolicy.confirmationMatches("secret123", ""))
  org.junit.Assert.assertFalse(com.example.chat.ui.auth.AuthFormPolicy.confirmationMatches("", ""))
  org.junit.Assert.assertFalse(com.example.chat.ui.auth.AuthFormPolicy.confirmationMatches("secret123", "other"))
  org.junit.Assert.assertTrue(com.example.chat.ui.auth.AuthFormPolicy.confirmationMatches("secret123", "secret123"))
 }
}
