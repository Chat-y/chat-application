package com.example.chat.data.api

/** Challenge routing only. No OTP, password, token or device proof. Never counts as server verification. */
data class EmailCodeResume(val challengeId:String,val email:String,val tenant:String,val baseUrl:String,
    val userId:String?,val defaultTenant:Boolean,val expiresAtEpochMs:Long,val resendAfterSeconds:Int,val ownerTenant:String,val mayResend:Boolean=true,val resendAtEpochMs:Long=0L)
