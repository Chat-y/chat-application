package com.example.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Authored UNRUN source-order guards. These do NOT replace runtime ingress,
 * native, crash, storage, or first-use integration fixtures. */
class SecureRecoverySourceContractTest {
    private fun source(path: String): String {
        val local = File("src/$path")
        return (if (local.exists()) local else File("app/src/$path")).readText()
    }
    @Test fun startupSweepPrecedesAdmissionAndIsNotLazyOrAsync() {
        val s = source("main/kotlin/com/example/chat/ServiceLocator.kt")
        val init = s.substring(s.indexOf("fun init(context:"), s.indexOf("private val apiBackendTailRan"))
        assertTrue(init.indexOf("sweepSecureAttachmentPartials()") < init.indexOf("appScope.launch"))
        val lazy = s.substring(s.indexOf("val secureAttachmentFiles by lazy"), s.indexOf("val secureAttachmentFiles by lazy") + 350)
        assertFalse(lazy.contains("sweepSecureAttachmentPartials()"))
        assertTrue(lazy.contains("secureMediaRecoveryReady.get()"))
    }
    @Test fun forgedIngressAndFailureCannotKeepControlAuthority() {
        val s = source("main/java/com/example/chat/data/messaging/ConversationsRepository.kt")
        val ingress = s.substring(s.indexOf("val row = source.toMutableMap()"), s.indexOf("val senderId = row[", s.indexOf("val row = source.toMutableMap()")))
        assertTrue(ingress.contains("row.remove(\"_verified_mls_sender\")"))
        assertTrue(ingress.contains("ingressMetadata - SecureControlEnvelope.KEY"))
        val failure = s.substring(s.indexOf("private fun decryptionFailureRow"), s.indexOf("private fun decryptionFailureRow") + 800)
        assertTrue(failure.contains("row.remove(\"_verified_mls_sender\")"))
        assertTrue(failure.contains("it != SecureControlEnvelope.KEY"))
        val control = source("main/java/com/example/chat/data/messaging/SecureControlEnvelope.kt")
        assertTrue(control.contains("message.authenticatedMlsSender ?: return null"))
    }
    @Test fun admissionPrecedesNativeDispatchAndCanonicalBindingRemovesOptimisticId() {
        val s = source("main/java/com/example/chat/data/messaging/ConversationsRepository.kt")
        val deliver = s.substring(s.indexOf("private suspend fun deliverAndResolve"))
        assertTrue(deliver.indexOf("durableQueue.put") < deliver.indexOf("transport.deliverEncryptedMessage"))
        assertTrue(deliver.contains("it.id == send.clientMessageId && it.senderId == uid"))
    }
}
