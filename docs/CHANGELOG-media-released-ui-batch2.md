# Released media UI batches 1-2

Full cumulative patch against kotlin c891dc22ebfc0dcd3f65b6369843f74bfb81c305. Includes batch1; replace batch1 with this patch rather than applying both. Independent of the MR-R1-004 codec patch.

- MR-R3-027 (batch1): editor Exporting label and trailing18dp spinner, existing save/disabling flow unchanged. Three new Compose source tests NOT RUN.
- MR-R3-026 (batch2): move Undo stroke/Redo stroke after Rotate and immediately before Reset. Callback contents, labels and enabled states unchanged; old position retained as line comments.
- MR-R3-029 (batch2): remaining three labelSmall sites in review (fallback type label, GIF badge, Edited badge) receive 0.2sp letter spacing; metadata/warnings already use it. Original lines retained as comments; theme tokens/colors unchanged.

Batch2 adds eight explicit device/interaction/theme/large-font cases in docs/media-released-ui-batch2-test-cases.md. NOT RUN. Source-only DONE means written, not verified visuals/runtime. No build, compiler, commit, push or CI. No security, disclosure wording, deletion/retention, backend, paid, credentials, migrations or startup/login changes. Whitespace and clean-base patch application checked. Full Compose/app/native/device/UI/pixel acceptance NOT RUN.
