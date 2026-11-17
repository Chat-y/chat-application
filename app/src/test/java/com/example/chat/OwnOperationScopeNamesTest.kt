package com.example.chat

import com.example.chat.data.local.SnapshotScopeNames
import org.junit.Assert.*
import org.junit.Test

/** UNRUN pure scope-name fixture; authenticated store enumeration still needs device fixture. */
class OwnOperationScopeNamesTest {
    @Test fun recoveryAllowlistIsSeparateFromBackupAndExport() {
        val files = listOf("chaty_snapshot_owner_own_binding_abc.bin", "chaty_snapshot_owner_own_mls_abc.bin",
            "chaty_snapshot_owner_messages_room.bin", "chaty_snapshot_other_own_mls_abc.bin",
            "chaty_snapshot_owner_account_deletion.bin")
        assertEquals(listOf("own_binding_abc", "own_mls_abc"), SnapshotScopeNames.ownOperationsFor("owner", files))
        assertEquals(listOf("messages_room"), SnapshotScopeNames.scopesFor("owner", files))
    }
}
