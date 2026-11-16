package com.example.chat.ui.settings.privacy

import com.example.chat.core.preferences.PreferenceHistoryEntry

/** Controller history() is already newest first (logHistory inserts at index0).
 * Preserve that order; this projection never writes or compensates for controller retention. */
internal fun privacyRecentHistory(history: List<PreferenceHistoryEntry>): List<PreferenceHistoryEntry> =
    history.filter { it.key == "privacy" }.take(50)
