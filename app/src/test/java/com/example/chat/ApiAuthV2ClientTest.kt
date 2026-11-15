package com.example.chat

import com.example.chat.data.api.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiAuthV2ClientTest {
 private data class Call(val method: String,val url:String,val headers:Map<String,String>,val body:String?)
 private class Transport : ApiHttpTransport {
  val calls=mutableListOf<Call>(); var status=200;var response="""{"success":true,"data":{"status":"success"}}"""
  var after:()->Unit={}
  override fun execute(method:String,url:String,headers:Map<String,String>,body:String?):Pair<Int,String> {
   calls+=Call(method,url,headers,body); after(); return status to response
  }
 }
 private val proof=mapOf("x-device-id" to "test-id","x-device-public-key" to "test-public","x-device-challenge-id" to "test-challenge","x-device-proof" to "test-proof")
 private fun client(t:Transport, current:()->Boolean={true})=ApiAuthV2Client("https://test.invalid/","qa",t,"test-access",current,{true})
 @Test fun productionFlagOffMeansZeroRequests() {
  val t=Transport();val c=ApiAuthV2Client("https://test.invalid","qa",t)
  try { c.onboarding("qa");fail("disabled expected") } catch (_:ApiAuthV2Client.Disabled) { }
  assertEquals(0,t.calls.size);assertFalse(ReleaseFeatures.AUTH_V2_CONTRACTS)
 }
 @Test fun publicRoutesEncodePathAndDoNotLeakAccountToken() {
  val t=Transport();val c=client(t)
  c.onboarding("one/two");c.discover("QA & chat");c.domainSuggestion("example.com");c.usernameAlternatives("maya+work")
  assertTrue(t.calls[0].url.endsWith("organization/onboarding/one%2Ftwo"))
  assertTrue(t.calls[1].url.contains("query=QA+%26+chat"))
  for(call in t.calls) { assertEquals("qa",call.headers["x-tenant-id"]);assertFalse(call.headers.containsKey("Authorization")) }
 }
 @Test fun registrationKeyIsStableAcrossStartRetryAndCompletion() {
  val t=Transport();val c=client(t);val key=ApiAuthV2Client.RegistrationKey.create()
  t.status=202;t.response="""{"data":{"status":"registration_received"}}"""
  repeat(2) { c.registerStart("maya+tag@example.com","maya","password9","+12025550123",key) }
  t.response="""{"data":{"status":"pending_approval","approval_id":"approval"}}"""
  val result=c.registerComplete("maya+tag@example.com","123456","maya",key,proof)
  assertTrue(result.pendingApproval);assertEquals(202,result.status)
  assertEquals(1,t.calls.map { it.headers["Idempotency-Key"] }.distinct().size)
  assertEquals("maya+tag@example.com",JSONObject(t.calls[0].body!!).getString("email"))
  assertFalse(result.toString().contains("approval_id"))
 }
 @Test fun deviceProofRequiredAndAuthenticatedReadsStayScoped() {
  val t=Transport();val c=client(t)
  try { c.login("maya","password9",true,emptyMap());fail("proof required") } catch (_:IllegalArgumentException) {}
  assertEquals(0,t.calls.size)
  c.login("maya","password9",true,proof);c.workspaces();c.switchWorkspace("other",proof)
  assertEquals(true,JSONObject(t.calls[0].body!!).getBoolean("remember_me"))
  assertEquals("Bearer test-access",t.calls[1].headers["Authorization"])
  assertEquals("test-proof",t.calls[2].headers["x-device-proof"])
 }
 @Test fun staleScopeBlocksBeforeAndAfterNetworkAndNeverRetries() {
  val t=Transport();var current=false;val c=client(t,{current})
  try { c.terms();fail("scope expected") } catch (_:ApiScopeChangedException) {}
  assertEquals(0,t.calls.size)
  current=true;t.after={current=false}
  try { c.terms();fail("scope expected") } catch (_:ApiScopeChangedException) {}
  assertEquals(1,t.calls.size)
 }
 @Test fun structuredErrorNeverFallsBackToLegacyPost() {
  val t=Transport();val c=client(t);t.status=409
  t.response="""{"detail":{"code":"IDEMPOTENCY_KEY_REUSED","message":"Start a new registration."}}"""
  try { c.registerStart("maya@example.com","maya","password9","+12025550123",ApiAuthV2Client.RegistrationKey.create());fail("failure expected") }
  catch(e:ApiAuthV2Client.Failure) {assertEquals(409,e.status);assertEquals("IDEMPOTENCY_KEY_REUSED",e.code)}
  assertEquals(1,t.calls.size)
 }
 @Test fun allAdditiveReadAndRecoveryRoutesUseDocumentedMethods() {
  val t=Transport();val c=client(t)
  c.terms();c.providers();c.startSso("test-idp");c.forgotUsername("maya@example.com")
  c.sendVerificationLink("maya@example.com");c.confirmVerificationLink("one/time?token")
  c.approval("approval/id","maya+tag@example.com");c.acceptTerms("version1")
  c.resetPassword("maya@example.com","123456","password9",false)
  assertEquals(listOf("GET","GET","POST","POST","POST","POST","GET","POST","POST"),t.calls.map{it.method})
  assertTrue(t.calls[5].url.contains("token=one%2Ftime%3Ftoken"))
  assertTrue(t.calls[6].url.contains("approval%2Fid?email=maya%2Btag%40example.com"))
  assertEquals(false,JSONObject(t.calls.last().body!!).getBoolean("sign_in"))
 }
 @Test fun resetSignInRequiresProofAndUnicodeByteLimitIsSpecificToV2() {
  val t=Transport();val c=client(t)
  try {c.resetPassword("a@example.com","123456","password9",true);fail("proof required")} catch(_:IllegalArgumentException){}
  try {c.registerStart("a@example.com","maya","💬".repeat(20),"+12025550123",ApiAuthV2Client.RegistrationKey.create());fail("byte limit expected")} catch(_:IllegalArgumentException){}
  assertEquals(0,t.calls.size)
 }
}
