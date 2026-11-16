# Conversation selection and banner change log

## 2026-11-15 - source-only checkpoint

- `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt`: selection action strip receives the selected count, publishes count-specific Copy/Forward/Delete/Star labels, groups directional focus and scrolls horizontally instead of compressing controls at narrow widths. Each action and Cancel selection has an explicit minimum 48dp target. Duplicate Star icon speech removed.
- Same file: edit/reply banner dismiss buttons have explicit 48dp targets and distinct Cancel editing message / Cancel reply labels. Subtitles permit two lines instead of one. Existing polite live-region behaviour retained.
- Verified: changed source parsed with Kotlin tree-sitter; whitespace check and patch apply-check. These are not Android type or UI checks.
- Unverified: no compilation, tests, builds, emulator/device, actual pixels, focus traversal or TalkBack inspection. CHA-31 and CHA-48 remain partial pending those checks. No completion claim for the complete review entries.
- Scope: no callback semantics, persistence, backend, CI, environment or four reassigned Conversation settings screens changed.
