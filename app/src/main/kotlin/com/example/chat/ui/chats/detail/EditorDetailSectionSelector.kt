package com.example.chat.ui.chats.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** MR-R3-005 UI-only disclosure; selecting a panel never resets any edit. */
@Composable
internal fun EditorDetailSectionSelector(label: String, selected: String, sections: List<String>, enabled: Boolean, keyLabels: Map<String, String> = emptyMap(), onSelect: (String) -> Unit) {
    // Keep raw section keys for selection and callbacks; labels can vary by locale.
    val chosenLabel = keyLabels[selected] ?: selected
    val selectorLabel = stringResource(com.example.chat.R.string.media_released_editor_section_format, label, chosenLabel)
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = selectorLabel }) {
            // MR-R4-043 prior: Text("$label: $selected")
            Text(selectorLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sections.forEach { section ->
                // MR-R4-043 prior: DropdownMenuItem(text = { Text(section) }
                DropdownMenuItem(text = { Text(keyLabels[section] ?: section) }, enabled = enabled,
                    modifier = Modifier.semantics { this.selected = section == selected },
                    onClick = { onSelect(section); expanded = false })
            }
        }
    }
}
