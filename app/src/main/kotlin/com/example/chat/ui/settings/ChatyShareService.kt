package com.example.chat.ui.settings

import android.content.Context
import android.content.Intent

/** Port of Dart ChatyShareService (data/services/chaty_share_service.dart): system share sheet with Chaty text + store link. */
object ChatyShareService {
    const val appName = "Chaty"
    /** Dart: String.fromEnvironment('CHATY_PLAY_STORE_URL', defaultValue: ...). */
    val playStoreUrl: String = com.example.chat.BuildConfig.CHATY_PLAY_STORE_URL
    val shareMessage: String
        // SET-R1-020: avoid an unverified privacy/security promise in outgoing app copy.
        // Previous copy: "Connect with me on $appName - private, fast, and customizable messaging!"
        get() = "Connect with me on $appName.\n\nDownload $appName here: $playStoreUrl"

    /** Returns true if the chooser was launched; on failure returns false and reports the error text. */
    fun shareApp(context: Context, onError: (String) -> Unit = {}): Boolean {
      return try {
        // Only a secure web store link may leave the app; config is not executable intent data.
        val uri = android.net.Uri.parse(playStoreUrl)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) {
            onError("The download link is unavailable. Try again later.")
            return false
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            putExtra(Intent.EXTRA_SUBJECT, "Join me on $appName")
        }
        val chooser = Intent.createChooser(send, null).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        // Previous raw exception disclosure: onError("Could not open share menu: $e")
        onError("Could not open the share menu. Try again.")
        false
      }
    }
}
