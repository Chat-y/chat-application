package com.example.chat.data.services

/** Display-only cache selection. Offer/push names are never identity evidence. */
object CallContactName {
    fun meaningful(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && it.lowercase(java.util.Locale.ROOT) !in placeholders
    }
    fun first(vararg values: String?): String? = values.firstNotNullOfOrNull(::meaningful)
    private val placeholders = setOf("chaty user", "user", "contact", "unknown caller", "chat")
}
