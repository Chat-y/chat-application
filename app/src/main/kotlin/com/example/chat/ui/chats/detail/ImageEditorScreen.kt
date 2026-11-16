package com.example.chat.ui.chats.detail

// Single-line policy now used inside the shared multiline renderer.
// import com.example.chat.core.media.ImageTextOverlayPolicy
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
fun ImageEditorScreen(source: String, onCancel: () -> Unit, onApply: (File) -> Unit, outputFile: File? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var originalBitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    // Original Unit-key lifetime let an old save survive a source replacement.
    // var saveJob by remember { mutableStateOf<Job?>(null) }; var active by remember { mutableStateOf(true) }
    // DisposableEffect(Unit) { onDispose { active = false; saveJob?.cancel() } }
    var saveJob by remember(source) { mutableStateOf<Job?>(null) }
    var active by remember(source) { mutableStateOf(true) }
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
    var textOverlay by remember(source) { mutableStateOf("") }
    var textMode by remember(source) { mutableStateOf(false) }
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
            bitmap = withContext(Dispatchers.IO) {
                val loader = ImageLoader(context)
                try {
                    val result = loader.execute(ImageRequest.Builder(context).data(source).allowHardware(false).size(decodeEdge)
                        .memoryCachePolicy(coil.request.CachePolicy.DISABLED).diskCachePolicy(coil.request.CachePolicy.DISABLED).build())
                    (result as? SuccessResult)?.drawable?.toBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
                        ?: throw IllegalStateException("Unable to load image for editing")
                } finally { loader.shutdown() }
            }
            originalBitmap = bitmap
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            // Original exception-message UI could expose a signed URL/provider path.
            // error = e.message ?: "Unable to load image"
            error = "Could not load this image. Close the editor and choose it again."
        }
    }
    androidx.activity.compose.BackHandler(onBack = ::cancelEditor)
    val editsSummary = "${strokes.size} strokes, saturation ${(saturation * 100).toInt()} percent, warmth ${(warmth * 100).toInt()} percent, tint ${(tint * 100).toInt()} percent, fade ${(fade * 100).toInt()} percent, brightness ${"%+.0f".format(brightness * 100)} percent, contrast ${"%.0f".format(contrast * 100)} percent, text alignment ${textAlignment.name}, text strip ${if (textStrip) "on" else "off"}, text: ${textOverlay.ifBlank { "none" }}"
    val accessibility = androidx.compose.ui.platform.LocalView.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().imePadding()
        .semantics { customActions = listOf(CustomAccessibilityAction("Edits summary") { accessibility.announceForAccessibility(editsSummary); true }) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(::cancelEditor) { Text("Cancel") }
            Text("Edit a copy", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (saving) return@TextButton
                val original = bitmap ?: return@TextButton
                saving = true
                // Snapshot render values on main before entering IO. No mutable preview state in export.
                val export = EditorRenderSnapshot(brightness, contrast, strokes.toList(), textOverlay,
                    textX, textY, textScale, ink.toArgb(), saturation, warmth, tint, fade, textAlignment, textStrip)
                saveJob = scope.launch {
                    val sourceExtension = android.net.Uri.parse(source).lastPathSegment?.substringAfterLast('.', "")?.lowercase(java.util.Locale.ROOT)
                    val preserveAlpha = (original.hasAlpha() || sourceExtension == "png") && (outputFile == null || outputFile.extension.equals("png", true))
                    val file = outputFile ?: File(context.cacheDir, "chaty-outgoing-edited-${java.util.UUID.randomUUID()}.${if (preserveAlpha) "png" else "jpg"}")
                    var handedOff = false
                    // Original check handled file:// only and ran before try/finally:
                    // val aliasesOriginal = sourceUri.scheme == "file" && file.canonicalPath == File(sourceUri.path.orEmpty()).canonicalPath
                    var safeToDeleteOutput = false
                    try {
                        withContext(Dispatchers.IO) {
                            require(!editorOutputAliasesSource(source, file)) { "Output cannot overwrite the source." }
                            safeToDeleteOutput = true
                            val edited = renderEditedImage(original, export.brightness, export.contrast, export.strokes,
                                export.text, export.textX, export.textY, export.textScale, export.textColor,
                                flattenAlpha = !preserveAlpha, saturation = export.saturation, warmth = export.warmth,
                                tint = export.tint, fade = export.fade, textAlignment = export.alignment, textStrip = export.strip)
                            try {
                                file.outputStream().use { check(edited.compress(if (preserveAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it)) }
                                coroutineContext.ensureActive()
                            } finally { edited.recycle() }
                        }
                        coroutineContext.ensureActive()
                        if (active) {
                            exportLineage.register(file, source)
                            onApply(file)
                            handedOff = true
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        // Original: error = e.message ?: "Could not save edit"
                        error = "Could not save the edited copy. Check device storage and try again."
                    }
                    finally {
                        if (!handedOff) {
                            runCatching { exportLineage.forget(file) }
                            if (safeToDeleteOutput) file.delete()
                        }
                        saving = false
                    }
                }
            }, enabled = bitmap != null && !saving) { Text(if (saving) "Saving" else "Apply") }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val image = bitmap
            if (image == null) {
                if (error == null) CircularProgressIndicator() else Text(error!!, color = MaterialTheme.colorScheme.onSurface)
            } else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ratio = image.width.toFloat() / image.height
                val width = minOf(maxWidth, maxHeight * ratio)
                Box(Modifier.width(width).aspectRatio(ratio)) {
                    Image(image.asImageBitmap(), "Image being edited", Modifier.fillMaxSize(), colorFilter = ColorFilter.colorMatrix(ColorMatrix(editorColorValues(brightness, contrast, saturation, warmth, tint, fade))))
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
                        strokes.forEach { stroke ->
                            val baseColor = Color(stroke.color.toInt())
                            val color = baseColor.copy(alpha = baseColor.alpha * editorBrushOpacity(stroke.opacity))
                            val widthPx = size.width * editorBrushWidth(stroke.widthFraction)
                            if (stroke.points.size == 1) { val p = stroke.points.first(); drawCircle(color, widthPx / 2f, Offset(p.x * size.width, p.y * size.height)) }
                            stroke.points.zipWithNext().forEach { (a, b) -> drawLine(color, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), widthPx, StrokeCap.Round) }
                        }
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
        // Keep the preview and Apply row present while extra tools scroll in a bounded pane.
        Column(Modifier.fillMaxWidth().heightIn(max = (configuration.screenHeightDp * 0.45f).dp.coerceIn(120.dp, 360.dp)).verticalScroll(rememberScrollState())) {
        if (bitmap?.hasAlpha() == true && outputFile != null && !outputFile.extension.equals("png", true)) Text("This output uses JPEG. Transparency will be flattened to white.", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(8.dp))
        Text("Edits remove location data and bake in rotation.", color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(8.dp), style = MaterialTheme.typography.bodySmall)
        if (saving) Text("Saving edited copy", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton({ textMode = !textMode; draw = false; adjustMode = false; accessibility.announceForAccessibility(if (textMode) "Text mode on, drag to move text or use position sliders" else "Text mode off") }, enabled = !saving) { Text(if (textMode) "Text on" else "Text") }
            TextButton({ draw = !draw; textMode = false; adjustMode = false; accessibility.announceForAccessibility(if (draw) "Drawing on, drag to draw" else "Drawing off") }, enabled = !saving) { Text(if (draw) "Drawing on" else "Draw") }
            TextButton({ adjustMode = !adjustMode; textMode = false; draw = false; accessibility.announceForAccessibility(if (adjustMode) "Adjustments open" else "Adjustments closed") }, enabled = !saving) { Text(if (adjustMode) "Adjust on" else "Adjust") }
            // Original stroke Undo rebuilt the outer list: strokes = strokes.dropLast(1)
            TextButton({
                if (strokes.isNotEmpty()) {
                    if (redoStrokes.size >= 20) redoStrokes.removeAt(0)
                    redoStrokes.add(strokes.removeAt(strokes.lastIndex))
                }
            }, enabled = strokes.isNotEmpty() && !saving) { Text("Undo stroke") }
            TextButton({
                if (redoStrokes.isNotEmpty()) strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
            }, enabled = redoStrokes.isNotEmpty() && !saving) { Text("Redo stroke") }
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
            }, enabled = bitmap != null && !saving) { Text("Rotate") }
            TextButton({ brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f; tint = 0f; fade = 0f; adjustMode = false; strokes.clear(); redoStrokes.clear(); brushWidth = 0.008f; brushOpacity = 1f; paletteOpen = false; textOverlay = ""; textX = 0.1f; textY = 0.3f; textScale = 0.07f; textAlignment = EditorTextAlignment.Left; textStrip = false; draw = false; textMode = false; bitmap = originalBitmap }, enabled = !saving) { Text("Reset") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("Crop", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(12.dp))
            listOf("Square" to 1f, "Portrait" to (3f / 4f), "Landscape" to (4f / 3f)).forEach { (label, ratio) ->
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
        if (textMode) Column(Modifier.fillMaxWidth().heightIn(max = 170.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            // Original input flattened to one line: OutlinedTextField(... ImageTextOverlayPolicy.text(it), singleLine = true)
            OutlinedTextField(textOverlay, { textOverlay = editorOverlayText(it) }, minLines = 2, maxLines = 4,
                label = { Text("Text on image") }, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface))
            Text("Up to 4 lines, 120 characters", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                EditorTextAlignment.entries.forEach { alignment ->
                    TextButton(onClick = { textAlignment = alignment }, enabled = !saving,
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            selected = textAlignment == alignment
                            contentDescription = "Align text ${alignment.name.lowercase(java.util.Locale.ROOT)}"
                        }) { Text(alignment.name) }
                }
                TextButton(onClick = { textStrip = !textStrip }, enabled = !saving,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { selected = textStrip }) {
                    Text(if (textStrip) "Strip on" else "Text strip")
                }
            }
            Text("Horizontal position: ${(textX * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface)
            Slider(textX, { textX = it }, valueRange = 0f..0.9f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Horizontal text position" })
            Text("Vertical position: ${(textY * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface)
            Slider(textY, { textY = it }, valueRange = 0.1f..0.95f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Vertical text position" })
            Text("Text size: ${(textScale * 100).toInt()}% of image width", color = MaterialTheme.colorScheme.onSurface)
            Slider(textScale, { textScale = it }, valueRange = 0.03f..0.15f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Text size" })
        }
        if (draw) Column(Modifier.fillMaxWidth().heightIn(max = 140.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Text("Pen width", style = MaterialTheme.typography.bodyMedium)
                listOf("Fine" to 0.004f, "Medium" to 0.008f, "Thick" to 0.016f).forEach { (name, value) ->
                    TextButton(onClick = { brushWidth = value }, enabled = !saving,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { selected = brushWidth == value; contentDescription = "$name pen width" }) { Text(name) }
                }
            }
            Text("Ink opacity: ${(brushOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(brushOpacity, { brushOpacity = it }, enabled = !saving, valueRange = 0.1f..1f,
                modifier = Modifier.semantics { contentDescription = "Ink opacity, 10 to 100 percent" })
        }
        if (draw || textMode) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
            // Original five quick ink colors remain; user-picked colors appear as named recents.
            (listOf("Red" to Color.Red, "Yellow" to Color.Yellow, "White" to Color.White, "Black" to Color.Black, "Cyan" to Color.Cyan) +
                recentInk.mapIndexed { index, color -> "Recent color ${index + 1}" to color }).forEach { (name, color) ->
                // Old glyph-only swatches: TextButton(...){ Text(if (ink == color) "●" else "○", color = color) }
                Box(Modifier.size(48.dp).selectable(selected = ink == color, enabled = !saving, role = Role.RadioButton, onClick = { ink = color })
                    .semantics { contentDescription = "$name ink"; stateDescription = if (ink == color) "Selected" else "Not selected" }
                    .padding(4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(color)
                        .border(if (ink == color) 2.dp else 1.dp, if (ink == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                    if (ink == color) Text("✓", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.surface, CircleShape).padding(horizontal = 2.dp).clearAndSetSemantics {})
                }
            }
        }
        if (draw || textMode) TextButton(onClick = {
            if (!paletteOpen) {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(ink.toArgb(), hsv)
                paletteHue = hsv[0]; paletteSaturation = hsv[1]; paletteValue = hsv[2]
            }
            paletteOpen = !paletteOpen
        }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (paletteOpen) "Close color palette" else "More ink colors") }
        if ((draw || textMode) && paletteOpen) Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Text("Hue: ${paletteHue.toInt()} degrees", style = MaterialTheme.typography.bodyMedium)
            // Spectrum colors describe ink, not app chrome. They never override theme tokens.
            Box(Modifier.fillMaxWidth().height(12.dp).background(androidx.compose.ui.graphics.Brush.horizontalGradient(
                (0..6).map { step -> Color(android.graphics.Color.HSVToColor(floatArrayOf(step * 60f, 1f, 1f))) })))
            Slider(paletteHue, { paletteHue = it }, enabled = !saving, valueRange = 0f..360f,
                modifier = Modifier.semantics { contentDescription = "Ink hue, 0 to 360 degrees" })
            Text("Saturation: ${(paletteSaturation * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(paletteSaturation, { paletteSaturation = it }, enabled = !saving, valueRange = 0f..1f,
                modifier = Modifier.semantics { contentDescription = "Ink saturation" })
            Text("Color brightness: ${(paletteValue * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(paletteValue, { paletteValue = it }, enabled = !saving, valueRange = 0f..1f,
                modifier = Modifier.semantics { contentDescription = "Ink color brightness" })
            val previewInk = Color(android.graphics.Color.HSVToColor(floatArrayOf(paletteHue, paletteSaturation, paletteValue)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(previewInk, CircleShape).border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                    .semantics { contentDescription = "Chosen ink preview" })
                TextButton(onClick = {
                    ink = previewInk
                    recentInk = (listOf(previewInk) + recentInk).distinct().take(5)
                    paletteOpen = false
                    accessibility.announceForAccessibility("Ink color selected")
                }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use ink color") }
            }
        }
        // Adjust controls used to be always visible; reveal only from the Adjust tool.
        if (adjustMode) Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Brightness: ${"%+.0f".format(brightness * 100)}%", color = MaterialTheme.colorScheme.onSurface)
            Slider(brightness, { brightness = it }, valueRange = -0.4f..0.4f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Brightness, minus 40 to plus 40 percent" })
            Text("Contrast: ${"%.0f".format(contrast * 100)}%", color = MaterialTheme.colorScheme.onSurface)
            Slider(contrast, { contrast = it }, valueRange = 0.5f..1.8f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Contrast, 50 to 180 percent" })
            Text("Saturation: ${(saturation * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(saturation, { saturation = it }, valueRange = 0f..2f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image saturation, 0 to 200 percent" })
            Text("Warmth: ${"%+.0f".format(warmth * 100)}%", style = MaterialTheme.typography.bodyMedium)
            Slider(warmth, { warmth = it }, valueRange = -0.5f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image warmth, minus 50 to plus 50 percent" })
            Text("Tint: ${"%+.0f".format(tint * 100)}%", style = MaterialTheme.typography.bodyMedium)
            Slider(tint, { tint = it }, valueRange = -0.5f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image tint, minus 50 to plus 50 percent" })
            Text("Fade: ${(fade * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(fade, { fade = it }, valueRange = 0f..0.5f, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Image fade, 0 to 50 percent" })
        }
        }
    }
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
    val output = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
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
    return output
}

/** Centered preset crop, never upscales. Caller retains the original input file. */
internal fun editorCenterCrop(width: Int, height: Int, ratio: Float): android.graphics.Rect {
    require(width > 0 && height > 0 && ratio > 0f)
    val w = minOf(width, (height * ratio).toInt()).coerceAtLeast(1)
    val h = minOf(height, (width / ratio).toInt()).coerceAtLeast(1)
    return android.graphics.Rect((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
}
