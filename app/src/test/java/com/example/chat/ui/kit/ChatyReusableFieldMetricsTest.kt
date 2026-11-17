package com.example.chat.ui.kit

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unrun regression fixtures. Layout policy only, not Android measurement or accessibility proof. */
class ChatyReusableFieldMetricsTest {
    @Test fun existingTaskDefaultsRemain54And104() {
        assertEquals(54.dp, ChatyFieldMetrics.minimumHeight(singleLine = true))
        assertEquals(104.dp, ChatyFieldMetrics.minimumHeight(singleLine = false))
        assertEquals(14.dp, ChatyFieldMetrics.verticalInset(ChatyFieldDensity.Standard))
    }

    @Test fun compactHeaderKeeps48dpTrailingTargetOutsideVerticalInsets() {
        assertEquals(48.dp, ChatyFieldMetrics.minimumHeight(true, ChatyFieldDensity.CompactHeader))
        assertEquals(0.dp, ChatyFieldMetrics.verticalInset(ChatyFieldDensity.CompactHeader))
        assertEquals(14.dp, ChatyFieldMetrics.inset)
    }

    @Test fun multilineAreaStillExpandsInCompactDensity() {
        assertEquals(104.dp, ChatyFieldMetrics.minimumHeight(false, ChatyFieldDensity.CompactHeader))
    }
}
