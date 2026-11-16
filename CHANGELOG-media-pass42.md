# Media implementation changelog

All entries are local/uncommitted. No entry is Android runtime or visual signoff. Status labels in the ledger mean source implementation only. Dates use Asia/Kolkata. Historical verification is not re-used as proof of later code. No builds or compiler invocations are allowed after the 2026-11-15 21:21 instruction.

## 2026-11-15, passes 1-2, delivered before overnight work

- Capture safety/accessibility: timer cancellation, zoom ceiling reset, bounded gallery import, camera announcements/scrim/options. Files: CameraCaptureScreen.kt, CameraCaptureWidgets.kt, CameraOptionsPanel.kt, CameraZoomControls.kt, LensCarousel.kt. Verification: parser-only scan; Android unverified.
- Local review/editor/trim: staged ownership, source validation, bounded previews, remove/Undo, captions, editor transforms/alpha, trim guards. Files: MediaReviewScreen.kt, MediaReviewImage.kt, ImageEditorScreen.kt, MediaTrimScreen.kt, MediaTrimCopy.kt. Verification: parser-only scan; Android UI/CameraX unverified. Ownership of Review and Trim later handed off; no newer changes here.
- Private viewer/audio: local FileProvider exports, hide-media gallery confirmation, playback/a11y/queue changes. Files: MediaViewerScreen.kt, ChatyAudioPlayerScreen.kt, MediaStaging.kt, MediaCopy.kt. Verification: historical core fixtures only; Android unverified. Viewer/audio later handed off, unchanged here since pass 2.
- Document/PDF/Studio: native reader, safer conversion/PNG/JPEG encoding, sanitation. Files: DocumentReaderScreen.kt, PdfWorkspaceScreen.kt, MediaWorkspaceScreen.kt, PdfProcessingService.kt, PdfImageEncoder.kt, FileSanitizationService.kt. Verification: 21 historical isolated JVM tests by end of pass 2; PDF fixture pixels inspected in pass 1. Android unverified. Screens later handed off, unchanged here since pass 2.
- Tests: MediaSafetyRegressionTest.kt, PdfProcessingParityTest.kt, ImageEditorPixelsTest.kt. Verification: isolated JVM subset executed before source-only overnight instruction; Android instrumentation not run.

## 2026-11-15, passes 3-5, delivered before overnight work

- Camera one-handoff photo guard/caption snapshot, paused/shutter states, clamped focus feedback, video timer visibility, bounded recent thumbnails, malformed-entry safe pane, overlay Back order and explicit ownership docs. Files: CameraCaptureScreen.kt, CameraCaptureWidgets.kt, CameraOptionsPanel.kt, CameraRecentStrip.kt. Status: source written, Android/visual unverified.
- Camera countdown and named frame-budget policy. Files: CameraCaptureTiming.kt, CameraZoomPolicy.kt, CameraCaptureTimingTest.kt. Status: core tests historically run, Android unverified.
- Editor 40dp ink wells in 48dp named targets and numeric adjustment labels. File: ImageEditorScreen.kt. Status: source written, Android/visual unverified.
- Picker sort/count/filter context/read-only details/deselect Undo. Files: CustomFilePickerScreen.kt, MediaBrowseOrdering.kt, MediaBrowseOrderingTest.kt. Status: core sort tests historically run; Android/visual unverified.
- Picker scheme guard, short-read headers, surrogate-safe bounds, saturating totals. Files: PickerInputPolicy.kt, PickerInputPolicyTest.kt, CustomFilePickerScreen.kt. Status: core tests historically run; Android/provider integration unverified.
- Picker layout-specific bounded previews and normal filename typography; removed composition-time exists check. File: CustomFilePickerScreen.kt. Status: source/parser-only, no visual acceptance.
- Camera TalkBack/Switch Access scripts. File: docs/media-camera-accessibility-checks.md. Status: document written, scripts NOT RUN.
- Historical verification at pass 5: 38 isolated JVM tests passed before the no-build instruction; parser/whitespace/apply checks passed. These results do not verify later changes.

## 2026-11-15 21:22, pass 6, source-only overnight checkpoint

- Exclusive hidden import copies, counted bytes, both stream-close success required, typed privacy-safe read/permission/storage/size errors, rollback before success. File: PickerImportCopy.kt (new). Status: source written, NOT COMPILED, tests NOT RUN.
- Batch classification before promotion, safe named item failure, neutral source hint, once-per-session copy explanation, persistent outcome/recovery notice, cancel -> Stopping -> cleaned Canceled state. File: CustomFilePickerScreen.kt. Status: source written, NOT COMPILED, Android/provider/UI/visual unverified. Existing all-or-nothing new-batch behavior retained; no new batch count/byte quota chosen.
- Import fixtures for null stream, permission, close failure, quota, empty bytes, cancellation and exclusive same-source outputs. File: PickerImportCopyTest.kt (new). Status: test source written, NOT RUN.
- Per-entry progress reasons/status and this per-change changelog. Files: delivered CSV ledger/delta and docs/CHANGELOG-media.md. Status: generated and text-inspected; no runtime claim.

Open caveats: promotion uses individual same-directory renames, not a whole-batch atomic transaction; active-file cache leases/startup sweep still absent. Source-only work is not production readiness. No commits, pushes, CI, APKs or full app builds have been made by this branch.

## 2026-11-15 21:24, pass 7, source-only editor checkpoint

- Per-stroke Fine/Medium/Thick width and opacity controls use the same bounded width/alpha policy in preview and export, retaining default constructor behavior. File: ImageEditorScreen.kt. Status: source written, NOT COMPILED, Android/visual unverified.
- Source-entry display-matched decode cap, explicit Apply reentry guard, and Draw/Text mode announcements. File: ImageEditorScreen.kt. Status: source written, NOT COMPILED, device/TalkBack unverified.
- Brush defaults, export-width coverage and half-opacity blend fixtures. File: EditorBrushPixelsTest.kt (new). Status: instrumentation test source only, NOT RUN.
- HSV hue-strip/saturation/brightness ink palette with five recent custom colors, named controls, explicit Use action and theme-colored chrome. File: ImageEditorScreen.kt. Status: source written, NOT COMPILED, visual/TalkBack unverified. Spectrum values are drawing colors, not hard-coded app chrome.
- Bounded scrollable editor tool pane keeps added palette/brush controls from consuming unbounded vertical space beside the weighted preview. File: ImageEditorScreen.kt. Status: source written, NOT COMPILED, small-window/large-text pixels unverified.

## 2026-11-15 21:26, pass 8, source-only adjustment checkpoint

- Added saturation/warmth/tint/fade controls behind progressive Adjust disclosure and shared preview/export color policy, retaining old renderer defaults/signature compatibility. Files: ImageEditorScreen.kt, EditorAdjustmentPolicy.kt (new). Status: source written, NOT COMPILED, pixel/UI/TalkBack unverified.
- Identity/grayscale/color-channel/alpha/finite-input matrix fixtures. File: EditorAdjustmentPolicyTest.kt (new). Status: test source only, NOT RUN.

## 2026-11-15 21:27, pass 9, source-only camera audit and corrections

- Included HDR in the bounded extension-failure plain-photo retry; previously only night/portrait retried at bind failure. File: CameraCaptureScreen.kt. Status: source written, NOT COMPILED, CameraX unverified.
- Published analyzer-fallback capability to a lens-session unavailable notice and cleared stale face solve state. File: CameraCaptureScreen.kt. Status: source written, NOT COMPILED, visual/device unverified.
- Found a redundant 45ms face gate still present despite the earlier shared-budget refactor; changed it to the 66ms named policy. Moved native face model setup/detect off main (only bitmap conversion had been off main). File: CameraCaptureScreen.kt. Status: source written, NOT COMPILED/profiled. This corrects the earlier broader policy-refactor description.
- Source-only ALREADY DONE audit for unsaved-video prompts, video lease/invalidation, recent-photo revocation, segment ownership transfer, executor/bridge disposal, analyzer backpressure and actionable camera failure copy. Files inspected: CameraCaptureScreen.kt, CameraZoomPolicy.kt, SegmentFrameSlot.kt, FaceDetectionBridge.kt, SegmentationBridge.kt. Status: source inspected; no runtime/race/leak/AT pass claimed. Entry IDs/reasons are in the ledger.

## 2026-11-15 21:28, pass 10, source-only face-worker lifecycle follow-up

- Source review of the pass-9 worker move found the existing eager model disposal could now race native detect/model creation. Added a synchronized full-operation busy/disposed gate and deferred once-only close after active work returns; cancellation now propagates from bridge and screen. Files: FaceDetectionBridge.kt, CameraCaptureScreen.kt. Status: source written, NOT COMPILED, native/concurrency tests NOT RUN. This is a safety follow-up, not proof of race freedom.

## 2026-11-15 21:30, pass 11, source-only camera notice cleanup

- Renamed scannerError to transientNotice throughout camera capture; original declaration retained as a comment. Added a cancellable 4-second notice lifetime, extended by Android accessibility timeout recommendations; sticky noCamera errors remain separate and are not auto-dismissed. File: CameraCaptureScreen.kt. Status: source written, NOT COMPILED, timing/TalkBack unverified.
- Camera transient notices now have a theme surface/content pair and polite live-region semantics instead of unbacked white text over the preview. File: CameraCaptureScreen.kt. Status: source written, NOT COMPILED, actual contrast/layout/pixels unverified.
- Inspection caught that the existing noCamera pane had only a gallery action, despite the review's Retry wording. Added an explicit Retry with a separate gallery fallback, preserving mode/options and blocking retry during recording/paused states; failed video sessions are closed before retry. Camera-in-use/init-timeout copy now names the actual Retry action. Files: CameraCaptureScreen.kt, CameraCaptureWidgets.kt. Status: source written, NOT COMPILED, device conflict/retry flow unverified.
- Audited direct aspect choices: existing panel already offers direct 1:1, 4:3, 16:9 choices plus Full; no cycle and no need to remove Full. File inspected: CameraOptionsPanel.kt. Status: source-only ALREADY DONE; no visual/device acceptance.
- Retained obsolete AtomicBoolean import as a comment after the pass-10 synchronized gate change. File: FaceDetectionBridge.kt. Status: source cleanup only, NOT COMPILED.

## 2026-11-15 21:32, pass 12, source-only drawing and camera privacy guards

- Replaced the editor's per-pointer-move outer stroke list rebuild with a snapshot state list and per-index stroke replacement; adapted add/Undo/Reset/rotate/crop and took a stroke snapshot before IO export. Point lists remain immutable copies; no claim of allocation-free drawing. File: ImageEditorScreen.kt. Status: source written, NOT COMPILED/profiled, drawing/crop/export pixels unverified.
- Added debug startup duplicate-lens-ID validation plus unique/duplicate source fixtures. Files: LensManager.kt, CameraLensIdsTest.kt (new). Status: source written, NOT COMPILED; tests NOT RUN.
- Removed exception message text from the zoom-failure debug diagnostic, retaining only exception class and numerical/cancellation state. File: CameraCaptureScreen.kt. Status: source log scan completed, device log audit NOT RUN.
- Added camera privacy notes describing local bridges and Send/Post handoff without promising upload success or unverified 24-hour crash cleanup. File: docs/media-camera-privacy.md (new). Status: text/source reviewed; no network/device/privacy audit run.

## 2026-11-15 21:33, pass 13, source-only picker labels and focus

- Picker query uses Search IME action; clearing a query returns focus to the search input. Result count already has a polite live region. File: CustomFilePickerScreen.kt. Status: source written, NOT COMPILED; keyboard/TalkBack/focus behavior unverified.
- Grid/list main actions now distinguish Open from Select/Deselect, with filename-first merged name/type/size semantics. Details remains a separate action. File: CustomFilePickerScreen.kt. Status: source written, NOT COMPILED; accessibility traversal and actual pixels unverified.
- Added mode-specific task cue, persistent MIME type in grid, MIME and separate suffix in summary cards, count-bearing Send/Share labels, and a polite scanning status. File: CustomFilePickerScreen.kt. Status: source written, NOT COMPILED; narrow-screen/large-text/translation/AT unverified. No send callback or destination change.

## 2026-11-15 21:34, pass 14, source-only editor source/export isolation

- Keyed save job, active flag, save indicator and disposal to the source so replacing the edited item cancels the old save. Captured all render parameters into an immutable main-thread export snapshot. File: ImageEditorScreen.kt. Status: source written, NOT COMPILED; source-swap/cancellation/race behavior unverified.
- Moved source/output alias validation inside the guarded IO export block, expanded canonical comparison to raw local paths, and withheld failure cleanup until the output is known not to be the original. Previously canonical-path failure could bypass finally and raw local aliases were not covered. File: ImageEditorScreen.kt. Status: source written, NOT COMPILED; storage/provider integration unverified.
- Added file-URI/raw-path/canonical-segment/distinct-copy/content/remote alias fixtures. File: EditorSourceAliasTest.kt (new). Status: instrumentation test source only, NOT RUN.

## 2026-11-15 21:35, pass 15, source-only multiline text overlays

- Added up to four explicit text lines within the existing 120-unit bound, surrogate-safe truncation, left/center/right alignment and optional translucent text strip; Reset clears the new styles. Files: ImageEditorScreen.kt, EditorTextLayout.kt (new). Status: source written, NOT COMPILED; IME/large-text/visual behavior unverified. Strip colors are image artwork, not hard-coded chrome.
- Preview and export call the same native text renderer, fitting the full text block within image bounds and capturing alignment/strip with the export snapshot. Files: ImageEditorScreen.kt, EditorTextLayout.kt. Status: source written, NOT COMPILED; actual preview/export pixels unverified.
- Added line/surrogate bounds, all alignment/strip preview/export parity and very-short-panorama fixtures. File: EditorTextLayoutTest.kt (new). Status: instrumentation test source only, NOT RUN. Shared core ImageTextOverlayPolicy and helper-owned screens were not changed.

## 2026-11-15 21:37, pass 16, source-only opt-in saved-selfie mirror

- Added a front-camera-only Mirror saved selfies panel toggle, default off to preserve existing behavior; no assumed default preference or persisted preference change. Files: CameraCaptureOptions.kt, CameraOptionsPanel.kt, CameraCaptureScreen.kt. Status: source written, NOT COMPILED; device/preview/layout unverified.
- Capture snapshots front/back identity at shutter time. Photo output mirrors oriented/cropped image pixels only for front captures with the explicit toggle, before a readable watermark. Original stays unchanged; unchanged/default/back output behavior retained. Files: CameraCaptureScreen.kt, CameraPhotoOutput.kt. Status: source written, NOT COMPILED; EXIF/lens/mirror device pixels unverified.
- Added default-off/back-no-op/front-opt-in/original-preservation source fixtures. File: CameraSelfieMirrorTest.kt (new). Status: instrumentation test source only, NOT RUN.

## 2026-11-15 21:38, pass 17, source-only face-bake freshness

- Track successful face observations on monotonic elapsed time; reject a completed detector result after camera-facing or session/reset generation changes. Clear timestamp/poses on new photo binds and timestamp on face-solver resets. File: CameraCaptureScreen.kt. Status: source written, NOT COMPILED; detector/lens/rotation races unverified.
- At shutter time snapshot lens, preview geometry and poses only when the last observed face is within 500ms; callback no longer reads a later mutable selection/pose. Empty/stale solve keeps plain capture. Files: CameraCaptureScreen.kt, CameraCaptureTiming.kt. Status: source written, NOT COMPILED; real moving-face/bake pixels unverified.
- Added boundary/missing/future/invalid timestamp source fixtures. File: CameraFaceFreshnessTest.kt (new). Status: test source only, NOT RUN.

## 2026-11-15 21:39, pass 18, source-only honest stroke redo

- Added bounded (20-entry) stroke redo beside explicitly named Undo stroke; new drawing and geometric/reset operations invalidate redo to prevent old-coordinate reapplication. File: ImageEditorScreen.kt. Status: source written, NOT COMPILED; gesture/history/UI behavior unverified.
- Full crop/rotate/adjust command history remains NOT DONE. This partial stroke history does not store 20 full bitmaps or claim global undo/redo. Files: ImageEditorScreen.kt, delivered per-entry ledger reasons. Status: source review only, no runtime or memory profile.

## 2026-11-15 21:40, pass 19, source-only bounded file-to-file lens bake

- Camera capture now file-decodes the lens copy on IO and writes JPEG directly, avoiding full source readBytes, intermediate PNG bytes/file and second decode; tracked bitmaps are released in finally and incomplete output is deleted. Files: LensCaptureBake.kt (new), CameraCaptureScreen.kt. Status: source written, NOT COMPILED; memory/cancellation/native painter/device pixels unverified. Legacy byte-array API remains unchanged for other callers.
- Lens output explicitly caps its longest side at 2048px, checks a conservative decode memory budget before allocation, applies EXIF orientation, and preserves the source on failure with a visible notice. A visible lens-copy size note avoids hiding this quality tradeoff. Files: LensCaptureBake.kt, CameraCaptureScreen.kt. Status: source written, NOT COMPILED; EXIF/front/mirror/geometry/quality pixels unverified. Previous lens path already downsized width to 2048; portrait long-edge cap is now explicit.
- Added long-edge/output-count, EXIF/original-preservation and output-registration cancellation rollback fixtures. File: CameraLensBakeTest.kt (new). Status: instrumentation test source only, NOT RUN.

## 2026-11-15 21:42, pass 20, source-only HDR exclusion clarity

- Scoped the requested photo-session HDR flag to photo/lenses with neither Night nor Portrait requested; native bind already enforces mutually exclusive extensions. Updated recovery notice to distinguish a mode conflict from unsupported HDR hardware. File: CameraCaptureScreen.kt. Status: source written, NOT COMPILED; vendor extension/bind transition behavior unverified.
- Added options-panel explanation that Night/Portrait replace HDR, and clarified that the 2048px lens copy note concerns saved overlays (plain fallback is not promised to be resized). Files: CameraOptionsPanel.kt, CameraCaptureScreen.kt. Status: source written, NOT COMPILED; visual/device behavior unverified.

## 2026-11-15 21:44, pass 21, source-only failure/cancellation follow-up

- Lens file helper now also deletes a just-produced output when cancellation wins the dispatcher return to its caller, beyond the worker's own finally cleanup. File: LensCaptureBake.kt. Status: source written, NOT COMPILED; dispatch-race and process-death behavior unverified.
- Guarded capture temp-file creation and synchronous takePicture failure so the capturing flag resets and private partial output is removed; native asynchronous failure uses path-free recovery copy. File: CameraCaptureScreen.kt. Status: source written, NOT COMPILED; storage/executor/CameraX failure injection unrun. This is not a complete free-space preflight or a guarantee of OOM recovery.
- Face bridge now closes its MPImage and releases owned frame bitmaps after synchronous IMAGE-mode detect/landmark extraction, including failure; removed the redundant bitmap-only dispatcher boundary that could lose an allocated result on cancellation. File: FaceDetectionBridge.kt. Status: source written, NOT COMPILED, native/memory/concurrency unverified. API ownership reference: https://developers.google.com/edge/api/mediapipe/java/com/google/mediapipe/framework/image/MPImage (close decrements the owned reference) and https://developers.google.com/edge/api/mediapipe/java/com/google/mediapipe/framework/image/BitmapImageBuilder (bitmap must not change while wrapped).

## 2026-11-15 21:45, pass 22, source-only state/support documentation

- Added binding-phase transition Kdoc that separates staged/sheet/video guards and makes missing transition tests explicit. File: CameraCaptureScreen.kt. Status: source review only, NOT COMPILED; transition tests NOT RUN, CAM-R4-001 remains incomplete.
- Added a camera cache-exposure response checklist without claiming unverified 24-hour cleanup, and a behavior-change record checklist naming defaults/opt-ins/verification. File: docs/media-camera-support.md (new). Status: text/source reviewed; no real incident asserted or device/security test run.
- Recorded error-copy pattern and known source examples while explicitly leaving full content/translation and screen-off verification incomplete. File: docs/media-camera-support.md. Status: text/source reviewed; no device behavior claim.

## 2026-11-15 21:47, pass 23, source-only segmented-preview lifecycle

- Moved segmented-preview control/state cleanup back to main with only native rendering on Default; capture the rendered bitmap inside the worker so dispatch cancellation retains cleanup ownership. Stale effect/intensity/camera-generation/front-back results are rejected; throttle now uses monotonic time. File: CameraCaptureScreen.kt. Status: source written, NOT COMPILED; native/Compose/cancellation races and pixels unverified.
- Added a synchronized segmentation work/disposed gate with deferred model close, matching the face bridge's lifecycle rule; new operations after disposal are rejected. File: SegmentationBridge.kt. Status: source written, NOT COMPILED; native thread/concurrency tests NOT RUN.
- Live segmentation now releases its image/mask and tracked composite intermediates in finally when segmentation or compositing fails/cancels, instead of success-only cleanup. Input identities are excluded from intermediate cleanup. File: LiveSegmentPreview.kt. Status: source written, NOT COMPILED; native memory/RenderThread verification NOT RUN. MPImage input ownership remains unchanged because the segmentation caller still needs its input bitmap for the composite.

## 2026-11-15 21:48, pass 24, source-only front-lens transform and error privacy

- Front-camera pose coordinates originate in mirrored preview space; the file lens bake now inverts that horizontal preview transform when painting onto upright sensor pixels. The explicit saved-selfie option still mirrors the completed image and overlay together afterward. Files: LensCaptureBake.kt, CameraCaptureScreen.kt. Status: source written, NOT COMPILED; real front-camera/EXIF/pose/artwork mapping pixels unverified. This corrects a source-level front-lens transform gap found during follow-up, not a tested parity claim.
- Editor load/save errors no longer display raw exception messages that could contain provider paths or signed URLs; recovery copy is generic and source data stays private. File: ImageEditorScreen.kt. Status: source written, NOT COMPILED; exception-injection/UI behavior unverified.

## 2026-11-15 21:49, pass 25, source-only filename spoofing display guard

- Picker display labels replace hidden directional controls with a visible replacement marker and normalize control whitespace; original filename/path is not renamed. Main filename paragraphs use content-based text direction, and MIME type/suffix remain separately shown. Files: PickerInputPolicy.kt, CustomFilePickerScreen.kt. Status: source written, NOT COMPILED; Arabic/Hebrew/mixed-script/TalkBack/visual verification unrun.
- Added directional-control/control-whitespace/mixed-script/zero-width-joiner source fixtures. File: PickerInputPolicyTest.kt. Status: test source only, NOT RUN. A full RTL release fixture remains NOT DONE.

## 2026-11-15 21:50, pass 26, source-only selection snapshot and scroll continuity

- Confirmation snapshots requested paths in existing library order, reports/removes missing selections and requires a second review instead of silently sending fewer files; duplicate selected paths block confirmation pending refresh. Files: PickerSelectionSnapshot.kt (new), CustomFilePickerScreen.kt. Status: source written, NOT COMPILED; refresh/send race and file-content identity remain unverified. Shared service dedup is still its owner's responsibility.
- Summary explicitly describes existing library-order behavior; no new chronological tap-order preference assumed. Grid and list now each remember independent scroll state across layout toggles. File: CustomFilePickerScreen.kt. Status: source written, NOT COMPILED; scroll/restoration/large-text/device behavior unverified.
- Added order/missing/duplicate/immutable-snapshot source fixtures. File: PickerSelectionSnapshotTest.kt (new). Status: test source only, NOT RUN. Full path-replacement/atomic-byte-handoff acceptance remains incomplete.

## 2026-11-15 21:51, pass 27, shared storage signal on helper baseline

- Extracted only MediaLibraryService.kt hunks from the parent's supplied Media Review/Document helper pass15 patch (header-fill loop and capture/picked staging exclusions) as the shared-file baseline. These are the other helper's work, not a second completion claim.
- getStorageBreakdown now returns a Map-compatible MediaStorageBreakdown with sizes/isComplete/scanCompleted/unavailableDirectories. Missing/unreadable roots and scan exceptions are not silently measured as empty; initial/in-progress scan is unknown. Nonnegative totals saturate on overflow. File: MediaLibraryService.kt. Status: source written, NOT COMPILED; real permission/refresh/storage behavior unverified. Workspace caller must consume !isComplete to display partial/unavailable, so DOC-R4-205 is not fully complete here.
- Added pre-scan unknown/missing-root/map-compatibility source fixtures. File: MediaStorageBreakdownTest.kt (new). Status: test source only, NOT RUN. Shared-file delta is delivered separately atop the exact helper pass15 baseline; regular camera/editor/picker deltas exclude MediaLibraryService.kt to avoid duplication.

## 2026-11-15 21:52, pass 28, provider labels, byte-derived suffix and flat-list contract

- Device import rejects separator/NUL provider labels; it derives a bounded safe local suffix from header-only classification rather than the provider suffix, with .bin/octet-stream for unknown bytes and generic .zip for ZIP containers. Sanitized provider label/type evidence stay explicitly unverified details. Files: CustomFilePickerScreen.kt, PickerProviderNamePolicy.kt (new). Status: source written, NOT COMPILED; provider integration/format coverage/hostile inputs unverified. Signature classification is not a malware/full-content validator.
- Added provider separator/spoofed suffix/unknown executable-header/ZIP-container/length source fixtures. File: PickerProviderNamePolicyTest.kt (new). Status: test source only, NOT RUN.
- Documented existing flat descriptor list versus receiving chat album policy and no raw content-URI diagnostic policy; source scan found no logger call in picker. File: docs/media-picker-contract.md (new). Status: text/source reviewed; no crash-report audit or receiver album preview. MR-R4-012 remains incomplete as the actual album preview is absent.

## 2026-11-15 21:52, pass 29, original UUID plus in-memory lineage contract

- Added bounded session-only path-to-source lineage with schema v1 and monotonic sequence; keeps UUID filenames untouched and has no logging, disk sidecar or file-delete behavior. File: MediaCopy.kt. Status: source written, NOT COMPILED; tests NOT RUN. This follows original MR-R4-015, correcting the shorthand request for renamed exports.
- Image editor registers completed exports before host handoff, forgets failed handoffs and clears lineage on source/session disposal. File: ImageEditorScreen.kt. Status: source written, NOT COMPILED; multi-step host/trim propagation is not wired, so MR-R4-015 remains partial NOT DONE.
- Added session bounds/schema/order/clear/forget fixtures without filesystem writes. File: MediaExportLineageTest.kt (new). Status: test source only, NOT RUN.

## 2026-11-15 21:53, pass 30, source-only player lease hooks (cleanup still held)

- Added process-local canonical-path current/queue protection with reference-counted idempotent leases; no ownership inference or delete/rename operations. File: MediaPlaybackFileLeases.kt (new). Status: source written, NOT COMPILED; cleanup owner must still combine isProtected with its explicit ownership check before any deletion. This hook alone does not complete DOC-R4-166/R5-230.
- Coordinator snapshots/pins current plus queue before native transition, serializes play/stop, retains pins for stopped-resumable queue or ambiguous failed/canceled transitions, releases previous pins only after native stop success and all pins after successful disposal. Raw playback exception text is no longer shown. File: MediaPlayerCoordinator.kt. Status: source written, NOT COMPILED; native/cancellation/disposal/policy race fixtures NOT RUN. Held cleanup/MediaStaging not edited.
- Added shared-owner/idempotent/canonical-alias/queued-file source fixtures without filesystem writes. File: MediaPlaybackFileLeasesTest.kt (new). Status: test source only, NOT RUN.

## 2026-11-15 21:54, pass 31, source-only byte-bound follow-up

- Import filename stems now obey a UTF-8 byte budget as well as a UTF-16 length bound, preserving code points and space for the UUID cache prefix. File: PickerProviderNamePolicy.kt. Status: source written, NOT COMPILED; actual provider/filesystem behavior unverified.
- Bounded media copy falls back to a single-byte read after a zero bulk read, with quota/cancellation/progress checks, rather than hot-spinning. File: MediaCopy.kt. Status: source written, NOT COMPILED; provider cancellation/blocking behavior unverified.
- Shared storage signal excludes debug demo descriptors from measured disk totals. File: MediaLibraryService.kt. Status: source written, NOT COMPILED; standalone storage delta updated, UI still needs incomplete handling.
- Added multibyte filename and zero-bulk-copy source fixtures. Files: PickerProviderNamePolicyTest.kt, PickerInputPolicyTest.kt. Status: test source only, NOT RUN.

## 2026-11-15 21:54, pass 32, storage fixture correction

- Updated the old storage test that counted debug demo descriptors as disk usage; it now measures two private PDF fixtures and separately asserts samples do not inflate totals. File: MediaServicesParityTest.kt. Status: source fixture updated, NOT RUN, not compiled. No app runtime behavior changed. This test travels only in the standalone storage delta, not regular media deltas.

## 2026-11-15 21:55, pass 33, source-only late-state privacy follow-up

- Reset the safe import item label before validating each new URI/name so a failed new item cannot inherit the previous document's label in failure UI. File: CustomFilePickerScreen.kt. Status: source written, NOT COMPILED; provider failure injection NOT RUN.
- Closed export-lineage sessions reject late metadata registration after clearing, preventing an in-flight completion from repopulating disposed source metadata; added a late-registration fixture. Files: MediaCopy.kt, MediaExportLineageTest.kt. Status: source written, NOT COMPILED; fixture NOT RUN.

## 2026-11-15 21:56, pass 34, partial reduced-motion controls

- Camera effect scale transitions use zero duration and programmatic mode-strip centering uses an immediate jump under the existing live app motion policy. Original expressions retained in comments. File: CameraCaptureWidgets.kt. Status: source inspected, NOT COMPILED; no UI/pixel/runtime verification.
- Added the remaining reduced-motion audit gaps and toggle checks. File: docs/media-camera-accessibility-checks.md. CAM-R1-045 stays NOT DONE: lens fling/settle and modal-sheet motion remain unaudited, and device checks have not run. No new completion credit.

## 2026-11-15 21:57, pass 35, import source audit

- Disabled the duplicate stateDescription import in CameraCaptureWidgets.kt; retained the duplicate line as a comment. Status: exact-import text scan across changed Kotlin sources; NOT COMPILED. No behavior change or new completion credit.

## 2026-11-15 21:58, pass 36, storage refresh-cancellation signal

- Invalidate completed-scan status at the start of every refresh. Prior sizes can remain as a stale snapshot, but cancellation cannot make them look like a completed latest scan. File: MediaLibraryService.kt. Status: source inspected, NOT COMPILED; no runtime verification. Included only in standalone storage delta v4 (replaces v3).
- Added a cancellation-after-prior-success fixture. File: MediaStorageBreakdownTest.kt. Status: source fixture only, NOT RUN. UI consumer still needs explicit incomplete/stale rendering; no new completion credit.

## 2026-11-15 21:58, pass 37, paper footprint sanity

- Raised accepted convex paper footprint from 2% to the requested 5% of normalized frame area. Detector proposals, manual-corner validity and crop entry share the same threshold; non-finite area is invalid. Files: DocumentQuad.kt, InlineDocumentProcessor.kt. Original threshold retained commented. Status: source inspected, NOT COMPILED; native crop and actual detector scenes NOT VERIFIED.
- Added Move closer/expand-corners guidance for undersized paper and a Move closer hint when no detector proposal survives. File: InlineDocumentReview.kt. Status: source written, no UI/pixel verification.
- Added below/boundary threshold, non-finite and tiny synthetic detector fixtures. File: DocumentQuadTest.kt. Status: source fixtures only, NOT RUN. CAM-R4-024 marked DONE for source implementation only, not device acceptance. Storage v4 remains frozen and unchanged.

## 2026-11-15 22:00, pass 38, camera privacy/staging notes

- Added the requested private-staging standing rule to CameraOwnedFiles Kdoc; no method, deletion, permission or filesystem behavior changed. File: CameraOwnedFiles.kt. Status: source comment inspected. CAM-R4-040 DONE for requested rule note only, not an OS-permissions audit.
- Added camera privacy Kdoc with explicit host delivery ownership and no unsupported 24-hour cleanup promise. File: CameraCaptureScreen.kt. Status: source comment inspected; no network/device audit. CAM-R5-044 stays NOT DONE because the proposed guaranteed cleanup/future in-app page is not implemented.
- Added an unrun airplane-mode smoke script and exact backup-source findings/remaining merged-manifest and OEM limits. File: docs/media-camera-support.md. Status: source/text inspected only; CAM-R4-007 and CAM-R4-041 remain NOT DONE. No builds, tests, compilers or device checks; storage v4 unchanged/frozen.

## 2026-11-15 22:01, pass 39, native intermediate ownership source pass

- Effect bake now releases segmented masks and pixelate intermediates on throwing draw paths, preserving borrowed-input aliases. The segmented bake tracks distinct owned intermediates and releases them in finally, rather than success-only cleanup. File: ImageFxBaker.kt. Original code retained commented. Status: source inspected, NOT COMPILED; allocation/draw failure injection and image pixels NOT VERIFIED.
- Both preview and bake blur release the downscaled intermediate if the upscale throws, without recycling an aliased source or returned image. Files: LiveSegmentPreview.kt, ImageFxBaker.kt. Status: source inspected only; no native memory/device tests or compiler run. Segmentation MPImage/resource ownership remains unresolved and unchanged. No new completion credit, original deletion/cleanup paths not expanded, storage v4 frozen/unchanged.

## 2026-11-15 22:02, pass 40, picker confirmation preflight

- Summary confirmation now checks regular-file/readability and unchanged exact size on IO before main-thread handoff. Changed size blocks the attempt and preserves the review; directory/missing/inaccessible paths are unavailable. Confirmation is revoked if the summary/session changes or closes while IO runs. File: CustomFilePickerScreen.kt; helper in PickerSelectionSnapshot.kt. Original block retained commented. Status: source inspected, NOT COMPILED; dispatcher/UI/provider behavior unverified.
- Added a private fixture covering ready/grown/deleted file preflight. File: PickerSelectionSnapshotTest.kt. Status: source fixture only, NOT RUN. FIL-R5-214/215 stay NOT DONE because their acceptance tests have not run; FIL-R3-115 stays incomplete because same-size/content/type changes and post-stat races are not solved.
- Callback errors keep the selection and ask the person to check the destination before retrying. Documented that this is not a receiver acknowledgement or new cleanup lease. File: docs/media-picker-contract.md. No deletion/overwrite changes, storage v4 frozen/unchanged, no new completion credit.

## 2026-11-15 22:03, pass 41, document-review snapshot and safe errors

- Snapshot the immutable paper quad on Crop before launching IO; explicit busy guard blocks repeated activation and drag input is suspended while cropping. File: InlineDocumentReview.kt. Status: source inspected, NOT COMPILED; rapid-click/drag/dispose and UI behavior NOT VERIFIED.
- Replaced raw read/crop exception messages with local recovery copy so exception paths cannot put a filesystem/provider detail in review text. Original expressions retained commented. Same file. Status: source inspected only; no injected-error/device tests. No new completion credit, no file-deletion behavior changed; storage v4 frozen/unchanged.

## 2026-11-15 22:16, pass 42, stale flash callback guard

- Flash requests now require a live ready photo camera and catch synchronous enableTorch failures. Completion checks request sequence, bind generation, facing, camera identity, live ownership, permission and photo mode before changing flash state or notices. A stale native future cannot write flash mode onto a replacement camera/session. File: CameraCaptureScreen.kt. Original guard/call retained commented. Status: source inspected, NOT COMPILED; rapid-toggle/flip/rebind/dispose/native-failure scenarios NOT RUN. No cycle-order/default changes, no new completion credit, no file cleanup changes; storage v4 frozen/unchanged.
