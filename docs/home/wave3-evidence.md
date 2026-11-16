# Home wave 3 test evidence

State when written: source only. Not compiled. No test was run. No device, emulator or screenshot.

Stack used for the apply check: pin 3c67bf51e87c007e37e2195ba714ced988ee87f6, start patch (sha256 e095f074...), wave 1, wave 2 (S1-S13, S16), wave 3 patches, in that order.

| Area | Test | Status |
|---|---|---|
| S5 selection (select all, invert, range, bulk actions, inspector order) | HomeFinalPoliciesTest | SOURCE_TESTED, unexecuted |
| S6 pane layout and divider clamp | HomeFinalPoliciesTest | SOURCE_TESTED, unexecuted. Hinge is a seam only, no fold source |
| S8 grouping (pinned collapse, date, alphabetical, direct/group) | HomeFinalPoliciesTest | SOURCE_TESTED, unexecuted |
| S9 keys and dedupe | HomeFinalPoliciesTest | SOURCE_TESTED, unexecuted |
| S11 operation state, retry intent, failure classes | HomeFinalPoliciesTest | SOURCE_TESTED, unexecuted |
| S12 folder policy | folderPickerFilter only | PARTIAL. The other functions need ChatFolderStore, which needs an Android Context |
| S13 saved views and history | HomeFinalPoliciesTest | Policy SOURCE_TESTED. UI wiring absent |
| R1-47 metrics additive | HomeFinalPoliciesTest | SOURCE_TESTED, unexecuted |
| Callback inventory | HomeCallbackInventoryTest | Source-text checks, unexecuted |
| Cross-file gaps | 5 tests marked @Ignore with the desired end state | BLOCKED on Screen, Bars and Host owners. Remove @Ignore when fixed |

Held and not touched: StarredMessagesScreen, storage schema and reset, R2-26 gesture, localization resources, external routes.

Not covered: Compose rendering, focus and pointer behaviour, process recreation, TalkBack, visual regression, folder store durability, real fold devices.

To close: run `./gradlew testDebugUnitTest`, fix any compile or assertion failure in these tests first (they were written without a compiler), then record the result and date here.
