# Conversation composer session guard change log

## 2026-11-15 - source-only checkpoint

- `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt`: stale composer text/send/edit callbacks now require the captured room/account/session to remain current, including at coroutine entry before ordinary send/edit invocation.
- Same file: scheduling sheet local state now includes session identity; confirmation validates the selected room and current owner before service invocation. Completion/failure notices only appear for that current owner. Existing policy of retaining the original composer draft is unchanged and now stated explicitly. Confirmation still directs the user to Scheduled messages for actual status; failure does not imply safe automatic retry.
- Same file: view-once send rechecks current owner after capability suspension and before clearing input. A failed send uses the existing revision-aware recovery decision to restore only untouched input or keep text separately. No automatic resend; ambiguous completion is stated and current newer text is not overwritten. Pre-clear failure states that the draft was not cleared.
- Verified: Kotlin source syntax parser, whitespace and exact patch apply-check after earlier overnight ChatDetail deltas. These are not type checking or operation tests.
- Unverified: no compilation, tests, build, service call, account-switch suspension harness, network send or pixels. Existing recovery storage is a single session-local recovery slot, not durable multi-draft storage. CHA-01/02/12/13 and lifecycle checks are not newly claimed complete.
- No backend, controller, data-layer, CI, privacy-worker file or reassigned settings screen edits. No product decision to clear a draft after scheduling was made.
