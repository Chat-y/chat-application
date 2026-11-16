package com.example.chat.ui.chats.linked_devices

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.theme.LocalAppColors
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** Same code may be delivered again after this cooldown: mobile_scanner's
 *  noDuplicates semantics are per-scan-session-with-completion, not forever.
 *  Without an expiry, a code whose resolution failed (offline, user not
 *  found) could never be scanned again until the screen was recreated. */
private const val RESCAN_COOLDOWN_MS = 4_000L

/** Camera QR scanner matching mobile_scanner's Android behavior for
 *  linked_devices_qr_screen.dart: back camera, QR formats only,
 *  noDuplicates-with-cooldown semantics (each distinct code delivered once
 *  per scanner session, re-delivered after [RESCAN_COOLDOWN_MS]).
 *  mobile_scanner itself uses ML Kit on Android, so this is the
 *  exact same decode engine as the Dart screen.
 *
 *  The CAMERA permission is handled in-view: while it is missing or denied
 *  the camera stack is not started and a rationale with 'Allow camera' and
 *  'Open Settings' recovery actions is shown instead of a dead black
 *  preview.
 *
 *  Requires gradle dependencies (integration owner), all free/on-device:
 *      implementation("androidx.camera:camera-camera2:1.4.1")
 *      implementation("androidx.camera:camera-lifecycle:1.4.1")
 *      implementation("androidx.camera:camera-view:1.4.1")
 *      implementation("com.google.mlkit:barcode-scanning:17.3.0")
 *  and the CAMERA permission in AndroidManifest (integration owner).
 *
 *  [enableTorchToggle] adds a low-light torch button over the preview (only
 *  when the bound back camera reports a flash unit); [onPermissionState]
 *  reports the CAMERA permission state so hosts can hide viewfinder
 *  overlays while the primer is showing. Both default off/no-op so existing
 *  callers (AuthWorkspaceScanner) are unchanged. [onDetected] stays the
 *  LAST parameter so a trailing-lambda call binds to it. */
@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerView(
    modifier: Modifier = Modifier,
    enableTorchToggle: Boolean = false,
    onPermissionState: (Boolean) -> Unit = {},
    onDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }
    LaunchedEffect(hasCameraPermission) { onPermissionState(hasCameraPermission) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // Settings can grant or revoke permission without recreating this composition.
    DisposableEffect(context, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasCameraPermission) {
        // Rationale + recovery instead of a dead black PreviewView.
        Column(
            modifier.background(colors.background).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The primer reads as ONE merged block, before any system
            // dialog can steal focus; the two recovery actions stay
            // separate nodes.
            Column(
                Modifier.semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Camera is needed to scan QR codes",
                    color = colors.foreground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Chaty uses the camera only to scan QR codes, on-device. No camera frames leave your phone.",
                    color = colors.foregroundSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Allow camera", color = colors.primary, fontWeight = FontWeight.Bold)
            }
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            },
                        )
                    }
                },
            ) {
                Text("Open Settings", color = colors.primary, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    // Prior lifecycleOwner lookup was below the permission primer's early return.
    // val previewView = remember { PreviewView(context) }
    val previewView = remember(context) { PreviewView(context) }
    // val executor = remember { Executors.newSingleThreadExecutor() }
    val executor = remember(context, lifecycleOwner) { Executors.newSingleThreadExecutor() }
    val detected = androidx.compose.runtime.rememberUpdatedState(onDetected)
    var boundCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    // DisposableEffect(Unit) { // Rebind to the actual preview/lifecycle owner.
    DisposableEffect(context, lifecycleOwner, previewView, executor) {
        val disposed = java.util.concurrent.atomic.AtomicBoolean(false)
        var ownedAnalysis: ImageAnalysis? = null
        var ownedPreview: Preview? = null
        val scanner = BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            if (disposed.get()) return@Runnable
            try {
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            // noDuplicates with cooldown: suppress repeat delivery of the same
            // raw value, but let it through again once the cooldown elapsed so
            // a failed resolution can be retried on the SAME code.
            ownedAnalysis = analysis
            ownedPreview = preview
            var lastDelivered: String? = null
            var lastDeliveredAtMs = 0L
            analysis.setAnalyzer(executor) { proxy ->
                // Top-level guard: an uncaught throw on this executor thread
                // kills analysis silently. LD-R2-24's diagnostics ring
                // buffer is unreleased; log in the ServiceLocator idiom
                // until it lands.
                try {
                    val mediaImage = proxy.image
                    if (mediaImage == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val raw = barcodes.firstNotNullOfOrNull { it.rawValue }
                            val nowMs = System.currentTimeMillis()
                            if (!disposed.get() && !raw.isNullOrEmpty() &&
                                (raw != lastDelivered || nowMs - lastDeliveredAtMs > RESCAN_COOLDOWN_MS)
                            ) {
                                lastDelivered = raw
                                lastDeliveredAtMs = nowMs
                                // onDetected(raw) // Do not retain a stale callback.
                                detected.value(raw)
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                } catch (t: Throwable) {
                    android.util.Log.w("QrScanner", "Analyzer failure; frame dropped", t)
                    runCatching { proxy.close() }
                }
            }
            // provider.unbindAll() // Do not tear down another camera owner.
            boundCamera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (failure: Exception) {
                android.util.Log.w("QrScanner", "Camera preview unavailable", failure)
            }
        }
        providerFuture.addListener(listener, androidx.core.content.ContextCompat.getMainExecutor(context))
        onDispose {
            disposed.set(true)
            ownedAnalysis?.clearAnalyzer()
            try {
                // providerFuture.get().unbindAll() // Never block Main waiting for camera startup.
                if (providerFuture.isDone) {
                    val useCases = listOfNotNull<androidx.camera.core.UseCase>(ownedPreview, ownedAnalysis).toTypedArray()
                    if (useCases.isNotEmpty()) providerFuture.get().unbind(*useCases)
                }
            } catch (_: Exception) {
            }
            boundCamera = null
            scanner.close()
            executor.shutdown()
        }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (enableTorchToggle) {
            // Torch for low-light scanning, shown only when the bound back
            // camera reports a flash unit.
            val camera = boundCamera
            if (camera != null && camera.cameraInfo.hasFlashUnit()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                        .background(colors.surfaceElevated.copy(alpha = 0.95f), CircleShape) // Prior: Color.Black.copy(alpha = 0.5f),
                ) {
                    IconButton(
                        onClick = {
                            val target = !torchOn
                            runCatching { camera.cameraControl.enableTorch(target) }
                                .onSuccess { torchOn = target }
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        ChatyIcon(
                            if (torchOn) ChatyIconId.cameraFlashOff else ChatyIconId.cameraFlashOn,
                            semanticLabel = if (torchOn) "Turn torch off" else "Turn torch on",
                            color = colors.foreground, // Prior: Color.White
                        )
                    }
                }
            }
        }
    }
}
