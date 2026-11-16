package com.example.chat

import com.example.chat.ui.chats.detail.ViewerDisplayMetadata
import com.example.chat.ui.chats.detail.viewerDisplayMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

/** MV-R2-007 source-only cases, NOT RUN. */
class ViewerDisplayMetadataTest {
    @Test fun originalMetadataUnchanged() { assertEquals(ViewerDisplayMetadata("clip.mp4", "10 MB"), viewerDisplayMetadata("clip.mp4", "10 MB", false, null)) }
    @Test fun editedMetadataUsesNewSize() { assertEquals(ViewerDisplayMetadata("clip.mp4 (edited)", "2 MB"), viewerDisplayMetadata("clip.mp4", "10 MB", true, "2 MB")) }
    @Test fun pendingOrFailedStatNeverShowsOldSize() { assertEquals(ViewerDisplayMetadata("photo.jpg (edited)", "Edited copy"), viewerDisplayMetadata("photo.jpg", "10 MB", true, null)) }
    @Test fun staleEditedSizeIgnoredWithoutCopy() { assertEquals(ViewerDisplayMetadata("clip.mp4", "10 MB"), viewerDisplayMetadata("clip.mp4", "10 MB", false, "2 MB")) }
}
