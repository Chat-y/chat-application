package com.example.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source contracts, not Android PiP/notification behavior or visual proof. */
class CallContinuityWiringTest {
    private val root get() = if (File("app/src/main").exists()) File("app/src/main") else File("src/main")
    private fun text(path: String) = File(root,path).readText()
    @Test fun homeAndBackRequestImmediatePipButNotificationExpands() {
        val main = text("kotlin/com/example/chat/MainActivity.kt")
        assertTrue(main.substringAfter("override fun onUserLeaveHint()").substringBefore("override fun onCreate").contains("CallPipActivity.launch(this)"))
        val pip = text("kotlin/com/example/chat/CallPipActivity.kt")
        assertTrue(pip.substringAfter("fun launch(context: Context)").contains("intentFor(context, enter = true)"))
        assertTrue(pip.contains("putExtra(EXTRA_ENTER, enter)"))
        assertTrue(pip.contains("enterWhenReady = !compact && intent.getBooleanExtra(EXTRA_ENTER, false)"))
        assertTrue(pip.contains("if (enterWhenReady) requestPip()"))
        val fgs = text("java/com/example/chat/data/services/CallForegroundService.kt")
        assertTrue(fgs.contains("CallPipActivity.intentFor(this)"))
    }
    @Test fun dedicatedPipNeverReplacesMainNavigationOrHostsAppRoutes() {
        val main = text("kotlin/com/example/chat/MainActivity.kt")
        assertTrue(main.contains("AppRoot()")); assertFalse(main.contains("pipRequest"))
        val pip = text("kotlin/com/example/chat/CallPipActivity.kt")
        assertFalse(pip.contains("AppRoot(")); assertFalse(pip.contains("MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR"))
        assertTrue(pip.contains("CallPipSessionPolicy.allows("))
        assertTrue(pip.contains("finishAndRemoveTask()"))
        assertFalse(pip.contains("CallForegroundService.sync")); assertFalse(pip.contains("createMediaTransport"))
        assertFalse(pip.contains("acceptCall(")); assertFalse(pip.contains("initiateCall("))
        assertTrue(pip.contains("FLAG_ACTIVITY_REORDER_TO_FRONT"))
        assertTrue(pip.contains("android.settings.PICTURE_IN_PICTURE_SETTINGS"))
        val surface = text("kotlin/com/example/chat/ui/core/components/calls/CallOnlySurface.kt")
        assertTrue(surface.contains("background(Color.Black)"))
        assertFalse(surface.contains("AppRoot("))
        val manifest = text("AndroidManifest.xml").substringAfter("android:name=\".CallPipActivity\"").substringBefore("/>")
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("taskAffinity=")); assertTrue(manifest.contains("supportsPictureInPicture=\"true\""))
    }
    @Test fun backDoesNotHangupAndTaskRemovalKeepsLiveForegroundCall() {
        val host = text("kotlin/com/example/chat/ui/calls/GlobalCallHost.kt")
        assertTrue(host.contains("BackHandler(enabled = session.isActive && presentation.isFullScreen)"))
        assertTrue(host.contains("CallPipActivity.launch(hostContext)"))
        assertTrue(host.contains("presentation.minimizeToPipOrIsland()"))
        val activity = text("kotlin/com/example/chat/MainActivity.kt")
        assertTrue(activity.contains("isFinishing && !isChangingConfigurations && !liveCall"))
        val fgs = text("java/com/example/chat/data/services/CallForegroundService.kt")
        assertTrue(fgs.contains("if (!CallForegroundPolicy.shouldRunFor(session)) stopSelf()"))
        assertTrue(fgs.contains("ChatyNotificationPoster.addOngoingCallActions"))
        val manifest = text("AndroidManifest.xml").substringAfter("android:name=\"com.example.chat.data.services.CallForegroundService\"").substringBefore("/>")
        assertTrue(manifest.contains("stopWithTask=\"false\""))
    }
    @Test fun backgroundSignalingExceptionClosesWhenCallEnds() {
        val api = text("kotlin/com/example/chat/data/api/ApiBackendService.kt")
        assertTrue(api.contains("token = { if (syncForeground || isCallActive()) session.token else null }"))
        assertTrue(api.contains("if (!isCallActive()) realtime.disconnect()"))
        assertTrue(api.contains("if (syncForeground || active) realtime.connect() else realtime.disconnect()"))
        assertTrue(text("kotlin/com/example/chat/ServiceLocator.kt").contains("svc.addStateListener { apiBackend.onCallSessionStateChanged() }"))
    }
    @Test fun phonePickerIsReusableReadOnlyAndSmsUiIsHidden() {
        val phone = text("kotlin/com/example/chat/ui/core/components/auth/PhoneNumberEntry.kt")
        assertTrue(phone.contains("onValueChange = { }, readOnly = true"))
        assertFalse(phone.contains("Enter it manually"))
        assertFalse(phone.contains("Locale.getDefault"))
        assertTrue(phone.contains("simCountryIso")); assertTrue(phone.contains("networkCountryIso"))
        assertTrue(text("kotlin/com/example/chat/ui/auth/RegisterScreen.kt").contains("private const val SMS_VERIFICATION_VISIBLE = false"))
        assertTrue(text("kotlin/com/example/chat/ui/auth/AuthComponents.kt").contains("readOnly: Boolean = false"))
    }
}
