package com.example.chat.ui.settings.conversation

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.core.preferences.LocalPreferencesStorage
import com.example.chat.core.theme.ConversationEntryStyles
import com.example.chat.core.theme.bubbleStyleIdFromString
import com.example.chat.core.theme.deliveryIconStyleFromString
import com.example.chat.domain.models.ConversationPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Instrumented real SharedPreferences/JSON upgrade cases. All UNRUN; no DataStore migration claim.
 * Each test gets a fresh isolated preference filename, never the app's normal settings file. */
class ConversationStoredStyleIdMigrationTest {
    private fun storage(): LocalPreferencesStorage {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = java.util.UUID.randomUUID().toString()
        val isolated = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): java.io.File = java.io.File(target.cacheDir,"conv-style-upgrade-$suffix").apply { mkdirs() }
            override fun getSharedPreferences(name: String,mode: Int): SharedPreferences = super.getSharedPreferences("conv-style-upgrade-$suffix-$name",mode)
        }
        return LocalPreferencesStorage(isolated)
    }
    private fun prefs(blob: Map<String,Any?>): ConversationPreferences {
        @Suppress("UNCHECKED_CAST")
        return ConversationPreferences.fromMap(blob["conversation"] as? Map<String,Any?> ?: emptyMap())
    }
    @Test fun oldBubbleAndTickLabelsSurviveExplicitIdUpgradeAndDiskReload() = runBlocking {
        val s=storage();val legacy=ConversationPreferences(bubbleStyle="Classic",tickStyle="Stock",hyperlinksColor=123L)
        assertTrue(s.savePreferences(mapOf("conversation" to legacy.toMap()),"test-account"))
        val before=prefs(s.loadPreferences("test-account"));val upgraded=upgradeConversationBubbleTickIds(before)
        assertTrue(s.savePreferences(mapOf("conversation" to upgraded.toMap()),"test-account"))
        val after=prefs(s.loadPreferences("test-account"))
        assertEquals(bubbleStyleIdFromString(before.bubbleStyle),bubbleStyleIdFromString(after.bubbleStyle))
        assertEquals(deliveryIconStyleFromString(before.tickStyle),deliveryIconStyleFromString(after.tickStyle))
        assertEquals(123L,after.hyperlinksColor)
    }
    @Test fun everyLegacyEntryNameKeepsLayoutAfterIdUpgradeAndReload() = runBlocking {
        val s=storage()
        for (option in ConversationEntryStyles.options) {
            val old=ConversationPreferences(entryStyle=option.legacyName)
            assertTrue(s.savePreferences(mapOf("conversation" to old.toMap()),"test-account"))
            val next=upgradeConversationEntryId(prefs(s.loadPreferences("test-account")))
            assertTrue(s.savePreferences(mapOf("conversation" to next.toMap()),"test-account"))
            val reloaded=prefs(s.loadPreferences("test-account"))
            assertEquals(option.id,reloaded.entryStyle)
            assertEquals(ConversationEntryStyles.rendererLayout(old.entryStyle),ConversationEntryStyles.rendererLayout(reloaded.entryStyle))
        }
    }
    @Test fun projectionIsIdempotentAndPreservesUnrelatedSettings() {
        val source=ConversationPreferences(entryStyle="Reference 2",bubbleStyle="Classic",tickStyle="Stock",hideContactName=true,sendButtonColor=123L)
        val first=upgradeConversationEntryId(upgradeConversationBubbleTickIds(source))
        assertEquals(first,upgradeConversationEntryId(upgradeConversationBubbleTickIds(first)))
        assertTrue(first.hideContactName);assertEquals(123L,first.sendButtonColor)
    }
    @Test fun upgradingOneAccountNeverChangesTheOtherAccountBlob() = runBlocking {
        val s=storage();val old=ConversationPreferences(entryStyle="Reference 2",bubbleStyle="Classic",tickStyle="Stock")
        val blob=mapOf("conversation" to old.toMap())
        assertTrue(s.savePreferences(blob,"account-a"));assertTrue(s.savePreferences(blob,"account-b"))
        val bBefore=s.loadPreferences("account-b")
        val next=upgradeConversationEntryId(upgradeConversationBubbleTickIds(prefs(s.loadPreferences("account-a"))))
        assertTrue(s.savePreferences(mapOf("conversation" to next.toMap()),"account-a"))
        assertEquals(bBefore,s.loadPreferences("account-b"))
    }
    @Test fun legacySnapshotRemainsReadableForRollback() = runBlocking {
        val s=storage();val old=ConversationPreferences(entryStyle="Reference 2",bubbleStyle="Classic",tickStyle="Stock")
        val oldSnapshot=old.toMap();val upgraded=upgradeConversationEntryId(upgradeConversationBubbleTickIds(old))
        assertTrue(s.savePreferences(mapOf("conversation" to upgraded.toMap()),"test-account"))
        assertTrue(s.savePreferences(mapOf("conversation" to oldSnapshot),"test-account"))
        val restored=prefs(s.loadPreferences("test-account"))
        assertEquals(old.entryStyle,restored.entryStyle);assertEquals(old.bubbleStyle,restored.bubbleStyle);assertEquals(old.tickStyle,restored.tickStyle)
    }
}
