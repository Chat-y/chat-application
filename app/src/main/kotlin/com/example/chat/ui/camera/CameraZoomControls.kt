package com.example.chat.ui.camera

import com.example.chat.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Reference zoom control (user video 2026-10-30, user-confirmed replacement of
 *  the arc design): a compact dark pill of preset values above the shutter -
 *  tapping a preset jumps, tapping the active value (or the pill) opens the
 *  horizontal slider (live value above, "-" / "+" ends, yellow fill + marker,
 *  drag the track). While a video records the slider stays open, because the
 *  reference zooms during recording. */
/** Presets offered by the zoom pill: the supported endpoints plus 1x/2x/4x/6x
 *  that fall inside the camera's real zoom range - never an unreachable value. */
fun zoomPresetsFor(min: Float, max: Float): List<Float> =
    (listOf(min, 1f, 2f, 4f, 6f, max).filter { it in min..max }).distinct().sorted()

/** The range a failed zoom request proved unreachable is clamped away, so the
 *  pill never keeps offering a preset the camera just rejected. */
fun effectiveZoomMax(reportedMax: Float, workingMax: Float): Float = minOf(reportedMax, workingMax)

@Composable
fun CameraZoomControls(current:Float,min:Float,max:Float,enabled:Boolean,forceSlider:Boolean=false,onZoom:(Float)->Unit) {
    var proposed by remember(current,min,max) { mutableStateOf(clampCameraZoom(current,min,max)) }
    var expanded by remember { mutableStateOf(false) }
    val presets = remember(min,max) { zoomPresetsFor(min,max) }
    val showSlider = (expanded || forceSlider) && max > min
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val tight = cameraControlsTight(configuration.screenWidthDp, configuration.screenHeightDp)
    val presetsLabel = stringResource(R.string.camera_zoom_presets)
    val zoomLabel = stringResource(R.string.camera_zoom)
    val zoomState = stringResource(R.string.camera_zoom_ratio, proposed)
    val zoomValueLabel = stringResource(R.string.camera_zoom_value, proposed)
    // CAM-R1-058 prior fixed gutter: Column(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
    Column(Modifier.fillMaxWidth().padding(horizontal=(if (tight) 12 else 20).dp),horizontalAlignment=Alignment.CenterHorizontally) {
        if(!showSlider) {
            // Compact pill (reference idle): preset values in a dark pill, active yellow.
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x99141414))
                    // CAM-R4-034 previous: .clickable(enabled=enabled){expanded=true}
                    .cameraActionFocusRing().clickable(enabled=enabled){expanded=true}
                    // CAM-R4-030 previous spacing: .padding(horizontal=8.dp,vertical=2.dp)
                    .padding(horizontal=8.dp,vertical=4.dp)
                    // CAM-R4-004 prior: .semantics{contentDescription="Zoom presets"},
                    .semantics{contentDescription=presetsLabel},
                verticalAlignment=Alignment.CenterVertically,
            ) {
                presets.forEach { ratio ->
                    val active=abs(current-ratio)<.05f
                    val presetLabel = stringResource(if(ratio%1f==0f) R.string.camera_zoom_preset_whole else R.string.camera_zoom_preset_fraction, ratio)
                    Text(
                        String.format(java.util.Locale.ROOT,if(ratio%1f==0f)"%.0fx" else "%.1fx",ratio),
                        color = if(active) Color(0xFFFFD54F) else Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            // CAM-R4-034 previous: .clickable(enabled=enabled){ if(active) expanded=true else {proposed=ratio;onZoom(ratio)} }
                            .cameraActionFocusRing().clickable(enabled=enabled){ if(active) expanded=true else {proposed=ratio;onZoom(ratio)} }
                            // CAM-R1-058 previous preset padding: .padding(horizontal=10.dp,vertical=6.dp)
                            // CAM-R4-030 previous spacing: .padding(horizontal=(if (tight) 6 else 10).dp,vertical=6.dp)
                            .padding(horizontal=(if (tight) 8 else 12).dp,vertical=8.dp)
                            // CAM-R4-004 prior: .semantics{contentDescription=String.format(java.util.Locale.ROOT,if(ratio%1f==0f)"Zoom %.0fx" else "%.1fx",ratio);selected=active;role=Role.Button},
                            .semantics{contentDescription=presetLabel;selected=active;role=Role.Button},
                    )
                }
            }
        } else {
            // Horizontal slider (reference): live value above the track.
            Text(
                String.format(java.util.Locale.ROOT,if(proposed%1f==0f)"%.0fx" else "%.1fx",proposed),
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    // CAM-R4-034 previous: .clickable(enabled=enabled && !forceSlider){expanded=false}
                    .cameraActionFocusRing().clickable(enabled=enabled && !forceSlider){expanded=false}
                    // CAM-R4-004 prior: .semantics{contentDescription=String.format(java.util.Locale.ROOT,"Zoom value %.1fx",proposed)},
                    .semantics{contentDescription=zoomValueLabel},
            )
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x99141414))
                    .cameraActionFocusRing()
                    .focusable(enabled = enabled)
                    .semantics {
                        // CAM-R4-004 prior: contentDescription = "Camera zoom"
                        contentDescription = zoomLabel
                        // CAM-R4-004 prior: stateDescription = String.format(java.util.Locale.ROOT, "Zoom %.1fx", proposed)
                        stateDescription = zoomState
                        progressBarRangeInfo = ProgressBarRangeInfo(proposed, min..max)
                        setProgress { requested ->
                            if (enabled) { proposed = clampCameraZoom(requested, min, max); onZoom(proposed); true } else false
                        }
                    }
                    .pointerInput(min,max,enabled) {
                        if(!enabled) return@pointerInput
                        var value = current
                        detectHorizontalDragGestures(
                            onDragStart = { value = current },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val widthPx = size.width.coerceAtLeast(1)
                                val f0 = ((value-min)/(max-min)).coerceIn(0f,1f)
                                val f1 = (f0 + dragAmount/widthPx).coerceIn(0f,1f)
                                val next = min + f1*(max-min)
                                if(abs(next-value)>0.005f) { value = next; proposed = clampCameraZoom(next,min,max); onZoom(proposed) }
                            },
                        )
                    },
            ) {
                val trackPad = 30.dp
                val f = ((proposed-min)/(max-min)).coerceIn(0f,1f)
                Text("-",color=Color.White,fontSize=14.sp,modifier=Modifier.align(Alignment.CenterStart).padding(start=12.dp))
                Text("+",color=Color.White,fontSize=14.sp,modifier=Modifier.align(Alignment.CenterEnd).padding(end=12.dp))
                Box(Modifier.align(Alignment.Center).padding(horizontal=trackPad).fillMaxWidth().height(2.dp).background(Color(0x44FFFFFF)))
                Box(Modifier.align(Alignment.CenterStart).padding(start=trackPad).fillMaxWidth(f).height(2.dp).background(Color(0xFFFFD54F)))
                Box(Modifier.align(Alignment.CenterStart).padding(start=trackPad+(maxWidth-trackPad*2-14.dp)*f).size(14.dp).clip(CircleShape).background(Color(0xFFFFD54F)))
            }
        }
        // CAM-R4-004 prior: if(current>=4f) Text("High zoom loses detail; hold steady.",color=Color.White)
        if(current>=4f) Text(stringResource(R.string.camera_zoom_warning),color=Color.White)
    }
}
