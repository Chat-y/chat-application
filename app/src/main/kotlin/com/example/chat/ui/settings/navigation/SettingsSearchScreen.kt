package com.example.chat.ui.settings.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Clear
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.settings.SettingsScreenDestination
import com.example.chat.ui.settings.SettingsSearchEntry
import com.example.chat.ui.theme.LocalAppColors

/** Compose port of settings_search_delegate.dart (api branch anchor 3bee780).
 *  SearchDelegate has no direct Compose equivalent, so this is a full-screen
 *  search page with the same behavior: back closes the search, a trailing
 *  clear empties the query, results and suggestions are the same filtered
 *  list, an empty query shows every entry, and tapping a row closes the
 *  search and navigates ([onSelect] receives the tapped entry).
 *
 *  Two Dart consumers exist, so the screen is generic over the entry shape:
 *  - settings_screen.dart feeds its own 18-entry index (no keywords); use the
 *    [SettingsSearchEntry] overload below (drop32 contract, consumed
 *    directly - no duplicate type or index here).
 *  - settings_root_screen.dart builds its index from
 *    SettingsRegistry.allSettings (real searchKeywords, canonicalRoute); that
 *    caller uses this generic overload with its own entry type, passing
 *    keywords = { it.searchKeywords } and resolving canonicalRoute in
 *    onSelect. */
@Composable
fun <T> SettingsSearchScreen(
    allSettings: List<T>,
    title: (T) -> String,
    category: (T) -> String,
    description: (T) -> String,
    keywords: (T) -> List<String>,
    icon: (T) -> ImageVector,
    onSelect: (T) -> Unit,
    onBack: () -> Unit,
    // SET-R2-010: no caller passes this yet; kept for a future chaty://settings/search?q= deep link.
    initialQuery: String = "",
) {
    val colors = LocalAppColors.current
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialQuery) }
    // SET-R2-024: only re-filter when the query or the index changes
    val results = remember(query, allSettings) { SettingsSearchLogic.filter(query, allSettings, title, category, description, keywords) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth()
                    // SET-R3-024: solid bar so list rows don't slide under a transparent field
                    .background(colors.surface)
                    // SET-R1-044
                    .semantics { paneTitle = "Settings search" }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Close search", color = colors.foreground)
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Search settings" }, // SET-R1-047
                    singleLine = true,
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_search_search_settings), color = colors.foregroundTertiary) }, // was: "Search settings..."
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.clear, semanticLabel = "Clear", color = colors.foreground)
                            }
                        }
                    },
                    // SET-R3-048: visible field boundary
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceSecondary,
                        unfocusedContainerColor = colors.surfaceSecondary,
                        disabledContainerColor = colors.surfaceSecondary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = colors.primary,
                        focusedTextColor = colors.foreground,
                        unfocusedTextColor = colors.foreground,
                    ),
                )
            }
        },
    ) { padding ->
        if (results.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.SearchOff,
                        contentDescription = null,
                        tint = colors.foregroundTertiary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        "No settings found for \"$query\"",
                        modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }.padding(top = 12.dp),
                        color = colors.foregroundTertiary,
                        fontSize = 14.sp,
                    )
                    // SET-R2-047: recovery from an empty result
                    androidx.compose.material3.TextButton(onClick = { query = "" }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_search_clear_search), color = colors.primary) } // was: "Clear search"
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                // SET-R1-052: popular searches while the query is empty
                if (query.isEmpty()) item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        listOf("Privacy", "Notifications", "Theme", "Storage").forEach { term ->
                            androidx.compose.material3.AssistChip(onClick = { query = term }, label = { Text(term) })
                        }
                    }
                }
                items(results) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon(item), contentDescription = null, tint = colors.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title(item), color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                "${category(item)} \u2022 ${description(item)}",
                                color = colors.foregroundSecondary,
                                fontSize = 12.sp,
                                maxLines = 2, // SET-R1-048
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chevronRight,
                            semanticLabel = null,
                            color = colors.foregroundTertiary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Convenience overload for the root Settings worker's drop32 contract:
 *  consumes [SettingsSearchEntry] directly; [onSelect] receives the entry's
 *  [SettingsScreenDestination] (same value space as
 *  SettingsScreen.onNavigate). The Dart settings_screen index never sets
 *  keywords (all 18 entries use the default []), so the keywords leg is
 *  empty here. */
@Composable
fun SettingsSearchScreen(
    allSettings: List<SettingsSearchEntry>,
    onSelect: (SettingsScreenDestination) -> Unit,
    onBack: () -> Unit,
    initialQuery: String = "",
) = SettingsSearchScreen(
    allSettings = allSettings,
    title = { it.title },
    category = { it.category },
    description = { it.description },
    keywords = { it.keywords },
    icon = { it.icon },
    onSelect = { onSelect(it.destination) },
    onBack = onBack,
    initialQuery = initialQuery,
)
