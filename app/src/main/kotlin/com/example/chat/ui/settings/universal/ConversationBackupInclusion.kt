package com.example.chat.ui.settings.universal

/** Omitting the complete settings/appearance section avoids leaking aliases or saved UI-map choices.
 * No restore semantics change: omitted sections are not staged or committed by the existing importer. */
internal fun includeConversationAppearanceBackup(includeAppearance: Boolean, sectionName: String): Boolean =
    includeAppearance || sectionName !in setOf("preferences", "wallpapers", "customization")

// Previous signature widened the nested preferences map to Map<String, Any?>.
internal fun <T> conversationBackupPreferenceSnapshot(values: Map<String, T>, includeAppearance: Boolean): Map<String, T> =
    if (includeAppearance) values else emptyMap()
