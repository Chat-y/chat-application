package com.example.chat.ui.media

/** DOC-R4-184: presentation only, never changes measured bytes or storage accounting. */
internal fun storageCategoryShare(bytes: Long, total: Long): Double? = when {
    bytes < 0 || total < 0 -> null
    total == 0L -> if (bytes == 0L) 0.0 else null
    else -> (bytes.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
}
