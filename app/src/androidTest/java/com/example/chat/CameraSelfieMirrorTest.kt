package com.example.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.camera.CameraCaptureOptions
import com.example.chat.ui.camera.CameraPhotoOutput
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Source-only opt-in output fixtures, NOT RUN. No build is authorized overnight. */
@RunWith(AndroidJUnit4::class)
class CameraSelfieMirrorTest {
    @Test fun defaultRemainsOffAndBackCaptureNeverMirrors() {
        assertFalse(CameraCaptureOptions().mirrorSelfies)
        checkMirror(front = false, mirror = true, expectedFlipped = false)
        checkMirror(front = true, mirror = false, expectedFlipped = false)
    }
    @Test fun explicitFrontOptInSwapsPixelsWithoutChangingTheSource() {
        checkMirror(front = true, mirror = true, expectedFlipped = true)
    }
    private fun checkMirror(front: Boolean, mirror: Boolean, expectedFlipped: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("selfie-test-", ".jpg", context.cacheDir)
        val pixels = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        for (y in 0 until 40) for (x in 0 until 80) pixels.setPixel(x, y, if (x < 40) Color.RED else Color.BLUE)
        source.outputStream().use { assertTrue(pixels.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
        pixels.recycle()
        val bytesBefore = source.readBytes()
        var output: File? = null
        try {
            val result = File(CameraPhotoOutput.process(context, source.path, CameraCaptureOptions(mirrorSelfies = mirror), 2f, front))
            output = result
            val decoded = BitmapFactory.decodeFile(result.path)
            try {
                val left = decoded.getPixel(10, 20)
                if (expectedFlipped) { assertTrue(Color.blue(left) > 230); assertNotEquals(source, output) }
                else assertTrue(Color.red(left) > 230)
                assertArrayEquals(bytesBefore, source.readBytes())
            } finally { decoded.recycle() }
        } finally { output?.takeUnless { it == source }?.delete(); source.delete() }
    }
}
