package com.example.chat.ui.auth

import com.example.chat.ui.core.components.auth.ChatyUiTokens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig

/** Play UGC policy (P-06): account creation must be gated on explicit terms
 *  acceptance. The row is shared by RegisterScreen and RegisterOrganizationScreen.
 *  The linked documents are bundled drafts (app/src/main/assets/legal) so the
 *  step works before the hosted copies exist; text is rendered, no WebView. */

internal const val TERMS_ANNOTATION = "terms"
internal const val PRIVACY_ANNOTATION = "privacy"

@Composable
fun TermsAcceptanceRow(
    theme: ThemeConfig,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))) { append(authCopy("I agree to the ")) }
        pushStringAnnotation(tag = TERMS_ANNOTATION, annotation = TERMS_ANNOTATION)
        withStyle(SpanStyle(color = Color(theme.linkColor), fontWeight = FontWeight.SemiBold)) {
            append(authCopy("Terms of Use"))
        }
        pop()
        withStyle(SpanStyle(color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))) { append(authCopy(" and ")) }
        pushStringAnnotation(tag = PRIVACY_ANNOTATION, annotation = PRIVACY_ANNOTATION)
        withStyle(SpanStyle(color = Color(theme.linkColor), fontWeight = FontWeight.SemiBold)) {
            append(authCopy("Privacy Policy"))
        }
        pop()
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(ChatyUiTokens.space8))
        ClickableText(
            text = annotated,
            style = androidx.compose.ui.text.TextStyle(fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text13),
            onClick = { offset ->
                when {
                    annotated.getStringAnnotations(TERMS_ANNOTATION, offset, offset).isNotEmpty() -> onOpenTerms()
                    annotated.getStringAnnotations(PRIVACY_ANNOTATION, offset, offset).isNotEmpty() -> onOpenPrivacy()
                    else -> onCheckedChange(!checked)
                }
            },
        )
    }
}

/** Simple reader for a bundled legal document. Theme tokens only. */
@Composable
fun LegalTextDialog(
    theme: ThemeConfig,
    title: String,
    assetPath: String,
    onDismiss: () -> Unit,
) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val context = LocalContext.current
    val body by produceState<String?>(initialValue = null, assetPath) {
        value = runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrElse { authCopy("Could not load this document from the app bundle.") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = onDismiss) { Text(authCopy("Close"), color = Color(theme.linkColor)) } },
        title = { Text(title, color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor), fontWeight = FontWeight.Bold, fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text16) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    body ?: authCopy("Loading..."),
                    color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                    fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text13,
                )
                Spacer(Modifier.height(ChatyUiTokens.space8))
            }
        },
    )
}

/** Reading the review drafts is always available. It does not activate consent or age gates. */
@Composable
fun AuthLocalLegalFooter(theme:ThemeConfig) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    var document by androidx.compose.runtime.remember {androidx.compose.runtime.mutableStateOf<Pair<String,String>?>(null)}
    Column(Modifier.fillMaxWidth().padding(top=ChatyUiTokens.space16)) {
        Text(authCopy("Review drafts - version 0.3, 15 November 2026. Not legally reviewed or published."),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text12)
        com.example.chat.ui.core.components.auth.ChatyAuthLink(authCopy("Terms of Use (draft)"),theme,{document=authCopy("Terms of Use - review draft") to "legal/terms_of_use.md"})
        com.example.chat.ui.core.components.auth.ChatyAuthLink(authCopy("Privacy Policy (draft)"),theme,{document=authCopy("Privacy Policy - review draft") to "legal/privacy_policy.md"})
    }
    Text(authCopy("Temporary policy settings. Company and contact are placeholders; no final legal acceptance is collected."),color=com.example.chat.ui.auth.authReadable(theme,theme.secondaryTextColor),fontSize=ChatyUiTokens.text12)
    document?.let {(title,path)->LegalTextDialog(theme,title,path,{document=null})}
}
