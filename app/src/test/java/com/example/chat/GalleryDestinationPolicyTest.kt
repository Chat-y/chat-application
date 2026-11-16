package com.example.chat

import com.example.chat.ui.media.GALLERY_DIR
import com.example.chat.ui.media.galleryRelativePath
import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-only fixtures, NOT RUN. No MediaStore/OEM/device acceptance. */
class GalleryDestinationPolicyTest {
    @Test fun photoDestinationRemainsPicturesChaty() { assertEquals("Pictures/Chaty", galleryRelativePath(false)) }
    @Test fun videoDestinationRemainsMoviesChaty() { assertEquals("Movies/Chaty", galleryRelativePath(true)) }
    @Test fun bothUseSingleBrandSegment() {
        assertEquals(GALLERY_DIR, galleryRelativePath(false).substringAfter('/'))
        assertEquals(GALLERY_DIR, galleryRelativePath(true).substringAfter('/'))
    }
}
