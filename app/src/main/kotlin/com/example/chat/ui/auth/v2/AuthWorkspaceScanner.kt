package com.example.chat.ui.auth.v2
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.core.components.auth.*

/** Camera opens only after the explicit permission action. Parsing is not invitation verification. */
@Composable
fun AuthWorkspaceScanner(theme:ThemeConfig,onBack:()->Unit,onDetected:(String)->Unit) {
    val authCopy=com.example.chat.core.i18n.rememberAuthCopy()
 val context=LocalContext.current
 var allowed by remember {mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
 var detected by remember {mutableStateOf(false)}
 val currentDetected by rememberUpdatedState(onDetected)
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed=it}
 ChatyAuthScreen(theme) {
  ChatyBackButton(onBack,theme)
  Text(authCopy("Scan a Chaty workspace QR"),color=com.example.chat.ui.auth.authReadable(theme, theme.primaryTextColor))
  Text(authCopy("A scanned link does not verify an invitation or sign you in."),color=com.example.chat.ui.auth.authReadable(theme, theme.secondaryTextColor))
  if(allowed) com.example.chat.ui.chats.linked_devices.QrScannerView(Modifier.fillMaxWidth().height(280.dp)) {raw->
   if(!detected) {detected=true;currentDetected(raw)}
  } else ChatyAuthLink(authCopy("Allow camera"),theme,{permission.launch(Manifest.permission.CAMERA)})
 }
}
