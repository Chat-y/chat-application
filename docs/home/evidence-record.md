# Home wave 2 evidence record (R5-50)

State when written: source only. Nothing was compiled and no test was run.

| Item | Evidence | Status |
|---|---|---|
| R2-45 visual set | Scene names (empty, long-name, high-unread, locked, selected, narrow-window) in HomeWave2FixturesTest | PARTIAL: names only, no rendering |
| R2-46 recreation | Survive/reset rule table and enum round-trip | PARTIAL: rule is stated, Screen not checked, needs emulator |
| R2-47 malformed summary | Blank name, future time, long Unicode, duplicate id | PARTIAL: invalid avatar color not covered, it is a Row param |
| R2-48 inventory | HomeFeatureInventory.kt | PARTIAL: no per-callback map, no no-op default check |
| R2-49 account transition | none beyond pure policy checks | BLOCKED: needs Host/Screen UI render |
| R5-48 counters | docs/home/diagnostic-counters.md | Policy text |

Each row must also carry the exact code change, test evidence, visual evidence where relevant, and the remaining caveat. Research and source-only tests are not runtime acceptance.

To close: run `./gradlew testDebugUnitTest` and an emulator recreation test, then record the result and date here.
