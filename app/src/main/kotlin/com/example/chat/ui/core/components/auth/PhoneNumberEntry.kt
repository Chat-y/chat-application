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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

internal data class PhoneCountry(val iso:String,val code:String) {
    val name:String get()=java.util.Locale("",iso).getDisplayCountry(java.util.Locale.getDefault())
}
internal object PhoneCountries {
    // ITU calling codes from libphonenumber 9.0.43 metadata, not a default country.
    val all = "AC:247;AD:376;AE:971;AF:93;AG:1;AI:1;AL:355;AM:374;AO:244;AR:54;AS:1;AT:43;AU:61;AW:297;AX:358;AZ:994;BA:387;BB:1;BD:880;BE:32;BF:226;BG:359;BH:973;BI:257;BJ:229;BL:590;BM:1;BN:673;BO:591;BQ:599;BR:55;BS:1;BT:975;BW:267;BY:375;BZ:501;CA:1;CC:61;CD:243;CF:236;CG:242;CH:41;CI:225;CK:682;CL:56;CM:237;CN:86;CO:57;CR:506;CU:53;CV:238;CW:599;CX:61;CY:357;CZ:420;DE:49;DJ:253;DK:45;DM:1;DO:1;DZ:213;EC:593;EE:372;EG:20;EH:212;ER:291;ES:34;ET:251;FI:358;FJ:679;FK:500;FM:691;FO:298;FR:33;GA:241;GB:44;GD:1;GE:995;GF:594;GG:44;GH:233;GI:350;GL:299;GM:220;GN:224;GP:590;GQ:240;GR:30;GT:502;GU:1;GW:245;GY:592;HK:852;HN:504;HR:385;HT:509;HU:36;ID:62;IE:353;IL:972;IM:44;IN:91;IO:246;IQ:964;IR:98;IS:354;IT:39;JE:44;JM:1;JO:962;JP:81;KE:254;KG:996;KH:855;KI:686;KM:269;KN:1;KP:850;KR:82;KW:965;KY:1;KZ:7;LA:856;LB:961;LC:1;LI:423;LK:94;LR:231;LS:266;LT:370;LU:352;LV:371;LY:218;MA:212;MC:377;MD:373;ME:382;MF:590;MG:261;MH:692;MK:389;ML:223;MM:95;MN:976;MO:853;MP:1;MQ:596;MR:222;MS:1;MT:356;MU:230;MV:960;MW:265;MX:52;MY:60;MZ:258;NA:264;NC:687;NE:227;NF:672;NG:234;NI:505;NL:31;NO:47;NP:977;NR:674;NU:683;NZ:64;OM:968;PA:507;PE:51;PF:689;PG:675;PH:63;PK:92;PL:48;PM:508;PR:1;PS:970;PT:351;PW:680;PY:595;QA:974;RE:262;RO:40;RS:381;RU:7;RW:250;SA:966;SB:677;SC:248;SD:249;SE:46;SG:65;SH:290;SI:386;SJ:47;SK:421;SL:232;SM:378;SN:221;SO:252;SR:597;SS:211;ST:239;SV:503;SX:1;SY:963;SZ:268;TA:290;TC:1;TD:235;TG:228;TH:66;TJ:992;TK:690;TL:670;TM:993;TN:216;TO:676;TR:90;TT:1;TV:688;TW:886;TZ:255;UA:380;UG:256;US:1;UY:598;UZ:998;VA:39;VC:1;VE:58;VG:1;VI:1;VN:84;VU:678;WF:681;WS:685;XK:383;YE:967;YT:262;ZA:27;ZM:260;ZW:263".split(';').map { val (iso,code)=it.split(':');PhoneCountry(iso,code) }
    fun detected(context:Context):PhoneCountry? {
        val phone=context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val iso=runCatching {phone?.simCountryIso}.getOrNull()?.takeIf {it.length==2}
            ?:runCatching {phone?.networkCountryIso}.getOrNull()?.takeIf {it.length==2}
        return all.firstOrNull {it.iso.equals(iso,true)}
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
    var country by remember(context) { mutableStateOf(PhoneCountries.detected(context)) }
    var national by remember { mutableStateOf("") }
    var hintCallingCode by remember { mutableStateOf<String?>(null) }
    var pickingCountry by remember { mutableStateOf(false) }
    var countryQuery by remember { mutableStateOf("") }
    fun emitNational(next:String, selected:PhoneCountry? = country) {
        if(!currentEnabled) return
        hintCallingCode=null
        national=next.filter(Char::isDigit).take(15)
        val e164=if(selected != null && national.isNotEmpty())
            PhoneNumberUtils.formatNumberToE164(national,selected.iso)?.let(PhoneNumberEntryPolicy::normalize) else null
        changed(e164 ?: "")
        normalizedCallback(e164)
    }
    var hintBusy by remember { mutableStateOf(false) }
    var hintStatus by remember { mutableStateOf<String?>(null) }
    var mounted by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { mounted = false } }
    fun emitValue(next: String) {
        val candidates=PhoneCountries.all.filter { next.startsWith("+"+it.code) }
        val longest=candidates.maxOfOrNull {it.code.length}
        val precise=candidates.filter {it.code.length==longest}
        val selected=precise.firstOrNull {it.iso==country?.iso} ?: precise.singleOrNull()
        country=selected
        hintCallingCode=precise.firstOrNull()?.code
        national=hintCallingCode?.let {next.removePrefix("+"+it)} ?: ""
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
        AuthTextField(value = national, onValueChange = { emitNational(it) }, readOnly = false,
            label = authCopy("Contact number"), hintText = authCopy("Phone number"), theme = theme,
            capsule = true, keyboardType = KeyboardType.Phone,
            prefixIcon = {
                TextButton(onClick={pickingCountry=true},enabled=enabled) {
                    Text(country?.let {"+${it.code} ▾"} ?: hintCallingCode?.let {"+$it ▾"} ?: authCopy("Code ▾"),color=androidx.compose.ui.graphics.Color(theme.accentColor))
                }
            },
            error = if(national.isNotEmpty() && normalized==null) authCopy("Choose country and enter a valid phone number") else null)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.info,
                color=androidx.compose.ui.graphics.Color(theme.secondaryTextColor),size=ChatyUiTokens.space16)
            Text(authCopy("Stored as unverified."),modifier=Modifier.weight(1f).padding(start=ChatyUiTokens.space8),
                color=androidx.compose.ui.graphics.Color(theme.secondaryTextColor),fontSize=ChatyUiTokens.text12)
            ChatyInlineAction(if(hintBusy) "Opening..." else authCopy("Use device number"),theme,selectDeviceNumber,
                enabled=enabled && activity!=null && !hintBusy)
        }
        hintStatus?.let { Text(authCopy(it),color=androidx.compose.ui.graphics.Color(theme.secondaryTextColor)) }
    }
    if(pickingCountry) AlertDialog(onDismissRequest={pickingCountry=false},
        title={Text(authCopy("Choose country"))},
        text={Column {
            AuthTextField(value=countryQuery,onValueChange={countryQuery=it},label=authCopy("Search country or code"),hintText=authCopy("Country or +code"),theme=theme)
            LazyColumn(Modifier.heightIn(max=ChatyUiTokens.space56*6)) {
                items(PhoneCountries.all.filter {countryQuery.isBlank() || it.name.contains(countryQuery,true) || it.iso.contains(countryQuery,true) || it.code.contains(countryQuery.removePrefix("+"))}.sortedBy {it.name},key={it.iso}) {option ->
                    TextButton(onClick={country=option;pickingCountry=false;emitNational(national,option)},modifier=Modifier.fillMaxWidth().heightIn(min=ChatyUiTokens.tapTarget)) {
                        Text("${option.name} (${option.iso})  +${option.code}",modifier=Modifier.fillMaxWidth())
                    }
                }
            }
        }},confirmButton={TextButton(onClick={pickingCountry=false}) {Text(authCopy("Close"))}})
}
