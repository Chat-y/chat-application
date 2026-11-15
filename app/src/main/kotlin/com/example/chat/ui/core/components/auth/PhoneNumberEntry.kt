package com.example.chat.ui.core.components.auth

import android.app.Activity
import android.content.Context
import com.example.chat.ui.auth.AuthTextField
import android.telephony.TelephonyManager
import android.telephony.PhoneNumberUtils
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity

/** Formatting is not ownership. Device selection is stored as an unverified contact claim. */
internal object PhoneNumberEntryPolicy {
    fun normalize(value: String): String? {
        return com.example.chat.data.api.RegistrationPhonePolicy.normalize(value)
    }

    /** Never rewrite an explicit international prefix from a hint. Locale is not SIM country. */
    fun normalizeDeviceHint(value: String, context: Context): String? {
        normalize(value)?.let { return it }
        if (value.trim().startsWith("+")) return null
        val phone = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val country = runCatching { phone?.simCountryIso }.getOrNull()?.takeIf { it.length == 2 }
            ?: runCatching { phone?.networkCountryIso }.getOrNull()?.takeIf { it.length == 2 }
            ?: return null
        return PhoneNumberUtils.formatNumberToE164(value, country.uppercase(java.util.Locale.ROOT))?.let(::normalize)
    }
}

private fun Context.hostActivity(): Activity? {
    var current:Context=this
    val seen=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Context,Boolean>())
    while(seen.add(current)) {
        if(current is Activity) return current
        current=(current as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}

@Composable
internal fun PhoneNumberEntry(value: String, theme: com.example.chat.core.theme.ThemeConfig, enabled: Boolean = true, onValueChange: (String) -> Unit, onNormalized: (String?) -> Unit) {
    val authCopy = com.example.chat.core.i18n.rememberAuthCopy()
    val context = LocalContext.current
    val activity = remember(context) { context.hostActivity() }
    val changed by rememberUpdatedState(onValueChange)
    val normalizedCallback by rememberUpdatedState(onNormalized)
    val currentEnabled by rememberUpdatedState(enabled)
    var hintBusy by remember { mutableStateOf(false) }
    var hintStatus by remember { mutableStateOf<String?>(null) }
    var mounted by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { mounted = false } }
    fun emitValue(next: String) {
        changed(next)
        normalizedCallback(PhoneNumberEntryPolicy.normalize(next))
    }
    val normalized = remember(value) { PhoneNumberEntryPolicy.normalize(value) }
    LaunchedEffect(normalized) { normalizedCallback(normalized) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if(!mounted || !currentEnabled) { hintBusy=false; return@rememberLauncherForActivityResult }
        hintBusy = false
        if (result.resultCode == Activity.RESULT_OK && activity != null) {
            val number = runCatching { Identity.getSignInClient(activity).getPhoneNumberFromIntent(result.data) }.getOrNull()
            val selected = number?.let { PhoneNumberEntryPolicy.normalizeDeviceHint(it, context) }
            if (selected != null) { emitValue(selected); hintStatus = authCopy("Number selected. Unverified.") }
            else hintStatus = "No usable international device number was supplied. Check your SIM or select another device number."
        } else hintStatus = "No number selected. Use device number to choose one."
    }
    val selectDeviceNumber: () -> Unit = chooser@{
            if(!mounted || !currentEnabled || hintBusy) return@chooser
            val host = activity ?: return@chooser
            hintBusy = true; hintStatus = null
            try { Identity.getSignInClient(host).getPhoneNumberHintIntent(GetPhoneNumberHintIntentRequest.builder().build())
                .addOnSuccessListener { pending ->
                    if (mounted && currentEnabled) {
                        try { launcher.launch(IntentSenderRequest.Builder(pending).build()) }
                        catch (_: Exception) { hintBusy = false; hintStatus = "Number chooser is unavailable. Check Google Play services and your SIM." }
                    }
                }
                .addOnFailureListener { if (mounted) { hintBusy = false; hintStatus = "No device number is available. Check your SIM and try again." } }
            } catch (_: Exception) {
                hintBusy = false; hintStatus = "Number chooser is unavailable. Check Google Play services and your SIM."
            }
    }
    Column(Modifier.fillMaxWidth()) {
        AuthTextField(value = value, onValueChange = { }, readOnly = true,
            label = authCopy("Contact number"), hintText = authCopy("Select device number"), theme = theme,
            capsule = true, keyboardType = KeyboardType.Phone,
            suffixIcon = {
                ChatyInlineAction(if (hintBusy) "Opening..." else authCopy("Use device number"), theme, selectDeviceNumber,
                    enabled = enabled && activity != null && !hintBusy)
            },
            error = if (value.isNotBlank() && normalized == null) authCopy("No usable device number") else null)
        if(value.isNotBlank()) Text(authCopy("Number selected from your device. Stored as unverified."))
        hintStatus?.let { Text(authCopy(it)) }
    }
}
