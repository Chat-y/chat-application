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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch
import com.example.chat.ui.kit.ChatyAdaptiveLogic

/** Port of lib/features/auth/create_new_password_screen.dart (api @ 3bee780d).
 *
 *  Reached from OtpVerificationScreen (ResetPassword purpose) after the
 *  forgot-password email arrives. AppRoot wires submitNewPassword to the
 *  custom backend: auth/reset-password with the emailed code, then an
 *  explicit login after completion. Previous behavior signed in automatically
 *  with the new password and landed on Home. showSuccess is wired to a Toast.
 *
 *  [email] is part of the Dart widget contract (main.dart passes
 *  state.session?.user.email) but is never rendered in the source; kept
 *  unused for parity.
 */
@Composable
fun CreateNewPasswordScreen(
    email: String,
    onBack: () -> Unit,
    submitNewPassword: suspend (newPassword: String) -> Unit,
    showSuccess: (message: String) -> Unit,
    onPasswordUpdated: () -> Unit,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val theme = LocalThemeConfig.current
    val online=com.example.chat.ui.core.components.auth.rememberAuthOnline()
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var obscurePassword by remember { mutableStateOf(true) }
    var obscureConfirm by remember { mutableStateOf(true) }
    // Auth screenshot blocking is off by default. Preserve the opt-in hook, do not apply globally.
    // com.example.chat.ui.kit.ProtectSensitiveWindow()
    com.example.chat.ui.kit.ProtectSensitiveWindow() // A1-14: enabled (FLAG_SECURE lease while this screen is shown)
    com.example.chat.ui.core.components.auth.RemaskPasswordOnBackground { obscurePassword = true; obscureConfirm = true }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }

    fun handleResetPassword() {
        if (isLoading) return
        // Dart step 1: validate every field in one pass.
        val errors = CreateNewPasswordLogic.validateForm(password, confirmPassword)
        passwordError = errors.passwordError
        confirmError = errors.confirmError
        // Dart step 2: redundant top-level mismatch guard (kept for parity).
        if (errors.topLevelError != null) {
            errorMessage = errors.topLevelError
            return
        }
        if (!errors.isValid) return

        isLoading = true
        errorMessage = null
        val submittedPassword = password
        scope.launch {
            try {
                authRequest { submitNewPassword(submittedPassword) }
                // Previous: showSuccess(CreateNewPasswordLogic.SUCCESS_MESSAGE)
                showSuccess(authCopy(CreateNewPasswordLogic.SUCCESS_MESSAGE))
                onPasswordUpdated()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) {
                errorMessage = CreateNewPasswordLogic.displayError(e)
            } finally { isLoading = false }
        }
    }

    val guardedBack = com.example.chat.ui.core.components.auth.rememberAuthBackAction(password.isNotEmpty() || confirmPassword.isNotEmpty(), onBack)
    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ChatyUiTokens.space24, vertical = ChatyUiTokens.space16),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.widthIn(max = ChatyAdaptiveLogic.AUTH_COLUMN_MAX_DP.dp).fillMaxWidth()) {
                AuthBackButton(onClick = guardedBack)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    com.example.chat.ui.core.components.auth.ChatyAppAuthIcon()
                }
                Spacer(Modifier.height(ChatyUiTokens.space32))
                com.example.chat.ui.core.components.auth.AuthOfflineNotice(online,theme)
        Text(
                    authCopy("Create New Password"),
                    color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
                    fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text26,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
modifier = androidx.compose.ui.Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(ChatyUiTokens.space8))
                Text(
                    authCopy("Use 12 to 128 characters with uppercase, lowercase, a number, and a symbol."),
                    color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                    fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text14,
                )
                Spacer(Modifier.height(ChatyUiTokens.space32))
                AuthTextField(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    autofillTypes = listOf("NewPassword"),
                    label = authCopy("Password"),
                    hintText = authCopy("Enter a strong password"),
                    capsule = true,
                    theme = theme,
                    value = password,
                    onValueChange = { password = it; passwordError = null; errorMessage = null },
                    obscureText = obscurePassword,
                    error = passwordError,
                    suffixIcon = {
                        IconButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = { obscurePassword = !obscurePassword }) {
                            Icon(
                                // Dart: visibility_off_outlined while obscured,
                                // visibility_outlined while visible.
                                if (obscurePassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (obscurePassword) authCopy("Show password") else authCopy("Hide password"),
                                tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(ChatyUiTokens.space16))
                AuthTextField(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    onDone = { if (!isLoading) handleResetPassword() },
                    label = authCopy("Confirm Password"),
                    hintText = authCopy("Confirm password"),
                    capsule = true,
                    theme = theme,
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; confirmError = null; errorMessage = null },
                    obscureText = obscureConfirm,
                    error = confirmError,
                    suffixIcon = {
                        IconButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = { obscureConfirm = !obscureConfirm }) {
                            Icon(
                                if (obscureConfirm) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (obscureConfirm) authCopy("Show password") else authCopy("Hide password"),
                                tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(ChatyUiTokens.space24))
                errorMessage?.let {
                    Text(
                        it,
                        color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor),
                        fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text13_5,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = ChatyUiTokens.space16),
                    )
                }
                AuthPrimaryButton(
                    text = authCopy("Reset Password"),
                    theme = theme,
                    onClick = { handleResetPassword() },
                    isLoading = isLoading,
                    capsule = true,
                )
            }
        }
    }
}
