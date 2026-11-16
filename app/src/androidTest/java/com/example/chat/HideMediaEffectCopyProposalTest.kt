package com.example.chat

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** HM4-48 grammatical-form cases, authored only and uncompiled/unrun. */
class HideMediaEffectCopyProposalTest {
    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.let { context ->
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }).resources
    }
    private val labels get() = listOf(R.string.privacy_media_photos_effect,
        R.string.privacy_media_videos_effect, R.string.privacy_media_gifs_effect).map { resources.getString(it) }

    @Test fun allEffectsUseSameActiveSentenceForm() {
        assertTrue(labels.all { it.startsWith("Maintain a private ") && it.endsWith("-folder marker; exports stay visible") })
    }

    @Test fun wordingKeepsExportsVisibleWithoutEncryptionOrDeletionPromise() {
        assertFalse(labels.any { it.contains("encrypt", true) || it.contains("delete", true) || it.contains("cloud", true) })
    }
    // Resource-only regression cases. These do not prove banner placement or gallery behavior.
    @Test fun bannerDescribesMarkersRatherThanGallerySuppression() {
        val banner = resources.getString(R.string.privacy_media_banner_effect)
        assertTrue(banner.contains("private chat-folder markers"))
        assertFalse(banner.contains("out of gallery scans", true))
    }

    @Test fun bannerPreservesExportVisibilityLimit() {
        assertTrue(resources.getString(R.string.privacy_media_banner_effect).contains("exported copies stay visible"))
    }
    @Test fun cloudBackupLimitDoesNotPromiseToStopOrDeleteExportedCopies() {
        val warning = resources.getString(R.string.privacy_media_cloud_backup_limit)
        assertTrue(warning.contains("does not stop gallery or cloud backup of exported copies"))
        assertTrue(warning.contains("gallery or backup service"))
        assertFalse(warning.contains("automatically delete", true))
    }
}
