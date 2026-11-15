package com.example.chat.ui.auth

/** Advisory UI policy. Never changes account identifiers, server password rules or auth decisions. */
object AuthFormPolicy {
    fun privateError(message: String, enteredIdentifier: String): String {
        val typed = enteredIdentifier.trim()
        if (!typed.contains('@') || typed.length < 3) return message
        return Regex(Regex.escape(typed), RegexOption.IGNORE_CASE).replace(message, "your email address")
    }
    fun shouldClearCodeClipboard(current: String?, verifiedCode: String, explicitPaste: String?): Boolean =
        current != null && (current == verifiedCode ||
            (current == explicitPaste && OtpVerificationLogic.sanitizeCode(current) == verifiedCode))
    fun emailSuggestion(value: String): String? {
        val email = value.trim(); val at = email.lastIndexOf('@')
        if (at <= 0) return null
        val replacement = when (email.substring(at + 1).lowercase()) {
            "gmial.com", "gamil.com", "gmail.con" -> "gmail.com"
            "outlok.com", "outlook.con" -> "outlook.com"
            "yaho.com", "yahoo.con" -> "yahoo.com"
            else -> return null
        }
        return email.substring(0, at + 1) + replacement
    }
    fun strengthLabel(password: String): String {
        if (password.isEmpty()) return ""
        val variety = listOf(password.any(Char::isLowerCase), password.any(Char::isUpperCase),
            password.any(Char::isDigit), password.any { !it.isLetterOrDigit() }).count { it }
        return when {
            password.length >= 14 && variety >= 3 -> "Stronger password"
            password.length >= 10 && variety >= 2 -> "Moderate password strength"
            else -> "Consider a longer, unique password"
        }
    }
    fun canResumeOtp(pending: Boolean, email: String, savedTenant: String, currentTenant: String,
        savedBase: String, currentBase: String, verifyingAccount: Boolean, authenticated: Boolean,
        savedUser: String, currentUser: String?): Boolean =
        pending && email.isNotBlank() && savedTenant == currentTenant && savedBase == currentBase &&
            (!verifyingAccount || (authenticated && savedUser.isNotBlank() && savedUser == currentUser))

    enum class ErrorKind { Connection, Server, RateLimit, Other }
    fun errorKind(message: String): ErrorKind = when {
        rateLimited(message) -> ErrorKind.RateLimit
        listOf("unable to connect", "unable to reach", "offline", "network", "timed out", "timeout")
            .any { message.contains(it, ignoreCase = true) } -> ErrorKind.Connection
        Regex("(?i)(?:HTTP\\s*|failed\\s*\\(|status[ :]*)(50[0-9])\\b").containsMatchIn(message) -> ErrorKind.Server
        else -> ErrorKind.Other
    }
    fun connectionProblem(message: String): Boolean = errorKind(message) == ErrorKind.Connection
    fun rateLimited(message: String): Boolean = listOf("429", "too many", "rate limit", "try again later")
        .any { message.contains(it, ignoreCase = true) }
}


class AuthRequestTimeoutException : Exception("The server did not answer in time. Your form is kept. Try again.")

suspend fun <T> authRequest(block: suspend () -> T): T = try {
    kotlinx.coroutines.withTimeout(35_000L) { com.example.chat.data.api.AuthHttpCallContext.owned {block()} }
} catch (_: kotlinx.coroutines.TimeoutCancellationException) { throw AuthRequestTimeoutException() }
