Counts/classification superseded by nested-comment correction in INCREMENT-2.md; migration count remains confirmed.

# Increment 1 - safe text actions and candidate triage

Apply after 2fa950ee + unified v3 + cb45d573afcfc7e6584a9c586b22584462ac57fe8f91e6dac35e52b3fdd2ee01.
This is incremental, not a replacement for the prior shared-component patch.

## Changes

- 52 additional stock TextButton calls migrated to ChatyTextButton in 22 UI files. Only calls with onClick/modifier/enabled and one plain Text child were selected. Label expressions, callback bodies, modifiers, enabled expressions, explicit text colors and font weights were carried through. Original source remains in line comments. No new shared API or behavioral flags were introduced.
- Scope: poll Create; scheduled-message UI; network/storage UI actions; navigation/template/app-icon settings; conversation appearance controls; About dialog; shared composer Send and overlay Cancel. No backend, queue, E2E/MLS, notification-security or persistence logic was edited.
- Shared overlay Confirm now uses ChatyActionButton with destructive/default tone. Destructive text now uses the shared semantic onError token instead of fixed white. Callback/label unchanged. Default tone follows Chaty's accent instead of stock Material presentation.
- ChatyPreviewRadioRow shadow now uses colors.shadow. Shared modal border now uses colors.borderSubtle. Both change appearance and remain visually unverified.

## Candidate classification

The two increment-1 triage TSVs classify every candidate from the previous snapshot: 681 control candidates and 746 color candidates. Their line numbers refer to that previous snapshot. This is a first-pass routing/classification, not individual acceptance of all expressions.

Control snapshot: 53 migrated; 38 held for specialized auth/onboarding review; 87 held for security/privacy/parallel-feature review; 503 still require individual presentation/parameter review.
Color snapshot: four migrated; ten reviewed exceptions retained (six arbitrary-background contrast fallbacks, four central semantic token definitions); 732 still require individual review. Most media/camera/call palette entries are not yet proven defects or exceptions.

Confirmed exceptions:
- ChatyChoiceControls.chatyReadableOn: black/white are final contrast fallbacks against arbitrary supplied backgrounds, after preferred/fallback theme colors fail 4.5:1.
- GbKit color-picker swatches: selected check glyph chooses black/white against arbitrary user-selected swatches. Replacing with a fixed theme glyph would lose that background-relative choice.
- AppColors central semantic definitions: base color values belong here, unlike per-screen literals. This is not an exception for unrelated theme-file code.
- Specialized auth fields/actions remain pending separate review; they are not automatically accepted as intentional exceptions.

Current inventories after this increment: 628 stock-control candidates in 134 UI files; 742 color candidates; zero Modifier.shadow calls missing explicit ambient/spot color arguments under ui. Counts are lexical, not compiler symbol resolution. Files under other source roots and other elevation mechanisms remain outside those counts.

## Binary row status

| Row | Status | Remaining gap |
| --- | --- | --- |
| 36 | Not done | Full 542 ledger not reconciled. 628 stock-control candidates and 742 color candidates remain, plus per-screen/variant acceptance. |
| 38 | Not done | Actual component image coverage still awaits authorized app build/render/device work. No invented screenshots. |
| 39 | Not done | Explicit Modifier.shadow color coverage is source-complete within the scan scope; other elevation APIs, per-screen depth adoption, reference match, light/dark rendering and clipping remain unchecked. |

## Checks

- Source matcher verified the 52 original stock bindings and preserved named outer arguments/label expressions against the previous snapshot. Explicit text color/fontWeight values were copied without reinterpretation.
- Delimiter deltas in changed Kotlin files are unchanged against the previous snapshot.
- Patch application and byte equality are checked at delivery, not claimed as compilation.
- No builds, test execution, app rendering, commits, pushes, CI or paid tools. No regression-free, compilation-success or reference-fidelity claim. Appearance and touch geometry change with shared controls.
