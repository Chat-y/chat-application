package com.example.chat.ui.profile

/** Localization adapter only. Shared validator acceptance/length rules stay unchanged. */
internal object ProfileValidationCopy {
    fun localize(context: android.content.Context, message: String): String {
        val resource = when (message) {
            "Username is required" -> com.example.chat.R.string.profile_validation_1
            "Username must be at least 3 characters" -> com.example.chat.R.string.profile_validation_2
            "Username must not exceed 30 characters" -> com.example.chat.R.string.profile_validation_3
            "Only letters, numbers, dots and underscores are allowed" -> com.example.chat.R.string.profile_validation_4
            "Username must contain at least one letter" -> com.example.chat.R.string.profile_validation_5
            "Username can't start or end with a dot" -> com.example.chat.R.string.profile_validation_6
            "This username is reserved and cannot be registered" -> com.example.chat.R.string.profile_validation_7
            "Display name is required" -> com.example.chat.R.string.profile_validation_8
            "Display name must not exceed 50 characters" -> com.example.chat.R.string.profile_validation_9
            "Bio must not exceed 256 characters" -> com.example.chat.R.string.profile_validation_10
            else -> return message
        }
        return context.getString(resource)
    }
}
