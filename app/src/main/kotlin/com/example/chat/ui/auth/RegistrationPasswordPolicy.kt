package com.example.chat.ui.auth

/** Registration-only policy. Existing sign-in and shared validator remain unchanged. */
internal fun registrationPasswordError(value: String): String? = when {
    value.length < 12 -> "Password must be at least 12 characters"
    value.length > 128 -> "Password cannot exceed 128 characters"
    value.none { it in 'a'..'z' } -> "Password must include a lowercase letter"
    value.none { it in 'A'..'Z' } -> "Password must include an uppercase letter"
    value.none { it in '0'..'9' } -> "Password must include a number"
    else -> null
}
