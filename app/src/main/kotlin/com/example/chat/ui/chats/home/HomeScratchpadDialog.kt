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
    ChatyConfirmationDialog(onDismissRequest=onDismiss,title={Text("Temporary Home scratchpad")},text={
        Column {
            Text("Temporary text only. Not saved, synced or sent. It can be lost when Home closes, the app restarts or accounts change.")
            OutlinedTextField(value=text,onValueChange={onText(HomeScratchpadPolicy.bounded(it))},
                label={Text("Scratch text")},modifier=Modifier.fillMaxWidth().heightIn(min=140.dp),
                minLines=4,maxLines=10,supportingText={Text("${text.codePointCount(0,text.length)} / ${HomeScratchpadPolicy.MAX_CODE_POINTS} characters")})
        }
    },confirmButton={ChatyTextButton(onClick=onDismiss,label="Close")},
      dismissButton={ChatyTextButton(onClick=onClear,enabled=text.isNotEmpty(),label="Clear text")})
}
