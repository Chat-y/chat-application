package com.example.chat.ui.settings

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.domain.models.UniversalPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Hoisted screen state for Hide media (HM1-46): owns the preference+marker update path as the
 * single repository-level API setHidden(kind, value) (HM3-50), the reset-with-Undo flow and the
 * saving/undo guards. Created once per preference scope via remember, so marker logic is not
 * recreated per recomposition (HM1-20).
 */
@Stable
class HideMediaState(
    private val preferences: ChatyPreferencesController,
    private val repository: HideMediaRepository,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
) {
    private val accountGeneration = preferences.scopeGeneration

    var saving by mutableStateOf(true); private set
    var confirmReset by mutableStateOf(false)
    var showHelp by mutableStateOf(false)
    var bannerVisible by mutableStateOf(false); private set
    var affectedCount by mutableIntStateOf(-1); private set
    private var operationGeneration by mutableLongStateOf(0L)

    private fun UniversalPreferences.markerState() =
        HideMediaRepository.MarkerState(hidePhotosFromGallery, hideVideosFromGallery, hideGifsFromGallery)

    /** Entry re-sync: markers may have been lost while the preferences say ON (partial HM1-08,
     *  screen-entry scope), with a debug drift self-check (HM3-49). */
    fun onScreenOpen() {
        scope.launch {
            var failure: String? = null
            try {
                val state = preferences.universal.markerState()
                repository.driftReport(state)?.let { repository.logDebugOnce(it) }
                repository.sync(state).onFailure {
                    failure = "Could not restore local media markers. Try saving the setting again."
                }
                bannerVisible = !repository.isBannerSeen()
                affectedCount = repository.affectedFileCount()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure = "Could not restore local media markers. Try saving the setting again."
            }
            saving = false
            failure?.let { snackbar.showSnackbar(it) }
        }
    }

    fun dismissBanner() {
        bannerVisible = false
        scope.launch { repository.markBannerSeen() }
    }

    /** Single owner API for one preference+marker change (HM3-50). */
    fun setHidden(kind: HideMediaRepository.MediaKind, value: Boolean) =
        update(offerUndo = false, auditEvent = "set ${kind.folderName}=$value") {
            when (kind) {
                HideMediaRepository.MediaKind.PHOTOS -> it.copy(hidePhotosFromGallery = value)
                HideMediaRepository.MediaKind.VIDEOS -> it.copy(hideVideosFromGallery = value)
                HideMediaRepository.MediaKind.GIFS -> it.copy(hideGifsFromGallery = value)
            }
        }

    fun requestReset() {
        confirmReset = true
    }

    fun confirmResetNow() {
        confirmReset = false
        update(offerUndo = true, auditEvent = "reset all=off") {
            it.copy(hidePhotosFromGallery = false, hideVideosFromGallery = false, hideGifsFromGallery = false)
        }
    }

    fun requestHelp() {
        showHelp = true
    }

    fun dismissHelp() {
        showHelp = false
    }

    private fun update(offerUndo: Boolean, auditEvent: String, transform: (UniversalPreferences) -> UniversalPreferences) {
        if (saving) return
        saving = true
        operationGeneration++
        val operation = operationGeneration
        scope.launch {
            try {
                if (preferences.scopeGeneration != accountGeneration) return@launch
                val latest = preferences.universal
                val requested = transform(latest)
                repository.sync(requested.markerState())
                    .onSuccess { repository.audit(auditEvent) }
                    .onFailure { repository.auditFailure(auditEvent) }
                    .getOrThrow()
                // Debug-only guard: marker state must equal the three preferences (HM3-45).
                repository.assertMarkersMatch(requested.markerState())
                // Do not overwrite unrelated preferences with the snapshot captured before I/O.
                if (preferences.scopeGeneration != accountGeneration) return@launch
                preferences.updateUniversal(transform(preferences.universal))
                affectedCount = repository.affectedFileCount()
                saving = false
                val result = snackbar.showSnackbar(
                    "Gallery preference saved. Existing exported copies are unchanged.",
                    actionLabel = if (offerUndo) "Undo" else null,
                    duration = SnackbarDuration.Long, // at least 5 s so screen readers announce it (HM4-27)
                )
                if (offerUndo && result == SnackbarResult.ActionPerformed &&
                    preferences.scopeGeneration == accountGeneration && operation == operationGeneration && !saving
                ) {
                    val current = preferences.universal
                    if (current.markerState() != requested.markerState()) {
                        snackbar.showSnackbar("Gallery choices changed after reset. Undo was not applied.")
                        return@launch
                    }
                    saving = true
                    repository.sync(latest.markerState()).getOrThrow()
                    if (preferences.scopeGeneration == accountGeneration) {
                        preferences.updateUniversal(
                            current.copy(
                                hidePhotosFromGallery = latest.hidePhotosFromGallery,
                                hideVideosFromGallery = latest.hideVideosFromGallery,
                                hideGifsFromGallery = latest.hideGifsFromGallery,
                            ),
                        )
                    }
                    repository.audit("undo reset")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saving = false
                snackbar.showSnackbar("Could not save the gallery preference. Your choice was not changed. Try again.")
            } finally {
                if (operation == operationGeneration) saving = false
            }
        }
    }
}
