package com.example.chat
import com.example.chat.ui.chats.home.HomeScratchpadPolicy
import org.junit.Test
import org.junit.Assert.*
class HomeScratchpadPolicyTest {
 @Test fun emptyAndShortTextAreUnchanged() {assertEquals("",HomeScratchpadPolicy.bounded(""));assertEquals("personal note",HomeScratchpadPolicy.bounded("personal note"))}
 @Test fun longTextIsBounded() {assertEquals(4000,HomeScratchpadPolicy.bounded("a".repeat(5000)).length)}
 @Test fun unicodePairsAreNeverSplitAtTheLimit() {
  val emoji=String(Character.toChars(0x1F600))
  val bounded=HomeScratchpadPolicy.bounded(emoji.repeat(4001))
  assertEquals(4000,bounded.codePointCount(0,bounded.length));assertEquals(8000,bounded.length)
  assertTrue(Character.isLowSurrogate(bounded.last()))
 }
}
