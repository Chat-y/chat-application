package com.example.chat.ui.camera

import androidx.compose.ui.graphics.vector.ImageVector

/** Camera icon purpose binding (camera-owner handoff, icon split by definition).
 *
 *  Every effect/lens definition binds its icon expression to a per-definition
 *  purpose ID so icon rendering can split definitions that share the same
 *  stock vector: the global name bridge (ChatyMaterialIconRoles) maps a stock
 *  NAME to a single role and cannot tell them apart (e.g. three definitions
 *  on Icons.Rounded.Pets). The IDs come verbatim from the Conversation
 *  worker's camera purpose inventory (131 definitions); that worker adds the
 *  matching bridge IDs and original artwork, so this seam intentionally
 *  returns the stock vector unchanged until that art lands - no art is
 *  invented here.
 */
// Previous pass-through retained: internal fun cameraIconPurpose(purposeId: String, stock: ImageVector): ImageVector = stock
/** Returns the named original purpose vector. The retained stock value is only a safe unknown-ID fallback. */
internal fun cameraIconPurpose(purposeId: String, stock: ImageVector): ImageVector {
    val purpose = com.example.chat.ui.icons.ChatyIconId.entries.firstOrNull { it.name == purposeId }
    return if (purpose != null) com.example.chat.ui.icons.ChatyGlyphVectors.of(purpose) else stock
}
