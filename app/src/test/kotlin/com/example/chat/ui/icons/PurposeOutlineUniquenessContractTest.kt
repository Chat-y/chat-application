package com.example.chat.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Covers the original procedural alias groups without dropping existing pack tests. */
class PurposeOutlineUniquenessContractTest {
    @Test fun canonicalBaseRolesNeverShareExactGeometry() {
        assertEquals(ChatyIconId.entries.toSet(), ChatyBaseOutlines.shapes.keys)
        val groups = ChatyBaseOutlines.shapes.entries.groupBy { it.value }.values.filter { it.size > 1 }
        assertEquals(emptyList<List<ChatyIconId>>(), groups.map { group -> group.map { it.key } })
    }
    @Test fun formerlyAliasedPurposesRemainDistinct() {
        val pairs = listOf(
            ChatyIconId.calls to ChatyIconId.callAudio,
            ChatyIconId.more to ChatyIconId.moreOptions,
            ChatyIconId.cameraFlip to ChatyIconId.cameraSwitch,
            ChatyIconId.grid to ChatyIconId.widgets,
            ChatyIconId.check to ChatyIconId.checkSingle,
            ChatyIconId.messageCheckDouble to ChatyIconId.checkDouble,
            ChatyIconId.callVideo to ChatyIconId.videoCam,
            ChatyIconId.unmute to ChatyIconId.speaker,
        )
        for ((a, b) in pairs) assertNotEquals("$a / $b", ChatyBaseOutlines.shapes[a], ChatyBaseOutlines.shapes[b])
    }
}
