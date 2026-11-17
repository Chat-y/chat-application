package com.example.chat.ui.chats.starred

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
// Previous source retained (superseded below):
// import androidx.compose.material.icons.rounded.ChevronLeft
// Unused import retained: import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.outlined.ChatBubbleOutline
// Previous source retained (superseded below):
// import androidx.compose.material.icons.rounded.Close
// Unused import retained: import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
// Previous source retained (superseded below):
// import androidx.compose.material.icons.rounded.Star
// import androidx.compose.material.icons.rounded.StarBorder
// Unused import retained: import androidx.compose.material.icons.rounded.Star
// Required by the empty-state branch below.
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
// Previous source retained (superseded below):
// import androidx.compose.runtime.getValue
// Unused import retained: import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
// Previous source retained (superseded below):
// import androidx.compose.runtime.setValue
// Unused import retained: import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.MessageType
// Previous source retained (superseded below):
// import java.time.Instant
// Unused import retained: import java.time.Instant
import java.time.ZoneId
import java.time.Clock
import java.time.LocalDate
import com.example.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Exact port of lib/features/messages/starred_messages_screen.dart (api@3bee780).
 *
 *  House-style stateless consumer: the Dart widget reads ChatyDataStore directly
 *  and rebuilds via dataStore.addListener; here the integration layer owns the
 *  service listener and passes fresh lists down. Wiring contract:
 *  - conversations / messagesFor  <- service conversations + getMessages(id);
 *    the integration listener drives recomposition (Dart _onStoreChanged).
 *  - onRefresh <- runs ensureConversationLoaded for EVERY conversation
 *    (Dart Future.wait over dataStore.conversations); returns false when any
 *    load failed (Dart catch -> loadError banner). Called once on first
 *    composition (Dart initState) and again from the app bar, the banner Retry,
 *    and pull-to-refresh.
 *  - onToggleStar(conversationId, messageId) <- toggleStarMessage
 *    (chaty_data_store.dart:559): the service looks up the message and flips
 *    the 'starred' user-state RPC; the screen intentionally passes no value.
 *  - onOpenConversation(conversationId, messageId) <- push ChatDetailScreen
 *    with initialMessageId = messageId (this screen always passes it).
 *  Freshness contract: bump messagesRevision on every service messages emission,
 *  star-state emission and privacy registry change. The host listens to all three.
 *  Colors come from MaterialTheme.colorScheme (Dart Theme.of(context).colorScheme),
 *  not ThemeConfig - the source screen uses the Material scheme directly.
 *  Deviation: Dart MaterialBanner has no stable material3 equivalent; the error
 *  banner is rebuilt as an equivalent row (leading icon + message + Retry). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
    conversations: List<Conversation>,
    messagesFor: (String) -> List<ChatMessage>,
    // Host must increment on every message-content/star-state change, not only list identity changes.
    messagesRevision: Int = 0,
    onRefresh: suspend () -> Boolean,
    onToggleStar: (conversationId: String, messageId: String) -> Unit,
    onOpenConversation: (conversationId: String, messageId: String?) -> Unit,
    onBack: () -> Unit,
    queryStateKey: Any? = null,
    // Stable account/routing display-state key only, never credentials or account-scope objects.
    savedQueryStateKey: String? = null,
    // Optional richer host contract. Existing hosts retain the legacy callbacks.
    refreshFailures: (suspend (List<String>?) -> List<String>)? = null,
    toggleStarLocally: (suspend (String, String) -> Boolean)? = null,
    /** Host sets this only from a typed authentication failure, never a generic network error. */
    authenticationRequired: Boolean = false,
) {
    // Supplied by the parallel privacy patch. Reference-counted screen lifetime veto.
    // Native notification-listener overlays are not covered by this hook.
    com.example.chat.ui.SuppressMessageOverlaysWhileVisible()
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val reduceStarredMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    if (authenticationRequired) {
        // Return before reading messages, saving query state, indexing content or composing stale rows.
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.social_starred_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back,
                            semanticLabel = stringResource(R.string.social_starred_back))
                    }
                })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.social_starred_sign_in_again), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Assertive })
                Text(stringResource(R.string.social_starred_session_expired), textAlign = TextAlign.Center)
                // Previous stock text action:
                // TextButton(onClick = onBack) { Text(stringResource(R.string.social_starred_back)) }
                com.example.chat.ui.kit.ChatyTextButton(
                    label = stringResource(R.string.social_starred_back),
                    onClick = onBack,
                )
            }
        }
        return
    }
    val paneLabel = stringResource(R.string.social_starred_title)
    val progressLabel = stringResource(R.string.social_starred_refreshing)
    // Prior source retained:
    //     var searchText by rememberSaveable(queryStateKey, key = "starred-query:$queryStateKey") { mutableStateOf("") }
    var searchText by rememberSaveable(queryStateKey, key = "starred-query:${savedQueryStateKey ?: queryStateKey}") { mutableStateOf("") }
    var query by remember(queryStateKey) { mutableStateOf("") }
    var sortOrder by rememberSaveable(queryStateKey) { mutableStateOf("Newest first") }
    LaunchedEffect(queryStateKey, searchText) {
        if (searchText.isNotBlank()) kotlinx.coroutines.delay(250)
        query = StarredSearchRules.normalize(searchText.trim())
    }
    // Save only scroll indices/offsets with the stable account/routing display key.
    val starredListState = rememberSaveable(savedQueryStateKey,
        key = "starred-scroll:${savedQueryStateKey ?: "default"}",
        saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val keyboard = LocalSoftwareKeyboardController.current
    var loading by remember(queryStateKey) { mutableStateOf(true) }
    var loadError by remember(queryStateKey) { mutableStateOf(false) }
    var refreshQueued by remember(queryStateKey) { mutableStateOf(false) }
    val currentRefresh by androidx.compose.runtime.rememberUpdatedState(onRefresh)
    val currentDetailedRefresh by androidx.compose.runtime.rememberUpdatedState(refreshFailures)
    val currentToggle by androidx.compose.runtime.rememberUpdatedState(toggleStarLocally)
    var failedConversationIds by remember(queryStateKey) { mutableStateOf<List<String>>(emptyList()) }
    var pendingStars by remember(queryStateKey) { mutableStateOf<Set<String>>(emptySet()) }
    var starFailure by remember(queryStateKey) { mutableStateOf(false) }
    val currentQueryStateKey by androidx.compose.runtime.rememberUpdatedState(queryStateKey)
    var manualRefreshJob by remember(queryStateKey) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    androidx.compose.runtime.DisposableEffect(queryStateKey) {
        onDispose { manualRefreshJob?.cancel() }
    }
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    var calendarRevision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) { calendarRevision++ }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_TICK)
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            addAction(android.content.Intent.ACTION_LOCALE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        calendarRevision++
        onDispose { context.unregisterReceiver(receiver) }
    }
    val clock = remember(calendarRevision, configuration) { Clock.systemDefaultZone() }
    val zone = clock.zone
    val today = LocalDate.now(clock)
    // DateFormat is UI-thread confined and shared instead of allocated per visible row.
    val dateFormatter = remember(configuration, calendarRevision) { android.text.format.DateFormat.getMediumDateFormat(context) }
    val timeFormatter = remember(configuration, calendarRevision) { android.text.format.DateFormat.getTimeFormat(context) }

    // Previous source retained (superseded below):
    // suspend fun performRefresh() {
    suspend fun performRefresh(retryFailedOnly: Boolean = false) {
        try {
            do {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (currentQueryStateKey != queryStateKey) return
                refreshQueued = false
                loadError = false
                // Previous source retained (superseded below):
                // val ok = try { currentRefresh() } catch (cancelled: kotlinx.coroutines.CancellationException) {
                val requested = if (retryFailedOnly && failedConversationIds.isNotEmpty()) failedConversationIds else null
                val ok = try {
                    val detailed = currentDetailedRefresh
                    if (detailed == null) currentRefresh() else {
                        failedConversationIds = detailed(requested)
                        failedConversationIds.isEmpty()
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) { false }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (currentQueryStateKey != queryStateKey) return
                loadError = !ok
            } while (refreshQueued)
        } finally { loading = false }
    }
    // Previous source retained (superseded below):
    // fun refresh() {
    fun refresh(retryFailedOnly: Boolean = false) {
        // Coalesce repeated gestures to one follow-up; never start concurrent reloads.
        if (loading) { refreshQueued = true; return }
        loading = true
        // Previous source retained (superseded below):
        // manualRefreshJob = scope.launch { performRefresh() }
        manualRefreshJob = scope.launch { performRefresh(retryFailedOnly) }
    }
    LaunchedEffect(queryStateKey) {
        loading = true
        performRefresh()
    }

    // Dart _starredMessages: per-conversation scan, skip unstarred/deleted-for-me,
    // query over "title text typeName", newest first.
    // Capture values: callback identity does not establish backend freshness.
    // Previous source retained (superseded below):
    // // Copying still happens on recomposition; filtering and sorting are reused
    // // only while both the conversation/message snapshots and query are equal.
    // Snapshots refresh on the host revision; index construction runs off the UI thread.
    // USER-SENSITIVE: in-memory display snapshots only. Never log message text or write these
    // snapshots to plain files/SharedPreferences; future persistence needs the encrypted store.
    // Previous source retained (superseded below):
    // val messageSnapshots = conversations.map { it to messagesFor(it.id).toList() }
    // val indexed = remember(messageSnapshots, messagesRevision, zone) {
    // Previous unconditional copies repeated on loading/search/menu recomposition:
    // val messageSnapshots = conversations.map { it to messagesFor(it.id).toList() }
    // Host revision MUST cover content and privacy changes. Copy on UI thread only.
    val messageSnapshots = remember(conversations, messagesRevision, queryStateKey) {
        conversations.map { it to messagesFor(it.id).toList() }
    }
    val indexTicket = remember(messageSnapshots, zone, queryStateKey) { Any() }
    val indexResult by androidx.compose.runtime.produceState(
    // Prior batch source retained:
    //         initialValue = Pair<List<Pair<Conversation, List<ChatMessage>>>?, List<Triple<Conversation, ChatMessage, String>>>(null, emptyList()),
    //         messageSnapshots, zone, queryStateKey,
        initialValue = Pair<Any?, List<Triple<Conversation, ChatMessage, String>>>(null, emptyList()),
        indexTicket,
    ) {
        // Do not leave a previous snapshot actionable while replacing the index.
        value = null to emptyList()
        val built = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        messageSnapshots.flatMap { (conversation, messages) ->
            messages.filter { it.isStarred && !it.isDeletedForMe }.map { message ->
                Triple(conversation, message,
                    StarredSearchRules.normalize("${conversation.title} ${message.text} ${message.type.name} ${message.attachment?.name.orEmpty()} ${StarredDateRules.localDayOrNull(message.createdAt, zone)?.toString().orEmpty()}"))
            }
        }.sortedWith(compareByDescending<Triple<Conversation, ChatMessage, String>> { it.second.createdAt }
            .thenBy { it.first.id }.thenBy { it.second.id })
        }
    // Prior batch source retained:
    //         value = messageSnapshots to built
        value = indexTicket to built
    }
    // Prior batch source retained:
    //     val indexing = indexResult.first != messageSnapshots
    val indexing = indexResult.first !== indexTicket
    val indexed = if (!indexing) indexResult.second else emptyList()
    val queryTokens = remember(query) { StarredSearchRules.tokens(query) }
    // Previous source retained (superseded below):
    // val starred = remember(indexed, queryTokens, sortOrder) {
        // val matching = indexed.filter { StarredSearchRules.matchesTokens(it.third, queryTokens) }
    // Prior batch source retained:
    //     val starred = remember(indexed, queryTokens, sortOrder, conversations) {
    //         val allowedIds = conversations.map { it.id }.toSet()
    //         val matching = indexed.filter { it.first.id in allowedIds && StarredSearchRules.matchesTokens(it.third, queryTokens) }
    //             .map { it.first to it.second }
    //         when (sortOrder) {
    //             "Oldest first" -> matching.reversed()
    //             "By conversation" -> matching.sortedWith(compareBy<Pair<Conversation, ChatMessage>> { it.first.title.lowercase(java.util.Locale.ROOT) }.thenBy { it.first.id }.thenByDescending { it.second.createdAt }.thenBy { it.second.id })
    //             else -> matching
    val searchTicket = remember(indexed, queryTokens, sortOrder, conversations, indexTicket) { Any() }
    val searchResult by androidx.compose.runtime.produceState(
        initialValue = Pair<Any?, List<Pair<Conversation, ChatMessage>>>(null, emptyList()),
        searchTicket,
    ) {
        value = null to emptyList()
        val matches = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val jobContext = kotlinx.coroutines.currentCoroutineContext()
            val allowedIds = conversations.map { it.id }.toSet()
            val comparator = when (sortOrder) {
                "By conversation" -> compareBy<Pair<Conversation, ChatMessage>> { it.first.title.lowercase(java.util.Locale.ROOT) }
                    .thenBy { it.first.id }.thenByDescending { it.second.createdAt }.thenBy { it.second.id }
                else -> null
            }
            StarredSelectionRules.select(indexed,
                accepts = { it.first.id in allowedIds && StarredSearchRules.matchesTokens(it.third, queryTokens) },
                project = { it.first to it.second },
                oldestFirst = sortOrder == "Oldest first",
                comparator = comparator,
                checkActive = { jobContext.ensureActive() })
        }
        value = searchTicket to matches
    }
    val filtering = searchResult.first !== searchTicket
    val starred = if (!filtering && !indexing) searchResult.second else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text(stringResource(R.string.social_starred_title), modifier = Modifier.semantics { heading() })
                    Text(pluralStringResource(if (query.isBlank()) R.plurals.social_starred_count else R.plurals.social_starred_match_count, starred.size, starred.size), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                } },
                navigationIcon = {
                    // Dart AppBar automaticallyImplyLeading on a pushed route.
                    IconButton(onClick = onBack) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = stringResource(R.string.social_starred_back))
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }, enabled = !loading) {

//Icon(
//                            Icons.Rounded.Refresh,
//                            contentDescription = stringResource(R.string.social_starred_refresh),
//                        )
                        com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.refresh, Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.social_starred_refresh),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            val showScrollTop by remember(starredListState) {
                androidx.compose.runtime.derivedStateOf {
                    StarredScrollRules.showTop(starredListState.firstVisibleItemIndex,
                        starredListState.layoutInfo.visibleItemsInfo.size)
                }
            }
            if (showScrollTop && starred.isNotEmpty()) {
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = { scope.launch { if (reduceStarredMotion) starredListState.scrollToItem(0) else starredListState.animateScrollToItem(0) } },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                ) {

//Icon(Icons.Rounded.ArrowUpward,
//                        contentDescription = stringResource(R.string.social_starred_back_to_top))
                    com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.arrowUp, Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(R.string.social_starred_back_to_top))
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("starred-messages-screen").semantics { paneTitle = paneLabel }
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp),
            ) {
                TextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                    },
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .testTag("starred-search-input")
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrect = false),
                    label = { Text(stringResource(R.string.social_starred_search_label)) },
                    // Prior manual query mutation retained: keyboardActions = KeyboardActions(onSearch = { query = StarredSearchRules.normalize(searchText.trim()); keyboard?.hide() }),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    placeholder = { Text(stringResource(R.string.social_starred_search_hint)) },
                    leadingIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search, semanticLabel = null) },
                    trailingIcon = if (searchText.isEmpty()) {
                        null
                    } else {
                        {
                            IconButton(
                                onClick = {
                                    searchText = ""
                                    // query = "" // Settled query is derived by the effect, not manually synchronized here.
                                },
                            ) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close,
                                    semanticLabel = stringResource(R.string.social_starred_clear),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceContainerHighest.copy(alpha = 0.45f),
                        unfocusedContainerColor = colors.surfaceContainerHighest.copy(alpha = 0.45f),
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                // Previous stock text action:
                // TextButton(onClick = {
                //                     sortOrder = when (sortOrder) {
                //                         "Newest first" -> "Oldest first"
                //                         "Oldest first" -> "By conversation"
                //                         else -> "Newest first"
                //                     }
                //                 }) { Text(stringResource(R.string.social_starred_sort, stringResource(when (sortOrder) {
                //                     "Oldest first" -> R.string.social_starred_oldest
                //                     "By conversation" -> R.string.social_starred_by_conversation
                //                     else -> R.string.social_starred_newest
                //                 }))) }
                com.example.chat.ui.kit.ChatyTextButton(
                    label = stringResource(R.string.social_starred_sort, stringResource(when (sortOrder) {
                    "Oldest first" -> R.string.social_starred_oldest
                    "By conversation" -> R.string.social_starred_by_conversation
                    else -> R.string.social_starred_newest
                })),
                    onClick = {
                    sortOrder = when (sortOrder) {
                        "Newest first" -> "Oldest first"
                        "Oldest first" -> "By conversation"
                        else -> "Newest first"
                    }
                },
                )
    // Prior batch source retained:
    //                 if (loading || indexing) {
                if (loading || indexing || filtering) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp).semantics { contentDescription = progressLabel },
                    )
                }
                if (starFailure) {
                    Text(stringResource(R.string.social_starred_star_failed),
                        modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                        color = colors.error)
                }
                if (loadError) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .semantics { liveRegion = LiveRegionMode.Assertive },
                    ) {

//Icon(
//                            Icons.Rounded.SyncProblem,
//                            contentDescription = null,
//                            tint = colors.onSurfaceVariant,
//                        )
                        com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.syncProblem, Icons.Rounded.SyncProblem,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            // Previous source retained (superseded below):
                            // stringResource(R.string.social_starred_refresh_error),
                            if (failedConversationIds.isEmpty()) stringResource(R.string.social_starred_refresh_error)
                            else stringResource(R.string.social_starred_partial_failure,
                                failedConversationIds.mapNotNull { id -> conversations.firstOrNull { it.id == id }?.title }.joinToString()),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        // Previous source retained (superseded below):
                        // TextButton(onClick = { refresh() }) { Text(stringResource(R.string.social_starred_retry)) }
                        // Previous stock text action:
                        // TextButton(onClick = { refresh(retryFailedOnly = true) }) { Text(stringResource(R.string.social_starred_retry)) }
                        com.example.chat.ui.kit.ChatyTextButton(
                            label = stringResource(R.string.social_starred_retry),
                            onClick = { refresh(retryFailedOnly = true) },
                        )
                    }
                }
                PullToRefreshBox(
                    isRefreshing = loading,
                    onRefresh = { refresh() },
                    modifier = Modifier.weight(1f),
                ) {
                    if (starred.isEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 72.dp),
                        ) {
                            item {
                                Column(
                                    modifier = Modifier.fillParentMaxWidth().semantics(mergeDescendants = true) {},
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        if (query.isEmpty()) {
                                            Icons.Rounded.StarBorder
                                        } else {
                                            Icons.Rounded.SearchOff
                                        },
                                        contentDescription = null,
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Text(
    // Prior batch source retained:
    //                                         if (loading || indexing) {
                                        if (loading || indexing || filtering) {
                                            stringResource(R.string.social_starred_loading_title)
                                        } else if (loadError) {
                                            stringResource(R.string.social_starred_failed_title)
                                        } else if (query.isEmpty()) {
                                            stringResource(R.string.social_starred_empty_title)
                                        } else {
                                            stringResource(R.string.social_starred_no_matches)
                                        },
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.W700,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
    // Prior batch source retained:
    //                                         if (loading || indexing) {
                                        if (loading || indexing || filtering) {
                                            stringResource(R.string.social_starred_loading_body)
                                        } else if (loadError) {
                                            stringResource(R.string.social_starred_failed_body)
                                        } else if (query.isEmpty()) {
                                            stringResource(R.string.social_starred_empty_body)
                                        } else {
                                            stringResource(R.string.social_starred_search_empty_body)
                                        },
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = starredListState,
                            modifier = Modifier.fillMaxSize().testTag("starred-message-list"),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                top = 8.dp,
                                end = 12.dp,
                                bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Stable keys keep row state attached to the right
                            // starred message when entries are added/removed.
                            items(starred.size, key = { index ->
                                val (conversation, message) = starred[index]
                                StarredSearchRules.rowKey(conversation.id, message.id)
                            }, contentType = { "starred_message" }) { index ->
                                val (conversation, message) = starred[index]
                                StarredMessageRow(
                                    conversation = conversation,
                                    message = message,
                                    zone = zone,
                                    today = today,
                                    dateFormatter = dateFormatter,
                                    timeFormatter = timeFormatter,
                                    onOpen = {
                                        onOpenConversation(conversation.id, message.id.takeUnless { message.isDeletedForEveryone })
                                    },
                                    // Previous source retained (superseded below):
                                    // onUnstar = { onToggleStar(conversation.id, message.id) },
                                    starPending = StarredSearchRules.rowKey(conversation.id, message.id) in pendingStars,
                                    onUnstar = {
                                        val key = StarredSearchRules.rowKey(conversation.id, message.id)
                                        if (key !in pendingStars) {
                                            pendingStars = pendingStars + key
                                            starFailure = false
                                            scope.launch {
                                                try {
                                                    // Boolean acknowledges LOCAL state only, never server persistence.
                                                    val ok = currentToggle?.invoke(conversation.id, message.id)
                                                        ?: run { onToggleStar(conversation.id, message.id); true }
                                                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                                    if (currentQueryStateKey == queryStateKey) starFailure = !ok
                                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                                                } catch (_: Exception) {
                                                    if (currentQueryStateKey == queryStateKey) starFailure = true
                                                } finally { pendingStars = pendingStars - key }
                                            }
                                        }
                                    },
                                )
                                // Spacing belongs to LazyColumn, not an extra item node.
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dart Card + ListTile row (contentPadding 14/7/6/7) with the trailing
 *  PopupMenuButton ('open' / 'unstar') rendered as star + date. */
@Composable
private fun StarredMessageRow(
    conversation: Conversation,
    message: ChatMessage,
    zone: ZoneId,
    today: LocalDate,
    dateFormatter: java.text.DateFormat,
    timeFormatter: java.text.DateFormat,
    onOpen: () -> Unit,
    onUnstar: () -> Unit,
    starPending: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val unavailable = stringResource(R.string.social_starred_time_unavailable)
    val dateLabel = remember(message.createdAt, dateFormatter, timeFormatter, zone, today, unavailable) {
        StarredDateRules.label(message.createdAt, zone, today, unavailable, timeFormatter::format, dateFormatter::format)
    }
    // Prior source retained:
    //     val rowDescription = stringResource(R.string.social_starred_row_description, conversation.title)
    val preview = previewText(message)
    val rowDescription = stringResource(R.string.social_starred_row_summary, conversation.title, preview, dateLabel)
    var rowFocused by remember(conversation.id, message.id) { mutableStateOf(false) }
    val openLabel = stringResource(R.string.social_starred_open)
    val unstarLabel = stringResource(R.string.social_starred_remove)
    val menuLabel = stringResource(R.string.social_starred_options_description)
    var menuOpen by remember(conversation.id, message.id) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
    // Prior source retained:
    //             1.dp,
    //             colors.outlineVariant.copy(alpha = 0.55f),
            if (rowFocused) 2.dp else 1.dp,
            if (rowFocused) colors.primary else colors.outlineVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("starred-message:${conversation.id}:${message.id}")
    // Prior source retained:
    //                 .semantics {
                .onFocusChanged { rowFocused = it.hasFocus }
                .semantics(mergeDescendants = true) {
                    contentDescription = rowDescription
                    customActions = listOf(
                        CustomAccessibilityAction(openLabel) { onOpen(); true },
                        // Previous source retained (superseded below):
                        // CustomAccessibilityAction(unstarLabel) { onUnstar(); true },
                        CustomAccessibilityAction(unstarLabel) { if (!starPending) onUnstar(); !starPending },
                    )
                }
                .clickable(role = Role.Button, onClick = onOpen)
    // Prior source retained:
    //                 .padding(start = 14.dp, top = 7.dp, end = 6.dp, bottom = 7.dp),
                .padding(start = 14.dp, top = 7.dp, end = 0.dp, bottom = 7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (conversation.type == ConversationType.group) {
                        Icons.Rounded.Groups
                    } else {
                        Icons.Outlined.ChatBubbleOutline
                    },
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
    // Prior source retained:
    //             Column(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.weight(1f).clearAndSetSemantics { }) {
                Text(
                    conversation.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W700,
                )
                Text(
    // Prior source retained:
    //                     previewText(message),
                    preview,
                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) 6 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Text(dateLabel, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Box {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("starred-menu:${conversation.id}:${message.id}")
                        // Previous source retained (superseded below):
                        // .clickable(role = Role.Button, onClickLabel = menuLabel) { menuOpen = true }
                        .clickable(enabled = !starPending, role = Role.Button, onClickLabel = menuLabel) { menuOpen = true }
                        .padding(12.dp),
                ) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.starFilled,
                        semanticLabel = menuLabel,
                        color = colors.primary,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    // Previous source retained (superseded below):
                    // Text(stringResource(R.string.social_starred_options), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(if (starPending) R.string.social_starred_saving else R.string.social_starred_options), style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape,
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chats, semanticLabel = null)
                        },
                        text = { Text(stringResource(R.string.social_starred_open)) },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.star, semanticLabel = null)
                        },
                        enabled = !starPending,
                        text = { Text(stringResource(R.string.social_starred_remove)) },
                        onClick = {
                            menuOpen = false
                            onUnstar()
                        },
                    )
                }
            }
        }
    }
}

/** Dart _previewText: deleted-for-everyone placeholder, trimmed text, else the
 *  per-type label (MessageType.text's own fallback is "Message attachment"). */
@Composable
private fun previewText(message: ChatMessage): String {
    if (message.isDeletedForEveryone) return stringResource(R.string.social_starred_deleted)
    val text = message.text.trim()
    if (text.isNotEmpty()) return text
    return when (message.type) {
        MessageType.image -> stringResource(R.string.social_starred_photo)
        MessageType.video -> stringResource(R.string.social_starred_video)
        MessageType.audio -> stringResource(R.string.social_starred_voice)
        MessageType.document -> stringResource(R.string.social_starred_document)
        MessageType.location -> stringResource(R.string.social_starred_location)
        MessageType.contact -> stringResource(R.string.social_starred_contact)
        MessageType.taskCard -> stringResource(R.string.social_starred_task)
        MessageType.system -> stringResource(R.string.social_starred_system)
        MessageType.text -> stringResource(R.string.social_starred_blank)
    }
}
