package com.example.chat.data.services

/** Pure structural bounds BEFORE animated decoder allocation. Server must repeat validation. */
object BannerGifPolicy {
    const val MAX_BYTES = 4 * 1024 * 1024
    const val MAX_SIDE = 720
    const val MAX_PIXELS = 512 * 512
    const val MAX_FRAMES = 200
    const val MAX_DURATION_MS = 10_000L
    const val MAX_FRAME_PIXELS = 30_000_000L
    data class Info(val width: Int, val height: Int, val frames: Int, val durationMs: Long)
    fun isGif(bytes: ByteArray): Boolean = bytes.size >= 6 &&
        (bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII) in setOf("GIF87a", "GIF89a"))
    fun inspect(bytes: ByteArray): Info {
        require(bytes.size in 14..MAX_BYTES && isGif(bytes)) { "Choose a GIF banner under 4 MB." }
        var p = 6
        fun u8(): Int { require(p < bytes.size) { "Incomplete GIF banner." }; return bytes[p++].toInt() and 255 }
        fun u16(): Int { val lo = u8(); return lo or (u8() shl 8) }
        fun skip(n: Int) { require(n >= 0 && p <= bytes.size - n) { "Incomplete GIF banner." }; p += n }
        fun blocks() { while (true) { val n = u8(); if (n == 0) break; skip(n) } }
        val w = u16(); val h = u16()
        require(w in 1..MAX_SIDE && h in 1..MAX_SIDE && w.toLong() * h <= MAX_PIXELS) {
            "Choose a GIF banner up to 720px and 262144 pixels."
        }
        val packed = u8(); skip(2)
        if ((packed and 128) != 0) skip(3 * (1 shl ((packed and 7) + 1)))
        var frames = 0; var duration = 0L; var delay = 100L
        var timedFrames = 0; var hasControl = false
        while (true) when (u8()) {
            0x21 -> {
                val label = u8()
                if (label == 0xF9) {
                    require(u8() == 4) { "Invalid GIF control block." }
                    val flags = u8()
                    require((flags and 2) == 0) { "Interactive GIF banners are not supported." }
                    val rawDelay = u16() * 10L
                    // Same minimum-delay policy as Coil's GifDecoder (GIF delays <20ms become100ms).
                    delay = if (rawDelay < 20) 100 else rawDelay
                    hasControl = true
                    skip(1); require(u8() == 0) { "Invalid GIF control block." }
                } else {
                    // Plain-text rendering can add timing/content not counted by image frames.
                    require(label != 0x01) { "Text-frame GIF banners are not supported." }
                    blocks()
                }
            }
            0x2C -> {
                val left = u16(); val top = u16(); val fw = u16(); val fh = u16()
                require(fw > 0 && fh > 0 && left.toLong() + fw <= w && top.toLong() + fh <= h) { "Invalid GIF frame bounds." }
                val flags = u8()
                if ((flags and 128) != 0) skip(3 * (1 shl ((flags and 7) + 1)))
                require(u8() in 2..8) { "Invalid GIF image data." }
                blocks()
                frames++; duration += delay; delay = 100
                if (hasControl) timedFrames++
                hasControl = false
                require(frames <= MAX_FRAMES && w.toLong() * h * frames <= MAX_FRAME_PIXELS) { "Choose a simpler GIF banner." }
                require(duration <= MAX_DURATION_MS) { "GIF banner animations must be 10 seconds or shorter." }
            }
            0x3B -> {
                require(frames <= 1 || timedFrames == frames) { "Choose a GIF with explicit frame timing." }
                require(frames > 0 && p == bytes.size) { "Invalid GIF banner ending." }
                return Info(w, h, frames, duration)
            }
            else -> error("Invalid GIF banner block.")
        }
    }
}
