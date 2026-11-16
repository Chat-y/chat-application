package com.example.chat.core.preferences
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real migration/disk source cases, UNRUN. Every test has an isolated legacy file AND DataStore directory. */
class ConversationAppearanceDataStoreMigrationTest {
    private fun isolatedContext(): Context {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val id=java.util.UUID.randomUUID().toString()
        return object:ContextWrapper(target) {
            override fun getApplicationContext(): Context=this
            override fun getFilesDir(): java.io.File=java.io.File(target.cacheDir,"conv-datastore-$id").apply { mkdirs() }
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=super.getSharedPreferences("conv-datastore-$id-$name",mode)
        }
    }
    private fun blob(color:Long)=JSONObject().put("conversation",JSONObject().put("actionBarColor",color).put("hideContactName",true)).put("unrelated",JSONObject().put("keep",true)).toString()
    @Test fun migratesAppearanceWithoutRemovingOrChangingTheLegacyKey()=runBlocking {
        val ctx=isolatedContext();val old=ctx.getSharedPreferences("chaty_local_preferences",0);val bucket=PreferenceKeys.scopedPreferences("a");val original=blob(123L)
        assertTrue(old.edit().putString(bucket,original).commit())
        val result=LocalPreferencesStorage(ctx).loadPreferences("a")
        assertEquals(original,old.getString(bucket,null));assertTrue(result.containsKey("unrelated"))
        val current=ConversationAppearanceDataStore.get(ctx,old,bucket).readConversation()!!
        assertEquals(123L,(current["actionBarColor"] as Number).toLong())
    }
    @Test fun bothFormatsReadTheSameCommittedAppearance()=runBlocking {
        val ctx=isolatedContext();val store=LocalPreferencesStorage(ctx)
        val values=mapOf("conversation" to mapOf("actionBarColor" to 123L,"hyperlinksColor" to 456L,"wallpaperPath" to "unchanged"))
        assertTrue(store.savePreferences(values,"a"))
        val legacy=ctx.getSharedPreferences("chaty_local_preferences",0).getString(PreferenceKeys.scopedPreferences("a"),null)!!
        val ds=ConversationAppearanceDataStore.get(ctx,ctx.getSharedPreferences("chaty_local_preferences",0),PreferenceKeys.scopedPreferences("a")).readConversation()
        assertEquals(decodeConversationJson(conversationJsonFromLegacy(legacy)!!),ds)
        assertEquals("unchanged",(store.loadPreferences("a")["conversation"] as Map<*,*>)["wallpaperPath"])
    }
    @Test fun laterLegacyOnlyRollbackWriteIsImportedInsteadOfIgnored()=runBlocking {
        val ctx=isolatedContext();val store=LocalPreferencesStorage(ctx);val old=ctx.getSharedPreferences("chaty_local_preferences",0);val key=PreferenceKeys.scopedPreferences("a")
        assertTrue(old.edit().putString(key,blob(1L)).commit());store.loadPreferences("a")
        assertTrue(old.edit().putString(key,blob(2L)).commit())
        val conv=store.loadPreferences("a")["conversation"] as Map<*,*>
        assertEquals(2L,(conv["actionBarColor"] as Number).toLong());assertEquals(blob(2L),old.getString(key,null))
    }
    @Test fun accountNamespacesAndSignedOutBucketNeverMix()=runBlocking {
        val ctx=isolatedContext();val s=LocalPreferencesStorage(ctx)
        listOf(null to 1L,"a" to 2L,"b" to 3L).forEach { (account,color)->assertTrue(s.savePreferences(mapOf("conversation" to mapOf("actionBarColor" to color)),account)) }
        listOf(null to 1L,"a" to 2L,"b" to 3L).forEach { (account,color)->assertEquals(color,((s.loadPreferences(account)["conversation"] as Map<*,*>)["actionBarColor"] as Number).toLong()) }
    }
    @Test fun missingLegacyConversationDoesNotResurrectOldDataStoreValues()=runBlocking {
        val ctx=isolatedContext();val s=LocalPreferencesStorage(ctx);val old=ctx.getSharedPreferences("chaty_local_preferences",0);val key=PreferenceKeys.scopedPreferences("a")
        assertTrue(old.edit().putString(key,blob(123L)).commit());s.loadPreferences("a")
        assertTrue(old.edit().putString(key,"{}").commit());assertFalse(s.loadPreferences("a").containsKey("conversation"))
        assertEquals("{}",old.getString(key,null))
    }
}
