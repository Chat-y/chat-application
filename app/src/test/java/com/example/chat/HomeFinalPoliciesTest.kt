package com.example.chat
import androidx.compose.ui.geometry.Rect
import com.example.chat.data.local.ChatFolder
import com.example.chat.domain.models.*
import com.example.chat.ui.chats.home.*
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CancellationException
import org.junit.Test
import org.junit.Ignore
import org.junit.Assert.*
/** Home waves 1-3 final-policy checks. Source-only: written against the stacked patches, NOT compiled and NOT run.
 * Tests named *Gap* are @Ignore and state the DESIRED end state for a cross-file gap owned by another group. */
class HomeFinalPoliciesTest {
 private val now=Instant.parse("2026-11-15T12:00:00Z")
 private val utc=ZoneId.of("UTC")
 private fun room(id:String,title:String=id,type:ConversationType=ConversationType.direct,time:Instant=now,pinned:Boolean=false,muted:Boolean=false,archived:Boolean=false,unread:Int=0)=
  Conversation(id,type,title,listOf("me",id),lastMessageText="t",lastMessageTime=time,lastMessageSenderId=id,isPinned=pinned,isMuted=muted,isArchived=archived,unreadCount=unread)
 private fun src(path:String)=java.io.File("app/src/main/$path").readText()
 private val screen="kotlin/com/example/chat/ui/chats/ChatsHomeScreen.kt"
 // ---- S5 selection
 @Test fun selectionScopeIsRenderOrderWithoutBlankOrDuplicateIds() {
  val v=listOf(room("a"),room(""),room("b"),room("a"))
  assertEquals(listOf("a","b"),HomeSelectionPolicy.selectAll(v).toList())
 }
 @Test fun invertDropsOffScopeIds() {
  val v=listOf(room("a"),room("b"),room("c"))
  assertEquals(setOf("a","c"),HomeSelectionPolicy.invert(v,setOf("b","gone")))
 }
 @Test fun rangeIsInclusiveEitherDirectionAndEmptyForVanishedAnchor() {
  val v=listOf(room("a"),room("b"),room("c"),room("d"))
  assertEquals(listOf("b","c","d"),HomeSelectionPolicy.range(v,"b","d").toList())
  assertEquals(listOf("b","c","d"),HomeSelectionPolicy.range(v,"d","b").toList())
  assertTrue(HomeSelectionPolicy.range(v,"zz","b").isEmpty())
 }
 @Test fun bulkActionsFailClosedOnEmptyBlankOrDuplicate() {
  assertTrue(HomeSelectionPolicy.validBulkActions(emptyList()).isEmpty())
  assertTrue(HomeSelectionPolicy.validBulkActions(listOf(room(""))).isEmpty())
  assertTrue(HomeSelectionPolicy.validBulkActions(listOf(room("a"),room("a"))).isEmpty())
 }
 @Test fun bulkActionsOfferToggleFamilyOnlyForUniformState() {
  val mixed=HomeSelectionPolicy.validBulkActions(listOf(room("a",pinned=true),room("b")))
  assertFalse(HomeBulkAction.PIN in mixed); assertFalse(HomeBulkAction.UNPIN in mixed)
  assertTrue(HomeBulkAction.DELETE in mixed)
  val pinned=HomeSelectionPolicy.validBulkActions(listOf(room("a",pinned=true),room("b",pinned=true)))
  assertTrue(HomeBulkAction.UNPIN in pinned); assertFalse(HomeBulkAction.PIN in pinned)
 }
 @Test fun bulkActionsNeverIncludeLockOrUnlock() {
  assertTrue(HomeBulkAction.entries.none {it.name.contains("LOCK")})
 }
 @Test fun inspectorOrderFollowsHomeOrderNotSelectionOrder() {
  val v=listOf(room("a"),room("b"),room("c"))
  assertEquals(listOf("a","c"),HomeSelectionPolicy.inspectorOrder(v,linkedSetOf("c","a")).map {it.id})
 }
 // ---- S6 layout
 @Test fun clampDividerBoundsAndNonFinite() {
  assertEquals(0.2f,clampDivider(0f),0f); assertEquals(0.8f,clampDivider(5f),0f)
  assertEquals(0.4f,clampDivider(Float.NaN),0f); assertEquals(0.4f,clampDivider(Float.POSITIVE_INFINITY),0f)
 }
 @Test fun clampDividerSnapsOutOfVerticalHingeAndIgnoresHorizontalOne() {
  val hinge=Rect(0.45f,0f,0.55f,1f)
  val snapped=clampDivider(0.5f,listOf(hinge))
  assertTrue(snapped<=0.45f||snapped>=0.55f)
  val horizontal=Rect(0.3f,0.4f,0.7f,0.5f)
  assertEquals(0.5f,clampDivider(0.5f,listOf(horizontal)),0f)
 }
 @Test fun narrowWindowKeepsDetailAndNeverSplits() {
  val l=HomePaneState().layout(availableWidthDp=500f,detailOpen=true)
  assertFalse(l.isSplit); assertTrue(l.showDetail); assertFalse(l.showList)
  val noDetail=HomePaneState().layout(availableWidthDp=500f,detailOpen=false)
  assertTrue(noDetail.showList); assertFalse(noDetail.showDetail)
 }
 @Test fun wideWindowSplitsAndMaximizeTogglesBack() {
  val s=HomePaneState()
  assertTrue(s.layout(availableWidthDp=1000f,detailOpen=true).isSplit)
  val max=s.toggleMaximized(HomePaneMax.Detail)
  assertFalse(max.layout(availableWidthDp=1000f,detailOpen=true).showList)
  assertEquals(HomePaneMax.None,max.toggleMaximized(HomePaneMax.Detail).maximized)
  assertEquals(s.dividerFraction,max.restoreSplit().dividerFraction,0f)
 }
 @Test fun layoutRespectsRailSubtractionWhenCallerPassesUsableWidth() {
  // The caller must pass width MINUS folder rail (181dp) and a divider width. 800-181-16=603 < 720 so no split.
  assertFalse(HomePaneState().layout(availableWidthDp=800f-181f,detailOpen=true,dividerWidthDp=16f).isSplit)
  assertTrue(HomePaneState().layout(availableWidthDp=950f-181f,detailOpen=true,dividerWidthDp=16f).isSplit)
 }
 @Test fun layoutInvalidInputsFallBackToDefaults() {
  val l=HomePaneState().layout(availableWidthDp=Float.NaN,detailOpen=true,minListWidthDp=-1f)
  assertFalse(l.isSplit)
 }
 // ---- S8 grouping
 @Test fun noneModeAndNoCollapseReturnsSameInstance() {
  val e=listOf<HomeEntry>(HomeEntry.Section("MESSAGES"),HomeEntry.Item(room("a")))
  assertSame(e,groupHomeEntries(e,HomeGroupMode.None,false))
 }
 @Test fun collapsedPinnedHeaderCarriesCountsAndHidesRows() {
  val e=listOf<HomeEntry>(HomeEntry.Section("PINNED"),HomeEntry.Item(room("a",pinned=true,unread=2)),HomeEntry.Item(room("b",pinned=true,unread=-5)),
   HomeEntry.Section("MESSAGES"),HomeEntry.Item(room("c")))
  val out=groupHomeEntries(e,HomeGroupMode.None,true)
  assertEquals("PINNED (2 chats, 2 unread)",(out[0] as HomeEntry.Section).label)
  assertEquals(1,out.count {it is HomeEntry.Item})
 }
 @Test fun collapsedPinnedLabelStillStartsWithPinnedSoToggleCanMatchIt() {
  val e=listOf<HomeEntry>(HomeEntry.Section("PINNED"),HomeEntry.Item(room("a",pinned=true)))
  val label=(groupHomeEntries(e,HomeGroupMode.None,true)[0] as HomeEntry.Section).label
  assertTrue(label.startsWith("PINNED"))
  assertNotEquals("PINNED",label)
 }
 @Test fun dateGroupingUsesInjectedClockAndFuturesAndLockedAreSeparate() {
  val e=listOf<HomeEntry>(HomeEntry.Section("MESSAGES"),
   HomeEntry.Item(room("today",time=now)),HomeEntry.Item(room("yest",time=now.minusSeconds(86400))),
   HomeEntry.Item(room("old",time=now.minusSeconds(86400L*5))),HomeEntry.Item(room("fut",time=now.plusSeconds(86400L*3))),
   HomeEntry.Item(room("lk",time=now)))
  val out=groupHomeEntries(e,HomeGroupMode.Date({it=="lk"},now,utc),false)
  val labels=out.filterIsInstance<HomeEntry.Section>().map {it.label}
  assertEquals(listOf("MESSAGES","Today","Yesterday","Earlier","Future activity","Locked conversations"),labels)
 }
 @Test fun dateGroupingChangesWhenClockOrZoneChanges() {
  val e=listOf<HomeEntry>(HomeEntry.Item(room("a",time=Instant.parse("2026-11-15T20:00:00Z"))))
  fun first(now:Instant,z:ZoneId)=(groupHomeEntries(e,HomeGroupMode.Date({false},now,z),false)[0] as HomeEntry.Section).label
  assertEquals("Today",first(Instant.parse("2026-11-15T21:00:00Z"),utc))
  assertEquals("Yesterday",first(Instant.parse("2026-11-16T21:00:00Z"),utc))
  // 20:00Z is already Nov 16 in Asia/Kolkata (01:30), and so is "now": still Today there.
  assertEquals("Today",first(Instant.parse("2026-11-15T21:00:00Z"),ZoneId.of("Asia/Kolkata")))
 }
 @Test fun alphabeticalUsesDisplayTitleAndHashForNonLetters() {
  val a=room("1",title="secret-raw-zzz"); val b=room("2",title="raw-aaa")
  val shown=mapOf("1" to "Alice","2" to "9lives")
  val out=groupHomeEntries(listOf(HomeEntry.Item(a),HomeEntry.Item(b)),HomeGroupMode.Alphabetical({shown.getValue(it.id)},Locale.ENGLISH),false)
  val labels=out.filterIsInstance<HomeEntry.Section>().map {it.label}
  assertEquals(listOf("#","A"),labels)
  assertEquals("#",homeAlphabeticalSectionLabel("  "))
 }
 @Test fun directGroupSplitsByTypeAndKeepsArchivedEntry() {
  val e=listOf<HomeEntry>(HomeEntry.Archived(3),HomeEntry.Section("MESSAGES"),HomeEntry.Item(room("g",type=ConversationType.group)),HomeEntry.Item(room("d")))
  val out=groupHomeEntries(e,HomeGroupMode.DirectGroup,false)
  assertTrue(out[0] is HomeEntry.Archived)
  assertEquals(listOf("MESSAGES","Direct","Groups"),out.filterIsInstance<HomeEntry.Section>().map {it.label})
 }
 // ---- S9 keys
 @Test fun entryKeysAreNamespacedAndDuplicateSafe() {
  val seen=mutableSetOf<String>()
  assertEquals("chat:a",homeEntryKey(HomeEntry.Item(room("a")),seen))
  assertEquals("chat:a#2",homeEntryKey(HomeEntry.Item(room("a")),seen))
  assertEquals("chat:a#3",homeEntryKey(HomeEntry.Item(room("a")),seen))
  assertEquals("archive:entry",homeEntryKey(HomeEntry.Archived(1),seen))
  assertEquals("section:PINNED",homeEntryKey(HomeEntry.Section("PINNED"),seen))
 }
 @Test fun chatIdThatLooksLikeSectionKeyCannotCollide() {
  val seen=mutableSetOf<String>()
  val a=homeEntryKey(HomeEntry.Section("x"),seen); val b=homeEntryKey(HomeEntry.Item(room("section:x")),seen)
  assertNotEquals(a,b)
 }
 @Test fun dedupeKeepsFirstAndReturnsSameInstanceWhenClean() {
  val clean=listOf(room("a"),room("b"))
  assertSame(clean,dedupeConversations(clean))
  val dup=listOf(room("a",title="first"),room("b"),room("a",title="second"))
  val out=dedupeConversations(dup)
  assertEquals(listOf("a","b"),out.map {it.id}); assertEquals("first",out[0].title)
 }
 // ---- S11 operation state
 private val k=HomeOpKey("pin","c1")
 @Test fun repeatedStartWhilePendingIsIgnored() {
  val s1=reduce(emptyMap(),HomeOpEvent.Start(k,1,true))
  assertSame(s1,reduce(s1,HomeOpEvent.Start(k,2,false)))
 }
 @Test fun staleCompletionIsIgnoredAndTerminalTokenBlocksOldStart() {
  val s1=reduce(emptyMap(),HomeOpEvent.Start(k,5,true))
  assertSame(s1,reduce(s1,HomeOpEvent.Succeed(k,4)))
  val done=reduce(s1,HomeOpEvent.Succeed(k,5))
  assertTrue(done[k] is HomeOpState.Succeeded)
  assertSame(done,reduce(done,HomeOpEvent.Start(k,5,true)))
  assertTrue(reduce(done,HomeOpEvent.Start(k,6,true))[k] is HomeOpState.Pending)
 }
 @Test fun failCarriesRetryOnlyWhenKeyAndDesiredValueMatch() {
  val s=reduce(emptyMap(),HomeOpEvent.Start(k,1,true))
  val id=Any()
  val good=HomeRetryIntent(k,id,true)
  val bad=HomeRetryIntent(k,id,false)
  assertNotNull((reduce(s,HomeOpEvent.Fail(k,1,HomeFailureKind.Offline,good))[k] as HomeOpState.Failed).retryIntent)
  assertNull((reduce(s,HomeOpEvent.Fail(k,1,HomeFailureKind.Offline,bad))[k] as HomeOpState.Failed).retryIntent)
 }
 @Test fun cancelledFailureBecomesCancelledState() {
  val s=reduce(emptyMap(),HomeOpEvent.Start(k,1))
  assertTrue(reduce(s,HomeOpEvent.Fail(k,1,HomeFailureKind.Cancelled))[k] is HomeOpState.Cancelled)
  assertTrue(reduce(s,HomeOpEvent.Cancel(k,1))[k] is HomeOpState.Cancelled)
 }
 @Test fun pruneKeepsAccountWideKeysAndResetClears() {
  val acct=HomeOpKey("markAllRead","")
  var s=reduce(emptyMap(),HomeOpEvent.Start(k,1)); s=reduce(s,HomeOpEvent.Start(acct,2))
  assertEquals(setOf(acct),reduce(s,HomeOpEvent.Prune(setOf("other"))).keys)
  assertTrue(reduce(s,HomeOpEvent.Reset).isEmpty())
 }
 @Test fun retryIntentChecksAccountByReferenceAndHidesContentInToString() {
  val id=Any(); val r=HomeRetryIntent(k,id,null,mapOf("title" to "secret"))
  assertTrue(r.isValidFor(id,setOf("c1"))); assertFalse(r.isValidFor(Any(),setOf("c1"))); assertFalse(r.isValidFor(id,emptySet()))
  assertFalse(r.toString().contains("secret"))
 }
 @Test fun classifyFailureRethrowsCancellationEvenWrapped() {
  try { classifyFailure(RuntimeException("x",CancellationException("c"))); fail("expected CancellationException") } catch(e:CancellationException) {}
 }
 @Test fun classifyFailureOfflineVsUnknownAndFeedbackIsContentFree() {
  assertEquals(HomeFailureKind.Offline,classifyFailure(java.net.UnknownHostException("host-secret")))
  assertEquals(HomeFailureKind.Unknown,classifyFailure(java.net.SocketTimeoutException("t")))
  assertEquals(HomeFailureKind.Unknown,classifyFailure(java.io.IOException("disk")))
  assertEquals("",HomeFailureKind.Cancelled.feedback())
  assertTrue(HomeFailureKind.entries.none {it.feedback().contains("secret")})
 }
 // ---- S12 folder policy (pure part only; store needs Android context)
 @Test fun folderPickerFilterIsNameOnlyTrimmedAndOrderPreserving() {
  val f=listOf(ChatFolder("1","Work"),ChatFolder("2","Family"),ChatFolder("3","Network"))
  assertEquals(listOf("1","3"),HomeFolderPolicy.folderPickerFilter("  wor ",f).map {it.id})
  assertSame(f,HomeFolderPolicy.folderPickerFilter("   ",f))
 }
 // ---- S13 quick views
 @Test fun saveViewNormalizesCapsAndReplacesCaseInsensitively() {
  var l=emptyList<HomeSavedView>()
  l=HomeQuickViewPolicy.saveView(l,HomeSavedView("  Work   chats "))
  l=HomeQuickViewPolicy.saveView(l,HomeSavedView("work chats",view=HomeQuickView.Pinned))
  assertEquals(1,l.size); assertEquals(HomeQuickView.Pinned,l[0].view); assertEquals("work chats",l[0].name)
  assertSame(l,HomeQuickViewPolicy.saveView(l,HomeSavedView("   ")))
  repeat(30) {l=HomeQuickViewPolicy.saveView(l,HomeSavedView("v$it"))}
  assertEquals(HOME_SAVED_VIEW_MAX,l.size); assertEquals("v29",l.last().name)
 }
 @Test fun historyDedupesTopCapsAndPopsLast() {
  val a=HomeViewState(); val b=HomeViewState(view=HomeQuickView.Muted)
  val s1=HomeQuickViewPolicy.pushHistory(emptyList(),a)
  assertSame(s1,HomeQuickViewPolicy.pushHistory(s1,a))
  var s=s1; repeat(40) {s=HomeQuickViewPolicy.pushHistory(s,HomeViewState(groupingKey="g$it"))}
  assertEquals(HOME_VIEW_HISTORY_MAX,s.size)
  val (top,rest)=HomeQuickViewPolicy.popHistory(listOf(a,b))
  assertEquals(b,top); assertEquals(listOf(a),rest)
  assertNull(HomeQuickViewPolicy.popHistory(emptyList()).first)
 }
 @Test fun previewUsesDisplayTitlesAndNeverRawTitles() {
  val chats=listOf(room("a",title="+919999999999"),room("b",title="Bee"))
  val p=HomeQuickViewPolicy.preview(HomeSavedView("x"),chats,now,{false},displayTitleFor={if(it.id=="a") "Unknown contact" else it.title},sample=2)
  assertEquals(2,p.matched); assertFalse(p.sampleTitles.any {it.contains("9199")})
 }
 @Test fun describeNeverMentionsSearchOrDraftText() {
  val d=HomeQuickViewPolicy.describe(HomeViewState(folderId="f1",groupingKey="Date"),{null})
  assertTrue(d.contains("Folder: unknown")); assertTrue(d.contains("Grouped: Date"))
 }
 // ---- R1-47 metrics are additive
 @Test fun existingSharedIdentityMetricsStayUnchanged() {
  assertEquals(104,com.example.chat.ui.navigation.ReferenceLayoutMetrics.compactIdentityHeightDp)
  assertEquals(30,com.example.chat.ui.navigation.ReferenceLayoutMetrics.identityTextSizeSp)
  assertEquals(64,com.example.chat.ui.navigation.ReferenceLayoutMetrics.homeCompactIdentityMinHeightDp)
 }
 // ---- cross-file gaps (desired end state; owners are other groups) ----
 @Ignore("GAP Screen owner: onSectionHeaderTap must match collapsed 'PINNED (n chats, m unread)' labels, not only 'PINNED'.")
 @Test fun gapPinnedToggleMatchesCountBearingHeader() {
  val s=src(screen)
  assertTrue(s.contains("label.startsWith(\"PINNED\")")||s.contains("startsWith(\"PINNED (\")"))
 }
 @Ignore("GAP Screen owner: pane layout must receive width minus folder rail and the real 16dp divider width.")
 @Test fun gapPaneLayoutSubtractsRailAndUsesDividerWidth() {
  val s=src(screen)
  val i=s.indexOf("homePaneState.layout(")
  val call=s.substring(i,s.indexOf(")",s.indexOf("expandedListDetail = true",i)))
  assertTrue(call.contains("dividerWidthDp"))
  assertTrue(call.contains("railWidthDp")||call.contains("maxWidth.value -"))
 }
 @Ignore("GAP Screen owner: ChatFolderChips defaults canInspectConversation to false; Screen must pass an unlocked/visible predicate.")
 @Test fun gapFolderChipsReceiveInspectPredicate() {
  assertTrue(src(screen).contains("canInspectConversation ="))
 }
 @Ignore("GAP Screen/Bars owner: S13 saved views and history have a policy but no Screen wiring or Bars callbacks.")
 @Test fun gapSavedViewsAndHistoryHaveEntryPoints() {
  val bars=src("kotlin/com/example/chat/ui/chats/home/HomeAppBars.kt")
  assertTrue(bars.contains("onOpenSavedViews")); assertTrue(bars.contains("onOpenViewHistory"))
  assertTrue(src(screen).contains("pushHistory"))
 }
 @Ignore("GAP Screen owner: S1 date grouping must take a fresh Date from rememberHomeDateContext, not a stale Instant.now() default.")
 @Test fun gapDateGroupingUsesFreshClock() {
  val s=src(screen)
  val i=s.indexOf("HomeGroupMode.Date(")
  assertTrue(i>=0)
  val call=s.substring(i,s.indexOf("))",i))
  assertTrue(call.contains("now =")&&call.contains("zone ="))
 }
}
