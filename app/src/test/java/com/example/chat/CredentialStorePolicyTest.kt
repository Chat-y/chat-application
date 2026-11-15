package com.example.chat
import com.example.chat.core.auth.CredentialStorePolicy as P
import org.junit.Assert.*
import org.junit.Test
class CredentialStorePolicyTest {
 @Test fun onlyConfirmedNonDemoPasswordsCanBeSaved() {
  assertTrue(P.canOffer("a@example.com","secret",true,false))
  assertFalse(P.canOffer("a@example.com","secret",false,false))
  assertFalse(P.canOffer("a@example.com","secret",true,true))
  assertFalse(P.canOffer(" ","secret",true,false))
  assertFalse(P.canOffer("name","",true,false))
 }
 @Test fun credentialRepresentationNeverRevealsFields() {
  val value=com.example.chat.core.auth.PasswordCredentialStore.RetrieveOutcome.Found("private@example.com","secret")
  assertFalse(value.toString().contains("private"));assertFalse(value.toString().contains("secret"))
 }
}
