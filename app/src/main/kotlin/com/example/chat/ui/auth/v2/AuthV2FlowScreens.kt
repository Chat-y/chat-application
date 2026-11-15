package com.example.chat.ui.auth.v2

import androidx.compose.ui.semantics.clearAndSetSemantics
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.data.auth.v2.AuthV2Approval
import com.example.chat.data.auth.v2.AuthV2ContactAdmin
import com.example.chat.data.auth.v2.AuthV2SsoProvider
import com.example.chat.data.auth.v2.AuthV2Terms
import com.example.chat.data.auth.v2.AuthV2WorkspaceAccount
import com.example.chat.data.auth.v2.AuthV2WorkspaceSummary
import com.example.chat.ui.auth.AuthOrDivider
import com.example.chat.ui.auth.AuthPrimaryButton
import com.example.chat.ui.auth.AuthTextField
import com.example.chat.ui.core.components.auth.AuthErrorNotice
import com.example.chat.ui.core.components.auth.ChatyAuthLink
import com.example.chat.ui.core.components.auth.ChatyAuthScreen
import com.example.chat.ui.core.components.auth.ChatyUiTokens
import com.example.chat.ui.core.components.auth.OtpCodeCells
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import kotlinx.coroutines.delay

/** Additive v2 auth screens. Every screen is driven by global tokens/theme
 *  and shared auth components, takes its data and callbacks as parameters
 *  (never touches ServiceLocator), and is mounted only by the v2 flow host
 *  while ReleaseFeatures.AUTH_V2_CONTRACTS gates the flow. Legacy login and
 *  register screens are untouched. Links open with the system viewer until
 *  the approved Custom Tab dependency lands with the shared-res owner. */

private fun openUri(context: android.content.Context, uri: String) {
    runCatching {
        val parsed=Uri.parse(uri)
        require(parsed.scheme=="https" && !parsed.host.isNullOrBlank() && parsed.userInfo==null)
        // Standard Custom Tabs protocol, no WebView or page-script bridge.
        val extras=android.os.Bundle().apply {putBinder("android.support.customtabs.extra.SESSION",null)}
        context.startActivity(Intent(Intent.ACTION_VIEW,parsed).putExtras(extras)
            .putExtra("android.support.customtabs.extra.TITLE_VISIBILITY",1)
            .putExtra("android.support.customtabs.extra.SHARE_MENU_ITEM",false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Masked for accessibility only (item 18): the visible text is untouched. */
internal fun maskEmailForA11y(email: String): String = com.example.chat.ui.auth.OtpVerificationLogic.maskEmail(email)

/** Find-your-workspace gate (items 31/32/36): slug hint, debounced discovery
 *  results, and the email-domain suggestion row. */
@Composable
fun AuthV2WorkspaceGate(
    theme: ThemeConfig,
    workspace: String,
    onWorkspaceChange: (String) -> Unit,
    email: String,
    suggestions: List<AuthV2WorkspaceSummary>,
    domainSuggestion: AuthV2WorkspaceSummary?,
    searching: Boolean,
    error: String?,
    onSearch: (String) -> Unit,
    onPick: (AuthV2WorkspaceSummary) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    ChatyAuthScreen(theme) {
        ChatyIcon(ChatyIconId.workspace, color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor), size = ChatyUiTokens.space40)
        Spacer(Modifier.height(ChatyUiTokens.space16))
        Text(authCopy("Find your workspace"), color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
            fontSize = ChatyUiTokens.text22, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        Text(authCopy("Enter the name your team gave you, or search for it."),
            color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), fontSize = ChatyUiTokens.text14)
        Spacer(Modifier.height(ChatyUiTokens.space24))
        AuthTextField(
            label = authCopy("Workspace"), hintText = "your-team", theme = theme,
            value = workspace, onValueChange = onWorkspaceChange,
            keyboardType = KeyboardType.Text, capsule = true,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done, onDone = onContinue,
        )
        Text(authCopy("Letters, numbers and dashes - the short name in your invite."),
            color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), fontSize = ChatyUiTokens.text12)
        if (domainSuggestion != null) {
            Spacer(Modifier.height(ChatyUiTokens.space16))
            Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically) {
                ChatyIcon(ChatyIconId.info, color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor), size = ChatyUiTokens.icon)
                Spacer(Modifier.width(ChatyUiTokens.space8))
                Text(authCopy("From your email: "), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), fontSize = ChatyUiTokens.text13)
                ChatyAuthLink(domainSuggestion.name, theme, onClick = { onPick(domainSuggestion) })
            }
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(ChatyUiTokens.space16))
            suggestions.forEach { entry ->
                TextButton(onClick = { onPick(entry) }, Modifier.fillMaxWidth().padding(vertical=ChatyUiTokens.touchGap / 2).heightIn(min = ChatyUiTokens.tapTarget)) {
                    ChatyIcon(ChatyIconId.workspace, color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), size = ChatyUiTokens.icon)
                    Spacer(Modifier.width(ChatyUiTokens.space8))
                    Text(entry.name, color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor), fontSize = ChatyUiTokens.text14)
                }
            }
        }
        AuthErrorNotice(error, theme)
        Spacer(Modifier.height(ChatyUiTokens.space24))
        AuthPrimaryButton(if (searching) "Searching…" else "Continue", theme,
            onClick = { if (suggestions.isEmpty()) onSearch(workspace) else onContinue() },
            isLoading = searching, capsule = true, enabled = workspace.isNotBlank())
        ChatyAuthLink(authCopy("Back"), theme, onClick = onBack)
    }
}

/** Check-your-email + code step: masked email, 6-box code with auto-submit
 *  (13), resend countdown, Open mail app, wrong-address recovery (51). */
@Composable
fun AuthV2CheckEmailScreen(
    theme: ThemeConfig,
    email: String,
    code: String,
    onCodeChange: (String) -> Unit,
    resendAfterSeconds: Int,
    submitting: Boolean,
    error: String?,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onWrongAddress: () -> Unit,
    codeLength: Int = 6,
) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    val context = LocalContext.current
    var deadline by remember(resendAfterSeconds,email) { mutableStateOf(android.os.SystemClock.elapsedRealtime()+resendAfterSeconds*1000L) }
    var secondsLeft by remember {mutableStateOf(resendAfterSeconds)}
    var submittedCode by remember(email) {mutableStateOf<String?>(null)}
    LaunchedEffect(deadline) {
        do {
            secondsLeft=(((deadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)+999L)/1000L).toInt()
            if(secondsLeft>0) delay(250)
        } while(secondsLeft>0)
    }
    LaunchedEffect(code) {
        if(code.length<codeLength) submittedCode=null
        if(code.length==codeLength && !submitting && submittedCode!=code) {submittedCode=code;onSubmit()}
    }
    ChatyAuthScreen(theme) {
        ChatyIcon(ChatyIconId.mail, color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor), size = ChatyUiTokens.space40)
        Spacer(Modifier.height(ChatyUiTokens.space16))
        Text(authCopy("Check your email"), color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
            fontSize = ChatyUiTokens.text22, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        Text(authCopy("We sent a 6-digit code to"),
            color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), fontSize = ChatyUiTokens.text14)
        Text(email, color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor), fontSize = ChatyUiTokens.text14,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(maskEmailForA11y(email)) })
        Spacer(Modifier.height(ChatyUiTokens.space24))
        OtpCodeCells(code = code, onCodeChange = onCodeChange, theme = theme)
        AuthErrorNotice(error, theme)
        Spacer(Modifier.height(ChatyUiTokens.space16))
        AuthPrimaryButton(authCopy("Verify"), theme, onClick = onSubmit, isLoading = submitting,
            capsule = true, enabled = code.length == codeLength)
        Spacer(Modifier.height(ChatyUiTokens.space8))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            if (secondsLeft > 0) {
                Text(com.example.chat.core.i18n.authFormatted("Resend in %1\$ds",secondsLeft), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                    fontSize = ChatyUiTokens.text13,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } else {
                ChatyAuthLink(authCopy("Resend code"), theme, onClick = {deadline=android.os.SystemClock.elapsedRealtime()+resendAfterSeconds*1000L;submittedCode=null;onResend()}, enabled = !submitting)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            ChatyAuthLink(authCopy("Open mail app"), theme, onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
            ChatyAuthLink(authCopy("Wrong address?"), theme, onClick = onWrongAddress, enabled = !submitting)
        }
    }
}

/** Waiting-for-approval (item 42) with contact-admin (item 37). */
@Composable
fun AuthV2PendingApprovalScreen(
    theme: ThemeConfig,
    email: String,
    approval: AuthV2Approval?,
    contactAdmin: AuthV2ContactAdmin?,
    checking: Boolean,
    onCheckAgain: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    val context = LocalContext.current
    ChatyAuthScreen(theme) {
        ChatyIcon(ChatyIconId.hourglass, color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor), size = ChatyUiTokens.space40)
        Spacer(Modifier.height(ChatyUiTokens.space16))
        Text(authCopy("Waiting for approval"), color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
            fontSize = ChatyUiTokens.text22, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        Text(
            when (approval?.state) {
                AuthV2Approval.State.REJECTED -> approval.reason
                    ?: authCopy("This workspace did not approve the request. Contact your administrator.")
                AuthV2Approval.State.APPROVED -> authCopy("Approved. You can sign in now.")
                else -> authCopy("Your workspace administrator needs to approve your account. We will take you in as soon as it is approved.")
            },
            color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), fontSize = ChatyUiTokens.text14,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (contactAdmin?.available == true) {
            Spacer(Modifier.height(ChatyUiTokens.space16))
            contactAdmin.message?.let {
                Text(authCopy(it), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), fontSize = ChatyUiTokens.text13)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                contactAdmin.email?.let { adminEmail ->
                    ChatyAuthLink(authCopy("Email admin"), theme, onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$adminEmail"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    })
                }
                contactAdmin.url?.let { adminUrl -> ChatyAuthLink(authCopy("Contact admin"), theme, onClick = { openUri(context, adminUrl) }) }
            }
        }
        Spacer(Modifier.height(ChatyUiTokens.space24))
        if (approval?.state == AuthV2Approval.State.APPROVED) {
            AuthPrimaryButton(authCopy("Sign in"), theme, onClick = onBackToLogin, capsule = true)
        } else {
            AuthPrimaryButton(authCopy("Check again"), theme, onClick = onCheckAgain, isLoading = checking, capsule = true)
        }
        ChatyAuthLink(authCopy("Back to login"), theme, onClick = onBackToLogin)
    }
}

/** Forgot username (item 39): always the same server answer. */
@Composable
fun AuthV2ForgotUsernameScreen(
    theme: ThemeConfig,
    email: String,
    onEmailChange: (String) -> Unit,
    sending: Boolean,
    sentMessage: String?,
    error: String?,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    ChatyAuthScreen(theme) {
        Text(authCopy("Forgot username"), color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
            fontSize = ChatyUiTokens.text22, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(ChatyUiTokens.space16))
        if (sentMessage != null) {
            Text(sentMessage, color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor), fontSize = ChatyUiTokens.text14,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(Modifier.height(ChatyUiTokens.space24))
            AuthPrimaryButton(authCopy("Back to login"), theme, onClick = onBack, capsule = true)
        } else {
            Text(authCopy("Enter your email and we will send your username if the address is registered."),
                color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), fontSize = ChatyUiTokens.text14)
            Spacer(Modifier.height(ChatyUiTokens.space24))
            AuthTextField(authCopy("Email"), "you@example.com", theme, email, onEmailChange,
                keyboardType = KeyboardType.Email, capsule = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done, onDone = onSend)
            AuthErrorNotice(error, theme)
            Spacer(Modifier.height(ChatyUiTokens.space24))
            AuthPrimaryButton(authCopy("Send username"), theme, onClick = onSend, isLoading = sending,
                capsule = true, enabled = email.isNotBlank())
            ChatyAuthLink(authCopy("Back"), theme, onClick = onBack)
        }
    }
}

/** Verification-link sent confirmation (item 54). */
@Composable
fun AuthV2VerificationLinkSentScreen(
    theme: ThemeConfig,
    message: String,
    onBackToLogin: () -> Unit,
) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    ChatyAuthScreen(theme) {
        ChatyIcon(ChatyIconId.mail, color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor), size = ChatyUiTokens.space40)
        Spacer(Modifier.height(ChatyUiTokens.space16))
        Text(authCopy("Link sent"), color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
            fontSize = ChatyUiTokens.text22, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        Text(message, color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), fontSize = ChatyUiTokens.text14,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(ChatyUiTokens.space24))
        AuthPrimaryButton(authCopy("Back to login"), theme, onClick = onBackToLogin, capsule = true)
    }
}

/** SSO scaffold (item 43) with admin-contact fallback (item 36). Disabled
 *  providers stay visible but inert; the server callback is 501 by design. */
@Composable
fun AuthV2SsoSection(
    theme: ThemeConfig,
    providers: List<AuthV2SsoProvider>,
    contactAdmin: AuthV2ContactAdmin?,
    starting: Boolean,
    onStart: (AuthV2SsoProvider) -> Unit,
) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    if (providers.isEmpty()) return
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        AuthOrDivider(theme)
        providers.forEach { provider ->
            AuthPrimaryButton(
                authFormat("Continue with %1\$s",provider.name), theme,
                onClick = { onStart(provider) },
                isLoading = starting, capsule = true, enabled = provider.enabled && !starting,
            )
            if (!provider.enabled && contactAdmin?.available == true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(authCopy("SSO is not set up yet."), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                        fontSize = ChatyUiTokens.text12)
                    contactAdmin.email?.let { adminEmail ->
                        ChatyAuthLink(authCopy("Contact admin"), theme, onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$adminEmail"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        })
                    }
                }
            }
            Spacer(Modifier.height(ChatyUiTokens.space8))
        }
    }
}

/** Terms/privacy footer (round-4 footer + item 56). */
@Composable
fun AuthV2TermsFooter(theme: ThemeConfig, terms: AuthV2Terms?) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    if(terms==null) return
    Text(com.example.chat.core.i18n.authFormatted("Workspace terms version: %1\$s. Local documents are review drafts, not the published workspace terms.",terms.version ?: authCopy("not supplied")),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),fontSize=ChatyUiTokens.text12)
    // The owner's current direction is local draft documents, no external legal URLs.

}

/** Workspace switcher rows (item 35) for the signed-in account. */
@Composable
fun AuthV2WorkspaceSwitcher(
    theme: ThemeConfig,
    accounts: List<AuthV2WorkspaceAccount>,
    switching: Boolean,
    onSwitch: (AuthV2WorkspaceAccount) -> Unit,
) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    Column(Modifier.fillMaxWidth()) {
        accounts.forEach { account ->
            TextButton(
                onClick = { if (account.switchable) onSwitch(account) },
                enabled = account.switchable && !switching,
                modifier = Modifier.padding(ChatyUiTokens.touchGap / 2).fillMaxWidth().padding(vertical=ChatyUiTokens.touchGap / 2).heightIn(min = ChatyUiTokens.tapTarget),
            ) {
                ChatyIcon(ChatyIconId.workspace, color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor), size = ChatyUiTokens.icon)
                Spacer(Modifier.width(ChatyUiTokens.space8))
                Column(Modifier.weight(1f)) {
                    Text(account.name, color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor), fontSize = ChatyUiTokens.text14)
                    Text(
                        when {
                            account.current -> authCopy("Current workspace")
                            account.accountStatus != "active" -> authCopy("Unavailable")
                            else -> account.username
                        },
                        color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), fontSize = ChatyUiTokens.text12)
                }
                if (account.current) {
                    ChatyIcon(ChatyIconId.check, color = Color(theme.successColor), size = ChatyUiTokens.icon)
                }
            }
        }
    }
}

/** Visible deferred state, not a button launching a callback that is known not to work. */
@Composable
fun AuthV2DeferredSsoSection(theme:ThemeConfig,providers:List<AuthV2SsoProvider>,contact:AuthV2ContactAdmin?) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val context=LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical=ChatyUiTokens.touchGap)) {
        Text(authCopy(com.example.chat.data.auth.v2.AuthPendingContracts.ssoNotice),color=com.example.chat.ui.auth.authReadable(theme,theme.secondaryTextColor))
        providers.forEach {provider->Text(provider.name,color=com.example.chat.ui.auth.authReadable(theme,theme.secondaryTextColor))}
        contact?.email?.takeIf {it.matches(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"))}?.let {email->
            ChatyAuthLink(authCopy("Contact admin"),theme,{
                runCatching {context.startActivity(Intent(Intent.ACTION_SENDTO,Uri.fromParts("mailto",email,null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
            })
        }
        if(contact?.available!=true) Text(authCopy("Ask your workspace administrator which sign-in methods are available."),color=com.example.chat.ui.auth.authReadable(theme,theme.secondaryTextColor))
    }
}
