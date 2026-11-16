package com.example.chat

import com.example.chat.ui.profile.ProfileErrorMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileErrorMessagesTest {
    private fun friendly(message: String?, save: Boolean = false) =
        ProfileErrorMessages.friendly(Exception(message), "fallback", save)

    @Test
    fun `short user readable messages pass through`() {
        assertEquals("Could not process profile photo.", friendly("Could not process profile photo."))
    }

    @Test
    fun `backend internals fall back`() {
        assertEquals("fallback", friendly("java.lang.IllegalStateException: boom at com.x.Y"))
        assertEquals("fallback", friendly("HTTP 500 from https://api.example.com/profile"))
        assertEquals("fallback", friendly(null))
    }

    @Test
    fun `known statuses get specific copy`() {
        assertEquals("Your session has expired. Please sign in again.", friendly("401 Unauthorized"))
        assertEquals("That photo is too large. Choose a smaller one.", friendly("413 Payload Too Large"))
        assertEquals("That file type isn't supported. Try a JPEG or PNG.", friendly("415 Unsupported Media Type"))
    }

    @Test
    fun `username conflict only maps on save`() {
        assertEquals("That username is already taken. Try another one.", friendly("409 conflict already taken", save = true))
        assertFalse(friendly("409 conflict already taken", save = false).startsWith("That username"))
    }

    @Test
    fun `offline errors map`() {
        val msg = ProfileErrorMessages.friendly(java.net.UnknownHostException("x"), "fallback")
        assertEquals("You're offline or the connection is weak. Check your connection and try again.", msg)
    }
}
