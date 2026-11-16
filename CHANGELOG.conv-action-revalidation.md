# Conversation action revalidation change log

## 2026-11-15 - source-only checkpoint

- `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt`: single-message menu activation now requires the captured room/account/session to remain current and resolves exactly one latest message, rejecting duplicate identity matches.
- Same file: Copy/Forward/Star/Delete selection callbacks now re-read latest message input and selected IDs, verify session/room ownership, reject blank/missing/duplicate/cross-room selection identities, and recheck capability intersection before invoking any callback. No partial bulk action is substituted when selected rows disappear. Existing copy formatting and selected-content clearing remain unchanged after success.
- Dependency: the separate fail-closed SelectionActionPolicy patch improves protection decoding. This patch does not edit that policy again.
- Verified: Kotlin syntax parse, whitespace inspection and exact patch apply-check after prior shared and accessibility deltas. These do not establish Android type correctness.
- Unverified: no compiler, build, tests, device, screenshots or live actions. Callback/server authorization and cross-session race harnesses remain open. CHA-07/CHA-21 remain partial rather than fully completed.
- Source-only edits. No backend, controller, CI, environment, privacy-worker files or four reassigned Conversation settings screens changed.
