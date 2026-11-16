package com.example.chat.ui.calls

import androidx.compose.runtime.collectAsState

import kotlinx.coroutines.flow.collect
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.PeopleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

enum class CallContactPickerMode { contacts, dialpad }

private val NON_DIGITS = Regex("[^0-9]")

private data class DialKey(val digit: String, val subtitle: String)

private val dialKeys = listOf(
    DialKey("1", ""), DialKey("2", "ABC"), DialKey("3", "DEF"),
    DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO"),
    DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ"),
    DialKey("*", ""), DialKey("0", "+"), DialKey("#", ""),
)

/**
 * Port of features/calls/call_contact_picker_screen.dart: full-screen contact
 * picker shared by the Calls FABs. Dialpad mode filters registered Chaty
 * contacts live as the user enters a name or number; a call starts only for
 * one unambiguous registered-contact match.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallContactPickerScreen(
    contacts: List<UserProfile>,
    currentUserId: String,
    initialMode: CallContactPickerMode = CallContactPickerMode.contacts,
    onBack: () -> Unit,
// Previous implementation retained:     onStartCall: (contact: UserProfile, isVideo: Boolean) -> Unit,
    onStartCall: suspend (contact: UserProfile, isVideo: Boolean) -> Unit,
    showToast: (message: String, isError: Boolean) -> Unit,
) {
    val theme = LocalThemeConfig.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val soundScope = androidx.compose.runtime.rememberCoroutineScope()
    val soundPreferences = remember(context, soundScope) { KeypadSoundPreferences.onDevice(context, soundScope) }
    val appKeypadSounds by soundPreferences.enabled.collectAsState()
    val soundPreferenceReady by soundPreferences.ready.collectAsState()
    val soundPreferenceFailed by soundPreferences.saveFailed.collectAsState()

    // Respect silent/vibrate and the device's dial-pad tone preference.
    val tones = remember(context) { runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull() }
    DisposableEffect(tones) { onDispose { tones?.release() } }

    val toneDispatcher = remember(tones) {
        DialpadToneDispatcher { tone, durationMs ->
            val androidTone = when (tone) {
                DialpadToneDispatcher.Tone.STAR -> ToneGenerator.TONE_DTMF_S
                DialpadToneDispatcher.Tone.HASH -> ToneGenerator.TONE_DTMF_P
                else -> ToneGenerator.TONE_DTMF_0 + tone.ordinal
            }
            tones?.startTone(androidTone, durationMs) == true
        }
    }
// Previous implementation retained:     // CALLS-R1-33: read the system tone preference and ringer mode once at screen entry
// Previous implementation retained:     // instead of two binder calls per keypress; the read must never block dialing.
// Previous implementation retained:     val dialpadSoundsEnabled = remember {
// Previous implementation retained:         runCatching { Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) != 0 }.getOrDefault(true)
// Previous implementation retained:     }
// Previous implementation retained:     val normalRingerMode = remember {
// Previous implementation retained:         runCatching { context.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_NORMAL }.getOrDefault(true)
// Previous implementation retained:     }
    fun playDialTone(digit: String) {
// Previous implementation retained: // Previous implementation retained:         runCatching { toneDispatcher.dispatch(digit, dialpadSoundsEnabled, normalRingerMode) }
// Previous implementation retained:         runCatching { toneDispatcher.dispatch(digit, appKeypadSounds && dialpadSoundsEnabled, normalRingerMode) }
        // Re-read on every tap. Entry-time snapshots can sound after switching to Silent.
        // An unavailable system check fails silent without blocking digit entry.
        val systemEnabled = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) != 0
        }.getOrDefault(false)
        val normalRinger = runCatching {
            context.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_NORMAL
        }.getOrDefault(false)
        runCatching { toneDispatcher.dispatch(digit, appKeypadSounds && systemEnabled, normalRinger) }
    }

    // CALLS-R1-23: dialed-but-never-connected digits are highly sensitive; this state must
    // never be captured into analytics or crash breadcrumbs.
    var query by remember { mutableStateOf("") }
    var isDialpad by remember { mutableStateOf(initialMode == CallContactPickerMode.dialpad) }
    var isKeypadCollapsed by remember { mutableStateOf(false) }
    var predictiveKeypadProgress by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val reduceKeypadMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    androidx.activity.compose.PredictiveBackHandler(enabled = isDialpad && !isKeypadCollapsed) { events ->
        completeCallPickerBack(events.map { it.progress },
            onProgress = { predictiveKeypadProgress = it },
            onCommit = { if (isDialpad && !isKeypadCollapsed) isKeypadCollapsed = true })
    }
    // CALLS-R1-45: session/rotation-scoped choice; no global preference is silently written.
    var keepKeypadOpen by rememberSaveable { mutableStateOf(false) }
    var startingContactId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Dart _handleScroll: scrolling down in dialpad mode collapses the keypad.
    // CALLS-R1-31: stop collecting scroll frames once the keypad has collapsed.
// Previous implementation retained:     LaunchedEffect(isDialpad, isKeypadCollapsed) {
// Previous implementation retained:         if (!isDialpad || isKeypadCollapsed) return@LaunchedEffect
    LaunchedEffect(isDialpad, isKeypadCollapsed, keepKeypadOpen) {
        if (!isDialpad || isKeypadCollapsed || keepKeypadOpen) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemScrollOffset to listState.firstVisibleItemIndex }
            .collect { (offset, index) ->
                if ((index > 0 || offset > 24) && !isKeypadCollapsed) {
                    isKeypadCollapsed = true
                }
            }
    }

    // CALLS-R1-30: T9/digit forms computed once per contact, not per keystroke.
    val contactIndex = remember(contacts, currentUserId) {
        contacts.filter { it.id != currentUserId }
            .map { it to dialerContactSearchBlob(it.displayName, it.phone) }
    }
    val visibleContacts by remember(query, contactIndex) {
        derivedStateOf { contactIndex.filter { matchesDialerBlob(query, it.second) }.map { it.first } }
    }

    fun initials(name: String): String {
        val normalized = name.trim()
// Previous implementation retained:         return if (normalized.isEmpty()) "?" else normalized.substring(0, 1).uppercase()
        return if (normalized.isEmpty()) "?" else callInitials(normalized)
    }

    val scope = rememberCoroutineScope()
    fun startCall(contact: UserProfile, isVideo: Boolean) {
        if (startingContactId != null) return
        startingContactId = contact.id
        // CALLS-R1-58: confirm the consequential action with haptics.
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
// Previous implementation retained:         onStartCall(contact, isVideo)
// Previous implementation retained:         // CALLS-R1-10: the owner completes the async initiate; hold the guard (and the
// Previous implementation retained:         // spinner) through that window instead of reopening it synchronously.
        scope.launch {
// Previous implementation retained:             delay(3000)
// Previous implementation retained:             if (startingContactId == contact.id) startingContactId = null
            try { onStartCall(contact, isVideo) }
            finally { if (startingContactId == contact.id) startingContactId = null }
        }
    }

    var digitLimitHit by remember { mutableStateOf(false) }
    fun appendDigit(digit: String) {
        if (startingContactId != null) return
        if (query.length >= 25) { digitLimitHit = true; return }
        digitLimitHit = false
        query += digit
        playDialTone(digit)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun removeDigit() {
        if (query.isEmpty()) return
        digitLimitHit = false
        query = query.substring(0, query.length - 1)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    var backspaceRepeating by remember { mutableStateOf(false) }
    LaunchedEffect(backspaceRepeating, startingContactId) {
        if (!backspaceRepeating) return@LaunchedEffect
        if (startingContactId != null) { backspaceRepeating = false; return@LaunchedEffect }
        while (backspaceRepeating && query.isNotEmpty() && startingContactId == null) {
            delay(100)
            if (query.isEmpty() || startingContactId != null) break
            removeDigit()
        }
        backspaceRepeating = false
    }

    fun triggerCall() {
        val digits = query.replace(NON_DIGITS, "")
        // CALLS-R1-11: only a full normalized number match auto-starts; suffix-only matches
        // never dial on their own - the user taps the candidate row instead.
        val exact = contacts.filter { contact ->
            if (contact.id == currentUserId) return@filter false
            contact.phone.replace(NON_DIGITS, "") == digits
        }
        if (exact.size == 1 && digits.length >= 7) {
            startCall(exact.single(), isVideo = false)
            return
        }
        // CALLS-R1-13: one neutral message - never confirm whether a typed number is registered.
        showToast(
// Previous implementation retained:             if (exact.size > 1) "More than one contact matches. Choose the right contact below."
// Previous implementation retained:             else "No call can be started for this number. Tap a matching contact below to call.",
            if (exact.size > 1) context.getString(com.example.chat.R.string.calls_ui_24)
            else context.getString(com.example.chat.R.string.calls_ui_25),
            true,
        )
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom)),
        ),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color(theme.backgroundColor),
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,
        floatingActionButton = {
            if (isDialpad && isKeypadCollapsed) {
                FloatingActionButton(
                    modifier = Modifier.testTag("dialpad-reopen").size(56.dp),
                    onClick = { isKeypadCollapsed = false },
                    containerColor = Color(theme.successColor),
                    contentColor = Color.White,
                ) {
// Previous implementation retained:                     Icon(Icons.Rounded.Dialpad, contentDescription = "Show dialpad", modifier = Modifier.size(28.dp))
                    Icon(Icons.Rounded.Dialpad, contentDescription = context.getString(com.example.chat.R.string.calls_ui_26), modifier = Modifier.size(28.dp))
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val compactHeight = maxHeight < 600.dp
// Previous implementation retained:         Column(Modifier.fillMaxSize()) {
        // CALLS-R2-16: keep keypad/list above the soft keyboard instead of being covered by it.
        Column(Modifier.fillMaxSize().imePadding()) {
            // App bar row (Dart AppBar: back, title, mode toggle).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onBack) {
// Previous implementation retained:                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Back", color = Color(theme.primaryTextColor))
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = context.getString(com.example.chat.R.string.calls_ui_27), color = Color(theme.primaryTextColor))
                }
                Text(
// Previous implementation retained:                     if (isDialpad) "Add call" else "Contacts",
                    if (isDialpad) context.getString(com.example.chat.R.string.calls_ui_6) else context.getString(com.example.chat.R.string.calls_ui_28),
                    color = Color(theme.primaryTextColor),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { isDialpad = !isDialpad },
                    modifier = Modifier.size(48.dp).semantics(mergeDescendants = true) {
// Previous implementation retained:                         contentDescription = if (isDialpad) "Show contacts" else "Open dialpad"
// Previous implementation retained:                         stateDescription = if (isDialpad) "Dialpad mode" else "Contacts mode"
                        contentDescription = if (isDialpad) context.getString(com.example.chat.R.string.calls_ui_29) else context.getString(com.example.chat.R.string.calls_ui_30)
                        stateDescription = if (isDialpad) context.getString(com.example.chat.R.string.calls_ui_31) else context.getString(com.example.chat.R.string.calls_ui_32)
                    }) {
                    Icon(
                        if (isDialpad) Icons.Rounded.Contacts else Icons.Rounded.Dialpad,
                        contentDescription = null,
                        tint = Color(theme.accentColor),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            // Owner UI-only change: hide the two dialpad options. Stored sound defaults,
            // current preference reads, tone gating and keypad-collapse behavior stay intact.
// Previous implementation retained:             if (isDialpad) {
// Previous implementation retained:                 Row(
// Previous implementation retained:                     Modifier.fillMaxWidth().toggleable(
// Previous implementation retained:                         value = keepKeypadOpen,
// Previous implementation retained:                         role = androidx.compose.ui.semantics.Role.Checkbox,
// Previous implementation retained:                         onValueChange = { keepKeypadOpen = it },
// Previous implementation retained:                     ).padding(horizontal = 20.dp, vertical = 4.dp),
// Previous implementation retained:                     verticalAlignment = Alignment.CenterVertically,
// Previous implementation retained:                 ) {
// Previous implementation retained:                     Checkbox(checked = keepKeypadOpen, onCheckedChange = null)
// Previous implementation retained:                     Spacer(Modifier.width(8.dp))
// Previous implementation retained: // Previous implementation retained:                     Text("Keep dialpad open", color = Color(theme.primaryTextColor), fontSize = 14.sp)
// Previous implementation retained:                     Text(context.getString(com.example.chat.R.string.calls_ui_33), color = Color(theme.primaryTextColor), fontSize = 14.sp)
// Previous implementation retained:                 }
// Previous implementation retained:             }
// Previous implementation retained
// Previous implementation retained:             if (isDialpad) {
// Previous implementation retained:                 Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
// Previous implementation retained:                     value = appKeypadSounds, enabled = soundPreferenceReady,
// Previous implementation retained:                     role = Role.Checkbox, onValueChange = { soundPreferences.choose(it) }
// Previous implementation retained:                 ).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
// Previous implementation retained:                     Checkbox(checked = appKeypadSounds, onCheckedChange = null, enabled = soundPreferenceReady)
// Previous implementation retained:                     Text(if (appKeypadSounds) "Keypad sounds: on" else "Keypad sounds: off",
// Previous implementation retained:                         color = Color(theme.primaryTextColor))
// Previous implementation retained:                 }
// Previous implementation retained:                 if (soundPreferenceFailed) Text("Keypad sounds stay off because the choice could not be saved. Tap to try again.",
// Previous implementation retained:                     color = Color(theme.secondaryTextColor), modifier = Modifier.padding(horizontal = 20.dp))
// Previous implementation retained:             }
// Previous implementation retained
            // Search / number field.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 6.dp),
            ) {
                TextField(
                    value = query,
                    onValueChange = { if (!isDialpad) query = it },
                    readOnly = isDialpad,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isDialpad) KeyboardType.Text else KeyboardType.Text,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(theme.primaryTextColor),
                        fontSize = if (isDialpad) 32.sp else 16.sp,
                        letterSpacing = if (isDialpad) 1.4.sp else 0.sp,
                        fontWeight = if (isDialpad) FontWeight.Normal else FontWeight.Medium,
                        textAlign = if (isDialpad) TextAlign.Center else TextAlign.Start,
                    ),
                    placeholder = {
                        Text(
// Previous implementation retained:                             if (isDialpad) "Enter number" else "Search contacts",
                            if (isDialpad) context.getString(com.example.chat.R.string.calls_ui_34) else context.getString(com.example.chat.R.string.calls_ui_35),
                            color = Color(theme.secondaryTextColor),
                            textAlign = if (isDialpad) TextAlign.Center else TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    leadingIcon = if (!isDialpad) {
                        { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search, semanticLabel = null, color = Color(theme.secondaryTextColor)) }
                    } else null,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (isDialpad) Color.Transparent else Color(theme.cardColor),
                        unfocusedContainerColor = if (isDialpad) Color.Transparent else Color(theme.cardColor),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(theme.accentColor),
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).semantics { if (isDialpad) liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                )
            }

            if (isDialpad && digitLimitHit) {
                Text(
// Previous implementation retained:                     "Number too long",
                    context.getString(com.example.chat.R.string.calls_ui_36),
                    color = Color(theme.dangerColor),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (isDialpad && query.isNotEmpty()) {
                Text(
                    when {
// Previous implementation retained:                         visibleContacts.isEmpty() -> "No registered contact matches this number"
// Previous implementation retained:                         visibleContacts.size == 1 -> "1 matching Chaty contact"
// Previous implementation retained:                         else -> "${visibleContacts.size} matching Chaty contacts"
                        visibleContacts.isEmpty() -> context.getString(com.example.chat.R.string.calls_ui_37)
                        visibleContacts.size == 1 -> context.resources.getQuantityString(com.example.chat.R.plurals.call_picker_matching_count, 1, 1)
                        else -> context.resources.getQuantityString(com.example.chat.R.plurals.call_picker_matching_count, visibleContacts.size, visibleContacts.size)
                    },
                    color = Color(theme.secondaryTextColor),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (!isDialpad) {
                Text(
// Previous implementation retained:                     if (visibleContacts.size == 1) "1 contact" else "${visibleContacts.size} contacts",
                    context.resources.getQuantityString(com.example.chat.R.plurals.call_picker_contact_count, visibleContacts.size, visibleContacts.size),
                    color = Color(theme.secondaryTextColor),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 8.dp),
                )
            }

// Previous implementation retained:             Box(Modifier.weight(1f)) {
            // CALLS-R1-57: de-emphasize the list while the keypad sheet owns the foreground.
            val keypadExpanded = isDialpad && !isKeypadCollapsed
            Box(Modifier.weight(1f).then(if (keypadExpanded) Modifier.alpha(0.5f) else Modifier)) {
                if (visibleContacts.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                    ) {
                        Spacer(Modifier.height(60.dp))
                        Icon(
                            if (query.isEmpty()) Icons.Rounded.PeopleOutline else Icons.Rounded.PersonSearch,
                            contentDescription = null,
                            tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.6)),
                            modifier = Modifier.size(42.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
// Previous implementation retained:                             if (query.isEmpty()) "No contacts available" else "No matching contacts",
                            if (query.isEmpty()) context.getString(com.example.chat.R.string.calls_ui_38) else context.getString(com.example.chat.R.string.calls_ui_39),
                            color = Color(theme.primaryTextColor),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
// Previous implementation retained:                             if (query.isEmpty()) "Your registered Chaty contacts will appear here."
// Previous implementation retained:                             else "Calls are available for registered Chaty contacts only.",
                            if (query.isEmpty()) context.getString(com.example.chat.R.string.calls_ui_40)
                            else context.getString(com.example.chat.R.string.calls_ui_41),
                            color = Color(theme.secondaryTextColor),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
// Previous implementation retained:                         contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 16.dp),
                        contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = if (isDialpad && isKeypadCollapsed) 96.dp else 16.dp),
                    ) {
                        items(visibleContacts, key = { it.id }) { contact ->
                            val name = contact.displayName.trim()
                            val phone = contact.phone.trim()
                            val about = contact.about.trim()
                            // CALLS-R1-14: prefer the contact's about line over exposing the raw
                            // number in every row (shoulder-surfing/screenshots).
                            val subtitle = when {
                                about.isNotEmpty() -> about
// Previous implementation retained:                                 phone.isNotEmpty() -> phone
                                phone.isNotEmpty() -> callPhoneDisplay(phone, java.util.Locale.getDefault().country)
// Previous implementation retained:                                 else -> "Chaty contact"
                                else -> context.getString(com.example.chat.R.string.calls_ui_15)
                            }
                            val isThisCallStarting = startingContactId == contact.id
                            val isStartingCall = startingContactId != null

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isThisCallStarting && !isStartingCall) {
                                        startCall(contact, isVideo = false)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                CallAvatar(initials = initials(name), colorHex = contact.avatarColorHex, size = 46.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
// Previous implementation retained:                                         name.ifEmpty { "Chaty contact" },
                                        name.ifEmpty { context.getString(com.example.chat.R.string.calls_ui_15) },
                                        color = Color(theme.primaryTextColor),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
// Previous implementation retained:                                         maxLines = 1,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        subtitle,
                                        color = Color(theme.secondaryTextColor),
                                        fontSize = 12.5.sp,
// Previous implementation retained:                                         maxLines = 1,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isThisCallStarting) {
                                    // CALLS-R1-55: the spinner keeps the 96dp button slot so the row
                                    // doesn't jump; CALLS-R1-44: announced to screen readers.
                                    Box(
                                        Modifier.width(96.dp).semantics(mergeDescendants = true) {
// Previous implementation retained:                                             contentDescription = "Starting call to $name"
                                            contentDescription = context.getString(com.example.chat.R.string.call_picker_starting, name)
                                            liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.2.dp,
                                            color = Color(theme.accentColor),
                                            modifier = Modifier.size(40.dp).padding(10.dp),
                                        )
                                    }
                                } else {
                                    Row {
                                        IconButton(
                                            onClick = { startCall(contact, isVideo = false) },
                                            enabled = !isStartingCall,
                                        ) {
                                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callAudio,
// Previous implementation retained:                                                 semanticLabel = "Voice call",
                                                semanticLabel = context.getString(com.example.chat.R.string.calls_ui_20),
                                                color = Color(theme.accentColor),
                                                modifier = Modifier.size(21.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { startCall(contact, isVideo = true) },
                                            enabled = !isStartingCall,
                                        ) {
                                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callVideo,
// Previous implementation retained:                                                 semanticLabel = "Video call",
                                                semanticLabel = context.getString(com.example.chat.R.string.calls_ui_19),
                                                color = Color(theme.accentColor),
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = Color(theme.dividerColor),
                                thickness = 1.dp,
                                modifier = Modifier.padding(start = 72.dp),
                            )
                        }
                    }
                }
            }

            if (isDialpad && !isKeypadCollapsed) {
                // Dialpad sheet (Dart _buildDialpad).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = if (reduceKeypadMotion) 0f else size.height * predictiveKeypadProgress
                        }
// Previous implementation retained:                         .then(if (compactHeight) Modifier.weight(1f).semantics { contentDescription = "Dialpad keys" }.verticalScroll(rememberScrollState()) else Modifier)
                        .then(if (compactHeight) Modifier.weight(1f).semantics { contentDescription = context.getString(com.example.chat.R.string.calls_ui_42) }.verticalScroll(rememberScrollState()) else Modifier)
                        .background(
                            Color(theme.surfaceColor),
                            com.example.chat.ui.kit.ChatyModalShape,
                        )
                        .navigationBarsPadding()
                        .padding(start = 28.dp, top = 16.dp, end = 28.dp, bottom = 16.dp),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxWidth().height(302.dp),
                        userScrollEnabled = false,
                    ) {
                        items(dialKeys) { key ->
                            // The grid gives each item its column width. Keep that outer
                            // cell separate so the actual key remains a 68dp circle.
                            Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.Center) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .width(68.dp)
                                    .aspectRatio(1f)
                                        .testTag("dial-key-${key.digit}")
                                        .semantics(mergeDescendants = true) {
// Previous implementation retained:                                             contentDescription = "Dial ${key.digit}${if (key.subtitle.isNotEmpty()) ", ${key.subtitle}" else ""}${if (key.digit == "0") ", long press for plus" else ""}"
                                            contentDescription = context.getString(
                                                com.example.chat.R.string.call_picker_dial_key,
                                                key.digit,
                                                key.subtitle.takeIf { it.isNotEmpty() }?.let {
                                                    context.getString(com.example.chat.R.string.call_picker_key_letters, it)
                                                }.orEmpty(),
                                                if (key.digit == "0") context.getString(com.example.chat.R.string.call_picker_plus_hint) else "",
                                            )
                                            if (key.digit == "0") {
                                                customActions = listOf(
// Previous implementation retained:                                                     androidx.compose.ui.semantics.CustomAccessibilityAction("Insert plus") { appendDigit("+"); true },
                                                    androidx.compose.ui.semantics.CustomAccessibilityAction(context.getString(com.example.chat.R.string.calls_ui_43)) { appendDigit("+"); true },
                                                )
                                            }
                                        }
                                        .clip(CircleShape)
                                        .background(
                                            Color(argbWithAlpha(theme.cardColor, 0.5)),
                                            CircleShape,
                                        )
                                        .combinedClickable(
                                            onClick = { appendDigit(key.digit) },
                                            onLongClick = if (key.digit == "0") {
                                                { appendDigit("+") }
                                            } else null,
                                        ),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            key.digit,
                                            color = Color(theme.primaryTextColor),
                                            fontSize = 27.sp,
                                            fontWeight = FontWeight.Normal,
                                            letterSpacing = (-0.2).sp,
                                        )
                                        // CALLS-R1-43: voicemail glyph removed - it implied an action
                                        // that doesn't exist (no fake affordances). Restore when voicemail lands:
                                        // if (key.digit == "1") { Icon(ChatyGlyphVectors.of(ChatyIconId.voicemail), ...) } else if
                                        if (key.subtitle.isNotEmpty()) {
                                            Text(
                                                key.subtitle,
                                                color = Color(theme.secondaryTextColor),
                                                fontSize = 9.5.sp,
                                                letterSpacing = 1.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
// Previous implementation retained:                         IconButton(onClick = { isKeypadCollapsed = true }, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Hide dialpad" }) {
                        IconButton(onClick = { isKeypadCollapsed = true }, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = context.getString(com.example.chat.R.string.calls_ui_44) }) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(theme.secondaryTextColor),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(60.dp)
                                .testTag("dial-call")
// Previous implementation retained:                                 .semantics(mergeDescendants = true) { contentDescription = "Call number" }
                                .semantics(mergeDescendants = true) { contentDescription = context.getString(com.example.chat.R.string.calls_ui_45) }
                                .clip(CircleShape)
                                .background(Color(theme.successColor), CircleShape)
                                .clickable { triggerCall() },
                        ) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callAudio,
                                semanticLabel = null,
                                color = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .semantics(mergeDescendants = true) {
// Previous implementation retained:                                     contentDescription = "Delete digit"
                                    contentDescription = context.getString(com.example.chat.R.string.calls_ui_46)
                                    role = Role.Button
                                    // CALLS-R1-59: no actions while the field is empty - the dimmed
                                    // icon is then honestly disabled, not tappable-but-dead.
                                    if (query.isNotEmpty()) {
                                        // Semantic actions are for accessibility only. Pointer input below
                                        // keeps ownership of touch gestures, so a tap deletes only once.
// Previous implementation retained:                                         onClick(label = "Delete one digit") {
                                        onClick(label = context.getString(com.example.chat.R.string.calls_ui_47)) {
                                            if (startingContactId == null) removeDigit()
                                            true
                                        }
// Previous implementation retained:                                         onLongClick(label = "Delete digits repeatedly") {
                                        onLongClick(label = context.getString(com.example.chat.R.string.calls_ui_48)) {
                                            if (startingContactId == null) backspaceRepeating = true
                                            true
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { if (query.isNotEmpty()) removeDigit() },
                                        onLongPress = { backspaceRepeating = true },
                                        onPress = {
                                            awaitRelease()
                                            backspaceRepeating = false
                                        },
                                    )
                                },
                        ) {
                            Icon(
                                Icons.Rounded.Backspace,
                                contentDescription = null,
                                tint = if (query.isEmpty()) {
                                    Color(argbWithAlpha(theme.secondaryTextColor, 0.5))
                                } else {
                                    Color(theme.secondaryTextColor)
                                },
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
        } // Measured compact-height boundary.
    }
}


/** Collapse only after a committed back gesture; cancellation resets the preview, never navigates. */
internal suspend fun completeCallPickerBack(
    progress: kotlinx.coroutines.flow.Flow<Float>, onProgress: (Float) -> Unit, onCommit: () -> Unit,
) {
    try {
        progress.collect { onProgress(if (it.isFinite()) it.coerceIn(0f, 1f) else 0f) }
        onCommit()
    } finally { onProgress(0f) }
}
