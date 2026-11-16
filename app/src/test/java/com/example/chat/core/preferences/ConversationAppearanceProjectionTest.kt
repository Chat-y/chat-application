package com.example.chat.core.preferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class ConversationAppearanceProjectionTest {
    @Test fun onlyAppearanceFieldsLeaveTheLegacyBlob() {
        val raw=JSONObject().put("security",JSONObject().put("other","retained-only-in-legacy"))
            .put("conversation",JSONObject().put("actionBarColor",123L).put("hideContactName",true).put("wallpaperPath","local-only").put("unknown","not copied"))
        val projected=decodeConversationJson(conversationJsonFromLegacy(raw.toString())!!)!!
        assertEquals(setOf("actionBarColor","hideContactName"),projected.keys)
    }
    @Test fun allTwelveScalarColoursAndFourVisibilityFlagsAreEligible() {
        val colors=listOf("actionBarColor","contactStatusBgColor","contactStatusTextColor","emojiHeaderColor","emojiHeaderIconsColor","emojiPickerBgColor","hyperlinksColor","infoBalloonsTextColor","infoBalloonsBgColor","groupParticipantNameColor","voiceNotePlayingBarColor","voiceNotePlayButtonColor")
        val flags=listOf("hideProfilePicture","hideContactName","hideCallButton","disableContactStatus")
        val conv=JSONObject();colors.forEach { conv.put(it,123L) };flags.forEach { conv.put(it,true) }
        val projected=decodeConversationJson(conversationJsonFromLegacy(JSONObject().put("conversation",conv).toString())!!)!!
        assertEquals((colors+flags).toSet(),projected.keys)
    }
    @Test fun nullColourIsPreservedAndBadValueIsNotCopied() {
        val c=JSONObject().put("actionBarColor",JSONObject.NULL).put("hyperlinksColor","not-a-color")
        val v=decodeConversationJson(conversationJsonFromLegacy(JSONObject().put("conversation",c).toString())!!)!!
        assertTrue(v.containsKey("actionBarColor"));assertNull(v["actionBarColor"]);assertFalse(v.containsKey("hyperlinksColor"))
    }
    @Test fun groupedEmojiProjectionCopiesOnlyItsThreeColourFields() {
        val style=JSONObject().put("header",1L).put("icons",JSONObject.NULL).put("background",2L).put("unknown","not copied")
        val c=JSONObject().put("emojiPopupStyle",style)
        val v=decodeConversationJson(conversationJsonFromLegacy(JSONObject().put("conversation",c).toString())!!)!!
        assertEquals(setOf("header","icons","background"),(v["emojiPopupStyle"] as Map<*,*>).keys)
    }
    @Test fun missingMalformedOrNonObjectConversationDoesNotResurrectAnything() {
        listOf<String?>(null,"bad-json","{}","{\"conversation\":null}","{\"conversation\":42}").forEach { assertNull(conversationJsonFromLegacy(it)) }
    }
}
