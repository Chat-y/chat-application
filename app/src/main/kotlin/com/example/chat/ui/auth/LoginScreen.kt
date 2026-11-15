package com.example.chat.ui.auth

import com.example.chat.ui.core.components.auth.ChatyUiTokens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.DomainAdd
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.auth.friendlyLoginError
import com.example.chat.data.api.ApiSessionStorageException
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.core.validators.ChatyValidators
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch
import com.example.chat.ui.kit.ChatyAdaptiveLogic

/** Label for the secure-storage-failure support action. A share chooser is
 *  not customer care, so this is "Share error report" until a verified support
 *  address exists (publisher review 2026-11-04; Bandi asked, pending) - then
 *  flip this single constant to "Contact customer care". */
private const val STORAGE_FAILURE_ACTION_LABEL = "Share error report"

/** Port of lib/features/auth/login_screen.dart. The login call is injected so
 *  the screen never touches the service locator directly (testable seam);
 *  AppRoot wires ServiceLocator.apiBackend login plus
 *  LocalPreferencesStorage.setStoredUserId, mirroring the Dart call sequence. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: (userId: String) -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
    onRegisterOrganization: () -> Unit,
    login: suspend (identifier: String, password: String, tenant: String?, isDefaultTenant: Boolean) -> String,
    demoLogin: (suspend () -> String)? = null,
    /** Dart initState: getSavedTenantId + isUsingDefaultTenant. */
    loadSavedTenant: suspend () -> Pair<String?, Boolean>,
    active: Boolean = true,
    checkWorkspace: (suspend (String) -> String)? = null,
    initialIdentifier: String = "",
    initialWorkspace: String? = null,
    requestEmailCode: (suspend (email: String, tenant: String?, isDefaultTenant: Boolean) -> com.example.chat.data.api.ApiBackendService.EmailCodeAttempt)? = null,
    verifyEmailCode: (suspend (com.example.chat.data.api.ApiBackendService.EmailCodeAttempt, String) -> String)? = null,
    onCodeResumeState:(Boolean)->Unit = {},
    saveEmailAttempt: ((com.example.chat.data.api.ApiBackendService.EmailCodeAttempt)->com.example.chat.data.api.EmailCodeResume)? = null,
    restoreEmailAttempt: ((com.example.chat.data.api.EmailCodeResume)->com.example.chat.data.api.ApiBackendService.EmailCodeAttempt?)? = null,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    // Keep the deployed password route as the default until code-login backend is live.
    var useEmailCode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var identifier by androidx.compose.runtime.saveable.rememberSaveable(initialIdentifier) { mutableStateOf(initialIdentifier) }
    var workspace by androidx.compose.runtime.saveable.rememberSaveable(initialWorkspace) { mutableStateOf(initialWorkspace.orEmpty()) }
    val modePrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("chaty_auth_ui", android.content.Context.MODE_PRIVATE)
    var useOrganization by remember(initialWorkspace) { mutableStateOf(!initialWorkspace.isNullOrBlank()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (initialWorkspace == null) {
            val (saved, usingDefault) = loadSavedTenant()
            useOrganization = !usingDefault && !saved.isNullOrBlank() &&
                !saved.equals(com.example.chat.data.api.BackendRoutingPolicy.DEFAULT_TENANT_ID, ignoreCase = true)
            workspace = if (useOrganization) saved.orEmpty() else ""
            if (modePrefs.contains("organization")) useOrganization = modePrefs.getBoolean("organization", useOrganization)
        }
    }
    if (useEmailCode && requestEmailCode != null && verifyEmailCode != null) {
        EmailCodeLoginForm(active, onBack, onLoggedIn, onRegister, onRegisterOrganization,
            { workspace.takeIf { useOrganization } to !useOrganization }, identifier,
            workspace.takeIf { useOrganization }, requestEmailCode, verifyEmailCode, demoLogin,
            saveAttempt=saveEmailAttempt,restoreAttempt=restoreEmailAttempt,onResumeState=onCodeResumeState,
            onUsePassword = { email, selectedWorkspace, organization ->
                identifier = email
                workspace = selectedWorkspace
                useOrganization = organization
                useEmailCode = false
            })
        return
    }
    val theme = LocalThemeConfig.current
    val scope = com.example.chat.ui.core.components.auth.rememberAuthRequestScope(active)
    val feedbackView = androidx.compose.ui.platform.LocalView.current
    val online = com.example.chat.ui.core.components.auth.rememberAuthOnline()

    var password by remember { mutableStateOf("") }
    val credentialSupport=rememberAuthCredentials()
    var obscurePassword by remember { mutableStateOf(true) }
    // Auth screenshot blocking is off by default. Preserve the opt-in hook, do not apply globally.
    // com.example.chat.ui.kit.ProtectSensitiveWindow()
    com.example.chat.ui.core.components.auth.RemaskPasswordOnBackground { obscurePassword = true }

    var isLoading by remember { mutableStateOf(false) }
    var retryDeadline by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val retryWait = com.example.chat.ui.core.components.auth.rememberAuthRetryWait(retryDeadline)
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var identifierError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var workspaceError by remember { mutableStateOf<String?>(null) }
    // Non-null only when sign-in was stopped by a secure-storage failure
    // (fail-closed); drives the Contact-customer-care action (Bandi 2026-11-04).
    var storageFailure by remember { mutableStateOf<ApiSessionStorageException?>(null) }
    val context = LocalContext.current


    // Dart _validateIdentifier
    fun validateIdentifier(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return authCopy("Enter your email or username")
        if (value.contains("@")) return ChatyValidators.validateEmail(value)
        return ChatyValidators.validateUsername(value, forRegistration = false)
    }

    androidx.compose.runtime.LaunchedEffect(listOf(errorMessage, identifierError, passwordError, workspaceError)) {
        if (listOf(errorMessage, identifierError, passwordError, workspaceError).any { !it.isNullOrBlank() }) com.example.chat.ui.kit.ChatyHapticsPort.error(feedbackView)
    }

    fun handleLogin() {
        if (isLoading || retryWait > 0) return
        identifierError = validateIdentifier(identifier)
        passwordError = AuthInputPolicy.legacyPasswordError(password)?.let(authCopy)
        workspaceError =
            if (useOrganization && workspace.trim().isEmpty()) authCopy("Please enter your Workspace ID") else null
        if (identifierError != null || passwordError != null || isLoading) return
        if (useOrganization && workspace.trim().isEmpty()) {
            errorMessage = authCopy("Please enter your Workspace / Organization ID.")
            return
        }

        isLoading = true
        errorMessage = null
        storageFailure = null
        val submittedIdentifier=identifier.trim()
        val submittedPassword=password
        scope.launch {
            try {
                val userId = authRequest { login(
                    submittedIdentifier,
                    submittedPassword,
                    if (useOrganization) workspace.trim() else null,
                    !useOrganization,
                ) }
                com.example.chat.ui.kit.ChatyHapticsPort.success(feedbackView)
                if(useOrganization) AuthWorkspaceHistory.record(feedbackView.context,workspace)
                credentialSupport.save(submittedIdentifier,submittedPassword)
                onLoggedIn(userId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) {
                storageFailure = e as? ApiSessionStorageException
                errorMessage = AuthFormPolicy.privateError(friendlyLoginError(e), identifier)
                if (e is com.example.chat.data.api.AuthRateLimitException) retryDeadline = android.os.SystemClock.elapsedRealtime() + e.retryAfterSeconds * 1000L
            } finally { isLoading = false }
        }
    }

    var workspaceName by remember { mutableStateOf<String?>(null) }
    var checkingWorkspace by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(workspace, useOrganization, active) {
        workspaceName = null
        checkingWorkspace = false
        if (active && useOrganization && workspace.isNotBlank() && checkWorkspace != null) {
            checkingWorkspace = true
            try {
                kotlinx.coroutines.delay(400)
                workspaceName = checkWorkspace(workspace.trim())
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { workspaceName = null }
            finally { checkingWorkspace = false }
        }
    }
    val guardedBack = com.example.chat.ui.core.components.auth.rememberAuthBackAction(active && (identifier.isNotBlank() || password.isNotEmpty() || workspace.isNotBlank()), onBack)
    com.example.chat.ui.core.components.auth.ChatyAuthScreen(theme) {
                com.example.chat.ui.core.components.auth.ChatyBackButton(guardedBack, theme)
                com.example.chat.ui.core.components.auth.AuthOfflineNotice(online, theme)
                com.example.chat.ui.core.components.auth.AuthLookupProgress(checkingWorkspace, theme)
                workspaceName?.let { Text(authCopy(it), color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor), fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(ChatyUiTokens.space24))
                com.example.chat.ui.core.components.auth.ChatyAuthReferenceHeading(
                    authCopy("Welcome back"), authCopy("back"),
                    authCopy("Sign in to Chaty to continue your conversations"), theme, logo = true)
                Spacer(Modifier.height(ChatyUiTokens.space24))

                AuthCapsuleModeSelector(
                    theme = theme,
                    useOrganization = useOrganization,
                    onSelect = { useOrganization = it; modePrefs.edit().putBoolean("organization", it).apply(); errorMessage = null },
                )
                Spacer(Modifier.height(ChatyUiTokens.space16))

                if (useOrganization) {
                    Text(authCopy("Use the Workspace ID shared by your organization. It keeps your account in the right workspace."), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
                    AuthTextField(
                        label = authCopy("Workspace ID"),
                        hintText = authCopy("e.g. acme-corp"),
                        capsule = true,
                        theme = theme,
                        value = workspace,
                        onValueChange = { workspace = it; workspaceError = null; errorMessage = null },
                        error = workspaceError,
                        prefixIcon = {
                            AuthRoleIcon("workspace", theme)
                        },
                    )
                    AuthWorkspaceSuggestions(workspace,theme) {workspace=it;workspaceError=null;errorMessage=null}
                    Spacer(Modifier.height(ChatyUiTokens.space16))
                }

                AuthTextField(
                    autofillTypes = listOf("Username", "EmailAddress"),
                    label = authCopy("Email or username"),
                    hintText = authCopy("Email or @username"),
                    capsule = true,
                    prefixIcon = { AuthRoleIcon("mail", theme) },
                    theme = theme,
                    value = identifier,
                    onValueChange = { identifier = it; identifierError = null; errorMessage = null },
                    keyboardType = KeyboardType.Email,
                    error = identifierError,
                )
                if (identifier.contains('@')) com.example.chat.ui.core.components.auth.EmailSuggestion(identifier, !isLoading, theme) { identifier = it; identifierError = null }
                Spacer(Modifier.height(ChatyUiTokens.space16))
                AuthTextField(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    onDone = { if (!isLoading) handleLogin() },
                    autofillTypes = listOf("Password"),
                    keyboardType = KeyboardType.Password,
                    label = authCopy("Password"),
                    hintText = authCopy("Enter password"),
                    capsule = true,
                    prefixIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.lock, size = ChatyUiTokens.space20, color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor)) },
                    theme = theme,
                    value = password,
                    onValueChange = { password = it; passwordError = null; errorMessage = null },
                    obscureText = obscurePassword,
                    error = passwordError,
                    suffixIcon = {
                        IconButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = { obscurePassword = !obscurePassword }) {
                            AuthRoleIcon(if (obscurePassword) "eyeOff" else "eye", theme,
                                if (obscurePassword) authCopy("Show password") else authCopy("Hide password"))
                        }
                    },
                )
                Spacer(Modifier.height(ChatyUiTokens.space24))

                com.example.chat.ui.core.components.auth.AuthValidationSummary(listOf(identifierError, passwordError, workspaceError), theme)
                com.example.chat.ui.core.components.auth.AuthRetryNotice(retryWait, theme)
                com.example.chat.ui.core.components.auth.AuthErrorNotice(errorMessage, theme, identifier)

                storageFailure?.let { failure ->
                    // A share chooser is not authCopy("customer care"): this stays
                    // STORAGE_FAILURE_ACTION_LABEL (authCopy("Share error report")) until
                    // a verified support address exists (asked Bandi, pending),
                    // then the single constant flips to authCopy("Contact customer care")
                    // and the intent becomes a direct mailto. The exported
                    // report contains only the allowlisted failure copy - never
                    // the raw store exception, identifier, password, or tokens.
                    TextButton(
                        onClick = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, authCopy("Chaty sign-in problem: secure storage failure"))
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "Customer care,\n\nChaty could not sign me in because the app could not save the session securely on this device.\n\nDetails shown by the app:\n" +
                                        (failure.message ?: authCopy("unknown reason")) +
                                        "\n\nPlease advise how to fix this.",
                                )
                            }
                            runCatching {
                                context.startActivity(
                                    android.content.Intent.createChooser(send, authCopy("Contact customer care")),
                                )
                            }
                        },
                        modifier = Modifier.padding(ChatyUiTokens.touchGap / 2).padding(bottom = ChatyUiTokens.space16),
                    ) {
                        Text(
                            STORAGE_FAILURE_ACTION_LABEL,
                            color = Color(theme.linkColor),
                            fontSize = ChatyUiTokens.text13,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    com.example.chat.ui.core.components.auth.ChatySavedPasswordAction(theme,{
                    if(!isLoading) scope.launch {
                        when(val saved=credentialSupport.retrieve()) {
                            is com.example.chat.core.auth.PasswordCredentialStore.RetrieveOutcome.Found -> {
                                identifier=saved.identifier;password=saved.password;errorMessage=null
                            }
                            com.example.chat.core.auth.PasswordCredentialStore.RetrieveOutcome.None -> Unit
                            com.example.chat.core.auth.PasswordCredentialStore.RetrieveOutcome.Unavailable -> errorMessage=authCopy("Saved passwords are unavailable on this device. You can still sign in below.")
                        }
                    }
                },enabled=!isLoading)
                    com.example.chat.ui.core.components.auth.ChatyAuthLink(authCopy("Forgot Password?"), theme, onForgotPassword)
                }
                AuthPrimaryButton(enabled = retryWait == 0, text = authCopy("Log In"), theme = theme, onClick = { handleLogin() }, isLoading = isLoading, capsule = true)
                if (requestEmailCode != null && verifyEmailCode != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.HorizontalDivider(Modifier.weight(1f), color = Color(argbWithAlpha(theme.secondaryTextColor, 0.2)))
                        com.example.chat.ui.core.components.auth.ChatyAuthLink(authCopy("Use email code instead"), theme, { useEmailCode = true }, !isLoading)
                        androidx.compose.material3.HorizontalDivider(Modifier.weight(1f), color = Color(argbWithAlpha(theme.secondaryTextColor, 0.2)))
                    }
                }
                Spacer(Modifier.height(ChatyUiTokens.space8))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        authCopy("New to Chaty? "),
                        color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor),
                        fontSize = ChatyUiTokens.text13_5,
                    )
                    com.example.chat.ui.core.components.auth.ChatyAuthLink(authCopy("Register"), theme, onRegister, !isLoading)
                }
                Spacer(Modifier.height(ChatyUiTokens.space16))
                androidx.compose.material3.HorizontalDivider(color = Color(argbWithAlpha(theme.secondaryTextColor, 0.2)))
                AuthSecondaryButton(authCopy("Register a New Organization"), theme, onRegisterOrganization)
                // Hidden by owner request. Retain the demo UI source and callback, do not render it.
                // if (demoLogin != null) {
                //     AuthTextLink(text = authCopy("Try demo"), theme = theme, enabled = !isLoading, onClick = {
                //         errorMessage = null
                //         storageFailure = null
                //         isLoading = true
                //         scope.launch {
                //             try {
                //                 val userId = demoLogin()
                //                 onLoggedIn(userId)
                //             } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                //             catch (e: Exception) { errorMessage = e.message ?: authCopy("Demo login failed. Please try again.") }
                //             finally { isLoading = false }
                //         }
                //     })
                //     Spacer(Modifier.height(ChatyUiTokens.space8))
                // }
    }
}

/** Offline demo entry (NOT in Flutter): clearly labelled sample session. */
@Composable
private fun DemoLoginCard(theme: ThemeConfig, enabled: Boolean, onClick: () -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(argbWithAlpha(theme.accentColor, 0.06)), ChatyUiTokens.cardShape)
            .border(ChatyUiTokens.space1, Color(argbWithAlpha(theme.accentColor, 0.2)), ChatyUiTokens.cardShape)
            .padding(vertical=ChatyUiTokens.touchGap / 2).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ChatyUiTokens.space16, vertical = ChatyUiTokens.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(Color(argbWithAlpha(theme.accentColor, 0.15)), ChatyUiTokens.cardShape)
                .padding(ChatyUiTokens.space8),
        ) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.info,
                semanticLabel = null,
                color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor),
                modifier = Modifier.size(ChatyUiTokens.space20),
            )
        }
        Spacer(Modifier.width(ChatyUiTokens.touchGap))
        Column(Modifier.weight(1f)) {
            Text(
                authCopy("Try the demo"),
                color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
                fontSize = ChatyUiTokens.text13,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(ChatyUiTokens.space8))
            Text(
                authCopy("Explore with offline sample data - no account or server"),
                color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                fontSize = ChatyUiTokens.text11_5,
            )
        }
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
            semanticLabel = null,
            color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor),
            modifier = Modifier.size(ChatyUiTokens.space14),
        )
    }
}

@Composable
private fun RegisterOrgCard(theme: ThemeConfig, onClick: () -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(argbWithAlpha(theme.accentColor, 0.06)), ChatyUiTokens.cardShape)
            .border(ChatyUiTokens.space1, Color(argbWithAlpha(theme.accentColor, 0.2)), ChatyUiTokens.cardShape)
            .padding(vertical=ChatyUiTokens.touchGap / 2).clickable(onClick = onClick)
            .padding(horizontal = ChatyUiTokens.space16, vertical = ChatyUiTokens.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(Color(argbWithAlpha(theme.accentColor, 0.15)), ChatyUiTokens.cardShape)
                .padding(ChatyUiTokens.space8),
        ) {
            Icon(
                Icons.Rounded.DomainAdd,
                contentDescription = null,
                tint = Color(theme.accentColor),
                modifier = Modifier.size(ChatyUiTokens.space20),
            )
        }
        Spacer(Modifier.width(ChatyUiTokens.touchGap))
        Column(Modifier.weight(1f)) {
            Text(
                authCopy("Register an Organization"),
                color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
                fontSize = ChatyUiTokens.text13,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(ChatyUiTokens.space8))
            Text(
                authCopy("Set up a custom workspace & team space"),
                color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                fontSize = ChatyUiTokens.text11_5,
            )
        }
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
            semanticLabel = null,
            color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor),
            modifier = Modifier.size(ChatyUiTokens.space14),
        )
    }
}


/** Existing Login route's approved email-code mode. No local success bypass. */
@Composable
private fun EmailCodeLoginForm(
    active: Boolean,
    onBack: () -> Unit, onLoggedIn: (String) -> Unit,
    onRegister: () -> Unit, onRegisterOrganization: () -> Unit,
    loadSavedTenant: suspend () -> Pair<String?, Boolean>,
    initialEmail: String, initialWorkspace: String?,
    requestCode: suspend (String, String?, Boolean) -> com.example.chat.data.api.ApiBackendService.EmailCodeAttempt,
    verifyCode: suspend (com.example.chat.data.api.ApiBackendService.EmailCodeAttempt, String) -> String,
    demoLogin: (suspend () -> String)?,
    onResumeState:(Boolean)->Unit = {},
    saveAttempt: ((com.example.chat.data.api.ApiBackendService.EmailCodeAttempt)->com.example.chat.data.api.EmailCodeResume)? = null,
    restoreAttempt: ((com.example.chat.data.api.EmailCodeResume)->com.example.chat.data.api.ApiBackendService.EmailCodeAttempt?)? = null,
    onUsePassword: (String, String, Boolean) -> Unit,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val theme = LocalThemeConfig.current
    val scope = com.example.chat.ui.core.components.auth.rememberAuthRequestScope(active)
    var email by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialEmail.takeIf { it.contains('@') }.orEmpty()) }
    var workspace by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialWorkspace.orEmpty()) }
    var organization by remember { mutableStateOf(!initialWorkspace.isNullOrBlank()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf<com.example.chat.data.api.ApiBackendService.EmailCodeAttempt?>(null) }
    var signedInUser by remember { mutableStateOf<String?>(null) }
    val resumeSaver=androidx.compose.runtime.saveable.listSaver<com.example.chat.data.api.EmailCodeResume?,Any>(
        save={if(it==null) emptyList() else listOf(it.challengeId,it.email,it.tenant,it.baseUrl,it.userId.orEmpty(),it.defaultTenant,it.expiresAtEpochMs,it.resendAfterSeconds,it.ownerTenant)},
        restore={if(it.isEmpty()) null else com.example.chat.data.api.EmailCodeResume(it[0] as String,it[1] as String,it[2] as String,it[3] as String,(it[4] as String).ifEmpty {null},it[5] as Boolean,it[6] as Long,it[7] as Int,it[8] as String)})
    var resume by androidx.compose.runtime.saveable.rememberSaveable(stateSaver=resumeSaver) {mutableStateOf<com.example.chat.data.api.EmailCodeResume?>(null)}
    androidx.compose.runtime.LaunchedEffect(active,attempt) {
        if(active) onResumeState(attempt!=null || resume!=null)
        if(active && attempt==null && resume!=null) {
            attempt=restoreAttempt?.invoke(requireNotNull(resume))
            if(attempt==null) {resume=null;error=authCopy("The previous code expired or the account changed. Request a new code.")}
        }
        attempt?.let {if(saveAttempt!=null && resume?.challengeId!=it.challenge.id) resume=saveAttempt(it)}
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (initialWorkspace == null) {
            val (saved, usingDefault) = loadSavedTenant()
            organization = !usingDefault && !saved.isNullOrBlank()
            workspace = if (organization) saved.orEmpty() else ""
        }
    }
    val guardedEmailBack=com.example.chat.ui.core.components.auth.rememberAuthBackAction(active && (email.isNotBlank() || workspace.isNotBlank()),onBack)
    val active = attempt
    if (active != null) {
        androidx.compose.runtime.key(active.challenge.id) {
            OtpVerificationScreen(
                email = active.challenge.email, purpose = OtpPurpose.SignIn,
                onBack = { onResumeState(false);resume=null;attempt = null; error = null },
                verify = { code -> signedInUser = verifyCode(active, code) },
                resend = {
                    attempt = requestCode(active.challenge.email,
                        active.targetTenant.takeUnless { active.isDefaultTenant }, active.isDefaultTenant)
                    authCopy("If an account exists, a new sign-in code has been sent.")
                },
                onVerified = { onResumeState(false);resume=null;signedInUser?.let(onLoggedIn) ?: throw IllegalStateException(authCopy("Server session was not established.")) },
                deliveryMessage = authCopy("If an account exists for this email, a sign-in code has been sent."),
                initialResendSeconds = active.challenge.resendAfterSeconds,
            )
        }
        return
    }
    com.example.chat.ui.core.components.auth.ChatyAuthScreen(theme) {
                AuthBackButton(onClick = guardedEmailBack)
                Spacer(Modifier.height(ChatyUiTokens.space24))
                Text(authCopy("Welcome back"), color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor), fontSize = ChatyUiTokens.text28, fontWeight = FontWeight.ExtraBold)
                Text(authCopy("Sign in with an email code."), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
                Spacer(Modifier.height(ChatyUiTokens.space24))
                AuthCapsuleModeSelector(theme, organization) { organization = it; error = null }
                if (organization) {
                    Spacer(Modifier.height(ChatyUiTokens.space16))
                    AuthTextField(label = authCopy("Workspace ID"), hintText = authCopy("e.g. acme-corp"), capsule = true,
                        theme = theme, value = workspace, onValueChange = { workspace = it })
                }
                Spacer(Modifier.height(ChatyUiTokens.space16))
                AuthTextField(label = authCopy("Email"), hintText = authCopy("you@example.com"), capsule = true,
                    theme = theme, value = email, onValueChange = { email = it }, keyboardType = KeyboardType.Email)
                Spacer(Modifier.height(ChatyUiTokens.space16))
                error?.let { Text(authCopy(it), color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor)); Spacer(Modifier.height(ChatyUiTokens.touchGap)) }
                AuthPrimaryButton(text = authCopy("Send code"), isLoading = loading, capsule = true, theme = theme, onClick = {
                    error = ChatyValidators.validateEmail(email)
                    if (organization && workspace.isBlank()) error = authCopy("Enter a workspace ID.")
                    if (error == null && !loading) {
                        loading = true
                        val selectedEmail = email.trim()
                        val selectedWorkspace = workspace.trim().takeIf { organization }
                        val selectedDefault = !organization
                        scope.launch {
                            try { attempt = authRequest { requestCode(selectedEmail, selectedWorkspace, selectedDefault) } }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (e: Exception) { error = e.message ?: authCopy("Could not request a code.") }
                            finally { loading = false }
                        }
                    }
                })
                com.example.chat.ui.core.components.auth.ChatyAuthLink(authCopy("Use password instead"), theme, { onUsePassword(email, workspace, organization) }, !loading)
                com.example.chat.ui.core.components.auth.ChatyAuthLink(authCopy("Create account"), theme, onRegister, !loading)
                com.example.chat.ui.core.components.auth.ChatyAuthLink(authCopy("Create a workspace"), theme, onRegisterOrganization, !loading)
                if (demoLogin != null) {
                    TextButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), enabled = !loading, onClick = {
                        loading = true
                        scope.launch {
                            try { onLoggedIn(demoLogin()) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (e: Exception) { error = e.message ?: authCopy("Demo unavailable.") }
                            finally { loading = false }
                        }
                    }) { Text(authCopy("Offline demo"), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor)) }
                }
    }
}
