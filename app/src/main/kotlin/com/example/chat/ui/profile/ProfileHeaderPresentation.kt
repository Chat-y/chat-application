package com.example.chat.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chat.ui.navigation.ReferenceLayoutMetrics as Metrics

internal fun profileBannerHeight(width: Dp): Dp = if (width > Metrics.profileBannerWideBreakpointDp.dp)
    Metrics.profileBannerWideHeightDp.dp else width / Metrics.profileBannerAspect

@Composable
internal fun profileBannerShape() = RoundedCornerShape(
    com.example.chat.ui.theme.LocalThemeConfig.current.cornerRadius.toFloat().coerceAtLeast(0f).dp,
)
