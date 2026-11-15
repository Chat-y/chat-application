package com.example.chat.ui.core.components.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.chat.ui.calls.callSurface
import com.example.chat.ui.calls.callForeground

@Composable
fun CallControlButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    inactiveBackground: Color = callSurface(),
    icon: @Composable (tint: Color) -> Unit,
) {
    val tint = if (active) com.example.chat.ui.theme.LocalAppColors.current.onAccent else callForeground()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .background(
                if (active) activeColor.copy(alpha = 0.28f) else inactiveBackground,
                CircleShape,
            )
            .clickable(enabled = enabled) { onClick() },
    ) {
        icon(tint)
    }
}

