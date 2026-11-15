package com.example.chat
import com.example.chat.ui.auth.AuthLinkPolicy as P
import org.junit.Assert.*
import org.junit.Test
class AuthLinkPolicyTest {
 @Test fun cameraAndAppLinksShareStrictParser() {
  assertEquals("acme_1",P.parse("chaty://workspace?workspace=acme_1")?.workspace)
  assertEquals("invite",P.parse("chaty://invite?workspace=acme&token=abcdefghijklmnop")?.kind)
  assertEquals("verify-email",P.parse("chaty://verify-email?token=abcdefghijklmnop")?.kind)
  assertFalse(P.parse("chaty://invite?token=abcdefghijklmnop").toString().contains("abcdefghijklmnop"))
 }
 @Test fun rejectsAmbiguousDuplicateAndEndpointInjection() {
  for(value in listOf("https://workspace?workspace=acme","chaty://evil@workspace?workspace=acme","chaty://workspace?workspace=a&workspace=b","chaty://workspace?workspace=https%3A%2F%2Fevil.invalid","chaty://workspace/path?workspace=a","chaty://invite?token=short","chaty://invite?token=abcdefghijklmnop%20qrst","chaty://verify-email?token=abcdefghijklmnop%0A","chaty://workspace?workspace=a#b")) assertNull(value,P.parse(value))
 }
}
