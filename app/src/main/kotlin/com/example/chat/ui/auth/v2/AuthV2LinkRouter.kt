package com.example.chat.ui.auth.v2

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Incoming links are parameters, never approval or a replacement endpoint. Tokens remain memory-only. */
object AuthV2LinkRouter {
    class Link(val token: String?, val workspace: String?,val invite:Boolean=false) {
        override fun toString() = "AuthLink(redacted)"
    }
    class Verified(val token:String,val result:com.example.chat.data.auth.v2.InvitationVerification.Valid) {
        override fun toString()="VerifiedInvite(redacted)"
    }
    private val invitation=MutableStateFlow<Verified?>(null)
    val verifiedInvitation=invitation.asStateFlow()
    fun acceptVerified(token:String,result:com.example.chat.data.auth.v2.InvitationVerification.Valid) {invitation.value=Verified(token,result)}
    fun clearVerified() {invitation.value=null}
    private val pending = MutableStateFlow<Link?>(null)
    val links = pending.asStateFlow()
    fun receive(uri: Uri): Boolean {
        if (!com.example.chat.ReleaseFeatures.AUTH_V2_CONTRACTS || uri.scheme != "chaty" || uri.userInfo != null) return false
        val parameters=com.example.chat.ui.auth.AuthLinkPolicy.parse(uri.toString()) ?: return false
        val link=Link(parameters.token,parameters.workspace,parameters.kind=="invite")
        pending.value=link;return true
    }
    fun consume(link: Link) {pending.compareAndSet(link,null)}
}
