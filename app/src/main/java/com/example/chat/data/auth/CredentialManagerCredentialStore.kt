package com.example.chat.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.*
import com.example.chat.core.auth.PasswordCredentialStore
import kotlinx.coroutines.CancellationException

/** Passwords go directly to the selected Android provider, never preferences or logs. */
class CredentialManagerCredentialStore(context:Context):PasswordCredentialStore {
    private val manager by lazy {CredentialManager.create(context.applicationContext)}
    override suspend fun offerSave(activity:Activity,identifier:String,password:String):PasswordCredentialStore.SaveOutcome {
        if(identifier.isBlank() || password.isEmpty() || activity.isFinishing || activity.isDestroyed) return PasswordCredentialStore.SaveOutcome.Unavailable
        return try {
            manager.createCredential(activity,CreatePasswordRequest(identifier,password))
            PasswordCredentialStore.SaveOutcome.Saved
        } catch(cancel:CancellationException) {throw cancel}
        catch(_:CreateCredentialCancellationException){PasswordCredentialStore.SaveOutcome.Dismissed}
        catch(_:Exception){PasswordCredentialStore.SaveOutcome.Unavailable}
    }
    override suspend fun retrieve(activity:Activity):PasswordCredentialStore.RetrieveOutcome = try {
        if(activity.isFinishing || activity.isDestroyed) PasswordCredentialStore.RetrieveOutcome.Unavailable
        else {
            val credential=manager.getCredential(activity,GetCredentialRequest(listOf(GetPasswordOption()))).credential
            if(credential is PasswordCredential) PasswordCredentialStore.RetrieveOutcome.Found(credential.id,credential.password)
            else PasswordCredentialStore.RetrieveOutcome.None
        }
    } catch(cancel:CancellationException){throw cancel}
    catch(_:GetCredentialCancellationException){PasswordCredentialStore.RetrieveOutcome.None}
    catch(_:NoCredentialException){PasswordCredentialStore.RetrieveOutcome.None}
    catch(_:Exception){PasswordCredentialStore.RetrieveOutcome.Unavailable}
}
