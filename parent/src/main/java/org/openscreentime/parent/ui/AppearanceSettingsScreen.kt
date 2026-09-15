package org.openscreentime.parent.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openscreentime.parent.data.AppearancePrefs
import org.openscreentime.parent.data.TextSize
import org.openscreentime.parent.data.ThemeMode
import org.openscreentime.parent.data.label

/**
 * Local display preferences: theme and text size. Independent of the family
 * passcode - these are per-device, not synced anywhere.
 */
@Composable
fun AppearanceSettingsScreen(
    prefs: AppearancePrefs,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onTextSizeChanged: (TextSize) -> Unit,
    onBack: () -> Unit
) {
    var themeMode by remember { mutableStateOf(prefs.themeMode) }
    var textSize by remember { mutableStateOf(prefs.textSize) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text("Theme", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            ThemeMode.entries.forEach { option ->
                OptionRow(
                    label = option.label(),
                    selected = themeMode == option,
                    onSelect = {
                        themeMode = option
                        prefs.themeMode = option
                        onThemeModeChanged(option)
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("Text size", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            TextSize.entries.forEach { option ->
                OptionRow(
                    label = option.label(),
                    selected = textSize == option,
                    onSelect = {
                        textSize = option
                        prefs.textSize = option
                        onTextSizeChanged(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
