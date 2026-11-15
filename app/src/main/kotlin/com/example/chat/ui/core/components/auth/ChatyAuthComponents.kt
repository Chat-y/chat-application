package com.example.chat.ui.core.components.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId

/** Shared app UI dimensions. Colors always resolve from the active theme, never a copied palette. */
object ChatyUiTokens {
    val zero = 0.dp
    val space0 = 0.dp
    val space1 = 1.dp
    val space10 = 10.dp
    val space11 = 11.dp
    val space12 = 12.dp
    val space14 = 14.dp
    val space16 = 16.dp
    val space18 = 18.dp
    val space1_5 = 1.5.dp
    val space2 = 2.dp
    val space20 = 20.dp
    val space22 = 22.dp
    val space24 = 24.dp
    val space2_2 = 2.2.dp
    val space3 = 3.dp
    val space4 = 4.dp
    val space32 = 32.dp
    val space40 = 40.dp
    val space48 = 48.dp
    val space52 = 52.dp
    val space56 = 56.dp
    val space6 = 6.dp
    /** Legacy geometry alias, not layout spacing. New spacing uses8dp grid. */
    val space7 = 7.dp
    val space76 = 76.dp
    val space8 = 8.dp
    val text0_2 = 0.2.sp
    val text11_5 = 11.5.sp
    val text12 = 12.sp
    val text12_5 = 12.5.sp
    val text13 = 13.sp
    val text13_5 = 13.5.sp
    val text14 = 14.sp
    val text15 = 15.sp
    val text24 = 24.sp
    val text30 = 30.sp
    val text16 = 16.sp
    val text16_2 = 16.2.sp
    val text28 = 28.sp
    val text26 = 26.sp
    val text19_6 = 19.6.sp
    val space28 = 28.dp
    val text22 = 22.sp
    val spacingXs = 4.dp
    val spacingSm = 8.dp
    val spacingMd = 16.dp
    val spacingLg = 24.dp
    val spacingXl = 32.dp
    val spacingXxl = 40.dp
    val tapTarget = 48.dp
    val controlHeight = 56.dp
    val controlInset = 3.dp
    val border = 1.dp
    val icon = 20.dp
    val inlineMaxWidth = 136.dp
    val inlineText = 11.sp
    val labelText = 13.sp
    val linkText = 13.5.sp
    val minFittedText = 7.sp
    val compactScaleCap = 1.15f
    val shape = CircleShape
    val touchGap = 8.dp
    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    val controlElevation = 2.dp
    val contentMaxWidth get() = com.example.chat.ui.kit.ChatyAdaptiveLogic.AUTH_COLUMN_MAX_DP.dp
}

@Composable
fun ChatyAuthScreen(theme: ThemeConfig, content: @Composable ColumnScope.() -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val density = LocalDensity.current
    AuthInputPrivacy {
    Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val accent = Color(theme.accentColor)
                drawCircle(accent.copy(alpha=0.075f),size.width*0.23f,androidx.compose.ui.geometry.Offset(-size.width*0.1f,size.height*0.08f))
                drawCircle(accent.copy(alpha=0.09f),size.width*0.27f,androidx.compose.ui.geometry.Offset(size.width*1.06f,size.height*0.01f))
                drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(accent.copy(alpha=0.12f),Color.Transparent),
                    center=androidx.compose.ui.geometry.Offset(size.width,size.height),radius=size.width*0.6f))
            }
            val wide = com.example.chat.ui.auth.AuthPresentationPolicy.twoPane(maxWidth.value, maxHeight.value, density.fontScale)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                if (wide) Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(ChatyUiTokens.space24), verticalArrangement = Arrangement.Center) {
                    com.example.chat.ui.auth.AuthIllustration("welcome", theme)
                    Text(authCopy("Conversations, together."), color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor), fontSize = ChatyUiTokens.text26)
                    Text(authCopy("Chat and call in your own workspace."), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
                }
                Column(Modifier.widthIn(max = ChatyUiTokens.contentMaxWidth).fillMaxSize().semantics {isTraversalGroup=true}
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ChatyUiTokens.space24, vertical = ChatyUiTokens.space16)
                    .padding(bottom = ChatyUiTokens.space24)) {
                    content()
                    com.example.chat.ui.auth.AuthLocalLegalFooter(theme)
                }
            }
        }
    }
}
}

/** Measure at the actual available width, then choose one size before drawing. Never ellipsize or stack. */
@Composable
internal fun FittedAuthText(text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Medium) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val available = with(density) { maxWidth.toPx() }
        val base = fontSize.value * density.fontScale.coerceAtMost(ChatyUiTokens.compactScaleCap) / density.fontScale
        var size = base
        repeat(16) {
            if (measurer.measure(AnnotatedString(text), style = TextStyle(fontSize = size.sp, fontWeight = weight), softWrap = false).size.width > available && size > ChatyUiTokens.minFittedText.value / density.fontScale)
                size *= 0.9f
        }
        Text(text, color = color, fontSize = size.sp, fontWeight = weight, softWrap = false, maxLines = 1)
    }
}

@Composable
fun ChatyModeToggle(theme: ThemeConfig, useOrganization: Boolean, onSelect: (Boolean) -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
    BoxWithConstraints(Modifier.fillMaxWidth().clip(ChatyUiTokens.shape)
        .background(Color(theme.surfaceColor), ChatyUiTokens.shape)
        .border(ChatyUiTokens.border, Color(theme.secondaryTextColor).copy(alpha = 0.2f), ChatyUiTokens.shape)
        .padding(ChatyUiTokens.controlInset).selectableGroup()) {
        val offset by androidx.compose.animation.core.animateDpAsState(
            if (useOrganization) maxWidth / 2 else ChatyUiTokens.zero,
            androidx.compose.animation.core.tween(if (reduced) 0 else com.example.chat.ui.kit.ChatyMotion.fastMs,
                easing = com.example.chat.ui.kit.ChatyMotion.standard), label = authCopy("auth-mode-slide"))
        Box(Modifier.matchParentSize()) {
            Box(Modifier.offset(x = offset).fillMaxWidth(0.5f).fillMaxHeight().background(Color(theme.accentColor), ChatyUiTokens.shape))
        }
        Row(Modifier.fillMaxWidth()) {
            listOf(false to "Normal", true to "Organization").forEach { (organization, label) ->
                val selected = organization == useOrganization
                val tint = Color(com.example.chat.ui.auth.AuthPresentationPolicy.readableText(
                    (if (selected) theme.onAccentColor else theme.secondaryTextColor).toInt(),
                    (if (selected) theme.accentColor else theme.surfaceColor).toInt()))
                Row(Modifier.weight(1f).heightIn(min = ChatyUiTokens.tapTarget)
                    .padding(horizontal=ChatyUiTokens.touchGap / 2).selectable(selected, role = Role.Tab, onClick = { onSelect(organization) })
                    .padding(horizontal = ChatyUiTokens.space8, vertical = ChatyUiTokens.space8),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    ChatyIcon(if (organization) ChatyIconId.workspace else ChatyIconId.profile, color = tint, size = ChatyUiTokens.icon)
                    Spacer(Modifier.width(ChatyUiTokens.space8))
                    FittedAuthText(authCopy(label), tint, ChatyUiTokens.labelText, Modifier.weight(1f), if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun ChatyBackButton(onClick: () -> Unit, theme: ThemeConfig, label: String = "Back") {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    TextButton(onClick, Modifier.padding(vertical=ChatyUiTokens.touchGap / 2).heightIn(min = ChatyUiTokens.tapTarget), colors = ButtonDefaults.textButtonColors(contentColor = com.example.chat.ui.auth.authReadable(theme,theme.secondaryTextColor))) {
        ChatyIcon(ChatyIconId.back, color = com.example.chat.ui.auth.authReadable(theme, theme.accentColor), size = ChatyUiTokens.icon)
        Spacer(Modifier.width(ChatyUiTokens.space8))
        Text(authCopy(label))
    }
}

@Composable
fun ChatyAuthLink(text: String, theme: ThemeConfig, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    TextButton(onClick, modifier.padding(vertical = ChatyUiTokens.touchGap / 2).heightIn(min = ChatyUiTokens.tapTarget), enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = com.example.chat.ui.auth.authReadable(theme,theme.accentColor))) {
        Text(authCopy(text), fontSize = ChatyUiTokens.linkText, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChatyInlineAction(text: String, theme: ThemeConfig, onClick: () -> Unit, enabled: Boolean = true) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    TextButton(onClick, Modifier.padding(vertical=ChatyUiTokens.touchGap / 2).widthIn(max = ChatyUiTokens.inlineMaxWidth).heightIn(min = ChatyUiTokens.tapTarget), enabled = enabled,
        contentPadding = PaddingValues(horizontal = ChatyUiTokens.space8), colors = ButtonDefaults.textButtonColors(contentColor = com.example.chat.ui.auth.authReadable(theme,theme.accentColor))) {
        FittedAuthText(authCopy(text), Color(theme.accentColor), ChatyUiTokens.inlineText, weight = FontWeight.SemiBold)
    }
}

/** Shared reference-layout heading, using the selected app theme. */
@Composable
fun ChatyAuthReferenceHeading(title: String, accentWord: String, subtitle: String, theme: ThemeConfig, logo: Boolean = false) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (logo) Alignment.CenterHorizontally else Alignment.Start) {
        if (logo) {
            ChatyAppAuthIcon()
            Spacer(Modifier.height(ChatyUiTokens.space16))
        }
        val start = title.lastIndexOf(accentWord)
        val label = androidx.compose.ui.text.buildAnnotatedString {
            if (start < 0) append(title) else {
                append(title.substring(0, start))
                pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(theme.accentColor)))
                append(title.substring(start)); pop()
            }
        }
        Text(label, Modifier.fillMaxWidth().semantics { heading() },
            color = com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor),
            fontSize = if (logo) ChatyUiTokens.text30 else ChatyUiTokens.text24, fontWeight = FontWeight.ExtraBold,
            textAlign = if (logo) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start)
        Spacer(Modifier.height(ChatyUiTokens.touchGap))
        Text(subtitle, Modifier.fillMaxWidth(), color = com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor),
            fontSize = ChatyUiTokens.text14,
            textAlign = if (logo) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start)
    }
}

/** A credential chooser action styled as the reference's hollow circle, not a persisted preference. */
@Composable
fun ChatySavedPasswordAction(theme:ThemeConfig,onClick:()->Unit,enabled:Boolean=true) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    TextButton(onClick=onClick,enabled=enabled,contentPadding=PaddingValues(ChatyUiTokens.space0),modifier=Modifier.heightIn(min=ChatyUiTokens.tapTarget)) {
        ChatyIcon(ChatyIconId.radioOff,color=com.example.chat.ui.auth.authReadable(theme,theme.secondaryTextColor),size=ChatyUiTokens.space20)
        Spacer(Modifier.width(ChatyUiTokens.space8))
        Text(authCopy("Use a saved password"),color=com.example.chat.ui.auth.authReadable(theme,theme.secondaryTextColor),fontSize=ChatyUiTokens.text12)
    }
}

/** The app's bundled launcher artwork, not a redrawn placeholder logo. */
@Composable
fun ChatyAppAuthIcon() {
    androidx.compose.foundation.Image(
        painter=androidx.compose.ui.res.painterResource(com.example.chat.R.drawable.ic_launcher),
        contentDescription="Chaty",modifier=Modifier.size(ChatyUiTokens.space76).clip(ChatyUiTokens.cardShape))
}
