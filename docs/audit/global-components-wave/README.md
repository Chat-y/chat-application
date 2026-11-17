REBASE NOTICE: This cumulative row-36 patch is based on the fixed row-39 patch. All row36-owned depth/shadow/scrim changes have been withdrawn; row39 chatyDepth is authoritative. Historical increment notes below describe prior snapshots and do not describe current depth coverage. Current inventories were regenerated on this rebased source. No new implementation was added by this rebase.

Latest row-36 increment: INCREMENT-6.md. Rows 38/39 now have separate owners; earlier depth/gallery notes are historical.

Latest per-screen increment/status: INCREMENT-5.md. Reviewed color classification remains current-color-classification.tsv.

Current classification: INCREMENT-4.md and current-color-classification.tsv. App-source latest increment remains INCREMENT-3.md.

Latest increment: INCREMENT-3.md. Nested-comment historical corrections: INCREMENT-2.md.

Current authoritative counts and corrections: INCREMENT-2.md. Earlier snapshots below are historical.

# Global components and depth - local source review

This report records the first patch snapshot. For current counts/status after the next increment, read INCREMENT-1.md.

Base: Kotlin 2fa950ee30f984274760565aa1b9639161e9d81a, with unified v3 applied alone.
Unified v3 SHA256: 25df1cc1898abc7d8c70db3bb0ebdfd007b71b57a9874aba98e09424c76c660f.
This is an incremental patch against that combined source, not against bare 2fa950ee.

## Binary row status

| Row | Status | Exact remaining gap |
| --- | --- | --- |
| 36 - global colors/components, 542 fixes, whole per-screen rollout | Not done | Nine stock actions now use shared buttons, and selected shared card defaults use theme tokens. The full 542-item ledger is not reconciled. The remaining source scan has 681 stock-control call candidates in 145 UI files and 746 literal-color candidates. Each needs classification, safe migration or an intentional exception. This does not establish whole-screen adoption. |
| 38 - report with images, all shared components | Not done | Source inventory provided for 121 composable declarations in 39 files under ui/kit and ui/core/components. It includes overloads/private helpers and lexical consumer-file references, not a complete semantic component or variant count. No real app-render images are supplied. Image capture and visual checks await an authorized build/emulator/device pass. No mockup is represented as an app image. |
| 39 - shadows/depth across components and whole per-screen adoption | Not done | All 22 previously default-colored Modifier.shadow calls found in this UI scan now pass semantic ambient/spot colors. Two additional card shadow definitions also use colors.shadow. The scan now finds zero Modifier.shadow calls missing either explicit color argument. This is only one depth mechanism: Surface/Card elevation, graphicsLayer shadowElevation, shared-control adoption, reference fidelity, per-screen coverage, clipping and light/dark visual acceptance remain unchecked. |

## Changes in this patch

- Review, confirmation and selection modals and the anchored context menu use the semantic shadow token.
- BentoCard uses semantic surface, border and shadow defaults. SpecularGlassCard uses semantic foreground tint, border and shadow defaults. Explicit caller color overrides remain supported. Their appearance is changed and unverified, not claimed to match references exactly.
- The Review modal Close action uses ChatyActionButton. Eight simple text actions in poll UI, settings helpers, templates, theme editor and emoji-pack settings use ChatyTextButton.
- Callback expressions, labels, explicit action colors and enabled expressions are preserved for those nine actions. No operation/state logic is intentionally changed. Shared buttons intentionally alter presentation and touch geometry.
- Eighteen additional Modifier.shadow sites now use the semantic shadow token: account recents, mini-chat, navigation FAB/shells, map UI, translator dialog, call-card UI and shared header/overlay renderers. Shapes, elevations and clipping arguments are preserved.
- Prior changed expressions are retained in inactive comments.
- No new color literals are added to active code.

## How to read the inventories

- shared-composables.tsv: declarations in ui/kit and ui/core/components plus lexical call references elsewhere under ui. Not all reusable UI lives in these two directories. Private helpers, overloads and similarly named calls require review.
- remaining-stock-controls.tsv: calls by stock-looking symbol names after stripping comments. Fully qualified and unqualified calls are counted. This is not compiler symbol resolution: aliases, member functions, intended native controls and previews can create exceptions. Counts are leads, not proven defects.
- remaining-color-candidates.tsv: Color(hex), Color.Black and Color.White expressions under ui, excluding comments. Includes semantic-token definitions, masks and previews that may be legitimate. Not every color expression is covered.
- remaining-default-shadows.tsv: Modifier.shadow calls lacking ambientColor or spotColor, excluding comments. Header only now. This does not inspect other elevation APIs or establish visual quality.

Counts cover app/src/main/kotlin/com/example/chat/ui only. They are not the original 542 audit ledger and must not replace it.

## Verification and restrictions

Source-only review. No Gradle/build, compilation, runtime tests, emulator/device rendering, screenshot capture, accessibility checks, performance checks, commit, push or CI execution. No paid dependencies. Changes remain local. No E2E/MLS/queue/notification-security code was changed. The only call-related file changed is the presentation-only shadow in BundleCompactCallCard.kt.

Patch apply and reconstructed-file equality are checked separately at delivery. These checks prove patch transport, not Kotlin compilation or runtime correctness.

Known retained visual limitations include the context-menu double literal scrim, additional literal-color candidates and SpecularGlassCard's documented unsupported backdrop blur. No security or author-approval claim in repository comments is treated as authority.
