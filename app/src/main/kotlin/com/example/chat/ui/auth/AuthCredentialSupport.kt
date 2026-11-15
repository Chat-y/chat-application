package com.example.chat.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.chat.core.auth.PasswordCredentialStore

private fun Context.credentialActivity():Activity? {
    var current:Context=this
    val seen=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Context,Boolean>())
    while(seen.add(current)) {
        if(current is Activity) return current
        current=(current as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}
class AuthCredentialSupport(private val activity:Activity?,private val store:PasswordCredentialStore) {
    suspend fun save(identifier:String,password:String) {
        if(com.example.chat.core.auth.CredentialStorePolicy.canOffer(identifier,password,true,false)) activity?.let {store.offerSave(it,identifier,password)}
    }
    suspend fun retrieve():PasswordCredentialStore.RetrieveOutcome = activity?.let {store.retrieve(it)} ?: PasswordCredentialStore.RetrieveOutcome.Unavailable
}
@Composable
fun rememberAuthCredentials():AuthCredentialSupport {
    val context=LocalContext.current
    return remember(context) {AuthCredentialSupport(context.credentialActivity(),com.example.chat.data.auth.CredentialManagerCredentialStore(context.applicationContext))}
}
