package com.example.chat.ui.chats.detail

/** MR-R3-013: a paused/initializing video is not permission to hide the header. */
internal fun reviewShouldAutoHideChrome(video: Boolean, playing: Boolean, chromeVisible: Boolean, reducedMotion: Boolean): Boolean =
    video && playing && chromeVisible && !reducedMotion
