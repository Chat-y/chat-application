# Released media UI batch 1

Base: kotlin c891dc22ebfc0dcd3f65b6369843f74bfb81c305. Source-only, uncompiled. No build, commit, push or CI.

MR-R3-027: editor Apply now shows Exporting plus a trailing 18dp indeterminate spinner with an 8dp gap while saving. Existing save/reentry/disabled/cancellation/codec/ownership behavior unchanged. Trim already contains an 18dp spinner and Exporting label in this base, so it is not changed. No theme color overrides. Replaced editor label retained as a comment.

New EditorExportButtonContent helper isolates UI content for three new Compose test cases: idle/no spinner, busy/exactly one spinner, transition from busy to idle. Tests authored, NOT RUN. Full application/Compose compilation, device layout, first-frame feedback, TalkBack, theme/large-font/pixel verification NOT RUN. Source-only DONE is not visual or runtime acceptance.

Patch is independent of the earlier MR-R1-004 codec patch and applies to the clean base. It does not include or supersede that patch. File scope: ImageEditorScreen.kt; new EditorExportButtonContent.kt; new EditorExportButtonContentTest.kt; this CHANGELOG. No security, deletion/retention, backend, paid, credentials, opt-in migration or startup/login changes.
