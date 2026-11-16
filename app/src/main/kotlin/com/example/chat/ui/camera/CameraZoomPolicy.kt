package com.example.chat.ui.camera

/** Analysis budgets share units explicitly: native analyzer nanoseconds, UI solve milliseconds. */
internal const val CAMERA_FACE_INTERVAL_MS = 66L
internal const val CAMERA_FACE_INTERVAL_NS = CAMERA_FACE_INTERVAL_MS * 1_000_000L
internal const val CAMERA_SEGMENT_INTERVAL_MS = 150L

internal fun clampCameraZoom(requested:Float,min:Float,max:Float):Float =
    if (!requested.isFinite() || !min.isFinite() || !max.isFinite() || min<=0 || max<min) 1f else requested.coerceIn(min,max)
internal fun shouldAnalyzeCameraFrame(enabled:Boolean,now:Long,last:Long):Boolean = enabled && now-last>=CAMERA_FACE_INTERVAL_NS
