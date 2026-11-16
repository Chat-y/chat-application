package com.example.chat.ui.chats.home

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.example.chat.ui.kit.ChatyConfirmationDialog
import com.example.chat.ui.kit.ChatyTextButton

/** Reuses shared modal/button chrome and inherited global theme tokens.
 * Caller owns ephemeral account-keyed text. No repository or network action. */
@Composable
fun HomeScratchpadDialog(text:String,onText:(String)->Unit,onClear:()->Unit,onDismiss:()->Unit) {
    ChatyConfirmationDialog(onDismissRequest=onDismiss,title={Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_temporary_home_scratchpad))},text={  // was: "Temporary Home scratchpad"
        Column {
            Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_temporary_text_only_not_saved_synced_or_sent_it_ca))  // was: "Temporary text only. Not saved, synced or sent. It can be lost when Home closes, the app restarts or accounts change."
            OutlinedTextField(value=text,onValueChange={onText(HomeScratchpadPolicy.bounded(it))},
                label={Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_scratch_text))},modifier=Modifier.fillMaxWidth().heightIn(min=140.dp),  // was: "Scratch text"
                minLines=4,maxLines=10,supportingText={Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_scratch_count, text.codePointCount(0,text.length), HomeScratchpadPolicy.MAX_CODE_POINTS))})  // was: Text("${text.codePointCount(0,text.length)} / ${HomeScratchpadPolicy.MAX_CODE_POINTS} characters")
        }
    },confirmButton={ChatyTextButton(onClick=onDismiss,label="Close")},
      dismissButton={ChatyTextButton(onClick=onClear,enabled=text.isNotEmpty(),label="Clear text")})
}
