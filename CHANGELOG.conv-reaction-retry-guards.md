# Conversation reaction and retry activation guard change log

## 2026-11-15 - source-only checkpoint

- `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt`: album/double-tap/inline/anchored-menu/picker reaction activation now routes through one latest-message, current room/account/session, protected-content and release-feature check. Blank reaction values are ignored. Existing reaction semantics and emoji choices are unchanged.
- Same file: retry activation re-resolves exactly one current message and only invokes the existing callback while it is still the current user's failed, non-system, non-deleted/non-revoked message in the current session. Stale failed icons cannot retry a message that is already sent or belongs to a different room/session.
- Verified: Kotlin syntax parse, source call-site audit, whitespace and exact patch apply-check after recovery-operation-kind delta (or after its consolidated overnight overlay).
- Unverified: no compilation/test/build, transport retry/reaction, real pointer/accessibility activation or actual pixels. Retry de-duplication/in-flight exclusion still belongs at the operation boundary; the Unit callback provides no completion signal here. CHA-09/CHA-21 remain partial, not newly DONE.
- No MessageBubble, Contact info/privacy source, four reassigned settings screens, backend, controller, CI or environment edits.
