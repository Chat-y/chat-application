package com.example.chat
import com.example.chat.domain.models.*
import com.example.chat.ui.chats.home.*
import java.time.Instant
import org.junit.Test
import org.junit.Assert.*
class HomeQuickViewPolicyTest {
 private val now=Instant.parse("2026-11-15T12:00:00Z")
 private fun room(id:String,pinned:Boolean=false,muted:Boolean=false,draft:String="",days:Long=0,archived:Boolean=false)=
  Conversation(id,ConversationType.direct,id,listOf("me",id),lastMessageText="text",lastMessageTime=now.minusSeconds(days*86400),lastMessageSenderId=id,isPinned=pinned,isMuted=muted,draftText=draft,isArchived=archived)
 @Test fun defaultsNeverChangeInputOrder() {
  val chats=listOf(room("z"),room("a"));assertEquals(chats,HomeQuickViewPolicy.apply(chats,HomeQuickView.All,HomeLocalOrder.Existing,now,{false}))
 }
 @Test fun separatePinnedMutedAndDraftSlices() {
  val chats=listOf(room("pin",pinned=true),room("mute",muted=true),room("draft",draft="hi"))
  fun ids(view:HomeQuickView)=HomeQuickViewPolicy.apply(chats,view,HomeLocalOrder.Existing,now,{false}).map {it.id}
  assertEquals(listOf("pin"),ids(HomeQuickView.Pinned));assertEquals(listOf("mute"),ids(HomeQuickView.Muted));assertEquals(listOf("draft"),ids(HomeQuickView.Drafts))
 }
 @Test fun lockedDraftsExcludedFromViewAndAggregate() {
  val chats=listOf(room("open",draft="x"),room("locked",draft="private"));val locked:(String)->Boolean={it=="locked"}
  assertEquals(listOf("open"),HomeQuickViewPolicy.apply(chats,HomeQuickView.Drafts,HomeLocalOrder.Existing,now,locked).map {it.id})
  assertEquals(1,HomeQuickViewPolicy.summary(chats,locked).drafts)
 }
 @Test fun sevenDayBoundaryDoesNotOverlapOlder() {
  val chats=listOf(room("boundary",days=7),room("old",days=8),room("new"))
  assertEquals(listOf("boundary","new"),HomeQuickViewPolicy.apply(chats,HomeQuickView.Recent,HomeLocalOrder.Existing,now,{false}).map {it.id})
  assertEquals(listOf("old"),HomeQuickViewPolicy.apply(chats,HomeQuickView.Older,HomeLocalOrder.Existing,now,{false}).map {it.id})
 }
 @Test fun ageWindowMovesWhenClockAdvancesWithoutNewMessages() {
  val chats=listOf(room("boundary",days=7))
  assertEquals(1,HomeQuickViewPolicy.apply(chats,HomeQuickView.Recent,HomeLocalOrder.Existing,now,{false}).size)
  assertTrue(HomeQuickViewPolicy.apply(chats,HomeQuickView.Recent,HomeLocalOrder.Existing,now.plusSeconds(60),{false}).isEmpty())
  assertEquals(1,HomeQuickViewPolicy.apply(chats,HomeQuickView.Older,HomeLocalOrder.Existing,now.plusSeconds(60),{false}).size)
 }
 @Test fun orderChangesAreReversibleWithoutMutatingInput() {
  val chats=listOf(room("z"),room("a",days=3));
  assertEquals(listOf("a","z"),HomeQuickViewPolicy.apply(chats,HomeQuickView.All,HomeLocalOrder.Name,now,{false}).map {it.id})
  assertEquals(listOf("a","z"),HomeQuickViewPolicy.apply(chats,HomeQuickView.All,HomeLocalOrder.Oldest,now,{false}).map {it.id})
  assertEquals(listOf("z","a"),chats.map {it.id})
 }
 @Test fun nameOrderUsesDisplayedNamesAndExplicitLocale() {
  val chats=listOf(room("a"),room("z"))
  val titles=mapOf("a" to "Zulu","z" to "Alpha")
  assertEquals(listOf("z","a"),HomeQuickViewPolicy.apply(chats,HomeQuickView.All,HomeLocalOrder.Name,now,{false},{titles.getValue(it.id)},java.util.Locale.US).map {it.id})
 }
 @Test fun summaryExcludesArchivedAndDoesNotCountDraftCharacters() {
  val s=HomeQuickViewPolicy.summary(listOf(room("a",pinned=true,muted=true,draft="secret"),room("arch",draft="a",archived=true)),{false})
  assertEquals(HomeSnapshotSummary(1,1,1,1),s)
 }
 @Test fun matrixNeverAddsConversationsAndPreservesAccountInput() {
  for(pinned in listOf(false,true)) for(muted in listOf(false,true)) for(locked in listOf(false,true)) for(draft in listOf("","text")) {
   val input=listOf(room("account-a-chat",pinned,muted,draft,days=8))
   for(view in HomeQuickView.entries) for(order in HomeLocalOrder.entries) {
    val result=HomeQuickViewPolicy.apply(input,view,order,now,{locked})
    assertTrue(result.all {it.id=="account-a-chat"})
    if(view==HomeQuickView.Drafts && locked) assertTrue(result.isEmpty())
    assertEquals(1,input.size)
   }
  }
 }
}
