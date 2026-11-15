package com.example.chat.ui.auth

import com.example.chat.ui.core.components.auth.ChatyUiTokens

import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.ui.theme.LocalAppColors

private fun c(argb: Long): Color = Color(argb)

/**48dp clickable accessibility target around a24dp decorative chevron. */
@Composable
fun AuthBackButton(onClick: () -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    com.example.chat.ui.core.components.auth.ChatyBackButton(onClick, com.example.chat.ui.theme.LocalThemeConfig.current)
}

/** Rounded text input field with label and placeholder (AuthTextField). */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AuthTextField(
    label: String,
    hintText: String,
    theme: ThemeConfig,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    obscureText: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    prefixIcon: (@Composable () -> Unit)? = null,
    suffixIcon: (@Composable () -> Unit)? = null,
    capsule: Boolean = false,
    microMotion: Boolean = capsule,
    readOnly: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    autofillTypes: List<String> = emptyList(),
) {
    com.example.chat.ui.core.components.auth.AuthInputPrivacy {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val colors = LocalAppColors.current
    val autofill = androidx.compose.ui.platform.LocalAutofill.current
    val autofillTree = androidx.compose.ui.platform.LocalAutofillTree.current
    val currentChange by androidx.compose.runtime.rememberUpdatedState(onValueChange)
    val node = remember(autofillTypes) { androidx.compose.ui.autofill.AutofillNode(autofillTypes = autofillTypes.mapNotNull { value ->
        androidx.compose.ui.autofill.AutofillType.entries.firstOrNull { it.name == value }
    }, onFill = { currentChange(it) }) }
    androidx.compose.runtime.DisposableEffect(node, autofillTree) {
        if (autofillTypes.isNotEmpty() && !readOnly) autofillTree += node
        onDispose { autofillTree.children.remove(node.id) }
    }
    var capsLockOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val bringIntoView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val focusScope = androidx.compose.runtime.rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val focusTint by androidx.compose.animation.animateColorAsState(
        targetValue = if (focused) c(theme.accentColor) else colors.border,
        animationSpec = androidx.compose.animation.core.tween(if (reduced) 0 else com.example.chat.ui.kit.ChatyMotion.fastMs),
        label = authCopy("auth-focus-outline"),
    )
    val floatingLabel: (@Composable () -> Unit)? = if (capsule) null else ({ Text(label) })
    Column(modifier = modifier.padding(vertical=ChatyUiTokens.touchGap / 2)) {
        Text(
            label,
            color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
            fontSize = ChatyUiTokens.text14,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(ChatyUiTokens.space8))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            modifier = Modifier.fillMaxWidth()
                .onPreviewKeyEvent { capsLockOn = it.nativeKeyEvent.isCapsLockOn; false }
                .onGloballyPositioned { node.boundingBox = it.boundsInWindow() }
                .bringIntoViewRequester(bringIntoView)
                .onFocusChanged { state ->
                    if (state.isFocused) focusScope.launch { bringIntoView.bringIntoView() }
                    else if(!readOnly && keyboardType in listOf(KeyboardType.Email,KeyboardType.Ascii,KeyboardType.Uri) && value != value.trim()) currentChange(value.trim())
                    if (autofillTypes.isNotEmpty() && !readOnly) {
                        if (state.isFocused) autofill?.requestAutofillForNode(node) else autofill?.cancelAutofillForNode(node)
                    }
                }.heightIn(min = if (capsule) AuthCapsuleTokens.controlHeight else ChatyUiTokens.space0).testTag("auth-field-" + label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'))
                .semantics { contentDescription = label },
            label = floatingLabel,
            placeholder = {
                Text(
                    hintText,
                    color = Color(AuthPresentationPolicy.readableText(theme.secondaryTextColor.toInt(), colors.inputFill.toArgb())),
                    fontSize = ChatyUiTokens.text14,
                )
            },
            visualTransformation = if (obscureText) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrect = keyboardType != KeyboardType.Password),
            keyboardActions = KeyboardActions(
                onNext = { if (!focus.moveFocus(FocusDirection.Next)) focus.clearFocus() },
                onDone = { focus.clearFocus(); onDone?.invoke() }),
            isError = error != null,
            supportingText = (error ?: if (capsLockOn && keyboardType == KeyboardType.Password) authCopy("Caps Lock is on") else null)?.let { message -> {
                Row(verticalAlignment = Alignment.Top) {
                    if (capsule) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.errorOutline, size = ChatyUiTokens.space16, color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor))
                        Spacer(Modifier.width(ChatyUiTokens.touchGap))
                    }
                    Text(authCopy(message), color = com.example.chat.ui.auth.authReadable(theme, theme.dangerColor), fontSize = ChatyUiTokens.text12)
                }
            } },
            leadingIcon = prefixIcon,
            trailingIcon = suffixIcon ?: if(value.contains('@') && keyboardType==KeyboardType.Email && error==null && com.example.chat.core.validators.ChatyValidators.validateEmail(value)==null) ({
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.checkCircle,size=ChatyUiTokens.space20,color=com.example.chat.ui.auth.authReadable(theme, theme.accentColor),semanticLabel=authCopy("Valid email format"))
            }) else null,
            singleLine = true,
            interactionSource = interaction,
            shape = if (capsule) AuthCapsuleTokens.shape else ChatyUiTokens.cardShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(AuthPresentationPolicy.readableText(theme.primaryTextColor.toInt(),colors.inputFill.toArgb())),
                unfocusedTextColor = Color(AuthPresentationPolicy.readableText(theme.primaryTextColor.toInt(),colors.inputFill.toArgb())),
                focusedContainerColor = colors.inputFill,
                unfocusedContainerColor = colors.inputFill,
                errorContainerColor = colors.inputFill,
                focusedBorderColor = if (microMotion) focusTint else c(theme.accentColor),
                unfocusedBorderColor = colors.border,
                errorBorderColor = c(theme.dangerColor),
                cursorColor = c(theme.accentColor),
            ),
        )
    }
    }
}

/** Solid primary rounded button (AuthPrimaryButton). */
@Composable
fun AuthPrimaryButton(
    text: String,
    theme: ThemeConfig,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    capsule: Boolean = false,
    enabled: Boolean = true,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed && !reduced) .98f else 1f,
        androidx.compose.animation.core.tween(if(reduced) 0 else 90), label = authCopy("auth-press"))
    Button(
        onClick = onClick,
        interactionSource = interaction,
        enabled = enabled && !isLoading,
        modifier = Modifier.fillMaxWidth().padding(vertical=ChatyUiTokens.touchGap / 2).graphicsLayer { scaleX = scale; scaleY = scale }.heightIn(min = if (capsule) AuthCapsuleTokens.controlHeight else ChatyUiTokens.space52),
        shape = if (capsule) AuthCapsuleTokens.shape else ChatyUiTokens.cardShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = c(theme.accentColor),
            contentColor = c(AuthPresentationPolicy.readableText(theme.onAccentColor.toInt(), theme.accentColor.toInt()).toLong() and 0xffffffffL),
            disabledContainerColor = c(argbWithAlpha(theme.accentColor, 0.6)),
            disabledContentColor = c(theme.onAccentColor),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = ChatyUiTokens.controlElevation),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(ChatyUiTokens.space22),
                strokeWidth = ChatyUiTokens.space2_2,
                color = c(theme.onAccentColor),
            )
        } else {
            Text(authCopy(text), fontSize = ChatyUiTokens.text16, fontWeight = FontWeight.Bold, letterSpacing = ChatyUiTokens.text0_2)
        }
    }
}

/** Outlined button for secondary actions (AuthSecondaryButton). */
@Composable
fun AuthSecondaryButton(text: String, theme: ThemeConfig, onClick: () -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val colors = LocalAppColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical=ChatyUiTokens.touchGap / 2).heightIn(min = ChatyUiTokens.space52),
        shape = ChatyUiTokens.cardShape,
        border = androidx.compose.foundation.BorderStroke(
            ChatyUiTokens.space1_5,
            c(argbWithAlpha(theme.secondaryTextColor, 0.65)),
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c(theme.primaryTextColor)),
    ) {
        Text(authCopy(text), fontSize = ChatyUiTokens.text16, fontWeight = FontWeight.Bold, letterSpacing = ChatyUiTokens.text0_2)
    }
}

/** Divider with centered 'Or' (AuthOrDivider). */
@Composable
fun AuthOrDivider(theme: ThemeConfig) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = ChatyUiTokens.space1, color = colors.divider)
        Text(
            "Or",
            color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
            fontSize = ChatyUiTokens.text13,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = ChatyUiTokens.space16),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = ChatyUiTokens.space1, color = colors.divider)
    }
}

/** Custom vector-style illustration for auth screens (AuthIllustration).
 *  type: 'welcome', 'forgot_password', 'otp'. */
@Composable
fun AuthIllustration(type: String, theme: ThemeConfig, height: Int = 180) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val colors = LocalAppColors.current
    val (mainIcon: ImageVector, badgeText: String) = remember(type,authCopy) { when (type) {
        "forgot_password" -> Icons.Rounded.LockReset to authCopy("Reset Access")
        "otp" -> Icons.Rounded.VerifiedUser to authCopy("Email verification")
        "login" -> Icons.Rounded.Forum to authCopy("Welcome back")
        "register" -> Icons.Rounded.VerifiedUser to authCopy("Your new account")
        "success" -> Icons.Rounded.VerifiedUser to authCopy("You're ready")
        else -> Icons.Rounded.Forum to authCopy("Fast & Private")
    }}
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height.dp)
            .padding(vertical = ChatyUiTokens.space16)
            .border(
                ChatyUiTokens.space1_5,
                c(argbWithAlpha(theme.accentColor, 0.15)),
                ChatyUiTokens.cardShape,
            )
            .background(
                c(argbWithAlpha(theme.cardColor, 0.6)),
                ChatyUiTokens.cardShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(ChatyUiTokens.space76)
                    .background(c(argbWithAlpha(theme.accentColor, 0.15)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(mainIcon, contentDescription = null, tint = c(theme.accentColor), modifier = Modifier.size(ChatyUiTokens.space40))
            }
            Spacer(Modifier.height(ChatyUiTokens.touchGap))
            Box(
                modifier = Modifier
                    .background(c(argbWithAlpha(theme.accentColor, 0.12)), ChatyUiTokens.cardShape)
                    .padding(horizontal = ChatyUiTokens.space16, vertical = ChatyUiTokens.space8),
            ) {
                Text(badgeText, color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor), fontSize = ChatyUiTokens.text12, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WorkspaceModeSelector(
    theme: ThemeConfig,
    useOrganization: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    com.example.chat.ui.core.components.auth.ChatyModeToggle(theme, useOrganization, onSelect)
}

/** Approved capsule login tokens, opt-in until each later screen is approved. */
internal object AuthCapsuleTokens {
    val tapTarget = ChatyUiTokens.space48
    const val checkboxMs = 180
    val controlHeight = ChatyUiTokens.space56
    val shape = CircleShape
}

@Composable
internal fun AuthTextLink(
    text: String,
    theme: ThemeConfig,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    com.example.chat.ui.core.components.auth.ChatyAuthLink(text, theme, onClick, enabled, Modifier.fillMaxWidth())
}

/** No destination, persistence or authentication changes: caller owns mode state. */
@Composable
internal fun AuthCapsuleModeSelector(
    theme: ThemeConfig,
    useOrganization: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    com.example.chat.ui.core.components.auth.ChatyModeToggle(theme, useOrganization, onSelect)
}

/** Resolve through the persisted selected pack, including per-icon variants. */
@Composable
internal fun AuthRoleIcon(role: String, theme: ThemeConfig, label: String? = null, color: Color = c(theme.secondaryTextColor)) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val id = when (role) {
        "workspace" -> com.example.chat.ui.icons.ChatyIconId.workspace
        "eye" -> com.example.chat.ui.icons.ChatyIconId.eye
        "eyeOff" -> com.example.chat.ui.icons.ChatyIconId.eyeOff
        "info" -> com.example.chat.ui.icons.ChatyIconId.info
        "phone" -> com.example.chat.ui.icons.ChatyIconId.phone
        else -> com.example.chat.ui.icons.ChatyIconId.mail
    }
    com.example.chat.ui.icons.ChatyIcon(id, size = ChatyUiTokens.space20, color = color, semanticLabel = label)
}
