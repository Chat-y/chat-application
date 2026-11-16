# CHANGELOG - impl/data (base 3c67bf51). All entries UNVERIFIED (not compiled, no tests, no screenshots).

## Commit 1 - Storage & Network/Data usage
- StorageMediaScreen.kt: 700 MB switch commented out, read-only "Upload size limit" row (ChatMediaService.STORAGE_LIMIT_MB); real values in subtitles; confirm dialogs for clear cache and lowering limit; "Cache in use" row; radio-selectable cache options with Default badge; empty auto-download section replaced; merged "Local storage" section; sentence case. (ST1-01,02,03,06,07,10-15,21,22,25,26,31-35,41,42,46,50,51,54; 04 partial) - unverified
- SettingsSubHost.kt (StorageMedia branch): used-bytes state, clear/trim on IO thread. - unverified
- NetworkDataUsageScreen.kt: both screens in ChatySettingsPage; confirm dialogs for both resets; status/transport/Mbps labels; offline hides speeds; zero rows hidden; filenames sanitised/ellipsised; remember-split lists; keys; heading and merged semantics. (ND1-01,02,03,05,07,09,13,15,16,17,20,22,27,28,31,32,36,41,43,45,53; 04,06,12,38 partial) - unverified
## Commit 2 - Backup/restore
- BackupArchiveLogic.kt: chunk threshold shadow fix (R2-106); duplicate section/manifest rejection (R2-091/092); running decompressed cap (R2-096).
- BackupSectionStores.kt: allowlisted-key writeAll (R3-143); preferences rollback throws on save failure (R3-138); media rollback reports undeleted URIs (R3-131).
- BackupPortableLogic.kt: commit/rollback each distinct store once (R3-111).
- BackupAndRestoreScreen.kt: bounded file read (R2-076); Media rows disabled (R1-006); copy fixes (R1-001 partial, R1-007). - unverified
## Commit 3 - Accounts & Auth
- AccountSettingsScreen.kt: snackbar colours (AC1-01), friendly errors (AC1-03), QR tile commented out (AC1-04), delete ends in sign-out + progress overlay (AC1-10, AC1-40, AC2-13).
- AccountSwitcherOverlay.kt: tab pill selected semantics + 48dp (AC1-08, AC1-31).
- ForgotPasswordScreen.kt: shared email validator (A1-02). SplashScreen.kt: try/catch + 20 s timeout fallback (A1-03). RegisterOrganizationScreen.kt: rule text (A1-04). LoginScreen.kt: rename shadowed var (A1-06). WelcomeScreen.kt: authCopy("Register") (A1-49). - unverified
## Commit 4 - Auth headings (A1-39): ForgotPasswordScreen.kt, CreateNewPasswordScreen.kt - unverified
## Commit 5 (in progress)
- NetworkDataUsageScreen.kt: connection-state icon (ND1-46), direction/outcome icons (ND1-12, ND1-37), "Show all" for recent transfers (ND1-08). MediaCacheSettings.kt: nearest-option migration (ST1-08). - unverified
- AccountRecentsOverlay.kt: labelled Button action on the dismiss backdrop (AC1-33). - unverified
- ForgotPasswordScreen.kt: 60 s resend cooldown using OtpVerificationLogic.cooldownDeadline/cooldownSeconds (A1-19). - unverified
- LoginScreen.kt, RegisterScreen.kt: error-haptics effect keyed by the values, not a new list each recomposition (A1-26). - unverified
