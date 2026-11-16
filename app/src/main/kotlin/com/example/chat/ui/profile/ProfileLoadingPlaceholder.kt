package com.example.chat.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.example.chat.R
import com.example.chat.ui.theme.LocalAppColors

/** No fake identity or counts, no animation, and no dependency on a particular header template. */
@Composable
fun ProfileLoadingPlaceholder() {
    val colors = LocalAppColors.current
    val loadingLabel = stringResource(R.string.profile_loading)
    Box(
        Modifier.fillMaxSize().background(colors.background)
            .clearAndSetSemantics {
                contentDescription = loadingLabel
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(88.dp).background(colors.borderSubtle, CircleShape))
            Box(Modifier.fillMaxWidth(0.55f).height(20.dp).background(colors.borderSubtle, RoundedCornerShape(8.dp)))
            Box(Modifier.fillMaxWidth(0.35f).height(14.dp).background(colors.borderSubtle, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(16.dp))
            repeat(3) {
                Box(Modifier.fillMaxWidth().height(56.dp).background(colors.surface, RoundedCornerShape(12.dp)))
            }
        }
    }
}
