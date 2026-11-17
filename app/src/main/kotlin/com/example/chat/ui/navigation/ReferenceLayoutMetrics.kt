package com.example.chat.ui.navigation

/** Shared geometry from the supplied Default references and inspected GB home resource.
 * Geometry does not own data, destination inventory, colors, or the persisted style ID.
 */
object ReferenceLayoutMetrics {
    const val compactMediaProgressLabelMinHeightDp = 88
    const val compactMediaProgressLabelMinWidthDp = 144
    const val compactMediaProgressLabelMaxFontScale = 1.15f
    const val compactMediaProgressLabelGapDp = 2
    const val compactMediaProgressLabelHorizontalPaddingDp = 4
    const val compactMediaProgressLabelSurfaceAlpha = 0.9f
    const val messageMenuPreferredHeightDp = 416
    const val messageReactionRailHorizontalPaddingDp = 6
    const val messageReactionRailVerticalPaddingDp = 2
    const val messageReactionRailGapDp = 4
    const val messageReactionRailShadowDp = 6
    const val messageMenuTitleHorizontalPaddingDp = 16
    const val messageMenuTitleVerticalPaddingDp = 12
    const val messageMenuScrimAlpha = 0.25f
    const val messageMenuOuterMarginDp = 0
    const val messageMenuInnerPaddingDp = 0
    const val messageReactionTargetSizeDp = 48
    const val messageReactionEmojiSizeDp = 32
    const val messageReactionRailHeightDp = 56
    const val actionBarHeightDp = 56
    const val compactHeaderHeightDp = 160
    const val compactIdentityHeightDp = compactHeaderHeightDp - actionBarHeightDp
    const val compactIdentityTextSizeSp = 26
    const val expandedHeaderHeightDp = 250
    const val expandedIdentityHeightDp = expandedHeaderHeightDp - actionBarHeightDp
    // GB's toolbar overlays the expanded container, not a second block below it.
    // In a split Column, offset the identity by half the overlaid toolbar height.
    const val identityCenterOffsetDp = actionBarHeightDp / 2
    const val identityIconSizeDp = 38
    const val identityTextSizeSp = 30

    // R1-47: opt-in compact Home identity geometry; existing shared metrics stay
    // unchanged for other screens. Use the height as a minimum and allow text
    // to grow at large font scales rather than clipping personalization.
    const val homeCompactIdentityMinHeightDp = 64
    const val homeCompactIdentityIconSizeDp = 28
    const val homeCompactIdentityTextSizeSp = 20

    // Compact chat-list geometry. Minimum only: text and metadata may grow the row.
    // Existing non-default template geometry, named without changing its values.
    const val homeChatReferenceAvatarSizeDp = 48
    const val homeChatTemplateAvatarSizeDp = 50
    const val homeChatTemplateVerticalPaddingDp = 10
    const val homeChatDividerEndPaddingDp = 16
    const val recorderCardHorizontalPaddingDp = 16
    const val recorderCardVerticalPaddingDp = 12
    const val recorderContentGapDp = 12
    const val recorderControlGapDp = 8
    const val recorderMicrophoneSizeDp = 56
    const val recorderPauseMinWidthDp = 104
    const val recorderCardMarginDp = 0
    const val homeChatAvatarSizeDp = 44
    const val homeChatRowMinHeightDp = 64
    const val homeChatRowVerticalPaddingDp = 6
    const val homeChatRowHorizontalPaddingDp = 10
    const val homeChatReferenceAvatarGapDp = 8
    const val homeChatAvatarGapDp = 12

    // Header v5g geometry merged with shared-UI v2; existing row metrics retained.
    const val homeActionTextBudgetSp = 35
    const val homeActionVerticalBudgetDp = 14
    const val homeBannerActionLanes = 2
    const val homeTabIndicatorDp = 3
    const val homeTabsHeightDp = 48
    const val homeRowMetadataWeight = 1f
    const val homeRowContentWeight = 3f
    const val homePresenceTextSp = 9.5
    const val homePresenceMaxWidthDp = 105
    const val homeRowMetadataIconDp = 14

    // Opt-in shared profile/identity presentation. Existing Home metrics are unchanged.
    const val profileHeaderHorizontalDp = 16
    const val profileHeaderVerticalDp = 12
    const val profileBannerWideBreakpointDp = 480
    const val profileBannerWideHeightDp = 180
    const val profileBannerAspect = 16f / 9f
    const val profileAvatarOuterDp = 92
    const val profileAvatarRingDp = 4
    const val profileAvatarOverlapDp = 40
    const val profileHeaderFollowingGapDp = 8
    const val profileCropHorizontalDp = 24
    const val profileCropTopDp = 16
    // Previous: const val profileCropBottomDp = 28
    // Shared by avatar and banner crop footer, in addition to consumed safe/mandatory insets.
    const val profileCropBottomDp = 40
    const val profileCropActionHeightDp = 50
    const val identityGroupAvatarDp = 68
    const val identityExpandedMaxWidthDp = 420
    const val identityExpandedMaxHeightDp = 360
    const val identityToolbarAvatarDp = 32
    const val identityToolbarGapDp = 10
    const val identityScrollLiftDp = 12
    const val identityToolbarLiftDp = 8
    const val identityDismissFraction = 0.18f
    const val navigationUnderlineHeightDp = 3
    const val navigationUnderlineWidthDp = 24
    const val qrPagePaddingDp = 24
    const val qrCodeSideDp = 230
    const val qrFramePaddingDp = 24
    const val qrBadgeSideDp = 44
    const val qrBrandGlyphDp = 26
}
