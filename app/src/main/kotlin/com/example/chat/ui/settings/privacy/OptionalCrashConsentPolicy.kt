package com.example.chat.ui.settings.privacy

/** Local consent vocabulary only. No reporter is installed or started by this policy. */
internal const val OPTIONAL_CRASH_CONSENT_VERSION = 1
internal data class OptionalCrashConsent(val enabled: Boolean, val version: Int) {
    val needsReview: Boolean get() = enabled && version != OPTIONAL_CRASH_CONSENT_VERSION
    val allowedForCurrentVersion: Boolean get() = enabled && !needsReview
}
