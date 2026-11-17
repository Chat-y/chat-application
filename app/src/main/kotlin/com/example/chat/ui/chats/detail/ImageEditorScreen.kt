package com.example.chat.ui.chats.detail

// Single-line policy now used inside the shared multiline renderer.
// import com.example.chat.core.media.ImageTextOverlayPolicy
import com.example.chat.ui.kit.chatyPredictiveBack

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.semantics.*

/** Non-destructive editor. Apply returns a NEW cache file; no original or remote message is overwritten. */
@Composable
// MR-R3-044 previous signature: fun ImageEditorScreen(source: String, onCancel: () -> Unit, onApply: (File) -> Unit, outputFile: File? = null) {
fun ImageEditorScreen(source: String, onCancel: () -> Unit, onApply: (File) -> Unit, outputFile: File? = null, errorItemLabel: String = "Image") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var originalBitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var sourceMime by remember(source) { mutableStateOf<String?>(null) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    // Original Unit-key lifetime let an old save survive a source replacement.
    // var saveJob by remember { mutableStateOf<Job?>(null) }; var active by remember { mutableStateOf(true) }
    // DisposableEffect(Unit) { onDispose { active = false; saveJob?.cancel() } }
    var saveJob by remember(source) { mutableStateOf<Job?>(null) }
    var active by remember(source) { mutableStateOf(true) }
    val strokePictureCache = remember(source) { EditorStrokePictureCache() }
    val exportLineage = remember(source) { com.example.chat.ui.media.MediaExportLineage() }
    DisposableEffect(source) { onDispose { active = false; saveJob?.cancel(); exportLineage.close() } }
    fun cancelEditor() { saveJob?.cancel(); onCancel() }
    var saving by remember(source) { mutableStateOf(false) }
    var brightness by remember(source) { mutableFloatStateOf(0f) }
    var contrast by remember(source) { mutableFloatStateOf(1f) }
    var saturation by remember(source) { mutableFloatStateOf(1f) }
    var warmth by remember(source) { mutableFloatStateOf(0f) }
    var tint by remember(source) { mutableFloatStateOf(0f) }
    var fade by remember(source) { mutableFloatStateOf(0f) }
    var adjustMode by remember(source) { mutableStateOf(false) }
    // MR-R3-005 tool-mode disclosure only, independent of bitmap/edit state.
    var cropMode by remember(source) { mutableStateOf(false) }
    var adjustmentField by remember(source) { mutableStateOf("Brightness") }
    var adjustmentMenu by remember(source) { mutableStateOf(false) }
    var textOverlay by remember(source) { mutableStateOf("") }
    var textMode by remember(source) { mutableStateOf(false) }
    var textDetail by remember(source) { mutableStateOf("Content") }
    var drawDetail by remember(source) { mutableStateOf("Stroke") }
    var textX by remember(source) { mutableFloatStateOf(0.1f) }
    var textY by remember(source) { mutableFloatStateOf(0.3f) }
    var textScale by remember(source) { mutableFloatStateOf(0.07f) }
    var textAlignment by remember(source) { mutableStateOf(EditorTextAlignment.Left) }
    var textStrip by remember(source) { mutableStateOf(false) }
    var draw by remember(source) { mutableStateOf(false) }
    var ink by remember { mutableStateOf(Color.Red) }
    // Original outer list replacement: var strokes by remember(source) { mutableStateOf<List<EditorStroke>>(emptyList()) }
    val strokes = remember(source) { mutableStateListOf<EditorStroke>() }
    // This is intentionally stroke history only, not a false claim of crop/rotate/adjust history.
    val redoStrokes = remember(source) { mutableStateListOf<EditorStroke>() }
    var brushWidth by remember(source) { mutableFloatStateOf(0.008f) }
    var brushOpacity by remember(source) { mutableFloatStateOf(1f) }
    var paletteOpen by remember(source) { mutableStateOf(false) }
    var inkDetail by remember(source) { mutableStateOf("Preset") }
    var paletteHue by remember(source) { mutableFloatStateOf(0f) }
    var paletteSaturation by remember(source) { mutableFloatStateOf(1f) }
    var paletteValue by remember(source) { mutableFloatStateOf(1f) }
    var recentInk by remember(source) { mutableStateOf<List<Color>>(emptyList()) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Freeze the working decode budget for this source so rotation does not replace edited pixels.
    val decodeEdge = remember(source) { with(density) { maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.roundToPx() }.coerceIn(256, 2048) }
    LaunchedEffect(source) {
        try {
            // MR-R2-013: output size alone does not bound hostile source dimensions.
            require(withContext(Dispatchers.IO) {
                com.example.chat.ui.media.readAllowedMediaImageDimensions(context, source) != null
            }) { "Image dimensions are unsupported or too large" }
            sourceMime = withContext(Dispatchers.IO) { editorSourceMime(context, source) }
            bitmap = withContext(Dispatchers.IO) {
                // MR-R2-033 previous per-open loader lifecycle, retained inactive:
                // val loader = ImageLoader(context)
                // try {
                // val result = loader.execute(ImageRequest.Builder(context).data(source).allowHardware(false).size(decodeEdge)
                // .memoryCachePolicy(coil.request.CachePolicy.DISABLED).diskCachePolicy(coil.request.CachePolicy.DISABLED).build())
                // (result as? SuccessResult)?.drawable?.toBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
                // ?: throw IllegalStateException("Unable to load image for editing")
                // } finally { loader.shutdown() }
                // Reuse app loader, but preserve this sensitive editor request's disabled caches.
                val loader = coil.Coil.imageLoader(context)
                val result = loader.execute(ImageRequest.Builder(context).data(source).allowHardware(false).size(decodeEdge)
                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED).diskCachePolicy(coil.request.CachePolicy.DISABLED).build())
                (result as? SuccessResult)?.drawable?.toBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
                    ?: throw IllegalStateException("Unable to load image for editing")
            }
            originalBitmap = bitmap
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            // Original exception-message UI could expose a signed URL/provider path.
            // error = e.message ?: "Unable to load image"
            error = "Could not load this image. Close the editor and choose it again."
        }
    }
    val predictiveBack = com.example.chat.ui.kit.rememberChatyBackPresentation(source)
    // Previous registration retained inactive: androidx.activity.compose.BackHandler(onBack = ::cancelEditor)
    com.example.chat.ui.kit.ChatyPredictiveBackHandler(presentation = predictiveBack, onBack = ::cancelEditor)
    val detailLabels = mapOf(
        "Content" to stringResource(com.example.chat.R.string.media_released_editor_section_content),
        "Alignment" to stringResource(com.example.chat.R.string.media_released_editor_section_alignment),
        "Placement" to stringResource(com.example.chat.R.string.media_released_editor_section_placement),
        "Ink" to stringResource(com.example.chat.R.string.media_released_editor_section_ink),
        "Stroke" to stringResource(com.example.chat.R.string.media_released_editor_section_stroke),
        "Preset" to stringResource(com.example.chat.R.string.media_released_editor_section_preset),
        "Recent" to stringResource(com.example.chat.R.string.media_released_editor_section_recent),
        "Custom" to stringResource(com.example.chat.R.string.media_released_editor_section_custom),
    )
    val horizontalDescription = stringResource(com.example.chat.R.string.media_released_editor_horizontal_description)
    val verticalDescription = stringResource(com.example.chat.R.string.media_released_editor_vertical_description)
    val text_sizeDescription = stringResource(com.example.chat.R.string.media_released_editor_text_size_description)
    val ink_opacityDescription = stringResource(com.example.chat.R.string.media_released_editor_ink_opacity_description)
    val ink_hueDescription = stringResource(com.example.chat.R.string.media_released_editor_ink_hue_description)
    val ink_saturationDescription = stringResource(com.example.chat.R.string.media_released_editor_ink_saturation_description)
    val ink_brightnessDescription = stringResource(com.example.chat.R.string.media_released_editor_ink_brightness_description)
    val ink_previewDescription = stringResource(com.example.chat.R.string.media_released_editor_ink_preview_description)
    val brightnessDescription = stringResource(com.example.chat.R.string.media_released_editor_brightness_description)
    val contrastDescription = stringResource(com.example.chat.R.string.media_released_editor_contrast_description)
    val saturationDescription = stringResource(com.example.chat.R.string.media_released_editor_saturation_description)
    val warmthDescription = stringResource(com.example.chat.R.string.media_released_editor_warmth_description)
    val tintDescription = stringResource(com.example.chat.R.string.media_released_editor_tint_description)
    val fadeDescription = stringResource(com.example.chat.R.string.media_released_editor_fade_description)
    // MR-R4-043 prior: val editsSummary = "${strokes.size} strokes, saturation ${(saturation * 100).toInt()} percent, warmth ${(warmth * 100).toInt()} percent, tint ${(tint * 100).toInt()} percent, fade ${(fade * 100).toInt()} percent, brightness ${"%+.0f".format(brightness * 100)} percent, contrast ${"%.0f".format(contrast * 100)} percent, text alignment ${textAlignment.name}, text strip ${if (textStrip) "on" else "off"}, text: ${textOverlay.ifBlank { "none" }}"
    val summaryActionLabel = stringResource(com.example.chat.R.string.media_released_editor_summary_action)
    val summaryAlignment = stringResource(when (textAlignment) {
        EditorTextAlignment.Left -> com.example.chat.R.string.media_released_editor_align_left
        EditorTextAlignment.Center -> com.example.chat.R.string.media_released_editor_align_center
        EditorTextAlignment.Right -> com.example.chat.R.string.media_released_editor_align_right
    })
    val summaryStrip = stringResource(if (textStrip) com.example.chat.R.string.media_released_editor_summary_on else com.example.chat.R.string.media_released_editor_summary_off)
    val summaryNoText = stringResource(com.example.chat.R.string.media_released_editor_summary_none)
    val editsSummary = stringResource(com.example.chat.R.string.media_released_editor_summary,
        strokes.size, (saturation * 100).toInt(), (warmth * 100).toInt(), (tint * 100).toInt(), (fade * 100).toInt(),
        "%+.0f".format(brightness * 100), "%.0f".format(contrast * 100), summaryAlignment, summaryStrip, textOverlay.ifBlank { summaryNoText })
    val accessibility = androidx.compose.ui.platform.LocalView.current
    // MR-R3-049 debug overlay host; release composes no diagnostic content.
    Box(Modifier.chatyPredictiveBack(predictiveBack).fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().imePadding()
    // MR-R4-043 prior: .semantics { customActions = listOf(CustomAccessibilityAction("Edits summary") { accessibility.announceForAccessibility(editsSummary); true }) }) {
        .semantics { customActions = listOf(CustomAccessibilityAction(summaryActionLabel) { accessibility.announceForAccessibility(editsSummary); true }) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // MR-R4-043 prior: TextButton(::cancelEditor) { Text("Cancel") }
            TextButton(::cancelEditor) { Text(stringResource(com.example.chat.R.string.media_released_editor_cancel)) }
            // MR-R4-043 prior: Text("Edit a copy", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(stringResource(com.example.chat.R.string.media_released_editor_title), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (saving) return@TextButton
                val original = bitmap ?: return@TextButton
                val encoding = runCatching { editorOutputFormat(sourceMime,
                    android.net.Uri.parse(source).lastPathSegment?.substringAfterLast('.', ""),
                    original.hasAlpha(), outputFile?.extension) }.getOrElse {
                    error = "This destination format is unsupported. Close the editor and choose another destination."
                    return@TextButton
                }
                saving = true
                // Snapshot render values on main before entering IO. No mutable preview state in export.
                val export = EditorRenderSnapshot(brightness, contrast, strokes.toList(), textOverlay,
                    textX, textY, textScale, ink.toArgb(), saturation, warmth, tint, fade, textAlignment, textStrip)
                saveJob = scope.launch {
                    // val sourceExtension = android.net.Uri.parse(source).lastPathSegment?.substringAfterLast('.', "")?.lowercase(java.util.Locale.ROOT)
                    // val preserveAlpha = (original.hasAlpha() || sourceExtension == "png") && (outputFile == null || outputFile.extension.equals("png", true))
                    // val file = outputFile ?: File(context.cacheDir, "chaty-outgoing-edited-${java.util.UUID.randomUUID()}.${if (preserveAlpha) "png" else "jpg"}")
                    val file = outputFile ?: File(context.cacheDir, "chaty-outgoing-edited-${java.util.UUID.randomUUID()}.${encoding.extension}")
                    var handedOff = false
                    // Original check handled file:// only and ran before try/finally:
                    // val aliasesOriginal = sourceUri.scheme == "file" && file.canonicalPath == File(sourceUri.path.orEmpty()).canonicalPath
                    var ownedGeneratedOutput = false
                    try {
                        withContext(Dispatchers.IO) {
                            require(!editorOutputAliasesSource(source, file)) { "Output cannot overwrite the source." }
                            // MR-R2-016 / MR-R5-017: a caller-dictated edit cannot escape private
                            // storage. Reject before treating the destination as ours to delete.
                            require(com.example.chat.ui.media.isAllowedViewerSource(file.toURI().toString(),
                                listOf(context.cacheDir, context.filesDir))) { "Output must stay inside app-private storage." }
                            require(outputFile != null || !file.exists()) { "Editor output already exists." }
                            val edited = renderEditedImage(original, export.brightness, export.contrast, export.strokes,
                                export.text, export.textX, export.textY, export.textScale, export.textColor,
                                // Original: flattenAlpha = !preserveAlpha
                                flattenAlpha = encoding.flattenAlpha, saturation = export.saturation, warmth = export.warmth,
                                tint = export.tint, fade = export.fade, textAlignment = export.alignment, textStrip = export.strip)
                            try {
                                // Original JPEG/PNG-only codec selection:
                                // file.outputStream().use { check(edited.compress(if (preserveAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it)) }
                                writeEditorOutputAtomically(file,
                                    checkActive = { coroutineContext.ensureActive() }) { stream ->
                                    check(compressEditorImage(edited, encoding, stream))
                                }
                                // Caller-supplied destinations are never deleted by handoff failure.
                                ownedGeneratedOutput = outputFile == null
                            } finally { edited.recycle() }
                        }
                        coroutineContext.ensureActive()
                        if (active) {
                            exportLineage.register(file, source)
                            onApply(file)
                            handedOff = true
                            // MR-R2-047: confirm only this local copy handoff, never upload/send.
                            // Feedback failure must not turn a successful handoff into cleanup.
                            runCatching { com.example.chat.ui.kit.ChatyHapticsPort.success(accessibility) }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        // Original: error = e.message ?: "Could not save edit"
                        error = "Could not save the edited copy. Check device storage and try again."
                    }
                    finally {
                        if (!handedOff) {
                            runCatching { exportLineage.forget(file) }
                            if (ownedGeneratedOutput) file.delete()
                        }
                        saving = false
                    }
                }
            // Original label-only content retained for MR-R3-027:
            // }, enabled = bitmap != null && !saving) { Text(if (saving) "Saving" else "Apply") }
            // MR-R2-047 previous layout: }, enabled = bitmap != null && !saving) { EditorExportButtonContent(saving) }
            }, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp), enabled = bitmap != null && !saving) { EditorExportButtonContent(saving) }
        }
        // MR-R4-046 previous host: Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        @Composable fun editorPreview(previewModifier: Modifier) {
        Box(previewModifier, contentAlignment = Alignment.Center) {
            val image = bitmap
            if (image == null) {
                // MR-R3-044 previous load error: if (error == null) CircularProgressIndicator() else Text(error!!, color = MaterialTheme.colorScheme.onSurface)
                // MR-R3-007 previous spinner-only load state:
                // if (error == null) CircularProgressIndicator() else Text(mediaItemError(errorItemLabel, error!!), color = MaterialTheme.colorScheme.onSurface)
                // MR-R4-043 prior: if (error == null) ReviewPreviewLoadingFrame("Loading image for editing") else Text(mediaItemError(errorItemLabel, error!!), color = MaterialTheme.colorScheme.onSurface)
                if (error == null) ReviewPreviewLoadingFrame(stringResource(com.example.chat.R.string.media_released_editor_loading)) else Text(mediaItemError(errorItemLabel, error!!), color = MaterialTheme.colorScheme.onSurface)
            } else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ratio = image.width.toFloat() / image.height
                val width = minOf(maxWidth, maxHeight * ratio)
                Box(Modifier.width(width).aspectRatio(ratio)) {
                    // MR-R4-043 prior: Image(image.asImageBitmap(), "Image being edited", Modifier.fillMaxSize(), colorFilter = ColorFilter.colorMatrix(ColorMatrix(editorColorValues(brightness, contrast, saturation, warmth, tint, fade))))
                    Image(image.asImageBitmap(), stringResource(com.example.chat.R.string.media_released_editor_image_description), Modifier.fillMaxSize(), colorFilter = ColorFilter.colorMatrix(ColorMatrix(editorColorValues(brightness, contrast, saturation, warmth, tint, fade))))
                    Canvas(Modifier.fillMaxSize().pointerInput(draw, ink, image, saving, brushWidth, brushOpacity) {
                        if (draw && !saving) detectDragGestures(
                            // Original start copied the outer list: strokes = strokes + EditorStroke(...)
                            onDragStart = { point -> redoStrokes.clear(); strokes.add(EditorStroke(ink.toArgb().toLong(), listOf(Offset((point.x / size.width).coerceIn(0f, 1f), (point.y / size.height).coerceIn(0f, 1f))), brushWidth, brushOpacity)) },
                            onDrag = { change, _ ->
                                change.consume()
                                if (strokes.isNotEmpty()) {
                                    val last = strokes.last()
                                    val p = Offset((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                                    if (last.points.lastOrNull()?.let { previous -> (p - previous).getDistance() >= 0.003f } != false) {
                                        // Original outer copy: strokes = strokes.dropLast(1) + last.copy(points = last.points + p)
                                        strokes[strokes.lastIndex] = last.copy(points = last.points + p)
                                    }
                                }
                            },
                        )
                    }.pointerInput(textMode, image, saving) {
                        if (textMode && !saving) detectDragGestures { change, drag ->
                            change.consume()
                            textX = (textX + drag.x / size.width).coerceIn(0f, 0.9f)
                            textY = (textY + drag.y / size.height).coerceIn(0.1f, 0.95f)
                        }
                    }.pointerInput(draw, ink, image, saving, brushWidth, brushOpacity) {
                        if (draw && !saving) detectTapGestures { point ->
                            redoStrokes.clear()
                            strokes.add(EditorStroke(ink.toArgb().toLong(), listOf(Offset(
                                (point.x / size.width).coerceIn(0f, 1f), (point.y / size.height).coerceIn(0f, 1f))), brushWidth, brushOpacity))
                        }
                    }) {
                        // MR-R2-026 prior: strokes.forEach { stroke ->
                        // MR-R2-026 prior: val baseColor = Color(stroke.color.toInt())
                        // MR-R2-026 prior: val color = baseColor.copy(alpha = baseColor.alpha * editorBrushOpacity(stroke.opacity))
                        // MR-R2-026 prior: val widthPx = size.width * editorBrushWidth(stroke.widthFraction)
                        // MR-R2-026 prior: if (stroke.points.size == 1) { val p = stroke.points.first(); drawCircle(color, widthPx / 2f, Offset(p.x * size.width, p.y * size.height)) }
                        // MR-R2-026 prior: stroke.points.zipWithNext().forEach { (a, b) -> drawLine(color, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), widthPx, StrokeCap.Round) }
                        // MR-R2-026 prior: }
                        strokePictureCache.draw(this, strokes)
                        // Previous single-line paint path retained for review:
                        // if (textOverlay.isNotBlank()) drawContext.canvas.nativeCanvas.apply {
                        // val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        // color = ink.toArgb(); textSize = size.width * textScale
                        // setShadowLayer(size.width * 0.004f, 0f, 0f, android.graphics.Color.BLACK)
                        // }
                        // paint.textSize = ImageTextOverlayPolicy.fittedTextSize(size.width, textX, paint.textSize, paint.measureText(textOverlay))
                        // drawText(textOverlay, size.width * textX, size.height * textY, paint)
                        // }
                        drawEditorText(drawContext.canvas.nativeCanvas, size.width, size.height, textOverlay,
                            textX, textY, textScale, ink.toArgb(), textAlignment, textStrip)
                    }
                }
            }
        }
        }
        // Previous tools host: Column(Modifier.fillMaxWidth().heightIn(max = (configuration.screenHeightDp * 0.45f).dp.coerceIn(120.dp, 360.dp)).verticalScroll(rememberScrollState())) {
        @Composable fun editorTools(toolsModifier: Modifier) {
        Column(toolsModifier.verticalScroll(rememberScrollState())) {
        // Original warning treated every non-PNG destination as JPEG, including WebP.
        // if (bitmap?.hasAlpha() == true && outputFile != null && !outputFile.extension.equals("png", true)) Text("This output uses JPEG. Transparency will be flattened to white.", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(8.dp))
        val previewEncoding = runCatching { editorOutputFormat(sourceMime,
            android.net.Uri.parse(source).lastPathSegment?.substringAfterLast('.', ""), bitmap?.hasAlpha() == true,
            outputFile?.extension) }.getOrNull()
        Text(when {
            previewEncoding == null -> "This destination format is unsupported. Close the editor and choose another destination."
            previewEncoding == EditorOutputFormat.JPEG -> "Edited copy uses JPEG. Any transparency is flattened to white."
            previewEncoding == EditorOutputFormat.WEBP && android.os.Build.VERSION.SDK_INT < 30 -> "Edited copy uses WebP at maximum quality. This Android version may recompress image detail. Transparency is kept."
            else -> "Edited copy uses ${previewEncoding.name}. Transparency is kept."
        }, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
        // MR-R4-043 prior: Text("Edits remove location data and bake in rotation.", color = MaterialTheme.colorScheme.onSurfaceVariant,
        Text(stringResource(com.example.chat.R.string.media_released_editor_metadata_note), color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(8.dp), style = MaterialTheme.typography.bodySmall)
        // MR-R4-043 prior: if (saving) Text("Saving edited copy", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (saving) Text(stringResource(com.example.chat.R.string.media_released_editor_saving), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        // MR-R3-044 previous save error: error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        error?.let { Text(mediaItemError(errorItemLabel, it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        // MR-R2-046 previous interleaved toolbar and separate crop row:
        //         Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
        //             TextButton({ textMode = !textMode; draw = false; adjustMode = false; accessibility.announceForAccessibility(if (textMode) "Text mode on, drag to move text or use position sliders" else "Text mode off") }, enabled = !saving) { Text(if (textMode) "Text on" else "Text") }
        //             TextButton({ draw = !draw; textMode = false; adjustMode = false; accessibility.announceForAccessibility(if (draw) "Drawing on, drag to draw" else "Drawing off") }, enabled = !saving) { Text(if (draw) "Drawing on" else "Draw") }
        //             TextButton({ adjustMode = !adjustMode; textMode = false; draw = false; accessibility.announceForAccessibility(if (adjustMode) "Adjustments open" else "Adjustments closed") }, enabled = !saving) { Text(if (adjustMode) "Adjust on" else "Adjust") }
        //             // MR-R3-026 previous position: // Original stroke Undo rebuilt the outer list: strokes = strokes.dropLast(1)
        //             // MR-R3-026 previous position: TextButton({
        //             // MR-R3-026 previous position: if (strokes.isNotEmpty()) {
        //             // MR-R3-026 previous position: if (redoStrokes.size >= 20) redoStrokes.removeAt(0)
        //             // MR-R3-026 previous position: redoStrokes.add(strokes.removeAt(strokes.lastIndex))
        //             // MR-R3-026 previous position: }
        //             // MR-R3-026 previous position: }, enabled = strokes.isNotEmpty() && !saving) { Text("Undo stroke") }
        //             // MR-R3-026 previous position: TextButton({
        //             // MR-R3-026 previous position: if (redoStrokes.isNotEmpty()) strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
        //             // MR-R3-026 previous position: }, enabled = redoStrokes.isNotEmpty() && !saving) { Text("Redo stroke") }
        //             TextButton({
        //                 bitmap?.let { image ->
        //                     redoStrokes.clear()
        //                     val matrix = android.graphics.Matrix().apply { postRotate(90f) }
        //                     bitmap = Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
        //                     // Keep outer snapshot list identity, replacing only changed stroke values.
        //                     for (index in strokes.indices) {
        //                         val stroke = strokes[index]
        //                         strokes[index] = stroke.copy(points = stroke.points.map { Offset(1f - it.y, it.x) })
        //                     }
        //                     val oldTextX = textX
        //                     textX = (1f - textY).coerceIn(0f, 0.9f)
        //                     textY = oldTextX.coerceIn(0.1f, 0.95f)
        //                 }
        //             }, enabled = bitmap != null && !saving) { Text("Rotate") }
        //             // MR-R3-026: history actions trail transforms, immediately before Reset.
        //             // Original stroke Undo rebuilt the outer list: strokes = strokes.dropLast(1)
        //             TextButton({
        //                 if (strokes.isNotEmpty()) {
        //                     if (redoStrokes.size >= 20) redoStrokes.removeAt(0)
        //                     redoStrokes.add(strokes.removeAt(strokes.lastIndex))
        //                 }
        //             }, enabled = strokes.isNotEmpty() && !saving) { Text("Undo stroke") }
        //             TextButton({
        //                 if (redoStrokes.isNotEmpty()) strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
        //             }, enabled = redoStrokes.isNotEmpty() && !saving) { Text("Redo stroke") }
        //             TextButton({ brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f; tint = 0f; fade = 0f; adjustMode = false; strokes.clear(); redoStrokes.clear(); brushWidth = 0.008f; brushOpacity = 1f; paletteOpen = false; textOverlay = ""; textX = 0.1f; textY = 0.3f; textScale = 0.07f; textAlignment = EditorTextAlignment.Left; textStrip = false; draw = false; textMode = false; bitmap = originalBitmap }, enabled = !saving) { Text("Reset") }
        //         }
        //         Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
        //             Text("Crop", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(12.dp))
        //             listOf("Square" to 1f, "Portrait" to (3f / 4f), "Landscape" to (4f / 3f)).forEach { (label, ratio) ->
        //                 TextButton(onClick = {
        //                     bitmap?.let { image ->
        //                         redoStrokes.clear()
        //                         val crop = editorCenterCrop(image.width, image.height, ratio)
        //                         bitmap = Bitmap.createBitmap(image, crop.left, crop.top, crop.width(), crop.height())
        //                         textX = ((textX * image.width - crop.left) / crop.width()).coerceIn(0f, 0.9f)
        //                         textY = ((textY * image.height - crop.top) / crop.height()).coerceIn(0.1f, 0.95f)
        //                         // Existing drawing coordinates follow the crop. Clip discarded strokes at export.
        //                         for (index in strokes.indices) {
        //                             val stroke = strokes[index]
        //                             strokes[index] = stroke.copy(points = stroke.points.map { point ->
        //                                 Offset((point.x * image.width - crop.left) / crop.width(), (point.y * image.height - crop.top) / crop.height())
        //                             })
        //                         }
        //                     }
        //                 }, enabled = bitmap != null && !saving) { Text(label) }
        //             }
        //         }
        // MR-R2-046: stable functional clusters; history remains trailing for MR-R3-026.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.semantics { isTraversalGroup = true }, verticalAlignment = Alignment.CenterVertically) {
            // MR-R3-009 previous: TextButton({ textMode = !textMode; draw = false; adjustMode = false; accessibility.announceForAccessibility(if (textMode) "Text mode on, drag to move text or use position sliders" else "Text mode off") }, enabled = !saving) { Text(if (textMode) "Text on" else "Text") }
            // MR-R4-043 prior: ReviewSelectedButton("Text", textMode, !saving, { textMode = !textMode; cropMode = false; adjustmentMenu = false; draw = false; adjustMode = false; accessibility.announceForAccessibility(if (textMode) "Text mode on, drag to move text or use position sliders" else "Text mode off") })
            ReviewSelectedButton(stringResource(com.example.chat.R.string.media_released_editor_mode_text), textMode, !saving, { textMode = !textMode; cropMode = false; adjustmentMenu = false; draw = false; adjustMode = false; accessibility.announceForAccessibility(if (textMode) context.getString(com.example.chat.R.string.media_released_editor_text_on) else context.getString(com.example.chat.R.string.media_released_editor_text_off)) })
            // MR-R3-009 previous: TextButton({ draw = !draw; textMode = false; adjustMode = false; accessibility.announceForAccessibility(if (draw) "Drawing on, drag to draw" else "Drawing off") }, enabled = !saving) { Text(if (draw) "Drawing on" else "Draw") }
            // MR-R4-043 prior: ReviewSelectedButton("Draw", draw, !saving, { draw = !draw; cropMode = false; adjustmentMenu = false; textMode = false; adjustMode = false; accessibility.announceForAccessibility(if (draw) "Drawing on, drag to draw" else "Drawing off") })
            ReviewSelectedButton(stringResource(com.example.chat.R.string.media_released_editor_mode_draw), draw, !saving, { draw = !draw; cropMode = false; adjustmentMenu = false; textMode = false; adjustMode = false; accessibility.announceForAccessibility(if (draw) context.getString(com.example.chat.R.string.media_released_editor_draw_on) else context.getString(com.example.chat.R.string.media_released_editor_draw_off)) })
            // MR-R3-009 previous: TextButton({ adjustMode = !adjustMode; textMode = false; draw = false; accessibility.announceForAccessibility(if (adjustMode) "Adjustments open" else "Adjustments closed") }, enabled = !saving) { Text(if (adjustMode) "Adjust on" else "Adjust") }
            // MR-R4-043 prior: ReviewSelectedButton("Adjust", adjustMode, !saving, { adjustMode = !adjustMode; cropMode = false; textMode = false; draw = false; accessibility.announceForAccessibility(if (adjustMode) "Adjustments open" else "Adjustments closed") })
            ReviewSelectedButton(stringResource(com.example.chat.R.string.media_released_editor_mode_adjust), adjustMode, !saving, { adjustMode = !adjustMode; cropMode = false; textMode = false; draw = false; accessibility.announceForAccessibility(if (adjustMode) context.getString(com.example.chat.R.string.media_released_editor_adjust_on) else context.getString(com.example.chat.R.string.media_released_editor_adjust_off)) })
            // MR-R4-043 prior: ReviewSelectedButton("Crop", cropMode, !saving, { cropMode = !cropMode; adjustmentMenu = false; textMode = false; draw = false; adjustMode = false; paletteOpen = false })
            ReviewSelectedButton(stringResource(com.example.chat.R.string.media_released_editor_crop), cropMode, !saving, { cropMode = !cropMode; adjustmentMenu = false; textMode = false; draw = false; adjustMode = false; paletteOpen = false })
            }
            if (cropMode) {
            Spacer(Modifier.width(24.dp))
            Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant).clearAndSetSemantics {})
            Spacer(Modifier.width(24.dp))
            Row(Modifier.semantics { isTraversalGroup = true }, verticalAlignment = Alignment.CenterVertically) {
            TextButton({
                bitmap?.let { image ->
                    redoStrokes.clear()
                    val matrix = android.graphics.Matrix().apply { postRotate(90f) }
                    bitmap = Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
                    // Keep outer snapshot list identity, replacing only changed stroke values.
                    for (index in strokes.indices) {
                        val stroke = strokes[index]
                        strokes[index] = stroke.copy(points = stroke.points.map { Offset(1f - it.y, it.x) })
                    }
                    val oldTextX = textX
                    textX = (1f - textY).coerceIn(0f, 0.9f)
                    textY = oldTextX.coerceIn(0.1f, 0.95f)
                }
            // MR-R4-043 prior: }, enabled = bitmap != null && !saving) { Text("Rotate") }
            }, enabled = bitmap != null && !saving) { Text(stringResource(com.example.chat.R.string.media_released_editor_rotate)) }
            // MR-R4-043 prior: Text("Crop", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(12.dp))
            Text(stringResource(com.example.chat.R.string.media_released_editor_crop), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(12.dp))
            // MR-R4-043 prior: listOf("Square" to 1f, "Portrait" to (3f / 4f), "Landscape" to (4f / 3f)).forEach { (label, ratio) ->
            listOf(stringResource(com.example.chat.R.string.media_released_editor_square) to 1f, stringResource(com.example.chat.R.string.media_released_editor_portrait) to (3f / 4f), stringResource(com.example.chat.R.string.media_released_editor_landscape) to (4f / 3f)).forEach { (label, ratio) ->
                TextButton(onClick = {
                    bitmap?.let { image ->
                        redoStrokes.clear()
                        val crop = editorCenterCrop(image.width, image.height, ratio)
                        bitmap = Bitmap.createBitmap(image, crop.left, crop.top, crop.width(), crop.height())
                        textX = ((textX * image.width - crop.left) / crop.width()).coerceIn(0f, 0.9f)
                        textY = ((textY * image.height - crop.top) / crop.height()).coerceIn(0.1f, 0.95f)
                        // Existing drawing coordinates follow the crop. Clip discarded strokes at export.
                        for (index in strokes.indices) {
                            val stroke = strokes[index]
                            strokes[index] = stroke.copy(points = stroke.points.map { point ->
                                Offset((point.x * image.width - crop.left) / crop.width(), (point.y * image.height - crop.top) / crop.height())
                            })
                        }
                    }
                }, enabled = bitmap != null && !saving) { Text(label) }
            }
            }
            } // Crop group disclosure.
            Spacer(Modifier.width(24.dp))
            Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant).clearAndSetSemantics {})
            Spacer(Modifier.width(24.dp))
            Row(Modifier.semantics { isTraversalGroup = true }, verticalAlignment = Alignment.CenterVertically) {
            // Original stroke Undo rebuilt the outer list: strokes = strokes.dropLast(1)
            TextButton({
                if (strokes.isNotEmpty()) {
                    if (redoStrokes.size >= 20) redoStrokes.removeAt(0)
                    redoStrokes.add(strokes.removeAt(strokes.lastIndex))
                }
            // MR-R4-043 prior: }, enabled = strokes.isNotEmpty() && !saving) { Text("Undo stroke") }
            }, enabled = strokes.isNotEmpty() && !saving) { Text(stringResource(com.example.chat.R.string.media_released_editor_undo)) }
            TextButton({
                if (redoStrokes.isNotEmpty()) strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
            // MR-R4-043 prior: }, enabled = redoStrokes.isNotEmpty() && !saving) { Text("Redo stroke") }
            }, enabled = redoStrokes.isNotEmpty() && !saving) { Text(stringResource(com.example.chat.R.string.media_released_editor_redo)) }
            // MR-R4-043 prior: TextButton({ cropMode = false; adjustmentMenu = false; brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f; tint = 0f; fade = 0f; adjustMode = false; strokes.clear(); redoStrokes.clear(); brushWidth = 0.008f; brushOpacity = 1f; paletteOpen = false; textOverlay = ""; textX = 0.1f; textY = 0.3f; textScale = 0.07f; textAlignment = EditorTextAlignment.Left; textStrip = false; draw = false; textMode = false; bitmap = originalBitmap }, enabled = !saving) { Text("Reset") }
            TextButton({ cropMode = false; adjustmentMenu = false; brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f; tint = 0f; fade = 0f; adjustMode = false; strokes.clear(); redoStrokes.clear(); brushWidth = 0.008f; brushOpacity = 1f; paletteOpen = false; textOverlay = ""; textX = 0.1f; textY = 0.3f; textScale = 0.07f; textAlignment = EditorTextAlignment.Left; textStrip = false; draw = false; textMode = false; bitmap = originalBitmap }, enabled = !saving) { Text(stringResource(com.example.chat.R.string.media_released_editor_reset)) }
            }
        }
        // MR-R3-034 previous: if (textMode) Column(Modifier.fillMaxWidth().heightIn(max = 170.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        if (textMode) Column(Modifier.fillMaxWidth().heightIn(max = 172.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            // MR-R4-043 prior: EditorDetailSectionSelector("Text controls", textDetail,
            EditorDetailSectionSelector(stringResource(com.example.chat.R.string.media_released_editor_text_controls), textDetail,
                // MR-R4-043 prior: listOf("Content", "Alignment", "Placement", "Ink"), !saving) { textDetail = it }
                listOf("Content", "Alignment", "Placement", "Ink"), !saving, keyLabels = detailLabels) { textDetail = it }
            if (textDetail == "Content") {
            // Original input flattened to one line: OutlinedTextField(... ImageTextOverlayPolicy.text(it), singleLine = true)
            OutlinedTextField(textOverlay, { textOverlay = editorOverlayText(it) }, minLines = 2, maxLines = 4,
                // MR-R4-043 prior: label = { Text("Text on image") }, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(com.example.chat.R.string.media_released_editor_text_label)) }, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface))
            // MR-R4-043 prior: Text("Up to 4 lines, 120 characters", style = MaterialTheme.typography.bodySmall)
            Text(stringResource(com.example.chat.R.string.media_released_editor_text_limit), style = MaterialTheme.typography.bodySmall)
            } // Content disclosure.
            if (textDetail == "Alignment") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                EditorTextAlignment.entries.forEach { alignment ->
                    val alignmentLabel = stringResource(when (alignment) {
                        EditorTextAlignment.Left -> com.example.chat.R.string.media_released_editor_align_left
                        EditorTextAlignment.Center -> com.example.chat.R.string.media_released_editor_align_center
                        EditorTextAlignment.Right -> com.example.chat.R.string.media_released_editor_align_right
                    })
                    val alignmentDescription = stringResource(when (alignment) {
                        EditorTextAlignment.Left -> com.example.chat.R.string.media_released_editor_align_left_description
                        EditorTextAlignment.Center -> com.example.chat.R.string.media_released_editor_align_center_description
                        EditorTextAlignment.Right -> com.example.chat.R.string.media_released_editor_align_right_description
                    })
                    // TextButton(onClick = { textAlignment = alignment }, enabled = !saving,
                    // modifier = Modifier.heightIn(min = 48.dp).semantics {
                    // selected = textAlignment == alignment
                    // contentDescription = "Align text ${alignment.name.lowercase(java.util.Locale.ROOT)}"
                    // }) { Text(alignment.name) }
                    // MR-R3-009 previous text-only alignment buttons used selected semantics without the shared visual state.
                    // MR-R4-043 prior: ReviewSelectedButton(alignment.name, textAlignment == alignment, !saving, { textAlignment = alignment },
                    ReviewSelectedButton(alignmentLabel, textAlignment == alignment, !saving, { textAlignment = alignment },
                        // MR-R4-043 prior: Modifier.semantics { contentDescription = "Align text ${alignment.name.lowercase(java.util.Locale.ROOT)}" })
                        Modifier.semantics { contentDescription = alignmentDescription })
                }
                // MR-R3-009 previous: TextButton(onClick = { textStrip = !textStrip }, enabled = !saving,
                // MR-R3-009 previous: modifier = Modifier.heightIn(min = 48.dp).semantics { selected = textStrip }) {
                // MR-R3-009 previous: Text(if (textStrip) "Strip on" else "Text strip")
                // MR-R3-009 previous: }
                // MR-R4-043 prior: ReviewSelectedButton("Text strip", textStrip, !saving, { textStrip = !textStrip })
                ReviewSelectedButton(stringResource(com.example.chat.R.string.media_released_editor_text_strip), textStrip, !saving, { textStrip = !textStrip })
            }
            } // Alignment disclosure.
            if (textDetail == "Placement") {
            // MR-R4-043 prior: Text("Horizontal position: ${(textX * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(com.example.chat.R.string.media_released_editor_horizontal_percent, (textX * 100).toInt()), color = MaterialTheme.colorScheme.onSurface)
            // MR-R4-043 prior: Slider(textX, { textX = it }, valueRange = 0f..0.9f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Horizontal text position" })
            Slider(textX, { textX = it }, valueRange = 0f..0.9f, enabled = !saving, modifier = Modifier.semantics { contentDescription = horizontalDescription })
            // MR-R4-043 prior: Text("Vertical position: ${(textY * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(com.example.chat.R.string.media_released_editor_vertical_percent, (textY * 100).toInt()), color = MaterialTheme.colorScheme.onSurface)
            // MR-R4-043 prior: Slider(textY, { textY = it }, valueRange = 0.1f..0.95f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Vertical text position" })
            Slider(textY, { textY = it }, valueRange = 0.1f..0.95f, enabled = !saving, modifier = Modifier.semantics { contentDescription = verticalDescription })
            // MR-R4-043 prior: Text("Text size: ${(textScale * 100).toInt()}% of image width", color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(com.example.chat.R.string.media_released_editor_size_percent, (textScale * 100).toInt()), color = MaterialTheme.colorScheme.onSurface)
            // MR-R4-043 prior: Slider(textScale, { textScale = it }, valueRange = 0.03f..0.15f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Text size" })
            Slider(textScale, { textScale = it }, valueRange = 0.03f..0.15f, enabled = !saving, modifier = Modifier.semantics { contentDescription = text_sizeDescription })
            } // Placement disclosure; existing values remain remembered.
        }
        if (draw) Column(Modifier.fillMaxWidth().heightIn(max = 140.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            // MR-R4-043 prior: EditorDetailSectionSelector("Drawing controls", drawDetail, listOf("Stroke", "Ink"), !saving) { drawDetail = it }
            EditorDetailSectionSelector(stringResource(com.example.chat.R.string.media_released_editor_drawing_controls), drawDetail, listOf("Stroke", "Ink"), !saving, keyLabels = detailLabels) { drawDetail = it }
            if (drawDetail == "Stroke") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                // MR-R4-043 prior: Text("Pen width", style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(com.example.chat.R.string.media_released_editor_pen_width), style = MaterialTheme.typography.bodyMedium)
                // MR-R4-043 prior: listOf("Fine" to 0.004f, "Medium" to 0.008f, "Thick" to 0.016f).forEach { (name, value) ->
                listOf(stringResource(com.example.chat.R.string.media_released_editor_fine) to 0.004f, stringResource(com.example.chat.R.string.media_released_editor_medium) to 0.008f, stringResource(com.example.chat.R.string.media_released_editor_thick) to 0.016f).forEach { (name, value) ->
                    // MR-R3-009 previous: TextButton(onClick = { brushWidth = value }, enabled = !saving,
                    // MR-R3-009 previous: modifier = Modifier.heightIn(min = 48.dp).semantics { selected = brushWidth == value; contentDescription = "$name pen width" }) { Text(name) }
                    val penDescription = stringResource(com.example.chat.R.string.media_released_editor_pen_description, name)
                    ReviewSelectedButton(name, brushWidth == value, !saving, { brushWidth = value },
                        // MR-R4-043 prior: Modifier.semantics { contentDescription = "$name pen width" })
                        Modifier.semantics { contentDescription = penDescription })
                }
            }
            // MR-R4-043 prior: Text("Ink opacity: ${(brushOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(com.example.chat.R.string.media_released_editor_opacity_percent, (brushOpacity * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
            Slider(brushOpacity, { brushOpacity = it }, enabled = !saving, valueRange = 0.1f..1f,
                // MR-R4-043 prior: modifier = Modifier.semantics { contentDescription = "Ink opacity, 10 to 100 percent" })
                modifier = Modifier.semantics { contentDescription = ink_opacityDescription })
            } // Stroke disclosure.
        }
        // MR-R3-005 prior swatches condition: if (draw || textMode) Row
        val inkDetailsVisible = (draw && drawDetail == "Ink") || (textMode && textDetail == "Ink")
        // MR-R4-043 prior: if (inkDetailsVisible) EditorDetailSectionSelector("Ink controls", inkDetail,
        if (inkDetailsVisible) EditorDetailSectionSelector(stringResource(com.example.chat.R.string.media_released_editor_ink_controls), inkDetail,
            // MR-R4-043 prior: listOf("Preset", "Recent", "Custom"), !saving) { detail ->
            listOf("Preset", "Recent", "Custom"), !saving, keyLabels = detailLabels) { detail ->
            inkDetail = detail
            if (detail == "Custom" && !paletteOpen) {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(ink.toArgb(), hsv)
                paletteHue = hsv[0]; paletteSaturation = hsv[1]; paletteValue = hsv[2]
            }
            paletteOpen = detail == "Custom"
        }
        // MR-R3-005 prior combined swatch region: if ((draw && drawDetail == "Ink") || (textMode && textDetail == "Ink")) Row
        if (inkDetailsVisible && inkDetail != "Custom") Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            // Original five quick ink colors remain; user-picked colors appear as named recents.
            // MR-R3-005 prior list concatenated5 presets and up to5 recents simultaneously:
            // (listOf(Red, Yellow, White, Black, Cyan) + recentInk.mapIndexed(...)).forEach
            val swatches = if (inkDetail == "Preset")
                // MR-R4-043 prior: listOf("Red" to Color.Red, "Yellow" to Color.Yellow, "White" to Color.White, "Black" to Color.Black, "Cyan" to Color.Cyan)
                listOf(stringResource(com.example.chat.R.string.media_released_editor_ink_red) to Color.Red, stringResource(com.example.chat.R.string.media_released_editor_ink_yellow) to Color.Yellow, stringResource(com.example.chat.R.string.media_released_editor_ink_white) to Color.White, stringResource(com.example.chat.R.string.media_released_editor_ink_black) to Color.Black, stringResource(com.example.chat.R.string.media_released_editor_ink_cyan) to Color.Cyan)
                // MR-R4-043 prior: else recentInk.mapIndexed { index, color -> "Recent color ${index + 1}" to color }
                else recentInk.mapIndexed { index, color -> stringResource(com.example.chat.R.string.media_released_editor_recent_color, index + 1) to color }
            // MR-R4-043 prior: if (swatches.isEmpty()) Text("No recent ink colors yet. Choose Custom to mix a color.", style = MaterialTheme.typography.bodySmall)
            if (swatches.isEmpty()) Text(stringResource(com.example.chat.R.string.media_released_editor_no_recent_ink), style = MaterialTheme.typography.bodySmall)
            swatches.forEach { (name, color) ->
                val inkDescription = stringResource(com.example.chat.R.string.media_released_editor_ink_description, name)
                val inkSelectionDescription = stringResource(if (ink == color) com.example.chat.R.string.media_released_editor_ink_state_selected else com.example.chat.R.string.media_released_editor_ink_state_unselected)
                // Old glyph-only swatches: TextButton(...){ Text(if (ink == color) "●" else "○", color = color) }
                Box(Modifier.size(48.dp).selectable(selected = ink == color, enabled = !saving, role = Role.RadioButton, onClick = { ink = color })
                    // MR-R4-043 prior: .semantics { contentDescription = "$name ink"; stateDescription = if (ink == color) "Selected" else "Not selected" }
                    .semantics { contentDescription = inkDescription; stateDescription = inkSelectionDescription }
                    .background(if (ink == color) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                    .border(1.dp, if (ink == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .padding(4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(color)
                        .border(if (ink == color) 2.dp else 1.dp, if (ink == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                    if (ink == color) Text("✓", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.surface, CircleShape).padding(horizontal = 2.dp).clearAndSetSemantics {})
                }
            }
        }
        // MR-R3-009 previous: if (draw || textMode) TextButton(onClick = {
        // MR-R3-005 prior palette-toggle condition: if (draw || textMode)
        // MR-R3-005 superseded palette toggle: if ((draw && drawDetail == "Ink") || (textMode && textDetail == "Ink")) ReviewSelectedButton("Ink colors", paletteOpen, !saving, onClick = {
        // MR-R3-005 superseded palette toggle: if (!paletteOpen) {
        // MR-R3-005 superseded palette toggle: val hsv = FloatArray(3)
        // MR-R3-005 superseded palette toggle: android.graphics.Color.colorToHSV(ink.toArgb(), hsv)
        // MR-R3-005 superseded palette toggle: paletteHue = hsv[0]; paletteSaturation = hsv[1]; paletteValue = hsv[2]
        // MR-R3-005 superseded palette toggle: }
        // MR-R3-005 superseded palette toggle: paletteOpen = !paletteOpen
        // MR-R3-005 superseded palette toggle: // MR-R3-009 previous ending: }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (paletteOpen) "Close color palette" else "More ink colors") }
        // MR-R3-005 superseded palette toggle: })
        // MR-R3-005 prior palette condition: if ((draw || textMode) && paletteOpen)
        // MR-R3-005 prior condition combined mode checks with paletteOpen.
        // MR-R3-005 Custom disclosure is selected by detail, not legacy paletteOpen reset on mode/crop.
        if (inkDetailsVisible && inkDetail == "Custom") Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            // MR-R4-043 prior: Text("Hue: ${paletteHue.toInt()} degrees", style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(com.example.chat.R.string.media_released_editor_hue_degrees, paletteHue.toInt()), style = MaterialTheme.typography.bodyMedium)
            // Spectrum colors describe ink, not app chrome. They never override theme tokens.
            Box(Modifier.fillMaxWidth().height(12.dp).background(androidx.compose.ui.graphics.Brush.horizontalGradient(
                (0..6).map { step -> Color(android.graphics.Color.HSVToColor(floatArrayOf(step * 60f, 1f, 1f))) })))
            Slider(paletteHue, { paletteHue = it }, enabled = !saving, valueRange = 0f..360f,
                // MR-R4-043 prior: modifier = Modifier.semantics { contentDescription = "Ink hue, 0 to 360 degrees" })
                modifier = Modifier.semantics { contentDescription = ink_hueDescription })
            // MR-R4-043 prior: Text("Saturation: ${(paletteSaturation * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(com.example.chat.R.string.media_released_editor_ink_saturation_percent, (paletteSaturation * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
            Slider(paletteSaturation, { paletteSaturation = it }, enabled = !saving, valueRange = 0f..1f,
                // MR-R4-043 prior: modifier = Modifier.semantics { contentDescription = "Ink saturation" })
                modifier = Modifier.semantics { contentDescription = ink_saturationDescription })
            // MR-R4-043 prior: Text("Color brightness: ${(paletteValue * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(com.example.chat.R.string.media_released_editor_ink_brightness_percent, (paletteValue * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
            Slider(paletteValue, { paletteValue = it }, enabled = !saving, valueRange = 0f..1f,
                // MR-R4-043 prior: modifier = Modifier.semantics { contentDescription = "Ink color brightness" })
                modifier = Modifier.semantics { contentDescription = ink_brightnessDescription })
            val previewInk = Color(android.graphics.Color.HSVToColor(floatArrayOf(paletteHue, paletteSaturation, paletteValue)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(previewInk, CircleShape).border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                    // MR-R4-043 prior: .semantics { contentDescription = "Chosen ink preview" })
                    .semantics { contentDescription = ink_previewDescription })
                TextButton(onClick = {
                    ink = previewInk
                    recentInk = (listOf(previewInk) + recentInk).distinct().take(5)
                    paletteOpen = false
                    inkDetail = "Recent"
                    // MR-R4-043 prior: accessibility.announceForAccessibility("Ink color selected")
                    accessibility.announceForAccessibility(context.getString(com.example.chat.R.string.media_released_editor_ink_selected))
                // MR-R4-043 prior: }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use ink color") }
                }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(com.example.chat.R.string.media_released_editor_use_ink)) }
            }
        }
        // Adjust controls used to be always visible; reveal only from the Adjust tool.
        // MR-R3-005 previous simultaneous adjustment controls:
        //         if (adjustMode) Column(Modifier.padding(horizontal = 20.dp)) {
        //             Text("Brightness: ${"%+.0f".format(brightness * 100)}%", color = MaterialTheme.colorScheme.onSurface)
        //             Slider(brightness, { brightness = it }, valueRange = -0.4f..0.4f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Brightness, minus 40 to plus 40 percent" })
        //             Text("Contrast: ${"%.0f".format(contrast * 100)}%", color = MaterialTheme.colorScheme.onSurface)
        //             Slider(contrast, { contrast = it }, valueRange = 0.5f..1.8f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Contrast, 50 to 180 percent" })
        //             Text("Saturation: ${(saturation * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        //             Slider(saturation, { saturation = it }, valueRange = 0f..2f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image saturation, 0 to 200 percent" })
        //             Text("Warmth: ${"%+.0f".format(warmth * 100)}%", style = MaterialTheme.typography.bodyMedium)
        //             Slider(warmth, { warmth = it }, valueRange = -0.5f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image warmth, minus 50 to plus 50 percent" })
        //             Text("Tint: ${"%+.0f".format(tint * 100)}%", style = MaterialTheme.typography.bodyMedium)
        //             Slider(tint, { tint = it }, valueRange = -0.5f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image tint, minus 50 to plus 50 percent" })
        //             Text("Fade: ${(fade * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        //             Slider(fade, { fade = it }, valueRange = 0f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image fade, 0 to 50 percent" })
        //         }
        if (adjustMode) Column(Modifier.padding(horizontal = 20.dp)) {
            val adjustmentLabels = mapOf(
                "Brightness" to stringResource(com.example.chat.R.string.media_released_editor_adjustment_brightness),
                "Contrast" to stringResource(com.example.chat.R.string.media_released_editor_adjustment_contrast),
                "Saturation" to stringResource(com.example.chat.R.string.media_released_editor_adjustment_saturation),
                "Warmth" to stringResource(com.example.chat.R.string.media_released_editor_adjustment_warmth),
                "Tint" to stringResource(com.example.chat.R.string.media_released_editor_adjustment_tint),
                "Fade" to stringResource(com.example.chat.R.string.media_released_editor_adjustment_fade),
            )
            Box {
                // MR-R4-043 prior: OutlinedButton(onClick = { adjustmentMenu = true }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Adjustment: $adjustmentField") }
                OutlinedButton(onClick = { adjustmentMenu = true }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(com.example.chat.R.string.media_released_editor_adjustment_format, adjustmentLabels[adjustmentField] ?: adjustmentField)) }
                androidx.compose.material3.DropdownMenu(expanded = adjustmentMenu, onDismissRequest = { adjustmentMenu = false }) {
                    listOf("Brightness", "Contrast", "Saturation", "Warmth", "Tint", "Fade").forEach { field ->
                        // MR-R4-043 prior: androidx.compose.material3.DropdownMenuItem(text = { Text(field) }, enabled = !saving,
                        androidx.compose.material3.DropdownMenuItem(text = { Text(adjustmentLabels[field] ?: field) }, enabled = !saving,
                            onClick = { adjustmentField = field; adjustmentMenu = false })
                    }
                }
            }
            when (adjustmentField) {
                "Brightness" -> {
                    // MR-R4-043 prior: Text("Brightness: ${"%+.0f".format(brightness * 100)}%", color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(com.example.chat.R.string.media_released_editor_adjustment_brightness_value, "%+.0f".format(brightness * 100)), color = MaterialTheme.colorScheme.onSurface)
            // MR-R4-043 prior: Slider(brightness, { brightness = it }, valueRange = -0.4f..0.4f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Brightness, minus 40 to plus 40 percent" })
            Slider(brightness, { brightness = it }, valueRange = -0.4f..0.4f, enabled = !saving, modifier = Modifier.semantics { contentDescription = brightnessDescription })
                }
                "Contrast" -> {
                    // MR-R4-043 prior: Text("Contrast: ${"%.0f".format(contrast * 100)}%", color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(com.example.chat.R.string.media_released_editor_adjustment_contrast_value, "%.0f".format(contrast * 100)), color = MaterialTheme.colorScheme.onSurface)
            // MR-R4-043 prior: Slider(contrast, { contrast = it }, valueRange = 0.5f..1.8f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Contrast, 50 to 180 percent" })
            Slider(contrast, { contrast = it }, valueRange = 0.5f..1.8f, enabled = !saving, modifier = Modifier.semantics { contentDescription = contrastDescription })
                }
                "Saturation" -> {
                    // MR-R4-043 prior: Text("Saturation: ${(saturation * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(com.example.chat.R.string.media_released_editor_adjustment_saturation_value, (saturation * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
            // MR-R4-043 prior: Slider(saturation, { saturation = it }, valueRange = 0f..2f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image saturation, 0 to 200 percent" })
            Slider(saturation, { saturation = it }, valueRange = 0f..2f, enabled = !saving, modifier = Modifier.semantics { contentDescription = saturationDescription })
                }
                "Warmth" -> {
                    // MR-R4-043 prior: Text("Warmth: ${"%+.0f".format(warmth * 100)}%", style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(com.example.chat.R.string.media_released_editor_adjustment_warmth_value, "%+.0f".format(warmth * 100)), style = MaterialTheme.typography.bodyMedium)
            // MR-R4-043 prior: Slider(warmth, { warmth = it }, valueRange = -0.5f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image warmth, minus 50 to plus 50 percent" })
            Slider(warmth, { warmth = it }, valueRange = -0.5f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = warmthDescription })
                }
                "Tint" -> {
                    // MR-R4-043 prior: Text("Tint: ${"%+.0f".format(tint * 100)}%", style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(com.example.chat.R.string.media_released_editor_adjustment_tint_value, "%+.0f".format(tint * 100)), style = MaterialTheme.typography.bodyMedium)
            // MR-R4-043 prior: Slider(tint, { tint = it }, valueRange = -0.5f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image tint, minus 50 to plus 50 percent" })
            Slider(tint, { tint = it }, valueRange = -0.5f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = tintDescription })
                }
                "Fade" -> {
                    // MR-R4-043 prior: Text("Fade: ${(fade * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(com.example.chat.R.string.media_released_editor_adjustment_fade_value, (fade * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
            // MR-R4-043 prior: Slider(fade, { fade = it }, valueRange = 0f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image fade, 0 to 50 percent" })
            Slider(fade, { fade = it }, valueRange = 0f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = fadeDescription })
                }
            }
        }
        }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (editorUsesTwoPanes(maxWidth.value)) {
                Row(Modifier.fillMaxSize()) {
                    editorPreview(Modifier.weight(1f).fillMaxHeight())
                    editorTools(Modifier.width(320.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    editorPreview(Modifier.weight(1f).fillMaxWidth())
                    editorTools(Modifier.fillMaxWidth().heightIn(max = (configuration.screenHeightDp * 0.45f).dp.coerceIn(120.dp, 360.dp)))
                }
            }
        }

    }
    if (com.example.chat.BuildConfig.DEBUG) EditorDebugFrameOverlay(
        Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(8.dp),
    )
    } // Debug-only overlay host.
}

/** Immutable handoff from main-thread edit controls to the export worker. */
private data class EditorRenderSnapshot(
    val brightness: Float, val contrast: Float, val strokes: List<EditorStroke>,
    val text: String, val textX: Float, val textY: Float, val textScale: Float, val textColor: Int,
    val saturation: Float, val warmth: Float, val tint: Float, val fade: Float,
    val alignment: EditorTextAlignment, val strip: Boolean,
)

/** Canonical paths also reject a raw local path or symlink that points at the source. */
internal fun editorOutputAliasesSource(source: String, output: File): Boolean {
    val uri = android.net.Uri.parse(source)
    val local = when (uri.scheme?.lowercase(java.util.Locale.ROOT)) {
        null, "" -> File(source)
        "file" -> File(uri.path ?: return false)
        else -> return false
    }
    return output.canonicalFile == local.canonicalFile
}

// Original strokes used one fixed 0.008f width and full opacity.
internal data class EditorStroke(val color: Long, val points: List<Offset>, val widthFraction: Float = 0.008f, val opacity: Float = 1f)
internal fun editorBrushWidth(value: Float): Float = if (value.isFinite()) value.coerceIn(0.001f, 0.05f) else 0.008f
internal fun editorBrushOpacity(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
// Previous brightness/contrast-only implementation retained for comparison:
// internal fun editorColorValues(brightness: Float, contrast: Float): FloatArray {
//     val shift = brightness * 255f + 128f * (1f - contrast)
//     return floatArrayOf(contrast,0f,0f,0f,shift, 0f,contrast,0f,0f,shift, 0f,0f,contrast,0f,shift, 0f,0f,0f,1f,0f)
// }
internal fun editorColorValues(brightness: Float, contrast: Float, saturation: Float = 1f, warmth: Float = 0f, tint: Float = 0f, fade: Float = 0f): FloatArray =
    buildEditorColorValues(brightness, contrast, saturation, warmth, tint, fade)
internal fun renderEditedImage(original: Bitmap, brightness: Float, contrast: Float, strokes: List<EditorStroke>,
    text: String = "", textX: Float = 0.1f, textY: Float = 0.3f, textScale: Float = 0.07f,
    textColor: Int = android.graphics.Color.WHITE, flattenAlpha: Boolean = false,
    saturation: Float = 1f, warmth: Float = 0f, tint: Float = 0f, fade: Float = 0f,
    textAlignment: EditorTextAlignment = EditorTextAlignment.Left, textStrip: Boolean = false): Bitmap {
    return withOwnedEditorBitmap(original.width, original.height) { output ->
    val canvas = AndroidCanvas(output)
    if (flattenAlpha) canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawBitmap(original, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(AndroidColorMatrix(editorColorValues(brightness, contrast, saturation, warmth, tint, fade))) })
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = original.width * 0.008f }
    strokes.forEach { stroke ->
        paint.color = stroke.color.toInt()
        paint.alpha = (android.graphics.Color.alpha(stroke.color.toInt()) * editorBrushOpacity(stroke.opacity)).toInt().coerceIn(0, 255)
        paint.strokeWidth = original.width * editorBrushWidth(stroke.widthFraction)
        if (stroke.points.size == 1) { val p = stroke.points.first(); canvas.drawCircle(p.x * original.width, p.y * original.height, paint.strokeWidth / 2f, paint) }
        stroke.points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x * original.width, a.y * original.height, b.x * original.width, b.y * original.height, paint) }
    }
    // Previous single-line export retained for review:
    // if (text.isNotBlank()) canvas.drawText(ImageTextOverlayPolicy.text(text), original.width * ImageTextOverlayPolicy.x(textX),
    // original.height * ImageTextOverlayPolicy.y(textY), Paint(Paint.ANTI_ALIAS_FLAG).apply {
    // color = textColor; textSize = original.width * ImageTextOverlayPolicy.scale(textScale)
    // textSize = ImageTextOverlayPolicy.fittedTextSize(original.width.toFloat(), textX, textSize, measureText(ImageTextOverlayPolicy.text(text)))
    // setShadowLayer(original.width * 0.004f, 0f, 0f, android.graphics.Color.BLACK)
    // })
    drawEditorText(canvas, original.width.toFloat(), original.height.toFloat(), text,
        textX, textY, textScale, textColor, textAlignment, textStrip)
    }
}

/** A failed render owns and recycles only its newly allocated output, never the source. */
internal fun withOwnedEditorBitmap(width: Int, height: Int, draw: (Bitmap) -> Unit): Bitmap {
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        draw(output)
        return output
    } catch (error: Throwable) {
        try { output.recycle() } catch (cleanup: Throwable) {
            if (cleanup !== error) error.addSuppressed(cleanup)
        }
        throw error
    }
}

/** Centered preset crop, never upscales. Caller retains the original input file. */
internal fun editorCenterCrop(width: Int, height: Int, ratio: Float): android.graphics.Rect {
    require(width > 0 && height > 0 && ratio > 0f)
    val w = minOf(width, (height * ratio).toInt()).coerceAtLeast(1)
    val h = minOf(height, (width / ratio).toInt()).coerceAtLeast(1)
    return android.graphics.Rect((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
}

/* Previous implementation fragments retained (net219):
                    var safeToDeleteOutput = false
                            safeToDeleteOutput = true
                                file.outputStream().use { check(compressEditorImage(edited, encoding, it)) }
                                coroutineContext.ensureActive()
                            if (safeToDeleteOutput) file.delete()
    val output = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
    return output
*/
