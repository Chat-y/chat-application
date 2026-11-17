# Rows 63 and 65 against unified local v3

Base: 2fa950ee30f984274760565aa1b9639161e9d81a plus unified v3 SHA256 25df1cc1898abc7d8c70db3bb0ebdfd007b71b57a9874aba98e09424c76c660f, applied alone.

## Row 63: Done

The original mapping was recovered from the authenticated conversation on November 16. The explanation at 18:28:15 numbered the three Home controls; the owner's numbered response at 18:32:00 requested their changes; the explicit mapping at 18:32:21 was accepted at 18:33:08.

- Hide redundant Home unread-triage button; keep the Unread filter.
- Move appearance checker into Home three-dot menu as "Check Home appearance".
- Move Group by date to a Settings on/off switch.

Active source matches this mapping:

- `HomeAppBars.kt:551-559`: appearance checker in overflow, correct label and callback.
- `ChatsHomeScreen.kt:1331`: appearance callback opens the existing preview.
- `ChatsHomeScreen.kt:1491-1506`: old triage and appearance controls retained inside inactive block comment.
- `ChatsHomeScreen.kt:1566`: old Group Off/Date control inside inactive block comment.
- `HomeTagFilters.kt:56,342`: Unread filter retained.
- `HomeScreenSettingsPage.kt:199-202`: Group chats by date switch writes the existing preference under the scope guard.

Six reproducible source contract checks pass in the supplied audit script. No duplicate rename or UI change was needed. No owner decision remains for this recovered mapping. This says nothing about unrelated older menus whose mapping was not part of this exchange.

## Row 65: Not done

Exact gap: no exhaustive purpose-to-glyph inventory or verified visual uniqueness across the app. There are confirmed source collisions, not merely missing evidence.

The effective default unselected Hugeicons map contains 281 IDs and 10 exactly identical vector-XML groups. Examples:

- `calls`, `callAudio`, `earpiece`, `voiceCall` share one drawing.
- `messageClock`, `schedule` share one drawing.
- `grid`, `widgets` share one drawing.

Other asset families also have exact duplicates: Majesticons Line 7 groups; Majesticons Solid 8; Pikaicons Stroke 6; Pikaicons Solid 5; Phosphor Regular 1. The attached JSON lists every group and drawable identity. The source script accounts for supplemental mappings taking precedence over base mappings.

There is also a direct cross-purpose binding in active `HomeAppBars.kt`: "New Call" and "Who can call me?" both use `callAudio` (lines 532 and 539). A navigation/action alias is not automatically a different purpose, but initiating a call and choosing call privacy are different purposes.

The scanner finds 151 literal stock `Icon(Icons...)` call sites outside the icon implementation directory that still need purpose review. This is a conservative syntax-based count, not all remaining UI icons: wrapped, dynamic, aliased, multiline and materialized calls may need additional inspection. Direct ID reference counts are recorded only as triage leads; repeated use alone does not prove a semantic collision.

The audit does not change icon art without an inspected reference. Distinct XML is not proof of a distinct silhouette. Selected variants, ten procedural families, every intended purpose and actual pixel comparisons remain unverified. The existing `ChatyUniqueIconsTest` covers a limited audited subset, not an exhaustive app-wide uniqueness guarantee.

No binary Done claim is supported for row 65. No new owner decision is necessary to confirm the existing one-purpose requirement; the remaining work is implementation and visual acceptance. No security, E2E, MLS, queue or notification file was changed.

## Validation and patch contents

Only additive source-audit files. No production behavior changed, no files removed. Run:

`python3 scripts/audit_icon_purpose_coverage.py --output docs/audit/rows-63-65-unified-v3-source.json`

This is a source inspection, not Kotlin test execution. No build, compile, device render, commit, push or CI was run.
