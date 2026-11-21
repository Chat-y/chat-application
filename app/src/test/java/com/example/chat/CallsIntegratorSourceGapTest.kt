package com.example.chat

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Structural regressions only, not TalkBack, Silent-mode or concurrent-call proof. */
class CallsIntegratorSourceGapTest {
    private fun source(relative: String): String =
        listOf(java.io.File("src/main/$relative"), java.io.File("app/src/main/$relative")).first { it.isFile }.readText()
        .lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    @Test fun iosDeclineAndNextcloudCopyMatchRealActions() {
        val body = source("kotlin/com/example/chat/ui/calls/bundles/BundleIncomingCallView.kt")
        assertTrue(body.contains("onConfirmed, onDecline, Modifier.fillMaxWidth()"))
        assertTrue(body.contains("text = \"Swipe up to answer\""))
        assertFalse(body.contains("color = Color.White"))
    }
    @Test fun keypadChecksSystemPolicyAtEachTapAndWaitsForInitiation() {
        val picker = source("kotlin/com/example/chat/ui/calls/CallContactPickerScreen.kt")
        val tap = picker.substringAfter("fun playDialTone(digit: String)").substringBefore("var query")
        assertTrue(tap.contains("Settings.System.getInt"))
        assertTrue(tap.contains("ringerMode"))
        assertTrue(picker.contains("onStartCall: suspend"))
        assertFalse(picker.contains("delay(3000)"))
        val service = source("java/com/example/chat/data/services/CallSignalingService.kt")
        assertTrue(service.contains("outgoingInitiationInFlight.compareAndSet(false, true)"))
        assertTrue(service.contains("finally { outgoingInitiationInFlight.set(false) }"))
    }
}
