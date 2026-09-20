package org.openscreentime.sharedui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.openscreentime.shared.model.AppUsage
import org.openscreentime.shared.model.ChildProfile
import org.openscreentime.shared.model.FocusProfile

/**
 * The "Dumb phone" switch on a child's screen, or on the parent's own "My screen time" - see #42. Explains what
 * stays available, lets the adult pick more apps, and (for an adult's own phone only) offers a Travel profile
 * that is less strict.
 */
fun LazyListScope.focusModeSection(
    child: ChildProfile,
    isOwnPhone: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onSetProfile: (FocusProfile) -> Unit,
    onPickApps: (travelOnly: Boolean) -> Unit
) {
    item {
        Column(Modifier.padding(16.dp)) {
            Text("Dumb phone", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isOwnPhone) "Keep my phone simple" else "Keep ${child.name}'s phone simple",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Calls, texts and two-step sign-in apps stay available. Everything else is one step away" +
                            (if (isOwnPhone) " - you can open all apps for a few minutes when you need them." else " - only a parent can open more.") +
                            " Needs the accessibility service on, and works best as the phone's home screen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = child.focusMode,
                    onCheckedChange = onSetEnabled,
                    modifier = Modifier.testTag("focus_mode_switch")
                )
            }
            if (child.focusMode) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onPickApps(false) },
                    modifier = Modifier.fillMaxWidth().testTag("focus_pick_apps")
                ) { Text("Choose other allowed apps (${child.focusAllowedPackages.size})") }
                if (isOwnPhone) {
                    Spacer(Modifier.height(12.dp))
                    Text("Profile", style = MaterialTheme.typography.titleSmall)
                    FocusProfileRow(
                        selected = child.focusProfile != FocusProfile.TRAVEL.wireValue,
                        title = "Everyday",
                        detail = "Just the essentials and the apps you chose.",
                        onSelect = { onSetProfile(FocusProfile.STANDARD) }
                    )
                    FocusProfileRow(
                        selected = child.focusProfile == FocusProfile.TRAVEL.wireValue,
                        title = "Travel",
                        detail = "Also lets through wallet and tickets, maps, email, camera and photos, ride-share, translate and calendar.",
                        onSelect = { onSetProfile(FocusProfile.TRAVEL) }
                    )
                    OutlinedButton(
                        onClick = { onPickApps(true) },
                        modifier = Modifier.fillMaxWidth().testTag("focus_pick_travel_apps")
                    ) { Text("Choose more travel apps, like an airline (${child.travelAllowedPackages.size})") }
                }
            }
        }
    }
}

@Composable
private fun FocusProfileRow(selected: Boolean, title: String, detail: String, onSelect: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = { RadioButton(selected = selected, onClick = onSelect) }
    )
}

/** Pick which apps are allowed: every app on the phone, A to Z, with a checkbox each. */
@Composable
fun FocusAppPickerDialog(
    title: String,
    apps: List<AppUsage>,
    selected: Set<String>,
    onToggle: (packageName: String, allowed: Boolean) -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title) },
        text = {
            if (apps.isEmpty()) {
                Text("No apps to show yet. A kid's phone reports its apps about 15 minutes after it first connects.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(apps.sortedBy { it.appName.lowercase() }, key = { it.packageName }) { app ->
                        ListItem(
                            headlineContent = { Text(app.appName) },
                            trailingContent = {
                                Checkbox(
                                    checked = app.packageName in selected,
                                    onCheckedChange = { onToggle(app.packageName, it) }
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } }
    )
}
