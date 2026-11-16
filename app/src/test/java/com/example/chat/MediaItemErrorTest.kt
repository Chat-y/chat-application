package com.example.chat

import com.example.chat.ui.chats.detail.*
import org.junit.Assert.*
import org.junit.Test

/** MR-R3-044 source-only display cases, NOT RUN. */
class MediaItemErrorTest {
    @Test fun firstAndLaterItemUseOneBasedLabels() { assertEquals("Attachment 1", reviewItemErrorLabel(0)); assertEquals("Attachment 3", reviewItemErrorLabel(2)) }
    @Test fun missingIndexDoesNotInventPosition() { assertEquals("Attachment", reviewItemErrorLabel(-1)) }
    @Test fun problemAndExistingRecoveryStayIntact() { assertEquals("Attachment 2: Could not save. Check storage.", mediaItemError("Attachment 2", "Could not save. Check storage.")) }
    @Test fun optionalRecoveryAndBlankLabelAreExplicit() { assertEquals("Attachment: Trim failed. Try another range.", mediaItemError("", "Trim failed.", "Try another range.")) }
}
