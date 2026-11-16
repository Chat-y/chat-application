# Home diagnostic counters (R5-48)

Policy only. No code in this slice emits these counters.

Allowed: counts, enum names, durations in ms, boolean flags, app and schema versions.
Examples: `home.chats.visible=12`, `home.view=Drafts`, `home.op.failed.kind=Offline`.

Never allowed: message text, draft text, titles, phone numbers, contact names, conversation or account ids, folder names, search queries, scratchpad text.

Locked and hidden chats count as one anonymous total. Counters never list them by id.
Counters stay on the device unless the user shares them. No backend change.
