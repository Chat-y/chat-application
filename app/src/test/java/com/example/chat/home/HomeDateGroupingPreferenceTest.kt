package com.example.chat.ui.chats.home
import org.junit.Assert.*
import org.junit.Test
class HomeDateGroupingPreferenceTest {
    @Test fun absentPreferenceKeepsDateGroupingOff() { assertFalse(homeDateGroupingEnabled(emptyMap())) }
    @Test fun onlyBooleanTrueEnablesIt() { assertTrue(homeDateGroupingEnabled(mapOf(HOME_GROUP_CHATS_BY_DATE to true)));assertFalse(homeDateGroupingEnabled(mapOf(HOME_GROUP_CHATS_BY_DATE to "true"))) }
    @Test fun explicitFalseDisablesItWithoutTouchingOtherKeys() { val values=mapOf(HOME_GROUP_CHATS_BY_DATE to false,"unrelated" to true);assertFalse(homeDateGroupingEnabled(values));assertEquals(true,values["unrelated"]) }
}
