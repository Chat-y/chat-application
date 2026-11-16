package com.example.chat.ui.settings.security

/** Enrollment checks only. Existing credentials remain verifiable without migration. */
object CredentialStrength {
    fun pinError(pin: String): String? {
        if (pin.length !in setOf(4, 6) || pin.any { it !in '0'..'9' }) return "Use a 4 or 6 digit PIN."
        if (pin.toSet().size == 1) return "Weak PIN. Avoid repeating digits like 0000."
        if (pin.zipWithNext().all { (a, b) -> b - a == 1 } || pin.zipWithNext().all { (a, b) -> a - b == 1 })
            return "Avoid ascending or descending digit sequences."
        if (pin.indices.all { pin[it] == pin[it % 2] }) return "Avoid repeated digit pairs."
        if (pin in setOf("2580", "0852", "5683", "1998", "2000", "2001", "2002", "2003", "2004", "2005", "2010", "2020", "2024", "6969", "7777", "1004", "2004", "1313", "1122", "123123", "654321", "121212"))
            return "This PIN is commonly guessed. Choose another."
        return null
    }

    fun passwordError(password: String): String? = when {
        password.codePointCount(0, password.length) < 8 -> "Password must contain at least 8 characters."
        password.lowercase() in setOf("password", "password1", "12345678", "qwertyui", "letmein!", "iloveyou", "welcome1", "chaty123") -> "This password is commonly guessed. Choose another."
        else -> null
    }

    fun phraseError(phrase: String): String? = when {
        phrase.trim().codePointCount(0, phrase.trim().length) < 6 -> "Use at least 6 characters."
        phrase.trim().lowercase() in setOf("sesame", "secret", "hidden", "unlock", "password", "chaty123") -> "Choose a less common phrase."
        else -> null
    }
}
