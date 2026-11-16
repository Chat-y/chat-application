# Conversation selection protection change log

## 2026-11-15 - source-only checkpoint

- Changed `app/src/main/kotlin/com/example/chat/ui/chats/detail/SelectionActionPolicy.kt`: Copy, Forward and Star now fail closed for any `view_once` representation other than absent/null or explicit Boolean false. This includes string and numeric encodings and malformed values. Delete remains available as before. Ordinary text selection is unchanged.
- Added `app/src/test/java/com/example/chat/ConversationSelectionProtectionTest.kt`: source fixtures for ordinary/mixed/empty selection and Boolean/string/numeric/malformed flags.
- Verified: source diff inspection and whitespace check only. Kotlin parser inspection is reported alongside this patch.
- Unverified: tests were not compiled or run; no app build, emulator, device, screenshots or backend calls. Source-only restriction remains in force.
- Scope: local source edits only. No privacy-worker file, backend, controller, CI, workflow, environment or paid-service changes.
