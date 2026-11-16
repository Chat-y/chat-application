# Menu geometry archived reference

## 2026-11-16 - source-only rule correction

- `app/src/main/kotlin/com/example/chat/ui/chats/detail/MessageInteractionAnchorPolicy.kt`: retained the prior fixed-minimum geometry implementation in a disabled block comment, as required by the comment-out-not-delete rule. Active bounded geometry is unchanged.
- Verified: Kotlin syntax parser, whitespace and exact patch apply-check. No compiler/tests/build/runtime/pixel action. No behaviour or completion-count change.
