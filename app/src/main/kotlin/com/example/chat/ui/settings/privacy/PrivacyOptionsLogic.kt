package com.example.chat.ui.settings.privacy

import com.example.chat.domain.models.PrivacyAudienceValue
import com.example.chat.domain.models.PrivacyOnlineValue

/** Mappings between the server's privacy enums (research 5.2 / 5.3) and the
 *  WhatsApp-shaped labels the privacy center shows. Unknown server values
 *  map to null so callers fail closed instead of showing a wrong policy. */
object PrivacyOptionsLogic {

    /** everyone | contacts | contacts_except | nobody -> label. */
    fun audienceLabel(policy: String?): String? = when (policy) {
        "everyone" -> "Everyone"
        "contacts" -> "My Contacts"
        "contacts_except" -> "My Contacts Except…"
        "nobody" -> "Nobody"
        else -> null
    }

    /** Label -> server enum for the four-option audiences. */
//     fun audiencePolicy(label: String): String? = when (label) {
//         "Everyone" -> "everyone"
//         "My Contacts" -> "contacts"
//         "My Contacts Except…" -> "contacts_except"
//         "Nobody" -> "nobody"
//         else -> null
//     }
    fun audiencePolicy(label: String): String? = PrivacyAudienceValue.parse(label)?.wire

    val audienceLabels: List<String> = listOf("Everyone", "My Contacts", "My Contacts Except…", "Nobody")

    /** online_policy: everyone | same_as_last_seen (report 5.2). */
    fun onlineLabel(policy: String?): String? = when (policy) {
        "everyone" -> "Everyone"
        "same_as_last_seen" -> "Same as last seen"
        else -> null
    }

//     fun onlinePolicy(label: String): String? = when (label) {
//         "Everyone" -> "everyone"
//         "Same as last seen" -> "same_as_last_seen"
//         else -> null
//     }
    fun onlinePolicy(label: String): String? = PrivacyOnlineValue.parse(label)?.wire

    /** Aliases used by the privacy center's preference-write paths: the same
     *  policy -> label mappings as audienceLabel / onlineLabel. */
//     fun audiencePreferenceValue(policy: String?): String? = audienceLabel(policy)
    fun audiencePreferenceValue(policy: String?): String? = PrivacyAudienceValue.parse(policy)?.preference

//     fun onlinePreferenceValue(policy: String?): String? = onlineLabel(policy)
    fun onlinePreferenceValue(policy: String?): String? = PrivacyOnlineValue.parse(policy)?.preference

    val onlineLabels: List<String> = listOf("Everyone", "Same as last seen")
}
