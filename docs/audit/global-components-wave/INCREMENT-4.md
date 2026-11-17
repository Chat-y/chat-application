# Increment 4 - reviewed color exceptions and reproducible scan

Apply after increment 3 (878732d3) on the existing unified-v3 patch chain. Audit/report changes only; no app source changes in this increment.

The current 697 literal-color candidates are now classified in current-color-classification.tsv:

| Retained role | Candidate occurrences |
| --- | ---: |
| Per-effect camera previewColor metadata | 100 |
| Central call-bundle token palettes | 72 |
| Central camera-overlay palette | 11 |
| Central semantic definitions (both existing resolvers) | 15 |
| Central island token palette | 10 |
| Arbitrary-background contrast fallbacks | 6 |
| Pending individual review | 483 |

These 214 retained occurrences define content-specific palettes, shared semantic palettes or background-relative contrast choices. They are not accepted as proof of screen-level token adoption, reference fidelity or accessible rendering. In particular, literals in DynamicIslandViews, LensOverlay, call views and other consumers remain unreviewed. Keeping central bundle/island palettes does not approve corresponding raw colors in screens. No palette or effect artwork is changed by this report.

The 529 remaining stock-control candidates still require parameter/role classification and safe migration or a justified exception. None is silently accepted by this increment.

scan_ui_candidates.py is a standard-library-only source scanner. From the repository root, run:

    python docs/audit/global-components-wave/scan_ui_candidates.py .

It rewrites the four current lexical inventory TSVs under this directory. It tracks nested block comments, line comments and quoted strings. It does not compile Kotlin, resolve symbols, enumerate every possible color/elevation API or generate screenshots. Character/string-template and language-edge cases still require source review. Output counts must not be mistaken for a full audit ledger.

## Binary status

36: Not done. Full 542-item ledger was not found in this tree's docs/mdfiles/research or the two memory searches performed. The generated inventories cannot stand in for it. 529 control candidates and 483 still-unreviewed color occurrences remain, plus full per-screen/variant adoption and visual checks.
38: Not done. Actual shared-component images remain pending an authorized app build/render/device pass.
39: Not done. Explicit Modifier.shadow color coverage and one custom box-shadow migration do not prove all depth mechanisms/screens or reference fidelity.

This source-only iteration has no build, test execution, app rendering, commit, push, CI or paid action. The scanner is run only for source inventory, not runtime testing. No claim of bug-free, compile-success or visual acceptance is made.
