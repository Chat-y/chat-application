# Home callback inventory (waves 1-3)

State: source only. Read from the stacked tree (pin 3c67bf51 + start patch + wave 1, 2, 3). Nothing compiled, nothing run.
Machine-checkable copy: `app/src/test/java/com/example/chat/HomeCallbackInventory.kt`, checked by `HomeCallbackInventoryTest`.

Every entry is an optional parameter with a default, so existing callers keep compiling.

| Callback or input | Declared in | Status |
|---|---|---|
| onInvert, onInspect, onExitSelection, onSplitChat | SelectionHomeAppBar (HomeAppBars.kt) | Wired by ChatsHomeScreen |
| onSectionHeaderTap | HomeConversationList (ChatsHomeScreen.kt) | Wired. Gap: matches only label `PINNED` |
| onRangeSelectTo, onRowContextMenu, onRowActivate, onRowFocused, onHoverRow, focusRequesterFor | HomeConversationList | Wired |
| opState, onRetry | HomeConversationList | Default only. ChatsHomeHost does not pass them |
| summaries, selectedConversationIds, onOpenConversation | ChatFolderChips | Wired by Screen |
| canInspectConversation | ChatFolderChips | Declared, not passed. Defaults to false, so inspectors are empty |
| resolvedConversationIds | ChatFolderChips | Declared, not passed. Null keeps stale-reference removal off |
| badgeFor | ChatFolderChips | Declared, not passed |
| onReorder | ChatFolderChips | Declared, not passed. Held with the folder store |
| onOpenSavedViews, onOpenViewHistory | none | Do not exist. HomeQuickViewPolicy has saved views and history, with no Screen or Bars entry point |

## Cross-file gaps for other groups (not edited here)

1. Screen: `onSectionHeaderTap = { label -> if (label == "PINNED") ... }` cannot re-expand. `groupHomeEntries` replaces the collapsed header with `PINNED (n chats, m unread)`. Match `label.startsWith("PINNED")`.
2. Screen: `homePaneState.layout(...)` gets full `maxWidth` and the default 1dp divider. The rail (181dp) is subtracted later and the real divider is 16dp. Pass usable width and `dividerWidthDp = 16f`.
3. Screen: `HomeGroupMode.Date(...)` is built with no `now` or `zone`, so it keeps `Instant.now()` from click time. Pass `now` from `homeClockMinute` and the zone from `rememberHomeDateContext`.
4. Screen: pass `canInspectConversation` to ChatFolderChips. It must be false for locked and hidden chats. Folder summaries use `visibleConversations`, which may include locked chats: filter them first.
5. Bars and Screen: add `onOpenSavedViews` and `onOpenViewHistory`, and wire `HomeQuickViewDialog` to saved views and `pushHistory`/`popHistory` (S13 has no Screen wiring).
6. Host: pass `opState` and `onRetry` to Screen, with `HomeRetryIntent.isValidFor` before any retry.
