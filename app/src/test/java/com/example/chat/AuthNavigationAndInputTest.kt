package com.example.chat
import com.example.chat.ui.auth.AuthNavigationPolicy as N
import com.example.chat.ui.auth.AuthInputPolicy as I
import org.junit.Assert.*
import org.junit.Test
class AuthNavigationAndInputTest {
 @Test fun restorationRequiresExactAccountWorkspaceAndServer() {
  assertTrue(N.mayResume("u","w","https://one","u","w","https://one"))
  for(args in listOf(listOf("v","w","https://one"),listOf("u","x","https://one"),listOf("u","w","https://two")))
   assertFalse(N.mayResume("u","w","https://one",args[0],args[1],args[2]))
  assertFalse(N.mayResume(null,"w","https://one",null,"w","https://one"))
 }
 @Test fun signedInGuardPreservesVerificationAndReauthentication() {
  assertTrue(N.shouldLeaveAuth(true,false,false))
  assertFalse(N.shouldLeaveAuth(true,true,false));assertFalse(N.shouldLeaveAuth(true,false,true));assertFalse(N.shouldLeaveAuth(false,false,false))
 }
 @Test fun legacyPasswordAndWorkspaceMatrixNeverAddsMaximumOrRewritesSlug() {
  for(value in listOf("123456","a".repeat(300),"password 💬")) assertNull(I.legacyPasswordError(value))
  for(value in listOf("","12345")) assertNotNull(I.legacyPasswordError(value))
  assertNull(I.workspaceError("",false));assertNotNull(I.workspaceError(" ",true));assertNull(I.workspaceError("ACME_01",true))
  assertEquals("ACME_01",I.workspaceDisplay(" ACME_01 "))
 }
 @Test fun codeErrorCategoriesOnlyUseSuppliedServerMessage() {
  assertEquals(I.CodeError.Expired,I.codeError("This code expired"))
  assertEquals(I.CodeError.Wrong,I.codeError("Incorrect code"))
  assertEquals(I.CodeError.TooMany,I.codeError("Too many attempts"))
  assertEquals(I.CodeError.Other,I.codeError("Please contact support"))
 }
}
