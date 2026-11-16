package com.example.chat.ui.chats.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import com.example.chat.ui.kit.ChatyTextButton as TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Opt-in, session-only views. Never writes chat, account or server state. */
@Composable
fun HomeQuickViewDialog(view:HomeQuickView,order:HomeLocalOrder,summary:HomeSnapshotSummary,
    onView:(HomeQuickView)->Unit,onOrder:(HomeLocalOrder)->Unit,onReset:()->Unit,onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Home view options")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("This account's loaded, visible chats only. Hidden chats are excluded. These choices last for this Home session.")
            Text("${summary.chats} chats · ${summary.pinned} pinned · ${summary.muted} muted · ${summary.drafts} unlocked drafts")
            HorizontalDivider(Modifier.padding(vertical=12.dp))
            Text("Quick view",Modifier.semantics {heading()})
            HomeQuickView.entries.forEach {option ->
                TextButton(onClick={onView(option)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics(mergeDescendants=true) {selected=(view==option)},
                    label=(if(view==option) "Selected: " else "")+option.label)
            }
            Text("Order within pinned and recent sections",Modifier.semantics {heading()})
            HomeLocalOrder.entries.forEach {option ->
                TextButton(onClick={onOrder(option)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics(mergeDescendants=true) {selected=(order==option)},
                    label=(if(order==option) "Selected: " else "")+option.label)
            }
            Text("Activity is based on each chat's last loaded message timestamp, not complete remote history.")
        }
    },confirmButton={TextButton(onClick=onDismiss,label="Done")},
      dismissButton={TextButton(onClick=onReset,label="Reset view")})
}

/** R4-47/48: named session views. Save the current combination, preview before applying.
 *  Session-only: names and choices are cleared on account switch; nothing is persisted,
 *  and no search text or chat content is stored in a saved view. */
@Composable
fun HomeSavedViewsDialog(saved:List<HomeSavedView>,
    onSave:(String)->Unit,onPreview:(HomeSavedView)->Unit,onDelete:(String)->Unit,onDismiss:()->Unit) {
    val nameState=remember {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Saved Home views")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Named combinations of filter, folder and order for this account's Home session. They are cleared when you switch accounts and never store search text or chat content.")
            HorizontalDivider(Modifier.padding(vertical=12.dp))
            OutlinedTextField(value=nameState.value,onValueChange={nameState.value=it},
                label={Text("Name this view")},singleLine=true,modifier=Modifier.fillMaxWidth())
            if(HomeQuickViewPolicy.savedViewNameExists(saved,nameState.value))
                Text("A saved view with this name already exists; saving replaces it.")
            TextButton(onClick={onSave(nameState.value);nameState.value=""},enabled=nameState.value.isNotBlank(),
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),label="Save current view")
            HorizontalDivider(Modifier.padding(vertical=12.dp))
            if(saved.isEmpty()) Text("No saved views yet.")
            saved.forEach {config ->
                Text(config.name)
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick={onPreview(config)},modifier=Modifier.weight(1f).heightIn(min=48.dp),label="Preview")
                    TextButton(onClick={onDelete(config.name)},modifier=Modifier.weight(1f).heightIn(min=48.dp),label="Delete")
                }
            }
            Text("Applying a saved view always goes through the preview first.")
        }
    },confirmButton={TextButton(onClick=onDismiss,label="Done")})
}

/** R4-48: safe preview of a named view before it is applied. Counts and privacy-safe titles only.
 *  Applying changes presentation only, never chat membership or message state. */
@Composable
fun HomeViewPreviewDialog(config:HomeSavedView,preview:HomeViewPreview,onApply:()->Unit,onDismiss:()->Unit,
    confirmLabel:String="Apply view") {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Preview: "+config.name)},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(HomeQuickViewPolicy.describe(HomeViewState(config.filter,config.folderId,config.view,config.order,config.groupingKey)))
            Text("${preview.matched} unlocked chats in this safe preview. Locked and hidden chats are left out, so this is not the exact total the applied view will show.")
            if(preview.sampleTitles.isNotEmpty()) {
                Text("Sample of unlocked chats:")
                preview.sampleTitles.forEach {Text("· "+it)}
            }
            Text("Applying only changes how this Home list looks for this session. No chats, folders or messages change.")
        }
    },confirmButton={TextButton(onClick=onApply,label=confirmLabel)},
      dismissButton={TextButton(onClick=onDismiss,label="Back")})
}

/** R4-49: session back-history of view changes, most recent first. Restoring an earlier state
 *  changes only the Home view; it never overrides app navigation, and account switch clears it. */
@Composable
fun HomeViewHistoryDialog(history:List<HomeViewState>,describe:(HomeViewState)->String,
    onRestore:(HomeViewState)->Unit,onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Recent Home views")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if(history.isEmpty()) Text("No earlier views yet. Filter, folder and order changes appear here for this session.")
            history.asReversed().forEach {state ->
                TextButton(onClick={onRestore(state)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                    label=describe(state))
            }
        }
    },confirmButton={TextButton(onClick=onDismiss,label="Done")})
}
