# Increment 5 - per-screen search and Home view controls

Apply after increment 4 (04663d28) on the existing unified-v3 chain. Five Kotlin UI files plus source audit/report changes.

## Reviewed screens and changes

- HomeQuickViewDialog.kt: saved-view name input now uses ChatyTaskField; name state/change callback, localized label, single-line policy and full-width modifier preserved. The 12 TextButton calls in this file were already ChatyTextButton through an explicit import alias, not stock Material defects. They remain untouched and are listed in already-shared-aliases.tsv.
- TemplatesSettingsScreen.kt: template/component search now uses ChatyTaskField. Trimming/lowercasing callback, query state, search and clear icons, clear callback, single-line policy and caller modifier preserved. Shared field tokens replace stock field shape/color overrides.
- UpdatesScreen.kt: local status search now uses ChatyTaskField; local-only query callback, label, clear trailing action and caller modifier preserved. No feed, camera, publish or story behavior changed.
- SettingsSearchScreen.kt: search now uses compact-header ChatyTaskField. Query callback, clear icon/callback, single-line policy and weighted modifier preserved. Accessibility label explicitly remains "Search settings". Shared field tokens replace prior stock field colors/shape; placeholder color follows shared foregroundSecondary rather than the old tertiary override.
- ConversationStyleGallery.kt: family filter uses shared ChatyChoiceTile with compact geometry and selected state. Family label expression, existing check marker, selection semantics and callback retained. It remains a local preview filter; no theme persistence changed.

All four migrated fields make KeyboardOptions.Default, minLines=1 and maxLines=1 explicit. No submission/validation/IME/persistence behavior is added. Shared field/control geometry, label chrome and accessible grouping may differ and remain unrendered. Old source is retained in line comments.

## Updated audit meaning

The scanner now separates explicit shared-kit aliases from stock-looking unresolved calls. It does not count the 12 already-shared HomeQuickViewDialog actions as remaining stock controls. This is a classification correction, not 12 new migrations. Fully qualified stock calls are not excluded by an unrelated alias.

Current scan: 512 stock-control candidates in 122 UI files, 12 already-shared alias calls in one file, 697 color occurrences. The previously reviewed color classification is unchanged: 214 retained roles and 483 pending individual review. Zero Modifier.shadow calls missing ambient/spot colors under the scanned UI subtree. Remaining counts are still lexical candidates; no compiler resolution, all-screen or full-variant claim.

36: Not done. Original 542-item ledger remains unavailable. Whole-screen/variant rollout, 512 control candidates and 483 still-unreviewed color occurrences remain.
38: Not done. Actual images await authorized app build/render/device work.
39: Not done. Other elevation mechanisms, full-screen depth adoption and visual/reference acceptance remain.

Source checks verify all four migrated field contracts against the previous snapshot and unchanged delimiter deltas in changed Kotlin files. Patch transport is checked by apply-check, actual application and byte equality. No build, test execution, app rendering, screenshots, commit, push, CI, paid actions or behavior/security/backend edits. No compile, runtime, regression-free or exact-reference-match claim.
