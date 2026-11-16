package com.example.chat.domain.models

/** UI options and additive IDs. This contract never normalizes stored colors, positions or radii. */
object NotificationToastContract {
    data class Tone(val id: String, val label: String)
    val tones = listOf(Tone("default", "Default"), Tone("soft_ping", "Soft Ping"),
        Tone("pop_bubble", "Pop Bubble"), Tone("chime_bell", "Chime Bell"), Tone("silent", "Silent"))
    val ringtoneLabels = tones.map { it.label }
    val positions = listOf("Top of the screen", "Center of screen", "Bottom of screen")
    fun knownTone(value: Any?): Tone? = tones.firstOrNull {
        it.id == value || it.label == value || (it.id == "default" && value == "Default Chaty Tone")
    }
    fun storedTone(id: Any?, legacy: Any?): String {
        val label = legacy as? String
        val recognizedId = tones.firstOrNull { it.id == id }
        // Preserve exact legacy spelling and all unknown strings; unknown future IDs fall back.
        if (recognizedId == null) return label ?: "Default"
        return if (knownTone(label)?.id == recognizedId.id) label ?: recognizedId.label else recognizedId.label
    }
}
