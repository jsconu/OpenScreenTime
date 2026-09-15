package org.openscreentime.kid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openscreentime.kid.data.TextSize
import org.openscreentime.kid.data.ThemeMode
import org.openscreentime.kid.data.label
import org.openscreentime.kid.util.PermissionState

@Composable
fun StatusScreen(
    childName: String,
    permissions: PermissionState,
    themeMode: ThemeMode,
    textSize: TextSize,
    streakDays: Int,
    tip: String,
    tipAcknowledged: Boolean,
    tipDismissed: Boolean,
    onTipAcknowledge: () -> Unit,
    onTipDismiss: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onCycleTheme: () -> Unit,
    onCycleTextSize: () -> Unit,
    onOpenColorSettings: () -> Unit,
    onOpenParentMode: () -> Unit,
    onProposeChange: () -> Unit,
    onUnpair: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            "Hi, $childName",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("status_greeting")
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (permissions.allGranted) "Screen time monitoring is active." else "A few permissions are needed to finish setup.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (!tipDismissed) {
            Spacer(Modifier.height(12.dp))
            TipCard(tip, tipAcknowledged, onTipAcknowledge, onTipDismiss)
        }
        if (streakDays > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "$streakDays day${if (streakDays == 1) "" else "s"} in a row under your goal",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("status_streak")
            )
        }
        Spacer(Modifier.height(24.dp))

        PermissionRow(
            "Display over other apps",
            "Needed to show a screen when a limit is reached",
            permissions.overlay,
            onRequestOverlay
        )
        PermissionRow(
            "Accessibility service",
            "Needed to detect which app is open",
            permissions.accessibility,
            onRequestAccessibility
        )
        PermissionRow(
            "Notifications",
            "Shows the ongoing monitoring notification",
            permissions.notifications,
            onRequestNotifications
        )
        PermissionRow(
            "Battery optimization",
            "Stops the system from killing tracking in the background",
            permissions.ignoringBatteryOptimizations,
            onRequestBatteryExemption
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Some phone makers (Samsung, Xiaomi, and others) have their own extra battery " +
                "settings beyond this one - if tracking still stops unexpectedly after enabling " +
                "this, check this phone's battery/app settings for anything mentioning " +
                "\"auto-start,\" \"protected apps,\" or \"sleeping apps.\"",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start
        )

        Spacer(Modifier.height(24.dp))
        Text("Display", style = MaterialTheme.typography.labelLarge)
        ListItem(
            headlineContent = { Text("Theme") },
            supportingContent = { Text(themeMode.label()) },
            trailingContent = { TextButton(onClick = onCycleTheme) { Text("Change") } }
        )
        ListItem(
            headlineContent = { Text("Text size") },
            supportingContent = { Text(textSize.label()) },
            trailingContent = { TextButton(onClick = onCycleTextSize) { Text("Change") } }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Recommended: Android's built-in grayscale mode makes apps noticeably less " +
                "compelling to check, which research shows measurably cuts phone use. This app " +
                "can't turn it on directly - open Accessibility settings below, then look for " +
                "\"Color and motion\" or \"Color correction\" and turn on grayscale. The exact " +
                "wording and location varies by phone.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onOpenColorSettings) { Text("Open Accessibility settings") }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onProposeChange,
            modifier = Modifier.fillMaxWidth().testTag("status_propose_change")
        ) {
            Text("Suggest a change")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onOpenParentMode,
            modifier = Modifier.fillMaxWidth().testTag("status_parent_controls")
        ) {
            Text("Parent controls")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onUnpair, modifier = Modifier.fillMaxWidth()) {
            Text("Unpair this device")
        }
    }
}

/**
 * See #17 - dismissible, not naggy: acknowledging or dismissing never nudges again this
 * app open, and there's no visible "you never do this" state either way.
 */
@Composable
private fun TipCard(
    tip: String,
    acknowledged: Boolean,
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            if (acknowledged) {
                Text(
                    "Nice job! You're practicing great screen time management!",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            } else {
                Text("Idea", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(2.dp))
                Text(tip, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAcknowledge) { Text("I did this") }
                    TextButton(onClick = onDismiss) { Text("Not now") }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            if (granted) {
                Text("Granted", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelMedium)
            } else {
                TextButton(onClick = onClick) { Text("Fix") }
            }
        }
    )
}
