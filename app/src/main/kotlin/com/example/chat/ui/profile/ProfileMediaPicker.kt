package com.example.chat.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.chat.data.services.ProfileMediaSource
import com.example.chat.ui.theme.LocalThemeConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Android pick -> crop -> compress pipeline for profile media, porting the
 * platform halves of lib/data/services/profile_media_service.dart
 * (image_picker + image_editor_cropper_dialog.dart + flutter_image_compress)
 * that the JVM service seam leaves to callers (api @ 3bee780d).
 *
 * Flow: gallery (PickVisualMedia) or camera (TakePicture via FileProvider) ->
 * cache file (downscaled to the Dart picker's square*1.8 bound, EXIF
 * orientation applied like the plugin) -> full-screen crop dialog (1:1
 * circle "Crop Avatar Photo" / 16:9 rounded "Crop Profile Banner", pan/zoom
 * 1..4x, 90-degree rotations) -> cropped PNG file (cropped_avatar|banner_<ts>.png
 * in cacheDir, zero-fill and empty checks like the Dart) -> caller uploads.
 *
 * OWNER WIRING REQUIRED: the camera path needs a FileProvider manifest entry
 * (authority "${context.packageName}.fileprovider") plus res/xml/file_paths.xml
 * covering cache-path; the media worker's batch already queues the same
 * FileProvider for chat attachments - reconcile to one provider.
 */

/** flutter_image_compress seam implementation for ProfileMediaService:
 *  decode with EXIF orientation applied (the plugin's default), scale so the
 *  SHORTER side is [square] preserving aspect (Dart minWidth/minHeight),
 *  encode JPEG at [quality] with metadata dropped (keepExif: false). */
object ProfileImageCompressor {
    fun compress(bytes: ByteArray, square: Int, quality: Int): ByteArray? {
        val decoded = decodeWithOrientation(bytes, minShortEdgePx = square) ?: return null
        val src = decoded
        var allocatedScale: Bitmap? = null
        try {
        val short = minOf(src.width, src.height)
        val scaled = if (short > square) {
            val ratio = square.toFloat() / short
            Bitmap.createScaledBitmap(
                src,
                (src.width * ratio).toInt().coerceAtLeast(1),
                (src.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            src
        }
        allocatedScale = scaled
        val out = java.io.ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return if (ok) out.toByteArray() else null
        } finally {
            allocatedScale?.takeIf { it !== src }?.recycle()
            src.recycle()
        }
    }

    /** Orientation matrix for [bytes]' EXIF flag (identity when none). */
    fun orientationMatrix(bytes: ByteArray): Matrix {
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }
    }

    /** Decode with EXIF orientation baked in (Image.file / the plugins all
     *  present the oriented bitmap). [minShortEdgePx] bounds the decode: the
     *  sampled result keeps its short edge at or above it, so a compressor
     *  targeting a [square] short side never allocates the full-size source
     *  (a 50 MP gallery photo is ~200 MB of heap, decoded twice here with the
     *  rotation copy). Rotation preserves the short side, so sampling before
     *  it changes nothing downstream. */
    fun decodeWithOrientation(bytes: ByteArray, minShortEdgePx: Int = Int.MAX_VALUE): Bitmap? {
        val bitmap = if (minShortEdgePx == Int.MAX_VALUE) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        } else {
            com.example.chat.ui.core.system.SampledBitmapDecoder.decodeShortEdge(bytes, minShortEdgePx) ?: return null
        }
        val matrix = orientationMatrix(bytes)
        if (matrix.isIdentity) return bitmap
        try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            return rotated
        } catch (failure: Throwable) { bitmap.recycle(); throw failure }
    }

    /** Apply a precomputed orientation matrix to an already-decoded bitmap. */
    fun applyOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val matrix = orientationMatrix(bytes)
        if (matrix.isIdentity) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}

private class PendingCrop(
    val file: File,
    val isAvatar: Boolean,
    val title: String,
)

class ProfileMediaPickerState internal constructor(
    private val launchGallery: () -> Unit,
    private val launchCamera: () -> Unit,
) {
    /** Start picking from [source]; the crop dialog follows automatically. */
    fun launch(source: ProfileMediaSource, isAvatar: Boolean, square: Int = pendingSquare) {
        pendingSquare = square
        pendingIsAvatar = isAvatar
        pendingTitle = if (isAvatar) "Crop Avatar Photo" else "Crop Profile Banner"
        when (source) {
            ProfileMediaSource.camera -> launchCamera()
            ProfileMediaSource.gallery -> launchGallery()
        }
    }

    internal var pendingIsAvatar: Boolean = true
    internal var pendingSquare: Int = 512
    internal var pendingTitle: String = "Crop Avatar Photo"
}

/**
 * Self-contained pick+crop host. [onResult] receives the cropped PNG File,
 * or null when the user cancelled at any step (matching the Dart
 * _CancelledException path). Rendered once per screen that offers
 * avatar/banner picking.
 */
@Composable
fun rememberProfileMediaPicker(
    onResult: (File?) -> Unit,
    onError: (String) -> Unit = {},
    square: Int = 512,
    isCurrent: () -> Boolean = { true },
): ProfileMediaPickerState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestCheck = androidx.compose.runtime.rememberUpdatedState(isCurrent)
    val files = remember {
        ProfileLocalOutputs.initialize(context)
        ProfilePickFiles(ProfileLocalOutputs::record, ProfileLocalOutputs::delete)
    }
    fun active() = files.isActive() && latestCheck.value() && ProfileLocalOutputs.state() is ProfilePhotoFeatureState.Ready
    androidx.compose.runtime.DisposableEffect(files) { onDispose { runCatching { files.close() }.onFailure { if (it is Exception) ProfileLocalOutputs.markBlocked(it) } } }
    var pendingCrop by remember { mutableStateOf<PendingCrop?>(null) }
    val state = remember { mutableStateOf<ProfileMediaPickerState?>(null) }

    fun ingest(uri: Uri?, tagIsAvatar: Boolean, tagTitle: String) {
        if (!active()) return
        if (uri == null) {
            onResult(null)
            return
        }
        scope.launch {
            val file = try {
                copyPickToCache(context, uri, state.value?.pendingSquare ?: square, files)
            } catch (tooLarge: PickTooLargeException) {
                if (active()) onError(context.getString(com.example.chat.R.string.profile_pick_too_large))
                return@launch
            }
            if (!active()) { files.close(); return@launch }
            if (file == null) {
                onError(context.getString(com.example.chat.R.string.profile_pick_inaccessible))
            } else {
                pendingCrop = PendingCrop(file, tagIsAvatar, tagTitle)
            }
        }
    }

    // System photo picker: no storage permission, privacy-preserving (P1-06).
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val s = state.value ?: return@rememberLauncherForActivityResult
        ingest(uri, s.pendingIsAvatar, s.pendingTitle)
    }

    if (state.value == null) {
        // One-time init; launchers are captured by reference and stable.
        state.value = ProfileMediaPickerState(
            launchGallery = { check(active()); galleryLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            ) },
            launchCamera = {
                check(active())
                scope.launch {
                    try {
                        val captured = ProfileCameraResultBridge.pick()
                        if (captured == null) { if (active()) onResult(null); return@launch }
                        if (!active()) { captured.delete(); return@launch }
                        // Terminal external callback has fired; local owner can now own/delete it.
                        files.track(captured)
                        val bounded = boundPickedFile(context, captured, state.value?.pendingSquare ?: square, files)
                        if (!active()) { files.close(); return@launch }
                        if (bounded == null) onError(context.getString(com.example.chat.R.string.profile_pick_capture_unreadable))
                        else pendingCrop = PendingCrop(bounded, state.value?.pendingIsAvatar ?: true, state.value?.pendingTitle ?: "Crop Avatar Photo")
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { if (active()) onError(cameraErrorMessage(context, error)) }
                }

            },
        )
    }

    pendingCrop?.takeIf { active() }?.let { crop ->
        ProfileImageCropperOwnedDialog(
            sourceFile = crop.file,
            isAvatar = crop.isAvatar,
            title = crop.title,
            files = files,
            isCurrent = ::active,
            onDone = { result ->
                pendingCrop = null
                if (result == null) {
                    files.remove(crop.file)
                    if (active()) onResult(null)
                } else {
                    if (result.absolutePath != crop.file.absolutePath) files.remove(crop.file)
                    if (active() && files.transfer(result)) onResult(result)
                    else files.close()
                }
            },
            onError = onError,
        )
    }

    return state.value!!
}

/** image_picker half: copy the selected content into the cache dir, bounding
 *  the decode to square*1.8 on the long side (Dart maxWidth/maxHeight) and
 *  re-encoding JPEG at quality 95 (Dart imageQuality). EXIF orientation is
 *  applied, matching what Image.file shows. */
private suspend fun copyPickToCache(context: Context, uri: Uri, square: Int, files: ProfilePickFiles): File? =
    withContext(Dispatchers.IO) {
        // Bounded read: reject oversized sources before decoding (P1-15).
        var picked: ByteArray? = null
        try {
            picked = context.contentResolver.openInputStream(uri)?.use { stream ->
                readCapped(stream, MAX_PICK_BYTES) ?: throw PickTooLargeException()
            }
        } catch (tooLarge: PickTooLargeException) {
            throw tooLarge
        } catch (_: Exception) {
            picked = null
        }
        val bytes = picked ?: return@withContext null
        boundImageBytesToCache(context, bytes, square, files)
    }

private const val MAX_PICK_BYTES = 20L * 1024 * 1024

private class PickTooLargeException : Exception("That photo is too large. Choose one under 20 MB.")

/** Reads [stream] fully, or returns null as soon as it exceeds [max] bytes. */
private fun readCapped(stream: java.io.InputStream, max: Long): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val n = stream.read(buffer)
        if (n < 0) break
        total += n
        if (total > max) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** Draws [bitmap] onto opaque white so alpha never turns black when JPEG-encoded (P2-18). */
private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
    if (!bitmap.hasAlpha()) return bitmap
    val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return out
    } catch (failure: Throwable) { out.recycle(); throw failure }
}

private fun cameraErrorMessage(context: Context, error: Exception): String = when (error) {
    is android.content.ActivityNotFoundException -> context.getString(com.example.chat.R.string.profile_camera_unavailable)
    is SecurityException -> context.getString(com.example.chat.R.string.profile_camera_permission)
    else -> ProfileErrorMessages.friendly(context, error, context.getString(com.example.chat.R.string.profile_camera_failure))
}

/** Camera-capture variant (Dart image_picker bounds camera captures the same way). */
private suspend fun boundPickedFile(context: Context, file: File, square: Int, files: ProfilePickFiles): File? =
    withContext(Dispatchers.IO) {
        if (!files.tryBegin(file)) return@withContext null
        try {
            val bytes = file.readBytes()
            boundImageBytesToCache(context, bytes, square, files)
        } finally { files.end(file); if (files.isActive()) files.remove(file) }
    }

private fun boundImageBytesToCache(context: Context, bytes: ByteArray, square: Int, files: ProfilePickFiles): File? =
    runCatching {
            val bitmaps = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Bitmap, Boolean>())
            try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            // Memory bound (P4-05): long edge <= square*1.8 (avatar ~922px, banner ~2304px), ~4 bytes/px ARGB.
            val maxDim = (square * 1.8).toInt()
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return@runCatching null
            bitmaps.add(decoded)
            // Orientation flag lives on the ORIGINAL bytes.
            val oriented = ProfileImageCompressor.applyOrientation(decoded, bytes)
            bitmaps.add(oriented)
            val flat = flattenOnWhite(oriented)
            bitmaps.add(flat)
            val file = File(
                context.cacheDir,
                "chaty_pick_${System.currentTimeMillis()}.jpg",
            )
            files.track(file); files.begin(file)
            try { check(file.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, 95, it) }) { "Unable to encode profile photo" } }
            finally { files.end(file) }
            file
            } finally { bitmaps.forEach { if (!it.isRecycled) it.recycle() } }
        }.getOrNull()

/** Port of image_editor_cropper_dialog.dart as a full-screen dialog.
 *  The dialog window draws edge-to-edge, so the content column is padded by
 *  the safe-drawing + mandatory-gesture insets: the Cancel / Crop & Use row
 *  must stay fully above the gesture/3-button navigation area on every
 *  device (same inset policy as the camera and contact-picker screens). */
@Composable
fun ProfileImageCropperDialog(
    sourceFile: File,
    isAvatar: Boolean,
    title: String,
    onDone: (File?) -> Unit,
    onError: (String) -> Unit = {},
) = ProfileImageCropperOwnedDialog(sourceFile, isAvatar, title, onDone, onError, null, { true })

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ProfileImageCropperOwnedDialog(
    sourceFile: File, isAvatar: Boolean, title: String, onDone: (File?) -> Unit, onError: (String) -> Unit,
    files: ProfilePickFiles?, isCurrent: () -> Boolean,
) {
    val theme = LocalThemeConfig.current
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val accentStroke = remember(theme.accentColor) { Color(theme.accentColor).copy(alpha = 0.8f) }

    var quarterRotations by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }

    // Decode away from Main and pin the source against concurrent discard/account teardown.
    val currentPreviewCheck = androidx.compose.runtime.rememberUpdatedState(isCurrent)
    val preview = androidx.compose.runtime.produceState<Pair<ImageBitmap?, Boolean>>(null to false, sourceFile, files) {
        value = null to false // produceState retains its old value when a key changes.
        var unpublished: Bitmap? = null
        var pinned = false
        try {
            if (files != null) {
                if (!files.tryBegin(sourceFile)) {
                    if (currentPreviewCheck.value()) value = null to true
                    return@produceState
                }
                pinned = true
            }
            withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
                check(bounds.outWidth > 0 && bounds.outHeight > 0)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > 1280) sample *= 2
                unpublished = BitmapFactory.decodeFile(sourceFile.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample })
                checkNotNull(unpublished)
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (currentPreviewCheck.value()) {
                value = checkNotNull(unpublished).asImageBitmap() to false
                // Published pixels may still be held by Compose; only unpublished pixels are recycled here.
                unpublished = null
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { if (currentPreviewCheck.value()) value = null to true }
        finally {
            unpublished?.let { if (!it.isRecycled) it.recycle() }
            if (pinned) files?.end(sourceFile)
        }
    }.value
    val image = preview.first
    val srcW = image?.width?.toFloat() ?: 1f
    val srcH = image?.height?.toFloat() ?: 1f

    Dialog(
        onDismissRequest = { if (!isProcessing) onDone(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures)),
        ) {
            // App bar: close / title / reset / rotate.
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (!isProcessing) onDone(null) }) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close, semanticLabel = context.getString(com.example.chat.R.string.profile_crop_close), color = colors.foreground)
                }
                Text(
                    context.getString(if (isAvatar) com.example.chat.R.string.profile_crop_avatar_title else com.example.chat.R.string.profile_crop_banner_title),
                    color = colors.foreground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(enabled = !isProcessing, onClick = {
                    userScale = 1f
                    userOffset = Offset.Zero
                    quarterRotations = 0
                }) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.reset, semanticLabel = context.getString(com.example.chat.R.string.profile_crop_reset), color = colors.foregroundSecondary)
                }
                IconButton(enabled = !isProcessing, onClick = { quarterRotations = (quarterRotations + 1) % 4 }) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.rotateRight, semanticLabel = context.getString(com.example.chat.R.string.profile_crop_rotate), color = colors.foreground)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                // P3-50: step cue added (pick is step 1, crop is step 2).
                // if (isAvatar) "Pinch or drag to position inside the circular avatar"
                // else "Pinch or drag to frame the banner",
                if (isAvatar) context.getString(com.example.chat.R.string.profile_crop_avatar_instruction)
                else context.getString(com.example.chat.R.string.profile_crop_banner_instruction),
                textAlign = TextAlign.Center,
                color = colors.foregroundSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))

            // Crop frame sizing (Dart MediaQuery width - 48; banner 16:9).
            val cropBoxWidthDp = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 48).dp
            val cropBoxHeightDp = if (isAvatar) cropBoxWidthDp else cropBoxWidthDp * 9 / 16
            // Cached px values (P4-18): recomputed only when density or frame size changes.
            val cropWpx = remember(density, cropBoxWidthDp) { with(density) { cropBoxWidthDp.toPx() } }
            val cropHpx = remember(density, cropBoxHeightDp) { with(density) { cropBoxHeightDp.toPx() } }

            // Cover-fit display sizing and clamp - the Dart formulas, JVM-tested
            // in ProfileCropGeometryTest.
            val layout = ProfileCropGeometry.layout(srcW, srcH, quarterRotations, cropWpx, cropHpx)
            val childW = layout.childW
            val childH = layout.childH

            fun clampOffset(o: Offset, sc: Float): Offset {
                val (cx, cy) = ProfileCropGeometry.clampOffset(
                    o.x, o.y, sc, layout.footW, layout.footH, cropWpx, cropHpx,
                )
                return Offset(cx, cy)
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        // Rendered-bounds marker for ProfileCropAspectProofTest: the
                        // export contract is 2.5x of this rendered crop window.
                        .semantics {
                            testTagsAsResourceId = true
                            // Switch/TalkBack users can zoom without pinching (P1-36).
                            stateDescription = context.getString(com.example.chat.R.string.profile_crop_zoom_value, userScale)
                            customActions = listOf(
                                CustomAccessibilityAction(context.getString(com.example.chat.R.string.profile_crop_zoom_in)) {
                                    if (isProcessing) return@CustomAccessibilityAction false
                                    val next = (userScale + 0.5f).coerceAtMost(4f)
                                    userScale = next
                                    userOffset = clampOffset(userOffset, next)
                                    true
                                },
                                CustomAccessibilityAction(context.getString(com.example.chat.R.string.profile_crop_zoom_out)) {
                                    if (isProcessing) return@CustomAccessibilityAction false
                                    val next = (userScale - 0.5f).coerceAtLeast(1f)
                                    userScale = next
                                    userOffset = clampOffset(userOffset, next)
                                    true
                                },
                            )
                        }
                        .testTag("profile-crop-window")
                        // Dart BoxShadow blur 24 spread 8 black 0.6 (approximation:
                        // Compose shadow has no spread radius).
                        .shadow(
                            24.dp,
                            RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 16.dp),
                            clip = false,
                            ambientColor = colors.foreground.copy(alpha = 0.6f),
                            spotColor = colors.foreground.copy(alpha = 0.6f),
                        )
                        .size(cropBoxWidthDp, cropBoxHeightDp)
                        .background(colors.surfaceSecondary, RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 16.dp))
                        .border(
                            2.5.dp,
                            accentStroke,
                            RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 16.dp),
                        )
                        // Themed outline; pixel composition remains unchanged.
                        .border(
                            1.dp,
                            colors.borderSubtle,
                            RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 16.dp),
                        )
                        .clip(RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 14.dp))
                        // Double-tap resets zoom and position (P1-07).
                        .pointerInput(isProcessing) {
                            detectTapGestures(onDoubleTap = reset@{
                                if (isProcessing) return@reset
                                userScale = 1f
                                userOffset = Offset.Zero
                            })
                        }
                        .pointerInput(quarterRotations, isProcessing, cropWpx, cropHpx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (isProcessing) return@detectTransformGestures
                                val newScale = (userScale * zoom).coerceIn(1f, 4f)
                                userScale = newScale
                                userOffset = clampOffset(userOffset + pan, newScale)
                            }
                        },
                ) {
                    if (image == null) {
                        if (preview.second) Text(
                            context.getString(com.example.chat.R.string.profile_crop_preview_failure),
                            color = colors.foreground,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        ) else CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.primary)
                    }
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .align(Alignment.Center)
                                // requiredSize, not size: the cover-fit child is
                                // larger than the crop window on one axis, and
                                // size() would clamp it to the window and let
                                // FillBounds squash the image into it.
                                .requiredSize(
                                    with(density) { childW.toDp() },
                                    with(density) { childH.toDp() },
                                )
                                .graphicsLayer {
                                    scaleX = userScale
                                    scaleY = userScale
                                    translationX = userOffset.x
                                    translationY = userOffset.y
                                    rotationZ = quarterRotations * 90f
                                },
                        )
                    }
                }
            }

            // Slider alternative to pinch-zoom (WCAG 2.5.1 pointer gestures, P1-07).
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(context.getString(com.example.chat.R.string.profile_crop_zoom), color = colors.foregroundSecondary, fontSize = 13.sp)
                Slider(
                    value = userScale,
                    onValueChange = {
                        userScale = it
                        userOffset = clampOffset(userOffset, it)
                    },
                    valueRange = 1f..4f,
                    enabled = !isProcessing,
                    colors = SliderDefaults.colors(
                        thumbColor = accentStroke,
                        activeTrackColor = accentStroke,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .semantics { stateDescription = context.getString(com.example.chat.R.string.profile_crop_zoom_value, userScale) },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                OutlinedButton(
                    onClick = { if (!isProcessing) onDone(null) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.foregroundSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text(context.getString(com.example.chat.R.string.profile_crop_cancel), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(14.dp))
                Button(
                    onClick = {
                        if (isProcessing || image == null) return@Button
                        isProcessing = true
                        scope.launch {
                            try {
                                val out = captureCrop(
                                    context = context,
                                    sourceFile = sourceFile,
                                    isAvatar = isAvatar,
                                    quarterRotations = quarterRotations,
                                    userScale = userScale,
                                    userOffset = userOffset,
                                    cropWpx = cropWpx,
                                    cropHpx = cropHpx,
                                    files = files,
                                )
                                isProcessing = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isCurrent()) onDone(out) else files?.close()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                if (isCurrent()) onError(context.getString(com.example.chat.R.string.profile_crop_failure, ProfileErrorMessages.friendly(context, e, context.getString(com.example.chat.R.string.profile_retry))))
                            } finally { isProcessing = false }
                        }
                    },
                    enabled = !isProcessing && image != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.onPrimary,
                        )
                    } else {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.check, semanticLabel = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isProcessing) context.getString(com.example.chat.R.string.profile_crop_processing) else context.getString(com.example.chat.R.string.profile_crop_use),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Off-screen equivalent of the Dart RepaintBoundary.toImage(pixelRatio: 2.5):
 * renders the transformed bitmap into a crop-sized PNG at 2.5x. The transform
 * chain mirrors the Compose graphicsLayer (center -> rotate -> scale ->
 * translate offset), the layout cover-fit and the boundary clamp guarantee
 * the image covers the crop window. Output naming and zero-length checks
 * match the Dart _captureAndSaveCrop.
 */
private suspend fun captureCrop(
    context: Context,
    sourceFile: File,
    isAvatar: Boolean,
    quarterRotations: Int,
    userScale: Float,
    userOffset: Offset,
    cropWpx: Float,
    cropHpx: Float,
    files: ProfilePickFiles? = null,
): File = withContext(Dispatchers.IO) {
    if (files != null && !files.tryBegin(sourceFile)) throw kotlinx.coroutines.CancellationException("Profile picker closed")
    val bitmaps = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Bitmap, Boolean>())
    try {
    // Full-quality decode (bounded) with EXIF orientation applied.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw Exception(context.getString(com.example.chat.R.string.profile_pick_inaccessible))
    }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > 4096) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = BitmapFactory.decodeFile(sourceFile.absolutePath, opts)
        ?: throw Exception(context.getString(com.example.chat.R.string.profile_pick_inaccessible))
    bitmaps.add(raw)
    val bitmap = ProfileImageCompressor.applyOrientation(raw, sourceFile.readBytes())
    bitmaps.add(bitmap)

    val layout = ProfileCropGeometry.layout(
        bitmap.width.toFloat(), bitmap.height.toFloat(), quarterRotations, cropWpx, cropHpx,
    )
    val childW = layout.childW
    val childH = layout.childH

    val (outW, outH) = ProfileCropGeometry.captureSize(cropWpx, cropHpx)
    val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    bitmaps.add(output)
    val canvas = android.graphics.Canvas(output)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.scale(2.5f, 2.5f)
    canvas.translate(cropWpx / 2f + userOffset.x, cropHpx / 2f + userOffset.y)
    canvas.scale(userScale, userScale)
    canvas.rotate(quarterRotations * 90f)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
        bitmap,
        null,
        android.graphics.RectF(-childW / 2f, -childH / 2f, childW / 2f, childH / 2f),
        paint,
    )
    bitmap.recycle()

    val png = java.io.ByteArrayOutputStream()
    if (!output.compress(Bitmap.CompressFormat.PNG, 100, png)) {
        output.recycle()
        throw Exception(context.getString(com.example.chat.R.string.profile_crop_encode_failure))
    }
    output.recycle()
    val buffer = png.toByteArray()
    if (buffer.isEmpty()) {
        throw Exception(context.getString(com.example.chat.R.string.profile_crop_empty))
    }
    val tag = if (isAvatar) "avatar" else "banner"
    val file = File(
        context.cacheDir,
        "cropped_" + tag + "_" + System.currentTimeMillis() + ".png",
    )
    files?.track(file); files?.begin(file)
    try { file.writeBytes(buffer) } finally { files?.end(file) }
    if (file.length() == 0L) {
        throw Exception(context.getString(com.example.chat.R.string.profile_crop_save_failure))
    }
    file
    } finally {
        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        files?.end(sourceFile)
    }
}

/* Pre-cleanup implementation retained for audit history (not active):
package com.example.chat.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.chat.data.services.ProfileMediaSource
import com.example.chat.ui.theme.LocalThemeConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/ **
 * Android pick -> crop -> compress pipeline for profile media, porting the
 * platform halves of lib/data/services/profile_media_service.dart
 * (image_picker + image_editor_cropper_dialog.dart + flutter_image_compress)
 * that the JVM service seam leaves to callers (api @ 3bee780d).
 *
 * Flow: gallery (PickVisualMedia) or camera (TakePicture via FileProvider) ->
 * cache file (downscaled to the Dart picker's square*1.8 bound, EXIF
 * orientation applied like the plugin) -> full-screen crop dialog (1:1
 * circle "Crop Avatar Photo" / 16:9 rounded "Crop Profile Banner", pan/zoom
 * 1..4x, 90-degree rotations) -> cropped PNG file (cropped_avatar|banner_<ts>.png
 * in cacheDir, zero-fill and empty checks like the Dart) -> caller uploads.
 *
 * OWNER WIRING REQUIRED: the camera path needs a FileProvider manifest entry
 * (authority "${context.packageName}.fileprovider") plus res/xml/file_paths.xml
 * covering cache-path; the media worker's batch already queues the same
 * FileProvider for chat attachments - reconcile to one provider.
 * /

/ ** flutter_image_compress seam implementation for ProfileMediaService:
 *  decode with EXIF orientation applied (the plugin's default), scale so the
 *  SHORTER side is [square] preserving aspect (Dart minWidth/minHeight),
 *  encode JPEG at [quality] with metadata dropped (keepExif: false). * /
object ProfileImageCompressor {
    fun compress(bytes: ByteArray, square: Int, quality: Int): ByteArray? {
        val decoded = decodeWithOrientation(bytes, minShortEdgePx = square) ?: return null
        val src = decoded
        val short = minOf(src.width, src.height)
        val scaled = if (short > square) {
            val ratio = square.toFloat() / short
            Bitmap.createScaledBitmap(
                src,
                (src.width * ratio).toInt().coerceAtLeast(1),
                (src.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            src
        }
        val out = java.io.ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== src) scaled.recycle()
        src.recycle()
        return if (ok) out.toByteArray() else null
    }

    / ** Orientation matrix for [bytes]' EXIF flag (identity when none). * /
    fun orientationMatrix(bytes: ByteArray): Matrix {
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }
    }

    / ** Decode with EXIF orientation baked in (Image.file / the plugins all
     *  present the oriented bitmap). [minShortEdgePx] bounds the decode: the
     *  sampled result keeps its short edge at or above it, so a compressor
     *  targeting a [square] short side never allocates the full-size source
     *  (a 50 MP gallery photo is ~200 MB of heap, decoded twice here with the
     *  rotation copy). Rotation preserves the short side, so sampling before
     *  it changes nothing downstream. * /
    fun decodeWithOrientation(bytes: ByteArray, minShortEdgePx: Int = Int.MAX_VALUE): Bitmap? {
        val bitmap = if (minShortEdgePx == Int.MAX_VALUE) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        } else {
            com.example.chat.ui.core.system.SampledBitmapDecoder.decodeShortEdge(bytes, minShortEdgePx) ?: return null
        }
        val matrix = orientationMatrix(bytes)
        if (matrix.isIdentity) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    / ** Apply a precomputed orientation matrix to an already-decoded bitmap. * /
    fun applyOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val matrix = orientationMatrix(bytes)
        if (matrix.isIdentity) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}

private class PendingCrop(
    val file: File,
    val isAvatar: Boolean,
    val title: String,
)

class ProfileMediaPickerState internal constructor(
    private val launchGallery: () -> Unit,
    private val launchCamera: () -> Unit,
) {
    / ** Start picking from [source]; the crop dialog follows automatically. * /
    fun launch(source: ProfileMediaSource, isAvatar: Boolean, square: Int = pendingSquare) {
        pendingSquare = square
        pendingIsAvatar = isAvatar
        pendingTitle = if (isAvatar) "Crop Avatar Photo" else "Crop Profile Banner"
        when (source) {
            ProfileMediaSource.camera -> launchCamera()
            ProfileMediaSource.gallery -> launchGallery()
        }
    }

    internal var pendingIsAvatar: Boolean = true
    internal var pendingSquare: Int = 512
    internal var pendingTitle: String = "Crop Avatar Photo"
}

/ **
 * Self-contained pick+crop host. [onResult] receives the cropped PNG File,
 * or null when the user cancelled at any step (matching the Dart
 * _CancelledException path). Rendered once per screen that offers
 * avatar/banner picking.
 * /
@Composable
fun rememberProfileMediaPicker(
    onResult: (File?) -> Unit,
    onError: (String) -> Unit = {},
    square: Int = 512,
    isCurrent: () -> Boolean = { true },
): ProfileMediaPickerState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestCheck = androidx.compose.runtime.rememberUpdatedState(isCurrent)
    val files = remember {
        ProfileLocalOutputs.initialize(context)
        ProfilePickFiles(ProfileLocalOutputs::record, ProfileLocalOutputs::delete)
    }
    fun active() = files.isActive() && latestCheck.value() && ProfileLocalOutputs.state() is ProfilePhotoFeatureState.Ready
    androidx.compose.runtime.DisposableEffect(files) { onDispose { runCatching { files.close() }.onFailure { if (it is Exception) ProfileLocalOutputs.markBlocked(it) } } }
    var pendingCrop by remember { mutableStateOf<PendingCrop?>(null) }
    val state = remember { mutableStateOf<ProfileMediaPickerState?>(null) }

    fun ingest(uri: Uri?, tagIsAvatar: Boolean, tagTitle: String) {
        if (!active()) return
        if (uri == null) {
            onResult(null)
            return
        }
        scope.launch {
            val file = try {
                copyPickToCache(context, uri, state.value?.pendingSquare ?: square, files)
            } catch (tooLarge: PickTooLargeException) {
                if (active()) onError(tooLarge.message ?: "That photo is too large.")
                return@launch
            }
            if (!active()) { files.close(); return@launch }
            if (file == null) {
                onError("The selected image is no longer accessible.")
            } else {
                pendingCrop = PendingCrop(file, tagIsAvatar, tagTitle)
            }
        }
    }

    // System photo picker: no storage permission, privacy-preserving (P1-06).
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val s = state.value ?: return@rememberLauncherForActivityResult
        ingest(uri, s.pendingIsAvatar, s.pendingTitle)
    }

    if (state.value == null) {
        // One-time init; launchers are captured by reference and stable.
        state.value = ProfileMediaPickerState(
            launchGallery = { check(active()); galleryLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            ) },
            launchCamera = {
                check(active())
                scope.launch {
                    try {
                        val captured = ProfileCameraResultBridge.pick()
                        if (captured == null) { if (active()) onResult(null); return@launch }
                        if (!active()) { captured.delete(); return@launch }
                        // Terminal external callback has fired; local owner can now own/delete it.
                        files.track(captured)
                        val bounded = boundPickedFile(context, captured, state.value?.pendingSquare ?: square, files)
                        if (!active()) { files.close(); return@launch }
                        if (bounded == null) onError("The captured image could not be read.")
                        else pendingCrop = PendingCrop(bounded, state.value?.pendingIsAvatar ?: true, state.value?.pendingTitle ?: "Crop Avatar Photo")
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { if (active()) onError(cameraErrorMessage(error)) }
                }

            },
        )
    }

    pendingCrop?.takeIf { active() }?.let { crop ->
        ProfileImageCropperOwnedDialog(
            sourceFile = crop.file,
            isAvatar = crop.isAvatar,
            title = crop.title,
            files = files,
            isCurrent = ::active,
            onDone = { result ->
                pendingCrop = null
                if (result == null) {
                    files.remove(crop.file)
                    if (active()) onResult(null)
                } else {
                    if (result.absolutePath != crop.file.absolutePath) files.remove(crop.file)
                    if (active() && files.transfer(result)) onResult(result)
                    else files.close()
                }
            },
            onError = onError,
        )
    }

    return state.value!!
}

/ ** image_picker half: copy the selected content into the cache dir, bounding
 *  the decode to square*1.8 on the long side (Dart maxWidth/maxHeight) and
 *  re-encoding JPEG at quality 95 (Dart imageQuality). EXIF orientation is
 *  applied, matching what Image.file shows. * /
private suspend fun copyPickToCache(context: Context, uri: Uri, square: Int, files: ProfilePickFiles): File? =
    withContext(Dispatchers.IO) {
        // Bounded read: reject oversized sources before decoding (P1-15).
        var picked: ByteArray? = null
        try {
            picked = context.contentResolver.openInputStream(uri)?.use { stream ->
                readCapped(stream, MAX_PICK_BYTES) ?: throw PickTooLargeException()
            }
        } catch (tooLarge: PickTooLargeException) {
            throw tooLarge
        } catch (_: Exception) {
            picked = null
        }
        val bytes = picked ?: return@withContext null
        boundImageBytesToCache(context, bytes, square, files)
    }

private const val MAX_PICK_BYTES = 20L * 1024 * 1024

private class PickTooLargeException : Exception("That photo is too large. Choose one under 20 MB.")

/ ** Reads [stream] fully, or returns null as soon as it exceeds [max] bytes. * /
private fun readCapped(stream: java.io.InputStream, max: Long): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val n = stream.read(buffer)
        if (n < 0) break
        total += n
        if (total > max) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/ ** Draws [bitmap] onto opaque white so alpha never turns black when JPEG-encoded (P2-18). * /
private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
    if (!bitmap.hasAlpha()) return bitmap
    val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawBitmap(bitmap, 0f, 0f, null)
    return out
}

private fun cameraErrorMessage(error: Exception): String = when (error) {
    is android.content.ActivityNotFoundException -> "No camera app is available on this device."
    is SecurityException -> "Camera permission is needed to take a photo. You can allow it in Settings."
    else -> ProfileErrorMessages.friendly(error, "Could not capture profile photo.")
}

/ ** Camera-capture variant (Dart image_picker bounds camera captures the same way). * /
private suspend fun boundPickedFile(context: Context, file: File, square: Int, files: ProfilePickFiles): File? =
    withContext(Dispatchers.IO) {
        if (!files.tryBegin(file)) return@withContext null
        try {
            val bytes = file.readBytes()
            boundImageBytesToCache(context, bytes, square, files)
        } finally { files.end(file); if (files.isActive()) files.remove(file) }
    }

private fun boundImageBytesToCache(context: Context, bytes: ByteArray, square: Int, files: ProfilePickFiles): File? =
    runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            // Memory bound (P4-05): long edge <= square*1.8 (avatar ~922px, banner ~2304px), ~4 bytes/px ARGB.
            val maxDim = (square * 1.8).toInt()
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return@runCatching null
            // Orientation flag lives on the ORIGINAL bytes.
            val oriented = ProfileImageCompressor.applyOrientation(decoded, bytes)
            val flat = flattenOnWhite(oriented)
            val file = File(
                context.cacheDir,
                "chaty_pick_${System.currentTimeMillis()}.jpg",
            )
            files.track(file); files.begin(file)
            try { file.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
            finally { files.end(file) }
            if (flat !== oriented) flat.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
            file
        }.getOrNull()

/ ** Port of image_editor_cropper_dialog.dart as a full-screen dialog.
 *  The dialog window draws edge-to-edge, so the content column is padded by
 *  the safe-drawing + mandatory-gesture insets: the Cancel / Crop & Use row
 *  must stay fully above the gesture/3-button navigation area on every
 *  device (same inset policy as the camera and contact-picker screens). * /
@Composable
fun ProfileImageCropperDialog(
    sourceFile: File,
    isAvatar: Boolean,
    title: String,
    onDone: (File?) -> Unit,
    onError: (String) -> Unit = {},
) = ProfileImageCropperOwnedDialog(sourceFile, isAvatar, title, onDone, onError, null, { true })

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ProfileImageCropperOwnedDialog(
    sourceFile: File, isAvatar: Boolean, title: String, onDone: (File?) -> Unit, onError: (String) -> Unit,
    files: ProfilePickFiles?, isCurrent: () -> Boolean,
) {
    val theme = LocalThemeConfig.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val accentStroke = remember(theme.accentColor) { Color(theme.accentColor).copy(alpha = 0.8f) }

    var quarterRotations by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }

    // Decode once, bounded for display (Dart instantiates at 1280 target).
    val image: ImageBitmap? = remember(sourceFile) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > 1280) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(sourceFile.absolutePath, opts)?.asImageBitmap()
        }.getOrNull()
    }
    val srcW = image?.width?.toFloat() ?: 1f
    val srcH = image?.height?.toFloat() ?: 1f

    Dialog(
        onDismissRequest = { if (!isProcessing) onDone(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures)),
        ) {
            // App bar: close / title / reset / rotate.
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (!isProcessing) onDone(null) }) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close, semanticLabel = "Close", color = Color.White)
                }
                Text(
                    title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = {
                    userScale = 1f
                    userOffset = Offset.Zero
                    quarterRotations = 0
                }) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.reset, semanticLabel = "Reset", color = Color.White.copy(alpha = 0.7f))
                }
                IconButton(onClick = { quarterRotations = (quarterRotations + 1) % 4 }) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.rotateRight, semanticLabel = "Rotate 90°", color = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                // P3-50: step cue added (pick is step 1, crop is step 2).
                // if (isAvatar) "Pinch or drag to position inside the circular avatar"
                // else "Pinch or drag to frame the banner",
                if (isAvatar) "Step 2 of 2: pinch or drag to position inside the circular avatar"
                else "Step 2 of 2: pinch or drag to frame the banner",
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))

            // Crop frame sizing (Dart MediaQuery width - 48; banner 16:9).
            val cropBoxWidthDp = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 48).dp
            val cropBoxHeightDp = if (isAvatar) cropBoxWidthDp else cropBoxWidthDp * 9 / 16
            // Cached px values (P4-18): recomputed only when density or frame size changes.
            val cropWpx = remember(density, cropBoxWidthDp) { with(density) { cropBoxWidthDp.toPx() } }
            val cropHpx = remember(density, cropBoxHeightDp) { with(density) { cropBoxHeightDp.toPx() } }

            // Cover-fit display sizing and clamp - the Dart formulas, JVM-tested
            // in ProfileCropGeometryTest.
            val layout = ProfileCropGeometry.layout(srcW, srcH, quarterRotations, cropWpx, cropHpx)
            val childW = layout.childW
            val childH = layout.childH

            fun clampOffset(o: Offset, sc: Float): Offset {
                val (cx, cy) = ProfileCropGeometry.clampOffset(
                    o.x, o.y, sc, layout.footW, layout.footH, cropWpx, cropHpx,
                )
                return Offset(cx, cy)
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        // Rendered-bounds marker for ProfileCropAspectProofTest: the
                        // export contract is 2.5x of this rendered crop window.
                        .semantics {
                            testTagsAsResourceId = true
                            // Switch/TalkBack users can zoom without pinching (P1-36).
                            stateDescription = "Zoom " + "%.1f".format(userScale) + "x"
                            customActions = listOf(
                                CustomAccessibilityAction("Zoom in") {
                                    val next = (userScale + 0.5f).coerceAtMost(4f)
                                    userScale = next
                                    userOffset = clampOffset(userOffset, next)
                                    true
                                },
                                CustomAccessibilityAction("Zoom out") {
                                    val next = (userScale - 0.5f).coerceAtLeast(1f)
                                    userScale = next
                                    userOffset = clampOffset(userOffset, next)
                                    true
                                },
                            )
                        }
                        .testTag("profile-crop-window")
                        // Dart BoxShadow blur 24 spread 8 black 0.6 (approximation:
                        // Compose shadow has no spread radius).
                        .shadow(
                            24.dp,
                            RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 16.dp),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.6f),
                            spotColor = Color.Black.copy(alpha = 0.6f),
                        )
                        .size(cropBoxWidthDp, cropBoxHeightDp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 16.dp))
                        .border(
                            2.5.dp,
                            accentStroke,
                            RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 16.dp),
                        )
                        // Thin white outer edge so the frame stays visible on dark photos (P1-56).
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.9f),
                            RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 16.dp),
                        )
                        .clip(RoundedCornerShape(if (isAvatar) cropBoxWidthDp / 2 else 14.dp))
                        // Double-tap resets zoom and position (P1-07).
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                userScale = 1f
                                userOffset = Offset.Zero
                            })
                        }
                        .pointerInput(quarterRotations) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (userScale * zoom).coerceIn(1f, 4f)
                                userScale = newScale
                                userOffset = clampOffset(userOffset + pan, newScale)
                            }
                        },
                ) {
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .align(Alignment.Center)
                                // requiredSize, not size: the cover-fit child is
                                // larger than the crop window on one axis, and
                                // size() would clamp it to the window and let
                                // FillBounds squash the image into it.
                                .requiredSize(
                                    with(density) { childW.toDp() },
                                    with(density) { childH.toDp() },
                                )
                                .graphicsLayer {
                                    scaleX = userScale
                                    scaleY = userScale
                                    translationX = userOffset.x
                                    translationY = userOffset.y
                                    rotationZ = quarterRotations * 90f
                                },
                        )
                    }
                }
            }

            // Slider alternative to pinch-zoom (WCAG 2.5.1 pointer gestures, P1-07).
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Zoom", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Slider(
                    value = userScale,
                    onValueChange = {
                        userScale = it
                        userOffset = clampOffset(userOffset, it)
                    },
                    valueRange = 1f..4f,
                    enabled = !isProcessing,
                    colors = SliderDefaults.colors(
                        thumbColor = accentStroke,
                        activeTrackColor = accentStroke,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .semantics { stateDescription = "%.1fx".format(userScale) },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                OutlinedButton(
                    onClick = { if (!isProcessing) onDone(null) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(14.dp))
                Button(
                    onClick = {
                        if (isProcessing) return@Button
                        isProcessing = true
                        scope.launch {
                            try {
                                val out = captureCrop(
                                    context = context,
                                    sourceFile = sourceFile,
                                    isAvatar = isAvatar,
                                    quarterRotations = quarterRotations,
                                    userScale = userScale,
                                    userOffset = userOffset,
                                    cropWpx = cropWpx,
                                    cropHpx = cropHpx,
                                    files = files,
                                )
                                isProcessing = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isCurrent()) onDone(out) else files?.close()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                isProcessing = false
                                onError("Failed to save cropped image. " + ProfileErrorMessages.friendly(e, "Please try again."))
                            }
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(theme.accentColor),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.check, semanticLabel = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isProcessing) "Cropping…" else "Crop & Use",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/ **
 * Off-screen equivalent of the Dart RepaintBoundary.toImage(pixelRatio: 2.5):
 * renders the transformed bitmap into a crop-sized PNG at 2.5x. The transform
 * chain mirrors the Compose graphicsLayer (center -> rotate -> scale ->
 * translate offset), the layout cover-fit and the boundary clamp guarantee
 * the image covers the crop window. Output naming and zero-length checks
 * match the Dart _captureAndSaveCrop.
 * /
private suspend fun captureCrop(
    context: Context,
    sourceFile: File,
    isAvatar: Boolean,
    quarterRotations: Int,
    userScale: Float,
    userOffset: Offset,
    cropWpx: Float,
    cropHpx: Float,
    files: ProfilePickFiles? = null,
): File = withContext(Dispatchers.IO) {
    if (files != null && !files.tryBegin(sourceFile)) throw kotlinx.coroutines.CancellationException("Profile picker closed")
    try {
    // Full-quality decode (bounded) with EXIF orientation applied.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw Exception("The selected image is no longer accessible.")
    }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > 4096) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = BitmapFactory.decodeFile(sourceFile.absolutePath, opts)
        ?: throw Exception("The selected image is no longer accessible.")
    val bitmap = ProfileImageCompressor.applyOrientation(raw, sourceFile.readBytes())

    val layout = ProfileCropGeometry.layout(
        bitmap.width.toFloat(), bitmap.height.toFloat(), quarterRotations, cropWpx, cropHpx,
    )
    val childW = layout.childW
    val childH = layout.childH

    val (outW, outH) = ProfileCropGeometry.captureSize(cropWpx, cropHpx)
    val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.scale(2.5f, 2.5f)
    canvas.translate(cropWpx / 2f + userOffset.x, cropHpx / 2f + userOffset.y)
    canvas.scale(userScale, userScale)
    canvas.rotate(quarterRotations * 90f)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
        bitmap,
        null,
        android.graphics.RectF(-childW / 2f, -childH / 2f, childW / 2f, childH / 2f),
        paint,
    )
    bitmap.recycle()

    val png = java.io.ByteArrayOutputStream()
    if (!output.compress(Bitmap.CompressFormat.PNG, 100, png)) {
        output.recycle()
        throw Exception("Unable to encode cropped image bytes")
    }
    output.recycle()
    val buffer = png.toByteArray()
    if (buffer.isEmpty()) {
        throw Exception("The crop is empty. Reposition the image inside the frame.")
    }
    val tag = if (isAvatar) "avatar" else "banner"
    val file = File(
        context.cacheDir,
        "cropped_" + tag + "_" + System.currentTimeMillis() + ".png",
    )
    files?.track(file); files?.begin(file)
    try { file.writeBytes(buffer) } finally { files?.end(file) }
    if (file.length() == 0L) {
        throw Exception("The crop could not be saved. Try again.")
    }
    file
    } finally { files?.end(sourceFile) }
}

*/

// Previous localization paths retained:
//                 if (active()) onError(tooLarge.message ?: "That photo is too large.")
//                 onError("The selected image is no longer accessible.")
//                         if (bounded == null) onError("The captured image could not be read.")
//                     catch (error: Exception) { if (active()) onError(cameraErrorMessage(error)) }
// private fun cameraErrorMessage(error: Exception): String = when (error) {
//     is android.content.ActivityNotFoundException -> "No camera app is available on this device."
//     is SecurityException -> "Camera permission is needed to take a photo. You can allow it in Settings."
//     else -> ProfileErrorMessages.friendly(error, "Could not capture profile photo.")
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close, semanticLabel = "Close", color = colors.foreground)
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.reset, semanticLabel = "Reset", color = colors.foregroundSecondary)
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.rotateRight, semanticLabel = "Rotate 90°", color = colors.foreground)
//                 if (isAvatar) "Step 2 of 2: pinch or drag to position inside the circular avatar"
//                 else "Step 2 of 2: pinch or drag to frame the banner",
//                             stateDescription = "Zoom " + "%.1f".format(userScale) + "x"
//                                 CustomAccessibilityAction("Zoom in") {
//                                 CustomAccessibilityAction("Zoom out") {
//                 Text("Zoom", color = colors.foregroundSecondary, fontSize = 13.sp)
//                         .semantics { stateDescription = "%.1fx".format(userScale) },
//                     Text("Cancel", fontWeight = FontWeight.SemiBold)
//                                 if (isCurrent()) onError("Failed to save cropped image. " + ProfileErrorMessages.friendly(e, "Please try again."))
//                         if (isProcessing) "Cropping…" else "Crop & Use",
//         throw Exception("The selected image is no longer accessible.")
//         ?: throw Exception("The selected image is no longer accessible.")

// Previous profile copy retained:
//         throw Exception("Unable to encode cropped image bytes")
//         throw Exception("The crop is empty. Reposition the image inside the frame.")
//         throw Exception("The crop could not be saved. Try again.")

// Previous Main-thread crop preview decode retained:
//     // Decode once, bounded for display (Dart instantiates at 1280 target).
//     val image: ImageBitmap? = remember(sourceFile) {
//         runCatching {
//             val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
//             BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
//             var sample = 1
//             while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > 1280) sample *= 2
//             val opts = BitmapFactory.Options().apply { inSampleSize = sample }
//             BitmapFactory.decodeFile(sourceFile.absolutePath, opts)?.asImageBitmap()
//         }.getOrNull()
//     }
//                         if (isProcessing) return@Button
//                     enabled = !isProcessing,
