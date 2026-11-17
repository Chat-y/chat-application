package com.example.chat.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.example.chat.ui.effects.iosItemEntrance
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.ViewKanban
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyTextButton
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
import com.example.chat.ui.kit.ChatyChoiceTile
import com.example.chat.ui.kit.ChatyTaskField
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.chat.domain.models.ChatTask
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.TaskPriority
import com.example.chat.domain.models.TaskStatus
import com.example.chat.domain.models.TaskWorkflow
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalThemeConfig
import java.time.ZoneId
import kotlinx.coroutines.launch

/** Exact port of tasks_screen.dart: 'Action Items' header + create action,
 *  Task List / Kanban Board tabs, All/Urgent/High/Med/Low priority filter,
 *  cards with status+priority badges, overflow menu (Edit Task/View
 *  Details/Delete Task), assignee avatars, due label, kanban columns with
 *  workflow-validated drag & drop AND chevron moves, per-task history dialog.
 *
 *  [tasksRevision] bumps on every TasksController mutation so the screen
 *  re-reads controller.tasks (the same listener -> recompose pattern the
 *  owner's screens use on ApiBackendService). */
@Composable
fun TasksScreen(
    controller: TasksController,
    tasksRevision: Int,
    conversations: List<Conversation>,
    getUser: (String) -> UserProfile?,
    onOpenDetail: (ChatTask) -> Unit,
    onEditTask: (ChatTask) -> Unit,
    onCreateTask: () -> Unit,
    // TAS-SEARCH-1: bumped when the top-bar search is tapped on this destination.
    searchTick: Int = 0,
    onSearchTickConsumed: () -> Unit = {},
) {
    @Suppress("UNUSED_EXPRESSION") tasksRevision
    val theme = LocalThemeConfig.current
    val colors = remember(theme) { TaskColors(theme) }
    val tasks = controller.tasks
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tabIndex by remember { mutableIntStateOf(0) }
    var priorityFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var historyTask by remember { mutableStateOf<ChatTask?>(null) }
    // TAS-SEARCH-1: focus the search field when the top-bar search ticks. AppRoot
    // consumes the tick back to 0, so leaving and returning never refocuses.
    val searchFocusRequester = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(searchTick) {
        if (searchTick > 0) {
            runCatching { searchFocusRequester.requestFocus() }
            onSearchTickConsumed()
        }
    }
    var pendingDelete by remember { mutableStateOf<ChatTask?>(null) }

    fun moveTask(task: ChatTask, target: TaskStatus) {
        scope.launch {
            when (val result = controller.moveTask(task, target)) {
                is TasksController.MoveResult.Invalid -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(result.reason)
                }
                is TasksController.MoveResult.Failed -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(result.message)
                }
                else -> Unit
            }
        }
    }

    Scaffold(
        containerColor = com.example.chat.ui.navigation.LocalReferenceContentSurface.current ?: colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Header row
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (com.example.chat.ui.navigation.LocalDestinationTitlePresentation.current == "tasks") {
                    Spacer(Modifier.weight(1f))
                } else Text(
                    "Action Items",
                    color = colors.foreground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                // TAS-UI-1: header create-task button removed per Bandi 2026-11-17; the add action
                // stays on the host shell contextual FAB (AppRoot, outside this lane).
                // IconButton(
                //     onClick = onCreateTask,
                //     modifier = Modifier
                //         .clip(CircleShape)
                //         .background(colors.surfaceSecondary),
                // ) {
                //     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.add, semanticLabel = "Create task", color = colors.primary)
                // }
            }
            // A dedicated row keeps the input full width now that the header add action is gone.
            // TAS-UI-2 (revised x2): the shared ChatyTaskField now owns the clear affordance in
            // its trailing slot (reserved space, 48dp target, no overlap) and the keyboard shows
            // the search action.
            ChatyTaskField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search tasks",
                enabled = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(searchFocusRequester),
                leadingIcon = {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search,
                        semanticLabel = null, color = colors.foregroundSecondary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close,
                                semanticLabel = "Clear task search", color = colors.foregroundSecondary)
                        }
                    }
                },
                // Canonical field API; legacy imeAction is still accepted for compatibility.
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            )
            // Prior interim: Box overlay with the clear IconButton aligned end over the field
            // (obstructed the editable area; superseded by the ChatyTaskField trailing slot).
            // Prior stock field (pill CircleShape, soft shadow, per-field colors), retained for reference:
            // OutlinedTextField(
            //     value = searchQuery,
            //     onValueChange = { searchQuery = it },
            //     label = { Text("Search tasks") },
            //     singleLine = true,
            //     shape = CircleShape,
            //     modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).shadow(2.dp, CircleShape)
            //         .focusRequester(searchFocusRequester),
            //     leadingIcon = { ChatyIcon(ChatyIconId.search, null, colors.foregroundSecondary) },
            //     trailingIcon = { if (searchQuery.isNotEmpty()) IconButton({ searchQuery = "" }) { ChatyIcon(ChatyIconId.close, "Clear task search", colors.foregroundSecondary) } },
            //     colors = OutlinedTextFieldDefaults.colors(focused/unfocused text, cursor, borders, labels from colors.*),
            // )
            // Tabs
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = Color.Transparent,
                contentColor = colors.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    selectedContentColor = colors.primary,
                    unselectedContentColor = colors.foregroundSecondary,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 10.dp),
                    ) {

//Icon(Icons.Rounded.ListAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.list, Icons.Rounded.ListAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Task List")
                    }
                }
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    selectedContentColor = colors.primary,
                    unselectedContentColor = colors.foregroundSecondary,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 10.dp),
                    ) {

//Icon(Icons.Rounded.ViewKanban, contentDescription = null, modifier = Modifier.size(18.dp))
                        com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.kanban, Icons.Rounded.ViewKanban, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Kanban Board")
                    }
                }
            }
            // Priority filter: selectableGroup exposes the row as one single-select group.
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
            ) {
                for (p in listOf("All", "Urgent", "High", "Med", "Low")) {
                    val selected = priorityFilter == p
                    // TAS-UI-3 (revised): shared ChatyChoiceTile replaces the stock FilterChip
                    // (Bandi 2026-11-17); RadioButton role keeps the single-select semantics.
                    // widthIn keeps the 48dp touch width on short labels ("All", "Med", "Low").
                    ChatyChoiceTile(
                        label = p,
                        onClick = { priorityFilter = p },
                        selected = selected,
                        compact = true,
                        role = Role.RadioButton,
                        modifier = Modifier.padding(end = 8.dp).widthIn(min = 48.dp),
                    )
                    // Prior stock chip, retained for reference:
                    // FilterChip(
                    //     selected = selected,
                    //     onClick = { priorityFilter = p },
                    //     label = { Text(p, color/weight by selected, fontSize = 12.sp) },
                    //     modifier = Modifier.padding(end = 8.dp).shadow(2.dp, RoundedCornerShape(16.dp)),
                    // )
                }
            }

            // Memoized: controller.tasks returns a fresh copy each read and
            // this screen recomposes on selection/detail state too; List
            // equality keeps the memo stable across identical snapshots.
            // Previous priority-only filter retained for reference:
            // val filtered = remember(tasks, priorityFilter) {
            // tasks.filter { task ->
            // when (priorityFilter) {
            // "Urgent" -> task.priority == TaskPriority.urgent
            // "High" -> task.priority == TaskPriority.high
            // "Med" -> task.priority == TaskPriority.medium
            // "Low" -> task.priority == TaskPriority.low
            // else -> true
            // }
            // }
            // }
            // Resolve names on recomposition too: user profiles can change without task equality changing.
            val filtered = filterTasks(tasks, priorityFilter, searchQuery, getUser)

            // Both tabs use the same results and the same no-match recovery.
            // First-load state does not turn a failed load into a claimed success.
            if (!controller.hasLoadedOnce && tasks.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text("Tasks have not loaded yet.", color = colors.foregroundSecondary, style = MaterialTheme.typography.bodyMedium)
                    // P5 (Bandi 2026-11-17): show the real load failure instead of silence.
                    controller.lastLoadError?.let { loadError ->
                        Spacer(Modifier.height(6.dp))
                        Text(loadError, color = colors.error, style = MaterialTheme.typography.bodySmall)
                    }
                    // Shared Chaty button replaces the stock TextButton (Bandi 2026-11-17).
                    ChatyTextButton(onClick = { scope.launch { controller.loadTasks() } }, label = "Retry")
                    // Prior: androidx.compose.material3.TextButton(onClick = { scope.launch { controller.loadTasks() } }) { Text("Retry") }
                }
            } else if (tabIndex == 0 || filtered.isEmpty()) {
                TaskListView(
                    filtered = filtered,
                    colors = colors,
                    priorityFilter = priorityFilter,
                    hasSearch = searchQuery.isNotBlank(),
                    getUser = getUser,
                    // Previous recovery reset only priority: priorityFilter = "All"
                    onClearFilter = { priorityFilter = "All"; searchQuery = "" },
                    onOpenDetail = onOpenDetail,
                    onEditTask = onEditTask,
                    onCreateTask = onCreateTask,
                    onDeleteTask = { task -> pendingDelete = task },
                    // P6 (Bandi 2026-11-17): pull-to-refresh on the list.
                    onRefresh = { controller.loadTasks() },
                )
            } else {
                KanbanBoard(
                    filtered = filtered,
                    colors = colors,
                    onOpenDetail = onOpenDetail,
                    onEditTask = onEditTask,
                    onShowHistory = { historyTask = it },
                    onMove = ::moveTask,
                )
            }
        }
    }

    // TAS-004: confirm list deletion with the same dialog as detail.
    pendingDelete?.let { task ->
        DeleteTaskDialog(
            onCancel = { pendingDelete = null },
            onDelete = {
                pendingDelete = null
                // Previous failure was silently swallowed: scope.launch { runCatching { controller.deleteTask(task.id) } }
                scope.launch {
                    try {
                        if (!controller.deleteTask(task.id)) snackbar.showSnackbar("Task was not deleted. Try again.")
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { snackbar.showSnackbar("Task could not be deleted. Try again.") }
                }
            },
        )
    }

    historyTask?.let { task ->
        TaskHistoryDialog(
            task = task,
            entries = controller.historyFor(task.id),
            colors = colors,
            onClose = { historyTask = null },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TaskListView(
    filtered: List<ChatTask>,
    colors: TaskColors,
    priorityFilter: String,
    hasSearch: Boolean,
    getUser: (String) -> UserProfile?,
    onClearFilter: () -> Unit,
    onOpenDetail: (ChatTask) -> Unit,
    onEditTask: (ChatTask) -> Unit,
    onCreateTask: () -> Unit,
    onDeleteTask: (ChatTask) -> Unit,
    onRefresh: suspend () -> Unit,
) {
    if (filtered.isEmpty()) {
        // Dart _emptyState.
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Previously only priority could produce an empty result.
            if (hasSearch || priorityFilter != "All") {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.tasks, semanticLabel = null, color = colors.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    // Previous title: "No results for $priorityFilter Priority"
                    if (hasSearch) "No matching tasks" else "No results for $priorityFilter Priority",
                    color = colors.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    // Previous copy: "No tasks found matching the selected priority filter."
                    "No tasks found matching the current search and priority filter.",
                    color = colors.foregroundSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Show all tasks",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onClearFilter),
                )
            } else {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.tasks, semanticLabel = null, color = colors.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("No action items yet", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Create an action item from any message or using the create task button.",
                    color = colors.foregroundSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Create task",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onCreateTask),
                )
            }
        }
        return
    }

    // R2/R3: task cards capped at 720dp centered on larger windows
    // (approved responsive plan 8 Nov 2026).
                    // Prior: onDragStart = { offset -> onDragStart(offset) },
    // P6: pull-to-refresh wraps the list; the spinner ends when the load completes.
    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    // P6 (revised): indicator colors come from app tokens (surface/accent), not Material defaults.
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (!refreshing) {
                refreshing = true
                refreshScope.launch {
                    try { onRefresh() } finally { refreshing = false }
                }
            }
        },
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = colors.surface,
                color = colors.primary,
            )
        },
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Stable keys keep per-item state (expand, swipe, animations) attached
        // to the right task when the list reorders or a card completes.
        items(filtered.size, key = { index -> filtered[index].id }) { index ->
            val task = filtered[index]
            // Row 9 (iOS-style content motion): staggered entrance per card; stable key = task id.
// Previous implementation retained:             TaskListCard(
            Box(Modifier.iosItemEntrance(index, task.id)) {
            TaskListCard(
                task = task,
                colors = colors,
                getUser = getUser,
                onOpenDetail = { onOpenDetail(task) },
                onEditTask = { onEditTask(task) },
                onDeleteTask = { onDeleteTask(task) },
            )
            }
        }
    }
    }
}

@Composable
private fun TaskListCard(
    task: ChatTask,
    colors: TaskColors,
    getUser: (String) -> UserProfile?,
    onOpenDetail: () -> Unit,
    onEditTask: () -> Unit,
    onDeleteTask: () -> Unit,
) {
    val isOverdue = task.isOverdue
    var menuOpen by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val due = task.dueAt?.atZone(zone)
    // TAS-UI-4 (revised): card radius from the theme, shadow color from tokens (Bandi 2026-11-17).
    val cardShape = RoundedCornerShape(LocalThemeConfig.current.cornerRadius.toFloat().dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, cardShape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(cardShape)
            .background(colors.surfaceElevated)
            .border(
                width = 1.dp,
                color = if (isOverdue) colors.error.copy(alpha = 0.4f) else colors.borderSubtle,
                shape = cardShape,
            )
            // Prior: .shadow(4.dp, RoundedCornerShape(18.dp)) .clip(RoundedCornerShape(18.dp)) border shape 18.dp
            .clickable(onClick = onOpenDetail)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskStatusBadge(task.status, colors)
            Spacer(Modifier.width(8.dp))
            TaskPriorityBadge(task.priority, colors)
            Spacer(Modifier.weight(1f))
            if (isOverdue) {
                Text(
                    "OVERDUE",
                    color = colors.error,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.width(4.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.moreOptions, semanticLabel = "Task options", color = colors.foregroundSecondary, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape, expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit Task") },
                        // TAS-AUD-1: the Edit row showed the history glyph; the kanban edit button and the
                        // detail screen both use ChatyIconId.edit.
                        // Prior: leadingIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.history, semanticLabel = null, modifier = Modifier.size(18.dp)) },
                        leadingIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.edit, semanticLabel = null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onEditTask() },
                    )
                    DropdownMenuItem(
                        text = { Text("View Details") },
                        leadingIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.openInNew, semanticLabel = null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onOpenDetail() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Task", color = colors.error) },
                        leadingIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.deleteOutline, semanticLabel = null, color = colors.error, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onDeleteTask() },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            task.title,
            // TAS-109: list titles capped at 3 lines; the full text stays in detail.
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = colors.foreground,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        )
        if (task.description.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                task.description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            task.assigneeIds.take(4).forEach { id ->
                val contact = getUser(id)
                Box(Modifier.padding(end = 5.dp)) {
                    TaskAvatar(contact?.avatarInitials ?: "U", contact?.avatarColorHex, 24, colors)
                }
            }
            // P7 (Bandi 2026-11-17): overflow count like the detail screen (TAS-049).
            if (task.assigneeIds.size > 4) {
                Text(
                    "+${task.assigneeIds.size - 4}",
                    color = colors.foregroundSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                // P7: zero-padded to match the detail screen's date format.
                // Prior: "Due ${due.dayOfMonth}/${due.monthValue}"
                if (due == null) "No due date" else "Due ${due.dayOfMonth.toString().padStart(2, '0')}/${due.monthValue.toString().padStart(2, '0')}",
                color = if (isOverdue) colors.error else colors.foregroundSecondary,
                fontSize = 12.sp,
                fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

/** Kanban with the Dart responsive column sizing and workflow-validated
 *  drag & drop (LongPressDraggable + DragTarget) plus chevron moves. */
@Composable
private fun KanbanBoard(
    filtered: List<ChatTask>,
    colors: TaskColors,
    onOpenDetail: (ChatTask) -> Unit,
    onEditTask: (ChatTask) -> Unit,
    onShowHistory: (ChatTask) -> Unit,
    onMove: (ChatTask, TaskStatus) -> Unit,
) {
    val columns = TaskWorkflow.stages.map { it to TaskWorkflow.label(it) }
    var draggedTask by remember { mutableStateOf<ChatTask?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var pointerRoot by remember { mutableStateOf(Offset.Zero) }
    // TAS-101: grab point inside the card and the board origin, both in root coordinates.
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    // TAS-026: group once per filtered snapshot instead of filtering per column.
    val groupedByStatus = remember(filtered) { filtered.groupBy { it.status } }
    val columnBounds = remember { mutableStateOf(mapOf<TaskStatus, Rect>()) }
    val density = LocalDensity.current

    BoxWithConstraintsCompat { maxWidthPx, maxHeightPx ->
        val columnWidthDp = with(density) {
            // Dart: <500dp -> 82% width; else fit-all clamped to [240, 460]
            // (280 when fit-all < 240).
            val maxWidthDp = maxWidthPx.toDp().value
            val outerPadding = 16f * 2
            val innerMargins = 12f * columns.size
            val fitAll = (maxWidthDp - outerPadding - innerMargins) / columns.size
            when {
                maxWidthDp < 500 -> maxWidthDp * 0.82f
                fitAll < 240 -> 280f
                else -> fitAll.coerceIn(240f, 460f)
            }.dp
        }

        // Prior: Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { boardOrigin = it.positionInRoot() }) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for ((status, title) in columns) {
                    // val columnTasks = filtered.filter { it.status == status } // TAS-026: replaced by groupedByStatus
                    val columnTasks = groupedByStatus[status].orEmpty()
                    val isDropTarget = draggedTask != null &&
                        columnBounds.value[status]?.contains(pointerRoot) == true &&
                        TaskWorkflow.canMove(draggedTask!!.status, status)
                    val targetBg by animateColorAsState(
                        if (isDropTarget) colors.primary.copy(alpha = 0.1f) else colors.surfaceSecondary,
                        label = "kanban-column-bg",
                    )
                    // TAS-UI-6 (revised): column radius from the theme, shadow color from tokens.
                    val columnShape = RoundedCornerShape(LocalThemeConfig.current.cornerRadius.toFloat().dp)
                    Column(
                        modifier = Modifier
                            // Prior: .heightIn(min = with(density) { (maxHeightPx - 24).toDp() })
                            .width(columnWidthDp)
                            .height(with(density) { (maxHeightPx - 24).coerceAtLeast(0f).toDp() }) // TAS-025: bounded so the stage list can be a LazyColumn
                            // TAS-UI-6: soft shadow on the kanban column that holds the cards.
                            .shadow(3.dp, columnShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                            .clip(columnShape)
                            .background(targetBg)
                            .border(
                                width = if (isDropTarget) 1.5.dp else 1.dp,
                                color = if (isDropTarget) colors.primary else colors.border,
                                shape = columnShape,
                            )
                            // Prior: .shadow(3.dp, RoundedCornerShape(18.dp)) .clip(RoundedCornerShape(18.dp)) border shape 18.dp
                            .onGloballyPositioned { coords ->
                                columnBounds.value = columnBounds.value + (status to coords.boundsInRoot())
                        // Prior: for (task in columnTasks) {
                            }
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                color = colors.foreground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.surfaceSecondary)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "${columnTasks.size}",
                                    color = colors.foreground,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (columnTasks.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                // TAS-108: add a non-gesture instruction.
                                // Text("Drop tasks here", color = colors.foregroundTertiary, fontSize = 12.sp)
                                Text(
                                    "Drop tasks here, or use a task's Move to buttons",
                                    color = colors.foregroundTertiary,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                        // TAS-025: bounded LazyColumn with stable task IDs (was a for loop).
                        LazyColumn(Modifier.weight(1f)) {
                        items(columnTasks, key = { it.id }) { task ->
                            KanbanCard(
                                task = task,
                                colors = colors,
                                isDragging = draggedTask?.id == task.id,
                                onOpenDetail = { onOpenDetail(task) },
                                // Prior: onDragStart = { origin ->
                                onEditTask = { onEditTask(task) },
                                onShowHistory = { onShowHistory(task) },
                                    // Prior: pointerRoot = origin
                                onMove = onMove,
                                onDragStart = { rootPoint, grab ->
                                    draggedTask = task
                                    dragOffset = Offset.Zero
                                    pointerRoot = rootPoint
                                    grabOffset = grab
                                },
                                // TAS-102: cancelled drag clears state and never calls onMove.
                                onDragCancel = {
                                    draggedTask = null
                                    dragOffset = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    pointerRoot += amount
                                },
                                onDragEnd = {
                                    val task0 = draggedTask
                                    if (task0 != null) {
                                        val target = columnBounds.value.entries
                                            .firstOrNull { it.value.contains(pointerRoot) }?.key
                                        if (target != null && TaskWorkflow.canMove(task0.status, target)) {
                                            onMove(task0, target)
                                        }
                                    }
                                    draggedTask = null
                                    dragOffset = Offset.Zero
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        }
                    }
                }
            }

            // Drag feedback overlay (Dart: 240dp wide card at 0.92 opacity).
            draggedTask?.let { task ->
                Box(
                    modifier = Modifier
                        .zIndex(10f)
                        // .offset { IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) } // TAS-101: replaced, anchored to grab point
                        .offset { IntOffset((pointerRoot.x - grabOffset.x - boardOrigin.x).toInt(), (pointerRoot.y - grabOffset.y - boardOrigin.y).toInt()) }
                        .width(240.dp)
                        .alpha(0.92f),
                ) {
                    KanbanCardBody(
                        task = task,
                        colors = colors,
                        showActions = false,
                        onOpenDetail = {},
                        onEditTask = {},
                        onShowHistory = {},
                        onMove = { _, _ -> },
                    )
                }
            }
        }
    }
}

/** BoxWithConstraints without the experimental-API import churn: exposes
 *  pixel constraints to the lambda. */
@Composable
private fun BoxWithConstraintsCompat(content: @Composable (maxWidthPx: Float, maxHeightPx: Float) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        with(LocalDensity.current) {
            content(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        }
    }
}

@Composable
private fun KanbanCard(
    task: ChatTask,
    colors: TaskColors,
    // Prior: onDragStart: (Offset) -> Unit,
    isDragging: Boolean,
    onOpenDetail: () -> Unit,
    onEditTask: () -> Unit,
    onShowHistory: () -> Unit,
    onMove: (ChatTask, TaskStatus) -> Unit,
    onDragStart: (Offset, Offset) -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    var cardRoot by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .alpha(if (isDragging) 0.35f else 1f)
                    // Prior: onDragCancel = onDragEnd,
            .onGloballyPositioned { cardRoot = it.positionInRoot() }
            // Previous pointer key: .pointerInput(task.id) {
            .pointerInput(task) {
                detectDragGesturesAfterLongPress(
                    // TAS-101: offset is card-local; convert to root and keep the grab point.
                    onDragStart = { offset -> onDragStart(cardRoot + offset, offset) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                )
            },
    ) {
        KanbanCardBody(task, colors, true, onOpenDetail, onEditTask, onShowHistory, onMove)
    }
}

@Composable
private fun KanbanCardBody(
    task: ChatTask,
    colors: TaskColors,
    showActions: Boolean,
    onOpenDetail: () -> Unit,
    onEditTask: () -> Unit,
    onShowHistory: () -> Unit,
    onMove: (ChatTask, TaskStatus) -> Unit,
) {
    val previous = TaskWorkflow.previous(task.status)
    val next = TaskWorkflow.next(task.status)
    // TAS-UI-5 (revised): card radius from the theme, shadow color from tokens (Bandi 2026-11-17).
    val cardShape = RoundedCornerShape(LocalThemeConfig.current.cornerRadius.toFloat().dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, cardShape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(cardShape)
            .background(colors.surface)
            .border(1.dp, colors.border, cardShape)
            // Prior: .shadow(3.dp, RoundedCornerShape(12.dp)) .clip(RoundedCornerShape(12.dp)) border shape 12.dp
            .clickable(enabled = showActions, onClick = onOpenDetail)
            .padding(12.dp),
    ) {
        Text(
            task.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = colors.foreground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskPriorityBadge(task.priority, colors)
            Spacer(Modifier.weight(1f))
            if (showActions) {
                IconButton(onClick = onEditTask, modifier = Modifier.size(48.dp)) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.edit, semanticLabel = "Edit task", color = colors.primary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShowHistory, modifier = Modifier.size(48.dp)) {

//Icon(Icons.Rounded.History, contentDescription = "History", tint = colors.primary, modifier = Modifier.size(18.dp))
                    com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.history, Icons.Rounded.History, contentDescription = "History", tint = colors.primary, modifier = Modifier.size(18.dp))
                }
                if (previous != null) {
                    IconButton(onClick = { onMove(task, previous) }, modifier = Modifier.size(48.dp)) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
                            semanticLabel = "Move to ${com.example.chat.domain.models.TaskWorkflow.label(previous)}",
                            color = colors.primary,
                            modifier = Modifier.size(18.dp).mirrored(),
                        )
                    }
                }
                if (next != null) {
                    IconButton(onClick = { onMove(task, next) }, modifier = Modifier.size(48.dp)) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = "Move to ${com.example.chat.domain.models.TaskWorkflow.label(next)}", color = colors.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Mirror the chevron for 'Move back' (Dart reuses the chevron glyph). */
private fun Modifier.mirrored(): Modifier =
    this.then(Modifier.graphicsLayer(scaleX = -1f, scaleY = 1f))

/** Dart _showHistory dialog. */
@Composable
private fun TaskHistoryDialog(
    task: ChatTask,
    entries: List<String>,
    colors: TaskColors,
    onClose: () -> Unit,
) {
    com.example.chat.ui.kit.ChatyConfirmationDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onClose,
        title = { Text("History — ${task.title}") },
        text = {
            if (entries.isEmpty()) {
                Text(
                    "No stage changes recorded yet.",
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else {
                // Preserve reviewed bound inside the scrollable confirmation body.
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(entries.size) { i ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {

//Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.history, Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(entries[i], fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            ChatyTextButton(onClick = onClose, label = "Close")
        },
        containerColor = colors.surface,
    )
}

/** Local search only; no network request or task mutation. P7/P8 (Bandi 2026-11-17):
 *  assignee display names are searchable in addition to title and description. */
internal fun filterTasks(
    tasks: List<ChatTask>,
    priorityFilter: String,
    query: String,
    getUser: (String) -> UserProfile? = { null },
): List<ChatTask> {
    val term = query.trim()
    return tasks.filter { task ->
        val matchesPriority = when (priorityFilter) {
            "Urgent" -> task.priority == TaskPriority.urgent
            "High" -> task.priority == TaskPriority.high
            "Med" -> task.priority == TaskPriority.medium
            "Low" -> task.priority == TaskPriority.low
            else -> true
        }
        matchesPriority && (term.isEmpty() || task.title.contains(term, ignoreCase = true) ||
            task.description.contains(term, ignoreCase = true) ||
            task.assigneeIds.any { id -> getUser(id)?.displayName?.contains(term, ignoreCase = true) == true })
    }
}
