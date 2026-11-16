package com.example.chat.ui.chats.starred

import java.text.Normalizer
import java.util.Locale

/** Display-text matching only. Never use this to compare account IDs or usernames for authorization. */
internal object StarredSearchRules {
    /** Length prefixes avoid delimiter collisions and stay saveable as a String lazy-list key. */
    fun rowKey(conversationId: String, messageId: String): String =
        "${conversationId.length}:$conversationId${messageId.length}:$messageId"

    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        val result = StringBuilder(decomposed.length)
        var latinBase = false
        for (ch in decomposed) {
            val kind = Character.getType(ch)
            val mark = kind == Character.NON_SPACING_MARK.toInt() ||
                kind == Character.COMBINING_SPACING_MARK.toInt() ||
                kind == Character.ENCLOSING_MARK.toInt()
            if (mark) {
                // Drop Latin accents only. Other scripts' vowel signs and marks carry meaning.
                if (!latinBase) result.append(ch)
            } else {
                latinBase = Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.LATIN
                result.append(ch)
            }
        }
        return Normalizer.normalize(result, Normalizer.Form.NFC).lowercase(Locale.ROOT)
    }

    fun tokens(query: String): List<String> =
        normalize(query).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun matchesTokens(normalizedHaystack: String, tokens: List<String>): Boolean =
        tokens.all { normalizedHaystack.contains(it) }

    fun matches(normalizedHaystack: String, query: String): Boolean = matchesTokens(normalizedHaystack, tokens(query))
}
