# Conversation overnight patch routing

Local-only source checkpoint. No commits, merges, pushes, pipeline or builds were executed here.

## Consolidated route

1. Start at baseline 3c67bf51e87c007e37e2195ba714ced988ee87f6.
2. Apply conv-pass2-cumulative.patch (replaces all older pass-1 combined/split patches).
3. Apply conv-shared-after-pass2.patch (shared gallery sticky headings + one search projection).
4. Apply conv-night-final-overlay.patch.

The overnight overlay REPLACES the fifteen overnight deltas below. Do not apply the overlay on top of those deltas. It changes 11 Kotlin/resource files and includes 15 change logs. It does not include changes from the four separately owned Conversation settings screens after pass 2.

## Independent earlier patches

These remain separate and are NOT included in this overnight overlay:

- conv-failed-retry-only.patch (MessageBubble retry wrapper).
- conv-composer-16sp-only.patch (shared input/placeholder type size).
- conv-contact-host-awaited-only.patch (requires ContactInfoScreen's optional awaited callback).

The ContactInfo host-await patch does not establish transactional deletion or account safety inside data-layer suspension; keep its reported integration caveats.

## Alternative individual overnight route

Use these instead of the overnight overlay:

1. conv-selection-protection-only.patch (independent policy change).
2. conv-helper-resources-only.patch (independent shared helper change, after shared gallery/search).
3. conv-selection-accessibility-only.patch.
4. conv-action-revalidation-only.patch.
5. conv-translation-mode-guard-only.patch.
6. conv-single-tap-parity-only.patch.
7. conv-composer-session-guards-only.patch.
8. conv-exact-draft-recovery-only.patch.
9. conv-menu-geometry-only.patch.
10. conv-recovery-operation-kind-only.patch.
11. conv-reaction-retry-guards-only.patch.
12. conv-search-scope-semantics-only.patch.
13. conv-typing-publication-safety-only.patch.
14. conv-recovery-empty-edit-guard-only.patch.
15. conv-menu-geometry-archive-only.patch (independent policy comment addition after menu geometry).

ChatDetail deltas 3-14 are sequential except the independent helper-resource/policy branches. Each includes a change-by-change CHANGELOG.conv-*.md. Existing .gitignore ignores *.md; explicitly retain the logs in the final change set. No .gitignore change is needed for patch application.

## Status

No additional full-DONE count is claimed by these fifteen overnight deltas. They close specific source gaps within still-partial composer/security/accessibility items. Source parse, whitespace, XML and patch-application checks passed; no Android compilation, test execution, build, actual pixel, focus, accessibility, server or provider validation was performed for these changes. Earlier 17 passing JVM tests are historical and do not validate the overnight changes. The primary 446-entry ledger must be reconciled with the separately delivered screen changes rather than overwritten by an older count.

Remaining material caveats include the single session-local recovery slot, no durable recovery, backend plaintext reply metadata contract, server-side edit windows and callback authorization, async cancellation/account-switch harnesses, translation policy/integration beyond the new guard, tiny-pane menu chrome/accessibility, and all runtime/visual checks.

## Final consolidated handoff, 2026-11-16 03:01 IST

The final overlay contains every overnight change delivered through the 02:00 geometry archive correction, including reaction/retry activation guards, search scope semantics, typing callback safety and the empty-edit recovery correction. It replaces the earlier overnight checkpoint overlay plus all subsequent overnight deltas. Apply either the final overlay or the individual delta chain, never both. Fresh apply against baseline + pass-2 cumulative + shared gallery/search delta succeeded, with all 26 overlay files matching the authoring worktree by SHA-256. Ten Kotlin files parse and helper XML parses. Fifteen change logs are included. No new source work or additional full-DONE status is claimed by this consolidation.
