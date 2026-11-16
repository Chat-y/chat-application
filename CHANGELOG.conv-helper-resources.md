# Conversation shared helper change log

## 2026-11-15 - source-only checkpoint

- `ConversationReviewComponents.kt`: moved contrast/default/custom/reset/support-copy/disclosure labels to default Android string resources. Copy confirmation now resets when the exported appearance map changes instead of incorrectly continuing to say copied for new values.
- `ConversationColorOptions.kt`: moved bulk-apply and emoji preset labels to resources; preset keys are invariant identifiers, no longer dependent on visible Light/Dark/Accent wording.
- `ConversationStyleGallery.kt`: moved search/empty/All/session-recent labels to resources. Empty string is the invariant all-families key, separate from the localized All label.
- Added `app/src/main/res/values/strings_conversation_helpers.xml`: 19 unique helper resources. Source files above are under `app/src/main/kotlin/com/example/chat/ui/settings/conversation/`.
- Verified: XML parsing, unique resource names and reference coverage; Kotlin syntax parser, whitespace and exact patch apply-check. No compilation, tests or build executed.
- Unverified: Android resource compilation, localization, actual pixels, accessibility and state restoration. Added default-language resources only, not translations. No claim that the entire review's resource-extraction item is complete.
- No helper signature changes, four reassigned settings screens, existing shared string file, privacy-worker files, backend, controller or CI changes.
