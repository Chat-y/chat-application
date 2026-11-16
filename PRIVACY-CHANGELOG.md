# Privacy implementation change log

## Checkpoint 4 - 15 November 2026, 21:22 IST

Source-only work. No builds, test runs, APKs, commits, pushes or pipelines in this checkpoint.

- `ui/settings/security/PatternLockPad.kt`: bound selection haptics to one per 30 ms using monotonic elapsed realtime; selecting nodes is not delayed. Source inspected only. Device haptic timing unverified. Source entry SC4-25.
- `ui/settings/security/PatternLockPad.kt`: reuse a remembered Path and reset it before each trace draw. Framework-owned paint remains unchanged. Source inspected only; rendering/performance unverified. Source entry SC4-26.
- `ui/settings/security/PatternLockPad.kt`: key the pointer handler to the pad state, so replacing pad state cannot retain the old gesture target. Source inspected only; interaction unverified.

## Earlier delivered checkpoints - retrospective inventory

These entries describe the previously delivered patches. They do not claim new changes or repeat their tests. Verification at the 20:26 checkpoint: 29 JVM tests including 83 core checks and the standalone setup-flow suite passed, 25 changed Kotlin sources parsed. Full Android compilation did not finish; no Android UI, device, lifecycle, screenshot or accessibility verification. No APK or remote writes.

- `core/preferences/ChatyPreferencesController.kt`: read-only account scope generation exposed. Separate shared-file patch owned by coordinator.
- `domain/models/PreferenceGroups.kt`, `PrivacyPublicationPolicy.kt`, `ui/settings/privacy/PrivacyOptionsLogic.kt`: restore omitted privacy serialization and canonical/legacy label mappings. JVM tests passed at prior checkpoint.
- `ui/settings/privacy/PrivacyCenterScreen.kt`: local-only copy/reset, async generation checks, per-operation errors/busy state, presence mapping, accessibility and existing consumer wiring. Android UI unverified. Frozen after 20:24 and handed to another owner.
- `ui/settings/HideMediaScreen.kt`, `MediaVisibilityMarkers.kt`: independent private media markers, truthful existing/exported-copy limits, confirmation/Undo and semantics. Filesystem fixtures passed; Android integration unverified. Frozen after 20:24 and handed to another owner.
- `data/services/LocalLockService.kt`, `security/CredentialStrength.kt`: independent secret-discovery throttle, background PBKDF2, conservative new enrollment rules without invalidating old credentials, native-auth cancellation. Prior JVM tests passed; real biometric/device behavior unverified.
- `security/LockCredentialSetupFlow.kt`, `LockSetupServiceAdapter.kt`, `LockCredentialSetupSheet.kt`, `SecurityFlowPlan.kt`, `SecurityFlowRunner.kt`: setup/verification boundary and busy/error handling where present in patch. Prior setup-flow/JVM tests passed; Compose interactions unverified. Unchanged files are not new patch content.
- `security/SecurityCenterScreen.kt`, `LockMethodSelectorSheet.kt`: honest factor boundaries, secret setup verification and matching masked phrase confirmation, native-method availability and correct password copy. Source/syntax checked; Android UI unverified.
- `security/AppLockController.kt`, `AppLockOverlay.kt`, `PatternLockPad.kt`: cooldown refresh, clearing busy/secret input in finally, safe latest callbacks, PIN semantics/LTR and responsive scroll treatment. Controller JVM tests passed; Android rendering/interactions unverified.
- `permissions/SystemPermissionsScreen.kt`, `AndroidManifest.xml`: first-request versus blocked states, approximate/selected media states, callback refresh, rationale/settings explanation, special access guidance, bounded consented location reads and local test notification guard. Source/syntax checked; Android behavior unverified.
- `vault/HiddenVaultService.kt`, `HiddenVaultController.kt`, `HiddenVaultScreen.kt`: disable implicit/default unlock, preserve explicit legacy decoys, new salted decoys, owner generation, durable-first serialized registry changes, idempotent confirmed removal, lifecycle locking and semantics. Prior service/controller fixtures passed; Android lifecycle/platform/rendering unverified.
- `LocalLockServiceFullTest.kt`, `PreferenceGroupsParityTest.kt`, `PrivacySecurityVaultRegressionTest.kt`, `security/AppLockControllerTest.kt`, `security/LockCredentialSetupFlowTest.kt`: focused source-bound regression cases. Earlier results only, not rerun after the source-only restriction.

Paths above are under `app/src/main/java/com/example/chat/`, `app/src/main/kotlin/com/example/chat/ui/` or `app/src/test/java/com/example/chat/` as appropriate. The Android manifest is `app/src/main/AndroidManifest.xml`. Per-entry ledger carries unresolved and blocked source IDs; this log is not a declaration that all PDF items are complete.

## Checkpoint 5 - 15 November 2026, 21:23 IST

- `ui/settings/security/AppLockOverlay.kt`: key controller/password/setup state to account generation and selected method; use current unlock callback and recheck owner/method before forwarding success. Dispose replaced/removed controller. Source reviewed only; account-switch/platform lifecycle acceptance unverified.
- `ui/settings/security/AppLockController.kt`: invalidation rejects new authentication requests and late successful responses after disposal, clearing visible secret state. Source reviewed only; no build/tests run.
- `ui/settings/security/AppLockOverlay.kt`: replace font-dependent Unicode lock illustration with existing vector lock icon and suppress decorative semantics (SC5-09). Source reviewed only; pixels/TalkBack unverified.
- `ui/settings/security/AppLockControllerTest.kt`: add delayed-native-success-after-disposal regression case. Added but NOT RUN under source-only instruction.

## Checkpoint 6 - 15 November 2026, 21:24 IST

- `ui/settings/permissions/SystemPermissionsScreen.kt`: sequence overlapping asynchronous capability refreshes; an older completion cannot overwrite newer permission status. Loading is set/cleared for actual refresh work and suspended completions are invalidated on STOP/disposal. Source inspected and syntax parsed only; lifecycle/adapter behavior unverified.
- Same file: handle permission launcher failures at the shared request boundary, including rationale-confirmation requests. Release the location pending-action guard if no launcher callback can arrive. Show redacted error copy instead of throwing from the dialog callback. Source inspected only; failure injection unverified.
- Same file: route app-settings links through the existing guarded intent launcher, so unavailable settings handlers show fallback instructions. Source inspected only; OEM settings navigation unverified.
- Same file: refresh control exposes Checking/Ready state description. Source inspected only; TalkBack unverified.

## Checkpoint 7 - 15 November 2026, 21:25 IST

- `data/services/LocalLockService.kt`: propagate CancellationException from credential/phrase presence, attempt/cooldown and native-capability reads instead of translating cancellation into absent credentials or zero attempts. Ordinary platform/storage errors retain existing fallback behavior. Source reviewed only; cancellation tests not executed.
- `ui/settings/security/AppLockOverlay.kt`: use named fingerprint/backspace vectors and button roles, suppress decorative keypad letter semantics, announce digit count politely, mark title as heading, add explicit pattern alternative hint, and enforce 64dp minimum key height. Source/syntax only; TalkBack, touch geometry and rendered UI unverified. Source entries SC1-37/38/40.
- `ui/settings/security/SecurityCenterScreen.kt`: use existing accent theme token for Enable Lock and Test Lock rather than warning/error severity color. Source/syntax only; contrast/pixels unverified. Source entry SC1-48.

## Checkpoint 8 - 15 November 2026, 22:16 IST

- `ui/settings/security/LockCredentialSetupFlow.kt`: prevent setup actions before the credential-presence read succeeds; initialization failures stay closed with retry guidance. Clear resets initialization and advances a generation; late verification/native success cannot advance or complete an invalidated flow. Drop the retained new-secret string after a successful save. Source inspected only; delayed-storage/native regression and type compilation unverified. Already-started storage writes cannot be undone by UI invalidation.
- `ui/settings/security/LockCredentialSetupSheet.kt`: gate input/actions on initialization, accept ASCII PIN digits consistently with service validation, clear visible PIN/password/pattern before reporting saved. Source/syntax only; lifecycle and UI behavior unverified.
- `ui/settings/security/LockSetupOwnershipTest.kt` (test source): add four unexecuted regression cases for pre-init action gating, init error, init invalidation and verification invalidation. No passing test claim.

## Checkpoint 9 - 15 November 2026, 23:17 IST

- `data/services/LocalLockService.kt`, `security/LockSetupServiceAdapter.kt`: setup uses a separate strict credential-presence read that propagates storage errors, rejects partial/malformed verifier pairs and checks owner generation. A failed read no longer becomes an invitation to overwrite an existing credential through setup. Existing non-setup compatibility API is unchanged. Source/syntax only; storage/migration type and runtime checks unverified.
- `security/LockCredentialSetupFlow.kt`: validate ASCII PIN input at the state-machine boundary and reject malformed, duplicate, out-of-range or oversized new pattern strings, clearing stale selections after invalid input. Existing lock-service verification compatibility remains unchanged. Source-only; tests not run.
- `LocalLockServiceFullTest.kt`, `security/LockCredentialSetupFlowTest.kt`: add absent/valid/partial/corrupt verifier cases and update the pattern error expectation. Test sources updated but NOT RUN.

## Checkpoint 10 - 16 November 2026, 00:17 IST

- `permissions/SystemPermissionsScreen.kt`: serialize biometric test taps with a synchronous guard, reread native availability immediately before testing, redact adapter failures and explain that the test changes no permission. Propagate cancellation and clear busy in finally. Source/syntax only; native prompt/device behavior unverified.
- `vault/HiddenVaultScreen.kt`: reject stale PIN-length/authentication UI responses when the vault session owner changes; validate digit callback boundary; show a useful biometric-not-completed message. Source/syntax only; account/lifecycle behavior unverified.
- Same vault file: keep numeric keypad explicitly LTR; bound displayed title/avatar/removal-label input before layout and use a two-line title. Source/syntax only; RTL/font scaling/pixels/accessibility unverified. Original model data is unchanged.

## Checkpoint 11 - 16 November 2026, 01:17 IST

- `data/services/LocalLockService.kt`, `vault/HiddenVaultService.kt`: nested cleanup boundaries zero each allocated verifier array even if the next decode/derivation throws or is cancelled. Clear temporary decoy-PIN character array as well as PBEKeySpec. Existing encoded storage/verifier format unchanged. Source/syntax only; exception/cancellation runtime checks unverified. Kotlin immutable input strings remain non-zeroizable.
- `security/LockMethodSelectorSheet.kt`: make the method list vertically scrollable in constrained windows and reset its availability state when the provider changes. Source/syntax only; small-screen/font-scaling/pixels unverified.

## Checkpoint 12 - 16 November 2026, 02:17 IST

- `security/AppLockController.kt`, `security/AppLockOverlay.kt`: explicitly gate credential UI while capabilities load; thrown reads show a retry path rather than escaping LaunchedEffect or displaying an unproven missing-credential repair action. Native auto-start follows successful capability loading only. Source/syntax only; storage exceptions and lifecycle/UI unverified.
- `security/AppLockOverlay.kt`: clear visible password before dispatching either button or IME verification, including successful verification where the overlay may remain composed. Disabled IME actions do not submit. Source/syntax only; IME behavior unverified.

## Checkpoint 13 - 16 November 2026, 03:18 IST

- `security/AppLockController.kt`: capability loading uses strict verifier-presence validation, so unreadable/partial credential data shows Retry instead of misleading repair setup. Ignore late capability completion and cooldown/delete actions after disposal. Source/syntax only; runtime/type checks unverified.
- `security/LockCredentialSetupFlow.kt`: restrict the new canonical pattern-enrollment syntax to new/confirm steps; current-credential verification retains the existing service's legacy parser. This preserves old verifier encodings instead of imposing new enrollment rules on existing credentials. Source/syntax only; compatibility tests not run.

## Final handoff - 16 November 2026, 04:23 IST

No source changes since checkpoint 13. Packaged cumulative OWNED-FILES patch against exact base 3c67bf51e87c007e37e2195ba714ced988ee87f6. Includes this changelog, security/permissions/vault sources, LocalLockService, two permission manifest additions and focused test sources. Excludes frozen privacy/media implementations, shared preference model/getter patches and Contact privacy. Existing getter and helper foundations are integration prerequisites, not reintroduced ownership.

Final source verification: whitespace check, Kotlin syntax parser and patch apply-check only. No builds or test execution after the source-only restriction. Old 83-check results apply to the earlier 20:26 checkpoint, NOT this final revision. Android compilation, final JVM tests, real permission/biometric lifecycle, rendered UI and accessibility remain unverified.
