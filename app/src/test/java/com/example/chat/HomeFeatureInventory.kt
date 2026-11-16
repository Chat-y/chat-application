package com.example.chat
/** R2-48: inventory of Home features covered by source-only tests. No production behaviour.
 * Status values are honest: SOURCE_TESTED means a JVM test exists but nothing was run on a device. */
enum class HomeInventoryStatus {SOURCE_TESTED,NEEDS_DEVICE,HELD}
data class HomeInventoryItem(val id:String,val area:String,val status:HomeInventoryStatus,val note:String)
object HomeFeatureInventory {
 val items:List<HomeInventoryItem> = listOf(
  HomeInventoryItem("quick-views","HomeQuickViewPolicy",HomeInventoryStatus.SOURCE_TESTED,"Filters and order are pure functions."),
  HomeInventoryItem("scratchpad","HomeScratchpadPolicy",HomeInventoryStatus.SOURCE_TESTED,"4000 code point bound, session only."),
  HomeInventoryItem("locked-privacy","ConversationRow and search",HomeInventoryStatus.SOURCE_TESTED,"Checked by source text in HomeRound1SafetyTest."),
  HomeInventoryItem("process-recreation","Home state restore",HomeInventoryStatus.NEEDS_DEVICE,"Pure inputs are tested; real recreation needs an emulator."),
  HomeInventoryItem("visual-regression","Home screenshots",HomeInventoryStatus.NEEDS_DEVICE,"Scenario set is defined; no screenshots are captured here."),
  HomeInventoryItem("folder-store-durability","ChatFolderStore",HomeInventoryStatus.HELD,"Store edits are not part of this wave.")
 )
 fun byStatus(s:HomeInventoryStatus)=items.filter {it.status==s}
}
