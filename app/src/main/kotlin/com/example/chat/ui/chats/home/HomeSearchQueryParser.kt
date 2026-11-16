package com.example.chat.ui.chats.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * R3-21..27 (decision 6A): a PURE search-query parser and result-label model.
 *
 * It turns raw query text into a structured [HomeSearchIntent] and nothing
 * else: it never builds an index (R3-15 held), never scans message bodies,
 * never passes uncontrolled syntax to a storage query (R3-27), and never
 * publishes anything to launcher/system indexes (R3-28: local-only is the
 * default and only mode this wave). Unknown or malformed filters degrade to
 * plain search terms instead of failing or guessing.
 *
 * The existing Home search (conversation-title text filter) is untouched;
 * executing an intent against an index is future work gated on the store
 * proposal and the separate index decision.
 */

/** R3-21: structured file-type facets, from attachment metadata only. */
enum class HomeAttachmentType(val label: String) {
    Document("Documents"), Image("Images"), Audio("Audio"), Video("Video"), Other("Other files")
}

/** R3-22: bounded size range in bytes. Unknown sizes stay UNKNOWN (null),
 *  never zero, so they are neither silently included nor excluded. */
data class HomeSizeRange(val minBytes: Long?, val maxBytes: Long?) {
    init {
        require(minBytes == null || minBytes >= 0) { "size range min must be >= 0" }
        require(maxBytes == null || maxBytes >= 0) { "size range max must be >= 0" }
        require(minBytes == null || maxBytes == null || minBytes <= maxBytes) {
            "size range min must be <= max"
        }
    }
}

/** R3-24: explicit start/end instants resolved in the CHOSEN timezone.
 *  [reversed] flags a start-after-end input instead of throwing, so the UI
 *  can show a range error and refuse to run the query. */
data class HomeDateInterval(
    val start: Instant,
    val end: Instant,
    val zone: ZoneId,
    val reversed: Boolean = false,
)

/** Parsed intent for one raw query string. */
data class HomeSearchIntent(
    /** R3-27: exact-phrase segments from "quoted text". */
    val phrases: List<String> = emptyList(),
    /** Remaining free-text terms. */
    val terms: List<String> = emptyList(),
    /** R3-23: explicit participant selector token (from:...). Resolution to a
     *  stable user ID happens at execution time, never by name matching here. */
    val participantToken: String? = null,
    /** R3-21: requested attachment-type facets. */
    val attachmentTypes: Set<HomeAttachmentType> = emptySet(),
    /** R3-22: requested size range, if any. */
    val sizeRange: HomeSizeRange? = null,
    /** R3-24: requested time interval, if any. */
    val dateInterval: HomeDateInterval? = null,
    /** True when nothing usable was parsed (blank or only broken filters). */
    val isEmpty: Boolean = false,
)

/** R3-26: which field a hit came from. Labels are rendered ONLY from
 *  engine-provided match information; when the engine says nothing, the
 *  label is Unknown, never an invented relevance explanation. */
enum class HomeMatchedField(val label: String) {
    ConversationName("Matched chat name"),
    ParticipantName("Matched participant"),
    AttachmentFilename("Matched file name"),
    SavedNote("Matched your note"),
    Unknown("Match")
}

object HomeSearchQueryParser {

    private val SIZE_UNITS = mapOf(
        "b" to 1L, "kb" to 1_000L, "mb" to 1_000_000L, "gb" to 1_000_000_000L,
    )

    /**
     * Parse [raw] into a [HomeSearchIntent]. [zone] is the searcher's chosen
     * timezone used to resolve before:/after: dates (R3-24). This function
     * is total: malformed filters become plain terms, never exceptions.
     */
    fun parse(raw: String, zone: ZoneId = ZoneId.systemDefault()): HomeSearchIntent {
        if (raw.isBlank()) return HomeSearchIntent(isEmpty = true)
        val phrases = mutableListOf<String>()
        val terms = mutableListOf<String>()
        var participant: String? = null
        val types = mutableSetOf<HomeAttachmentType>()
        var size: HomeSizeRange? = null
        var before: LocalDate? = null
        var after: LocalDate? = null

        // R3-27: pull out "quoted phrases" first; an unterminated quote is
        // treated as plain text, not as syntax passed onward.
        val rest = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '"') {
                val end = raw.indexOf('"', i + 1)
                if (end > i + 1) {
                    phrases += raw.substring(i + 1, end).trim()
                    i = end + 1
                    continue
                }
            }
            rest.append(c)
            i++
        }

        for (token in rest.toString().split(Regex("\\s+"))) {
            if (token.isBlank()) continue
            val colon = token.indexOf(':')
            val key = if (colon > 0) token.substring(0, colon).lowercase() else null
            val value = if (colon > 0) token.substring(colon + 1) else null
            when {
                key == "from" && !value.isNullOrBlank() && participant == null ->
                    participant = value
                key == "type" && !value.isNullOrBlank() ->
                    parseType(value)?.let(types::add) ?: terms.add(token)
                key == "size" && !value.isNullOrBlank() ->
                    parseSize(value)?.let { size = it } ?: terms.add(token)
                key == "before" && !value.isNullOrBlank() ->
                    parseDate(value)?.let { before = it } ?: terms.add(token)
                key == "after" && !value.isNullOrBlank() ->
                    parseDate(value)?.let { after = it } ?: terms.add(token)
                else -> terms += token
            }
        }

        val interval = buildInterval(before, after, zone)
        val empty = phrases.isEmpty() && terms.isEmpty() && participant == null &&
            types.isEmpty() && size == null && interval == null
        return HomeSearchIntent(
            phrases = phrases.filter { it.isNotBlank() },
            terms = terms,
            participantToken = participant,
            attachmentTypes = types,
            sizeRange = size,
            dateInterval = interval,
            isEmpty = empty,
        )
    }

    private fun parseType(value: String): HomeAttachmentType? = when (value.lowercase()) {
        "doc", "docs", "document", "documents" -> HomeAttachmentType.Document
        "image", "images", "photo", "photos" -> HomeAttachmentType.Image
        "audio", "voice" -> HomeAttachmentType.Audio
        "video", "videos" -> HomeAttachmentType.Video
        "file", "files", "other" -> HomeAttachmentType.Other
        else -> null
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        null
    }

    /**
     * R3-22 accepted forms: size:>10mb, size:<500kb, size:1mb..10mb,
     * size:2mb (exact lower bound, open upper). Unknown/malformed -> null,
     * which the caller keeps as a plain term.
     */
    private fun parseSize(value: String): HomeSizeRange? {
        val v = value.lowercase()
        fun amount(s: String): Long? {
            val m = Regex("^(\\d+)(b|kb|mb|gb)?$").find(s) ?: return null
            val n = m.groupValues[1].toLongOrNull() ?: return null
            val unit = SIZE_UNITS[m.groupValues[2].ifEmpty { "b" }] ?: return null
            return if (n > Long.MAX_VALUE / unit) null else n * unit
        }
        return when {
            v.startsWith(">") -> amount(v.removePrefix(">"))?.let { HomeSizeRange(it, null) }
            v.startsWith("<") -> amount(v.removePrefix("<"))?.let { HomeSizeRange(null, it) }
            ".." in v -> {
                val parts = v.split("..", limit = 2)
                val lo = amount(parts[0])
                val hi = amount(parts.getOrElse(1) { "" })
                if (lo == null && hi == null) null else try {
                    HomeSizeRange(lo, hi)
                } catch (_: IllegalArgumentException) {
                    null // reversed range degrades to a plain term
                }
            }
            else -> amount(v)?.let { HomeSizeRange(it, null) }
        }
    }

    /**
     * R3-24: before:/after: dates resolve to explicit instants in the chosen
     * zone: after:D -> start of day D, before:D -> start of day D (exclusive
     * upper bound). A reversed range is flagged, not silently swapped.
     */
    private fun buildInterval(before: LocalDate?, after: LocalDate?, zone: ZoneId): HomeDateInterval? {
        if (before == null && after == null) return null
        val start = after?.atStartOfDay(zone)?.toInstant() ?: Instant.EPOCH
        val end = before?.atStartOfDay(zone)?.toInstant() ?: Instant.MAX
        return HomeDateInterval(start = start, end = end, zone = zone, reversed = end.isBefore(start))
    }

    /** R3-27 capability check: when the executing engine cannot honour exact
     *  phrases, the safe fallback is to treat each phrase as ordinary terms
     *  and tell the caller, so the UI can label results honestly. */
    fun effectiveTerms(intent: HomeSearchIntent, supportsExactPhrase: Boolean): Pair<List<String>, Boolean> =
        if (supportsExactPhrase || intent.phrases.isEmpty()) {
            intent.terms to true
        } else {
            (intent.terms + intent.phrases) to false
        }

    /** R3-26: label only what the engine actually reported. */
    fun matchLabel(field: HomeMatchedField?): String = (field ?: HomeMatchedField.Unknown).label
}
