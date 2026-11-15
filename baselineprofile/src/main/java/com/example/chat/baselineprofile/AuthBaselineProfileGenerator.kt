package com.example.chat.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Final-list item 23: Baseline Profile for cold start plus the login journey.
 * Generate locally with `./gradlew :app:generateReleaseBaselineProfile` on an
 * API 28+ emulator (connected-device generation needs root below API 33).
 * Navigation only - no backend, no credentials, nothing typed into fields.
 */
@RunWith(AndroidJUnit4::class)
class AuthBaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun coldStartAndAuthScreens() = rule.collect(
        packageName = "com.logybyte.chaty",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Welcome -> Login.
        device.wait(Until.hasObject(By.text("Welcome to Chaty")), 20_000)
        device.findObject(By.text("Log In"))?.click()
        device.wait(Until.hasObject(By.text("Welcome back")), 20_000)

        // Login -> Forgot Password -> back.
        device.findObject(By.text("Forgot Password?"))?.click()
        device.wait(Until.hasObject(By.text("Forgot Password?")), 10_000)
        device.pressBack()
        device.wait(Until.hasObject(By.text("Welcome back")), 10_000)

        // Login -> Register -> back.
        device.findObject(By.text("Register"))?.click()
        device.wait(Until.hasObject(By.text("Everything You Need!")), 20_000)
        device.pressBack()
    }
}
