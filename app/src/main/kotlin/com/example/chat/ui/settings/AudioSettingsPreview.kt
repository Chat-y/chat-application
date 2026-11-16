package com.example.chat.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.example.chat.core.audio.*
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** IDE-only entry point. This fixture never touches AudioManager or a real call. */
@Preview(name = "Audio - standard fixture", widthDp = 360, heightDp = 800)
@Preview(name = "Audio - large text fixture", widthDp = 320, heightDp = 800, fontScale = 2f)
@Composable
private fun AudioSettingsScreenPreview() {
    val scope = rememberCoroutineScope()
    val capability = remember(scope) { AudioCapabilityService(PreviewAudioPlatform(), scope) }
    val coordinator = remember(capability) { AudioPolicyCoordinator(capability) }
    val settings = remember(capability, coordinator) { AudioSettingsController(capability, coordinator) }
    ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
        AudioSettingsScreen(settings, capability, coordinator, scope, onBack = {})
    }
}

private class PreviewAudioPlatform : ChatyAudioPlatform {
    override val capabilityChanges: Flow<AudioCapabilities> = emptyFlow()
    override val routeChanges: Flow<AudioOutputRoute> = emptyFlow()
    override suspend fun getCapabilities(useCase: AudioUseCase) = defaultAudioCapabilities(useCase)
    override suspend fun getCurrentRoute(useCase: AudioUseCase) = AudioOutputRoute.systemDefault
    override suspend fun getDualOutputStatus(useCase: AudioUseCase) = DualOutputStatus.notVerified
    override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute) =
        defaultAudioCapabilities(useCase).copy(activeRoute = route)
    override suspend fun releaseSession(useCase: AudioUseCase) {}
}
