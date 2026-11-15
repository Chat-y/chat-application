package com.example.chat
import com.example.chat.ui.auth.AuthWorkspacePolicy as P
import org.junit.Assert.*
import org.junit.Test
class AuthWorkspacePolicyTest {
 @Test fun formattingIsAnExplicitSuggestionNotAnIdentityRewrite() {
  assertEquals("acme-team",P.slugSuggestion(" Acme Team "))
  assertNull(P.slugSuggestion("acme-team"));assertNull(P.slugSuggestion("https://evil.invalid"));assertNull(P.slugSuggestion("माया"))
 }
 @Test fun autocompleteUsesOnlyStoredCandidatesAndPreservesTheirCase() {
  assertEquals(listOf("Acme_1","acme-2"),P.suggestions("ac",listOf("Acme_1","acme-2","Other")))
  assertEquals(emptyList<String>(),P.suggestions("ACME",listOf("ACME")))
  assertEquals(3,P.suggestions("",listOf("a","b","c","d")).size)
 }
}
