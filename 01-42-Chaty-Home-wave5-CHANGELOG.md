# Corrected patch 42: account stores, source-only

This patch supersedes the earlier patch 42, `42-Chaty-Home-wave5-account-stores-source-only-5231b3a8.patch`. Do not apply both versions. Apply the corrected patch once to the pinned base with patches 01-40 already applied, without the earlier 42. An existing tree containing the earlier 42 must first return to its pre-42 state without discarding unrelated work.

Corrected patch: `42-Chaty-Home-wave5-account-stores-source-only-corrected.patch`

SHA-256: `4498b5b84686672d68d08cdca6f57125d3d8d7dd18a6aaecbdcd66c040e7439b`

Base: `Chat-y/chat-application` at `3c67bf51e87c007e37e2195ba714ced988ee87f6`, followed by the supplied 01-40 sequence. No patch 41 is required or applied.

## Exact changes vs the superseded 42

All paths below are under `app/src/main/java/com/example/chat/data/local/`. These remain the same five NEW files. No other repository file is added or changed by corrected 42.

| File | Removed method | Scope label | Release status |
| --- | --- | --- | --- |
| ConversationNoteStore.kt | `deleteNote(conversationId)` | R3-42; Decision 4A | pending explicit deletion release |
| ConversationNoteStore.kt | `resetCorruptedStore()` | Decision 4A; note-store corruption recovery | pending explicit deletion release |
| EncryptedAccountStore.kt | `delete(key)` including `prefs.edit().remove(key).commit()` | Decision 1B storage primitive | pending explicit deletion release |
| FollowUpStore.kt | `remove(id)` | Decision 2A | pending explicit deletion release |
| FollowUpStore.kt | `resetCorruptedStore()` | Decision 2A; follow-up corruption recovery | pending explicit deletion release |
| ReminderSeenKeyStore.kt | `resetCorruptedStore()` | Decision 3A; R3-47 store corruption handling | pending explicit deletion release |
| SavedItemStore.kt | `remove(id)` | R3-17; Decision 4A | pending explicit deletion release |
| SavedItemStore.kt | `resetCorruptedStore()` | Decision 4A; saved-item corruption recovery | pending explicit deletion release |

R3-17 identifies bookmark removal from the correction scope supplied with this revision. Other labels above preserve the source's existing decision/requirement associations; no new individual R-ID is inferred for a reset or primitive.

Comments were updated to mark deletion held and recovery pending a later explicit release, remove stale reset links/instructions, remove the deleted `deleteNote` suggestion for blank input, and identify the held note/bookmark operations. All five store headers now say source-only with deletion held instead of claiming general review approval. Patch hunk lengths and new-file blob hashes were regenerated.

Executable source is otherwise unchanged. A comment-insensitive source comparison confirmed that the only executable differences are the eight method removals listed above.

## Preserved behavior and boundaries

- `isCorrupted()` remains in each of the four typed stores. There is no method to reset the corruption flag after detection.
- Malformed/unsupported payload handling keeps the original state key untouched, attempts to preserve raw bytes under the backup key only if absent, and refuses subsequent typed-store writes. A failed backup attempt does not discard the original state payload.
- Follow-up and saved-item reads return empty lists after codec failure. Note lookup returns no note from the empty map. Reminder seen-key lookup keeps its existing fail-closed behavior: nonblank IDs report seen while corrupted; blank IDs remain false. It does not become an ordinary empty/false lookup.
- Normal `upsert`, `save`, `putNote`, `markSeen`, read/list methods, codecs, revision updates, validation, encryption/account scoping, and Message-kind rejection remain unchanged.
- `EncryptedAccountStore.write` and `writeAll` remain normal put/commit primitives. `writeAll` does not clear keys omitted from its input. On the typed stores' corruption paths, the only write remains the conditional raw-backup write; no state deletion, empty-state replacement or corruption-flag reset is performed.
- No delete-like executable code remains in these five new files: no reset, remove, deleteNote, encrypted delete, clear/remove-all, `filterNot` record-removal path or map subtraction. No single-record deletion exception remains in this revision.
- The supplied 01-40 stack and unrelated pre-existing stores are not changed by this correction. The no-deletion grep assertion is scoped to the five files delivered by corrected 42, not the whole application.

## Source checks performed

1. Fresh clone of `https://github.com/Chat-y/chat-application.git`; detached checkout and exact HEAD verification at the pin above; clean starting tree verified.
2. Verified all 40 supplied patch bytes against their manifest SHA-256 values and applied 01-40 in numbered order. Each patch passed `git apply --check` immediately before `git apply`.
3. Final corrected 42 passed `git apply --check` and was applied successfully on that 01-40 baseline.
4. Verified the corrected patch adds exactly the expected five new files.
5. Compared the five corrected files with the superseded sources: exactly eight executable method deletions, with all other executable content unchanged.
6. Ran scoped grep checks for removed method names, destructive API definitions/calls, record-removal expressions, preserved `isCorrupted`, Message rejection, corruption latches, raw backup keys, and remaining write paths. Exact command patterns, line references and exit codes are in `42-Chaty-Home-wave5-source-checks.txt`. Both prohibited-pattern searches returned no matches (exit 1).

## Limits

Source-only verification. No compile, build, test suite, CI, runtime execution, UI check, commit or push was performed. Patch application and source checks do not establish Kotlin type correctness, dependency compatibility, Android behavior, encryption behavior, durable-write behavior or runtime acceptance. The new stores remain unwired by this patch. Corrupted stores stay write-refusing; recovery and every removed deletion operation await a later explicit release.
