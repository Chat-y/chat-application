package com.example.chat

import com.example.chat.domain.models.*
import com.example.chat.ui.chats.contact_info.ContactInfoLogic
import com.example.chat.ui.chats.contact_info.parseAvatarColorArgb
import com.example.chat.ui.chats.contact_info.parseAvatarColorArgbKit
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Written for Contact info and privacy review items R1-09/R1-11, R2-01..R2-23, R2-50, R3-20..R3-22, R5-06.
 *  Not compiled or run in the authoring environment. */
class ContactInfoLogicContractTest {
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-11-15T12:00:00Z")
    private fun label(rec: Boolean = false, typ: Boolean = false, on: Boolean = false, seen: Instant? = null, zone: ZoneId = utc, at: Instant = now) =
        ContactInfoLogic.presenceLabel(rec, typ, on, seen, at, zone)
    private fun msg(id: String = "m", type: String = "image", text: String = "", name: String = "Photo") = ChatMessage(
        id, "r", "u", MessageType.image, text, MessageAttachment("a", type, name, "1KB", "https://example.invalid/p"), createdAt = Instant.EPOCH)

    @Test fun recordingWins() { assertEquals("recording voice message…", label(rec = true, typ = true, on = true)) } // R2-01
    @Test fun typingBeatsOnline() { assertEquals("typing…", label(typ = true, on = true, seen = now)) } // R2-02
    @Test fun onlineHasNoTimestamp() { assertEquals("Online", label(on = true, seen = Instant.parse("2020-01-01T00:00:00Z"))) } // R2-03
    @Test fun hiddenFallback() { assertEquals("Last seen hidden", label()) } // R2-04
    @Test fun todayBranchFollowsInjectedZone() { // R2-05, R2-08
        val seen = Instant.parse("2026-11-15T18:40:00Z") // 00:10 on 16 Nov in Asia/Kolkata
        val kolkata = ZoneId.of("Asia/Kolkata")
        assertEquals("Last seen 16/11/2026 at 00:10", label(seen = seen, zone = kolkata, at = Instant.parse("2026-11-16T00:00:00Z").plusSeconds(60 * 60 * 24)))
        assertEquals("Last seen today at 18:40", label(seen = seen, zone = utc, at = Instant.parse("2026-11-15T19:00:00Z")))
    }
    @Test fun dstBoundaryUsesZoneRules() { // R2-06
        val ny = ZoneId.of("America/New_York")
        val seen = Instant.parse("2026-03-08T07:30:00Z") // 03:30 EDT after the spring-forward gap
        assertEquals("Last seen 8/3/2026 at 03:30", label(seen = seen, zone = ny, at = Instant.parse("2026-03-09T12:00:00Z")))
    }
    @Test fun leapDayKept() { assertEquals("Last seen 29/2/2028 at 10:00", label(seen = Instant.parse("2028-02-29T10:00:00Z"), at = Instant.parse("2028-03-05T00:00:00Z"))) } // R2-07
    @Test fun futureLastSeenClamped() { assertEquals("Last seen today at 12:00", label(seen = now.plusSeconds(3600))) } // R1-11

    @Test fun deletedFixturesExcluded() { // R2-09, R2-10, R2-11
        val list = listOf(msg("a").copy(isDeletedForEveryone = true), msg("b").copy(isDeletedForMe = true), msg("c").copy(isRevokedBySender = true), msg("d"))
        assertEquals(listOf("d"), ContactInfoLogic.sharedMedia(list).map { it.id })
    }
    @Test fun noSaveProtectedRestrictedExcluded() { // R2-13, R2-14, R2-15
        for (key in listOf("no_save", "protected", "restricted")) {
            val m = msg(text = "https://example.invalid/x").copy(metadata = mapOf(key to true))
            assertTrue(ContactInfoLogic.sharedMedia(listOf(m)).isEmpty())
            assertTrue(ContactInfoLogic.extractLinks(listOf(m)).isEmpty())
        }
    }
    @Test fun ordinaryAttachmentKept() { assertEquals(1, ContactInfoLogic.sharedMedia(listOf(msg())).size) } // R2-16
    @Test fun unknownKindNotForced() { val m = msg(type = "sticker"); assertTrue(ContactInfoLogic.sharedMedia(listOf(m)).isEmpty()); assertTrue(ContactInfoLogic.sharedDocuments(listOf(m)).isEmpty()) } // R2-17
    @Test fun urlSchemeCaseInsensitiveButStringsDistinct() { // R2-18
        val links = ContactInfoLogic.extractLinks(listOf(msg(text = "HTTPS://A.example/x https://a.example/x")))
        assertEquals(2, links.size)
    }
    @Test fun trailingPunctuationIsPartOfCurrentMatch() { // R2-19 documents current behaviour
        val sampleText = "see (https://a.example/x).".removePrefix("see (")
        val links = ContactInfoLogic.extractLinks(listOf(msg(text = sampleText)))
        assertEquals(listOf("https://a.example/x)."), links)
    }
    @Test fun repeatedUrlKeptOnceInOrder() { // R2-20
        val links = ContactInfoLogic.extractLinks(listOf(msg("1", text = "https://b.example https://a.example"), msg("2", text = "https://b.example")))
        assertEquals(listOf("https://b.example", "https://a.example"), links)
    }
    @Test fun fragmentsAreDistinct() { assertEquals(2, ContactInfoLogic.extractLinks(listOf(msg(text = "https://a.example/p#1 https://a.example/p#2"))).size) } // R2-21
    @Test fun nonHttpIgnored() { assertTrue(ContactInfoLogic.extractLinks(listOf(msg(text = "mailto:a@b.c javascript:alert(1) example.com"))).isEmpty()) } // R2-22
    @Test fun threeUrlsInOneMessageCountAsThree() { assertEquals(3, ContactInfoLogic.extractLinks(listOf(msg(text = "https://a.example https://b.example https://c.example"))).size) } // R2-23

    @Test fun duplicateIdsCollapse() { assertEquals(1, ContactInfoLogic.sharedMedia(listOf(msg("x"), msg("x"))).size) } // R5-06
    @Test fun queryBoundKeepsSurrogatePairs() { // R2-30
        val s = "a".repeat(199) + "\uD83D\uDE00" + "tail"
        val bounded = ContactInfoLogic.boundQuery(s)
        assertEquals(199, bounded.length)
        assertTrue(bounded.length <= 200)
    }
    @Test fun displayNameIsCleaned() { // R3-20, R3-21, R3-22, R4-29
        assertEquals("this contact", ContactInfoLogic.safeDisplayName("  "))
        assertEquals("Ann", ContactInfoLogic.safeDisplayName("\u202EAnn\u202C"))
        assertEquals("<b>x</b>", ContactInfoLogic.safeDisplayName("<b>x</b>"))
        assertTrue(ContactInfoLogic.safeDisplayName("n".repeat(500)).length <= 81)
    }
    @Test fun usernameLine() { assertNull(ContactInfoLogic.usernameLine(" ")); assertEquals("@ann", ContactInfoLogic.usernameLine("ann")) } // R1-09
    @Test fun avatarParsers() { // R2-50
        assertEquals(0xFF112233L, parseAvatarColorArgb("#112233", 1L))
        assertEquals(1L, parseAvatarColorArgb("zzz", 1L))
        assertEquals(1L, parseAvatarColorArgb("", 1L))
        assertEquals(0xFF112233L, parseAvatarColorArgbKit("0x112233"))
        assertEquals(0xFF6366F1L, parseAvatarColorArgbKit("nothex"))
        assertEquals(0x80112233L, parseAvatarColorArgbKit("80112233"))
    }
}
