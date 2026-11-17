package com.example.chat.ui.camera

import com.example.chat.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.chat.ui.theme.LocalAppColors
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import kotlin.math.atan2
import kotlin.math.abs

@Composable
fun CameraOptionsPanel(
    options: CameraCaptureOptions,
    stabilizationSupported: Boolean,
    nightSupported: Boolean,
    onChange: (CameraCaptureOptions) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showPhotoTimer: Boolean = true,
    frontCamera: Boolean = false,
) {
    val panelLabel = stringResource(R.string.camera_options_panel)
    // Depth-model consistency (looks-only): the lane's other floating surfaces
    // (LensBrowserSheet tiles, EffectPickerSheet container) separate from the
    // live preview with a 1.dp borderSubtle outline instead of shadow/elevation.
    // The panel had neither, so over a bright viewfinder it read as unseparated.
    // Border draws inside the existing bounds: no layout, spacing or content change.
    Column(modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
        .border(1.dp, LocalAppColors.current.borderSubtle, RoundedCornerShape(10.dp))
        .heightIn(max = 350.dp).verticalScroll(rememberScrollState())
        // CAM-R4-030 previous spacing: .semantics { contentDescription="Camera options panel" }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // CAM-R4-004 prior semantics label: "Camera options panel"
        .semantics { contentDescription=panelLabel }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            // CAM-R4-004 prior: Text("Camera options",color=androidx.compose.material3.MaterialTheme.colorScheme.onSurface,modifier=Modifier.weight(1f))
            Text(stringResource(R.string.camera_options_title),color=androidx.compose.material3.MaterialTheme.colorScheme.onSurface,modifier=Modifier.weight(1f))
            // CAM-R4-004 prior: androidx.compose.material3.TextButton(modifier = Modifier.cameraActionFocusRing(), shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onClose){Text("Close",color=androidx.compose.material3.MaterialTheme.colorScheme.onSurface)}
            androidx.compose.material3.TextButton(modifier = Modifier.cameraActionFocusRing(), shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onClose){Text(stringResource(R.string.camera_close),color=androidx.compose.material3.MaterialTheme.colorScheme.onSurface)}
        }
        // CAM-R4-004 prior: Text("Capture", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.camera_capture_group), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        // CAM-R4-004 prior: Text("Ratio",color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.camera_ratio),color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        // CAM-R4-030 previous spacing: Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptureAspect.entries.forEach { aspect ->
                OptionChip(aspect.label, options.aspect == aspect, { onChange(options.copy(aspect = aspect)) }, Modifier.weight(1f))
            }
        }
        if (showPhotoTimer) {
        // CAM-R4-004 prior: Text("Timer",color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.camera_timer),color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        // CAM-R4-030 previous spacing: Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 3, 5, 10).forEach { seconds ->
                // CAM-R4-004 prior: OptionChip(if (seconds == 0) "Off" else "${seconds}s", options.timerSeconds == seconds,
                OptionChip(if (seconds == 0) stringResource(R.string.camera_timer_off) else stringResource(R.string.camera_timer_seconds, seconds), options.timerSeconds == seconds,
                    { onChange(options.copy(timerSeconds = seconds)) }, Modifier.weight(1f))
            }
        }
        }
        if (showPhotoTimer) {
            // CAM-R4-004 prior: OptionChip(if (frontCamera) "Mirror saved selfies" else "Mirror selfies · front camera only",
            OptionChip(if (frontCamera) stringResource(R.string.camera_mirror) else stringResource(R.string.camera_mirror_unavailable),
                options.mirrorSelfies, { onChange(options.copy(mirrorSelfies = !options.mirrorSelfies)) },
                Modifier.fillMaxWidth(), frontCamera)
        }
        // CAM-R4-004 prior: Text("Overlays", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.camera_overlays_group), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        // CAM-R4-030 previous spacing: Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // CAM-R4-004 prior: OptionChip("Grid lines", options.grid, { onChange(options.copy(grid = !options.grid)) }, Modifier.weight(1f))
            OptionChip(stringResource(R.string.camera_grid), options.grid, { onChange(options.copy(grid = !options.grid)) }, Modifier.weight(1f))
            // CAM-R4-004 prior: OptionChip("Level", options.level, { onChange(options.copy(level = !options.level)) }, Modifier.weight(1f))
            OptionChip(stringResource(R.string.camera_level), options.level, { onChange(options.copy(level = !options.level)) }, Modifier.weight(1f))
        }
        // CAM-R4-004 prior: Text("Video / preview", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.camera_video_preview_group), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        // CAM-R4-004 prior: OptionChip(if (stabilizationSupported) "Preview stabilization" else "Stabilization unavailable on this camera",
        OptionChip(if (stabilizationSupported) stringResource(R.string.camera_stabilization) else stringResource(R.string.camera_stabilization_unavailable),
            options.stabilization && stabilizationSupported, { if (stabilizationSupported) onChange(options.copy(stabilization = !options.stabilization)) }, Modifier.fillMaxWidth(), stabilizationSupported)
        // CAM-R4-030 previous spacing: Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // CAM-R4-004 prior: OptionChip("Watermark", options.watermark, { onChange(options.copy(watermark = !options.watermark)) }, Modifier.weight(1f))
            OptionChip(stringResource(R.string.camera_watermark), options.watermark, { onChange(options.copy(watermark = !options.watermark)) }, Modifier.weight(1f))
            // CAM-R4-004 prior: OptionChip("Clarity", options.clarity, { onChange(options.copy(clarity = !options.clarity)) }, Modifier.weight(1f))
            OptionChip(stringResource(R.string.camera_clarity), options.clarity, { onChange(options.copy(clarity = !options.clarity)) }, Modifier.weight(1f))
        }
        // CAM-R4-004 prior: OptionChip(if (nightSupported) "Night mode · hold still" else "Night mode unavailable on this camera",
        OptionChip(if (nightSupported) stringResource(R.string.camera_night) else stringResource(R.string.camera_night_unavailable),
            options.night && nightSupported, { if (nightSupported) onChange(options.copy(night = !options.night)) }, Modifier.fillMaxWidth(), nightSupported)
        // CAM-R4-004 prior: Text("Night and Portrait replace HDR while active; these extension modes do not stack.",
        Text(stringResource(R.string.camera_extension_exclusion_note),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        // CAM-R4-004 prior: Text("Night uses the phone maker's supported multi-frame mode. It cannot see in total darkness. Timer, grid and level still work. Face lenses and preview stabilization are off during Night.",color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.camera_night_note),color=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        // CAM-R4-004 prior: Text("Clarity adjusts photo contrast only. Stabilization affects preview where supported; it does not restore blurred detail. Document scanning has its own crop review.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.camera_clarity_note), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    Box(modifier.heightIn(min=48.dp).clip(com.example.chat.ui.kit.ChatyActionShape).background(if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, com.example.chat.ui.kit.ChatyActionShape)
        .clearAndSetSemantics {
            this.text = AnnotatedString(text)
            this.selected = selected
            role = Role.Button
            if(!enabled) disabled() else onClick { onClick(); true }
        }
        // CAM-R4-034 previous: .clickable(enabled = enabled, onClick = onClick).padding(horizontal=10.dp,vertical=12.dp), contentAlignment = Alignment.Center) {
        // CAM-R4-030 previous spacing: .cameraActionFocusRing().clickable(enabled = enabled, onClick = onClick).padding(horizontal=10.dp,vertical=12.dp), contentAlignment = Alignment.Center) {
        .cameraActionFocusRing().clickable(enabled = enabled, onClick = onClick).padding(horizontal=12.dp,vertical=12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.onPrimary else if (enabled) androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}

@Composable
fun CameraGridAndLevel(grid: Boolean, level: Boolean, modifier: Modifier = Modifier) {
    // Resolve theme tokens during composition, not inside Canvas drawing.
    val overlayColors = rememberCameraOverlayColors()
    val context = LocalContext.current
    var roll by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(level) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size >= 3) {
                    // Display rotation determines which sensor axes are horizontal.
                    @Suppress("DEPRECATION")
                    val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay?.rotation ?: 0
                    val x = event.values[0].toDouble()
                    val y = event.values[1].toDouble()
                    val angle = when (rotation) {
                        android.view.Surface.ROTATION_90 -> atan2(y, x)
                        android.view.Surface.ROTATION_180 -> atan2(x, -y)
                        android.view.Surface.ROTATION_270 -> atan2(-y, -x)
                        else -> atan2(-x, y)
                    }
                    roll = if (kotlin.math.hypot(x,y) < 1.0) null else Math.toDegrees(angle).toFloat()
                }
            }
        }
        if (level && sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager?.unregisterListener(listener) }
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            if (grid) for (n in 1..2) {
                // Previous value: Color.White.copy(alpha = .4f)
                drawLine(overlayColors.content.copy(alpha = .4f), Offset(size.width * n / 3, 0f), Offset(size.width * n / 3, size.height), 1f)
                // Previous value: Color.White.copy(alpha = .4f)
                drawLine(overlayColors.content.copy(alpha = .4f), Offset(0f, size.height * n / 3), Offset(size.width, size.height * n / 3), 1f)
            }
        }
        // CAM-R4-004 prior: if (level) Text(roll?.let { "Level ${it.toInt()}°" } ?: "Level sensor unavailable", color = if (roll != null && abs(roll!!) < 2) Color.Green else Color.White,
        // Previous value: Color.Green / Color.White
        if (level) Text(if (roll != null) stringResource(R.string.camera_level_degrees, roll!!.toInt()) else stringResource(R.string.camera_level_unavailable), color = if (roll != null && abs(roll!!) < 2) overlayColors.success else overlayColors.content,
            // Previous value: Color(0x88000000)
            modifier = Modifier.align(Alignment.Center).background(overlayColors.scrimStrong, RoundedCornerShape(10.dp)).padding(8.dp))
    }
}

// CAM-R4-034 prior UI modifier lines retained inactive:
//             androidx.compose.material3.TextButton(shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onClose){Text("Close",color=androidx.compose.material3.MaterialTheme.colorScheme.onSurface)}
