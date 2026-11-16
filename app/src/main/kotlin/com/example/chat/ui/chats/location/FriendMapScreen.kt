package com.example.chat.ui.chats.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.chat.ui.kit.ChatyConfirmationDialog
import com.example.chat.ui.kit.ChatyDropdownMenu
import com.example.chat.ui.kit.ChatyModalShape
import com.example.chat.ui.kit.ChatyDropdownMenuItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Snap Map-style friends map, FRONTEND ONLY. The Chaty backend has no location-sharing
 * endpoints yet, so this screen is an honest preview: it shows the map, the layout and the
 * controls, and says clearly that friend locations are not connected. It renders NO friend
 * pins (there is no real friend data and none is invented), publishes NO location and keeps
 * every sharing control visibly disabled. The user's own pin is local-only.
 *
 * The existing per-chat location flow (MapViewerScreen / LocationSendGate) is untouched.
 * Opening this screen starts map tile requests, not GPS; GPS runs only on an explicit
 * locate tap. Provider attribution and the online-service notice remain in the map.
 */
@Composable
fun FriendMapScreen(
    context: Context,
    onDismiss: () -> Unit,
    onAttributionLink: (Uri) -> Unit,
    currentLocationEnabled: Boolean = true,
    onMapReady: () -> Unit = {},
) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val systemDark = (LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val mapColors = if (systemDark) darkColorScheme() else lightColorScheme()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var mapActive by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var mapView by remember { mutableStateOf<GeoLibreLocationMapView?>(null) }
    var requestedBefore by rememberSaveable { mutableStateOf(false) }
    var ownPin by remember { mutableStateOf<FriendMapOwnPin?>(null) }
    // MAP-R1-010: renderer and location status are modeled independently (prior: one shared status string).
    // Prior: var status by remember { mutableStateOf("Loading map...") }
    var rendererStatus by remember { mutableStateOf("Loading map...") }
    var locationStatus by remember { mutableStateOf("") }
    // MAP-R1-024/046: a style selection stays pending until the page confirms it.
    var stylePending by remember { mutableStateOf<String?>(null) }
    // MAP-R1-045: set when the renderer reports unavailable; drives the Retry control.
    var rendererFailed by remember { mutableStateOf(false) }
    // MAP-R1-045: bumped to recreate the renderer on Retry.
    var mapGeneration by remember { mutableIntStateOf(0) }
    // MAP-R1-001: latest best-effort camera snapshot (zoom,bearing,pitch,lat,lng).
    var cameraSnapshot by rememberSaveable { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }

    var buildingsEnabled by remember { mutableStateOf(false) }
    val buildingsStateDescription = if (buildingsEnabled) "3D buildings enabled" else "2D map"
    var rendererReady by remember { mutableStateOf(false) }
    var layersOpen by remember { mutableStateOf(false) }
    var selectedStyle by remember { mutableStateOf(if (systemDark) "dark" else "positron") }
    // MAP-R1-003: an explicit style choice survives system theme changes.
    var styleChosenByUser by remember { mutableStateOf(false) }
    LaunchedEffect(systemDark) {
        if (!styleChosenByUser) selectedStyle = if (systemDark) "dark" else "positron"
        // MAP-R1-004: 3D buildings reset to off on every theme/style change by design, so the toggle
        // state always matches the freshly loaded style. Documented, not a bug.
        buildingsEnabled = false
        mapView?.setTheme(systemDark)
        mapView?.selectStyle(selectedStyle)
    }
    var rationale by remember { mutableStateOf(false) }
    var locateJob by remember { mutableStateOf<Job?>(null) }

    // The only connection state that exists until the backend phase lands.
    val connection: FriendMapConnection = FriendMapConnection.NotConnected

    val activity = remember(context) { context.findFriendMapActivity() }
    val manager = remember(context) { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun stopLocate() { locateJob?.cancel(); locateJob = null; locating = false }
    fun acquireCurrentFix() {
        stopLocate()
        locating = true
        locationStatus = "Getting your location for this map only. Nothing is shared."
        locateJob = scope.launch {
            try {
                // Recheck immediately before the API call; never use a cached last-known fix.
                val precise = granted(Manifest.permission.ACCESS_FINE_LOCATION)
                check(precise || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) { "Location permission is not available." }
                check(LocationManagerCompat.isLocationEnabled(manager)) { "Location services are off." }
                val provider = when {
                    !precise && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    // MAP-R2-073: coarse-only never falls through to GPS (previous fallthrough kept below for precise).
                    !precise -> error("Approximate location is unavailable on this device.")
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> error("No location provider is available.")
                }
                val fix = withTimeout(20_000) { friendMapCurrentLocationFix(context, manager, provider) }
                    ?: error("No current location was found. Try again.")
                val ageMs = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000
                check(ageMs in 0..15_000) { "Location is too old. Try again." }
                check(fix.hasAccuracy() && fix.accuracy.isFinite() && fix.accuracy >= 0f) { "Location accuracy is unavailable." } // MAP-R2-077
                val pin = FriendMapOwnPin(LocationCoordinate(fix.latitude, fix.longitude), precise,
                    accuracyMeters = fix.accuracy.toDouble(), acquiredAtElapsedMs = fix.elapsedRealtimeNanos / 1_000_000)
                ownPin = pin
                mapView?.showPin(pin.coordinate)
                locationStatus = if (precise) "Your location is shown on this map only. Nothing is shared." else "Approximate location shown on this map only. Nothing is shared."
            } catch (timeout: TimeoutCancellationException) {
                locationStatus = "Location timed out. Try again."
            } catch (cancelled: CancellationException) {
                locationStatus = "Location request stopped."
                throw cancelled
            } catch (error: Exception) {
                locationStatus = error.message ?: "Location is unavailable."
            } finally { locating = false }
        }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) acquireCurrentFix()
        } else locationStatus = "Location permission was denied. The map still works without it."
    }
    fun requestPermission() {
        requestedBefore = true
        permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    fun locate() {
        val access = ForegroundLocationAccess(
            fineGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            coarseGranted = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            requestedBefore = requestedBefore,
            shouldShowRationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_COARSE_LOCATION) || ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) } == true,
            locationServicesEnabled = LocationManagerCompat.isLocationEnabled(manager),
        )
        when (nextLocateAction(access)) {
            LocateAction.usePreciseFix, LocateAction.useApproximateFix -> acquireCurrentFix()
            LocateAction.requestForeground -> requestPermission()
            LocateAction.explainRationale -> rationale = true
            LocateAction.offerAppSettings -> {
                locationStatus = "Permission is unavailable. You can allow location in app settings."
                // MAP-R2-070: guarded so a missing settings handler cannot crash.
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
            }
            LocateAction.offerLocationSettings -> {
                locationStatus = "Location services are off. Turn them on, then tap Current location again."
                runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } // MAP-R2-070
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // MAP-R1-002/022: pause the live page instead of tearing the map down on every pause.
                // Prior: Lifecycle.Event.ON_PAUSE -> { rendererReady = false; layersOpen = false; stopLocate(); mapActive = false; mapView?.disposeMap(); mapView = null }
                Lifecycle.Event.ON_PAUSE -> {
                    layersOpen = false
                    stopLocate()
                    // MAP-R1-001: capture a best-effort camera snapshot before pausing.
                    mapView?.snapshotCamera { saved -> if (saved != null) cameraSnapshot = saved }
                    mapView?.pauseMap()
                }
                // Prior: Lifecycle.Event.ON_RESUME -> { mapActive = true }
                Lifecycle.Event.ON_RESUME -> { mapActive = true; mapView?.resumeMap() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stopLocate(); mapView?.disposeMap(); mapView = null }
    }
    androidx.activity.compose.BackHandler { stopLocate(); onDismiss() }
    MaterialTheme(colorScheme = mapColors) {
    Dialog(onDismissRequest = { stopLocate(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Surface(modifier = Modifier.fillMaxSize(), color = mapColors.background, contentColor = mapColors.onBackground) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FriendMapControlButton(onClick = { stopLocate(); onDismiss() }, enabled = true, contentDescription = "Back", containerColor = mapColors.secondaryContainer, contentColor = mapColors.onSecondaryContainer, disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant, icon = Icons.Rounded.ChevronLeft)
                // MAP-R1-044: bounded flexible title so both actions stay reachable at large font scale.
                Text("Friends map", modifier = Modifier.weight(1f, fill = false).padding(12.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(48.dp))
            }
            Box(Modifier.fillMaxWidth().weight(1f).semantics { paneTitle = "Friends map preview" }) { // MAP-R4-168
            if (mapActive) {
                // MAP-R1-045: keying on mapGeneration lets the Retry action recreate a failed renderer.
                key(mapGeneration) {
                AndroidView(
                    factory = { viewContext -> GeoLibreLocationMapView(viewContext,
                        // Friend pins are never fabricated; map taps place nothing in this preview.
                        onPinSelected = { },
                        onUnavailable = { rendererStatus = it; rendererFailed = true }, onAttributionLink = onAttributionLink,
                        onMapReady = {
                            rendererReady = true
                            mapView?.selectStyle(selectedStyle)
                            if (buildingsEnabled) mapView?.control("toggleBuildings")
                            // MAP-R1-001: restore the last camera snapshot after the page is ready.
                            mapView?.restoreCamera(cameraSnapshot)
                            if (rendererStatus == "Loading map...") rendererStatus = ""
                            onMapReady()
                        }, nativeLayers = true,
                        systemDark = systemDark, onStyleChanged = { selectedStyle = it; styleChosenByUser = true; stylePending = null }, onViewChanged = { enabled ->
                            buildingsEnabled = enabled
                            // MAP-R1-047: say what 3D means here; missing building data is not an error.
                            rendererStatus = if (enabled) "3D buildings show where height data exists. Not satellite imagery." else ""
                        },
                    ).also { mapView = it; ownPin?.let { pin -> it.showPin(pin.coordinate) } } },
                    modifier = Modifier.fillMaxSize(),
                    update = { it.setTheme(systemDark) },
                )
                }
            }
            // Honest top chip: friend sharing is not live.
            Surface(
                color = mapColors.surfaceContainerHigh,
                contentColor = mapColors.onSurface,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp), // MAP-R1-038: redundant contentDescription removed (Text is read once)
                // .semantics { contentDescription = FriendMapCopy.MAP_STATUS_CHIP },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.eyeOff, semanticLabel = null, modifier = Modifier.size(14.dp), color = mapColors.onSurfaceVariant)
                    Spacer(Modifier.size(6.dp))
                    Text(FriendMapCopy.MAP_STATUS_CHIP, fontSize = 12.sp, fontWeight = FontWeight.W600, color = mapColors.onSurfaceVariant)
                }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FriendMapControlButton(onClick = { mapView?.control("zoomIn") }, enabled = rendererReady, contentDescription = "Zoom in", containerColor = mapColors.secondaryContainer, contentColor = mapColors.onSecondaryContainer, disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant, icon = Icons.Rounded.Add)
                FriendMapControlButton(onClick = { mapView?.control("zoomOut") }, enabled = rendererReady, contentDescription = "Zoom out", containerColor = mapColors.secondaryContainer, contentColor = mapColors.onSecondaryContainer, disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant, icon = Icons.Rounded.Remove)
                FriendMapControlButton(onClick = { mapView?.control("toggleBuildings") }, enabled = rendererReady, contentDescription = "Toggle 3D buildings", stateDescription = buildingsStateDescription, containerColor = mapColors.secondaryContainer, contentColor = mapColors.onSecondaryContainer, disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant, icon = Icons.Rounded.ViewInAr)
                Box {
                FriendMapControlButton(onClick = { layersOpen = true }, enabled = rendererReady, contentDescription = "Map layers", containerColor = mapColors.secondaryContainer, contentColor = mapColors.onSecondaryContainer, disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant, icon = Icons.Rounded.Layers)
                ChatyDropdownMenu(expanded = layersOpen, onDismissRequest = { layersOpen = false }, containerColor = mapColors.surfaceContainerHigh) {
                    listOf("liberty" to "Streets", "positron" to "Light", "dark" to "Dark", "bright" to "Bright", "fiord" to "Fiord").forEach { (style, label) ->
                        ChatyDropdownMenuItem(
                            text = { Text(label, color = mapColors.onSurface, fontSize = 15.5.sp, fontWeight = FontWeight.W600) },
                            trailingIcon = { if (selectedStyle == style) com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.check, semanticLabel = "Selected") },
                            onClick = {
                                layersOpen = false
                                // MAP-R1-024/046: mark the style pending until the page confirms the change.
                                if (style != selectedStyle) { stylePending = style; mapView?.selectStyle(style) }
                            },
                        )
                    }
                }
                }
            }
            // Bottom panel: Snap Map-style friends tray, honest not-connected content.
            val panelMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
            Surface(
                color = mapColors.surfaceContainerHigh,
                contentColor = mapColors.onSurface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = panelMaxHeight),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // MAP-R4-197: drag-handle look removed (tray is not draggable); commented out, not deleted.
                    // Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).background(mapColors.onSurfaceVariant.copy(alpha = 0.4f), CircleShape))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Friends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.W700)
                            // MAP-R1-048: was "0 sharing with you" while not connected
                            Text("Friend sharing not connected", fontSize = 12.5.sp, color = mapColors.onSurfaceVariant)
                        }
                        FriendMapControlButton(
                            onClick = { locate() }, enabled = currentLocationEnabled && !locating,
                            contentDescription = "Show my location on this map only",
                            containerColor = mapColors.primary, contentColor = mapColors.onPrimary,
                            disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant,
                        ) { tint ->
                            if (locating) FriendMapLocatingSpinner(tint)
                            else Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = tint)
                        }
                    }
                    // MAP-R1-010: renderer and location status are announced independently.
                    // Prior: if (status.isNotBlank()) { ... Text(status, ...) }
                    if (rendererStatus.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        // MAP-R1-034: polite live region so state changes are announced once
                        Text(rendererStatus, fontSize = 12.sp, color = mapColors.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                    }
                    if (locationStatus.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(locationStatus, fontSize = 12.sp, color = mapColors.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                    }
                    // MAP-R1-024/046: pending style is shown until the page confirms the change.
                    if (stylePending != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("Switching map style...", fontSize = 12.sp, color = mapColors.onSurfaceVariant)
                    }
                    // MAP-R1-035: textual coordinate summary for the local-only pin.
                    ownPin?.let { pin ->
                        Spacer(Modifier.height(6.dp))
                        Text("Your pin: ${"%.5f".format(java.util.Locale.US, pin.coordinate.latitude)}, ${"%.5f".format(java.util.Locale.US, pin.coordinate.longitude)}", fontSize = 12.sp, color = mapColors.onSurfaceVariant)
                    }
                    // MAP-R1-045: explicit local retry when the renderer reports unavailable.
                    if (rendererFailed) {
                        Spacer(Modifier.height(6.dp))
                        FriendMapDialogAction("Retry map", color = mapColors.primary) {
                            mapView?.disposeMap()
                            mapView = null
                            rendererReady = false
                            rendererFailed = false
                            rendererStatus = "Loading map..."
                            mapGeneration += 1
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    when (connection) {
                        FriendMapConnection.NotConnected -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.groups, semanticLabel = null, modifier = Modifier.size(36.dp), color = mapColors.onSurfaceVariant)
                                Spacer(Modifier.size(12.dp))
                                Column {
                                    Text(FriendMapCopy.NOT_CONNECTED_TITLE, fontWeight = FontWeight.W600, fontSize = 14.5.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(FriendMapCopy.NOT_CONNECTED_BODY, fontSize = 12.5.sp, color = mapColors.onSurfaceVariant, lineHeight = 17.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = mapColors.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.eyeOff, semanticLabel = null, modifier = Modifier.size(20.dp), color = mapColors.onSurfaceVariant)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your location: not shared", fontWeight = FontWeight.W600, fontSize = 14.sp)
                            Text(FriendMapCopy.OWN_LOCATION_CAPTION, fontSize = 12.sp, color = mapColors.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Who could see you (not active yet)", fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = mapColors.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    FriendMapVisibilityPreview.entries.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth().alpha(0.55f).semantics(mergeDescendants = true) { stateDescription = "Not available until the Chaty backend is connected" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(20.dp).clip(CircleShape).border(BorderStroke(2.dp, mapColors.outline), CircleShape))
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(choice.label, fontSize = 14.sp, fontWeight = FontWeight.W600)
                                Text(choice.description, fontSize = 12.sp, color = mapColors.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(FriendMapCopy.VISIBILITY_INACTIVE_CAPTION, fontSize = 12.sp, color = mapColors.onSurfaceVariant, lineHeight = 16.sp)
                    Spacer(Modifier.height(if (isLandscape) 16.dp else 8.dp))
                }
            }
            }
        }
    }
    }
    if (rationale) {
        ChatyConfirmationDialog(
            shape = ChatyModalShape,
            onDismissRequest = { rationale = false },
            title = { Text("Show your location on this map?", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Chaty needs foreground location access only to show your own position on this map. Approximate access is accepted. Nothing is shared with anyone.", fontSize = 14.sp) },
            confirmButton = { FriendMapDialogAction("Continue", color = MaterialTheme.colorScheme.primary) { rationale = false; requestPermission() } },
            dismissButton = { FriendMapDialogAction("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant) { rationale = false } },
        )
    }
    }
}

/**
 * Custom circular map control built from Chaty token colors. Replaces the Material
 * FilledIconButton/IconButton chrome on this screen: same 48dp circle target, same
 * tokens, no Material default control styling.
 */
@Composable
private fun FriendMapControlButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    disabledContainerColor: Color,
    disabledContentColor: Color,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
    content: @Composable (tint: Color) -> Unit,
) {
    val tint = if (enabled) contentColor else disabledContentColor
    Box(
        modifier.size(48.dp)
            .semantics {
                this.contentDescription = contentDescription
                if (stateDescription != null) this.stateDescription = stateDescription
            }
            .clip(CircleShape)
            .background(if (enabled) containerColor else disabledContainerColor)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(tint) }
}

@Composable
private fun FriendMapControlButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    disabledContainerColor: Color,
    disabledContentColor: Color,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
    icon: ImageVector,
) = FriendMapControlButton(
    onClick, enabled, contentDescription, containerColor, contentColor,
    disabledContainerColor, disabledContentColor, modifier, stateDescription,
) { tint -> Icon(icon, contentDescription = null, tint = tint) }

/** Minimal token-colored indeterminate arc; replaces the Material progress control. */
@Composable
private fun FriendMapLocatingSpinner(color: Color) {
    // Use the existing reactive app/system reduced-motion policy, not a remembered Settings snapshot.
    val animationsOff = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val angle = if (animationsOff) 0f else {
        val transition = rememberInfiniteTransition(label = "friendMapLocate")
        val animatedAngle by transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
            label = "sweep",
        )
        animatedAngle
    }
    // MAP-R1-033: expose an indeterminate progress state
    Canvas(Modifier.size(24.dp).semantics { progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate }) {
        drawArc(color = color, startAngle = angle, sweepAngle = 270f, useCenter = false, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Text action with Chaty styling for custom dialogs; replaces Material TextButton chrome. */
@Composable
private fun FriendMapDialogAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label, color = color, fontSize = 15.sp, fontWeight = FontWeight.W600,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), // MAP-R1-040: 48dp target
    )
}

private tailrec fun Context.findFriendMapActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findFriendMapActivity()
    else -> null
}

@android.annotation.SuppressLint("MissingPermission")
private suspend fun friendMapCurrentLocationFix(context: Context, manager: LocationManager, provider: String): Location? =
    suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }
