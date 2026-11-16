package com.example.chat.ui.chats.detail

/** MV-R2-007: display-only derivative metadata. Never rename a file or change export identity. */
internal data class ViewerDisplayMetadata(val title: String, val size: String)

internal fun viewerDisplayMetadata(title: String, originalSize: String, edited: Boolean, editedSize: String?): ViewerDisplayMetadata =
    if (!edited) ViewerDisplayMetadata(title, originalSize)
    else ViewerDisplayMetadata("$title (edited)", editedSize ?: "Edited copy")
