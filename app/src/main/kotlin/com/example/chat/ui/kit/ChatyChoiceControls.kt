package com.example.chat.ui.kit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalAppColors

/** Layout policy shared with compact headers; these are minimums, not fixed text heights. */
enum class ChatyFieldDensity { Standard, CompactHeader }

/** Shared field metrics. Existing task-field defaults remain unchanged. */
object ChatyFieldMetrics {
    val borderWidth = 1.dp
    val inset = 14.dp
    val gap = 6.dp
    val iconGap = 12.dp
    val lineHeight = 54.dp
    val areaHeight = 104.dp
    val textSize = 15.sp
    val textLeading = 22.sp
    val supportingSize = 12.sp
    val compactHeight = 48.dp
    fun verticalInset(density: ChatyFieldDensity) = if (density == ChatyFieldDensity.CompactHeader) 0.dp else inset
    fun minimumHeight(singleLine: Boolean, density: ChatyFieldDensity = ChatyFieldDensity.Standard) =
        if (!singleLine) areaHeight else if (density == ChatyFieldDensity.CompactHeader) compactHeight else lineHeight
}

/**
 * Shared editable field, search input and text-area chrome. Callers retain all validation,
 * IME, privacy, truncation, debounce, persistence and focus policy. No validation is inferred.
 * [errorMessage] is a caller-approved visible error; [statusMessage] can describe real async work.
 * AuthTextField remains the specialized auth editor and is not replaced by this control.
 */
@Composable
fun ChatyTaskField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    label: String? = null,
    accessibilityLabel: String? = null,
    supportingText: String? = null,
    errorMessage: String? = null,
    statusMessage: String? = null,
    readOnly: Boolean = false,
    minLines: Int = if (singleLine) 1 else 3,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    density: ChatyFieldDensity = ChatyFieldDensity.Standard,
    emphasizeFocus: Boolean = false,
    styleDisabled: Boolean = false,
    textArea: Boolean = !singleLine,
    // Compatibility with the held Tasks search API. Non-null imeAction overrides keyboardOptions.imeAction.
    imeAction: ImeAction? = null,
) {
    val c = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // Semantics and the caller modifier stay on the actual editor, not an outer layout.
    BasicTextField(
        value = value, onValueChange = onValueChange, enabled = enabled, readOnly = readOnly,
        singleLine = singleLine, minLines = minLines, maxLines = maxLines,
        textStyle = TextStyle(color = if (enabled || !styleDisabled) c.foreground else c.disabledForeground,
            fontSize = ChatyFieldMetrics.textSize, lineHeight = ChatyFieldMetrics.textLeading),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = imeAction?.let { keyboardOptions.copy(imeAction = it) } ?: keyboardOptions, keyboardActions = keyboardActions,
        visualTransformation = visualTransformation, interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = accessibilityLabel ?: label ?: placeholder
            errorMessage?.let { error(it) }
            statusMessage?.let { stateDescription = it }
        },
        decorationBox = { input ->
            Column {
                Row(
                    Modifier.clip(ChatyModalShape).background(if (enabled || !styleDisabled) c.inputFill else c.disabled)
                        .border(ChatyFieldMetrics.borderWidth, if (errorMessage != null) c.error else if (emphasizeFocus && focused && enabled) c.accent else c.inputBorder, ChatyModalShape)
                        .heightIn(min = ChatyFieldMetrics.minimumHeight(!textArea, density)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Trailing touch controls stay outside the padded editor lane (Tasks v2).
                    Row(Modifier.weight(1f).padding(horizontal = ChatyFieldMetrics.inset,
                        vertical = ChatyFieldMetrics.verticalInset(density)), verticalAlignment = Alignment.CenterVertically) {
                    leadingIcon?.let {
                        CompositionLocalProvider(LocalContentColor provides c.foregroundSecondary) { it() }
                        Spacer(Modifier.width(ChatyFieldMetrics.iconGap))
                    }
                    Column(Modifier.weight(1f)) {
                        label?.let { Text(it, color = c.foregroundSecondary, fontSize = ChatyFieldMetrics.supportingSize) }
                        androidx.compose.foundation.layout.Box {
                            if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder,
                                color = c.foregroundSecondary, fontSize = ChatyFieldMetrics.textSize)
                            input()
                        }
                    }
                    }
                    trailingIcon?.let {
                        CompositionLocalProvider(LocalContentColor provides c.foregroundSecondary) { it() }
                    }
                }
                // Error/support/status remain visible when disabled/read-only and never clear input.
                val message = errorMessage ?: statusMessage ?: supportingText
                if (message != null) {
                    Spacer(Modifier.height(ChatyFieldMetrics.gap))
                    Text(message, color = if (errorMessage != null) c.error else c.foregroundSecondary,
                        fontSize = ChatyFieldMetrics.supportingSize)
                }
            }
        },
    )
}

// Previous field implementation retained inactive for review.
// /** Chaty field chrome, with the editable text semantics and IME supplied by BasicTextField. */
// @Composable
// fun ChatyTaskField(
//     value: String,
//     onValueChange: (String) -> Unit,
//     placeholder: String,
//     enabled: Boolean,
//     modifier: Modifier = Modifier,
//     singleLine: Boolean = true,
//     leadingIcon: (@Composable () -> Unit)? = null,
// ) {
//     val c = LocalAppColors.current
//     BasicTextField(
//         value = value, onValueChange = onValueChange, enabled = enabled,
//         singleLine = singleLine, minLines = if (singleLine) 1 else 3,
//         textStyle = TextStyle(color = c.foreground, fontSize = 15.sp, lineHeight = 22.sp),
//         cursorBrush = SolidColor(c.accent),
//         keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
//         modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
//         decorationBox = { input ->
//             Row(
//                 Modifier.clip(ChatyModalShape).background(c.inputFill)
//                     .border(1.dp, c.inputBorder, ChatyModalShape)
//                     .heightIn(min = if (singleLine) 54.dp else 104.dp).padding(14.dp),
//                 verticalAlignment = Alignment.CenterVertically,
//             ) {
//                 leadingIcon?.let {
//                     CompositionLocalProvider(LocalContentColor provides c.foregroundSecondary) { it() }
//                     Spacer(Modifier.width(12.dp))
//                 }
//                 androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
//                     if (value.isEmpty()) Text(placeholder, color = c.foregroundSecondary, fontSize = 15.sp)
//                     input()
//                 }
//             }
//         },
//     )
// }
//

/** App-token choice/action tile. Selection is exposed to accessibility, not drawn as a stock chip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatyChoiceTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    prominent: Boolean = false,
    role: Role = Role.Button,
    compact: Boolean = false,
    leadingIcon: ChatyIconId? = null,
    supportingText: String? = null,
    errorMessage: String? = null,
    statusMessage: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val c = LocalAppColors.current
    val filled = prominent || selected
    Row(
        modifier
            .then(if (errorMessage != null || statusMessage != null) Modifier.semantics {
                errorMessage?.let { error(it) }
                statusMessage?.let { stateDescription = it }
            } else Modifier)
            .clip(ChatyActionShape)
            .background(if (prominent && !enabled) c.disabled else if (prominent) c.accent else if (selected) c.accent.copy(alpha = 0.12f) else c.surface)
            .border(1.dp, if (prominent && !enabled) c.border else if (filled) c.accent.copy(alpha = 0.6f) else c.border, ChatyActionShape)
//             .then(if (role == Role.Checkbox) Modifier.toggleable(value = selected, enabled = enabled, role = role, onValueChange = { onClick() })
//                 else Modifier.selectable(selected = selected, enabled = enabled, role = role, onClick = onClick))
            .then(if (onLongClick != null) Modifier.semantics { this.selected = selected }
                .combinedClickable(enabled = enabled, role = role, onClick = onClick,
                    onLongClickLabel = onLongClickLabel, onLongClick = onLongClick)
                else if (role == Role.Checkbox) Modifier.toggleable(value = selected, enabled = enabled, role = role, onValueChange = { onClick() })
                else Modifier.selectable(selected = selected, enabled = enabled, role = role, onClick = onClick))
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp).padding(horizontal = if (compact) 2.dp else 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val promFill = if (enabled) c.accent else c.disabled
        val foreground = if (!enabled) (if (prominent) chatyReadableOn(promFill, c.foregroundSecondary, c.foreground) else c.disabledForeground) else if (prominent) chatyReadableOn(promFill, c.onAccent, c.foreground) else if (selected) c.accent else c.foreground
        // Semantic IDs resolve through the active app icon pack, including future defaults.
        val actionIcon = leadingIcon ?: when (label) {
            "Apply" -> ChatyIconId.check
            "Cancel" -> ChatyIconId.close
            else -> null
        }
        if (actionIcon != null) {
            ChatyIcon(actionIcon, size = 18.dp, color = foreground)
            Spacer(Modifier.width(if (compact) 4.dp else 8.dp))
        }
//         Text(label, modifier = Modifier.weight(1f, fill = false), color = foreground,
//             fontSize = 13.sp, fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium)
        // Original label path remains unchanged when no supporting state is supplied.
        val message = errorMessage ?: statusMessage ?: supportingText
        if (message == null) {
            Text(label, modifier = Modifier.weight(1f, fill = false), color = foreground,
                fontSize = 13.sp, fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium)
        } else Column(Modifier.weight(1f, fill = false)) {
            Text(label, color = foreground, fontSize = 13.sp,
                fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium)
            Text(message, color = if (prominent) foreground else if (errorMessage != null) c.error else c.foregroundSecondary, fontSize = 12.sp)
        }
    }
}

private fun chatyContrast(a: Color, b: Color): Float {
    val x = a.luminance(); val y = b.luminance()
    return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
}

/** Label colour for text on [bg]: the preferred theme token when it reaches 4.5:1, otherwise the first fallback
 *  token that does, otherwise black or white (whichever contrasts more). Prominent buttons use it so "Apply" stays
 *  readable on any accent (white on the dark theme's turquoise was 2.2:1). */
internal fun chatyReadableOn(bg: Color, preferred: Color, fallback: Color): Color {
    for (c in listOf(preferred, fallback)) if (chatyContrast(c, bg) >= 4.5f) return c
    return if (chatyContrast(Color.Black, bg) >= chatyContrast(Color.White, bg)) Color.Black else Color.White
}
