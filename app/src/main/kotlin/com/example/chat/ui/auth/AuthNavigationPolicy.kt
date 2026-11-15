package com.example.chat.ui.auth

/** Route restoration never carries credentials or discloses a different account's destination. */
object AuthNavigationPolicy {
    fun mayResume(savedUser:String?,savedTenant:String?,savedBase:String?,currentUser:String?,currentTenant:String,currentBase:String):Boolean =
        !savedUser.isNullOrBlank() && savedUser==currentUser && savedTenant==currentTenant && savedBase==currentBase
    fun shouldLeaveAuth(authenticated:Boolean,registrationVerification:Boolean,reauthenticating:Boolean):Boolean =
        authenticated && !registrationVerification && !reauthenticating
}
