package com.example.chat.ui.chats.home

/** Port of the template-model types the home screen consumes
 *  (lib/ui/core/templates/template_models.dart). The full TemplateController
 *  port is a settings-slice concern; until it lands the home screen reads
 *  these through [HomeTemplateProvider], which defaults to the Dart
 *  registry's default template (template_registry.dart lines 28-34:
 *  comfortable / circle / presence badge on / no divider / 76dp rows). */

enum class HomeHeaderStyle { compact, prominentIdentity, searchForward, storiesFirst }

enum class ChatListDensity { compact, regular, comfortable }

data class ChatListTemplate(
    val density: ChatListDensity,
    val avatarShape: String,
    val showPresenceBadge: Boolean = true,
    val showDivider: Boolean = true,
    val itemHeight: Double = 72.0,
    val referenceDefault: Boolean = false,
    // Presentation-only baseline flag; explicit template/row choices default to false.
    val compactDefault: Boolean = false,
)

data class HomeTemplates(
    val headerStyle: HomeHeaderStyle,
    val chatList: ChatListTemplate,
)

/** Dart default template ("Chaty Classic" registry entry). */
val defaultHomeTemplates = HomeTemplates(
    headerStyle = HomeHeaderStyle.prominentIdentity,
    chatList = ChatListTemplate(
        // Previous default density: ChatListDensity.comfortable
        density = ChatListDensity.regular,
        avatarShape = "circle",
        showPresenceBadge = true,
        showDivider = false,
        // Previous default minimum: 76.0
        itemHeight = com.example.chat.ui.navigation.ReferenceLayoutMetrics.homeChatRowMinHeightDp.toDouble(),
        compactDefault = true,
    ),
)

typealias HomeTemplateProvider = () -> HomeTemplates

/** chats_home_screen.dart: effectiveDensity multiplies the tile density by
 *  the chat-list template's density factor. */
fun chatListDensityFactor(density: ChatListDensity): Double = when (density) {
    ChatListDensity.compact -> 0.92
    ChatListDensity.regular -> 1.0
    ChatListDensity.comfortable -> 1.08
}

/** Supplied Default reference geometry; selected only at the uncustomized row baseline. */
val referenceDefaultHomeTemplates = defaultHomeTemplates.copy(chatList = defaultHomeTemplates.chatList.copy(
    // Previous reference minimum: 72.0
    density = ChatListDensity.regular, showDivider = true,
    itemHeight = com.example.chat.ui.navigation.ReferenceLayoutMetrics.homeChatRowMinHeightDp.toDouble(), referenceDefault = true,
))
