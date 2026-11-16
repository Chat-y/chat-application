package com.example.chat.ui.auth

import com.example.chat.ui.core.components.auth.OtpCodeCells
import com.example.chat.ui.core.components.auth.ChatyUiTokens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.chat.ui.kit.ChatyAdaptiveLogic

/** Email OTP entry, reached after registration (VerifyEmail) or after the
 *  forgot-password request (ResetPassword). Layout follows the approved
 *  preview 7: six code cells with dash placeholders, "Code sent to" masked
 *  destination, "Verify and continue" primary action, and a
 *  resend-countdown / change-email row.
 *
 *  Behavior follows the auth-flow research report:
 *  - Digits shown as typed, numeric keyboard, paste accepted (4a.1).
 *  - Destination shown masked so a typo is visible without the full address.
 *  - Resend sits behind a countdown; the server's escalating cooldown and
 *    429 message still apply and are shown inline (4a.2).
 *  - Wrong/expired codes show the server's detail inline, including the
 *    attempts remaining (4a.3).
 *
 *  For ResetPassword there is no server "check code" endpoint: the code is
 *  only validated by auth/reset-password at the final step, so Continue just
 *  carries it forward and a wrong code reports on the new-password screen.
 *
 *  Debug builds only: the approved offline-demo code (000000) short-circuits
 *  to onVerified through DebugOtpGate before any backend call - no request,
 *  no token. Release builds never contain the gate. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OtpVerificationScreen(
    email: String,
    purpose: OtpPurpose,
    onBack: () -> Unit,
    /** VerifyEmail only: confirm the code with the server. Not called for
     *  ResetPassword (the code is carried to the new-password screen). */
    verify: suspend (code: String) -> Unit,
    resend: suspend () -> String,
    onVerified: (code: String) -> Unit,
    /** VerifyEmail only: verification is a nudge, not a gate — skipping lands
     *  on Home, where the banner offers to verify later. */
    onSkip: (() -> Unit)? = null,
    deliveryMessage: String? = null,
    initialResendSeconds: Int = OtpVerificationLogic.RESEND_COUNTDOWN_SECONDS,
    resendAllowed: Boolean = true,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    val theme = LocalThemeConfig.current
    val online=com.example.chat.ui.core.components.auth.rememberAuthOnline()
    val scope = rememberCoroutineScope()
    // Auth screenshot blocking is off by default. Preserve the opt-in hook, do not apply globally.
    // com.example.chat.ui.kit.ProtectSensitiveWindow()
    com.example.chat.ui.kit.ProtectSensitiveWindow() // A1-14: enabled (FLAG_SECURE lease while this screen is shown)
    val clipboardForCode = androidx.compose.ui.platform.LocalClipboardManager.current
    val feedbackView = androidx.compose.ui.platform.LocalView.current
    var submittedCode by remember { mutableStateOf<String?>(null) }
    var explicitlyPastedText by remember { mutableStateOf<String?>(null) }


    // A2-14 skipped by owner pick: one-time codes stay out of saved state.
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val completeCode by remember {androidx.compose.runtime.derivedStateOf {OtpVerificationLogic.isComplete(code)}}
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resendMessage by remember(email, deliveryMessage) { mutableStateOf(deliveryMessage) }
    var resendDeadline by remember { androidx.compose.runtime.mutableLongStateOf(
        OtpVerificationLogic.cooldownDeadline(android.os.SystemClock.elapsedRealtime(), initialResendSeconds)) }
    var resendIn by remember { mutableIntStateOf(OtpVerificationLogic.cooldownSeconds(resendDeadline, android.os.SystemClock.elapsedRealtime())) }

    LaunchedEffect(resendDeadline) {
        while (true) {
            resendIn = OtpVerificationLogic.cooldownSeconds(resendDeadline, android.os.SystemClock.elapsedRealtime())
            if (resendIn <= 0) break
            delay(250)
        }
    }

    fun handleContinue() {
        if (isLoading || !completeCode) return
        if (purpose == OtpPurpose.ResetPassword) {
            onVerified(code)
            return
        }
        // Debug-only offline demo gate (approved preview 7): in any debug build, as explicitly requested13:51
        // the demo code skips the server entirely - no production request, no
        // token. Release builds never contain the gate, so this is always
        // false there.
        if (purpose == OtpPurpose.VerifyEmail && DebugOtpGate.acceptsDebugCode(code)) {
            errorMessage = null
            onVerified(code)
            return
        }
        val submitted = code
        val pasted = explicitlyPastedText
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                authRequest { verify(submitted) }
                isLoading = false
                if (AuthFormPolicy.shouldClearCodeClipboard(clipboardForCode.getText()?.text, submitted, pasted)) clipboardForCode.setText(androidx.compose.ui.text.AnnotatedString(""))
                com.example.chat.ui.kit.ChatyHapticsPort.success(feedbackView)
                onVerified(submitted)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) {
                isLoading = false
                errorMessage = AuthFormPolicy.privateError(OtpVerificationLogic.displayError(e), email)
            } finally { isLoading = false }
        }
    }

    LaunchedEffect(code, isLoading) {
        if (!isLoading && completeCode && submittedCode != code) {
            submittedCode = code
            handleContinue()
        }
    }

    fun handleResend() {
        if (isLoading || !resendAllowed || resendIn > 0) return
        isLoading = true
        errorMessage = null
        resendMessage = null
        scope.launch {
            try {
                resendMessage = authRequest {resend()}
                code = ""; submittedCode = null
                resendDeadline = OtpVerificationLogic.cooldownDeadline(android.os.SystemClock.elapsedRealtime(), OtpVerificationLogic.RESEND_COUNTDOWN_SECONDS)
                resendIn = OtpVerificationLogic.cooldownSeconds(resendDeadline, android.os.SystemClock.elapsedRealtime())
                isLoading = false
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) {
                errorMessage = AuthFormPolicy.privateError(OtpVerificationLogic.displayError(e), email)
            } finally { isLoading = false }
        }
    }

    val guardedBack = com.example.chat.ui.core.components.auth.rememberAuthBackAction(code.isNotEmpty(), onBack)
    com.example.chat.ui.core.components.auth.ChatyAuthScreen(theme) {
        com.example.chat.ui.core.components.auth.AuthOfflineNotice(online,theme)
                    AuthBackButton(onClick = guardedBack)
                    AuthIllustration(type = "otp", theme = theme, height = 190)
                    Spacer(Modifier.height(ChatyUiTokens.space16))
                    Text(
                        when (purpose) { OtpPurpose.VerifyEmail -> authCopy("Verify Your Email"); OtpPurpose.SignIn -> authCopy("Sign in with email code"); OtpPurpose.ResetPassword -> authCopy("Enter Reset Code") },
                        modifier = Modifier.semantics { heading() },
                        color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
                        fontSize = ChatyUiTokens.text26,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    )
                    Spacer(Modifier.height(ChatyUiTokens.space8))
                    Text(
                        authFormat("Code sent to %1\$s.",OtpVerificationLogic.maskEmail(email)),
                        color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                        fontSize = ChatyUiTokens.text14,
                        lineHeight = ChatyUiTokens.text19_6,
                    )
                    Spacer(Modifier.height(ChatyUiTokens.space24))
                    com.example.chat.ui.core.components.auth.CheckEmailActions(theme, guardedBack, !isLoading)
                    OtpCodeCells(
                        code = code,
                        onCodeChange = {
                            val next = OtpVerificationLogic.sanitizeCode(it)
                            if (next.length == code.length + 1) com.example.chat.ui.kit.ChatyHapticsPort.selection(feedbackView)
                            code = next
                            errorMessage = null
                        },
                        theme = theme,
                    )
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    com.example.chat.ui.core.components.auth.ChatyInlineAction(authCopy("Paste email code"), theme, {
                        clipboard.getText()?.text?.let {
                            explicitlyPastedText = it
                            code = OtpVerificationLogic.sanitizeCode(it)
                            errorMessage = null
                        }
                    }, enabled = !isLoading)
                    errorMessage?.let {
                        Spacer(Modifier.height(ChatyUiTokens.touchGap))
                        Text(
                            it,
                            color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor),
                            fontSize = ChatyUiTokens.text13_5,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    resendMessage?.let {
                        Spacer(Modifier.height(ChatyUiTokens.touchGap))
                        Text(
                            it,
                            color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                            fontSize = ChatyUiTokens.text13_5,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(ChatyUiTokens.space24))
                    AuthPrimaryButton(
                        text = when (purpose) { OtpPurpose.VerifyEmail -> authCopy("Verify and continue"); OtpPurpose.SignIn -> authCopy("Verify and sign in"); OtpPurpose.ResetPassword -> authCopy("Continue") },
                        theme = theme,
                        onClick = { handleContinue() },
                        isLoading = isLoading,
                        capsule = true,
                    )
                    Spacer(Modifier.height(ChatyUiTokens.space8))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(
                            onClick = { handleResend() },
                            enabled = resendAllowed && !isLoading && resendIn <= 0,
                            modifier = Modifier.padding(ChatyUiTokens.touchGap / 2).heightIn(min = AuthCapsuleTokens.tapTarget),
                        ) {
                            Text(
                                if (resendIn > 0) authFormat("Resend code in %1\$ds",resendIn) else authCopy("Resend code"),
                                color = Color(
                                    if (resendIn > 0) theme.secondaryTextColor else theme.accentColor,
                                ),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = ChatyUiTokens.text13_5,
                            )
                        }
                        Text(
                            "•",
                            color = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                            fontSize = ChatyUiTokens.text13_5,
                        )
                        TextButton(
                            onClick = guardedBack,
                            enabled = !isLoading,
                            modifier = Modifier.padding(ChatyUiTokens.touchGap / 2).heightIn(min = AuthCapsuleTokens.tapTarget),
                        ) {
                            Text(
                                authCopy("Change email"),
                                color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = ChatyUiTokens.text13_5,
                            )
                        }
                    }
                    if (onSkip != null && purpose == OtpPurpose.VerifyEmail) {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.padding(ChatyUiTokens.touchGap / 2).fillMaxWidth().heightIn(min = AuthCapsuleTokens.tapTarget),
                        ) {
                            Text(
                                authCopy("Skip for now"),
                                color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                                fontSize = ChatyUiTokens.text13_5,
                            )
                        }
                    }
    }
}

/** The approved preview 7 code entry: CODE_LENGTH cells with dash
 *  placeholders over a single transparent input, so typing, paste, autofill
 *  and the numeric keyboard all behave like one field while the cells give
 *  the per-digit readout. Cells are decorative for accessibility; the hidden
 *  field carries the label and the entered-count state. Cell tint animation
 *  honors reduced motion. */
