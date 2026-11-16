package com.example.chat.core.audio

import kotlinx.coroutines.flow.StateFlow

/** Minimal screen-facing contract. Native ownership and policy stay in the production service. */
interface AudioCapabilitySource {
    val communicationCapabilities: StateFlow<AudioCapabilities?>
    suspend fun getCapabilities(useCase: AudioUseCase): AudioCapabilities
    /** Explicit permission/retry refresh; existing synthetic sources may use their live read. */
    suspend fun refreshCapabilities(useCase: AudioUseCase): AudioCapabilities = getCapabilities(useCase)
    suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute): AudioCapabilities
}
