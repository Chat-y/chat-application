package com.example.chat.ui.settings.conversation

import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.chat.domain.models.ConversationPreferences

/** Exact seven-field header projection only; no IDs, names, messages or new visual-map choices. */
internal fun conversationHeaderLookValues(state: ConversationPreferences, includeVisibility: Boolean): Map<String, Any?> = linkedMapOf<String, Any?>(
    "actionBarColor" to state.actionBarColor,
    "contactStatusBgColor" to state.contactStatusBgColor,
    "contactStatusTextColor" to state.contactStatusTextColor,
).let { colors -> if (!includeVisibility) colors else colors + linkedMapOf(
    "hideProfilePicture" to state.hideProfilePicture,
    "hideContactName" to state.hideContactName,
    "hideCallButton" to state.hideCallButton,
    "disableContactStatus" to state.disableContactStatus,
) }

/** User-chosen local document destination. Does not send/share or auto-export. */
@Composable
internal fun ConversationHeaderLookExport(state: ConversationPreferences) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val strings = context.resources
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var saving by androidx.compose.runtime.remember { mutableStateOf(false) }
    var includeVisibility by rememberSaveable { mutableStateOf(false) }
    var pendingJson by rememberSaveable { mutableStateOf<String?>(null) }
    var feedback by rememberSaveable { mutableStateOf<Int?>(null) }
    val destination = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val snapshot = pendingJson
        pendingJson = null
        if (uri != null && snapshot != null) {
            saving = true
            scope.launch {
                try {
                    feedback = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val output = context.contentResolver.openOutputStream(uri, "w") ?: error("No destination stream")
                            output.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) }
                            com.example.chat.R.string.conv_proposal_look_saved
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { com.example.chat.R.string.conv_proposal_look_save_failed }
                    }
                } finally { saving = false }
            }
        }
    }
    com.example.chat.ui.kit.GbCardContainer(headerText = strings.getString(com.example.chat.R.string.conv_proposal_header_look)) {
        androidx.compose.material3.Text(strings.getString(com.example.chat.R.string.conv_proposal_header_look_scope))
        com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_include_header_visibility),
            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_header_visibility_warning), subtitleMaxLines = 1,
            switchValue = includeVisibility, onSwitchChanged = { includeVisibility = it })
        androidx.compose.material3.TextButton(enabled = !saving && pendingJson == null, onClick = {
            val json = org.json.JSONObject()
            conversationHeaderLookValues(state,includeVisibility).forEach { (key,value) -> json.put(key,value ?: org.json.JSONObject.NULL) }
            pendingJson = json.toString(2)
            feedback = null
            try { destination.launch("chaty-header-look.json") }
            catch (_: Exception) { pendingJson = null; feedback = com.example.chat.R.string.conv_proposal_look_save_failed }
        }) { androidx.compose.material3.Text(strings.getString(com.example.chat.R.string.conv_proposal_export_header_look)) }
        feedback?.let { androidx.compose.material3.Text(strings.getString(it)) }
    }
}

// Prior synchronous destination IO retained inactive:
//             feedback = try {
//                 val output = context.contentResolver.openOutputStream(uri, "w") ?: error("No destination stream")
//                 output.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) }
//                 com.example.chat.R.string.conv_proposal_look_saved
//             } catch (_: java.io.IOException) { com.example.chat.R.string.conv_proposal_look_save_failed } catch (_: SecurityException) { com.example.chat.R.string.conv_proposal_look_save_failed } catch (_: IllegalStateException) { com.example.chat.R.string.conv_proposal_look_save_failed }
