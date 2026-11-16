# Conversation translation-mode guard change log

## 2026-11-15 - source-only translation-wiring edge case

- `app/src/main/kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt`: uses the already-supplied translation-mode parameter instead of ignoring it. Known Server, Server + No outside apps and Server + In-outside apps modes can reach the existing Google provider disclosure/approval dialog. In-app/offline, external-app-only and unknown/blank values receive an honest unavailable dialog without creating the provider dialog or invoking it.
- Same file: translation eligibility also checks current captured room/account/session and resolves exactly one latest message before/after provider work through the existing eligibility callback.
- Grounding: Universal settings writes Server or In-app; its labels distinguish Google/Cloud from Offline/Direct. Host passes that Universal value. Existing legacy server labels are retained. No offline translator was invented.
- Verified: source Kotlin syntax parser, whitespace and exact patch apply-check. No compilation or execution.
- Unverified: settings-to-dialog runtime, cancellation, provider call counts and actual UI pixels. This is an edge-case guard, not completion of held translation wiring or CHA-22.
- Scope: only ChatDetailScreen plus this change log. No translation client/dialog, Universal settings, backend, controller, CI, environment, privacy-worker file or reassigned Conversation screen edits.
