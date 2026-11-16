# CHANGELOG - worker "rest" (branch impl/rest, base 3c67bf51)
Status for every entry: UNVERIFIED (no compiler, SDK, tests or emulator available to this worker). Nothing pushed. Authored as Bandi.
Files are under app/src/main/kotlin/com/example/chat/ unless noted.

## Subscription & Pricing
- SUB-R1-001/002/003: price shown without ".0", currency symbol from ISO code (INR keeps rupee sign), string prices parsed. ui/subscription/PlanSubscriptionScreen.kt. UNVERIFIED.
- SUB-R1-004/043: "Save N%" computed from plan data, hidden when unknown. same file. UNVERIFIED.
- SUB-R1-005/011/012: one subscribePlan pipeline, free plan always 1 month, one isFree predicate. same file. UNVERIFIED.
- SUB-R1-006/R2-007/R1-020: id-less, duplicate-id and negative-price plans dropped; selection null-safe. same file. UNVERIFIED.
- SUB-R1-007: cycle and selection survive rotation (rememberSaveable). same file. UNVERIFIED.
- SUB-R1-008: back hidden while subscribing. same file. UNVERIFIED.
- SUB-R1-009 (opt-in): confirmation dialog with plan, price, cycle for paid plans. same file. UNVERIFIED.
- SUB-R1-010: blank organizationId fails fast with message. same file. UNVERIFIED.
- SUB-R1-016/R3-015: canned error copy, raw detail only in log. same file. UNVERIFIED.
- SUB-R1-014/015: renewal/cancel text and annual total line on ProPricingScreen. ui/settings/pro/ProPricingScreen.kt. UNVERIFIED.
- SUB-R1-017: About text no longer claims end-to-end encryption. ui/settings/SettingsScreen.kt line 434 (shared file, one line). UNVERIFIED.
- SUB-R1-037/038/039/046: plan cards selectable RadioButton, billing tabs selectable Tab with 48dp, ProPricing segments 48dp. both pricing files. UNVERIFIED.
- SUB-R1-042/R4-035: error banner polite live region, "Error:" prefix. UNVERIFIED.
- SUB-R1-045/R2-032: pane title and headings. both files. UNVERIFIED.
- SUB-R2-002/003/004/010/019: 15s load timeout, selection kept across retry, plan re-verified at submit, org name capped. UNVERIFIED.
- SUB-R2-033/036/046/048, R3-003/004, R4-031: free shortcut 48dp, chips 11sp, friendlier empty copy, onAccent token, features accept string, description 4 lines, test tags. UNVERIFIED.
- SUB-R3-037: ProPricing feature rows merged for TalkBack. UNVERIFIED.
- SUB-R4-015: unsubstantiated "Next-generation privacy & encryption tools" line commented out. UNVERIFIED.

## Tasks
- TAS-001/003/005/006: update/delete return Boolean; moveTask fails and records no history without confirmation; detail restores status; delete leaves only after success; editor stays open on failure. ui/tasks/TasksController.kt, TaskDetailScreen.kt, TaskCreateEditModal.kt. Test updated: app/src/test/java/com/example/chat/TasksSliceParityTest.kt. UNVERIFIED.
- TAS-002 (opt-in): detail status change goes through moveTask (validation + history). TaskDetailScreen.kt. UNVERIFIED.
- TAS-004: list delete confirmation. TasksScreen.kt. UNVERIFIED.
- TAS-007/010: load bound to account and generation. TasksController.kt. UNVERIFIED.
- TAS-011: activity text "not available on this server". TaskDetailScreen.kt. UNVERIFIED.
- TAS-043/044/045/052: 48dp targets, "Move to <stage>" labels, edit icon. TasksScreen.kt, TaskDetailScreen.kt. UNVERIFIED.
- TAS-038: polite live-region error in editor. TaskCreateEditModal.kt. UNVERIFIED.
- TAS-049/050/051: "+N more" assignees, wrapping chips and labels. TaskDetailScreen.kt. UNVERIFIED.
- TAS-034/093: avatar colour remembered; section headings. TasksComponents.kt, TaskDetailScreen.kt. UNVERIFIED.

## Message automation
- AUT-001/002/003/005: error + Retry state, draft kept until created, cancel failure snackbar, refresh keyed by service. ui/automation/ScheduledMessagesScreen.kt. UNVERIFIED.
- AUT-006/007: date pick read as UTC date, built once. ui/automation/ScheduleMessageSheet.kt. UNVERIFIED.
- AUT-042/047/048/118: disabled-Confirm reason, loading and refresh labels, sheet closes when chats vanish. both files. UNVERIFIED.

## Split workspace
- SPL-01: restored focus normalized to surviving pane. ui/split/SplitChatModels.kt. UNVERIFIED.
- SPL-09: pane title in Minimize/Open full chat/Close labels. ui/split/MiniChatWindow.kt. UNVERIFIED.
- SPL-30/036: guides hidden from accessibility, non-finite saved sizes fall back. MiniChatWindow.kt. UNVERIFIED.

## Maps & location
- MAP-R1-003 (opt-in): explicit style choice survives theme change. ui/chats/location/MapViewerScreen.kt, FriendMapScreen.kt. UNVERIFIED.
- MAP-R4-168: pane titles. both files. UNVERIFIED.
- MAP-R4-197: misleading tray handle commented out. FriendMapScreen.kt. UNVERIFIED.
