package com.example.chat.core.audio

/** Recorded lab evidence only. Never implies that the current phone was tested. */
enum class DeviceVerificationStatus(val label: String) {
    verifiedDualOutput("Verified Dual Output"),
    nativeStereo("Native Stereo"),
    standardSingleRoute("Standard Single Route"),
    unsupportedOrFailed("Unsupported or Failed"),
    notYetTested("Not Yet Tested"),
    unknown("Unknown");

    companion object {
        fun parse(value: String?): DeviceVerificationStatus =
            entries.firstOrNull { it.label.equals(value?.trim(), ignoreCase = true) } ?: unknown
    }
}

data class DeviceVerification(
    val deviceModel: String,
    val manufacturer: String,
    val osVersion: String,
    val platform: String,
    val useCase: String,
    val routeConfiguration: String,
    val status: DeviceVerificationStatus,
    val method: String,
    val date: java.time.LocalDate?,
    val recordedDate: String?,
    val failureReason: String?,
    /** Historical lab metadata only. Never filled with this phone's current patch level. */
    val securityPatchLevel: java.time.LocalDate? = null,
) {
    companion object {
        fun fromRecord(record: Map<String, Any?>): DeviceVerification {
            fun text(key: String): String? = (record[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val rawDate = text("testDate")
            return DeviceVerification(
                deviceModel = text("deviceModel") ?: "Unknown device",
                manufacturer = text("manufacturer").orEmpty(), osVersion = text("osVersion").orEmpty(),
                platform = text("platform").orEmpty(), useCase = text("audioUseCase").orEmpty(),
                routeConfiguration = text("routeConfiguration").orEmpty(),
                status = DeviceVerificationStatus.parse(text("testStatus")),
                method = text("verificationMethod").orEmpty(),
                date = rawDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                recordedDate = rawDate, failureReason = text("failureReason"),
                securityPatchLevel = text("securityPatchLevel")?.takeIf {
                    it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                }?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
            )
        }
    }
}
