package com.example.chat.core.auth

interface PasswordCredentialStore {
    enum class SaveOutcome { Saved, Dismissed, Unavailable }
    sealed interface RetrieveOutcome {
        class Found(val identifier:String,val password:String):RetrieveOutcome {
            override fun toString()="PasswordCredential(redacted)"
        }
        data object None:RetrieveOutcome
        data object Unavailable:RetrieveOutcome
    }
    suspend fun offerSave(activity:android.app.Activity,identifier:String,password:String):SaveOutcome
    suspend fun retrieve(activity:android.app.Activity):RetrieveOutcome
    fun passkeysAvailable():Boolean=false
}
object CredentialStorePolicy {
    fun canOffer(identifier:String,password:String,serverConfirmed:Boolean,demo:Boolean)=
        serverConfirmed && !demo && identifier.isNotBlank() && password.isNotEmpty()
}
