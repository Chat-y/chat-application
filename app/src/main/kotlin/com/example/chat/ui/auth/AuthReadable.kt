package com.example.chat.ui.auth

import androidx.compose.ui.graphics.Color
import com.example.chat.core.theme.ThemeConfig

/** Opaque foreground fallback on the actual auth surface. Does not rewrite a saved theme. */
fun authReadable(theme:ThemeConfig,preferred:Long,background:Long=theme.backgroundColor):Color =
    Color(AuthPresentationPolicy.readableText(preferred.toInt(),background.toInt()))
