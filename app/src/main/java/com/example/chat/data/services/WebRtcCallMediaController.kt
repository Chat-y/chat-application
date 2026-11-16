package com.example.chat.data.services

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** Render seam consumed by the Compose call surfaces: the Dart screens read
 *  RTCVideoRenderer-attached MediaStreams from the signaling service; here the
 *  UI hands SurfaceViewRenderers to the live media controller instead. */
interface CallVideoSurfaceProvider {
    val eglContext: EglBase.Context
    val hasRemoteVideo: Boolean
    val isFrontCamera: Boolean
    /** Native track reference identities only, never user/account data. Optional for previews. */
    val localRendererBindingIdentity: Any? get() = null
    val remoteRendererBindingIdentity: Any? get() = null
    fun attachLocalRenderer(renderer: org.webrtc.SurfaceViewRenderer)
    fun attachRemoteRenderer(renderer: org.webrtc.SurfaceViewRenderer)
    fun detachRenderer(renderer: org.webrtc.SurfaceViewRenderer)
}

/**
 * org.webrtc-backed [CallMediaController]: the native counterpart of the
 * flutter_webrtc calls in call_signaling_service.dart. WebRTC is the media
 * source of truth; this class owns the peer connection, local/remote/screen
 * streams and track-level controls.
 *
 * Video surfaces are NOT created here: the Compose UI creates
 * SurfaceViewRenderers and attaches them via [attachLocalRenderer] /
 * [attachRemoteRenderer] so the service layer stays UI-free.
 */
class WebRtcCallMediaController(
    private val appContext: Context,
    private val audioSessions: com.example.chat.core.audio.OwnedCallAudioSessions? = null,
    private val foregroundStillExpected: () -> Boolean = { true },
    communicationArbiter: com.example.chat.core.audio.CommunicationMediaArbiter? = null,
// Previous implementation retained: ) : CallMediaController, CallVideoSurfaceProvider {
) : CallMediaController, CallVideoSurfaceProvider, CallMicrophoneCaptureProvider {

    private val mediaLifecycleMutex = kotlinx.coroutines.sync.Mutex()
    private val safetyCleanupScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var foregroundLossListener: (() -> Unit)? = null
    override fun setOnForegroundExecutionLost(listener: (() -> Unit)?) { foregroundLossListener = listener }
    private val communicationOwnership = communicationArbiter?.let {
        com.example.chat.core.audio.CallCommunicationOwnership(it)
    }
    private val microphoneCapture = CallMicrophoneCaptureMonitor()
    private var microphoneToken: CallMicrophoneCaptureMonitor.Token? = null
    override val microphoneCaptureState get() = microphoneCapture.state
    private val audioSessionOwner = audioSessions?.newOwner()
    private var preparedInitialSpeakerMode: Boolean? = null
    private var foregroundLease: CallMediaForegroundLease? = null
    private var callFocus: CallMediaAudioFocus? = null
    @Volatile private var focusMayPlay = true
// Previous implementation retained:     private var requestedLocalAudioEnabled = true
    @Volatile private var requestedLocalAudioEnabled = true
    var onAudioFocusInterruption: ((Boolean) -> Unit)? = null

    private fun applyFocusMayPlay(mayPlay: Boolean) {
        focusMayPlay = mayPlay
        localAudioTrack?.setEnabled(requestedLocalAudioEnabled && mayPlay)
        remoteStream?.audioTracks?.forEach { it.setEnabled(remoteAudioEnabled && mayPlay) }
        onAudioFocusInterruption?.invoke(!mayPlay)
    }


// Previous implementation retained:     private val eglBase: EglBase = EglBase.create()
// Previous implementation retained:     override val eglContext: EglBase.Context get() = eglBase.eglBaseContext
    @Volatile private var terminallyDisposed = false
    @Volatile private var nativeGeneration = 0L
    private var eglBase: EglBase? = null
    override val eglContext: EglBase.Context
        @Synchronized get() {
            check(!terminallyDisposed) { "Call media is closed." }
            return (eglBase ?: EglBase.create().also { eglBase = it }).eglBaseContext
        }
    private fun generationIsCurrent(generation: Long): Boolean =
        !terminallyDisposed && nativeGeneration == generation


    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localStream: MediaStream? = null
    private var remoteStream: MediaStream? = null

    private var videoCapturer: VideoCapturer? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var screenStream: MediaStream? = null
    private var screenSurfaceTextureHelper: SurfaceTextureHelper? = null
    private var screenVideoSource: VideoSource? = null
    private var screenVideoTrack: VideoTrack? = null
    private var localAudioSource: AudioSource? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    /** MediaProjection permission result, captured by the UI via
     *  MediaProjectionManager.createScreenCaptureIntent() and handed here
     *  before [startScreenShare]. */
    private var screenCapturePermission: Intent? = null

    fun setScreenCapturePermission(data: Intent) {
        screenCapturePermission = data
    }

    override var hasLocalMedia: Boolean = false
        private set
    override var hasRemoteMedia: Boolean = false
        private set

    override var onLocalIceCandidate: ((String, String?, Int?) -> Unit)? = null
    override var onRemoteStream: (() -> Unit)? = null
    private var onScreenShareStopped: (() -> Unit)? = null

    override fun setOnScreenShareStopped(listener: (() -> Unit)?) {
        onScreenShareStopped = listener
    }
    override var onConnectionState: ((CallTransportState) -> Unit)? = null
    override var onIceFailed: (() -> Unit)? = null

    /** restartIce: re-gather candidates on network switch / ICE failure without
     *  tearing down the peer connection (WebRTC standard practice, MDN). The
     *  next createOffer carries fresh ICE credentials for renegotiation. */
    override fun restartIce(): Boolean {
        val peer = peerConnection ?: return false
        return try {
            peer.restartIce()
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Cumulative WebRTC RTP bytes since this peer connected. */
    var onTrafficBytes: ((uploadedBytes: Long, downloadedBytes: Long) -> Unit)? = null
    var onNetworkMetrics: ((upstreamKbps: Int, packetLossPercent: Double, roundTripTimeMs: Long) -> Unit)? = null
    var onTrafficEnded: (() -> Unit)? = null
    private val statsHandler = Handler(Looper.getMainLooper())
    private var statsStarted = false
    private var trafficSessionPrepared = false
    private var trafficSessionEnded = false
    private var lastSentBytes = 0L
    private var lastReceivedBytes = 0L
    private val statsPoll = object : Runnable {
        override fun run() { pollTrafficStats() }
    }

    private val rendererBindings = java.util.IdentityHashMap<org.webrtc.SurfaceViewRenderer, VideoTrack?>()
    override val localRendererBindingIdentity: Any? get() = localVideoTrackForRender
    override val remoteRendererBindingIdentity: Any? get() = remoteVideoTrackForRender
    val localVideoTrackForRender: VideoTrack? get() = localVideoTrack
    val remoteVideoTrackForRender: VideoTrack? get() =
        remoteStream?.videoTracks?.firstOrNull()
    override val hasRemoteVideo: Boolean
        get() = remoteStream?.videoTracks?.isNotEmpty() == true

// Previous implementation retained:     override val isFrontCamera: Boolean = true
    @Volatile private var cameraFacingFront = false
    @Volatile private var cameraSwitchUncertain = false
    override val isFrontCamera: Boolean get() = cameraFacingFront

    /** _createMediaTransport: peer connection + local media. */
    override suspend fun prepare(
        isVideo: Boolean,
        lowData: Boolean,
        iceServers: List<Map<String, Any?>>,
    ) = mediaLifecycleMutex.withLock { prepareOwned(isVideo, lowData, iceServers) }

    private suspend fun prepareOwned(
        isVideo: Boolean,
        lowData: Boolean,
        iceServers: List<Map<String, Any?>>,
    ) {
        check(!terminallyDisposed) { "Call media is closed." }
        CallPermissionGate.ensure(appContext, isVideo)
// Previous implementation retained:         disposeInternal()
// Previous implementation retained:         dispose()
        disposeOwned()
        val generation = nativeGeneration
        try {
            checkNotNull(communicationOwnership) { "Call audio coordination is unavailable." }.acquire()
            // acquire checks mediaSuspended before any FGS/focus/native setup.
            // Permissions are checked above; foreground execution/focus precede any local capture.
            foregroundLease = CallForegroundService.ensureCallMediaForeground(appContext, isVideo) {
                foregroundStillExpected() && (audioSessions == null ||
                    audioSessions.isLatestOwner(checkNotNull(audioSessionOwner)))
            }
                ?: throw IllegalStateException("Call foreground audio could not be started.")
            val readyForeground = checkNotNull(foregroundLease)
            val lossListenerForGeneration = foregroundLossListener
            readyForeground.onLoss {
                if (foregroundLease !== readyForeground) return@onLoss
                // Signal the matching session and independently queue cleanup even if UI/signaling
                // callback delivery fails. Never restart FGS, microphone, camera or the call.
// Previous implementation retained:                 try { foregroundLossListener?.invoke() }
                try { lossListenerForGeneration?.invoke() }
                finally {
                    safetyCleanupScope.launch {
// Previous implementation retained:                         try { dispose() } catch (_: Exception) { /* Sticky recovery status retains failure. */ }
                        try {
                            mediaLifecycleMutex.withLock {
// Previous implementation retained:                                 if (foregroundLease === readyForeground) disposeOwned()
                                if (foregroundLease === readyForeground) {
                                    // Loss of execution is terminal even if signaling delivery failed.
                                    // Already inside the lifecycle mutex, so do not recurse into dispose().
                                    terminallyDisposed = true
                                    disposeOwned()
                                }
                            }
                        } catch (_: Exception) { /* Sticky recovery status retains failure. */ }
                    }
                }
            }
            check(readyForeground.isActive) { "Call foreground execution was lost." }
            val focus = CallMediaAudioFocus(audioManager, ::applyFocusMayPlay)
            callFocus = focus
// Previous implementation retained:             check(focus.acquire()) { "Call audio focus is unavailable. Try again from the call screen." }
            val focusGranted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { focus.acquire() }
            check(focusGranted) { "Call audio focus is unavailable. Try again from the call screen." }

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
// Previous implementation retained:         val factory = PeerConnectionFactory.builder()
// Previous implementation retained:             .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
// Previous implementation retained:             .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
// Previous implementation retained:             .createPeerConnectionFactory()
        val captureToken = microphoneCapture.begin()
        microphoneToken = captureToken
        val audioModule = org.webrtc.audio.JavaAudioDeviceModule.builder(appContext)
            .setAudioRecordStateCallback(object : org.webrtc.audio.JavaAudioDeviceModule.AudioRecordStateCallback {
                override fun onWebRtcAudioRecordStart() { microphoneCapture.started(captureToken) }
                override fun onWebRtcAudioRecordStop() { microphoneCapture.stopped(captureToken) }
            })
            .setAudioRecordErrorCallback(object : org.webrtc.audio.JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(errorMessage: String?) { microphoneCapture.unavailable(captureToken) }
                override fun onWebRtcAudioRecordStartError(
                    errorCode: org.webrtc.audio.JavaAudioDeviceModule.AudioRecordStartErrorCode?, errorMessage: String?,
                ) { microphoneCapture.unavailable(captureToken) }
                override fun onWebRtcAudioRecordError(errorMessage: String?) { microphoneCapture.unavailable(captureToken) }
            })
            .setEnableVolumeLogger(false)
            .createAudioDeviceModule()
        var factoryCreationFailure: Throwable? = null
        val factory = try {
            PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioModule)
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
// Previous implementation retained:                 .createPeerConnectionFactory()
                .createPeerConnectionFactory().also { peerConnectionFactory = it }
        } catch (failure: Throwable) {
            factoryCreationFailure = failure
            throw failure
        } finally {
// Previous implementation retained:             // WebRTC's factory owns its native reference; release this creator's reference.
// Previous implementation retained:             audioModule.release()
            // Retain the factory before releasing the creator reference, so a release failure
            // cannot strand an unowned native factory outside disposeInternal's cleanup list.
            try { audioModule.release() }
            catch (failure: Throwable) {
                communicationOwnership?.markCleanupUncertain()
// Previous implementation retained:                 throw failure
                val original = factoryCreationFailure
                if (original == null) throw failure
                if (original !== failure) original.addSuppressed(failure)
            }
        }
        peerConnectionFactory = factory

        val rtcConfig = PeerConnection.RTCConfiguration(parseIceServers(iceServers)).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val peer = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                synchronized(this@WebRtcCallMediaController) {
                    if (!generationIsCurrent(generation)) return

                val sdp = candidate?.sdp
                if (!sdp.isNullOrEmpty()) {
                    onLocalIceCandidate?.invoke(sdp, candidate.sdpMid, candidate.sdpMLineIndex)
                }
// Previous implementation retained

                }
            }

            override fun onAddStream(stream: MediaStream?) {
                synchronized(this@WebRtcCallMediaController) {
                    if (!generationIsCurrent(generation)) return

                if (stream != null) {
                    remoteStream = stream
// Previous implementation retained:                     stream.audioTracks.forEach { it.setEnabled(remoteAudioEnabled) }
                    stream.audioTracks.forEach { it.setEnabled(remoteAudioEnabled && focusMayPlay) }
                    hasRemoteMedia = true
                    onRemoteStream?.invoke()
                }
// Previous implementation retained

                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                synchronized(this@WebRtcCallMediaController) {
                    if (!generationIsCurrent(generation)) return

                val stream = streams?.firstOrNull()
                if (stream != null && remoteStream == null) {
                    remoteStream = stream
// Previous implementation retained:                     stream.audioTracks.forEach { it.setEnabled(remoteAudioEnabled) }
                    stream.audioTracks.forEach { it.setEnabled(remoteAudioEnabled && focusMayPlay) }
                    hasRemoteMedia = true
                    onRemoteStream?.invoke()
                }
// Previous implementation retained

                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                synchronized(this@WebRtcCallMediaController) {
                    if (!generationIsCurrent(generation)) return

                val mapped = when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> CallTransportState.connected
                    PeerConnection.PeerConnectionState.DISCONNECTED -> CallTransportState.disconnected
                    PeerConnection.PeerConnectionState.FAILED -> CallTransportState.failed
                    PeerConnection.PeerConnectionState.CLOSED -> CallTransportState.closed
                    else -> CallTransportState.other
                }
                onConnectionState?.invoke(mapped)
                if (mapped == CallTransportState.connected) startTrafficStats()
                else if (mapped == CallTransportState.closed || mapped == CallTransportState.failed) stopTrafficStats()
// Previous implementation retained

                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                synchronized(this@WebRtcCallMediaController) {
                    if (!generationIsCurrent(generation)) return

                if (newState == PeerConnection.IceConnectionState.FAILED) {
                    onIceFailed?.invoke()
                }
// Previous implementation retained

                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        }) ?: throw IllegalStateException("WebRTC did not produce a peer connection.")
        peerConnection = peer
        trafficSessionPrepared = true

        LocalMediaPreparation.run(
// Previous implementation retained:             initialize = { openLocalMedia(isVideo, lowData, factory, peer) },
            initialize = {
                check(readyForeground.isActive) { "Call foreground execution was lost." }
                openLocalMedia(isVideo, lowData, factory, peer)
                check(readyForeground.isActive) { "Call foreground execution was lost." }
            },
            rollback = { disposeInternal() },
        )
// Previous implementation retained:             foregroundLease = CallForegroundService.ensureCallMediaForeground(appContext, isVideo)
// Previous implementation retained:             foregroundLease = CallForegroundService.ensureCallMediaForeground(appContext, isVideo, foregroundStillExpected)
// Previous implementation retained:                 ?: throw IllegalStateException("Call foreground audio could not be started.")
// Previous implementation retained:             val focus = CallMediaAudioFocus(audioManager, ::applyFocusMayPlay)
// Previous implementation retained:             callFocus = focus
// Previous implementation retained:             check(focus.acquire()) { "Call audio focus is unavailable. Try again from the call screen." }
            if (audioSessions?.start(checkNotNull(audioSessionOwner), isVideo) == false) {
                throw IllegalStateException("Call audio could not be initialized.")
            }
            check(readyForeground.isActive) { "Call foreground execution was lost." }
            preparedInitialSpeakerMode = if (audioSessions != null) isVideo else null
        } catch (failure: Throwable) {
            try {
// Previous implementation retained:                 kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { dispose() }
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { disposeOwned() }
            } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    /** _openLocalMedia: audio/video constraints mirror the Dart getUserMedia
     *  constraints, including the low-data profile. */
    private fun openLocalMedia(
        isVideo: Boolean,
        lowData: Boolean,
        factory: PeerConnectionFactory,
        peer: PeerConnection,
    ) {
        if (localStream != null) return

        val stream = factory.createLocalMediaStream("chaty_local_stream")
        localStream = stream // provisional ownership; failed preparation rolls back every field

        // Keep voice processing enabled in every network profile; low bandwidth must
        // not mean degraded intelligibility. The adaptive sender bitrate handles load.
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            optional.add(MediaConstraints.KeyValuePair("channelCount", "1"))
        }
        val audioSource: AudioSource = factory.createAudioSource(audioConstraints)
        localAudioSource = audioSource
        localAudioTrack = factory.createAudioTrack("chaty_audio", audioSource)
        // Focus may have changed before the track existed; do not start it enabled by default.
        localAudioTrack?.setEnabled(requestedLocalAudioEnabled && focusMayPlay)
        stream.addTrack(localAudioTrack)

        if (isVideo) {
            val capturer = createCameraCapturer()
                ?: throw IllegalStateException("No usable camera for the video call.")
            videoCapturer = capturer
            val helper = SurfaceTextureHelper.create("chaty_capture", eglContext)
            surfaceTextureHelper = helper
            val videoSource: VideoSource = factory.createVideoSource(capturer.isScreencast)
            localVideoSource = videoSource
            capturer.initialize(helper, appContext, videoSource.capturerObserver)
            val width = if (lowData) 640 else 1280
            val height = if (lowData) 360 else 720
            val frameRate = if (lowData) 15 else 30
            capturer.startCapture(width, height, frameRate)
            localVideoTrack = factory.createVideoTrack("chaty_video", videoSource)
            stream.addTrack(localVideoTrack)
        }

        for (track in stream.audioTracks) {
            peer.addTrack(track, listOf(stream.id))
        }
        for (track in stream.videoTracks) {
            peer.addTrack(track, listOf(stream.id))
        }
        hasLocalMedia = true
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
// Previous implementation retained:         for (deviceName in enumerator.deviceNames) {
// Previous implementation retained:             if (enumerator.isFrontFacing(deviceName)) {
// Previous implementation retained:                 return enumerator.createCapturer(deviceName, null)
// Previous implementation retained:             }
// Previous implementation retained:         }
// Previous implementation retained:         for (deviceName in enumerator.deviceNames) {
// Previous implementation retained:             return enumerator.createCapturer(deviceName, null)
        val names = enumerator.deviceNames.toList()
        for (deviceName in names.sortedByDescending { enumerator.isFrontFacing(it) }) {
            val capturer = enumerator.createCapturer(deviceName, null) ?: continue
            cameraFacingFront = enumerator.isFrontFacing(deviceName)
            return capturer
        }
        return null
    }

    private fun startTrafficStats() {
        if (statsStarted) return
        statsStarted = true
        lastSentBytes = 0L
        lastReceivedBytes = 0L
        pollTrafficStats()
    }

// Previous implementation retained:     @Synchronized
    private fun pollTrafficStats() {
        val generation = nativeGeneration
        if (!generationIsCurrent(generation)) return
        val peer = peerConnection ?: return
        if (!statsStarted) return
        peer.getStats(object : org.webrtc.StatsObserver {
            override fun onComplete(reports: Array<org.webrtc.StatsReport>) {
                synchronized(this@WebRtcCallMediaController) {
                    if (!generationIsCurrent(generation) || peerConnection !== peer) return

                if (!statsStarted) return
                var sent = 0L
                var received = 0L
                var availableOutgoingBps = 0L
                var packetsSent = 0L
                var packetsLost = 0L
                var roundTripTimeMs = 0L
                reports.forEach { report ->
                    report.values.forEach { value ->
                        val number = value.value.toDoubleOrNull() ?: 0.0
                        when (value.name) {
                            "bytesSent" -> if (report.type == "ssrc" || report.type == "outbound-rtp") sent += number.toLong()
                            "bytesReceived" -> if (report.type == "ssrc" || report.type == "inbound-rtp") received += number.toLong()
                            "availableOutgoingBitrate" -> availableOutgoingBps = maxOf(availableOutgoingBps, number.toLong())
                            "packetsSent" -> packetsSent += number.toLong()
                            "packetsLost" -> packetsLost += number.toLong().coerceAtLeast(0L)
                            "currentRoundTripTime" -> roundTripTimeMs = maxOf(roundTripTimeMs, (number * 1_000.0).toLong())
                        }
                    }
                }
                val lossDenominator = packetsSent + packetsLost
                val lossPercent = if (lossDenominator > 0L) packetsLost * 100.0 / lossDenominator else 0.0
                onNetworkMetrics?.invoke((availableOutgoingBps / 1_000L).toInt(), lossPercent, roundTripTimeMs)
                if (sent > 0L || received > 0L) {
                    lastSentBytes = maxOf(lastSentBytes, sent)
                    lastReceivedBytes = maxOf(lastReceivedBytes, received)
                    onTrafficBytes?.invoke(lastSentBytes, lastReceivedBytes)
                }
                if (statsStarted) statsHandler.postDelayed(statsPoll, 1_000L)
// Previous implementation retained

                }
            }
        }, null)
    }

    private fun stopTrafficStats() {
        statsStarted = false
        statsHandler.removeCallbacks(statsPoll)
        if (trafficSessionPrepared && !trafficSessionEnded) {
            trafficSessionEnded = true
            onTrafficEnded?.invoke()
        }
    }

    /** Applies live video and Opus bitrate profiles; audio remains present at every quality tier. */
// Previous implementation retained:     override suspend fun applyNetworkProfile(profile: CallNetworkProfile) {
    override suspend fun applyNetworkProfile(profile: CallNetworkProfile) = mediaLifecycleMutex.withLock {
        check(!terminallyDisposed) { "Call media is closed." }
        applyNetworkProfileOwned(profile)
    }

    private suspend fun applyNetworkProfileOwned(profile: CallNetworkProfile) {
        val capturer = videoCapturer
        if (capturer != null) {
            runCatching {
                capturer.changeCaptureFormat(profile.width, profile.height, profile.frameRate)
            }.onFailure {
                android.util.Log.w("ChatyWebRTC", "Camera profile update deferred", it)
            }
        }
        val peer = peerConnection ?: return
        peer.senders.forEach { sender ->
            val kind = sender.track()?.kind()
            if (kind != "video" && kind != "audio") return@forEach
            runCatching {
                val parameters = sender.parameters
                parameters.encodings.forEach { encoding ->
                    if (kind == "video") {
                        encoding.maxBitrateBps = profile.maxBitrateBps
                        encoding.maxFramerate = profile.frameRate
                    } else {
                        encoding.maxBitrateBps = profile.audioBitrateBps
                    }
                }
                if (!sender.setParameters(parameters)) {
                    android.util.Log.w("ChatyWebRTC", "$kind sender rejected adaptive bitrate profile")
                }
            }.onFailure {
                android.util.Log.w("ChatyWebRTC", "Could not apply adaptive $kind bitrate", it)
            }
        }
    }

    override suspend fun createOffer(isVideo: Boolean): String {
        val peer = peerConnection ?: throw IllegalStateException("No peer connection.")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo.toString()))
        }
        val offer = createSdp(peer, constraints, isOffer = true)
        setLocalDescription(peer, offer)
        if (offer.description.isEmpty()) {
            throw IllegalStateException("WebRTC did not produce an SDP offer.")
        }
        return offer.description
    }

    override suspend fun createAnswer(isVideo: Boolean): String {
        val peer = peerConnection ?: throw IllegalStateException("No peer connection.")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo.toString()))
        }
        val answer = createSdp(peer, constraints, isOffer = false)
        setLocalDescription(peer, answer)
        if (answer.description.isEmpty()) {
            throw IllegalStateException("WebRTC did not produce an SDP answer.")
        }
        return answer.description
    }

    override suspend fun setRemoteDescription(sdp: String, type: String) {
        val peer = peerConnection ?: throw IllegalStateException("No peer connection.")
        val description = SessionDescription(
            if (type == "offer") SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER,
            sdp,
        )
        suspendCancellableCoroutine<Unit> { cont ->
            peer.setRemoteDescription(
                object : SdpObserver {
                    override fun onSetSuccess() { cont.resume(Unit) }
                    override fun onSetFailure(error: String?) {
                        cont.resumeWithException(IllegalStateException(error ?: "setRemoteDescription failed"))
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                },
                description,
            )
        }
    }

    override suspend fun addRemoteCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex ?: 0, candidate))
    }

// Previous implementation retained:     private var remoteAudioEnabled = true
    @Volatile private var remoteAudioEnabled = true
    override val supportsLocalHold: Boolean get() = true
    override fun setRemoteAudioEnabled(enabled: Boolean): Boolean {
        val tracks = remoteStream?.audioTracks.orEmpty()
        val previous = remoteAudioEnabled
// Previous implementation retained:         val outcomes = tracks.map { it.setEnabled(enabled) }
        val outcomes = tracks.map { it.setEnabled(enabled && focusMayPlay) }
        if (outcomes.any { !it }) {
            // Do not claim held if any current playout track rejected the change.
// Previous implementation retained:             tracks.forEach { it.setEnabled(previous) }
            tracks.forEach { it.setEnabled(previous && focusMayPlay) }
            return false
        }
        remoteAudioEnabled = enabled // Also applied to tracks that arrive later.
        return true
    }

    override fun setAudioEnabled(enabled: Boolean): Boolean {
        val track = localAudioTrack ?: return false
// Previous implementation retained:         return track.setEnabled(enabled)
        val accepted = track.setEnabled(enabled && focusMayPlay)
        if (accepted) requestedLocalAudioEnabled = enabled
        return accepted
    }

    override fun setVideoEnabled(enabled: Boolean): Boolean {
        val track = localVideoTrack ?: return false
        return track.setEnabled(enabled)
    }

    // This sender owns its track separately from localStream; ordinary audio is untouched.
    private var upgradeSender: org.webrtc.RtpSender? = null
    private var upgradePrepared = false
    private var upgradeCommitted = false
    // Kept disabled until rollback/camera/ICE behavior passes the two-endpoint gate.
    override val supportsVideoUpgrade: Boolean get() = false

    override suspend fun prepareVideoUpgrade() {
        check(localVideoTrack == null && !upgradePrepared) { "Video already allocated" }
        CallPermissionGate.ensure(appContext, true)
        val peer = peerConnection ?: error("No peer connection")
        val factory = peerConnectionFactory ?: error("No media factory")
        try {
            val capturer = createCameraCapturer() ?: error("No usable camera")
            videoCapturer = capturer
            val helper = SurfaceTextureHelper.create("chaty_upgrade_capture", eglContext)
            surfaceTextureHelper = helper
            val source = factory.createVideoSource(false)
            localVideoSource = source
            capturer.initialize(helper, appContext, source.capturerObserver)
            val track = factory.createVideoTrack("chaty_upgrade_video", source)
            track.setEnabled(false)
            localVideoTrack = track
            // No capture before matching remote answer+commit. Disabled track cannot send frames.
            upgradeSender = peer.addTrack(track, listOf("chaty_upgrade")) ?: error("No video sender")
            upgradePrepared = true
        } catch (error: Exception) { releaseUpgradeVideo(); throw error }
    }

    override suspend fun commitVideoUpgrade() {
        check(upgradePrepared) { "No pending video" }
        val capturer = videoCapturer ?: error("No camera")
        check(!upgradeCommitted) { "Video already committed" }
        capturer.startCapture(640, 360, 24)
        check(localVideoTrack?.setEnabled(true) == true) { "Video enable failed" }
        upgradeCommitted = true
    }

    override suspend fun abortVideoUpgrade() {
        if (!upgradePrepared && upgradeSender == null) return
        val peer = peerConnection
        // Cancel an in-progress offer without destroying the established audio transport.
        if (peer != null && (peer.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER ||
            peer.signalingState() == PeerConnection.SignalingState.HAVE_REMOTE_OFFER)) {
            runCatching { setLocalDescription(peer, SessionDescription(SessionDescription.Type.ROLLBACK, "")) }
        }
        releaseUpgradeVideo()
    }

    private fun releaseUpgradeVideo() {
        if (upgradeSender == null && !upgradePrepared && localStream?.videoTracks?.isNotEmpty() == true) return
        localVideoTrack?.setEnabled(false)
        upgradeSender?.let { sender -> runCatching { sender.setTrack(null, false) }; runCatching { peerConnection?.removeTrack(sender) } }
        upgradeSender = null
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }; videoCapturer = null
        runCatching { localVideoTrack?.dispose() }; localVideoTrack = null
        runCatching { localVideoSource?.dispose() }; localVideoSource = null
        runCatching { surfaceTextureHelper?.dispose() }; surfaceTextureHelper = null
        upgradePrepared = false
        upgradeCommitted = false
    }

// Previous implementation retained:     override suspend fun switchCamera(): Boolean {
    override suspend fun switchCamera(): Boolean = mediaLifecycleMutex.withLock {
        check(!terminallyDisposed) { "Call media is closed." }
        switchCameraOwned()
    }

    private suspend fun switchCameraOwned(): Boolean {
        check(!cameraSwitchUncertain) { "Camera switch cleanup needs attention." }
        val capturer = videoCapturer as? CameraVideoCapturer ?: return false
// Previous implementation retained:         return suspendCancellableCoroutine { cont ->
// Previous implementation retained:             capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
// Previous implementation retained: // Previous implementation retained:                 override fun onCameraSwitchDone(isFrontCamera: Boolean) { cont.resume(true) }
// Previous implementation retained: // Previous implementation retained:                 override fun onCameraSwitchError(error: String?) { cont.resume(false) }
// Previous implementation retained:                 override fun onCameraSwitchDone(isFrontCamera: Boolean) {
// Previous implementation retained:                     if (videoCapturer !== capturer) {
// Previous implementation retained:                         if (cont.isActive) cont.resume(false)
// Previous implementation retained:                         return
// Previous implementation retained:                     }
// Previous implementation retained:                     cameraFacingFront = isFrontCamera
// Previous implementation retained:                     if (cont.isActive) cont.resume(true)
        val generation = nativeGeneration
        try {
            val result = kotlinx.coroutines.withTimeoutOrNull(2_500L) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                        override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                            if (!cont.isActive) return
                            if (videoCapturer !== capturer || !generationIsCurrent(generation)) {
                                cont.resume(false)
                                return
                            }
                            cameraFacingFront = isFrontCamera
                            cont.resume(true)
                        }
                        override fun onCameraSwitchError(error: String?) { if (cont.isActive) cont.resume(false) }
                    })
                }
// Previous implementation retained:                 override fun onCameraSwitchError(error: String?) { if (cont.isActive) cont.resume(false) }
// Previous implementation retained:             })
            }
            if (result == null) {
                cameraSwitchUncertain = true
                communicationOwnership?.markCleanupUncertain()
            }
            return result ?: false
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            cameraSwitchUncertain = true
            communicationOwnership?.markCleanupUncertain()
            throw cancelled
        }
    }

    override suspend fun setSpeakerphoneOn(on: Boolean) {
// Previous implementation retained:         audioManager.isSpeakerphoneOn = on
        val sessions = audioSessions
        // Signaling repeats its initial default after prepare. Initialization already requested it;
        // do not issue a second HAL write or turn an unavailable earpiece into a failed voice call.
        val preparedDefault = preparedInitialSpeakerMode
        preparedInitialSpeakerMode = null
        if (sessions != null && preparedDefault == on) return
        if (sessions == null) {
            audioManager.isSpeakerphoneOn = on
        } else {
            val route = if (on) com.example.chat.core.audio.AudioOutputRoute.speaker
                else com.example.chat.core.audio.AudioOutputRoute.receiver
            check(sessions.switchRoute(checkNotNull(audioSessionOwner), route)) { "Call audio route did not switch." }
        }
    }

    /** getDisplayMedia + replaceTrack on the video sender. Requires
     *  [setScreenCapturePermission] first (MediaProjection consent). */
// Previous implementation retained:     override suspend fun startScreenShare(): Boolean {
    override suspend fun startScreenShare(): Boolean = mediaLifecycleMutex.withLock {
        check(!terminallyDisposed) { "Call media is closed." }
        startScreenShareOwned()
    }

    private suspend fun startScreenShareOwned(): Boolean {
        if (screenCapturer != null || screenStream != null) return true
        val generation = nativeGeneration
        val peer = peerConnection ?: return false
        val factory = peerConnectionFactory ?: return false
        val permission = screenCapturePermission ?: return false
        // Android 10+ requires an active mediaProjection foreground-service
        // type before ScreenCapturerAndroid creates the MediaProjection.
        if (!CallForegroundService.ensureScreenProjectionForeground(appContext)) return false
        if (!generationIsCurrent(generation) || peerConnection !== peer || foregroundLease?.isActive != true) return false

        // Resolve the sender before allocating projection resources. The old
        // ordering leaked a live capturer when a video sender was unavailable.
        val videoSender = peer.senders.firstOrNull { it.track()?.kind() == "video" }
            ?: return false

        var capturer: ScreenCapturerAndroid? = null
        var helper: SurfaceTextureHelper? = null
        var source: VideoSource? = null
        var track: VideoTrack? = null
        var stream: MediaStream? = null
        return try {
            val createdCapturer = ScreenCapturerAndroid(
                permission,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        // Android/user revoked projection: restore the camera,
                        // release resources, and let the call state drop the
                        // mediaProjection foreground-service type.
// Previous implementation retained:                         Handler(Looper.getMainLooper()).post { stopScreenShareInternal() }
                        safetyCleanupScope.launch {
                            mediaLifecycleMutex.withLock {
                                if (generationIsCurrent(generation) && screenCapturer === capturer) {
                                    try { stopScreenShareInternal() }
                                    catch (_: Exception) { communicationOwnership?.markCleanupUncertain() }
                                }
                            }
                        }
                    }
                },
            )
            capturer = createdCapturer
            val createdHelper = SurfaceTextureHelper.create("chaty_screen", eglContext)
            helper = createdHelper
            val createdSource = factory.createVideoSource(true)
            source = createdSource
            createdCapturer.initialize(createdHelper, appContext, createdSource.capturerObserver)
            createdCapturer.startCapture(1280, 720, 30)
            val createdTrack = factory.createVideoTrack("chaty_screen", createdSource)
            track = createdTrack
            createdTrack.setEnabled(true)
            val createdStream = factory.createLocalMediaStream("chaty_screen_stream")
            stream = createdStream
            createdStream.addTrack(createdTrack)
            if (!videoSender.setTrack(createdTrack, false)) {
                throw IllegalStateException("Unable to attach screen capture track.")
            }

            screenCapturer = createdCapturer
            screenSurfaceTextureHelper = createdHelper
            screenVideoSource = createdSource
            screenVideoTrack = createdTrack
            screenStream = createdStream
            screenCapturePermission = null // MediaProjection consent is single-use.
            true
// Previous implementation retained:         } catch (_: Exception) {
// Previous implementation retained:             try { capturer?.stopCapture() } catch (_: Exception) {}
// Previous implementation retained:             try { capturer?.dispose() } catch (_: Exception) {}
// Previous implementation retained:             // MediaStream owns and disposes tracks added to it.
// Previous implementation retained:             if (stream != null) {
// Previous implementation retained:                 val owned = stream.videoTracks.any { it === track }
// Previous implementation retained:                 try { stream.dispose() } catch (_: Exception) {}
// Previous implementation retained:                 if (!owned) try { track?.dispose() } catch (_: Exception) {}
// Previous implementation retained:             } else try { track?.dispose() } catch (_: Exception) {}
// Previous implementation retained:             try { source?.dispose() } catch (_: Exception) {}
// Previous implementation retained:             try { helper?.dispose() } catch (_: Exception) {}
        } catch (failure: Throwable) {
            val cleanup = CallMediaCleanup()
            cleanup.attempt { capturer?.stopCapture(); Unit }
            cleanup.attempt { capturer?.dispose(); Unit }
            cleanup.attempt {
                val owned = stream?.videoTracks?.any { it === track } == true
                StreamTrackCleanup.dispose(
                    stream = stream?.let { { it.dispose() } },
                    tracks = listOf(owned to { track?.dispose(); Unit }),
                )
            }
            cleanup.attempt { source?.dispose(); Unit }
            cleanup.attempt { helper?.dispose(); Unit }
            screenCapturePermission = null
// Previous implementation retained:             false
            try { cleanup.finish() }
            catch (release: Throwable) {
                communicationOwnership?.markCleanupUncertain()
                if (release !== failure) failure.addSuppressed(release)
            }
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            return false
        }
    }

// Previous implementation retained: // Previous implementation retained:     override suspend fun stopScreenShare() {
    override suspend fun stopScreenShare() = mediaLifecycleMutex.withLock {
        check(!terminallyDisposed) { "Call media is closed." }
        stopScreenShareOwned()
    }

    private suspend fun stopScreenShareOwned() {
        stopScreenShareInternal()
    }

// Previous implementation retained:     @Synchronized
    private fun stopScreenShareInternal() {
        val wasActive = screenCapturer != null || screenStream != null
        val cleanup = CallMediaCleanup()
        val capturer = screenCapturer
// Previous implementation retained:         screenCapturer = null
// Previous implementation retained:         try {
// Previous implementation retained:             capturer?.stopCapture()
// Previous implementation retained:         } catch (_: Exception) {
        cleanup.attempt { capturer?.stopCapture(); Unit }
        cleanup.attempt { capturer?.dispose(); if (screenCapturer === capturer) screenCapturer = null }
        cleanup.attempt {
            val cameraTrack = localVideoTrack
            val sender = peerConnection?.senders?.firstOrNull { it.track()?.kind() == "video" }
            if (sender != null && cameraTrack != null) check(sender.setTrack(cameraTrack, false)) { "Camera restoration failed." }
        }
// Previous implementation retained:         try { capturer?.dispose() } catch (_: Exception) {}
// Previous implementation retained
// Previous implementation retained:         val cameraTrack = localVideoTrack
// Previous implementation retained:         val videoSender = peerConnection?.senders?.firstOrNull { it.track()?.kind() == "video" }
// Previous implementation retained:         if (videoSender != null && cameraTrack != null) {
// Previous implementation retained:             videoSender.setTrack(cameraTrack, false)
        val oldStream = screenStream
        val oldTrack = screenVideoTrack
        var trackOwned = oldStream != null // Unknown stream ownership is never permission to double-dispose.
        cleanup.attempt { trackOwned = oldStream?.videoTracks?.any { it === oldTrack } == true }
        cleanup.attempt {
            StreamTrackCleanup.dispose(
                stream = oldStream?.let { { it.dispose() } },
                tracks = listOf(trackOwned to { oldTrack?.dispose(); Unit }),
            )
            if (screenStream === oldStream) screenStream = null
            if (screenVideoTrack === oldTrack) screenVideoTrack = null
        }
// Previous implementation retained:         val oldScreenStream = screenStream.also { screenStream = null }
// Previous implementation retained:         val oldScreenTrack = screenVideoTrack.also { screenVideoTrack = null }
// Previous implementation retained:         val screenTrackOwned = oldScreenStream?.videoTracks?.any { it === oldScreenTrack } == true
// Previous implementation retained:         StreamTrackCleanup.dispose(
// Previous implementation retained:             stream = oldScreenStream?.let { { it.dispose() } },
// Previous implementation retained:             tracks = listOf(screenTrackOwned to { oldScreenTrack?.dispose(); Unit }),
// Previous implementation retained:         )
// Previous implementation retained:         screenVideoSource?.dispose()
// Previous implementation retained:         screenVideoSource = null
// Previous implementation retained:         screenSurfaceTextureHelper?.dispose()
// Previous implementation retained:         screenSurfaceTextureHelper = null
        val source = screenVideoSource
        cleanup.attempt { source?.dispose(); if (screenVideoSource === source) screenVideoSource = null }
        val helper = screenSurfaceTextureHelper
        cleanup.attempt { helper?.dispose(); if (screenSurfaceTextureHelper === helper) screenSurfaceTextureHelper = null }
        screenCapturePermission = null
// Previous implementation retained:         if (wasActive) onScreenShareStopped?.invoke()
        if (wasActive) cleanup.attempt { onScreenShareStopped?.invoke(); Unit }
        try { cleanup.finish() }
        catch (failure: Throwable) { communicationOwnership?.markCleanupUncertain(); throw failure }
    }

// Previous implementation retained:     /** Attach a SurfaceViewRenderer created by the Compose UI. */
// Previous implementation retained:     override fun attachLocalRenderer(renderer: org.webrtc.SurfaceViewRenderer) {
// Previous implementation retained:         renderer.init(eglContext, null)
// Previous implementation retained:         localVideoTrackForRender?.addSink(renderer)
    /** A renderer owns the exact track attached at creation, never a later current-track slot. */
// Previous implementation retained:     @Synchronized
    private fun attachRenderer(renderer: org.webrtc.SurfaceViewRenderer, track: VideoTrack?) {
        check(!terminallyDisposed) { "Call media is closed." }
        try { renderer.init(eglContext, null) }
        catch (failure: Throwable) {
            try { renderer.release() } catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
            throw failure
        }
        synchronized(rendererBindings) { rendererBindings[renderer] = track }
        try { track?.addSink(renderer) }
        catch (failure: Throwable) {
            try { detachRenderer(renderer) } catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
            throw failure
        }
    }

// Previous implementation retained:     override fun attachRemoteRenderer(renderer: org.webrtc.SurfaceViewRenderer) {
// Previous implementation retained:         renderer.init(eglContext, null)
// Previous implementation retained:         remoteVideoTrackForRender?.addSink(renderer)
// Previous implementation retained:     }
    override fun attachLocalRenderer(renderer: org.webrtc.SurfaceViewRenderer) =
        attachRenderer(renderer, localVideoTrackForRender)

    override fun attachRemoteRenderer(renderer: org.webrtc.SurfaceViewRenderer) =
        attachRenderer(renderer, remoteVideoTrackForRender)

    override fun detachRenderer(renderer: org.webrtc.SurfaceViewRenderer) {
// Previous implementation retained:         try {
// Previous implementation retained:             localVideoTrackForRender?.removeSink(renderer)
// Previous implementation retained:             remoteVideoTrackForRender?.removeSink(renderer)
// Previous implementation retained:             renderer.release()
// Previous implementation retained:         } catch (_: Exception) {
        val originalTrack = synchronized(rendererBindings) {
            if (!rendererBindings.containsKey(renderer)) return
            rendererBindings.remove(renderer)
        }
        // A track may already have been disposed by transport teardown. Surface release still runs.
        val cleanup = CallMediaCleanup()
        cleanup.attempt { originalTrack?.removeSink(renderer); Unit }
        cleanup.attempt { renderer.release() }
// Previous implementation retained:         cleanup.finish()
        try { cleanup.finish() }
        catch (_: Exception) {
            // AndroidView release must not crash composition because a disposed track rejected
            // removeSink. All release attempts above have run; no EGL success is fabricated.
            android.util.Log.w("CallVideoRenderer", "Renderer cleanup could not be confirmed.")
        }
    }

    // Prior un-serialized dispose signature retained: override suspend fun dispose() {
    override suspend fun dispose() = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        // Previous implementation retained: mediaLifecycleMutex.withLock { disposeOwned() }
        mediaLifecycleMutex.withLock {
            terminallyDisposed = true
            disposeOwned()
        }
    }

    /** Only public terminal disposal releases renderers/EGL. prepareOwned reuse does not. */
    private fun releaseTerminalRenderContext() {
        val cleanup = CallMediaCleanup()
        val renderers = synchronized(rendererBindings) { rendererBindings.keys.toList() }
        renderers.forEach { renderer -> cleanup.attempt { detachRenderer(renderer) } }
        val egl = eglBase.also { eglBase = null }
        cleanup.attempt { egl?.release(); Unit }
        cleanup.finish()
    }

    private suspend fun disposeOwned() {
        preparedInitialSpeakerMode = null
        finishCallAudioCleanup(listOf(
            { disposeInternal() },
            { if (terminallyDisposed) releaseTerminalRenderContext() },
            { microphoneCapture.close(microphoneToken); microphoneToken = null },
            { audioSessions?.end(checkNotNull(audioSessionOwner)); Unit },
            {
                val focus = callFocus.also { callFocus = null }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { focus?.close() }
                Unit
            },
            { val foreground = foregroundLease.also { foregroundLease = null }; foreground?.close(); Unit },
            { focusMayPlay = true; requestedLocalAudioEnabled = true },
        )) { succeeded -> communicationOwnership?.finishCleanup(succeeded) }
    }

/* Prior first-error-preserving teardown retained; replaced with the shared staged cleanup:
    override suspend fun dispose() {
// Previous implementation retained:         disposeInternal()
        preparedInitialSpeakerMode = null
// Superseded cleanup retained: try { disposeInternal() } finally { withContext(NonCancellable) { audioSessions?.end(owner) } }
        var disposalFailure: Throwable? = null
        try { disposeInternal() }
        catch (failure: Throwable) { disposalFailure = failure; throw failure }
        finally {
            microphoneCapture.close(microphoneToken)
            microphoneToken = null
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    try { audioSessions?.end(checkNotNull(audioSessionOwner)) }
                    finally {
                        try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { callFocus?.close() } }
                        finally {
                            callFocus = null
                            try { foregroundLease?.close() }
                            finally { foregroundLease = null; focusMayPlay = true; requestedLocalAudioEnabled = true }
                        }
                    }
                }
            } catch (cleanup: Throwable) {
                // Preserve the first resource failure when audio cleanup also fails.
                val original = disposalFailure
                if (original != null) original.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

*/
    // Previous implementation retained: @Synchronized
    private fun disposeInternal() {
        // Drain callback mutations before native teardown, never hold this monitor over native release.
        synchronized(this) { nativeGeneration++ }
// Previous implementation retained:         if (upgradePrepared || upgradeSender != null) releaseUpgradeVideo()
// Previous implementation retained:         stopTrafficStats()
        val cleanup = CallMediaCleanup()
        cleanup.attempt { if (upgradePrepared || upgradeSender != null) releaseUpgradeVideo() }
        upgradeSender = null
        upgradePrepared = false
        upgradeCommitted = false
        cleanup.attempt { stopTrafficStats() }
        trafficSessionPrepared = false
        hasLocalMedia = false
        hasRemoteMedia = false
        remoteAudioEnabled = true
// Previous implementation retained:         val screen = screenCapturer
// Previous implementation retained:         screenCapturer = null
// Previous implementation retained:         try {
// Previous implementation retained:             screen?.stopCapture()
// Previous implementation retained:         } catch (_: Exception) {
// Previous implementation retained:         }
// Previous implementation retained:         screen?.dispose()
        // Detach wrappers before attempting release. A repeated disposal cannot double-dispose them.
        val screen = screenCapturer.also { screenCapturer = null }
        cleanup.attempt { screen?.stopCapture(); Unit }
        cleanup.attempt { screen?.dispose(); Unit }
        val oldScreenStream = screenStream.also { screenStream = null }
        val oldScreenTrack = screenVideoTrack.also { screenVideoTrack = null }
        val screenTrackOwned = oldScreenStream?.videoTracks?.any { it === oldScreenTrack } == true
// Previous implementation retained:         StreamTrackCleanup.dispose(
// Previous implementation retained:             stream = oldScreenStream?.let { { it.dispose() } },
// Previous implementation retained:             tracks = listOf(screenTrackOwned to { oldScreenTrack?.dispose(); Unit }),
// Previous implementation retained:         )
// Previous implementation retained:         screenVideoSource?.dispose()
// Previous implementation retained:         screenVideoSource = null
// Previous implementation retained:         screenSurfaceTextureHelper?.dispose()
// Previous implementation retained:         screenSurfaceTextureHelper = null
        cleanup.attempt {
            StreamTrackCleanup.dispose(
                stream = oldScreenStream?.let { { it.dispose() } },
                tracks = listOf(screenTrackOwned to { oldScreenTrack?.dispose(); Unit }),
            )
        }
        val oldScreenSource = screenVideoSource.also { screenVideoSource = null }
        val oldScreenHelper = screenSurfaceTextureHelper.also { screenSurfaceTextureHelper = null }
        screenCapturePermission = null
// Previous implementation retained:         try {
// Previous implementation retained:             videoCapturer?.stopCapture()
// Previous implementation retained:         } catch (_: Exception) {
// Previous implementation retained:         }
// Previous implementation retained:         videoCapturer?.dispose()
// Previous implementation retained:         videoCapturer = null
// Previous implementation retained:         surfaceTextureHelper?.dispose()
// Previous implementation retained:         surfaceTextureHelper = null
// Previous implementation retained:         // A MediaStream disposes all tracks added to it. Do not dispose those wrappers twice.
        cleanup.attempt { oldScreenSource?.dispose(); Unit }
        cleanup.attempt { oldScreenHelper?.dispose(); Unit }
        val oldCapturer = videoCapturer.also { videoCapturer = null }
        cleanup.attempt { oldCapturer?.stopCapture(); Unit }
        cleanup.attempt { oldCapturer?.dispose(); Unit }
        val oldSurfaceHelper = surfaceTextureHelper.also { surfaceTextureHelper = null }
        cleanup.attempt { oldSurfaceHelper?.dispose(); Unit }
        // A MediaStream owns attached track wrappers even if its disposal throws part-way.
        val oldLocalStream = localStream.also { localStream = null }
        val oldAudioTrack = localAudioTrack.also { localAudioTrack = null }
        val oldVideoTrack = localVideoTrack.also { localVideoTrack = null }
        val audioOwned = oldLocalStream?.audioTracks?.any { it === oldAudioTrack } == true
        val videoOwned = oldLocalStream?.videoTracks?.any { it === oldVideoTrack } == true
// Previous implementation retained:         StreamTrackCleanup.dispose(
// Previous implementation retained:             stream = oldLocalStream?.let { { it.dispose() } },
// Previous implementation retained:             tracks = listOf(audioOwned to { oldAudioTrack?.dispose(); Unit }, videoOwned to { oldVideoTrack?.dispose(); Unit }),
// Previous implementation retained:         )
// Previous implementation retained:         localAudioSource?.dispose()
// Previous implementation retained:         localAudioSource = null
// Previous implementation retained:         localVideoSource?.dispose()
// Previous implementation retained:         localVideoSource = null
// Previous implementation retained:         remoteStream?.dispose()
// Previous implementation retained:         remoteStream = null
// Previous implementation retained:         peerConnection?.dispose()
// Previous implementation retained:         peerConnection = null
// Previous implementation retained:         peerConnectionFactory?.dispose()
// Previous implementation retained:         peerConnectionFactory = null
        cleanup.attempt {
            StreamTrackCleanup.dispose(
                stream = oldLocalStream?.let { { it.dispose() } },
                tracks = listOf(audioOwned to { oldAudioTrack?.dispose(); Unit }, videoOwned to { oldVideoTrack?.dispose(); Unit }),
            )
        }
        val oldAudioSource = localAudioSource.also { localAudioSource = null }
        val oldVideoSource = localVideoSource.also { localVideoSource = null }
        val oldRemoteStream = remoteStream.also { remoteStream = null }
        val oldPeer = peerConnection.also { peerConnection = null }
        val oldFactory = peerConnectionFactory.also { peerConnectionFactory = null }
        cleanup.attempt { oldAudioSource?.dispose(); Unit }
        cleanup.attempt { oldVideoSource?.dispose(); Unit }
        cleanup.attempt { oldRemoteStream?.dispose(); Unit }
        cleanup.attempt { oldPeer?.dispose(); Unit }
        cleanup.attempt { oldFactory?.dispose(); Unit }
// Previous implementation retained:         cleanup.finish()
        try { cleanup.finish() }
        catch (failure: Throwable) {
            // LocalMediaPreparation can rollback before outer dispose. Preserve that uncertainty.
            communicationOwnership?.markCleanupUncertain()
            throw failure
        }
    }

    private fun parseIceServers(raw: List<Map<String, Any?>>): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()
        for (entry in raw) {
            val urlsValue = entry["urls"]
            val urls = when (urlsValue) {
                is List<*> -> urlsValue.mapNotNull { it?.toString() }
                is String -> listOf(urlsValue)
                else -> emptyList()
            }
            if (urls.isEmpty()) continue
            val builder = PeerConnection.IceServer.builder(urls)
            val username = entry["username"]?.toString()
            val credential = entry["credential"]?.toString()
            if (username != null && credential != null) {
                builder.setUsername(username)
                builder.setPassword(credential)
            }
            servers.add(builder.createIceServer())
        }
        return servers
    }

    private suspend fun createSdp(
        peer: PeerConnection,
        constraints: MediaConstraints,
        isOffer: Boolean,
    ): SessionDescription = suspendCancellableCoroutine { cont ->
        val observer = object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) {
                    cont.resumeWithException(IllegalStateException("WebRTC returned no SDP."))
                } else {
                    cont.resume(description)
                }
            }
            override fun onCreateFailure(error: String?) {
                cont.resumeWithException(IllegalStateException(error ?: "WebRTC SDP failure."))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }
        if (isOffer) {
            peer.createOffer(observer, constraints)
        } else {
            peer.createAnswer(observer, constraints)
        }
    }

    private suspend fun setLocalDescription(peer: PeerConnection, description: SessionDescription) {
        suspendCancellableCoroutine<Unit> { cont ->
            peer.setLocalDescription(
                object : SdpObserver {
                    override fun onSetSuccess() { cont.resume(Unit) }
                    override fun onSetFailure(error: String?) {
                        cont.resumeWithException(IllegalStateException(error ?: "setLocalDescription failed"))
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                },
                description,
            )
        }
    }
}
