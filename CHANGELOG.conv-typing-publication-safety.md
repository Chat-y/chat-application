# Conversation typing publication safety change log

## 2026-11-15 - source-only checkpoint

- `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt`: positive typing, blank-draft stop, idle stop, lifecycle-stop and disposal-stop publications now pass through a best-effort exception boundary. Ordinary synchronous presence callback errors cannot interrupt composer changes or a send after its input is cleared. CancellationException still propagates.
- Original room/account-bound callback capture is retained for lifecycle/disposal cleanup. Cadence, idle time, draft persistence, and local text updates are unchanged. No replay queue, retries, private text logging or per-keystroke snackbars added.
- Verified: Kotlin source syntax parse, source call-site review, whitespace and exact patch apply-check after search-scope-semantics delta.
- Unverified: no compilation/test/build, lifecycle/account-switch harness, injected callback failure test, device UI or network call. Draft-persistence callback failures are not swallowed by this change. CHA-46 remains partial.
- No MessageBubble, privacy-worker files, four reassigned settings screens, backend, controller, environment or CI changes.
