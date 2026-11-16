package com.example.chat.ui.chats.detail

/** MV-R2-045: measured available host width, including split-window resizing. */
internal fun viewerUsesActionRail(availableWidthDp: Float): Boolean =
    availableWidthDp.isFinite() && availableWidthDp >= 600f
