package com.example.chat.ui.chats.detail

/** New optional extras only. Existing nine scalar colors keep legacy reads. New presets/group reset are visible only while enabled; rolling back keeps all stored keys. */
internal const val CONVERSATION_EXTRA_OPTIONS = "conv_additional_extra_options"
internal fun conversationExtraOptionsEnabled(values: Map<String, Any?>): Boolean = values[CONVERSATION_EXTRA_OPTIONS] as? Boolean ?: false
internal fun conversationVaryCustomNamesEnabled(values: Map<String, Any?>): Boolean =
    conversationExtraOptionsEnabled(values) && values[CONVERSATION_VARY_CUSTOM_NAMES] == true
