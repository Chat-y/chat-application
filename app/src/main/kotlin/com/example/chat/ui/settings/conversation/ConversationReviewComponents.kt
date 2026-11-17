package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.theme.LocalThemeConfig

/** Scope listeners to conversation changes; unrelated preference changes do not redraw previews. */
@Composable
internal fun rememberConversationPreferences(
    controller: ChatyPreferencesController,
    project: (ConversationPreferences) -> ConversationPreferences = { it },
): ConversationPreferences {
    val currentProject by androidx.compose.runtime.rememberUpdatedState(project)
    var state by remember(controller) { mutableStateOf(project(controller.conversation)) }
    DisposableEffect(controller) {
        val listener: () -> Unit = { state = currentProject(controller.conversation) }
        controller.addListener(listener)
        // Close the render-to-listener-registration race without waiting for another update.
        listener()
        onDispose { controller.removeListener(listener) }
    }
    return state
}

/* Previous whole-conversation snapshot retained for reference.
@Composable
internal fun rememberConversationPreferences(controller: ChatyPreferencesController): ConversationPreferences {
    var state by remember(controller) { mutableStateOf(controller.conversation) }
    DisposableEffect(controller) {
        val listener: () -> Unit = { state = controller.conversation }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }
    return state
}

*/

/** All writes from these pages read the latest snapshot under the same controller lock. */
internal fun ChatyPreferencesController.editConversation(change: (ConversationPreferences) -> ConversationPreferences) {
    synchronized(this) { updateConversation(change(conversation)) }
}

/** WCAG ratio with foreground alpha composited over its supplied background. */
internal fun conversationContrast(a: Color, b: Color): Float {
    return com.example.chat.ui.kit.ColorPickerLogic.contrastRatio(
        a.compositeOver(b).toArgb().toLong() and 0xFFFFFFFFL, b.toArgb().toLong() and 0xFFFFFFFFL).toFloat()
}

internal fun conversationOnColor(background: Color, first: Color, second: Color): Color =
    if (conversationContrast(first, background) >= conversationContrast(second, background)) first else second

/** Pure threshold decision shared by the note and source regression tests. */
internal fun isConversationContrastLow(ratio: Float, minimum: Float): Boolean = ratio < minimum

@Composable
internal fun ConversationContrastNote(foreground: Color, background: Color, minimum: Float, label: String) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val canvas = Color(LocalThemeConfig.current.backgroundColor)
    val opaqueBackground = background.compositeOver(canvas)
    if (isConversationContrastLow(conversationContrast(foreground, opaqueBackground), minimum)) {
        Text(strings.getString(com.example.chat.R.string.conv_helper_contrast_warning, label, minimum.toString()),
            color = Color(LocalThemeConfig.current.primaryTextColor), fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** Explicit default/custom state, effective swatch and an independent 48dp reset action. */
@Composable
internal fun ConversationColorRow(
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    colorValue: Long?,
    effectiveColor: Color,
    onColorTap: () -> Unit,
    onReset: () -> Unit,
    enabled: Boolean = true,
    iconContent: (@Composable () -> Unit)? = null,
    subtitleMaxLines: Int = Int.MAX_VALUE,
) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val colourPreference = ConversationColorPreference.fromStored(colorValue)
    val description = when (colourPreference) {
        ConversationColorPreference.ThemeDefault -> strings.getString(com.example.chat.R.string.conv_helper_theme_default)
        is ConversationColorPreference.Custom -> strings.getString(com.example.chat.R.string.conv_helper_custom_colour,
            colourPreference.argb and 0xFFFFFFFFL)
    }
    // Previous implicit null/custom branching retained:
    // val description = colorValue?.let { strings.getString(com.example.chat.R.string.conv_helper_custom_colour, it and 0xFFFFFFFFL) } ?: strings.getString(com.example.chat.R.string.conv_helper_theme_default)
    Column(Modifier.alpha(if (enabled) 1f else 0.38f).semantics { stateDescription = description; if (!enabled) disabled() }) {
        GbSettingRow(title = title, icon = icon, iconContent = iconContent, subtitleMaxLines = subtitleMaxLines, subtitle = listOfNotNull(subtitle, description).joinToString(" · "),
            onTap = { if (enabled) onColorTap() },
            trailing = {
                Row {
                    Box(Modifier.padding(12.dp).size(24.dp).background(effectiveColor, CircleShape))
                    // Previous stock text action:
                    // TextButton(onClick = onReset, enabled = enabled && colorValue != null, modifier = Modifier.heightIn(min = 48.dp)) {
                    //                         Text(strings.getString(com.example.chat.R.string.conv_helper_reset))
                    //                     }
                    com.example.chat.ui.kit.ChatyTextButton(
                        label = strings.getString(com.example.chat.R.string.conv_helper_reset),
                        onClick = onReset,
                        enabled = enabled && colorValue != null,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            })
    }
}

/** User-triggered support copy. No contacts, account identity, wallpaper paths or message data. */
@Composable
internal fun ConversationSupportCopy(values: Map<String, Any?>) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember(values) { mutableStateOf(false) }
    // Previous stock text action:
    // TextButton(onClick = {
    //         val json = org.json.JSONObject()
    //         values.forEach { (key, value) -> json.put(key, value ?: org.json.JSONObject.NULL) }
    //         clipboard.setText(androidx.compose.ui.text.AnnotatedString(json.toString(2)))
    //         copied = true
    //     }) { Text(if (copied) strings.getString(com.example.chat.R.string.conv_helper_appearance_copied) else strings.getString(com.example.chat.R.string.conv_helper_copy_appearance_support)) }
    com.example.chat.ui.kit.ChatyTextButton(
        label = if (copied) strings.getString(com.example.chat.R.string.conv_helper_appearance_copied) else strings.getString(com.example.chat.R.string.conv_helper_copy_appearance_support),
        onClick = {
        val json = org.json.JSONObject()
        values.forEach { (key, value) -> json.put(key, value ?: org.json.JSONObject.NULL) }
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(json.toString(2)))
        copied = true
    },
    )
}

/** Saveable screen-local disclosure, not a new persistent chat behaviour setting. */
@Composable
internal fun ConversationSettingsSection(title: String, content: @Composable () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(true) }
    com.example.chat.ui.kit.GbCardContainer {
        // Previous stock text action:
        // TextButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics {
        //             stateDescription = if (expanded) strings.getString(com.example.chat.R.string.conv_helper_expanded) else strings.getString(com.example.chat.R.string.conv_helper_collapsed)
        //         }) { Text(if (expanded) "▾ $title" else "▸ $title") }
        com.example.chat.ui.kit.ChatyTextButton(
            label = if (expanded) "▾ $title" else "▸ $title",
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics {
            stateDescription = if (expanded) strings.getString(com.example.chat.R.string.conv_helper_expanded) else strings.getString(com.example.chat.R.string.conv_helper_collapsed)
        },
        )
        if (expanded) content()
    }
}
