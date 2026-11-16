package com.example.chat.ui.auth

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.chat.ui.core.components.auth.ChatyUiTokens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.DomainAdd
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.chat.core.auth.friendlyOrgRegisterError
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.core.validators.ChatyValidators
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch
import com.example.chat.ui.kit.ChatyAdaptiveLogic

/** Port of lib/features/auth/register_organization_screen.dart. The backend
 *  call is injected; AppRoot wires ApiBackendService.registerOrganization
 *  (REST) plus the stored-user-id persistence. [onRegistered] receives
 *  (userId, organizationId, organizationName) and routes to the subscription
 *  plan screen destination. */
@Composable
fun RegisterOrganizationScreen(
    onBack: () -> Unit,
    onRegistered: (userId: String, organizationId: String, organizationName: String) -> Unit,
    active:Boolean=true,
    registerOrganization: suspend (
        tenantId: String,
        organizationName: String,
        adminUsername: String,
        adminEmail: String,
        adminPassword: String,
        adminFullName: String,
        allowUserRegistration: Boolean,
    ) -> Triple<String, String, String>,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val theme = LocalThemeConfig.current
    val scope = com.example.chat.ui.core.components.auth.rememberAuthRequestScope(active)
    val online = com.example.chat.ui.core.components.auth.rememberAuthOnline()

    var orgName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var tenantId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var adminFullName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var adminUsername by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var adminEmail by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var obscurePassword by remember { mutableStateOf(true) }
    com.example.chat.ui.core.components.auth.RemaskPasswordOnBackground {obscurePassword=true}
    val credentialSupport=rememberAuthCredentials()
    var isLoading by remember { mutableStateOf(false) }
    var retryDeadline by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val retryWait = com.example.chat.ui.core.components.auth.rememberAuthRetryWait(retryDeadline)
    var hasManuallyEditedSlug by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var allowUserRegistration by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Play UGC policy (P-06): account creation is gated on explicit acceptance.
    var acceptedTerms by remember { mutableStateOf(false) }
    var ageConfirmed by remember {mutableStateOf(false)}
    var legalDialogAsset by remember { mutableStateOf<Pair<String, String>?>(null) }

    var orgNameError by remember { mutableStateOf<String?>(null) }
    var tenantIdError by remember { mutableStateOf<String?>(null) }
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    fun onOrgNameChanged(value: String) {
        orgName = value
        orgNameError = null
        errorMessage = null
        if (!hasManuallyEditedSlug) {
            tenantIdError = null
            tenantId = value.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .replace(Regex("^-+|-+$"), "")
        }
    }

    fun validateTenantId(raw: String): String? {
        val clean = raw.trim().lowercase()
        if (clean.isEmpty()) return authCopy("Workspace ID is required")
        if (clean.length < 3) return authCopy("Workspace ID must be at least 3 characters")
        if (!Regex("^[a-z0-9_-]+$").matches(clean)) return authCopy("Use lowercase letters, numbers, hyphens, and underscores only")
        return null
    }

    fun handleRegister() {
        if (isLoading || retryWait > 0) return
        if(AuthTemporaryPolicy.ageRuleConfirmed && !ageConfirmed) {errorMessage=authCopy("Confirm the configured age requirement before continuing.");return}
        orgNameError = when {
            orgName.trim().isEmpty() -> authCopy("Please enter your organization name")
            orgName.trim().length < 2 -> authCopy("Name must be at least 2 characters")
            else -> null
        }
        tenantIdError = validateTenantId(tenantId)
        fullNameError = if (adminFullName.trim().isEmpty()) authCopy("Please enter administrator full name") else null
        usernameError = ChatyValidators.validateUsername(adminUsername.trim(), forRegistration = true)
        emailError = ChatyValidators.validateEmail(adminEmail.trim())
        passwordError = when {
            adminPassword.isEmpty() -> authCopy("Please enter a password")
            // Prior minimum6character check retained inactive; owner approved registration-only tightening.
            // adminPassword.length < 6 -> authCopy("Password must be at least 6 characters")
            else -> registrationPasswordError(adminPassword)?.let { authCopy(it) }
        }
        if (listOf(orgNameError, tenantIdError, fullNameError, usernameError, emailError, passwordError)
                .any { it != null } || isLoading
        ) return

        if (AuthTemporaryPolicy.acceptanceReady() && !acceptedTerms) {
            errorMessage = authCopy("Please accept the Terms of Use and Privacy Policy to create your organization.")
            return
        }

        isLoading = true
        errorMessage = null
        // Freeze the validated request and credential-save pair before launch.
        val submittedTenant=tenantId.trim().lowercase()
        val submittedName=orgName.trim()
        val submittedUsername=adminUsername.trim()
        val submittedEmail=adminEmail.trim()
        val submittedPassword=adminPassword
        val submittedFullName=adminFullName.trim()
        val submittedAllowRegistration=allowUserRegistration
        scope.launch {
            try {
                val (userId, orgId, resolvedName) = authRequest {registerOrganization(
                    // Previous validated arguments: tenantId.trim().lowercase(), orgName.trim(),
                    // adminUsername.trim(), adminEmail.trim(), adminPassword, adminFullName.trim(), allowUserRegistration
                    submittedTenant,
                    submittedName,
                    submittedUsername,
                    submittedEmail,
                    submittedPassword,
                    submittedFullName,
                    submittedAllowRegistration,
                )}
                // credentialSupport.save(adminEmail.trim(),adminPassword)
                credentialSupport.save(submittedEmail,submittedPassword)
                onRegistered(userId, orgId, resolvedName)
            } catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch (e: Exception) {
                // Previous: errorMessage = AuthFormPolicy.privateError(friendlyOrgRegisterError(e),adminEmail)
                errorMessage = AuthFormPolicy.privateError(friendlyOrgRegisterError(e),submittedEmail)
                if (e is com.example.chat.data.api.AuthRateLimitException) retryDeadline = android.os.SystemClock.elapsedRealtime() + e.retryAfterSeconds * 1000L
            } finally {isLoading=false}
        }
    }

    val guardedBack=com.example.chat.ui.core.components.auth.rememberAuthBackAction(active && (orgName.isNotBlank() || adminPassword.isNotBlank() || adminEmail.isNotBlank()),onBack)
    com.example.chat.ui.core.components.auth.ChatyAuthScreen(theme) {
                AuthBackButton(onClick = guardedBack)
                com.example.chat.ui.core.components.auth.AuthOfflineNotice(online, theme)
                Spacer(Modifier.height(ChatyUiTokens.space16))
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                    com.example.chat.ui.core.components.auth.ChatyAppAuthIcon()
                }
                Spacer(Modifier.height(ChatyUiTokens.space16))
                com.example.chat.ui.core.components.auth.ChatyAuthReferenceHeading(
                    authCopy("Register Organization"),authCopy("Organization"),
                    authCopy("Create a dedicated workspace for your team"),theme)
                Spacer(Modifier.height(ChatyUiTokens.space24))

                SectionHeader(theme, authCopy("1. Organization Details")) {
                    Icon(Icons.Rounded.Apartment, contentDescription = null, tint = Color(theme.accentColor), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(ChatyUiTokens.space16))
                AuthTextField(
                    readOnly = false,
                    capsule = true,
                    label = authCopy("Organization Name"),
                    hintText = authCopy("e.g. Acme Corporation"),
                    theme = theme,
                    value = orgName,
                    onValueChange = { onOrgNameChanged(it) },
                    error = orgNameError,
                    prefixIcon = { PrefixIcon(theme) { Icon(Icons.Rounded.Business, null, tint = it, modifier = Modifier.size(20.dp)) } },
                )
                Spacer(Modifier.height(ChatyUiTokens.space16))
                AuthTextField(
                    readOnly = false,
                    capsule = true,
                    label = authCopy("Workspace / Tenant ID"),
                    hintText = authCopy("e.g. acme-corp"),
                    theme = theme,
                    value = tenantId,
                    // Previous: onValueChange = { tenantId = it; hasManuallyEditedSlug = true },
                    onValueChange = { tenantId = it; hasManuallyEditedSlug = true; tenantIdError = null; errorMessage = null },
                    error = tenantIdError,
                    prefixIcon = { PrefixIcon(theme) { Icon(Icons.Rounded.Link, null, tint = it, modifier = Modifier.size(20.dp)) } },
                )
                Text(
                    authCopy("Your team members will use this Workspace ID to sign into your organization."),
                    color = Color(argbWithAlpha(theme.secondaryTextColor, 0.8)),
                    fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text12,
                    modifier = Modifier.padding(top = ChatyUiTokens.space8, start = ChatyUiTokens.space8),
                )
                Spacer(Modifier.height(ChatyUiTokens.space32))

                SectionHeader(theme, authCopy("2. Admin Account")) {
                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = Color(theme.accentColor), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(ChatyUiTokens.space16))
                AuthTextField(
                    readOnly = false,
                    capsule = true,
                    label = authCopy("Admin Full Name"),
                    hintText = authCopy("e.g. Alex Rivera"),
                    theme = theme,
                    value = adminFullName,
                    onValueChange = { adminFullName = it;fullNameError=null;errorMessage=null },
                    error = fullNameError,
                    prefixIcon = { PrefixIcon(theme) { Icon(Icons.Rounded.Badge, null, tint = it, modifier = Modifier.size(20.dp)) } },
                )
                Spacer(Modifier.height(ChatyUiTokens.space16))
                AuthTextField(
                    readOnly = false,
                    capsule = true,
                    label = authCopy("Admin Username"),
                    hintText = authCopy("e.g. alex_admin"),
                    theme = theme,
                    value = adminUsername,
                    onValueChange = { adminUsername = it;usernameError=null;errorMessage=null },
                    error = usernameError,
                    prefixIcon = { PrefixIcon(theme) { Icon(Icons.Rounded.AlternateEmail, null, tint = it, modifier = Modifier.size(20.dp)) } },
                )
                Spacer(Modifier.height(ChatyUiTokens.space16))
                AuthTextField(
                    readOnly = false,
                    capsule = true,
                    label = authCopy("Admin Email"),
                    hintText = authCopy("e.g. alex@acme.com"),
                    theme = theme,
                    value = adminEmail,
                    onValueChange = { adminEmail = it;emailError=null;errorMessage=null },
                    keyboardType = KeyboardType.Email,
                    error = emailError,
                    prefixIcon = { PrefixIcon(theme) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.mail, semanticLabel = null, color = it, modifier = Modifier.size(20.dp)) } },
                )
                Spacer(Modifier.height(ChatyUiTokens.space16))
                AuthTextField(
                    readOnly = false,
                    capsule = true,
                    label = authCopy("Admin Password"),
                    // Prior: hintText = authCopy("Minimum 6 characters"),
                    hintText = authCopy("12 to 128 characters with an uppercase letter, a lowercase letter and a number."),
                    theme = theme,
                    value = adminPassword,
                    onValueChange = { adminPassword = it;passwordError=null;errorMessage=null },
                    obscureText = obscurePassword,
                    error = passwordError,
                    prefixIcon = { PrefixIcon(theme) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.lock, semanticLabel = null, color = it, modifier = Modifier.size(20.dp)) } },
                    suffixIcon = {
                        IconButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = { obscurePassword = !obscurePassword }) {
                            Icon(
                                if (obscurePassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                if (obscurePassword) authCopy("Show password") else authCopy("Hide password"),
                                tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(ChatyUiTokens.space16))

                AllowRegistrationCard(
                    theme = theme,
                    allow = allowUserRegistration,
                    onChange = { allowUserRegistration = it },
                )
                Spacer(Modifier.height(ChatyUiTokens.space24))

                com.example.chat.ui.core.components.auth.AuthValidationSummary(
                    listOf(orgNameError, tenantIdError, fullNameError, usernameError, emailError, passwordError), theme)
                errorMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .padding(bottom = ChatyUiTokens.space16)
                            .fillMaxWidth()
                            .background(Color(argbWithAlpha(theme.dangerColor, 0.12)), ChatyUiTokens.cardShape)
                            .border(1.dp, Color(argbWithAlpha(theme.dangerColor, 0.4)), ChatyUiTokens.cardShape)
                            .padding(ChatyUiTokens.space16),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.errorOutline, semanticLabel = null, color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(ChatyUiTokens.space8))
                        Text(msg, color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor), fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text13, fontWeight = FontWeight.Medium)
                    }
                }

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

                com.example.chat.ui.core.components.auth.AuthRetryNotice(retryWait, theme)
                AuthPrimaryButton(
                    // Previous: enabled = !isLoading,
                    enabled = !isLoading && retryWait == 0,
                    text = authCopy("Create Organization"),
                    capsule = true,
                    theme = theme,
                    onClick = { handleRegister() },
                    isLoading = isLoading,
                )

                legalDialogAsset?.let { (title, asset) ->
                    LegalTextDialog(theme = theme, title = title, assetPath = asset, onDismiss = { legalDialogAsset = null })
                }
                Spacer(Modifier.height(ChatyUiTokens.space16))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = guardedBack) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back,
                            semanticLabel = null,
                            color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(ChatyUiTokens.space8))
                        Text(
                            authCopy("Already have an organization? Log in"),
                            color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
                            fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text13_5,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
    }
}

@Composable
private fun PrefixIcon(theme: ThemeConfig, icon: @Composable (Color) -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    icon(Color(argbWithAlpha(theme.secondaryTextColor, 0.7)))
}

@Composable
private fun SectionHeader(theme: ThemeConfig, title: String, icon: @Composable () -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(ChatyUiTokens.space8))
        Text(
            title,
            color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
            fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text15,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.1.sp,
            modifier = Modifier.semantics { heading() }, // A1-57: section heading
        )
    }
}

@Composable
private fun AllowRegistrationCard(theme: ThemeConfig, allow: Boolean, onChange: (Boolean) -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(argbWithAlpha(theme.surfaceColor, 0.6)), ChatyUiTokens.cardShape)
            .border(1.dp, Color(argbWithAlpha(theme.secondaryTextColor, 0.25)), ChatyUiTokens.cardShape)
            .padding(horizontal = ChatyUiTokens.space16, vertical = ChatyUiTokens.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val activeColor = if (allow) theme.accentColor else theme.secondaryTextColor
        Box(
            modifier = Modifier
                .background(Color(argbWithAlpha(activeColor, 0.12)), ChatyUiTokens.cardShape)
                .padding(ChatyUiTokens.space8),
        ) {
            Icon(Icons.Rounded.GroupAdd, contentDescription = null, tint = Color(activeColor), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(ChatyUiTokens.space16))
        Column(Modifier.weight(1f)) {
            Text(
                authCopy("Allow Member Self-Registration"),
                color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
                fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text13_5,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(ChatyUiTokens.space8))
            Text(
                if (allow) authCopy("Team members can join this workspace with the Workspace ID.")
                else authCopy("Self-registration disabled (only admins can invite members)."),
                color = Color(argbWithAlpha(theme.secondaryTextColor, 0.8)),
                fontSize = com.example.chat.ui.core.components.auth.ChatyUiTokens.text11_5,
            )
        }
        Spacer(Modifier.width(ChatyUiTokens.space8))
        Switch(
            checked = allow,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(theme.accentColor)),
        )
    }
}
