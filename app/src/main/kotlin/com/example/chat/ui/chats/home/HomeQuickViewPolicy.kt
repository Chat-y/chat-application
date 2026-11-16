package com.example.chat.ui.chats.home

import com.example.chat.domain.models.Conversation
import java.time.Instant

enum class HomeQuickView(val label:String) {
    All("All loaded chats"),Pinned("Pinned"),Muted("Muted"),Drafts("Drafts"),Recent("Active in 7 days"),Older("Older activity")
}
enum class HomeLocalOrder(val label:String) {Existing("Existing order"),Name("Name A-Z"),Oldest("Oldest first")}
data class HomeSnapshotSummary(val chats:Int,val pinned:Int,val muted:Int,val drafts:Int)
/** R4-47/48: session-only named Home view configuration (filter, folder, grouping and order).
 *  Carries no query text, message bodies or draft text by construction. groupingKey is the
 *  HomeGrouping mode name resolved by the caller; "" keeps the current list shape. */
data class HomeSavedView(val name:String,val filter:String="All",val folderId:String?=null,
    val view:HomeQuickView=HomeQuickView.All,val order:HomeLocalOrder=HomeLocalOrder.Existing,
    val groupingKey:String="")
/** R4-49: one session-only Home view state for the back-history. Distinct from last-opened chat. */
data class HomeViewState(val filter:String="All",val folderId:String?=null,
    val view:HomeQuickView=HomeQuickView.All,val order:HomeLocalOrder=HomeLocalOrder.Existing,
    val groupingKey:String="")
/** R4-48: safe preview of a named configuration. Counts and privacy-safe display titles only. */
data class HomeViewPreview(val matched:Int,val sampleTitles:List<String>)
const val HOME_SAVED_VIEW_MAX=20
const val HOME_VIEW_HISTORY_MAX=20
object HomeQuickViewPolicy {
    fun summary(chats:List<Conversation>,locked:(String)->Boolean):HomeSnapshotSummary {
        val visible=chats.filterNot {it.isArchived}
        return HomeSnapshotSummary(visible.size,visible.count {it.isPinned},visible.count {it.isMuted},visible.count {!locked(it.id) && it.draftText.isNotBlank()})
    }
    fun apply(chats:List<Conversation>,view:HomeQuickView,order:HomeLocalOrder,
              now:Instant,locked:(String)->Boolean,displayTitleFor:(Conversation)->String={it.title},
              locale:java.util.Locale=java.util.Locale.getDefault()):List<Conversation> {
        val cutoff=now.minusSeconds(7*24*60*60L)
        val filtered=chats.filter {c -> when(view) {
            HomeQuickView.All -> true
            HomeQuickView.Pinned -> c.isPinned
            HomeQuickView.Muted -> c.isMuted
            HomeQuickView.Drafts -> !locked(c.id) && c.draftText.isNotBlank()
            HomeQuickView.Recent -> !c.lastMessageTime.isBefore(cutoff)
            HomeQuickView.Older -> c.lastMessageTime.isBefore(cutoff)
        }}
        return when(order) {
            HomeLocalOrder.Existing -> filtered
            HomeLocalOrder.Name -> {
                // Removed raw-title/Locale.ROOT order: sorting must match the displayed privacy-safe name.
                val collator=java.text.Collator.getInstance(locale)
                filtered.sortedWith(Comparator {a,b ->
                    val byName=collator.compare(displayTitleFor(a),displayTitleFor(b))
                    if(byName!=0) byName else a.id.compareTo(b.id)
                })
            }
            HomeLocalOrder.Oldest -> filtered.sortedWith(compareBy<Conversation> {it.lastMessageTime}.thenBy {it.id})
        }
    }
    fun normalizeSavedViewName(raw:String):String=raw.trim().replace(Regex("\\s+")," ").take(48)
    /** R4-47: capture the current session view state as a named saved view. Returns null
     *  when the name normalizes to blank; carries no query text, message or draft content. */
    fun savedViewFromState(name:String,state:HomeViewState):HomeSavedView? {
        val normalized=normalizeSavedViewName(name)
        if(normalized.isEmpty()) return null
        return HomeSavedView(normalized,state.filter,state.folderId,state.view,state.order,state.groupingKey)
    }
    /** R4-47: the session state a saved view restores when applied. */
    fun viewStateOf(config:HomeSavedView):HomeViewState=
        HomeViewState(config.filter,config.folderId,config.view,config.order,config.groupingKey)
    /** R4-48: true when saving under this (normalized) name would replace an existing view,
     *  so the UI can surface that before the save. */
    fun savedViewNameExists(saved:List<HomeSavedView>,name:String):Boolean {
        val normalized=normalizeSavedViewName(name)
        return normalized.isNotEmpty() && saved.any {it.name.equals(normalized,ignoreCase=true)}
    }
    fun saveView(saved:List<HomeSavedView>,config:HomeSavedView):List<HomeSavedView> {
        val name=normalizeSavedViewName(config.name)
        if(name.isEmpty()) return saved
        val next=saved.filterNot {it.name.equals(name,ignoreCase=true)}+config.copy(name=name)
        return if(next.size<=HOME_SAVED_VIEW_MAX) next else next.drop(next.size-HOME_SAVED_VIEW_MAX)
    }
    fun removeView(saved:List<HomeSavedView>,name:String):List<HomeSavedView> = saved.filterNot {it.name==name}
    fun preview(config:HomeSavedView,chats:List<Conversation>,now:Instant,locked:(String)->Boolean,
                inFolder:(String)->Boolean={true},displayTitleFor:(Conversation)->String={it.title},
                locale:java.util.Locale=java.util.Locale.getDefault(),sample:Int=3):HomeViewPreview {
        // Caller pre-applies its filter/search pass; this adds folder scope, view and order only.
        val scoped=if(config.folderId==null) chats else chats.filter {inFolder(it.id)}
        val result=apply(scoped,config.view,config.order,now,locked,displayTitleFor,locale)
        return HomeViewPreview(result.size,result.take(sample).map(displayTitleFor))
    }
    fun pushHistory(stack:List<HomeViewState>,state:HomeViewState):List<HomeViewState> {
        if(stack.lastOrNull()==state) return stack
        val next=stack+state
        return if(next.size<=HOME_VIEW_HISTORY_MAX) next else next.drop(next.size-HOME_VIEW_HISTORY_MAX)
    }
    fun popHistory(stack:List<HomeViewState>):Pair<HomeViewState?,List<HomeViewState>> =
        if(stack.isEmpty()) null to stack else stack.last() to stack.dropLast(1)
    /** R4-49: restore an entry picked from the session history: the state to apply, and the
     *  remaining history with that entry and everything after it dropped, so going back moves
     *  the point instead of piling old states on top. An unknown state leaves the stack as-is. */
    fun restoreFromHistory(stack:List<HomeViewState>,state:HomeViewState):Pair<HomeViewState?,List<HomeViewState>> {
        val index=stack.lastIndexOf(state)
        return if(index<0) null to stack else stack[index] to stack.subList(0,index)
    }
    fun describe(state:HomeViewState,folderNameFor:(String)->String?={null}):String {
        val parts=mutableListOf(state.view.label)
        if(state.order!=HomeLocalOrder.Existing) parts+=state.order.label
        if(state.filter!="All") parts+=state.filter
        state.folderId?.let {parts+=("Folder: "+(folderNameFor(it) ?: "unknown"))}
        if(state.groupingKey.isNotEmpty()) parts+=("Grouped: "+state.groupingKey)
        return parts.joinToString(" · ")
    }
}
