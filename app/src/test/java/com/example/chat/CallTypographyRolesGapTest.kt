package com.example.chat

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.example.chat.ui.calls.callTypographyRoles
import org.junit.Assert.assertEquals
import org.junit.Test

/** NOT RUN. Role selection only, not bundle layout/font-scale screenshot coverage. */
class CallTypographyRolesGapTest {
    @Test fun rolesFollowSuppliedThemeInsteadOfBundleFontLiterals() {
        val typography = Typography(headlineSmall = TextStyle(fontSize = 31.sp),
            bodyMedium = TextStyle(fontSize = 19.sp), labelMedium = TextStyle(fontSize = 15.sp))
        val roles = callTypographyRoles(typography)
        assertEquals(typography.headlineSmall, roles.caller)
        assertEquals(typography.bodyMedium, roles.status)
        assertEquals(typography.labelMedium, roles.control)
    }
}
