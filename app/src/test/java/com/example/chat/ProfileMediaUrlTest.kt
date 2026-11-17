package com.example.chat

import com.example.chat.data.remote.ProfileMediaUrl
import org.junit.Assert.*
import org.junit.Test

/** Authored regression fixtures. Not executed in this source-only change. */
class ProfileMediaUrlTest {
    @Test fun resolvesRootAndPathRelativeUrls() {
        assertEquals("https://host.invalid/uploads/a.jpg", ProfileMediaUrl.resolve(" /uploads/a.jpg ", "https://host.invalid/api/v1"))
        assertEquals("https://host.invalid/api/v1/uploads/a.jpg", ProfileMediaUrl.resolve("uploads/a.jpg", "https://host.invalid/api/v1"))
    }
    @Test fun trimsWithoutChangingSignedQueryEncoding() {
        assertEquals("https://cdn.invalid/a.jpg?sig=a%2Fb&n=1", ProfileMediaUrl.resolve(" https://cdn.invalid/a.jpg?sig=a%2Fb&n=1 ", "https://host.invalid"))
    }
    @Test fun rejectsMissingUnsafeAndMalformedReferences() {
        listOf(null, "", "  ", "null", "file:///tmp/a.jpg", "content://picker/a", "javascript:alert(1)",
            "https://user:pass@host.invalid/a.jpg", "https://host.invalid/", "?token=only", "#only", "not an image")
            .forEach { assertNull(ProfileMediaUrl.resolve(it, "https://host.invalid")) }
    }
    @Test fun formLabelsContainNoMediaAddress() {
        assertEquals("root-relative", ProfileMediaUrl.form("/private-path.jpg?token=secret"))
        assertEquals("absolute-http", ProfileMediaUrl.form("https://cdn.invalid/private.jpg?token=secret"))
        assertEquals("missing", ProfileMediaUrl.form("null"))
    }
}
