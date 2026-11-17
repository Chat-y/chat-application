package com.example.chat.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotScopeNamesTest {
    @Test fun `listing filters to allowed scopes of one user and dedupes`() {
        val names = listOf(
            "chaty_snapshot_u1_conversations.bin", "chaty_snapshot_u1_messages_c-1.bin",
            "chaty_snapshot_u1_other_thing.bin", "chaty_snapshot_u2_conversations.bin",
            "chaty_snapshot_u1_messages_c-1.bin.tmp", "unrelated.bin",
        )
        assertEquals(listOf("conversations", "messages_c-1"), SnapshotScopeNames.scopesFor("u1", names))
    }
    @Test fun revisionPayloadInventoryIsSeparateFromBackupAndIncludesOrphans() {
        val id = "11111111-1111-1111-1111-111111111111"
        val files = listOf("chaty_snapshot_u1_own_revision_orphan.bin", "chaty_snapshot_u1_revision_pending_$id.bin",
            "chaty_snapshot_u2_own_revision_other.bin", "chaty_snapshot_u1_messages_c1.bin")
        assertEquals(listOf("own_revision_orphan", "revision_pending_$id"), SnapshotScopeNames.revisionPayloadsFor("u1", files))
        assertEquals(listOf("messages_c1"), SnapshotScopeNames.scopesFor("u1", files))
    }

}
