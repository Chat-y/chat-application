package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.ChatyChoiceTile
import com.example.chat.ui.kit.ChatyPreviewRadioRow
import com.example.chat.ui.kit.ChatySelectionModal
import com.example.chat.ui.theme.LocalAppColors

/** Conversation-only searchable gallery. A draft choice never changes saved preferences before Apply. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun <T> ConversationStyleGallery(
    title: String,
    values: List<T>,
    selected: T,
    labelOf: (T) -> String,
    keyOf: (T) -> String,
    onSelected: (T) -> Unit,
    familyOf: (T) -> String = { "Styles" },
    subtitle: String? = null,
    quickPickCount: Int = 0,
    enhanced: Boolean = true,
    quickPickKeys: List<String> = emptyList(),
    quickPickTitle: String? = null,
    previewOf: @Composable (T) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val colors = LocalAppColors.current
    var choicesOpen by rememberSaveable(title) { mutableStateOf(false) }
    var family by rememberSaveable(title) { mutableStateOf("") }
    var recentKeys by rememberSaveable(title) { mutableStateOf(arrayListOf<String>()) }
    var query by rememberSaveable(title) { mutableStateOf("") }
    var draftKey by rememberSaveable(title) { mutableStateOf(keyOf(selected)) }
    Column(Modifier.padding(16.dp)) {
        ChatyChoiceTile("$title: ${labelOf(selected)}", {
            draftKey = keyOf(selected)
            query = ""
            family = ""
            choicesOpen = true
        }, Modifier.fillMaxWidth())
        subtitle?.let { Text(it, color = colors.foregroundSecondary, fontSize = 12.sp) }
        if (enhanced && quickPickCount > 0) {
            Text(quickPickTitle ?: strings.getString(com.example.chat.R.string.conv_proposal_quick_picks), color = colors.foregroundSecondary)
            val quick = conversationQuickPicks(values, keyOf, quickPickKeys + recentKeys, quickPickCount)
            LazyRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quick, key = keyOf) { option ->
                    com.example.chat.ui.kit.ChatyOptionCard(label = labelOf(option), selected = keyOf(option) == keyOf(selected),
                        onTap = {
                            // Quick cards enter the existing review/Apply flow; never save a surprise selection.
                            draftKey = keyOf(option); query = ""; family = ""; choicesOpen = true
                        }) { previewOf(option) }
                }
            }
            // Previous stock text action:
            // TextButton(onClick = { draftKey = keyOf(selected); query = ""; family = ""; choicesOpen = true }) {
            //                 Text(strings.getString(com.example.chat.R.string.conv_proposal_see_all))
            //             }
            com.example.chat.ui.kit.ChatyTextButton(
                label = strings.getString(com.example.chat.R.string.conv_proposal_see_all),
                onClick = { draftKey = keyOf(selected); query = ""; family = ""; choicesOpen = true },
            )
        }
    }
    if (choicesOpen && !enhanced) ChatySelectionModal(
        title = title,onDismiss = { choicesOpen = false },
        applyEnabled = draftKey != keyOf(selected) && values.any { keyOf(it) == draftKey },
        onApply = { values.firstOrNull { keyOf(it) == draftKey }?.let(onSelected);choicesOpen = false },
    ) {
        androidx.compose.foundation.lazy.LazyColumn {
            items(values,key = keyOf) { option -> ChatyPreviewRadioRow(label = labelOf(option),selected = keyOf(option) == draftKey,onSelect = { draftKey = keyOf(option) }) }
        }
    }
    else if (choicesOpen) ChatySelectionModal(
        title = title,
        onDismiss = { choicesOpen = false },
        applyEnabled = draftKey != keyOf(selected) && values.any { keyOf(it) == draftKey },
        onApply = {
            values.firstOrNull { keyOf(it) == draftKey }?.let { option ->
                onSelected(option)
                recentKeys = ArrayList((listOf(draftKey) + recentKeys).distinct().take(6))
            }
            choicesOpen = false
        },
    ) {
        Column {
            // Previous stock field:
            // OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            //                 label = { Text(strings.getString(com.example.chat.R.string.conv_helper_search_styles)) }, modifier = Modifier.fillMaxWidth().padding(16.dp))
            com.example.chat.ui.kit.ChatyTaskField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = strings.getString(com.example.chat.R.string.conv_helper_search_styles),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                minLines = 1,
                maxLines = 1,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
            )
            val families = listOf("") + values.map(familyOf).filter { it.isNotEmpty() }.distinct()
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(families, key = { it }) { name ->
                    // Previous: TextButton(onClick = { family = name }, modifier = Modifier.semantics { this.selected = family == name }) {
                    // Previous: val familyLabel = if (name.isEmpty()) strings.getString(com.example.chat.R.string.conv_helper_all) else name
                    // Previous: Text(if (family == name) "✓ $familyLabel" else familyLabel)
                    // Previous: }
                    val familyLabel = if (name.isEmpty()) strings.getString(com.example.chat.R.string.conv_helper_all) else name
                    com.example.chat.ui.kit.ChatyChoiceTile(
                        label = if (family == name) "✓ $familyLabel" else familyLabel,
                        onClick = { family = name }, selected = family == name,
                        modifier = Modifier.semantics { this.selected = family == name },
                        compact = true,
                    )
                }
            }
            val matching = values.filter {
                labelOf(it).contains(query.trim(), ignoreCase = true) && (family.isEmpty() || familyOf(it) == family)
            }
            if (matching.isEmpty()) Text(strings.getString(com.example.chat.R.string.conv_helper_no_styles), color = colors.foregroundSecondary, modifier = Modifier.padding(16.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val recent = recentKeys.mapNotNull { key -> matching.firstOrNull { keyOf(it) == key } }
                if (recent.isNotEmpty() && query.isBlank() && family.isEmpty()) {
                    item(key = "recent-heading") { Text(strings.getString(com.example.chat.R.string.conv_helper_session_recent), color = colors.foregroundSecondary) }
                    items(recent, key = { "recent-${keyOf(it)}" }) { option ->
                        ChatyPreviewRadioRow(label = labelOf(option), selected = keyOf(option) == draftKey,
                            onSelect = { draftKey = keyOf(option) })
                    }
                }
                matching.groupBy(familyOf).forEach { (group, options) ->
                    stickyHeader(key = "family-$group") {
                        Text(group, color = colors.foregroundSecondary,
                            modifier = Modifier.fillMaxWidth().background(colors.surfaceElevated)
                                .padding(vertical = 8.dp).semantics { heading() })
                    }
                    items(options, key = { "style-${keyOf(it)}" }) { option ->
                        Column {
                            ChatyPreviewRadioRow(label = labelOf(option), selected = keyOf(option) == draftKey,
                                onSelect = { draftKey = keyOf(option) })
                            // Full width matters for entry bars with several 48dp actions.
                            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                // Previous per-row live renderer: previewOf(option)
                                // Render only the pending selection; other rows remain cheap searchable labels.
                                if (keyOf(option) == draftKey) previewOf(option)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Session recents first, then existing registry order. No popularity or personal-favourite claim. */
internal fun <T> conversationQuickPicks(values: List<T>, keyOf: (T) -> String, recents: List<String>, count: Int): List<T> {
    val byKey = values.associateBy(keyOf)
    return (recents.mapNotNull(byKey::get) + values).distinctBy(keyOf).take(count.coerceAtLeast(0))
}

// Superseded source retained inactive:
//         if (quickPickCount > 0) {
//     if (choicesOpen) ChatySelectionModal(
