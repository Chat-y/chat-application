# Released media UI batch 2 - test cases, NOT RUN

All cases require an Android Compose host. No build/compiler/device/pixel checks were run. These cases add to the three batch1 source tests and do not certify accessibility or layout.

## MR-R3-026

1. Open editor idle: toolbar reading order Text, Draw, Adjust, Rotate, Undo stroke, Redo stroke, Reset. History controls must follow Rotate and immediately precede Reset. Horizontal scroll preserves every control at font scale200%.
2. Draw a dot; Undo removes it, Redo restores it. Moving the controls must not alter enabled state or callbacks.
3. Draw, rotate, then inspect Redo: geometric transform still invalidates old-coordinate redo. Reset still clears all the same edit state.
4. Start export: all toolbar edit/history controls remain disabled exactly as before; no state or output file behavior changes.

## MR-R3-029

1. Light and dark review: numeric/type fallback label, GIF badge and Edited badge use theme labelSmall with 0.2sp letter spacing. Existing already-spaced metadata/warnings remain unchanged.
2. Render each at font scales100%,150%,200%, smallest supported window and long localized labels. No clipping or target shrinking; text still uses scaled typography.
3. Check non-image/non-video fallback, GIF and edited still-image items, with selected/unselected states. Badge wording and semantics are unchanged.
4. Compare colors before/after: theme roles unchanged, no new color literals. Required device/screenshot verification remains open.
