package com.example.chat.ui.auth

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.chat.ui.core.components.auth.ChatyUiTokens

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch
import com.example.chat.ui.kit.ChatyAdaptiveLogic

/** Port of lib/features/auth/forgot_password_screen.dart. The recovery call is
 *  injected; AppRoot wires ApiBackendService.resetPasswordWithCode (the
 *  path — the ApiBackendService variant lands with the organization slice). */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    sendRecovery: suspend (email: String) -> Unit,
    /** Called once the reset code email is accepted: the flow continues on the
     *  OTP screen (research 4a.5: the masked destination on the OTP screen IS
     *  the generic confirmation — no separate "check your email" page). */
    onSent: (email: String) -> Unit = {},
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val theme = LocalThemeConfig.current
    val online=com.example.chat.ui.core.components.auth.rememberAuthOnline()
    val scope = rememberCoroutineScope()

    var email by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    // Prior: var emailSent by remember { mutableStateOf(false) }
    var emailSent by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) } // A2-15: survives rotation
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }

    fun validateEmail(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return authCopy("Please enter your email")
        // A1-02: use the shared validator so Login, Register and Forgot password accept the same addresses.
        return com.example.chat.core.validators.ChatyValidators.validateEmail(value)?.let { authCopy(it) }
    }

    // A1-19: client-side resend cooldown (60 s), same monotonic helper as the OTP screen.
    var resendNotBefore by remember { mutableStateOf(0L) }
    fun handleSend() {
        if (isLoading) return
        if (emailSent) {
            val waitSeconds = OtpVerificationLogic.cooldownSeconds(resendNotBefore, android.os.SystemClock.elapsedRealtime())
            if (waitSeconds > 0) {
                errorMessage = authCopy("Please wait $waitSeconds seconds before requesting another code.")
                return
            }
        }
        emailError = validateEmail(email)
        if (emailError != null || isLoading) return
        isLoading = true
        errorMessage = null
        val submittedEmail = email.trim()
        scope.launch {
            try {
                authRequest { sendRecovery(submittedEmail) }
                isLoading = false
                emailSent = true
                resendNotBefore = OtpVerificationLogic.cooldownDeadline(android.os.SystemClock.elapsedRealtime(), 60)
                onSent(submittedEmail)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) {
                isLoading = false
                errorMessage = AuthFormPolicy.privateError((e.message ?: e.toString()).replaceFirst("Exception: ", ""), submittedEmail)
            } finally { isLoading = false }
        }
    }

    val guardedBack = com.example.chat.ui.core.components.auth.rememberAuthBackAction(email.isNotBlank(), onBack)
    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.padding(start = ChatyUiTokens.space16, end = ChatyUiTokens.space16, top = ChatyUiTokens.space8, bottom = ChatyUiTokens.space8)) {
                AuthBackButton(onClick = guardedBack)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ChatyUiTokens.space24, vertical = ChatyUiTokens.space16),
                contentAlignment = Alignment.Center,
            ) {
                Column(modifier = Modifier.widthIn(max = ChatyAdaptiveLogic.AUTH_COLUMN_MAX_DP.dp).fillMaxWidth()) {
                    AuthIllustration(type = "forgot_password", theme = theme, height = 190)
                    Spacer(Modifier.height(ChatyUiTokens.space16))
                    com.example.chat.ui.core.components.auth.AuthOfflineNotice(online,theme)
        Text(
                        authCopy("Forgot Password?"),
                        color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
                        fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text26,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
modifier = androidx.compose.ui.Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(ChatyUiTokens.space8))
                    Text(
                        if (emailSent)
                            authCopy("If an account exists with this email, a 6-digit reset code is on its way.")
                        else
                            authCopy("Enter the email registered with your Chaty account."),
                        color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                        fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text14,
                        lineHeight = 19.6.sp,
                    )
                    Spacer(Modifier.height(ChatyUiTokens.space32))
                    AuthTextField(
                        label = authCopy("Email"),
                        hintText = authCopy("Enter email"),
                        capsule = true,
                        theme = theme,
                        value = email,
                        onValueChange = { email = it; emailError = null; errorMessage = null },
                        keyboardType = KeyboardType.Email,
                        error = emailError,
                    )
                    Spacer(Modifier.height(ChatyUiTokens.space16))
                    errorMessage?.let {
                        Text(
                            it,
                            color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor),
                            fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text13_5,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = ChatyUiTokens.space16),
                        )
                    }
                    Spacer(Modifier.height(ChatyUiTokens.space16))
                    AuthPrimaryButton(
                        text = if (emailSent) authCopy("Resend Code") else authCopy("Send Reset Code"),
                        theme = theme,
                        onClick = { handleSend() },
                        isLoading = isLoading,
                        capsule = true,
                    )
                    if (emailSent) {
                        Spacer(Modifier.height(ChatyUiTokens.space16))
                        OutlinedButton(
                            onClick = guardedBack,
                            modifier = Modifier.padding(ChatyUiTokens.touchGap / 2).fillMaxWidth().heightIn(min = 48.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color(argbWithAlpha(theme.accentColor, 0.5)),
                            ),
                        ) {
                            Text(
                                authCopy("Back to Sign In"),
                                color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text15,
                            )
                        }
                    }
                }
            }
        }
    }
}
