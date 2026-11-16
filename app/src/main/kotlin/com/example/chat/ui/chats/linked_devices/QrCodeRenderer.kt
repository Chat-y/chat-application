package com.example.chat.ui.chats.linked_devices

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Renders the Chaty contact QR as qr_flutter's QrImageView does for
 *  linked_devices_qr_screen.dart: QR auto-version, error correction H,
 *  square eyes/modules, quiet zone included.
 *
 *  Modules are painted dark on an opaque WHITE ground regardless of the
 *  active theme: QR readers expect dark modules on a light ground, and a
 *  dark theme's light-foreground modules on a dark card invert the code,
 *  which many scanners fail on. The caller passes the module color.
 *
 *  The bitmap is filled row-major into an IntArray and written with ONE
 *  Bitmap.setPixels call instead of ~sizePx^2 setPixel calls on the
 *  composition thread.
 *
 *  Requires gradle dependency (integration owner):
 *      implementation("com.google.zxing:core:3.5.3")
 *  Free, on-device, JVM-friendly (also usable from unit tests). */
object QrCodeRenderer {

    /** Dart QrImageView(data:, size: 230, errorCorrectionLevel: H). */
    fun render(payload: String, sizePx: Int, foregroundArgb: Int): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val rowOffset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[rowOffset + x] = if (matrix.get(x, y)) foregroundArgb else android.graphics.Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        return bitmap
    }
}
