package com.example.chat

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Source regression check only. Does not assert Android flag/pixels/device behavior. */
class CallPrivacySourceContractGapTest {
    private fun source(relative: String): String {
        val paths = listOf(java.io.File("src/main/$relative"), java.io.File("app/src/main/$relative"))
        return paths.first { it.isFile }.readText()
    }
    @Test fun callSurfacesRemainUnderRootOptInWindowProtection() {
        val root = source("kotlin/com/example/chat/ui/AppRoot.kt")
        assertTrue(Regex("ProtectSensitiveWindow\\s*\\(\\s*ServiceLocator\\.preferencesController\\.security\\.protectFromScreenshots").containsMatchIn(root))
        assertTrue(root.contains("com.example.chat.ui.calls.CallsScreen("))
        assertTrue(root.contains("com.example.chat.ui.calls.CallContactPickerScreen("))
        val flag = source("kotlin/com/example/chat/ui/kit/ProtectSensitiveWindow.kt")
        assertTrue(flag.contains("window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)"))
        assertTrue(flag.contains("if (!lease.alreadySecure) window.clearFlags"))
        val pip = source("kotlin/com/example/chat/CallPipActivity.kt")
        assertTrue(pip.contains("com.example.chat.ui.kit.ProtectSensitiveWindow("))
        assertTrue(pip.contains("ServiceLocator.preferencesController.security.protectFromScreenshots"))
        assertTrue(pip.contains("preferencesController.addListener(preferencesChanged)"))
        assertTrue(pip.contains("preferencesController.removeListener(preferencesChanged)"))
    }
    @Test fun clearCopyDoesNotPromiseOtherDevicesOrPermanentServerErasure() {
        val resources = source("res/values/strings_calls_gap.xml")
        val clearCopy = Regex("<string name=\"calls_ui_22\">(.*?)</string>").findAll(resources).last().groupValues[1]
        assertTrue(clearCopy.contains("sends a request"))
        assertTrue(clearCopy.contains("cannot confirm server retention or deletion on your other devices"))
    }
}
