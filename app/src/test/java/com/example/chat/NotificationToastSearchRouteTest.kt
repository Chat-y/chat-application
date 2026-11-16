package com.example.chat

import com.example.chat.ui.settings.SettingsDestination
import com.example.chat.ui.settings.hostedSettingsDestinations
import com.example.chat.ui.settings.registry.SettingsRegistry
import org.junit.Assert.*
import org.junit.Test

/** Source contracts only. Actual search-tap/back navigation is not device-tested. */
class NotificationToastSearchRouteTest {
    @Test fun registryHasOneDistinctSearchableToastEntry() {
        val entry = SettingsRegistry.allSettings.single { it.id == "notifications_toast_customization" }
        assertEquals("/settings/notifications/toasts", entry.canonicalRoute)
        assertTrue(entry.searchKeywords.contains("toast"))
        assertTrue(entry.searchKeywords.contains("typing toast"))
        assertTrue(hostedSettingsDestinations.contains(SettingsDestination.ToastNotifications))
        assertNotEquals("/settings/notifications", entry.canonicalRoute)
    }
}
