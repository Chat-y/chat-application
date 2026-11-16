package com.example.chat.ui.settings.conversation

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.bubbleStyleIdFromString
import com.example.chat.core.theme.deliveryIconStyleFromString
import com.example.chat.domain.models.ConversationPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BubblePreviewTheme(val config: ThemeConfig, val surface: Color, val secondarySurface: Color)
internal data class BubbleAndTicksUiState(val preferences: ConversationPreferences, val preview: BubblePreviewState)

/** The same fallback policy as the previous screen, independent of composition and persistence. */
internal fun resolveBubblePreview(p: ConversationPreferences, theme: BubblePreviewTheme): BubblePreviewState {
    val config = theme.config
    val accent = Color(config.accentColor)
    fun color(value: Long?, fallback: Color) = value?.let(::Color) ?: fallback
    val right = color(p.rightBubbleColor, accent)
    val rightText = color(p.rightChatBubbleTextColor,
        conversationOnColor(right, Color(config.primaryTextColor), Color(config.backgroundColor)))
    return BubblePreviewState(
        bubbleStyle = bubbleStyleIdFromString(p.bubbleStyle), tickStyle = deliveryIconStyleFromString(p.tickStyle),
        convBg = color(p.convBackgroundColor, theme.surface), rightBubbleBg = right,
        rightTextCol = rightText, rightTimeCol = color(p.rightBubbleTimeColor, rightText),
        leftBubbleBg = color(p.leftBubbleColor, theme.secondarySurface),
        leftTextCol = color(p.leftChatBubbleTextColor, Color(config.primaryTextColor)),
        leftTimeCol = color(p.leftBubbleTimeColor, Color(config.secondaryTextColor)),
        quotedBg = color(p.quotedBackgroundColor, theme.surface),
        quotedDivider = color(p.quotedDividerColor, accent), quotedName = color(p.quotedNameColor, accent),
        quotedMsg = color(p.quotedMessageColor, Color(config.secondaryTextColor)),
        deletedIconColor = color(p.deletedMessageIconColor, Color(config.secondaryTextColor)),
        textSize = p.messageTextSize.toFloat().coerceIn(12f, 26f),
        makeTextSelectable = p.makeTextSelectable, removeReadMore = p.removeReadMore,
    )
}

/** UI adapter only. Theme and preference notifications publish one resolved preview state. */
internal class BubbleAndTicksViewModel(
    private val read: () -> ConversationPreferences,
    private val write: (ConversationPreferences) -> Unit,
    private val subscribe: (() -> Unit) -> Unit,
    private val unsubscribe: (() -> Unit) -> Unit,
    initialTheme: BubblePreviewTheme,
    private val transaction: (((ConversationPreferences) -> ConversationPreferences) -> Unit)? = null,
    private val scopeIsCurrent: () -> Boolean = { true },
) : ViewModel() {
    private var theme = initialTheme
    private fun snapshot(): BubbleAndTicksUiState {
        val preferences = selectBubbleAndTicksPreferences(read())
        return BubbleAndTicksUiState(preferences, resolveBubblePreview(preferences, theme))
    }
    private fun update(transform: (ConversationPreferences) -> ConversationPreferences) {
        if (!scopeIsCurrent()) return
        val commit = transaction
        if (commit != null) commit { latest -> if (scopeIsCurrent()) transform(latest) else latest }
        else {
            val latest = read()
            if (scopeIsCurrent()) {
                val next = transform(latest)
                if (scopeIsCurrent()) write(next)
            }
        }
    }
    private val mutableState = MutableStateFlow(snapshot())
    val uiState: StateFlow<BubbleAndTicksUiState> = mutableState.asStateFlow()
    // A retained old model must neither publish nor read the next account.
    private val listener: () -> Unit = { if (scopeIsCurrent()) mutableState.value = snapshot() }
    init { subscribe(listener); listener() }
    fun edit(transform: (ConversationPreferences) -> ConversationPreferences) { update(transform); listener() }
    fun setTheme(value: BubblePreviewTheme) { if (scopeIsCurrent() && theme != value) { theme = value; listener() } }
    override fun onCleared() { unsubscribe(listener); super.onCleared() }
}
