package com.example.chat.ui.chats.detail

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.chat.core.media.ImageTextOverlayPolicy

internal enum class EditorTextAlignment { Left, Center, Right }

/** Preserve up to four explicit lines within the existing 120 UTF-16 unit budget. */
internal fun editorOverlayText(value: String): String {
    val lines = value.replace("\r\n", "\n").replace('\r', '\n').lineSequence().take(4).joinToString("\n")
    var end = minOf(lines.length, 120)
    if (end < lines.length && end > 0 && lines[end - 1].isHighSurrogate() && lines[end].isLowSurrogate()) end--
    return lines.substring(0, end)
}

/** Shared native drawing for Compose preview and exported bitmap, not separate approximations. */
internal fun drawEditorText(
    canvas: Canvas, width: Float, height: Float, text: String,
    x: Float, y: Float, scale: Float, color: Int,
    alignment: EditorTextAlignment = EditorTextAlignment.Left,
    strip: Boolean = false,
) {
    if (width <= 0f || height <= 0f) return
    val bounded = editorOverlayText(text)
    if (bounded.isBlank()) return
    val lines = bounded.split('\n')
    val left = width * ImageTextOverlayPolicy.x(x)
    val margin = width * 0.02f
    val available = (width - left - margin).coerceAtLeast(1f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = width * ImageTextOverlayPolicy.scale(scale)
    }
    val requested = paint.textSize
    val widest = lines.maxOf { paint.measureText(it) }
    paint.textSize = ImageTextOverlayPolicy.fittedTextSize(width, x, requested, widest)
    // Fit the whole block in the bitmap even when its requested baseline is near the bottom.
    val fullHeight = (paint.descent() - paint.ascent()) + paint.fontSpacing * (lines.size - 1)
    val verticalMargin = minOf(margin, height * 0.02f)
    val availableHeight = (height - 2f * verticalMargin).coerceAtLeast(0.01f)
    if (fullHeight > availableHeight) paint.textSize *= availableHeight / fullHeight
    val lineStep = paint.fontSpacing
    val firstMin = verticalMargin - paint.ascent()
    val firstMax = height - verticalMargin - paint.descent() - lineStep * (lines.size - 1)
    val firstBaseline = (height * ImageTextOverlayPolicy.y(y)).coerceIn(firstMin, maxOf(firstMin, firstMax))
    paint.textAlign = when (alignment) {
        EditorTextAlignment.Left -> Paint.Align.LEFT
        EditorTextAlignment.Center -> Paint.Align.CENTER
        EditorTextAlignment.Right -> Paint.Align.RIGHT
    }
    val anchor = when (alignment) {
        EditorTextAlignment.Left -> left
        EditorTextAlignment.Center -> left + available / 2f
        EditorTextAlignment.Right -> left + available
    }
    if (strip) {
        val background = Paint().apply { this.color = Color.argb(160, 0, 0, 0) }
        canvas.drawRect(left - margin / 2f, firstBaseline + paint.ascent() - verticalMargin / 2f,
            left + available + margin / 2f,
            firstBaseline + lineStep * (lines.size - 1) + paint.descent() + verticalMargin / 2f, background)
    }
    paint.setShadowLayer(width * 0.004f, 0f, 0f, Color.BLACK)
    lines.forEachIndexed { index, line -> canvas.drawText(line, anchor, firstBaseline + index * lineStep, paint) }
}
