package com.example.chat.ui.core.components.auth

import com.example.chat.ui.auth.OtpVerificationLogic

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

@Composable
fun OtpCodeCells(
    code: String,
    onCodeChange: (String) -> Unit,
    theme: ThemeConfig,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val colors = LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val description = context.getString(context.resources.getIdentifier("auth_otp_field_cd","string",context.packageName), OtpVerificationLogic.CODE_LENGTH)
    val progress = context.getString(context.resources.getIdentifier("auth_otp_progress_cd","string",context.packageName), code.length, OtpVerificationLogic.CODE_LENGTH)
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical=ChatyUiTokens.touchGap / 2).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusRequester.requestFocus() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(ChatyUiTokens.space8),
        ) {
            repeat(OtpVerificationLogic.CODE_LENGTH) { index ->
                val digit = code.getOrNull(index)
                val cellTint by animateColorAsState(
                    targetValue = if (digit != null || index == code.length) {
                        Color(theme.accentColor)
                    } else {
                        colors.border
                    },
                    animationSpec = tween(
                        durationMillis = if (reduced) 0 else com.example.chat.ui.kit.ChatyMotion.fastMs,
                    ),
                    label = "otp-cell-tint-$index",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = ChatyUiTokens.space56)
                        .background(colors.inputFill, ChatyUiTokens.cardShape)
                        .border(ChatyUiTokens.space1, cellTint, ChatyUiTokens.cardShape),
                    contentAlignment = Alignment.Center,
                ) {
                    FittedAuthText(
                        digit?.toString() ?: "-",
                        color = if (digit != null) {
                            Color(theme.primaryTextColor)
                        } else {
                            Color(argbWithAlpha(theme.secondaryTextColor, 0.5))
                        },
                        fontSize = ChatyUiTokens.text22,
                        weight = FontWeight.Bold,
                    )
                }
            }
        }
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .focusRequester(focusRequester)
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = progress
                },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
        )
    }
}
