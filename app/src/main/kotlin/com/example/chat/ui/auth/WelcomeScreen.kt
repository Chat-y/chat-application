package com.example.chat.ui.auth

import com.example.chat.ui.core.components.auth.ChatyUiTokens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.kit.ChatyAdaptiveLogic
import com.example.chat.ui.theme.LocalThemeConfig

/** Port of lib/features/auth/welcome_screen.dart. */
@Composable
fun WelcomeScreen(onLogin: () -> Unit, onRegister: () -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val theme = LocalThemeConfig.current
    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = ChatyUiTokens.space24, vertical = ChatyUiTokens.space16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // R3: auth content caps at 460dp, centered (responsive plan).
            Column(
                modifier = Modifier
                    .widthIn(max = ChatyAdaptiveLogic.AUTH_COLUMN_MAX_DP.dp)
                    .fillMaxWidth()
                    .weight(1f),
            ) {
            Spacer(Modifier.height(ChatyUiTokens.space24))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                AuthIllustration(type = "welcome", theme = theme, height = 220)
                Spacer(Modifier.height(ChatyUiTokens.space24))
                Text(
                    authCopy("Welcome to Chaty"),
                    textAlign = TextAlign.Center,
                    color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
                    fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text30,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(ChatyUiTokens.space8))
                Text(
                    authCopy("Private, customizable, and lightning-fast messaging."),
                    textAlign = TextAlign.Center,
                    color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                    fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text15,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.weight(1f))
            }
            AuthPrimaryButton(text = authCopy("Log In"), theme = theme, onClick = onLogin)
            Spacer(Modifier.height(ChatyUiTokens.space16))
            AuthSecondaryButton(text = authCopy("Register"), theme = theme, onClick = onRegister)
            Spacer(Modifier.height(ChatyUiTokens.space24))
            }
        }
    }
}
