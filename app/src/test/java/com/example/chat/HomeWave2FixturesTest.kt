package com.example.chat
import com.example.chat.domain.models.*
import com.example.chat.ui.chats.home.*
import java.time.Instant
import org.junit.Test
import org.junit.Assert.*
/** R2-45, R2-46, R2-47: fixtures and JVM checks. Not compiled or run when written. */
class HomeWave2FixturesTest {
 private val now=Instant.parse("2026-11-15T12:00:00Z")
 private fun room(id:String,title:String=id,text:String="text",time:Instant=now,pinned:Boolean=false,muted:Boolean=false,draft:String="",archived:Boolean=false)=
  Conversation(id,ConversationType.direct,title,listOf("me",id),lastMessageText=text,lastMessageTime=time,lastMessageSenderId=id,isPinned=pinned,isMuted=muted,draftText=draft,isArchived=archived)
 /** R2-49: fixture with hostile and edge values. */
 private fun malformed()=listOf(
  room("blank-title",title=""),
  room("epoch",time=Instant.EPOCH),
  room("whitespace-draft",draft="   \n\t"),
  room("long-title",title="x".repeat(500)),
  room("emoji-title",title=String(Character.toChars(0x1F600)).repeat(50)),
  room("dup",title="same"),room("dup",title="same"),
  room("future",time=now.plusSeconds(86400L*365)),
  room("archived-draft",draft="d",archived=true))
 /** R2-45: named scenarios a screenshot suite should render. Names only, no pixels. */
 private val visualScenarios=listOf("empty","long-name","high-unread","locked","selected","narrow-window")
 @Test fun visualScenarioNamesAreUniqueAndNonBlank() {
  assertEquals(visualScenarios.size,visualScenarios.toSet().size)
  assertTrue(visualScenarios.all {it.isNotBlank()})
 }
 @Test fun summaryHandlesMalformedFixtureWithoutThrowing() {
  val s=HomeQuickViewPolicy.summary(malformed(),{false})
  assertEquals(8,s.chats)
  assertEquals(0,s.drafts)
 }
 @Test fun everyViewAndOrderSurvivesMalformedFixture() {
  val input=malformed()
  for(v in HomeQuickView.entries) for(o in HomeLocalOrder.entries) {
   val out=HomeQuickViewPolicy.apply(input,v,o,now,{false})
   assertTrue(out.size<=input.size)
  }
  assertEquals(9,input.size)
 }
 @Test fun futureTimestampIsNeverOlderAndNeverCrashes() {
  val c=listOf(room("future",time=now.plusSeconds(86400L*365)))
  assertEquals(1,HomeQuickViewPolicy.apply(c,HomeQuickView.Recent,HomeLocalOrder.Existing,now,{false}).size)
  assertTrue(HomeQuickViewPolicy.apply(c,HomeQuickView.Older,HomeLocalOrder.Existing,now,{false}).isEmpty())
 }
 /** R2-46 rule table: browsing choices may survive recreation, sensitive transient state must reset. */
 private val surviveRecreation=setOf("quickView","localOrder")
 private val resetOnRecreation=setOf("scratchpadText","selection","unlockedChats","searchQuery")
 @Test fun recreationRuleSetsDoNotOverlap() {
  assertTrue(surviveRecreation.intersect(resetOnRecreation).isEmpty())
 }
 @Test fun epochChatIsOlderNotRecent() {
  val c=listOf(room("epoch",time=Instant.EPOCH))
  assertTrue(HomeQuickViewPolicy.apply(c,HomeQuickView.Recent,HomeLocalOrder.Existing,now,{false}).isEmpty())
  assertEquals(1,HomeQuickViewPolicy.apply(c,HomeQuickView.Older,HomeLocalOrder.Existing,now,{false}).size)
 }
 @Test fun nameOrderIsStableForEqualTitles() {
  val c=listOf(room("b",title="same"),room("a",title="same"))
  assertEquals(listOf("a","b"),HomeQuickViewPolicy.apply(c,HomeQuickView.All,HomeLocalOrder.Name,now,{false}).map {it.id})
 }
 /** R2-46: the Home view state is plain enums and strings, so a restore can rebuild it by name.
 * Real process recreation still needs an emulator test. */
 @Test fun viewAndOrderStateRoundTripsByName() {
  for(v in HomeQuickView.entries) assertEquals(v,HomeQuickView.valueOf(v.name))
  for(o in HomeLocalOrder.entries) assertEquals(o,HomeLocalOrder.valueOf(o.name))
 }
 @Test fun sameInputsGiveSameResultAfterRebuild() {
  val a=HomeQuickViewPolicy.apply(malformed(),HomeQuickView.Older,HomeLocalOrder.Oldest,now,{false}).map {it.id}
  val b=HomeQuickViewPolicy.apply(malformed(),HomeQuickView.Older,HomeLocalOrder.Oldest,now,{false}).map {it.id}
  assertEquals(a,b)
 }
 @Test fun scratchpadBoundIsIdempotent() {
  val once=HomeScratchpadPolicy.bounded("a".repeat(5000))
  assertEquals(once,HomeScratchpadPolicy.bounded(once))
 }
 @Test fun inventoryIdsAreUnique() {
  val ids=HomeFeatureInventory.items.map {it.id}
  assertEquals(ids.size,ids.toSet().size)
  assertTrue(HomeFeatureInventory.byStatus(HomeInventoryStatus.NEEDS_DEVICE).isNotEmpty())
 }
}
