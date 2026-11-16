package com.example.chat.ui.settings.conversation

/** UI boundary type. Existing nullable-Long persistence remains unchanged; no migration. */
internal sealed interface ConversationColorPreference {
    object ThemeDefault : ConversationColorPreference
    data class Custom(val argb: Long) : ConversationColorPreference
    fun resolve(themeValue: Long): Long = when (this) {
        ThemeDefault -> themeValue
        is Custom -> argb
    }
    fun toStored(): Long? = when (this) {
        ThemeDefault -> null
        is Custom -> argb
    }
    companion object {
        fun fromStored(value: Long?): ConversationColorPreference = value?.let(::Custom) ?: ThemeDefault
    }
}

internal enum class ConversationHeaderColorTarget {
    Bar, StatusBackground, StatusText;
    fun write(state: com.example.chat.domain.models.ConversationPreferences, value: Long?) = when (this) {
        Bar -> state.copy(actionBarColor = value)
        StatusBackground -> state.copy(contactStatusBgColor = value)
        StatusText -> state.copy(contactStatusTextColor = value)
    }
    companion object {
        fun fromSavedKey(key: String?) = entries.firstOrNull { it.name == key }
    }
}
