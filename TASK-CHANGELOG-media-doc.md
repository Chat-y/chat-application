# Media Review + Document/PDF Workspace task changelog

Branch: task-media-review-doc (local only, never pushed). Base: 3c67bf51 + owner cumulative media patch.
Every entry below is SOURCE-ONLY: written and reviewed, never compiled (no Android SDK/toolchain in this
environment) and never run on a device or emulator. "Unverified" means exactly that.

## Pass 1 (commit 6ec6328a, 47d1f770) - Media review/trim/editor core - UNVERIFIED
- MediaReviewScreen.kt: per-item view-once + per-item captions keyed by stableId (MR-R1-003 partial,
  apply-to-all shortcut kept; MR-R1-058, MR-R2-010); HorizontalPager preview with reduce-motion gating
  (MR-R1-049, MR-R2-037/R4-037); video mute via audio-free copy at send (MR-R1-051); EXIF/location strip
  default-on for unedited photos (MR-R2-015; no user-facing off toggle); location chip (MR-R4-024);
  format preservation PNG/WebP with flatten warning (MR-R1-004); 100 MP decode guard; StatFs preflight;
  30-item/500 MB batch cap (MR-R1-022); door validation of unreadable picks (MR-R2-011); content://
  staging to app cache (MR-R2-020/R4-022); 24 h stale-derivative sweep (MR-R1-016); output root guard;
  post-edit type re-sniff (MR-R1-021); caption byte limit with honest trim (MR-R4-017); large/long video
  expectation chip (MR-R4-018); empty-state BackHandler fix (MR-R1-001); undo-on-remove; discard prompt;
  view-once honesty note.
- MediaTrimScreen.kt / MediaTrimCopy.kt: filmstrip (10 bounded frames, 2 retrievers); scan-for-duration
  when metadata lies; ENOENT parent re-create; spherical-metadata + keyframe-shift warnings; negative-CTS
  clamp; 1 s retry rate-limit; rememberSaveable trim range; single mm:ss.cs formatter.
- ImageEditorScreen.kt (pass 1 only; ownership later moved to the media owner worker - do not merge this
  file from my branch): blur/redaction tool (MR-R1-023); pen width/opacity; stroke-only redo (MR-R4-009
  partial); draft resume; tool modes with 48dp wells and mode announcements.

## Pass 2 (commit b1baf14e) - Document reader + PDF workspace honesty - UNVERIFIED
- DocumentReaderScreen.kt: search match scroll-into-view (DOC-R1-004); 2 MB bounded text preview with
  truncation banner (DOC-R1-015); BOM-aware decode UTF-8/16, UTF-32 refused (DOC-R2-070); binary sniff
  refusal (DOC-R2-069); zero-page PDF state (DOC-R2-067); missing-file state offers only Back to Library
  (DOC-R2-109).
- PdfWorkspaceScreen.kt: success sheet shows PdfRenderer-validated page count (DOC-R3-156) and announces
  completion once (DOC-R4-183).
- MediaWorkspaceScreen.kt + FileSanitizationService.kt: cleanup result names scope (chaty_media, >24 h)
  and freed bytes via CleanupReport.freedBytes (DOC-R4-167).

## Pass 3 (commit 77eecada) - Media review UX round - UNVERIFIED
- MediaReviewScreen.kt: keyboard/DPAD (Left/Right page, Ctrl+Enter send, Delete remove) (MR-R2-042);
  swipe-down dismiss with spring-back (MR-R2-044; VideoView consumes its own drags); header chrome
  auto-hide on video pages with tap restore (MR-R3-013); reorder dialog with 48dp up/down buttons
  (MR-R5-030); strip collection semantics (MR-R4-035) and focus ring (MR-R4-038); PlainTooltips
  (MR-R3-031); caption row on tonal Surface (MR-R3-016); letterSpacing on small labels (MR-R3-029);
  HEIC/AVIF version gating (MR-R5-016); motion-photo still-frame warning (MR-R5-010); superseded-edit
  deletion on IO dispatcher (MR-R4-033); send logic extracted to attemptSend(); design-rule kdoc block
  (MR-R4-049, R5-041, R5-048, R5-034, R3-030, R2-043, R3-011, R5-047, R5-033, R4-031, R3-017, R3-050).

## Pass 4 (commit cd863115) - PDF service + reader states - UNVERIFIED
- PdfImageEncoder.kt: streaming buildTo() writes object-by-object to the pending file, one raster in
  memory at a time (DOC-R1-018/R3-134/R3-136); EXIF orientation applied once as page /Rotate with swapped
  fit math for 90/270 (DOC-R3-117/R3-147); buffered build() kept for tests.
- PdfProcessingService.kt: images-to-PDF via streaming path; text-to-PDF title from
  ConversionRequest.options["sourceTitle"] (DOC-R3-120); A4 documented as stated default (DOC-R3-115).
- DocumentReaderScreen.kt: encrypted-PDF state (DOC-R2-062); CRLF/CR normalization (DOC-R5-218); search
  policy kdoc (DOC-R2-072/074/075/076, R5-220); rememberSaveable anchors (DOC-R2-107/R5-252); heading on
  filename (DOC-R4-171); match position+snippet announcement (DOC-R4-172); settled page announcement
  (DOC-R4-173); jump dialog label (DOC-R4-174); audio route-out branch (DOC-R1-060).

## Pass 5 (commit ca25572c) - Workspace honesty round - UNVERIFIED
- FileSanitizationService.kt: estimateExpiredTempBytes() dry-run for the confirmation dialog.
- MediaWorkspaceScreen.kt: cleanup confirmation with scope/exclusions/estimate, no undo promise
  (DOC-R4-201/R1-052/R4-202); single-snapshot totals+breakdown (DOC-R4-168); refresh disables while
  scanning (DOC-R4-187); storage card labeled as Chaty usage not device capacity (DOC-R4-210); empty
  state names first action (DOC-R4-209).
- PdfWorkspaceScreen.kt: cards gated while processing (DOC-R3-132); Open PDF rechecks output existence
  (DOC-R3-158); honest "Plain text (.txt/.md) to PDF" subtitle (DOC-R1-038); sourceTitle query from
  picker display name.

## Pass 6 (commit ef0b1982) - Media review interaction round - UNVERIFIED
- MediaReviewScreen.kt: long-press drag-to-reorder on the strip (MR-R4-001); preview custom
  accessibility actions Edit/Remove (MR-R3-045); one-time Edit/Trim coach mark on second open
  (MR-R4-042); process-death draft for captions + view-once with 30 min expiry, cleared on
  send/discard (MR-R5-002); send errors follow the failure template (MR-R3-044); startEdit()
  shared by header and custom action.

## Pass 7 (commit bef0804f) - Landscape two-pane layout - UNVERIFIED
- MediaReviewScreen.kt: at 600dp+ width, Row { preview (weight) | 320dp scrolling controls
  column }; stacked layout unchanged below (MR-R3-037). Layout split into local pagerContent /
  controlsContent composables.

## Pass 8 (workspace rows, reader comfort) - UNVERIFIED
- MediaWorkspaceScreen.kt: list header relabeled "RECENTLY MODIFIED FILES" to match the
  modifiedAt sort (DOC-R4-161); studio cards stack below 340dp (DOC-R4-194/DOC-R5-256);
  row metadata raised to 12sp with explicit type label, relative modified time and visible
  audio queue scope (DOC-R1-046/DOC-R4-169/DOC-R4-206/DOC-R4-162); audio rows show a play
  affordance with an action-specific label (DOC-R4-208); ActionCard subtitle at 12sp.
- DocumentReaderScreen.kt: reading-size setting Small/Normal/Large in the overflow menu for
  text files, 16sp default baseline (DOC-R2-103); reading width capped at 600dp and centered
  (DOC-R4-191); details dialog prioritizes name/type/size and moves Path last (DOC-R2-108);
  InfoRow stacks label above value so large fonts wrap (DOC-R1-048).

## Pass 9 (PDF tools, reader details, semantics) - UNVERIFIED
- PdfWorkspaceScreen.kt: conversion cards stack below 340dp so subtitles wrap without clipping
  (DOC-R4-196/DOC-R5-256); ToolCard subtitle at 12sp (DOC-R1-046); ToolCard merges into one
  button node for screen readers (DOC-R4-181).
- MediaWorkspaceScreen.kt: ActionCard merges into one button node (DOC-R4-181).
- DocumentReaderScreen.kt: video branch no longer autoplays (DOC-R2-106); details dialog
  buttons stack below 340dp for 200% text (DOC-R4-195/DOC-R5-246).

## Pass 10 (review decode + editability source) - UNVERIFIED
- MediaReviewImage.kt: Coil decode bounded by the actual preview viewport, capped at 2048 px
  (MR-R4-030).
- MediaReviewScreen.kt: isCopyEditable() is now the single source of truth for which review
  types get copy-edit actions, used by the header Edit button and the pager custom action
  (MR-R3-047).

## Pass 11 (PDF text extraction escapes) - UNVERIFIED (algorithm validated in a Python port, 12 cases)
- PdfProcessingService.kt: extractTextFromPdf now scans literal strings honoring nested
  balanced parentheses and backslash escapes (escaped parens, n/r/t/b/f, octal, line
  continuations) instead of a regex that truncated at the first ')' (DOC-R5-211/DOC-R5-212);
  hex strings shown by Tj decode byte-wise behind a printable-ratio guard (DOC-R5-213
  partial; ToUnicode font mapping DOC-R5-214 stays out of scope). Old regex kept commented.

## Pass 12 (playback error surface, recognizable names) - UNVERIFIED
- MediaWorkspaceScreen.kt: playTrack failures surface a snackbar instead of vanishing into
  the coroutine (DOC-R4-163).
- PdfWorkspaceScreen.kt: generated PDFs get timestamped, recognizable names with a short id
  suffix against same-minute collisions (DOC-R3-153).

## Pass 13 (stale descriptors, named hand-offs) - UNVERIFIED
- MediaWorkspaceScreen.kt: tapping a row whose file vanished says so and refreshes the
  library instead of failing silently (DOC-R4-164).
- DocumentReaderScreen.kt: share/open actions name the document being handed off in their
  accessible labels (DOC-R4-179).

## Pass 14 (share MIME, error redaction, row action labels) - UNVERIFIED
- DocumentReaderScreen.kt: unknown MIME falls back to application/octet-stream, never */*
  (DOC-R2-086).
- PdfProcessingService.kt: extraction failures report only the category; parser internals no
  longer escape in the exception chain (DOC-R2-090).
- MediaWorkspaceScreen.kt: each library row exposes an explicit action label - "Play <name>"
  for audio, "Open <name>" otherwise (DOC-R4-188).

## Pass 15 (shared library parity fixes + tests) - UNVERIFIED
- MediaLibraryService.kt (shared, assigned by parent from the media worker's findings):
  directory-scan header reads now loop until the buffer fills or EOF (a single read() could
  short-read and misclassify); the broad cache listing also excludes unsent chaty_capture_ /
  chaty_picked_ staging. Both additive; legacy lines kept commented.
- PdfProcessingService.kt: scanTjStrings widened private -> internal for test pinning.
- app/src/test: MediaReviewTrimFormatTest (table-driven formatClipTime pinning, MR-R5-037 /
  MR-R5-038) and PdfTextScannerTest (12 scanner cases, DOC-R5-211/212/213). Authored but
  NOT executed - no toolchain in this environment.

## Source review corrections after pass 15 - UNVERIFIED
- DocumentReaderScreen.kt: add missing widthIn and semantics imports found during source review.
- DocumentReaderScreen.kt / MediaWorkspaceScreen.kt: correct escaped interpolation so labels
  show the actual filename rather than a literal placeholder.
- PdfProcessingService.kt: remove the IllegalStateException passthrough so extraction errors
  consistently have no private cause chain.

## Pass 16 (source review safety fixes) - UNVERIFIED
- MediaWorkspaceScreen.kt: add missing mutableStateOf import.
- MediaReviewScreen.kt: avoid clamping selection against an empty range before the empty-state
  branch. Draft restoration completes before persistence starts, file operations serialize,
  and send/discard deletion completes before navigation. Removes the local-function forward
  reference. These are source-reviewed corrections only; lifecycle behavior needs device tests.

## Pass 17 (JPEG metadata strip) - UNVERIFIED (algorithm validated in a Python port)
- PdfImageEncoder.kt: generated PDFs no longer embed camera metadata - APP1 (EXIF/XMP),
  APP13 (IPTC) and COM segments are stripped before /DCTDecode embedding; pixel data, JFIF,
  Adobe, ICC (APP2) and coding tables stay byte-identical, entropy stream untouched. EXIF
  orientation is read before stripping, so page /Rotate is unaffected. PNGs were already
  metadata-free by construction (DOC-R3-129, policy = strip).
- app/src/test: PdfJpegMetadataStripTest (synthetic JPEG: segment removal, JFIF/ICC survival,
  scan-data identity, no-metadata passthrough, end-to-end build contains no GPS bytes).
  Authored, NOT executed.

## Pass 18 (canonical write containment) - UNVERIFIED
- PdfProcessingService.kt: both PDF write paths now require canonical destination containment
  via FileSanitizationService.validateSafePath before creating any output (DOC-R3-126 /
  DOC-R5-227).

## Review pass 19 (self-review corrections) - UNVERIFIED
- DocumentReaderScreen.kt: scrollToFocusedMatch now derives line height from the reading-size
  setting instead of a hardcoded 14sp*1.6, so match focus lands correctly at Small/Large.

## Pass 20 (storage breakdown consumption) - UNVERIFIED
- MediaLibraryService.kt: media-worker delta applied verbatim (MediaStorageBreakdown with
  isComplete/scanCompleted/unavailableDirectories; overflow-safe sums; worker's test file
  MediaStorageBreakdownTest, unexecuted).
- MediaWorkspaceScreen.kt: total and breakdown again come from one service snapshot;
  "Measuring storage..." before the first scan completes and "Some folders could not be read.
  These totals are partial." when the scan was incomplete (DOC-R4-205). StorageOverviewCard
  gained measured/complete params with defaults.

## Pass 21 (review corrections + worker delta rebase) - UNVERIFIED
- MediaTrimScreen.kt: self-review found the duration/rotation probe still read the
  composition-time effectiveSource, so after staging a remote/provider source the retriever
  re-opened the ORIGINAL remote URI instead of the staged local copy; it now reads the
  staging state inside the coroutine (MR-R4-021/022 staging is actually honored).
- MediaTrimScreen.kt: added the missing androidx.compose.foundation.lazy.items import the
  filmstrip LazyRow uses (would not have compiled without it).
- MediaLibraryService.kt / MediaServicesParityTest.kt / MediaStorageBreakdownTest.kt:
  media-worker delta v4 applied verbatim, replacing v1/v2/v3 (2782c987 and successors,
  all superseded) - debug demo descriptors excluded from storage totals, parity test
  fixture updated, and a canceled refresh no longer relabels stale totals as a completed
  scan (storageScanCompleted resets at refresh start).
  API unchanged (sizes/unavailableDirectories/scanCompleted/isComplete); the pass 20
  MediaWorkspaceScreen wiring was re-confirmed against it by source read.
- Ledger: DOC-R4-166 and DOC-R5-230 stay NOT DONE - playback lease hooks landed as
  process-local MediaPlaybackFileLeases/isProtected (media worker pass30), and a cleanup
  must prove file ownership AND honor isProtected; that full protocol is integrator-held.
