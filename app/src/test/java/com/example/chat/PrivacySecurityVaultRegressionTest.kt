package com.example.chat

import com.example.chat.domain.models.*
import com.example.chat.ui.settings.privacy.PrivacyOptionsLogic
import com.example.chat.ui.settings.security.*
import com.example.chat.ui.vault.*
import com.example.chat.data.services.LocalLockService
import com.example.chat.data.e2ee.SecureKeyValueStore
import kotlinx.coroutines.*
import com.example.chat.ui.settings.MediaVisibilityMarkers
import org.junit.Test

private class PrivacyRegressionStore : SecureKeyValueStore {
    val values = mutableMapOf<String, String>()
    var failWrite = false
    var failRead = false
    override suspend fun read(key: String): String? { if(failRead) error("read failed"); return values[key] }
    override suspend fun write(key: String, value: String) { if(failWrite) error("write failed"); values[key]=value }
    override suspend fun delete(key: String) { values.remove(key) }
}
private class PrivacyRegressionSetup : LockSetupService {
    var secret = ""; var throwVerify = false
    override suspend fun hasCredential(method:String)= secret.isNotEmpty()
    override suspend fun verifyCredential(method:String, secret:String):Boolean { if(throwVerify) error("private error");return this.secret==secret }
    override suspend fun setCredential(method:String,secret:String,pinLength:Int?) {this.secret=secret}
    override suspend fun authenticateBiometric(reason:String)=false
    override suspend fun authenticateDeviceCredential(reason:String)=false
}
class PrivacySecurityVaultRegressionTest {
@Test fun coreRegressionChecks() = runBlocking {
    var checks=0
    fun verify(value:Boolean) {check(value) { "check ${checks+1} failed" }; checks++}
    val original=PrivacyPreferences(antiDisappearingMessages=false,hideOnlineStatus=true)
    verify(PrivacyPreferences.fromMap(original.toMap())==original)
    for(label in PrivacyOptionsLogic.audienceLabels) verify(PrivacyOptionsLogic.audienceLabel(PrivacyOptionsLogic.audiencePolicy(label))==label)
    verify(PrivacyOptionsLogic.audiencePolicy("My Contacts Except...")=="contacts_except")
    verify(PrivacyOptionsLogic.onlinePreferenceValue("same_as_last_seen")=="Same as Last Seen")
    val policy=PrivacyPublicationPolicy()
    for(label in listOf("Same as Last Seen","Same as last seen","same_as_last_seen")) verify(!policy.allows(PrivacyPublication.presence,PrivacyPreferencesSnapshot(hideLastSeenAudience="Nobody",hideOnlineAudience=label)))
    for(pin in listOf("1234","4321","1212","1111","123456","654321","121212")) verify(CredentialStrength.pinError(pin)!=null)
    verify(CredentialStrength.pinError("1357")==null)
    verify(CredentialStrength.passwordError("1234567")!=null)
    verify(CredentialStrength.passwordError("  safe word  ")==null)
    verify(CredentialStrength.phraseError("secret")!=null)
    val setup=PrivacyRegressionSetup(); val flow=LockCredentialSetupFlow("Password",4,setup); flow.init()
    verify(flow.handleContinue("  safe word  ")==SetupOutcome.clearText)
    verify(flow.handleContinue("  safe word  ")==SetupOutcome.saved)
    verify(setup.secret=="  safe word  " && !flow.busy)
    setup.throwVerify=true
    val flow2=LockCredentialSetupFlow("Password",4,setup); flow2.init()
    verify(flow2.handleContinue("anything")==SetupOutcome.none && !flow2.busy)
    val store=PrivacyRegressionStore(); val local=LocalLockService(store); var now=1000L
    val vault=HiddenVaultService(local,store,now={now})
    verify(!vault.verifyPin("1357"))
    verify(!vault.verifyPin("0000"))
    local.setCredential("pin","1357",4)
    verify(!vault.verifyPin("123456"))
    verify(vault.verifyPin("1357") && vault.isUnlocked)
    vault.toggleConversationLock("one")
    verify(vault.lockedConversationIds==setOf("one"))
    store.failWrite=true
    verify(runCatching { vault.toggleConversationLock("two") }.isFailure)
    verify(vault.lockedConversationIds==setOf("one") && vault.registryError!=null)
    store.failWrite=false
    vault.removeConversationLock("one"); vault.removeConversationLock("one")
    verify(vault.lockedConversationIds.isEmpty())
    store.failRead=true; vault.loadVaultRegistry(); verify(vault.registryError!=null && !vault.registryLoading)
    store.failRead=false; vault.loadVaultRegistry(); verify(vault.registryError==null)
    store.values["chaty.hidden_vault.decoy_pin"] = "8642"
    vault.lockVault()
    verify(vault.verifyPin("8642") && vault.isDecoyMode)
    vault.lockVault()
    verify(vault.verifyPin("1357") && !vault.isDecoyMode)
    val attemptsBeforeEnrollment = local.getFailedAttempts()
    vault.setDecoyPin("2468")
    verify(local.getFailedAttempts() == attemptsBeforeEnrollment)
    verify(store.values.keys.any {it.endsWith("decoy_pin.hash")} && store.values.values.none {it=="2468"})
    verify("chaty.hidden_vault.decoy_pin" !in store.values)
    verify(runCatching{vault.setDecoyPin("1357")}.isFailure)
    vault.lockVault(); verify(vault.verifyPin("2468") && vault.isDecoyMode && vault.lockedConversationIds.isEmpty())
    vault.lockVault(); verify(vault.verifyPin("1357") && !vault.isDecoyMode)
    now += vault.autoLockDurationMs; verify(!vault.isUnlocked)
    val gate=CompletableDeferred<Boolean>()
    val slow=HiddenVaultService(local,store,authenticateBiometric={gate.await()})
    val attempt=async { slow.unlockWithBiometrics() };yield()
    slow.resetForAccountSwitchSync("other"); gate.complete(true)
    verify(!attempt.await() && !slow.isUnlocked)
    // Ordinary nonmatching search must not increase App Lock failure counters.
    local.setSecretPhrase("a safe phrase")
    val before=local.getFailedAttempts(); local.verifySecretPhrase("ordinary query")
    verify(local.getFailedAttempts()==before)
    val directory = java.nio.file.Files.createTempDirectory("chaty-media-check").toFile()
    try {
        MediaVisibilityMarkers.sync(directory, true, false, true)
        val markerTimestamp = java.io.File(directory, "Photos/.nomedia").lastModified()
        MediaVisibilityMarkers.sync(directory, true, false, true)
        verify(java.io.File(directory, "Photos/.nomedia").lastModified() == markerTimestamp)
        verify(!java.io.File(directory, ".nomedia").exists())
        verify(java.io.File(directory, "Photos/.nomedia").isFile)
        verify(!java.io.File(directory, "Videos/.nomedia").exists())
        verify(java.io.File(directory, "GIFs/.nomedia").isFile)
        MediaVisibilityMarkers.sync(directory, false, true, false)
        verify(!java.io.File(directory, "Photos/.nomedia").exists())
        verify(java.io.File(directory, "Videos/.nomedia").isFile)
        MediaVisibilityMarkers.sync(directory, false, false, false)
        verify(!java.io.File(directory, "Videos/.nomedia").exists())
        val badRoot = java.io.File(directory, "not-a-directory").apply { writeText("file") }
        verify(runCatching { MediaVisibilityMarkers.sync(badRoot, true, true, true) }.isFailure)
    } finally { directory.deleteRecursively() }
    val cancelledGate=CompletableDeferred<Boolean>()
    val pendingVault=HiddenVaultService(local,store,authenticateBiometric={cancelledGate.await()})
    val pendingAttempt=async { pendingVault.unlockWithBiometrics() };yield()
    pendingVault.lockVault();cancelledGate.complete(true)
    verify(!pendingAttempt.await() && !pendingVault.isUnlocked)
    // Service-level acceptance probes: these do not stand in for device/UI tests.
    val isolatedStore=PrivacyRegressionStore()
    var clock=10_000L
    val isolatedLock=LocalLockService(isolatedStore, now={clock})
    val isolatedVault=HiddenVaultService(isolatedLock,isolatedStore,now={clock})
    isolatedLock.setCredential("pin","135790",6)
    verify(isolatedVault.getPinLength()==6)
    verify(!isolatedVault.verifyPin("1357"))
    verify(!isolatedVault.verifyPin(""))
    verify(!isolatedVault.verifyPin("123"))
    verify(!isolatedVault.verifyPin("abcdef"))
    verify(isolatedVault.verifyPin("135790"))
    verify(!isolatedVault.verifyPin("235790"))
    isolatedVault.lockVault()
    verify(!isolatedVault.isDecoyMode && !isolatedVault.isUnlocked)
    verify(runCatching { isolatedVault.toggleConversationLock("forbidden") }.isFailure)
    verify(isolatedVault.verifyPin("135790"))
    coroutineScope {
        listOf("a","b","c").map { id -> async { isolatedVault.toggleConversationLock(id) } }.awaitAll()
    }
    verify(isolatedVault.lockedConversationIds==setOf("a","b","c"))
    isolatedVault.loadVaultRegistry()
    verify(isolatedVault.lockedConversationIds==setOf("a","b","c"))
    isolatedVault.recordActivity()
    verify(isolatedVault.lastActivityTime==clock)
    isolatedVault.onAppPaused()
    verify(!isolatedVault.isUnlocked)
    val forwardedController=HiddenVaultController(isolatedVault)
    var notifications=0
    val listener:()->Unit={notifications++}
    forwardedController.addListener(listener)
    isolatedVault.lockVault()
    verify(notifications>0)
    forwardedController.removeListener(listener)
    val removedCount=notifications
    isolatedVault.lockVault()
    verify(notifications==removedCount)
    forwardedController.addListener(listener)
    forwardedController.dispose()
    isolatedVault.lockVault()
    verify(notifications==removedCount)
    isolatedVault.switchAccountNamespace("second",migrateLegacyIfEmpty=false)
    verify(isolatedVault.lockedConversationIds.isEmpty())
    isolatedVault.switchAccountNamespace("",migrateLegacyIfEmpty=false)
    verify(isolatedVault.lockedConversationIds==setOf("a","b","c"))
    // Retry availability progresses without reopening the lock overlay.
    val appController=com.example.chat.ui.settings.security.AppLockController(isolatedLock,{"PIN"},"test",{})
    repeat(5) { isolatedLock.recordFailedAttempt() }
    appController.loadCapabilities()
    verify(appController.state.value.cooldownSeconds>0)
    clock+=31_000
    appController.refreshCooldown()
    verify(appController.state.value.cooldownSeconds==0)
    val beforeInvalid=appController.state.value.enteredPin
    appController.verifyPinDigit("ab")
    verify(appController.state.value.enteredPin==beforeInvalid)
    val biometricStore=PrivacyRegressionStore()
    val biometricLock=LocalLockService(biometricStore)
    val biometricVault=HiddenVaultService(biometricLock,biometricStore,authenticateBiometric={true})
    verify(biometricVault.unlockWithBiometrics() && biometricVault.isUnlocked)
    biometricVault.lockVault()
    val cancelledVault=HiddenVaultService(biometricLock,biometricStore,authenticateBiometric={false})
    verify(!cancelledVault.unlockWithBiometrics() && !cancelledVault.isUnlocked)
    val throwingVault=HiddenVaultService(biometricLock,biometricStore,authenticateBiometric={error("private adapter failure")})
    verify(!throwingVault.unlockWithBiometrics() && !throwingVault.isUnlocked)
    val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Boolean>()
    var concurrent=0;var maximumConcurrent=0
    val serializedVault=HiddenVaultService(biometricLock,biometricStore,authenticateBiometric={
        concurrent++;maximumConcurrent=maxOf(maximumConcurrent,concurrent);entered.complete(Unit)
        try { release.await() } finally { concurrent-- }
    })
    val firstPrompt=async { serializedVault.unlockWithBiometrics() };entered.await()
    val secondPrompt=async { serializedVault.unlockWithBiometrics() };yield()
    release.complete(false)
    verify(!firstPrompt.await() && !secondPrompt.await() && maximumConcurrent==1)
    val cancellingVault=HiddenVaultService(biometricLock,biometricStore,authenticateBiometric={throw CancellationException("cancelled")})
    verify(runCatching { cancellingVault.unlockWithBiometrics() }.exceptionOrNull() is CancellationException)
    println("PASS: $checks core checks")
}
}
