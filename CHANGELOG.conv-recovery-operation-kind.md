# Conversation recovery operation kind change log

## 2026-11-15 - source-only checkpoint

- `app/src/main/java/com/example/chat/core/messages/ConversationDraftRecovery.kt`: added a small session-local recoverable draft value with exact text, explicit regular/edit/view-once kind and edit-message ID. Edit recovery cannot be created without an ID; non-edit recovery cannot carry an implicit edit target.
- `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt`: failed edits kept separately retain the target ID and restore editing mode only after current ownership, current message and existing edit-policy checks. Auto-restoration follows the same rule; unavailable targets stay separately retained rather than becoming new messages. Edit callback execution also checks current target text/edit timestamp against the editor-opening snapshot and asks review if it changed.
- Same file: failed view-once text is explicitly retained separately, not auto-restored into ordinary send. Restoring it displays a send-mode choice reminder and blocks ordinary send/scheduling until the user explicitly chooses regular send or chooses Send as view once again. No mode is silently selected and no send occurs from Restore or Use regular send buttons. Clearing the recovered draft discards its pending choice.
- Added `app/src/test/java/com/example/chat/ConversationRecoverableDraftTest.kt`: five source fixtures for exact text, edit identity, distinct view-once kind and invalid mixed/missing target state.
- Verified: Kotlin source syntax parser, whitespace and exact patch apply-check after the menu-geometry delta. These are not compilation or executed tests.
- Unverified: no builds, tests, live callbacks, account-switch/failure harness, visual layout, focus or TalkBack. Recovery remains one session-local slot, not durable multi-draft storage. Server-side edit-window enforcement is unchanged; the existing UI registry does not encode a time window. No new full-DONE count is claimed.
- No MessageBubble, Contact info/privacy source, four reassigned settings screens, backend, controller, environment or CI edits.
