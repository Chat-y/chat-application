package com.example.chat.ui.chats.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.data.local.ChatFolder
import com.example.chat.data.local.ChatFolderStore
import com.example.chat.data.local.FolderSaveResult
import com.example.chat.data.local.FolderSaveStatus
import com.example.chat.domain.models.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tags are the existing local folder IDs, not server groups or new conversations. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeTagFilters(
    theme: ThemeConfig,
    store: ChatFolderStore,
    enabled: Boolean,
    accountIdentity: Any?,
    conversations: List<Conversation>,
    visibleConversations: List<Conversation>,
    titles: Map<String, String>,
    selectedFilter: String,
    selectedTagId: String?,
    onFilter: (String) -> Unit,
    onTag: (String) -> Unit,
    onSaved: (String) -> Unit,
    filters: List<String> = listOf("All", "Unread", "Groups", "Direct"),
) {
    // The key disposes the sheet and its coroutine scope across account/store changes.
    key(accountIdentity, store) {
        val revision by store.revision.collectAsState()
        val tags = remember(store, revision) { store.folders() }
        var create by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<ChatFolder?>(null) }
        val accent = Color(theme.accentColor)
        val fg = Color(theme.primaryTextColor)
        val card = Color(theme.cardColor)
        LazyRow(
            modifier = Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "Chat filters and local tags" },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filters, key = { "filter:$it" }) { filter ->
                TagPill(filter, selectedTagId == null && selectedFilter == filter, accent, fg, card,
                    onClick = { onFilter(filter) })
            }
            if (enabled) {
                items(tags, key = { "tag:${it.id}" }) { tag ->
                    TagPill(tag.name, selectedTagId == tag.id, accent, fg, card,
                        onClick = { onTag(tag.id) }, onLongClick = { editing = tag })
                }
                item(key = "create-tag") {
                    TagPill("+", false, accent, fg, card, "Create local tag", onClick = { create = true })
                }
            }
        }
        if (create || editing != null) {
            val target = editing
            key(target?.id) {
                HomeTagEditor(theme, store, target, conversations, visibleConversations, titles,
                    onDismiss = { create = false; editing = null },
                    onSaved = { id -> create = false; editing = null; onSaved(id) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagPill(
    text: String, selected: Boolean, accent: Color, fg: Color, card: Color,
    label: String = text, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
) {
    // Box(
    // Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
    // .clip(RoundedCornerShape(percent = 50))
    // .background(if (selected) accent.copy(alpha = .18f) else card, RoundedCornerShape(percent = 50))
    // .border(1.dp, if (selected) accent.copy(alpha = .5f) else fg.copy(alpha = .12f), RoundedCornerShape(percent = 50))
    // .semantics { contentDescription = label; this.selected = selected }
    // .then(if (onLongClick == null) Modifier.selectable(selected, role = Role.Tab, onClick = onClick)
    // else Modifier.combinedClickable(role = Role.Tab, onClick = onClick,
    // onLongClickLabel = "Edit local tag", onLongClick = onLongClick))
    // .padding(horizontal = 14.dp, vertical = 12.dp),
    // contentAlignment = Alignment.Center,
    // ) { Text(text, color = if (selected) accent else fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
    com.example.chat.ui.kit.ChatyChoiceTile(
        label = text, selected = selected, onClick = onClick, role = Role.Tab,
        modifier = Modifier.semantics { contentDescription = label },
        onLongClick = onLongClick, onLongClickLabel = "Edit local tag",
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTagEditor(
    theme: ThemeConfig, store: ChatFolderStore, target: ChatFolder?,
    conversations: List<Conversation>, visibleConversations: List<Conversation>, titles: Map<String, String>,
    onDismiss: () -> Unit, onSaved: (String) -> Unit,
) {
    var snapshot by remember { mutableStateOf(store.editSnapshot()) }
    var name by remember { mutableStateOf(target?.name.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var members by remember { mutableStateOf(snapshot.members[target?.id].orEmpty()) }
    var result by remember { mutableStateOf<FolderSaveResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ownerAlive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(store) { onDispose { ownerAlive.set(false) } }
    val liveVisible by rememberUpdatedState(visibleConversations.map { it.id }.toSet())
    val liveKnown by rememberUpdatedState(conversations.map { it.id }.toSet())
    val originalMembers = snapshot.members[target?.id].orEmpty()
    val blocked = snapshot.issues.isNotEmpty() || result?.status in setOf(
        FolderSaveStatus.CONFLICT, FolderSaveStatus.INVALID_DOCUMENT, FolderSaveStatus.NOT_FOUND,
    )
    val retry = result?.status == FolderSaveStatus.STORAGE_FAILURE
    val locked = busy || blocked || retry
    val accent = Color(theme.accentColor)
    val fg = Color(theme.primaryTextColor)
    fun save() {
        if (!ownerAlive.get() || busy || blocked || name.isBlank()) return
        // Do not drop hidden/unloaded references during an edit. New selections must still be visible.
        val selected = members.toSet()
        if ((selected - originalMembers).any { it !in liveVisible } || (selected - originalMembers).any { it !in liveKnown }) {
            error = "Some selected chats are unavailable. Reload the chats before saving."
            return
        }
        val expected = if (retry) result?.retryToken ?: return else snapshot.token
        val id = if (retry) result?.folderId else target?.id
        val known = liveKnown.toSet()
        val saveName = name
        busy = true
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    if (ownerAlive.get()) store.saveFolderDurably(saveName, selected, known, expected, id, preserveExistingUnavailableMembers = target != null) else null
                }
                if (saved == null || !ownerAlive.get()) return@launch
                result = saved
                if (saved.status == FolderSaveStatus.DURABLE) saved.folderId?.let(onSaved)
                else error = when (saved.status) {
                    FolderSaveStatus.STORAGE_FAILURE -> "Not confirmed on disk. Keep this sheet open and retry the same save."
                    FolderSaveStatus.CONFLICT -> "Tags changed elsewhere. Reload before editing again."
                    FolderSaveStatus.INVALID_NAME -> "Use a unique tag name (up to 24 characters)."
                    FolderSaveStatus.INVALID_MEMBERS -> "Some chats are unavailable. Nothing was saved."
                    else -> "This tag cannot be saved. Reload to inspect the current state."
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
                error = "The save could not be confirmed. Reload the tag before trying again."
                result = com.example.chat.data.local.FolderSaveResult(FolderSaveStatus.CONFLICT, target?.id)
            } finally { busy = false }
        }
    }
    // Full-screen editor, not a bottom sheet. It owns Back and Save while open.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (!busy && !retry) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            containerColor = Color(theme.backgroundColor),
            topBar = {
                Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // Previous stock text action:
                    // TextButton(onClick = onDismiss, enabled = !busy && !retry) { Text("Back") }
                    com.example.chat.ui.kit.ChatyTextButton(
                        label = "Back",
                        onClick = onDismiss,
                        enabled = !busy && !retry,
                    )
                    Text(if (target == null) "Create tag" else "Edit tag", color = fg,
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    // Previous stock text action:
                    // TextButton(onClick = { save() }, enabled = !busy && !blocked && name.isNotBlank()) {
                    //                         Text(if (busy) "Saving..." else if (retry) "Retry save" else "Save")
                    //                     }
                    com.example.chat.ui.kit.ChatyTextButton(
                        label = if (busy) "Saving..." else if (retry) "Retry save" else "Save",
                        onClick = { save() },
                        enabled = !busy && !blocked && name.isNotBlank(),
                    )
                }
            },
        ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Local to this account. Existing chats only; no chats or invites are created.", color = fg)
            // OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth().padding(top = 12.dp),
            // label = { Text("Tag name") }, shape = RoundedCornerShape(100.dp), singleLine = true, enabled = !locked)
            com.example.chat.ui.kit.ChatyTaskField(
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
                value = name, onValueChange = { name = it; error = null },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                label = "Tag name", singleLine = true, enabled = !locked,
            )
            // OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
            // label = { Text("Search existing chat names") }, shape = RoundedCornerShape(100.dp), singleLine = true, enabled = !locked)
            com.example.chat.ui.kit.ChatyTaskField(
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = "Search existing chat names", singleLine = true, enabled = !locked,
            )
            Text("${members.intersect(liveVisible).size} visible chats selected", color = fg, modifier = Modifier.padding(vertical = 10.dp))
            val matches = visibleConversations.distinctBy { it.id }.filter {
                query.isBlank() || (titles[it.id] ?: it.title).contains(query, ignoreCase = true)
            }
            Column(Modifier.fillMaxWidth()) {
                matches.forEach { chat ->
                    val checked = chat.id in members
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(checked, enabled = !locked, role = Role.Checkbox) {
                            members = if (it) members + chat.id else members - chat.id
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).background(if (checked) accent else Color.Transparent, CircleShape)
                            .border(2.dp, if (checked) accent else fg.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                            if (checked) Text("✓", color = Color(theme.onAccentColor))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(titles[chat.id] ?: chat.title, color = fg, modifier = Modifier.weight(1f))
                    }
                }
                if (matches.isEmpty()) Text("No existing chats match.", color = fg)
            }
            /* Previous nested fixed-height list retained.
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(matches, key = { it.id }) { chat ->
                    val checked = chat.id in members
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(checked, enabled = !locked, role = Role.Checkbox) {
                            members = if (it) members + chat.id else members - chat.id
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).background(if (checked) accent else Color.Transparent, CircleShape)
                            .border(2.dp, if (checked) accent else fg.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                            if (checked) Text("✓", color = Color(theme.onAccentColor))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(titles[chat.id] ?: chat.title, color = fg, modifier = Modifier.weight(1f))
                    }
                }
                if (matches.isEmpty()) item { Text("No existing chats match.", color = fg) }
            }
            */
            if (snapshot.issues.isNotEmpty()) Text("Stored tags need review. No data will be rewritten.", color = fg)
            error?.let { Text(it, color = Color(theme.dangerColor), modifier = Modifier.padding(top = 8.dp)) }
            if (blocked) // Previous stock text action:
            // TextButton(onClick = {
            //                 snapshot = store.editSnapshot(); result = null; error = null
            //                 name = snapshot.folders.firstOrNull { it.id == target?.id }?.name.orEmpty()
            //                 members = snapshot.members[target?.id].orEmpty()
            //             }) { Text("Reload tag") }
            com.example.chat.ui.kit.ChatyTextButton(
                label = "Reload tag",
                onClick = {
                snapshot = store.editSnapshot(); result = null; error = null
                name = snapshot.folders.firstOrNull { it.id == target?.id }?.name.orEmpty()
                members = snapshot.members[target?.id].orEmpty()
            },
            )

        }
        }
    }
}

/* Before sheet/retry/preserved-members review repair.
package com.example.chat.ui.chats.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.data.local.ChatFolder
import com.example.chat.data.local.ChatFolderStore
import com.example.chat.data.local.FolderSaveResult
import com.example.chat.data.local.FolderSaveStatus
import com.example.chat.domain.models.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tags are the existing local folder IDs, not server groups or new conversations. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeTagFilters(
    theme: ThemeConfig,
    store: ChatFolderStore,
    enabled: Boolean,
    accountIdentity: Any?,
    conversations: List<Conversation>,
    visibleConversations: List<Conversation>,
    titles: Map<String, String>,
    selectedFilter: String,
    selectedTagId: String?,
    onFilter: (String) -> Unit,
    onTag: (String) -> Unit,
    onSaved: (String) -> Unit,
) {
    // The key disposes the sheet and its coroutine scope across account/store changes.
    key(accountIdentity, store) {
        val revision by store.revision.collectAsState()
        val tags = remember(store, revision) { store.folders() }
        var create by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<ChatFolder?>(null) }
        val accent = Color(theme.accentColor)
        val fg = Color(theme.primaryTextColor)
        val card = Color(theme.cardColor)
        LazyRow(
            modifier = Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "Chat filters and local tags" },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf("All", "Unread", "Groups", "Direct"), key = { "filter:$it" }) { filter ->
                TagPill(filter, selectedTagId == null && selectedFilter == filter, accent, fg, card,
                    onClick = { onFilter(filter) })
            }
            if (enabled) {
                items(tags, key = { "tag:${it.id}" }) { tag ->
                    TagPill(tag.name, selectedTagId == tag.id, accent, fg, card,
                        onClick = { onTag(tag.id) }, onLongClick = { editing = tag })
                }
                item(key = "create-tag") {
                    TagPill("+", false, accent, fg, card, "Create local tag", onClick = { create = true })
                }
            }
        }
        if (create || editing != null) {
            val target = editing
            key(target?.id) {
                HomeTagEditor(theme, store, target, conversations, visibleConversations, titles,
                    onDismiss = { create = false; editing = null },
                    onSaved = { id -> create = false; editing = null; onSaved(id) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagPill(
    text: String, selected: Boolean, accent: Color, fg: Color, card: Color,
    label: String = text, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
) {
    Box(
        Modifier.heightIn(min = 48.dp)
            .background(if (selected) accent.copy(alpha = .18f) else card, RoundedCornerShape(100.dp))
            .border(1.dp, if (selected) accent.copy(alpha = .5f) else fg.copy(alpha = .12f), RoundedCornerShape(100.dp))
            .semantics { contentDescription = label; this.selected = selected }
            .then(if (onLongClick == null) Modifier.selectable(selected, role = Role.Tab, onClick = onClick)
                else Modifier.combinedClickable(role = Role.Tab, onClick = onClick,
                    onLongClickLabel = "Edit local tag", onLongClick = onLongClick))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) accent else fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTagEditor(
    theme: ThemeConfig, store: ChatFolderStore, target: ChatFolder?,
    conversations: List<Conversation>, visibleConversations: List<Conversation>, titles: Map<String, String>,
    onDismiss: () -> Unit, onSaved: (String) -> Unit,
) {
    var snapshot by remember { mutableStateOf(store.editSnapshot()) }
    var name by remember { mutableStateOf(target?.name.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var members by remember { mutableStateOf(snapshot.members[target?.id].orEmpty()) }
    var result by remember { mutableStateOf<FolderSaveResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ownerAlive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(store) { onDispose { ownerAlive.set(false) } }
    val liveVisible by rememberUpdatedState(visibleConversations.map { it.id }.toSet())
    val liveKnown by rememberUpdatedState(conversations.map { it.id }.toSet())
    val originalMembers = snapshot.members[target?.id].orEmpty()
    val blocked = snapshot.issues.isNotEmpty() || result?.status in setOf(
        FolderSaveStatus.CONFLICT, FolderSaveStatus.INVALID_DOCUMENT, FolderSaveStatus.NOT_FOUND,
    )
    val retry = result?.status == FolderSaveStatus.STORAGE_FAILURE
    val locked = busy || blocked || retry
    val accent = Color(theme.accentColor)
    val fg = Color(theme.primaryTextColor)
    fun save() {
        if (!ownerAlive.get() || busy || blocked || name.isBlank()) return
        // Do not drop hidden/unloaded references during an edit. New selections must still be visible.
        val selected = members.toSet()
        if ((selected - originalMembers).any { it !in liveVisible } || selected.any { it !in liveKnown }) {
            error = "Some selected chats are unavailable. Reload the chats before saving."
            return
        }
        val expected = if (retry) result?.retryToken ?: return else snapshot.token
        val id = if (retry) result?.folderId else target?.id
        val known = liveKnown.toSet()
        val saveName = name
        busy = true
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    if (ownerAlive.get()) store.saveFolderDurably(saveName, selected, known, expected, id) else null
                }
                if (saved == null || !ownerAlive.get()) return@launch
                result = saved
                if (saved.status == FolderSaveStatus.DURABLE) saved.folderId?.let(onSaved)
                else error = when (saved.status) {
                    FolderSaveStatus.STORAGE_FAILURE -> "Not confirmed on disk. Keep this sheet open and retry the same save."
                    FolderSaveStatus.CONFLICT -> "Tags changed elsewhere. Reload before editing again."
                    FolderSaveStatus.INVALID_NAME -> "Use a unique tag name (up to 24 characters)."
                    FolderSaveStatus.INVALID_MEMBERS -> "Some chats are unavailable. Nothing was saved."
                    else -> "This tag cannot be saved. Reload to inspect the current state."
                }
            } finally { busy = false }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy && !retry) onDismiss() }, containerColor = Color(theme.cardColor)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(if (target == null) "Create tag" else "Edit tag", color = fg, style = MaterialTheme.typography.titleLarge)
            Text("Local to this account. Existing chats only; no chats or invites are created.", color = fg)
            // OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth().padding(top = 12.dp),
            // label = { Text("Tag name") }, shape = RoundedCornerShape(100.dp), singleLine = true, enabled = !locked)
            com.example.chat.ui.kit.ChatyTaskField(
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
                value = name, onValueChange = { name = it; error = null },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                label = "Tag name", singleLine = true, enabled = !locked,
            )
            // OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
            // label = { Text("Search existing chat names") }, shape = RoundedCornerShape(100.dp), singleLine = true, enabled = !locked)
            com.example.chat.ui.kit.ChatyTaskField(
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = "Search existing chat names", singleLine = true, enabled = !locked,
            )
            Text("${members.intersect(liveVisible).size} visible chats selected", color = fg, modifier = Modifier.padding(vertical = 10.dp))
            val matches = visibleConversations.distinctBy { it.id }.filter {
                query.isBlank() || (titles[it.id] ?: it.title).contains(query, ignoreCase = true)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(matches, key = { it.id }) { chat ->
                    val checked = chat.id in members
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(checked, enabled = !locked, role = Role.Checkbox) {
                            members = if (it) members + chat.id else members - chat.id
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).background(if (checked) accent else Color.Transparent, CircleShape)
                            .border(2.dp, if (checked) accent else fg.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                            if (checked) Text("✓", color = Color(theme.onAccentColor))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(titles[chat.id] ?: chat.title, color = fg, modifier = Modifier.weight(1f))
                    }
                }
                if (matches.isEmpty()) item { Text("No existing chats match.", color = fg) }
            }
            if (snapshot.issues.isNotEmpty()) Text("Stored tags need review. No data will be rewritten.", color = fg)
            error?.let { Text(it, color = Color(theme.dangerColor), modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !busy && !retry) { Text("Cancel") }
                if (blocked) TextButton(onClick = {
                    snapshot = store.editSnapshot(); result = null; error = null
                    name = snapshot.folders.firstOrNull { it.id == target?.id }?.name.orEmpty()
                    members = snapshot.members[target?.id].orEmpty()
                }) { Text("Reload") }
                TextButton(onClick = { save() }, enabled = !busy && !blocked && name.isNotBlank()) {
                    Text(if (busy) "Saving..." else if (retry) "Retry save" else "Save tag")
                }
            }
        }
    }
}

*/

/* Previous bottom sheet container retained.
    ModalBottomSheet(onDismissRequest = { if (!busy && !retry) onDismiss() }, containerColor = Color(theme.cardColor)) {
        // The sheet scrolls as one unit. IME inset keeps bottom actions reachable.
        // Previous fixed root: Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding())
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !busy && !retry) { Text("Cancel") }
                if (blocked) TextButton(onClick = {
                    snapshot = store.editSnapshot(); result = null; error = null
                    name = snapshot.folders.firstOrNull { it.id == target?.id }?.name.orEmpty()
                    members = snapshot.members[target?.id].orEmpty()
                }) { Text("Reload") }
                TextButton(onClick = { save() }, enabled = !busy && !blocked && name.isNotBlank()) {
                    Text(if (busy) "Saving..." else if (retry) "Retry save" else "Save tag")
                }
            }
*/

/* Superseded expanded-sheet container retained.
    // Expanded modal sheet from +, never a navigation destination.
    val modalState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { next -> next != SheetValue.Hidden || (!busy && !retry) })
    ModalBottomSheet(
        onDismissRequest = { if (!busy && !retry) onDismiss() },
        sheetState = modalState,
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(theme.backgroundColor),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {

*/

/* Prior fixed-radius chip retained.
private fun TagPill(
    text: String, selected: Boolean, accent: Color, fg: Color, card: Color,
    label: String = text, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
) {
    Box(
        Modifier.heightIn(min = 48.dp)
            .background(if (selected) accent.copy(alpha = .18f) else card, RoundedCornerShape(100.dp))
            .border(1.dp, if (selected) accent.copy(alpha = .5f) else fg.copy(alpha = .12f), RoundedCornerShape(100.dp))
            .semantics { contentDescription = label; this.selected = selected }
            .then(if (onLongClick == null) Modifier.selectable(selected, role = Role.Tab, onClick = onClick)
                else Modifier.combinedClickable(role = Role.Tab, onClick = onClick,
                    onLongClickLabel = "Edit local tag", onLongClick = onLongClick))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) accent else fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

*/

// Pre-Home-review source retained.
// package com.example.chat.ui.chats.home
//
// import androidx.compose.foundation.rememberScrollState
// import androidx.compose.foundation.verticalScroll
// import androidx.compose.foundation.background
// import androidx.compose.foundation.border
// import androidx.compose.foundation.combinedClickable
// import androidx.compose.foundation.ExperimentalFoundationApi
// import androidx.compose.foundation.layout.*
// import androidx.compose.foundation.lazy.LazyColumn
// import androidx.compose.foundation.lazy.LazyRow
// import androidx.compose.foundation.lazy.items
// import androidx.compose.foundation.selection.selectable
// import androidx.compose.foundation.selection.selectableGroup
// import androidx.compose.foundation.selection.toggleable
// import androidx.compose.foundation.shape.CircleShape
// import androidx.compose.foundation.shape.RoundedCornerShape
// import androidx.compose.material3.*
// import androidx.compose.runtime.*
// import androidx.compose.ui.Alignment
// import androidx.compose.ui.Modifier
// import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.draw.clip
// import androidx.compose.ui.semantics.selected
// import androidx.compose.ui.semantics.Role
// import androidx.compose.ui.semantics.contentDescription
// import androidx.compose.ui.semantics.semantics
// import androidx.compose.ui.text.font.FontWeight
// import androidx.compose.ui.unit.dp
// import com.example.chat.core.theme.ThemeConfig
// import com.example.chat.data.local.ChatFolder
// import com.example.chat.data.local.ChatFolderStore
// import com.example.chat.data.local.FolderSaveResult
// import com.example.chat.data.local.FolderSaveStatus
// import com.example.chat.domain.models.Conversation
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.launch
// import kotlinx.coroutines.withContext
//
// /** Tags are the existing local folder IDs, not server groups or new conversations. */
// @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
// @Composable
// fun HomeTagFilters(
//     theme: ThemeConfig,
//     store: ChatFolderStore,
//     enabled: Boolean,
//     accountIdentity: Any?,
//     conversations: List<Conversation>,
//     visibleConversations: List<Conversation>,
//     titles: Map<String, String>,
//     selectedFilter: String,
//     selectedTagId: String?,
//     onFilter: (String) -> Unit,
//     onTag: (String) -> Unit,
//     onSaved: (String) -> Unit,
// ) {
//     // The key disposes the sheet and its coroutine scope across account/store changes.
//     key(accountIdentity, store) {
//         val revision by store.revision.collectAsState()
//         val tags = remember(store, revision) { store.folders() }
//         var create by remember { mutableStateOf(false) }
//         var editing by remember { mutableStateOf<ChatFolder?>(null) }
//         val accent = Color(theme.accentColor)
//         val fg = Color(theme.primaryTextColor)
//         val card = Color(theme.cardColor)
//         LazyRow(
//             modifier = Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "Chat filters and local tags" },
//             contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
//             horizontalArrangement = Arrangement.spacedBy(8.dp),
//         ) {
//             items(listOf("All", "Unread", "Groups", "Direct"), key = { "filter:$it" }) { filter ->
//                 TagPill(filter, selectedTagId == null && selectedFilter == filter, accent, fg, card,
//                     onClick = { onFilter(filter) })
//             }
//             if (enabled) {
//                 items(tags, key = { "tag:${it.id}" }) { tag ->
//                     TagPill(tag.name, selectedTagId == tag.id, accent, fg, card,
//                         onClick = { onTag(tag.id) }, onLongClick = { editing = tag })
//                 }
//                 item(key = "create-tag") {
//                     TagPill("+", false, accent, fg, card, "Create local tag", onClick = { create = true })
//                 }
//             }
//         }
//         if (create || editing != null) {
//             val target = editing
//             key(target?.id) {
//                 HomeTagEditor(theme, store, target, conversations, visibleConversations, titles,
//                     onDismiss = { create = false; editing = null },
//                     onSaved = { id -> create = false; editing = null; onSaved(id) })
//             }
//         }
//     }
// }
//
// @OptIn(ExperimentalFoundationApi::class)
// @Composable
// private fun TagPill(
//     text: String, selected: Boolean, accent: Color, fg: Color, card: Color,
//     label: String = text, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
// ) {
//     Box(
//         Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
//             .clip(RoundedCornerShape(percent = 50))
//             .background(if (selected) accent.copy(alpha = .18f) else card, RoundedCornerShape(percent = 50))
//             .border(1.dp, if (selected) accent.copy(alpha = .5f) else fg.copy(alpha = .12f), RoundedCornerShape(percent = 50))
//             .semantics { contentDescription = label; this.selected = selected }
//             .then(if (onLongClick == null) Modifier.selectable(selected, role = Role.Tab, onClick = onClick)
//                 else Modifier.combinedClickable(role = Role.Tab, onClick = onClick,
//                     onLongClickLabel = "Edit local tag", onLongClick = onLongClick))
//             .padding(horizontal = 14.dp, vertical = 12.dp),
//         contentAlignment = Alignment.Center,
//     ) { Text(text, color = if (selected) accent else fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
// }
//
// @OptIn(ExperimentalMaterial3Api::class)
// @Composable
// private fun HomeTagEditor(
//     theme: ThemeConfig, store: ChatFolderStore, target: ChatFolder?,
//     conversations: List<Conversation>, visibleConversations: List<Conversation>, titles: Map<String, String>,
//     onDismiss: () -> Unit, onSaved: (String) -> Unit,
// ) {
//     var snapshot by remember { mutableStateOf(store.editSnapshot()) }
//     var name by remember { mutableStateOf(target?.name.orEmpty()) }
//     var query by remember { mutableStateOf("") }
//     var members by remember { mutableStateOf(snapshot.members[target?.id].orEmpty()) }
//     var result by remember { mutableStateOf<FolderSaveResult?>(null) }
//     var error by remember { mutableStateOf<String?>(null) }
//     var busy by remember { mutableStateOf(false) }
//     val scope = rememberCoroutineScope()
//     val ownerAlive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
//     DisposableEffect(store) { onDispose { ownerAlive.set(false) } }
//     val liveVisible by rememberUpdatedState(visibleConversations.map { it.id }.toSet())
//     val liveKnown by rememberUpdatedState(conversations.map { it.id }.toSet())
//     val originalMembers = snapshot.members[target?.id].orEmpty()
//     val blocked = snapshot.issues.isNotEmpty() || result?.status in setOf(
//         FolderSaveStatus.CONFLICT, FolderSaveStatus.INVALID_DOCUMENT, FolderSaveStatus.NOT_FOUND,
//     )
//     val retry = result?.status == FolderSaveStatus.STORAGE_FAILURE
//     val locked = busy || blocked || retry
//     val accent = Color(theme.accentColor)
//     val fg = Color(theme.primaryTextColor)
//     fun save() {
//         if (!ownerAlive.get() || busy || blocked || name.isBlank()) return
//         // Do not drop hidden/unloaded references during an edit. New selections must still be visible.
//         val selected = members.toSet()
//         if ((selected - originalMembers).any { it !in liveVisible } || (selected - originalMembers).any { it !in liveKnown }) {
//             error = "Some selected chats are unavailable. Reload the chats before saving."
//             return
//         }
//         val expected = if (retry) result?.retryToken ?: return else snapshot.token
//         val id = if (retry) result?.folderId else target?.id
//         val known = liveKnown.toSet()
//         val saveName = name
//         busy = true
//         scope.launch {
//             try {
//                 val saved = withContext(Dispatchers.IO) {
//                     if (ownerAlive.get()) store.saveFolderDurably(saveName, selected, known, expected, id, preserveExistingUnavailableMembers = target != null) else null
//                 }
//                 if (saved == null || !ownerAlive.get()) return@launch
//                 result = saved
//                 if (saved.status == FolderSaveStatus.DURABLE) saved.folderId?.let(onSaved)
//                 else error = when (saved.status) {
//                     FolderSaveStatus.STORAGE_FAILURE -> "Not confirmed on disk. Keep this sheet open and retry the same save."
//                     FolderSaveStatus.CONFLICT -> "Tags changed elsewhere. Reload before editing again."
//                     FolderSaveStatus.INVALID_NAME -> "Use a unique tag name (up to 24 characters)."
//                     FolderSaveStatus.INVALID_MEMBERS -> "Some chats are unavailable. Nothing was saved."
//                     else -> "This tag cannot be saved. Reload to inspect the current state."
//                 }
//             } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
//                 error = "The save could not be confirmed. Reload the tag before trying again."
//                 result = com.example.chat.data.local.FolderSaveResult(FolderSaveStatus.CONFLICT, target?.id)
//             } finally { busy = false }
//         }
//     }
//     // Full-screen editor, not a bottom sheet. It owns Back and Save while open.
//     androidx.compose.ui.window.Dialog(
//         onDismissRequest = { if (!busy && !retry) onDismiss() },
//         properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
//     ) {
//         Scaffold(
//             containerColor = Color(theme.backgroundColor),
//             topBar = {
//                 Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp).padding(horizontal = 8.dp),
//                     verticalAlignment = Alignment.CenterVertically) {
//                     TextButton(onClick = onDismiss, enabled = !busy && !retry) { Text("Back") }
//                     Text(if (target == null) "Create tag" else "Edit tag", color = fg,
//                         style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
//                     TextButton(onClick = { save() }, enabled = !busy && !blocked && name.isNotBlank()) {
//                         Text(if (busy) "Saving..." else if (retry) "Retry save" else "Save")
//                     }
//                 }
//             },
//         ) { insets ->
//         Column(Modifier.fillMaxSize().padding(insets).imePadding().navigationBarsPadding()
//             .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
//             Text("Local to this account. Existing chats only; no chats or invites are created.", color = fg)
//             OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth().padding(top = 12.dp),
//                 label = { Text("Tag name") }, shape = RoundedCornerShape(100.dp), singleLine = true, enabled = !locked)
//             OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
//                 label = { Text("Search existing chat names") }, shape = RoundedCornerShape(100.dp), singleLine = true, enabled = !locked)
//             Text("${members.intersect(liveVisible).size} visible chats selected", color = fg, modifier = Modifier.padding(vertical = 10.dp))
//             val matches = visibleConversations.distinctBy { it.id }.filter {
//                 query.isBlank() || (titles[it.id] ?: it.title).contains(query, ignoreCase = true)
//             }
//             Column(Modifier.fillMaxWidth()) {
//                 matches.forEach { chat ->
//                     val checked = chat.id in members
//                     Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
//                         .toggleable(checked, enabled = !locked, role = Role.Checkbox) {
//                             members = if (it) members + chat.id else members - chat.id
//                         }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
//                         Box(Modifier.size(26.dp).background(if (checked) accent else Color.Transparent, CircleShape)
//                             .border(2.dp, if (checked) accent else fg.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
//                             if (checked) Text("✓", color = Color(theme.onAccentColor))
//                         }
//                         Spacer(Modifier.width(12.dp))
//                         Text(titles[chat.id] ?: chat.title, color = fg, modifier = Modifier.weight(1f))
//                     }
//                 }
//                 if (matches.isEmpty()) Text("No existing chats match.", color = fg)
//             }
//             /* Previous nested fixed-height list retained.
//             LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
//                 items(matches, key = { it.id }) { chat ->
//                     val checked = chat.id in members
//                     Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
//                         .toggleable(checked, enabled = !locked, role = Role.Checkbox) {
//                             members = if (it) members + chat.id else members - chat.id
//                         }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
//                         Box(Modifier.size(26.dp).background(if (checked) accent else Color.Transparent, CircleShape)
//                             .border(2.dp, if (checked) accent else fg.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
//                             if (checked) Text("✓", color = Color(theme.onAccentColor))
//                         }
//                         Spacer(Modifier.width(12.dp))
//                         Text(titles[chat.id] ?: chat.title, color = fg, modifier = Modifier.weight(1f))
//                     }
//                 }
//                 if (matches.isEmpty()) item { Text("No existing chats match.", color = fg) }
//             }
//             */
//             if (snapshot.issues.isNotEmpty()) Text("Stored tags need review. No data will be rewritten.", color = fg)
//             error?.let { Text(it, color = Color(theme.dangerColor), modifier = Modifier.padding(top = 8.dp)) }
//             if (blocked) TextButton(onClick = {
//                 snapshot = store.editSnapshot(); result = null; error = null
//                 name = snapshot.folders.firstOrNull { it.id == target?.id }?.name.orEmpty()
//                 members = snapshot.members[target?.id].orEmpty()
//             }) { Text("Reload tag") }
//
//         }
//         }
//     }
// }
//
// /* Before sheet/retry/preserved-members review repair.
// package com.example.chat.ui.chats.home
//
// import androidx.compose.foundation.background
// import androidx.compose.foundation.border
// import androidx.compose.foundation.combinedClickable
// import androidx.compose.foundation.ExperimentalFoundationApi
// import androidx.compose.foundation.layout.*
// import androidx.compose.foundation.lazy.LazyColumn
// import androidx.compose.foundation.lazy.LazyRow
// import androidx.compose.foundation.lazy.items
// import androidx.compose.foundation.selection.selectable
// import androidx.compose.foundation.selection.selectableGroup
// import androidx.compose.foundation.selection.toggleable
// import androidx.compose.foundation.shape.CircleShape
// import androidx.compose.foundation.shape.RoundedCornerShape
// import androidx.compose.material3.*
// import androidx.compose.runtime.*
// import androidx.compose.ui.Alignment
// import androidx.compose.ui.Modifier
// import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.semantics.selected
// import androidx.compose.ui.semantics.Role
// import androidx.compose.ui.semantics.contentDescription
// import androidx.compose.ui.semantics.semantics
// import androidx.compose.ui.text.font.FontWeight
// import androidx.compose.ui.unit.dp
// import com.example.chat.core.theme.ThemeConfig
// import com.example.chat.data.local.ChatFolder
// import com.example.chat.data.local.ChatFolderStore
// import com.example.chat.data.local.FolderSaveResult
// import com.example.chat.data.local.FolderSaveStatus
// import com.example.chat.domain.models.Conversation
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.launch
// import kotlinx.coroutines.withContext
//
// /** Tags are the existing local folder IDs, not server groups or new conversations. */
// @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
// @Composable
// fun HomeTagFilters(
//     theme: ThemeConfig,
//     store: ChatFolderStore,
//     enabled: Boolean,
//     accountIdentity: Any?,
//     conversations: List<Conversation>,
//     visibleConversations: List<Conversation>,
//     titles: Map<String, String>,
//     selectedFilter: String,
//     selectedTagId: String?,
//     onFilter: (String) -> Unit,
//     onTag: (String) -> Unit,
//     onSaved: (String) -> Unit,
// ) {
//     // The key disposes the sheet and its coroutine scope across account/store changes.
//     key(accountIdentity, store) {
//         val revision by store.revision.collectAsState()
//         val tags = remember(store, revision) { store.folders() }
//         var create by remember { mutableStateOf(false) }
//         var editing by remember { mutableStateOf<ChatFolder?>(null) }
//         val accent = Color(theme.accentColor)
//         val fg = Color(theme.primaryTextColor)
//         val card = Color(theme.cardColor)
//         LazyRow(
//             modifier = Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "Chat filters and local tags" },
//             contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
//             horizontalArrangement = Arrangement.spacedBy(8.dp),
//         ) {
//             items(listOf("All", "Unread", "Groups", "Direct"), key = { "filter:$it" }) { filter ->
//                 TagPill(filter, selectedTagId == null && selectedFilter == filter, accent, fg, card,
//                     onClick = { onFilter(filter) })
//             }
//             if (enabled) {
//                 items(tags, key = { "tag:${it.id}" }) { tag ->
//                     TagPill(tag.name, selectedTagId == tag.id, accent, fg, card,
//                         onClick = { onTag(tag.id) }, onLongClick = { editing = tag })
//                 }
//                 item(key = "create-tag") {
//                     TagPill("+", false, accent, fg, card, "Create local tag", onClick = { create = true })
//                 }
//             }
//         }
//         if (create || editing != null) {
//             val target = editing
//             key(target?.id) {
//                 HomeTagEditor(theme, store, target, conversations, visibleConversations, titles,
//                     onDismiss = { create = false; editing = null },
//                     onSaved = { id -> create = false; editing = null; onSaved(id) })
//             }
//         }
//     }
// }
//
// @OptIn(ExperimentalFoundationApi::class)
// @Composable
// private fun TagPill(
//     text: String, selected: Boolean, accent: Color, fg: Color, card: Color,
//     label: String = text, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
// ) {
//     Box(
//         Modifier.heightIn(min = 48.dp)
//             .background(if (selected) accent.copy(alpha = .18f) else card, RoundedCornerShape(100.dp))
//             .border(1.dp, if (selected) accent.copy(alpha = .5f) else fg.copy(alpha = .12f), RoundedCornerShape(100.dp))
//             .semantics { contentDescription = label; this.selected = selected }
//             .then(if (onLongClick == null) Modifier.selectable(selected, role = Role.Tab, onClick = onClick)
//                 else Modifier.combinedClickable(role = Role.Tab, onClick = onClick,
//                     onLongClickLabel = "Edit local tag", onLongClick = onLongClick))
//             .padding(horizontal = 14.dp, vertical = 12.dp),
//         contentAlignment = Alignment.Center,
//     ) { Text(text, color = if (selected) accent else fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
// }
//
// @OptIn(ExperimentalMaterial3Api::class)
// @Composable
// private fun HomeTagEditor(
//     theme: ThemeConfig, store: ChatFolderStore, target: ChatFolder?,
//     conversations: List<Conversation>, visibleConversations: List<Conversation>, titles: Map<String, String>,
//     onDismiss: () -> Unit, onSaved: (String) -> Unit,
// ) {
//     var snapshot by remember { mutableStateOf(store.editSnapshot()) }
//     var name by remember { mutableStateOf(target?.name.orEmpty()) }
//     var query by remember { mutableStateOf("") }
//     var members by remember { mutableStateOf(snapshot.members[target?.id].orEmpty()) }
//     var result by remember { mutableStateOf<FolderSaveResult?>(null) }
//     var error by remember { mutableStateOf<String?>(null) }
//     var busy by remember { mutableStateOf(false) }
//     val scope = rememberCoroutineScope()
//     val ownerAlive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
//     DisposableEffect(store) { onDispose { ownerAlive.set(false) } }
//     val liveVisible by rememberUpdatedState(visibleConversations.map { it.id }.toSet())
//     val liveKnown by rememberUpdatedState(conversations.map { it.id }.toSet())
//     val originalMembers = snapshot.members[target?.id].orEmpty()
//     val blocked = snapshot.issues.isNotEmpty() || result?.status in setOf(
//         FolderSaveStatus.CONFLICT, FolderSaveStatus.INVALID_DOCUMENT, FolderSaveStatus.NOT_FOUND,
//     )
//     val retry = result?.status == FolderSaveStatus.STORAGE_FAILURE
//     val locked = busy || blocked || retry
//     val accent = Color(theme.accentColor)
//     val fg = Color(theme.primaryTextColor)
//     fun save() {
//         if (!ownerAlive.get() || busy || blocked || name.isBlank()) return
//         // Do not drop hidden/unloaded references during an edit. New selections must still be visible.
//         val selected = members.toSet()
//         if ((selected - originalMembers).any { it !in liveVisible } || selected.any { it !in liveKnown }) {
//             error = "Some selected chats are unavailable. Reload the chats before saving."
//             return
//         }
//         val expected = if (retry) result?.retryToken ?: return else snapshot.token
//         val id = if (retry) result?.folderId else target?.id
//         val known = liveKnown.toSet()
//         val saveName = name
//         busy = true
//         scope.launch {
//             try {
//                 val saved = withContext(Dispatchers.IO) {
//                     if (ownerAlive.get()) store.saveFolderDurably(saveName, selected, known, expected, id) else null
//                 }
//                 if (saved == null || !ownerAlive.get()) return@launch
//                 result = saved
//                 if (saved.status == FolderSaveStatus.DURABLE) saved.folderId?.let(onSaved)
//                 else error = when (saved.status) {
//                     FolderSaveStatus.STORAGE_FAILURE -> "Not confirmed on disk. Keep this sheet open and retry the same save."
//                     FolderSaveStatus.CONFLICT -> "Tags changed elsewhere. Reload before editing again."
//                     FolderSaveStatus.INVALID_NAME -> "Use a unique tag name (up to 24 characters)."
//                     FolderSaveStatus.INVALID_MEMBERS -> "Some chats are unavailable. Nothing was saved."
//                     else -> "This tag cannot be saved. Reload to inspect the current state."
//                 }
//             } finally { busy = false }
//         }
//     }
//     ModalBottomSheet(onDismissRequest = { if (!busy && !retry) onDismiss() }, containerColor = Color(theme.cardColor)) {
//         Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
//             Text(if (target == null) "Create tag" else "Edit tag", color = fg, style = MaterialTheme.typography.titleLarge)
//             Text("Local to this account. Existing chats only; no chats or invites are created.", color = fg)
//             OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth().padding(top = 12.dp),
//                 label = { Text("Tag name") }, shape = RoundedCornerShape(100.dp), singleLine = true, enabled = !locked)
//             OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
//                 label = { Text("Search existing chat names") }, shape = RoundedCornerShape(100.dp), singleLine = true, enabled = !locked)
//             Text("${members.intersect(liveVisible).size} visible chats selected", color = fg, modifier = Modifier.padding(vertical = 10.dp))
//             val matches = visibleConversations.distinctBy { it.id }.filter {
//                 query.isBlank() || (titles[it.id] ?: it.title).contains(query, ignoreCase = true)
//             }
//             LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
//                 items(matches, key = { it.id }) { chat ->
//                     val checked = chat.id in members
//                     Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
//                         .toggleable(checked, enabled = !locked, role = Role.Checkbox) {
//                             members = if (it) members + chat.id else members - chat.id
//                         }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
//                         Box(Modifier.size(26.dp).background(if (checked) accent else Color.Transparent, CircleShape)
//                             .border(2.dp, if (checked) accent else fg.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
//                             if (checked) Text("✓", color = Color(theme.onAccentColor))
//                         }
//                         Spacer(Modifier.width(12.dp))
//                         Text(titles[chat.id] ?: chat.title, color = fg, modifier = Modifier.weight(1f))
//                     }
//                 }
//                 if (matches.isEmpty()) item { Text("No existing chats match.", color = fg) }
//             }
//             if (snapshot.issues.isNotEmpty()) Text("Stored tags need review. No data will be rewritten.", color = fg)
//             error?.let { Text(it, color = Color(theme.dangerColor), modifier = Modifier.padding(top = 8.dp)) }
//             Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
//                 TextButton(onClick = onDismiss, enabled = !busy && !retry) { Text("Cancel") }
//                 if (blocked) TextButton(onClick = {
//                     snapshot = store.editSnapshot(); result = null; error = null
//                     name = snapshot.folders.firstOrNull { it.id == target?.id }?.name.orEmpty()
//                     members = snapshot.members[target?.id].orEmpty()
//                 }) { Text("Reload") }
//                 TextButton(onClick = { save() }, enabled = !busy && !blocked && name.isNotBlank()) {
//                     Text(if (busy) "Saving..." else if (retry) "Retry save" else "Save tag")
//                 }
//             }
//         }
//     }
// }
//
// */
//
// /* Previous bottom sheet container retained.
//     ModalBottomSheet(onDismissRequest = { if (!busy && !retry) onDismiss() }, containerColor = Color(theme.cardColor)) {
//         // The sheet scrolls as one unit. IME inset keeps bottom actions reachable.
//         // Previous fixed root: Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding())
//         Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
//             .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
//             Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
//                 TextButton(onClick = onDismiss, enabled = !busy && !retry) { Text("Cancel") }
//                 if (blocked) TextButton(onClick = {
//                     snapshot = store.editSnapshot(); result = null; error = null
//                     name = snapshot.folders.firstOrNull { it.id == target?.id }?.name.orEmpty()
//                     members = snapshot.members[target?.id].orEmpty()
//                 }) { Text("Reload") }
//                 TextButton(onClick = { save() }, enabled = !busy && !blocked && name.isNotBlank()) {
//                     Text(if (busy) "Saving..." else if (retry) "Retry save" else "Save tag")
//                 }
//             }
// */
//
// /* Superseded expanded-sheet container retained.
//     // Expanded modal sheet from +, never a navigation destination.
//     val modalState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
//         confirmValueChange = { next -> next != SheetValue.Hidden || (!busy && !retry) })
//     ModalBottomSheet(
//         onDismissRequest = { if (!busy && !retry) onDismiss() },
//         sheetState = modalState,
//         modifier = Modifier.fillMaxSize(),
//         containerColor = Color(theme.backgroundColor),
//         dragHandle = { BottomSheetDefaults.DragHandle() },
//     ) {
//
// */
//
// /* Prior fixed-radius chip retained.
// private fun TagPill(
//     text: String, selected: Boolean, accent: Color, fg: Color, card: Color,
//     label: String = text, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
// ) {
//     Box(
//         Modifier.heightIn(min = 48.dp)
//             .background(if (selected) accent.copy(alpha = .18f) else card, RoundedCornerShape(100.dp))
//             .border(1.dp, if (selected) accent.copy(alpha = .5f) else fg.copy(alpha = .12f), RoundedCornerShape(100.dp))
//             .semantics { contentDescription = label; this.selected = selected }
//             .then(if (onLongClick == null) Modifier.selectable(selected, role = Role.Tab, onClick = onClick)
//                 else Modifier.combinedClickable(role = Role.Tab, onClick = onClick,
//                     onLongClickLabel = "Edit local tag", onLongClick = onLongClick))
//             .padding(horizontal = 14.dp, vertical = 12.dp),
//         contentAlignment = Alignment.Center,
//     ) { Text(text, color = if (selected) accent else fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
// }
//
// */
