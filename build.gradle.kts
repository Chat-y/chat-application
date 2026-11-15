plugins {
    id("com.android.application") version "8.10.0" apply false
    id("com.android.test") version "8.10.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    // Final-list item 23: Baseline Profile generation for cold start + auth.
    id("androidx.baselineprofile") version "1.3.4" apply false
}
