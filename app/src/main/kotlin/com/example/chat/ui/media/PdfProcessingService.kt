package com.example.chat.ui.media

import java.io.File
import java.nio.charset.StandardCharsets

/** Exact port of lib/features/media/services/pdf_processing_service.dart.
 *
 *  Clean-room standard PDF byte generator (images -> PDF, text -> PDF) and
 *  BT...ET text extraction. No platform PDF APIs involved; works anywhere.
 */
class PdfProcessingService(
    private val sanitizer: FileSanitizationService,
) {
    /** Converts multiple image files into a single, clean, valid PDF byte stream.
     *  Page size is A4 (595x842 pt) - a stated default, not an accident (DOC-R3-115); any
     *  Letter/custom option must be opt-in. Fit is always aspect-preserving (DOC-R3-116). */
    fun convertImagesToPdf(request: ConversionRequest): ConversionResult {
        val start = System.nanoTime()
        try {
            val validImages = request.sourceFilePaths
                .map { File(it) }
                .filter { it.isFile }
            if (validImages.isEmpty()) {
                return ConversionResult(
                    success = false,
                    errorMessage = "No accessible source images were provided.",
                )
            }

            val outDir = File(request.outputDirectory)
            if (!outDir.exists()) outDir.mkdirs()

            val safeName = sanitizer.sanitizeFileName(request.outputFileName)
            val outPath = "${outDir.path}/$safeName${if (safeName.endsWith(".pdf")) "" else ".pdf"}"

            require(validImages.size == request.sourceFilePaths.size) { "A selected image is unavailable" }
            require(validImages.none { it.canonicalFile == File(outPath).canonicalFile }) { "Output cannot overwrite an input" }
            // DOC-R3-126 / DOC-R5-227: sanitized names alone do not stop a redirected path;
            // the final destination must stay inside the requested output directory.
            require(sanitizer.validateSafePath(outDir.path, outPath)) { "Output path escapes the destination directory" }
            val output = File(outPath)
            require(!output.exists()) { "An output with this name already exists" }
            // DOC-R1-018 / DOC-R3-136: images stream straight to the pending file; no whole-document buffer.
            val pending = File.createTempFile("chaty-pdf-", ".part", output.parentFile)
            val totalBytes: Long
            try {
                java.io.BufferedOutputStream(pending.outputStream(), 64 * 1024).use { stream -> PdfImageEncoder.buildTo(validImages, stream) }
                totalBytes = pending.length()
                check(!output.exists() && pending.renameTo(output)) { "Could not publish PDF output" }
            } finally { pending.delete() }

            return ConversionResult(
                success = true,
                outputFilePaths = listOf(outPath),
                totalBytes = totalBytes,
                elapsed = kotlin.time.Duration.parse("${(System.nanoTime() - start)}ns"),
            )
        } catch (e: Exception) {
            return ConversionResult(
                success = false,
                errorMessage = "Image conversion failed. Use valid JPEG or 8-bit noninterlaced PNG images within the file-size limit.",
                elapsed = kotlin.time.Duration.parse("${(System.nanoTime() - start)}ns"),
            )
        }
    }

    /** Converts plain text or markdown content into a structured, readable PDF. */
    fun convertTextToPdf(request: ConversionRequest): ConversionResult {
        val start = System.nanoTime()
        try {
            if (request.sourceFilePaths.isEmpty()) {
                return ConversionResult(success = false, errorMessage = "No source text file specified.")
            }
            val source = File(request.sourceFilePaths.first())
            if (!source.exists()) {
                return ConversionResult(success = false, errorMessage = "Source file does not exist.")
            }

            require(source.length() <= FileSanitizationService.MAX_SAFE_FILE_SIZE) { "Text file exceeds safe size" }
            val text = source.readText()
            val outDir = File(request.outputDirectory)
            if (!outDir.exists()) outDir.mkdirs()

            val safeName = sanitizer.sanitizeFileName(request.outputFileName)
            val outPath = "${outDir.path}/$safeName${if (safeName.endsWith(".pdf")) "" else ".pdf"}"

            require(source.canonicalFile != File(outPath).canonicalFile) { "Output cannot overwrite an input" }
            // DOC-R3-120: the human title comes from the original display name carried in the
            // request options, never from the generated cache filename.
            val title = (request.options["sourceTitle"] as? String)
                ?.let { sanitizer.sanitizeFileName(it).substringBeforeLast('.') }
                ?.takeIf { it.isNotBlank() } ?: source.name
            val pdfBytes = buildPdfFromText(text, title = title)
            writePdfOutput(File(outPath), pdfBytes)

            return ConversionResult(
                success = true,
                outputFilePaths = listOf(outPath),
                totalBytes = pdfBytes.size.toLong(),
                elapsed = kotlin.time.Duration.parse("${(System.nanoTime() - start)}ns"),
            )
        } catch (e: Exception) {
            return ConversionResult(
                success = false,
                errorMessage = "Text conversion failed. This writer currently supports Windows-1252 text only. The original was not changed.",
                elapsed = kotlin.time.Duration.parse("${(System.nanoTime() - start)}ns"),
            )
        }
    }

    /** Extracts textual contents from PDF streams. */
    fun extractTextFromPdf(pdfPath: String): String {
        val file = File(pdfPath)
        if (!file.exists()) return ""

        try {
            require(file.length() <= FileSanitizationService.MAX_SAFE_FILE_SIZE) { "PDF exceeds safe extraction limit" }
            val bytes = file.readBytes()
            val latin1Str = String(bytes, StandardCharsets.ISO_8859_1)
            val buffer = StringBuilder()

            // Extract text within BT ... ET blocks
            val btRegex = Regex("BT[\\s\\S]*?ET")
            val matches = btRegex.findAll(latin1Str)

            for (m in matches) {
                val block = m.value
                // Legacy matcher kept for reference, disabled: it stopped at the first ')'
                // and never decoded escapes (DOC-R5-211 / DOC-R5-212).
                // Look for (text) Tj
                // val tjRegex = Regex("\\((.*?)\\)\\s*Tj")
                // for (tj in tjRegex.findAll(block)) {
                //     val t = tj.groupValues[1]
                //     if (t.isNotEmpty()) {
                //         buffer.append("$t ")
                //     }
                // }
                for (t in scanTjStrings(block)) buffer.append("$t ")
            }
            val extracted = buffer.toString().trim()
            return extracted.ifEmpty { "No accessible extractable text stream found in this PDF." }
        } catch (e: Exception) {
            // Do not rethrow parser exception instances with private cause chains.
            // DOC-R2-090: never let parser internals (which can carry extracted text or
            // identifiers) escape into an exception chain; report only the category.
            throw IllegalStateException("Could not extract PDF text.")
        }
    }


    /**
     * DOC-R5-211 / DOC-R5-212: PDF literal strings can nest balanced parentheses and use
     * backslash escapes (parens, backslash, n, r, t, b, f, octal ddd, backslash-newline
     * continuation). A regex that stops at the first ')' truncates any escaped parenthesis.
     * This scanner walks the block honoring escapes and depth, keeping only strings a Tj
     * operator shows. DOC-R5-213: hex strings (<...> Tj) are decoded byte-wise with a
     * printable-ratio guard; font ToUnicode mapping (DOC-R5-214) stays out of scope.
     */
    internal fun scanTjStrings(block: String): List<String> {
        val out = ArrayList<String>()
        val n = block.length
        fun followedByTj(pos: Int): Boolean {
            var k = pos
            while (k < n && (block[k] == ' ' || block[k] == '\t' || block[k] == '\r' || block[k] == '\n')) k++
            return k + 1 < n && block[k] == 'T' && block[k + 1] == 'j'
        }
        var i = 0
        while (i < n) {
            val c0 = block[i]
            if (c0 == '(') {
                val sb = StringBuilder()
                var depth = 1
                var j = i + 1
                var closed = false
                while (j < n && !closed) {
                    val c = block[j]
                    when {
                        c == '\\' && j + 1 < n -> {
                            val e = block[j + 1]
                            when {
                                e == 'n' -> { sb.append('\n'); j += 2 }
                                e == 'r' -> { sb.append('\r'); j += 2 }
                                e == 't' -> { sb.append('\t'); j += 2 }
                                e == 'b' -> { sb.append('\b'); j += 2 }
                                e == 'f' -> { sb.append('\u000C'); j += 2 }
                                e == '(' || e == ')' || e == '\\' -> { sb.append(e); j += 2 }
                                e == '\n' -> { j += 2 } // line continuation
                                e == '\r' -> { j += if (j + 2 < n && block[j + 2] == '\n') 3 else 2 }
                                e in '0'..'7' -> {
                                    var k = j + 1; var v = 0; var digits = 0
                                    while (k < n && digits < 3 && block[k] in '0'..'7') { v = v * 8 + (block[k] - '0'); k++; digits++ }
                                    sb.append(v.toChar()); j = k
                                }
                                else -> { sb.append(e); j += 2 } // unknown escape: keep the literal char
                            }
                        }
                        c == '(' -> { depth++; sb.append(c); j++ }
                        c == ')' -> { depth--; if (depth == 0) { closed = true; j++ } else { sb.append(c); j++ } }
                        else -> { sb.append(c); j++ }
                    }
                }
                if (!closed) break // unbalanced string: stop reading this block
                if (followedByTj(j)) {
                    val t = sb.toString()
                    if (t.isNotEmpty()) out.add(t)
                }
                i = j
            } else if (c0 == '<' && i + 1 < n && block[i + 1] != '<') {
                // DOC-R5-213: hex string shown by Tj. Decode byte-wise (ISO-8859-1 view);
                // two-byte glyph IDs cannot be resolved without the font, so reject results
                // that are not mostly printable instead of showing garbage.
                var j = i + 1
                val hex = StringBuilder()
                while (j < n && block[j] != '>') { hex.append(block[j]); j++ }
                if (j >= n) break
                val digits = hex.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                if (digits.length >= 2 && digits.length % 2 == 0 && followedByTj(j + 1)) {
                    val sb = StringBuilder()
                    var k = 0
                    while (k + 1 < digits.length) {
                        sb.append(digits.substring(k, k + 2).toInt(16).toChar()); k += 2
                    }
                    val t = sb.toString()
                    val printable = t.count { it in ' '..'~' || it == '\n' || it == '\t' }
                    if (t.isNotEmpty() && printable * 5 >= t.length * 4) out.add(t)
                }
                i = j + 1
            } else {
                i++
            }
        }
        return out
    }

    /** Low-level clean-room standard PDF byte generator for images. */
    fun buildPdfFromImages(images: List<File>): ByteArray = PdfImageEncoder.build(images)

//      Legacy image embedding retained for reference, disabled: PNG container bytes are not a Flate pixel stream.
//     fun buildPdfFromImages(images: List<File>): ByteArray {
//         val buffer = java.io.ByteArrayOutputStream()
//         fun write(s: String) { buffer.write(s.toByteArray(StandardCharsets.UTF_8)) }
//
//         // Header
//         write("%PDF-1.4\n%âãÏÓ\n")
//
//         val offsets = mutableListOf<Int>()
//         var currentOffset = buffer.size()
//
//         // 1: Catalog
//         offsets.add(currentOffset)
//         write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
//         currentOffset = buffer.size()
//
//         // Obj 2: Pages; per image i: Obj (3+i*3) Page, (4+i*3) XObject, (5+i*3) content
//         val pageCount = images.size
//         val kids = (0 until pageCount).joinToString(" ") { "${3 + it * 3} 0 R" }
//
//         offsets.add(currentOffset)
//         write("2 0 obj\n<< /Type /Pages /Kids [ $kids ] /Count $pageCount >>\nendobj\n")
//         currentOffset = buffer.size()
//
//         for (i in 0 until pageCount) {
//             val imgBytes = images[i].readBytes()
//             val isPng = imgBytes.size > 4 &&
//                 (imgBytes[0].toInt() and 0xFF) == 0x89 &&
//                 (imgBytes[1].toInt() and 0xFF) == 0x50
//
//             // Page dimensions (standard A4: 595 x 842 pt)
//             val pageWidth = 595
//             val pageHeight = 842
//
//             val pageObjIndex = 3 + i * 3
//             val imgObjIndex = 4 + i * 3
//             val contentObjIndex = 5 + i * 3
//
//             // Page Object
//             offsets.add(currentOffset)
//             write("$pageObjIndex 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [ 0 0 $pageWidth $pageHeight ] /Resources << /XObject << /Im$i $imgObjIndex 0 R >> >> /Contents $contentObjIndex 0 R >>\nendobj\n")
//             currentOffset = buffer.size()
//
//             // Image Object (DCTDecode for JPEG or Flate for standard)
//             offsets.add(currentOffset)
//             val filter = if (isPng) "/FlateDecode" else "/DCTDecode"
//             write("$imgObjIndex 0 obj\n<< /Type /XObject /Subtype /Image /Width $pageWidth /Height $pageHeight /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter $filter /Length ${imgBytes.size} >>\nstream\n")
//             buffer.write(imgBytes)
//             write("\nendstream\nendobj\n")
//             currentOffset = buffer.size()
//
//             // Content stream (Draw image scaled to page)
//             val contentStream = "q\n$pageWidth 0 0 $pageHeight 0 0 cm\n/Im$i Do\nQ\n"
//             val contentBytes = contentStream.toByteArray(StandardCharsets.UTF_8)
//             offsets.add(currentOffset)
//             write("$contentObjIndex 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n$contentStream\nendstream\nendobj\n")
//             currentOffset = buffer.size()
//         }
//
//         // XRef table
//         val xrefOffset = currentOffset
//         val totalObjs = 2 + pageCount * 3 + 1
//         write("xref\n0 $totalObjs\n0000000000 65535 f \n")
//         for (off in offsets) {
//             write("${off.toString().padStart(10, '0')} 00000 n \n")
//         }
//
//         // Trailer
//         write("trailer\n<< /Size $totalObjs /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
//
//         return buffer.toByteArray()
//     }
//
//

    /** Low-level clean-room PDF generator for text documents. */
    fun buildPdfFromText(text: String, title: String = "Document"): ByteArray {
        val charset = java.nio.charset.Charset.forName("windows-1252")
        require(charset.newEncoder().canEncode(text) && charset.newEncoder().canEncode(title)) { "This PDF writer needs a Unicode font for this text. Original kept unchanged." }
        val buffer = java.io.ByteArrayOutputStream()
        fun write(s: String) { buffer.write(s.toByteArray(charset)) }

        write("%PDF-1.4\n%âãÏÓ\n")

        val offsets = mutableListOf<Int>()
        var currentOffset = buffer.size()

        // 1: Catalog
        offsets.add(currentOffset)
        write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        currentOffset = buffer.size()

        // Split text into chunks/pages
        val lines = text.replace("\r\n", "\n").replace("\t", "    ").split("\n").flatMap { line -> if (line.isEmpty()) listOf("") else line.chunked(80) }
        val linesPerPage = 42
        val pagesList = mutableListOf<List<String>>()
        var i = 0
        while (i < lines.size) {
            pagesList.add(lines.subList(i, minOf(i + linesPerPage, lines.size)))
            i += linesPerPage
        }
        if (pagesList.isEmpty()) pagesList.add(listOf("[Empty Document]"))

        val pageCount = pagesList.size
        val kids = (0 until pageCount).joinToString(" ") { "${3 + it * 2} 0 R" }

        // 2: Pages
        offsets.add(currentOffset)
        write("2 0 obj\n<< /Type /Pages /Kids [ $kids ] /Count $pageCount >>\nendobj\n")
        currentOffset = buffer.size()

        for (p in 0 until pageCount) {
            val pageLines = pagesList[p]
            val pageObjIndex = 3 + p * 2
            val contentObjIndex = 4 + p * 2

            // Page Object
            offsets.add(currentOffset)
            write("$pageObjIndex 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [ 0 0 595 842 ] /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >> >> >> /Contents $contentObjIndex 0 R >>\nendobj\n")
            currentOffset = buffer.size()

            // Stream content
            val streamBuilder = StringBuilder()
            streamBuilder.append("BT\n/F1 14 Tf\n50 800 Td\n(${escapePdfText(if (title.length > 40) title.take(39) + "~" else title)} - Page ${p + 1}/$pageCount) Tj\n/F1 10 Tf\n0 -25 Td\n")
            for (line in pageLines) {
                val safeLine = escapePdfText(line.trimEnd())
                streamBuilder.append("($safeLine) Tj\n0 -16 Td\n")
            }
            streamBuilder.append("ET\n")

            val contentString = streamBuilder.toString()
            val contentBytes = contentString.toByteArray(charset)
            offsets.add(currentOffset)
            write("$contentObjIndex 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n$contentString\nendstream\nendobj\n")
            currentOffset = buffer.size()
        }

        val xrefOffset = currentOffset
        val totalObjs = 2 + pageCount * 2 + 1
        write("xref\n0 $totalObjs\n0000000000 65535 f \n")
        for (off in offsets) {
            write("${off.toString().padStart(10, '0')} 00000 n \n")
        }

        write("trailer\n<< /Size $totalObjs /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return buffer.toByteArray()
    }

    private fun writePdfOutput(output: File, bytes: ByteArray) {
        // DOC-R3-126 / DOC-R5-227: canonical containment before any write.
        val parent = output.absoluteFile.parentFile ?: error("Output has no destination directory")
        require(sanitizer.validateSafePath(parent.path, output.path)) { "Output path escapes the destination directory" }
        require(!output.exists()) { "An output with this name already exists" }
        val pending = File.createTempFile("chaty-pdf-", ".part", output.parentFile)
        try {
            pending.outputStream().use { stream -> stream.write(bytes); stream.flush() }
            check(pending.length() == bytes.size.toLong() && !output.exists() && pending.renameTo(output)) { "Could not publish PDF output" }
        } finally { pending.delete() }
    }

    private fun escapePdfText(text: String): String {
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    }
}
