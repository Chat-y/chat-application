package com.example.chat.ui.auth

import com.example.chat.ui.core.components.auth.ChatyUiTokens

import com.example.chat.ui.core.components.auth.PhoneNumberEntry
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DomainAdd
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.auth.friendlyRegisterError
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.core.validators.ChatyValidators
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.chat.ui.kit.ChatyAdaptiveLogic

/** Result of the workspace availability/self-registration probe
 *  (ApiBackendService.checkOrganization on the Dart side). */
data class OrgCheckResult(val name: String, val allowUserRegistration: Boolean)

/** Port of lib/features/auth/register_screen.dart.
 *
 *  Injected seams (wired by AppRoot):
 *  - [register]: default-tenant sign-up (ApiBackendService.register).
 *  - [checkOrganization]: workspace probe. The ApiBackendService slice has not
 *    landed yet; AppRoot wires a seam that surfaces the workspace-not-found
 *    banner for organization mode until then. Default-mode registration is
 *    fully functional against the real backend. */
// Keep the SMS implementation intact, but registration uses a device-selected unverified number.
private const val SMS_VERIFICATION_VISIBLE = false

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onRegistered: (userId: String, displayName: String) -> Unit,
    onRegisterOrganization: () -> Unit,
    register: suspend (email: String, username: String, password: String, phoneNumber: String, tenant: String?, isDefaultTenant: Boolean) -> Pair<String, String>,
    checkOrganization: suspend (workspace: String) -> OrgCheckResult,
    /** Public registration preflight for the selected workspace. Missing or
     *  unavailable endpoint shows "could not check", never Available. */
    checkUsernameAvailability: (suspend (username: String, tenant: String?, isDefaultTenant: Boolean) -> UsernameAvailabilityLogic.Probe)? = null,
    /** Dart initState: getSavedTenantId + isUsingDefaultTenant. */
    loadSavedTenant: suspend () -> Pair<String?, Boolean>,
    active: Boolean = true,
    registerWithPhone: (suspend (String, String, String, String, String?, Boolean, com.example.chat.data.api.PhoneVerificationProof) -> Pair<String, String>)? = null,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    val theme = LocalThemeConfig.current
    val scope = com.example.chat.ui.core.components.auth.rememberAuthRequestScope(active)
    val feedbackView = androidx.compose.ui.platform.LocalView.current
    val online = com.example.chat.ui.core.components.auth.rememberAuthOnline()

    var email by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var username by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    // Required contact claim is unverified unless a real matching proof is provided.
    var contactNumber by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var normalizedContactNumber by remember { mutableStateOf<String?>(null) }
    var phoneProof by remember { mutableStateOf<com.example.chat.data.api.PhoneVerificationProof?>(null) }
    var password by remember { mutableStateOf("") }
    val credentialSupport=rememberAuthCredentials()
    var confirmPassword by remember { mutableStateOf("") }
    var confirmHidden by remember { mutableStateOf(true) }
    com.example.chat.ui.core.components.auth.RemaskPasswordOnBackground { confirmHidden = true }

    var workspace by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var obscurePassword by remember { mutableStateOf(true) }
    // Auth screenshot blocking is off by default. Preserve the opt-in hook, do not apply globally.
    // com.example.chat.ui.kit.ProtectSensitiveWindow()
    com.example.chat.ui.core.components.auth.RemaskPasswordOnBackground { obscurePassword = true }

    var isLoading by remember { mutableStateOf(false) }
    var retryDeadline by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val retryWait = com.example.chat.ui.core.components.auth.rememberAuthRetryWait(retryDeadline)
    var completedRegistration by remember { mutableStateOf<Pair<String,String>?>(null) }
    val modePrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("chaty_auth_ui", android.content.Context.MODE_PRIVATE)
    var useOrganization by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var emailError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var contactNumberError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var workspaceError by remember { mutableStateOf<String?>(null) }
    // Play UGC policy (P-06): account creation is gated on explicit acceptance.
    var acceptedTerms by remember { mutableStateOf(false) }
    var ageConfirmed by remember {mutableStateOf(false)}
    var legalDialogAsset by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Username availability indicator (approved preview 8).
    var usernameCheckJob by remember { mutableStateOf<Job?>(null) }
    var usernameCheckState by remember { mutableStateOf(UsernameAvailabilityLogic.State.Idle) }
    var usernameFormatRule by remember { mutableStateOf<String?>(null) }
    // Generation counter so a slow probe cannot overwrite a newer edit.
    var usernameCheckGeneration by remember { mutableIntStateOf(0) }

    var orgCheckJob by remember { mutableStateOf<Job?>(null) }
    var isCheckingOrg by remember { mutableStateOf(false) }
    var orgFound by remember { mutableStateOf<Boolean?>(null) }
    var orgName by remember { mutableStateOf<String?>(null) }
    var allowUserRegistration by remember { mutableStateOf(true) }
    var orgCheckError by remember { mutableStateOf<String?>(null) }


    var orgCheckGeneration by remember { mutableIntStateOf(0) }
    fun checkOrg(ws: String) {
        orgCheckJob?.cancel()
        val generation = ++orgCheckGeneration
        val checked = ws.trim()
        isCheckingOrg = true
        orgCheckJob = scope.launch {
            try {
                val data = checkOrganization(checked)
                if (generation != orgCheckGeneration || workspace.trim() != checked || !useOrganization) return@launch
                orgFound = true
                orgName = data.name
                allowUserRegistration = data.allowUserRegistration
                orgCheckError = null
                if (!data.allowUserRegistration) errorMessage = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) {
                if (generation != orgCheckGeneration || workspace.trim() != checked || !useOrganization) return@launch
                orgFound = false
                orgName = null
                allowUserRegistration = false
                orgCheckError = (e.message ?: e.toString()).replaceFirst("Exception: ", "")
            } finally {
                if (generation == orgCheckGeneration) isCheckingOrg = false
            }
        }
    }

    LaunchedEffect(active) {
        if (!active) {
            orgCheckJob?.cancel(); orgCheckGeneration++; isCheckingOrg = false
        } else if (useOrganization && workspace.isNotBlank()) checkOrg(workspace)
    }

    /** Debounced availability probe. Any probe failure or missing endpoint
     *  lands on CouldNotCheck - never Available (approved preview 8). */
    fun launchUsernameProbe(clean: String) {
        usernameCheckJob?.cancel()
        val generation = ++usernameCheckGeneration
        val checkedWorkspace = workspace.trim()
        val checkedOrganization = useOrganization
        usernameCheckState = UsernameAvailabilityLogic.State.Checking
        usernameCheckJob = scope.launch {
            delay(UsernameAvailabilityLogic.DEBOUNCE_MS)
            val probe = checkUsernameAvailability
            val result = if (probe == null) {
                UsernameAvailabilityLogic.State.CouldNotCheck
            } else {
                try {
                    UsernameAvailabilityLogic.stateForProbe(probe(clean, checkedWorkspace.takeIf { checkedOrganization }, !checkedOrganization))
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    UsernameAvailabilityLogic.State.CouldNotCheck
                }
            }
            if (generation == usernameCheckGeneration && workspace.trim() == checkedWorkspace && useOrganization == checkedOrganization) usernameCheckState = result
        }
    }

    LaunchedEffect(workspace, useOrganization, active) {
        usernameCheckJob?.cancel()
        if (!active) { usernameCheckState = UsernameAvailabilityLogic.State.Idle; return@LaunchedEffect }
        usernameCheckGeneration++
        val value = username.trim()
        if (value.isNotEmpty() && UsernameAvailabilityLogic.localFormatError(value) == null) launchUsernameProbe(value)
        else usernameCheckState = UsernameAvailabilityLogic.State.Idle
    }

    fun onUsernameChanged(value: String) {
        username = value
        usernameError = null
        errorMessage=null
        usernameCheckJob?.cancel()
        val clean = value.trim()
        val formatError = UsernameAvailabilityLogic.localFormatError(clean)
        usernameFormatRule = formatError
        when {
            clean.isEmpty() -> {
                ++usernameCheckGeneration
                usernameCheckState = UsernameAvailabilityLogic.State.Idle
            }
            formatError != null -> {
                ++usernameCheckGeneration
                usernameCheckState = UsernameAvailabilityLogic.State.Invalid
            }
            else -> launchUsernameProbe(clean)
        }
    }

    fun onWorkspaceChanged(value: String) {
        workspace = value
        orgCheckJob?.cancel()
        val clean = value.trim()
        if (clean.isEmpty()) {
            isCheckingOrg = false
            orgFound = null
            orgName = null
            allowUserRegistration = true
            orgCheckError = null
            return
        }
        isCheckingOrg = true
        orgCheckError = null
        orgCheckJob = scope.launch {
            delay(400) // Dart: 400ms debounce timer
            checkOrg(clean)
        }
    }

    androidx.compose.runtime.LaunchedEffect(listOf(errorMessage, emailError, usernameError, passwordError, contactNumberError, workspaceError)) {
        if (listOf(errorMessage, emailError, usernameError, passwordError, contactNumberError, workspaceError).any { !it.isNullOrBlank() }) com.example.chat.ui.kit.ChatyHapticsPort.error(feedbackView)
    }

    fun handleRegister() {
        if(AuthTemporaryPolicy.ageRuleConfirmed && !ageConfirmed) {errorMessage=authCopy("Confirm the configured age requirement before continuing.");return}
        if (isLoading || retryWait > 0) return
        emailError = ChatyValidators.validateEmail(email.trim())
        usernameError = ChatyValidators.validateUsername(username.trim(), forRegistration = true)
        val requiredNumber = com.example.chat.data.api.RegistrationPhonePolicy.normalize(contactNumber)
        contactNumberError = if (requiredNumber != null) null else authCopy("Select your contact number with Use device number.")
        passwordError = when {
            password.isEmpty() -> authCopy("Please enter a password")
            password.length < 6 -> authCopy("Password must be at least 6 characters")
            else -> null
        }
        workspaceError =
            if (useOrganization && workspace.trim().isEmpty()) authCopy("Please enter your Workspace ID") else null
        if (emailError != null || usernameError != null || passwordError != null || contactNumberError != null || workspaceError != null) return

        // The indicator is advisory; the server's final Create still enforces
        // uniqueness. Block only states the user must act on first.
        if (usernameCheckState == UsernameAvailabilityLogic.State.Checking) {
            errorMessage = authCopy("Checking username availability, please wait...")
            return
        }
        if (usernameCheckState == UsernameAvailabilityLogic.State.Taken) {
            errorMessage = authCopy("This username is taken. Choose a different username.")
            return
        }

        if (AuthTemporaryPolicy.acceptanceReady() && !acceptedTerms) {
            errorMessage = authCopy("Please accept the Terms of Use and Privacy Policy to create your account.")
            return
        }

        if (useOrganization) {
            if (isCheckingOrg) {
                errorMessage = authCopy("Checking workspace settings, please wait...")
                return
            }
            if (!allowUserRegistration) {
                errorMessage =
                    authFormat("User self-registration is disabled for %1\$s. Contact your administrator.",orgName ?: workspace.trim())
                return
            }
        }

        val selectedProof = phoneProof
        if (selectedProof != null && (!selectedProof.expiresAt.isAfter(java.time.Instant.now()) || selectedProof.normalizedNumber != normalizedContactNumber)) {
            phoneProof = null
            errorMessage = authCopy("Phone verification expired or the number changed. Verify again or continue with an unverified number.")
            return
        }
        isLoading = true
        errorMessage = null
        val submittedPassword=password
        val submittedIdentifier=email.trim()
        scope.launch {
            try {
                val proof = selectedProof
                check(proof == null || proof.expiresAt.isAfter(java.time.Instant.now())) { authCopy("Phone verification expired. Verify it again before creating your account.") }
                val result = authRequest { if (proof != null && registerWithPhone != null) registerWithPhone(
                    email.trim(), username.trim(), password, requireNotNull(requiredNumber), if (useOrganization) workspace.trim() else null, !useOrganization, proof)
                else register(email.trim(), username.trim(), password, requireNotNull(requiredNumber), if (useOrganization) workspace.trim() else null, !useOrganization) }
                val (userId, displayName) = result
                com.example.chat.ui.kit.ChatyHapticsPort.success(feedbackView)
                if(useOrganization) AuthWorkspaceHistory.record(feedbackView.context,workspace)
                credentialSupport.save(submittedIdentifier,submittedPassword)
                completedRegistration = userId to displayName
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (e: Exception) {
                errorMessage = AuthFormPolicy.privateError(friendlyRegisterError(e), email)
                if (e is com.example.chat.data.api.AuthRateLimitException) retryDeadline = android.os.SystemClock.elapsedRealtime() + e.retryAfterSeconds * 1000L
            } finally { isLoading = false }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val (saved, usingDefault) = loadSavedTenant()
        if (!saved.isNullOrEmpty() && !saved.equals(com.example.chat.data.api.BackendRoutingPolicy.DEFAULT_TENANT_ID, ignoreCase = true)) {
            workspace = saved
            useOrganization = true
            checkOrg(saved)
        } else {
            useOrganization = false
        }
        if (modePrefs.contains("organization")) useOrganization = modePrefs.getBoolean("organization", useOrganization)
    }

    completedRegistration?.let { (id, name) ->
        val proceed = { completedRegistration = null; onRegistered(id, name) }
        androidx.compose.material3.AlertDialog(onDismissRequest = proceed,
            title = { Text(authCopy("Account created")) },
            text = { Column {
                Text(if (name.isBlank()) authCopy("Welcome to Chaty.") else com.example.chat.core.i18n.authFormatted("Welcome, %1\$s.",name))
                Text(authCopy("Continue to check your email and finish setting up."))
            } },
            confirmButton = { androidx.compose.material3.TextButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = proceed) { Text(authCopy("Continue")) } })
    }
    val guardedBack = com.example.chat.ui.core.components.auth.rememberAuthBackAction(active && (email.isNotBlank() || username.isNotBlank() || password.isNotEmpty() || contactNumber.isNotBlank() || workspace.isNotBlank()), onBack)
    com.example.chat.ui.core.components.auth.ChatyAuthScreen(theme) {
                    com.example.chat.ui.core.components.auth.ChatyBackButton(guardedBack, theme)
                com.example.chat.ui.core.components.auth.AuthOfflineNotice(online, theme)
                com.example.chat.ui.core.components.auth.AuthLookupProgress(active && useOrganization && isCheckingOrg, theme)
                if(useOrganization) AuthWorkspaceSuggestions(workspace,theme) {workspace=it;workspaceError=null;errorMessage=null;checkOrg(it)}
                    Text(
                        authCopy("Everything You Need!"),
                    modifier = Modifier.semantics { heading() },
                        color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
                        fontSize = ChatyUiTokens.text28,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    )
                    Spacer(Modifier.height(ChatyUiTokens.touchGap))
                    Text(
                        authCopy("Create your Chaty account with a unique public username."),
                        color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                        fontSize = ChatyUiTokens.text14,
                    )
                    Spacer(Modifier.height(ChatyUiTokens.space24))

                    WorkspaceModeSelector(
                        theme = theme,
                        useOrganization = useOrganization,
                        onSelect = { org ->
                            useOrganization = org
                            modePrefs.edit().putBoolean("organization", org).apply()
                            errorMessage = null
                            if (org && workspace.trim().isNotEmpty()) checkOrg(workspace.trim())
                        },
                    )
                    Spacer(Modifier.height(ChatyUiTokens.space16))

                    if (useOrganization) {
                        Text(authCopy("Use the Workspace ID shared by your organization. Your account belongs to the selected workspace."), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
                        AuthTextField(
                            label = authCopy("Organization / Workspace ID"),
                            hintText = authCopy("e.g. acme-corp or school_123"),
                            capsule = true,
                            theme = theme,
                            value = workspace,
                            onValueChange = { onWorkspaceChanged(it) },
                            error = workspaceError,
                            prefixIcon = {
                                Icon(
                                    Icons.Rounded.Apartment,
                                    contentDescription = null,
                                    tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.7)),
                                    modifier = Modifier.size(ChatyUiTokens.space20),
                                )
                            },
                        )
                        OrgStatusBanner(
                            theme = theme,
                            workspace = workspace.trim(),
                            isChecking = isCheckingOrg,
                            orgFound = orgFound,
                            orgName = orgName,
                            allowUserRegistration = allowUserRegistration,
                            orgCheckError = orgCheckError,
                            onDefaultMode = { useOrganization = false; errorMessage = null },
                            onNewOrg = onRegisterOrganization,
                        )
                        Spacer(Modifier.height(ChatyUiTokens.space16))
                    }

                    AuthTextField(
                        autofillTypes = listOf("EmailAddress"),
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
                    com.example.chat.ui.core.components.auth.EmailSuggestion(email, !isLoading, theme) { email = it; emailError = null }
                    PhoneNumberEntry(contactNumber, theme = theme, enabled = !isLoading,
                        onValueChange = { contactNumber = it; normalizedContactNumber = null; phoneProof = null; contactNumberError = null; errorMessage=null },
                        onNormalized = { if (it != normalizedContactNumber) phoneProof = null; normalizedContactNumber = it })
                    if (SMS_VERIFICATION_VISIBLE && registerWithPhone != null) PhoneVerificationSection(normalizedContactNumber,
                        if (useOrganization) workspace.trim() else null, !useOrganization, !isLoading,
                        onProof = { phoneProof = it })
                    contactNumberError?.let { Text(authCopy(it), color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor)) }
                    Spacer(Modifier.height(ChatyUiTokens.space16))
                    AuthTextField(
                        autofillTypes = listOf("NewUsername"),
                        label = authCopy("Username"),
                        hintText = authCopy("Choose a unique username"),
                        capsule = true,
                        theme = theme,
                        value = username,
                        onValueChange = { onUsernameChanged(it) },
                        error = usernameError,
                        prefixIcon = {
                            Icon(
                                Icons.Rounded.AlternateEmail,
                                contentDescription = null,
                                tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.7)),
                                modifier = Modifier.size(ChatyUiTokens.space20),
                            )
                        },
                    )
                    UsernameAvailabilityIndicator(
                        theme = theme,
                        state = usernameCheckState,
                        formatRule = usernameFormatRule,
                        onRetry = {
                            val clean = username.trim()
                            if (clean.isNotEmpty() && UsernameAvailabilityLogic.localFormatError(clean) == null) {
                                launchUsernameProbe(clean)
                            }
                        },
                    )
                    Spacer(Modifier.height(ChatyUiTokens.space16))
                    AuthTextField(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        onDone = { if (!isLoading) handleRegister() },
                        autofillTypes = listOf("NewPassword"),
                        keyboardType = KeyboardType.Password,
                        label = authCopy("Password"),
                        hintText = authCopy("At least 6 characters. No app-set maximum."),
                        capsule = true,
                        theme = theme,
                        value = password,
                        onValueChange = { password = it; passwordError = null; errorMessage = null },
                        obscureText = obscurePassword,
                        error = passwordError,
                        suffixIcon = {
                            IconButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = { obscurePassword = !obscurePassword }) {
                                Icon(
                                    if (obscurePassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (obscurePassword) authCopy("Show password") else authCopy("Hide password"),
                                    tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                                    modifier = Modifier.size(ChatyUiTokens.space20),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(ChatyUiTokens.space16))
                    Spacer(Modifier.height(ChatyUiTokens.space16))

                com.example.chat.ui.core.components.auth.AuthValidationSummary(listOf(emailError, usernameError, passwordError, contactNumberError, workspaceError), theme)
                com.example.chat.ui.core.components.auth.AuthRetryNotice(retryWait, theme)
                com.example.chat.ui.core.components.auth.AuthErrorNotice(errorMessage, theme, email)
                if(errorMessage?.contains("already registered",ignoreCase=true)==true || errorMessage?.contains("already exists",ignoreCase=true)==true)
                    com.example.chat.ui.core.components.auth.ChatyAuthLink(authCopy("Log In"),theme,guardedBack)

                    AuthAgeConsentStep(theme,ageConfirmed,{ageConfirmed=it})
                if (AuthTemporaryPolicy.acceptanceReady()) {
                        TermsAcceptanceRow(
                            theme = theme,
                            checked = acceptedTerms,
                            onCheckedChange = { acceptedTerms = it; if (it) errorMessage = null },
                            onOpenTerms = { legalDialogAsset = authCopy("Terms of Use") to "legal/terms_of_use.md" },
                            onOpenPrivacy = { legalDialogAsset = authCopy("Privacy Policy") to "legal/privacy_policy.md" },
                            modifier = Modifier.padding(bottom = ChatyUiTokens.space16),
                        )
                    }

                    Text(if(useOrganization) authCopy("Username is checked in the selected workspace. Use letters, numbers, dots and underscores.") else authCopy("Choose a unique public username. Use letters, numbers, dots and underscores."),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
                    Text(AuthFormPolicy.strengthLabel(password), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
                    Text(authCopy("Use a unique password. Chaty never asks you to share it."), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
                    AuthTextField(label = authCopy("Confirm password (optional)"), hintText = authCopy("Re-enter to check the match"), theme = theme,
                        value = confirmPassword, onValueChange = { confirmPassword = it }, capsule = true,
                        obscureText = confirmHidden, keyboardType = KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done, onDone = { if (!isLoading) handleRegister() },
                        suffixIcon = { IconButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = { confirmHidden = !confirmHidden }) {
                            AuthRoleIcon(if (confirmHidden) "eyeOff" else "eye", theme, if (confirmHidden) authCopy("Show confirmation password") else authCopy("Hide confirmation password"))
                        } })
                    if (confirmPassword.isNotEmpty()) Text(if (confirmPassword == password) authCopy("Passwords match") else authCopy("Passwords do not match yet"),
                        color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    // Advisory only: matching confirmation never changes existing validators or payloads.
                    val registrationBlocked by remember {androidx.compose.runtime.derivedStateOf {useOrganization && !allowUserRegistration}}
                    AuthPrimaryButton(
                        enabled = !registrationBlocked && retryWait == 0,
                        text = if (registrationBlocked) authCopy("Registration Disabled") else authCopy("Create account"),
                        theme = theme,
                        onClick = { if (!registrationBlocked) handleRegister() },
                        isLoading = isLoading,
                        capsule = true,
                    )
                    Spacer(Modifier.height(ChatyUiTokens.touchGap))

                    RegisterOrgCard(theme = theme, onClick = onRegisterOrganization)

                    Spacer(Modifier.height(ChatyUiTokens.space24))

                    legalDialogAsset?.let { (title, asset) ->
                        LegalTextDialog(theme = theme, title = title, assetPath = asset, onDismiss = { legalDialogAsset = null })
                    }
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text(authCopy("Already have an account? "), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), fontSize = ChatyUiTokens.text13_5)
                        com.example.chat.ui.core.components.auth.ChatyAuthLink(authCopy("Log In"), theme, guardedBack, !isLoading)
                    }
                    Spacer(Modifier.height(ChatyUiTokens.space16))
    }
}

/** Approved preview 8 username-availability indicator. Checking / Available
 *  / Taken / Invalid / CouldNotCheck under the username field. Announced via
 *  a polite live region; Retry keeps a 48dp target. CouldNotCheck never
 *  claims availability and never blocks Create. */
@Composable
private fun UsernameAvailabilityIndicator(
    theme: ThemeConfig,
    state: UsernameAvailabilityLogic.State,
    formatRule: String?,
    onRetry: () -> Unit,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    if (state == UsernameAvailabilityLogic.State.Idle) return

    val title: String
    val body: String?
    val tint: Color
    when (state) {
        UsernameAvailabilityLogic.State.Checking -> {
            title = authCopy("Checking...")
            body = authCopy("Wait for the server result.")
            tint = Color(theme.secondaryTextColor)
        }
        UsernameAvailabilityLogic.State.Available -> {
            title = authCopy("Username available")
            body = null
            tint = Color(theme.successColor)
        }
        UsernameAvailabilityLogic.State.Taken -> {
            title = authCopy("Username taken")
            body = authCopy("Choose a different username.")
            tint = Color(theme.dangerColor)
        }
        UsernameAvailabilityLogic.State.Invalid -> {
            title = authCopy("Invalid username")
            body = formatRule
            tint = Color(theme.dangerColor)
        }
        UsernameAvailabilityLogic.State.CouldNotCheck -> {
            title = authCopy("Could not check")
            body = authCopy("Availability isn't confirmed. You can retry; the final Create still enforces uniqueness.")
            tint = Color(theme.secondaryTextColor)
        }
        UsernameAvailabilityLogic.State.Idle -> return
    }

    Column(
        modifier = Modifier
            .padding(top = ChatyUiTokens.space8)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                UsernameAvailabilityLogic.State.Checking -> CircularProgressIndicator(
                    modifier = Modifier.size(ChatyUiTokens.space16),
                    strokeWidth = ChatyUiTokens.space2,
                    color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor),
                )
                UsernameAvailabilityLogic.State.Available ->
                    com.example.chat.ui.icons.ChatyIcon(
                        com.example.chat.ui.icons.ChatyIconId.checkCircle,
                        semanticLabel = null,
                        color = tint,
                        modifier = Modifier.size(ChatyUiTokens.space16),
                    )
                UsernameAvailabilityLogic.State.Taken, UsernameAvailabilityLogic.State.Invalid ->
                    com.example.chat.ui.icons.ChatyIcon(
                        com.example.chat.ui.icons.ChatyIconId.errorOutline,
                        semanticLabel = null,
                        color = tint,
                        modifier = Modifier.size(ChatyUiTokens.space16),
                    )
                else ->
                    com.example.chat.ui.icons.ChatyIcon(
                        com.example.chat.ui.icons.ChatyIconId.info,
                        semanticLabel = null,
                        color = tint,
                        modifier = Modifier.size(ChatyUiTokens.space16),
                    )
            }
            Spacer(Modifier.width(ChatyUiTokens.space8))
            Text(title, color = tint, fontSize = ChatyUiTokens.text13, fontWeight = FontWeight.SemiBold)
        }
        if (body != null) {
            Spacer(Modifier.height(ChatyUiTokens.space8))
            Text(
                body,
                color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                fontSize = ChatyUiTokens.text12,
                lineHeight = ChatyUiTokens.text16,
                modifier = Modifier.padding(start = ChatyUiTokens.space24),
            )
        }
        if (state == UsernameAvailabilityLogic.State.CouldNotCheck) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.padding(ChatyUiTokens.touchGap / 2).heightIn(min = AuthCapsuleTokens.tapTarget),
            ) {
                Text(
                    authCopy("Retry"),
                    color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor),
                    fontSize = ChatyUiTokens.text13,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun OrgStatusBanner(
    theme: ThemeConfig,
    workspace: String,
    isChecking: Boolean,
    orgFound: Boolean?,
    orgName: String?,
    allowUserRegistration: Boolean,
    orgCheckError: String?,
    onDefaultMode: () -> Unit,
    onNewOrg: () -> Unit,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    if (workspace.isEmpty()) return

    if (isChecking) {
        Row(
            modifier = Modifier
                .padding(top = ChatyUiTokens.space8, bottom = ChatyUiTokens.space8)
                .fillMaxWidth()
                .background(Color(argbWithAlpha(theme.surfaceColor, 0.5)), ChatyUiTokens.cardShape)
                .border(ChatyUiTokens.space1, Color(argbWithAlpha(theme.secondaryTextColor, 0.2)), ChatyUiTokens.cardShape)
                .padding(horizontal = ChatyUiTokens.space16, vertical = ChatyUiTokens.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(ChatyUiTokens.space14), strokeWidth = ChatyUiTokens.space2, color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor))
            Spacer(Modifier.width(ChatyUiTokens.touchGap))
            Text(
                authCopy("Checking workspace registration support..."),
                color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                fontSize = ChatyUiTokens.text12_5,
                fontWeight = FontWeight.Medium,
            )
        }
        return
    }

    if (orgFound == true) {
        if (allowUserRegistration) {
            Row(
                modifier = Modifier
                    .padding(top = ChatyUiTokens.space8, bottom = ChatyUiTokens.space8)
                    .fillMaxWidth()
                    .background(Color(0xFF10B981).copy(alpha = 0.1f), ChatyUiTokens.cardShape)
                    .border(ChatyUiTokens.space1, Color(0xFF10B981).copy(alpha = 0.35f), ChatyUiTokens.cardShape)
                    .padding(horizontal = ChatyUiTokens.space16, vertical = ChatyUiTokens.space8),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(ChatyUiTokens.space16).padding(top = ChatyUiTokens.space8))
                Spacer(Modifier.width(ChatyUiTokens.touchGap))
                Column {
                    Text(
                        if (!orgName.isNullOrEmpty()) authFormat("Organization: %1\$s",orgName.orEmpty()) else authCopy("Valid Organization"),
                        color = Color(0xFF047857),
                        fontSize = ChatyUiTokens.text13,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(ChatyUiTokens.space8))
                    Text(
                        authCopy("User self-registration is enabled for this workspace."),
                        color = Color(0xFF047857).copy(alpha = 0.9f),
                        fontSize = ChatyUiTokens.text12,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(top = ChatyUiTokens.space8, bottom = ChatyUiTokens.space8)
                    .fillMaxWidth()
                    .background(Color(0xFFF59E0B).copy(alpha = 0.12f), ChatyUiTokens.cardShape)
                    .border(ChatyUiTokens.space1, Color(0xFFF59E0B).copy(alpha = 0.4f), ChatyUiTokens.cardShape)
                    .padding(ChatyUiTokens.space16),
            ) {
                Row {
                    Box(
                        modifier = Modifier.background(Color(0xFFF59E0B).copy(alpha = 0.2f), CircleShape).padding(ChatyUiTokens.space8),
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.lock, semanticLabel = null, color = Color(0xFFB45309), modifier = Modifier.size(ChatyUiTokens.space16))
                    }
                    Spacer(Modifier.width(ChatyUiTokens.touchGap))
                    Column {
                        Text(authCopy("Self-Registration Disabled"), color = Color(0xFFB45309), fontSize = ChatyUiTokens.text13_5, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(ChatyUiTokens.space8))
                        Text(
                            authFormat("Organization %1\$s has restricted user self-registration. Only administrators can add or invite members to this workspace.",orgName ?: workspace),
                            color = Color(0xFF92400E),
                            fontSize = ChatyUiTokens.text12,
                            lineHeight = ChatyUiTokens.text16_2,
                        )
                    }
                }
                Spacer(Modifier.height(ChatyUiTokens.touchGap))
                Row {
                    OutlinedButton(
                        onClick = onDefaultMode,
                        modifier = Modifier.padding(ChatyUiTokens.touchGap / 2).weight(1f),
                        shape = ChatyUiTokens.cardShape,
                        border = androidx.compose.foundation.BorderStroke(ChatyUiTokens.space1, Color(0xFFB45309).copy(alpha = 0.4f)),
                    ) {
                        Text(authCopy("Default Mode"), fontSize = ChatyUiTokens.text11_5, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                    }
                    Spacer(Modifier.width(ChatyUiTokens.space8))
                    Button(
                        onClick = onNewOrg,
                        modifier = Modifier.padding(ChatyUiTokens.touchGap / 2).weight(1f),
                        shape = ChatyUiTokens.cardShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309), contentColor = Color.White),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = ChatyUiTokens.space0),
                    ) {
                        Text(authCopy("New Org"), fontSize = ChatyUiTokens.text11_5, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    if (orgFound == false && orgCheckError != null) {
        Row(
            modifier = Modifier
                .padding(top = ChatyUiTokens.space8, bottom = ChatyUiTokens.space8)
                .fillMaxWidth()
                .background(Color(argbWithAlpha(theme.dangerColor, 0.08)), ChatyUiTokens.cardShape)
                .border(ChatyUiTokens.space1, Color(argbWithAlpha(theme.dangerColor, 0.3)), ChatyUiTokens.cardShape)
                .padding(horizontal = ChatyUiTokens.space16, vertical = ChatyUiTokens.space8),
        ) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.errorOutline, semanticLabel = null, color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor), modifier = Modifier.size(ChatyUiTokens.space16))
            Spacer(Modifier.width(ChatyUiTokens.space8))
            Text(orgCheckError, color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor), fontSize = ChatyUiTokens.text12, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RegisterOrgCard(theme: ThemeConfig, onClick: () -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val authFormat=com.example.chat.core.i18n.rememberAuthFormat()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(argbWithAlpha(theme.accentColor, 0.06)), ChatyUiTokens.cardShape)
            .border(ChatyUiTokens.space1, Color(argbWithAlpha(theme.accentColor, 0.2)), ChatyUiTokens.cardShape)
            .padding(vertical=ChatyUiTokens.touchGap / 2).clickable(onClick = onClick)
            .padding(horizontal = ChatyUiTokens.space16, vertical = ChatyUiTokens.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.background(Color(argbWithAlpha(theme.accentColor, 0.15)), ChatyUiTokens.cardShape).padding(ChatyUiTokens.space8)) {
            Icon(
                Icons.Rounded.DomainAdd,
                contentDescription = null,
                tint = Color(theme.accentColor),
                modifier = Modifier.size(ChatyUiTokens.space20),
            )
        }
        Spacer(Modifier.width(ChatyUiTokens.touchGap))
        Column(Modifier.weight(1f)) {
            // Register screen's card title differs from the login screen's.
            Text(authCopy("Register a New Organization"), color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor), fontSize = ChatyUiTokens.text13, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(ChatyUiTokens.space8))
            Text(authCopy("Set up a custom workspace & team space"), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor), fontSize = ChatyUiTokens.text11_5)
        }
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
            semanticLabel = null,
            color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor),
            modifier = Modifier.size(ChatyUiTokens.space14),
        )
    }
}
