# Released media UI batch3 - preserve existing source behavior

Cumulative full patch against c891dc22. Replaces batches1-2; do not apply the cumulative patches together. Contains the same three implemented rows from those batches, plus source-audit documentation. Independent of MR-R1-004 codec patch. No new application code in batch3.

Five released proposal rows already have the requested source behavior and should not cause gratuitous UI changes:

- MR-R3-015: thumbnails use foundation clickable/default indication; headers use Material controls. Pressed-state pixels NOT VERIFIED.
- MR-R3-016: caption/send already uses Surface(tonalElevation=2.dp).
- MR-R3-025: only Square/Portrait/Landscape crop presets; no overlay triggers conditional extensions.
- MR-R3-041: required Start/End -1s/+1s trim buttons remain with 48dp minimum height.
- MR-R3-048: keyframe-honesty text stays owned by shared MediaTrimScreen used by both entry points; no localization migration.

Batch3 adds these NOT RUN cases: 1) press/release thumbnails/header in light/dark and inspect ripple/focus; 2) caption container remains distinct from media in both themes; 3) crop list stays exactly three and works at200% text; 4) complete trim start/end adjustment without a drag using touch and TalkBack; 5) enter Trim from review and viewer and compare keyframe disclaimer verbatim.

Counts from cumulative8-row ledger:3 DONE source implementations,5 ALREADY DONE source audits,0 NOT DONE. No runtime/visual acceptance, compiler/build, commit/push or CI. Original safety/permission/retention/ownership rules unchanged. No security, backend, paid, credentials or migrations touched.
