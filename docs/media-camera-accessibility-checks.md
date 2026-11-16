# Camera accessibility smoke scripts

Status: scripts prepared, NOT RUN. This file is not an accessibility signoff.

Use a test account, a synthetic scene with no private information, and a test-only recipient if sending is part of the run. Do not send to a real contact as a substitute for a test fixture. Do not record secrets, private documents or bystanders in screenshots.

Record app revision, device/API, camera facing, theme, font scale, assistive technology and the outcome of each step. Attach actual screenshots/logs only after the run, with private identifiers removed. Keep failures open rather than changing an expected result to match a defect.

## TalkBack: run after every camera-touching release

1. Turn on TalkBack using the device's accessibility controls. Open the camera from the normal photo entry without touching an unlabeled coordinate.
2. Navigate by accessibility focus. Confirm Back, gallery, flash where supported, options, mode choices, shutter and camera switch have distinct names. Unsupported controls must be absent or explain their state.
3. Choose another supported photo mode, then return to Photo. The selected mode must be distinguishable without color. Open and dismiss options, effects and lens UI. Back must dismiss the active overlay before exiting the camera.
4. On a device with denied camera permission, verify the permission message is announced and Open settings is reachable. Return without a grant and verify there is no focus trap. Repeat with permission granted. Record keyboard focus and TalkBack focus separately.
5. Select a timed photo. Verify countdown status is spoken, shutter remains reachable, and activating shutter again cancels. Cancellation must not create a delayed surprise photo. Repeat after background/foreground.
6. Take a synthetic photo. The shutter must expose Capturing photo and the in-flight state must end honestly on success or failure. Move to confirm. The caption must not open the keyboard automatically.
7. Edit the caption with TalkBack. Verify Send/Post remains the one primary action and Discard is a separate named secondary action. During a slow effect bake, repeated Send activation must not create multiple handoffs; controls must expose their busy state.
8. Discard a different staged photo. Confirm the original fixture remains unchanged and no unwanted send occurred. If a discard prompt exists in the containing review flow, Keep editing and Discard must both be reachable.
9. For a supported video entry, start, pause, resume and stop a synthetic recording. Recording must expose elapsed seconds; Paused must be a visible and spoken state rather than a frozen timer alone. Confirm the unsaved-video exit prompt offers both retention and discard choices.
10. With the recent strip open, navigate the named recent-photo choices and close the strip without importing. Verify focus does not disappear into the preview surface.
11. Repeat the key capture/confirm/discard flow in both themes and at 200% font scale. Record clipping, overlapping targets, lost labels and unreachable actions as failures, not source-level passes.

Pass means the full flow is operable using TalkBack, names and state are meaningful, no accidental capture/send occurs, and all observed failures have evidence. A script existing is not a passed run.

## Switch Access: run at least once per release

1. Turn on Switch Access using a configured test switch or the device's supported input method. Start from the normal camera entry with scan controls visible.
2. Complete forward scan through Back, supported camera controls, mode selection, options and shutter. Every action must be reached in a finite scan; decorative glyphs must not create repeated empty stops.
3. Open each available overlay. Complete a scan within it, activate its close/dismiss control and return to the prior camera focus. Verify a modal does not create an endless scan loop or send focus behind itself.
4. Set and cancel a photo timer using only scan-and-activate actions. Capture a synthetic photo and reach the confirm pane without a touch gesture.
5. Enter a short caption through the supported switch input method. Reach Send/Post and Discard as distinct targets. Exercise discard on one fixture and send only to the approved test recipient on another. Verify no duplicated handoff after repeated activation.
6. In supported video entry, activate record, pause/resume and stop. Reach Keep reviewing and Discard in the unsaved-draft prompt. Record any action that requires pinch, swipe or dragging with no operable button alternative.
7. Repeat with large text and landscape. Required controls must remain in the scan order, visible when focused and outside system bars/cutouts.

Pass means the complete task terminates without a touch-only escape, focus stays in the active surface, and each activation has one intended effect. Record unavailable hardware and untested steps explicitly.

## Release review gates

- Run the scripts; record device evidence and unresolved failures before claiming verification.
- Review every new animation for the app's reduced-motion preference. The current capture-flash check is not proof that every sheet/carousel animation has been audited.
- Preserve the tested Back order: overlay, staged media, then unsaved-video/host navigation as applicable.
- Keep capture-result ownership separate from delivery confirmation. The receiver retains accepted bytes for its retry policy; the camera's callback return is not an upload receipt.
- Do not promise that unsent process-death orphans disappear within 24 hours. In-session ownership cleanup exists; a camera startup orphan sweep is still outstanding.
- List user-visible changes in the release notes only when they actually ship. Local source edits and unrun tests are not a release.

## Reduced-motion follow-up, source only

The capture flash is already suppressed under the app's live motion policy. Mode-strip programmatic centering now jumps without animated travel and effect-selection scale uses zero-duration transitions when that policy is enabled. Neither change alters the selected mode/effect or records a new preference.

NOT VERIFIED: device appearance, a live policy toggle midway through an animation, lens carousel fling/settle, and Material modal-sheet motion. CAM-R1-045 stays NOT DONE until all listed surfaces and the preference-toggle path are covered and inspected. Test with motion enabled/disabled before opening the camera and while it remains open. Check that both modes can still reach and select every supported choice.
