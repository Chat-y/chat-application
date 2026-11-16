package com.example.chat.ui.auth

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.core.components.auth.ChatyAuthLink

/** Only workspace IDs, never account identifiers, credentials or response contents. */
object AuthWorkspaceHistory {
    fun read(context:Context):List<String> = context.getSharedPreferences("chaty_auth_workspaces",Context.MODE_PRIVATE)
        .getString("recent","").orEmpty().split('\n').filter {it.isNotBlank()}.take(5)
    fun record(context:Context,workspace:String) {
        val clean=workspace.trim()
        if(clean.isBlank() || clean.length>128 || clean.any(Char::isISOControl)) return
        // Previous: val recent=(listOf(clean)+read(context)).distinct().take(5)
        val recent=(listOf(clean)+read(context)).distinctBy {it.lowercase(java.util.Locale.ROOT)}.take(5)
        context.getSharedPreferences("chaty_auth_workspaces",Context.MODE_PRIVATE).edit().putString("recent",recent.joinToString("\n")).apply()
    }
}
object AuthWorkspacePolicy {
    fun slugSuggestion(raw:String):String? {
        val value=raw.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"),"-")
        return value.takeIf {it.isNotBlank() && it!=raw && it.matches(Regex("[a-z0-9][a-z0-9_-]{0,127}"))}
    }
    fun suggestions(raw:String,recent:List<String>):List<String> = recent.distinct().filter {
        it!=raw && it.startsWith(raw.trim(),ignoreCase=true)
    }.take(3)
}
@Composable
fun AuthWorkspaceSuggestions(workspace:String,theme:ThemeConfig,onSelect:(String)->Unit) {
    val context=LocalContext.current
    val recent=remember(context) {AuthWorkspaceHistory.read(context)}
    val suggestions=remember(workspace,recent) {AuthWorkspacePolicy.suggestions(workspace,recent)}
    val slug=remember(workspace) {AuthWorkspacePolicy.slugSuggestion(workspace)}
    suggestions.forEach {candidate->ChatyAuthLink(com.example.chat.core.i18n.authFormatted("Use recent workspace: %1\$s",candidate),theme,{onSelect(candidate)})}
    if(slug!=null && slug !in suggestions) ChatyAuthLink(com.example.chat.core.i18n.authFormatted("Use Workspace ID format: %1\$s",slug),theme,{onSelect(slug)})
}
