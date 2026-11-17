# Increment 6 - row 36 only, template screen filled actions

Apply after increment 5 (47e501c3) on the existing unified-v3 chain. This increment owns row 36 only. No depth or component-image work.

TemplatesSettingsScreen.kt:
- Import, Apply confirmation and Reset confirmation now use ChatyActionButton. Existing callback bodies, labels and coroutine/error paths are preserved, with old stock source retained in line comments.
- The full-template card action now uses ChatyActionButton with the same callback, enabled condition, full-width modifier and state-dependent container/foreground roles. Fixed white foreground on primary becomes colors.onPrimary. Stock 12dp shape becomes the existing shared capsule; disabled colors follow the shared disabled tokens. No data/template application code is changed.
- No new API, color literal, dependency or behavior flag.

Source review intentionally leaves ComponentOverrideScreen's card action pending: it supplies explicit disabled surface/foreground overrides, which the shared action API does not accept separately. Switching it blindly would lose those disabled presentation choices. This is an unresolved mapping, not a permanent approved Material exception.

Current row-36 inventories: 508 stock-control candidates in 121 UI files; 12 already-shared aliases; 696 color candidates (214 retained definition/content/contrast roles, 482 pending review). Current-color-classification line references are refreshed. Lexical candidates are not compiler-resolved defects. The original 542-item ledger is unavailable and is not replaced by these inventories.

Row 36: Not done. Remaining candidates, all-screen/variant adoption, full original-ledger reconciliation and visual checks remain. Rows 38 and 39 are outside this increment and now have separate owners; their status is not advanced here.

Checks: source comparison of three filled-action callback maps/labels, manual card-action callback/condition/role comparison, unchanged Kotlin delimiter deltas, strict patch apply-check, actual apply and changed-file byte equality. No build, tests, runtime rendering, screenshots, commit, push, CI or paid actions. Exact appearance, accessibility and regression freedom remain unverified. No queue, E2E/MLS, notification-security, backend or persistence edits.
