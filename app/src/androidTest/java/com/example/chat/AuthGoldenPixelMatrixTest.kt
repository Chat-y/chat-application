package com.example.chat

import android.graphics.Bitmap
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.auth.CreateNewPasswordScreen
import com.example.chat.ui.auth.ForgotPasswordScreen
import com.example.chat.ui.auth.LoginScreen
import com.example.chat.ui.auth.OrgCheckResult
import com.example.chat.ui.auth.OtpPurpose
import com.example.chat.ui.auth.OtpVerificationScreen
import com.example.chat.ui.auth.RegisterScreen
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screenshot matrix (missing-list item 24, final-list item 34): every auth
 * screen x system light/dark x font 1.0/1.3/2.0, captured as PNGs for human
 * review, the same proof pattern as AuthScreensPixelProofTest. Captures are
 * artifacts; assertions only guard blank/flat renders, not pixel goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AuthGoldenPixelMatrixTest {

    @get:Rule val rule = createAndroidComposeRule<ChatFixtureActivity>()

    private val outDir: File
        get() = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/auth-matrix",
        ).apply { mkdirs() }

    private data class CaptureCase(
        val name: String,
        val content: @androidx.compose.runtime.Composable () -> Unit,
    )

    private fun captureBitmap(name: String) {
        rule.waitForIdle()
        Thread.sleep(400) // let transitions settle, same approach as the pixel proof
        val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: throw AssertionError("takeScreenshot returned null for $name")
        FileOutputStream(File(outDir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        // Blank-render guard: a flat single-colour frame means the screen never drew.
        val width = bitmap.width
        val height = bitmap.height
        val samples = IntArray(8)
        var i = 0
        for (y in listOf(height / 8, height / 2, height * 7 / 8)) {
            for (x in listOf(width / 8, width / 2, width * 7 / 8)) {
                if (i < samples.size) samples[i++] = bitmap.getPixel(x, y)
            }
        }
        // Explicit comparison mode never silently creates or approves a missing reference.
        val arguments=InstrumentationRegistry.getArguments()
        if(arguments.getString("compareAuthGoldens")=="true") {
            val path="auth-goldens/$name.png"
            val reference=try { InstrumentationRegistry.getInstrumentation().context.assets.open(path).use {
                android.graphics.BitmapFactory.decodeStream(it)
            } } catch(error:java.io.IOException) { throw AssertionError("Approved reference missing: $path",error) }
            requireNotNull(reference) { "Unreadable golden: $path" }
            assertTrue("Golden dimensions differ for $name",reference.width==width && reference.height==height)
            var changed=0L
            for(y in 0 until height) for(x in 0 until width) {
                val actual=bitmap.getPixel(x,y);val expected=reference.getPixel(x,y)
                if(listOf(0,8,16).any {kotlin.math.abs(((actual ushr it) and 255)-((expected ushr it) and 255))>8}) changed++
            }
            reference.recycle()
            assertTrue("Golden mismatch $name: $changed pixels",changed.toDouble()/(width.toLong()*height)<=0.005)
        }
        bitmap.recycle()
        assertTrue("$name rendered as a flat frame", samples.distinct().size > 1)
    }

    @Test fun captureAuthScreenMatrix() {
        val cases = listOf(
            CaptureCase("login") {
                LoginScreen(
                    onBack = {}, onLoggedIn = {}, onForgotPassword = {},
                    onRegister = {}, onRegisterOrganization = {},
                    login = { _, _, _, _ -> "u" }, loadSavedTenant = { null to true },
                )
            },
            CaptureCase("register") {
                RegisterScreen(
                    onBack = {}, onRegistered = { _, _ -> }, onRegisterOrganization = {},
                    register = { _, _, _, _, _, _ -> "u" to "n" },
                    checkOrganization = { OrgCheckResult("Acme", true) },
                    loadSavedTenant = { null to true },
                )
            },
            CaptureCase("otp") {
                OtpVerificationScreen(
                    email = "bandi@example.com", purpose = OtpPurpose.VerifyEmail,
                    onBack = {}, verify = {}, resend = { "sent" }, onVerified = {},
                    initialResendSeconds = 60,
                )
            },
            CaptureCase("forgot") {
                ForgotPasswordScreen(onBack = {}, sendRecovery = {})
            },
            CaptureCase("new-password") {
                CreateNewPasswordScreen(
                    email = "bandi@example.com", onBack = {},
                    submitNewPassword = {}, showSuccess = {}, onPasswordUpdated = {},
                )
            },
        )
        val themes = listOf("light" to ThemePresets.warmNeutral, "dark" to ThemePresets.warmNeutralDark)
        val fontScales = listOf("1.0" to 1f, "1.3" to 1.3f, "2.0" to 2f)
        for ((themeName, theme) in themes) {
            for ((fontName, fontScale) in fontScales) {
                for (locale in listOf("en","hi","ar","he")) for (case in cases) {
                    rule.setThemedContent(theme, fontScale,locale) { case.content() }
                    // A screen that never composed has no text nodes at all.
                    rule.waitUntil(5_000) {
                        rule.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes().isNotEmpty()
                    }
                    captureBitmap("${case.name}-$themeName-font$fontName-$locale")
                }
            }
        }
    }
}
