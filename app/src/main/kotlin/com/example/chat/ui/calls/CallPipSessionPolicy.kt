package com.example.chat.ui.calls

import com.example.chat.data.services.ChatyCallSession
import com.example.chat.data.services.CallForegroundPolicy

/** UI identity gate only. It never starts/resumes/answers media or adopts an account. */
object CallPipSessionPolicy {
    fun allows(owner: Any?, currentOwner: Any?, callId: String?, session: ChatyCallSession?): Boolean =
        owner != null && owner === currentOwner && !callId.isNullOrBlank() &&
            session?.callId == callId && CallForegroundPolicy.shouldRunFor(session)
}
