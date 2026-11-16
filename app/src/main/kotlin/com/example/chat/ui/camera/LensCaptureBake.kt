package com.example.chat.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Bounded file-to-file capture bake. No full JPEG/PNG byte arrays or intermediate PNG file. */
internal suspend fun bakeCameraLensFile(
    source: File, directory: File, previewSize: LensSize, lens: LensDefinition,
    poses: List<FacePose>, mirroredPreview: Boolean = false, onCreated: (File) -> Unit = {},
): File {
    // A cancellable dispatcher return can discard a just-created result before the caller owns it.
    var pendingOutput: File? = null
    try {
        return withContext(Dispatchers.IO) {
    require(previewSize.width.isFinite() && previewSize.height.isFinite() && previewSize.width > 0 && previewSize.height > 0)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot read the captured photo." }
    var sample = 1
    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
    while (longEdge / (sample * 2) >= 2048) sample *= 2
    val pixels = (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample)
    val runtime = Runtime.getRuntime()
    val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
    check(pixels <= Long.MAX_VALUE / 16L && pixels * 16L < available / 4L * 3L) {
        "Not enough memory for this lens copy. Original kept."
    }
    val ownedBitmaps = mutableListOf<Bitmap>()
    fun own(bitmap: Bitmap): Bitmap {
        if (ownedBitmaps.none { it === bitmap }) ownedBitmaps += bitmap
        return bitmap
    }
    var output: File? = null
    var completed = false
    try {
        coroutineContext.ensureActive()
        val raw = own(BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Cannot decode the captured photo."))
        val orientation = ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                // Previous: TRANSPOSE { postScale(-1f, 1f); postRotate(90f) }; TRANSVERSE { postScale(-1f, 1f); postRotate(270f) }
                // - compose order swapped the two corrections (scale-then-rotate maps 5<->7).
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        val upright = own(Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true))
        if (upright !== raw) raw.recycle()
        val resize = minOf(1f, 2048f / maxOf(upright.width, upright.height))
        val image = if (resize < 1f) own(Bitmap.createScaledBitmap(upright,
            (upright.width * resize).roundToInt().coerceAtLeast(1),
            (upright.height * resize).roundToInt().coerceAtLeast(1), true)) else upright
        if (image !== upright) upright.recycle()
        coroutineContext.ensureActive()
        val scale = max(previewSize.width / image.width, previewSize.height / image.height)
        val cropLeft = (previewSize.width - image.width * scale) / 2f
        val cropTop = (previewSize.height - image.height * scale) / 2f
        fun toImage(point: LensOffset) = LensOffset((point.dx - cropLeft) / scale, (point.dy - cropTop) / scale)
        val mapped = poses.map { pose ->
            FacePose(pose.trackingId,
                LensRect.fromPoints(toImage(LensOffset(pose.headBounds.left, pose.headBounds.top)),
                    toImage(LensOffset(pose.headBounds.right, pose.headBounds.bottom))),
                toImage(pose.leftEye), toImage(pose.rightEye), toImage(pose.noseBase), toImage(pose.mouthCenter),
                pose.rollRadians, pose.yawRadians, pose.pitchRadians, pose.confidence)
        }
        val rendered = own(Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888))
        val canvas = android.graphics.Canvas(rendered)
        canvas.drawBitmap(image, 0f, 0f, null)
        // Face poses are solved in mirrored front-preview space. Invert that preview transform
        // for the upright sensor pixels. Optional saved-selfie mirroring happens later to BOTH.
        val overlaySave = canvas.save()
        if (mirroredPreview) { canvas.translate(image.width.toFloat(), 0f); canvas.scale(-1f, 1f) }
        try { LensAnchorPainter.paint(androidx.compose.ui.graphics.Canvas(canvas), lens, mapped, 1f) }
        finally { canvas.restoreToCount(overlaySave) }
        val destination = File.createTempFile("chaty_lens_", ".jpg", directory)
        output = destination
        pendingOutput = destination
        onCreated(destination)
        coroutineContext.ensureActive()
        destination.outputStream().use { check(rendered.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
        coroutineContext.ensureActive()
        completed = true
        destination
    } finally {
        ownedBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        if (!completed) output?.delete()
    }
}
    } catch (failure: Throwable) {
        pendingOutput?.delete()
        throw failure
    }
}
