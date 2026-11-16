# Settings group CHANGELOG (Settings Root / Search / Navigation PDF)
Branch impl/settings, base 3c67bf51, HEAD c026189f. Status for EVERY entry: UNVERIFIED (no compiler, no tests run, no screenshots).
Files: S=SettingsScreen.kt, R=SettingsRootScreen.kt, N=navigation/NavigationDestinationsSettingsScreen.kt, L=navigation/NavigationDestinationsLogic.kt, Q=navigation/SettingsSearchScreen.kt, I=navigation/SettingsIcons.kt, T=new tests under app/src/test/java/com/example/chat/ui/settings/navigation/

| Entry | Change | File | Status |
|---|---|---|---|
| R1-001 | Back button hidden when onBack is null | S | unverified |
| R1-002 | Removed hardcoded "BM"/"Bandi Maya" special cases | S | unverified |
| R1-003 | Export dialog no longer claims success; button disabled "coming soon" (real export still BLOCKED) | S | unverified |
| R1-004 | Confirm dialog before Reset ALL | S | unverified |
| R1-006 | content:// banner URLs passed through | R | unverified |
| R1-007 | Banner width from 720dp-capped container | R | unverified |
| R1-008 | Single reconcile() call | N | unverified |
| R1-009 | Reorder indexes over visible rows only | N | unverified |
| R1-010 | Discard-changes dialog on Back when dirty | N | unverified |
| R1-011 | Blank display name blocked, 50-char cap | S | unverified |
| R1-012 | rememberSaveable search query | Q | unverified |
| R1-013 | About copy no longer claims E2E | S | unverified |
| R1-014 | Canned upload error toast, detail logged | R | unverified |
| R1-016/028 | About version lookup remembered, guarded, API33 flags | R | unverified |
| R1-019 | In-flight guard on avatar/banner upload | R | unverified |
| R1-023 | Canned nav save error text | N | unverified |
| R1-025/026, R3-002 | RootRow uses RootTint enum; groups computed once (lazy) | R | unverified |
| R1-037 | Top inset padding on nav top bar | N | unverified |
| R1-038 | Role.Button on root tiles | R | unverified |
| R1-039 | Avatar content description + Role.Button | R | unverified |
| R1-040 | Export icon QrCodeScanner -> IosShare, label "Export chats" | S | unverified |
| R1-041 | "n of count" stateDescription on nav rows | N | unverified |
| R1-042 | Reorder buttons 48dp | N | unverified |
| R1-043 | Name row 48dp min | S | unverified |
| R1-044 | paneTitle on SettingsScreen, nav, search (root already had it) | S,N,Q | unverified |
| R1-045 | Section/group headers marked heading | S,R | unverified |
| R2-001 | Nav state re-seeds when inputs change | N | unverified |
| R2-003 | SettingsIcons.resolveOrDefault added (callers not rewired) | I | unverified |
| R2-005 | Synonym keywords for 18 search entries | S | unverified |
| R2-006 | ALREADY DONE (sheet state created per open) | S | n/a |
| R2-007 | Theme reset dismisses sheet first | S | unverified |
| R2-008 | MAX_PRIMARY used in strings and enable checks | N | unverified |
| R2-009 | Enums for dialog / pick state | R | unverified |
| R2-011 | Bidi / zero-width chars stripped on profile save (client only) | S | unverified |
| R2-017 | Confirm dialog for Reset Privacy | S | unverified |
| R2-024 | Remembered search filter | Q | unverified |
| R2-026 | Saveable scroll state | S,R | unverified |
| R2-031 | Logout content description | S,R | unverified (claims host confirm exists, unverified) |
| R2-032 | Profile sheet scrolls with IME | S | unverified |
| R2-033 | Nav Reset 48dp + confirm | N | unverified |
| R2-036 | Mood pill is a real button | S | unverified |
| R2-044 | Hardcoded amber -> colors.warning | S | unverified |
| R2-045 | Banner height capped 180dp on wide windows | R | unverified |
| R2-047 | "Clear search" in empty state | Q | unverified |
| R3-005 | keywords field on SettingsSearchEntry, passed through | S,Q | unverified |
| R3-007 | reorder KDoc fixed | L | unverified |
| R3-012 | About text capped 140 with counter | S | unverified |
| R3-022 | SectionHeader/Tile lifted to top level | S | unverified |
| R3-024 | Solid search top bar | Q | unverified |
| R3-031 | Keyboard options on profile fields | S | unverified |
| R3-032 | Sheet paneTitle | S | unverified |
| R3-036 | Banner image decorative | R | unverified |
| R3-042 | Section header start padding 16dp | S | unverified |
| R3-043 | Tasks icon outlined like the others | N | unverified |
| R3-045 | Nav-bar inset in root sheet | R | unverified |
| R3-048 | Visible search field container | Q | unverified |
| R4-003 | Comment says 18 entries | S | unverified |
| R4-004 | NavigationDestinationsLogicTest (7 tests) | T | unverified, not run |
| R4-005 | SettingsSearchLogicTest (6 tests) | T | unverified, not run |
| R4-016 | Removed "Pure private messaging architecture" copy from About tile | S | unverified |
| R4-033 | Empty-search message is a polite live region | Q | unverified |
| R4-035 | Mood text maxLines 2 | S | unverified |
