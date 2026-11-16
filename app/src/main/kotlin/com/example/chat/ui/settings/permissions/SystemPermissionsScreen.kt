package com.example.chat.ui.settings.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.net.ConnectivityManager
import android.app.NotificationManager
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.res.stringResource
import androidx.biometric.BiometricManager
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.LiveRegionMode
import com.example.chat.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.chat.ui.onboarding.ChatyPermissions
import com.example.chat.ui.settings.ChatyPreviewCard
import com.example.chat.ui.settings.ChatySettingsPage
import com.example.chat.ui.settings.ChatySettingsSection
import com.example.chat.ui.settings.ChatySettingsTile
import com.example.chat.ui.settings.ChatySwitchTile
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

/** Logical Android access, distinguishing limited media and approximate location. */
// private enum class PermStatus
internal enum class PermStatus { Checking, Granted, Limited, Approximate, Denied, PermanentlyDenied }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findActivity()
    else -> null
}

private data class PermissionLocationFix(val latitude: Double, val longitude: Double, val accuracy: Float)

private data class TrackedPermission(
    val id: String,
    /** Runtime permissions to request (permission_handler may fan one
     *  logical permission out to several Android permissions). */
    val androidPermissions: List<String>,
)

private val TRACKED = listOf(
    TrackedPermission("notification", listOfNotNull(ChatyPermissions.notification)),
    TrackedPermission("photos", if (Build.VERSION.SDK_INT >= 33)
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) +
            (if (Build.VERSION.SDK_INT >= 34) listOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else emptyList())
        else listOf(ChatyPermissions.photos)),
    // TrackedPermission("location",
    //     listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
    TrackedPermission("location", overviewLocationPermissions),
    TrackedPermission("camera", listOf(Manifest.permission.CAMERA)),
    TrackedPermission("microphone", listOf(Manifest.permission.RECORD_AUDIO)),
    TrackedPermission("contacts", listOf(Manifest.permission.READ_CONTACTS)),
)

private fun statusOf(context: Context, activity: Activity?, tracked: TrackedPermission): PermStatus {
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    if (tracked.id == "notification" && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        if (tracked.androidPermissions.isEmpty()) return PermStatus.PermanentlyDenied
    } else if (tracked.id == "notification") return PermStatus.Granted
    if (tracked.id == "location") {
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) return PermStatus.Granted
        if (granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return PermStatus.Approximate
    } else if (tracked.id == "photos" && Build.VERSION.SDK_INT >= 33) {
        if (granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)) return PermStatus.Granted
        if (granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO) ||
            (Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))) return PermStatus.Limited
    } else if (tracked.androidPermissions.any(::granted)) return PermStatus.Granted
    val asked = context.getSharedPreferences("chaty_permission_requests", Context.MODE_PRIVATE)
        .getBoolean(tracked.id, false)
    val rationale = activity != null && tracked.androidPermissions.any {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
    // A first request has no rationale too. Only a completed prior request can be blocked.
    // return if (!asked || rationale || activity == null) PermStatus.Denied else PermStatus.PermanentlyDenied
    return if (shouldOfferPermissionSettings(asked, rationale, activity != null)) PermStatus.PermanentlyDenied else PermStatus.Denied
}

/** Port of lib/features/settings/permissions/system_permissions_screen.dart.
 *
 *  [triggerTestNotification], [canUseBiometrics] and [authenticateBiometric]
 *  are injected; the host wires ChatyNotificationService.triggerEventNotification
 *  (title "Chaty test notification", body "This is a sample notification from
 *  Chaty.", notifications icon, theme primary color) and LocalLockService
 *  (canUseBiometrics = canCheckBiometrics && availableBiometrics.isNotEmpty,
 *  authenticateBiometric = biometric-only prompt), matching the Dart service. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemPermissionsScreen(
    onBack: () -> Unit,
    triggerTestNotification: () -> Unit,
    canUseBiometrics: suspend () -> Boolean,
    authenticateBiometric: suspend (reason: String) -> Boolean,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

// Superseded source retained below per additive-only review rule.
//     var statuses by remember { mutableStateOf(TRACKED.associate { it.id to PermStatus.Checking }) }
    // Previously replaced the entire Map for every single-row result.
    val statuses = remember { mutableStateMapOf<String, PermStatus>().apply { putAll(TRACKED.associate { it.id to PermStatus.Checking }) } }
    val refreshMutex = remember { kotlinx.coroutines.sync.Mutex() }
    // Initial refresh is owned by LaunchedEffect; no duplicate capability IPC on first RESUME.
    var backgrounded by remember { mutableStateOf(false) }
    var biometricsAvailable by remember { mutableStateOf(false) }
    var biometricCapabilityLoaded by remember { mutableStateOf(false) }
    var biometricPlatformStatus by remember { mutableStateOf<Int?>(null) }
    var biometricTesting by remember { mutableStateOf(false) }
    var biometricJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var foregroundGeneration by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    val refreshGeneration = remember { longArrayOf(0L) }
    var pendingAction by remember { mutableStateOf("none") } // "none" | "requestAll" | "locationFetch"
    var locationFix by remember { mutableStateOf<PermissionLocationFix?>(null) }
    var showCheckup by remember { mutableStateOf(false) }
    var showLocationConsent by remember { mutableStateOf(false) }
    var lastNotificationTestAt by remember { mutableStateOf(0L) }
    var platformStatusVersion by remember { mutableStateOf(0) }
    var revokedSinceLastCheck by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pickedContact by remember { mutableStateOf<Pair<String, String>?>(null) }
    var contactReadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showOverlayWarning by remember { mutableStateOf(false) }
    var showDataUseHelp by remember { mutableStateOf(false) }
    var rationaleFor by remember { mutableStateOf<TrackedPermission?>(null) }
    var settingsFor by remember { mutableStateOf<TrackedPermission?>(null) }
    var locationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun snack(message: String, color: Color? = null) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

// Superseded source retained below per additive-only review rule.
//     suspend fun refreshAll() {
    suspend fun refreshAll(refreshBiometrics: Boolean = false) {
        refreshMutex.lock()
        val generation = ++refreshGeneration[0]
        loading = true
        try {
            val map = TRACKED.associate { it.id to statusOf(context, activity, it) }
            val grantHistory = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val history = context.getSharedPreferences("chaty_permission_requests", Context.MODE_PRIVATE)
                val before = history.getStringSet("last_granted_categories", emptySet()).orEmpty().toSet()
                history to before
            }

            val bio = if (!biometricCapabilityLoaded || refreshBiometrics) {
                try { canUseBiometrics() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { false }
            } else biometricsAvailable
            val platformBio = try { BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) }
                catch (_: Exception) { null }
            // Resume/manual/result refreshes can overlap while the platform adapter suspends.
            if (generation == refreshGeneration[0]) {
                val allowed = map.filterValues { it == PermStatus.Granted || it == PermStatus.Limited || it == PermStatus.Approximate }.keys
                val denied = map.filterValues { it == PermStatus.Denied || it == PermStatus.PermanentlyDenied }.keys
                revokedSinceLastCheck = PermissionAccessHistory.offSinceLastCheck(grantHistory.second, denied, revokedSinceLastCheck)
                grantHistory.first.edit().putStringSet("last_granted_categories", allowed.toSet()).apply()
                statuses.putAll(map)
                biometricsAvailable = bio
                biometricCapabilityLoaded = true
                biometricPlatformStatus = platformBio
                platformStatusVersion++
            }
        } finally {
            if (generation == refreshGeneration[0]) loading = false
            refreshMutex.unlock()
        }
    }

    fun refreshOne(id: String) {
        val tracked = TRACKED.first { it.id == id }
// Superseded source retained below per additive-only review rule.
//         statuses = statuses + (id to statusOf(context, activity, tracked))
        statuses[id] = statusOf(context, activity, tracked)
    }

    LaunchedEffect(Unit) { refreshAll() }

    // Dart didChangeAppLifecycleState: re-read after returning from settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                foregroundGeneration++
                biometricJob?.cancel()
                backgrounded = true
                refreshGeneration[0]++ // Invalidate a suspended platform status response.
                loading = false
                locationFix = null
                locationJob?.cancel()
                contactReadJob?.cancel()
                pickedContact = null
            }
// Superseded source retained below per additive-only review rule.
//             if (event == Lifecycle.Event.ON_RESUME) {
//                 scope.launch { refreshAll() }
//             }
            if (event == Lifecycle.Event.ON_RESUME && backgrounded) {
                backgrounded = false
                scope.launch { refreshAll(refreshBiometrics = true) }
            } // First entry is loaded once by LaunchedEffect, not again by the initial RESUME.
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            foregroundGeneration++
            biometricJob?.cancel()
            contactReadJob?.cancel()
            refreshGeneration[0]++
            locationJob?.cancel()
            locationFix = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (pendingAction != "contactPicker") return@rememberLauncherForActivityResult
        pendingAction = "none"
        // A stopped picker result must not initiate a contacts query.
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@rememberLauncherForActivityResult
        val contactGeneration = foregroundGeneration
        val selectedUri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && selectedUri?.scheme == "content") {
            pendingAction = "contactReading"
            contactReadJob = scope.launch {
                try {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (foregroundGeneration != contactGeneration ||
                        !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
                    val selected = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if (foregroundGeneration != contactGeneration ||
                            !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@withContext null
                        context.contentResolver.query(selectedUri,
                            arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                            null, null, null)?.use { cursor ->
                            if (!cursor.moveToFirst()) null else
                                cursor.getString(cursor.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)).orEmpty() to
                                    cursor.getString(cursor.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)).orEmpty()
                        }
                    }
                    // if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) pickedContact = selected
                    if (foregroundGeneration == contactGeneration &&
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) pickedContact = selected
                } catch (cancelled: CancellationException) { throw cancelled }
                // catch (_: Exception) { snack(context.getString(com.example.chat.R.string.permissions_contact_picker_unavailable)) }
                catch (_: Exception) {
                    if (foregroundGeneration == contactGeneration &&
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        snack(context.getString(com.example.chat.R.string.permissions_contact_picker_unavailable))
                }
                finally { if (pendingAction == "contactReading") pendingAction = "none" }
            }
        }
    }

    // Single/multi permission requests. Dart _toggle / _requestAll always
    // re-read the status afterwards.
    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val completedAction = pendingAction
        TRACKED.firstOrNull { it.id == completedAction || (completedAction == "locationFetch" && it.id == "location") }?.let {
            context.getSharedPreferences("chaty_permission_requests", Context.MODE_PRIVATE).edit().putBoolean(it.id, true).apply()
        }
        pendingAction = "none"
        if (completedAction == "locationFetch") {
            if (!results.values.any { it }) snack("Location permission was denied.", colors.error)
            else {
                pendingAction = "locationReading"
                locationJob = scope.launch {
                    try {
                        val fix = withTimeout(15_000) { currentLocation(context) }
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) locationFix = PermissionLocationFix(fix.latitude, fix.longitude, fix.accuracy)
                    } catch (cancelled: CancellationException) {
                        if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                        snack("Location request timed out. Try again.", colors.error)
                    } catch (_: Exception) {
                        snack("Location is unavailable. Check permission and location services, then retry.", colors.error)
                    } finally { pendingAction = "none" }
                }
            }
        }
        scope.launch {
            refreshAll()
            TRACKED.firstOrNull { it.id == completedAction }?.let { tracked ->
                snack("${tracked.id.replaceFirstChar { it.uppercase() }}: ${statuses[tracked.id]?.name ?: "Checking"}.")
            }
        }
    }

    fun request(tracked: TrackedPermission) {
        if (pendingAction != "none") return
        if (tracked.androidPermissions.isEmpty()) {
            refreshOne(tracked.id)
        } else {
            pendingAction = tracked.id
            try {
                requestLauncher.launch(tracked.androidPermissions.toTypedArray())
            } catch (_: Exception) {
                pendingAction = "none"
                snack("The Android permission prompt is unavailable. Try again or use system settings.")
            }
        }
    }

    // Dart _toggle: granted -> honest deep-link; blocked -> settings; else ask.
    fun toggle(tracked: TrackedPermission) {
        if (pendingAction != "none") return
        try {
            when (statuses[tracked.id]) {
                PermStatus.Granted, PermStatus.Approximate, PermStatus.Limited, PermStatus.PermanentlyDenied -> settingsFor = tracked
                else -> {
                    val needsRationale = activity != null && tracked.androidPermissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
                    if (needsRationale) rationaleFor = tracked else request(tracked)
                }
            }
        } catch (_: Exception) {
            snack("Could not update this permission.", colors.error)
        }
        // ActivityResult and ON_RESUME own refresh, not this launch frame.
    }

    // Dart _fetchLocation.
    fun fetchLocation() {
        if (pendingAction != "none") return
        pendingAction = "locationReading"
        locationJob = scope.launch {
            try {
                val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val serviceEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                if (!serviceEnabled) {
                    snack("Location services are turned off on this device.", colors.error)
                    return@launch
                }
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    pendingAction = "locationFetch"
                    try {
                        // requestLauncher.launch(arrayOf(
                        //     Manifest.permission.ACCESS_FINE_LOCATION,
                        //     Manifest.permission.ACCESS_COARSE_LOCATION,
                        // ))
                        requestLauncher.launch(overviewLocationPermissions.toTypedArray())
                    } catch (_: Exception) {
                        // A launch failure has no result callback to release this guard.
                        pendingAction = "none"
                        snack("The location permission prompt is unavailable. Try again or use system settings.")
                    }
                    return@launch
                }
                val fix = withTimeout(15_000) { currentLocation(context) }
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) locationFix = PermissionLocationFix(fix.latitude, fix.longitude, fix.accuracy)
            } catch (cancelled: CancellationException) {
                if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                snack("Location request timed out. Try again.", colors.error)
            } catch (e: Exception) {
                snack("Location is unavailable. Check permission and location services, then retry.", colors.error)
            } finally {
                if (pendingAction == "locationReading") pendingAction = "none"
                refreshOne("location")
            }
        }
    }

    // Dart _requestAll.
    fun requestAll() {
        if (pendingAction == "none") showCheckup = true
    }

    if (showCheckup) {
        AlertDialog(
            shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showCheckup = false },
            title = { Text("Permission checkup") },
            text = {
                Column {
                    Text("Choose one category to review. Only allow access for features you want to use. You can keep other permissions off.")
                    TRACKED.forEach { tracked ->
                        TextButton(onClick = { showCheckup = false; toggle(tracked) }) {
                            Text("${tracked.id.replaceFirstChar { it.uppercase() }}: ${statuses[tracked.id]?.name ?: "Checking"}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCheckup = false }) { Text("Done") } },
        )
    }

    fun openSystemPage(action: String, packageUri: Boolean = false) {
        try {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageUri) intent.data = Uri.parse("package:${context.packageName}")
            if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            context.startActivity(intent)
        } catch (_: Exception) { snack("This settings page is unavailable. Open Chaty app info in Android settings.") }
    }

    rationaleFor?.let { tracked ->
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { rationaleFor = null }, title = { Text("Review ${tracked.id} access") },
            text = { Text("This access is optional. Without it, features using ${tracked.id} may be unavailable. You can keep access off and change it later in Android settings.") },
            dismissButton = { TextButton(onClick = { rationaleFor = null }) { Text("Not now") } },
            confirmButton = { TextButton(onClick = { rationaleFor = null; request(tracked) }) { Text("Continue to Android prompt") } },
        )
    }
    settingsFor?.let { tracked ->
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { settingsFor = null }, title = { Text("Manage ${tracked.id} in Android") },
            text = { Text("Open Chaty app info, choose Permissions, then choose ${tracked.id}. Android may close Chaty if you revoke access. Return with Android Back; this screen will refresh the status.") },
            dismissButton = { TextButton(onClick = { settingsFor = null }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = { settingsFor = null; openSystemPage(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }) { Text("Open system settings") } },
        )
    }

    if (showOverlayWarning) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showOverlayWarning = false },
            title = { Text("Display over other apps?") },
            text = { Text("Overlay access lets call or Island controls appear above another app. Only enable it if you need that feature. Overlays can cover controls; be careful when entering sensitive information.") },
            dismissButton = { TextButton(onClick = { showOverlayWarning = false }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = { showOverlayWarning = false; openSystemPage(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }) { Text("Open Android settings") } },
        )
    }
    if (showDataUseHelp) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showDataUseHelp = false }, title = { Text("Why access is needed") },
            text = { Column {
                Text("Camera: take photos and send video to the person in a call. Microphone: send recordings and call audio.")
                Text("Contacts: contact-discovery features may compare address-book details with the service. Only enable this if you want discovery.")
                Text("Media: choose files to send to a chat. Location: share only through a separate chat action; this screen's test sends nothing.")
                Text("Notifications: display alerts on this device. Biometrics: Android performs authentication; this screen does not receive fingerprint or face data.")
            } },
            confirmButton = { TextButton(onClick = { showDataUseHelp = false }) { Text("Done") } },
        )
    }

    com.example.chat.ui.kit.ProtectSensitiveWindow(active = pickedContact != null)
    pickedContact?.let { selected ->
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            properties = androidx.compose.ui.window.DialogProperties(
                securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
            onDismissRequest = { pickedContact = null },
            title = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.permissions_selected_contact)) },
            text = { Column {
                Text(selected.first)
                Text(selected.second)
                Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.permissions_contact_picker_local_only))
            } },
            confirmButton = { TextButton(onClick = { pickedContact = null }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.permissions_close_preview)) } },
        )
    }

    if (showLocationConsent) {
        AlertDialog(
            shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showLocationConsent = false },
            title = { Text("Test device location?") },
            text = { Text("Read a location fix on this device only. It is not sent to Chaty or a contact and clears when you close the result or leave the app.") },
            dismissButton = { TextButton(onClick = { showLocationConsent = false }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = { showLocationConsent = false; fetchLocation() }) { Text("Read location") } },
        )
    }

    com.example.chat.ui.kit.ProtectSensitiveWindow(active = locationFix != null)
    locationFix?.let { fix ->
        // var exactVisible by remember(fix) { mutableStateOf(false) }
        var moreDigitsVisible by remember(fix) { mutableStateOf(false) }
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            properties = androidx.compose.ui.window.DialogProperties(
                securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
            onDismissRequest = { locationFix = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
// Superseded source retained below per additive-only review rule.
//                     Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = colors.error)
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Current device location")
                }
            },
            text = {
                Column {
//                     Text("Latitude: ${"%.3f".format(fix.latitude)} degrees", fontWeight = FontWeight.Bold)
//                     Text("Longitude: ${"%.3f".format(fix.longitude)} degrees", fontWeight = FontWeight.Bold)
                    // val precision = if (exactVisible) "%.5f" else "%.3f"
                    val precision = if (moreDigitsVisible) "%.5f" else "%.3f"
                    Text("Latitude: ${precision.format(fix.latitude)} degrees", fontWeight = FontWeight.Bold)
                    Text("Longitude: ${precision.format(fix.longitude)} degrees", fontWeight = FontWeight.Bold)
//                     TextButton(onClick = { exactVisible = !exactVisible }) {
//                         Text(androidx.compose.ui.res.stringResource(if (exactVisible)
//                             com.example.chat.R.string.permissions_hide_exact_location else com.example.chat.R.string.permissions_show_exact_location))
//                     }
                    TextButton(onClick = { moreDigitsVisible = !moreDigitsVisible }) {
                        Text(stringResource(if (moreDigitsVisible)
                            R.string.privacy_location_fewer_digits else R.string.privacy_location_more_digits))
                    }
                    Text(stringResource(R.string.privacy_location_digits_accuracy),
                        fontSize = 12.sp, color = colors.foregroundSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Accuracy: ${"%.0f".format(fix.accuracy)} m",
                        fontSize = 12.sp,
                        color = colors.foregroundSecondary,
                    )
                }
            },
            dismissButton = { TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Device location", "${"%.3f".format(fix.latitude)}, ${"%.3f".format(fix.longitude)} (accuracy ${"%.0f".format(fix.accuracy)} m)")
                if (Build.VERSION.SDK_INT >= 24) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                clipboard.setPrimaryClip(clip)
                snack("Location copied to the device clipboard. Other apps may be able to paste it.")
            }) { Text("Copy location") } },
            confirmButton = {
                TextButton(onClick = { locationFix = null }) { Text("Close") }
            },
        )
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Box(Modifier.fillMaxSize().padding(padding)) {
        PermissionContentFrame(Modifier.padding(padding)) {
            ChatySettingsPage(
                title = "App Permissions & Hardware",
// Superseded source retained below per additive-only review rule.
//                 subtitle = "Notifications, media, location, camera, mic & more",
                subtitle = "Choose access for the features you use",
                onBack = onBack,
                trailingHeaderWidget = {
                    // IconButton(modifier = Modifier.semantics { ... }, onClick = { scope.launch { refreshAll(); ... } }, enabled = !loading) {
                    IconButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { stateDescription = if (loading) "Checking" else "Ready" }, onClick = { scope.launch { refreshAll(); snack("Permission status refreshed.") } }, enabled = !loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Re-check permissions")
                    }
                },
            ) {
                // Live status center.
                ChatyPreviewCard(title = "Permission Status Center") {
                    Column {
                        PermissionSystemSettingsButton(Modifier.align(Alignment.End)) {
                            openSystemPage(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
                        }
                        val enabledCount = statuses.values.count { it == PermStatus.Granted || it == PermStatus.Approximate }
                        Text(if (loading) "Checking permissions..." else "On $enabledCount · Off or limited ${TRACKED.size - enabledCount}", modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                        Text("Optional access is not a requirement. Off or limited categories are listed below; manage all access in Android settings.", color = colors.foregroundSecondary, fontSize = 14.sp)
                        val spokenStatusSummary = permissionStatusSummary(
                            loading,
                            listOf("Notifications" to statuses["notification"], "Media & Photos" to statuses["photos"],
                                "Location" to statuses["location"], "Camera" to statuses["camera"],
                                "Microphone" to statuses["microphone"], "Contacts" to statuses["contacts"]),
                            biometricsAvailable,
                        )
                        FlowRow(
                            modifier = Modifier.testTag("permission-status-summary").clearAndSetSemantics {
                                heading()
                                contentDescription = spokenStatusSummary
                            },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (loading || statuses["notification"] != PermStatus.Granted) {
                            StatusChip("Notifications", if (loading) null else statuses["notification"], Icons.Rounded.NotificationsActive)
                            }
                            if (loading || statuses["photos"] != PermStatus.Granted) {
                            StatusChip("Media & Photos", if (loading) null else statuses["photos"], Icons.Rounded.PermMedia)
                            }
                            if (loading || statuses["location"] != PermStatus.Granted) {
                            StatusChip("Location", if (loading) null else statuses["location"], Icons.Rounded.LocationOn)
                            }
                            if (loading || statuses["camera"] != PermStatus.Granted) {
                            StatusChip("Camera", if (loading) null else statuses["camera"], Icons.Rounded.CameraAlt)
                            }
                            if (loading || statuses["microphone"] != PermStatus.Granted) {
                            StatusChip("Microphone", if (loading) null else statuses["microphone"], Icons.Rounded.Mic)
                            }
                            if (loading || statuses["contacts"] != PermStatus.Granted) {
                            StatusChip("Contacts", if (loading) null else statuses["contacts"], Icons.Rounded.Contacts)
                            } // Keep biometrics independent of the contacts status and close the category guard.
                            StatusChip(stringResource(R.string.privacy_biometric_unlock), if (loading) null else if (biometricsAvailable) PermStatus.Granted else PermStatus.Denied, Icons.Rounded.Fingerprint)
                        }
                        // Moved unchanged settings action to the card top-right (PM5-01).
                        // TextButton(onClick = { openSystemPage(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }) { Text("Open system settings") }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (loading) "Checking current permission status..."
                            else "Review a permission or manage it in Android settings. Unused-app access may be reset by Android.",
                            fontSize = 14.sp,
                            color = colors.foregroundSecondary,
                        )
                    }
                }

                // 1. Notifications
                ChatySettingsSection(
                    title = "Push Notifications & Alerts",
// Superseded source retained below per additive-only review rule.
//                     description = "Receive alerts for calls, mentions, and incoming messages.",
                    description = "Choose whether Chaty can interrupt you with alerts.",
                ) {
                    PermissionSwitch(
                        icon = Icons.Rounded.NotificationsActive,
                        color = colors.primary,
                        // title = "Notification Permission",
                        title = stringResource(R.string.privacy_allow_notifications),
                        stableTagTitle = "Notification Permission",
                        subtitle = "Allow Chaty to display alerts and heads-up notifications",
                        status = statuses["notification"],
                        onToggle = { toggle(TRACKED[0]) },
                        onOpenSettings = { settingsFor = TRACKED[0] },
                    )
                    ChatySettingsTile(
                        icon = Icons.Rounded.Send,
                        iconColor = colors.info,
//                         title = "Send a test notification",
//                         subtitle = "Display a sample Chaty notification now",
                        title = stringResource(R.string.privacy_preview_in_app_alert),
                        subtitle = stringResource(R.string.privacy_in_app_alert_explanation),
                        onTap = {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (lastNotificationTestAt == 0L || now - lastNotificationTestAt >= 10_000) {
                                lastNotificationTestAt = now
//                                 try { triggerTestNotification(); snack("Test notification requested. Delivery depends on Android settings.") }
                                try { triggerTestNotification(); snack(context.getString(R.string.privacy_in_app_alert_requested)) }
                                catch (_: Exception) { snack("Could not send the test notification. Check notification settings.") }
                            } else snack("Wait a few seconds before testing again.")
                        },
                    )
                }

                // 2. Media, Photos & Storage
                ChatySettingsSection(
                    title = "Media, Photos & File System",
                    description = "Share images, videos, audio notes, and attachments in chats.",
                ) {
                    PermissionSwitch(
                        icon = Icons.Rounded.PermMedia,
                        color = colors.info,
                        // title = "Media & Photo Library",
                        title = stringResource(R.string.privacy_allow_media),
                        stableTagTitle = "Media & Photo Library",
                        subtitle = "Access the gallery to send photos and video",
                        status = statuses["photos"],
                        onToggle = { toggle(TRACKED[1]) },
                        onOpenSettings = { settingsFor = TRACKED[1] },
                    )
                }

                // 3. Location
                ChatySettingsSection(
                    title = "Location & Map Coordinates",
                    description = "Share your live location or drop a pin in a conversation.",
                ) {
                    PermissionSwitch(
                        icon = Icons.Rounded.LocationOn,
                        color = colors.warning,
                        // title = "Location Permission",
                        title = stringResource(R.string.privacy_allow_location),
                        stableTagTitle = "Location Permission",
                        subtitle = "Access device GPS to share your location in chats",
                        status = statuses["location"],
                        onToggle = { toggle(TRACKED[2]) },
                        onOpenSettings = { settingsFor = TRACKED[2] },
                    )
                    if (statuses["location"] == PermStatus.Approximate) {
                        TextButton(
                            enabled = pendingAction == "none",
                            onClick = { request(TRACKED.first { it.id == "location" }) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.privacy_request_precise_location)) }
                    } // The test action and reading state are not restricted to approximate grants.
                    if (pendingAction == "locationReading") {
                        Row(Modifier.padding(16.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                            verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp).clearAndSetSemantics { }, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.privacy_reading_device_location))
                        }
                    }
                    ChatySettingsTile(
                        icon = Icons.Rounded.MyLocation,
                        iconColor = colors.info,
                        title = "Test location",
                        subtitle = "Test on this device only. Nothing is sent; the result clears when closed.",
                        onTap = { if (pendingAction == "none") showLocationConsent = true },
                    )
                }

                // 4. Hardware sensors
                ChatySettingsSection(
                    title = "Hardware & Sensors",
// Superseded source retained below per additive-only review rule.
//                     description = "Control camera, microphone, contacts, and biometric access.",
                    description = "Allow only the inputs you need for sharing and calls.",
                ) {
                    PermissionSwitch(
                        icon = Icons.Rounded.CameraAlt,
                        color = colors.primary,
                        // title = "Camera",
                        title = stringResource(R.string.privacy_allow_camera),
                        stableTagTitle = "Camera",
                        subtitle = "Take photos, record stories and start video calls",
                        status = statuses["camera"],
                        onToggle = { toggle(TRACKED[3]) },
                        onOpenSettings = { settingsFor = TRACKED[3] },
                    )
                    PermissionSwitch(
                        icon = Icons.Rounded.Mic,
                        color = colors.error,
                        // title = "Microphone",
                        title = stringResource(R.string.privacy_allow_microphone),
                        stableTagTitle = "Microphone",
                        subtitle = "Record voice messages and voice calls",
                        status = statuses["microphone"],
                        onToggle = { toggle(TRACKED[4]) },
                        onOpenSettings = { settingsFor = TRACKED[4] },
                    )
                    PermissionSwitch(
                        icon = Icons.Rounded.Contacts,
                        color = colors.success,
                        // title = "Contacts",
                        title = stringResource(R.string.privacy_allow_contacts),
                        stableTagTitle = "Contacts",
// Superseded source retained below per additive-only review rule.
//                         subtitle = "Discover which of your contacts are on Chaty",
                        subtitle = "Optional contact discovery. Review what leaves this device in Why Chaty needs access.",
                        status = statuses["contacts"],
                        onToggle = { toggle(TRACKED[5]) },
                        onOpenSettings = { settingsFor = TRACKED[5] },
                    )
                    ChatySettingsTile(icon = Icons.Rounded.Contacts,
                        title = androidx.compose.ui.res.stringResource(com.example.chat.R.string.permissions_pick_one_contact),
                        subtitle = androidx.compose.ui.res.stringResource(com.example.chat.R.string.permissions_pick_one_contact_detail),
                        enabled = pendingAction == "none",
                        onTap = {
                            if (pendingAction == "none") {
                                pendingAction = "contactPicker"
                                try { contactPicker.launch(Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
                                catch (_: Exception) {
                                    pendingAction = "none"
                                    snack(context.getString(com.example.chat.R.string.permissions_contact_picker_unavailable))
                                }
                            }
                        })
                    ChatySettingsTile(
                        icon = Icons.Rounded.Fingerprint,
                        iconColor = colors.primary,
//                         title = "Biometrics & Fingerprint",
//                         subtitle = if (biometricTesting) "Testing biometric authentication..." else if (biometricsAvailable) "Tap to test biometric unlock"
//                             else "No enrolled biometrics detected on this device",
                        title = stringResource(R.string.privacy_biometric_unlock),
                        subtitle = stringResource(when {
                            biometricTesting -> R.string.privacy_biometric_testing
                            loading -> R.string.privacy_biometric_checking
                            biometricsAvailable -> R.string.privacy_biometric_ready
                            biometricPlatformStatus == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> R.string.privacy_biometric_no_hardware
                            biometricPlatformStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> R.string.privacy_biometric_not_enrolled
                            biometricPlatformStatus == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> R.string.privacy_biometric_temporarily_unavailable
                            biometricPlatformStatus == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> R.string.privacy_biometric_update_required
                            else -> R.string.privacy_biometric_unknown
                        }),
                        badgeText = if (loading) null else (if (biometricsAvailable) "Available" else "Unavailable"),
                        badgeColor = if (biometricsAvailable) colors.success else colors.disabled,
                        onTap = {
//                             if (!biometricTesting && pendingAction == "none") {
//                                 biometricTesting = true
//                                 scope.launch {
//                                     try {
//                                         // Enrollment can change in Android while this screen remains composed.
//                                         val available = canUseBiometrics()
//                                         biometricsAvailable = available
//                                         if (!available) {
//                                             snack("Biometrics are unavailable. Check enrollment and device settings.")
//                                             return@launch
//                                         }
//                                         val ok = authenticateBiometric("Test biometric authentication on this device")
//                                         if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
//                                             snack(if (ok) "Biometric test succeeded. No permission was changed."
//                                                 else "Biometric test was not completed. No permission was changed.")
//                                         }
//                                     } catch (cancelled: CancellationException) { throw cancelled }
//                                     catch (_: Exception) { snack("Biometric test is unavailable. Try again or check device settings.") }
//                                     finally { biometricTesting = false }
//                                 }
//                             }
                            if (!biometricTesting && pendingAction == "none" &&
                                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                // Reserve the same lane that permission, location and contact actions use.
                                pendingAction = "biometricTest"
                                biometricTesting = true
                                val biometricGeneration = foregroundGeneration
                                biometricJob = scope.launch {
                                    try {
                                        val available = canUseBiometrics()
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        // Adapter may suspend or ignore cancellation across STOP/RESUME.
                                        if (foregroundGeneration != biometricGeneration ||
                                            !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                                        biometricsAvailable = available
                                        if (!available) {
                                            snack("Biometrics are unavailable. Check enrollment and device settings.")
                                            return@launch
                                        }
                                        val ok = authenticateBiometric("Test biometric authentication on this device")
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        if (foregroundGeneration == biometricGeneration &&
                                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                            snack(if (ok) "Biometric test succeeded. No permission was changed."
                                                else "Biometric test was not completed. No permission was changed.")
                                        }
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) {
                                        if (foregroundGeneration == biometricGeneration &&
                                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                                            snack("Biometric test is unavailable. Try again or check device settings.")
                                    } finally {
                                        biometricTesting = false
                                        if (pendingAction == "biometricTest") pendingAction = "none"
                                    }
                                }
                            }
                        },
                    )
                }

                if (revokedSinceLastCheck.isNotEmpty()) {
                    ChatySettingsTile(icon = Icons.Rounded.Settings,
                        title = androidx.compose.ui.res.stringResource(com.example.chat.R.string.permissions_access_changed),
                        subtitle = androidx.compose.ui.res.stringResource(com.example.chat.R.string.permissions_unused_access_hint),
                        onTap = { openSystemPage(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) })
                }

                @Suppress("UNUSED_EXPRESSION") platformStatusVersion
                ChatySettingsSection(title = "Special Android access", description = "These are system settings, not ordinary permission switches. Review only what you need.") {
                    ChatySettingsTile(icon = Icons.Rounded.Settings, title = "Display over other apps",
                        subtitle = if (Settings.canDrawOverlays(context)) "Allowed. Used by call or Island overlays." else "Off. Optional call or Island overlays may not appear.",
                        onTap = { showOverlayWarning = true })
                    if (Build.VERSION.SDK_INT >= 31) {
                        val bluetoothOn = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                        ChatySettingsTile(icon = Icons.Rounded.Settings, title = "Nearby devices",
                            subtitle = if (bluetoothOn) "Bluetooth headset access is on." else "Bluetooth headset access is off. Manage it in app permissions.",
                            onTap = { openSystemPage(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) })
                    }
                    if (Build.VERSION.SDK_INT >= 34) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        ChatySettingsTile(icon = Icons.Rounded.NotificationsActive, title = "Full-screen call alerts",
                            subtitle = if (notificationManager.canUseFullScreenIntent()) "Allowed by Android." else "Off. Calls may appear as notifications instead.",
                            onTap = { openSystemPage(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, true) })
                    }
                    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    ChatySettingsTile(icon = Icons.Rounded.Settings, title = "Background battery access",
                        subtitle = if (power.isIgnoringBatteryOptimizations(context.packageName)) "Battery optimization exemption is on." else "Android may limit background work. Device-specific battery settings can add other limits.",
                        onTap = { openSystemPage(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) })
                    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    ChatySettingsTile(icon = Icons.Rounded.Settings, title = "Data saver",
                        subtitle = when (connectivity.restrictBackgroundStatus) {
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "Background data is restricted."
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "Chaty has an exception to Data saver."
                            else -> "Background data is not restricted by Data saver."
                        }, onTap = { openSystemPage(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, true) })
                    if (Build.VERSION.SDK_INT >= 26) {
                        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        listOf(
                            com.example.chat.notifications.ChatyNotifications.CHANNEL_MESSAGES to "Messages",
                            com.example.chat.notifications.ChatyNotifications.CHANNEL_CALLS to "Incoming calls",
                            com.example.chat.notifications.ChatyNotifications.CHANNEL_MISSED to "Missed calls",
                        ).forEach { (id, label) ->
                            val channel = manager.getNotificationChannel(id)
                            ChatySettingsTile(icon = Icons.Rounded.NotificationsActive, title = "$label notification category",
                                subtitle = when (channel?.importance) {
                                    null -> "Not created on this device yet."
                                    NotificationManager.IMPORTANCE_NONE -> "Off in Android settings."
                                    else -> "On. Android importance ${channel.importance}."
                                }, onTap = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            .putExtra(Settings.EXTRA_CHANNEL_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (_: Exception) { snack("Open Chaty notification categories in Android settings.") }
                                })
                        }
                    }
                    ChatySettingsTile(icon = Icons.Rounded.NotificationsActive, title = "Notification categories",
                        subtitle = "Review each Android notification channel and its importance.",
                        onTap = { openSystemPage(Settings.ACTION_APP_NOTIFICATION_SETTINGS) })
                    ChatySettingsTile(icon = Icons.Rounded.Settings, title = "Why Chaty needs access",
                        subtitle = "Read what features use and what may leave your device.", onTap = { showDataUseHelp = true })
                }

                // 5. Bulk actions
                ChatySettingsSection(title = "Review access") {
                    if (Build.VERSION.SDK_INT >= 31) {
                        ChatySettingsTile(icon = Icons.Rounded.Settings,
                            title = stringResource(R.string.privacy_android_access_history),
                            subtitle = stringResource(R.string.privacy_android_access_history_help),
                            onTap = { openSystemPage(Settings.ACTION_PRIVACY_SETTINGS) })
                    }
                    ChatySettingsTile(
                        icon = Icons.Rounded.DoneAll,
// Superseded source retained below per additive-only review rule.
//                         iconColor = colors.success,
                        iconColor = colors.primary,
                        title = "Permission checkup",
                        subtitle = "Review and request one permission category at a time",
                        onTap = { requestAll() },
                    )
                    ChatySettingsTile(
                        icon = Icons.Rounded.Settings,
                        iconColor = colors.foregroundSecondary,
                        title = "Open system settings",
                        subtitle = "Open Permissions in app info, choose a permission, then select the access you want. Return with Android Back.",
                        onTap = { openSystemPage(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) },
                    )
                }
                Text("Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}", color = colors.foregroundSecondary, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

/** Dart _permissionSwitch: granted state + '(blocked in settings)' suffix. */
@Composable
// private fun PermissionSwitch(
internal fun PermissionSwitch(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    status: PermStatus?,
    onToggle: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    stableTagTitle: String = title,
) {
    val openSettingsLabel = stringResource(R.string.privacy_permission_open_app_settings)
    val granted = status == PermStatus.Granted || status == PermStatus.Approximate
    val opensSettings = granted || status == PermStatus.PermanentlyDenied || status == PermStatus.Limited
    val state = when (status) {
        PermStatus.Granted -> "On"
        PermStatus.Limited -> "Selected media only"
        PermStatus.Approximate -> "Approximate location"
        PermStatus.PermanentlyDenied -> "Blocked in settings"
        PermStatus.Denied -> "Off"
        else -> "Checking"
    }
// Superseded source retained below per additive-only review rule.
//     Box(Modifier.testTag("permission-${title.lowercase().replace(' ', '-')}") ) {
    // Box(Modifier.testTag("permission-${stableTagTitle.lowercase().replace(' ', '-')}").semantics { liveRegion = ...Polite; stateDescription = state; role = Role.Button } ) {
    Box(Modifier.testTag("permission-${stableTagTitle.lowercase().replace(' ', '-')}").semantics(mergeDescendants = true) {
        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite; stateDescription = state; if (opensSettings) role = Role.Button
        if (onOpenSettings != null) customActions = listOf(
            CustomAccessibilityAction(openSettingsLabel) { onOpenSettings(); true },
        )
    } ) {
        // if (granted || status == PermStatus.PermanentlyDenied || status == PermStatus.Limited) {
        if (opensSettings) {
            ChatySettingsTile(icon = icon, iconColor = color, title = title,
                subtitle = "$state. $subtitle. Tap to manage access.", onTap = onToggle)
        } else {
// Superseded source retained below per additive-only review rule.
//             ChatySwitchTile(icon = icon, iconColor = color, title = title,
//                 subtitle = "$state. $subtitle", value = false,
//                 enabled = status != null && status != PermStatus.Checking,
//                 onChanged = { onToggle() })
            // Former passive-state switch announced a toggle before Android had answered.
            ChatySettingsTile(icon = icon, iconColor = color, title = title,
                subtitle = "$state. $subtitle. Tap to request access.",
                enabled = status != null && status != PermStatus.Checking, onTap = onToggle)
        }
    }
}

/** Dart _buildStatusChip. */
// The original StatusChip annotation belongs with that function below, not this inserted helper.
/** PM5-17: keep narrow windows fluid and center the page on wide windows. */
@Composable
internal fun PermissionContentFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 720.dp).fillMaxSize(), content = content)
    }
}

/** PM5-01: same system-settings route, separate from permission request actions. */
@Composable
internal fun PermissionSystemSettingsButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
        Text("Open system settings")
    }
}

/** PM2-29: summarize noninteractive chips without conflating limited access or biometric availability. */
internal fun permissionStatusSummary(
    loading: Boolean,
    statuses: List<Pair<String, PermStatus?>>,
    biometricsAvailable: Boolean,
): String {
    if (loading) return "Permission summary. Checking permissions."
    val groups = listOf(
        "Granted" to PermStatus.Granted,
        "Approximate location" to PermStatus.Approximate,
        "Selected media only" to PermStatus.Limited,
        "Blocked in settings" to PermStatus.PermanentlyDenied,
        "Off" to PermStatus.Denied,
        "Checking" to PermStatus.Checking,
    )
    val categories = groups.mapNotNull { (label, status) ->
        statuses.filter { (_, current) -> (current ?: PermStatus.Checking) == status }
            .map { it.first }.takeIf { it.isNotEmpty() }?.let { "$label: ${it.joinToString(", ")}" }
    }
    return (listOf("Permission summary") + categories +
        "Biometrics: ${if (biometricsAvailable) "Available" else "Unavailable"}").joinToString(". ") + "."
}

@Composable
// private fun StatusChip(
internal fun StatusChip(label: String, status: PermStatus?, icon: ImageVector) {
    val colors = LocalAppColors.current
    val color = colors.foregroundSecondary
    val shape = RoundedCornerShape(8.dp) // PM3-40: shared background/border shape.
    val granted = status == PermStatus.Granted || status == PermStatus.Approximate
    val state = when(status) {
        PermStatus.Granted -> "On"
        PermStatus.Approximate -> "Approximate"
        PermStatus.Limited -> "Limited"
        PermStatus.PermanentlyDenied -> "Blocked"
        PermStatus.Denied -> "Off"
        else -> "Checking"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .semantics(mergeDescendants = true) { stateDescription = state }
            .heightIn(min = 32.dp)
            // .background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            // .border(1.dp, color, RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f), shape)
            .border(1.dp, color, shape)
            // .padding(horizontal = 10.dp, vertical = 6.dp),
            .padding(8.dp),
    ) {
// Superseded source retained below per additive-only review rule.
//         Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text("$label: $state", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        Spacer(Modifier.width(4.dp))
        Icon(
            when {
                status == null || status == PermStatus.Checking -> com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.hourglass)
                granted -> Icons.Rounded.Check
                else -> Icons.Rounded.Close
            },
            contentDescription = null,
            tint = color,
// Superseded source retained below per additive-only review rule.
//             modifier = Modifier.size(12.dp),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Geolocator.getCurrentPosition equivalent for minSdk 24:
 *  getCurrentLocation on API 30+, requestSingleUpdate below it. */
@SuppressLint("MissingPermission")
private suspend fun currentLocation(context: Context): Location = suspendCancellableCoroutine { cont ->
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = when {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> {
            cont.resumeWith(Result.failure(Exception("No location provider available")))
            return@suspendCancellableCoroutine
        }
    }
    if (Build.VERSION.SDK_INT >= 30) {
        val cancellation = android.os.CancellationSignal()
        cont.invokeOnCancellation { cancellation.cancel() }
        manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location ->
            if (!cont.isActive) return@getCurrentLocation
            if (location != null) cont.resumeWith(Result.success(location))
            else cont.resumeWith(Result.failure(Exception("Location unavailable")))
        }
    } else {
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                if (cont.isActive) cont.resumeWith(Result.success(location))
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(disabledProvider: String) {
                if (disabledProvider != provider) return
                manager.removeUpdates(this)
                if (cont.isActive) cont.resumeWith(Result.failure(Exception("Location provider disabled")))
            }
            @Deprecated("Required callback on pre-Android R devices")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        }
        cont.invokeOnCancellation { manager.removeUpdates(listener) }
        manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
    }
}

