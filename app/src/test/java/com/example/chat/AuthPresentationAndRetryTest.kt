package com.example.chat
import com.example.chat.ui.auth.AuthPresentationPolicy as P
import com.example.chat.data.api.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AuthPresentationAndRetryTest {
 @Test fun contrastFallbackMeetsAAWithoutChangingPassingChoices() {
  val backgrounds=listOf(0xff000000.toInt(),0xffffffff.toInt(),0xff777777.toInt(),0xff6366f1.toInt(),0xfffaf3ed.toInt())
  for(bg in backgrounds) for(fg in backgrounds) assertTrue(P.contrast(P.readableText(fg,bg),bg)>=4.5)
  assertEquals(0xff000000.toInt(),P.readableText(0xff000000.toInt(),0xffffffff.toInt()))
 }
 @Test fun twoPaneFallsBackForLargeFontsAndShortWindows() {
  assertTrue(P.twoPane(1000f,800f,1f));assertFalse(P.twoPane(400f,800f,1f))
  assertFalse(P.twoPane(1000f,800f,2f));assertFalse(P.twoPane(1000f,400f,1f))
 }
 @Test fun greetingsUseHourNotStoredPersonalData() {
  assertEquals("Good morning",P.greeting(8));assertEquals("Good afternoon",P.greeting(13))
  assertEquals("Good evening",P.greeting(22))
 }
 @Test fun retryAfterUsesActualHeaderAndBounds() {
  val now=Instant.parse("2026-11-15T06:00:00Z")
  assertNull(AuthRateLimitTransport.parseRetryAfter(null,now));assertNull(AuthRateLimitTransport.parseRetryAfter("later",now))
  assertEquals(30,AuthRateLimitTransport.parseRetryAfter("30",now))
  assertEquals(0,AuthRateLimitTransport.parseRetryAfter("-2",now))
  assertEquals(86400,AuthRateLimitTransport.parseRetryAfter("9999999",now))
  assertEquals(60,AuthRateLimitTransport.parseRetryAfter("Sun, 15 Nov 2026 06:01:00 GMT",now))
 }
 @Test fun transportHonors429OnlyAndNeverRetries() {
  var requests=0
  val delegate=object:ApiHeaderHttpTransport {
   override fun execute(method:String,url:String,headers:Map<String,String>,body:String?) = 429 to ""
   override fun executeWithHeaders(method:String,url:String,headers:Map<String,String>,body:String?):ApiHeaderResponse {
    requests++;return ApiHeaderResponse(429,"""{"detail":"Please wait."}""",null,"12")
   }
  }
  try {AuthRateLimitTransport(delegate).execute("POST","https://test.invalid",emptyMap(),"{}");fail("wait expected")}
  catch(e:AuthRateLimitException){assertEquals(12,e.retryAfterSeconds);assertEquals("Please wait.",e.message)}
  assertEquals(1,requests)
 }
}
