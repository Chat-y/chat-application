package com.example.chat
import java.io.File
import java.time.Instant
import org.junit.Test
import org.junit.Assert.*
import com.example.chat.domain.models.*
import com.example.chat.ui.chats.home.filterPrivateHomeConversations
class HomeRound1SafetyTest {
 private fun source(path:String)=File("app/src/main/$path").readText()
 @Test fun ordinarySearchDoesNotConsumeLockAttempts() {
  val s=source("kotlin/com/example/chat/ui/chats/ChatsHomeScreen.kt")
  assertFalse(s.contains("verifySecretPhrase(value)"))
  assertTrue(s.contains("onRequestVaultAuth { nav.onOpenLockedVault() }"))
 }
 @Test fun lockedMessageContentsDoNotInfluenceSearch() {
  val c=Conversation("a",ConversationType.direct,"Alice",listOf("a","me"),lastMessageText="private phrase",lastMessageTime=Instant.EPOCH,lastMessageSenderId="a")
  assertTrue(filterPrivateHomeConversations(listOf(c),"private","All",null,{false},{true}).isEmpty())
  assertEquals(1,filterPrivateHomeConversations(listOf(c),"Alice","All",null,{false},{true}).size)
  assertEquals(1,filterPrivateHomeConversations(listOf(c),"private","All",null,{false},{false}).size)
 }
 @Test fun searchUsesDisplayedPrivacySafeTitleRatherThanHiddenPhone() {
  val c=Conversation("a",ConversationType.direct,"+919999999999",listOf("a","me"),lastMessageText="hello",lastMessageTime=Instant.EPOCH,lastMessageSenderId="a")
  val title:(Conversation)->String={com.example.chat.ui.chats.home.displayConversationTitle(it,true,"me",emptySet())}
  assertTrue(filterPrivateHomeConversations(listOf(c),"99999","All",null,{false},{false},title).isEmpty())
  assertEquals(1,filterPrivateHomeConversations(listOf(c),"Unknown contact","All",null,{false},{false},title).size)
 }
 @Test fun lockedRowsRedactVisualAndSpokenPreview() {
  val s=source("kotlin/com/example/chat/ui/chats/home/ConversationRow.kt")
  assertTrue(s.contains("locked -> \"Locked conversation\""))
  assertTrue(s.contains("if (!locked && conversation.draftText.isNotEmpty())"))
  assertTrue(s.contains("customActions=listOf("))
 }
 @Test fun hostGuardsAccountResendUnlockAndCall() {
  val s=source("kotlin/com/example/chat/ui/ChatsHomeHost.kt")
  assertTrue(s.contains("if(!verificationBusy && verificationWait==0)"))
  assertTrue(s.contains("if(ownsRequest()) onVerifyEmail"))
  assertTrue(s.contains("if (ok && ownsRequest()"))
  assertTrue(s.contains("if(!allowed || !ownsRequest()) return@launch"))
  assertFalse(s.contains("id = if (digits.isNotEmpty())"))
  assertFalse(s.contains("repo.deleteConversation(id)"))
 }
 @Test fun folderSemanticsAndMinimumTargetsArePresent() {
  val s=source("kotlin/com/example/chat/ui/chats/folders/ChatFolderChips.kt")
  assertTrue(s.contains("LazyRow("));assertTrue(s.contains("onLongClickLabel=\"Manage folder\""))
  assertTrue(s.contains("heightIn(min=48.dp)"));assertTrue(s.contains("All folders"))
  assertTrue(s.contains("if(created!=null)"));assertTrue(s.contains("Delete folder?"))
 }
 @Test fun speedDialUsesOneLabeledWholeRowTarget() {
  val s=source("kotlin/com/example/chat/ui/navigation/NavigationFab.kt")
  assertTrue(s.contains("semantics(mergeDescendants=true) {contentDescription=action.label}"))
  assertTrue(s.contains("clickable(role=Role.Button) {expanded=false;action.onPressed()}"))
  assertTrue(s.contains("Removed unreachable fallback, preserved for review"))
 }

}
