package com.example.chat.ui.chats.contact_info

import com.example.chat.domain.models.ChatMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Pure helpers ported from contact_info_screen.dart (api branch anchor
 *  3bee780): presence label, shared media/document split, link extraction. */
object ContactInfoLogic {

    /** _presenceLabel: recording -> typing -> online -> last-seen strings,
     *  with the Dart 'Last seen hidden' fallback when no timestamp exists. */
    fun presenceLabel(
        isRecording: Boolean,
        isTyping: Boolean,
        isOnline: Boolean,
        lastSeen: Instant?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (isRecording) return "recording voice message…"
        if (isTyping) return "typing…"
        if (isOnline) return "Online"
        if (lastSeen == null) return "Last seen hidden"
        // R1-11: a last-seen time in the future (clock skew) is clamped to now, never shown as a future time.
        val effectiveLastSeen = if (lastSeen.isAfter(now)) now else lastSeen
        val local = effectiveLastSeen.atZone(zone)
        val hh = local.hour.toString().padStart(2, '0')
        val mm = local.minute.toString().padStart(2, '0')
        val today = now.atZone(zone).toLocalDate()
        return if (local.toLocalDate() == today) {
            "Last seen today at $hh:$mm"
        } else {
            "Last seen ${local.dayOfMonth}/${local.monthValue}/${local.year} at $hh:$mm"
        }
    }

    /** R3-20/R3-21/R3-22/R4-29: display names are untrusted text. Strip control and bidi-override
     *  characters, bound the length, and fall back to a neutral label when nothing readable remains. */
    fun safeDisplayName(raw: String?, fallback: String = "this contact", maxChars: Int = 80): String {
        if (raw == null) return fallback
        val cleaned = buildString {
            raw.forEach { ch ->
                val bidi = ch in '\u202A'..'\u202E' || ch in '\u2066'..'\u2069' || ch == '\u200E' || ch == '\u200F' || ch == '\u061C'
                if (!bidi && !Character.isISOControl(ch)) append(ch)
            }
        }.trim()
        if (cleaned.isEmpty()) return fallback
        if (cleaned.length <= maxChars) return cleaned
        var end = maxChars
        if (Character.isHighSurrogate(cleaned[end - 1])) end -= 1
        return cleaned.substring(0, end).trimEnd() + "…"
    }

    /** R2-30: bound a search query without splitting a surrogate pair. */
    fun boundQuery(raw: String, max: Int = 200): String {
        if (raw.length <= max) return raw
        var end = max
        if (Character.isHighSurrogate(raw[end - 1])) end -= 1
        return raw.substring(0, end)
    }

    /** R1-09: show a handle only when one exists; never invent "@". */
    fun usernameLine(username: String?): String? = username?.trim()?.takeIf { it.isNotEmpty() }?.let { "@$it" }

    /** R1-12/R1-53: the summary count is the number of loaded media items + documents + unique links. */
    fun loadedItemCount(media: Int, documents: Int, links: Int): Int = media + documents + links

    /** An index must not bypass deletion, view-once or no-copy rules in the message surface. */
    fun indexable(message: ChatMessage): Boolean = !message.isDeletedForEveryone && !message.isDeletedForMe &&
        !message.isRevokedBySender && !com.example.chat.data.local.AttachmentCachePolicy.viewOnceRestricted(message.metadata) &&
        message.metadata["no_save"] != true && message.metadata["protected"] != true &&
        message.metadata["restricted"] != true

    /** _media getter: attachment type image or video. */
    fun sharedMedia(messages: List<ChatMessage>): List<ChatMessage> =
        messages.filter { indexable(it) && (it.attachment?.type == "image" || it.attachment?.type == "video") }.distinctBy { it.id } // R5-06: unique keys

    /** _documents getter: attachment type document. */
    fun sharedDocuments(messages: List<ChatMessage>): List<ChatMessage> =
        messages.filter { indexable(it) && it.attachment?.type == "document" }.distinctBy { it.id }

    private val LINK_REGEX = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

    /** _links getter: every http(s) URL across message texts, deduped with
     *  Dart LinkedHashSet (insertion-order) semantics. */
    fun extractLinks(messages: List<ChatMessage>): List<String> {
        val links = LinkedHashSet<String>()
        for (message in messages.filter(::indexable)) {
            for (match in LINK_REGEX.findAll(message.text)) {
                if (match.value.isNotEmpty()) links.add(match.value)
            }
        }
        return links.toList()
    }
}
