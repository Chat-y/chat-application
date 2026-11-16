package com.example.chat.ui.settings.permissions

/** A missing rationale alone cannot distinguish a first request from a blocked request. */
internal fun shouldOfferPermissionSettings(askedBefore: Boolean, rationale: Boolean, hasActivity: Boolean): Boolean =
    askedBefore && !rationale && hasActivity
