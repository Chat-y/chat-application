# Conversation implementation checkpoint

Baseline: 3c67bf51e87c007e37e2195ba714ced988ee87f6
Local branch: impl/conv
No commits, pushes, PRs, CI runs, APK builds, backend/.env/CI edits or paid services.

**This is an incomplete checkpoint, not completion of all 446 items.**

DONE means code written, not Android-verified. Pure JVM tests are identified explicitly. All Compose/UI edits remain uncompiled and visually unverified.

## Counts

- DONE: 104
- ALREADY DONE: 15
- BLOCKED: 68
- NOT DONE: 259

Total: 446

## Verification

- 17 isolated JVM JUnit tests passed: style IDs/legacy reads/fallbacks, bubble/tick ID round-trips, draft decisions, arrival identities, protected search and contrast thresholds.
- Actual new pure sources compiled with downloaded Kotlin 2.0.21. The search test compiled actual ChatMessage plus its pure dependencies and a DTO-only copy of MessageReceiptTimes to avoid unrelated network stack classes. This is not a full app test.
- All changed Kotlin source/test files passed a tree-sitter syntax parse. This does not type-check Compose or Android references.
- git diff --check passed. Exported patch also applied cleanly to a fresh baseline worktree.
- No Android SDK, emulator or Gradle wrapper/toolchain available. No app compilation, instrumented test, app screenshot, accessibility run, performance profile or live backend/provider call.
- OpenCode was unavailable in this environment. Independent OpenCode review remains required before use.

## Important caveats

- Shared changes are new helpers/registry and narrowly scoped ChatComposer changes. No global settings kit/model/controller mutation was made.
- The local edit helper serializes edits made through these four screens only. It is not a controller-wide atomic mutation guarantee.
- Contrast warnings currently use opaque token ratios. Actual wallpaper/alpha compositing needs runtime validation.
- New style IDs preserve legacy labels on read, but a downgrade to an older APK will not understand newly saved IDs. No rollback flag was implemented.
- Many review items propose architecture alternatives, device audits or policy work rather than immediate code changes. They remain explicit below.

## Per-screen counts

- AB: DONE 22, ALREADY DONE 5, BLOCKED 27, NOT DONE 87
- MO: DONE 23, ALREADY DONE 2, BLOCKED 18, NOT DONE 53
- BT: DONE 30, ALREADY DONE 2, BLOCKED 11, NOT DONE 41
- ES: DONE 22, ALREADY DONE 5, BLOCKED 11, NOT DONE 35
- CHA: DONE 7, ALREADY DONE 1, BLOCKED 1, NOT DONE 43

## Action Bar

### AB-1.01 | DONE | CODE Colour picker opens on the wrong colour
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.02 | DONE | CODE Colour rows can never return to the theme default
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.03 | DONE | CODE Hiding name and picture leaves an anonymous header
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.04 | NOT DONE | CODE OPT-IN One switch hides both call buttons
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.05 | DONE | CODE Status colour rows stay live when the status line is disabled
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.06 | NOT DONE | CODE Preview is a hand-built copy of the real header
Settings preview remains a separate composable from the production header; no extraction shared with chat was attempted without Android compilation.

### AB-1.07 | DONE | CODE Preview text ignores the font-size setting
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.08 | DONE | CODE Preview carries another product's slogan
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.09 | NOT DONE | CODE Stale snapshot p in click lambdas
Partial: new conversation-only equality snapshot and fresh-state edit helper are used by these four pages. No controller-wide atomic API, StateFlow/lifecycle collector or global scaffold migration was made.

### AB-1.10 | NOT DONE | CODE Screen recomposes on every preference change
Partial: new conversation-only equality snapshot and fresh-state edit helper are used by these four pages. No controller-wide atomic API, StateFlow/lifecycle collector or global scaffold migration was made.

### AB-1.11 | NOT DONE | CODE Unused imports
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.12 | DONE | PROPOSAL "Hide Contact Name" is not a privacy feature
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.13 | NOT DONE | PROPOSAL Validate colour values on read
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.14 | NOT DONE | PROPOSAL Backups and shared looks carry these flags
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.15 | ALREADY DONE | PROPOSAL Writes should not block the main thread
Controller persist() launches writes through its ordered coroutine writer; no new write mechanism required. StrictMode verification still not run.

### AB-1.16 | NOT DONE | PROPOSAL Stable callbacks and params
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.17 | NOT DONE | PROPOSAL Skip preview recomposition on unrelated taps
Settings preview remains a separate composable from the production header; no extraction shared with chat was attempted without Android compilation.

### AB-1.18 | BLOCKED | PROPOSAL Baseline profile coverage
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### AB-1.19 | NOT DONE | PROPOSAL Debounce continuous picker output
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.20 | NOT DONE | CODE Preview card has no accessibility summary
Partial: preview has a generic sample-header summary, but it does not enumerate live hidden/shown state or add a polite preview liveRegion.

### AB-1.21 | DONE | PROPOSAL Colour rows expose a swatch only
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.22 | NOT DONE | PROPOSAL Screen title needs a heading role
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.23 | DONE | PROPOSAL Header uses a hand-built bar instead of ChatyAppBar
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.24 | ALREADY DONE | PROPOSAL Switch tile semantics
Existing GbSettingRow uses whole-row toggleable(role=Switch), selection haptic and padded row. Semantics/device hit-bounds audit not run.

### AB-1.25 | DONE | PROPOSAL Contrast of the bar, icons and text on a custom colour
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.26 | DONE | PROPOSAL "Online" in accent over a custom bar
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.27 | DONE | PROPOSAL 12sp italic status text
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.28 | NOT DONE | PROPOSAL Header at 200% font scale
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.29 | NOT DONE | PROPOSAL Preview changes silently
Partial: preview has a generic sample-header summary, but it does not enumerate live hidden/shown state or add a polite preview liveRegion.

### AB-1.30 | NOT DONE | PROPOSAL Hidden controls must disappear from the a11y tree
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.31 | DONE | PROPOSAL State must not rely on colour alone
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.32 | NOT DONE | PROPOSAL Reduce motion
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.33 | NOT DONE | PROPOSAL Preview vs real header size
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.34 | NOT DONE | PROPOSAL Icon size 20 and 22dp
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.35 | NOT DONE | PROPOSAL Icon spacing 14dp and 12dp
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.36 | DONE | PROPOSAL Screen title 19sp
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.37 | DONE | PROPOSAL Name 15sp bold
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.38 | DONE | PROPOSAL Rounded top-only bar
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.39 | NOT DONE | PROPOSAL Mixed icon families
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.40 | NOT DONE | PROPOSAL Inconsistent title case
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.41 | NOT DONE | PROPOSAL Group related controls
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.42 | NOT DONE | PROPOSAL Row order
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.43 | NOT DONE | PROPOSAL Most-used first
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.44 | DONE | PROPOSAL Preview caption
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.45 | NOT DONE | PROPOSAL Sample name
Partial: sample name changed to Alex; no debug long-name toggle added.

### AB-1.46 | NOT DONE | PROPOSAL Row subtitle length
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.47 | NOT DONE | PROPOSAL Vague wording
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-1.48 | DONE | PROPOSAL Settings groupings use cards of about 14dp margin
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-1.49 | NOT DONE | PROPOSAL Dark-mode preview check
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.01 | NOT DONE | PROPOSAL Move logic into a ViewModel
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.02 | NOT DONE | PROPOSAL Single UiState class
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.03 | NOT DONE | PROPOSAL Use collectAsStateWithLifecycle
Partial: new conversation-only equality snapshot and fresh-state edit helper are used by these four pages. No controller-wide atomic API, StateFlow/lifecycle collector or global scaffold migration was made.

### AB-2.04 | NOT DONE | PROPOSAL Move to DataStore
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.05 | NOT DONE | PROPOSAL Typed colour preference
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.06 | NOT DONE | PROPOSAL Map fallbacks to Material roles
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.07 | DONE | PROPOSAL Respect dynamic colour
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-2.08 | NOT DONE | PROPOSAL Standard top app bar
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.09 | DONE | PROPOSAL Edge-to-edge insets on content
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-2.10 | NOT DONE | PROPOSAL Remember picker target across rotation
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.11 | NOT DONE | PROPOSAL testTag preview nodes
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.12 | NOT DONE | PROPOSAL Compose previews
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.13 | NOT DONE | PROPOSAL Accessibility checks in UI tests
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.14 | NOT DONE | PROPOSAL Extract fallback colour logic
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.15 | NOT DONE | PROPOSAL Hex entry in the picker
Existing picker accepts 6-digit RGB hex, but the proposed 8-digit ARGB input was not added.

### AB-2.16 | NOT DONE | PROPOSAL Named colour palette
Existing picker has 12 preset swatches, but they are hex-labelled rather than named colours.

### AB-2.17 | NOT DONE | PROPOSAL OPT-IN Auto-contrast helper
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.18 | ALREADY DONE | PROPOSAL Haptic on toggle
Existing GbSettingRow uses whole-row toggleable(role=Switch), selection haptic and padded row. Semantics/device hit-bounds audit not run.

### AB-2.19 | NOT DONE | PROPOSAL Search registry entry
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.20 | NOT DONE | PROPOSAL Deep link
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.21 | NOT DONE | PROPOSAL Strings in resources
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.22 | ALREADY DONE | PROPOSAL Whole-row tap target
Existing GbSettingRow uses whole-row toggleable(role=Switch), selection haptic and padded row. Semantics/device hit-bounds audit not run.

### AB-2.23 | NOT DONE | PROPOSAL Tablet width cap
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.24 | NOT DONE | PROPOSAL Two-pane on wide screens
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.25 | NOT DONE | PROPOSAL Fold-aware layout
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.26 | NOT DONE | PROPOSAL Immutable prefs model
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.27 | NOT DONE | PROPOSAL Document prefs in KDoc
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.28 | NOT DONE | PROPOSAL Feature flag
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.29 | NOT DONE | PROPOSAL Extract settings scaffold
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.30 | NOT DONE | PROPOSAL Design tokens
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-2.31 | NOT DONE | PROPOSAL Typography tokens
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.01 | NOT DONE | PROPOSAL OPT-IN WhatsApp-like header baseline
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.02 | NOT DONE | PROPOSAL OPT-IN Telegram-style subtitle choice
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.03 | NOT DONE | PROPOSAL OPT-IN Signal per-chat colour
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.04 | NOT DONE | PROPOSAL OPT-IN Hide presence line
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.05 | NOT DONE | PROPOSAL Typing and presence preview states
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.06 | NOT DONE | PROPOSAL Muted-chat and pinned indicators
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.07 | NOT DONE | PROPOSAL OPT-IN Translucent bar
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.08 | NOT DONE | PROPOSAL OPT-IN Header alignment
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.09 | NOT DONE | PROPOSAL OPT-IN Avatar shape and size
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.10 | NOT DONE | PROPOSAL OPT-IN Call buttons to overflow
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.11 | NOT DONE | PROPOSAL OPT-IN Separate icon colour
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.12 | NOT DONE | PROPOSAL OPT-IN Header divider
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.13 | NOT DONE | PROPOSAL Tappable preview
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.14 | NOT DONE | PROPOSAL Preview with two sample bubbles
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.15 | NOT DONE | PROPOSAL OPT-IN Share a look
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.16 | NOT DONE | PROPOSAL Name weight option
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.17 | NOT DONE | PROPOSAL Landscape compact bar
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.18 | NOT DONE | PROPOSAL Status marquee
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-3.19 | NOT DONE | PROPOSAL OPT-IN Unread badge near back
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-4.01 | BLOCKED | PROPOSAL RTL mirroring of the header
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.02 | BLOCKED | PROPOSAL Indic and Arabic line height
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.03 | BLOCKED | PROPOSAL Very long contact names
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.04 | BLOCKED | PROPOSAL Emoji and ZWJ sequences in names
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.05 | BLOCKED | PROPOSAL Tablet and 10-inch width
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.06 | BLOCKED | PROPOSAL Multi-window, 300dp-high window
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.07 | BLOCKED | PROPOSAL Display cutout in landscape
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.08 | DONE | PROPOSAL Three-button and gesture navigation
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-4.09 | BLOCKED | PROPOSAL Rotation with the colour picker open
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.10 | BLOCKED | PROPOSAL Theme switch while the screen is open
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.11 | BLOCKED | PROPOSAL Two screens writing conversation prefs
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.12 | DONE | PROPOSAL fontScale of zero or extreme
Local implementation written; shared app bar, effective/reset colour rows, preview text/semantics, warning or insets as applicable. Android compile and runtime verification pending.

### AB-4.13 | BLOCKED | PROPOSAL Accent equals bar colour
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.14 | BLOCKED | PROPOSAL Bar colour equals chat background
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.15 | ALREADY DONE | PROPOSAL Hide everything
Back and overflow are outside hide-name/profile/call conditions in the existing preview. Device audit not run.

### AB-4.16 | BLOCKED | PROPOSAL Hardware keyboard
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.17 | BLOCKED | PROPOSAL Voice Access label match
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.18 | BLOCKED | PROPOSAL Largest display size and magnification
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.19 | BLOCKED | PROPOSAL Colour correction and inversion
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.20 | BLOCKED | PROPOSAL Corrupt or unreadable prefs
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.21 | BLOCKED | PROPOSAL Downgrade after new keys
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.22 | BLOCKED | PROPOSAL Rapid toggling
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-4.23 | BLOCKED | PROPOSAL Low-end device response
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### AB-5.01 | NOT DONE | PROPOSAL Unit test: prefs copy semantics
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-5.02 | NOT DONE | PROPOSAL Unit test: fallback colour chain
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-5.03 | NOT DONE | PROPOSAL Compose test: hide name
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-5.04 | NOT DONE | PROPOSAL Compose test: both hidden warning
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-5.05 | NOT DONE | PROPOSAL Compose test: reset writes null
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-5.06 | BLOCKED | PROPOSAL Screenshot tests across themes and font scales
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### AB-5.07 | BLOCKED | PROPOSAL Accessibility checks in CI
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### AB-5.08 | BLOCKED | PROPOSAL Lint for hard-coded UI strings
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### AB-5.09 | BLOCKED | PROPOSAL Compose lint rules
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### AB-5.10 | BLOCKED | PROPOSAL Macrobenchmark for opening Settings pages
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### AB-5.11 | BLOCKED | PROPOSAL StrictMode in debug
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### AB-5.12 | NOT DONE | PROPOSAL Contrast gate for shipped themes
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-5.13 | NOT DONE | PROPOSAL Rewrite subtitles in Chaty voice
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-5.14 | NOT DONE | PROPOSAL Use one icon pack for every row
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-5.15 | NOT DONE | PROPOSAL Shared settings hooks and scaffold
Partial: new conversation-only equality snapshot and fresh-state edit helper are used by these four pages. No controller-wide atomic API, StateFlow/lifecycle collector or global scaffold migration was made.

### AB-5.16 | NOT DONE | PROPOSAL Local usage counters in a debug menu
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-5.17 | NOT DONE | PROPOSAL Support export
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-5.18 | NOT DONE | PROPOSAL Rollout and rollback
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### AB-5.19 | NOT DONE | PROPOSAL PR checklist for settings screens
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.


## More Options

### MO-1.01 | NOT DONE | CODE Five of nine options have no preview
Partial: emoji panel/header/icons, group name and underlined link samples added. Link/group samples are not embedded in production message bubbles.

### MO-1.02 | NOT DONE | CODE Play glyph is hard-coded white
Partial: preview chooses the higher-contrast of two theme tokens instead of fixed white. Actual player still uses its existing white glyph; no guaranteed black/white auto-contrast implemented.

### MO-1.03 | DONE | CODE "Playing bar" colour paints the whole pill, not the bar
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.04 | DONE | CODE Track colour ignores the user pick
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.05 | DONE | CODE Info balloon default colours are hard-coded
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.06 | DONE | CODE Info balloon text is 11sp
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.07 | DONE | CODE Preview ignores the font-size setting
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.08 | DONE | CODE Preview claims end-to-end encryption
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.09 | DONE | CODE Info balloon sample does not match what the setting says
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.10 | DONE | CODE Nested cards
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.11 | DONE | CODE Colour pick starts on accent
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.12 | DONE | CODE No reset to default on nine rows
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.13 | DONE | CODE Sibling screens pass different row API
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.14 | NOT DONE | CODE Stale snapshot p in nine lambdas
Partial: fresh-state local edit helper and conversation-only equality listener added; no controller-wide atomic API, ViewModel or StateFlow migration.

### MO-1.15 | NOT DONE | CODE Whole-screen recomposition per change
Partial: fresh-state local edit helper and conversation-only equality listener added; no controller-wide atomic API, ViewModel or StateFlow migration.

### MO-1.16 | NOT DONE | CODE Nine similar rows in one list
Partial: section cards added. Participant row still sits with system rows rather than the requested exact grouping.

### MO-1.17 | NOT DONE | CODE Wording is inconsistent
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.18 | DONE | PROPOSAL Hyperlink colour needs 4.5:1 on both bubble colours
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.19 | NOT DONE | PROPOSAL Links must not rely on colour alone
Partial: sample link is underlined; actual link-renderer underline parity was not changed or verified.

### MO-1.20 | DONE | PROPOSAL Emoji header icons vs header colour
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.21 | DONE | PROPOSAL Info balloon text vs background
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.22 | NOT DONE | PROPOSAL Participant name colour in a group
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.23 | DONE | PROPOSAL Participant name contrast on both bubbles
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.24 | ALREADY DONE | PROPOSAL Play button is 32dp in the sample
Actual voice player has a 48dp toggle. Preview visual now also uses 48dp. Device bounds not measured.

### MO-1.25 | NOT DONE | PROPOSAL Voice note elapsed text 12sp
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.26 | DONE | PROPOSAL Preview card has no accessibility summary
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.27 | DONE | PROPOSAL Row colour value is only a swatch
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.28 | NOT DONE | PROPOSAL Title needs heading role
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.29 | NOT DONE | PROPOSAL Title 19sp
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.30 | DONE | PROPOSAL Header uses a hand-built bar instead of ChatyAppBar
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.31 | DONE | PROPOSAL Screen name "More options" is vague
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.32 | DONE | PROPOSAL Colour-only values
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-1.33 | NOT DONE | PROPOSAL Live preview heading is 14sp bold fixed
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.34 | NOT DONE | PROPOSAL Spacing
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.35 | NOT DONE | PROPOSAL Icons from three families
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.36 | NOT DONE | PROPOSAL Emoji rows share an icon metaphor
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.37 | NOT DONE | PROPOSAL Persisted colour validation
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.38 | NOT DONE | PROPOSAL Backups
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.39 | NOT DONE | PROPOSAL Preview must stay fake
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-1.40 | BLOCKED | PROPOSAL Baseline profile
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### MO-1.41 | ALREADY DONE | PROPOSAL Debounce picker writes
GbPickers.showColor explicitly calls back only when Apply is pressed, so it does not persist continuous drag output.

### MO-2.01 | NOT DONE | PROPOSAL ViewModel with UiState
Partial: fresh-state local edit helper and conversation-only equality listener added; no controller-wide atomic API, ViewModel or StateFlow migration.

### MO-2.02 | NOT DONE | PROPOSAL DataStore migration
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.03 | NOT DONE | PROPOSAL Table-driven rows
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.04 | NOT DONE | PROPOSAL Typed colour preference
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.05 | NOT DONE | PROPOSAL Emoji popup theme integration
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.06 | NOT DONE | PROPOSAL Material colour roles
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.07 | NOT DONE | PROPOSAL Stability annotations
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.08 | NOT DONE | PROPOSAL Extract preview into a composable
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.09 | NOT DONE | PROPOSAL Scaffold
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.10 | DONE | PROPOSAL Edge-to-edge insets
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-2.11 | NOT DONE | PROPOSAL rememberSaveable picker target
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.12 | NOT DONE | PROPOSAL Compose previews
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.13 | NOT DONE | PROPOSAL Accessibility tests
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.14 | NOT DONE | PROPOSAL testTags
Partial: preview semantic descriptions added; the requested testTags were not added.

### MO-2.15 | DONE | PROPOSAL Contrast helper shared with Action Bar
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-2.16 | NOT DONE | PROPOSAL Hex input
Existing RGB hex input retained; additional 8-digit/typed colour workflow not added.

### MO-2.17 | NOT DONE | PROPOSAL Recent colours
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.18 | NOT DONE | PROPOSAL Apply one colour to several rows
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.19 | NOT DONE | PROPOSAL Search registry
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.20 | NOT DONE | PROPOSAL Two-pane layout
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-2.21 | NOT DONE | PROPOSAL Feature flag
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-3.01 | NOT DONE | PROPOSAL WhatsApp links are blue underlined by default
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-3.02 | NOT DONE | PROPOSAL OPT-IN Telegram and WhatsApp use several hues for group senders
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-3.03 | NOT DONE | PROPOSAL Date chips
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-3.04 | NOT DONE | PROPOSAL Voice note UI
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-3.05 | NOT DONE | PROPOSAL OPT-IN Emoji panel style
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-3.06 | NOT DONE | PROPOSAL OPT-IN Signal colours per chat
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-3.07 | NOT DONE | PROPOSAL Info bubble content
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-3.08 | NOT DONE | PROPOSAL OPT-IN Reduce to presets
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-4.01 | BLOCKED | PROPOSAL RTL order of the voice pill
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.02 | BLOCKED | PROPOSAL Long group member names
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.03 | BLOCKED | PROPOSAL Links inside RTL text
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.04 | BLOCKED | PROPOSAL Light link colour on a light bubble
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.05 | NOT DONE | PROPOSAL Light play button with white glyph
Partial: preview chooses the higher-contrast of two theme tokens instead of fixed white. Actual player still uses its existing white glyph; no guaranteed black/white auto-contrast implemented.

### MO-4.06 | BLOCKED | PROPOSAL Emoji icons equal header
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.07 | BLOCKED | PROPOSAL Theme switch with screen open
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.08 | BLOCKED | PROPOSAL Custom colours with dynamic colour
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.09 | BLOCKED | PROPOSAL Font scale 200% in the preview
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.10 | BLOCKED | PROPOSAL Landscape and tablet width
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.11 | DONE | PROPOSAL Cutout and system bar insets
Local code written for effective/reset colours, progress-track parity, neutral two-chip sample, scaling, preview samples/semantics, contrast warnings or insets. Unverified Android UI.

### MO-4.12 | BLOCKED | PROPOSAL Rotation with the picker open
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.13 | BLOCKED | PROPOSAL Rapid changes across nine rows
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.14 | BLOCKED | PROPOSAL Corrupt or downgraded prefs
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.15 | BLOCKED | PROPOSAL Hardware keyboard and Voice Access
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-4.16 | BLOCKED | PROPOSAL Colour-blind filters
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### MO-5.01 | NOT DONE | PROPOSAL Unit test for the contrast helper
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-5.02 | NOT DONE | PROPOSAL One UI test per row
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-5.03 | NOT DONE | PROPOSAL Row-to-pref coverage test
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-5.04 | BLOCKED | PROPOSAL Screenshot tests across themes and 2x font
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### MO-5.05 | BLOCKED | PROPOSAL Accessibility checks in CI
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### MO-5.06 | BLOCKED | PROPOSAL Lint for hard-coded strings and compose rules
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### MO-5.07 | NOT DONE | PROPOSAL Wording pass in Chaty voice
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-5.08 | NOT DONE | PROPOSAL One icon pack across rows
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-5.09 | NOT DONE | PROPOSAL Rollout and rollback
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### MO-5.10 | NOT DONE | PROPOSAL Support export
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.


## Bubbles and Ticks

### BT-1.01 | DONE | CODE Left bubbles ignore the chosen bubble style
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.02 | DONE | CODE Tick preview shows one state only
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.03 | DONE | CODE Gallery tick tiles use accent, live preview uses time colour
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.04 | DONE | CODE Two storage conventions for tick style
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.05 | DONE | CODE Bubble style is written twice
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.06 | DONE | CODE Quote divider is a square rect over a rounded box
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.07 | NOT DONE | CODE Right text and time default to fixed white
Partial: outgoing text chooses higher contrast of two theme tokens; time follows it and warnings show low contrast. This is not a guaranteed black/white or Material onColor implementation.

### BT-1.08 | DONE | CODE Time text is 10sp
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.09 | DONE | CODE Preview text size is not tied to system font scale
Text-size row now states before-system-scaling scope and preview uses bounded selected sp. Device 200% check pending.

### BT-1.10 | DONE | CODE Pinned preview takes a fixed share of the screen
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.11 | DONE | CODE "Right" and "Left" bubble labels
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.12 | DONE | CODE The two switches sit in the "Colors" card
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.13 | NOT DONE | CODE Behaviour switches have no preview
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-1.14 | DONE | CODE OPT-IN "Remove Read more..." changes layout
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.15 | NOT DONE | CODE Colour rows: 12 plus 2 switches plus slider plus 2 galleries
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-1.16 | DONE | CODE Picker starts on accent
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.17 | DONE | CODE No Reset on 12 colour rows
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.18 | NOT DONE | CODE Conversation background colour only applies with no wallpaper
Subtitle is honest but wallpaper-aware dimming and actual wallpaper preview were not added.

### BT-1.19 | NOT DONE | CODE Deleted-message icon colour has no preview
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-1.20 | DONE | CODE Time shows "07:11 PM" fixed
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.21 | NOT DONE | CODE Preview text hard-coded
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-1.22 | DONE | CODE Gallery ignores the passed subtitle
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.23 | DONE | CODE "(vector redraw)" is implementation jargon
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.24 | BLOCKED | CODE Style names reference other products and characters
Requires asset rights/store-policy judgment; neutral renaming alone cannot prove rights. No legal claim made.

### BT-1.25 | NOT DONE | CODE Option names are creator handles
Partial: gallery now searchable and keyed. Creator/brand style labels, recents and family grouping remain unchanged.

### BT-1.26 | NOT DONE | CODE 95 tick styles and 59 bubble styles in plain lists
Partial: gallery now searchable and keyed. Creator/brand style labels, recents and family grouping remain unchanged.

### BT-1.27 | NOT DONE | CODE Preview rows in the modal compose live bubbles
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-1.28 | NOT DONE | CODE Whole screen recomposes on any pref change
Partial: conversation-only equality snapshot and page-local fresh edits, but no ViewModel, controller-wide atomic update, StateFlow or extracted BubblePreview(state).

### BT-1.29 | DONE | CODE Wording case
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.30 | NOT DONE | PROPOSAL Gallery as horizontal cards
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-1.31 | NOT DONE | PROPOSAL ChatyOptionCard accessibility
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-1.32 | ALREADY DONE | PROPOSAL Selected state not by colour alone
Existing option-card selection uses border, checkmark and weight. No replacement needed; new gallery uses existing radio selection cues.

### BT-1.33 | DONE | PROPOSAL Contrast of quoted text
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.34 | NOT DONE | PROPOSAL Contrast of sender name on left bubble
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-1.35 | NOT DONE | PROPOSAL Contrast between bubbles and background
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-1.36 | NOT DONE | PROPOSAL Hyperlink and tick colour on custom bubbles
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-1.37 | NOT DONE | PROPOSAL Slider accessibility
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-1.38 | DONE | PROPOSAL Slider target and range
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.39 | DONE | PROPOSAL Preview card summary
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.40 | DONE | PROPOSAL Screen title
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-1.41 | NOT DONE | PROPOSAL Bubble max widths 240 and 250dp
Partial: incoming/outgoing preview maxima are both 250dp, but no responsive 80-percent-of-pane policy was added.

### BT-1.42 | NOT DONE | PROPOSAL Spacing
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-2.01 | NOT DONE | PROPOSAL One style registry for ticks
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-2.02 | NOT DONE | PROPOSAL One style registry for bubbles
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-2.03 | NOT DONE | PROPOSAL ViewModel and UiState
Partial: conversation-only equality snapshot and page-local fresh edits, but no ViewModel, controller-wide atomic update, StateFlow or extracted BubblePreview(state).

### BT-2.04 | NOT DONE | PROPOSAL Atomic prefs update
Partial: conversation-only equality snapshot and page-local fresh edits, but no ViewModel, controller-wide atomic update, StateFlow or extracted BubblePreview(state).

### BT-2.05 | DONE | PROPOSAL Shared ContrastChecker
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-2.06 | NOT DONE | PROPOSAL Default text colour from bubble
Partial: outgoing text chooses higher contrast of two theme tokens; time follows it and warnings show low contrast. This is not a guaranteed black/white or Material onColor implementation.

### BT-2.07 | DONE | PROPOSAL Dynamic colour
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-2.08 | DONE | PROPOSAL Search in the style modal
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-2.09 | NOT DONE | PROPOSAL Family grouping
Partial: gallery now searchable and keyed. Creator/brand style labels, recents and family grouping remain unchanged.

### BT-2.10 | DONE | PROPOSAL Keys in lazy lists
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-2.11 | NOT DONE | PROPOSAL Live preview scoped recomposition
Partial: conversation-only equality snapshot and page-local fresh edits, but no ViewModel, controller-wide atomic update, StateFlow or extracted BubblePreview(state).

### BT-2.12 | NOT DONE | PROPOSAL Stability
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-2.13 | DONE | PROPOSAL Edge-to-edge insets
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-2.14 | NOT DONE | PROPOSAL Landscape and tablet
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-2.15 | NOT DONE | PROPOSAL Compose previews
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-2.16 | NOT DONE | PROPOSAL Haptics
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-2.17 | NOT DONE | PROPOSAL Reduce motion
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-3.01 | NOT DONE | PROPOSAL WhatsApp bubbles are one shape with a tail
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-3.02 | NOT DONE | PROPOSAL OPT-IN Telegram groups bubbles by sender
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-3.03 | NOT DONE | PROPOSAL Signal chat colours
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-3.04 | NOT DONE | PROPOSAL iMessage-like tails and clustering
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-3.05 | DONE | PROPOSAL Telegram message text size
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-3.06 | NOT DONE | PROPOSAL Read receipt style
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-3.07 | NOT DONE | PROPOSAL OPT-IN Message density
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-4.01 | BLOCKED | PROPOSAL Long single word
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### BT-4.02 | BLOCKED | PROPOSAL RTL conversation
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### BT-4.03 | BLOCKED | PROPOSAL Font scale 200% and text size 26
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### BT-4.04 | BLOCKED | PROPOSAL Small width 320dp
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### BT-4.05 | BLOCKED | PROPOSAL Landscape pinned preview
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### BT-4.06 | BLOCKED | PROPOSAL Transparent bubble style with a custom colour
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### BT-4.07 | DONE | PROPOSAL Quote very long text
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-4.08 | ALREADY DONE | PROPOSAL Tick style missing after update
Existing bubble/tick resolver falls back to stock for unknown values. Round-trip tests retain legacy/name reads.

### BT-4.09 | BLOCKED | PROPOSAL TalkBack on option cards
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### BT-4.10 | BLOCKED | PROPOSAL Keyboard navigation in the modal
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### BT-5.01 | NOT DONE | PROPOSAL Test every style id has a renderer
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-5.02 | NOT DONE | PROPOSAL Migration test for stored ids
Stable bubble/tick ID round-trip JVM tests pass; no instrumented migration test was run or added.

### BT-5.03 | BLOCKED | PROPOSAL Screenshot tests
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### BT-5.04 | NOT DONE | PROPOSAL Contrast gate for default pairs
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-5.05 | NOT DONE | PROPOSAL Compose test: left and right bubble follow the style
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### BT-5.06 | BLOCKED | PROPOSAL Rights review checklist
Requires asset provenance/rights and policy review beyond source-only implementation. No legal or store-compliance claim made.

### BT-5.07 | DONE | PROPOSAL Remove vector-redraw jargon
Local implementation written: both-direction renderer parity, sent/delivered/read previews, stable enum IDs, no duplicate GB write, clipped quote, scalable minimum type, collapsible preview, labels/grouping, reset/effective colours, searchable keyed gallery or insets. Stable-id tests pass; Android UI uncompiled.

### BT-5.08 | NOT DONE | PROPOSAL Rollout and rollback
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.


## Entry Style

### ES-1.01 | DONE | CODE About 150 lines of dead preview code
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.02 | DONE | CODE Seven styles exist only in dead code
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.03 | NOT DONE | CODE "Reference 2" has no dedicated layout in the dead preview
Partial: Reference 2 has its own production renderer, and all 31 registry names map to known layouts by source inspection. No render/distinctness test written.

### ES-1.04 | DONE | CODE Style is stored as a display string
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.05 | NOT DONE | CODE Keyboard can cover the live preview
Partial: IME inset added. Focus-triggered bringIntoView not implemented.

### ES-1.06 | DONE | CODE Preview text is not saved across rotation
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.07 | DONE | CODE Preview send action just clears the text
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.08 | DONE | CODE Picker opens on accent
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.09 | DONE | CODE No way back to theme default
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.10 | DONE | CODE Snackbar ignores system bars
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.11 | DONE | CODE Snackbar has no undo
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.12 | NOT DONE | CODE Entry style radio list has 24 equal choices
Partial: searchable lazy Apply gallery, stable keys and family labels added. No horizontal mini-preview gallery or family chips.

### ES-1.13 | NOT DONE | CODE Names reference other products
Partial: visible registry labels are neutral descriptive names. Rights/trademark review was not performed.

### ES-1.14 | DONE | CODE Prefix "Conversation" repeated
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.15 | DONE | CODE Subtitle repeats the title
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.16 | NOT DONE | CODE Selected state is hidden in a subtitle line
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-1.17 | NOT DONE | CODE Six colours with overlapping meanings
Colour and style controls remain in one card; requested distinct grouping not completed.

### ES-1.18 | NOT DONE | CODE Send glyph on custom circle
Partial: warning uses the actual onAccent glyph against send fill. The report describes sendButtonColor as glyph, but ComposerColors confirms it is a fill. No glyph-colour preference was invented.

### ES-1.19 | DONE | CODE Text entry colour vs field background
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.20 | DONE | CODE Icons colour vs bar background
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.21 | ALREADY DONE | CODE Preview font ignores scale
Real ChatEntryBar preview receives theme config and ComposerTextField already applies theme.fontScale. System 200% visual validation pending.

### ES-1.22 | NOT DONE | CODE Preview card has no accessibility summary
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-1.23 | DONE | CODE Colour rows are swatch-only
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-1.24 | ALREADY DONE | PROPOSAL Touch targets in the composer
Production ComposerActionButton is already size(48.dp); obsolete 44dp renderer is now commented out. Device overlap audit pending.

### ES-1.25 | ALREADY DONE | PROPOSAL Mic circle 44dp
Production ComposerActionButton is already size(48.dp); obsolete 44dp renderer is now commented out. Device overlap audit pending.

### ES-1.26 | NOT DONE | PROPOSAL Text size 15sp
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-1.27 | NOT DONE | PROPOSAL Placeholder contrast
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-1.28 | NOT DONE | PROPOSAL Preview in light and dark
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-1.29 | NOT DONE | PROPOSAL Section headers and spacing
Colour and style controls remain in one card; requested distinct grouping not completed.

### ES-1.30 | NOT DONE | PROPOSAL Title bar
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-1.31 | NOT DONE | PROPOSAL Preview card padding
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-1.32 | ALREADY DONE | PROPOSAL Writes read fresh state
Original Entry screen already used fresh preferences.conversation.copy. New page-local helper preserves fresh reads, but controller-wide update API is not added.

### ES-1.33 | NOT DONE | PROPOSAL Backup and theme export
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-1.34 | BLOCKED | PROPOSAL Baseline profile and open time
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### ES-1.35 | NOT DONE | PROPOSAL Whole-screen recomposition
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-2.01 | NOT DONE | PROPOSAL ViewModel and UiState
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-2.02 | NOT DONE | PROPOSAL Move snackbar into a one-shot event
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-2.03 | DONE | PROPOSAL Stable style ids and registry
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-2.04 | NOT DONE | PROPOSAL Typed colour pref
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-2.05 | DONE | PROPOSAL Shared contrast helper
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-2.06 | NOT DONE | PROPOSAL Gallery component
Partial: searchable lazy Apply gallery, stable keys and family labels added. No horizontal mini-preview gallery or family chips.

### ES-2.07 | NOT DONE | PROPOSAL Preview thumbnail cost
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-2.08 | NOT DONE | PROPOSAL Search and group by family
Partial: searchable lazy Apply gallery, stable keys and family labels added. No horizontal mini-preview gallery or family chips.

### ES-2.09 | DONE | PROPOSAL Edge-to-edge insets
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-2.10 | NOT DONE | PROPOSAL Compose previews
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-2.11 | NOT DONE | PROPOSAL Haptics
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-3.01 | NOT DONE | PROPOSAL WhatsApp and Telegram offer no style picker for the composer
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-3.02 | DONE | PROPOSAL Instagram and Messenger show the camera next to the field
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-3.03 | DONE | PROPOSAL iOS style composer
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-3.04 | NOT DONE | PROPOSAL Telegram attach inside field
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-3.05 | NOT DONE | PROPOSAL OPT-IN Signal compact
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-3.06 | NOT DONE | PROPOSAL Send button visibility
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-3.07 | NOT DONE | PROPOSAL OPT-IN Floating bar
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-4.01 | BLOCKED | PROPOSAL RTL composer
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### ES-4.02 | BLOCKED | PROPOSAL Long multi-line input
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### ES-4.03 | BLOCKED | PROPOSAL IME with split keyboards
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### ES-4.04 | BLOCKED | PROPOSAL Font scale 200%
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### ES-4.05 | BLOCKED | PROPOSAL Dark and light
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### ES-4.06 | DONE | PROPOSAL Light picks for icon colours
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-4.07 | DONE | PROPOSAL Text colour equals field background
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-4.08 | DONE | PROPOSAL Unknown stored style
Local code written: archived dead renderer, 31-id registry with legacy reads, seven supported layouts exposed, saveable text, truthful preview, reset/effective colours, undo, warnings or insets. Registry round-trip/fallback tests pass; UI uncompiled.

### ES-4.09 | ALREADY DONE | PROPOSAL Rapid style changes
Existing snackbar dismiss-before-show pattern retained in new undo flow. Rapid-tap device test not run.

### ES-4.10 | NOT DONE | PROPOSAL Rotation with dialog open
Partial: new gallery saves open/query/draft key using rememberSaveable. Rotation test not run.

### ES-4.11 | BLOCKED | PROPOSAL Cutout and gesture bar
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### ES-4.12 | BLOCKED | PROPOSAL Hardware keyboard
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### ES-4.13 | BLOCKED | PROPOSAL TalkBack on composer
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### ES-4.14 | BLOCKED | PROPOSAL Low-end device
Requires runtime device/emulator verification (layout, rotation, keyboard, accessibility, theme, perf or corrupted persisted state). No Android SDK/emulator available. No test result invented.

### ES-5.01 | NOT DONE | PROPOSAL Test every style name has a layout
Partial: Reference 2 has its own production renderer, and all 31 registry names map to known layouts by source inspection. No render/distinctness test written.

### ES-5.02 | BLOCKED | PROPOSAL Screenshot test for all 24 styles
Needs Android/Compose toolchain, instrumented device, performance runner or forbidden CI changes. No Android SDK/emulator available; CI/workflows were explicitly out of scope.

### ES-5.03 | NOT DONE | PROPOSAL Compose test: selecting a style updates the preview
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-5.04 | NOT DONE | PROPOSAL Migration test for style ids
Stable registry legacy/id round-trip JVM tests pass; no instrumented preference migration test was run or added.

### ES-5.05 | NOT DONE | PROPOSAL Remove dead code check
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### ES-5.06 | NOT DONE | PROPOSAL Rollout and rollback
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.


## Chat Detail and Composer

### CHA-01 | DONE | Capture submitted draft: combined logic review
Captured room/account/session and draft revision; failed send restores only unchanged blank input, otherwise keeps a separate recoverable draft. No automatic retry; timeout ambiguity disclosed. Pure decision tests pass; callback UI unverified.

### CHA-02 | NOT DONE | Compound state ownership: combined security review
Partial: draft/reply/edit/selection/menu/translation/search states keyed by room/account/session; stale completion UI gated. Host uses an account composition key already. Full suspended send/session-switch harness not run.

### CHA-03 | DONE | Precompile handles: combined performance review
Mention patterns are compiled once per handle-set change and reused across messages; no worker-thread or incremental indexing introduced without measurement.

### CHA-04 | NOT DONE | Tap parity: combined accessibility review
Partial: disabled actions now expose disabled semantics and cannot advertise click/long-click. Full semantic/pointer/keyboard parity audit not run.

### CHA-05 | NOT DONE | Type-scale proposal: combined ui review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-06 | BLOCKED | Contract trace: combined logic review
Plaintext host onSend accepts only String while secure repo supports reply fields. No verified plaintext reply server contract; did not invent metadata keys or backend changes.

### CHA-07 | NOT DONE | Single-action parity: combined security review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-08 | NOT DONE | Single search projection: combined performance review
Partial: both search paths share one pure policy but still invoke it separately. No single memoized projection/benchmark.

### CHA-09 | NOT DONE | Retry action semantics: combined accessibility review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-10 | NOT DONE | Tiny viewport: combined ui review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-11 | DONE | Versioned edit recovery: combined logic review
Edit recovery propagates cancellation, checks captured owner and revision, preserves newer draft, and only restores old edit into untouched input. Pure recovery decision tests pass; UI not exercised.

### CHA-12 | ALREADY DONE | Capability suspension: combined security review
Existing special send modes capture authenticated owner and compare snapshots. Existing code retained; suspension harness not run.

### CHA-13 | NOT DONE | Original draft policy: combined logic review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-14 | NOT DONE | Partial history: combined logic review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-15 | NOT DONE | Absent initial ID: combined logic review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-16 | DONE | Prepend history: combined logic review
Replaced size delta with identity/order arrival policy. Tests cover history prepend, equal-size replacement, append, duplicate/blank IDs and initial hydration.

### CHA-17 | NOT DONE | Pending target lifetime: combined logic review
Partial: missing target retained while loading; loaded-history absence gets explicit snackbar. No full pagination capability to prove target unavailable or delayed highlight handoff.

### CHA-18 | DONE | Placeholder truth: combined logic review
Commented out synthesized task/due-date fallback. Missing task remains null; tap explains unavailable data and mutation routes stay null-safe. Deleted-vs-unloaded distinction remains unavailable from current source.

### CHA-19 | NOT DONE | Supported command audit: combined logic review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-20 | DONE | Protected text filtering: combined logic review
Pure protected search excludes deleted/revoked/system/view-once and malformed view-once flags, uses Locale.ROOT, deduplicates hits, clamps counters and labels scope as loaded history. Four isolated tests pass.

### CHA-21 | NOT DONE | Unified eligibility: combined security review
Partial: fresh room/message/blank-id and registry eligibility rechecks before actions added. Callback/service permission boundary, mixed races and edit policy still require integration audit.

### CHA-22 | NOT DONE | Provider disclosure: combined security review
Partial: provider/text disclosure plus explicit Translate acceptance and fresh eligibility checks added. End-to-end translateOption restrictions were not traced/verified.

### CHA-23 | NOT DONE | Recording alternative proposal: combined accessibility review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-24 | NOT DONE | Menu reply parity: combined accessibility review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-25 | NOT DONE | Day boundary: combined ui review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-26 | NOT DONE | Theme fixture sweep: combined ui review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-27 | NOT DONE | Spoken state audit: combined accessibility review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-28 | NOT DONE | Recipient invariant: combined ui review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-29 | NOT DONE | No infinite announcements: combined accessibility review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-30 | NOT DONE | Counter format: combined ui review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-31 | NOT DONE | Selection count: combined accessibility review
Partial: selection count gets stateDescription; existing cancel/back retained. Focus/custom action audit not run.

### CHA-32 | NOT DONE | Action grouping: combined ui review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-33 | NOT DONE | Sensitive clip metadata: combined security review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-34 | NOT DONE | Safe language lookup: combined security review
Partial: unknown language safely falls back and selected language is shown before request. Account-vs-device preference scope was not changed or labelled.

### CHA-35 | NOT DONE | Eligibility at share: combined security review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-36 | NOT DONE | Payload audit: combined security review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-37 | NOT DONE | Receipt interpretation: combined security review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-38 | NOT DONE | Callback capability: combined security review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-39 | NOT DONE | Commit thread audit: combined logic review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-40 | NOT DONE | State retention bound: combined performance review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-41 | NOT DONE | Immutable input contract: combined performance review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-42 | NOT DONE | Duplicate fixture: combined logic review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-43 | NOT DONE | Variant semantics matrix: combined accessibility review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-44 | DONE | Cancellation propagation: combined performance review
Translation propagates CancellationException, resets result per attempt, uses deliberate retry and checks current eligibility before/after provider call. No live provider requests were made.

### CHA-45 | NOT DONE | Pane-width boundary: combined ui review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-46 | NOT DONE | Blank draft: combined logic review
Partial: typing DisposableEffect now keys session/account as well as room; virtual-clock/lifecycle failure tests not run.

### CHA-47 | NOT DONE | Trigger grammar: combined ui review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-48 | NOT DONE | Banner role: combined accessibility review
Partial: banners gain polite liveRegion; no full urgency/focus/wrapping audit.

### CHA-49 | NOT DONE | Wallpaper contrast: combined ui review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-50 | NOT DONE | Scope wording: combined logic review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-51 | NOT DONE | Message grouping: combined accessibility review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.

### CHA-52 | NOT DONE | Fake-source harness: combined performance review
Not implemented in this conservative local patch. This proposal/verification remains open; no optional setting, model migration, backend capability or successful test is implied.
