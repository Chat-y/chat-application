package com.example.chat.ui.chats.folders

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.chat.data.local.FolderEditToken
import com.example.chat.data.local.FolderSaveResult
import com.example.chat.data.local.FolderSaveStatus
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.chat.data.local.ChatFolder
import com.example.chat.data.local.ChatFolderStore
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.domain.models.Conversation

/** Local chat folders as a second chip row under the stock filter chips
 *  (Telegram parity, client-side only). "All" restores the stock list; a
 *  folder chip filters to its member conversations; long-press manages. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatFolderChips(
    theme: ThemeConfig,
    store: ChatFolderStore,
    conversations: List<Conversation>,
    selectedFolderId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    badgeFor: ((String) -> String?)? = null,
    // Reserved for an owner with a persistent order contract. This UI does not fake reordering.
    onReorder: ((List<String>) -> Unit)? = null,
    summaries: Map<String, String> = emptyMap(),
    selectedConversationIds: Set<String> = emptySet(),
    // New inspectors are opt-in. The caller must exclude hidden and locked chats.
    canInspectConversation: (String) -> Boolean = { false },
    // Null means incomplete or unverified account data: never infer stale IDs from a filtered list.
    resolvedConversationIds: Set<String>? = null,
    onOpenConversation: ((String) -> Unit)? = null,
) {
    val revision by store.revision.collectAsState()
    // Legacy getters stay readable; checked snapshots are required only for writes.
    val folders = remember(store, revision) { store.folders() }
    val selectedFolderIndex = folders.indexOfFirst { it.id == selectedFolderId } + 1
    var createOpen by remember(store) { mutableStateOf(false) }
    var manageFolder by remember(store) { mutableStateOf<ChatFolder?>(null) }
    var pickerOpen by remember(store) { mutableStateOf(false) }
    var membershipMode by remember(store) { mutableStateOf<String?>(null) }
    var duplicateFolder by remember(store) { mutableStateOf<ChatFolder?>(null) }
    val membershipByFolder = remember(store, revision) { folders.associate { it.id to store.memberIds(it.id) } }
    val inspectable = conversations.distinctBy { it.id }.filter { canInspectConversation(it.id) }
    val chipsState = rememberLazyListState()
    val chipsScope = rememberFolderWriteScope(store)

    val accent = Color(theme.accentColor)
    val card = Color(theme.cardColor)
    val surface = Color(theme.surfaceColor)
    val foreground = Color(theme.primaryTextColor)
    val onAccent = Color(theme.onAccentColor)

    Box(Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) false
        else {
            val target = when (event.key) {
                Key.DirectionLeft -> (selectedFolderIndex - 1).coerceAtLeast(0)
                Key.DirectionRight -> (selectedFolderIndex + 1).coerceAtMost(folders.size)
                Key.MoveHome -> 0
                Key.MoveEnd -> folders.size
                else -> null
            }
            if (target == null) false else {
                onSelect(folders.getOrNull(target - 1)?.id)
                chipsScope.launch { chipsState.animateScrollToItem(target + 1) }
                true
            }
        }
    }) {
    LazyRow(
        modifier.fillMaxWidth().selectableGroup().semantics {contentDescription="Chat folders"}.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        state = chipsState,
    ) {
        item {
            Row {
                TextButton(
                    enabled = selectedFolderIndex > 0,
                    onClick = {
                        onSelect(folders.getOrNull(selectedFolderIndex - 2)?.id)
                    },
                ) { Text("Previous folder", color = if (selectedFolderIndex > 0) foreground else foreground.copy(alpha = 0.38f)) }
                TextButton(
                    enabled = selectedFolderIndex < folders.size,
                    onClick = {
                        folders.getOrNull(selectedFolderIndex)?.let { onSelect(it.id) }
                    },
                ) { Text("Next folder", color = if (selectedFolderIndex < folders.size) foreground else foreground.copy(alpha = 0.38f)) }
            }
        }
        item { FolderChip("All folders", selectedFolderId == null, accent, card, surface, foreground, onAccent) { onSelect(null) } }
        items(folders,key={it.id}) { folder ->
            Box(
                Modifier
                    .background(if (folder.id == selectedFolderId) accent else card, RoundedCornerShape(16.dp))
                    .border(1.dp, surface, RoundedCornerShape(16.dp))
                    .heightIn(min=48.dp)
                    .semantics {selected=folder.id==selectedFolderId}
                    .combinedClickable(role=Role.Tab,
                        onClick={onSelect(if(selectedFolderId==folder.id) null else folder.id)},
                        onLongClickLabel="Manage folder",onLongClick={manageFolder=folder})
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Column {
                Text(folder.name, color = if (folder.id == selectedFolderId) onAccent else foreground, fontSize = 13.sp)
                    badgeFor?.invoke(folder.id)?.takeIf { it.isNotBlank() }?.let { badge ->
                        Text(badge, color = if (folder.id == selectedFolderId) onAccent else foreground, fontSize = 11.sp)
                    }
                }
            }
        }
        item {FolderChip("+ New folder", false, accent, card, surface, foreground, onAccent) { createOpen = true }}
        item { FolderChip("Find folder", false, accent, card, surface, foreground, onAccent) { pickerOpen = true } }
        item { FolderChip("Folder membership", false, accent, card, surface, foreground, onAccent) { membershipMode = "Inspect" } }
        item { FolderChip("Unfiled", false, accent, card, surface, foreground, onAccent) { membershipMode = "Unfiled" } }
    }

    }

    if (pickerOpen) {
        var query by remember(store) { mutableStateOf("") }
        val matches = com.example.chat.ui.chats.home.HomeFolderPolicy.folderPickerFilter(query, folders)
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Find a local folder") },
            text = {
                Column {
                    OutlinedTextField(value = query, onValueChange = { query = it.take(100) },
                        label = { Text("Folder name, not messages") }, singleLine = true)
                    Text("Current: ${folders.firstOrNull { it.id == selectedFolderId }?.name ?: "All folders"}")
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(matches, key = { it.id }) { folder ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onSelect(folder.id); pickerOpen = false }) { Text(folder.name) }
                                TextButton(onClick = { manageFolder = folder; pickerOpen = false }) { Text("Manage") }
                            }
                        }
                        if (matches.isEmpty()) item { Text("No local folders match.") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickerOpen = false }) { Text("Close") } },
        )
    }

    membershipMode?.let { mode ->
        FolderMembershipToolsDialog(
            store = store, folders = folders, membershipByFolder = membershipByFolder,
            conversations = inspectable, selectedConversationIds = selectedConversationIds,
            selectedFolderId = selectedFolderId, initialMode = mode,
            onOpenConversation = onOpenConversation?.let { open ->
                { id -> if (canInspectConversation(id)) open(id) }
            }, knownConversationIds = resolvedConversationIds,
            onDismiss = { membershipMode = null },
        )
    }

    duplicateFolder?.let { source ->
        var copyName by remember(store, source.id) { mutableStateOf((source.name.take(18) + " copy").take(24)) }
        var copyError by remember(store, source.id) { mutableStateOf<String?>(null) }
        val writer = remember(store, source.id) { FolderWriteSession(store) }
        val copiedIds = if (resolvedConversationIds == null) membershipByFolder[source.id].orEmpty()
            else writer.snapshot.members[source.id].orEmpty()
        val visibleMembers = inspectable.filter { it.id in copiedIds }
        AlertDialog(
            onDismissRequest = { if (!writer.busy) duplicateFolder = null },
            title = { Text("Duplicate local folder") },
            text = {
                Column {
                    OutlinedTextField(value = copyName, onValueChange = { copyName = it; copyError = null },
                        label = { Text("New folder name (24 characters)") }, singleLine = true, enabled = !writer.locked)
                    Text("Copy ${copiedIds.size} member references to a new local folder. No chats are created, invited or synced.")
                    Text("${copiedIds.size - visibleMembers.size} references are not shown; hidden or locked names stay private.")
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                        items(visibleMembers, key = { it.id }) { Text(it.title) }
                    }
                    copyError?.let { Text(it, color = Color(theme.dangerColor)) }
                    FolderSaveFeedback(writer, chipsScope, { resolvedConversationIds },
                        onSaved = { id -> duplicateFolder = null; onSelect(id) })
                }
            },
            confirmButton = {
                TextButton(enabled = !writer.locked, onClick = {
                    if ((if (resolvedConversationIds == null) folders else writer.snapshot.folders).none { it.id == source.id }) {
                        copyError = "The source is no longer available. Close and choose again."
                    } else {
                        writer.save(chipsScope, listOf(FolderDraft(null, copyName, copiedIds)),
                            resolvedConversationIds) { id -> duplicateFolder = null; onSelect(id) }
                    }
                }) { Text("Create local copy") }
            },
            dismissButton = { TextButton(enabled = !writer.busy, onClick = { duplicateFolder = null }) { Text("Cancel") } },
        )
    }

    if (createOpen) {
        var name by remember { mutableStateOf("") }
        val writer = remember(store) { FolderWriteSession(store) }
        AlertDialog(
            onDismissRequest = { if (!writer.busy) createOpen = false },
            title = { Text("New folder") },
            text = {
                Column {
                    Text("Folders are stored on this device only.")
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        enabled = !writer.locked,
                        label = { Text("Folder name (24 characters)") }, singleLine = true)
                    FolderSaveFeedback(writer, chipsScope, { resolvedConversationIds },
                        onSaved = { id -> onSelect(id); createOpen = false })
                }
            },
            confirmButton = {
                TextButton(enabled = !writer.locked, onClick = {
                    writer.save(chipsScope, listOf(FolderDraft(null, name, emptySet(), writeMembers = false)),
                        resolvedConversationIds) { id -> onSelect(id); createOpen = false }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(enabled = !writer.busy, onClick = { createOpen = false }) { Text("Cancel") } },
        )
    }

    manageFolder?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        var memberQuery by remember(folder.id) { mutableStateOf("") }
        var confirmDelete by remember(folder.id) {mutableStateOf(false)}
        val writer = remember(store, folder.id) { FolderWriteSession(store) }
        val originalMembers = if (resolvedConversationIds == null) membershipByFolder[folder.id].orEmpty()
            else writer.snapshot.members[folder.id].orEmpty()
        var reviewStale by remember(store, folder.id) { mutableStateOf(false) }
        val staleIds = resolvedConversationIds?.let { originalMembers - it }
        var members by remember(store,folder.id) {mutableStateOf(originalMembers)}
        val candidates = inspectable.filter { it.title.contains(memberQuery.trim(), ignoreCase = true) }
        if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},
            title={Text("Delete folder?")},text={Text("This removes the folder only. Your chats are kept.")},
            confirmButton={TextButton(onClick={store.deleteFolder(folder.id);if(selectedFolderId==folder.id) onSelect(null);manageFolder=null}) {Text("Delete")}},
            dismissButton={TextButton(onClick={confirmDelete=false}) {Text("Cancel")}})
        AlertDialog(
            onDismissRequest = { if (!writer.busy) manageFolder = null },
            title = { Text("Manage folder") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Column {
                    Text("Folders are stored on this device only.")
                    // Caller summaries may include protected chats. Derive only from authorized rows.
                    Text(com.example.chat.ui.chats.home.HomeFolderPolicy.folderActivitySummary(store, folder.id, inspectable), fontSize = 13.sp)
                    FolderSaveFeedback(writer, chipsScope, { resolvedConversationIds },
                        onReload = {
                            name = writer.snapshot.folders.firstOrNull { it.id == folder.id }?.name ?: name
                            members = writer.snapshot.members[folder.id].orEmpty()
                            reviewStale = false
                        }, onSaved = { manageFolder = null })
                    Text("Manual membership: chats were explicitly included in this local folder. No rule evaluator is active.", fontSize = 12.sp)
                    TextButton(enabled = !writer.locked, onClick = { duplicateFolder = folder; manageFolder = null }) { Text("Duplicate folder") }
                    if (staleIds == null) Text("Missing-member count unavailable until this account's complete chat IDs are loaded.", fontSize = 12.sp)
                    else {
                        Text("Missing members: ${staleIds.size}", fontSize = 12.sp)
                        if (staleIds.isNotEmpty()) TextButton(enabled = !writer.locked, onClick = { reviewStale = true }) { Text("Review stale reference removal") }
                    }
                    OutlinedTextField(value = name, enabled = !writer.locked, onValueChange = { name = it },
                        label = { Text("Folder name (24 characters)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = memberQuery, onValueChange = { memberQuery = it.take(100) },
                        label = { Text("Add chats") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(
                        "Pending changes: ${(members - originalMembers).size} added, ${(originalMembers - members).size} removed. Cancel closes without undoing any already accepted save.",
                        color = foreground,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(top = 4.dp)) {
                        items(candidates, key = { it.id }) { conversation ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !writer.locked) { members=if(conversation.id in members) members-conversation.id else members+conversation.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = conversation.id in members,
                                    enabled = !writer.locked,
                                    onCheckedChange = { members=if(conversation.id in members) members-conversation.id else members+conversation.id },
                                )
                                Text(conversation.title, color = foreground, fontSize = 14.sp, maxLines = 1)
                            }
                        }
                    }
                }
                }
            },
            confirmButton = {
                TextButton(enabled = !writer.locked, onClick = {
                    writer.save(chipsScope, listOf(FolderDraft(folder.id, name, members)),
                        resolvedConversationIds) { manageFolder = null }
                }) { Text("Done") }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = !writer.busy, onClick = { manageFolder = null }) { Text("Cancel") }
                TextButton(enabled = !writer.locked, onClick = {
                    confirmDelete=true
                }) { Text("Delete") }
                }
            },
        )
        if (reviewStale && staleIds != null) AlertDialog(
            onDismissRequest = { reviewStale = false },
            title = { Text("Remove stale references?") },
            text = { Text("Remove ${staleIds.size} references that do not resolve in this active account. This does not mean chats were deleted remotely. Changes remain pending until Done.") },
            confirmButton = { TextButton(onClick = { members = members - staleIds; reviewStale = false }) { Text("Stage removal") } },
            dismissButton = { TextButton(onClick = { reviewStale = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FolderChip(
    label: String,
    selected: Boolean,
    accent: Color,
    card: Color,
    surface: Color,
    foreground: Color,
    onAccent: Color,
    onTap: () -> Unit,
) {
    Box(
        Modifier
            .background(if (selected) accent else card, RoundedCornerShape(16.dp))
            .border(1.dp, surface, RoundedCornerShape(16.dp))
            .heightIn(min=48.dp)
            .selectable(selected=selected,role=Role.Tab,onClick=onTap)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (selected) onAccent else foreground, fontSize = 13.sp)
    }
}

/** Local references only. The caller has already removed protected chats from this list. */
@Composable
private fun FolderMembershipToolsDialog(
    store: ChatFolderStore,
    folders: List<ChatFolder>,
    membershipByFolder: Map<String, Set<String>>,
    conversations: List<Conversation>,
    selectedConversationIds: Set<String>,
    selectedFolderId: String?,
    initialMode: String,
    onOpenConversation: ((String) -> Unit)?,
    knownConversationIds: Set<String>?,
    onDismiss: () -> Unit,
) {
    val scope = rememberFolderWriteScope(store)
    var writer by remember(store) { mutableStateOf(FolderWriteSession(store)) }
    var mode by remember(store) { mutableStateOf(initialMode) }
    var folderQuery by remember(store) { mutableStateOf("") }
    var chatQuery by remember(store) { mutableStateOf("") }
    var chosenIds by remember(store) { mutableStateOf(selectedConversationIds) }
    var chosenFolderIds by remember(store) { mutableStateOf(emptySet<String>()) }
    var action by remember(store) { mutableStateOf("Add these chats") }
    var pending by remember(store) { mutableStateOf<Map<String, Set<String>>?>(null) }
    var expected by remember(store) { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var reviewedIds by remember(store) { mutableStateOf(emptySet<String>()) }
    var error by remember(store) { mutableStateOf<String?>(null) }
    val visibleIds = conversations.map { it.id }.toSet()
    val unavailableSelection = chosenIds - visibleIds
    val matchingFolders = com.example.chat.ui.chats.home.HomeFolderPolicy.folderPickerFilter(folderQuery, folders)
    val unfiled = com.example.chat.ui.chats.home.HomeFolderPolicy.chatsOutsideFolders(store, conversations)
    val shownChats = (if (mode == "Unfiled") unfiled else conversations)
        .filter { it.title.contains(chatQuery.trim(), ignoreCase = true) }
    val currentFolder = folders.firstOrNull { it.id == selectedFolderId }
    val targetFolderIds = if (action == "Remove from this folder") setOfNotNull(currentFolder?.id) else chosenFolderIds


    AlertDialog(
        onDismissRequest = { if (!writer.busy) onDismiss() },
        title = { Text("Local folder membership") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("Inspect", "Unfiled", "Edit memberships").forEach { value ->
                        TextButton(onClick = { mode = value; error = null }) { Text(if (mode == value) "[$value]" else value) }
                    }
                }
                Text("Only caller-authorized visible chats are shown. Folder labels are local metadata, not unread counts.", fontSize = 12.sp)
                OutlinedTextField(value = chatQuery, onValueChange = { chatQuery = it.take(100) },
                    label = { Text("Visible chat names") }, singleLine = true)
                if (mode == "Edit memberships") {
                    Text("${chosenIds.size} selected chats; ${targetFolderIds.size} target folders", fontSize = 12.sp)
                    if (unavailableSelection.isNotEmpty()) Text("Selection includes unavailable or protected chats. Clear it before choosing visible chats.", fontSize = 12.sp)
                    TextButton(onClick = { chosenIds = emptySet(); error = null }) { Text("Clear selection") }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("Add these chats", "Replace folder members", "Remove from this folder").forEach { value ->
                            TextButton(enabled = value != "Remove from this folder" || currentFolder != null,
                                onClick = { action = value; error = null }) { Text(if (action == value) "[$value]" else value) }
                        }
                    }
                    if (action == "Remove from this folder") Text("Current folder: ${currentFolder?.name ?: "None"}")
                    else OutlinedTextField(value = folderQuery, onValueChange = { folderQuery = it.take(100) },
                        label = { Text("Find target folders by name") }, singleLine = true)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    if (mode == "Edit memberships" && action != "Remove from this folder") {
                        items(matchingFolders, key = { "folder:${it.id}" }) { folder ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = folder.id in chosenFolderIds,
                                    onCheckedChange = { checked -> chosenFolderIds = if (checked) chosenFolderIds + folder.id else chosenFolderIds - folder.id })
                                Text(folder.name)
                            }
                        }
                        if (matchingFolders.isEmpty()) item { Text("No local folders match.") }
                    }
                    items(shownChats, key = { "chat:${it.id}" }) { chat ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (mode == "Edit memberships") Checkbox(checked = chat.id in chosenIds,
                                    onCheckedChange = { checked -> chosenIds = if (checked) chosenIds + chat.id else chosenIds - chat.id })
                                Text(chat.title, modifier = Modifier.weight(1f))
                                if (mode == "Unfiled" && onOpenConversation != null) TextButton(onClick = { onDismiss(); onOpenConversation(chat.id) }) { Text("Open") }
                            }
                            if (mode == "Inspect") {
                                val labels = folders.filter { chat.id in membershipByFolder[it.id].orEmpty() }.map { it.name }
                                Text(if (labels.isEmpty()) "Unfiled" else labels.joinToString(" · "), fontSize = 12.sp)
                                Text("Manual inclusion only; no rule-based membership is evaluated.", fontSize = 11.sp)
                            }
                        }
                    }
                    if (shownChats.isEmpty()) item { Text("No visible chats in this scope.") }
                }
                error?.let { Text(it, fontSize = 12.sp) }
                if (knownConversationIds == null) Text("Complete chat IDs are unavailable. Legacy saves confirm memory only, not disk storage or membership validation.")
            }
        },
        confirmButton = {
            if (mode == "Edit memberships") TextButton(
                enabled = chosenIds.isNotEmpty() && unavailableSelection.isEmpty() && targetFolderIds.isNotEmpty() && !store.isCorrupted(),
                onClick = {
                    val activeFolders = store.folders().map { it.id }.toSet()
                    if (!activeFolders.containsAll(targetFolderIds)) error = "A target folder is no longer available. Choose again."
                    else {
                        writer = FolderWriteSession(store)
                        expected = targetFolderIds.associateWith { if (knownConversationIds == null) store.memberIds(it) else writer.snapshot.members[it].orEmpty() }
                        reviewedIds = chosenIds.toSet()
                        pending = expected.mapValues { (_, before) ->
                            when (action) {
                                "Replace folder members" -> chosenIds.toSet()
                                "Remove from this folder" -> before - chosenIds
                                else -> before + chosenIds
                            }
                        }
                    }
                },
            ) { Text("Preview changes") }
            else TextButton(onClick = { mode = "Edit memberships" }) { Text("Edit memberships") }
        },
        dismissButton = { TextButton(enabled = !writer.busy, onClick = onDismiss) { Text("Close") } },
    )
    pending?.let { changes ->
        AlertDialog(
            onDismissRequest = { if (!writer.busy) pending = null },
            title = { Text("Review local membership changes") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    item {
                        Text("$action in ${changes.size} folders. Chats are not deleted, moved, archived or synced. Folder saves run separately, not as one transaction.")
                        if (action == "Replace folder members") Text("Replacement removes existing references not in the selected list, including references not shown here.")
                        FolderSaveFeedback(writer, scope,
                            { knownConversationIds?.takeIf { visibleIds.containsAll(reviewedIds) } },
                            onReload = { pending = null; error = "Folder data reloaded. Preview the selection again." },
                            onSaved = {
                                error = if (writer.durableConfirmed) "All ${changes.size} local folder saves are confirmed durable."
                                else "Updated local folders in memory only. Durable storage and complete member validation are not confirmed."
                                pending = null
                            })
                    }
                    items(changes.keys.toList(), key = { it }) { id ->
                        val before = expected[id].orEmpty()
                        val after = changes[id].orEmpty()
                        Text("${folders.firstOrNull { it.id == id }?.name ?: "Unavailable folder"}: ${before.size} → ${after.size}; ${(after - before).size} added, ${(before - after).size} removed")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !writer.locked && writer.result == null, onClick = {
                    if (!visibleIds.containsAll(reviewedIds)) {
                        error = "The visible chat scope changed. Review your selection again."
                        pending = null
                    } else if (knownConversationIds == null && changes.keys.any { id ->
                        store.folders().none { it.id == id } || store.memberIds(id) != expected[id]
                    }) {
                        error = "Folder membership changed while reviewing. Nothing was changed; review again."
                        pending = null
                    } else if (knownConversationIds != null && changes.keys.any { id ->
                        val name = writer.snapshot.folders.firstOrNull { it.id == id }?.name
                        name == null || boundedEditorName(name) != name
                    }) {
                        error = "A target folder needs an explicit name review before membership can be saved. Manage that folder first."
                        pending = null
                    } else {
                        writer.save(scope, changes.map { (id, ids) ->
                            FolderDraft(id, (if (knownConversationIds == null) folders else writer.snapshot.folders).firstOrNull { it.id == id }?.name.orEmpty(), ids, rename = false)
                        }, knownConversationIds) {
                            error = if (writer.durableConfirmed) "All ${changes.size} local folder saves are confirmed durable."
                                else "Updated local folders in memory only. Durable storage and complete member validation are not confirmed."
                            pending = null
                        }
                    }
                }) { Text(if (action == "Replace folder members") "Confirm replacement" else "Confirm local changes") }
            },
            dismissButton = { TextButton(enabled = !writer.busy, onClick = { pending = null }) { Text("Cancel") } },
        )
    }

}

private data class FolderDraft(
    val id: String?, val name: String, val members: Set<String>,
    val rename: Boolean = true, val writeMembers: Boolean = true,
)

/** One editor owns a checked baseline. Completed durable writes are never replayed. */
private class FolderWriteSession(private val store: ChatFolderStore) {
    var snapshot by mutableStateOf(store.editSnapshot())
        private set
    var result by mutableStateOf<FolderSaveResult?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var durableConfirmed by mutableStateOf(false)
        private set
    var completed by mutableStateOf(0)
        private set
    private var requests: List<FolderDraft> = emptyList()
    private var token: FolderEditToken = snapshot.token
    private var lastId: String? = null
    val locked: Boolean get() = busy || result?.status in setOf(
        FolderSaveStatus.STORAGE_FAILURE, FolderSaveStatus.CONFLICT,
        FolderSaveStatus.INVALID_DOCUMENT, FolderSaveStatus.NOT_FOUND,
    )

    fun reload() {
        if (busy) return
        snapshot = store.editSnapshot()
        token = snapshot.token
        result = null
        requests = emptyList()
        completed = 0
    }

    fun save(scope: CoroutineScope, drafts: List<FolderDraft>, knownIds: Set<String>?, onSaved: (String?) -> Unit) {
        if (locked) return
        durableConfirmed = false
        if (knownIds == null) {
            saveLegacy(scope, drafts, onSaved)
            return
        }
        requests = drafts.map { it.copy(members = it.members.toSet()) }
        completed = 0
        run(scope, knownIds.toSet(), onSaved)
    }

    fun retry(scope: CoroutineScope, knownIds: Set<String>?, onSaved: (String?) -> Unit) {
        if (busy || knownIds == null || result?.status != FolderSaveStatus.STORAGE_FAILURE) return
        val retry = result?.retryToken ?: return
        token = retry
        run(scope, knownIds.toSet(), onSaved)
    }

    // Compatibility path only: retains the pre-existing unvalidated, in-memory API.
    // It is not an atomic or durable substitute for saveFolderDurably.
    private fun saveLegacy(scope: CoroutineScope, drafts: List<FolderDraft>, onSaved: (String?) -> Unit) {
        busy = true
        scope.launch {
            try {
                val accepted = withContext(Dispatchers.IO) {
                    var id: String? = null
                    var count = 0
                    for (draft in drafts) {
                        id = draft.id
                        if (id == null) id = store.createFolder(draft.name)?.id
                        else if (draft.rename && !store.renameFolder(id, draft.name)) break
                        if (id == null) break
                        if (draft.writeMembers) {
                            store.setMembers(id, draft.members)
                            if (store.memberIds(id) != draft.members) break
                        }
                        count++
                    }
                    Triple(count, id, store.isCorrupted())
                }
                result = FolderSaveResult(if (accepted.third) FolderSaveStatus.INVALID_DOCUMENT
                    else FolderSaveStatus.ACCEPTED_IN_MEMORY)
                if (accepted.first == drafts.size) onSaved(accepted.second)
            } finally {
                busy = false
            }
        }
    }

    private fun run(scope: CoroutineScope, knownIds: Set<String>, onSaved: (String?) -> Unit) {
        busy = true
        scope.launch {
            try {
                while (completed < requests.size) {
                    val draft = requests[completed]
                    val saved = withContext(Dispatchers.IO) {
                        store.saveFolderDurably(draft.name, draft.members, knownIds, token, draft.id)
                    }
                    result = saved
                    if (saved.status != FolderSaveStatus.DURABLE) {
                        // A failed create may already have an ID in preferences memory.
                        // Keep that ID for exact-token retry instead of creating a duplicate.
                        if (saved.status == FolderSaveStatus.STORAGE_FAILURE) {
                            requests = requests.toMutableList().also {
                                it[completed] = draft.copy(id = saved.folderId)
                            }
                        }
                        return@launch
                    }
                    lastId = saved.folderId
                    completed++
                    if (completed == requests.size) {
                        durableConfirmed = true
                        onSaved(lastId)
                        return@launch
                    }
                    // Do not adopt another editor's state between individual batch writes.
                    // The snapshot is safe to advance only if it is exactly our intended save.
                    val next = store.editSnapshot()
                    val id = saved.folderId
                    val expectedFolders = if (draft.id == null && id != null)
                        snapshot.folders + ChatFolder(id, boundedEditorName(draft.name))
                    else snapshot.folders.map { if (it.id == draft.id) it.copy(name = boundedEditorName(draft.name)) else it }
                    val expectedMembers = snapshot.members + (id.orEmpty() to draft.members)
                    if (next.issues.isNotEmpty() || next.folders != expectedFolders || next.members != expectedMembers) {
                        result = FolderSaveResult(FolderSaveStatus.CONFLICT, id)
                        return@launch
                    }
                    snapshot = next
                    token = next.token
                }
            } finally {
                busy = false
            }
        }
    }
}

@Composable
private fun FolderSaveFeedback(
    writer: FolderWriteSession,
    scope: CoroutineScope,
    knownIds: () -> Set<String>?,
    onReload: () -> Unit = {},
    onSaved: (String?) -> Unit,
) {
    if (knownIds() == null) Text("Complete chat IDs are unavailable. Legacy saves confirm memory only, not disk storage or membership validation.", fontSize = 12.sp)
    if (writer.busy) Text(if (knownIds() == null) "Saving locally..." else "Saving locally. Waiting for durable storage confirmation...", fontSize = 12.sp)
    if (writer.snapshot.issues.isNotEmpty()) Text("Folder data needs review. Existing saved bytes are preserved; no reset is offered here.", fontSize = 12.sp)
    val status = writer.result?.status
    val message = when (status) {
        FolderSaveStatus.STORAGE_FAILURE -> "Storage did not confirm this save. It may be in memory only. Your input is retained. Free space, then retry the same edit."
        FolderSaveStatus.CONFLICT -> "Folder data changed in another editor. No automatic merge or retry. Reload the current data and review again."
        FolderSaveStatus.INVALID_DOCUMENT -> "Folder data could not be safely rewritten. Saved data is preserved."
        FolderSaveStatus.INVALID_NAME -> "Choose a nonempty, unique folder name. Names are limited to 24 user-perceived characters."
        FolderSaveStatus.INVALID_MEMBERS -> "Some member IDs are not in this account's complete chat list. Review references; none were silently dropped."
        FolderSaveStatus.NOT_FOUND -> "This folder no longer exists. Reload before choosing another folder."
        FolderSaveStatus.ACCEPTED_IN_MEMORY -> "Legacy save was not fully verified. Some changes may have applied in memory. Review local folders before retrying; durable storage and complete member validation are not confirmed."
        else -> null
    }
    message?.let { Text(it, fontSize = 12.sp) }
    if (status != null && status != FolderSaveStatus.DURABLE) {
        Text("${writer.completed} folder saves confirmed durable in this operation.", fontSize = 12.sp)
        if (status == FolderSaveStatus.STORAGE_FAILURE) TextButton(
            enabled = !writer.busy && knownIds() != null && writer.result?.retryToken != null,
            onClick = { writer.retry(scope, knownIds(), onSaved) },
        ) { Text("Retry exact save") }
        TextButton(enabled = !writer.busy, onClick = { writer.reload(); onReload() }) {
            Text("Reload current data and review")
        }
    }
}

private fun boundedEditorName(name: String): String {
    val trimmed = name.trim()
    val iterator = android.icu.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
    iterator.setText(trimmed)
    var end = iterator.first()
    repeat(24) {
        val next = iterator.next()
        if (next == android.icu.text.BreakIterator.DONE) return trimmed
        end = next
    }
    return trimmed.substring(0, end)
}

/** Cancel callbacks on account switch or disposal; an already running disk write
 * stays scoped to its original store and never selects a folder in the new account. */
@Composable
private fun rememberFolderWriteScope(store: ChatFolderStore): CoroutineScope {
    val parent = rememberCoroutineScope()
    val scope = remember(store) { CoroutineScope(parent.coroutineContext + Job(parent.coroutineContext[Job])) }
    DisposableEffect(scope) { onDispose { scope.cancel() } }
    return scope
}
