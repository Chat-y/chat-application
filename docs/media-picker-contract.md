# File picker batch and privacy contract

The file picker returns a flat list of file descriptors. Selecting multiple images here does not promise an album, a receiver grid, or a grouping toggle. The receiving chat flow decides message grouping. This picker preserves existing library order for library selections, not chronological tap order. Imported batches retain provider result order. A true receiver-side album preview remains unfinished.

## Device document labels and types

Provider display names are untrusted. Import rejects slash, backslash and NUL labels, sanitizes/bounds the remaining label, then derives a local suffix from bytes rather than the provider name. Recognized signatures use a short allowed suffix. Unknown bytes are application/octet-stream and .bin; a ZIP header is only a ZIP container, not proof of Office/APK safety. The original sanitized provider label is shown only as unverified details. A signature is not a malware or full-content scan.

## Private URI handling

The picker does not log content URIs or exception messages. UI failures use local categories and ordinal/item labels rather than provider URI strings. Do not add raw URI logging, crash breadcrumbs or analytics fields. If a future diagnostic genuinely requires scheme context, record only a fixed content://<redacted> marker, never the source URI or authority. This source inspection is not a device crash-report audit.

## Still incomplete

File-content identity at send, full provider MIME/signature disagreement quarantine, active-file cleanup leases, crash orphan sweep, approved batch quota and receiver album preview are not established by this source change. No device or release tests have been run under the overnight source-only instruction.

## Confirmation preflight

Confirmation checks regular-file/readability and exact byte size against the reviewed descriptor on IO, then hands the immutable list to the host on main. A changed size blocks that attempt and keeps the summary for cancellation/reselection. It does not silently update the displayed total or accept a larger file. This stat check is not a content hash, type revalidation, inode/version binding or atomic lease. Same-size replacement and changes after the check remain possible. A host callback exception keeps the selection; the message asks the person to check the destination before retrying because a Unit-returning callback cannot prove whether it accepted bytes before throwing. No new receiver acknowledgement/ownership protocol is introduced here.
