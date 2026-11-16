# Hide media (settings > Universal > Hide media)

Scope: the Hide media screen (`HideMediaScreen.kt`), its state holder (`HideMediaStateHolder.kt`),
the marker writer (`MediaVisibilityMarkers.kt`) and its repository (`HideMediaRepository.kt`).

## What the feature is

Three per-type preferences (photos, videos, GIFs) each maintain a `.nomedia` marker in a matching
folder under the app-private `ChatyMedia` directory. A `.nomedia` file is a hint to gallery
scanners: it asks them not to index that folder. It is not encryption and not a security
boundary. Private chat files already stay inside Chaty; existing gallery copies, camera captures
and cloud backups are unchanged by these markers (HM1-02, HM1-15, HM1-32).

## Threat model (HM5-01)

| Threat | Covered? | Notes |
| --- | --- | --- |
| Curious family member browsing the gallery | Partially | Markers hide new scans in gallery apps that honour `.nomedia`; some gallery apps ignore it. |
| Lost unlocked phone | No | Markers do not encrypt; use the app lock and device lock. |
| Gallery sync to cloud (Google Photos backup) | No | Previously exported copies and backups are unchanged (HM5-02, HM5-03, stated on the page). |
| Malware with storage access | No | Private app storage is the real boundary, not the marker (HM1-16). |

## Definition of done (HM5-32)

A switch is "done" when toggling it changes visible behaviour in at least three gallery apps on
device (for example Google Photos, Samsung Gallery, Files). Today that acceptance is unverified:
markers are maintained correctly in source (unit-tested), but no device pass has run.

## Decision log (HM5-29)

- `.nomedia` markers, per-type folders: chosen because chat media lives in app-private storage;
  a single parent marker would hide every type at once (HM1-01).
- Markers are a convention, not security: the page copy says so instead of promising privacy
  (HM1-15). Known limit: some apps may still show files (HM2-38, HM2-40).
- Save-to-gallery export and per-chat visibility belong to the media screens; this page only
  maintains markers and preferences.
- Competitor note: WhatsApp's "Media visibility" defaults to on (visible), matching our default
  OFF for the hide switches (HM5-44).

## Support article draft (HM5-27)

**Hide chat media from your gallery.** Open Settings > Universal > Hide media and turn on the
types you want hidden. Chaty keeps a small marker file so gallery apps skip those folders.
Good to know: files stay inside Chaty and are not moved or deleted; copies you already saved to a
gallery or a cloud backup stay there; a few gallery apps ignore these markers. If media still
appears, open "How to find hidden media" on that page and use "Open gallery app" to check.
