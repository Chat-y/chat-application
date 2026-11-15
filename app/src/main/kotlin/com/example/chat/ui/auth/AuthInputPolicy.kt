package com.example.chat.ui.auth

/** Existing deployed form rules. No truncation, case rewrite or undocumented server maximum. */
object AuthInputPolicy {
    fun legacyPasswordError(password:String):String? = when {
        password.isEmpty()->"Please enter your password"
        password.length<6->"Password must be at least 6 characters"
        else->null
    }
    fun workspaceError(workspace:String,required:Boolean):String? =
        if(required && workspace.trim().isEmpty()) "Please enter your Workspace ID" else null
    fun workspaceDisplay(raw:String)=raw.trim()
    enum class CodeError { Expired, Wrong, TooMany, Other }
    fun codeError(message:String):CodeError = when {
        AuthFormPolicy.rateLimited(message)->CodeError.TooMany
        listOf("expired","expiration","no longer valid").any {message.contains(it,true)}->CodeError.Expired
        listOf("invalid code","wrong code","incorrect code","code_invalid").any {message.contains(it,true)}->CodeError.Wrong
        else->CodeError.Other
    }
}
