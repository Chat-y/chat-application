package com.example.chat

import org.junit.Assert.*
import org.junit.Test

/** Source only: not compiled/run by this change. */
class CallsSettingsDeepLinkPolicyTest {
    @Test fun acceptsOnlyCanonicalCallsSettingsLink() {
        val rejected = listOf(
            "chaty://settings/calls?account=other", "chaty://settings/calls#open",
            "chaty://settings/calls/", "chaty://settings/privacy", "https://settings/calls",
            "chaty://user@settings/calls", "chaty://settings:80/calls", "chaty://settings/%63alls",
        )
        try {
            for (uri in rejected) assertFalse(LauncherShortcutRouter.receiveCallsSettingsLink(uri, "u", 1))
            assertTrue(LauncherShortcutRouter.receiveCallsSettingsLink("chaty://settings/calls", "u", 1))
            assertEquals(LauncherShortcutRouter.ACTION_CALL_SETTINGS, LauncherShortcutRouter.pending.value?.action)
            assertTrue(LauncherShortcutRouter.scopeAllows("u", 1))
            assertFalse(LauncherShortcutRouter.scopeAllows("u", 2))
            assertFalse(LauncherShortcutRouter.scopeAllows("other", 1))
        } finally { LauncherShortcutRouter.consume() }
    }
    @Test fun waitsForAuthenticationAndUnlockAndDropsStaleScope() {
        val action = LauncherShortcutRouter.ACTION_CALL_SETTINGS
        assertEquals(LauncherShortcutPolicy.Decision.Wait, LauncherShortcutPolicy.decide(action, true, true, false, false))
        assertEquals(LauncherShortcutPolicy.Decision.Wait, LauncherShortcutPolicy.decide(action, true, true, true, true))
        assertEquals(LauncherShortcutPolicy.Decision.Drop, LauncherShortcutPolicy.decide(action, true, false, true, false))
        assertEquals(LauncherShortcutPolicy.Decision.Drop, LauncherShortcutPolicy.decide(action, false, true, true, false))
        assertEquals(LauncherShortcutPolicy.Decision.OpenCallSettings, LauncherShortcutPolicy.decide(action, true, true, true, false))
    }
}
