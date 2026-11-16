package com.example.chat.ui.media

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Bounded JPEG/8-bit noninterlaced PNG embedding. Unsupported or corrupt input fails, never guessed. */
internal object PdfImageEncoder {
    private const val LIMIT = FileSanitizationService.MAX_SAFE_FILE_SIZE
    private data class Raster(val width: Int, val height: Int, val colors: Int, val bytes: ByteArray,
                              val jpeg: Boolean, val alpha: ByteArray? = null)

    /**
     * DOC-R1-018 / DOC-R3-134 / DOC-R3-136: streaming build. Decodes, writes and releases one
     * bounded image at a time directly to [out]; peak memory is one raster plus its encoded
     * form, never the whole document. Returns the number of pages written.
     */
    fun buildTo(images: List<File>, out: java.io.OutputStream): Int {
        require(images.isNotEmpty()) { "No images supplied" }
        val counting = CountingStream(out)
        val count = 3 + images.size * 4
        val offsets = IntArray(count)
        var encodedBytes = 0L
        fun write(value: String) { counting.write(value.toByteArray(Charsets.ISO_8859_1)) }
        fun obj(id: Int, body: () -> Unit) { offsets[id] = counting.size; write("$id 0 obj\n"); body(); write("\nendobj\n") }
        fun stream(id: Int, dictionary: String, bytes: ByteArray) = obj(id) {
            write("<< $dictionary /Length ${bytes.size} >>\nstream\n"); counting.write(bytes); write("\nendstream")
        }
        write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n")
        obj(1) { write("<< /Type /Catalog /Pages 2 0 R >>") }
        obj(2) { write("<< /Type /Pages /Count ${images.size} /Kids [${images.indices.joinToString(" ") { "${3 + it * 4} 0 R" }}] >>") }
        images.forEachIndexed { i, file ->
            require(file.isFile && file.length() in 1..LIMIT.toLong()) { "Image unavailable or too large" }
            val raster = decode(file.readBytes())
            encodedBytes += raster.bytes.size + (raster.alpha?.size ?: 0)
            require(encodedBytes <= LIMIT) { "Combined images exceed safe PDF size" }
            val page = 3 + i * 4; val image = page + 1; val content = page + 2; val mask = page + 3
            // DOC-R3-117 / DOC-R3-147: EXIF orientation is applied exactly once, as a page
            // /Rotate entry - pixels are not re-encoded and nothing is rotated twice. Only pure
            // rotations are honored; mirrored orientations fall back to unrotated.
            val rotation = jpegRotationDegrees(file)
            val quarter = rotation == 90 || rotation == 270
            val factor = if (quarter) minOf(770.0 / raster.height, 523.0 / raster.width) else minOf(523.0 / raster.width, 770.0 / raster.height)
            val width = raster.width * factor; val height = raster.height * factor
            val x = (595 - width) / 2; val y = (842 - height) / 2
            fun number(value: Double) = String.format(java.util.Locale.ROOT, "%.4f", value)
            val rotateEntry = if (rotation != 0) " /Rotate $rotation" else ""
            obj(page) { write("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842]$rotateEntry /Resources << /XObject << /Im$i $image 0 R >> >> /Contents $content 0 R >>") }
            val colorSpace = when (raster.colors) { 1 -> "/DeviceGray"; 4 -> "/DeviceCMYK"; else -> "/DeviceRGB" }
            val filter = if (raster.jpeg) "/DCTDecode" else "/FlateDecode"
            val alpha = if (raster.alpha != null) "/SMask $mask 0 R" else ""
            stream(image, "/Type /XObject /Subtype /Image /Width ${raster.width} /Height ${raster.height} /ColorSpace $colorSpace /BitsPerComponent 8 /Filter $filter $alpha", raster.bytes)
            stream(content, "", "q\n${number(width)} 0 0 ${number(height)} ${number(x)} ${number(y)} cm\n/Im$i Do\nQ\n".toByteArray())
            if (raster.alpha != null) stream(mask, "/Type /XObject /Subtype /Image /Width ${raster.width} /Height ${raster.height} /ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /FlateDecode", raster.alpha)
            else obj(mask) { write("null") }
            require(counting.size <= LIMIT) { "PDF exceeds safe output limit" }
        }
        val xref = counting.size
        write("xref\n0 $count\n0000000000 65535 f \n")
        for (id in 1 until count) write("${offsets[id].toString().padStart(10, '0')} 00000 n \n")
        write("trailer\n<< /Size $count /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        counting.flush()
        return images.size
    }

    /** Buffered convenience over [buildTo] for callers and tests that want bytes. */
    fun build(images: List<File>): ByteArray {
        val buffer = ByteArrayOutputStream()
        buildTo(images, buffer)
        return buffer.toByteArray()
    }

    /** DOC-R3-117: pure EXIF rotation for JPEGs; 0 when absent, unreadable, mirrored or PNG. */
    private fun jpegRotationDegrees(file: File): Int = runCatching {
        when (androidx.exifinterface.media.ExifInterface(file.path).getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    /** Byte-counting sink so xref offsets stay exact while streaming. */
    private class CountingStream(out: java.io.OutputStream) : java.io.FilterOutputStream(out) {
        var size = 0
            private set
        override fun write(b: Int) { out.write(b); size++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); size += len }
    }

    private fun decode(bytes: ByteArray): Raster {
        if (bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()) return jpeg(bytes)
        val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        require(bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(signature)) { "Only JPEG and PNG images are supported" }
        val input = DataInputStream(ByteArrayInputStream(bytes, 8, bytes.size - 8))
        var width = 0; var height = 0; var color = -1
        var palette: ByteArray? = null; var transparency: ByteArray? = null
        val compressed = ByteArrayOutputStream()
        var end = false
        while (input.available() > 0 && !end) {
            val size = input.readInt(); require(size >= 0 && size <= input.available() - 8) { "Invalid PNG chunk" }
            val typeBytes = ByteArray(4).also(input::readFully)
            val type = String(typeBytes, Charsets.US_ASCII)
            val data = ByteArray(size).also(input::readFully)
            val crc = input.readInt().toLong() and 0xffffffffL
            require(CRC32().apply { update(typeBytes); update(data) }.value == crc) { "Corrupt PNG chunk" }
            when (type) {
                "IHDR" -> {
                    require(width == 0 && size == 13) { "Invalid PNG header" }
                    val header = DataInputStream(ByteArrayInputStream(data))
                    width = header.readInt(); height = header.readInt()
                    require(header.readUnsignedByte() == 8) { "PNG must use 8-bit channels" }
                    color = header.readUnsignedByte()
                    require(color in setOf(0, 2, 3, 4, 6) && header.readUnsignedByte() == 0 && header.readUnsignedByte() == 0 && header.readUnsignedByte() == 0) { "Unsupported PNG layout" }
                    require(width > 0 && height > 0 && width.toLong() * height * 4 <= LIMIT) { "Decoded image exceeds safe size" }
                }
                "PLTE" -> palette = data
                "tRNS" -> transparency = data
                "IDAT" -> { require(width > 0); compressed.write(data); require(compressed.size() <= LIMIT) }
                "IEND" -> end = true
            }
        }
        require(end && width > 0) { "Incomplete PNG" }
        val channels = when (color) { 0, 3 -> 1; 2 -> 3; 4 -> 2; else -> 4 }
        val rowSize = width * channels
        val rgb = ByteArray(width * height * 3)
        val alpha = ByteArray(width * height) { 0xff.toByte() }
        var anyAlpha = false
        var previous = ByteArray(rowSize)
        InflaterInputStream(ByteArrayInputStream(compressed.toByteArray())).use { inflated ->
            val data = DataInputStream(inflated)
            for (y in 0 until height) {
                val filter = data.readUnsignedByte(); require(filter in 0..4) { "Invalid PNG row filter" }
                val row = ByteArray(rowSize).also(data::readFully)
                for (i in row.indices) {
                    val a = if (i >= channels) row[i - channels].toInt() and 255 else 0
                    val b = previous[i].toInt() and 255
                    val c = if (i >= channels) previous[i - channels].toInt() and 255 else 0
                    val predictor = when (filter) { 1 -> a; 2 -> b; 3 -> (a + b) / 2; 4 -> paeth(a, b, c); else -> 0 }
                    row[i] = ((row[i].toInt() and 255) + predictor).toByte()
                }
                for (x in 0 until width) {
                    val src = x * channels; val pixel = y * width + x; val dest = pixel * 3
                    when (color) {
                        0, 4 -> { rgb[dest] = row[src]; rgb[dest + 1] = row[src]; rgb[dest + 2] = row[src] }
                        2, 6 -> { rgb[dest] = row[src]; rgb[dest + 1] = row[src + 1]; rgb[dest + 2] = row[src + 2] }
                        3 -> {
                            val index = row[src].toInt() and 255; val colors = requireNotNull(palette) { "Missing PNG palette" }
                            require(index * 3 + 2 < colors.size) { "Invalid PNG palette index" }
                            colors.copyInto(rgb, dest, index * 3, index * 3 + 3)
                            alpha[pixel] = transparency?.getOrNull(index) ?: 0xff.toByte()
                        }
                    }
                    if (color == 4 || color == 6) alpha[pixel] = row[src + channels - 1]
                    if (color == 0 && transparency?.size == 2 && (row[src].toInt() and 255) == (transparency!![1].toInt() and 255)) alpha[pixel] = 0
                    if (color == 2 && transparency?.size == 6 && (0..2).all { channel -> (row[src + channel].toInt() and 255) == (transparency!![channel * 2 + 1].toInt() and 255) }) alpha[pixel] = 0
                    if (alpha[pixel] != 0xff.toByte()) anyAlpha = true
                }
                previous = row
            }
            require(data.read() == -1) { "Unexpected PNG pixel data" }
        }
        return Raster(width, height, 3, deflate(rgb), false, if (anyAlpha) deflate(alpha) else null)
    }

    /**
     * DOC-R3-129: JPEGs are embedded verbatim via /DCTDecode, so a camera photo would carry
     * EXIF location/device data (APP1), IPTC data (APP13) and comments (COM) into every
     * generated PDF. Strip those segments; pixel data, JFIF/Adobe headers, ICC (APP2) and all
     * coding tables stay byte-identical, and the entropy stream after SOS is copied untouched.
     * EXIF orientation is read from the original file by jpegRotationDegrees before this runs,
     * so page /Rotate still honors it. PNGs lose metadata by construction (pixels are
     * re-encoded). Malformed input fails closed on the same requires the decoder already used.
     */
    internal fun stripJpegMetadata(bytes: ByteArray): ByteArray {
        require(bytes.size >= 4 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()) { "Not a JPEG" }
        val out = ByteArrayOutputStream(bytes.size)
        out.write(bytes, 0, 2) // SOI
        var position = 2
        while (position < bytes.size) {
            require(bytes[position] == 0xff.toByte()) { "Invalid JPEG marker" }
            val markerStart = position
            while (position < bytes.size && bytes[position] == 0xff.toByte()) position++
            require(position < bytes.size) { "Truncated JPEG" }
            val marker = bytes[position++].toInt() and 255
            if (marker == 0xda || marker == 0xd9) { // SOS or EOI: copy the rest verbatim
                out.write(bytes, markerStart, bytes.size - markerStart)
                return out.toByteArray()
            }
            if (marker in 0xd0..0xd7 || marker == 0x01) { // standalone markers carry no payload
                out.write(bytes, markerStart, position - markerStart)
                continue
            }
            require(position + 1 < bytes.size) { "Truncated JPEG segment" }
            val length = ((bytes[position].toInt() and 255) shl 8) or (bytes[position + 1].toInt() and 255)
            require(length >= 2 && position.toLong() + length <= bytes.size) { "Invalid JPEG segment" }
            val drop = marker == 0xe1 || marker == 0xed || marker == 0xfe // APP1, APP13, COM
            if (!drop) out.write(bytes, markerStart, position - markerStart + length)
            position += length
        }
        error("Incomplete JPEG")
    }

    private fun jpeg(bytes: ByteArray): Raster {
        var position = 2
        while (position + 3 < bytes.size) {
            require(bytes[position] == 0xff.toByte()) { "Invalid JPEG marker" }
            while (position < bytes.size && bytes[position] == 0xff.toByte()) position++
            require(position < bytes.size)
            val marker = bytes[position++].toInt() and 255
            if (marker == 0xd9 || marker == 0xda) break
            if (marker in 0xd0..0xd7 || marker == 0x01) continue
            require(position + 1 < bytes.size)
            val length = ((bytes[position].toInt() and 255) shl 8) or (bytes[position + 1].toInt() and 255)
            require(length >= 2 && position.toLong() + length <= bytes.size) { "Invalid JPEG segment" }
            if (marker in setOf(0xc0, 0xc1, 0xc2)) {
                require(length >= 8 && bytes[position + 2].toInt() == 8) { "Unsupported JPEG precision" }
                val height = ((bytes[position + 3].toInt() and 255) shl 8) or (bytes[position + 4].toInt() and 255)
                val width = ((bytes[position + 5].toInt() and 255) shl 8) or (bytes[position + 6].toInt() and 255)
                val colors = bytes[position + 7].toInt() and 255
                require(width > 0 && height > 0 && width.toLong() * height * colors <= LIMIT && colors in setOf(1, 3)) { "Unsupported JPEG dimensions" }
                require(bytes[bytes.size - 2] == 0xff.toByte() && bytes.last() == 0xd9.toByte()) { "Incomplete JPEG" }
                // DOC-R3-129: never carry camera metadata into a generated PDF.
                return Raster(width, height, colors, stripJpegMetadata(bytes), true)
            }
            position += length
        }
        error("JPEG dimensions unavailable")
    }
    private fun deflate(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(bytes) } }.toByteArray()
    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c; val pa = kotlin.math.abs(p - a); val pb = kotlin.math.abs(p - b); val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }
}
