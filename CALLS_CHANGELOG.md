# Calls group - change ledger

Worker: calls-group task agent. Branch: `impl/calls`, base `3c67bf5`.
Scope: Call settings (CS1-CS5), Audio settings (AU1-AU5), Incoming & Ongoing Call (CALL-R1..R5), Calls History & Contact Picker (CALLS-R1..R5), Notification/toast settings (NTF-R1..R5).

Verification status key:
- UNVERIFIED: source edited, brace/paren balance check passed, NOT compiled (no compiler available in this environment).
- All entries below are UNVERIFIED unless noted otherwise.
- Judgment calls are marked [judgment].

## d1c0e3b - Call settings R1 logic/a11y fixes
Files: ui/settings/CallSettingsScreen.kt, ui/calls/CallPresentation.kt
- Count label fix, dialog enum + rememberSaveable, cancel reverts selection, toggleable rows, search field, capability checks, permissions rows, snackbar + undo, copy fixes.

## 7c96947 - SHARED kit: section headings + merged switch-tile semantics (CS4-30/31)
Files: ui/settings/ChatySettingsPrimitives.kt
- Shared component; small separate commit per instruction. Heading semantics on section headers; switch tiles expose one merged toggleable node.

## 78991a4 - Audio settings R1 fixes
Files: ui/settings/AudioSettingsScreen.kt
- Radio roles, theme tokens, route feedback + undo + speaker confirm, friendly device names, honest dataset framing, a11y, copy fixes.

## 12b0a3b - syntax fix
Files: ui/calls/CallPresentation.kt
- Closed CallPresentationPreferences companion with `}` (caught by balance check).

## 963a4e5 - Toast settings NTF R1-R4 fixes
Files: ui/settings/notifications/NotificationToastSettingsScreen.kt
- Positive online switch, elevation switches, debounced radius sliders, contrast guard, sample name, POST_NOTIFICATIONS warning, disclosures, reset action, a11y semantics.

## 2100307 - SHARED kit: GbCardContainer heading + GbSliderTile stateDescription (NTF-R1-038/R2-033)
Files: ui/kit/GbDesignSystem.kt
- Shared component; small separate commit per instruction.

## 95c3a43 - Calls history R1 fixes
Files: ui/calls/CallsScreen.kt
- Redial guard + cooldown, day buckets, locale time, m:ss duration, undo delete, merged a11y rows, group-call label, saveable dialog, centered empty state, copy fixes.

## 8d10003 - remember avatar color parse (CALLS-R1-28)
Files: ui/calls/CallAvatar.kt

## 3548dd7 - Contact picker / dialpad R1 fixes
Files: ui/calls/CallContactPickerScreen.kt, ui/calls/DialerContactMatching.kt
- Exact-match dial, neutral copy, guard window, T9 precompute, cached IPC reads, theme-token green, a11y keys/spinner/backspace, digit-limit hint, dead voicemail glyph commented out.

## 415c599 - picker/history honesty + a11y
Files: ui/calls/CallContactPickerScreen.kt, ui/calls/CallsScreen.kt
- Honest retry copy (R3-13), mode-toggle stateDescription + dialpad live region (R4-03/05), dead import cleanup (R5-46).

## 862f422 - syntax fix
Files: ui/calls/CallContactPickerScreen.kt
- Merged dialpad live-region into TextField modifier (duplicate argument; caught by balance check).

## 16bbae7 - Incoming/Ongoing call CALL R1 fixes
Files: ui/calls/CallThemeRoles.kt, InAppCallPip.kt, IncomingCallScreen.kt, OngoingCallScreen.kt, bundles/BundleGroupMeetingView.kt, bundles/BundleIncomingCallView.kt
- PiP param honored, single end-call path, h:mm:ss duration, 5s busy banner, null-session race, non-static backdrop local, EGL release + scaling, remembered control list, live-region status, neutral security copy, RoundAction semantics, dead drag handle commented out, tap-to-answer disabled, raw-id line dropped.

## 2fa39fc - dead bundle files annotated (CALL-R1-08)
Files: bundles/BundleOngoingCallView.kt, bundles/BundleCompactCallCard.kt
- Marked as dead code with fake buttons; commented/annotated per rule (no deletion). Both files were already UNCOMPILED ports at baseline.

## 9bcc3e4 - calls R1 pass 2
Files: CallAvatar.kt, CallThemeRoles.kt, InAppCallPip.kt, IncomingCallScreen.kt, OngoingCallScreen.kt, bundles/BundleCompactCallCard.kt, BundleGroupMeetingView.kt, BundleIncomingCallView.kt, BundleOngoingCallView.kt
- One callInitials rule everywhere (R1-07), remembered bundle tokens (R1-28), hidden panels leave semantics tree (R1-38).

## 73584d9 - calls R1 pass 3
Files: OngoingCallScreen.kt, bundles/BundleCompactCallCard.kt, BundleGroupMeetingView.kt, BundleIncomingCallView.kt, CallExperienceBundlePickerScreen.kt
- Control cells role+state (R1-39), share banner (R1-18), preview action labels (R1-48), card-tap previews (R1-57), Applied-is-state (R1-58), 48dp mini actions (R1-45), dim inert concept controls (R1-54).

## 31b1876 - calls R1 pass 4
Files: bundles/BundleIncomingCallView.kt
- One-way puck Decline a11y action (R1-43), ring pulse sleeps when lifecycle not RESUMED (R1-27).

## d9e9e61 - calls R1 pass 5 (design-light)
Files: OngoingCallScreen.kt, bundles/BundleIncomingCallView.kt
- End-call 56dp floor (R1-51), preview PiP 16dp inset clamp + snap-to-edge (R1-52), animated chevron swipe hint replacing static glyph (R1-59).

## da45965 - calls R1 pass 6 (design-light)
Files: OngoingCallScreen.kt, bundles/CallExperienceBundlePickerScreen.kt
- Header scrim over iOS voice gradient (R1-53), preview tabs regrouped 9 -> 3 groups + sub-chips (R1-56).

## Hard-coded color audit (full diff scan)
- Two `Color.White` lines are carry-overs of pre-existing avatar-initials text color, moved into the shared `callInitials` call sites unchanged. [judgment]
- Preview-sheet chip colors use `Color.White` to match the surrounding preview sheet, whose pre-existing style is hard-coded white throughout. [judgment]
- `Color(0xFF11161D)` in the avatar contrast helper is a computed contrast color against arbitrary dynamic avatar colors; a fixed theme token cannot express it. [judgment]
- No other hard-coded colors introduced; all other new UI uses theme tokens.

## Known pre-existing issues (not introduced by this branch)
- BundleOngoingCallView.kt and BundleCompactCallCard.kt carry baseline "UNCOMPILED (Compose)" port headers with pre-existing compile errors; kept commented/annotated per rule.
- CALL-R1-10 bundle id/displayName swap intentionally untouched per standing instruction.

## Overnight review pass 1 (verification, no new features)
- Balance sweep re-run over all 19 touched files: OK.
- Hard-coded color audit over full diff: 4 lines, all judgment calls documented above.
- Cross-file symbol checks: callInitials, VoiceCallBackdrop, bundleTokens, themedCallBundleTokens, CallBundleThemeTokens/AppColors fields, NotificationToastPreferences fields, ChatyHapticsPort.selection, icons - all resolve.
- Shared-kit commits 7c96947 / 2100307 confirmed additive-only (no signature changes); GbSliderTile unit/suffix valid; ChatySwitchTile inner switch is clearAndSetSemantics so no double announcement.
- FIXED 2 real compile errors in CallSettingsScreen.kt (new commit, UNVERIFIED):
  1. descriptionBuilder lambda returned null in one branch; kit type is (T) -> String. Now returns "" (repo pattern, GbKit.kt:151; blank descriptions are not rendered).
  2. listSaver<Set<String>, String> type mismatch (its save lambda takes List<Original>). Replaced with Saver<Set<String>, ArrayList<String>>.

## Overnight review pass 2 (verification, no new features)
- FIXED: duplicate `import androidx.compose.ui.semantics.stateDescription` in OngoingCallScreen.kt (harmless but untidy; new commit).
- Full diff re-reads, all clean: 963a4e5 NotificationToastSettingsScreen (toast derived getters, GbSettingRow/GbSwitchTile/GbSliderTile/GbLivePreviewCard/pickers signatures, NotificationToastPreferences() constructible default, pickers.Host()); 16bbae7 incoming/ongoing (SurfaceViewRenderer.release(), RendererCommon scaling enums, session-null early return + smart cast, setupError/pictureInPictureEnabled params, onError token); 9bcc3e4 (AppColors type, remember imports); 73584d9 (ScreenShare import, previewBundleId/pendingBundle states, ControlIconButton call shape unchanged); 31b1876 (VerticalDragPuck onTap has a default; lifecycle observer add/remove symmetric).

## Overnight review pass 3 (tidy + one real correction)
- FIXED: removed orphaned SpeakerPhone import in CallSettingsScreen.kt (its tile was replaced by Audio & Speakers).
- CORRECTION (revert): CALL-R1-25 had added renderer.release() after detachRenderer() in 3 onRelease lambdas; detachRenderer() already calls renderer.release() internally (WebRtcCallMediaController.kt:657), so the extra call was a redundant double-release. Reverted to detach-only with a comment. The setScalingType additions from the same item stay.
- Sweeps clean: no added-but-unused imports anywhere in the cumulative diff; no leftover AlertDialog alias refs; no conflict markers or TODO/FIXME introduced; final balance sweep 19/19 files OK.

## FINAL INVENTORY (closing delivery, 4:39 AM IST Nov 16)
Branch impl/calls: 22 commits over base 3c67bf5. 19 files changed, +1392/-434.
- 18 feature/fix commits across the 5 assigned screen groups (CS, AU, NTF, CALLS history+picker, CALL incoming/ongoing), incl. 2 shared-kit commits flagged separately (7c96947 ChatySettingsPrimitives, 2100307 GbDesignSystem).
- 4 overnight review fixes: b5afb36 (descriptionBuilder null->"", listSaver->Saver+ArrayList), f81d560 (duplicate import), b301295 (orphaned SpeakerPhone import), 6ba076a (revert redundant renderer.release()).
- Light re-checks at 11:13 PM and 3:15 AM: tree clean, nothing drifted.
- Final sweeps: balance 19/19 files OK; no added-but-unused imports; no conflict markers/TODO/FIXME.
- STATUS: every entry UNVERIFIED (no compiler available); verified only by manual re-read + mechanical sweeps.
- Caveats: nothing pushed, no PR, nothing on bandi-new; CALL-R1-10 bundle id/displayName swap untouched per standing instruction; BundleOngoingCallView/BundleCompactCallCard annotated as dead code, not deleted; *.md ledger not committed (repo .gitignore), delivered as attachment.
