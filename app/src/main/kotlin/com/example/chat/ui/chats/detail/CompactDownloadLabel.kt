package com.example.chat.ui.chats.detail

import com.example.chat.data.media.MediaDownloadFormat
import com.example.chat.data.media.MediaDownloadState

/** Short album progress text. Never invent a percentage when the sender's total is unknown. */
fun compactDownloadLabel(state: MediaDownloadState): String? = when (state) {
    is MediaDownloadState.Downloading -> MediaDownloadFormat.percent(state.doneBytes, state.totalBytes)?.let { "$it%" } ?: "Downloading"
    is MediaDownloadState.Paused -> MediaDownloadFormat.percent(state.doneBytes, state.totalBytes)?.let { "Paused $it%" } ?: "Paused"
    is MediaDownloadState.Queued -> "Waiting"
    else -> null
}
