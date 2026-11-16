package com.example.chat.ui.media

import com.example.chat.core.audio.AudioPolicyGate
import com.example.chat.core.audio.AudioSessionState
import com.example.chat.core.audio.AudioUseCase
import com.example.chat.ui.island.DynamicIslandController
import com.example.chat.ui.island.DynamicIslandPayload
import com.example.chat.ui.island.IslandPayloadType
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** just_audio ProcessingState port for the player seam. */
enum class MediaProcessingState {
    idle,
    loading,
    buffering,
    ready,
    completed,
}

/** just_audio PlayerState port for the player seam. */
data class MediaPlayerState(
    val processingState: MediaProcessingState,
    val playing: Boolean,
)

/** Seam over the just_audio AudioPlayer surface used by the coordinator.
 *  Android implementation: Media3 ExoPlayer adapter (integration owner;
 *  Media3 is a free androidx dependency to add). */
interface MediaAudioPlayer {
    val positionStream: SharedFlow<Duration>
    val durationStream: SharedFlow<Duration?>
    val bufferedPositionStream: SharedFlow<Duration>
    val playerStateStream: SharedFlow<MediaPlayerState>
    suspend fun stop()
    suspend fun setFilePath(path: String)
    suspend fun setSpeed(speed: Double)
    suspend fun play()
    suspend fun pause()
    suspend fun seek(position: Duration)
    fun dispose()
}

/** Exact port of lib/features/media/services/media_player_coordinator.dart.
 *
 *  Coordinates playback state, queue navigation, repeat/shuffle, audio
 *  policy interruption handling, and Dynamic Island media payload sync.
 *  The Dart locator fallback for the island controller is expressed as the
 *  [islandControllerProvider] seam. ChangeNotifier is ported as the same
 *  listener pattern used by ChatyPreferencesController.
 */
class MediaPlayerCoordinator(
    private val audioPlayer: MediaAudioPlayer,
    private val scope: CoroutineScope,
    private val policyCoordinator: AudioPolicyGate? = null,
    private val islandController: DynamicIslandController? = null,
    private val islandControllerProvider: () -> DynamicIslandController? = { null },
    private val communicationArbiter: com.example.chat.core.audio.CommunicationMediaArbiter =
        com.example.chat.ServiceLocator.communicationMediaArbiter,
    private val accountGeneration: () -> Long = { com.example.chat.ServiceLocator.apiBackend.authSnapshot().generation },
) {
    private val playbackMutex = Mutex()
    private var disposed = false
    // Keep queue pins while stopped because Resume/Skip still reference the queue.
    // Conservatively retain failed-start pins until a successful stop/new-start or dispose.
    private val playbackLeases = mutableListOf<AutoCloseable>()
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    private var _state = MediaPlaybackState()
    val state: MediaPlaybackState get() = _state

    private var posJob: Job? = null
    private var durJob: Job? = null
    private var stateJob: Job? = null
    private var bufJob: Job? = null
    // Previous: private var shouldResumeAfterInterruption = false
    private var playbackInterrupted = false
    private var nativeDisposed = false
    private var boundAccountGeneration: Long? = null
    private var nativeItemReady = false
    private var playbackRevision = 0L
    private var mediaReservation: com.example.chat.core.audio.CommunicationMediaReservation? = null

    /** Caller holds playbackMutex. Call suspension uses the same local mutex. */
    private suspend fun guardedPlayback(
        expectedGeneration: Long? = boundAccountGeneration,
        operation: suspend () -> Unit,
    ) {
        val reservation = mediaReservation ?: communicationArbiter.acquirePlayback(
            com.example.chat.core.audio.CommunicationMediaOwner(),
            suspendForCall = {
                playbackMutex.withLock {
                    playbackInterrupted = true
                    if (!nativeDisposed) {
                        check(!disposed) { "Audio cleanup is incomplete." }
                        audioPlayer.pause() // Receipt only after native pause returns successfully.
                    }
                    _state = _state.copy(status = PlaybackStatus.paused)
                    syncIslandPayload()
                    notifyListeners()
                }
            },
        )?.also { mediaReservation = it }
        check(reservation != null) { "Cannot play audio while a call owns audio." }
        check(reservation.runNative {
            check(expectedGeneration != null && accountGeneration() == expectedGeneration) { "Playback account changed." }
            val policy = policyCoordinator
            check(policy == null || policy.startMediaSession()) { "Call owns the audio session." }
            operation()
            if (accountGeneration() != expectedGeneration) {
                nativeItemReady = false
                audioPlayer.pause()
                error("Playback account changed during native start.")
            }
            boundAccountGeneration = expectedGeneration
            playbackInterrupted = false
        }) { "Cannot resume audio while a call owns audio." }
    }
    private var lastSyncedSec = -1

    private val onPolicyCoordinatorChanged: () -> Unit = {
        handlePolicyChanged()
    }

    init {
        initListeners()
    }

    private fun initListeners() {
        policyCoordinator?.addListener(onPolicyCoordinatorChanged)

        posJob = scope.launch {
            audioPlayer.positionStream.collect { pos ->
                _state = _state.copy(position = pos)
                if (pos.inWholeSeconds != lastSyncedSec.toLong()) {
                    lastSyncedSec = pos.inWholeSeconds.toInt()
                    syncIslandPayload()
                }
                notifyListeners()
            }
        }

        durJob = scope.launch {
            audioPlayer.durationStream.collect { dur ->
                if (dur != null) {
                    _state = _state.copy(duration = dur)
                    syncIslandPayload()
                    notifyListeners()
                }
            }
        }

        bufJob = scope.launch {
            audioPlayer.bufferedPositionStream.collect { buf ->
                _state = _state.copy(bufferedPosition = buf)
                notifyListeners()
            }
        }

        stateJob = scope.launch {
            audioPlayer.playerStateStream.collect { ps ->
                var status = PlaybackStatus.idle
                if (ps.processingState == MediaProcessingState.loading ||
                    ps.processingState == MediaProcessingState.buffering
                ) {
                    status = PlaybackStatus.loading
                } else if (ps.playing && !playbackInterrupted) {
                    status = PlaybackStatus.playing
                } else if (ps.processingState == MediaProcessingState.completed) {
                    status = PlaybackStatus.stopped
                    onTrackCompleted()
                } else {
                    status = PlaybackStatus.paused
                }
                _state = _state.copy(status = status)
                syncIslandPayload()
                notifyListeners()
            }
        }
    }

    /* Previous interruption handler retained for source history:
    private fun handlePolicyChanged() {
        val coordinator = policyCoordinator ?: return

        if (coordinator.state == AudioSessionState.interrupted ||
            coordinator.activeOwner == AudioUseCase.communication
        ) {
            if (_state.status == PlaybackStatus.playing) {
                shouldResumeAfterInterruption = true
                scope.launch { pause() }
            }
        } else if (coordinator.state == AudioSessionState.recovering ||
            coordinator.activeOwner == null
        ) {
            if (shouldResumeAfterInterruption) {
                shouldResumeAfterInterruption = false
                scope.launch { resume() }
            }
        }
    }

    */
    private fun handlePolicyChanged() {
        val coordinator = policyCoordinator ?: return
        if (coordinator.state == AudioSessionState.interrupted ||
            coordinator.activeOwner == AudioUseCase.communication
        ) {
            playbackInterrupted = true
            scope.launch { pause() }
        }
        // No automatic resume on recovery or call release. User must press Play.
    }

    private fun resolveIslandController(): DynamicIslandController? =
        islandController ?: islandControllerProvider()

    private fun syncIslandPayload() {
        val islandCtrl = resolveIslandController() ?: return

        if (_state.status == PlaybackStatus.playing || _state.status == PlaybackStatus.paused) {
            val active = _state.activeItem
            if (active != null) {
                val durSec = _state.duration.inWholeSeconds
                val durMs = _state.duration.inWholeMilliseconds
                val posMs = _state.position.inWholeMilliseconds
                val progress = if (durMs > 0) {
                    (posMs.toDouble() / durMs).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }

                islandCtrl.postPayload(
                    DynamicIslandPayload(
                        id = "media_active",
                        type = IslandPayloadType.media,
                        title = active.metadata["title"] as? String ?: active.name,
                        mediaArtist = active.metadata["artist"] as? String ?: "Audio Track",
                        durationSeconds = durSec.toInt(),
                        progress = progress,
                        isPlaying = _state.status == PlaybackStatus.playing,
                        timestamp = Instant.now(),
                        onTogglePlay = { scope.launch { togglePlayPause() } },
                        onSeek = { p ->
                            val targetMs = (p * durMs).toLong() +
                                if ((p * durMs) % 1 >= 0.5) 1 else 0 // Dart .round()
                            scope.launch { seek(targetMs.milliseconds) }
                        },
                    ),
                    autoExpand = false,
                )
            }
        } else if (_state.status == PlaybackStatus.stopped || _state.status == PlaybackStatus.idle) {
            islandCtrl.removePayload("media_active")
        }
    }

    suspend fun playTrack(item: MediaFileDescriptor, queue: List<MediaFileDescriptor>? = null) =
        playTrackForGeneration(item, queue, accountGeneration())

    private suspend fun playTrackForGeneration(
        item: MediaFileDescriptor,
        queue: List<MediaFileDescriptor>?,
        requestedGeneration: Long,
        expectedRevision: Long? = null,
    ) = playbackMutex.withLock {
        if (disposed) return@withLock
        if (expectedRevision != null && (playbackInterrupted || playbackRevision != expectedRevision ||
            boundAccountGeneration != requestedGeneration || accountGeneration() != requestedGeneration)) return@withLock
        playbackRevision++
        try {
            /* Previous preflight-only check, retained:
            // 1. Check with policy coordinator before playing
            val coordinator = policyCoordinator
            if (coordinator != null) {
                val acquired = coordinator.startMediaSession()
                if (!acquired) {
                    _state = _state.copy(
                        status = PlaybackStatus.error,
                        errorMessage = "Cannot play audio while an active call owns the session.",
                    )
                    notifyListeners()
                    return@withLock
                }
            }

            */
            // requestedGeneration is the explicit selection's generation OR original automatic receipt.
            guardedPlayback(expectedGeneration = requestedGeneration) {
            val newQueue = (queue ?: listOf(item)).toList()
            val newLease = MediaPlaybackFileLeases.acquire(newQueue.map { it.path } + item.path)
            val previousLeases = playbackLeases.toList()
            playbackLeases += newLease
            val idx = newQueue.indexOfFirst { it.path == item.path }

            _state = _state.copy(
                activeItem = item,
                queue = newQueue,
                queueIndex = if (idx != -1) idx else 0,
                status = PlaybackStatus.loading,
                errorMessage = null,
            )
            notifyListeners()

            nativeItemReady = false // Failed source switches cannot resume an older native item.
            audioPlayer.stop()
            check(!disposed) { "Player was disposed." }
            // Previous native use ended successfully; the new queue is already pinned.
            previousLeases.forEach { it.close(); playbackLeases.remove(it) }
            audioPlayer.setFilePath(item.path)
            check(!disposed) { "Player was disposed." }
            audioPlayer.setSpeed(_state.speed)
            check(!disposed) { "Player was disposed." }
            check(accountGeneration() == requestedGeneration) { "Playback account changed." }
            audioPlayer.play()
            nativeItemReady = true
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            // Native stop/start cancellation may be ambiguous. Retain protection until dispose/rebind.
            throw cancelled
        } catch (_: Exception) {
            _state = _state.copy(
                status = PlaybackStatus.error,
                // Original raw exception could expose local paths/URIs: "Playback error: $e"
                errorMessage = "This audio could not play. Choose it again or try another file.",
            )
            notifyListeners()
        }
    }

    suspend fun togglePlayPause() {
        if (_state.status == PlaybackStatus.playing) {
            pause()
        } else {
            resume()
        }
    }

    suspend fun pause() = playbackMutex.withLock {
        if (disposed) return@withLock
        audioPlayer.pause()
        _state = _state.copy(status = PlaybackStatus.paused)
        notifyListeners()
    }

    suspend fun resume() = playbackMutex.withLock {
        if (disposed) return@withLock
        if (_state.activeItem != null) {
            if (!nativeItemReady) {
                _state = _state.copy(status = PlaybackStatus.paused,
                    errorMessage = "Choose the audio again before resuming.")
                notifyListeners()
                return@withLock
            }
            // Previous unguarded native play retained: audioPlayer.play()
            try {
                guardedPlayback { audioPlayer.play() }
                _state = _state.copy(status = PlaybackStatus.playing)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state = _state.copy(status = PlaybackStatus.paused,
                    errorMessage = "Audio cannot resume right now. Finish the call and press Play again.")
            }
            notifyListeners()
        }
    }

    suspend fun seek(position: Duration) = playbackMutex.withLock {
        if (!disposed) audioPlayer.seek(position)
    }

    suspend fun setSpeed(speed: Double) = playbackMutex.withLock {
        if (disposed) return@withLock
        _state = _state.copy(speed = speed)
        // A speed setter can start some native adapters. While paused only save the preference.
        if (_state.status == PlaybackStatus.playing) {
            try { guardedPlayback { audioPlayer.setSpeed(speed) } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state = _state.copy(errorMessage = "Playback speed cannot change during a call.")
            }
        }
        notifyListeners()
    }

    fun toggleShuffle() {
        _state = _state.copy(isShuffle = !_state.isShuffle)
        notifyListeners()
    }

    fun cycleRepeatMode() {
        val next = when (_state.repeatMode) {
            ChatyRepeatMode.off -> ChatyRepeatMode.all
            ChatyRepeatMode.all -> ChatyRepeatMode.one
            ChatyRepeatMode.one -> ChatyRepeatMode.off
        }
        _state = _state.copy(repeatMode = next)
        notifyListeners()
    }

    suspend fun skipNext() {
        if (_state.hasNext) {
            val nextItem = _state.queue[_state.queueIndex + 1]
            playTrack(nextItem, queue = _state.queue)
        }
    }

    suspend fun skipPrevious() {
        if (_state.hasPrevious) {
            val prevItem = _state.queue[_state.queueIndex - 1]
            playTrack(prevItem, queue = _state.queue)
        }
    }

    /** Dart has both cycleRepeatMode and cycleChatyRepeatMode with identical
     *  bodies; kept for call-site parity. */
    fun cycleChatyRepeatMode() {
        val nextMode = when (_state.repeatMode) {
            ChatyRepeatMode.off -> ChatyRepeatMode.all
            ChatyRepeatMode.all -> ChatyRepeatMode.one
            ChatyRepeatMode.one -> ChatyRepeatMode.off
        }
        _state = _state.copy(repeatMode = nextMode)
        notifyListeners()
    }

    private fun onTrackCompleted() {
        if (playbackInterrupted || disposed) return
        val generation = boundAccountGeneration ?: return
        if (accountGeneration() != generation) return
        val revision = playbackRevision
        val queue = _state.queue.toList()
        val next = when {
            _state.repeatMode == ChatyRepeatMode.one -> _state.activeItem
            _state.hasNext -> queue.getOrNull(_state.queueIndex + 1)
            _state.repeatMode == ChatyRepeatMode.all -> queue.firstOrNull()
            else -> null
        } ?: return
        scope.launch { playTrackForGeneration(next, queue, generation, revision) }
    }

    suspend fun stop() = playbackMutex.withLock {
        if (disposed) return@withLock
        playbackRevision++ // Invalidates already queued automatic continuations.
        audioPlayer.stop()
        _state = _state.copy(
            status = PlaybackStatus.stopped,
            position = Duration.ZERO,
        )
        syncIslandPayload()
        mediaReservation?.release()
        mediaReservation = null
        policyCoordinator?.stopMediaSession()
        notifyListeners()
    }

    /* Previous synchronous teardown retained for source history:
    fun dispose() {
        if (disposed) return
        disposed = true
        policyCoordinator?.removeListener(onPolicyCoordinatorChanged)
        resolveIslandController()?.removePayload("media_active")
        posJob?.cancel()
        durJob?.cancel()
        stateJob?.cancel()
        bufJob?.cancel()
        // If native disposal fails, keep pins rather than permit cleanup under uncertain playback.
        audioPlayer.dispose()
        nativeDisposed = true
        val ended = mediaReservation
        mediaReservation = null
        scope.launch(kotlinx.coroutines.NonCancellable) { ended?.release() }
        playbackLeases.toList().forEach { it.close() }
        playbackLeases.clear()
    }
    */
    /** Awaitable, serialized and retryable. Failure keeps both the reservation and file pins.
     * A second dispose retries native cleanup even though new playback is already disabled. */
    suspend fun dispose() = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        playbackMutex.withLock {
        // Native success and all-clean success are separate. Retry retained bookkeeping too.
        if (nativeDisposed && mediaReservation == null && playbackLeases.isEmpty()) return@withLock
        if (!disposed) {
            disposed = true
            policyCoordinator?.removeListener(onPolicyCoordinatorChanged)
            resolveIslandController()?.removePayload("media_active")
            posJob?.cancel(); durJob?.cancel(); stateJob?.cancel(); bufJob?.cancel()
        }
        if (!nativeDisposed) {
            audioPlayer.dispose() // If this throws, nativeDisposed stays false and retry is possible.
            nativeDisposed = true
        }
        mediaReservation?.release() // NonCancellable: native success cannot strand the reservation.
        mediaReservation = null
        playbackLeases.toList().forEach { it.close(); playbackLeases.remove(it) }
        }
    }
}

/* Previous implementation fragments retained (net213):
                } else if (ps.playing) {
    suspend fun seek(position: Duration) {
        audioPlayer.seek(position)
    suspend fun setSpeed(speed: Double) {
        audioPlayer.setSpeed(speed)
*/

/* Previous implementation fragments retained (net214):
    private suspend fun guardedPlayback(operation: suspend () -> Unit) {
            guardedPlayback {
*/

/* Previous implementation fragments retained (net215):
    suspend fun playTrack(item: MediaFileDescriptor, queue: List<MediaFileDescriptor>? = null) = playbackMutex.withLock {
            val requestedGeneration = accountGeneration()
        if (_state.repeatMode == ChatyRepeatMode.one && _state.activeItem != null) {
            scope.launch { if (!playbackInterrupted && !disposed) playTrack(_state.activeItem!!, queue = _state.queue) }
        } else if (_state.hasNext) {
            scope.launch { if (!playbackInterrupted && !disposed) skipNext() }
        } else if (_state.repeatMode == ChatyRepeatMode.all && _state.queue.isNotEmpty()) {
            scope.launch { if (!playbackInterrupted && !disposed) playTrack(_state.queue.first(), queue = _state.queue) }
        }
*/
