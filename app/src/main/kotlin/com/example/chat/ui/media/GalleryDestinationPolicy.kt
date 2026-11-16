package com.example.chat.ui.media

/** MV-R2-015: shared branding segment, preserving the existing MediaStore destinations. */
internal const val GALLERY_DIR = "Chaty"

internal fun galleryRelativePath(isVideo: Boolean): String =
    (if (isVideo) "Movies" else "Pictures") + "/" + GALLERY_DIR
