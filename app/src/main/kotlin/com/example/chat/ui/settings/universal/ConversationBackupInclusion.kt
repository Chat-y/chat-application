package com.example.chat.ui.settings.universal

/** Omitting the complete settings/appearance section avoids leaking aliases or saved UI-map choices.
 * No restore semantics change: omitted sections are not staged or committed by the existing importer. */
internal fun includeConversationAppearanceBackup(includeAppearance: Boolean, sectionName: String): Boolean =
    includeAppearance || sectionName !in setOf("preferences", "wallpapers", "customization")

internal fun conversationBackupPreferenceSnapshot(values: Map<String, Any?>, includeAppearance: Boolean): Map<String, Any?> =
    if (includeAppearance) values else emptyMap()
