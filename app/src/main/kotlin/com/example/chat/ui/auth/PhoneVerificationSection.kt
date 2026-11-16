package com.example.chat.ui.auth
import androidx.compose.foundation.layout.padding

import com.example.chat.ui.core.components.auth.ChatyUiTokens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.Modifier
import com.example.chat.ServiceLocator
import com.example.chat.data.api.PhoneVerificationChallenge
import com.example.chat.data.api.PhoneVerificationFailure
import com.example.chat.data.api.PhoneVerificationProof
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Optional verification, not login or account creation. No automatic SMS request or READ_SMS access. */
@Composable
internal fun PhoneVerificationSection(number: String?, tenant: String?, useDefault: Boolean, enabled: Boolean,
    onProof: (PhoneVerificationProof?) -> Unit) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
    key(number, tenant, useDefault) {
        val api = ServiceLocator.apiBackend
        val owner = remember { api.authSnapshot() }
        val latestProof by rememberUpdatedState(onProof)
        val scope = rememberCoroutineScope()
        var challenge by remember { mutableStateOf<PhoneVerificationChallenge?>(null) }
        var proof by remember { mutableStateOf<PhoneVerificationProof?>(null) }
        var code by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf<String?>(null) }
        var retryAt by remember { mutableStateOf<Instant?>(null) }
        var now by remember { mutableStateOf(Instant.now()) }
        LaunchedEffect(Unit) { latestProof(null) }
        LaunchedEffect(retryAt, challenge, proof) {
            while (true) {
                now = Instant.now()
                if (proof?.expiresAt?.isAfter(now) == false) { proof = null; latestProof(null); status = authCopy("Phone proof expired. Verify again or continue unverified.") }
                if (challenge?.expiresAt?.isAfter(now) == false) { challenge = null; code = ""; status = authCopy("Code expired. Request a new code.") }
                if (retryAt?.isAfter(now) != true && challenge == null && proof == null) break
                delay(1_000)
            }
        }
        fun failure(error: Exception) {
            status = error.message ?: authCopy("Verification failed. Continue with an unverified number or try again.")
            (error as? PhoneVerificationFailure)?.retryAfterSeconds?.let { retryAt = Instant.now().plusSeconds(it.toLong()) }
        }
        if (number != null) Column(Modifier.fillMaxWidth(),verticalArrangement=androidx.compose.foundation.layout.Arrangement.spacedBy(ChatyUiTokens.touchGap)) {
            Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.auth_phone_verification_optional))
            OutlinedButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2).fillMaxWidth().heightIn(min = 48.dp), shape = ChatyUiTokens.cardShape,
                enabled = enabled && number != null && !busy && proof == null && retryAt?.isAfter(now) != true,
                onClick = {
                    val target = number ?: return@OutlinedButton
                    if (busy || api.authSnapshot() !== owner) return@OutlinedButton
                    busy = true; status = null; code = ""
                    scope.launch {
                        try {
                            val result = api.requestPhoneVerification(target, tenant, useDefault, owner)
                            if (api.authSnapshot() === owner) { challenge = result; retryAt = result.resendAt; status = authCopy("Enter the code sent by SMS.") }
                        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                        catch (error: Exception) { failure(error) }
                        finally { busy = false }
                    }
                }) { Text(if (busy) authCopy("Working...") else if (retryAt?.isAfter(now) == true) authCopy("Resend available shortly") else if (challenge == null) authCopy("Verify number by SMS") else authCopy("Resend code")) }
            challenge?.let { pending ->
                if (proof == null) {
                    OutlinedTextField(value = code, onValueChange = { code = it.filter { ch -> ch in '0'..'9' }.take(6) },
                        label = { Text(authCopy("Six-digit SMS code")) }, singleLine = true, enabled = enabled && !busy,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    TextButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), enabled = enabled && !busy && code.length == 6, onClick = {
                        if (busy || api.authSnapshot() !== owner) return@TextButton
                        busy = true; status = null
                        val captured = code
                        scope.launch {
                            try {
                                val result = api.verifyPhoneNumber(pending, captured, owner)
                                if (api.authSnapshot() === owner) { proof = result; latestProof(result); code = ""; status = authCopy("Number verified") }
                            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                            catch (error: Exception) { failure(error) }
                            finally { busy = false }
                        }
                    }) { Text(authCopy("Confirm code")) }
                }
            }
            // Prior: status?.let { Text(authCopy(it)) }
            status?.let { Text(authCopy(it), modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) } // A1-42
            if (proof != null) TextButton(modifier = Modifier.padding(ChatyUiTokens.touchGap / 2), onClick = { proof = null; challenge = null; code = ""; latestProof(null); status = authCopy("Number will be stored as unverified.") }, enabled = !busy) { Text(authCopy("Continue unverified")) }
        }
    }
}
