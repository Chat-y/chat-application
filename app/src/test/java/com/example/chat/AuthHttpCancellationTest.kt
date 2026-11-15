package com.example.chat
import com.example.chat.data.api.AuthHttpCallContext as C
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class AuthHttpCancellationTest {
 @Test fun cancellationCancelsOnlyOwnedCallAndCleanupStopsNotifications()=runBlocking {
  var cancelled=0
  val started=CompletableDeferred<Unit>()
  val job=launch(Dispatchers.Default) {C.owned {
   val handle=C.onCancellation {cancelled++}
   started.complete(Unit)
   try {awaitCancellation()} finally {handle?.dispose()}
  }}
  started.await();job.cancelAndJoin();assertEquals(1,cancelled)
  assertNull(C.onCancellation {fail("owner leaked")})
 }
 @Test fun successfulCallDisposesWithoutCancellation()=runBlocking {
  var cancelled=false
  C.owned {C.onCancellation {cancelled=true}?.dispose()}
  assertFalse(cancelled)
 }
}
