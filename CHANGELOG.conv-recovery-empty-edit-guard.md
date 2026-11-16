# Recovery while an empty edit is active

## 2026-11-15 - source-only correction

- `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt`: restoring any separately retained failed draft now requires no active edit, not merely empty composer text. This prevents a restored regular/view-once draft from accidentally inheriting an unrelated empty edit target, or replacing another edit target. Wording asks the user to finish/cancel the current edit first.
- Same file: explicit regular-send choice for recovered view-once text rechecks current captured owner/session and absence of an active edit before changing its pending-mode state.
- Verified: Kotlin syntax parser, whitespace and exact patch apply-check after typing-publication-safety delta. No compiler/tests/build/runtime/visual actions.
- Unverified: actual restoration/focus behaviour and race harnesses. Single recovery-slot and session-local state limitations remain. No new full-DONE count.
- No backend, controller, MessageBubble, privacy or reassigned settings files changed.
