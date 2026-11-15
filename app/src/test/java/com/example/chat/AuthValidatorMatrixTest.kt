package com.example.chat

import com.example.chat.core.validators.ChatyValidators
import com.example.chat.ui.auth.OtpVerificationLogic
import org.junit.Assert.*
import org.junit.Test

/** Records existing client policy, not a new server contract. */
class AuthValidatorMatrixTest {
 @Test fun emailMatrixPreservesPlusTags() {
  for (value in listOf("maya@example.com", "maya+work@example.com", " maya@example.com ", "maya@sub.example.com"))
   assertNull(value, ChatyValidators.validateEmail(value))
  for (value in listOf("", "maya", "maya@", "@example.com", "maya @example.com"))
   assertNotNull(value, ChatyValidators.validateEmail(value))
 }
 @Test fun usernameMatrixRecordsCurrentAsciiAndReservedPolicy() {
  for (value in listOf("maya", "@maya", "maya_chat", "maya.123", " Maya "))
   assertNull(value, ChatyValidators.validateUsername(value))
  for (value in listOf("ab", "admin", "1234", ".maya", "maya.", "maya💬", "माया", "maya chat", "m".repeat(31)))
   assertNotNull(value, ChatyValidators.validateUsername(value))
  assertNull(ChatyValidators.validateUsername("admin", forRegistration = false))
 }
 @Test fun resetPasswordMatrixDoesNotChangeLoginRegistrationPolicy() {
  assertNull(ChatyValidators.validatePassword("StrongUnique!123"))
  for (value in listOf("", "Aa1!", "alllowercase1!", "ALLUPPERCASE1!", "NoNumberPassword!", "NoSymbolPassword1", "Aa1!" + "x".repeat(125)))
   assertNotNull(value, ChatyValidators.validatePassword(value))
 }
 @Test fun phoneMatrixPreservesExplicitInternationalNumbers() {
  for (value in listOf("+12025550123", "+1 (415) 555-0199", "1234567"))
   assertNull(value, ChatyValidators.validatePhone(value))
  for (value in listOf("123", "call me", "+" + "1".repeat(16)))
   assertNotNull(value, ChatyValidators.validatePhone(value))
 }
 @Test fun otpPasteMatrixRetainsCurrentSixDigitPolicy() {
  for (value in listOf("123456", "123 456", "123-456", " 12 34 56 "))
   assertEquals("123456", OtpVerificationLogic.sanitizeCode(value))
  assertEquals("123456", OtpVerificationLogic.sanitizeCode("123456789"))
  for (value in listOf("", "12345", "12345a")) assertFalse(OtpVerificationLogic.isComplete(value))
 }
 @Test fun monotonicCooldownHandlesBackgroundJumpsAndFractionalSeconds() {
  val deadline=OtpVerificationLogic.cooldownDeadline(1000L, 60)
  assertEquals(61_000L, deadline)
  for ((now, expected) in listOf(1000L to 60, 1001L to 60, 2000L to 59, 60_999L to 1, 61_000L to 0, 600_000L to 0))
   assertEquals(expected, OtpVerificationLogic.cooldownSeconds(deadline, now))
  assertEquals(1000L, OtpVerificationLogic.cooldownDeadline(0L, -1))
  assertEquals(3_600_000L, OtpVerificationLogic.cooldownDeadline(0L, Int.MAX_VALUE))
 }
}
