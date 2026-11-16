# Released media UI batch4

Cumulative full patch against kotlin c891dc22; replaces batches1-3, do not stack. Independent of earlier codec patch.

MR-R3-036: constrain review dialog text/body widths to328dp: discard, coach explanation, view-once explanation and reorder column. Max width only, no forced minimum/fixed height. Original lines retained as comments. All text, actions, dismissal behavior, security/retention promises and persisted flags unchanged. Typography/layout-only.

MR-R3-031 source audit: current header's only icon-only action is More actions and already has PlainTooltip/TooltipBox; Edit/Trim are visible text buttons. Send also has tooltip. No source change needed. No discovery/AT acceptance claimed.

Added NOT RUN cases: 1) all four dialog body bounds <=328dp on600dp foldable; 2) narrow280dp retains natural width without horizontal overflow; 3) font200% keeps complete text and scrolls reorder entries; 4) RTL text wraps in same capped body; 5) dismissal and buttons retain callbacks/persisted behavior; 6) long-press More actions and Send reveals verb tooltip while visible Trim/Edit retain text names.

Cumulative10-row counts:4 DONE source-only,6 ALREADY DONE source-audit,0 NOT DONE. No build/compiler/device/native/UI/pixel checks, commit/push or CI. Whitespace and clean-base apply-check pass. No security policy, deletion/retention mechanism, backend, paid, credentials, migration or startup changes. Pixel verification remains open for all UI changes.
