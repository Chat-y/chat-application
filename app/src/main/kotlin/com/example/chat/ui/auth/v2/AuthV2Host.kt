package com.example.chat.ui.auth.v2

import com.example.chat.ui.core.components.auth.ChatyUiTokens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.example.chat.data.auth.v2.*
import com.example.chat.ui.auth.*
import com.example.chat.ui.core.components.auth.*
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/** Flag-gated destination. Legacy entry points stay unchanged while the server is undeployed. */
@Composable
fun AuthV2Host(flow: AuthV2FlowController, register: Boolean, initialWorkspace: String,
    onBack: () -> Unit, onSignedIn: () -> Unit, onWorkspace: (String) -> Unit = {}, active: Boolean = true) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    val theme = LocalThemeConfig.current
    val context=androidx.compose.ui.platform.LocalContext.current
    val credentialSupport=rememberAuthCredentials()
    val scope = com.example.chat.ui.core.components.auth.rememberAuthRequestScope(active)
    var step by remember { mutableStateOf(if(register) "register" else "login") }
    val invitation by AuthV2LinkRouter.verifiedInvitation.collectAsState()
    var email by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var username by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var retryDeadline by remember {mutableStateOf(0L)}
    var retryWait by remember {mutableStateOf(0)}
    LaunchedEffect(retryDeadline) {do {
        retryWait=(((retryDeadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0)+999)/1000).toInt()
        if(retryWait>0) kotlinx.coroutines.delay(250)
    } while(retryWait>0)}
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var rememberMe by remember { mutableStateOf(false) }
    var signInAfterReset by remember {mutableStateOf(false)}
    var termsAccepted by remember { mutableStateOf(false) }
    var ageConfirmed by remember {mutableStateOf(false)}
    var onboarding by remember { mutableStateOf<AuthV2Onboarding?>(null) }
    var alternatives by remember { mutableStateOf<List<String>>(emptyList()) }
    var approvalId by remember { mutableStateOf<String?>(null) }
    var approval by remember { mutableStateOf<AuthV2Approval?>(null) }
    var workspace by remember(initialWorkspace) { mutableStateOf(initialWorkspace) }
    var suggestions by remember { mutableStateOf<List<AuthV2WorkspaceSummary>>(emptyList()) }
    var domain by remember { mutableStateOf<AuthV2WorkspaceSummary?>(null) }
    var accounts by remember { mutableStateOf<List<AuthV2WorkspaceAccount>>(emptyList()) }
    fun run(action: suspend () -> Unit) {
        if(!active || busy || retryWait>0) return
        busy=true;error=null
        scope.launch {
            try { authRequest { action() } }
            catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
            catch(pending:AuthV2PendingApprovalException){approvalId=pending.approvalId;email=pending.email;password="";step="approval"}
            catch(e:Exception){
                error=AuthFormPolicy.privateError(e.message ?: "Could not complete the request.",email)
                if(e is AuthV2FlowException && e.retryAfterSeconds!=null) retryDeadline=android.os.SystemClock.elapsedRealtime()+e.retryAfterSeconds*1000L
            }
            finally {busy=false}
        }
    }
    DisposableEffect(flow) { onDispose { flow.discardRegistration() } }
    LaunchedEffect(flow,initialWorkspace,active) {
        if(!active) return@LaunchedEffect
        try { onboarding=flow.onboarding(workspace) }
        catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(e:Exception){error=e.message}
    }
    LaunchedEffect(email,flow,active) {
        domain=null
        if(active && email.contains('@') && email.substringAfter('@').contains('.')) {
            kotlinx.coroutines.delay(450)
            try {domain=flow.suggestWorkspaceForEmail(email)} catch(cancel:kotlinx.coroutines.CancellationException){throw cancel} catch(_:Exception){}
        }
    }
    LaunchedEffect(invitation,workspace) {
        invitation?.takeIf {it.result.workspace==workspace}?.let {
            email=it.result.boundEmail ?: email;step="register"
        }
    }
    val inviteForForm=invitation?.takeIf {it.result.workspace==workspace && it.result.expiresAt.isAfter(java.time.Instant.now())}
    when(step) {
        "scan" -> AuthWorkspaceScanner(theme,onBack={step="login"}) {raw->
            val parsed=com.example.chat.ui.auth.AuthLinkPolicy.parse(raw)
            if(parsed==null) notice="That is not a supported Chaty workspace or invitation QR."
            else if(parsed.kind=="invite") run {
                AuthV2LinkRouter.clearVerified()
                when(val result=flow.verifyInvitation(parsed.token.orEmpty(),parsed.workspace)) {
                    is InvitationVerification.Valid->{AuthV2LinkRouter.acceptVerified(parsed.token.orEmpty(),result);onWorkspace(result.workspace);workspace=result.workspace;email=result.boundEmail ?: email;step="register";notice="Invitation checked. Complete registration to join the workspace."}
                    InvitationVerification.Expired->notice="This invitation has expired. Ask for a new one."
                    InvitationVerification.Redeemed->notice="This invitation was already used. Ask for a new one."
                    else->notice="This invitation could not be verified."
                }
            }
            else if(parsed.workspace!=null) {run {val validated=flow.onboarding(parsed.workspace);workspace=validated.tenantId;onWorkspace(validated.tenantId);notice="Workspace selected. Sign in to continue."}}
            else notice="Open email-verification links from your email app."
            step="login"
        }
        "workspace" -> AuthV2WorkspaceGate(theme,workspace,{workspace=it},email,suggestions,domain,busy,error,
            onSearch={query->run {suggestions=flow.discoverWorkspaces(query); if(email.contains('@')) domain=flow.suggestWorkspaceForEmail(email)}},
            onPick={picked->workspace=picked.tenantId;onWorkspace(picked.tenantId);step=if(register) "register" else "login"},
            onContinue={onWorkspace(workspace.trim());step=if(register) "register" else "login"},onBack={step=if(register) "register" else "login"})
        "code" -> AuthV2CheckEmailScreen(theme,email,code,{code=OtpVerificationLogic.sanitizeCode(it)},60,busy,error,
            onSubmit={run {when(val result=flow.completeRegister(code)) {
                is AuthV2FlowController.AuthV2RegisterCompletion.Adopted -> {credentialSupport.save(username.trim(),password);password="";code="";AuthV2LinkRouter.clearVerified();onSignedIn()}
                is AuthV2FlowController.AuthV2RegisterCompletion.PendingApproval -> {approvalId=result.approvalId;password="";code="";step="approval"}
            }}},onResend={run {notice=flow.retryRegisterStart().message;code=""}},
            onWrongAddress={flow.discardRegistration();code="";step="register"})
        "approval" -> AuthV2PendingApprovalScreen(theme,email,approval,onboarding?.contactAdmin,busy,
            onCheckAgain={approvalId?.let {id->run {approval=flow.approvalState(id,email)}}},
            onBackToLogin={step="login"})
        "reset" -> ChatyAuthScreen(theme) {
            ChatyBackButton({password="";code="";step="login"},theme)
            Text(authCopy("Reset password"),color=com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor))
            AuthTextField(authCopy("Email"),"you@example.com",theme,email,{if(inviteForForm?.result?.boundEmail==null) email=it},keyboardType=KeyboardType.Email,readOnly=inviteForForm?.result?.boundEmail!=null)
            ChatyAuthLink(authCopy("Send reset code"),theme,{run {notice=com.example.chat.ServiceLocator.apiBackend.forgotPassword(email)}})
            AuthTextField(authCopy("Email code"),"",theme,code,{code=OtpVerificationLogic.sanitizeCode(it)},keyboardType=KeyboardType.NumberPassword)
            AuthTextField(authCopy("New password"),authCopy("8 characters minimum, 72 UTF-8 bytes maximum"),theme,password,{password=it},obscureText=true,keyboardType=KeyboardType.Password)
            Text(authCopy("Changing your password signs out other devices. Sign in again with the new password."),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
            Row(Modifier.padding(vertical=ChatyUiTokens.touchGap / 2)) {Checkbox(signInAfterReset,{signInAfterReset=it});Text(authCopy("Sign in after reset"),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))}
            if(retryWait>0) Text(com.example.chat.core.i18n.authFormatted("Try again in %1\$ds.",retryWait),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
            notice?.let {Text(authCopy(it),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))}
            AuthErrorNotice(error,theme,email)
            AuthPrimaryButton(authCopy("Reset password"),theme,onClick={run {
                when(flow.resetPassword(email,code,password,signInAfterReset,rememberMe)) {
                    is AuthV2FlowController.AuthV2ResetResult.SignedIn -> {password="";code="";onSignedIn()}
                    is AuthV2FlowController.AuthV2ResetResult.PasswordUpdated -> {password="";code="";notice="Password changed. Sign in with your new password.";step="login"}
                }
            }},isLoading=busy)
        }
        "forgot" -> AuthV2ForgotUsernameScreen(theme,email,{email=it},busy,notice,error,
            onSend={run {notice=flow.forgotUsername(email)}},onBack={notice=null;step="login"})
        "link" -> AuthV2VerificationLinkSentScreen(theme,notice ?: authCopy("Check your email."),onBackToLogin={step="login"})
        else -> ChatyAuthScreen(theme) {
            ChatyBackButton(onBack,theme)
            onboarding?.branding?.displayName?.let {Text(authCopy(it),color=com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor))}
            onboarding?.branding?.logoUrl?.takeIf {it.startsWith("https://")}?.let {url->
                coil.compose.AsyncImage(model=url,contentDescription=null,modifier=Modifier.heightIn(max=ChatyUiTokens.space76))
            }
            Text(if(step=="register") authCopy("Create account") else authCopy("Sign in"),fontSize=ChatyUiTokens.text26,color=com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor))
            notice?.let {Text(authCopy(it),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))}
            if(step=="register") {
                AuthTextField(authCopy("Email"),"you@example.com",theme,email,{if(inviteForForm?.result?.boundEmail==null) email=it},keyboardType=KeyboardType.Email,readOnly=inviteForForm?.result?.boundEmail!=null)
                com.example.chat.ui.core.components.auth.PhoneNumberEntry(phone,theme=theme,enabled=!busy,onValueChange={phone=it},onNormalized={phone=it.orEmpty()})
            }
            AuthTextField(if(step=="register") authCopy("Username") else authCopy("Email or username"),"",theme,username,{username=it})
            AuthTextField(authCopy("Password"),"",theme,password,{password=it},obscureText=true,keyboardType=KeyboardType.Password)
            Text(authCopy("Password: at least 8 characters, at most 72 UTF-8 bytes. Usernames use letters, numbers and underscores."),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
            Row(Modifier.padding(vertical=ChatyUiTokens.touchGap / 2)) {Checkbox(rememberMe,{rememberMe=it});Text(authCopy("Keep me signed in for longer. Turn off on a shared device; the workspace controls session length."),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))}
            onboarding?.terms?.takeIf {it.required && com.example.chat.data.auth.v2.AuthConsentReadiness.mayCollectAcceptance(com.example.chat.ReleaseFeatures.LEGAL_ACCEPTANCE_GATE,it.version,com.example.chat.data.auth.v2.AuthPendingContracts.finalLegalPolicyAvailable)}?.let {
                Row(Modifier.padding(vertical=ChatyUiTokens.touchGap / 2)) {Checkbox(termsAccepted,{termsAccepted=it});Text(com.example.chat.core.i18n.authFormatted("I accept the terms, version %1\$s",it.version.orEmpty()),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))}
            }
            if(step=="register") com.example.chat.ui.auth.AuthAgeConsentStep(theme,ageConfirmed,{ageConfirmed=it})
            AuthV2TermsFooter(theme,onboarding?.terms)
            domain?.let {suggested->ChatyAuthLink(suggested.name,theme,{onWorkspace(suggested.tenantId)})}
            alternatives.forEach {candidate->ChatyAuthLink(candidate,theme,{username=candidate})}
            AuthErrorNotice(error,theme,email)
            AuthPrimaryButton(if(step=="register") "Create account" else "Log In",theme,onClick={run {
                if(step=="register") {
                    check(!com.example.chat.ui.auth.AuthTemporaryPolicy.ageRuleConfirmed || ageConfirmed) {"Confirm the configured age requirement before continuing."}
                    check(invitation==null || inviteForForm!=null) {"This invitation has expired or belongs to another workspace. Verify it again before registering."}
                    check(onboarding?.terms?.required != true || (com.example.chat.data.auth.v2.AuthConsentReadiness.mayCollectAcceptance(com.example.chat.ReleaseFeatures.LEGAL_ACCEPTANCE_GATE,onboarding?.terms?.version,com.example.chat.data.auth.v2.AuthPendingContracts.finalLegalPolicyAvailable) && termsAccepted)){"This workspace requires published terms. The local review draft is not an acceptance document yet."}
                    val result=flow.startRegister(email,username,password,phone,rememberMe,onboarding?.terms?.version.takeIf {termsAccepted},inviteToken=inviteForForm?.token)
                    alternatives=result.usernameAlternatives;notice=result.message
                    if(result.usernameAlternatives.isEmpty()) step="code"
                } else {val submittedName=username.trim();val submittedPassword=password;flow.login(submittedName,submittedPassword,rememberMe);credentialSupport.save(submittedName,submittedPassword);password="";onSignedIn()}
            }},isLoading=busy)
            if(step=="register") ChatyAuthLink(authCopy("Suggest usernames"),theme,{run {alternatives=flow.usernameAlternatives(username)}})
            ChatyAuthLink(authCopy("Scan a workspace QR"),theme,{step="scan"})
            if(inviteForForm==null) ChatyAuthLink(authCopy("Find your workspace"),theme,{step="workspace"})
            else ChatyAuthLink(authCopy("Use a different invitation"),theme,{AuthV2LinkRouter.clearVerified();email="";password="";step="workspace"})
            if(step=="login") ChatyAuthLink(authCopy("Use a saved password"),theme,{run {
                val saved=credentialSupport.retrieve()
                if(saved is com.example.chat.core.auth.PasswordCredentialStore.RetrieveOutcome.Found) {username=saved.identifier;password=saved.password}
            }})
            ChatyAuthLink(authCopy("Forgot password?"),theme,{notice=null;step="reset"})
            ChatyAuthLink(authCopy("Forgot username?"),theme,{notice=null;step="forgot"})
            ChatyAuthLink(authCopy("Send verification link"),theme,{run {notice=flow.sendVerificationLink(email.ifBlank{username});step="link"}})
            ChatyAuthLink(authCopy("Your workspaces"),theme,{run {accounts=flow.myWorkspaces()}})
            AuthV2WorkspaceSwitcher(theme,accounts,busy){account->run {flow.switchWorkspace(account.tenantId);onSignedIn()}}
            if(onboarding?.ssoEnabled==true) {
                // Owner deferred SSO: retain UI source but do not mount it.
                // AuthV2DeferredSsoSection(theme,onboarding?.ssoProviders.orEmpty(),onboarding?.contactAdmin)

                // Callback remains501 server-side. Do not launch an SSO journey that cannot finish.
            }
        }
    }
}
