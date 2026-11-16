package com.example.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.ui.camera.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Source fixtures only. NOT RUN under the overnight source-only instruction. */
@RunWith(AndroidJUnit4::class)
class CameraLensBakeTest {
    private fun directory() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "lens-bake-fixture-${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
    private fun source(directory: File, width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return File(directory, "source.jpg").also { file ->
            try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) } }
            finally { bitmap.recycle() }
        }
    }
    @Test fun longestEdgeBoundedAndOnlyOneDerivedJpeg() = runBlocking {
        val dir = directory()
        try {
            val original = source(dir, 3000, 1000)
            val before = original.readBytes()
            val output = bakeCameraLensFile(original, dir, LensSize(300f, 100f), kBuiltInLenses.first(), emptyList())
            val decoded = BitmapFactory.decodeFile(output.path)
            try { assertEquals(2048, decoded.width); assertTrue(decoded.height in 681..683) }
            finally { decoded.recycle() }
            assertEquals("jpg", output.extension)
            assertEquals(2, dir.listFiles()!!.size)
            assertArrayEquals(before, original.readBytes())
        } finally { dir.deleteRecursively() }
    }
    @Test fun exifOrientationAppliedWithoutChangingOriginal() = runBlocking {
        val dir = directory()
        try {
            val original = source(dir, 80, 40)
            ExifInterface(original.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
            val before = original.readBytes()
            val output = bakeCameraLensFile(original, dir, LensSize(40f, 80f), kBuiltInLenses.first(), emptyList())
            val decoded = BitmapFactory.decodeFile(output.path)
            try { assertEquals(40, decoded.width); assertEquals(80, decoded.height) }
            finally { decoded.recycle() }
            assertArrayEquals(before, original.readBytes())
        } finally { dir.deleteRecursively() }
    }
    @Test fun rejectionAfterOutputCreationRollsBackDerivedFile() = runBlocking {
        val dir = directory()
        try {
            val original = source(dir, 80, 40)
            try {
                bakeCameraLensFile(original, dir, LensSize(80f, 40f), kBuiltInLenses.first(), emptyList()) { throw CancellationException("fixture") }
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
            assertEquals(listOf(original), dir.listFiles()!!.toList())
        } finally { dir.deleteRecursively() }
    }
}
