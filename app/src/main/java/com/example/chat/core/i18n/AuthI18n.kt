package com.example.chat.core.i18n

import android.text.TextUtils
import androidx.compose.ui.unit.LayoutDirection

import java.util.Locale

/**
 * Lane 3 (2026-11-15): locale, RTL and accessibility string helper for the
 * Chaty auth flow (login, register, OTP, reset).
 *
 * Pure helper: no state, no side effects, safe to call from composables and
 * plain logic. Every function returns either a resolved value or a resource
 * ID the caller resolves with stringResource / pluralStringResource, so this
 * file stays JVM-testable except where noted.
 *
 * Nothing here reads or writes the network, disk, or secrets.
 */
object AuthI18n {
    private var resolver: ((String,String) -> Int)? = null
    fun install(context: android.content.Context) {
        val app = context.applicationContext
        resolver = { name,type -> app.resources.getIdentifier(name,type,app.packageName) }
    }
    private fun res(name: String, type: String = "string"): Int = resolver?.invoke(name,type) ?: 0


    // ---------------------------------------------------------------------
    // RTL readiness (Round 6, item 3: Arabic / Hebrew ready)
    // ---------------------------------------------------------------------

    /**
     * True when [locale] is written right-to-left (Arabic, Hebrew, Farsi,
     * Urdu...). Uses the framework's ICU table, no hardcoded language list.
     */
    fun isRtl(locale: Locale = Locale.getDefault()): Boolean =
        TextUtils.getLayoutDirectionFromLocale(locale) ==
            android.util.LayoutDirection.RTL

    /** True when a Compose [LayoutDirection] is RTL. Overload for call sites
     *  that already read LocalLayoutDirection.current. */
    fun isRtl(direction: LayoutDirection): Boolean =
        direction == LayoutDirection.Rtl

    /** Compose [LayoutDirection] for [locale]; pass to
     *  CompositionLocalProvider(LocalLayoutDirection) in auth previews/tests. */
    fun layoutDirection(locale: Locale = Locale.getDefault()): LayoutDirection =
        if (isRtl(locale)) LayoutDirection.Rtl else LayoutDirection.Ltr

    // ---------------------------------------------------------------------
    // Email masking for screen-reader announcements (Final 48, item 18:
     // never announce the raw typed email)
    // ---------------------------------------------------------------------

    /**
     * b****@gmail.com-style mask, identical rule to
     * ui/auth/OtpVerificationLogic.maskEmail, duplicated here so ANY
     * announcement site (banners, error summaries, live regions) can mask
     * without depending on the OTP screen's logic object. Keeps the first
     * local-part character and the full domain; an input without a usable
     * local part passes through unchanged.
     *
     * Use the result for BOTH the visible line and the TalkBack announcement
     * so they never disagree (Voice Access labels must match visible text,
     * Final 48 item 35).
     */
    fun maskEmail(email: String): String {
        val at = email.lastIndexOf('@')
        if (at <= 0) return email
        return email.substring(0, 1) + "****" + email.substring(at)
    }

    /**
     * Announce [templateRes] (e.g. res("auth_otp_sent_to_masked")) with the
     * email already masked. Convenience wrapper so callers cannot forget the
     * masking step: they hand over the raw email and get back
     * (stringRes, maskedEmail) to feed stringResource(templateRes, masked).
     */
    fun maskedEmailArg(templateRes: Int, email: String): Pair<Int, String> =
        templateRes to maskEmail(email)

    // ---------------------------------------------------------------------
    // Time-aware greeting (Final 48, item 45)
    // ---------------------------------------------------------------------

    /**
     * Greeting string resource for the login header.
     * 05:00-11:59 morning, 12:00-16:59 afternoon, everything else evening
     * (no separate "good night" copy: evening covers late hours).
     *
     * @param hourOfDay 0-23, caller supplies (Calendar/LocalTime) so tests
     *        can pin the clock (fake-clock tests, missing-pieces item 30).
     */
    fun timeAwareGreetingRes(hourOfDay: Int): Int = when (hourOfDay) {
        in 5..11 -> res("auth_greeting_morning")
        in 12..16 -> res("auth_greeting_afternoon")
        else -> res("auth_greeting_evening")
    }

    // ---------------------------------------------------------------------
    // OTP resend countdown (Round 1-2: resend countdown; tone in plurals)
    // ---------------------------------------------------------------------

    /** Plurals resource for the resend countdown label. Resolve with:
     *  pluralStringResource(AuthI18n.resendCountdownPlurals, seconds, seconds) */
    val resendCountdownPlurals: Int get() = res("auth_resend_countdown", "plurals")

    /** String resource for the steady-state "Resend code" action. */
    val resendCodeString: Int get() = res("auth_resend_code")

    // ---------------------------------------------------------------------
    // Error summary chip (Final 48, item 6)
    // ---------------------------------------------------------------------

    /** Plurals resource for the error summary chip. Resolve with:
     *  pluralStringResource(AuthI18n.errorSummaryPlurals, fieldCount, fieldCount) */
    val errorSummaryPlurals: Int get() = res("auth_error_summary", "plurals")

    // ---------------------------------------------------------------------
    // Misc resolution helpers
    // ---------------------------------------------------------------------

    /** OTP progress announcement ("%1$d of %2$d digits entered"). Returns the
     *  string resource; caller formats with stringResource(res, entered, total). */
    fun otpProgressString(): Int = res("auth_otp_progress_cd")

    /** OTP field content description ("One-time code, %1$d digits"). */
    fun otpFieldDescriptionString(): Int = res("auth_otp_field_cd")

    /** Password strength label resource for a 0..3 score from the form
     *  policy's strength check. Clamps out-of-range scores to the ends. */
    fun passwordStrengthRes(score: Int): Int = when {
        score <= 0 -> res("auth_password_strength_weak")
        score == 1 -> res("auth_password_strength_fair")
        score == 2 -> res("auth_password_strength_good")
        else -> res("auth_password_strength_strong")
    }
}
