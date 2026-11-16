package com.example.chat.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Production Android implementation of [ChatyAudioPlatform], replacing the
 * Flutter MethodChannel native side of lib/core/audio/chaty_audio_platform.dart
 * with direct AudioManager calls. Error fallbacks match the Dart catch-blocks
 * (default capabilities / systemDefault / notVerified).
 *
 * HARDWARE-VERIFICATION GAP: route enumeration, setCommunicationDevice
 * behavior, and dual-output status are device- and OEM-dependent and cannot
 * be validated without a physical device.
 */
class AndroidChatyAudioPlatform(context: Context) : ChatyAudioPlatform {

// Previous implementation retained:     private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override suspend fun beginCommunicationSession() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    override suspend fun getCapabilities(useCase: AudioUseCase): AudioCapabilities =
        try {
            queryCapabilities()
// Previous implementation retained:         } catch (_: Exception) {
// Previous implementation retained:             defaultAudioCapabilities(useCase)
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            failedAudioCapabilities(useCase, failure)
        }

    override suspend fun getCurrentRoute(useCase: AudioUseCase): AudioOutputRoute =
        try {
            currentCommunicationRoute()
        } catch (_: Exception) {
            AudioOutputRoute.systemDefault
        }

    override suspend fun setPreferredRoute(
        useCase: AudioUseCase,
        route: AudioOutputRoute,
    ): AudioCapabilities =
        try {
// Previous implementation retained:             if (useCase == AudioUseCase.communication) {
// Previous implementation retained:                 audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
// Previous implementation retained:             }
            applyRoute(route)
            queryCapabilities()
// Previous implementation retained:         } catch (_: Exception) {
// Previous implementation retained:             defaultAudioCapabilities(useCase)
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            failedAudioCapabilities(useCase, failure)
        }

    override suspend fun getDualOutputStatus(useCase: AudioUseCase): DualOutputStatus =
        // No standard Android API can verify simultaneous earpiece+speaker
        // output; matches the Dart notVerified fallback when the platform
        // channel could not confirm.
        DualOutputStatus.notVerified

    override suspend fun releaseSession(useCase: AudioUseCase) {
// Previous implementation retained:         try {
// Previous implementation retained:             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
// Previous implementation retained:                 audioManager.clearCommunicationDevice()
// Previous implementation retained:             }
// Previous implementation retained:             audioManager.isSpeakerphoneOn = false
// Previous implementation retained:             if (audioManager.isBluetoothScoOn) {
// Previous implementation retained:                 audioManager.stopBluetoothSco()
// Previous implementation retained:                 audioManager.isBluetoothScoOn = false
// Previous implementation retained:             }
// Previous implementation retained:             audioManager.mode = AudioManager.MODE_NORMAL
// Previous implementation retained:         } catch (_: Exception) {
// Previous implementation retained:             // Ignored for release, matching the Dart implementation.
// Previous implementation retained:         }
        // Each restoration is attempted even when an earlier OEM operation throws.
        attemptAudioCleanup(listOf(
            { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice() },
            { audioManager.isSpeakerphoneOn = false },
            { audioManager.stopBluetoothSco() },
            { audioManager.isBluetoothScoOn = false },
            { audioManager.mode = AudioManager.MODE_NORMAL },
        ))
    }

    override val capabilityChanges: Flow<AudioCapabilities> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
// Previous implementation retained:                 trySend(queryCapabilities())
                trySend(capabilitySnapshot())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
// Previous implementation retained:                 trySend(queryCapabilities())
                trySend(capabilitySnapshot())
            }
        }
// Previous implementation retained:         audioManager.registerAudioDeviceCallback(callback, null)
        audioManager.registerAudioDeviceCallback(callback, mainHandler)
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    override val routeChanges: Flow<AudioOutputRoute> = callbackFlow {
        fun emitCurrentRoute() {
            try { trySend(currentCommunicationRoute()) }
            catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                // A failed query is not evidence that output changed to system default.
            }
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
// Previous implementation retained:                 trySend(currentCommunicationRoute())
                emitCurrentRoute()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
// Previous implementation retained:                 trySend(currentCommunicationRoute())
                emitCurrentRoute()
            }
        }
// Previous implementation retained:         audioManager.registerAudioDeviceCallback(callback, null)
// Previous implementation retained:         awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
        audioManager.registerAudioDeviceCallback(callback, mainHandler)
        val communicationListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
// Previous implementation retained:             AudioManager.OnCommunicationDeviceChangedListener { trySend(currentCommunicationRoute()) }.also {
            AudioManager.OnCommunicationDeviceChangedListener { emitCurrentRoute() }.also {
                audioManager.addOnCommunicationDeviceChangedListener(
                    androidx.core.content.ContextCompat.getMainExecutor(appContext), it)
            }
        } else null
// Previous implementation retained:         trySend(currentCommunicationRoute())
        emitCurrentRoute()
        awaitClose {
            audioManager.unregisterAudioDeviceCallback(callback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && communicationListener != null) {
                audioManager.removeOnCommunicationDeviceChangedListener(communicationListener)
            }
        }
    }

    private fun capabilitySnapshot(): AudioCapabilities = try { queryCapabilities() }
        catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            failedAudioCapabilities(AudioUseCase.communication, failure)
        }

    private fun queryCapabilities(): AudioCapabilities {
        val routes = mutableListOf<AudioOutputRoute>()
// Previous implementation retained:         val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.filter { it.isSink }
        } else audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
        val hasEarpiece = devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
        val hasSpeaker = devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val hasBluetooth = devices.any {
// Previous implementation retained:             it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            audioRouteMatchesDeviceType(AudioOutputRoute.bluetooth, it.type)
        }
        val hasWired = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }
        val hasUsb = devices.any {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        routes.add(AudioOutputRoute.systemDefault)
        if (hasSpeaker) routes.add(AudioOutputRoute.speaker)
        if (hasEarpiece) routes.add(AudioOutputRoute.receiver)
        if (hasBluetooth) routes.add(AudioOutputRoute.bluetooth)
        if (hasWired) routes.add(AudioOutputRoute.wiredHeadset)
        if (hasUsb) routes.add(AudioOutputRoute.usb)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && devices.any {
            audioRouteMatchesDeviceType(AudioOutputRoute.hearingAid, it.type)
        }) routes.add(AudioOutputRoute.hearingAid)
        return AudioCapabilities(
            availableRoutes = routes,
            activeRoute = currentCommunicationRoute(),
            dualOutputStatus = DualOutputStatus.notVerified,
            isStereoSupported = false,
            platform = "android",
        )
    }

    private fun currentCommunicationRoute(): AudioOutputRoute {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (audioManager.communicationDevice?.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutputRoute.speaker
// Previous implementation retained:                 AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioOutputRoute.bluetooth
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioOutputRoute.bluetooth
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
                    AudioOutputRoute.wiredHeadset
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> AudioOutputRoute.usb
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioOutputRoute.receiver
                AudioDeviceInfo.TYPE_HEARING_AID -> AudioOutputRoute.hearingAid
                else -> AudioOutputRoute.systemDefault
            }
        }
        @Suppress("DEPRECATION")
        return when {
            audioManager.isBluetoothScoOn -> AudioOutputRoute.bluetooth
            audioManager.isSpeakerphoneOn -> AudioOutputRoute.speaker
            audioManager.isWiredHeadsetOn -> AudioOutputRoute.wiredHeadset
            else -> AudioOutputRoute.receiver
        }
    }

    private fun applyRoute(route: AudioOutputRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
// Previous implementation retained:             val targetType = when (route) {
// Previous implementation retained:                 AudioOutputRoute.speaker -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
// Previous implementation retained:                 AudioOutputRoute.bluetooth -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
// Previous implementation retained:                 AudioOutputRoute.wiredHeadset -> AudioDeviceInfo.TYPE_WIRED_HEADSET
// Previous implementation retained:                 AudioOutputRoute.usb -> AudioDeviceInfo.TYPE_USB_HEADSET
// Previous implementation retained:                 else -> null
// Previous implementation retained:             }
// Previous implementation retained:             if (targetType == null || route == AudioOutputRoute.receiver || route == AudioOutputRoute.systemDefault) {
            if (route == AudioOutputRoute.systemDefault) {
                audioManager.clearCommunicationDevice()
            } else {
// Previous implementation retained:                 val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
// Previous implementation retained:                     ?: throw IllegalStateException("No communication device for route $route")
                // Select from the live communication list and reject non-output devices.
                val device = audioManager.availableCommunicationDevices.firstOrNull {
                    it.isSink && audioRouteMatchesDeviceType(route, it.type)
                } ?: throw IllegalStateException("No communication device for route $route")
                if (!audioManager.setCommunicationDevice(device)) {
                    throw IllegalStateException("setCommunicationDevice rejected route $route")
                }
            }
        } else {
            require(route != AudioOutputRoute.hearingAid) { "Hearing-aid communication selection requires API 31" }
            @Suppress("DEPRECATION")
            when (route) {
                AudioOutputRoute.speaker -> {
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = true
                }
                AudioOutputRoute.bluetooth -> {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
                else -> {
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
            }
        }
    }
}


/** Logical route matching, shared by selection fixtures. No persistent route model changes. */
internal fun audioRouteMatchesDeviceType(route: AudioOutputRoute, type: Int): Boolean = when (route) {
    AudioOutputRoute.receiver -> type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
    AudioOutputRoute.speaker -> type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
// Previous implementation retained:     AudioOutputRoute.bluetooth -> type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    AudioOutputRoute.bluetooth -> type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
    AudioOutputRoute.wiredHeadset -> type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
    AudioOutputRoute.usb -> type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_DEVICE
    AudioOutputRoute.hearingAid -> type == AudioDeviceInfo.TYPE_HEARING_AID
    AudioOutputRoute.systemDefault -> false
}
