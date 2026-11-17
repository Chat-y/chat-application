package com.example.chat.ui.settings.notifications

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
// ChevronRight has no AutoMirrored variant in the pinned icon library.
// Previous import: androidx.compose.material.icons.automirrored.rounded.ChevronRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.Warning
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.material3.Surface
import com.example.chat.ui.kit.ChatySingleChoiceModal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Slider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.GbSliderTile
import com.example.chat.ui.kit.GbSwitchTile
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.theme.LocalThemeConfig

private val LocalToastControlsEnabled = compositionLocalOf { true }

private val LocalToastPickerReturnFocus = compositionLocalOf<(FocusRequester) -> Unit> { {} }

private class ToastPositionRequest(val current: String, val apply: (String) -> Unit)
private class ToastPositionPreview(val position: String)

/** Neutral placeholder person used by all toast previews (never the device owner's real name). */
private const val SAMPLE_NAME = "Aarav"
private const val SAMPLE_ONLINE = "Aarav Online"
private const val SAMPLE_STORY = "Aarav Status"
private const val SAMPLE_PROFILE = "Aarav Profile"
private const val SAMPLE_TYPING = "Aarav Typing..."

// Hoisted option lists: created once, not per recomposition (NTF-R1-029).
// Prior inline positions retained: Top of the screen, Center of screen, Bottom of screen
private val TOAST_POSITIONS = com.example.chat.domain.models.NotificationToastContract.positions
// Previous implementation retained: private val TOAST_RINGTONES = listOf("Default Chaty Tone", "Soft Ping", "Pop Bubble", "Chime Bell", "Silent")
// Prior inline tones retained: Default, Soft Ping, Pop Bubble, Chime Bell, Silent
private val TOAST_RINGTONES = com.example.chat.domain.models.NotificationToastContract.ringtoneLabels

private const val TOAST_RADIUS_MIN = 0.0
private const val TOAST_RADIUS_MAX = 30.0

/** NTF-R1-008/NTF-R1-046: when no custom text color is set, derive a readable one from the background. */
@Composable
private fun readableTextFor(bg: Color): Color {
// Previous implementation retained:     val l = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
// Previous implementation retained:     return if (l > 0.45f) Color(0xFF11161D) else Color.White
    val scheme = MaterialTheme.colorScheme
    fun packed(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
    // Pick the more legible theme foreground, rather than adding a fixed black/white color.
    return listOf(scheme.onSurface, scheme.inverseOnSurface).maxBy { candidate ->
        com.example.chat.ui.kit.ColorPickerLogic.contrastRatio(packed(bg), packed(candidate))
    }
}

private fun formatColorHex(value: Long): String = "#" + value.toString(16).uppercase().padStart(8, '0')

/**
// Previous implementation retained:  * Dart _buildToastPreview. Previews are deliberately always dark-styled (NTF-R3-035 documents this as
// Previous implementation retained:  * a pinned product decision): the fixed default background shows what the floating toast chrome looks
// Previous implementation retained:  * like regardless of app theme; custom colors override it. UNCOMPILED (Compose).
// Previous implementation retained:  * Render the toast sample with a dark default matching the existing floating-toast chrome.
// Previous implementation retained:  * This is an implementation default, not a claim of user preference; custom colors override it.
// Previous implementation retained:  * Frame/typography follow the app theme. UNCOMPILED (Compose).
 * Render the sample with theme-token default colors; explicit custom colors override them.
 * This preview choice changes no stored preference or notification-renderer behavior.
 * UNCOMPILED (Compose).
 */
@Composable
private fun ToastPreview(text: String, hasAvatar: Boolean, hasElevation: Boolean, radius: Double, bgColor: Long?, textColor: Long?, defaultBg: Color) {
// Previous implementation retained:     val bg = bgColor?.let { Color(it) } ?: defaultBg
    val shadowColor = com.example.chat.ui.theme.LocalAppColors.current.shadow
    val targetBg = bgColor?.let { Color(it) } ?: defaultBg
    val bg by androidx.compose.animation.animateColorAsState(targetBg, animationSpec = androidx.compose.animation.core.tween(200), label = "toast background")
    // Previous implementation retained:     // NTF-R1-008: unset text color follows the background so previews never show unreadable combos.
    // NTF-R1-008: when unset, prefer the theme foreground with the higher contrast against this background.
// Previous implementation retained:     val fg = textColor?.let { Color(it) } ?: readableTextFor(bg)
    // Previous implementation retained: val targetFg = textColor?.let { Color(it) } ?: readableTextFor(targetBg)
    // Previous implementation retained: val fg by androidx.compose.animation.animateColorAsState(targetFg, animationSpec = androidx.compose.animation.core.tween(200), label = "toast text")
    // Resolve against each animated background frame; independent color interpolation can lose contrast.
    val scheme = MaterialTheme.colorScheme
    fun packed(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
    val fg = Color(com.example.chat.ui.kit.ToastContrastPolicy.readable(packed(bg), textColor,
        listOf(packed(scheme.onSurface), packed(scheme.inverseOnSurface))))
    val animatedRadius by androidx.compose.animation.core.animateFloatAsState(
        radius.coerceIn(TOAST_RADIUS_MIN, TOAST_RADIUS_MAX).toFloat(),
        animationSpec = androidx.compose.animation.core.tween(200), label = "toast radius")
    var changed by remember { mutableStateOf(false) }
    var initialPreview by remember { mutableStateOf(true) }
    LaunchedEffect(text, hasAvatar, hasElevation, radius, bgColor, textColor) {
        if (initialPreview) initialPreview = false else {
            changed = true
            kotlinx.coroutines.delay(200)
            changed = false
        }
    }
    // NTF-R1-005/NTF-R4-005: clamp at read so an out-of-range stored value never disagrees with the slider.
// Previous implementation retained:     val shape = RoundedCornerShape(radius.coerceIn(TOAST_RADIUS_MIN, TOAST_RADIUS_MAX).toFloat().dp)
    val shape = RoundedCornerShape(animatedRadius.dp)
    Row(
// Previous implementation retained:         Modifier.let { if (hasElevation) it.shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f)) else it }
        Modifier.let { if (hasElevation) it.shadow(8.dp, shape, ambientColor = shadowColor, spotColor = shadowColor) else it }
// Previous implementation retained: // Previous implementation retained:             .background(bg, shape).padding(horizontal = 14.dp, vertical = 8.dp),
// Previous implementation retained:             .background(bg, shape).padding(horizontal = 12.dp, vertical = 8.dp),
// Previous implementation retained:             .widthIn(max = 320.dp).background(bg, shape).padding(horizontal = 14.dp, vertical = 8.dp),
            .widthIn(max = 320.dp).background(bg, shape)
            .then(if (changed) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasAvatar) {
// Previous implementation retained:             Box(Modifier.size(24.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
// Previous implementation retained:                 com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.profile, semanticLabel = null, color = Color.White, modifier = Modifier.size(14.dp))
            Box(Modifier.size(24.dp).background(fg.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.profile, semanticLabel = null, color = fg, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
// Previous implementation retained:         Text(text, color = fg, fontWeight = FontWeight.W600, fontSize = 13.sp, maxLines = 1)
        Text(text, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.W600, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Named palettes use current theme tokens only; low-contrast custom theme pairs stay disabled. */
@Composable
private fun ToastPaletteButtons(onSelected: (Long, Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val darkTheme = LocalThemeConfig.current.isDark
    val base = scheme.surface to scheme.onSurface
    val inverse = scheme.inverseSurface to scheme.inverseOnSurface
    fun argb(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
    fun ratio(pair: Pair<Color, Color>): Double = com.example.chat.ui.kit.ColorPickerLogic.contrastRatio(argb(pair.first), argb(pair.second))
    // Previous implementation retained: palette pairs were allocated on every composition.
    val palettes = remember(scheme, darkTheme) {
        listOf(
            "High contrast" to if (ratio(base) >= ratio(inverse)) base else inverse,
            "Soft dark" to if (darkTheme) base else inverse,
            "Soft light" to if (darkTheme) inverse else base,
        )
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text("Color presets", style = MaterialTheme.typography.labelLarge)
        palettes.forEach { (name, pair) ->
            TextButton(
                onClick = { onSelected(argb(pair.first), argb(pair.second)) },
// Previous implementation retained:                 enabled = ratio(pair) >= 4.5,
                enabled = LocalToastControlsEnabled.current && ratio(pair) >= 4.5,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(name) }
        }
    }
}

/** Local row anatomy: one full-width target, decorative trailing swatch/chevron, wrapping text. */
@Composable
private fun ToastSettingRow(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    colorValue: Long? = null,
    hasSubScreen: Boolean = false,
    onTap: (() -> Unit)? = null,
    onColorTap: (() -> Unit)? = null,
) {
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    val focusRequester = remember { FocusRequester() }
    val rememberReturnFocus = LocalToastPickerReturnFocus.current
    val controlsEnabled = LocalToastControlsEnabled.current
    Row(
// Previous implementation retained:         Modifier.fillMaxWidth().heightIn(min = 56.dp)
// Previous implementation retained: // Previous implementation retained:             .clickable(role = Role.Button) { (onColorTap ?: onTap)?.invoke() }
// Previous implementation retained:             .clickable(enabled = controlsEnabled, role = Role.Button) { (onColorTap ?: onTap)?.invoke() }
        Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRequester(focusRequester)
// Previous implementation retained (ignored the controls-enabled flag):             .clickable(role = Role.Button) {
// Previous implementation retained:                 rememberReturnFocus(focusRequester)
// Previous implementation retained:                 (onColorTap ?: onTap)?.invoke()
// Previous implementation retained:             }
            .clickable(enabled = controlsEnabled, role = Role.Button) {
                rememberReturnFocus(focusRequester)
                (onColorTap ?: onTap)?.invoke()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, color = colors.foregroundSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        if (colorValue != null) {
            // The full row is the touch target. Swatch adds no duplicate small click target.
            Box(Modifier.size(28.dp).background(Color(colorValue), CircleShape).border(1.dp, colors.borderSubtle, CircleShape))
        } else if (hasSubScreen) {
            // Previous unavailable icon: Icons.AutoMirrored.Rounded.ChevronRight

//Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.foregroundSecondary.copy(alpha = 0.54f), modifier = Modifier.size(24.dp))
            com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.foregroundSecondary.copy(alpha = 0.54f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ToastSwitchTile(icon: ImageVector, title: String, subtitle: String? = null, value: Boolean, onChanged: (Boolean) -> Unit) {
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    val controlsEnabled = LocalToastControlsEnabled.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
// Previous implementation retained:             .toggleable(value = value, role = Role.Switch, onValueChange = onChanged)
            .toggleable(value = value, enabled = controlsEnabled, role = Role.Switch, onValueChange = onChanged)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, color = colors.foregroundSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
// Previous implementation retained:         Switch(checked = value, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
        Switch(checked = value, onCheckedChange = null, enabled = controlsEnabled, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** NTF-R1-033: tonal preview frame avoids an extra shadow layer per visible card. */
@Composable
// Previous implementation retained: private fun ToastPreviewFrame(content: @Composable () -> Unit) {
internal fun ToastPreviewFrame(content: @Composable () -> Unit) {
    val config = LocalThemeConfig.current
// Previous implementation retained:     Surface(
// Previous implementation retained:         modifier = Modifier.fillMaxWidth().padding(12.dp),
// Previous implementation retained: // Previous implementation retained:         color = Color(config.surfaceColor),
// Previous implementation retained:         color = MaterialTheme.colorScheme.surfaceVariant,
// Previous implementation retained:         tonalElevation = 2.dp,
// Previous implementation retained:         shape = RoundedCornerShape(16.dp),
// Previous implementation retained:     ) {
// Previous implementation retained:         Column(Modifier.padding(12.dp)) {
// Previous implementation retained:             Text("Sample preview", color = Color(config.secondaryTextColor), style = MaterialTheme.typography.labelMedium)
// Previous implementation retained:             Spacer(Modifier.height(8.dp))
// Previous implementation retained: // Previous implementation retained:             Box(Modifier.fillMaxWidth().heightIn(min = 70.dp), contentAlignment = Alignment.Center) { content() }
// Previous implementation retained:             Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) { content() }
    var previewDark by rememberSaveable { mutableStateOf(config.isDark) }
    var previewLargeText by rememberSaveable { mutableStateOf(false) }
    val originalDensity = androidx.compose.ui.platform.LocalDensity.current
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Sample preview", style = MaterialTheme.typography.labelMedium)
        // Preview-only switches never write preferences or change the app/device theme or font size.
        ToastSwitchTile(Icons.Rounded.FormatPaint, "Dark preview", value = previewDark,
            onChanged = { previewDark = it })
        ToastSwitchTile(Icons.Rounded.FormatColorText, "Large text preview (200%)", value = previewLargeText,
            onChanged = { previewLargeText = it })
        // Previous implementation retained: com.example.chat.ui.theme.ChatyTheme(com.example.chat.core.theme.ThemePresets.getSystemDefaultTheme(previewDark))
        com.example.chat.ui.theme.ChatyTheme(com.example.chat.core.theme.ThemePresets.getSystemDefaultTheme(!previewDark)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
            ) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(originalDensity.density,
                        toastPreviewFontScale(originalDensity.fontScale, previewLargeText))) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(12.dp),
                        contentAlignment = Alignment.Center) { content() }
                }
            }
        }
    }
}

/** NTF-R5-047: keep secondary appearance controls out of the first decision path. */
@Composable
private fun ToastAdvancedSettings(content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp)) {
// Previous implementation retained:         TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
        TextButton(onClick = { expanded = !expanded }, enabled = LocalToastControlsEnabled.current, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        }) {
// Previous implementation retained:             Text("Advanced appearance", modifier = Modifier.weight(1f))
            Text("Advanced appearance (this toast type)", modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
        }
        if (expanded) content()
    }
}

/** NTF-R1-051/NTF-R2-044/NTF-R3-021: distinct type icon and collapsible secondary cards. */
@Composable
private fun ToastTypeCard(key: String, title: String, icon: ImageVector, initiallyExpanded: Boolean, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(key) { mutableStateOf(initiallyExpanded) }
    val config = LocalThemeConfig.current
// Previous implementation retained:     GbCardContainer {
    GbCardContainer(padding = PaddingValues(vertical = 4.dp), margin = PaddingValues(vertical = 8.dp)) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                heading()
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        ) {
            Icon(icon, null, tint = Color(config.accentColor), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color(config.primaryTextColor))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Color(config.secondaryTextColor))
        }
        if (expanded) content()
    }
}

/** Explicit keyboard path and visible transient value while adjusting radius. */
@Composable
private fun ToastRadiusSlider(title: String, subtitle: String?, value: Float, min: Float, max: Float, divisions: Int, unit: String, onChanged: (Float) -> Unit) {
    val controlsEnabled = LocalToastControlsEnabled.current
    var adjusting by remember { mutableStateOf(false) }
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    LaunchedEffect(value, adjusting) {
        if (adjusting) { kotlinx.coroutines.delay(450); adjusting = false }
    }
// Previous implementation retained:     fun setValue(next: Float) { adjusting = true; onChanged(next.coerceIn(min, max)) }
// Previous implementation retained:     fun setValue(next: Float) { if (controlsEnabled) { adjusting = true; onChanged(next.coerceIn(min, max)) } }
    fun setValue(next: Float) {
        if (controlsEnabled) {
            android.os.Trace.beginSection("toast-settings-radius-input")
            try { adjusting = true; onChanged(next.coerceIn(min, max)) }
            finally { android.os.Trace.endSection() }
        }
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).onPreviewKeyEvent { event ->
// Previous implementation retained:         if (event.type != KeyEventType.KeyDown) false else {
        if (!controlsEnabled || event.type != KeyEventType.KeyDown) false else {
            val step = (max - min) / divisions.coerceAtLeast(1)
            when (event.key) {
                Key.DirectionRight -> { setValue(value + if (rtl) -step else step); true }
                Key.DirectionLeft -> { setValue(value + if (rtl) step else -step); true }
                Key.DirectionUp -> { setValue(value + step); true }
                Key.DirectionDown -> { setValue(value - step); true }
                Key.MoveHome -> { setValue(min); true }
                Key.MoveEnd -> { setValue(max); true }
                else -> false
            }
        }
    }) {
// Previous implementation retained:         GbSliderTile(title = title, subtitle = subtitle, value = value, min = min, max = max,
// Previous implementation retained:             divisions = divisions, unit = unit, onChanged = { setValue(it) })
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Slider(value = value.coerceIn(min, max), onValueChange = { setValue(it) },
                enabled = controlsEnabled, valueRange = min..max, steps = (divisions - 1).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = "${value.toInt()}$unit" })
            if (adjusting) {
                Surface(modifier = Modifier.align(Alignment.End).width(128.dp).height(88.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(topStart = (value * 2).dp)) {
                    Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.padding(12.dp)) {
                        Text("Corner detail (2x)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (adjusting) {
            Surface(modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp),
                color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(8.dp)) {
                Text("${value.toInt()}$unit", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

/** NTF-R1-047/NTF-R4-034: keyboard and switch-access alternative to dragging the slider. */
@Composable
private fun ToastRadiusInput(label: String, value: Double, onChanged: (Float) -> Unit) {
    var draft by rememberSaveable(value) { mutableStateOf(value.toInt().toString()) }
    val parsed = draft.toIntOrNull()
    val invalid = draft.isNotEmpty() && (parsed == null || parsed !in 0..30)
    OutlinedTextField(
        value = draft,
        enabled = LocalToastControlsEnabled.current,
        onValueChange = { raw ->
            if (raw.length <= 2 && raw.all { it in '0'..'9' }) {
                draft = raw
                raw.toIntOrNull()?.takeIf { it in 0..30 }?.let { onChanged(it.toFloat()) }
            }
        },
        label = { Text("$label radius (0-30dp)") },
        isError = invalid,
        supportingText = { if (invalid) Text("Enter a number from 0 to 30") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}


/** Shared body renders one immutable toast slice; persistence stays in the parent callback. */
@Composable
// Previous implementation retained: private fun ToastCardBody(
internal fun ToastCardBody(
    type: ToastCardType, ui: ToastCardUiState, radius: Double, onRadiusChange: (Float) -> Unit,
    onChanged: ((ToastCardUiState) -> ToastCardUiState) -> Unit,
    // Previous implementation retained: pickColor: (String, Long?, (Long) -> Unit) -> Unit,
    pickColor: (String, Long?, Long?, (Long) -> Unit) -> Unit,
    showRingtoneModal: (String, (String) -> Unit) -> Unit,
    showPositionModal: (String, (String) -> Unit) -> Unit,
// Previous implementation retained:     ringtoneSubtitle: (String) -> String,
// Previous implementation retained:     colorSubtitle: (Long?, String) -> String,
) {
    val previewBackground = ui.background ?: (MaterialTheme.colorScheme.inverseSurface.toArgb().toLong() and 0xFFFFFFFFL)
    val colorFallback = com.example.chat.ui.kit.ToastContrastPolicy.needsFallback(previewBackground, ui.foreground)
    val title = when (type) {
        ToastCardType.online -> "Contact Online Toast"
        ToastCardType.story -> "Viewed Story Toast"
        ToastCardType.profile -> "Profile Toast"
        ToastCardType.typing -> "Typing Toast"
    }
    val icon = when (type) {
        ToastCardType.online -> Icons.Rounded.NotificationsNone
        ToastCardType.story -> Icons.Rounded.HistoryEdu
        ToastCardType.profile -> Icons.Rounded.AccountBox
        ToastCardType.typing -> Icons.Rounded.Keyboard
    }
    val sample = when (type) {
        ToastCardType.online -> SAMPLE_ONLINE
        ToastCardType.story -> SAMPLE_STORY
        ToastCardType.profile -> SAMPLE_PROFILE
        ToastCardType.typing -> SAMPLE_TYPING
    }
    val enabledSubtitle = when (type) {
        ToastCardType.online -> "Show a toast when a contact comes online"
        ToastCardType.story -> "Show a toast when someone views your status. Contacts aren't told their views trigger this toast."
        ToastCardType.profile -> "Show a toast when someone views your profile. Contacts aren't told their views trigger this toast."
        ToastCardType.typing -> "Show a toast when someone starts typing to you. Contacts aren't told typing triggers this toast."
    }
            ToastTypeCard(type.name, title, icon, initiallyExpanded = type == ToastCardType.online) {
                Column {
                    Text(
                        if (toastCardUsesDefaults(type, ui)) "Default settings for this toast type"
                        else "Custom settings for this toast type",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    ToastPreviewFrame {
                        Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Preview: $title" }, contentAlignment = Alignment.Center) {
// Previous implementation retained:                             ToastPreview(sample, ui.avatar, ui.elevated, radius, ui.background, ui.foreground, Color(0xFF1E2630))
                            ToastPreview(sample, ui.avatar, ui.elevated, radius, ui.background, ui.foreground, MaterialTheme.colorScheme.inverseSurface)
                        }
                    }
                    if (colorFallback) Text(
                        "Saved colors have low contrast. The preview and floating toast use a readable theme text color; your saved color is unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                    )
                    // NTF-R1-001: positive phrasing - the switch reads the stored enabled flag directly.
                    ToastSwitchTile(
// Previous implementation retained:                         icon = Icons.Rounded.NotificationsNone, title = "Show ${type.name} toasts", subtitle = enabledSubtitle, value = ui.enabled,
                        icon = icon, title = "Show ${type.name} toasts", subtitle = enabledSubtitle, value = ui.enabled,
                        onChanged = { next -> onChanged { it.copy(enabled = next) } },
                    )
                    if (!ui.enabled) Text("Turn on this toast type to edit its settings",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    CompositionLocalProvider(LocalToastControlsEnabled provides ui.enabled) {
                    Column(Modifier.padding(start = 16.dp).alpha(if (ui.enabled) 1f else 0.38f)) {
                    if (type == ToastCardType.online || type == ToastCardType.story) ToastSwitchTile(
                        icon = Icons.Rounded.NotificationsNone, title = "Notification in the Notification Bar", subtitle = "Show a notification on the phone's status bar", value = ui.inBar,
                        onChanged = { next -> onChanged { it.copy(inBar = next) } },
                    )
                    ToastSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to toast message", value = ui.avatar,
                        onChanged = { next -> onChanged { it.copy(avatar = next) } },
                    )
                    ToastSwitchTile(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = ui.elevated,
                        onChanged = { next -> onChanged { it.copy(elevated = next) } },
                    )
                    ToastAdvancedSettings {
                    ToastPaletteButtons { bg, fg ->
                        onChanged { it.copy(background = bg, foreground = fg) }
                    }
                    // NTF-R4-045: separate behavior switches from appearance controls.
                    Spacer(Modifier.height(8.dp))
                    ToastRadiusSlider(
                        title = "Radius of toast notifications", subtitle = "Rounded corners radius (0-30dp) · now ${radius.toInt()}dp", value = radius.toFloat(), min = 0f, max = 30f, divisions = 15, unit = "dp",
                        onChanged = onRadiusChange,
                    )
                    ToastRadiusInput(type.name, radius) { onRadiusChange(it) }
                    if (type == ToastCardType.online) ToastSettingRow(
// Previous implementation retained:                         icon = Icons.Rounded.MusicNote, title = "Ringtone", subtitle = ringtoneSubtitle(ui.ringtone), hasSubScreen = true,
                        icon = Icons.Rounded.MusicNote, title = "Ringtone", subtitle = ui.ringtoneLabel, hasSubScreen = true,
                        onTap = { showRingtoneModal(ui.ringtone) { v -> onChanged { it.copy(ringtone = v) } } },
                    )
                    ToastSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = ui.position, hasSubScreen = true,
                        onTap = { showPositionModal(ui.position) { v -> onChanged { it.copy(position = v) } } },
                    )
                    ToastSettingRow(
// Previous implementation retained:                         icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(ui.background, "Default"), colorValue = ui.background,
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = ui.backgroundLabel, colorValue = ui.background,
// Previous implementation retained:                         onTap = { pickColor("Toast Background Color", ui.background) { c -> onChanged { it.copy(background = c) } } },
// Previous implementation retained:                         onColorTap = { pickColor("Toast Background Color", ui.background) { c -> onChanged { it.copy(background = c) } } },
                        onTap = { pickColor("$title background color", ui.background, ui.foreground) { c -> onChanged { it.copy(background = c) } } },
                        onColorTap = { pickColor("$title background color", ui.background, ui.foreground) { c -> onChanged { it.copy(background = c) } } },
                    )
                    ToastSettingRow(
// Previous implementation retained:                         icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(ui.foreground, "Auto (readable on background)"), colorValue = ui.foreground,
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = ui.foregroundLabel, colorValue = ui.foreground,
// Previous implementation retained:                         onTap = { pickColor("Toast Text Color", ui.foreground) { c -> onChanged { it.copy(foreground = c) } } },
// Previous implementation retained:                         onColorTap = { pickColor("Toast Text Color", ui.foreground) { c -> onChanged { it.copy(foreground = c) } } },
                        onTap = { pickColor("$title text color", ui.foreground, previewBackground) { c -> onChanged { it.copy(foreground = c) } } },
                        onColorTap = { pickColor("$title text color", ui.foreground, previewBackground) { c -> onChanged { it.copy(foreground = c) } } },
                    )
                    }
                    }
                    }
                }
            }

}

/** NTF-R1-013: system notification permission check (framework-only, no androidx.core dependency). */
private fun systemNotificationsAllowed(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return true
    val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return true
    return nm.areNotificationsEnabled()
}

/**
 * Port of NotificationToastSettingsScreen. Dart field aliases map to the Kotlin real names
 * (e.g. onlineWithProfilePic -> onlineToastWithProfilePicture). Capability matrix (NTF-R1-009):
 * ringtone applies to the Online card only; the notification-bar toggle applies to Online and Story;
 * radius, position, background/text color and elevation are per-type on every card.
 * Previews use a neutral sample name, never the device owner's (NTF-R1-007).
 * Performance risks still to profile: delayed writes on navigation, lazy card invalidation, and picker first-open cost.
 * UNCOMPILED (Compose).
 */
@Composable
fun NotificationToastSettingsScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val config = LocalThemeConfig.current
    val context = androidx.compose.ui.platform.LocalContext.current
// Previous implementation retained:     var version by remember { mutableIntStateOf(0) }
// Previous implementation retained:     DisposableEffect(preferences) {
// Previous implementation retained: // Previous implementation retained:         val l: () -> Unit = { version += 1 }
// Previous implementation retained:         // NTF-R4-007: unrelated preference updates must not invalidate the toast screen.
// Previous implementation retained:         var lastToasts = preferences.toasts
// Previous implementation retained:         val l: () -> Unit = {
// Previous implementation retained:             val latest = preferences.toasts
// Previous implementation retained:             if (latest != lastToasts) {
// Previous implementation retained:                 lastToasts = latest
// Previous implementation retained:                 version += 1
    val lifecycleOwner = LocalLifecycleOwner.current
    // Review fix: the account scope this composition belongs to. Closures captured here keep THIS value, so a
    // delayed drag commit, Undo, picker apply or Reset from an older scope sees a mismatch and does nothing.
    val scopeGen = preferences.scopeGeneration
    fun scopeIsCurrent() = preferences.scopeGeneration == scopeGen
// Previous implementation retained:     var toastPrefs by remember(preferences) { mutableStateOf(preferences.toasts) }
    var toastPrefs by remember(preferences, scopeGen) { mutableStateOf(preferences.toasts) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    // NTF-R2-028/NTF-R4-007/021: observe only while started, refresh latest on return,
    // and deliver immutable toast snapshots on Main rather than incrementing a version counter.
// Previous implementation retained:     DisposableEffect(preferences, lifecycleOwner) {
    DisposableEffect(preferences, lifecycleOwner, scopeGen) { // review fix: re-subscribe when the account scope changes; old listener is removed in onDispose
        var observing = false
        var disposed = false
        val listener: () -> Unit = {
            mainHandler.post { if (!disposed && observing) toastPrefs = preferences.toasts }
        }
        fun startObserving() {
            if (!observing) { observing = true; preferences.addListener(listener) }
            toastPrefs = preferences.toasts
        }
        fun stopObserving() {
            if (observing) { observing = false; preferences.removeListener(listener) }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startObserving()
                Lifecycle.Event.ON_STOP -> stopObserving()
                else -> Unit
            }
        }
// Previous implementation retained:         preferences.addListener(l)
// Previous implementation retained:         onDispose { preferences.removeListener(l) }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startObserving()
        onDispose { disposed = true; stopObserving(); lifecycleOwner.lifecycle.removeObserver(observer) }
    }
// Previous implementation retained:     val p = preferences.toasts
    val p = toastPrefs
    val saveState by preferences.saveState.collectAsState()
    val onlineCard = remember(toastPrefs) { toastCardSlice(ToastCardType.online, toastPrefs) }
    val storyCard = remember(toastPrefs) { toastCardSlice(ToastCardType.story, toastPrefs) }
    val profileCard = remember(toastPrefs) { toastCardSlice(ToastCardType.profile, toastPrefs) }
    val typingCard = remember(toastPrefs) { toastCardSlice(ToastCardType.typing, toastPrefs) }
// Previous implementation retained:     fun updateCard(type: ToastCardType, transform: (ToastCardUiState) -> ToastCardUiState) {
// Previous implementation retained:         val latest = preferences.toasts
// Previous implementation retained:         preferences.updateToasts(withToastCardSlice(type, latest, transform(toastCardSlice(type, latest))))
// Previous implementation retained:     }
    val pickers = rememberGbPickers()
    val snackbarHost = remember { SnackbarHostState() }
    val feedbackScope = rememberCoroutineScope()
// Previous implementation retained:     var undoAppearanceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var undoAppearanceJob by remember(preferences, scopeGen) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // A scope change drops a pending Undo and its snackbar.
    LaunchedEffect(scopeGen) { snackbarHost.currentSnackbarData?.dismiss() }
    fun updateCard(type: ToastCardType, transform: (ToastCardUiState) -> ToastCardUiState) {
        if (!scopeIsCurrent()) return // review fix: stale generation
        val latest = preferences.toasts
        val before = toastCardSlice(type, latest)
        val after = transform(before)
        preferences.updateToasts(withToastCardSlice(type, latest, after))
        if (toastAppearanceChanged(before, after)) {
            undoAppearanceJob?.cancel()
            snackbarHost.currentSnackbarData?.dismiss()
            undoAppearanceJob = feedbackScope.launch {
                val result = kotlinx.coroutines.withTimeoutOrNull(5000) {
                    snackbarHost.showSnackbar("${type.name.replaceFirstChar { it.uppercaseChar() }} toast appearance changed",
                        actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Indefinite)
                }
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed && scopeIsCurrent()) { // review fix: no Undo across accounts
                    val current = preferences.toasts
                    val undone = undoToastAppearance(before, after, toastCardSlice(type, current))
                    preferences.updateToasts(withToastCardSlice(type, current, undone))
                }
            }
        }
    }
    var positionRequest by remember(preferences, scopeGen) { mutableStateOf<ToastPositionRequest?>(null) }
    var pickerReturnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val anyPickerOpen = pickers.radio != null || pickers.color != null || positionRequest != null
    var pickerWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(anyPickerOpen) {
        if (pickerWasOpen && !anyPickerOpen) {
            // Wait one composition frame for the modal focus target to leave.
            androidx.compose.runtime.withFrameNanos { }
            // A row may have been removed after a reset/navigation; do not crash on stale focus.
            runCatching { pickerReturnFocus?.requestFocus() }
        }
        pickerWasOpen = anyPickerOpen
    }
    var positionPreview by remember { mutableStateOf<ToastPositionPreview?>(null) }
    LaunchedEffect(positionPreview) {
        if (positionPreview != null) { kotlinx.coroutines.delay(2000); positionPreview = null }
    }
    // NTF-R1-017: force alpha FF on picked colors so a translucent pick cannot break readability.
    // Previous implementation retained: pickers.showColor(title, current ?: config.accentColor) { if (scopeIsCurrent()) apply(it or 0xFF000000L) }
    fun pickColor(title: String, current: Long?, contrastWith: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor, backgroundContextColor = contrastWith) {
            if (scopeIsCurrent()) apply(it or 0xFF000000L)
        }
// Previous implementation retained:     fun showPositionModal(current: String, onSelected: (String) -> Unit) = pickers.showRadio(
// Previous implementation retained:         "Position of Toast", TOAST_POSITIONS, current, onSelected = onSelected,
// Previous implementation retained:     )
    fun showPositionModal(current: String, onSelected: (String) -> Unit) {
        positionRequest = ToastPositionRequest(current) { v -> if (scopeIsCurrent()) onSelected(v) } // review fix
    }
    fun showRingtoneModal(current: String, onSelected: (String) -> Unit) = pickers.showRadio(
// Previous implementation retained: // Previous implementation retained:         "Ringtone", TOAST_RINGTONES, current, onSelected = onSelected,
// Previous implementation retained:         "Ringtone", TOAST_RINGTONES, if (current == "Default Chaty Tone") "Default" else current, onSelected = onSelected,
        "Ringtone", TOAST_RINGTONES, current,
        // NTF-R2-015: only fixed-list tones can be persisted; ignore anything else.
// Previous implementation retained:         onSelected = { v -> if (v in TOAST_RINGTONES) onSelected(v) },
        onSelected = { v ->
            if (v in TOAST_RINGTONES && scopeIsCurrent()) { // review fix: stale-scope modal apply ignored
                onSelected(v)
                if (v == "Silent") feedbackScope.launch { snackbarHost.showSnackbar("Silent selected - no sound") }
            }
        },
    )
// Previous implementation retained:     fun ringtoneSubtitle(value: String) = if (value == "Silent") "Silent - no sound will play" else value
// Previous implementation retained: // Previous implementation retained:     fun colorSubtitle(value: Long?, unset: String) = value?.let { formatColorHex(it) } ?: unset
// Previous implementation retained:     fun colorSubtitle(value: Long?, unset: String): String {
// Previous implementation retained:         if (value == null) return unset
// Previous implementation retained:         val hex = formatColorHex(value)
// Previous implementation retained:         val name = when (hex) {
// Previous implementation retained:             "#FFFFFFFF" -> "White"
// Previous implementation retained:             "#FF000000" -> "Black"
// Previous implementation retained:             "#FFFF0000" -> "Red"
// Previous implementation retained:             "#FF00FF00" -> "Green"
// Previous implementation retained:             "#FF0000FF" -> "Blue"
// Previous implementation retained:             else -> "Hex " + hex.removePrefix("#").toList().joinToString(" ")
// Previous implementation retained:         }
// Previous implementation retained:         return "$name ($hex)"
// Previous implementation retained:     }
// Previous implementation retained:
    // NTF-R1-013: recheck the system permission whenever the screen recomposes.
// Previous implementation retained:     val notificationsAllowed = remember(version) { systemNotificationsAllowed(context) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
// Previous implementation retained:     val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
// Previous implementation retained:     val notificationsAllowed = remember(context, version, permissionRefresh) { systemNotificationsAllowed(context) }
    val notificationsAllowed = remember(context, permissionRefresh) { systemNotificationsAllowed(context) }

    // NTF-R1-003/NTF-R1-025: radius sliders keep a local drag value and commit ~450ms after the last
    // movement instead of writing to disk on every drag event; previews read the drag value so they
    // stay live while dragging (NTF-R4-026).
// Previous implementation retained:     var onlineRadiusDrag by remember { mutableStateOf<Float?>(null) }
// Previous implementation retained:     var storyRadiusDrag by remember { mutableStateOf<Float?>(null) }
// Previous implementation retained:     var profileRadiusDrag by remember { mutableStateOf<Float?>(null) }
// Previous implementation retained:     var typingRadiusDrag by remember { mutableStateOf<Float?>(null) }
    var onlineRadiusDrag by rememberSaveable(scopeGen) { mutableStateOf<Float?>(null) }
    var storyRadiusDrag by rememberSaveable(scopeGen) { mutableStateOf<Float?>(null) }
    var profileRadiusDrag by rememberSaveable(scopeGen) { mutableStateOf<Float?>(null) }
    var typingRadiusDrag by rememberSaveable(scopeGen) { mutableStateOf<Float?>(null) }
    val onlineRadius = (onlineRadiusDrag?.toDouble() ?: p.onlineRadius).coerceIn(TOAST_RADIUS_MIN, TOAST_RADIUS_MAX)
    val storyRadius = (storyRadiusDrag?.toDouble() ?: p.storyRadius).coerceIn(TOAST_RADIUS_MIN, TOAST_RADIUS_MAX)
    val profileRadius = (profileRadiusDrag?.toDouble() ?: p.profileRadius).coerceIn(TOAST_RADIUS_MIN, TOAST_RADIUS_MAX)
    val typingRadius = (typingRadiusDrag?.toDouble() ?: p.typingRadius).coerceIn(TOAST_RADIUS_MIN, TOAST_RADIUS_MAX)
// Previous implementation retained:     LaunchedEffect(onlineRadiusDrag) { val d = onlineRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); preferences.updateToasts(preferences.toasts.copy(onlineToastRadius = d.toDouble())); onlineRadiusDrag = null }
// Previous implementation retained:     LaunchedEffect(storyRadiusDrag) { val d = storyRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); preferences.updateToasts(preferences.toasts.copy(storyToastRadius = d.toDouble())); storyRadiusDrag = null }
// Previous implementation retained:     LaunchedEffect(profileRadiusDrag) { val d = profileRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); preferences.updateToasts(preferences.toasts.copy(profileToastRadius = d.toDouble())); profileRadiusDrag = null }
// Previous implementation retained:     LaunchedEffect(typingRadiusDrag) { val d = typingRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); preferences.updateToasts(preferences.toasts.copy(typingToastRadius = d.toDouble())); typingRadiusDrag = null }
    LaunchedEffect(onlineRadiusDrag, scopeGen) { val d = onlineRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); if (!scopeIsCurrent()) { onlineRadiusDrag = null; return@LaunchedEffect }; updateCard(ToastCardType.online) { it.copy(radius = d.toDouble()) }; onlineRadiusDrag = null }
    LaunchedEffect(storyRadiusDrag, scopeGen) { val d = storyRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); if (!scopeIsCurrent()) { storyRadiusDrag = null; return@LaunchedEffect }; updateCard(ToastCardType.story) { it.copy(radius = d.toDouble()) }; storyRadiusDrag = null }
    LaunchedEffect(profileRadiusDrag, scopeGen) { val d = profileRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); if (!scopeIsCurrent()) { profileRadiusDrag = null; return@LaunchedEffect }; updateCard(ToastCardType.profile) { it.copy(radius = d.toDouble()) }; profileRadiusDrag = null }
    LaunchedEffect(typingRadiusDrag, scopeGen) { val d = typingRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); if (!scopeIsCurrent()) { typingRadiusDrag = null; return@LaunchedEffect }; updateCard(ToastCardType.typing) { it.copy(radius = d.toDouble()) }; typingRadiusDrag = null }

    // NTF-R1-011: keep scroll position across rotation/process death.
    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    var confirmReset by remember(preferences, scopeGen) { mutableStateOf(false) }

    val allTypesEnabled = p.onlineToastEnabled && p.viewedStoryToast && p.profileToast && p.typingToast

    CompositionLocalProvider(LocalToastPickerReturnFocus provides { pickerReturnFocus = it }) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor)).semantics { paneTitle = "Toast notifications" }) {
        Row(
            Modifier.fillMaxWidth().background(Color(config.surfaceColor))
// Previous implementation retained:                 .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            // NTF-R1-045/R3-045: distinct name (not Android's own "Notifications"); NTF-R1-044: fixed size,
            // no manual fontScale multiplication; NTF-R1-038: heading semantics.
// Previous implementation retained:             Text("Toast Notifications", Modifier.semantics { heading() }, color = Color(config.primaryTextColor), fontSize = 22.sp, fontWeight = FontWeight.W700)
            Text("Toast Notifications", Modifier.semantics { heading() }, color = Color(config.primaryTextColor), style = MaterialTheme.typography.titleLarge)
        }
// Previous implementation retained:         Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 14.dp, vertical = 12.dp)) {
        // NTF-R1-026/035: one keyed lazy item per card; only nearby cards are composed.
// Previous implementation retained:         LazyColumn(state = rememberLazyListState(), modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
// Previous implementation retained:             item(key = "toast_summary", contentType = "toast_summary") {
        ToastSaveFailureRow(saveState) { if (scopeIsCurrent()) preferences.retrySave() }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 360.dp),
            state = rememberLazyGridState(),
// Previous implementation retained:             contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            item(key = "toast_summary", contentType = "toast_summary", span = { GridItemSpan(maxLineSpan) }) {
            if (!notificationsAllowed) {
                // NTF-R1-013: nothing on this screen fires when the system permission is off - say so and deep-link.
// Previous implementation retained:                 GbCardContainer {
                GbCardContainer(padding = PaddingValues(vertical = 4.dp), margin = PaddingValues(vertical = 8.dp)) {
// Previous implementation retained:                     GbSettingRow(
                    ToastSettingRow(
                        icon = Icons.Rounded.Warning, title = "Android notifications are off", subtitle = "Toast notifications can't appear while notifications are off for Chaty. Tap to open system settings.", hasSubScreen = true,
                        onTap = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            context.startActivity(intent)
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
// Previous implementation retained:             GbCardContainer(headerText = "Activity toasts") {
            GbCardContainer(padding = PaddingValues(vertical = 4.dp), margin = PaddingValues(vertical = 8.dp), headerText = "Activity toasts") {
                // NTF-R1-018: one master switch writing all four type toggles in a single update.
// Previous implementation retained:                 GbSwitchTile(
                ToastSwitchTile(
                    icon = Icons.Rounded.ToggleOn, title = "All activity toasts", subtitle = "Online, story, profile and typing toasts. Toasts may also appear on your lock screen and notification shade.",
                    value = allTypesEnabled,
                    onChanged = { on ->
                        val t = preferences.toasts
                        if (scopeIsCurrent()) preferences.updateToasts(t.copy(onlineToastEnabled = on, storyToastEnabled = on, profileToastEnabled = on, typingToastEnabled = on)) // review fix
                    },
                )
            }
            if (!allTypesEnabled && !p.onlineToastEnabled && !p.viewedStoryToast && !p.profileToast && !p.typingToast) {
                // NTF-R4-006: say it in one glance when the whole feature is off.
                Spacer(Modifier.height(8.dp))
// Previous implementation retained:                 Text("All toast types are off", color = Color(config.secondaryTextColor), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
                Text("All toast types are off", color = Color(config.secondaryTextColor), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
            }
            }
// Previous implementation retained:             item(key = "online", contentType = "toast_card") {
// Previous implementation retained: // Previous implementation retained:             Spacer(Modifier.height(16.dp))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:             GbCardContainer(headerText = "Contact Online Toast") {
// Previous implementation retained: // Previous implementation retained:             ToastTypeCard("online", "Contact Online Toast", Icons.Rounded.NotificationsNone, initiallyExpanded = true) {
// Previous implementation retained: // Previous implementation retained:                 Column {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbLivePreviewCard(height = 70.dp) {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         Box(Modifier.fillMaxWidth().fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "Preview: contact online toast" }, contentAlignment = Alignment.Center) {
// Previous implementation retained: // Previous implementation retained:                     ToastPreviewFrame {
// Previous implementation retained: // Previous implementation retained:                         Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Preview: contact online toast" }, contentAlignment = Alignment.Center) {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview("$SAMPLE_NAME Online", p.onlineWithProfilePic, p.onlineElevation, onlineRadius, p.onlineBgColor, p.onlineTextColor, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview(SAMPLE_ONLINE, p.onlineWithProfilePic, p.onlineElevation, onlineRadius, p.onlineBgColor, p.onlineTextColor, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview("$SAMPLE_NAME Online", onlineCard.avatar, onlineCard.elevated, onlineRadius, onlineCard.background, onlineCard.foreground, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained:                             ToastPreview(SAMPLE_ONLINE, onlineCard.avatar, onlineCard.elevated, onlineRadius, onlineCard.background, onlineCard.foreground, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained:                         }
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained:                     // NTF-R1-001: positive phrasing - the switch reads the stored enabled flag directly.
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.NotificationsNone, title = "Contact online toast", subtitle = "Show a toast when a contact comes online", value = p.onlineToastEnabled,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.NotificationsNone, title = "Show online toasts", subtitle = "Show a toast when a contact comes online", value = p.onlineToastEnabled,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastEnabled = it)) },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.NotificationsNone, title = "Contact online toast", subtitle = "Show a toast when a contact comes online", value = onlineCard.enabled,
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.NotificationsNone, title = "Show online toasts", subtitle = "Show a toast when a contact comes online", value = onlineCard.enabled,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.online) { it.copy(enabled = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.NotificationsNone, title = "Notification in the Notification Bar", subtitle = "Show a notification on the phone's status bar", value = p.onlineInNotificationBar,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastInNotificationBar = it)) },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.NotificationsNone, title = "Notification in the Notification Bar", subtitle = "Show a notification on the phone's status bar", value = onlineCard.inBar,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.online) { it.copy(inBar = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to toast message", value = p.onlineWithProfilePic,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastWithProfilePicture = it)) },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to toast message", value = onlineCard.avatar,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.online) { it.copy(avatar = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = p.onlineElevation,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastElevation = it)) },
// Previous implementation retained: // Previous implementation retained:                         icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = onlineCard.elevated,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.online) { it.copy(elevated = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     ToastAdvancedSettings {
// Previous implementation retained: // Previous implementation retained:                     ToastPaletteButtons { bg, fg ->
// Previous implementation retained: // Previous implementation retained:                         val latest = preferences.toasts
// Previous implementation retained: // Previous implementation retained:                         preferences.updateToasts(latest.copy(onlineToastBgColor = bg, onlineToastTextColor = fg))
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained:                     // NTF-R4-045: separate behavior switches from appearance controls.
// Previous implementation retained: // Previous implementation retained:                     Spacer(Modifier.height(8.dp))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSliderTile(
// Previous implementation retained: // Previous implementation retained:                     ToastRadiusSlider(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${onlineRadius.toInt()}dp", value = onlineRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius (0-30dp) · now ${onlineRadius.toInt()}dp", value = onlineRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${onlineRadius.toInt()}dp", value = onlineRadius.toFloat(), min = 0f, max = 30f, divisions = 15, unit = "dp",
// Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius (0-30dp) · now ${onlineRadius.toInt()}dp", value = onlineRadius.toFloat(), min = 0f, max = 30f, divisions = 15, unit = "dp",
// Previous implementation retained: // Previous implementation retained:                         onChanged = { onlineRadiusDrag = it },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     ToastRadiusInput("Online", onlineRadius) { onlineRadiusDrag = it }
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.MusicNote, title = "Ringtone", subtitle = ringtoneSubtitle(p.onlineRingtone), hasSubScreen = true,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { showRingtoneModal(p.onlineRingtone) { v -> preferences.updateToasts(preferences.toasts.copy(onlineToastRingtone = v)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.MusicNote, title = "Ringtone", subtitle = ringtoneSubtitle(onlineCard.ringtone), hasSubScreen = true,
// Previous implementation retained: // Previous implementation retained:                         onTap = { showRingtoneModal(onlineCard.ringtone) { v -> updateCard(ToastCardType.online) { it.copy(ringtone = v) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.onlinePosition, hasSubScreen = true,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { showPositionModal(p.onlinePosition) { v -> preferences.updateToasts(preferences.toasts.copy(onlineToastPosition = v)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = onlineCard.position, hasSubScreen = true,
// Previous implementation retained: // Previous implementation retained:                         onTap = { showPositionModal(onlineCard.position) { v -> updateCard(ToastCardType.online) { it.copy(position = v) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(p.onlineBgColor, "Default"), colorValue = p.onlineBgColor,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Toast Background Color", p.onlineBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastBgColor = c)) } },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Toast Background Color", p.onlineBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastBgColor = c)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(onlineCard.background, "Default"), colorValue = onlineCard.background,
// Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Toast Background Color", onlineCard.background) { c -> updateCard(ToastCardType.online) { it.copy(background = c) } } },
// Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Toast Background Color", onlineCard.background) { c -> updateCard(ToastCardType.online) { it.copy(background = c) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(p.onlineTextColor, "Auto (readable on background)"), colorValue = p.onlineTextColor,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Toast Text Color", p.onlineTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastTextColor = c)) } },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Toast Text Color", p.onlineTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastTextColor = c)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(onlineCard.foreground, "Auto (readable on background)"), colorValue = onlineCard.foreground,
// Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Toast Text Color", onlineCard.foreground) { c -> updateCard(ToastCardType.online) { it.copy(foreground = c) } } },
// Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Toast Text Color", onlineCard.foreground) { c -> updateCard(ToastCardType.online) { it.copy(foreground = c) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained:                 }
// Previous implementation retained: // Previous implementation retained:             }
            // The type registry drives card creation; adding a type makes slice/radius mappings exhaustive.
            items(ToastCardType.entries, key = { it.name }, contentType = { "toast_card" }) { type ->
                val ui = when (type) {
                    ToastCardType.online -> onlineCard
                    ToastCardType.story -> storyCard
                    ToastCardType.profile -> profileCard
                    ToastCardType.typing -> typingCard
                }
                val radius = when (type) {
                    ToastCardType.online -> onlineRadius
                    ToastCardType.story -> storyRadius
                    ToastCardType.profile -> profileRadius
                    ToastCardType.typing -> typingRadius
                }
                Spacer(Modifier.height(16.dp))
// Previous implementation retained:                 ToastCardBody(ToastCardType.online, onlineCard, onlineRadius,
// Previous implementation retained:                     onRadiusChange = { onlineRadiusDrag = it },
// Previous implementation retained:                     onChanged = { transform -> updateCard(ToastCardType.online, transform) },
                ToastCardBody(type, ui, radius,
                    onRadiusChange = { next -> when (type) {
                        ToastCardType.online -> onlineRadiusDrag = next
                        ToastCardType.story -> storyRadiusDrag = next
                        ToastCardType.profile -> profileRadiusDrag = next
                        ToastCardType.typing -> typingRadiusDrag = next
                    } },
                    onChanged = { transform -> updateCard(type, transform) },
                    pickColor = ::pickColor, showRingtoneModal = ::showRingtoneModal,
// Previous implementation retained:                     showPositionModal = ::showPositionModal, ringtoneSubtitle = ::ringtoneSubtitle,
// Previous implementation retained:                     colorSubtitle = ::colorSubtitle)
                    showPositionModal = ::showPositionModal)
            }
// Previous implementation retained:             item(key = "story", contentType = "toast_card") {
// Previous implementation retained: // Previous implementation retained:             Spacer(Modifier.height(16.dp))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:             GbCardContainer(headerText = "Viewed Story Toast") {
// Previous implementation retained: // Previous implementation retained:             ToastTypeCard("story", "Viewed Story Toast", Icons.Rounded.HistoryEdu, initiallyExpanded = false) {
// Previous implementation retained: // Previous implementation retained:                 Column {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbLivePreviewCard(height = 70.dp) {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         Box(Modifier.fillMaxWidth().fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "Preview: story view toast" }, contentAlignment = Alignment.Center) {
// Previous implementation retained: // Previous implementation retained:                     ToastPreviewFrame {
// Previous implementation retained: // Previous implementation retained:                         Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Preview: story view toast" }, contentAlignment = Alignment.Center) {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview("$SAMPLE_NAME Status", p.storyWithProfilePic, p.storyElevation, storyRadius, p.storyBgColor, p.storyTextColor, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview(SAMPLE_STORY, p.storyWithProfilePic, p.storyElevation, storyRadius, p.storyBgColor, p.storyTextColor, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview("$SAMPLE_NAME Status", storyCard.avatar, storyCard.elevated, storyRadius, storyCard.background, storyCard.foreground, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained:                             ToastPreview(SAMPLE_STORY, storyCard.avatar, storyCard.elevated, storyRadius, storyCard.background, storyCard.foreground, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained:                         }
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.HistoryEdu, title = "Story view toast", subtitle = "Show a toast when someone views your status. Contacts aren't told their views trigger this toast.", value = p.viewedStoryToast,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.HistoryEdu, title = "Show story toasts", subtitle = "Show a toast when someone views your status. Contacts aren't told their views trigger this toast.", value = p.viewedStoryToast,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastEnabled = it)) },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.HistoryEdu, title = "Story view toast", subtitle = "Show a toast when someone views your status. Contacts aren't told their views trigger this toast.", value = storyCard.enabled,
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.HistoryEdu, title = "Show story toasts", subtitle = "Show a toast when someone views your status. Contacts aren't told their views trigger this toast.", value = storyCard.enabled,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.story) { it.copy(enabled = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.NotificationsNone, title = "Notification in the Notification Bar", subtitle = "Show in status bar", value = p.storyInNotificationBar,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastInNotificationBar = it)) },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.NotificationsNone, title = "Notification in the Notification Bar", subtitle = "Show in status bar", value = storyCard.inBar,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.story) { it.copy(inBar = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to status toast", value = p.storyWithProfilePic,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastWithProfilePicture = it)) },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to status toast", value = storyCard.avatar,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.story) { it.copy(avatar = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = p.storyElevation,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastElevation = it)) },
// Previous implementation retained: // Previous implementation retained:                         icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = storyCard.elevated,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.story) { it.copy(elevated = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     ToastAdvancedSettings {
// Previous implementation retained: // Previous implementation retained:                     ToastPaletteButtons { bg, fg ->
// Previous implementation retained: // Previous implementation retained:                         val latest = preferences.toasts
// Previous implementation retained: // Previous implementation retained:                         preferences.updateToasts(latest.copy(storyToastBgColor = bg, storyToastTextColor = fg))
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained:                     // NTF-R4-045: separate behavior switches from appearance controls.
// Previous implementation retained: // Previous implementation retained:                     Spacer(Modifier.height(8.dp))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSliderTile(
// Previous implementation retained: // Previous implementation retained:                     ToastRadiusSlider(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${storyRadius.toInt()}dp", value = storyRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius (0-30dp) · now ${storyRadius.toInt()}dp", value = storyRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${storyRadius.toInt()}dp", value = storyRadius.toFloat(), min = 0f, max = 30f, divisions = 15, unit = "dp",
// Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius (0-30dp) · now ${storyRadius.toInt()}dp", value = storyRadius.toFloat(), min = 0f, max = 30f, divisions = 15, unit = "dp",
// Previous implementation retained: // Previous implementation retained:                         onChanged = { storyRadiusDrag = it },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     ToastRadiusInput("Story", storyRadius) { storyRadiusDrag = it }
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.storyPosition, hasSubScreen = true,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { showPositionModal(p.storyPosition) { v -> preferences.updateToasts(preferences.toasts.copy(storyToastPosition = v)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = storyCard.position, hasSubScreen = true,
// Previous implementation retained: // Previous implementation retained:                         onTap = { showPositionModal(storyCard.position) { v -> updateCard(ToastCardType.story) { it.copy(position = v) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(p.storyBgColor, "Default"), colorValue = p.storyBgColor,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Story Toast Background Color", p.storyBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastBgColor = c)) } },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Story Toast Background Color", p.storyBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastBgColor = c)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(storyCard.background, "Default"), colorValue = storyCard.background,
// Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Story Toast Background Color", storyCard.background) { c -> updateCard(ToastCardType.story) { it.copy(background = c) } } },
// Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Story Toast Background Color", storyCard.background) { c -> updateCard(ToastCardType.story) { it.copy(background = c) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(p.storyTextColor, "Auto (readable on background)"), colorValue = p.storyTextColor,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Story Toast Text Color", p.storyTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastTextColor = c)) } },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Story Toast Text Color", p.storyTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastTextColor = c)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(storyCard.foreground, "Auto (readable on background)"), colorValue = storyCard.foreground,
// Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Story Toast Text Color", storyCard.foreground) { c -> updateCard(ToastCardType.story) { it.copy(foreground = c) } } },
// Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Story Toast Text Color", storyCard.foreground) { c -> updateCard(ToastCardType.story) { it.copy(foreground = c) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained:                 }
// Previous implementation retained: // Previous implementation retained:             }
// Previous implementation retained:                 Spacer(Modifier.height(16.dp))
// Previous implementation retained:                 ToastCardBody(ToastCardType.story, storyCard, storyRadius,
// Previous implementation retained:                     onRadiusChange = { storyRadiusDrag = it },
// Previous implementation retained:                     onChanged = { transform -> updateCard(ToastCardType.story, transform) },
// Previous implementation retained:                     pickColor = ::pickColor, showRingtoneModal = ::showRingtoneModal,
// Previous implementation retained:                     showPositionModal = ::showPositionModal, ringtoneSubtitle = ::ringtoneSubtitle,
// Previous implementation retained:                     colorSubtitle = ::colorSubtitle)
// Previous implementation retained:             }
// Previous implementation retained:             item(key = "profile", contentType = "toast_card") {
// Previous implementation retained: // Previous implementation retained:             Spacer(Modifier.height(16.dp))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:             GbCardContainer(headerText = "Profile Toast") {
// Previous implementation retained: // Previous implementation retained:             ToastTypeCard("profile", "Profile Toast", Icons.Rounded.AccountBox, initiallyExpanded = false) {
// Previous implementation retained: // Previous implementation retained:                 Column {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbLivePreviewCard(height = 70.dp) {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         Box(Modifier.fillMaxWidth().fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "Preview: profile view toast" }, contentAlignment = Alignment.Center) {
// Previous implementation retained: // Previous implementation retained:                     ToastPreviewFrame {
// Previous implementation retained: // Previous implementation retained:                         Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Preview: profile view toast" }, contentAlignment = Alignment.Center) {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview("$SAMPLE_NAME Profile", p.profileWithProfilePic, p.profileElevation, profileRadius, p.profileBgColor, p.profileTextColor, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview(SAMPLE_PROFILE, p.profileWithProfilePic, p.profileElevation, profileRadius, p.profileBgColor, p.profileTextColor, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview("$SAMPLE_NAME Profile", profileCard.avatar, profileCard.elevated, profileRadius, profileCard.background, profileCard.foreground, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained:                             ToastPreview(SAMPLE_PROFILE, profileCard.avatar, profileCard.elevated, profileRadius, profileCard.background, profileCard.foreground, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained:                         }
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.AccountBox, title = "Profile view toast", subtitle = "Show a toast when someone views your profile. Contacts aren't told their views trigger this toast.", value = p.profileToast,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.AccountBox, title = "Show profile toasts", subtitle = "Show a toast when someone views your profile. Contacts aren't told their views trigger this toast.", value = p.profileToast,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastEnabled = it)) },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.AccountBox, title = "Profile view toast", subtitle = "Show a toast when someone views your profile. Contacts aren't told their views trigger this toast.", value = profileCard.enabled,
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.AccountBox, title = "Show profile toasts", subtitle = "Show a toast when someone views your profile. Contacts aren't told their views trigger this toast.", value = profileCard.enabled,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.profile) { it.copy(enabled = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar", value = p.profileWithProfilePic,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastWithProfilePicture = it)) },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar", value = profileCard.avatar,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.profile) { it.copy(avatar = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = p.profileElevation,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastElevation = it)) },
// Previous implementation retained: // Previous implementation retained:                         icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = profileCard.elevated,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.profile) { it.copy(elevated = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     ToastAdvancedSettings {
// Previous implementation retained: // Previous implementation retained:                     ToastPaletteButtons { bg, fg ->
// Previous implementation retained: // Previous implementation retained:                         val latest = preferences.toasts
// Previous implementation retained: // Previous implementation retained:                         preferences.updateToasts(latest.copy(profileToastBgColor = bg, profileToastTextColor = fg))
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained:                     // NTF-R4-045: separate behavior switches from appearance controls.
// Previous implementation retained: // Previous implementation retained:                     Spacer(Modifier.height(8.dp))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSliderTile(
// Previous implementation retained: // Previous implementation retained:                     ToastRadiusSlider(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${profileRadius.toInt()}dp", value = profileRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius (0-30dp) · now ${profileRadius.toInt()}dp", value = profileRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${profileRadius.toInt()}dp", value = profileRadius.toFloat(), min = 0f, max = 30f, divisions = 15, unit = "dp",
// Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius (0-30dp) · now ${profileRadius.toInt()}dp", value = profileRadius.toFloat(), min = 0f, max = 30f, divisions = 15, unit = "dp",
// Previous implementation retained: // Previous implementation retained:                         onChanged = { profileRadiusDrag = it },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     ToastRadiusInput("Profile", profileRadius) { profileRadiusDrag = it }
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.profilePosition, hasSubScreen = true,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { showPositionModal(p.profilePosition) { v -> preferences.updateToasts(preferences.toasts.copy(profileToastPosition = v)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = profileCard.position, hasSubScreen = true,
// Previous implementation retained: // Previous implementation retained:                         onTap = { showPositionModal(profileCard.position) { v -> updateCard(ToastCardType.profile) { it.copy(position = v) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(p.profileBgColor, "Default"), colorValue = p.profileBgColor,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Profile Toast Background Color", p.profileBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastBgColor = c)) } },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Profile Toast Background Color", p.profileBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastBgColor = c)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(profileCard.background, "Default"), colorValue = profileCard.background,
// Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Profile Toast Background Color", profileCard.background) { c -> updateCard(ToastCardType.profile) { it.copy(background = c) } } },
// Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Profile Toast Background Color", profileCard.background) { c -> updateCard(ToastCardType.profile) { it.copy(background = c) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(p.profileTextColor, "Auto (readable on background)"), colorValue = p.profileTextColor,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Profile Toast Text Color", p.profileTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastTextColor = c)) } },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Profile Toast Text Color", p.profileTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastTextColor = c)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(profileCard.foreground, "Auto (readable on background)"), colorValue = profileCard.foreground,
// Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Profile Toast Text Color", profileCard.foreground) { c -> updateCard(ToastCardType.profile) { it.copy(foreground = c) } } },
// Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Profile Toast Text Color", profileCard.foreground) { c -> updateCard(ToastCardType.profile) { it.copy(foreground = c) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained:                 }
// Previous implementation retained: // Previous implementation retained:             }
// Previous implementation retained:                 Spacer(Modifier.height(16.dp))
// Previous implementation retained:                 ToastCardBody(ToastCardType.profile, profileCard, profileRadius,
// Previous implementation retained:                     onRadiusChange = { profileRadiusDrag = it },
// Previous implementation retained:                     onChanged = { transform -> updateCard(ToastCardType.profile, transform) },
// Previous implementation retained:                     pickColor = ::pickColor, showRingtoneModal = ::showRingtoneModal,
// Previous implementation retained:                     showPositionModal = ::showPositionModal, ringtoneSubtitle = ::ringtoneSubtitle,
// Previous implementation retained:                     colorSubtitle = ::colorSubtitle)
// Previous implementation retained:             }
// Previous implementation retained:             item(key = "typing", contentType = "toast_card") {
// Previous implementation retained: // Previous implementation retained:             Spacer(Modifier.height(16.dp))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:             GbCardContainer(headerText = "Typing Toast") {
// Previous implementation retained: // Previous implementation retained:             ToastTypeCard("typing", "Typing Toast", Icons.Rounded.Keyboard, initiallyExpanded = false) {
// Previous implementation retained: // Previous implementation retained:                 Column {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbLivePreviewCard(height = 70.dp) {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         Box(Modifier.fillMaxWidth().fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "Preview: typing toast" }, contentAlignment = Alignment.Center) {
// Previous implementation retained: // Previous implementation retained:                     ToastPreviewFrame {
// Previous implementation retained: // Previous implementation retained:                         Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Preview: typing toast" }, contentAlignment = Alignment.Center) {
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview("$SAMPLE_NAME Typing...", p.typingWithProfilePic, p.typingElevation, typingRadius, p.typingBgColor, p.typingTextColor, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview(SAMPLE_TYPING, p.typingWithProfilePic, p.typingElevation, typingRadius, p.typingBgColor, p.typingTextColor, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                             ToastPreview("$SAMPLE_NAME Typing...", typingCard.avatar, typingCard.elevated, typingRadius, typingCard.background, typingCard.foreground, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained:                             ToastPreview(SAMPLE_TYPING, typingCard.avatar, typingCard.elevated, typingRadius, typingCard.background, typingCard.foreground, Color(0xFF1E2630))
// Previous implementation retained: // Previous implementation retained:                         }
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.Keyboard, title = "Typing toast", subtitle = "Show a toast when someone starts typing to you. Contacts aren't told typing triggers this toast.", value = p.typingToast,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.Keyboard, title = "Show typing toasts", subtitle = "Show a toast when someone starts typing to you. Contacts aren't told typing triggers this toast.", value = p.typingToast,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastEnabled = it)) },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.Keyboard, title = "Typing toast", subtitle = "Show a toast when someone starts typing to you. Contacts aren't told typing triggers this toast.", value = typingCard.enabled,
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.Keyboard, title = "Show typing toasts", subtitle = "Show a toast when someone starts typing to you. Contacts aren't told typing triggers this toast.", value = typingCard.enabled,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.typing) { it.copy(enabled = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to typing alert", value = p.typingWithProfilePic,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastWithProfilePicture = it)) },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to typing alert", value = typingCard.avatar,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.typing) { it.copy(avatar = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSwitchTile(
// Previous implementation retained: // Previous implementation retained:                     ToastSwitchTile(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = p.typingElevation,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastElevation = it)) },
// Previous implementation retained: // Previous implementation retained:                         icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = typingCard.elevated,
// Previous implementation retained: // Previous implementation retained:                         onChanged = { next -> updateCard(ToastCardType.typing) { it.copy(elevated = next) } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     ToastAdvancedSettings {
// Previous implementation retained: // Previous implementation retained:                     ToastPaletteButtons { bg, fg ->
// Previous implementation retained: // Previous implementation retained:                         val latest = preferences.toasts
// Previous implementation retained: // Previous implementation retained:                         preferences.updateToasts(latest.copy(typingToastBgColor = bg, typingToastTextColor = fg))
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained:                     // NTF-R4-045: separate behavior switches from appearance controls.
// Previous implementation retained: // Previous implementation retained:                     Spacer(Modifier.height(8.dp))
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSliderTile(
// Previous implementation retained: // Previous implementation retained:                     ToastRadiusSlider(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${typingRadius.toInt()}dp", value = typingRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius (0-30dp) · now ${typingRadius.toInt()}dp", value = typingRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${typingRadius.toInt()}dp", value = typingRadius.toFloat(), min = 0f, max = 30f, divisions = 15, unit = "dp",
// Previous implementation retained: // Previous implementation retained:                         title = "Radius of toast notifications", subtitle = "Rounded corners radius (0-30dp) · now ${typingRadius.toInt()}dp", value = typingRadius.toFloat(), min = 0f, max = 30f, divisions = 15, unit = "dp",
// Previous implementation retained: // Previous implementation retained:                         onChanged = { typingRadiusDrag = it },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     ToastRadiusInput("Typing", typingRadius) { typingRadiusDrag = it }
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.typingPosition, hasSubScreen = true,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { showPositionModal(p.typingPosition) { v -> preferences.updateToasts(preferences.toasts.copy(typingToastPosition = v)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = typingCard.position, hasSubScreen = true,
// Previous implementation retained: // Previous implementation retained:                         onTap = { showPositionModal(typingCard.position) { v -> updateCard(ToastCardType.typing) { it.copy(position = v) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(p.typingBgColor, "Default"), colorValue = p.typingBgColor,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Typing Toast Background Color", p.typingBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastBgColor = c)) } },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Typing Toast Background Color", p.typingBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastBgColor = c)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(typingCard.background, "Default"), colorValue = typingCard.background,
// Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Typing Toast Background Color", typingCard.background) { c -> updateCard(ToastCardType.typing) { it.copy(background = c) } } },
// Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Typing Toast Background Color", typingCard.background) { c -> updateCard(ToastCardType.typing) { it.copy(background = c) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                     GbSettingRow(
// Previous implementation retained: // Previous implementation retained:                     ToastSettingRow(
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(p.typingTextColor, "Auto (readable on background)"), colorValue = p.typingTextColor,
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Typing Toast Text Color", p.typingTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastTextColor = c)) } },
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Typing Toast Text Color", p.typingTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastTextColor = c)) } },
// Previous implementation retained: // Previous implementation retained:                         icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(typingCard.foreground, "Auto (readable on background)"), colorValue = typingCard.foreground,
// Previous implementation retained: // Previous implementation retained:                         onTap = { pickColor("Typing Toast Text Color", typingCard.foreground) { c -> updateCard(ToastCardType.typing) { it.copy(foreground = c) } } },
// Previous implementation retained: // Previous implementation retained:                         onColorTap = { pickColor("Typing Toast Text Color", typingCard.foreground) { c -> updateCard(ToastCardType.typing) { it.copy(foreground = c) } } },
// Previous implementation retained: // Previous implementation retained:                     )
// Previous implementation retained: // Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained:                 }
// Previous implementation retained:                 Spacer(Modifier.height(16.dp))
// Previous implementation retained:                 ToastCardBody(ToastCardType.typing, typingCard, typingRadius,
// Previous implementation retained:                     onRadiusChange = { typingRadiusDrag = it },
// Previous implementation retained:                     onChanged = { transform -> updateCard(ToastCardType.typing, transform) },
// Previous implementation retained:                     pickColor = ::pickColor, showRingtoneModal = ::showRingtoneModal,
// Previous implementation retained:                     showPositionModal = ::showPositionModal, ringtoneSubtitle = ::ringtoneSubtitle,
// Previous implementation retained:                     colorSubtitle = ::colorSubtitle)
// Previous implementation retained:             }
// Previous implementation retained: // Previous implementation retained:             }
// Previous implementation retained: // Previous implementation retained: // Previous implementation retained:             item(key = "toast_reset", contentType = "toast_reset") {
            item(key = "toast_reset", contentType = "toast_reset", span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(16.dp))
            // NTF-R1-060: restore the whole toasts group to its defaults (constructible defaults model).
// Previous implementation retained:             GbCardContainer {
            GbCardContainer(padding = PaddingValues(vertical = 4.dp), margin = PaddingValues(vertical = 8.dp)) {
// Previous implementation retained:                 GbSettingRow(
                ToastSettingRow(
                    icon = Icons.Rounded.Refresh, title = "Reset toast settings", subtitle = "Restore the default toast appearance and behavior for all four toast types", hasSubScreen = false,
                    onTap = { confirmReset = true },
                )
            }
// Previous implementation retained:             GbSettingRow(
            ToastSettingRow(
                icon = Icons.Rounded.NotificationsNone,
                title = "More notification options in system settings",
                subtitle = "Android controls app notification channels, sounds and lock-screen options",
                hasSubScreen = true,
                onTap = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
                },
            )
            // NTF-R3-044: keep the last card clear of the system gesture bar.
            Spacer(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).height(24.dp))
            }
        }
    }
    SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    positionPreview?.let { sample ->
        // NTF-R2-041: sample stays in-app; never posts a real notification or reveals contact data.
        val alignment = when (sample.position) {
            TOAST_POSITIONS[1] -> Alignment.Center
            TOAST_POSITIONS[2] -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }
        Surface(
            modifier = Modifier.align(alignment).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 72.dp)
                .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Sample toast: ${sample.position}" },
            color = Color(config.surfaceColor),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 3.dp,
        ) { Text("Sample toast", modifier = Modifier.padding(16.dp), color = Color(config.primaryTextColor)) }
    }
    }
    }
    positionRequest?.let { request ->
        ChatySingleChoiceModal(
            title = "Position of Toast",
            value = request.current,
            options = TOAST_POSITIONS,
            labelBuilder = { it },
            descriptionBuilder = { position -> when (position) {
                TOAST_POSITIONS[0] -> "Appears near the top of the screen"
                TOAST_POSITIONS[1] -> "Appears in the middle of the screen"
                else -> "Appears near the bottom of the screen"
            } },
            onApply = { selected ->
                positionRequest = null
                request.apply(selected)
                positionPreview = ToastPositionPreview(selected)
            },
            onDismiss = { positionRequest = null },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset toast settings?") },
            text = { Text("This restores the default appearance and behavior for all four toast types.") },
            confirmButton = {
                TextButton(onClick = {
                    // Review fix: Reset drops pending radius drags and any pending Undo (job + snackbar) so a delayed
                    // commit or Undo cannot overwrite the defaults, and is ignored if the account scope changed.
                    onlineRadiusDrag = null; storyRadiusDrag = null; profileRadiusDrag = null; typingRadiusDrag = null
                    undoAppearanceJob?.cancel(); undoAppearanceJob = null
                    snackbarHost.currentSnackbarData?.dismiss()
                    if (scopeIsCurrent()) preferences.updateToasts(com.example.chat.domain.models.NotificationToastPreferences())
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    pickers.Host()
}

/** Failed local writes remain visibly pending until Retry/new save succeeds. No silent success or destructive rollback. */
@Composable
internal fun ToastSaveFailureRow(state: com.example.chat.core.preferences.SaveState, onRetry: () -> Unit) {
    if (state !is com.example.chat.core.preferences.SaveState.Failed) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Changes aren't saved on this device yet. Try again before leaving.",
            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
    }
}
