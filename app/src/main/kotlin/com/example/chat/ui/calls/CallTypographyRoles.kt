package com.example.chat.ui.calls

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

/** The theme owns type scale; bundles keep geometry and their existing weight/alignment. */
data class CallTypographyRoles(val caller: TextStyle, val status: TextStyle, val control: TextStyle)

internal fun callTypographyRoles(typography: Typography): CallTypographyRoles = CallTypographyRoles(
    caller = typography.headlineSmall,
    status = typography.bodyMedium,
    control = typography.labelMedium,
)
