package com.example.chat
import com.example.chat.data.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScopedCallCoordinatorTest {
 @Test fun oldUnauthorizedCannotSuspendNewAccountPolling() = runTest {
  val lock=Any();var a=ApiAccountScope("a","token","tenant","https://example.invalid","",1)
  var requests=0;var cancels=0
  lateinit var coordinator:ScopedCallCoordinator
  coordinator=ScopedCallCoordinator(backgroundScope,{synchronized(lock){a}}, { expected, block -> synchronized(lock){if(a !== expected)false else {block();true}} },
   {expected, wire->ApiCallSignalClient({if(a === expected)wire else null},ApiHttpTransport{_,_,_,_->
    requests++;if(requests==1){synchronized(lock){a=ApiAccountScope("b","token","tenant","https://example.invalid","",2);coordinator.transition(a)};401 to ""}else 200 to "{\"data\":[]}"})},
   {cancels++},{_,_->fail("No signals")},{0})
  coordinator.transition(a);coordinator.foreground(true);coordinator.tick();runCurrent()
  coordinator.tick();runCurrent();assertEquals(2,requests);assertTrue(cancels>=2)
 }
 @Test fun cadenceHasOnePollAndBacksOffFailures() = runTest {
  val a=ApiAccountScope("a","t","tenant","https://example.invalid","",1);var now=0L;var requests=0
  val coordinator=ScopedCallCoordinator(backgroundScope,{a},{e,b->if(e === a){b();true}else false},
   {_,wire->ApiCallSignalClient({wire},ApiHttpTransport{_,_,_,_->requests++;500 to ""})},{},{_,_->},{now})
  coordinator.transition(a);coordinator.foreground(true);coordinator.tick();coordinator.tick();runCurrent();assertEquals(1,requests)
  now=4000;coordinator.tick();runCurrent();assertEquals(1,requests)
  now=8000;coordinator.tick();runCurrent();assertEquals(2,requests)
 }
 @Test fun resetAdoptAndSignoutLoginUseCurrentScopeForPostAndPoll() = runTest {
  var owner=ApiAccountScope("a","t","tenant","https://example.invalid","",1)
  var sessionJob=SupervisorJob();var sessionScope=CoroutineScope(coroutineContext+sessionJob)
  val methods=mutableListOf<String>()
  val coordinator=ScopedCallCoordinator(sessionScope,{owner},{e,b->if(e===owner){b();true}else false},
   {expected,wire->ApiCallSignalClient({if(owner===expected)wire else null},ApiHttpTransport { method,_,_,_->
    methods+=method;200 to if(method=="GET") "{\"data\":[]}" else "{}" })},{},{_,_->},{0},launchScope={sessionScope})
  coordinator.transition(owner)
  fun reset(next:ApiAccountScope) {sessionJob.cancel();sessionJob=SupervisorJob();sessionScope=CoroutineScope(coroutineContext+sessionJob);owner=next;coordinator.transition(next)}
  val stale=owner
  reset(ApiAccountScope("b","t","tenant","https://example.invalid","",2))
  coordinator.send(owner,"peer",mapOf("call_id" to "c","signal_type" to "offer"));coordinator.pollNow(owner);runCurrent()
  assertEquals(listOf("POST","GET"),methods)
  coordinator.send(stale,"peer",mapOf("call_id" to "old","signal_type" to "offer"));coordinator.pollNow(stale);runCurrent()
  assertEquals(2,methods.size)
  reset(ApiAccountScope(null,null,"tenant","https://example.invalid","",3))
  coordinator.pollNow();runCurrent();assertEquals(2,methods.size)
  reset(ApiAccountScope("a","t2","tenant","https://example.invalid","",4))
  coordinator.send(owner,"peer",mapOf("call_id" to "new","signal_type" to "answer"));coordinator.pollNow();runCurrent()
  assertEquals(listOf("POST","GET","POST","GET"),methods);sessionJob.cancel()
 }

}
