package com.example.chat.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.core.components.auth.ChatyUiTokens

/** Local configurable policy step. Never derives age from an account or collects a date of birth. */
@Composable
fun AuthAgeConsentStep(theme:ThemeConfig,confirmed:Boolean,onConfirmed:(Boolean)->Unit) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    Text(authCopy("Age and consent - temporary settings"),color=authReadable(theme,theme.primaryTextColor))
    Text(com.example.chat.core.i18n.authFormatted("Configured minimum age: %1\$d. This provisional setting is not a reviewed legal requirement.",AuthTemporaryPolicy.configuredMinimumAge),color=authReadable(theme,theme.secondaryTextColor))
    Row(Modifier.fillMaxWidth().padding(vertical=ChatyUiTokens.touchGap / 2)) {
        Checkbox(checked=confirmed,onCheckedChange=onConfirmed)
        Text(com.example.chat.core.i18n.authFormatted("I confirm I am at least %1\$d years old.",AuthTemporaryPolicy.configuredMinimumAge),color=authReadable(theme,theme.primaryTextColor))
    }
    if(!AuthTemporaryPolicy.ageRuleConfirmed) Text(authCopy("Preview only. Age threshold and any guardian consent rules need owner review before this step can block registration."),color=authReadable(theme,theme.secondaryTextColor))
}
