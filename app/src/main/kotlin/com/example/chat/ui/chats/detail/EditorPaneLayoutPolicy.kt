package com.example.chat.ui.chats.detail

/** MR-R4-046: measured available window width, not the physical device's screen width. */
internal fun editorUsesTwoPanes(availableWidthDp: Float): Boolean =
    availableWidthDp.isFinite() && availableWidthDp >= 840f
