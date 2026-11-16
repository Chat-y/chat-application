# Camera support and release checks

## Temporary photo or video was exposed

1. Do not ask the person to send the image, its pixels, its full filesystem path, an account token or an unfiltered log. Ask for the app version, Android version, approximate time, whether the item was sent, and whether the phone was rooted or running a debug build.
2. If the camera screen is still open, do not promise that leaving will preserve an unsent draft. Explain that unsent photos and videos are temporary. Let the person decide whether to send/save the intended item or discard it.
3. Distinguish an app-private temporary file from a copy explicitly shared through another app. Revoking or deleting a local cache file cannot retract a recipient's copy. Do not claim that a cache incident proves a server upload.
4. In-session camera ownership removes tracked unsent siblings on discard/close and transfers only the confirmed final file to the host. Accepted host results may still be needed for retry. Do not delete active or transferred send files as a bulk cleanup response.
5. Crash/process-death orphan cleanup and a guaranteed 24-hour deletion deadline are not verified. There is no support promise of automatic deletion within 24 hours. Rooted-device and debug-build access need a separate assessment.
6. Record only the failure category and non-content environment details for escalation. A real exposure or suspected unauthorized access needs a security review. This note is a response checklist, not evidence that such an incident occurred.

## Behavior-change record required before release

Every camera behavior change needs its own CHANGELOG entry with:

- What changed and which files changed.
- The previous and new default, if a default changes.
- Whether the behavior is an explicit opt-in or needs a product/user choice.
- What was inspected, compiled, tested, visually checked and device-checked. Missing checks must say NOT RUN or unverified.
- Backlog IDs and any remaining acceptance gaps.

Do not describe code-written status as device acceptance. Keep changes to countdown/cancel, flash cycle, selfie mirroring, lens output size and Send/ownership behavior visible in release review. Existing mirror output is preserved by default: Mirror saved selfies starts off. Full command history and crash-safe cache cleanup remain unfinished.

## Camera error copy

Say what happened, why only when known, then one useful recovery. Name the action that actually exists. Do not expose a provider URI, full path, signed link or raw exception message. Use a transient notice for a failed optional setting; keep binding/permission recovery visible until a decision or successful bind.

Current source review distinguishes unsupported hardware from Night/Portrait excluding HDR, offers Retry plus gallery for unavailable camera, uses a storage hint for failed capture-file creation, and labels lens-bake fallback as an original photo rather than a successful lens. Other strings still need a full content/translation/device audit.

## Remaining verification

No device incident was reproduced and no builds, tests or screenshots were run for this note. The CameraPhase Kdoc table describes current transitions but is not covered by a transition test suite. Screen-off/phone-call behavior depends on Android lifecycle delivery: CameraVideoSession currently calls stop on pause, validates a partial clip and exposes review when successful; it does not prove all devices or locked-screen cases behave the same way.

## Offline smoke script (CAM-R4-007), NOT RUN

Use synthetic scenes and a test-only destination. Fully install the intended local lens/model assets, then enable airplane mode and disable Wi-Fi. Open the camera, take and discard a plain photo, apply one non-segment effect, one face lens and one segmented background, and retake a document crop. Record which models were already present and any unavailable-model state. Confirm no flow falsely reports an upload or delivery. The host's Send/Post path is a separate online test; callback completion is not evidence of network delivery. Repeat from a cold process start with the intended release model packaging. Source inspection alone does not establish offline first-run behavior.

## Staging and backup source inspection

At the pinned source base, AndroidManifest.xml declares allowBackup=false and fullBackupContent=false. No backup_rules.xml or data-extraction rule file was found under app/src. This is not inspection of the merged manifest, an installed APK, OEM device-transfer behavior, or any app-managed backup/archive path. Those checks remain open under the source-only instruction. The camera-owned-file rule now forbids moving unsent review staging into shared storage; the rule itself does not enforce file permissions or establish a crash cleanup deadline.
