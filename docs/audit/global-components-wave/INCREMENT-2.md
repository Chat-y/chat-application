# Increment 2 - chat actions, shared width floor and corrected scan

Apply after unified v3, the first shared-theme patch cb45d573, and increment 1 (2c0f5642). This is another incremental patch, not a standalone replacement.

## Source changes

- 79 active stock text actions migrated to ChatyTextButton in 14 chat/search/update files. Scope: new-chat actions, chat detail action sheets, tag/folder controls, contact/group info actions, starred messages, emoji/sticker/pack pickers, translator, member quick view, search and updates.
- Only calls with onClick/modifier/enabled and one plain Text child were migrated. Original callbacks, labels, modifier/enable expressions and explicit color/fontWeight values are preserved. Old source is retained in line comments. No operation logic is intentionally changed. Shared presentation/touch geometry is different and remains visually unverified.
- Shared ChatyTextButton and ChatyActionButton now both have a 64dp minimum-width token alongside the existing 48dp minimum height. Parent layout constraints still take precedence, as with normal Compose minimum-size modifiers. This addresses short labels shrinking after replacement of stock Material buttons.
- Three composer glyphs on primary fills use onPrimary; four call-header foreground usages on success fill use onSuccess; navigation badge text uses onError. Selected capsule navigation glyph/text use the existing readable-on helper against their configurable active fill rather than fixed white. Ten fixed-white usages removed from active shared-renderer code. Explicit navigation active-color overrides remain supported.

## Nested-comment correction

Kotlin block comments can nest. The earlier regular-expression scan could see old inactive code after the first nested closing delimiter. The corrected scanner tracks nested block depth and strings separately.

Corrected counts under app/src/main/kotlin/com/example/chat/ui:

| Snapshot | Stock-control call candidates | UI files with those candidates | Color candidates | Modifier.shadow calls missing explicit ambient/spot color |
| --- | ---: | ---: | ---: | ---: |
| Unified v3 before this lane's patches | 673 | 147 | 727 | 21 |
| After first shared-theme patch | 665 | 145 | 714 | 0 |
| After increment 1 | 612 | 134 | 710 | 0 |
| After increment 2 | 533 | 126 | 700 | 0 |

The prior remaining counts of 681/746 and 628/742 included inactive code and are superseded by this table. Increment 1's 52 active text migrations and one active filled-action migration are confirmed. The first patch migrated eight active actions, not nine: its UniversalEmojiPackScreen rewrite was inside already-inactive nested-comment source and does not count as active adoption. Similarly, the first patch changed 21 active default-shadow sites, not 22. Inactive source rewrites do not prove active coverage.

The increment-1 triage files now explicitly mark inactive entries as discarded: 16 control entries and 32 color entries. Historical source line references remain baseline references. Reviewed exceptions (contrast fallbacks and central token definitions) remain. Other classifications are first-pass routing only, not full individual acceptance.

Current shared inventory still has 121 composable declarations in 39 kit/core-component files. This includes private helpers and overloads and excludes reusable UI outside those directories; it is not a complete variant catalog.

## Binary status

| Row | Status | Exact gap |
| --- | --- | --- |
| 36 | Not done | Full 542 ledger not reconciled. 533 stock-control and 700 color candidates remain for individual defect/exception classification plus complete per-screen/variant adoption. |
| 38 | Not done | Actual app-render images still pending authorized build/emulator/device work. No invented mockups. |
| 39 | Not done | All active Modifier.shadow calls in the scanned UI subtree have explicit colors, but other elevation mechanisms, all-screen adoption and visual/reference acceptance remain unchecked. |

## Checks and limits

Source checks verify 79 original stock bindings and unchanged outer named arguments/label expressions; explicit text color/fontWeight expressions were copied directly. Delimiter deltas remain unchanged across all changed Kotlin files. Patch transport is validated separately by applying to the previous snapshot and byte-comparing every changed file. None of these checks proves compilation or runtime behavior.

No build, Gradle/test execution, runtime rendering, screenshots, commit, push, CI or paid actions. No E2E/MLS, queue, notification-security, persistence or backend changes. Visual reference fidelity, clipping, light/dark readability, accessibility and performance remain unverified.
