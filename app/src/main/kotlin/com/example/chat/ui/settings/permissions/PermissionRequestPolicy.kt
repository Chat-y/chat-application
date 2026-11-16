package com.example.chat.ui.settings.permissions

import android.Manifest

/** This overview/test has no feature that requires precision. Never escalate its request.
 * Kept outside the screen initializer so the policy can be unit-tested without composing UI. */
internal val overviewLocationPermissions = listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
