package com.example.chat.ui.calls

/** Display only. Never feed formatted output into identity lookup, search or call initiation. */
internal fun callPhoneDisplay(raw: String, region: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    return runCatching { android.telephony.PhoneNumberUtils.formatNumber(trimmed, region) }
        .getOrNull()?.takeIf { it.isNotBlank() } ?: trimmed
}
