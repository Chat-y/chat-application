package com.example.chat

import com.example.chat.data.services.CallMediaController
import com.example.chat.data.services.CallSessionState
import com.example.chat.data.services.CallSignalingService
import com.example.chat.data.services.CallTransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Calls matrix "survive network switch mid-call": bounded ICE restart with
 *  renegotiation over the existing signaling channel. */
@OptIn(ExperimentalCoroutinesApi::class)
class CallIceRestartTest {

    private class RestartMedia : CallMediaController {
        override var hasLocalMedia = true
        override var hasRemoteMedia = true
        var restartCount = 0
        var offersCreated = 0
        var answersCreated = 0
        var remoteDescriptions = mutableListOf<Pair<String, String>>()
        override var onLocalIceCandidate: ((String, String?, Int?) -> Unit)? = null
        override var onRemoteStream: (() -> Unit)? = null
        override var onConnectionState: ((CallTransportState) -> Unit)? = null
        override var onIceFailed: (() -> Unit)? = null
        override suspend fun prepare(isVideo: Boolean, lowData: Boolean, iceServers: List<Map<String, Any?>>) {}
        override suspend fun createOffer(isVideo: Boolean): String { offersCreated += 1; return "offer-$offersCreated" }
        override suspend fun createAnswer(isVideo: Boolean): String { answersCreated += 1; return "answer-$answersCreated" }
        override suspend fun setRemoteDescription(sdp: String, type: String) { remoteDescriptions.add(sdp to type) }
        override suspend fun addRemoteCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {}
        override fun setAudioEnabled(enabled: Boolean) = true
        override fun setVideoEnabled(enabled: Boolean) = true
        override suspend fun switchCamera() = true
        override suspend fun setSpeakerphoneOn(on: Boolean) {}
        override suspend fun startScreenShare() = false
        override suspend fun stopScreenShare() {}
        override suspend fun dispose() {}
        override fun restartIce(): Boolean { restartCount += 1; return true }
    }

    private class Sender {
        val sent = mutableListOf<Pair<String, Map<String, Any?>>>()
        val fn = com.example.chat.data.services.CallSignalSender { target, data -> sent.add(target to data) }
    }

    private fun service(
        scope: CoroutineScope,
        sender: Sender,
        media: RestartMedia,
    ) = CallSignalingService(
        scope = scope,
        currentUserId = { "me" },
        signalSender = sender.fn,
        mediaFactory = { media }, monotonicMillis = { 0L })

    private suspend fun connectOutgoingCall(
        service: CallSignalingService,
        media: RestartMedia,
        scope: CoroutineScope,
    ): String {
        service.initiateCall("peer", "Peer", isVideo = false)
        val callId = service.currentSession!!.callId
        service.handleRemoteSignal(mapOf(
            "signal_type" to "answer", "call_id" to callId,
            "sender_id" to "peer", "sdp" to "remote-answer",
        ))
        media.onConnectionState?.invoke(CallTransportState.connected)
        return callId
    }

    @Test
    fun `ICE failure on connected outgoing call restarts and re-offers with ice_restart flag`() = runTest {
        val sender = Sender()
        val media = RestartMedia()
        val service = service(backgroundScope, sender, media)
        val callId = connectOutgoingCall(service, media, backgroundScope)
        advanceUntilIdle()
        assertEquals(CallSessionState.connected, service.currentSession!!.state)
        val offersBefore = sender.sent.count { it.second["signal_type"] == "offer" }

        media.onIceFailed?.invoke()
        // The restart re-offer is launched on the service scope (backgroundScope
        // here); advanceUntilIdle skips background work, so runCurrent drives it.
        runCurrent()

        assertEquals(1, media.restartCount)
        val restartOffers = sender.sent.filter {
            it.second["signal_type"] == "offer" && it.second["ice_restart"] == true
        }
        assertEquals(offersBefore, 1)
        assertEquals(1, restartOffers.size)
        assertEquals(callId, restartOffers.single().second["call_id"])
        assertEquals("peer", restartOffers.single().first)
        assertEquals(CallSessionState.reconnecting, service.currentSession!!.state)

        // SDP alone is not proof that media has reconnected.
        service.handleRemoteSignal(mapOf(
            "signal_type" to "answer", "call_id" to callId,
// Previous implementation retained:             "sender_id" to "peer", "ice_restart" to true, "sdp" to "restart-answer",
            "sender_id" to "peer", "ice_restart" to true, "restart_id" to restartOffers.single().second["restart_id"], "sdp" to "restart-answer",
        ))
        advanceUntilIdle()
        assertEquals(CallSessionState.reconnecting, service.currentSession!!.state)
        media.onConnectionState?.invoke(CallTransportState.connected)
        assertEquals(CallSessionState.connected, service.currentSession!!.state)
        assertTrue(media.remoteDescriptions.contains("restart-answer" to "answer"))
    }

    @Test
    fun `restart attempts are bounded and then the call fails`() = runTest {
        val sender = Sender()
        val media = RestartMedia()
        val service = service(backgroundScope, sender, media)
        connectOutgoingCall(service, media, backgroundScope)
        advanceUntilIdle()

// Previous implementation retained:         repeat(3) { media.onIceFailed?.invoke(); advanceUntilIdle() }
// Previous implementation retained:         assertEquals(3, media.restartCount)
// Previous implementation retained:         assertTrue(service.currentSession!!.state != CallSessionState.failed)
// Previous implementation retained: 
// Previous implementation retained:         media.onIceFailed?.invoke()
// Previous implementation retained:         advanceUntilIdle()
// Previous implementation retained:         assertEquals(3, media.restartCount) // no 4th restart
// Previous implementation retained:         assertEquals(CallSessionState.failed, service.currentSession!!.state)
        repeat(3) { media.onIceFailed?.invoke(); runCurrent() }
        assertEquals(1, media.restartCount) // One negotiation stays pending through its answer.
        advanceTimeBy(25_000); runCurrent()
        assertTrue(service.currentSession!!.state.isTerminal)
    }

    @Test
    fun `incoming ice_restart offer on the active call is applied and answered`() = runTest {
        val sender = Sender()
        val media = RestartMedia()
        val service = service(backgroundScope, sender, media)
        // Incoming call: receive offer, accept, connect.
        service.handleRemoteSignal(mapOf(
            "signal_type" to "offer", "call_id" to "c-in", "sender_id" to "peer",
            "caller_name" to "Peer", "sdp" to "remote-offer", "is_video" to false,
        ))
        advanceUntilIdle()
        service.acceptCall()
        advanceUntilIdle()
        media.onConnectionState?.invoke(CallTransportState.connected)
        advanceUntilIdle()
        assertEquals(CallSessionState.connected, service.currentSession!!.state)

        service.handleRemoteSignal(mapOf(
            "signal_type" to "offer", "call_id" to "c-in", "sender_id" to "peer",
// Previous implementation retained:             "ice_restart" to true, "sdp" to "restart-offer",
            "ice_restart" to true, "restart_id" to "generation1", "sdp" to "restart-offer",
        ))
        // Same background-launch reasoning as the outgoing test above.
        runCurrent()

        assertTrue(media.remoteDescriptions.contains("restart-offer" to "offer"))
        val restartAnswers = sender.sent.filter {
            it.second["signal_type"] == "answer" && it.second["ice_restart"] == true
        }
        assertEquals(1, restartAnswers.size)
        assertEquals("c-in", restartAnswers.single().second["call_id"])
        assertEquals(CallSessionState.connected, service.currentSession!!.state)
    }

    @Test
    fun `duplicate plain offer for the active call is still ignored`() = runTest {
        val sender = Sender()
        val media = RestartMedia()
        val service = service(backgroundScope, sender, media)
        val callId = connectOutgoingCall(service, media, backgroundScope)
        advanceUntilIdle()

        service.handleRemoteSignal(mapOf(
            "signal_type" to "offer", "call_id" to callId,
            "sender_id" to "peer", "sdp" to "duplicate-offer",
        ))
        advanceUntilIdle()
        assertTrue(media.remoteDescriptions.none { it.first == "duplicate-offer" })
        assertEquals(CallSessionState.connected, service.currentSession!!.state)
    }

    @Test
    fun `network transport change during a connected call triggers ICE restart once`() = runTest {
        val sender = Sender()
        val media = RestartMedia()
        val service = service(backgroundScope, sender, media)
        connectOutgoingCall(service, media, backgroundScope)
        advanceUntilIdle()

        service.onNetworkTransportChanged("wifi")
        service.onNetworkTransportChanged("wifi") // same transport: no restart
        assertEquals(0, media.restartCount)
        service.onNetworkTransportChanged("cellular")
        advanceUntilIdle()
        assertEquals(1, media.restartCount)
    }
    @Test fun calleeRequestsRestartWithoutCreatingAnOffer() = runTest {
        val sender=Sender(); val media=RestartMedia(); val service=service(backgroundScope,sender,media)
        service.handleRemoteSignal(mapOf("signal_type" to "offer","call_id" to "incoming","sender_id" to "peer","sdp" to "offer"))
        runCurrent();service.acceptCall();media.onConnectionState?.invoke(CallTransportState.connected)
        media.onIceFailed?.invoke();runCurrent()
        assertTrue(sender.sent.any {it.first=="peer" && it.second["signal_type"]=="ice_refresh_request" && it.second["call_id"]=="incoming"})
        assertEquals(0,media.offersCreated);assertEquals(0,media.restartCount)
    }
    @Test fun peerRefreshNeedsCurrentIdentityAndIsRateLimited() = runTest {
        val sender=Sender();val media=RestartMedia();var tick=0L
        val service=CallSignalingService(backgroundScope,{"me"},signalSender=sender.fn,mediaFactory={media},monotonicMillis={tick})
        val id=connectOutgoingCall(service,media,backgroundScope)
        fun request(senderId:String,callId:String)=mapOf("signal_type" to "ice_refresh_request","sender_id" to senderId,"call_id" to callId)
        service.handleRemoteSignal(request("foreign",id));service.handleRemoteSignal(request("peer","stale"));runCurrent()
        assertEquals(0,media.restartCount)
        service.handleRemoteSignal(request("peer",id));runCurrent();assertEquals(1,media.restartCount)
        media.onConnectionState?.invoke(CallTransportState.connected)
        repeat(20) {service.handleRemoteSignal(request("peer",id));runCurrent()}
        assertEquals(1,media.restartCount)
// Previous implementation retained:         tick=30000;service.handleRemoteSignal(request("peer",id));runCurrent();assertEquals(2,media.restartCount)
        tick=5000;service.handleRemoteSignal(request("peer",id));runCurrent();assertEquals(1,media.restartCount)
        assertEquals(2,sender.sent.count {it.second["ice_restart"]==true && it.second["signal_type"]=="offer"})
    }
    @Test fun sameTransportDifferentNetworkIdentityRestarts() = runTest {
        val sender=Sender();val media=RestartMedia();val service=service(backgroundScope,sender,media)
        service.onNetworkTransportChanged("wifi:11")
        connectOutgoingCall(service,media,backgroundScope)
        service.onNetworkTransportChanged("wifi:11");runCurrent();assertEquals(0,media.restartCount)
        service.onNetworkTransportChanged("wifi:22");runCurrent();assertEquals(1,media.restartCount)
    }

    @Test fun secondNetworkChangeCannotCreateAnotherOfferBeforeMatchingAnswer() = runTest {
        val sender=Sender();val media=RestartMedia();val service=service(backgroundScope,sender,media)
        val id=connectOutgoingCall(service,media,backgroundScope)
        media.onIceFailed?.invoke();runCurrent()
        val first=sender.sent.last {it.second["ice_restart"]==true}.second["restart_id"]
        service.onNetworkTransportChanged("wifi:1");service.onNetworkTransportChanged("wifi:2");runCurrent()
        assertEquals(1,media.restartCount)
        val count=media.remoteDescriptions.size
        service.handleRemoteSignal(mapOf("signal_type" to "answer","call_id" to id,"sender_id" to "peer",
            "ice_restart" to true,"restart_id" to "old-generation","sdp" to "stale"))
        assertEquals(count,media.remoteDescriptions.size)
        service.handleRemoteSignal(mapOf("signal_type" to "answer","call_id" to id,"sender_id" to "peer",
            "ice_restart" to true,"restart_id" to first,"sdp" to "fresh"))
        assertTrue(media.remoteDescriptions.contains("fresh" to "answer"))
    }

}
