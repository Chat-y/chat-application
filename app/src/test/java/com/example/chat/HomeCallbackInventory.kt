package com.example.chat
/** Inventory of optional Home callbacks and inputs added by waves 1-3. Test-only data. No production behaviour.
 * Status is read from source text at the stacked baseline. Nothing here was compiled or run. */
enum class HomeWiring {WIRED,DECLARED_NOT_WIRED,DEFAULT_ONLY}
data class HomeCallback(val name:String,val file:String,val declaration:String,val wiring:HomeWiring,val note:String)
object HomeCallbackInventory {
 private const val M="kotlin/com/example/chat/ui/"
 const val SCREEN=M+"chats/ChatsHomeScreen.kt"
 const val BARS=M+"chats/home/HomeAppBars.kt"
 const val CHIPS=M+"chats/folders/ChatFolderChips.kt"
 const val HOST=M+"ChatsHomeHost.kt"
 val items:List<HomeCallback> = listOf(
  HomeCallback("onInvert",BARS,"onInvert: (() -> Unit)? = null",HomeWiring.WIRED,"Screen passes HomeSelectionPolicy.invert."),
  HomeCallback("onInspect",BARS,"onInspect: (() -> Unit)? = null",HomeWiring.WIRED,"Screen passes inspectorOrder targets."),
  HomeCallback("onExitSelection",BARS,"onExitSelection: (() -> Unit)? = null",HomeWiring.WIRED,"Clears selection only."),
  HomeCallback("onSplitChat",BARS,"onSplitChat: (() -> Unit)? = null",HomeWiring.WIRED,"Pre-existing optional."),
  HomeCallback("onSectionHeaderTap",SCREEN,"onSectionHeaderTap: (String) -> Unit = {}",HomeWiring.WIRED,"GAP: matches label == PINNED only; grouping rewrites it to PINNED (n chats, m unread)."),
  HomeCallback("onRangeSelectTo",SCREEN,"onRangeSelectTo: (String) -> Unit = {}",HomeWiring.WIRED,"Uses HomeSelectionPolicy.range."),
  HomeCallback("onRowContextMenu",SCREEN,"onRowContextMenu: (Conversation) -> Unit = {}",HomeWiring.WIRED,"Input modifiers."),
  HomeCallback("onRowActivate",SCREEN,"onRowActivate: (Conversation) -> Unit = {}",HomeWiring.WIRED,"Input modifiers."),
  HomeCallback("onRowFocused",SCREEN,"onRowFocused: (String?) -> Unit = {}",HomeWiring.WIRED,"Focus ring state."),
  HomeCallback("onHoverRow",SCREEN,"onHoverRow: (String?) -> Unit = {}",HomeWiring.WIRED,"Hover state."),
  HomeCallback("focusRequesterFor",SCREEN,"focusRequesterFor: (String) -> FocusRequester",HomeWiring.WIRED,"Per-row requester."),
  HomeCallback("opState",SCREEN,"opState: Map<HomeOpKey, HomeOpState> = emptyMap()",HomeWiring.DEFAULT_ONLY,"Host does not pass it: Home shows no operation state until the Host owner wires S11."),
  HomeCallback("onRetry",SCREEN,"onRetry: (HomeOpKey) -> Unit = {}",HomeWiring.DEFAULT_ONLY,"Host does not pass it. Retry must revalidate HomeRetryIntent.isValidFor."),
  HomeCallback("summaries",CHIPS,"summaries: Map<String, String> = emptyMap()",HomeWiring.WIRED,"Screen passes folderActivitySummaries."),
  HomeCallback("selectedConversationIds",CHIPS,"selectedConversationIds: Set<String> = emptySet()",HomeWiring.WIRED,"Screen passes selectedIds."),
  HomeCallback("onOpenConversation",CHIPS,"onOpenConversation: ((String) -> Unit)? = null",HomeWiring.WIRED,"Screen routes through handleConversationTap."),
  HomeCallback("canInspectConversation",CHIPS,"canInspectConversation: (String) -> Boolean = { false }",HomeWiring.DECLARED_NOT_WIRED,"GAP: Screen does not pass it, so folder inspectors stay empty. Must exclude locked and hidden chats."),
  HomeCallback("resolvedConversationIds",CHIPS,"resolvedConversationIds: Set<String>? = null",HomeWiring.DECLARED_NOT_WIRED,"Null keeps stale-reference removal off. Pass only after conversation load completed."),
  HomeCallback("badgeFor",CHIPS,"badgeFor: ((String) -> String?)? = null",HomeWiring.DECLARED_NOT_WIRED,"Opt-in badges (R4-46). Never reveal labels for locked rows."),
  HomeCallback("onReorder",CHIPS,"onReorder: ((List<String>) -> Unit)? = null",HomeWiring.DECLARED_NOT_WIRED,"Held: needs a persisted order contract (store is HELD).")
 )
 fun byWiring(w:HomeWiring)=items.filter {it.wiring==w}
 /** Names the planner expected but that do not exist anywhere in source at this baseline. */
 val missing:List<String> = listOf("onOpenSavedViews","onOpenViewHistory")
}
