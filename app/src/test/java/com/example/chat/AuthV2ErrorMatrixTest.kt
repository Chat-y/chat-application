package com.example.chat
import com.example.chat.data.auth.v2.AuthV2ErrorPolicy as P
import org.junit.Assert.*
import org.junit.Test
class AuthV2ErrorMatrixTest {
 @Test fun everyDocumentedCodeHasActionAndSafeFallback() {
  assertEquals(32,P.documentedCodes.size)
  for((code,action) in P.documentedCodes) {
   assertEquals(action,P.action(code,400));assertTrue(P.message(code,400,null).isNotBlank())
   assertEquals("Server readable explanation",P.message(code,400,"Server readable explanation"))
  }
 }
 @Test fun unknownAndUnstructuredHttpResponsesStayGeneric() {
  for(status in listOf(400,401,403,404,409,422)) assertEquals(P.Action.Generic,P.action("NEW_UNKNOWN_CODE",status))
  assertEquals(P.Action.RetryLater,P.action(null,429));assertEquals(P.Action.RetryLater,P.action(null,503))
  assertFalse(P.message(null,401,null).contains("account exists"))
 }
}
