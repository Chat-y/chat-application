package com.example.chat.ui.search

import com.example.chat.ui.kit.chatyPredictiveBack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import com.example.chat.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.example.chat.ui.effects.iosItemEntrance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import com.example.chat.ui.kit.ChatyActionButton
import com.example.chat.ui.kit.ChatyTextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.tasks.TaskAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

/** Exact port of lib/features/search/global_search_screen.dart.
 *
 *  Seams:
 *  - [searchUsers]: ChatyDataStore.searchUsersRemote - on the api (custom
 *    REST backend) branch this delegates to ApiBackendService.searchUsers
 *    (loadUsers(query) over GET /chat/users?search=, then local filter
 *    excluding self).
 *  - [openDirectConversation]: ChatyDataStore.getOrCreateDirectConversation -
 *    on the api branch ApiBackendService.createOrGetDirectConversation
 *    (POST /chat/rooms/direct {target_user_id}; 402 -> subscription error).
 *  - [verifySecretPhrase]: LocalLockService.verifySecretPhrase - reveals the
 *    locked-chats vault when the typed query matches the secret phrase.
 *  - [isConversationHidden]: preferences hidden-locked-chats privacy rule.
 *  - [onOpenLockedChats] / [onOpenConversation] (push) /
 *    [onOpenConversationReplace] (pushReplacement): navigation wiring.
 *  - [onNotify]: SnackBar equivalent for the exact error strings. */
@Composable
fun GlobalSearchScreen(
    colors: ChatyColors,
    conversations: List<Conversation>,
    searchUsers: suspend (String) -> List<UserProfile>,
    openDirectConversation: suspend (UserProfile) -> Conversation,
    modifier: Modifier = Modifier,
    verifySecretPhrase: suspend (String) -> Boolean = { false },
    isConversationHidden: (String) -> Boolean,
    onBack: () -> Unit = {},
    onOpenLockedChats: () -> Unit = {},
    onOpenConversation: (String) -> Unit = {},
    onOpenConversationReplace: (String) -> Unit = {},
    onNotify: (String) -> Unit = {},
    // Ordinary search never renders protected chat content. The owner must
    // supply the real account-scoped privacy predicate and session identity.
    isConversationProtected: (String) -> Boolean,
    searchSessionIdentity: Any,
    isSearchSessionCurrent: () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val paneLabel = stringResource(R.string.social_search_title)
    val inputLabel = stringResource(R.string.social_search_input_label)
    val busyLabel = stringResource(R.string.social_search_busy)
    val openingLabel = stringResource(R.string.social_search_opening)
    val checkingLabel = stringResource(R.string.privacy_search_checking_local)
    // Suspended work must read current host policy/callbacks after it resumes.
    val currentSessionIdentity by androidx.compose.runtime.rememberUpdatedState(searchSessionIdentity)
    val currentSessionCheck by androidx.compose.runtime.rememberUpdatedState(isSearchSessionCurrent)
    val currentHiddenCheck by androidx.compose.runtime.rememberUpdatedState(isConversationHidden)
    val currentProtectedCheck by androidx.compose.runtime.rememberUpdatedState(isConversationProtected)
    val currentReplace by androidx.compose.runtime.rememberUpdatedState(onOpenConversationReplace)
    val currentNotify by androidx.compose.runtime.rememberUpdatedState(onNotify)
    val currentUnlock by androidx.compose.runtime.rememberUpdatedState(onOpenLockedChats)
    val currentVerifier by androidx.compose.runtime.rememberUpdatedState(verifySecretPhrase)
    val currentDirectory by androidx.compose.runtime.rememberUpdatedState(searchUsers)
    val currentConversations by androidx.compose.runtime.rememberUpdatedState(conversations)
    val currentOpenDirect by androidx.compose.runtime.rememberUpdatedState(openDirectConversation)
    fun isCurrentSearchSession(): Boolean = currentSessionIdentity == searchSessionIdentity && currentSessionCheck()
    val requestGuard = remember(searchSessionIdentity) { SearchRequestGuard() }
    var query by remember(searchSessionIdentity) { mutableStateOf("") }
    var resultFilter by remember(searchSessionIdentity) { mutableStateOf("All") }
    var localOnly by remember(searchSessionIdentity) { mutableStateOf(false) }
    var showPrivacyInfo by remember { mutableStateOf(false) }
    var matchedUsers by remember(searchSessionIdentity) { mutableStateOf<List<UserProfile>>(emptyList()) }
    var directoryTruncated by remember(searchSessionIdentity) { mutableStateOf(false) }
    // Derive local matches from the live list on every composition, not a
    // snapshot taken when the query changed. Privacy can change mid-search.
    val matchedConversations = if (isCurrentSearchSession()) {
        LocalConversationSearch.filter(conversations, query) { id ->
            isConversationHidden(id) || isConversationProtected(id)
        }
    } else emptyList()
    var isSearching by remember(searchSessionIdentity) { mutableStateOf(false) }
    // Presentation only: never used for verification, routing or search eligibility.
    var verifyingRequestId by remember(searchSessionIdentity) { mutableStateOf<Int?>(null) }
    var searchFailed by remember(searchSessionIdentity) { mutableStateOf(false) }
    var isOpeningChat by remember(searchSessionIdentity) { mutableStateOf(false) }
    var openingUserId by remember(searchSessionIdentity) { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    var remoteJob by remember(searchSessionIdentity) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun performRemoteSearch(searchQuery: String, requestId: Int) {
        if (!isCurrentSearchSession()) return
        remoteJob = scope.launch {
            try {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!requestGuard.isCurrent(requestId) || !isCurrentSearchSession()) return@launch
                val users = kotlinx.coroutines.withTimeout(10_000L) { currentDirectory(searchQuery) }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!requestGuard.isCurrent(requestId) || !isCurrentSearchSession()) return@launch
                val window = SearchResultWindow.first(users) { it.id }
                matchedUsers = window.items
                directoryTruncated = window.truncated
                isSearching = false
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (requestGuard.isCurrent(requestId) && isCurrentSearchSession()) {
                    isSearching = false
                    searchFailed = true
                    matchedUsers = emptyList()
                    directoryTruncated = false
                    currentNotify(context.getString(R.string.social_search_timeout))
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!requestGuard.isCurrent(requestId) || !isCurrentSearchSession()) return@launch
                isSearching = false
                matchedUsers = emptyList()
                directoryTruncated = false
                searchFailed = true
                currentNotify(context.getString(R.string.social_search_unavailable))
            }
        }
    }

    // Exact port of _queueSearch: invalidate in-flight work first, check the
    // secret phrase, run the local filter, gate remote search on length >= 2,
    // then debounce 280ms.
    var debounceJob by remember(searchSessionIdentity) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun queueSearch(raw: String) {
        searchFailed = false
        debounceJob?.cancel()
        remoteJob?.cancel()
        verifyingRequestId = null
        val trimmed = raw.trim()
        val requestId = requestGuard.begin()
        // Keep the previous section stable while the next query loads, but disable its actions.
        if (trimmed.length < 2 || localOnly) { matchedUsers = emptyList(); directoryTruncated = false }
        if (!isCurrentSearchSession()) { isSearching = false; return }

        if (trimmed.isEmpty()) { isSearching = false; return }
        isSearching = trimmed.length >= 2 && !localOnly
        debounceJob = scope.launch {
            delay(280)
            if (!requestGuard.isCurrent(requestId) || !isCurrentSearchSession()) return@launch
            // A vault secret is not a remote username. Wait for local
            // verification before any remote search, even if it is slow.
            verifyingRequestId = requestId
            val isMatch = try {
                kotlinx.coroutines.withTimeout(10_000L) { currentVerifier(trimmed) }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (requestGuard.isCurrent(requestId) && isCurrentSearchSession()) {
                    isSearching = false
                    searchFailed = true
                    matchedUsers = emptyList()
                    directoryTruncated = false
                    currentNotify(context.getString(R.string.social_search_unavailable))
                }
                return@launch // Fail closed: never send the unverified query to the directory.
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (requestGuard.isCurrent(requestId) && isCurrentSearchSession()) {
                    isSearching = false
                    searchFailed = true
                    matchedUsers = emptyList()
                    directoryTruncated = false
                    currentNotify(context.getString(R.string.social_search_private_failure))
                }
                return@launch
            } finally {
                // An older cancelled check must not clear a newer request's indicator.
                if (verifyingRequestId == requestId) verifyingRequestId = null
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (!requestGuard.isCurrent(requestId) || !isCurrentSearchSession()) return@launch
            if (isMatch) {
                requestGuard.begin()
                query = ""
                matchedUsers = emptyList()
                directoryTruncated = false
                isSearching = false
                currentUnlock()
                return@launch
            }
            if (trimmed.length < 2 || localOnly) { isSearching = false; return@launch }
            if (requestGuard.isCurrent(requestId) && isCurrentSearchSession()) {
                performRemoteSearch(trimmed, requestId)
            }
        }
    }

    var openingJob by remember(searchSessionIdentity) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun startConversationWith(user: UserProfile) {
        if (isOpeningChat || isSearching || !(currentSessionIdentity == searchSessionIdentity && currentSessionCheck())) return
        if (SearchRecipientRules.hasProtectedDirect(user.id, currentConversations) {
                currentHiddenCheck(it) || currentProtectedCheck(it)
            }) {
            currentNotify(context.getString(R.string.social_search_open_failure))
            return
        }
        isOpeningChat = true
        openingUserId = user.id
        openingJob = scope.launch {
            try {
                if (!isCurrentSearchSession()) return@launch
                check(!SearchRecipientRules.hasProtectedDirect(user.id, currentConversations) {
                    currentHiddenCheck(it) || currentProtectedCheck(it)
                })
                val conversation = currentOpenDirect(user)
                check(conversation.id.isNotBlank() && conversation.isDirect)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (currentSessionIdentity == searchSessionIdentity && currentSessionCheck() && !currentHiddenCheck(conversation.id) &&
                    !currentProtectedCheck(conversation.id)) {
                    currentReplace(conversation.id)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (currentSessionIdentity == searchSessionIdentity && currentSessionCheck()) currentNotify(context.getString(R.string.social_search_open_failure))
            } finally {
                // Cancellation and host-navigation exceptions must not leave a busy row.
                isOpeningChat = false
                openingUserId = null
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(searchSessionIdentity) {
        onDispose {
            requestGuard.begin()
            debounceJob?.cancel()
            remoteJob?.cancel()
            openingJob?.cancel()
        }
    }

    val predictiveBack = com.example.chat.ui.kit.rememberChatyBackPresentation(Unit)
    // Previous registration retained inactive: BackHandler(onBack = onBack)
    com.example.chat.ui.kit.ChatyPredictiveBackHandler(presentation = predictiveBack, onBack = onBack)

    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    Column(modifier = modifier.chatyPredictiveBack(predictiveBack).widthIn(max = 840.dp).fillMaxSize().background(colors.background).semantics { paneTitle = paneLabel }) {
        // ChatyAppBar equivalent: back + the 40dp pill search field + the
        // progress/clear action.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = stringResource(R.string.social_search_back), color = colors.foreground)
            }
            // Row(
            // verticalAlignment = Alignment.CenterVertically,
            // modifier = Modifier
            // .weight(1f)
            // .heightIn(min = 48.dp)
            // .clip(CircleShape)
            // .background(colors.inputFill)
            // .padding(horizontal = 16.dp),
            // ) {
            // com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search,
            // semanticLabel = null,
            // color = colors.foreground.copy(alpha = 0.5f),
            // modifier = Modifier.size(20.dp),
            // )
            // Spacer(Modifier.width(8.dp))
            // BasicTextField(
            // value = query,
            // onValueChange = {
            // query = it
            // queueSearch(it)
            // },
            // singleLine = true,
            // keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrect = false),
            // keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            // textStyle = TextStyle(color = colors.foreground, fontSize = 15.sp),
            // cursorBrush = SolidColor(colors.primary),
            // modifier = Modifier.weight(1f).testTag("global-search-input").focusRequester(focusRequester).semantics {
            // contentDescription = inputLabel
            // },
            // decorationBox = { inner ->
            // Box {
            // if (query.isEmpty()) {
            // Text(
            // stringResource(R.string.social_search_hint),
            // color = colors.foreground.copy(alpha = 0.5f),
            // fontSize = 12.sp,
            // )
            // }
            // inner()
            // }
            // },
            // )
            // }
            com.example.chat.ui.kit.ChatyTaskField(
                value = query,
                onValueChange = {
                    query = it
                    queueSearch(it)
                },
                placeholder = stringResource(R.string.social_search_hint),
                accessibilityLabel = inputLabel,
                density = com.example.chat.ui.kit.ChatyFieldDensity.CompactHeader,
                modifier = Modifier.weight(1f).testTag("global-search-input").focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrect = false),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                leadingIcon = {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search,
                        semanticLabel = null, color = colors.foregroundSecondary,
                        modifier = Modifier.size(20.dp))
                },
            )
            // if (isSearching || isOpeningChat) {
            if (isSearching || isOpeningChat || verifyingRequestId != null) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp).size(18.dp).semantics {
                        // contentDescription = if (isOpeningChat) openingLabel else busyLabel
                        contentDescription = when {
                            isOpeningChat -> openingLabel
                            verifyingRequestId != null -> checkingLabel
                            else -> busyLabel
                        }
                        liveRegion = LiveRegionMode.Polite
                    },
                    strokeWidth = 2.dp,
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    query = ""
                    queueSearch("")
                    runCatching { focusRequester.requestFocus() }
                }) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.clear, semanticLabel = stringResource(R.string.social_search_clear), color = colors.foreground)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).toggleable(value = localOnly, role = Role.Checkbox, onValueChange = {
                localOnly = it
                queueSearch(query)
            }), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = localOnly, onCheckedChange = null)
                Text(stringResource(R.string.social_search_local_only), color = colors.foreground)
            }
            ChatyTextButton(onClick = { showPrivacyInfo = true }, label = stringResource(R.string.social_search_privacy_title))
        }
        if (localOnly) Text(stringResource(R.string.social_search_local_explanation), color = colors.foregroundSecondary, modifier = Modifier.padding(horizontal = 16.dp))
        if (showPrivacyInfo) androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPrivacyInfo = false },
            title = { Text(stringResource(R.string.social_search_privacy_title)) },
            text = { Text(stringResource(R.string.social_search_privacy_body)) },
            confirmButton = { // Previous stock text action:
            // androidx.compose.material3.TextButton(onClick = { showPrivacyInfo = false }) { Text(stringResource(R.string.social_search_close)) }
            com.example.chat.ui.kit.ChatyTextButton(
                label = stringResource(R.string.social_search_close),
                onClick = { showPrivacyInfo = false },
            ) },
        )
        if (query.isNotBlank()) {
            androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "People", "Chats", "Groups"), key = { it }) { filter ->
                    FilterChip(selected = resultFilter == filter, onClick = { resultFilter = filter }, label = { Text(stringResource(when (filter) {
                        "People" -> R.string.social_search_filter_people
                        "Chats" -> R.string.social_search_filter_chats
                        "Groups" -> R.string.social_search_filter_groups
                        else -> R.string.social_search_filter_all
                    })) })
                }
            }
            Text(
                if (isSearching) stringResource(R.string.social_search_busy) else stringResource(R.string.social_search_result_summary,
                    pluralStringResource(R.plurals.social_search_people_count, matchedUsers.size, matchedUsers.size),
                    pluralStringResource(R.plurals.social_search_conversation_count, matchedConversations.size, matchedConversations.size)),
                color = colors.foregroundSecondary,
                modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (query.trim().isEmpty()) {
            // _buildEmptyPrompt
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(40.dp).semantics(mergeDescendants = true) {},
            ) {

//Icon(
//                    Icons.Rounded.AlternateEmail,
//                    contentDescription = null,
//                    tint = colors.primary,
//                    modifier = Modifier.size(40.dp),
//                )
                com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.atSign, Icons.Rounded.AlternateEmail,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.social_search_discovery), color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.social_search_discovery_body),
                    color = colors.foreground.copy(alpha = 0.65f),
                    fontSize = 12.5.sp,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().imePadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (searchFailed) item(key = "search-failure", contentType = "feedback") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.social_search_failure), color = colors.foregroundSecondary)
                        ChatyTextButton(onClick = { queueSearch(query) }, label = stringResource(R.string.social_search_retry))
                    }
                }
                if (directoryTruncated && !isSearching && resultFilter in listOf("All", "People")) {
                    item(key = "directory-truncated", contentType = "feedback") {
                        Text(stringResource(R.string.social_search_truncated), color = colors.foregroundSecondary,
                            modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                if (resultFilter in listOf("All", "People") && isCurrentSearchSession() && matchedUsers.isNotEmpty()) {
                    item(key = "people_header", contentType = "header") {
                        Text(stringResource(R.string.social_search_people_header), color = colors.foregroundSecondary,
                            modifier = Modifier.padding(vertical = 8.dp).semantics { heading() })
                    }
// Previous implementation retained:                     items(matchedUsers, key = { "person:${it.id}" }, contentType = { "person" }) { user ->
// Previous implementation retained:                         UserTile(user, colors, openingUserId == user.id, query = query, enabled = !isOpeningChat && !isSearching) { startConversationWith(user) }
// Previous implementation retained:                     }
                    itemsIndexed(matchedUsers, key = { _, it -> "person:${it.id}" }, contentType = { _, _ -> "person" }) { index, user ->
                        // Row 9 (iOS-style content motion): staggered entrance per result.
                        Box(Modifier.iosItemEntrance(index, user.id)) {
                        UserTile(user, colors, openingUserId == user.id, query = query, enabled = !isOpeningChat && !isSearching) { startConversationWith(user) }
                        }
                    }
                }
                val visibleConversations = matchedConversations.filter {
                    resultFilter == "All" || resultFilter == "Chats" ||
                        (resultFilter == "Groups" && it.type == com.example.chat.domain.models.ConversationType.group)
                }.takeUnless { resultFilter == "People" }.orEmpty()
                if (visibleConversations.isNotEmpty()) {
                    item(key = "chats_header", contentType = "header") {
                        Text(stringResource(R.string.social_search_chats_header), color = colors.foregroundSecondary,
                            modifier = Modifier.padding(vertical = 8.dp).semantics { heading() })
                    }
// Previous implementation retained:                     items(visibleConversations, key = { "conversation:${it.id}" }, contentType = { "conversation" }) { conversation ->
                    itemsIndexed(visibleConversations, key = { _, it -> "conversation:${it.id}" }, contentType = { _, _ -> "conversation" }) { index, conversation ->
                        Box(Modifier.iosItemEntrance(index, conversation.id)) {
                        ConversationTile(conversation, colors, query) {
                            if (isCurrentSearchSession() && !isConversationHidden(conversation.id) &&
                                !isConversationProtected(conversation.id)) onOpenConversation(conversation.id)
                        }
                        }
                    }
                }
                if (!isSearching && !searchFailed && (resultFilter !in listOf("All", "People") || matchedUsers.isEmpty()) && visibleConversations.isEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        ) {
                            Text(
                                if (!localOnly && query.trim().length < 2 && resultFilter in listOf("All", "People")) stringResource(R.string.social_search_keep_typing) else stringResource(R.string.social_search_no_matches),
                                color = colors.foreground,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.social_search_no_matches_body),
                                color = colors.foregroundSecondary,
                                fontSize = 12.5.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            ChatyTextButton(
                                onClick = {
                                    query = ""
                                    queueSearch("")
                                    runCatching { focusRequester.requestFocus() }
                                },
                                label = stringResource(R.string.social_search_clear_search),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** ChatyGroupedSection equivalent: section header + grouped card. */
@Composable
private fun SearchSection(
    title: String,
    colors: ChatyColors,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            title,
            color = colors.foregroundSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceElevated),
        ) {
            content()
        }
    }
}

@Composable
private fun UserTile(
    user: UserProfile,
    colors: ChatyColors,
    isOpeningChat: Boolean,
    query: String,
    enabled: Boolean,
    onMessage: () -> Unit,
) {
    val messageLabel = stringResource(R.string.social_search_message_person, user.displayName, user.username)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(colors.surfaceElevated)
            .semantics {
                customActions = if (enabled) listOf(CustomAccessibilityAction(messageLabel) { onMessage(); true }) else emptyList()
            }.padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        TaskAvatar(user.avatarInitials, user.avatarColorHex, 42, colors)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    highlightSearchMatch(user.displayName, query),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    color = colors.foreground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.isVerified) {
                    Spacer(Modifier.width(4.dp))

//Icon(Icons.Rounded.Verified, contentDescription = stringResource(R.string.social_search_verified), tint = colors.primary, modifier = Modifier.size(15.dp))
                    com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.verified, Icons.Rounded.Verified, contentDescription = stringResource(R.string.social_search_verified), tint = colors.primary, modifier = Modifier.size(15.dp))
                }
            }
            Text(
                highlightSearchMatch("@${user.username}${if (user.about.isNotEmpty()) " • ${user.about}" else ""}", query),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        ChatyActionButton(
            onClick = onMessage,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = messageLabel },
        ) {
            if (isOpeningChat) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.social_search_message), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ConversationTile(
    conversation: Conversation,
    colors: ChatyColors,
    query: String,
    onOpen: () -> Unit,
) {
    val openLabel = stringResource(R.string.social_search_open_conversation)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { customActions = listOf(CustomAccessibilityAction(openLabel) { onOpen(); true }) }
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        TaskAvatar(conversation.avatarInitials ?: "CH", conversation.avatarColorHex ?: "0xFF6366F1", 42, colors)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlightSearchMatch(conversation.title, query),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.foreground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Text(
                highlightSearchMatch(conversation.lastMessageText, query),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.foreground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
            semanticLabel = null,
            color = colors.foreground.copy(alpha = 0.35f),
        )
    }
}

/** Presentation-only matching. This never changes search identity or permission checks. */
private fun highlightSearchMatch(value: String, rawQuery: String): androidx.compose.ui.text.AnnotatedString {
    val term = rawQuery.trim()
    if (term.isEmpty()) return androidx.compose.ui.text.AnnotatedString(value)
    return androidx.compose.ui.text.buildAnnotatedString {
        append(value)
        var from = 0
        while (from < value.length) {
            val index = value.indexOf(term, startIndex = from, ignoreCase = true)
            if (index < 0) break
            addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold), index, index + term.length)
            from = index + term.length
        }
    }
}

/** Fail closed while the host cannot establish current account privacy policy.
 * The results composable is removed, which cancels work and drops its in-memory query/results.
 */
@Composable
fun SearchPolicyUnavailable(onBack: () -> Unit) {
    val unavailableBack = com.example.chat.ui.kit.rememberChatyBackPresentation()
    // Previous registration retained inactive: BackHandler(onBack = onBack)
    com.example.chat.ui.kit.ChatyPredictiveBackHandler(presentation = unavailableBack, onBack = onBack)
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    Column(
        Modifier.chatyPredictiveBack(unavailableBack).fillMaxSize().background(colors.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.social_search_policy_unavailable),
            color = colors.foreground,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        // Previous stock text action:
        // androidx.compose.material3.TextButton(onClick = onBack) {
        //             Text(stringResource(R.string.social_search_back))
        //         }
        com.example.chat.ui.kit.ChatyTextButton(
            label = stringResource(R.string.social_search_back),
            onClick = onBack,
        )
    }
}

// Complete original-line retention for review (inactive).
            //             confirmButton = { androidx.compose.material3.TextButton(onClick = { showPrivacyInfo = false }) { Text(stringResource(R.string.social_search_close)) } },
