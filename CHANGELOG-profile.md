# CHANGELOG - profile group (branch impl/profile, base 3c67bf51)
Status key: UNVERIFIED = written source-only, no compiler/tests/emulator available. A syntax-only kotlinc parse found no syntax errors; type-checking is left to CI.
All files under app/src/main/kotlin/com/example/chat/ui/profile/. Commits: a0238a0b, then the follow-up (see patch).

## New file
- ProfileErrorMessages.kt (P1-02/03, P2-16, P3-10, P3-16). Shared error-to-copy mapper. UNVERIFIED.

## ProfileEditScreen.kt
- Error copy via mapper, no raw error.message (P1-02, P2-16, P3-10, P3-16). UNVERIFIED
- Persistent inline save error + polite live region (P2-02). UNVERIFIED
- show(): dismisses current snackbar first (P2-24). UNVERIFIED
- Validation uses validateBio too (P1-54). UNVERIFIED
- Display name NFC-normalised, bidi controls stripped (P2-11, P3-20). UNVERIFIED
- Success copy "Profile updated." (P1-50, P5-20). UNVERIFIED
- Avatar camera button 48dp target / 34dp visual (P1-34, P5-01). UNVERIFIED
- Labels "Edit cover photo", "Edit profile photo" (P1-46, P3-33). UNVERIFIED
- Title/source-sheet title heading(); spinner live region (P1-38, P1-60). UNVERIFIED
- Bio counter live region only at 120/140 (P2-45). UNVERIFIED
- Name capitalization Words, imeAction Next on name/username (P5-21, P5-43). UNVERIFIED
- Source rows Role.Button (P5-32). UNVERIFIED
- Preview fallback text 10sp -> 12sp (P3-31). UNVERIFIED
- BringIntoViewRequester scroll to first invalid field (P5-22). UNVERIFIED
- Haptic on successful save (P3-39). UNVERIFIED

## ProfileScreen.kt
- ChatyAvatar a11yLabel param; "Profile photo of {name}" on headers (P1-35). UNVERIFIED
- Banner buttons 48dp targets (P1-31, P1-32). UNVERIFIED
- heading() on name and section titles (P1-60). UNVERIFIED
- bannerWithAvatar name maxLines 2/ellipsis, username 1 line (P1-57, P2-49). UNVERIFIED
- "Add a bio" prompt when bio empty (P1-47, P5-14). UNVERIFIED
- Tap own avatar opens full photo dialog (P5-29). UNVERIFIED
- Stats label 12sp (P3-31). UNVERIFIED

## ProfileMediaPicker.kt
- PickVisualMedia ImageOnly (P1-06). UNVERIFIED
- 20 MB capped read + "too large" error (P1-15). UNVERIFIED
- Alpha flattened on white at pick and crop (P2-18). UNVERIFIED
- Camera error mapping (P2-07, P5-26). UNVERIFIED
- Zoom slider, Zoom in/out actions, stateDescription (P1-07, P1-36, P5-08, P5-31). UNVERIFIED
- Double-tap resets crop (P1-07). UNVERIFIED
- Dual-tone frame stroke, accent colour remembered (P1-56, P5-19, P4-17, P4-36). UNVERIFIED
- cropW/Hpx remembered (P4-18); memory-bound comment (P4-05). UNVERIFIED
- Haptic on crop apply (P5-28). UNVERIFIED
- Friendly crop-save error (P2-16). UNVERIFIED

## ProfileCameraResultBridge.kt
- Revoke FileProvider grant on result/launch failure (P2-33). UNVERIFIED

## ProfileEditorSheet.kt (no call sites)
- Mapper errors, Save disabled while photo uploads, "Save" label, photo spinner live region (P1-03, P2-09, P1-38, P5-30). UNVERIFIED

## Round 2 (commit e9f4cf22)
- ProfileMediaPicker: double-tap resets crop (P1-07); haptic on crop apply (P5-28); px values remembered (P4-18); memory-bound comment (P4-05). UNVERIFIED
- ProfileEditScreen: BringIntoView on first invalid field (P5-22); haptic on save (P3-39). UNVERIFIED

## Round 3 (latest commit)
- New ProfileMotion.kt: rememberReducedMotion() (system animator scale 0). UNVERIFIED
- ProfileEditScreen: inline error uses animateContentSize, snaps when reduced motion (P3-24); check mark replaces Save after success (P2-44, P5-10). UNVERIFIED
- ProfileScreen: EditPill and SettingsTile rows get Role.Button; stat tiles merged into one "Label: N" node (P5-01 polish). UNVERIFIED
- ProfileMediaPicker: crop title heading(). UNVERIFIED
- New test app/src/test/java/com/example/chat/ProfileErrorMessagesTest.kt (JUnit4). The mapper logic itself was RUN-VERIFIED: compiled with kotlinc 2.0.21 and executed a scratch main with the same cases, all passed. The JUnit file is not run.
