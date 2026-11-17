# Increment 3 - simple fields and remaining shared depth

Apply after unified v3 + cb45d573 + increment 1 (2c0f5642) + increment 2 (bce1e0bb). Not a standalone replacement.

- Four string fields now use ChatyTaskField: poll question/option editors, template-profile JSON input and conversation-style gallery search. Original value/change callbacks, labels/placeholders, caller modifiers, single/multiline policy and explicit line limits are retained. Old Material editing defaults are explicit: KeyboardOptions.Default and minLines=1. Single-line search has maxLines=1; multiline defaults retain Int.MAX_VALUE unless originally bounded. No new validation, truncation, persistence, submission or IME action was added. Shared field geometry/label chrome and accessible naming differ and await runtime review.
- Context menu's two literal black backgrounds are replaced by a central contextMenuScrim token. Combined alpha .424 preserves the same-color .20/.28 layer composition. The new black base value lives only in the central theme token, not per-screen code. No theme-dependent light/dark scrim change is claimed.
- Shared call-activity capsule custom chatyBoxShadow now uses semantic shadow color; blur/offset/corner values and clipping order remain unchanged. This extends token adoption to one non-Modifier.shadow mechanism. It is presentation only, not call/session logic.

Current nested-comment-aware scan: 529 stock-control candidates in 126 UI files, 697 color candidates, zero Modifier.shadow calls missing explicit ambient/spot colors. Central c(hex) token definitions are not counted by the Color(hex)/Black/White candidate expression; these counts are not a universal hard-coded-color detector.

36: Not done. Full 542 ledger and remaining per-screen/variant adoption/classification unresolved.
38: Not done. Actual app images await authorized build/render/device work.
39: Not done. Other elevation mechanisms and all-screen visual/depth/reference acceptance remain unchecked.

Source checks compare the four migrated field argument maps with the previous snapshot. Delimiter deltas are unchanged across changed Kotlin files. Patch transport is checked by apply-check, actual application and file byte equality. No compilation, build/test execution, rendering, screenshots, commit/push/CI or paid actions. No runtime correctness or exact-reference-match claim. Notification-security, E2E/MLS, queue, backend and persistence logic remain untouched.
