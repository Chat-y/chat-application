# Media follow-up on kotlin c891dc22

Scope: MR-R1-004, the only media row in the supplied master CSV in codable-client-now or needs-review. Base c891dc22ebfc0dcd3f65b6369843f74bfb81c305. No commits, push or CI. Other nine media rows are recorded unchanged/out of this execution scope in the scoped CSV.

## Changes

- EditorOutputFormat.kt (new): one pure output policy keeps PNG and WebP formats (including opaque input), preserves unknown alpha as PNG, and treats a fixed destination suffix as the codec contract. Header signature detection distinguishes PNG/JPEG/WebP. Unsupported fixed suffixes fail before export. Status: isolated Kotlin compilation and six JVM unit tests PASS.
- EditorImageEncoding.kt (new): short 12-byte header read for local/content URI sources on IO; header evidence outranks provider MIME. No extra HTTP request or new upload. Encodes PNG, JPEG, WebP-lossless on API30+ and maximum-quality WebP on older supported Android versions. Status: production helper compiles against Android11 API classes and coroutine library; no native execution.
- ImageEditorScreen.kt: snapshot output policy before starting save, generate matching extension, use the selected codec and alpha-flattening policy. Shows explicit JPEG/white flatten notice, format-preservation notice, and older-WebP recompression notice. Existing source-alias/ownership/rollback behavior unchanged. Original replaced expressions retained as comments. Status: syntax parse PASS, full Android/Compose type-check and UI/pixel verification NOT RUN.
- EditorOutputFormatTest.kt (new): six executed tests cover opaque extensionless/provider PNG/WebP decisions, signatures/truncation, signature-MIME priority over misleading suffix, unknown-alpha fallback, fixed destination codecs and unsupported suffix refusal. Status: six PASS on Kotlin2.0.21/Java11.
- EditorEncodedFormatTest.kt (new): native PNG/WebP signature+alpha roundtrip and extensionless opaque PNG through FileProvider content URI; asserts source bytes unchanged. Status: NOT RUN, syntax parsed only.
- docs/CHANGELOG-media-open-gaps-c891.md (this file): per-file status/limits. No startup/login, backend, secrets, paid, deletion protocol, destination ownership, settings or color-token changes.

## Verification and limits

Both production policy/encoding helpers compile in isolation against Android11 API classes. Six pure policy tests PASS. All five changed/new Kotlin sources parse without syntax diagnostics. This is not compilation of ImageEditorScreen or the full application. Full app compilation is unavailable here: no Gradle wrapper or installed Gradle, no Android SDK/adb/emulator, Java11 only. Native codec/provider fixtures, API24-29 WebP behavior, Compose UI, screenshots, layout/theme/accessibility checks remain NOT RUN. UI wording is source-only and has no visual approval. No claim of zero bugs.

The current base review callback already inspects edited output bytes with FileTypeDetector rather than forcing image/jpeg. This patch deliberately leaves it unchanged. Fixed caller-owned JPEG output is still a JPEG contract, with visible flatten-to-white notice, rather than silently renaming a registry destination. The existing optional-output ownership/overwrite seam is not widened or certified by this format patch. Remote source MIME without a readable local header uses a source suffix/alpha fallback; there is no new unauthenticated remote probe. Unknown alpha stays PNG instead of being silently flattened. Animated GIF/WebP editing remains a still-image operation, not an animation-preservation feature.

## Ledger meaning

DONE for MR-R1-004 means source implementation plus the stated isolated checks, not device acceptance. The master-mapped decision/device-proof rows remain outside this requested bucket filter. The accompanying full historical ledger is an update of a reconstructed snapshot, not a substitute for later integration/helper evidence. Its prior UNRECOVERED categories stay marked except MR-R1-004, whose CODE GAP category is now grounded in the supplied master CSV.
