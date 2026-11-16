# Conversation menu geometry change log

## 2026-11-15 - source-only checkpoint

- `app/src/main/kotlin/com/example/chat/ui/chats/detail/MessageInteractionAnchorPolicy.kt`: sanitizes non-finite/negative dimensions and invalid density/anchor coordinates, limits margins/rail/menu extents to the actual viewport, and treats 120dp menu room as a preference rather than an overflowing forced minimum. Invalid zero-space input returns bounded zero extents rather than inventing 100dp.
- `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt`: uses policy-provided menu width and rail maximum width/height; the rail clips to its bounded viewport while retaining existing horizontal scrolling. Existing action-menu scrolling remains.
- `app/src/test/java/com/example/chat/MessageInteractionAnchorPolicyTest.kt`: authored fixtures for 220x160, 100x60, 8x8, zero viewport, invalid density/anchor and non-finite viewport. Existing normal-size assertions retained.
- Verified: Kotlin source syntax parser, whitespace and exact patch apply-check. These are not builds or executed geometry tests.
- Unverified: Android type checking, all test execution, actual tiny-pane/menu chrome layout, action reachability, keyboard, focus, screenshots and device hit bounds. Extremely short panes may still require a different compact menu presentation. CHA-10 remains partial, not complete.
- Scope: no MessageBubble, privacy-worker files, backend, controller, CI or reassigned Conversation settings screens changed.
