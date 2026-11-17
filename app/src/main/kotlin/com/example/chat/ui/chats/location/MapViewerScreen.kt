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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.*
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.ChatyDropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Dedicated GeoLibre viewer. Explicitly opening this screen starts map tile requests, not GPS.
 * Provider-attribution visibility remains unchanged in this patch. Licence/provenance notices
 * stay in assets/location-map/THIRD_PARTY_NOTICES.txt; no licence waiver is claimed.
 * MAP-R1-030: no offline-ready or cache-coverage state is exposed; styles and tiles need the
 * network and nothing in this screen claims otherwise. No Send path exists. */
@Composable
fun MapViewerScreen(
    context: Context,
    // Capture stable identifiers; currentScope must read the live owner, not a stale closure.
    accountId: String,
    conversationId: String,
    currentScope: () -> Pair<String?, String?>,
    onDismiss: () -> Unit,

    onAttributionLink: (Uri) -> Unit,
    initialCoordinate: LocationCoordinate? = null,
    currentLocationEnabled: Boolean = true,
    onMapReady: () -> Unit = {},
) {
    // Landscape: keep the Locate button left of the 56dp-wide map controls column so they never overlap.
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val systemDark = (LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    // AUDIT-MAP-2: a fresh ColorScheme every recomposition made MaterialTheme invalidate the whole
    // subtree on any status change. Prior: val mapColors = if (systemDark) darkColorScheme() else lightColorScheme()
    val mapColors = remember(systemDark) { if (systemDark) darkColorScheme() else lightColorScheme() }
    val controlColors = IconButtonDefaults.filledIconButtonColors(containerColor = mapColors.secondaryContainer, contentColor = mapColors.onSecondaryContainer, disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant)
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var mapActive by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var mapView by remember { mutableStateOf<GeoLibreLocationMapView?>(null) }
    var requestedBefore by rememberSaveable { mutableStateOf(false) }
    // MAP-R1-005: the draft is keyed on initialCoordinate so a changed initial coordinate starts a fresh draft.
    // Prior: var draft by remember { mutableStateOf<LocationDraft?>(initialCoordinate?.let { LocationDraft(it, LocationSource.manualPin) }) }
    var draft by remember(initialCoordinate) { mutableStateOf(initialCoordinate?.let { LocationDraft(it, LocationSource.manualPin) }) }
    // MAP-R1-010: renderer and location status are modeled independently so they no longer overwrite each other.
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
    val capturedScope = remember { accountId to conversationId }
    val liveScope by rememberUpdatedState(currentScope)

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
    var friendsMapOpen by remember { mutableStateOf(false) }
    // MAP-R1-025 / MAP-R5-238: pause this renderer while the friends map dialog owns the screen,
    // so two live WebView renderers never run at once. Resume only while the host is resumed.
    DisposableEffect(friendsMapOpen, mapView) {
        val pausedForFriends = friendsMapOpen
        if (pausedForFriends) mapView?.pauseMap()
        onDispose { if (pausedForFriends && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView?.resumeMap() }
    }
    val gate = remember { LocationSendGate() }
    // MAP-R1-005: a re-keyed initial coordinate replaces the gate selection and the on-map pin.
    LaunchedEffect(initialCoordinate) {
        gate.cancel() // A null replacement must invalidate an older confirmed pin too.
        initialCoordinate?.let { coordinate ->
            gate.select(LocationDraft(coordinate, LocationSource.manualPin))
            mapView?.showPin(coordinate)
        }
    }

    val activity = remember(context) { context.findMapActivity() }
    val manager = remember(context) { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun select(value: LocationDraft) { gate.select(value); draft = value }
    fun stopLocate() { locateJob?.cancel(); locateJob = null; locating = false }
    fun acquireCurrentFix() {
        stopLocate()
        locating = true
        locationStatus = "Getting current location for this map. Nothing is sent."
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
                val fix = withTimeout(20_000) { mapCurrentLocationFix(context, manager, provider) }
                    ?: error("No current location was found. Choose a pin or try again.")
                val ageMs = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000
                check(ageMs in 0..15_000) { "Location is too old. Choose a pin or try again." }
                // Prior: check(fix.hasAccuracy()) { "Location accuracy is unavailable." }
                check(fix.hasAccuracy() && fix.accuracy.isFinite() && fix.accuracy >= 0f) { "Location accuracy is unavailable." } // MAP-R2-077
                val value = LocationDraft(LocationCoordinate(fix.latitude, fix.longitude), LocationSource.currentFix,
                    fix.accuracy.toDouble(), if (precise) LocationPrecision.precise else LocationPrecision.approximate)
                check(liveScope() == capturedScope) { "Account or conversation changed." }
                select(value)
                mapView?.showPin(value.coordinate)
                // MAP-R2-078: show the measured accuracy radius instead of equating a fresh fix with a
                // precise one; a very poor radius (> 1000 m) is labeled approximate. Display-only policy:
                // no fix is rejected or hidden based on accuracy.
                // Prior: locationStatus = if (precise) "Current location shown on the map. Nothing is sent." else "Approximate location shown. You can move the pin. Nothing is sent."
                val accuracyMeters = fix.accuracy.toInt()
                locationStatus = when {
                    fix.accuracy > 1000f -> "Approximate location shown (accuracy about $accuracyMeters m). You can move the pin. Nothing is sent."
                    precise -> "Current location shown on the map (accuracy about $accuracyMeters m). Nothing is sent."
                    else -> "Approximate location shown (accuracy about $accuracyMeters m). You can move the pin. Nothing is sent."
                }
            } catch (timeout: TimeoutCancellationException) {
                locationStatus = "Location timed out. Choose a pin or try again."
            } catch (cancelled: CancellationException) {
                locationStatus = "Location request stopped. Choose a pin or try again."
                throw cancelled
            } catch (error: Exception) {
                locationStatus = error.message ?: "Location is unavailable. Choose a pin instead."
            } finally { locating = false }
        }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) acquireCurrentFix()
        } else locationStatus = "Location permission was denied. You can still choose a pin without it."
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
                // Prior: context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                locationStatus = "Permission is unavailable. You can allow location in app settings, or choose a pin."
                // MAP-R2-070: guarded so a missing settings handler cannot crash.
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
            }
                // Prior: context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            LocateAction.offerLocationSettings -> {
                locationStatus = "Location services are off. Turn them on, then tap Current location again, or choose a pin."
                runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } // MAP-R2-070
            }
        }
    }
    val currentFriendsMapOpen by rememberUpdatedState(friendsMapOpen)
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
                Lifecycle.Event.ON_RESUME -> { mapActive = true; if (!currentFriendsMapOpen) mapView?.resumeMap() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stopLocate(); mapView?.disposeMap(); mapView = null; gate.cancel() }
    }
    LaunchedEffect(accountId, conversationId) {
        if (capturedScope != (accountId to conversationId)) { stopLocate(); gate.cancel(); onDismiss() }
    }
    androidx.activity.compose.BackHandler { stopLocate(); gate.cancel(); onDismiss() }
    MaterialTheme(colorScheme = mapColors) {
    Dialog(onDismissRequest = { stopLocate(); gate.cancel(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Surface(modifier = Modifier.fillMaxSize(), color = mapColors.background, contentColor = mapColors.onBackground) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { stopLocate(); gate.cancel(); onDismiss() }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Back" }) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = null) }
                // MAP-R1-044: bounded flexible title so both actions stay reachable at large font scale.
                Text("Map", modifier = Modifier.weight(1f, fill = false).padding(12.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { friendsMapOpen = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Friends map" }) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.groups, semanticLabel = null) }
            }
            Box(Modifier.fillMaxWidth().weight(1f).semantics { paneTitle = "Map" }) { // MAP-R4-168
            if (mapActive) {
                // MAP-R1-045: keying on mapGeneration lets the Retry action recreate a failed renderer.
                key(mapGeneration) {
                AndroidView(
                    factory = { viewContext -> GeoLibreLocationMapView(viewContext,
                        onPinSelected = { point ->
                            stopLocate()
                            // MAP-R1-028: drop a redundant pin message for the coordinate already selected.
                            if (draft?.coordinate != point) {
                                select(LocationDraft(point, LocationSource.manualPin))
                                locationStatus = "Pin selected locally. Nothing is sent."
                            }
                        },
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
                        // MAP-R5-233: stylePending now clears when the style load reaches idle (onStyleLoading),
                        // not on the immediate page ack, so a slow network keeps the switching status visible.
                        // Prior: onStyleChanged = { selectedStyle = it; styleChosenByUser = true; stylePending = null }
                        systemDark = systemDark, onStyleChanged = { selectedStyle = it; styleChosenByUser = true },
                        onStyleLoading = { loading -> if (!loading) stylePending = null }, onViewChanged = { enabled ->
                            buildingsEnabled = enabled
                            // MAP-R1-047: say what 3D means here; missing building data is not an error.
                            rendererStatus = if (enabled) "3D buildings show where height data exists. Not satellite imagery." else ""
                        },
                    ).also { mapView = it; draft?.let { selected -> it.showPin(selected.coordinate) } } },
                    modifier = Modifier.fillMaxSize(),
                    update = { it.setTheme(systemDark) },
                )
                }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledIconButton(onClick = { mapView?.control("zoomIn") }, enabled = rendererReady, colors = controlColors, modifier = Modifier.size(48.dp).semantics { contentDescription = "Zoom in" }, shape = androidx.compose.foundation.shape.CircleShape) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.add, semanticLabel = null) }
                FilledIconButton(onClick = { mapView?.control("zoomOut") }, enabled = rendererReady, colors = controlColors, modifier = Modifier.size(48.dp).semantics { contentDescription = "Zoom out" }, shape = androidx.compose.foundation.shape.CircleShape) {
//Icon(Icons.Rounded.Remove, contentDescription = null)
                com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.minus, Icons.Rounded.Remove, contentDescription = null) }
                FilledIconButton(onClick = { mapView?.control("toggleBuildings") }, enabled = rendererReady, colors = controlColors,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Toggle 3D buildings"; stateDescription = buildingsStateDescription }, shape = androidx.compose.foundation.shape.CircleShape) { Text(if (buildingsEnabled) "2D" else "3D", modifier = Modifier.clearAndSetSemantics { }) }
                Box {
                FilledIconButton(onClick = { layersOpen = true }, enabled = rendererReady, colors = controlColors, modifier = Modifier.size(48.dp).semantics { contentDescription = "Map layers" }, shape = androidx.compose.foundation.shape.CircleShape) {

//Icon(Icons.Rounded.Layers, contentDescription = null)
                    com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.layers, Icons.Rounded.Layers, contentDescription = null)
                }
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
                // MAP-R1-036 / MAP-R5-242: keyboard and switch-accessible pin placement without a map gesture.
                FilledIconButton(onClick = { mapView?.dropPinAtCenter() }, enabled = rendererReady, colors = controlColors, modifier = Modifier.size(48.dp).semantics { contentDescription = "Drop pin at map center" }, shape = androidx.compose.foundation.shape.CircleShape) {
//Icon(Icons.Rounded.Place, contentDescription = null)
                com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.location, Icons.Rounded.Place, contentDescription = null) }
            }
            FilledIconButton(
                onClick = { locate() }, enabled = currentLocationEnabled && !locating,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = if (isLandscape) 72.dp else 16.dp, bottom = 112.dp).size(56.dp).semantics { contentDescription = "Locate current location" },
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = mapColors.primary, contentColor = mapColors.onPrimary, disabledContainerColor = mapColors.surfaceVariant, disabledContentColor = mapColors.onSurfaceVariant),
            ) {
                if (locating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = mapColors.onPrimary)
            // Prior: if (status.isNotBlank()) Text(status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                else
//Icon(Icons.Rounded.MyLocation, contentDescription = null)
                com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.locate, Icons.Rounded.MyLocation, contentDescription = null)
            }
            }
            // MAP-R1-034: polite live region
            // MAP-R1-010: renderer and location status are announced independently.
            // Prior: if (status.isNotBlank()) Text(status, modifier = ..., style = MaterialTheme.typography.bodySmall)
            if (rendererStatus.isNotBlank()) Text(rendererStatus, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
            if (locationStatus.isNotBlank()) Text(locationStatus, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
            // MAP-R1-024/046: pending style is shown until the page confirms the change.
            if (stylePending != null) Text("Switching map style...", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
            // MAP-R1-035: textual coordinate summary; the pin is inspectable without reading the map.
            draft?.let { selected -> Text("Pin: ${"%.5f".format(java.util.Locale.US, selected.coordinate.latitude)}, ${"%.5f".format(java.util.Locale.US, selected.coordinate.longitude)}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall) }
            // MAP-R1-045: explicit local retry when the renderer reports unavailable.
            if (rendererFailed) TextButton(onClick = {
                mapView?.disposeMap()
                mapView = null
                rendererReady = false
                rendererFailed = false
                rendererStatus = "Loading map..."
                mapGeneration += 1
            }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Retry map") }
        }
    }
    }
    if (friendsMapOpen) {
        // Global friends map (frontend-only): honest not-connected preview, no friend
        // data and no sharing. Opened from this map's toolbar; per-chat send flow is untouched.
        FriendMapScreen(
            context = context,
            onDismiss = { friendsMapOpen = false },
            onAttributionLink = onAttributionLink,
            currentLocationEnabled = currentLocationEnabled,
        )
    }
    if (rationale) AlertDialog(onDismissRequest = { rationale = false }, title = { Text("Use your current location?") },
        text = { Text("Chaty needs foreground location access only to find a point you request. Approximate access is accepted. You can choose a map pin without granting location access.") },
        confirmButton = { TextButton(onClick = { rationale = false; requestPermission() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { rationale = false }) { Text("Choose a pin instead") } })
    }
}

private tailrec fun Context.findMapActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findMapActivity()
    else -> null
}

@android.annotation.SuppressLint("MissingPermission")
private suspend fun mapCurrentLocationFix(context: Context, manager: LocationManager, provider: String): Location? =
    suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }

