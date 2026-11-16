# Conversation search scope semantics change log

## 2026-11-15 - source-only checkpoint

- `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt`: search input has an explicit loaded-history accessibility description and remains single-line. No-match and match-position state descriptions explicitly name loaded history rather than implying a complete remote search. The displayed current index clamps to 1..count while results change.
- Same file: search Back/Previous/Next/Clear controls explicitly use 48dp targets. The trailing control says Close search when query is empty and Clear search otherwise, matching the existing callback's behaviour.
- No live-region announcement loop, debounce, indexing, backend/full-history query or search algorithm change introduced.
- Verified: Kotlin source syntax parser, whitespace and exact patch apply-check after reaction-retry-guards (or the overnight overlay plus that delta).
- Unverified: no compiler, tests, builds, screenshots, keyboard/focus or TalkBack activation. CHA-30/50 remain partial until actual semantics and scope behaviour are verified. No additional full-DONE count.
- No MessageBubble, Contact info/privacy, four reassigned settings screens, controller, backend, environment or CI changes.
