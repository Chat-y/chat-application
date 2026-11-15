package com.example.chat.ui.auth

import kotlin.math.pow

/** Presentation only. Never participates in authentication or account lookup. */
object AuthPresentationPolicy {
    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color ushr shift) and 255) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
    fun contrast(first: Int, second: Int): Double {
        val a = luminance(first); val b = luminance(second)
        return (maxOf(a,b) + .05) / (minOf(a,b) + .05)
    }
    fun readableText(preferred: Int, background: Int): Int {
        if (contrast(preferred, background) >= 4.5) return preferred
        val black = 0xff000000.toInt(); val white = 0xffffffff.toInt()
        return if (contrast(black,background) >= contrast(white,background)) black else white
    }
    fun greeting(hour: Int): String = when (hour.coerceIn(0,23)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    fun twoPane(widthDp: Float, heightDp: Float, fontScale: Float): Boolean =
        widthDp >= 840f && heightDp >= 480f && fontScale <= 1.5f
}
