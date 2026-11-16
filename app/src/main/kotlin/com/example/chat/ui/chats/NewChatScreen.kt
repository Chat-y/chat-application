package com.example.chat.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.example.chat.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.GroupWork
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.newchat.NewChatRules
import com.example.chat.ui.chats.newchat.NewChatPresentationRules
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

/** Port of new_chat_screen.dart (api branch anchor 3bee780): contact/user
 *  directory search with the 250ms debounce + epoch gate, direct-chat open,
 *  group/broadcast member selection with the exact Dart title rules, and the
 *  long-press contact actions sheet.
 *
 *  All backend access arrives as suspend lambdas so this screen never
 *  touches shared state directly; AppRoot wires them to ApiBackendService
 *  (searchUsersRemote / createOrGetDirectConversation / createGroupRoom /
 *  contacts APIs). Account deletion is intentionally not exposed here.
 *  Skeleton/empty/no-results states mirror the Dart
 *  ChatySkeletonTile / ChatyEmptyState / ChatyNoResultsState widgets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    contacts: List<UserProfile>,
    startInGroupMode: Boolean = false,
    startInBroadcastMode: Boolean = false,
    searchUsersRemote: suspend (String) -> List<UserProfile>,
    openDirectConversation: suspend (UserProfile) -> Conversation,
    createGroup: suspend (String, List<String>) -> Conversation,
    @Suppress("UNUSED_PARAMETER") deleteUser: suspend (String) -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onBack: () -> Unit,
    // Contacts plane (research 5.3): username search -> request -> inbox.
    // Null = pre-plane server: the screen keeps its legacy tap-to-chat flow.
    loadContactRelations: (suspend () -> Pair<List<com.example.chat.data.api.ApiContactsClient.ContactEntry>, List<com.example.chat.data.api.ApiContactsClient.ContactRequestItem>>)? = null,
    sendContactRequest: (suspend (String) -> Unit)? = null,
    respondContactRequest: (suspend (String, Boolean) -> Unit)? = null,
    loadOutgoingRequests: (suspend () -> List<com.example.chat.data.api.ApiContactsClient.ContactRequestItem>)? = null,
    /** Fail closed unless the host can perform a fresh account-bound eligibility check. */
    validateRecipients: suspend (Set<String>) -> Boolean = { false },
    /** Separate delivery semantics: never emulate this by adding Broadcast to a group title. */
    createBroadcast: (suspend (String, List<String>) -> Conversation)? = null,
) {
    val avatarFallbackArgb = LocalThemeConfig.current.accentColor
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val configContext = androidx.compose.ui.platform.LocalContext.current
    val configApi = com.example.chat.ServiceLocator.apiBackend
    val configOwner = remember { configApi.authSnapshot() }
    // Never let an old contact selection become an operation on a newly selected account.
    val currentOnBack by androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.compose.runtime.DisposableEffect(configApi, configOwner) {
        val listener: (com.example.chat.data.api.ApiAccountScope) -> Unit = { next ->
            if (next !== configOwner) scope.launch { currentOnBack() }
        }
        configApi.addAuthScopeListener(listener)
        onDispose { configApi.removeAuthScopeListener(listener) }
    }
    val choiceStore = remember(configOwner) { configApi.currentAccountHash()?.let { hash ->
        runCatching { com.example.chat.core.config.GroupUiChoicesStore(configContext, hash) }.getOrNull()
    } }
    var groupUi by remember(configOwner) { mutableStateOf(com.example.chat.core.config.GroupUiConfigPolicy.defaults) }
    LaunchedEffect(configOwner) {
        try {
            val defaults = configApi.groupUiDefaults(configOwner)
            val choices = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { choiceStore?.read() }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (configApi.authSnapshot() === configOwner) groupUi = choices?.applyTo(defaults) ?: defaults
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (_: Exception) {
            // Keep safe local defaults if host config/cache storage throws.
            if (configApi.authSnapshot() === configOwner) groupUi = com.example.chat.core.config.GroupUiConfigPolicy.defaults
        }
    }

    val titleLimit = groupUi.nameMaxLength.coerceAtLeast(1)
    val draftAccountKey = remember(configOwner, startInGroupMode, startInBroadcastMode) {
        "${configApi.currentAccountHash().orEmpty()}:$startInGroupMode:$startInBroadcastMode"
    }
    var searchText by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode, key = "newchat-query:$draftAccountKey") { mutableStateOf("") }
    var requestedGroupDetails by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode,
        key = "newchat-review:$draftAccountKey") { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var showDiscardSelection by remember { mutableStateOf(false) }
    val memberNames = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    val memberUsernames = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var groupName by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode, key = "newchat-title:$draftAccountKey") { mutableStateOf("") }
    val selectedIdSaver = remember {
        androidx.compose.runtime.saveable.listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
    }
    // Persist immutable IDs only, never directory/profile payloads or callback/session objects.
    var selectedMembers by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode,
        key = "newchat-members:$draftAccountKey", stateSaver = selectedIdSaver) { mutableStateOf(setOf<String>()) }
    val selectedProfiles = remember(configOwner) { androidx.compose.runtime.mutableStateMapOf<String, UserProfile>() }
    LaunchedEffect(selectedMembers) {
        // Retain identity labels only while they support an intentional selection.
        selectedProfiles.keys.toList().filterNot { it in selectedMembers }.forEach { selectedProfiles.remove(it) }
        memberNames.keys.toList().filterNot { it in selectedMembers }.forEach { memberNames.remove(it) }
        memberUsernames.keys.toList().filterNot { it in selectedMembers }.forEach { memberUsernames.remove(it) }
    }
    var results by remember { mutableStateOf(emptyList<UserProfile>()) }
    var acceptedContacts by remember { mutableStateOf(emptyList<UserProfile>()) }
    var contactLoadError by remember { mutableStateOf<String?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var isCreatingGroup by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode, key = "newchat-group:$draftAccountKey") { mutableStateOf(startInGroupMode || startInBroadcastMode) }
    var isBroadcast by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode, key = "newchat-broadcast:$draftAccountKey") { mutableStateOf(startInBroadcastMode) }
    val selectedProfileSnapshot = selectedProfiles.toMap()
    val selectionProfiles = remember(contacts, acceptedContacts, results, selectedProfileSnapshot) {
        (selectedProfileSnapshot.values + contacts + acceptedContacts + results).associateBy { it.id }
    }
    val selectionEligible = NewChatRules.validSelection(selectedMembers, configOwner.userId, selectionProfiles.keys)
    // A restored review never renders as actionable while any saved identity is unresolved.
    val groupDetails = requestedGroupDetails && isCreatingGroup && selectionEligible
    var queryLoading by remember { mutableStateOf(false) }
    var contactsLoading by remember { mutableStateOf(false) }
    var isSubmittingGroup by remember { mutableStateOf(false) }
    // Session-only uncertainty. The owner can inspect chats and explicitly release it.
    var roomCreationBlocked by remember(configOwner) { mutableStateOf(false) }
    val roomUncertainMessage = "Group or broadcast creation was not confirmed. Check chats first. Creating again may make a duplicate room."
    var isOpeningDirect by remember(configOwner) { mutableStateOf(false) }
    val isLoading = queryLoading || contactsLoading || isSubmittingGroup || isOpeningDirect
    val operationLabel = stringResource(when {
        isSubmittingGroup -> if (isBroadcast) R.string.social_newchat_creating_broadcast else R.string.social_newchat_creating
        isOpeningDirect -> R.string.social_newchat_opening
        queryLoading -> R.string.social_newchat_checking
        else -> R.string.social_newchat_loading_contacts
    })
    var epoch by remember { mutableIntStateOf(0) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    androidx.compose.runtime.DisposableEffect(configOwner) {
        onDispose { debounceJob?.cancel(); searchJob?.cancel() }
    }
    var sheetUser by remember { mutableStateOf<UserProfile?>(null) }

    fun errorText(error: Throwable): String =
        configContext.getString(when (error) {
            is java.io.IOException -> R.string.social_newchat_error_connection
            is SecurityException -> R.string.social_newchat_error_permission
            else -> R.string.social_newchat_error_request
        })

    // Contacts plane state (research 5.3). Empty/absent on pre-plane servers.
    var contactIds by remember { mutableStateOf(setOf<String>()) }
    var contactUsernames by remember { mutableStateOf(setOf<String>()) }
    var contactRequests by remember { mutableStateOf(listOf<com.example.chat.data.api.ApiContactsClient.ContactRequestItem>()) }
    var requestedUsernames by remember { mutableStateOf(setOf<String>()) }
    var requestedUserIds by remember(configOwner) { mutableStateOf(setOf<String>()) }
    var uncertainRequestIds by rememberSaveable(configOwner, key = "newchat-uncertain:$draftAccountKey",
        stateSaver = selectedIdSaver) { mutableStateOf(setOf<String>()) }
    var outgoingReady by remember(configOwner) { mutableStateOf(false) }
    var outgoingLoading by remember(configOwner) { mutableStateOf(false) }
    var sendingRequestUsernames by remember(configOwner) { mutableStateOf(setOf<String>()) }
    var sendingRequestIds by remember(configOwner) { mutableStateOf(setOf<String>()) }
    var outgoingRevision by remember(configOwner) { mutableIntStateOf(0) }
    var contactsRevision by remember(configOwner) { mutableIntStateOf(0) }
    var respondingRequestIds by remember { mutableStateOf(setOf<String>()) }

    fun isContact(user: UserProfile): Boolean =
        user.id in contactIds || NewChatRules.usernameKey(user.username) in contactUsernames

    val contactsById = remember(contacts) { contacts.associateBy { it.id } }

    fun contactProfile(entry: com.example.chat.data.api.ApiContactsClient.ContactEntry): UserProfile =
        contactsById[entry.userId] ?: UserProfile(
            id = entry.userId,
            displayName = entry.displayName.ifBlank { entry.username },
            username = entry.username,
            avatarInitials = entry.displayName.ifBlank { entry.username }.take(2).uppercase(),
            avatarColorHex = "0x" + java.lang.Long.toHexString(avatarFallbackArgb),
            about = entry.about,
            avatarUrl = entry.avatarUrl,
            lastSeenAt = java.time.Instant.EPOCH,
        )

    suspend fun refreshOutgoingRequests() {
        val loader = loadOutgoingRequests ?: return
        if (outgoingLoading || configApi.authSnapshot() !== configOwner) return
        outgoingLoading = true
        val readRevision = outgoingRevision
        try {
            val outgoing = loader()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (configApi.authSnapshot() !== configOwner) return
            if (readRevision != outgoingRevision) return // Never overwrite a newer local send with an older snapshot.
            requestedUserIds = outgoing.map { it.from.userId }.filter { it.isNotBlank() }.toSet()
            requestedUsernames = outgoing.map { NewChatRules.usernameKey(it.from.username) }.toSet()
            uncertainRequestIds = uncertainRequestIds - requestedUserIds - contactIds
            outgoingReady = true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (configApi.authSnapshot() === configOwner && readRevision == outgoingRevision) outgoingReady = false
        } finally { outgoingLoading = false }
    }

    // Empty search is the accepted-contacts list, never the workspace directory.
    suspend fun refreshAcceptedContacts() {
        if (contactsLoading || configApi.authSnapshot() !== configOwner) return
        contactsLoading = true
        val readRevision = contactsRevision
        contactLoadError = null
        try {
            if (loadContactRelations != null) {
                val (entries, inbox) = loadContactRelations()
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return
                if (readRevision != contactsRevision) return // A completed accept/decline is newer than this fetch.
                contactIds = entries.map { it.userId }.toSet()
                contactUsernames = entries.map { NewChatRules.usernameKey(it.username) }.toSet()
                acceptedContacts = entries.filter { it.userId.isNotBlank() && it.userId != configOwner.userId }
                    .map(::contactProfile).distinctBy { it.id }
                contactRequests = inbox.filter { it.requestId.isNotBlank() && it.from.userId.isNotBlank() }
                    .distinctBy { it.requestId }
            } else {
                contactLoadError = configContext.getString(R.string.social_newchat_contacts_server_unavailable)
            }
            if (searchText.isBlank()) results = acceptedContacts
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (error: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (configApi.authSnapshot() === configOwner && readRevision == contactsRevision) {
                contactLoadError = configContext.getString(R.string.social_newchat_contacts_load_failure, errorText(error))
            }
        } finally {
            contactsLoading = false
        }
    }

    fun respondToRequest(req: com.example.chat.data.api.ApiContactsClient.ContactRequestItem, accept: Boolean) {
        if (req.requestId in respondingRequestIds) return
        val responder = respondContactRequest
        respondingRequestIds = respondingRequestIds + req.requestId
        scope.launch {
            try {
                checkNotNull(responder) { configContext.getString(R.string.social_newchat_requests_unavailable) }
                if (configApi.authSnapshot() !== configOwner) return@launch
                responder.invoke(req.requestId, accept)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                contactsRevision++
                contactRequests = contactRequests.filterNot { it.requestId == req.requestId }
                if (accept) {
                    contactIds = contactIds + req.from.userId
                    contactUsernames = contactUsernames + NewChatRules.usernameKey(req.from.username)
                    acceptedContacts = (acceptedContacts + contactProfile(req.from)).distinctBy { it.id }
                    if (searchText.isBlank()) results = acceptedContacts
                }
                snackbar.showSnackbar(if (accept) configContext.getString(R.string.social_newchat_contact_added, req.from.displayName) else configContext.getString(R.string.social_newchat_request_declined))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() === configOwner) snackbar.showSnackbar(configContext.getString(R.string.social_newchat_request_update_failure))
            } finally { respondingRequestIds = respondingRequestIds - req.requestId }
        }
    }

    fun runSearch(query: String, searchEpoch: Int) {
        searchJob = scope.launch {
            try {
                if (configApi.authSnapshot() !== configOwner || !NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                val users = kotlinx.coroutines.withTimeout(10_000L) { searchUsersRemote(query) }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                if (!NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                // The production host already does this; repeat the fail-closed contract at the UI seam.
                results = NewChatRules.exactUsernameMatch(users, query, configOwner.userId)
                queryLoading = false
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner || !NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                queryLoading = false
                results = emptyList()
                searchError = configContext.getString(R.string.social_newchat_lookup_timeout)
                snackbar.showSnackbar(searchError!!)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (error: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner || !NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                queryLoading = false
                results = emptyList()
                searchError = errorText(error)
                snackbar.showSnackbar(searchError!!)
            }
        }
    }

    // _onSearchChanged: cancel debounce; empty query restores contacts;
    // otherwise epoch++ + 250ms debounce before the remote search.
    fun onSearchChanged(value: String) {
        searchText = value
        searchError = null
        debounceJob?.cancel()
        searchJob?.cancel()
        val query = value.trim()
        val nextEpoch = ++epoch // Also invalidate an in-flight search when cleared.
        if (query.isEmpty()) {
            results = acceptedContacts
            queryLoading = false
            return
        }
        results = emptyList() // Never leave a stale result actionable while a new username is checked.
        queryLoading = true
        debounceJob = scope.launch {
            delay(NewChatRules.SEARCH_DEBOUNCE_MS)
            runSearch(query, nextEpoch)
        }
    }

    LaunchedEffect(configOwner) {
        refreshAcceptedContacts()
        refreshOutgoingRequests()
        if (searchText.isNotBlank() && configApi.authSnapshot() === configOwner) onSearchChanged(searchText)
    }

    fun finishGroupCreation() {
        val title = NewChatRules.effectiveGroupTitle(groupName, isBroadcast, selectedMembers.size)
        if (!NewChatRules.canCreateGroup(groupName, isBroadcast, selectedMembers.size, isLoading)) return
        if (roomCreationBlocked) { createError = roomUncertainMessage; return }
        if (!groupDetails || !selectionEligible || isSubmittingGroup || configApi.authSnapshot() !== configOwner) return
        if (!NewChatRules.validFinalTitle(groupName, isBroadcast, selectedMembers.size, titleLimit)) { createError = configContext.getString(R.string.social_newchat_name_too_long); return }
        val creator = NewChatRules.creatorFor(isBroadcast, createGroup, createBroadcast)
        if (creator == null) { createError = configContext.getString(R.string.social_newchat_broadcast_unavailable); return }
        val capturedMembers = selectedMembers.toList()
        isSubmittingGroup = true // Independent of search/contact loaders, which may finish concurrently.
        createError = null
        scope.launch {
            try {
                check(validateRecipients(capturedMembers.toSet()))
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                // Reserve immediately before calling the non-idempotent creator.
                roomCreationBlocked = true
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                val conversation = creator(title, capturedMembers)
                check(NewChatRules.validConversation(conversation, configOwner.userId, capturedMembers.toSet(), group = true))
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() === configOwner) {
                    roomCreationBlocked = false
                    if (configApi.authSnapshot() === configOwner) onOpenConversation(conversation)
                }
                /* Previous unreserved creation path retained.
                val conversation = creator(title, capturedMembers)
                check(NewChatRules.validConversation(conversation, configOwner.userId, capturedMembers.toSet(), group = true))
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() === configOwner) onOpenConversation(conversation)
                */
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (error: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                createError = if (roomCreationBlocked) roomUncertainMessage else errorText(error)
                snackbar.showSnackbar(createError!!)
            } finally { isSubmittingGroup = false }
        }
    }

    fun openDirectChat(user: UserProfile) {
        if (isOpeningDirect || isSubmittingGroup || queryLoading || contactsLoading || configApi.authSnapshot() !== configOwner) return
        isOpeningDirect = true
        scope.launch {
            try {
                check(user.id.isNotBlank() && user.id != configOwner.userId && validateRecipients(setOf(user.id)))
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                val conversation = openDirectConversation(user)
                check(NewChatRules.validConversation(conversation, configOwner.userId, setOf(user.id), group = false))
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() === configOwner) onOpenConversation(conversation)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                snackbar.showSnackbar(errorText(error))
            } finally { isOpeningDirect = false }
        }
    }

    val selectedMemberControl: @Composable (String) -> Unit = { id ->
        val current = selectionProfiles[id]
        val name = current?.displayName?.takeIf { it.isNotBlank() } ?: memberNames[id]
            ?: stringResource(R.string.social_newchat_unavailable_member)
        val username = current?.username?.takeIf { it.isNotBlank() } ?: memberUsernames[id]
        val identity = stringResource(R.string.social_newchat_selected_identity, NewChatPresentationRules.identityText(name),
            NewChatPresentationRules.identityText(username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: id))
        val display = if (current == null) stringResource(R.string.social_newchat_unavailable_identity, identity) else identity
        val removeLabel = stringResource(R.string.social_newchat_remove_member, display)
        TextButton(enabled = !isLoading, onClick = { requestedGroupDetails = false; selectedMembers = selectedMembers - id },
            modifier = Modifier.widthIn(max = 360.dp)) {
            Text(removeLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val selectedSection: @Composable () -> Unit = {
        if (isCreatingGroup) {
            Text(if (groupDetails) stringResource(if (isBroadcast) R.string.social_newchat_broadcast_review_step else R.string.social_newchat_review_step) else stringResource(R.string.social_newchat_selection_step),
                color = colors.foregroundSecondary, modifier = Modifier.semantics { heading() })
            if (groupUi.showSelectedCount) Text(
                pluralStringResource(R.plurals.social_newchat_selected_count, selectedMembers.size, selectedMembers.size),
                color = colors.foregroundSecondary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (groupUi.selectedDisplay == "list") LazyColumn(
                Modifier.heightIn(max = 160.dp),
            ) {
                items(selectedMembers.toList(), key = { it }) { id -> selectedMemberControl(id) }
            } else androidx.compose.foundation.lazy.LazyRow {
                items(selectedMembers.toList(), key = { it }) { id -> selectedMemberControl(id) }
            }
            if (selectedMembers.isNotEmpty() && !selectionEligible) Text(
                stringResource(R.string.social_newchat_restore_unavailable), color = colors.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (roomCreationBlocked) {
                Text(roomUncertainMessage, color = colors.error)
                Column {
                    TextButton(enabled = !isSubmittingGroup, onClick = { onBack() }) { Text("Check chats") }
                    TextButton(enabled = !isSubmittingGroup, onClick = {
                        // Explicit release only, never automatically submit from this button.
                        roomCreationBlocked = false; createError = null
                    }) { Text("Create anyway") }
                }
            } else createError?.let { Text(it, color = colors.error) }
        }
    }
    val searchSection: @Composable () -> Unit = {
                if (!groupDetails) NewChatInput(
                    value = searchText,
                    onValueChange = { onSearchChanged(it) },
                    hint = if (isCreatingGroup) groupUi.searchLabel else stringResource(R.string.social_newchat_username_hint),
                    label = stringResource(R.string.social_newchat_username_label),
                    fieldError = searchError,
                    imeAction = ImeAction.Search,
                    onImeAction = { onSearchChanged(searchText) },
                    leading = {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search,
                            semanticLabel = null,
                            color = colors.foregroundTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailing = if (searchText.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchChanged("") }, modifier = Modifier.size(48.dp)) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close,
                                    semanticLabel = stringResource(R.string.social_newchat_clear_search),
                                    color = colors.foregroundSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
                if (queryLoading && !groupDetails) Text(
                    stringResource(R.string.social_newchat_checking), color = colors.foregroundSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
    }
    val requestsContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            // Incoming contact requests inbox (research 5.3: Signal's
            // message-request pattern applied to contacts).
            if (!isCreatingGroup && contactRequests.isNotEmpty()) {
                item(key = "request-header", contentType = "header") {
                    Text(
                        stringResource(R.string.social_newchat_requests_title),
                        color = colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp).semantics { heading() },
                    )
                }
                    items(contactRequests.distinctBy { it.requestId }, key = { "request:${it.requestId}" }, contentType = { "contact-request" }) { req ->
                        val pending = req.requestId in respondingRequestIds
                        val acceptLabel = stringResource(R.string.social_newchat_accept_request, req.from.username)
                        val declineLabel = stringResource(R.string.social_newchat_decline_request, req.from.username)
                        val pendingLabel = stringResource(R.string.social_newchat_request_updating)
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    NewChatPresentationRules.identityText(req.from.displayName),
                                    color = colors.foreground,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("@${NewChatPresentationRules.identityText(req.from.username)}", color = colors.foregroundSecondary, fontSize = 12.sp,
                                    style = TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                            TextButton(
                                enabled = !pending, onClick = { respondToRequest(req, true) },
                                modifier = Modifier.semantics {
                                    contentDescription = acceptLabel
                                    if (pending) stateDescription = pendingLabel
                                },
                            ) {
                                Text(stringResource(R.string.social_newchat_accept), color = colors.primary, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                enabled = !pending, onClick = { respondToRequest(req, false) },
                                modifier = Modifier.semantics {
                                    contentDescription = declineLabel
                                    if (pending) stateDescription = pendingLabel
                                },
                            ) {
                                Text(stringResource(R.string.social_newchat_decline), color = colors.error)
                            }
                            }
                        }
                    }
                item(key = "request-spacing", contentType = "spacing") { Spacer(Modifier.height(4.dp)) }
            }
    }
    val contactsContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            when {
                groupDetails -> Unit
                isLoading && results.isEmpty() -> {
                    items(8, key = { "loading:$it" }, contentType = { "newchat-loading" }) { SkeletonTile() }
                }
                results.isEmpty() && searchText.trim().isNotEmpty() -> item(key = "username-feedback", contentType = "feedback") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text(
                            searchError?.let { stringResource(R.string.social_newchat_lookup_failed_title) } ?: stringResource(R.string.social_newchat_lookup_empty_title),
                            color = colors.foreground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.social_newchat_checked_username,
                                NewChatPresentationRules.identityText(NewChatRules.normalizedUsername(searchText))),
                            style = TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                            color = colors.foregroundSecondary, fontSize = 14.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            searchError ?: stringResource(R.string.social_newchat_lookup_empty_body),
                            color = colors.foregroundSecondary,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (searchError != null) TextButton(onClick = { onSearchChanged(searchText) }) {
                            Text(stringResource(R.string.social_newchat_lookup_retry), color = colors.primary)
                        }
                        TextButton(onClick = { onSearchChanged("") }) {
                            Text(stringResource(R.string.social_newchat_clear_search), color = colors.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                results.isEmpty() -> item(key = "contacts-feedback", contentType = "feedback") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PersonSearch,
                            contentDescription = null,
                            tint = colors.foregroundTertiary,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (contactLoadError == null) stringResource(R.string.social_newchat_contacts_empty_title) else stringResource(R.string.social_newchat_contacts_unavailable_title),
                            color = colors.foreground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            contactLoadError ?: stringResource(R.string.social_newchat_contacts_empty_body),
                            color = colors.foregroundSecondary,
                            fontSize = 13.sp,
                        )
                        if (contactLoadError != null && loadContactRelations != null) {
                            TextButton(onClick = { scope.launch { refreshAcceptedContacts() } }) {
                                Text(stringResource(R.string.social_newchat_retry), color = colors.accent)
                            }
                        }
                    }
                }
                else -> {
                    item(key = "contact-results-header", contentType = "header") {
                        Text(pluralStringResource(R.plurals.social_newchat_result_count, results.size, results.size),
                            color = colors.foregroundSecondary, modifier = Modifier.padding(vertical = 4.dp).semantics { heading() })
                    }
                        itemsIndexed(results, key = { _, u -> "person:${u.id}" }, contentType = { _, _ -> "newchat-person" }) { index, user ->
                            val isSelected = selectedMembers.contains(user.id)
                            val activateUser: () -> Unit = {
                                if (!isLoading && !groupDetails) {
                                    if (isCreatingGroup) {
                                        requestedGroupDetails = false
                                        selectedProfiles[user.id] = user
                                        memberNames[user.id] = user.displayName
                                        memberUsernames[user.id] = user.username
                                        selectedMembers = NewChatRules.toggleMember(selectedMembers, user.id)
                                    } else if (loadContactRelations != null && !isContact(user)) sheetUser = user
                                    else openDirectChat(user)
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isCreatingGroup) Modifier.toggleable(
                                        value = isSelected, enabled = !isLoading && !groupDetails,
                                        role = Role.Checkbox, onValueChange = { activateUser() },
                                    ) else Modifier.clickable(
                                        enabled = !isLoading && !groupDetails, role = Role.Button, onClick = activateUser,
                                    ))
                                    .sizeIn(minHeight = 48.dp)
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NewChatAvatar(user)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        NewChatPresentationRules.identityText(user.displayName),
                                        color = colors.foreground,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = (-0.2).sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "@${NewChatPresentationRules.identityText(user.username)}",
                                        style = TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                                        color = colors.foregroundSecondary, fontSize = 12.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    val relationshipLabel = when {
                                        isContact(user) -> stringResource(R.string.social_newchat_contact_badge)
                                        NewChatRules.usernameKey(user.username) in requestedUsernames -> stringResource(R.string.social_newchat_requested_badge)
                                        else -> null
                                    }
                                    relationshipLabel?.let { Text(it, color = colors.primary, fontSize = 12.sp) }
                                    if (user.about.isNotEmpty()) Text(
                                        NewChatPresentationRules.identityText(user.about),
                                        color = colors.foregroundSecondary, fontSize = 12.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isCreatingGroup) {
                                    Checkbox(
                                        checked = isSelected,
                                        enabled = !isLoading && !groupDetails,
                                        onCheckedChange = null, // The full row owns the single toggle action.
                                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                                            checkedColor = colors.primary,
                                        ),
                                    )
                                } else {
                                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
                                        semanticLabel = null,
                                        color = colors.foregroundTertiary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (index < results.size - 1) {
                                Box(
                                    Modifier
                                        .padding(start = 64.dp)
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(colors.divider),
                                )
                            }
                        }
                }
            }
    }
    fun handleBack() {
        if (isSubmittingGroup || isOpeningDirect) return
        debounceJob?.cancel()
        searchJob?.cancel()
        epoch++
        queryLoading = false
        if (isCreatingGroup && groupDetails) {
            requestedGroupDetails = false
        } else if (isCreatingGroup) {
            if (selectedMembers.isNotEmpty() || groupName.isNotBlank()) showDiscardSelection = true
            else { isCreatingGroup = false; isBroadcast = false; requestedGroupDetails = false }
        } else onBack()
    }
    androidx.activity.compose.BackHandler(enabled = sheetUser == null && !showDiscardSelection) { handleBack() }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::handleBack) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = stringResource(R.string.social_newchat_back), color = colors.foreground)
                }
                Text(
                    if (isCreatingGroup) {
                        if (isBroadcast) stringResource(R.string.social_newchat_broadcast_title, selectedMembers.size) else stringResource(R.string.social_newchat_configured_title_count, groupUi.title, selectedMembers.size)
                    } else {
                        stringResource(R.string.social_newchat_title)
                    },
                    color = colors.foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.2.dp,
                        color = colors.primary,
                        modifier = Modifier.padding(horizontal = 16.dp).size(18.dp).semantics {
                            contentDescription = operationLabel
                            if (!queryLoading) liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
                if (isCreatingGroup && !isLoading) {
                    TextButton(
                        onClick = {
                            if (!groupDetails) {
                                debounceJob?.cancel(); epoch++; queryLoading = false
                                requestedGroupDetails = true; createError = null
                            } else finishGroupCreation()
                        },
                        enabled = selectionEligible && (!isBroadcast || createBroadcast != null) && (!groupDetails || (NewChatRules.canCreateGroup(groupName, isBroadcast, selectedMembers.size, isLoading) && NewChatRules.validFinalTitle(groupName, isBroadcast, selectedMembers.size, titleLimit))),
                    ) {
                        Text(
                            if (groupDetails) groupUi.createLabel else groupUi.nextLabel,
                            color = if (selectedMembers.isNotEmpty()) colors.primary else colors.foregroundTertiary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Lists are capped at 720dp and centered on larger windows.
        // Source layout intent only; actual responsive pixels still need verification.
        Box(Modifier.fillMaxSize().testTag("newchat-screen").padding(padding).imePadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            if (isCreatingGroup && isBroadcast && createBroadcast == null) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.social_newchat_broadcast_unavailable), color = colors.foregroundSecondary)
                    TextButton(enabled = !isLoading, onClick = { isBroadcast = false; createError = null }) {
                        Text(stringResource(R.string.social_newchat_switch_to_group))
                    }
                }
            }
            if (isCreatingGroup && !groupDetails) {
                LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("newchat-group-results"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    com.example.chat.ui.chats.newchat.NewChatPresentationRules.sections(groupUi.order).forEach { section ->
                        when (section) {
                            "search" -> item(key = "group-search", contentType = "header") {
                                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { searchSection() }
                            }
                            "selected" -> item(key = "group-selected", contentType = "selection") {
                                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { selectedSection() }
                            }
                            "contacts" -> contactsContent()
                        }
                    }
                }
            } else {
            Column(Modifier.fillMaxWidth().then(if (groupDetails) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp)) {
                selectedSection()
                if (isCreatingGroup && groupDetails) {
                    Text(stringResource(if (isBroadcast) R.string.social_newchat_broadcast_review_explanation else R.string.social_newchat_review_explanation), color = colors.foregroundSecondary)
                    NewChatInput(
                        value = groupName,
                        onValueChange = { groupName = com.example.chat.ui.chats.newchat.NewChatPresentationRules.clampTitle(it, titleLimit); createError = null },
                        hint = if (isBroadcast) stringResource(R.string.social_newchat_broadcast_hint) else groupUi.nameLabel,
                        label = stringResource(if (isBroadcast) R.string.social_newchat_broadcast_label else R.string.social_newchat_group_label),
                        fieldError = createError,
                        imeAction = ImeAction.Done,
                        leading = {
                            Icon(
                                if (isBroadcast) Icons.Rounded.Cast else Icons.Rounded.GroupWork,
                                contentDescription = null,
                                tint = if (isBroadcast) colors.info else colors.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                    Text(stringResource(R.string.social_newchat_name_length, NewChatRules.effectiveGroupTitle(groupName, isBroadcast, selectedMembers.size).length, titleLimit),
                        color = colors.foregroundSecondary, fontSize = 14.sp)
                    val finalTitle = NewChatRules.effectiveGroupTitle(groupName, isBroadcast, selectedMembers.size)
                    if (finalTitle.isNotBlank()) {
                        Text(stringResource(R.string.social_newchat_final_title_label), color = colors.foregroundSecondary,
                            modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                        Text(finalTitle, color = colors.foreground, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                }
                searchSection()

            }
            if (!groupDetails) LazyColumn(
                Modifier.fillMaxWidth().weight(1f).testTag("newchat-unified-list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
            if (!isCreatingGroup) {
                item(key = "create-group", contentType = "entry") { NewChatEntryCard(
                    title = stringResource(R.string.social_newchat_create_group_entry),
                    subtitle = stringResource(R.string.social_newchat_create_group_body),
                    icon = { tint ->
                        Icon(Icons.Rounded.GroupAdd, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    },
                    iconTint = colors.primary,
                    chevronTint = colors.foregroundTertiary,
                    onClick = {
                        isCreatingGroup = true
                        isBroadcast = false
                    },
                ) }
                if (createBroadcast != null) {
                item(key = "create-broadcast", contentType = "entry") { NewChatEntryCard(
                    title = stringResource(R.string.social_newchat_create_broadcast_entry),
                    subtitle = stringResource(R.string.social_newchat_create_broadcast_body),
                    icon = { tint ->
                        Icon(Icons.Rounded.Cast, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    },
                    iconTint = colors.info,
                    chevronTint = colors.foregroundTertiary,
                    onClick = {
                        isCreatingGroup = true
                        isBroadcast = true
                    },
                ) }
                } else item(key = "broadcast-unavailable", contentType = "info") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(stringResource(R.string.social_newchat_create_broadcast_entry), color = colors.foreground,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.social_newchat_broadcast_unavailable), color = colors.foregroundSecondary)
                        TextButton(onClick = { isCreatingGroup = true; isBroadcast = false; createError = null }) {
                            Text(stringResource(R.string.social_newchat_switch_to_group))
                        }
                    }
                }
                item(key = "entry-spacing", contentType = "spacing") { Spacer(Modifier.height(4.dp)) }
            }
            requestsContent()
            contactsContent()
            } // Unified entry/request/contact LazyColumn.
            }
        }
        }
    }

    if (showDiscardSelection) {
        val keepEditingFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { keepEditingFocus.requestFocus() }
        com.example.chat.ui.kit.ChatyConfirmationDialog(
            onDismissRequest = { showDiscardSelection = false },
            title = { Text(stringResource(R.string.social_newchat_discard_title)) },
            text = { Text(stringResource(R.string.social_newchat_discard_body)) },
            dismissButton = {
                TextButton(onClick = { showDiscardSelection = false }, modifier = Modifier.focusRequester(keepEditingFocus)) {
                    Text(stringResource(R.string.social_newchat_keep_editing))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardSelection = false
                    requestedGroupDetails = false
                    selectedMembers = emptySet()
                    groupName = ""
                    createError = null
                    isCreatingGroup = false
                    isBroadcast = false
                }) { Text(stringResource(R.string.social_newchat_discard_selection), color = colors.error) }
            },
        )
    }

    // Contact actions. Account deletion belongs to the verified Settings flow.
    val sheetTarget = sheetUser
    if (sheetTarget != null) {
        ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp,
            onDismissRequest = { sheetUser = null },
            containerColor = colors.surface,
            shape = com.example.chat.ui.kit.ChatyModalShape,
        ) {
            val sheetPane = stringResource(R.string.social_newchat_person_actions, sheetTarget.username)
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 24.dp).semantics { paneTitle = sheetPane }) {
                Text(
                    "${NewChatPresentationRules.identityText(sheetTarget.displayName)} (@${NewChatPresentationRules.identityText(sheetTarget.username)})",
                    color = colors.foreground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )
                if (sendContactRequest != null && !isContact(sheetTarget)) {
                    if (sheetTarget.id in requestedUserIds || NewChatRules.usernameKey(sheetTarget.username) in requestedUsernames) {
                        Text(stringResource(R.string.social_newchat_request_pending), color = colors.foregroundTertiary, modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    } else if (!outgoingReady || sheetTarget.id in uncertainRequestIds) {
                        Text(stringResource(if (sheetTarget.id in uncertainRequestIds) R.string.social_newchat_request_uncertain
                            else R.string.social_newchat_request_status_unavailable),
                            color = colors.foregroundSecondary, modifier = Modifier.padding(16.dp))
                        TextButton(enabled = !outgoingLoading,
                            onClick = { scope.launch { refreshOutgoingRequests(); refreshAcceptedContacts() } }) {
                            Text(stringResource(R.string.social_newchat_check_request_status))
                        }
                    } else if (sheetTarget.id in sendingRequestIds || NewChatRules.usernameKey(sheetTarget.username) in sendingRequestUsernames) {
                        Text(stringResource(R.string.social_newchat_request_sending), color = colors.foregroundTertiary,
                            modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    } else {
                        SheetAction(
                            label = stringResource(R.string.social_newchat_send_request),
                            tint = colors.primary,
                            onClick = {
                                val target = sheetTarget
                                val targetKey = NewChatRules.usernameKey(target.username)
                                if (outgoingReady && !isContact(target) &&
                                    NewChatRules.canReserveRequest(target.id, configOwner.userId, requestedUserIds, sendingRequestIds, uncertainRequestIds) &&
                                    targetKey !in sendingRequestUsernames &&
                                    targetKey !in requestedUsernames && configApi.authSnapshot() === configOwner) {
                                sendingRequestIds = sendingRequestIds + target.id
                                sendingRequestUsernames = sendingRequestUsernames + targetKey
                                sheetUser = null
                                scope.launch {
                                    var requestAttempted = false
                                    try {
                                        if (configApi.authSnapshot() !== configOwner) return@launch
                                        check(validateRecipients(setOf(target.id)))
                                        val fresh = NewChatRules.exactUsernameMatch(searchUsersRemote(target.username), target.username, configOwner.userId)
                                        check(fresh.singleOrNull()?.id == target.id)
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        if (configApi.authSnapshot() !== configOwner) return@launch
                                        requestAttempted = true
                                        outgoingRevision++
                                        // Reserve uncertainty before crossing the mutation boundary so cancellation is safe too.
                                        uncertainRequestIds = uncertainRequestIds + target.id
                                        sendContactRequest(target.username)
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        if (configApi.authSnapshot() !== configOwner) return@launch
                                        outgoingRevision++
                                        uncertainRequestIds = uncertainRequestIds - target.id
                                        requestedUserIds = requestedUserIds + target.id
                                        requestedUsernames = requestedUsernames + NewChatRules.usernameKey(target.username)
                                        snackbar.showSnackbar(configContext.getString(R.string.social_newchat_request_sent, target.displayName))
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        if (configApi.authSnapshot() === configOwner) {
                                            if (requestAttempted) uncertainRequestIds = uncertainRequestIds + target.id
                                            snackbar.showSnackbar(configContext.getString(if (requestAttempted)
                                                R.string.social_newchat_request_uncertain else R.string.social_newchat_request_send_failure))
                                        }
                                    } finally {
                                        sendingRequestIds = sendingRequestIds - target.id
                                        sendingRequestUsernames = sendingRequestUsernames - targetKey
                                    }
                                }
                                }
                            },
                        )
                    }
                }
                SheetAction(
                    label = stringResource(R.string.social_newchat_start_chat),
                    tint = colors.primary,
                    onClick = {
                        sheetUser = null
                        openDirectChat(sheetTarget)
                    },
                )
                // This host exposes only self-account deletion, not directory administration.
                // Do not route a selected contact into an account-deletion endpoint.
                Text(stringResource(R.string.social_newchat_account_deletion_settings),
                    color = colors.foregroundSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
            }
        }
    }


}

/** ChatyInput stand-in for this slice: filled rounded field with leading /
 *  trailing slots, matching the Dart widget's look on the default theme. */
@Composable
private fun NewChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    label: String,
    fieldError: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, color = colors.foregroundSecondary, fontSize = 14.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
                .background(colors.inputFill, RoundedCornerShape(14.dp))
                .then(if (focused) Modifier.border(2.dp, colors.primary, RoundedCornerShape(14.dp)) else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = imeAction, autoCorrect = false),
                keyboardActions = KeyboardActions(
                    onSearch = { onImeAction(); keyboard?.hide() },
                    onDone = { onImeAction(); keyboard?.hide() },
                ),
                textStyle = TextStyle(color = colors.foreground, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused }.semantics {
                    contentDescription = label
                    fieldError?.let { error(it) }
                },
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(hint, color = colors.foregroundTertiary, fontSize = 16.sp)
                    inner()
                },
            )
            if (trailing != null) trailing()
        }
    }
}

@Composable
private fun NewChatEntryCard(
    title: String,
    subtitle: String,
    icon: @Composable (Color) -> Unit,
    iconTint: Color,
    chevronTint: Color,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(iconTint.copy(alpha = 0.12f), CircleShape)
                .padding(8.dp),
        ) {
            icon(iconTint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 14.sp)
        }
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = chevronTint)
    }
}

/** AppAvatar + ChatyOnlineDot port (app_avatar.dart / chaty_kit.dart):
 *  initials circle painted with the parsed avatarColorHex, presence dot
 *  bottom-right (offset 1dp past the edge), diameter (size*0.28) clamped to
 *  10..14dp, 2dp ring in the surrounding surface color, presence-colored
 *  (online success, away warning, typing primary, offline tertiary). The
 *  Dart call site passes showOnlineBadge: true, so the dot always renders. */
@Composable
private fun NewChatAvatar(user: UserProfile) {
    val colors = LocalAppColors.current
    val presenceLabel = stringResource(when (user.presence) {
        com.example.chat.domain.models.PresenceState.online -> R.string.social_newchat_presence_online
        com.example.chat.domain.models.PresenceState.away -> R.string.social_newchat_presence_away
        com.example.chat.domain.models.PresenceState.typing -> R.string.social_newchat_presence_typing
        com.example.chat.domain.models.PresenceState.offline -> R.string.social_newchat_presence_offline
    })
    val presenceColor = when (user.presence) {
        com.example.chat.domain.models.PresenceState.online -> colors.success
        com.example.chat.domain.models.PresenceState.away -> colors.warning
        com.example.chat.domain.models.PresenceState.typing -> colors.primary
        com.example.chat.domain.models.PresenceState.offline -> colors.foregroundTertiary
    }
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    Color(com.example.chat.ui.chats.contact_info.parseAvatarColorArgb(user.avatarColorHex, LocalThemeConfig.current.accentColor)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                user.avatarInitials,
                color = colors.onPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 1.dp, y = 1.dp)
                .size(((42f * 0.28f).coerceIn(10f, 14f)).dp)
                .border(2.dp, colors.background, CircleShape)
                .background(presenceColor, CircleShape).semantics { contentDescription = presenceLabel },
        )
    }
}

/** ChatySkeletonTile stand-in: static placeholder row (Dart renders a
 *  shimmering avatar + two bars). */
@Composable
private fun SkeletonTile() {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(colors.borderSubtle, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Box(Modifier.width(140.dp).height(12.dp).background(colors.borderSubtle, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(220.dp).height(10.dp).background(colors.borderSubtle, RoundedCornerShape(5.dp)))
        }
    }
}

@Composable
private fun SheetAction(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        label,
        color = tint,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

// Superseded source lines retained inactive for review:
// import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
//  *  long-press Start Chat / Delete User sheet.
//  *  deleteUser). Skeleton/empty/no-results states mirror the Dart
//     deleteUser: suspend (String) -> Unit,
//     var isDeletingUser by remember { mutableStateOf(false) }
//     val isLoading = queryLoading || contactsLoading || isSubmittingGroup || isOpeningDirect || isDeletingUser
//         isDeletingUser -> R.string.social_newchat_deleting
//     var deleteCandidate by remember { mutableStateOf<UserProfile?>(null) }
//         contacts.firstOrNull { it.id == entry.userId } ?: UserProfile(
//         if (groupName.length > titleLimit) { createError = configContext.getString(R.string.social_newchat_name_too_long); return }
//         if (isOpeningDirect || isSubmittingGroup || isDeletingUser || queryLoading || contactsLoading || configApi.authSnapshot() !== configOwner) return
//             if (groupUi.selectedDisplay == "list") Column(
//                 Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
//                 selectedMembers.forEach { id -> androidx.compose.runtime.key(id) { selectedMemberControl(id) } }
//         if (isSubmittingGroup || isOpeningDirect || isDeletingUser) return
//     androidx.activity.compose.BackHandler(enabled = sheetUser == null && deleteCandidate == null) { handleBack() }
//                 modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
//                         enabled = selectedMembers.isNotEmpty() && (!groupDetails || NewChatRules.canCreateGroup(groupName, isBroadcast, selectedMembers.size, isLoading)),
//                     Text(stringResource(R.string.social_newchat_name_length, groupName.length, titleLimit),
//     // _onUserLongPress sheet: Start Chat / Delete User.
//                 SheetAction(
//                     label = "Delete User",
//                     tint = colors.error,
//                     onClick = {
//                         sheetUser = null
//                         deleteCandidate = sheetTarget
//                     },
//                 )
//     // ChatyConfirmDialog for Delete User.
//     val deleteTarget = deleteCandidate
//     if (deleteTarget != null) {
//         AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
//             onDismissRequest = { deleteCandidate = null },
//             containerColor = colors.surface,
//             title = { Text("Delete ${deleteTarget.displayName} (@${deleteTarget.username})?", color = colors.foreground, modifier = Modifier.semantics { heading() }) },
//             text = {
//                 Text(
//                     "This user will be permanently removed from your directory and workspace.",
//                     color = colors.foregroundSecondary,
//                 )
//             },
//             confirmButton = {
//                 TextButton(onClick = confirmDelete@ {
//                     if (isDeletingUser || isSubmittingGroup || isOpeningDirect || queryLoading || contactsLoading || deleteCandidate?.id != deleteTarget.id || configApi.authSnapshot() !== configOwner) return@confirmDelete
//                     isDeletingUser = true
//                     deleteCandidate = null
//                     scope.launch {
//                         try {
//                             if (configApi.authSnapshot() !== configOwner) return@launch
//                             deleteUser(deleteTarget.id)
//                             if (configApi.authSnapshot() !== configOwner) return@launch
//                             acceptedContacts = acceptedContacts.filterNot { it.id == deleteTarget.id }
//                             results = results.filterNot { it.id == deleteTarget.id }
//                             snackbar.showSnackbar("${deleteTarget.displayName} deleted")
//                         } catch (error: Exception) {
//                             if (error is kotlinx.coroutines.CancellationException) throw error
//                             if (configApi.authSnapshot() === configOwner) snackbar.showSnackbar("Unable to delete this user. Try again.")
//                         } finally {
//                             isDeletingUser = false
//                         }
//                     }
//                 }) {
//                     Text("Delete", color = colors.error, fontWeight = FontWeight.Bold)
//                 }
//             },
//             dismissButton = {
//                 TextButton(onClick = { deleteCandidate = null }) {
//                     Text("Cancel", color = colors.foregroundSecondary)
//                 }
//             },
//         )
//     }
//                 modifier = Modifier.weight(1f).semantics {

// Superseded source retained inactive:
// import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
//  *  long-press Start Chat / Delete User sheet.
//  *  deleteUser). Skeleton/empty/no-results states mirror the Dart
//     deleteUser: suspend (String) -> Unit,
//     val draftAccountKey = configApi.currentAccountHash().orEmpty()
//     var searchText by rememberSaveable(configOwner, key = "newchat-query:$draftAccountKey") { mutableStateOf("") }
//     var groupDetails by remember { mutableStateOf(false) }
//     var groupName by rememberSaveable(configOwner, key = "newchat-title:$draftAccountKey") { mutableStateOf("") }
//     var selectedMembers by remember { mutableStateOf(setOf<String>()) }
//     var isCreatingGroup by rememberSaveable(configOwner, key = "newchat-group:$draftAccountKey") { mutableStateOf(startInGroupMode || startInBroadcastMode) }
//     var isBroadcast by rememberSaveable(configOwner, key = "newchat-broadcast:$draftAccountKey") { mutableStateOf(startInBroadcastMode) }
//     var isDeletingUser by remember { mutableStateOf(false) }
//     val isLoading = queryLoading || contactsLoading || isSubmittingGroup || isOpeningDirect || isDeletingUser
//         isDeletingUser -> R.string.social_newchat_deleting
//     var deleteCandidate by remember { mutableStateOf<UserProfile?>(null) }
//         contacts.firstOrNull { it.id == entry.userId } ?: UserProfile(
//                 acceptedContacts = entries.map(::contactProfile).distinctBy { it.id }
//                 contactRequests = inbox
//     LaunchedEffect(Unit) { refreshAcceptedContacts() }
//         if (!groupDetails || isSubmittingGroup || configApi.authSnapshot() !== configOwner) return
//         if (groupName.length > titleLimit) { createError = configContext.getString(R.string.social_newchat_name_too_long); return }
//         if (isOpeningDirect || isSubmittingGroup || isDeletingUser || queryLoading || contactsLoading || configApi.authSnapshot() !== configOwner) return
//     val selectionProfiles = remember(contacts, acceptedContacts, results) {
//         (contacts + acceptedContacts + results).associateBy { it.id }
//     }
//         TextButton(enabled = !isLoading, onClick = { selectedMembers = selectedMembers - id },
//             if (groupUi.selectedDisplay == "list") Column(
//                 Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
//                 selectedMembers.forEach { id -> androidx.compose.runtime.key(id) { selectedMemberControl(id) } }
//         if (isSubmittingGroup || isOpeningDirect || isDeletingUser) return
//             groupDetails = false
//             isCreatingGroup = false
//             isBroadcast = false
//             selectedMembers = emptySet()
//     androidx.activity.compose.BackHandler(enabled = sheetUser == null && deleteCandidate == null) { handleBack() }
//                 modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
//                                 groupDetails = true; createError = null
//                         enabled = selectedMembers.isNotEmpty() && (!groupDetails || NewChatRules.canCreateGroup(groupName, isBroadcast, selectedMembers.size, isLoading)),
//                     Text(stringResource(R.string.social_newchat_name_length, groupName.length, titleLimit),
//                     if (NewChatRules.usernameKey(sheetTarget.username) in requestedUsernames) {
//                                 if (targetKey !in sendingRequestUsernames && targetKey !in requestedUsernames && configApi.authSnapshot() === configOwner) {
//                                         if (configApi.authSnapshot() === configOwner) snackbar.showSnackbar(configContext.getString(R.string.social_newchat_request_send_failure))
//                 SheetAction(
//                     label = "Delete User",
//                     tint = colors.error,
//                     onClick = {
//                         sheetUser = null
//                         deleteCandidate = sheetTarget
//                     },
//                 )
//     val deleteTarget = deleteCandidate
//     if (deleteTarget != null) {
//         AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
//             onDismissRequest = { deleteCandidate = null },
//             containerColor = colors.surface,
//             title = { Text("Delete ${deleteTarget.displayName} (@${deleteTarget.username})?", color = colors.foreground, modifier = Modifier.semantics { heading() }) },
//             text = {
//                 Text(
//                     "This user will be permanently removed from your directory and workspace.",
//                     color = colors.foregroundSecondary,
//                 )
//             },
//             confirmButton = {
//                 TextButton(onClick = confirmDelete@ {
//                     if (isDeletingUser || isSubmittingGroup || isOpeningDirect || queryLoading || contactsLoading || deleteCandidate?.id != deleteTarget.id || configApi.authSnapshot() !== configOwner) return@confirmDelete
//                     isDeletingUser = true
//                     deleteCandidate = null
//                     scope.launch {
//                         try {
//                             if (configApi.authSnapshot() !== configOwner) return@launch
//                             deleteUser(deleteTarget.id)
//                             if (configApi.authSnapshot() !== configOwner) return@launch
//                             acceptedContacts = acceptedContacts.filterNot { it.id == deleteTarget.id }
//                             results = results.filterNot { it.id == deleteTarget.id }
//                             snackbar.showSnackbar("${deleteTarget.displayName} deleted")
//                         } catch (error: Exception) {
//                             if (error is kotlinx.coroutines.CancellationException) throw error
//                             if (configApi.authSnapshot() === configOwner) snackbar.showSnackbar("Unable to delete this user. Try again.")
//                         } finally {
//                             isDeletingUser = false
//                         }
//                     }
//                 }) {
//                     Text("Delete", color = colors.error, fontWeight = FontWeight.Bold)
//                 }
//             },
//             dismissButton = {
//                 TextButton(onClick = { deleteCandidate = null }) {
//                     Text("Cancel", color = colors.foregroundSecondary)
//                 }
//             },
//         )
//     }
//                 modifier = Modifier.weight(1f).semantics {

/* Pre-review source retained unchanged.
package com.example.chat.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.example.chat.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.GroupWork
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.newchat.NewChatRules
import com.example.chat.ui.chats.newchat.NewChatPresentationRules
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

/** Port of new_chat_screen.dart (api branch anchor 3bee780): contact/user
 *  directory search with the 250ms debounce + epoch gate, direct-chat open,
 *  group/broadcast member selection with the exact Dart title rules, and the
 *  long-press contact actions sheet.
 *
 *  All backend access arrives as suspend lambdas so this screen never
 *  touches shared state directly; AppRoot wires them to ApiBackendService
 *  (searchUsersRemote / createOrGetDirectConversation / createGroupRoom /
 *  contacts APIs). Account deletion is intentionally not exposed here.
 *  Skeleton/empty/no-results states mirror the Dart
 *  ChatySkeletonTile / ChatyEmptyState / ChatyNoResultsState widgets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    contacts: List<UserProfile>,
    startInGroupMode: Boolean = false,
    startInBroadcastMode: Boolean = false,
    searchUsersRemote: suspend (String) -> List<UserProfile>,
    openDirectConversation: suspend (UserProfile) -> Conversation,
    createGroup: suspend (String, List<String>) -> Conversation,
    @Suppress("UNUSED_PARAMETER") deleteUser: suspend (String) -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onBack: () -> Unit,
    // Contacts plane (research 5.3): username search -> request -> inbox.
    // Null = pre-plane server: the screen keeps its legacy tap-to-chat flow.
    loadContactRelations: (suspend () -> Pair<List<com.example.chat.data.api.ApiContactsClient.ContactEntry>, List<com.example.chat.data.api.ApiContactsClient.ContactRequestItem>>)? = null,
    sendContactRequest: (suspend (String) -> Unit)? = null,
    respondContactRequest: (suspend (String, Boolean) -> Unit)? = null,
    loadOutgoingRequests: (suspend () -> List<com.example.chat.data.api.ApiContactsClient.ContactRequestItem>)? = null,
    /** Fail closed unless the host can perform a fresh account-bound eligibility check. */
    validateRecipients: suspend (Set<String>) -> Boolean = { false },
    /** Separate delivery semantics: never emulate this by adding Broadcast to a group title. */
    createBroadcast: (suspend (String, List<String>) -> Conversation)? = null,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val configContext = androidx.compose.ui.platform.LocalContext.current
    val configApi = com.example.chat.ServiceLocator.apiBackend
    val configOwner = remember { configApi.authSnapshot() }
    // Never let an old contact selection become an operation on a newly selected account.
    val currentOnBack by androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.compose.runtime.DisposableEffect(configApi, configOwner) {
        val listener: (com.example.chat.data.api.ApiAccountScope) -> Unit = { next ->
            if (next !== configOwner) scope.launch { currentOnBack() }
        }
        configApi.addAuthScopeListener(listener)
        onDispose { configApi.removeAuthScopeListener(listener) }
    }
    val choiceStore = remember(configOwner) { configApi.currentAccountHash()?.let { hash ->
        runCatching { com.example.chat.core.config.GroupUiChoicesStore(configContext, hash) }.getOrNull()
    } }
    var groupUi by remember(configOwner) { mutableStateOf(com.example.chat.core.config.GroupUiConfigPolicy.defaults) }
    LaunchedEffect(configOwner) {
        val defaults = configApi.groupUiDefaults(configOwner)
        val choices = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { choiceStore?.read() }
        if (configApi.authSnapshot() === configOwner) groupUi = choices?.applyTo(defaults) ?: defaults
    }

    val titleLimit = groupUi.nameMaxLength.coerceAtLeast(1)
    val draftAccountKey = remember(configOwner, startInGroupMode, startInBroadcastMode) {
        "${configApi.currentAccountHash().orEmpty()}:$startInGroupMode:$startInBroadcastMode"
    }
    var searchText by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode, key = "newchat-query:$draftAccountKey") { mutableStateOf("") }
    var requestedGroupDetails by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode,
        key = "newchat-review:$draftAccountKey") { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var showDiscardSelection by remember { mutableStateOf(false) }
    val memberNames = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    val memberUsernames = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var groupName by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode, key = "newchat-title:$draftAccountKey") { mutableStateOf("") }
    val selectedIdSaver = remember {
        androidx.compose.runtime.saveable.listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
    }
    // Persist immutable IDs only, never directory/profile payloads or callback/session objects.
    var selectedMembers by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode,
        key = "newchat-members:$draftAccountKey", stateSaver = selectedIdSaver) { mutableStateOf(setOf<String>()) }
    val selectedProfiles = remember(configOwner) { androidx.compose.runtime.mutableStateMapOf<String, UserProfile>() }
    LaunchedEffect(selectedMembers) {
        // Retain identity labels only while they support an intentional selection.
        selectedProfiles.keys.toList().filterNot { it in selectedMembers }.forEach { selectedProfiles.remove(it) }
        memberNames.keys.toList().filterNot { it in selectedMembers }.forEach { memberNames.remove(it) }
        memberUsernames.keys.toList().filterNot { it in selectedMembers }.forEach { memberUsernames.remove(it) }
    }
    var results by remember { mutableStateOf(emptyList<UserProfile>()) }
    var acceptedContacts by remember { mutableStateOf(emptyList<UserProfile>()) }
    var contactLoadError by remember { mutableStateOf<String?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var isCreatingGroup by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode, key = "newchat-group:$draftAccountKey") { mutableStateOf(startInGroupMode || startInBroadcastMode) }
    var isBroadcast by rememberSaveable(configOwner, startInGroupMode, startInBroadcastMode, key = "newchat-broadcast:$draftAccountKey") { mutableStateOf(startInBroadcastMode) }
    val selectedProfileSnapshot = selectedProfiles.toMap()
    val selectionProfiles = remember(contacts, acceptedContacts, results, selectedProfileSnapshot) {
        (selectedProfileSnapshot.values + contacts + acceptedContacts + results).associateBy { it.id }
    }
    val selectionEligible = NewChatRules.validSelection(selectedMembers, configOwner.userId, selectionProfiles.keys)
    // A restored review never renders as actionable while any saved identity is unresolved.
    val groupDetails = requestedGroupDetails && isCreatingGroup && selectionEligible
    var queryLoading by remember { mutableStateOf(false) }
    var contactsLoading by remember { mutableStateOf(false) }
    var isSubmittingGroup by remember { mutableStateOf(false) }
    var isOpeningDirect by remember(configOwner) { mutableStateOf(false) }
    val isLoading = queryLoading || contactsLoading || isSubmittingGroup || isOpeningDirect
    val operationLabel = stringResource(when {
        isSubmittingGroup -> if (isBroadcast) R.string.social_newchat_creating_broadcast else R.string.social_newchat_creating
        isOpeningDirect -> R.string.social_newchat_opening
        queryLoading -> R.string.social_newchat_checking
        else -> R.string.social_newchat_loading_contacts
    })
    var epoch by remember { mutableIntStateOf(0) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    androidx.compose.runtime.DisposableEffect(configOwner) {
        onDispose { debounceJob?.cancel(); searchJob?.cancel() }
    }
    var sheetUser by remember { mutableStateOf<UserProfile?>(null) }

    fun errorText(error: Throwable): String =
        configContext.getString(when (error) {
            is java.io.IOException -> R.string.social_newchat_error_connection
            is SecurityException -> R.string.social_newchat_error_permission
            else -> R.string.social_newchat_error_request
        })

    // Contacts plane state (research 5.3). Empty/absent on pre-plane servers.
    var contactIds by remember { mutableStateOf(setOf<String>()) }
    var contactUsernames by remember { mutableStateOf(setOf<String>()) }
    var contactRequests by remember { mutableStateOf(listOf<com.example.chat.data.api.ApiContactsClient.ContactRequestItem>()) }
    var requestedUsernames by remember { mutableStateOf(setOf<String>()) }
    var requestedUserIds by remember(configOwner) { mutableStateOf(setOf<String>()) }
    var uncertainRequestIds by rememberSaveable(configOwner, key = "newchat-uncertain:$draftAccountKey",
        stateSaver = selectedIdSaver) { mutableStateOf(setOf<String>()) }
    var outgoingReady by remember(configOwner) { mutableStateOf(false) }
    var outgoingLoading by remember(configOwner) { mutableStateOf(false) }
    var sendingRequestUsernames by remember(configOwner) { mutableStateOf(setOf<String>()) }
    var sendingRequestIds by remember(configOwner) { mutableStateOf(setOf<String>()) }
    var outgoingRevision by remember(configOwner) { mutableIntStateOf(0) }
    var contactsRevision by remember(configOwner) { mutableIntStateOf(0) }
    var respondingRequestIds by remember { mutableStateOf(setOf<String>()) }

    fun isContact(user: UserProfile): Boolean =
        user.id in contactIds || NewChatRules.usernameKey(user.username) in contactUsernames

    val contactsById = remember(contacts) { contacts.associateBy { it.id } }

    fun contactProfile(entry: com.example.chat.data.api.ApiContactsClient.ContactEntry): UserProfile =
        contactsById[entry.userId] ?: UserProfile(
            id = entry.userId,
            displayName = entry.displayName.ifBlank { entry.username },
            username = entry.username,
            avatarInitials = entry.displayName.ifBlank { entry.username }.take(2).uppercase(),
            avatarColorHex = "0xFF6366F1",
            about = entry.about,
            avatarUrl = entry.avatarUrl,
            lastSeenAt = java.time.Instant.EPOCH,
        )

    suspend fun refreshOutgoingRequests() {
        val loader = loadOutgoingRequests ?: return
        if (outgoingLoading || configApi.authSnapshot() !== configOwner) return
        outgoingLoading = true
        val readRevision = outgoingRevision
        try {
            val outgoing = loader()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (configApi.authSnapshot() !== configOwner) return
            if (readRevision != outgoingRevision) return // Never overwrite a newer local send with an older snapshot.
            requestedUserIds = outgoing.map { it.from.userId }.filter { it.isNotBlank() }.toSet()
            requestedUsernames = outgoing.map { NewChatRules.usernameKey(it.from.username) }.toSet()
            uncertainRequestIds = uncertainRequestIds - requestedUserIds - contactIds
            outgoingReady = true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (configApi.authSnapshot() === configOwner && readRevision == outgoingRevision) outgoingReady = false
        } finally { outgoingLoading = false }
    }

    // Empty search is the accepted-contacts list, never the workspace directory.
    suspend fun refreshAcceptedContacts() {
        if (contactsLoading || configApi.authSnapshot() !== configOwner) return
        contactsLoading = true
        val readRevision = contactsRevision
        contactLoadError = null
        try {
            if (loadContactRelations != null) {
                val (entries, inbox) = loadContactRelations()
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return
                if (readRevision != contactsRevision) return // A completed accept/decline is newer than this fetch.
                contactIds = entries.map { it.userId }.toSet()
                contactUsernames = entries.map { NewChatRules.usernameKey(it.username) }.toSet()
                acceptedContacts = entries.filter { it.userId.isNotBlank() && it.userId != configOwner.userId }
                    .map(::contactProfile).distinctBy { it.id }
                contactRequests = inbox.filter { it.requestId.isNotBlank() && it.from.userId.isNotBlank() }
                    .distinctBy { it.requestId }
            } else {
                contactLoadError = configContext.getString(R.string.social_newchat_contacts_server_unavailable)
            }
            if (searchText.isBlank()) results = acceptedContacts
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (error: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (configApi.authSnapshot() === configOwner && readRevision == contactsRevision) {
                contactLoadError = configContext.getString(R.string.social_newchat_contacts_load_failure, errorText(error))
            }
        } finally {
            contactsLoading = false
        }
    }

    fun respondToRequest(req: com.example.chat.data.api.ApiContactsClient.ContactRequestItem, accept: Boolean) {
        if (req.requestId in respondingRequestIds) return
        val responder = respondContactRequest
        respondingRequestIds = respondingRequestIds + req.requestId
        scope.launch {
            try {
                checkNotNull(responder) { configContext.getString(R.string.social_newchat_requests_unavailable) }
                if (configApi.authSnapshot() !== configOwner) return@launch
                responder.invoke(req.requestId, accept)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                contactsRevision++
                contactRequests = contactRequests.filterNot { it.requestId == req.requestId }
                if (accept) {
                    contactIds = contactIds + req.from.userId
                    contactUsernames = contactUsernames + NewChatRules.usernameKey(req.from.username)
                    acceptedContacts = (acceptedContacts + contactProfile(req.from)).distinctBy { it.id }
                    if (searchText.isBlank()) results = acceptedContacts
                }
                snackbar.showSnackbar(if (accept) configContext.getString(R.string.social_newchat_contact_added, req.from.displayName) else configContext.getString(R.string.social_newchat_request_declined))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() === configOwner) snackbar.showSnackbar(configContext.getString(R.string.social_newchat_request_update_failure))
            } finally { respondingRequestIds = respondingRequestIds - req.requestId }
        }
    }

    fun runSearch(query: String, searchEpoch: Int) {
        searchJob = scope.launch {
            try {
                if (configApi.authSnapshot() !== configOwner || !NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                val users = kotlinx.coroutines.withTimeout(10_000L) { searchUsersRemote(query) }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                if (!NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                // The production host already does this; repeat the fail-closed contract at the UI seam.
                results = NewChatRules.exactUsernameMatch(users, query, configOwner.userId)
                queryLoading = false
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner || !NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                queryLoading = false
                results = emptyList()
                searchError = configContext.getString(R.string.social_newchat_lookup_timeout)
                snackbar.showSnackbar(searchError!!)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (error: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner || !NewChatRules.isCurrentEpoch(searchEpoch, epoch)) return@launch
                queryLoading = false
                results = emptyList()
                searchError = errorText(error)
                snackbar.showSnackbar(searchError!!)
            }
        }
    }

    // _onSearchChanged: cancel debounce; empty query restores contacts;
    // otherwise epoch++ + 250ms debounce before the remote search.
    fun onSearchChanged(value: String) {
        searchText = value
        searchError = null
        debounceJob?.cancel()
        searchJob?.cancel()
        val query = value.trim()
        val nextEpoch = ++epoch // Also invalidate an in-flight search when cleared.
        if (query.isEmpty()) {
            results = acceptedContacts
            queryLoading = false
            return
        }
        results = emptyList() // Never leave a stale result actionable while a new username is checked.
        queryLoading = true
        debounceJob = scope.launch {
            delay(NewChatRules.SEARCH_DEBOUNCE_MS)
            runSearch(query, nextEpoch)
        }
    }

    LaunchedEffect(configOwner) {
        refreshAcceptedContacts()
        refreshOutgoingRequests()
        if (searchText.isNotBlank() && configApi.authSnapshot() === configOwner) onSearchChanged(searchText)
    }

    fun finishGroupCreation() {
        val title = NewChatRules.effectiveGroupTitle(groupName, isBroadcast, selectedMembers.size)
        if (!NewChatRules.canCreateGroup(groupName, isBroadcast, selectedMembers.size, isLoading)) return
        if (!groupDetails || !selectionEligible || isSubmittingGroup || configApi.authSnapshot() !== configOwner) return
        if (!NewChatRules.validFinalTitle(groupName, isBroadcast, selectedMembers.size, titleLimit)) { createError = configContext.getString(R.string.social_newchat_name_too_long); return }
        val creator = NewChatRules.creatorFor(isBroadcast, createGroup, createBroadcast)
        if (creator == null) { createError = configContext.getString(R.string.social_newchat_broadcast_unavailable); return }
        val capturedMembers = selectedMembers.toList()
        isSubmittingGroup = true // Independent of search/contact loaders, which may finish concurrently.
        createError = null
        scope.launch {
            try {
                check(validateRecipients(capturedMembers.toSet()))
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                val conversation = creator(title, capturedMembers)
                check(NewChatRules.validConversation(conversation, configOwner.userId, capturedMembers.toSet(), group = true))
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() === configOwner) onOpenConversation(conversation)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (error: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                createError = errorText(error)
                snackbar.showSnackbar(errorText(error))
            } finally { isSubmittingGroup = false }
        }
    }

    fun openDirectChat(user: UserProfile) {
        if (isOpeningDirect || isSubmittingGroup || queryLoading || contactsLoading || configApi.authSnapshot() !== configOwner) return
        isOpeningDirect = true
        scope.launch {
            try {
                check(user.id.isNotBlank() && user.id != configOwner.userId && validateRecipients(setOf(user.id)))
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                val conversation = openDirectConversation(user)
                check(NewChatRules.validConversation(conversation, configOwner.userId, setOf(user.id), group = false))
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() === configOwner) onOpenConversation(conversation)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (configApi.authSnapshot() !== configOwner) return@launch
                snackbar.showSnackbar(errorText(error))
            } finally { isOpeningDirect = false }
        }
    }

    val selectedMemberControl: @Composable (String) -> Unit = { id ->
        val current = selectionProfiles[id]
        val name = current?.displayName?.takeIf { it.isNotBlank() } ?: memberNames[id]
            ?: stringResource(R.string.social_newchat_unavailable_member)
        val username = current?.username?.takeIf { it.isNotBlank() } ?: memberUsernames[id]
        val identity = stringResource(R.string.social_newchat_selected_identity, NewChatPresentationRules.identityText(name),
            NewChatPresentationRules.identityText(username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: id))
        val display = if (current == null) stringResource(R.string.social_newchat_unavailable_identity, identity) else identity
        val removeLabel = stringResource(R.string.social_newchat_remove_member, display)
        TextButton(enabled = !isLoading, onClick = { requestedGroupDetails = false; selectedMembers = selectedMembers - id },
            modifier = Modifier.widthIn(max = 360.dp)) {
            Text(removeLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val selectedSection: @Composable () -> Unit = {
        if (isCreatingGroup) {
            Text(if (groupDetails) stringResource(if (isBroadcast) R.string.social_newchat_broadcast_review_step else R.string.social_newchat_review_step) else stringResource(R.string.social_newchat_selection_step),
                color = colors.foregroundSecondary, modifier = Modifier.semantics { heading() })
            if (groupUi.showSelectedCount) Text(
                pluralStringResource(R.plurals.social_newchat_selected_count, selectedMembers.size, selectedMembers.size),
                color = colors.foregroundSecondary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (groupUi.selectedDisplay == "list") LazyColumn(
                Modifier.heightIn(max = 160.dp),
            ) {
                items(selectedMembers.toList(), key = { it }) { id -> selectedMemberControl(id) }
            } else androidx.compose.foundation.lazy.LazyRow {
                items(selectedMembers.toList(), key = { it }) { id -> selectedMemberControl(id) }
            }
            if (selectedMembers.isNotEmpty() && !selectionEligible) Text(
                stringResource(R.string.social_newchat_restore_unavailable), color = colors.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            createError?.let { Text(it, color = colors.error) }
        }
    }
    val searchSection: @Composable () -> Unit = {
                if (!groupDetails) NewChatInput(
                    value = searchText,
                    onValueChange = { onSearchChanged(it) },
                    hint = if (isCreatingGroup) groupUi.searchLabel else stringResource(R.string.social_newchat_username_hint),
                    label = stringResource(R.string.social_newchat_username_label),
                    fieldError = searchError,
                    imeAction = ImeAction.Search,
                    onImeAction = { onSearchChanged(searchText) },
                    leading = {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search,
                            semanticLabel = null,
                            color = colors.foregroundTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailing = if (searchText.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchChanged("") }, modifier = Modifier.size(48.dp)) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close,
                                    semanticLabel = stringResource(R.string.social_newchat_clear_search),
                                    color = colors.foregroundSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
                if (queryLoading && !groupDetails) Text(
                    stringResource(R.string.social_newchat_checking), color = colors.foregroundSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
    }
    val requestsContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            // Incoming contact requests inbox (research 5.3: Signal's
            // message-request pattern applied to contacts).
            if (!isCreatingGroup && contactRequests.isNotEmpty()) {
                item(key = "request-header", contentType = "header") {
                    Text(
                        stringResource(R.string.social_newchat_requests_title),
                        color = colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp).semantics { heading() },
                    )
                }
                    items(contactRequests.distinctBy { it.requestId }, key = { "request:${it.requestId}" }, contentType = { "contact-request" }) { req ->
                        val pending = req.requestId in respondingRequestIds
                        val acceptLabel = stringResource(R.string.social_newchat_accept_request, req.from.username)
                        val declineLabel = stringResource(R.string.social_newchat_decline_request, req.from.username)
                        val pendingLabel = stringResource(R.string.social_newchat_request_updating)
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    NewChatPresentationRules.identityText(req.from.displayName),
                                    color = colors.foreground,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("@${NewChatPresentationRules.identityText(req.from.username)}", color = colors.foregroundSecondary, fontSize = 12.sp,
                                    style = TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                            TextButton(
                                enabled = !pending, onClick = { respondToRequest(req, true) },
                                modifier = Modifier.semantics {
                                    contentDescription = acceptLabel
                                    if (pending) stateDescription = pendingLabel
                                },
                            ) {
                                Text(stringResource(R.string.social_newchat_accept), color = colors.primary, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                enabled = !pending, onClick = { respondToRequest(req, false) },
                                modifier = Modifier.semantics {
                                    contentDescription = declineLabel
                                    if (pending) stateDescription = pendingLabel
                                },
                            ) {
                                Text(stringResource(R.string.social_newchat_decline), color = colors.error)
                            }
                            }
                        }
                    }
                item(key = "request-spacing", contentType = "spacing") { Spacer(Modifier.height(4.dp)) }
            }
    }
    val contactsContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            when {
                groupDetails -> Unit
                isLoading && results.isEmpty() -> {
                    items(8, key = { "loading:$it" }, contentType = { "newchat-loading" }) { SkeletonTile() }
                }
                results.isEmpty() && searchText.trim().isNotEmpty() -> item(key = "username-feedback", contentType = "feedback") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text(
                            searchError?.let { stringResource(R.string.social_newchat_lookup_failed_title) } ?: stringResource(R.string.social_newchat_lookup_empty_title),
                            color = colors.foreground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.social_newchat_checked_username,
                                NewChatPresentationRules.identityText(NewChatRules.normalizedUsername(searchText))),
                            style = TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                            color = colors.foregroundSecondary, fontSize = 14.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            searchError ?: stringResource(R.string.social_newchat_lookup_empty_body),
                            color = colors.foregroundSecondary,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (searchError != null) TextButton(onClick = { onSearchChanged(searchText) }) {
                            Text(stringResource(R.string.social_newchat_lookup_retry), color = colors.primary)
                        }
                        TextButton(onClick = { onSearchChanged("") }) {
                            Text(stringResource(R.string.social_newchat_clear_search), color = colors.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                results.isEmpty() -> item(key = "contacts-feedback", contentType = "feedback") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PersonSearch,
                            contentDescription = null,
                            tint = colors.foregroundTertiary,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (contactLoadError == null) stringResource(R.string.social_newchat_contacts_empty_title) else stringResource(R.string.social_newchat_contacts_unavailable_title),
                            color = colors.foreground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            contactLoadError ?: stringResource(R.string.social_newchat_contacts_empty_body),
                            color = colors.foregroundSecondary,
                            fontSize = 13.sp,
                        )
                        if (contactLoadError != null && loadContactRelations != null) {
                            TextButton(onClick = { scope.launch { refreshAcceptedContacts() } }) {
                                Text(stringResource(R.string.social_newchat_retry), color = colors.accent)
                            }
                        }
                    }
                }
                else -> {
                    item(key = "contact-results-header", contentType = "header") {
                        Text(pluralStringResource(R.plurals.social_newchat_result_count, results.size, results.size),
                            color = colors.foregroundSecondary, modifier = Modifier.padding(vertical = 4.dp).semantics { heading() })
                    }
                        itemsIndexed(results, key = { _, u -> "person:${u.id}" }, contentType = { _, _ -> "newchat-person" }) { index, user ->
                            val isSelected = selectedMembers.contains(user.id)
                            val activateUser: () -> Unit = {
                                if (!isLoading && !groupDetails) {
                                    if (isCreatingGroup) {
                                        requestedGroupDetails = false
                                        selectedProfiles[user.id] = user
                                        memberNames[user.id] = user.displayName
                                        memberUsernames[user.id] = user.username
                                        selectedMembers = NewChatRules.toggleMember(selectedMembers, user.id)
                                    } else if (loadContactRelations != null && !isContact(user)) sheetUser = user
                                    else openDirectChat(user)
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isCreatingGroup) Modifier.toggleable(
                                        value = isSelected, enabled = !isLoading && !groupDetails,
                                        role = Role.Checkbox, onValueChange = { activateUser() },
                                    ) else Modifier.clickable(
                                        enabled = !isLoading && !groupDetails, role = Role.Button, onClick = activateUser,
                                    ))
                                    .sizeIn(minHeight = 48.dp)
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NewChatAvatar(user)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        NewChatPresentationRules.identityText(user.displayName),
                                        color = colors.foreground,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = (-0.2).sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "@${NewChatPresentationRules.identityText(user.username)}",
                                        style = TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                                        color = colors.foregroundSecondary, fontSize = 12.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    val relationshipLabel = when {
                                        isContact(user) -> stringResource(R.string.social_newchat_contact_badge)
                                        NewChatRules.usernameKey(user.username) in requestedUsernames -> stringResource(R.string.social_newchat_requested_badge)
                                        else -> null
                                    }
                                    relationshipLabel?.let { Text(it, color = colors.primary, fontSize = 12.sp) }
                                    if (user.about.isNotEmpty()) Text(
                                        NewChatPresentationRules.identityText(user.about),
                                        color = colors.foregroundSecondary, fontSize = 12.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isCreatingGroup) {
                                    Checkbox(
                                        checked = isSelected,
                                        enabled = !isLoading && !groupDetails,
                                        onCheckedChange = null, // The full row owns the single toggle action.
                                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                                            checkedColor = colors.primary,
                                        ),
                                    )
                                } else {
                                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
                                        semanticLabel = null,
                                        color = colors.foregroundTertiary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (index < results.size - 1) {
                                Box(
                                    Modifier
                                        .padding(start = 64.dp)
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(colors.divider),
                                )
                            }
                        }
                }
            }
    }
    fun handleBack() {
        if (isSubmittingGroup || isOpeningDirect) return
        debounceJob?.cancel()
        searchJob?.cancel()
        epoch++
        queryLoading = false
        if (isCreatingGroup && groupDetails) {
            requestedGroupDetails = false
        } else if (isCreatingGroup) {
            if (selectedMembers.isNotEmpty() || groupName.isNotBlank()) showDiscardSelection = true
            else { isCreatingGroup = false; isBroadcast = false; requestedGroupDetails = false }
        } else onBack()
    }
    androidx.activity.compose.BackHandler(enabled = sheetUser == null && !showDiscardSelection) { handleBack() }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::handleBack) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = stringResource(R.string.social_newchat_back), color = colors.foreground)
                }
                Text(
                    if (isCreatingGroup) {
                        if (isBroadcast) stringResource(R.string.social_newchat_broadcast_title, selectedMembers.size) else stringResource(R.string.social_newchat_configured_title_count, groupUi.title, selectedMembers.size)
                    } else {
                        stringResource(R.string.social_newchat_title)
                    },
                    color = colors.foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.2.dp,
                        color = colors.primary,
                        modifier = Modifier.padding(horizontal = 16.dp).size(18.dp).semantics {
                            contentDescription = operationLabel
                            if (!queryLoading) liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
                if (isCreatingGroup && !isLoading) {
                    TextButton(
                        onClick = {
                            if (!groupDetails) {
                                debounceJob?.cancel(); epoch++; queryLoading = false
                                requestedGroupDetails = true; createError = null
                            } else finishGroupCreation()
                        },
                        enabled = selectionEligible && (!isBroadcast || createBroadcast != null) && (!groupDetails || (NewChatRules.canCreateGroup(groupName, isBroadcast, selectedMembers.size, isLoading) && NewChatRules.validFinalTitle(groupName, isBroadcast, selectedMembers.size, titleLimit))),
                    ) {
                        Text(
                            if (groupDetails) groupUi.createLabel else groupUi.nextLabel,
                            color = if (selectedMembers.isNotEmpty()) colors.primary else colors.foregroundTertiary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Lists are capped at 720dp and centered on larger windows.
        // Source layout intent only; actual responsive pixels still need verification.
        Box(Modifier.fillMaxSize().testTag("newchat-screen").padding(padding).imePadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            if (isCreatingGroup && isBroadcast && createBroadcast == null) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.social_newchat_broadcast_unavailable), color = colors.foregroundSecondary)
                    TextButton(enabled = !isLoading, onClick = { isBroadcast = false; createError = null }) {
                        Text(stringResource(R.string.social_newchat_switch_to_group))
                    }
                }
            }
            if (isCreatingGroup && !groupDetails) {
                com.example.chat.ui.chats.newchat.NewChatPresentationRules.sections(groupUi.order).forEach { section ->
                    when (section) {
                        "search" -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { searchSection() }
                        "selected" -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { selectedSection() }
                        "contacts" -> LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("newchat-group-results"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), content = contactsContent)
                    }
                }
            } else {
            Column(Modifier.fillMaxWidth().then(if (groupDetails) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp)) {
                selectedSection()
                if (isCreatingGroup && groupDetails) {
                    Text(stringResource(if (isBroadcast) R.string.social_newchat_broadcast_review_explanation else R.string.social_newchat_review_explanation), color = colors.foregroundSecondary)
                    NewChatInput(
                        value = groupName,
                        onValueChange = { groupName = com.example.chat.ui.chats.newchat.NewChatPresentationRules.clampTitle(it, titleLimit); createError = null },
                        hint = if (isBroadcast) stringResource(R.string.social_newchat_broadcast_hint) else groupUi.nameLabel,
                        label = stringResource(if (isBroadcast) R.string.social_newchat_broadcast_label else R.string.social_newchat_group_label),
                        fieldError = createError,
                        imeAction = ImeAction.Done,
                        leading = {
                            Icon(
                                if (isBroadcast) Icons.Rounded.Cast else Icons.Rounded.GroupWork,
                                contentDescription = null,
                                tint = if (isBroadcast) colors.info else colors.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                    Text(stringResource(R.string.social_newchat_name_length, NewChatRules.effectiveGroupTitle(groupName, isBroadcast, selectedMembers.size).length, titleLimit),
                        color = colors.foregroundSecondary, fontSize = 14.sp)
                    val finalTitle = NewChatRules.effectiveGroupTitle(groupName, isBroadcast, selectedMembers.size)
                    if (finalTitle.isNotBlank()) {
                        Text(stringResource(R.string.social_newchat_final_title_label), color = colors.foregroundSecondary,
                            modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                        Text(finalTitle, color = colors.foreground, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                }
                searchSection()

            }
            if (!groupDetails) LazyColumn(
                Modifier.fillMaxWidth().weight(1f).testTag("newchat-unified-list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
            if (!isCreatingGroup) {
                item(key = "create-group", contentType = "entry") { NewChatEntryCard(
                    title = stringResource(R.string.social_newchat_create_group_entry),
                    subtitle = stringResource(R.string.social_newchat_create_group_body),
                    icon = { tint ->
                        Icon(Icons.Rounded.GroupAdd, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    },
                    iconTint = colors.primary,
                    chevronTint = colors.foregroundTertiary,
                    onClick = {
                        isCreatingGroup = true
                        isBroadcast = false
                    },
                ) }
                if (createBroadcast != null) {
                item(key = "create-broadcast", contentType = "entry") { NewChatEntryCard(
                    title = stringResource(R.string.social_newchat_create_broadcast_entry),
                    subtitle = stringResource(R.string.social_newchat_create_broadcast_body),
                    icon = { tint ->
                        Icon(Icons.Rounded.Cast, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    },
                    iconTint = colors.info,
                    chevronTint = colors.foregroundTertiary,
                    onClick = {
                        isCreatingGroup = true
                        isBroadcast = true
                    },
                ) }
                } else item(key = "broadcast-unavailable", contentType = "info") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(stringResource(R.string.social_newchat_create_broadcast_entry), color = colors.foreground,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.social_newchat_broadcast_unavailable), color = colors.foregroundSecondary)
                        TextButton(onClick = { isCreatingGroup = true; isBroadcast = false; createError = null }) {
                            Text(stringResource(R.string.social_newchat_switch_to_group))
                        }
                    }
                }
                item(key = "entry-spacing", contentType = "spacing") { Spacer(Modifier.height(4.dp)) }
            }
            requestsContent()
            contactsContent()
            } // Unified entry/request/contact LazyColumn.
            }
        }
        }
    }

    if (showDiscardSelection) {
        val keepEditingFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { keepEditingFocus.requestFocus() }
        com.example.chat.ui.kit.ChatyConfirmationDialog(
            onDismissRequest = { showDiscardSelection = false },
            title = { Text(stringResource(R.string.social_newchat_discard_title)) },
            text = { Text(stringResource(R.string.social_newchat_discard_body)) },
            dismissButton = {
                TextButton(onClick = { showDiscardSelection = false }, modifier = Modifier.focusRequester(keepEditingFocus)) {
                    Text(stringResource(R.string.social_newchat_keep_editing))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardSelection = false
                    requestedGroupDetails = false
                    selectedMembers = emptySet()
                    groupName = ""
                    createError = null
                    isCreatingGroup = false
                    isBroadcast = false
                }) { Text(stringResource(R.string.social_newchat_discard_selection), color = colors.error) }
            },
        )
    }

    // Contact actions. Account deletion belongs to the verified Settings flow.
    val sheetTarget = sheetUser
    if (sheetTarget != null) {
        ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp,
            onDismissRequest = { sheetUser = null },
            containerColor = colors.surface,
            shape = com.example.chat.ui.kit.ChatyModalShape,
        ) {
            val sheetPane = stringResource(R.string.social_newchat_person_actions, sheetTarget.username)
            Column(Modifier.padding(bottom = 24.dp).semantics { paneTitle = sheetPane }) {
                Text(
                    "${NewChatPresentationRules.identityText(sheetTarget.displayName)} (@${NewChatPresentationRules.identityText(sheetTarget.username)})",
                    color = colors.foreground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )
                if (sendContactRequest != null && !isContact(sheetTarget)) {
                    if (sheetTarget.id in requestedUserIds || NewChatRules.usernameKey(sheetTarget.username) in requestedUsernames) {
                        Text(stringResource(R.string.social_newchat_request_pending), color = colors.foregroundTertiary, modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    } else if (!outgoingReady || sheetTarget.id in uncertainRequestIds) {
                        Text(stringResource(if (sheetTarget.id in uncertainRequestIds) R.string.social_newchat_request_uncertain
                            else R.string.social_newchat_request_status_unavailable),
                            color = colors.foregroundSecondary, modifier = Modifier.padding(16.dp))
                        TextButton(enabled = !outgoingLoading,
                            onClick = { scope.launch { refreshOutgoingRequests(); refreshAcceptedContacts() } }) {
                            Text(stringResource(R.string.social_newchat_check_request_status))
                        }
                    } else if (sheetTarget.id in sendingRequestIds || NewChatRules.usernameKey(sheetTarget.username) in sendingRequestUsernames) {
                        Text(stringResource(R.string.social_newchat_request_sending), color = colors.foregroundTertiary,
                            modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    } else {
                        SheetAction(
                            label = stringResource(R.string.social_newchat_send_request),
                            tint = colors.primary,
                            onClick = {
                                val target = sheetTarget
                                val targetKey = NewChatRules.usernameKey(target.username)
                                if (outgoingReady && !isContact(target) &&
                                    NewChatRules.canReserveRequest(target.id, configOwner.userId, requestedUserIds, sendingRequestIds, uncertainRequestIds) &&
                                    targetKey !in sendingRequestUsernames &&
                                    targetKey !in requestedUsernames && configApi.authSnapshot() === configOwner) {
                                sendingRequestIds = sendingRequestIds + target.id
                                sendingRequestUsernames = sendingRequestUsernames + targetKey
                                sheetUser = null
                                scope.launch {
                                    var requestAttempted = false
                                    try {
                                        if (configApi.authSnapshot() !== configOwner) return@launch
                                        check(validateRecipients(setOf(target.id)))
                                        val fresh = NewChatRules.exactUsernameMatch(searchUsersRemote(target.username), target.username, configOwner.userId)
                                        check(fresh.singleOrNull()?.id == target.id)
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        if (configApi.authSnapshot() !== configOwner) return@launch
                                        requestAttempted = true
                                        outgoingRevision++
                                        // Reserve uncertainty before crossing the mutation boundary so cancellation is safe too.
                                        uncertainRequestIds = uncertainRequestIds + target.id
                                        sendContactRequest(target.username)
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        if (configApi.authSnapshot() !== configOwner) return@launch
                                        outgoingRevision++
                                        uncertainRequestIds = uncertainRequestIds - target.id
                                        requestedUserIds = requestedUserIds + target.id
                                        requestedUsernames = requestedUsernames + NewChatRules.usernameKey(target.username)
                                        snackbar.showSnackbar(configContext.getString(R.string.social_newchat_request_sent, target.displayName))
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        if (configApi.authSnapshot() === configOwner) {
                                            if (requestAttempted) uncertainRequestIds = uncertainRequestIds + target.id
                                            snackbar.showSnackbar(configContext.getString(if (requestAttempted)
                                                R.string.social_newchat_request_uncertain else R.string.social_newchat_request_send_failure))
                                        }
                                    } finally {
                                        sendingRequestIds = sendingRequestIds - target.id
                                        sendingRequestUsernames = sendingRequestUsernames - targetKey
                                    }
                                }
                                }
                            },
                        )
                    }
                }
                SheetAction(
                    label = stringResource(R.string.social_newchat_start_chat),
                    tint = colors.primary,
                    onClick = {
                        sheetUser = null
                        openDirectChat(sheetTarget)
                    },
                )
                // This host exposes only self-account deletion, not directory administration.
                // Do not route a selected contact into an account-deletion endpoint.
                Text(stringResource(R.string.social_newchat_account_deletion_settings),
                    color = colors.foregroundSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
            }
        }
    }


}

/** ChatyInput stand-in for this slice: filled rounded field with leading /
 *  trailing slots, matching the Dart widget's look on the default theme. */
@Composable
private fun NewChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    label: String,
    fieldError: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, color = colors.foregroundSecondary, fontSize = 14.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
                .background(colors.inputFill, RoundedCornerShape(14.dp))
                .then(if (focused) Modifier.border(2.dp, colors.primary, RoundedCornerShape(14.dp)) else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = imeAction, autoCorrect = false),
                keyboardActions = KeyboardActions(
                    onSearch = { onImeAction(); keyboard?.hide() },
                    onDone = { onImeAction(); keyboard?.hide() },
                ),
                textStyle = TextStyle(color = colors.foreground, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused }.semantics {
                    contentDescription = label
                    fieldError?.let { error(it) }
                },
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(hint, color = colors.foregroundTertiary, fontSize = 16.sp)
                    inner()
                },
            )
            if (trailing != null) trailing()
        }
    }
}

@Composable
private fun NewChatEntryCard(
    title: String,
    subtitle: String,
    icon: @Composable (Color) -> Unit,
    iconTint: Color,
    chevronTint: Color,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(iconTint.copy(alpha = 0.12f), CircleShape)
                .padding(8.dp),
        ) {
            icon(iconTint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 14.sp)
        }
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight, semanticLabel = null, color = chevronTint)
    }
}

/** AppAvatar + ChatyOnlineDot port (app_avatar.dart / chaty_kit.dart):
 *  initials circle painted with the parsed avatarColorHex, presence dot
 *  bottom-right (offset 1dp past the edge), diameter (size*0.28) clamped to
 *  10..14dp, 2dp ring in the surrounding surface color, presence-colored
 *  (online success, away warning, typing primary, offline tertiary). The
 *  Dart call site passes showOnlineBadge: true, so the dot always renders. */
@Composable
private fun NewChatAvatar(user: UserProfile) {
    val colors = LocalAppColors.current
    val presenceLabel = stringResource(when (user.presence) {
        com.example.chat.domain.models.PresenceState.online -> R.string.social_newchat_presence_online
        com.example.chat.domain.models.PresenceState.away -> R.string.social_newchat_presence_away
        com.example.chat.domain.models.PresenceState.typing -> R.string.social_newchat_presence_typing
        com.example.chat.domain.models.PresenceState.offline -> R.string.social_newchat_presence_offline
    })
    val presenceColor = when (user.presence) {
        com.example.chat.domain.models.PresenceState.online -> colors.success
        com.example.chat.domain.models.PresenceState.away -> colors.warning
        com.example.chat.domain.models.PresenceState.typing -> colors.primary
        com.example.chat.domain.models.PresenceState.offline -> colors.foregroundTertiary
    }
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    Color(com.example.chat.ui.chats.contact_info.parseAvatarColorArgb(user.avatarColorHex, LocalThemeConfig.current.accentColor)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                user.avatarInitials,
                color = colors.onPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 1.dp, y = 1.dp)
                .size(((42f * 0.28f).coerceIn(10f, 14f)).dp)
                .border(2.dp, colors.background, CircleShape)
                .background(presenceColor, CircleShape).semantics { contentDescription = presenceLabel },
        )
    }
}

/** ChatySkeletonTile stand-in: static placeholder row (Dart renders a
 *  shimmering avatar + two bars). */
@Composable
private fun SkeletonTile() {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(colors.borderSubtle, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Box(Modifier.width(140.dp).height(12.dp).background(colors.borderSubtle, RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(220.dp).height(10.dp).background(colors.borderSubtle, RoundedCornerShape(5.dp)))
        }
    }
}

@Composable
private fun SheetAction(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        label,
        color = tint,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

// Superseded source lines retained inactive for review:
// import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
//  *  long-press Start Chat / Delete User sheet.
//  *  deleteUser). Skeleton/empty/no-results states mirror the Dart
//     deleteUser: suspend (String) -> Unit,
//     var isDeletingUser by remember { mutableStateOf(false) }
//     val isLoading = queryLoading || contactsLoading || isSubmittingGroup || isOpeningDirect || isDeletingUser
//         isDeletingUser -> R.string.social_newchat_deleting
//     var deleteCandidate by remember { mutableStateOf<UserProfile?>(null) }
//         contacts.firstOrNull { it.id == entry.userId } ?: UserProfile(
//         if (groupName.length > titleLimit) { createError = configContext.getString(R.string.social_newchat_name_too_long); return }
//         if (isOpeningDirect || isSubmittingGroup || isDeletingUser || queryLoading || contactsLoading || configApi.authSnapshot() !== configOwner) return
//             if (groupUi.selectedDisplay == "list") Column(
//                 Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
//                 selectedMembers.forEach { id -> androidx.compose.runtime.key(id) { selectedMemberControl(id) } }
//         if (isSubmittingGroup || isOpeningDirect || isDeletingUser) return
//     androidx.activity.compose.BackHandler(enabled = sheetUser == null && deleteCandidate == null) { handleBack() }
//                 modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
//                         enabled = selectedMembers.isNotEmpty() && (!groupDetails || NewChatRules.canCreateGroup(groupName, isBroadcast, selectedMembers.size, isLoading)),
//                     Text(stringResource(R.string.social_newchat_name_length, groupName.length, titleLimit),
//     // _onUserLongPress sheet: Start Chat / Delete User.
//                 SheetAction(
//                     label = "Delete User",
//                     tint = colors.error,
//                     onClick = {
//                         sheetUser = null
//                         deleteCandidate = sheetTarget
//                     },
//                 )
//     // ChatyConfirmDialog for Delete User.
//     val deleteTarget = deleteCandidate
//     if (deleteTarget != null) {
//         AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
//             onDismissRequest = { deleteCandidate = null },
//             containerColor = colors.surface,
//             title = { Text("Delete ${deleteTarget.displayName} (@${deleteTarget.username})?", color = colors.foreground, modifier = Modifier.semantics { heading() }) },
//             text = {
//                 Text(
//                     "This user will be permanently removed from your directory and workspace.",
//                     color = colors.foregroundSecondary,
//                 )
//             },
//             confirmButton = {
//                 TextButton(onClick = confirmDelete@ {
//                     if (isDeletingUser || isSubmittingGroup || isOpeningDirect || queryLoading || contactsLoading || deleteCandidate?.id != deleteTarget.id || configApi.authSnapshot() !== configOwner) return@confirmDelete
//                     isDeletingUser = true
//                     deleteCandidate = null
//                     scope.launch {
//                         try {
//                             if (configApi.authSnapshot() !== configOwner) return@launch
//                             deleteUser(deleteTarget.id)
//                             if (configApi.authSnapshot() !== configOwner) return@launch
//                             acceptedContacts = acceptedContacts.filterNot { it.id == deleteTarget.id }
//                             results = results.filterNot { it.id == deleteTarget.id }
//                             snackbar.showSnackbar("${deleteTarget.displayName} deleted")
//                         } catch (error: Exception) {
//                             if (error is kotlinx.coroutines.CancellationException) throw error
//                             if (configApi.authSnapshot() === configOwner) snackbar.showSnackbar("Unable to delete this user. Try again.")
//                         } finally {
//                             isDeletingUser = false
//                         }
//                     }
//                 }) {
//                     Text("Delete", color = colors.error, fontWeight = FontWeight.Bold)
//                 }
//             },
//             dismissButton = {
//                 TextButton(onClick = { deleteCandidate = null }) {
//                     Text("Cancel", color = colors.foregroundSecondary)
//                 }
//             },
//         )
//     }
//                 modifier = Modifier.weight(1f).semantics {

// Superseded source retained inactive:
// import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
//  *  long-press Start Chat / Delete User sheet.
//  *  deleteUser). Skeleton/empty/no-results states mirror the Dart
//     deleteUser: suspend (String) -> Unit,
//     val draftAccountKey = configApi.currentAccountHash().orEmpty()
//     var searchText by rememberSaveable(configOwner, key = "newchat-query:$draftAccountKey") { mutableStateOf("") }
//     var groupDetails by remember { mutableStateOf(false) }
//     var groupName by rememberSaveable(configOwner, key = "newchat-title:$draftAccountKey") { mutableStateOf("") }
//     var selectedMembers by remember { mutableStateOf(setOf<String>()) }
//     var isCreatingGroup by rememberSaveable(configOwner, key = "newchat-group:$draftAccountKey") { mutableStateOf(startInGroupMode || startInBroadcastMode) }
//     var isBroadcast by rememberSaveable(configOwner, key = "newchat-broadcast:$draftAccountKey") { mutableStateOf(startInBroadcastMode) }
//     var isDeletingUser by remember { mutableStateOf(false) }
//     val isLoading = queryLoading || contactsLoading || isSubmittingGroup || isOpeningDirect || isDeletingUser
//         isDeletingUser -> R.string.social_newchat_deleting
//     var deleteCandidate by remember { mutableStateOf<UserProfile?>(null) }
//         contacts.firstOrNull { it.id == entry.userId } ?: UserProfile(
//                 acceptedContacts = entries.map(::contactProfile).distinctBy { it.id }
//                 contactRequests = inbox
//     LaunchedEffect(Unit) { refreshAcceptedContacts() }
//         if (!groupDetails || isSubmittingGroup || configApi.authSnapshot() !== configOwner) return
//         if (groupName.length > titleLimit) { createError = configContext.getString(R.string.social_newchat_name_too_long); return }
//         if (isOpeningDirect || isSubmittingGroup || isDeletingUser || queryLoading || contactsLoading || configApi.authSnapshot() !== configOwner) return
//     val selectionProfiles = remember(contacts, acceptedContacts, results) {
//         (contacts + acceptedContacts + results).associateBy { it.id }
//     }
//         TextButton(enabled = !isLoading, onClick = { selectedMembers = selectedMembers - id },
//             if (groupUi.selectedDisplay == "list") Column(
//                 Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
//                 selectedMembers.forEach { id -> androidx.compose.runtime.key(id) { selectedMemberControl(id) } }
//         if (isSubmittingGroup || isOpeningDirect || isDeletingUser) return
//             groupDetails = false
//             isCreatingGroup = false
//             isBroadcast = false
//             selectedMembers = emptySet()
//     androidx.activity.compose.BackHandler(enabled = sheetUser == null && deleteCandidate == null) { handleBack() }
//                 modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
//                                 groupDetails = true; createError = null
//                         enabled = selectedMembers.isNotEmpty() && (!groupDetails || NewChatRules.canCreateGroup(groupName, isBroadcast, selectedMembers.size, isLoading)),
//                     Text(stringResource(R.string.social_newchat_name_length, groupName.length, titleLimit),
//                     if (NewChatRules.usernameKey(sheetTarget.username) in requestedUsernames) {
//                                 if (targetKey !in sendingRequestUsernames && targetKey !in requestedUsernames && configApi.authSnapshot() === configOwner) {
//                                         if (configApi.authSnapshot() === configOwner) snackbar.showSnackbar(configContext.getString(R.string.social_newchat_request_send_failure))
//                 SheetAction(
//                     label = "Delete User",
//                     tint = colors.error,
//                     onClick = {
//                         sheetUser = null
//                         deleteCandidate = sheetTarget
//                     },
//                 )
//     val deleteTarget = deleteCandidate
//     if (deleteTarget != null) {
//         AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
//             onDismissRequest = { deleteCandidate = null },
//             containerColor = colors.surface,
//             title = { Text("Delete ${deleteTarget.displayName} (@${deleteTarget.username})?", color = colors.foreground, modifier = Modifier.semantics { heading() }) },
//             text = {
//                 Text(
//                     "This user will be permanently removed from your directory and workspace.",
//                     color = colors.foregroundSecondary,
//                 )
//             },
//             confirmButton = {
//                 TextButton(onClick = confirmDelete@ {
//                     if (isDeletingUser || isSubmittingGroup || isOpeningDirect || queryLoading || contactsLoading || deleteCandidate?.id != deleteTarget.id || configApi.authSnapshot() !== configOwner) return@confirmDelete
//                     isDeletingUser = true
//                     deleteCandidate = null
//                     scope.launch {
//                         try {
//                             if (configApi.authSnapshot() !== configOwner) return@launch
//                             deleteUser(deleteTarget.id)
//                             if (configApi.authSnapshot() !== configOwner) return@launch
//                             acceptedContacts = acceptedContacts.filterNot { it.id == deleteTarget.id }
//                             results = results.filterNot { it.id == deleteTarget.id }
//                             snackbar.showSnackbar("${deleteTarget.displayName} deleted")
//                         } catch (error: Exception) {
//                             if (error is kotlinx.coroutines.CancellationException) throw error
//                             if (configApi.authSnapshot() === configOwner) snackbar.showSnackbar("Unable to delete this user. Try again.")
//                         } finally {
//                             isDeletingUser = false
//                         }
//                     }
//                 }) {
//                     Text("Delete", color = colors.error, fontWeight = FontWeight.Bold)
//                 }
//             },
//             dismissButton = {
//                 TextButton(onClick = { deleteCandidate = null }) {
//                     Text("Cancel", color = colors.foregroundSecondary)
//                 }
//             },
//         )
//     }
//                 modifier = Modifier.weight(1f).semantics {

*/
