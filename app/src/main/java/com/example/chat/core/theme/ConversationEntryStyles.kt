package com.example.chat.core.theme

/** Stable saved ids with legacy read support. Labels may change without changing saved layouts. */
data class EntryStyleOption(val id: String, val label: String, val legacyName: String, val family: String)

object ConversationEntryStyles {
    val options: List<EntryStyleOption> = listOf(
        EntryStyleOption("stock", "Classic (recommended)", "Stock", "Classic"),
        EntryStyleOption("reference_two", "Split actions", "Reference 2", "Classic"),
        EntryStyleOption("yowa", "Emoji and attachment", "YOWA Style", "Rounded"),
        EntryStyleOption("wamod", "Two-row actions", "WAMOD", "Classic"),
        EntryStyleOption("hangouts", "Bottom action row", "Hangouts", "Classic"),
        EntryStyleOption("simple", "Minimal attachment", "Simple", "Minimal"),
        EntryStyleOption("aran_v2", "Side microphone", "Aran V2", "Classic"),
        EntryStyleOption("mood", "Wide pill", "Mood", "Rounded"),
        EntryStyleOption("wanh", "Side microphone alternate", "WANH", "Classic"),
        EntryStyleOption("hangouts_v2", "Two-row alternate", "Hangouts V2", "Classic"),
        EntryStyleOption("dribble", "Action shelf", "Dribble", "Classic"),
        EntryStyleOption("dribble_v2", "Action shelf alternate", "Dribble V2", "Classic"),
        EntryStyleOption("allo", "Attachment and emoji inside", "Allo", "Rounded"),
        EntryStyleOption("rounded", "Rounded shelf", "Rounded", "Rounded"),
        EntryStyleOption("old_stock", "Classic 2016", "Old Stock (2016)", "Classic"),
        EntryStyleOption("rc_google", "Outlined field", "RC-MSG Google", "Classic"),
        EntryStyleOption("rc_telegram", "Inside-field actions", "RC-Telegram X iOS", "Rounded"),
        EntryStyleOption("rc_ios", "Plus and pill", "RC-IOS 11", "Rounded"),
        EntryStyleOption("rc_line", "Plus and compact field", "RC-Line", "Minimal"),
        EntryStyleOption("concept", "Expanded action rail", "Concept", "Classic"),
        EntryStyleOption("instagram", "Camera first", "Instagram", "Rounded"),
        EntryStyleOption("ios14", "Plus and rounded field", "iOS 14", "Rounded"),
        EntryStyleOption("messenger", "Outside actions", "FB Messenger", "Rounded"),
        EntryStyleOption("rc_mess", "Outside actions alternate", "RC Mess", "Rounded"),
        EntryStyleOption("telegram_modern", "Modern attachment", "Telegram Modern", "Rounded"),
        EntryStyleOption("ios_clean", "Clean rounded", "iOS Clean Rounded", "Rounded"),
        EntryStyleOption("hangouts_minimal", "Minimal plus", "Hangouts Minimal", "Minimal"),
        EntryStyleOption("simple_pill", "Simple pill", "Simple Pill", "Rounded"),
        EntryStyleOption("flat_floating", "Flat floating", "RC-Flat Floating", "Floating"),
        EntryStyleOption("classic_3d", "Raised classic", "WhatsApp Classic 3D", "Classic"),
        EntryStyleOption("compact_bubble", "Compact bubble", "Signal Compact Bubble", "Minimal"),
    )

    fun resolve(raw: String): EntryStyleOption = options.firstOrNull {
        it.id == raw.trim() || it.legacyName == raw.trim()
    } ?: options.first()

    fun layoutName(raw: String): String = resolve(raw).legacyName
}
