package com.example.chat.ui.media

import com.example.chat.ui.kit.ChatyHapticsPort
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.TextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.chat.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.example.chat.ui.chats.detail.viewerImageDismissGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.kit.springInteractiveScale
import com.example.chat.ui.media.icons.ChatyIconType
import com.example.chat.ui.media.icons.ChatyMediaIcon
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Exact port of lib/features/media/ui/audio/chaty_audio_player_screen.dart.
 *
 *  Fullscreen audio player: spinning vinyl disc (14s rotation while playing),
 *  seek bar, transport controls, speed pill selector, queue bottom sheet.
 *  Navigation: Dart popped the fullscreen route via the app bar chevron; the
 *  host supplies [onClose]. Backdrop/queue sheet uses ModalBottomSheet.
 *  MV-R1-053 zone model: art / identity / scrub / transport / secondary.
 *  Play/pause is the sole dominant filled circle; secondary speed pills stay subordinate.
 *  MV-R5-047: no completion dings or queue UI sounds; playback audio is content, not feedback.
 *  UI feedback remains visual, semantic and through the existing haptics port.
 *  MV-R3-005 control order is fixed: art, title, scrubber, transport, secondary speeds.
 *  Keep that order when adding controls; queue/navigation actions belong in the top bar.
 *  Disc animation is 14 seconds per turn at 1x and follows the selected speed (MV-R3-023).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatyAudioPlayerScreen(
    coordinator: MediaPlayerCoordinator,
    onClose: () -> Unit = {},
    onBrowseMedia: (() -> Unit)? = null,
) {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(coordinator) {
        // Previous listener: val listener: () -> Unit = { tick++ }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var attached = true
        val listener: () -> Unit = { handler.post { if (attached) tick++ }; Unit }
        coordinator.addListener(listener)
        onDispose { attached=false;coordinator.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val state = coordinator.state
    // MV-R2-011: UI gate only; coordinator remains owner of native operation ordering.
    var seekInFlight by remember(coordinator) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val narrow = LocalConfiguration.current.screenWidthDp < 340
    // MR/MV narrow policy keeps the existing short-window144dp override.
    // val shortWindow = LocalConfiguration.current.screenHeightDp < 600
    val horizontalGutter = if (narrow) 12.dp else 28.dp
    LaunchedEffect(state.activeItem?.path, state.queueIndex) {
        state.activeItem?.let { track -> view.announceForAccessibility("${track.name}, item ${state.queueIndex + 1} of ${state.queue.size.coerceAtLeast(1)}") }
    }

    // Spinning vinyl disc animation (Dart: 14s AnimationController, repeat
    // while playing, stop otherwise - Animatable.stop() preserves the angle).
    val discAngle = remember { Animatable(0f) }
    val reduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    // MV-R3-023 prior effect omitted playback speed: LaunchedEffect(state.isPlaying, reduceMotion)
    LaunchedEffect(state.isPlaying, reduceMotion, state.speed) {
        if (state.isPlaying && !reduceMotion) {
            discAngle.animateTo(
                targetValue = discAngle.value + 360f,
                // MV-R3-023 prior fixed rate: durationMillis = 14000
                animationSpec = infiniteRepeatable(tween(durationMillis = audioDiscRotationMillis(state.speed), easing = LinearEasing)),
            )
        } else {
            discAngle.stop()
        }
    }

    var showQueue by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler { if (showQueue) showQueue = false else onClose() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    // MV-R5-024 previous tooltip: AudioPlayerTooltip("Close player") {
                    AudioPlayerTooltip("Close") {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Close",
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                },
                // MV-R4-042 prior text: title = { Text("Now Playing", fontSize = 15.sp, fontWeight = FontWeight.W700) },
                title = { Text(stringResource(R.string.media_player_now_playing), fontSize = 15.sp, fontWeight = FontWeight.W700) },
                actions = {
                    // MV-R5-024 previous tooltip: AudioPlayerTooltip("Open queue") {
                    AudioPlayerTooltip("Open audio queue") {
                        IconButton(modifier = Modifier.semantics { contentDescription = "Open audio queue" }, onClick = { showQueue = true }) {
                            ChatyMediaIcon(
                                icon = ChatyIconType.queue,
                                size = 20.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { padding ->
        val track = state.activeItem
        if (track == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                // MV-R5-034 prior empty content: Text("No audio playing")
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // MV-R4-042 prior text: Text("No audio playing")
                    Text(stringResource(R.string.media_player_empty))
                    onBrowseMedia?.let { browse ->
                        androidx.compose.material3.TextButton(
                            onClick = browse, modifier = Modifier.heightIn(min = 48.dp),
                        // MV-R4-042 prior text: ) { Text("Browse media") }
                        ) { Text(stringResource(R.string.media_player_browse)) }
                    }
                }
            }
        } else {
            // MV-R3-029 previous single-pane host (body moved into shared artwork/controls):
            //             Column(
            //                 // MV-R3-003 previous content spacing: modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = horizontalGutter)
            //                 modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = horizontalGutter, vertical = 16.dp)
            //                     .semantics {
            //                         customActions = listOf(
            //                             CustomAccessibilityAction(if (state.isPlaying) "Pause audio" else "Play audio") { scope.launch { coordinator.togglePlayPause() }; true },
            //                             // MV-R3-013 original: CustomAccessibilityAction("Next track") { if (state.hasNext) { scope.launch { coordinator.skipNext() }; true } else false },
            // CustomAccessibilityAction("Next track") { if (state.hasNext) { runCatching { ChatyHapticsPort.selection(view) }; scope.launch { coordinator.skipNext() }; true } else false },
            //                             // MV-R3-013 original: CustomAccessibilityAction("Previous track") { if (state.hasPrevious) { scope.launch { coordinator.skipPrevious() }; true } else false },
            // CustomAccessibilityAction("Previous track") { if (state.hasPrevious) { runCatching { ChatyHapticsPort.selection(view) }; scope.launch { coordinator.skipPrevious() }; true } else false },
            //                             // MV-R2-011 prior custom action retained inactive:
            //                             // CustomAccessibilityAction("Change playback speed") {
            //                             // val speeds = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
            //                             // val next = speeds.firstOrNull { it > state.speed } ?: speeds.first()
            //                             // scope.launch { coordinator.setSpeed(next) }; true
            //                             // },
            //                             CustomAccessibilityAction("Change playback speed") {
            //                                 if (seekInFlight) false else {
            //                                     val speeds = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
            //                                     val next = speeds.firstOrNull { it > state.speed } ?: speeds.first()
            //                                     scope.launch { coordinator.setSpeed(next) }; true
            //                                 }
            //                             },
            //                         )
            //                     },
            //                 // MV-R3-003 prior distribution: verticalArrangement = Arrangement.SpaceEvenly,
            //                 // Explicit zone separation survives short, scrollable viewports.
            //                 verticalArrangement = Arrangement.spacedBy(16.dp),
            //                 horizontalAlignment = Alignment.CenterHorizontally,
            //             ) {
            // Previous disc size: .size(audioDiscSizeDp(LocalConfiguration.current.screenWidthDp, LocalConfiguration.current.screenHeightDp).dp)
            // Previous single-pane end: }
            // MV-R3-029/MV-R4-040: one set of callbacks, alternate layout containers only.
            @Composable fun artwork(discSize: androidx.compose.ui.unit.Dp) {
                // MV-R1-050 passive art only: keep gesture outside rotating disc coordinates.
                Box(Modifier.size(discSize).viewerImageDismissGesture(
                    identity = coordinator,
                    reduceMotion = reduceMotion,
                    canDismiss = { !showQueue && !seekInFlight },
                    onDismiss = onClose,
                ), contentAlignment = Alignment.Center) {
                // Large Spinning Vinyl Disc
                Box(
                    modifier = Modifier
                        .graphicsLayer { rotationZ = discAngle.value }
                        // MV-R3-033 original: .size(if (shortWindow) 144.dp else 240.dp)
                        .size(discSize)
                        // MV-R3-021: decorative art is excluded, transport controls stay operable.
                        .clearAndSetSemantics { }
                        .shadow(
                            elevation = 36.dp,
                            shape = CircleShape,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        )
                        .background(
                            Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.3f to MaterialTheme.colorScheme.surfaceVariant,
                                    0.6f to MaterialTheme.colorScheme.surface,
                                    0.85f to MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    1.0f to MaterialTheme.colorScheme.background,
                                ),
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChatyMediaIcon(icon = ChatyIconType.audio, size = 32.dp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                } // Passive artwork gesture wrapper, controls/scroll host unchanged.
            }
            @Composable fun controls(modifier: Modifier = Modifier) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Track Title & Details
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        track.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.W800,
                            letterSpacing = (-0.3).sp,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        // MV-R5-026 prior decorative separator: "${track.humanReadableSize} • ${track.mimeType.ifBlank { "Audio file" }}",
                        "${track.humanReadableSize}, ${track.mimeType.ifBlank { "Audio file" }}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }

                // Seek Bar & Timers
                Column {
                    val durationMs = state.duration.inWholeMilliseconds
                    var seekDraft by remember(track.path) { mutableStateOf<Float?>(null) }
                    val seekInteraction = remember(track.path) { MutableInteractionSource() }
                    val seekColors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant, thumbColor = MaterialTheme.colorScheme.primary)
                    Slider(
                        value = seekDraft?.coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()) ?: if (durationMs > 0)
                            state.position.inWholeMilliseconds.coerceIn(0, durationMs).toFloat()
                        else 0f,
                        onValueChange = { seekDraft = it },
                        onValueChangeFinished = {
                            // MV-R2-011 previous: seekDraft?.let { target -> scope.launch { coordinator.seek(target.toLong().coerceIn(0L, durationMs).milliseconds) } }
                            if (!seekInFlight) seekDraft?.let { target ->
                                seekInFlight = true
                                scope.launch {
                                    try { coordinator.seek(target.toLong().coerceIn(0L, durationMs).milliseconds) }
                                    finally { seekInFlight = false }
                                }
                            }
                            seekDraft = null
                        },
                        // MV-R2-011 previous: enabled = durationMs > 0,
                        enabled = durationMs > 0 && !seekInFlight,
                        valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                        // MV-R3-038 prior inline colors retained in seekColors above.
                        // colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary,
                        // inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant, thumbColor = MaterialTheme.colorScheme.primary),
                        colors = seekColors,
                        interactionSource = seekInteraction,
                        thumb = { sliderState -> SeekTimeBubbleThumb(seekInteraction, seekColors, durationMs > 0 && !seekInFlight,
                            formatDuration(sliderState.value.toLong().coerceIn(0L, durationMs.coerceAtLeast(0L)).milliseconds)) },
                        // MV-R3-041 prior: modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Audio playback position" },
                        modifier = Modifier.heightIn(min = 48.dp)
                            .audioSeekKeyboard(state.position.inWholeMilliseconds, durationMs, !seekInFlight) { target ->
                                if (!seekInFlight) {
                                    seekInFlight = true
                                    seekDraft = null
                                    scope.launch {
                                        try { coordinator.seek(target.milliseconds) }
                                        finally { seekInFlight = false }
                                    }
                                }
                            }
                            .semantics {
                                contentDescription = "Audio playback position"
                                stateDescription = "${formatDuration(state.position)} of ${formatDuration(state.duration)}. Left and right arrows seek five seconds."
                            },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            formatDuration(state.position),
                            style = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = "tnum", letterSpacing = 0.2.sp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "- ${formatDuration(state.duration - state.position)}",
                            style = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = "tnum", letterSpacing = 0.2.sp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Main Controls Row
                Row(
                    // MV-R3-029 previous modifier: Modifier.fillMaxWidth()
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).widthIn(min = 280.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // MV-R5-024 previous tooltip: AudioPlayerTooltip(if (state.isShuffle) "Turn shuffle off" else "Turn shuffle on") {
                    AudioPlayerTooltip(if (state.isShuffle) "Shuffle on" else "Shuffle off") {
                        // MV-R3-009 prior tint-only button: IconButton(onClick = { coordinator.toggleShuffle() }) {
                        IconButton(modifier = Modifier.clip(CircleShape)
                            .background(if (state.isShuffle) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            // MV-R4-048 label moves from decorative child to the same action owner.
                            .semantics { selected = state.isShuffle; contentDescription = if (state.isShuffle) "Shuffle on" else "Shuffle off" }, onClick = { coordinator.toggleShuffle() }) {
                            // MV-R4-048 previous: Icon(
                            // MV-R4-048 previous: Icons.Rounded.Shuffle,
                            // MV-R4-048 previous: contentDescription = if (state.isShuffle) "Shuffle on" else "Shuffle off",
                            // MV-R4-048 previous: tint = if (state.isShuffle) MaterialTheme.colorScheme.primary
                            // MV-R4-048 previous: else (MaterialTheme.colorScheme.onSurfaceVariant),
                            // MV-R4-048 previous: )
                            ChatyMediaIcon(ChatyIconType.shuffle, color = if (state.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AudioPlayerTooltip("Previous track") {
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = "Previous track" },
                            // MV-R3-013 original: onClick = { scope.launch { coordinator.skipPrevious() } },
                            onClick = { runCatching { ChatyHapticsPort.selection(view) }; scope.launch { coordinator.skipPrevious() } },
                            enabled = state.hasPrevious,
                        ) {
                            ChatyMediaIcon(
                                icon = ChatyIconType.seekBackward,
                                size = 24.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    AudioPlayerTooltip(if (state.isPlaying) "Pause audio" else "Play audio") {
                        Box(
                            Modifier.springInteractiveScale(0.94f).semantics { contentDescription = if (state.isPlaying) "Pause audio" else "Play audio"; role = Role.Button }.clickable {
                                ChatyHapticsPort.threshold(view)
                                scope.launch { coordinator.togglePlayPause() }
                            },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .shadow(
                                        elevation = 18.dp,
                                        shape = CircleShape,
                                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    )
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                ChatyMediaIcon(
                                    icon = if (state.isPlaying) ChatyIconType.pause else ChatyIconType.play,
                                    size = 28.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                    AudioPlayerTooltip("Next track") {
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = "Next track" },
                            // MV-R3-013 original: onClick = { scope.launch { coordinator.skipNext() } },
                            onClick = { runCatching { ChatyHapticsPort.selection(view) }; scope.launch { coordinator.skipNext() } },
                            enabled = state.hasNext,
                        ) {
                            ChatyMediaIcon(
                                icon = ChatyIconType.seekForward,
                                size = 24.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    // MV-R5-024 previous tooltip: AudioPlayerTooltip("Change repeat mode") {
                    AudioPlayerTooltip("Repeat ${state.repeatMode.name}") {
                        // MV-R3-009 prior tint-only button: IconButton(onClick = { coordinator.cycleRepeatMode() }) {
                        IconButton(modifier = Modifier.clip(CircleShape)
                            .background(if (state.repeatMode != ChatyRepeatMode.off) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .semantics { selected = state.repeatMode != ChatyRepeatMode.off; contentDescription = "Repeat ${state.repeatMode.name}" }, onClick = { coordinator.cycleRepeatMode() }) {
                            // MV-R4-048 previous: Icon(
                            // MV-R4-048 previous: if (state.repeatMode == ChatyRepeatMode.one) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            // MV-R4-048 previous: contentDescription = "Repeat ${state.repeatMode.name}",
                            // MV-R4-048 previous: tint = if (state.repeatMode != ChatyRepeatMode.off) MaterialTheme.colorScheme.primary
                            // MV-R4-048 previous: else (MaterialTheme.colorScheme.onSurfaceVariant),
                            // MV-R4-048 previous: )
                            ChatyMediaIcon(if (state.repeatMode == ChatyRepeatMode.one) ChatyIconType.repeatOne else ChatyIconType.repeat,
                                color = if (state.repeatMode != ChatyRepeatMode.off) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // MV-R3-003: status and speed are one secondary zone, not new primary choices.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // MV-R5-026 prior decorative separator: Text("Shuffle ${if (state.isShuffle) "on" else "off"} · Repeat ${state.repeatMode.name}", style = MaterialTheme.typography.labelMedium)
                Text("Shuffle ${if (state.isShuffle) "on" else "off"}, repeat ${state.repeatMode.name}", style = MaterialTheme.typography.labelMedium)
                Text("Playback speed ${state.speed}x", style = MaterialTheme.typography.labelMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                // Speed Pill Row
                // MV-R2-011 previous: SpeedSelector(state, isDark) { s ->
                SpeedSelector(state, isDark, enabled = !seekInFlight) { s ->
                    ChatyHapticsPort.selection(view)
                    scope.launch { coordinator.setSpeed(s) }
                }
                } // Secondary zone.
                }
            }
            val configuration = LocalConfiguration.current
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.fillMaxSize().padding(padding)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(if (state.isPlaying) "Pause audio" else "Play audio") { scope.launch { coordinator.togglePlayPause() }; true },
                            // MV-R3-013 original: CustomAccessibilityAction("Next track") { if (state.hasNext) { scope.launch { coordinator.skipNext() }; true } else false },
CustomAccessibilityAction("Next track") { if (state.hasNext) { runCatching { ChatyHapticsPort.selection(view) }; scope.launch { coordinator.skipNext() }; true } else false },
                            // MV-R3-013 original: CustomAccessibilityAction("Previous track") { if (state.hasPrevious) { scope.launch { coordinator.skipPrevious() }; true } else false },
CustomAccessibilityAction("Previous track") { if (state.hasPrevious) { runCatching { ChatyHapticsPort.selection(view) }; scope.launch { coordinator.skipPrevious() }; true } else false },
                            // MV-R2-011 prior custom action retained inactive:
                            // CustomAccessibilityAction("Change playback speed") {
                            // val speeds = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
                            // val next = speeds.firstOrNull { it > state.speed } ?: speeds.first()
                            // scope.launch { coordinator.setSpeed(next) }; true
                            // },
                            CustomAccessibilityAction("Change playback speed") {
                                if (seekInFlight) false else {
                                    val speeds = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
                                    val next = speeds.firstOrNull { it > state.speed } ?: speeds.first()
                                    scope.launch { coordinator.setSpeed(next) }; true
                                }
                            },
                        )
                    }
            ) {
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                if (audioUseLandscapePanes(isLandscape, maxWidth.value, maxHeight.value)) {
                    Row(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                        // Weight fractions apply after safe Scaffold padding. Art cannot draw into controls.
                        androidx.compose.foundation.layout.BoxWithConstraints(
                            Modifier.weight(0.4f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.Center,
                        ) {
                            val disc = audioLandscapeDiscSizeDp(maxWidth.value, maxHeight.value,
                                audioDiscSizeDp(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()).dp
                            artwork(disc)
                        }
                        controls(Modifier.weight(0.6f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp))
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = horizontalGutter, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        artwork(audioDiscSizeDp(configuration.screenWidthDp, configuration.screenHeightDp).dp)
                        controls(Modifier.fillMaxWidth())
                    }
                }
            }

        }
    }

    if (showQueue) {
        ModalBottomSheet(tonalElevation = 0.dp,
            onDismissRequest = { showQueue = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = com.example.chat.ui.kit.ChatyModalShape,
        ) {
            val sheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.6f
            var queueQuery by remember { mutableStateOf("") }
            val queueItems = remember(state.queue, queueQuery) { state.queue.mapIndexed { index, file -> index to file }.filter { (_, file) -> queueQuery.isBlank() || file.name.contains(queueQuery, ignoreCase = true) } }
            val queueTitle = stringResource(R.string.media_player_queue_title)
            val queueCount = pluralStringResource(R.plurals.media_player_queue_tracks, queueItems.size, queueItems.size)
            Column(modifier = Modifier.height(sheetHeight)) {
//                 Spacer(Modifier.height(12.dp))
//                 Box(
//                     modifier = Modifier
//                         .align(Alignment.CenterHorizontally)
//                         .size(width = 36.dp, height = 4.dp)
//                         .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
//                 )
//                 Spacer(Modifier.height(14.dp))
                Text(
                    // MV-R4-042 prior text: "Play Queue",
                    stringResource(R.string.media_player_queue_title),
                    fontWeight = FontWeight.W700,
                    fontSize = 16.sp,
                    // MV-R3-012: main title already retains its -0.3sp optical tracking.
                    letterSpacing = (-0.1).sp,
                    // MV-R4-042 prior modifier: modifier = Modifier.align(Alignment.CenterHorizontally),
                    modifier = Modifier.align(Alignment.CenterHorizontally).semantics {
                        contentDescription = "$queueTitle, $queueCount"
                    },
                )
                // MV-R5-028: visible, labeled dismissal independent of sheet scrim/gesture.
                androidx.compose.material3.TextButton(
                    onClick = { showQueue = false },
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp).heightIn(min = 48.dp),
                // MV-R4-042 prior text: ) { Text("Close queue") }
                ) { Text(stringResource(R.string.media_player_queue_close)) }
                Spacer(Modifier.height(10.dp))
                if (state.queue.size > 20) TextField(
                    // MV-R4-042 prior text: value = queueQuery, onValueChange = { queueQuery = it }, label = { Text("Search audio queue") },
                    value = queueQuery, onValueChange = { queueQuery = it }, label = { Text(stringResource(R.string.media_player_queue_search)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                // MV-R4-042 prior text: if (queueItems.isEmpty()) Text("No matching tracks", modifier = Modifier.padding(16.dp))
                if (queueItems.isEmpty()) Text(stringResource(R.string.media_player_queue_no_matches), modifier = Modifier.padding(16.dp))
                LazyColumn(Modifier.semantics { collectionInfo = CollectionInfo(queueItems.size, 1) }) {
                    items(queueItems.size, key = { queueItems[it].first }) { filteredIndex ->
                        val (index, item) = queueItems[filteredIndex]
                        val isCurrent = index == state.queueIndex
                        ListItem(
                            leadingContent = {
                                // MV-R4-048 previous: ChatyIcon(
                                // MV-R4-048 previous: icon = if (isCurrent) ChatyIconId.playArrow else ChatyIconId.musicNote,
                                // MV-R4-048 previous: color = if (isCurrent) MaterialTheme.colorScheme.primary else null,
                                // MV-R4-048 previous: )
                                ChatyMediaIcon(icon = if (isCurrent) ChatyIconType.play else ChatyIconType.audio,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            },
                            headlineContent = {
                                Text(
                                    item.name,
                                    fontWeight = if (isCurrent) FontWeight.W700 else FontWeight.W500,
                                )
                            },
                            supportingContent = { Text(item.humanReadableSize) },
                            // MV-R3-036 previous: modifier = androidx.compose.ui.Modifier.semantics {
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics {
                                selected = isCurrent
                                stateDescription = if (isCurrent) "Now playing, item ${index + 1} of ${state.queue.size}" else "Item ${index + 1} of ${state.queue.size}"
                                role = Role.Button
                            }.clickable {
                                // MV-R3-013: selection feedback only, no optimistic playback claim.
                                runCatching { ChatyHapticsPort.selection(view) }
                                scope.launch { coordinator.playTrack(item, queue = state.queue) }
                                showQueue = false
                            },
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSelector(state: MediaPlaybackState, isDark: Boolean, enabled: Boolean = true, onSelect: (Double) -> Unit) {
    val speeds = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.horizontalScroll(rememberScrollState())
            .semantics { contentDescription = "Playback speed" },
    ) {
        speeds.forEachIndexed { index, speed ->
            SegmentedButton(
                selected = kotlin.math.abs(state.speed - speed) < 0.01,
                enabled = enabled,
                onClick = { onSelect(speed) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = speeds.size),
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp)
                    .semantics { contentDescription = "Playback speed ${speed}x" },
            ) { Text("${speed}x") }
        }
    }
}

// MV-R2-049 prior complete custom-pill implementation retained inactive.
// @Composable
// // MV-R2-011 previous: private fun SpeedSelector(state: MediaPlaybackState, isDark: Boolean, onSelect: (Double) -> Unit) {
// private fun SpeedSelector(state: MediaPlaybackState, isDark: Boolean, enabled: Boolean = true, onSelect: (Double) -> Unit) {
//     val speeds = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
//     Row(
//         modifier = Modifier
//             .horizontalScroll(rememberScrollState())
//             .selectableGroup()
//             .semantics { contentDescription = "Playback speed" }
//             .clip(RoundedCornerShape(20.dp))
//             .background(MaterialTheme.colorScheme.surfaceVariant)
//             .padding(horizontal = 10.dp, vertical = 4.dp),
//     ) {
//         speeds.forEach { s ->
//             val isSelected = kotlin.math.abs(state.speed - s) < 0.01
//             // MV-R3-018: observe actual pill presses without changing speed selection.
//             val pressSource = remember { MutableInteractionSource() }
//             val pressed by pressSource.collectIsPressedAsState()
//             // MV-R3-011 prior target: Box(Modifier.heightIn(min = 48.dp).semantics { selected = isSelected; stateDescription = if (isSelected) "Selected" else "Not selected"; contentDescription = "Playback speed ${s}x"; role = Role.RadioButton }.springInteractiveScale(0.95f).clickable { onSelect(s) }, contentAlignment = Alignment.Center) {
//             // MV-R3-018 prior interaction: Box(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { selected = isSelected; stateDescription = if (isSelected) "Selected" else "Not selected"; contentDescription = "Playback speed ${s}x"; role = Role.RadioButton }.springInteractiveScale(0.95f).clickable { onSelect(s) }, contentAlignment = Alignment.Center) {
//             // MV-R2-011 previous: Box(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { selected = isSelected; stateDescription = if (isSelected) "Selected" else "Not selected"; contentDescription = "Playback speed ${s}x"; role = Role.RadioButton }.springInteractiveScale(0.95f).clickable(interactionSource = pressSource, indication = LocalIndication.current) { onSelect(s) }, contentAlignment = Alignment.Center) {
//             Box(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { if (!enabled) disabled(); selected = isSelected; stateDescription = if (isSelected) "Selected" else "Not selected"; contentDescription = "Playback speed ${s}x"; role = Role.RadioButton }.springInteractiveScale(0.95f).clickable(enabled = enabled, interactionSource = pressSource, indication = LocalIndication.current) { onSelect(s) }, contentAlignment = Alignment.Center) {
//                 Box(
//                     modifier = Modifier
//                         .clip(RoundedCornerShape(16.dp))
//                         .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
//                         .background(if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
//                         .padding(horizontal = 10.dp, vertical = 5.dp),
//                 ) {
//                     Text(
//                         "${s}x",
//                         fontSize = 12.sp,
//                         fontWeight = if (isSelected) FontWeight.W800 else FontWeight.W500,
//                         color = if (isSelected) MaterialTheme.colorScheme.onPrimary
//                         else (MaterialTheme.colorScheme.onSurfaceVariant),
//                     )
//                 }
//             }
//         }
//     }
// }

// Source-preservation review: inactive baseline statements retained verbatim.
// LaunchedEffect(state.isPlaying, reduceMotion) {
// animationSpec = infiniteRepeatable(tween(durationMillis = 14000, easing = LinearEasing)),
