# Exact submitted draft recovery change log

## 2026-11-15 - source-only checkpoint

- `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt`: ordinary send, edit and view-once failure recovery retain the original submitted composer text, including leading/trailing whitespace, instead of restoring the trimmed outbound payload.
- Outbound send/edit/view-once payload remains trimmed exactly as before; only failed-draft restoration/separate storage uses the raw submitted draft. Existing revision and owner checks remain unchanged.
- Verified: Kotlin source syntax parser, whitespace and patch apply-check after the composer-session-guards delta. No compiler/test/build/device/provider operation ran.
- Unverified: failure recovery UI, race harness and durable recovery. No new completed-entry count. No backend, CI, privacy or reassigned settings screen edits.
